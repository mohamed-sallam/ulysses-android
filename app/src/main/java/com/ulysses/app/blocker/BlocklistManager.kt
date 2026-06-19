package com.ulysses.app.blocker

import android.content.Context
import android.util.Log
import com.ulysses.app.data.db.UlyssesDatabase
import com.ulysses.app.data.db.entities.DnsBlockedDomainEntity
import com.ulysses.app.data.db.entities.DnsCategoryEntity
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Manages curated DNS blocklists for category-based blocking.
 *
 * Sources for domain lists (public, well-maintained hostlists):
 * - StevenBlack/hosts for adult + gambling
 * - Social media domains from curated community lists
 * - Streaming/gaming from community lists
 *
 * Downloads and parses hosts-format files into the Room database.
 */
object BlocklistManager {

    private const val TAG = "BlocklistMgr"

    // Curated blocklist sources
    private val CATEGORY_SOURCES = mapOf(
        "Social Media" to listOf(
            "https://raw.githubusercontent.com/nickspaargaren/pihole-google/master/categories/socialmedia.txt"
        ),
        "Adult Content" to listOf(
            "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/porn-only/hosts"
        ),
        "Gaming" to listOf(
            "https://raw.githubusercontent.com/nickspaargaren/pihole-google/master/categories/games.txt"
        ),
        "Streaming" to listOf(
            "https://raw.githubusercontent.com/nickspaargaren/pihole-google/master/categories/streaming.txt"
        ),
        "News" to listOf(
            "https://raw.githubusercontent.com/nickspaargaren/pihole-google/master/categories/news.txt"
        )
    )

    // Well-known social media domains (fallback if download fails)
    private val SOCIAL_MEDIA_FALLBACK = setOf(
        "facebook.com", "www.facebook.com", "m.facebook.com", "web.facebook.com",
        "instagram.com", "www.instagram.com",
        "twitter.com", "www.twitter.com", "x.com", "www.x.com",
        "tiktok.com", "www.tiktok.com", "vm.tiktok.com",
        "reddit.com", "www.reddit.com", "old.reddit.com",
        "snapchat.com", "www.snapchat.com",
        "pinterest.com", "www.pinterest.com",
        "linkedin.com", "www.linkedin.com",
        "tumblr.com", "www.tumblr.com",
        "discord.com", "www.discord.com",
        "threads.net", "www.threads.net",
        "mastodon.social"
    )

    private val STREAMING_FALLBACK = setOf(
        "youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be",
        "netflix.com", "www.netflix.com",
        "twitch.tv", "www.twitch.tv",
        "hulu.com", "www.hulu.com",
        "disneyplus.com", "www.disneyplus.com",
        "primevideo.com", "www.primevideo.com",
        "crunchyroll.com", "www.crunchyroll.com"
    )

    private val GAMING_FALLBACK = setOf(
        "store.steampowered.com", "steamcommunity.com",
        "epicgames.com", "www.epicgames.com",
        "roblox.com", "www.roblox.com",
        "minecraft.net",
        "ea.com", "www.ea.com",
        "twitch.tv", "www.twitch.tv"
    )

    /**
     * Download and update domain lists for all active categories.
     * Call periodically (e.g., weekly) or on first setup.
     */
    suspend fun refreshAllCategories(context: Context) {
        val db = UlyssesDatabase.getInstance(context)
        val categories = db.dnsCategoryDao().getActiveCategoriesSync()

        for (category in categories) {
            refreshCategory(context, category)
        }
    }

    /**
     * Download and parse a blocklist for a specific category.
     */
    suspend fun refreshCategory(context: Context, category: DnsCategoryEntity) {
        val db = UlyssesDatabase.getInstance(context)
        val sources = CATEGORY_SOURCES[category.name] ?: return

        val domains = mutableSetOf<String>()

        for (sourceUrl in sources) {
            try {
                val downloaded = downloadHostsList(sourceUrl)
                domains.addAll(downloaded)
                Log.d(TAG, "Downloaded ${downloaded.size} domains from $sourceUrl for ${category.name}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to download $sourceUrl", e)
            }
        }

        // Use fallback if download failed
        if (domains.isEmpty()) {
            domains.addAll(getFallbackDomains(category.name))
            Log.d(TAG, "Using ${domains.size} fallback domains for ${category.name}")
        }

        // Update database
        db.dnsCategoryDao().deleteDomainsByCategory(category.id)
        val entities = domains.map { domain ->
            DnsBlockedDomainEntity(categoryId = category.id, domain = domain)
        }
        // Insert in chunks to avoid SQLite limits
        entities.chunked(500).forEach { chunk ->
            db.dnsCategoryDao().insertDomains(chunk)
        }

        // Update domain count
        db.dnsCategoryDao().updateCategory(
            category.copy(domainCount = domains.size, lastUpdated = System.currentTimeMillis(), sourceUrl = sources.first())
        )

        Log.d(TAG, "Updated ${category.name}: ${domains.size} domains")
    }

    /**
     * Parse a hosts-format file (0.0.0.0 domain.com or 127.0.0.1 domain.com format).
     */
    private suspend fun downloadHostsList(urlString: String): Set<String> = withContext(Dispatchers.IO) {
        val domains = mutableSetOf<String>()
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 30000

        try {
            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            reader.forEachLine { line ->
                val trimmed = line.trim()
                if (trimmed.isBlank() || trimmed.startsWith("#")) return@forEachLine

                // Parse hosts format: "0.0.0.0 domain.com" or "127.0.0.1 domain.com"
                val parts = trimmed.split(Regex("\\s+"))
                if (parts.size >= 2 && (parts[0] == "0.0.0.0" || parts[0] == "127.0.0.1")) {
                    val domain = parts[1].lowercase().trim()
                    if (domain.isNotEmpty() && domain != "localhost" && domain.contains(".")) {
                        domains.add(domain)
                    }
                } else if (parts.size == 1 && parts[0].contains(".") && !parts[0].startsWith("#")) {
                    // Plain domain list format (one domain per line)
                    domains.add(parts[0].lowercase().trim())
                }
            }
            reader.close()
        } finally {
            connection.disconnect()
        }

        domains
    }

    private fun getFallbackDomains(categoryName: String): Set<String> {
        return when {
            categoryName.contains("Social", true) -> SOCIAL_MEDIA_FALLBACK
            categoryName.contains("Stream", true) -> STREAMING_FALLBACK
            categoryName.contains("Gaming", true) -> GAMING_FALLBACK
            else -> emptySet()
        }
    }
}
