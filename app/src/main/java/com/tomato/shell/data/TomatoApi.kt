package com.tomato.shell.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * 番茄下载器引擎的 HTTP API 客户端。
 * 引擎由 EngineManager 启动，监听本机 127.0.0.1:18423，这里通过本地端口访问它。
 */
class TomatoApi(private val baseUrl: String = "http://127.0.0.1:18423") {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    private suspend fun <T> get(path: String, parse: (String) -> T): T =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(baseUrl + path)
                .get()
                .build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IOException("HTTP ${resp.code} for $path")
                }
                parse(resp.body?.string().orEmpty())
            }
        }

    private suspend fun post(path: String, bodyJson: String): String =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(baseUrl + path)
                .post(bodyJson.toRequestBody(jsonMedia))
                .build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IOException("HTTP ${resp.code} for POST $path")
                }
                resp.body?.string().orEmpty()
            }
        }

    /** 引擎是否存活（探测 status 接口） */
    suspend fun ping(): Boolean = try {
        get("/api/status") { true }
    } catch (e: Exception) {
        false
    }

    suspend fun status(): EngineStatus? = try {
        get("/api/status") { json.decodeFromString<EngineStatus>(it) }
    } catch (e: Exception) {
        null
    }

    /** 搜索书籍 */
    suspend fun search(keyword: String): List<SearchItem> {
        val encoded = URLEncoder.encode(keyword, "UTF-8")
        return get("/api/search?q=$encoded") {
            json.decodeFromString<SearchResponse>(it).items
                // 过滤掉没有 book_id 的异常条目（无法发起下载）
                .filter { it.bookId.isNotBlank() }
        }
    }

    /** 获取书籍详情 */
    suspend fun preview(bookId: String): BookDetail? = try {
        get("/api/preview/$bookId") { json.decodeFromString<BookDetail>(it) }
    } catch (e: Exception) {
        null
    }

    /**
     * 创建下载任务。
     *
     * @param rangeStart 起始章节（1-based，含）。与 rangeEnd 必须同时给或同时不给。
     * @param rangeEnd   结束章节（含）。两个都为 null 表示下载全部章节。
     */
    suspend fun createJob(bookId: String, rangeStart: Int? = null, rangeEnd: Int? = null): CreateJobResponse {
        // 引擎要求两个参数成对出现，这里做一次防御性归一
        val hasRange = rangeStart != null && rangeEnd != null
        val req = CreateJobRequest(
            bookId = bookId,
            rangeStart = if (hasRange) rangeStart else null,
            rangeEnd = if (hasRange) rangeEnd else null,
        )
        return json.decodeFromString(
            post("/api/jobs", json.encodeToString(CreateJobRequest.serializer(), req))
        )
    }

    /** 获取任务列表 */
    suspend fun jobs(): List<Job> = try {
        get("/api/jobs") { json.decodeFromString<JobListResponse>(it).items }
    } catch (e: Exception) {
        android.util.Log.e("TomatoApi", "拉取任务列表失败: ${e.message}")
        emptyList()
    }

    /** 取消任务 */
    suspend fun cancelJob(id: Long): Boolean = try {
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(baseUrl + "/api/jobs/$id/cancel")
                .post("".toRequestBody(jsonMedia))
                .build()
            client.newCall(request).execute().use { it.isSuccessful }
        }
    } catch (e: Exception) {
        false
    }

    /**
     * 设置书籍保存目录。
     * 引擎默认 save_path 为空，会退化成 `/`（不可写），所以启动后必须显式设置。
     * 通过 /api/config/full 提交（/api/config 不接受该字段）。
     */
    suspend fun setSavePath(path: String): Boolean = try {
        // 路径由 App 自己指定，不含引号等特殊字符，直接拼接即可
        post("/api/config/full", """{"save_path":"$path"}""")
        true
    } catch (e: Exception) {
        false
    }

    /**
     * 列出书库文件（异步扫描，首次调用触发扫描，需轮询到 running=false）。
     */
    suspend fun library(): LibraryResponse? = try {
        get("/api/library") { json.decodeFromString<LibraryResponse>(it) }
    } catch (e: Exception) {
        android.util.Log.e("TomatoApi", "拉取书库失败: ${e.message}")
        null
    }

    /**
     * 扫描本地书籍更新（异步扫描，首次调用触发，需轮询到 running=false）。
     * `updates` 是有更新的书，`no_updates` 是已最新的书。
     */
    suspend fun updates(): UpdatesResponse? = try {
        get("/api/updates") { json.decodeFromString<UpdatesResponse>(it) }
    } catch (e: Exception) {
        android.util.Log.e("TomatoApi", "扫描更新失败: ${e.message}")
        null
    }
}
