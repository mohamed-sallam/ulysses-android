package com.ulysses.app.service

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import com.ulysses.app.blocker.DnsProxy
import com.ulysses.app.data.db.UlyssesDatabase
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Local VPN service that intercepts DNS queries and blocks domains.
 *
 * Strategies:
 * 1. DNS Category blocking - curated blocklists (Social Media, Adult, etc.)
 * 2. User-entered specific websites - from block session entries
 * 3. Wildcard support (*.instagram.com covers subdomains)
 *
 * The VPN only intercepts DNS (port 53) and returns NXDOMAIN for blocked domains.
 * All other traffic passes through normally.
 */
class UlyssesVpnService : VpnService() {

    companion object {
        private const val TAG = "UlyssesVPN"
        private const val VPN_ADDRESS = "10.0.0.2"
        private const val VPN_DNS = "10.0.0.1"
        const val ACTION_START = "com.ulysses.app.vpn.START"
        const val ACTION_STOP = "com.ulysses.app.vpn.STOP"

        @Volatile
        var isRunning = false
            private set
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var dnsProxy: DnsProxy? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopVpn()
                return START_NOT_STICKY
            }
            else -> {
                startVpn()
                return START_STICKY
            }
        }
    }

    private fun startVpn() {
        if (isRunning) return

        try {
            val builder = Builder()
            builder.setSession("Ulysses DNS Filter")
            builder.addAddress(VPN_ADDRESS, 32)
            builder.addDnsServer(VPN_DNS)
            builder.addRoute("10.0.0.0", 24) // Only route our VPN subnet
            // We specifically do NOT route all traffic - only DNS
            builder.setBlocking(true)
            builder.setMtu(1500)

            // Exclude self from VPN to avoid loops
            builder.addDisallowedApplication(packageName)

            vpnInterface = builder.establish()

            if (vpnInterface == null) {
                Log.e(TAG, "Failed to establish VPN interface")
                return
            }

            isRunning = true
            Log.d(TAG, "VPN interface established")

            // Start the DNS proxy thread
            serviceScope.launch {
                runDnsProxy()
            }

            // Periodically refresh blocked domains
            serviceScope.launch {
                while (isActive) {
                    refreshBlockedDomains()
                    delay(5000)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start VPN", e)
        }
    }

    private fun stopVpn() {
        isRunning = false
        try {
            vpnInterface?.close()
            vpnInterface = null
            dnsProxy = null
            serviceScope.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping VPN", e)
        }
        stopSelf()
    }

    private suspend fun refreshBlockedDomains() {
        try {
            val db = UlyssesDatabase.getInstance(this)

            // Get user-entered website blocks from active sessions
            val activeSessions = db.sessionDao().getActiveSessionsSync()
            val now = System.currentTimeMillis()
            val validSessions = activeSessions.filter { s ->
                if (s.lockType == "timer" && s.endsAt != null) now < s.endsAt else true
            }

            val deniedDomains = mutableSetOf<String>()
            val allowedDomains = mutableSetOf<String>()
            
            for (session in validSessions) {
                val entries = db.blockDao().getAllEntriesForBlock(session.blockId)
                for (entry in entries) {
                    if (entry.ruleType == "domain" || entry.ruleType == "url" || entry.ruleType == "keyword") {
                        val host = try {
                            val uriStr = if (entry.value.startsWith("http")) entry.value else "https://${entry.value}"
                            java.net.URI(uriStr).host?.lowercase() ?: entry.value.lowercase()
                        } catch (_: Exception) {
                            entry.value.lowercase()
                        }
                        
                        if (entry.type == "allow") {
                            allowedDomains.add(host)
                        } else {
                            deniedDomains.add(host)
                        }
                    } else if (entry.ruleType == "wildcard" && entry.value == ".") {
                        deniedDomains.add(".") // Special case for all domains
                    } else if (entry.ruleType == "wildcard") {
                        deniedDomains.add(entry.value.lowercase().removePrefix("*."))
                    }
                }
            }

            val finalBlocked = deniedDomains - allowedDomains
            dnsProxy?.updateBlockedDomains(finalBlocked)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh blocked domains", e)
        }
    }

    private suspend fun runDnsProxy() {
        val fd = vpnInterface ?: return
        val proxy = DnsProxy(this)
        dnsProxy = proxy

        val inputStream = FileInputStream(fd.fileDescriptor)
        val outputStream = FileOutputStream(fd.fileDescriptor)

        val buffer = ByteArray(32767)

        try {
            while (isRunning) {
                val length = inputStream.read(buffer)
                if (length <= 0) {
                    delay(10)
                    continue
                }

                val packet = buffer.copyOf(length)
                
                // Launch in a coroutine to not block the VPN read thread while waiting for upstream DNS
                serviceScope.launch {
                    val response = proxy.processPacket(packet, length)

                    if (response != null) {
                        synchronized(outputStream) {
                            try {
                                outputStream.write(response)
                                outputStream.flush()
                            } catch (e: Exception) {
                                // ignore
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            if (isRunning) {
                Log.e(TAG, "DNS proxy error", e)
            }
        }
    }

    override fun onRevoke() {
        stopVpn()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }
}
