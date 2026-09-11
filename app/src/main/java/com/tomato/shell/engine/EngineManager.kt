package com.tomato.shell.engine

import android.content.Context
import android.util.Log
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 引擎进程管理器。
 *
 * ## 为什么不用 jniLibs
 *
 * 最初把官方安卓二进制改名为 `libtomato_engine.so` 放进 `jniLibs/arm64-v8a/`，
 * 期望安装时被解压到 `nativeLibraryDir`（该目录允许 exec）。但实测（realme Android 13）
 * 安装后 `/data/app/.../lib/arm64/` **是空的**——安装器会校验文件是否为合法共享库
 * （需要 DT_SONAME 等标记），裸 ELF 可执行文件即使改名 .so 也会被丢弃。
 *
 * ## 现行方案：assets + filesDir + linker64
 *
 * 1. 引擎以原样文件放在 `assets/tomato_engine`（assets 不做 so 校验，原样打包）
 * 2. 首次启动复制到 `filesDir/tomato_engine` 并 chmod +x
 * 3. 用系统动态链接器间接执行：`/system/bin/linker64 <path> --server`
 *
 * 第 3 步是关键：安卓 10+ 对 targetSdk>=29 的 App 禁止直接 exec 私有目录文件
 * （SELinux W^X），但通过 linker64 间接执行时内核/SELinux 只看到受信任的 linker，
 * 因此**非 root 可运行**。这是 termux 的同款机制。
 */
object EngineManager {

    private const val TAG = "EngineManager"

    // 引擎在 assets 中的文件名
    private const val ASSET_NAME = "tomato_engine"

    // 复制到 filesDir 后的文件名
    private const val BIN_NAME = "tomato_engine"

    // 引擎默认监听端口（与官方一致）
    const val PORT = 18423

    private var process: Process? = null
    private var started = false

    /** 引擎是否已启动且端口就绪 */
    val isRunning: Boolean get() = started && process?.isAlive == true

    /** 引擎监听地址（App 内访问本机引擎用 127.0.0.1） */
    val baseUrl: String get() = "http://127.0.0.1:$PORT"

    /**
     * 启动引擎进程。返回是否成功（端口就绪前会阻塞轮询）。
     *
     * 先探测端口：若已有引擎在监听（比如上次 App 被杀、引擎残留），直接复用，
     * 避免重复启动导致端口冲突。
     */
    fun start(context: Context): Boolean {
        if (started && process?.isAlive == true) return true

        // 端口已在监听 → 复用已有引擎（残留进程），不重复启动
        if (isPortOpen()) {
            Log.i(TAG, "端口 $PORT 已在监听，复用已有引擎")
            started = true
            return true
        }

        val enginePath = prepareEngine(context) ?: run {
            Log.e(TAG, "引擎二进制准备失败")
            return false
        }
        val dataDir = File(context.filesDir, "tomato_data").apply { mkdirs() }
        val logFile = File(context.filesDir, "tomato_engine.log")

        Log.i(TAG, "enginePath=$enginePath")
        Log.i(TAG, "dataDir=$dataDir")

        // 关键：用管道读取子进程输出（后台线程持续读走）。
        // 不要用 redirectOutput(File)：实测在某些 ROM 上重定向会让引擎进程卡住不监听端口。
        val cmd = arrayOf("/system/bin/linker64", enginePath, "--server", "--data-dir", dataDir.absolutePath)
        try {
            val pb = ProcessBuilder(*cmd)
            pb.redirectErrorStream(true)
            val p = pb.start()
            process = p
            Log.i(TAG, "已启动进程: ${cmd.joinToString(" ")}")
            // 后台线程读走 stdout/stderr，同时写入日志文件备查
            Thread {
                try {
                    p.inputStream.bufferedReader().useLines { lines ->
                        logFile.outputStream().bufferedWriter().use { w ->
                            lines.forEach { line ->
                                Log.i(TAG, "engine> $line")
                                w.write(line); w.newLine(); w.flush()
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "读取引擎输出结束: ${e.message}")
                }
            }.apply { isDaemon = true }.start()
        } catch (e: Exception) {
            Log.e(TAG, "linker64 启动失败: ${e.message}")
            return false
        }

        val ready = waitForPort(60_000)
        if (ready) {
            started = true
            Log.i(TAG, "引擎启动成功，监听 $baseUrl")
        } else {
            // 便于不连电脑时排查：把引擎自己的日志片段打到 logcat
            val tail = readLog(context, 2048)
            Log.e(TAG, "引擎端口未就绪（进程 alive=${process?.isAlive}）。引擎日志尾部：\n$tail")
            stop()
        }
        return started
    }

    /**
     * 确保引擎二进制在 filesDir 就绪：
     * 从 assets 复制（带大小校验，避免每次启动都拷 9MB），并设置可执行权限。
     */
    private fun prepareEngine(context: Context): String? {
        val dest = File(context.filesDir, BIN_NAME)
        return try {
            // 用 assets 里的文件大小做简单校验，一致就跳过复制
            val assetSize = context.assets.openFd(ASSET_NAME).use { it.length }
            if (!dest.exists() || dest.length() != assetSize) {
                Log.i(TAG, "从 assets 复制引擎到 filesDir（$assetSize 字节）")
                context.assets.open(ASSET_NAME).use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
                dest.setExecutable(true, false)
            } else {
                Log.i(TAG, "filesDir 已有引擎，跳过复制")
            }
            if (!dest.exists()) {
                Log.e(TAG, "复制后文件仍不存在: ${dest.absolutePath}")
                return null
            }
            dest.setExecutable(true, false)
            dest.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "准备引擎失败: ${e.message}")
            null
        }
    }

    /** 轮询引擎端口（TCP 连接探测）直到可用，超时返回 false */
    private fun waitForPort(timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (process?.isAlive != true) {
                Log.e(TAG, "进程已退出（可能在启动阶段崩溃）")
                return false
            }
            if (isPortOpen()) return true
            try {
                Thread.sleep(200)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                return false
            }
        }
        return false
    }

    /** 尝试与本机引擎端口建立 TCP 连接，成功即认为就绪 */
    private fun isPortOpen(): Boolean = try {
        java.net.Socket("127.0.0.1", PORT).use { true }
    } catch (e: Exception) {
        false
    }

    /** 停止引擎进程 */
    fun stop() {
        started = false
        process?.let {
            if (it.isAlive) {
                it.destroy()
                try {
                    it.waitFor(2, TimeUnit.SECONDS)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
                if (it.isAlive) it.destroyForcibly()
            }
        }
        process = null
    }

    /** 读取引擎日志（排查用） */
    fun readLog(context: Context, maxBytes: Int = 16_384): String = try {
        val f = File(context.filesDir, "tomato_engine.log")
        if (!f.exists()) "(无日志)"
        else f.readText().takeLast(maxBytes)
    } catch (e: Exception) {
        "读取日志失败: ${e.message}"
    }
}
