package com.tomato.shell.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 搜索结果条目。
 *
 * 注意：引擎把书名放在**顶层 `title`** 字段（不是 `book_name`，那个只在 raw 子对象里），
 * 实测返回结构：{"author":"...","book_id":"...","title":"仙界闭关小能手"}
 */
@Serializable
data class SearchItem(
    @SerialName("book_id") val bookId: String = "",
    /** 书名（顶层字段，主力） */
    val title: String = "",
    val author: String = "",
    /** 兜底书名字段（不同条目可能在这几个字段里） */
    @SerialName("book_name") val bookName: String = "",
    @SerialName("original_book_name") val originalBookName: String? = null,
    val category: String? = null,
    @SerialName("cover_url") val coverUrl: String? = null,
    /** 原始详情对象，含封面/简介等，字段较多，按需取用 */
    val raw: RawBook? = null,
) {
    /** 统一的显示书名：优先顶层 title，再退到其它字段 */
    val displayName: String
        get() = title.ifBlank { bookName }.ifBlank { originalBookName ?: "" }.ifBlank { "(未知书名)" }

    /** 封面：顶层没有就从 raw 里取 */
    val bestCover: String?
        get() = coverUrl ?: raw?.coverUrl ?: raw?.thumbUrl
}

/** raw 子对象（引擎原始书籍详情，只声明用得到的字段） */
@Serializable
data class RawBook(
    @SerialName("book_name") val bookName: String? = null,
    @SerialName("original_book_name") val originalBookName: String? = null,
    val abstract: String? = null,
    @SerialName("thumb_url") val thumbUrl: String? = null,
    @SerialName("audio_thumb_uri") val coverUrl: String? = null,
    @SerialName("word_number") val wordNumber: String? = null,
    @SerialName("chapter_number") val chapterNumber: String? = null,
    @SerialName("last_chapter_title") val lastChapterTitle: String? = null,
)

/** 搜索响应 */
@Serializable
data class SearchResponse(
    val items: List<SearchItem> = emptyList(),
)

/** 书籍详情（/api/preview/:book_id 返回，字段对齐引擎实际结构） */
@Serializable
data class BookDetail(
    @SerialName("book_id") val bookId: String = "",
    @SerialName("book_name") val bookName: String = "",
    val author: String? = null,
    val category: String? = null,
    val description: String? = null,
    @SerialName("chapter_count") val chapterCount: Long? = null,
    @SerialName("word_count") val wordCount: Long? = null,
    val finished: Boolean? = null,
    @SerialName("cover_url") val coverUrl: String? = null,
    @SerialName("first_chapter_title") val firstChapterTitle: String? = null,
    @SerialName("last_chapter_title") val lastChapterTitle: String? = null,
    @SerialName("read_count_text") val readCountText: String? = null,
    val score: Double? = null,
    val tags: List<String> = emptyList(),
)

/** 下载任务 */
@Serializable
data class Job(
    val id: Long,
    val title: String? = null,
    val state: String? = null,          // queued / running / done / error / cancelled
    val message: String? = null,
    val progress: JobProgress? = null,
    @SerialName("book_id") val bookId: String? = null,
    @SerialName("author") val author: String? = null,
    /**
     * 本地已有书籍标记（非引擎返回）。
     * 引擎任务列表是内存态，重启后清空，但 EPUB 还在磁盘上——
     * 这类条目由 App 启动时扫描本地目录生成，id 用负数避免与引擎任务冲突。
     */
    val local: Boolean = false,
    /** 本地文件绝对路径（仅 local=true 时有值） */
    @SerialName("local_path") val localPath: String? = null,
    /** 本地文件大小（字节，仅 local=true 时有值） */
    @SerialName("local_size") val localSize: Long? = null,
    // ---- 以下为本地书籍的元信息（从 status.json 读取，仅 local=true 时有值） ----
    val category: String? = null,
    val description: String? = null,
    @SerialName("word_count") val wordCount: Long? = null,
    @SerialName("read_count_text") val readCountText: String? = null,
    val tags: String? = null,
    @SerialName("local_chapters") val localChapters: Long? = null,
    @SerialName("last_chapter_title") val lastChapterTitle: String? = null,
    @SerialName("cover_path") val coverPath: String? = null,
)

/** 任务进度 */
@Serializable
data class JobProgress(
    @SerialName("chapter_total") val chapterTotal: Long? = null,
    @SerialName("saved_chapters") val savedChapters: Long? = null,
    @SerialName("save_phase") val savePhase: String? = null,
    @SerialName("group_total") val groupTotal: Long? = null,
    @SerialName("group_done") val groupDone: Long? = null,
    @SerialName("audiobook_generated") val audiobookGenerated: Long? = null,
)

/** 任务列表响应 */
@Serializable
data class JobListResponse(
    val items: List<Job> = emptyList(),
    @SerialName("done_retention_ms") val doneRetentionMs: Long? = null,
)

/**
 * 创建任务请求。
 *
 * 引擎（src/ui/web/routes/jobs.rs 的 CreateJobReq）支持章节范围：
 * - `range_start` / `range_end` 必须**成对出现**，只给一个会报错
 * - 校验：`start >= 1 && end >= 1 && start <= end`
 * - 两个都不传 = 下载全部章节
 */
@Serializable
data class CreateJobRequest(
    @SerialName("book_id") val bookId: String,
    @SerialName("range_start") val rangeStart: Int? = null,
    @SerialName("range_end") val rangeEnd: Int? = null,
)

/** 创建任务响应 */
@Serializable
data class CreateJobResponse(
    val id: Long,
    val state: String? = null,
    @SerialName("book_id") val bookId: String? = null,
)

/** 引擎状态 */
@Serializable
data class EngineStatus(
    @SerialName("bind_addr") val bindAddr: String? = null,
    val version: String? = null,
    @SerialName("save_dir") val saveDir: String? = null,
    val locked: Boolean? = null,
)

/** 书库文件/目录项（/api/library） */
@Serializable
data class LibraryItem(
    val name: String = "",
    /** 相对书库根的路径，如 "书名.epub" */
    @SerialName("rel_path") val relPath: String = "",
    val kind: String = "",          // "file" / "dir"
    val ext: String? = null,
    @SerialName("size") val size: Long? = null,
)

/** 书库列表响应 */
@Serializable
data class LibraryResponse(
    val items: List<LibraryItem> = emptyList(),
    val path: String = "",
    val root: String? = null,
    val running: Boolean = false,
    val scanned: Long = 0,
    val total: Long = 0,
    val error: String? = null,
)

/** 更新扫描结果里的一本书 */
@Serializable
data class UpdateRow(
    @SerialName("book_id") val bookId: String = "",
    val title: String? = null,
    @SerialName("has_update") val hasUpdate: Boolean = false,
    @SerialName("new_chapters") val newChapters: Long? = null,
    @SerialName("message") val message: String? = null,
)

/** 更新扫描响应（/api/updates，异步扫描，需轮询到 running=false） */
@Serializable
data class UpdatesResponse(
    val updates: List<UpdateRow> = emptyList(),
    @SerialName("no_updates") val noUpdates: List<UpdateRow> = emptyList(),
    val running: Boolean = false,
    val scanned: Long = 0,
    val total: Long = 0,
    @SerialName("save_dir") val saveDir: String? = null,
    val error: String? = null,
)
