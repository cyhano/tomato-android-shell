package com.tomato.shell.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tomato.shell.data.BookRefParser
import com.tomato.shell.data.BookDetail
import com.tomato.shell.data.EngineUpdateInfo
import com.tomato.shell.data.Job
import com.tomato.shell.data.JobProgress
import com.tomato.shell.data.SearchItem
import com.tomato.shell.data.TomatoApi
import com.tomato.shell.engine.EngineManager
import com.tomato.shell.engine.EngineUpdater
import com.tomato.shell.ui.screens.ChapterRangeParser
import com.tomato.shell.ui.screens.ChapterRangeParser.Result as RangeResult
import kotlinx.coroutines.Job as CoroutineJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val api = TomatoApi(EngineManager.baseUrl)

    // ---- 引擎状态 ----
    private val _engineReady = MutableStateFlow(false)
    val engineReady: StateFlow<Boolean> = _engineReady.asStateFlow()

    /** 引擎正在启动中（冷启动约需 10-20 秒，期间界面显示加载态而非错误） */
    private val _engineStarting = MutableStateFlow(true)
    val engineStarting: StateFlow<Boolean> = _engineStarting.asStateFlow()

    private val _engineVersion = MutableStateFlow("")
    val engineVersion: StateFlow<String> = _engineVersion.asStateFlow()

    // 引擎启动失败原因 / 日志（调试用，显示在界面上）
    private val _engineLog = MutableStateFlow("")
    val engineLog: StateFlow<String> = _engineLog.asStateFlow()

    // ---- 引擎自升级 ----

    /** 引擎升级弹层的完整状态 */
    data class EngineUpdateUiState(
        val checking: Boolean = false,
        val info: EngineUpdateInfo? = null,   // 检查结果（null = 还没检查过）
        val downloading: Boolean = false,
        val percent: Int = 0,                 // 下载进度 0-100
        val restarting: Boolean = false,      // 停引擎/替换/重启阶段
        val done: Boolean = false,
        val error: String? = null,
    )

    private val _engineUpdate = MutableStateFlow(EngineUpdateUiState())
    val engineUpdate: StateFlow<EngineUpdateUiState> = _engineUpdate.asStateFlow()

    /** 检查上游引擎新版本（GitHub Release） */
    fun checkEngineUpdate() {
        if (_engineUpdate.value.checking || _engineUpdate.value.downloading) return
        viewModelScope.launch {
            _engineUpdate.value = EngineUpdateUiState(checking = true)
            val current = _engineVersion.value.ifBlank { EngineManager.BUILTIN_ENGINE_VERSION }
            val info = withContext(Dispatchers.IO) { EngineUpdater.checkLatest(current) }
            _engineUpdate.value = EngineUpdateUiState(info = info)
        }
    }

    /**
     * 执行引擎升级：下载新二进制 → 停引擎 → 替换 → 重启。
     * 注意引擎自带的 /api/self_update 在 linker64 启动模式下不可用（见 EngineUpdater 注释）。
     */
    fun startEngineUpdate() {
        val state = _engineUpdate.value
        val url = state.info?.downloadUrl ?: return
        val tag = state.info.latestTag ?: return
        if (state.downloading || state.restarting) return
        viewModelScope.launch {
            _engineUpdate.value = state.copy(downloading = true, percent = 0, error = null)
            val app = getApplication<Application>()
            val tmp = File(app.filesDir, "tomato_engine.new")
            try {
                val ok = withContext(Dispatchers.IO) {
                    EngineUpdater.download(url, tmp) { p ->
                        _engineUpdate.value = _engineUpdate.value.copy(percent = p)
                    }
                }
                if (!ok) throw java.io.IOException("下载失败，请确认能访问 GitHub 后重试")

                _engineUpdate.value = _engineUpdate.value.copy(downloading = false, restarting = true)
                withContext(Dispatchers.IO) {
                    EngineManager.stop()
                    EngineManager.killEngineProcesses(app)
                    if (!EngineManager.installEngine(app, tmp, tag)) {
                        throw java.io.IOException("替换引擎文件失败")
                    }
                    // 重启引擎（prepareEngine 会看到 self 标记，保留新版本）
                    EngineManager.start(app)
                }
                _engineVersion.value =
                    withContext(Dispatchers.IO) { api.status()?.version } ?: tag.removePrefix("v")
                _engineReady.value = EngineManager.isRunning
                _engineUpdate.value = EngineUpdateUiState(done = true)
            } catch (e: Exception) {
                _engineUpdate.value = _engineUpdate.value.copy(
                    downloading = false,
                    restarting = false,
                    error = e.message ?: "升级失败",
                )
            }
        }
    }

    fun resetEngineUpdatePanel() {
        if (!_engineUpdate.value.downloading && !_engineUpdate.value.restarting) {
            _engineUpdate.value = EngineUpdateUiState()
        }
    }

    // ---- 搜索状态 ----
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SearchItem>>(emptyList())
    val searchResults: StateFlow<List<SearchItem>> = _searchResults.asStateFlow()

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    private val _searchError = MutableStateFlow<String?>(null)
    val searchError: StateFlow<String?> = _searchError.asStateFlow()

    // ---- 下载任务 ----
    private val _jobs = MutableStateFlow<List<Job>>(emptyList())
    val jobs: StateFlow<List<Job>> = _jobs.asStateFlow()

    private val _creatingJob = MutableStateFlow(false)
    val creatingJob: StateFlow<Boolean> = _creatingJob.asStateFlow()

    // ---- 书籍详情页 ----
    private val _openBookId = MutableStateFlow<String?>(null)
    val openBookId: StateFlow<String?> = _openBookId.asStateFlow()

    private val _detail = MutableStateFlow<BookDetail?>(null)
    val detail: StateFlow<BookDetail?> = _detail.asStateFlow()

    private val _detailLoading = MutableStateFlow(false)
    val detailLoading: StateFlow<Boolean> = _detailLoading.asStateFlow()

    private val _rangeText = MutableStateFlow("")
    val rangeText: StateFlow<String> = _rangeText.asStateFlow()

    private val _detailError = MutableStateFlow<String?>(null)
    val detailError: StateFlow<String?> = _detailError.asStateFlow()

    private var jobsPoller: CoroutineJob? = null

    init {
        // 先立即加载本地已下载的书——不依赖引擎，App 一打开下载列表就有内容。
        viewModelScope.launch {
            _jobs.value = withContext(Dispatchers.IO) { scanLocalBooks(force = true) }
        }

        viewModelScope.launch {
            // 注意：引擎启动包含 9MB 文件复制 + 阻塞式端口轮询（最长 60s），
            // 必须放在 IO 线程，否则会阻塞主线程导致 ANR。
            val ok = withContext(Dispatchers.IO) {
                EngineManager.start(getApplication())
            }
            _engineReady.value = ok
            _engineStarting.value = false
            _engineVersion.value = withContext(Dispatchers.IO) { api.status()?.version } ?: ""
            if (ok) {
                // 引擎默认 save_path 为空（退化成 / 不可写），启动后必须设置保存目录。
                // 用 App 外部私有目录：无需存储权限，用户也能通过文件管理器找到。
                val saveDir = getApplication<Application>()
                    .getExternalFilesDir(null)?.let { java.io.File(it, "books") }
                withContext(Dispatchers.IO) {
                    saveDir?.mkdirs()
                    saveDir?.let { api.setSavePath(it.absolutePath) }
                }
                startJobsPolling()
            } else {
                // 失败时把引擎日志抓到界面上，便于不连电脑也能排查
                _engineLog.value = withContext(Dispatchers.IO) {
                    EngineManager.readLog(getApplication())
                }
            }
        }
    }

    fun onQueryChange(q: String) {
        _searchQuery.value = q
    }

    /**
     * 执行搜索。
     * 输入是链接 / 纯数字 ID 时直接进详情页（与 TUI 的"书名/ID/链接"行为一致），
     * 否则作为关键词调引擎搜索。
     */
    fun search() {
        val q = _searchQuery.value.trim()
        if (q.isEmpty()) return
        viewModelScope.launch {
            _searching.value = true
            _searchError.value = null
            try {
                // 短链才会发网络请求，普通关键词零开销
                val bookId = withContext(Dispatchers.IO) { BookRefParser.resolve(q) }
                if (bookId != null) {
                    _searching.value = false
                    openDetail(bookId)
                } else {
                    _searchResults.value = api.search(q)
                }
            } catch (e: Exception) {
                _searchError.value = e.message ?: "搜索失败"
                _searchResults.value = emptyList()
            } finally {
                _searching.value = false
            }
        }
    }

    /** 处理外部分享/粘贴进来的文本（分享目标入口）：能解析出书就直接进详情页 */
    fun onExternalText(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            val bookId = withContext(Dispatchers.IO) { BookRefParser.resolve(text) }
            if (bookId != null) {
                openDetail(bookId)
            } else {
                // 解析不出就填进搜索框，让用户自己按搜索
                _searchQuery.value = text.trim().take(120)
            }
        }
    }

    // ---- 详情页操作 ----

    /** 详情页对应书的本地已下载章节数（>0 时详情页显示续传提示） */
    private val _detailOwned = MutableStateFlow<Long?>(null)
    val detailOwned: StateFlow<Long?> = _detailOwned.asStateFlow()

    /** 打开某本书的详情页（并拉取详情）。从「更新」按钮进入时，带出本地已下载章数。 */
    fun openDetail(bookId: String) {
        _openBookId.value = bookId
        _detail.value = null
        _rangeText.value = ""
        _detailError.value = null
        _detailLoading.value = true
        viewModelScope.launch {
            _detailOwned.value = withContext(Dispatchers.IO) {
                scanLocalBooks().firstOrNull { it.bookId == bookId }?.localChapters
            }
            val d = withContext(Dispatchers.IO) { api.preview(bookId) }
            _detail.value = d
            _detailLoading.value = false
            if (d == null) _detailError.value = "拉取书籍详情失败，请检查网络后重试"
        }
    }

    /** 关闭详情页 */
    fun closeDetail() {
        _openBookId.value = null
        _detail.value = null
        _detailError.value = null
        _detailOwned.value = null
    }

    fun onRangeChange(t: String) {
        _rangeText.value = t
    }

    /**
     * 从详情页发起下载。
     * 解析章节范围输入（空=全部、`1-100`、`50`），转成引擎的 range_start/range_end。
     */
    fun downloadFromDetail() {
        val bookId = _openBookId.value ?: return
        val total = _detail.value?.chapterCount ?: 0
        when (val r = ChapterRangeParser.parse(_rangeText.value, total)) {
            is RangeResult.Invalid -> {
                _detailError.value = r.reason
                return
            }
            RangeResult.All -> {
                _detailError.value = null
                doCreateJob(bookId, null, null)
            }
            is RangeResult.Range -> {
                _detailError.value = null
                doCreateJob(bookId, r.start, r.end)
            }
        }
    }

    private fun doCreateJob(bookId: String, start: Int?, end: Int?) {
        viewModelScope.launch {
            _creatingJob.value = true
            try {
                api.createJob(bookId, start, end)
                _jobs.value = api.jobs()
                // 创建成功后退出详情页，让用户看到下载进度
                closeDetail()
            } catch (e: Exception) {
                _detailError.value = "发起下载失败: ${e.message}"
            } finally {
                _creatingJob.value = false
            }
        }
    }

    /** 取消任务 */
    fun cancelJob(id: Long) {
        viewModelScope.launch {
            api.cancelJob(id)
            _jobs.value = api.jobs()
        }
    }

    // ---- 打开已下载的书 ----

    private val _openMessage = MutableStateFlow<String?>(null)
    val openMessage: StateFlow<String?> = _openMessage.asStateFlow()

    fun clearOpenMessage() {
        _openMessage.value = null
    }

    /**
     * 用其它 App（微信读书、静读天下等）打开已下载的 EPUB。
     *
     * 本地条目（local=true）直接用记录好的路径；引擎任务则按书名在下载目录里匹配。
     */
    fun openDownloadedBook(job: Job) {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            // 本地条目：路径已知，直接用
            if (job.local && job.localPath != null) {
                val f = File(job.localPath)
                if (!f.exists()) {
                    _openMessage.value = "文件已不存在：${f.absolutePath}"
                    refreshLocalBooks()
                    return@launch
                }
                if (!openFileWithSystem(ctx, f)) {
                    _openMessage.value = "没有找到能打开 EPUB 的应用，请先安装阅读器"
                }
                return@launch
            }

            // 引擎任务：按书名在下载目录里找
            val title = job.title ?: job.bookId ?: return@launch
            val epubs = withContext(Dispatchers.IO) {
                booksDir().listFiles { f -> f.isFile && f.name.endsWith(".epub", true) }
                    ?.toList().orEmpty()
            }
            if (epubs.isEmpty()) {
                _openMessage.value = "下载目录里还没有 EPUB 文件（${booksDir().absolutePath}）"
                return@launch
            }
            val target = epubs.firstOrNull { it.name.contains(title) }
                ?: epubs.firstOrNull { title.contains(it.nameWithoutExtension) }
                ?: epubs.singleOrNull()
            if (target == null) {
                _openMessage.value =
                    "在下载目录没找到《$title》对应的文件，请确认是否已下载完成"
                return@launch
            }
            if (!openFileWithSystem(ctx, target)) {
                _openMessage.value = "没有找到能打开 EPUB 的应用，请先安装阅读器"
            }
        }
    }

    /** 用系统 Intent 打开本地文件，返回是否成功唤起 */
    private fun openFileWithSystem(ctx: android.content.Context, file: File): Boolean = try {
        // 通过 FileProvider 提供 URI（Android 7+ 禁止直接传 file://）
        val uri = androidx.core.content.FileProvider.getUriForFile(
            ctx, "${ctx.packageName}.fileprovider", file
        )
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/epub+zip")
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        // 先试 EPUB 专用类型，没有应用时退到 */* 让系统列出所有能处理的 App
        try {
            ctx.startActivity(intent)
            true
        } catch (e: android.content.ActivityNotFoundException) {
            val fallback = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "*/*")
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(fallback)
            true
        }
    } catch (e: Exception) {
        android.util.Log.e("AppViewModel", "打开文件失败: ${e.message}")
        false
    }

    // ---- 检查更新 ----

    private val _updateScanning = MutableStateFlow(false)
    val updateScanning: StateFlow<Boolean> = _updateScanning.asStateFlow()

    private val _updateResult = MutableStateFlow<String?>(null)
    val updateResult: StateFlow<String?> = _updateResult.asStateFlow()

    fun clearUpdateResult() {
        _updateResult.value = null
    }

    /**
     * 检查本地书籍更新（App 自研实现，不用引擎的 /api/updates）。
     *
     * 引擎的 /api/updates 会卡死：它内部逐本书调第三方 API 查目录，
     * 而那个联网请求**没有超时**，网络不通时永久挂起（scanned 恒为 0）。
     * 这里改用可靠的两步：
     *   1. 复用本地书扫描结果（status.json 的 downloaded 条目数 = 实际拥有的章节数）
     *   2. 调 /api/preview/<book_id> 拿远端最新 chapter_count
     * 远端 > 实际拥有 → 有新章节（含之前只下载部分章的情况）。
     */
    fun checkUpdates() {
        if (_updateScanning.value) return
        viewModelScope.launch {
            _updateScanning.value = true
            _updateResult.value = null
            try {
                val books = withContext(Dispatchers.IO) {
                    scanLocalBooks(force = true).filter { it.bookId != null }
                }
                if (books.isEmpty()) {
                    _updateResult.value = "本地还没有已下载的书"
                    return@launch
                }
                val updated = mutableListOf<String>()
                var checked = 0
                val remoteMap = mutableMapOf<String, Long>()
                for (b in books) {
                    val detail = withContext(Dispatchers.IO) { api.preview(b.bookId!!) }
                    val remote = detail?.chapterCount ?: continue
                    val owned = b.localChapters ?: 0
                    checked++
                    // 记录远端最新章节数，卡片上的「更新」按钮据此显示
                    remoteMap[b.bookId!!] = remote
                    if (remote > owned) {
                        updated += "${b.title ?: b.bookId}（+${remote - owned}章）"
                    }
                }
                _remoteChapters.value = remoteMap
                _updateResult.value = when {
                    checked == 0 -> "检查失败，请确认网络后重试"
                    updated.isEmpty() -> "已检查 $checked 本，都是最新的"
                    else -> "有 ${updated.size} 本可更新：${updated.joinToString("、")}"
                }
            } catch (e: Exception) {
                _updateResult.value = "检查失败: ${e.message}"
            } finally {
                _updateScanning.value = false
            }
        }
    }

    /** 检查更新后记录的远端最新章节数（bookId -> chapter_count），卡片据此显示「更新」按钮 */
    private val _remoteChapters = MutableStateFlow<Map<String, Long>>(emptyMap())
    val remoteChapters: StateFlow<Map<String, Long>> = _remoteChapters.asStateFlow()

    // ---- 本地已下载书籍（持久化） ----

    /** 本地磁盘上的 EPUB 缓存。引擎任务列表是内存态重启即清空，靠它恢复列表。 */
    private var localCache: List<Job> = emptyList()
    private var localCacheAt = 0L

    /** 下载目录 */
    private fun booksDir(): File =
        File(getApplication<Application>().getExternalFilesDir(null), "books")

    /**
     * 扫描下载目录里已完成的 EPUB，构造成 state=done 的本地 Job。
     *
     * 除了 EPUB 本身，还会读同 book_id 目录下的 status.json 补全元信息
     * （作者/简介/章节数/最新章节/封面），让列表卡片能显示丰富内容。
     * 带 3 秒结果缓存，避免每秒轮询都读盘。
     */
    private fun scanLocalBooks(force: Boolean = false): List<Job> {
        val now = System.currentTimeMillis()
        if (!force && now - localCacheAt < 3000) return localCache
        val dir = booksDir()
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".epub", true) }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()
        android.util.Log.i("AppViewModel", "扫描本地书: dir=${dir.absolutePath} epub数=${files.size}")
        localCache = files.mapIndexed { i, f ->
            val title = f.nameWithoutExtension
            // 找对应的 <book_id> 缓存目录（status.json 里有完整元信息）
            val meta = findStatusFor(f, dir)
            Job(
                // 本地条目 id 用负数，避免和引擎任务 id（正数）冲突
                id = -(i + 1).toLong(),
                title = title,
                state = "done",
                local = true,
                bookId = meta?.bookId,
                localPath = f.absolutePath,
                localSize = f.length(),
                author = meta?.author,
                category = meta?.category,
                description = meta?.description,
                wordCount = meta?.wordCount,
                readCountText = meta?.readCountText,
                tags = meta?.tags,
                localChapters = meta?.localChapters,
                chapterCount = meta?.chapterCount,
                lastChapterTitle = meta?.lastChapterTitle,
                coverPath = meta?.coverPath,
                progress = JobProgress(savePhase = "本地文件"),
            )
        }
        localCacheAt = now
        return localCache
    }

    /** status.json 里提取的元信息 */
    private data class StatusMeta(
        val bookId: String?,
        val author: String?,
        val category: String?,
        val description: String?,
        val wordCount: Long?,
        val readCountText: String?,
        val tags: String?,
        val chapterCount: Long?,
        val localChapters: Long?,
        val lastChapterTitle: String?,
        val coverPath: String?,
    )

    /**
     * 为某个 EPUB 找到对应的 status.json 并提取元信息。
     *
     * 先按文件名匹配同名的 <book_id> 目录；匹配不到就按标题在 status.json 的
     * book_name 里找。
     *
     * status.json 含全书正文（实测有 30MB 的），所以：
     * - 头部 8KB：正则提取 author/category/description 等静态字段
     * - 全文件流式扫描：逐行匹配 `"<章节id>": ["标题", ...` 模式，
     *   同时得到「已下载章节数」和「最后一章标题」
     */
    private fun findStatusFor(epub: File, dir: File): StatusMeta? {
        val candidates = dir.listFiles { f -> f.isDirectory }?.toList().orEmpty()
        for (folder in candidates) {
            val status = File(folder, "status.json")
            if (!status.exists()) continue
            val head = readHead(status, 8192) ?: continue
            val bookName = Regex("\"book_name\"\\s*:\\s*\"([^\"]*)\"").find(head)
                ?.groupValues?.get(1) ?: continue
            if (bookName != epub.nameWithoutExtension) continue

            // 流式扫描章节条目。status.json 是 pretty-print 格式，一章跨多行：
            //   "7424078857809969726": [      ← 条目行（数章节数）
            //     "第1章 开篇",                ← 标题行（取最后一章标题）
            //     "<article>...</article>"     ← 正文行
            //   ],
            var chapters = 0L
            var lastTitle: String? = null
            var expectTitle = false
            val entryLine = Regex("^\\s*\"\\d+\"\\s*:\\s*\\[")
            val titleLine = Regex("^\\s*\"([^\"]{1,120})\"\\s*,?\\s*$")
            try {
                status.bufferedReader().useLines { lines ->
                    for (line in lines) {
                        if (expectTitle) {
                            // 紧跟条目行的第一行是章节标题
                            titleLine.find(line)?.let { lastTitle = it.groupValues[1] }
                            expectTitle = false
                            continue
                        }
                        if (entryLine.containsMatchIn(line)) {
                            chapters++
                            expectTitle = true
                        }
                    }
                }
            } catch (e: Exception) {
                // 读不完就用已统计到的部分
            }

            val cover = File(folder, "cover.png").takeIf { it.exists() }
                ?: File(folder, "cover.jpg").takeIf { it.exists() }
            return StatusMeta(
                bookId = Regex("\"book_id\"\\s*:\\s*\"(\\d+)\"").find(head)?.groupValues?.get(1)
                    ?: folder.name,
                author = Regex("\"author\"\\s*:\\s*\"([^\"]*)\"").find(head)?.groupValues?.get(1),
                category = Regex("\"category\"\\s*:\\s*\"([^\"]*)\"").find(head)?.groupValues?.get(1),
                description = Regex("\"description\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
                    .find(head)?.groupValues?.get(1)
                    ?.replace("\\n", "\n")?.replace("\\\"", "\""),
                wordCount = Regex("\"word_count\"\\s*:\\s*(\\d+)").find(head)
                    ?.groupValues?.get(1)?.toLongOrNull(),
                readCountText = Regex("\"read_count_text\"\\s*:\\s*\"([^\"]*)\"").find(head)
                    ?.groupValues?.get(1),
                tags = Regex("\"tags\"\\s*:\\s*\\[([^\\]]*)\\]").find(head)
                    ?.groupValues?.get(1)?.replace("\"", "")?.replace(",", " · "),
                chapterCount = Regex("\"chapter_count\"\\s*:\\s*(\\d+)").find(head)
                    ?.groupValues?.get(1)?.toLongOrNull(),
                localChapters = chapters.takeIf { it > 0 },
                lastChapterTitle = lastTitle,
                coverPath = cover?.absolutePath,
            )
        }
        return null
    }

    /** 读文件前 N 字节（status.json 可能很大，不整个载入） */
    private fun readHead(f: File, n: Int): String? = try {
        f.inputStream().use { ins ->
            val buf = ByteArray(n)
            val len = ins.read(buf)
            if (len <= 0) "" else String(buf, 0, len, Charsets.UTF_8)
        }
    } catch (e: Exception) {
        null
    }

    /** 合并引擎任务与本地书籍。 */
    private fun mergeJobs(engineJobs: List<Job>): List<Job> {
        val local = scanLocalBooks()
        if (local.isEmpty()) return engineJobs
        val localByTitle = local.associateBy { it.title }
        // 引擎已完成且本地已有同名 EPUB 的任务 → 丢弃引擎条目，
        // 显示本地富卡片（含封面/章节数/「更新」按钮，可直接打开）
        val kept = engineJobs.filter { it.state != "done" || localByTitle[it.title] == null }
        val keptTitles = kept.mapNotNull { it.title }.toSet()
        val extra = local.filter { it.title !in keptTitles }
        return kept + extra
    }

    /** 开始轮询任务列表（合并本地已下载书籍，App 重启后列表不会空） */
    fun startJobsPolling() {
        jobsPoller?.cancel()
        jobsPoller = viewModelScope.launch {
            while (true) {
                _jobs.value = mergeJobs(api.jobs())
                delay(1000)
            }
        }
    }

    /** 强制刷新本地书缓存（下载完成、删除文件后调用） */
    fun refreshLocalBooks() {
        scanLocalBooks(force = true)
    }

    override fun onCleared() {
        jobsPoller?.cancel()
        super.onCleared()
    }
}
