package com.ulysses.app.network

import android.content.Context
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.ulysses.app.data.db.UlyssesDatabase
import com.ulysses.app.data.db.entities.TriggerEntity
import com.ulysses.app.data.repository.UlyssesRepository
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Cross-device synchronization using OpenDHT via its REST Proxy API.
 *
 * Architecture (matching the Linux daemon's network_manager.cpp):
 * - Connects to an OpenDHT proxy node (default: bootstrap.jami.net or self-hosted)
 * - Uses the same DHT key derivation: SHA-1 of "ulysses:<channel>:<groupUuid><salt>"
 * - Same AES-256-GCM encryption with the shared group secret
 * - Same message protocol: presence, trigger_sync, fire, disable
 * - Same CRDT: LWW with Lamport timestamps, tie-break on UUID
 * - Same nonce-based replay protection
 *
 * REST API (from OpenDHT wiki):
 *   GET  /key/{infohash}        → get values
 *   POST /key/{infohash}        → put value  (body: {"data":"base64"})
 *   GET  /key/{infohash}/listen  → long-poll listen (streaming JSON)
 *
 * This is protocol-compatible with the Linux daemon's DhtRunner put/listen.
 */
class DhtNetworkManager(private val context: Context) {

    companion object {
        private const val TAG = "DhtNetwork"
        // Default OpenDHT proxy — you can self-host one with:
        //   dhtnode -n 0 -p 4222 --proxyserver 8000 -b bootstrap.jami.net:4222
        // Or use a public Jami proxy if available
        private const val DEFAULT_PROXY_URL = "http://bootstrap.jami.net:8000"
        private const val GCM_TAG_BITS = 128
        private const val GCM_NONCE_LENGTH = 12
    }

    // Group identity (matches C++ GroupInfo struct)
    private var groupUuid: String? = null
    private var groupSecret: ByteArray? = null // 32-byte AES key
    private var salt: ByteArray? = null         // 16-byte salt
    private var deviceId: String = UUID.randomUUID().toString()
    private var proxyUrl: String = DEFAULT_PROXY_URL

    // Known peers
    private val peers = ConcurrentHashMap<String, PeerInfo>()

    // Replay protection
    private val seenNonces = ConcurrentHashMap<String, Long>()

    // Lamport clock
    @Volatile
    private var lamportClock: Long = 0

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val gson = Gson()

    @Volatile
    var isRunning = false
        private set

    // ── Data Classes ──

    data class PeerInfo(
        val deviceId: String,
        val lastSeen: Long = System.currentTimeMillis()
    )

    data class GroupInfo(
        val groupUuid: String,
        val secretBase64: String,
        val saltBase64: String,
        val proxyUrl: String = DEFAULT_PROXY_URL
    ) {
        fun toQrString(): String = Gson().toJson(this)
        companion object {
            fun fromQrString(qr: String): GroupInfo? = try {
                Gson().fromJson(qr, GroupInfo::class.java)
            } catch (_: Exception) { null }
        }
    }

    // ── DHT Key Derivation (matches C++ Crypto::deriveDhtKey) ──

    /**
     * Derive a DHT InfoHash key the same way the Linux daemon does:
     * SHA-1( prefix + groupUuid + salt )
     * Returns the hex string of the hash (40 chars), which is the InfoHash.
     */
    private fun deriveDhtKey(prefix: String): String {
        val gId = groupUuid ?: return ""
        val s = salt ?: byteArrayOf()
        val input = prefix + gId + String(s, Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-1")
        val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    private fun groupDiscoveryKey(): String = deriveDhtKey("ulysses:group:")
    private fun triggerSyncKey(): String = deriveDhtKey("ulysses:triggers:")
    private fun fireEventKey(): String = deriveDhtKey("ulysses:fire:")

    // ── Group Management ──

    fun createGroup(): GroupInfo {
        val gId = UUID.randomUUID().toString()
        val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val s = ByteArray(16).also { SecureRandom().nextBytes(it) }
        deviceId = UUID.randomUUID().toString()

        groupUuid = gId
        groupSecret = key
        salt = s

        saveGroupToPrefs()

        val info = GroupInfo(
            groupUuid = gId,
            secretBase64 = Base64.encodeToString(key, Base64.NO_WRAP),
            saltBase64 = Base64.encodeToString(s, Base64.NO_WRAP),
            proxyUrl = proxyUrl
        )

        Log.d(TAG, "Created group: $gId, device: $deviceId")
        if (!isRunning) start()
        publishPresence()
        return info
    }

    fun joinGroup(qrData: String): Boolean {
        val info = GroupInfo.fromQrString(qrData) ?: return false
        groupUuid = info.groupUuid
        groupSecret = Base64.decode(info.secretBase64, Base64.NO_WRAP)
        salt = Base64.decode(info.saltBase64, Base64.NO_WRAP)
        proxyUrl = info.proxyUrl.ifEmpty { DEFAULT_PROXY_URL }
        deviceId = UUID.randomUUID().toString()

        saveGroupToPrefs()
        Log.d(TAG, "Joined group: ${info.groupUuid}")
        if (!isRunning) start()
        publishPresence()
        return true
    }

    fun getGroupInfo(): GroupInfo? {
        val gId = groupUuid ?: return null
        val key = groupSecret ?: return null
        val s = salt ?: return null
        return GroupInfo(gId, Base64.encodeToString(key, Base64.NO_WRAP), Base64.encodeToString(s, Base64.NO_WRAP), proxyUrl)
    }

    fun isInGroup(): Boolean = groupUuid != null && groupSecret != null
    fun getConnectedPeers(): List<PeerInfo> {
        val cutoff = System.currentTimeMillis() - 180_000
        return peers.values.filter { it.lastSeen > cutoff }
    }

    fun leaveGroup() {
        groupUuid = null; groupSecret = null; salt = null
        peers.clear(); seenNonces.clear()
        context.getSharedPreferences("ulysses_network", Context.MODE_PRIVATE).edit().clear().apply()
        stop()
    }

    // ── Network Lifecycle ──

    fun start() {
        if (isRunning) return
        loadGroupFromPrefs()
        if (groupUuid == null) return
        isRunning = true

        // Listen on all three DHT channels
        scope.launch { listenLoop(groupDiscoveryKey(), "group_discovery") }
        scope.launch { listenLoop(triggerSyncKey(), "trigger_sync") }
        scope.launch { listenLoop(fireEventKey(), "fire_events") }

        // Periodic presence + trigger sync + cleanup
        scope.launch { presenceLoop() }
        scope.launch { triggerSyncLoop() }
        scope.launch { peerScanLoop() }
        scope.launch { nonceCleanupLoop() }

        Log.d(TAG, "DHT network started for group $groupUuid via proxy $proxyUrl")
    }

    fun stop() {
        isRunning = false
        scope.coroutineContext.cancelChildren()
        Log.d(TAG, "DHT network stopped")
    }

    // ── DHT REST Operations ──

    /**
     * PUT a value to the DHT via proxy REST API.
     * Matches C++ m_dht.put(keyHash, Value(data))
     */
    private fun dhtPut(infohash: String, data: ByteArray) {
        try {
            val url = URL("$proxyUrl/key/$infohash")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 10000
            conn.readTimeout = 10000

            val b64 = Base64.encodeToString(data, Base64.NO_WRAP)
            val body = """{"data":"$b64"}"""

            val writer = OutputStreamWriter(conn.outputStream)
            writer.write(body)
            writer.flush()
            writer.close()

            val code = conn.responseCode
            if (code !in 200..299) {
                Log.w(TAG, "DHT PUT failed with code $code for hash $infohash")
            }
            conn.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "DHT PUT error", e)
        }
    }

    /**
     * LISTEN on a DHT key via long-poll REST API.
     * The proxy streams JSON values as they arrive (no timeout until closed).
     * Matches C++ m_dht.listen(keyHash, callback)
     */
    private suspend fun listenLoop(infohash: String, label: String) {
        while (isRunning) {
            try {
                val url = URL("$proxyUrl/key/$infohash/listen")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 15000
                conn.readTimeout = 0 // No read timeout for long-poll

                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                var line: String? = null

                while (isRunning && reader.readLine().also { line = it } != null) {
                    val json = line?.trim() ?: continue
                    if (json.isEmpty() || !json.startsWith("{")) continue

                    try {
                        // Parse the DHT value envelope: {"data":"base64","id":"...","type":...}
                        val envelope = gson.fromJson(json, DhtValueEnvelope::class.java)
                        val rawData = Base64.decode(envelope.data ?: envelope.cypher ?: continue, Base64.DEFAULT)
                        processRawMessage(rawData)
                    } catch (e: Exception) {
                        Log.w(TAG, "[$label] Failed to parse value: ${e.message}")
                    }
                }

                reader.close()
                conn.disconnect()
            } catch (e: Exception) {
                if (isRunning) {
                    Log.w(TAG, "[$label] Listen connection lost, reconnecting in 5s: ${e.message}")
                    delay(5000)
                }
            }
        }
    }

    // Envelope for DHT REST values
    private data class DhtValueEnvelope(
        val data: String? = null,
        val cypher: String? = null,
        val id: String? = null,
        val type: Int = 0
    )

    // ── Message Sending (matches C++ sendEncryptedMessage) ──

    private fun sendEncryptedMessage(dhtKey: String, message: Map<String, Any?>) {
        if (!isInGroup()) return

        val plaintext = gson.toJson(message).toByteArray(Charsets.UTF_8)
        val encrypted = encrypt(plaintext) ?: return

        // Build packet: [4-byte channel prefix][encrypted data]
        // (matches C++ packet format for compatibility)
        val channelPrefix = dhtKey.substring(0, 8).toByteArray(Charsets.UTF_8).copyOf(4)
        val packet = ByteArray(4 + encrypted.size)
        System.arraycopy(channelPrefix, 0, packet, 0, 4)
        System.arraycopy(encrypted, 0, packet, 4, encrypted.size)

        scope.launch { dhtPut(dhtKey, packet) }
    }

    // ── Message Processing (matches C++ processMessage) ──

    private suspend fun processRawMessage(data: ByteArray) {
        if (!isInGroup() || data.size < 5) return

        val encrypted = data.copyOfRange(4, data.size) // Skip 4-byte channel prefix
        val plaintext = decrypt(encrypted) ?: return    // Decryption failed = wrong group

        val msg = try {
            gson.fromJson(String(plaintext, Charsets.UTF_8), Map::class.java) as Map<String, Any?>
        } catch (_: Exception) { return }

        val msgType = msg["type"] as? String ?: return
        val senderId = msg["device_id"] as? String ?: return
        val nonce = msg["nonce"] as? String ?: return

        // Ignore own messages
        if (senderId == deviceId) return

        // Replay protection
        if (seenNonces.containsKey(nonce)) return
        seenNonces[nonce] = System.currentTimeMillis()

        when (msgType) {
            "presence" -> {
                val isNew = !peers.containsKey(senderId)
                peers[senderId] = PeerInfo(senderId)
                if (isNew) Log.d(TAG, "Peer discovered: ${senderId.take(16)}...")
            }
            "trigger_sync" -> {
                @Suppress("UNCHECKED_CAST")
                val triggerData = msg["trigger"] as? Map<String, Any?> ?: return
                handleTriggerSync(triggerData)
            }
            "fire" -> {
                val triggerUuid = msg["trigger_uuid"] as? String ?: return
                handleFireEvent(triggerUuid)
            }
            "disable" -> {
                val triggerUuid = msg["trigger_uuid"] as? String ?: return
                handleDisableEvent(triggerUuid)
            }
        }
    }

    // ── Trigger Sync (CRDT LWW, matches C++ publishTrigger) ──

    fun publishTrigger(trigger: TriggerEntity) {
        val triggerData = mapOf(
            "uuid" to trigger.id,
            "name" to trigger.name,
            "block_id" to trigger.blockId,
            "propagate" to trigger.propagateToNetwork,
            "lamport_ts" to trigger.lamportTimestamp,
            "deleted" to trigger.isDeleted
        )
        val msg = mapOf(
            "type" to "trigger_sync",
            "device_id" to deviceId,
            "nonce" to generateNonce(),
            "trigger" to triggerData,
            "timestamp" to System.currentTimeMillis() / 1000
        )
        sendEncryptedMessage(triggerSyncKey(), msg)
    }

    fun publishTombstone(triggerUuid: String, lamportTs: Long) {
        val triggerData = mapOf(
            "uuid" to triggerUuid,
            "deleted" to true,
            "lamport_ts" to lamportTs
        )
        val msg = mapOf(
            "type" to "trigger_sync",
            "device_id" to deviceId,
            "nonce" to generateNonce(),
            "trigger" to triggerData,
            "timestamp" to System.currentTimeMillis() / 1000
        )
        sendEncryptedMessage(triggerSyncKey(), msg)
    }

    private suspend fun handleTriggerSync(triggerData: Map<String, Any?>) {
        val uuid = triggerData["uuid"] as? String ?: return
        val remoteTs = (triggerData["lamport_ts"] as? Number)?.toLong() ?: 1
        val isDeleted = triggerData["deleted"] as? Boolean ?: false

        val db = UlyssesDatabase.getInstance(context)
        val local = db.triggerDao().getTriggerById(uuid)

        if (local == null && !isDeleted) {
            // New trigger from network
            val name = triggerData["name"] as? String ?: "Synced Trigger"
            val blockId = triggerData["block_id"] as? String ?: return
            val propagate = triggerData["propagate"] as? Boolean ?: false
            db.triggerDao().insertTrigger(TriggerEntity(
                id = uuid, name = name, blockId = blockId,
                propagateToNetwork = propagate, lamportTimestamp = remoteTs
            ))
            Log.d(TAG, "Added trigger from network: $name")
        } else if (local != null && remoteTs > local.lamportTimestamp) {
            // LWW: remote wins
            if (isDeleted) {
                db.triggerDao().softDeleteTrigger(uuid)
            } else {
                val name = triggerData["name"] as? String ?: local.name
                val blockId = triggerData["block_id"] as? String ?: local.blockId
                val propagate = triggerData["propagate"] as? Boolean ?: local.propagateToNetwork
                db.triggerDao().insertTrigger(local.copy(
                    name = name, blockId = blockId,
                    propagateToNetwork = propagate,
                    lamportTimestamp = remoteTs, isDeleted = isDeleted
                ))
            }
            Log.d(TAG, "Updated trigger from network (LWW): $uuid")
        } else if (local != null && remoteTs == local.lamportTimestamp && uuid > local.id) {
            // Tie-break on UUID (deterministic)
            if (isDeleted) db.triggerDao().softDeleteTrigger(uuid)
        }

        lamportClock = maxOf(lamportClock, remoteTs) + 1
    }

    // ── Fire Event (matches C++ publishFireEvent) ──

    suspend fun publishFireEvent(triggerUuid: String, lamportTs: Long) {
        val msg = mapOf(
            "type" to "fire",
            "device_id" to deviceId,
            "nonce" to generateNonce(),
            "trigger_uuid" to triggerUuid,
            "lamport_ts" to lamportTs,
            "action" to "fire"
        )
        sendEncryptedMessage(fireEventKey(), msg)
        Log.d(TAG, "Published fire event for trigger: $triggerUuid")
    }

    private suspend fun handleFireEvent(triggerUuid: String) {
        Log.d(TAG, "Received fire event for trigger: $triggerUuid")
        val db = UlyssesDatabase.getInstance(context)
        val trigger = db.triggerDao().getTriggerById(triggerUuid)

        if (trigger == null) {
            Log.w(TAG, "Trigger $triggerUuid not found locally")
            return
        }

        val block = db.blockDao().getBlockById(trigger.blockId) ?: return
        val repo = UlyssesRepository(db, context)
        repo.startSession(
            blockId = block.id,
            lockType = block.defaultLockType,
            lockValue = block.defaultLockValue,
            triggerId = trigger.id,
            propagate = false // Don't re-propagate
        )
        Log.d(TAG, "Started session from network fire: ${trigger.name}")
    }

    // ── Disable Event (matches C++ publishDisableEvent) ──

    suspend fun publishDisableEvent(triggerUuid: String, lamportTs: Long) {
        val msg = mapOf(
            "type" to "disable",
            "device_id" to deviceId,
            "nonce" to generateNonce(),
            "trigger_uuid" to triggerUuid,
            "lamport_ts" to lamportTs,
            "action" to "disable"
        )
        sendEncryptedMessage(fireEventKey(), msg)
        Log.d(TAG, "Published disable event for trigger: $triggerUuid")
    }

    private suspend fun handleDisableEvent(triggerUuid: String) {
        Log.d(TAG, "Received disable event for trigger: $triggerUuid")
        val db = UlyssesDatabase.getInstance(context)
        val sessions = db.sessionDao().getActiveSessionsSync()
        for (session in sessions) {
            if (session.triggerId == triggerUuid && session.isActive) {
                db.sessionDao().deactivateSession(session.id)
                Log.d(TAG, "Disabled session ${session.id} from network")
            }
        }
    }

    // ── Presence (matches C++ publishPresence) ──

    private fun publishPresence() {
        val msg = mapOf(
            "type" to "presence",
            "device_id" to deviceId,
            "nonce" to generateNonce(),
            "timestamp" to System.currentTimeMillis() / 1000
        )
        sendEncryptedMessage(groupDiscoveryKey(), msg)
    }

    private suspend fun presenceLoop() {
        while (isRunning) { publishPresence(); delay(60_000) }
    }

    private suspend fun triggerSyncLoop() {
        while (isRunning) {
            delay(30_000)
            try {
                val db = UlyssesDatabase.getInstance(context)
                // Collect current triggers and publish each
                val job = scope.launch {
                    db.triggerDao().getAllTriggers().collect { triggers ->
                        for (t in triggers) publishTrigger(t)
                        cancel()
                    }
                }
                job.join()
            } catch (e: Exception) {
                Log.e(TAG, "Trigger sync error", e)
            }
        }
    }

    private suspend fun peerScanLoop() {
        while (isRunning) {
            delay(30_000)
            val cutoff = System.currentTimeMillis() - 180_000
            val stale = peers.entries.filter { it.value.lastSeen < cutoff }
            for (entry in stale) {
                peers.remove(entry.key)
                Log.d(TAG, "Peer lost: ${entry.key.take(16)}...")
            }
        }
    }

    private suspend fun nonceCleanupLoop() {
        while (isRunning) {
            delay(120_000)
            if (seenNonces.size > 10000) seenNonces.clear()
        }
    }

    // ── AES-256-GCM Encryption (matches C++ Crypto::encrypt/decrypt) ──

    private fun encrypt(plaintext: ByteArray): ByteArray? {
        val key = groupSecret ?: return null
        return try {
            val nonce = ByteArray(GCM_NONCE_LENGTH).also { SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))
            val ciphertext = cipher.doFinal(plaintext)
            // Prepend nonce: [12-byte nonce][ciphertext+tag]
            nonce + ciphertext
        } catch (e: Exception) {
            Log.e(TAG, "Encrypt error", e); null
        }
    }

    private fun decrypt(data: ByteArray): ByteArray? {
        val key = groupSecret ?: return null
        if (data.size < GCM_NONCE_LENGTH + 1) return null
        return try {
            val nonce = data.copyOfRange(0, GCM_NONCE_LENGTH)
            val ciphertext = data.copyOfRange(GCM_NONCE_LENGTH, data.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))
            cipher.doFinal(ciphertext)
        } catch (_: Exception) { null } // Expected for messages from other groups
    }

    // ── Utilities ──

    private fun generateNonce(): String = ByteArray(16).also { SecureRandom().nextBytes(it) }
        .joinToString("") { "%02x".format(it) }

    private fun saveGroupToPrefs() {
        context.getSharedPreferences("ulysses_network", Context.MODE_PRIVATE).edit()
            .putString("group_uuid", groupUuid)
            .putString("group_secret", groupSecret?.let { Base64.encodeToString(it, Base64.NO_WRAP) })
            .putString("salt", salt?.let { Base64.encodeToString(it, Base64.NO_WRAP) })
            .putString("device_id", deviceId)
            .putString("proxy_url", proxyUrl)
            .apply()
    }

    private fun loadGroupFromPrefs() {
        val prefs = context.getSharedPreferences("ulysses_network", Context.MODE_PRIVATE)
        groupUuid = prefs.getString("group_uuid", null)
        groupSecret = prefs.getString("group_secret", null)?.let { Base64.decode(it, Base64.NO_WRAP) }
        salt = prefs.getString("salt", null)?.let { Base64.decode(it, Base64.NO_WRAP) }
        deviceId = prefs.getString("device_id", null) ?: UUID.randomUUID().toString()
        proxyUrl = prefs.getString("proxy_url", DEFAULT_PROXY_URL) ?: DEFAULT_PROXY_URL
    }
}
