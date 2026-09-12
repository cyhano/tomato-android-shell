package com.tomato.shell.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 解析用户粘贴/分享进来的文本，提取番茄小说 book_id。
 *
 * 规则与引擎 TUI 的 book_id.rs 一致（Web 端 /api/search 不做这个解析，所以 APP 自己做）：
 * - 纯数字字符串（19 位左右的 book_id）→ 直接返回
 * - URL 带参数 `book_id=数字` / `bookId=数字` → 提取
 * - URL 路径 `/page/数字` → 提取
 * - 短链（`https://host/t/token`）→ 跟随一次 HTTP 跳转后再解析
 * 解析不出返回 null，调用方把它当普通关键词搜索。
 */
object BookRefParser {

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val reUrl = Regex("https?://\\S+")
    private val reQs = Regex("(?i)(book_id|bookId)=([0-9]+)")
    private val rePage = Regex("/page/(\\d+)")
    private val reShortLink = Regex("(?i)^https?://[^/\\s]+/t/[A-Za-z0-9_-]+/?(?:[?#]\\S*)?$")

    /** 解析文本里的 book_id。仅在短链时才发网络请求，其余都是纯文本处理。 */
    suspend fun resolve(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        // 1. 纯数字 ID
        if (trimmed.all { it.isDigit() }) return trimmed

        // 2. 从文本里截出 URL（分享文案通常带额外文字）
        val url = reUrl.find(trimmed)?.value ?: return null

        // 3. 普通 URL 直接提取
        parseFromUrl(url)?.let { return it }

        // 4. 短链：跟随跳转后重新解析
        if (reShortLink.containsMatchIn(url)) {
            return withContext(Dispatchers.IO) { followShortLink(url) }
        }
        return null
    }

    private fun parseFromUrl(url: String): String? {
        reQs.find(url)?.let { return it.groupValues[2] }
        rePage.find(url)?.let { return it.groupValues[1] }
        return null
    }

    /** 跟随短链跳转，从最终 URL 提取 book_id */
    private fun followShortLink(url: String): String? = try {
        client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            parseFromUrl(resp.request.url.toString())
        }
    } catch (e: Exception) {
        null
    }
}
