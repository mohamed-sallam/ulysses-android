package com.ulysses.app.blocker

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.LruCache
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.ulysses.app.data.db.UlyssesDatabase
import com.ulysses.app.data.db.entities.BlockListEntryEntity
import com.ulysses.app.service.UlyssesAccessibilityService
import com.ulysses.app.ui.BlockedActivity
import kotlinx.coroutines.*
import java.util.Locale

/**
 * Website and keyword blocker that works through the accessibility service.
 *
 * Strategies (from curbox KeywordBlocker):
 * 1. Detect browser URL bars via known resource IDs
 * 2. Extract current URL text
 * 3. Match against blocked domains, URLs, and keywords
 * 4. Press BACK then HOME to block (curbox approach)
 * 5. Show warning overlay (awb approach)
 *
 * This handles blocking at the accessibility layer (URL bar interception).
 * DNS-level blocking is handled separately by UlyssesVpnService.
 */
class WebsiteBlocker {

    companion object {
        private const val TAG = "WebsiteBlocker"
        private const val EVENT_MASK =
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED

        /**
         * Known browser URL bar resource IDs for popular browsers.
         */
        val BROWSER_URL_BARS = mapOf(
            "com.android.chrome" to BrowserInfo("com.android.chrome:id/url_bar", "com.android.chrome:id/url_bar"),
            "org.chromium.chrome" to BrowserInfo("org.chromium.chrome:id/url_bar", "org.chromium.chrome:id/url_bar"),
            "org.cromite.cromite" to BrowserInfo("org.cromite.cromite:id/url_bar", "org.cromite.cromite:id/url_bar"),
            "app.vanadium.browser" to BrowserInfo("app.vanadium.browser:id/url_bar", "app.vanadium.browser:id/url_bar"),
            "com.brave.browser" to BrowserInfo("com.brave.browser:id/url_bar", "com.brave.browser:id/url_bar"),
            "org.mozilla.firefox" to BrowserInfo("org.mozilla.firefox:id/mozac_browser_toolbar_url_view", "org.mozilla.firefox:id/mozac_browser_toolbar_edit_url_view"),
            "org.mozilla.firefox_beta" to BrowserInfo("org.mozilla.firefox_beta:id/mozac_browser_toolbar_url_view", "org.mozilla.firefox_beta:id/mozac_browser_toolbar_edit_url_view"),
            "org.mozilla.fennec_fdroid" to BrowserInfo("org.mozilla.fennec_fdroid:id/mozac_browser_toolbar_url_view", "org.mozilla.fennec_fdroid:id/mozac_browser_toolbar_edit_url_view"),
            "com.opera.browser" to BrowserInfo("com.opera.browser:id/url_field", "com.opera.browser:id/url_field"),
            "com.opera.mini.native" to BrowserInfo("com.opera.mini.native:id/url_field", "com.opera.mini.native:id/url_field"),
            "com.microsoft.emmx" to BrowserInfo("com.microsoft.emmx:id/url_bar", "com.microsoft.emmx:id/url_bar"),
            "com.duckduckgo.mobile.android" to BrowserInfo("com.duckduckgo.mobile.android:id/omnibarTextInput", "com.duckduckgo.mobile.android:id/omnibarTextInput"),
            "com.vivaldi.browser" to BrowserInfo("com.vivaldi.browser:id/url_bar", "com.vivaldi.browser:id/url_bar"),
            "com.samsung.android.app.sbrowser" to BrowserInfo("com.samsung.android.app.sbrowser:id/location_bar_edit_text", "com.samsung.android.app.sbrowser:id/location_bar_edit_text"),
        )
    }

    data class BrowserInfo(
        val displayUrlBarId: String,
        val editUrlBarId: String
    )

    private lateinit var service: UlyssesAccessibilityService
    private val handler = Handler(Looper.getMainLooper())

    // Cache: URL text → whether it's blocked
    private val detectionCache = LruCache<String, Boolean>(200)

    @Volatile
    private var blockedWebEntries = listOf<BlockListEntryEntity>()

    @Volatile
    private var allowedWebEntries = listOf<BlockListEntryEntity>()

    @Volatile
    private var compiledDenyRegex = listOf<Regex>()

    @Volatile
    private var compiledAllowRegex = listOf<Regex>()

    private var lastPackage = ""
    private var lastUrl = ""
    private var lastBlockTime = 0L
    private var refreshJob: Job? = null

    private val wordSplitRegex = Regex("[^a-zA-Z0-9]+")

    fun setup(service: UlyssesAccessibilityService) {
        this.service = service
        startRefreshLoop()
    }

    private fun startRefreshLoop() {
        refreshJob?.cancel()
        refreshJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                refreshBlockedWebsites()
                delay(3000)
            }
        }
    }

    private suspend fun refreshBlockedWebsites() {
        try {
            val db = UlyssesDatabase.getInstance(service)
            val activeSessions = db.sessionDao().getActiveSessionsSync()
            val now = System.currentTimeMillis()

            val validSessions = activeSessions.filter { session ->
                if (session.lockType == "timer" && session.endsAt != null) now < session.endsAt
                else true
            }

            val denied = mutableListOf<BlockListEntryEntity>()
            val allowed = mutableListOf<BlockListEntryEntity>()

            for (session in validSessions) {
                val entries = db.blockDao().getAllEntriesForBlock(session.blockId)

                for (entry in entries) {
                    if (entry.ruleType == "domain" || entry.ruleType == "url" || entry.ruleType == "wildcard" || entry.ruleType == "keyword") {
                        if (entry.type == "allow") {
                            allowed.add(entry)
                        } else {
                            denied.add(entry)
                        }
                    }
                }
            }

            blockedWebEntries = denied
            allowedWebEntries = allowed
            compiledDenyRegex = compileRegexList(denied)
            compiledAllowRegex = compileRegexList(allowed)
            detectionCache.evictAll()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh web entries", e)
        }
    }

    private val browserPackagesCache = mutableMapOf<String, Boolean>()

    private fun isAppBrowser(packageName: String): Boolean {
        if (browserPackagesCache.containsKey(packageName)) {
            return browserPackagesCache[packageName]!!
        }
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("http://www.curbox.life"))
        intent.setPackage(packageName)
        val pm = service.packageManager
        val activities = pm.queryIntentActivities(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
        val isBrowser = activities.isNotEmpty()
        browserPackagesCache[packageName] = isBrowser
        return isBrowser
    }

    fun filterOutUrlFromPlainText(inputText: String?): String? {
        if (inputText.isNullOrBlank()) return null

        val urlRegex = """(?:https?://|www\.)?[a-zA-Z0-9][a-zA-Z0-9\-]{1,61}[a-zA-Z0-9]\.[a-zA-Z]{2,}(?:[/\?#][a-zA-Z0-9\-._~:/?#\[\]@!$&'()*+,;=%]*)?"""
        val pattern = java.util.regex.Pattern.compile(urlRegex, java.util.regex.Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(inputText)

        if (matcher.find()) {
            var cleanUrl = matcher.group(0) ?: return null
            cleanUrl = cleanUrl.trimEnd('.', ',', ')', ']', '\'', '"', '>')
            if (cleanUrl.contains(".") && cleanUrl.length > 4) {
                return cleanUrl
            }
        }
        return null
    }



    private fun extractUrlFromBrowser(root: AccessibilityNodeInfo, browserInfo: BrowserInfo): String? {
        val urlNode = findNodeById(root, browserInfo.displayUrlBarId)
            ?: findNodeById(root, browserInfo.editUrlBarId)
            ?: return null

        val text = urlNode.text?.toString() ?: urlNode.contentDescription?.toString()
        urlNode.recycle()
        return text
    }

    private fun findNodeById(root: AccessibilityNodeInfo, id: String): AccessibilityNodeInfo? {
        val nodes = root.findAccessibilityNodeInfosByViewId(id)
        return if (nodes.isNullOrEmpty()) null else nodes[0]
    }

    fun checkEvent(event: AccessibilityEvent?) {
        if (event == null || (event.eventType and EVENT_MASK) == 0) return
        if (blockedWebEntries.isEmpty()) return

        val packageName = event.packageName?.toString() ?: return

        // Check if it's a browser package
        if (!isAppBrowser(packageName)) return

        // If it's a browser but not supported, block it completely to prevent bypass
        val browserInfo = BROWSER_URL_BARS[packageName]
        if (browserInfo == null) {
            blockWebsite(packageName, "unsupported_browser")
            return
        }

        val rootNode = service.rootInActiveWindow ?: return

        try {
            // Get exact text from URL bar (URL or search keywords)
            val urlText = extractUrlFromBrowser(rootNode, browserInfo)
            if (urlText.isNullOrBlank() || urlText == lastUrl) return

            lastUrl = urlText

            // Check cache first
            val cached = detectionCache.get(urlText)
            if (cached != null) {
                if (cached) blockWebsite(packageName, urlText)
                return
            }

            val isBlocked = isUrlBlocked(urlText)
            detectionCache.put(urlText, isBlocked)

            if (isBlocked) {
                blockWebsite(packageName, urlText)
            }
        } finally {
            rootNode.recycle()
        }
    }

    /**
     * Check if a URL matches any blocked entry.
     * Supports: domain, URL, keyword/wildcard, and "block all internet" (.).
     *
     * Adapted from curbox's keyword matching logic.
     */
    fun isUrlBlocked(url: String): Boolean {
        val lowerUrl = url.lowercase(Locale.ROOT)

        // First check allowlist - allowed entries override denials
        for (entry in allowedWebEntries) {
            if (matchesEntry(lowerUrl, entry)) return false
        }

        // Then check denylist
        for (entry in blockedWebEntries) {
            if (matchesEntry(lowerUrl, entry)) return true
        }

        // Check compiled regex patterns
        if (compiledDenyRegex.any { it.containsMatchIn(lowerUrl) }) {
            if (compiledAllowRegex.any { it.containsMatchIn(lowerUrl) }) return false
            return true
        }

        return false
    }

    private fun matchesEntry(lowerUrl: String, entry: BlockListEntryEntity): Boolean {
        val lowerValue = entry.value.lowercase(Locale.ROOT)

        return when (entry.ruleType) {
            "domain" -> {
                // Match domain and all subdomains
                val cleanUrl = lowerUrl.removePrefix("https://").removePrefix("http://").removePrefix("www.")
                cleanUrl.startsWith(lowerValue) || cleanUrl.contains(".$lowerValue")
            }
            "url" -> {
                // Match specific URL path
                lowerUrl.contains(lowerValue)
            }
            "keyword", "wildcard" -> {
                if (lowerValue == ".") return true // Block all internet special case

                // Keyword matching
                if (lowerValue.contains("*")) {
                    // Wildcard pattern
                    val regex = wildcardToRegex(lowerValue)
                    regex.containsMatchIn(lowerUrl)
                } else {
                    // Literal keyword
                    val keywords = parseTextForKeywords(lowerUrl)
                    keywords.contains(lowerValue) || lowerUrl.contains(lowerValue)
                }
            }
            else -> false
        }
    }

    /**
     * Parse URL into keywords for matching (from curbox's KeywordBlocker).
     */
    private fun parseTextForKeywords(input: String): Set<String> {
        val words = mutableSetOf<String>()
        try {
            val uri = java.net.URI(input)
            if (uri.host != null) {
                val host = uri.host.lowercase(Locale.ROOT)
                words.add(host)
                val parts = host.split(".")
                if (parts.size >= 2) words.add(parts[parts.size - 2])
                uri.path?.let { path ->
                    words.addAll(path.split(wordSplitRegex).filter { it.isNotEmpty() }.map { it.lowercase(Locale.ROOT) })
                }
                uri.query?.split("&")?.forEach { param ->
                    param.split("=", limit = 2).forEach { part ->
                        words.addAll(part.split(wordSplitRegex).filter { it.isNotEmpty() }.map { it.lowercase(Locale.ROOT) })
                    }
                }
                return words
            }
        } catch (_: Exception) {}

        words.add(input.lowercase(Locale.ROOT))
        words.addAll(input.split(wordSplitRegex).filter { it.isNotEmpty() }.map { it.lowercase(Locale.ROOT) })
        return words
    }

    /**
     * Convert wildcard pattern (* = anything) to regex.
     */
    private fun wildcardToRegex(pattern: String): Regex {
        val lowerPattern = pattern.lowercase(Locale.ROOT)
        val looksLikeDomain = !lowerPattern.startsWith("http") &&
            !lowerPattern.startsWith("*") &&
            !lowerPattern.startsWith("/")
        val prefix = if (looksLikeDomain) "(?:https?://)?(?:www\\.)?" else ""
        val escaped = lowerPattern
            .replace(Regex("[.\\+^$()|\\[\\]\\\\{}]"), "\\\\$0")
            .replace("*", ".*")
        return Regex(prefix + escaped)
    }

    private fun compileRegexList(entries: List<BlockListEntryEntity>): List<Regex> {
        return entries
            .filter { it.type == "web_keyword" && it.value.contains("*") }
            .map { wildcardToRegex(it.value.lowercase(Locale.ROOT)) }
    }

    /**
     * Block website: press BACK + HOME (curbox approach), then show warning overlay.
     */
    private fun blockWebsite(packageName: String, url: String) {
        val now = System.currentTimeMillis()
        if (now - lastBlockTime < 1500) return
        lastBlockTime = now

        Log.d(TAG, "Blocking website: $url in $packageName")

        CoroutineScope(Dispatchers.IO).launch {
            // Press BACK (curbox approach)
            service.pressBack()
            delay(1000)
            service.pressHome()

            withContext(Dispatchers.Main) {
                handler.postDelayed({
                    try {
                        val intent = Intent(service, BlockedActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            putExtra("blocked_url", url)
                            putExtra("blocked_package", packageName)
                        }
                        service.startActivity(intent)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to launch BlockedActivity", e)
                    }
                }, 300)
            }
        }
    }

    fun destroy() {
        refreshJob?.cancel()
        handler.removeCallbacksAndMessages(null)
    }
}
