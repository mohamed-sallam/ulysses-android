package com.ulysses.app.blocker

import android.util.Log
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer

/**
 * DNS proxy that intercepts DNS queries and blocks domains.
 *
 * For blocked domains: returns NXDOMAIN (rcode 3)
 * For allowed domains: forwards to upstream DNS (8.8.8.8) and returns response
 *
 * Supports wildcard blocking: *.instagram.com blocks all subdomains.
 */
class DnsProxy(private val vpnService: android.net.VpnService) {

    companion object {
        private const val TAG = "DnsProxy"
        private const val UPSTREAM_DNS = "8.8.8.8"
        private const val UPSTREAM_DNS_PORT = 53
        private const val DNS_HEADER_SIZE = 12
    }

    @Volatile
    private var blockedDomains = setOf<String>()

    fun updateBlockedDomains(domains: Set<String>) {
        blockedDomains = domains
        Log.d(TAG, "Updated blocked domains: ${domains.size} entries")
    }

    /**
     * Process a raw IP packet containing a DNS query.
     * Returns a response packet or null if not a DNS query.
     */
    fun processPacket(packet: ByteArray, length: Int): ByteArray? {
        if (length < 28) return null // Too short for IP + UDP + DNS

        val buffer = ByteBuffer.wrap(packet, 0, length)

        // Parse IP header
        val versionAndIhl = buffer.get().toInt() and 0xFF
        val version = versionAndIhl shr 4
        if (version != 4) return null // Only handle IPv4

        val ihl = (versionAndIhl and 0x0F) * 4
        if (ihl < 20) return null

        buffer.position(9) // Protocol field
        val protocol = buffer.get().toInt() and 0xFF
        if (protocol != 17) return null // Only handle UDP

        buffer.position(12) // Source IP
        val srcIp = ByteArray(4)
        buffer.get(srcIp)

        val dstIp = ByteArray(4)
        buffer.get(dstIp)

        // Parse UDP header (starts at IHL offset)
        buffer.position(ihl)
        val srcPort = buffer.short.toInt() and 0xFFFF
        val dstPort = buffer.short.toInt() and 0xFFFF
        val udpLength = buffer.short.toInt() and 0xFFFF
        val udpChecksum = buffer.short.toInt() and 0xFFFF

        if (dstPort != 53) return null // Only handle DNS

        // Extract DNS payload
        val dnsOffset = ihl + 8
        val dnsLength = length - dnsOffset
        if (dnsLength < DNS_HEADER_SIZE) return null

        val dnsPayload = packet.copyOfRange(dnsOffset, length)

        // Parse DNS query
        val domain = parseDnsQueryDomain(dnsPayload) ?: return null

        Log.d(TAG, "DNS query for: $domain")

        val dnsResponse: ByteArray

        if (isDomainBlocked(domain)) {
            Log.d(TAG, "BLOCKED: $domain")
            dnsResponse = createNxDomainResponse(dnsPayload)
        } else {
            // Forward to upstream DNS
            val upstream = forwardToUpstream(dnsPayload) ?: return null
            dnsResponse = upstream
        }

        // Build response IP packet
        return buildResponsePacket(
            srcIp = dstIp,
            dstIp = srcIp,
            srcPort = dstPort,
            dstPort = srcPort,
            dnsPayload = dnsResponse
        )
    }

    /**
     * Parse domain name from DNS query payload.
     */
    private fun parseDnsQueryDomain(dns: ByteArray): String? {
        if (dns.size < DNS_HEADER_SIZE + 1) return null

        val buffer = ByteBuffer.wrap(dns)
        buffer.position(DNS_HEADER_SIZE) // Skip header

        val parts = mutableListOf<String>()
        try {
            while (buffer.hasRemaining()) {
                val labelLength = buffer.get().toInt() and 0xFF
                if (labelLength == 0) break
                if (labelLength > 63 || buffer.remaining() < labelLength) return null

                val label = ByteArray(labelLength)
                buffer.get(label)
                parts.add(String(label))
            }
        } catch (e: Exception) {
            return null
        }

        return if (parts.isNotEmpty()) parts.joinToString(".") else null
    }

    /**
     * Check if a domain is blocked, including wildcard/subdomain matching.
     */
    fun isDomainBlocked(domain: String): Boolean {
        val lower = domain.lowercase()

        for (blocked in blockedDomains) {
            if (blocked.isBlank()) continue
            if (blocked == ".") return true // wildcard everything

            // Exact match or subdomain match
            if (lower == blocked || lower.endsWith(".$blocked")) {
                return true
            }
        }

        return false
    }

    /**
     * Create an NXDOMAIN response for a blocked domain.
     */
    private fun createNxDomainResponse(query: ByteArray): ByteArray {
        val response = query.copyOf()
        val buffer = ByteBuffer.wrap(response)

        // Set QR bit (response), keep query ID
        val flags = buffer.getShort(2).toInt()
        val newFlags = (flags or 0x8000) or 0x0003 // QR=1, RCODE=3 (NXDOMAIN)
        buffer.putShort(2, newFlags.toShort())

        // Set answer count to 0
        buffer.putShort(6, 0)

        return response
    }

    /**
     * Forward DNS query to upstream DNS server and return response.
     */
    private fun forwardToUpstream(query: ByteArray): ByteArray? {
        return try {
            val socket = DatagramSocket()
            vpnService.protect(socket) // Bypasses the VPN for this socket to prevent routing loops!
            socket.soTimeout = 5000

            val address = InetAddress.getByName(UPSTREAM_DNS)
            val requestPacket = DatagramPacket(query, query.size, address, UPSTREAM_DNS_PORT)
            socket.send(requestPacket)

            val responseBuffer = ByteArray(4096)
            val responsePacket = DatagramPacket(responseBuffer, responseBuffer.size)
            socket.receive(responsePacket)

            socket.close()
            responseBuffer.copyOf(responsePacket.length)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to forward DNS query", e)
            null
        }
    }

    /**
     * Build a complete IPv4/UDP response packet wrapping a DNS payload.
     */
    private fun buildResponsePacket(
        srcIp: ByteArray,
        dstIp: ByteArray,
        srcPort: Int,
        dstPort: Int,
        dnsPayload: ByteArray
    ): ByteArray {
        val udpLength = 8 + dnsPayload.size
        val totalLength = 20 + udpLength

        val packet = ByteArray(totalLength)
        val buffer = ByteBuffer.wrap(packet)

        // IP Header
        buffer.put(0x45.toByte()) // Version 4, IHL 5
        buffer.put(0x00.toByte()) // DSCP/ECN
        buffer.putShort(totalLength.toShort()) // Total length
        buffer.putShort(0) // Identification
        buffer.putShort(0x4000.toShort()) // Flags: Don't Fragment
        buffer.put(64) // TTL
        buffer.put(17) // Protocol: UDP
        buffer.putShort(0) // Header checksum (will calculate)
        buffer.put(srcIp)
        buffer.put(dstIp)

        // Calculate IP header checksum
        var sum = 0L
        for (i in 0 until 20 step 2) {
            sum += ((packet[i].toInt() and 0xFF) shl 8) or (packet[i + 1].toInt() and 0xFF)
        }
        while (sum shr 16 > 0) sum = (sum and 0xFFFF) + (sum shr 16)
        val checksum = sum.inv().toShort()
        buffer.putShort(10, checksum)

        // UDP Header
        buffer.position(20)
        buffer.putShort(srcPort.toShort())
        buffer.putShort(dstPort.toShort())
        buffer.putShort(udpLength.toShort())
        buffer.putShort(0) // UDP checksum (optional for IPv4)

        // DNS Payload
        buffer.put(dnsPayload)

        return packet
    }
}
