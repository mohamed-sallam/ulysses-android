package com.ulysses.app.data.repository

import android.content.Context
import com.ulysses.app.data.db.UlyssesDatabase
import com.ulysses.app.data.db.entities.*
import com.ulysses.app.network.DhtNetworkManager
import com.ulysses.app.UlyssesApp
import kotlinx.coroutines.flow.Flow
import java.security.MessageDigest

class UlyssesRepository(private val db: UlyssesDatabase, private val context: Context? = null) {

    private val networkManager get() = UlyssesApp.instance.networkManager

    // ── Block Lists ──
    fun getAllLists(): Flow<List<BlockListEntity>> = db.blockListDao().getAllLists()

    suspend fun getListById(id: String) = db.blockListDao().getListById(id)

    suspend fun createList(name: String): BlockListEntity {
        val list = BlockListEntity(name = name)
        db.blockListDao().insertList(list)
        return list
    }

    suspend fun updateList(list: BlockListEntity) = db.blockListDao().updateList(list)
    suspend fun deleteList(list: BlockListEntity) {
        db.blockListDao().deleteAllEntriesForList(list.id)
        db.blockListDao().deleteList(list)
    }

    fun getEntriesForList(listId: String): Flow<List<BlockListEntryEntity>> =
        db.blockListDao().getEntriesForList(listId)

    suspend fun addEntry(listId: String, type: String, ruleType: String, value: String) {
        db.blockListDao().insertEntry(
            BlockListEntryEntity(listId = listId, type = type, ruleType = ruleType, value = value)
        )
    }

    suspend fun deleteEntry(entry: BlockListEntryEntity) = db.blockListDao().deleteEntry(entry)

    // ── Blocks ──
    fun getAllBlocks(): Flow<List<BlockEntity>> = db.blockDao().getAllBlocks()

    suspend fun getBlockById(id: String) = db.blockDao().getBlockById(id)

    suspend fun createBlock(
        name: String,
        description: String,
        lockType: String,
        lockValue: String,
        iconEmoji: String,
        listIds: List<String>
    ): BlockEntity {
        val block = BlockEntity(
            name = name,
            description = description,
            defaultLockType = lockType,
            defaultLockValue = lockValue,
            iconEmoji = iconEmoji
        )
        db.blockDao().insertBlock(block)
        listIds.forEach { listId ->
            db.blockDao().insertAssociation(BlockListAssociation(block.id, listId))
        }
        return block
    }

    suspend fun updateBlock(block: BlockEntity) = db.blockDao().updateBlock(block)

    suspend fun deleteBlock(block: BlockEntity) {
        db.blockDao().deleteAssociationsForBlock(block.id)
        db.blockDao().deleteBlock(block)
    }

    suspend fun getListsForBlock(blockId: String) = db.blockDao().getListsForBlock(blockId)
    suspend fun getAllEntriesForBlock(blockId: String) = db.blockDao().getAllEntriesForBlock(blockId)

    // ── Sessions ──
    fun getActiveSessions(): Flow<List<BlockSessionEntity>> = db.sessionDao().getActiveSessions()
    suspend fun getActiveSessionsSync(): List<BlockSessionEntity> = db.sessionDao().getActiveSessionsSync()

    suspend fun startSession(
        blockId: String,
        lockType: String,
        lockValue: String,
        triggerId: String? = null,
        propagate: Boolean = false
    ): BlockSessionEntity {
        val endsAt = if (lockType == "timer") {
            System.currentTimeMillis() + lockValue.toLong()
        } else null

        val session = BlockSessionEntity(
            blockId = blockId,
            triggerId = triggerId,
            lockType = lockType,
            lockValue = if (lockType == "timer") {
                (System.currentTimeMillis() + lockValue.toLong()).toString()
            } else lockValue,
            endsAt = endsAt,
            propagateToNetwork = propagate
        )
        db.sessionDao().insertSession(session)

        context?.let { ctx ->
            com.ulysses.app.protection.LauncherManager.enableLauncherComponent(ctx)
            val intent = android.content.Intent(ctx, com.ulysses.app.service.SessionForegroundService::class.java).apply {
                action = com.ulysses.app.service.SessionForegroundService.ACTION_CHECK_SESSIONS
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                ctx.startForegroundService(intent)
            } else {
                ctx.startService(intent)
            }
        }
        
        return session
    }

    suspend fun endSession(sessionId: String, authToken: String? = null): Boolean {
        val session = db.sessionDao().getSessionById(sessionId) ?: return false
        if (!session.isActive) return false

        when (session.lockType) {
            "timer" -> {
                val expiryTime = session.lockValue.toLongOrNull() ?: return false
                if (System.currentTimeMillis() < expiryTime) return false
            }
            "password" -> {
                if (authToken == null) return false
                val hash = hashPassword(authToken)
                if (hash != session.lockValue) return false
            }
        }

        db.sessionDao().deactivateSession(sessionId)

        // Broadcast disable event to network if this session was started by a propagating trigger
        if (session.triggerId != null && session.propagateToNetwork) {
            val trigger = db.triggerDao().getTriggerById(session.triggerId)
            if (trigger != null) {
                networkManager?.publishDisableEvent(trigger.id, trigger.lamportTimestamp)
            }
        }
        
        return true
    }

    suspend fun forceEndSession(sessionId: String) {
        db.sessionDao().deactivateSession(sessionId)
    }

    fun getAllSessions(): Flow<List<BlockSessionEntity>> = db.sessionDao().getAllSessions()

    // ── Triggers ──
    fun getAllTriggers(): Flow<List<TriggerEntity>> = db.triggerDao().getAllTriggers()

    suspend fun createTrigger(
        name: String,
        blockId: String,
        propagateToNetwork: Boolean = false
    ): TriggerEntity {
        val trigger = TriggerEntity(
            name = name,
            blockId = blockId,
            propagateToNetwork = propagateToNetwork
        )
        db.triggerDao().insertTrigger(trigger)
        networkManager?.publishTrigger(trigger)
        return trigger
    }

    suspend fun deleteTrigger(id: String) {
        val trigger = db.triggerDao().getTriggerById(id)
        if (trigger != null) {
             db.triggerDao().softDeleteTrigger(id)
             networkManager?.publishTombstone(id, trigger.lamportTimestamp + 1)
        }
    }

    suspend fun fireTrigger(triggerId: String): BlockSessionEntity? {
        val trigger = db.triggerDao().getTriggerById(triggerId) ?: return null
        val block = db.blockDao().getBlockById(trigger.blockId) ?: return null

        if (trigger.propagateToNetwork) {
            networkManager?.publishFireEvent(trigger.id, trigger.lamportTimestamp)
        }

        return startSession(
            blockId = block.id,
            lockType = block.defaultLockType,
            lockValue = block.defaultLockValue,
            triggerId = triggerId,
            propagate = trigger.propagateToNetwork
        )
    }

    // ── DNS Categories ──
    fun getAllDnsCategories(): Flow<List<DnsCategoryEntity>> = db.dnsCategoryDao().getAllCategories()
    suspend fun getActiveBlockedDomains(): List<String> = db.dnsCategoryDao().getAllActiveBlockedDomains()

    suspend fun toggleDnsCategory(category: DnsCategoryEntity) {
        db.dnsCategoryDao().updateCategory(category.copy(isActive = !category.isActive))
    }

    suspend fun seedDefaultDnsCategories() {
        val existing = db.dnsCategoryDao().getActiveCategoriesSync()
        if (existing.isEmpty()) {
            val categories = listOf(
                DnsCategoryEntity(
                    name = "Social Media",
                    description = "Facebook, Instagram, Twitter, TikTok, etc.",
                    domainCount = 0
                ),
                DnsCategoryEntity(
                    name = "Adult Content",
                    description = "Adult websites and content",
                    domainCount = 0
                ),
                DnsCategoryEntity(
                    name = "Gaming",
                    description = "Online gaming platforms",
                    domainCount = 0
                ),
                DnsCategoryEntity(
                    name = "Streaming",
                    description = "Netflix, YouTube, Twitch, etc.",
                    domainCount = 0
                ),
                DnsCategoryEntity(
                    name = "News",
                    description = "News websites and aggregators",
                    domainCount = 0
                )
            )
            categories.forEach { db.dnsCategoryDao().insertCategory(it) }
        }
    }

    // ── Utilities ──
    companion object {
        fun hashPassword(password: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val bytes = digest.digest(password.toByteArray())
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}
