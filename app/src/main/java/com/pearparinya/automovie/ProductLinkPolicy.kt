package com.pearparinya.automovie

import java.net.URI

/** Only public HTTPS storefront pages; redirect destinations must pass this same check. */
internal object ProductLinkPolicy {
    private val domains = listOf("tiktok.com", "tiktokshop.com", "shopee.co.th", "shope.ee", "s.shopee.co.th", "lazada.co.th", "lazada.com", "s.lazada.co.th", "s.lazada.com")
    fun allowed(raw: String): Boolean = runCatching {
        val uri = URI(raw.trim())
        val host = uri.host?.lowercase().orEmpty()
        uri.scheme.equals("https", true) && uri.userInfo == null && (uri.port == -1 || uri.port == 443) &&
            domains.any { host == it || host.endsWith(".$it") }
    }.getOrDefault(false)
    fun firstUrl(text: String): String = Regex("https://[^\\s<>]+", RegexOption.IGNORE_CASE).find(text)?.value?.trimEnd('.', ',', ')', ']', '"', '\'').orEmpty()
}
