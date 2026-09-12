package com.tomato.shell.engine

import android.util.Log
import com.tomato.shell.data.EngineUpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 引擎自升级：从上游 GitHub Release 下载最新安卓 arm64 二进制并替换本地的引擎文件。
 *
 * 为什么不用引擎自带的 POST /api/self_update：它把新文件 rename 到
 * `/proc/self/exe` 所在目录——我们用 `/system/bin/linker64 <path>` 间接执行，
 * self_exe 指向 linker64，引擎会试图写 /system/bin（无 root 必失败）。
 * 所以由 APP 侧完成「检查 → 下载 → 停引擎 → 替换 → 重启」全流程。
 */
object EngineUpdater {

    private const val TAG = "EngineUpdater"

    private const val REPO = "zhongbai2333/Tomato-Novel-Downloader"
    private const val LATEST_API = "https://api.github.com/repos/$REPO/releases/latest"

    /** 上游 release 的安卓资产为固定名，latest/download 会 302 到最新版本 */
    private const val DOWNLOAD_URL =
        "https://github.com/$REPO/releases/latest/download/TomatoNovelDownloader-Android_arm64"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** 查询上游最新版本（GitHub API）。失败时返回带 error 的结果。 */
    suspend fun checkLatest(currentVersion: String): EngineUpdateInfo = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(LATEST_API)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "tomato-android-shell")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("GitHub HTTP ${resp.code}")
                val body = resp.body?.string().orEmpty()
                val tag = Regex("\"tag_name\"\\s*:\\s*\"([^\"]+)\"").find(body)
                    ?.groupValues?.get(1)
                    ?: throw IOException("响应里没有版本号")
                val cmp = EngineManager.compareVersions(tag, currentVersion)
                EngineUpdateInfo(
                    current = currentVersion,
                    latestTag = tag,
                    hasUpdate = cmp > 0,
                    downloadUrl = if (cmp > 0) DOWNLOAD_URL else null,
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "检查引擎更新失败: ${e.message}")
            EngineUpdateInfo(current = currentVersion, error = "无法连接 GitHub：${e.message}")
        }
    }

    /** 流式下载引擎二进制，onProgress 回调 0-100。返回是否成功。 */
    suspend fun download(url: String, dest: File, onProgress: (Int) -> Unit): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "tomato-android-shell")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                    val body = resp.body ?: throw IOException("响应无内容")
                    val total = body.contentLength()
                    dest.outputStream().use { out ->
                        val input = body.byteStream()
                        val buf = ByteArray(64 * 1024)
                        var read = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n <= 0) break
                            out.write(buf, 0, n)
                            read += n
                            if (total > 0) {
                                onProgress((read * 100 / total).toInt().coerceIn(0, 100))
                            }
                        }
                    }
                    // 简单校验：非空且像 ELF（开头 4 字节 \x7fELF）
                    if (dest.length() < 1024 * 1024) throw IOException("文件过小，可能不是引擎二进制")
                    dest.inputStream().use { ins ->
                        val head = ByteArray(4)
                        ins.read(head)
                        if (!head.contentEquals(byteArrayOf(0x7f, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte()))) {
                            throw IOException("下载内容不是 ELF 二进制")
                        }
                    }
                    true
                }
            } catch (e: Exception) {
                Log.w(TAG, "下载引擎失败: ${e.message}")
                dest.delete()
                false
            }
        }
}
