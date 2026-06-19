package com.ulysses.app.data.db

import androidx.room.*
import com.ulysses.app.data.db.entities.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockListDao {
    @Query("SELECT * FROM block_lists ORDER BY updatedAt DESC")
    fun getAllLists(): Flow<List<BlockListEntity>>

    @Query("SELECT * FROM block_lists WHERE id = :id")
    suspend fun getListById(id: String): BlockListEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertList(list: BlockListEntity)

    @Update
    suspend fun updateList(list: BlockListEntity)

    @Delete
    suspend fun deleteList(list: BlockListEntity)

    @Query("SELECT * FROM block_list_entries WHERE listId = :listId AND isActive = 1")
    fun getEntriesForList(listId: String): Flow<List<BlockListEntryEntity>>

    @Query("SELECT * FROM block_list_entries WHERE listId = :listId AND isActive = 1")
    suspend fun getEntriesForListSync(listId: String): List<BlockListEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: BlockListEntryEntity)

    @Delete
    suspend fun deleteEntry(entry: BlockListEntryEntity)

    @Query("DELETE FROM block_list_entries WHERE listId = :listId")
    suspend fun deleteAllEntriesForList(listId: String)
}

@Dao
interface BlockDao {
    @Query("SELECT * FROM blocks ORDER BY updatedAt DESC")
    fun getAllBlocks(): Flow<List<BlockEntity>>

    @Query("SELECT * FROM blocks WHERE id = :id")
    suspend fun getBlockById(id: String): BlockEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlock(block: BlockEntity)

    @Update
    suspend fun updateBlock(block: BlockEntity)

    @Delete
    suspend fun deleteBlock(block: BlockEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAssociation(assoc: BlockListAssociation)

    @Query("DELETE FROM block_list_associations WHERE blockId = :blockId")
    suspend fun deleteAssociationsForBlock(blockId: String)

    @Query("""
        SELECT bl.* FROM block_lists bl 
        INNER JOIN block_list_associations bla ON bl.id = bla.listId 
        WHERE bla.blockId = :blockId
    """)
    suspend fun getListsForBlock(blockId: String): List<BlockListEntity>

    @Query("""
        SELECT ble.* FROM block_list_entries ble 
        INNER JOIN block_list_associations bla ON ble.listId = bla.listId 
        WHERE bla.blockId = :blockId AND ble.isActive = 1
    """)
    suspend fun getAllEntriesForBlock(blockId: String): List<BlockListEntryEntity>
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM block_sessions WHERE isActive = 1")
    fun getActiveSessions(): Flow<List<BlockSessionEntity>>

    @Query("SELECT * FROM block_sessions WHERE isActive = 1")
    suspend fun getActiveSessionsSync(): List<BlockSessionEntity>

    @Query("SELECT * FROM block_sessions WHERE id = :id")
    suspend fun getSessionById(id: String): BlockSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: BlockSessionEntity)

    @Update
    suspend fun updateSession(session: BlockSessionEntity)

    @Query("UPDATE block_sessions SET isActive = 0 WHERE id = :sessionId")
    suspend fun deactivateSession(sessionId: String)

    @Query("SELECT * FROM block_sessions ORDER BY startedAt DESC")
    fun getAllSessions(): Flow<List<BlockSessionEntity>>
}

@Dao
interface TriggerDao {
    @Query("SELECT * FROM triggers WHERE isDeleted = 0 ORDER BY updatedAt DESC")
    fun getAllTriggers(): Flow<List<TriggerEntity>>

    @Query("SELECT * FROM triggers WHERE id = :id")
    suspend fun getTriggerById(id: String): TriggerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrigger(trigger: TriggerEntity)

    @Update
    suspend fun updateTrigger(trigger: TriggerEntity)

    @Query("UPDATE triggers SET isDeleted = 1, lamportTimestamp = lamportTimestamp + 1 WHERE id = :id")
    suspend fun softDeleteTrigger(id: String)
}

@Dao
interface DnsCategoryDao {
    @Query("SELECT * FROM dns_categories ORDER BY name")
    fun getAllCategories(): Flow<List<DnsCategoryEntity>>

    @Query("SELECT * FROM dns_categories WHERE isActive = 1")
    suspend fun getActiveCategoriesSync(): List<DnsCategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: DnsCategoryEntity)

    @Update
    suspend fun updateCategory(category: DnsCategoryEntity)

    @Query("SELECT domain FROM dns_blocked_domains WHERE categoryId IN (SELECT id FROM dns_categories WHERE isActive = 1)")
    suspend fun getAllActiveBlockedDomains(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDomains(domains: List<DnsBlockedDomainEntity>)

    @Query("DELETE FROM dns_blocked_domains WHERE categoryId = :categoryId")
    suspend fun deleteDomainsByCategory(categoryId: String)
}
