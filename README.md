# 番茄小说下载器 · 安卓原生套壳 App

用 **Kotlin + Jetpack Compose** 写的原生安卓界面，把官方 [Tomato-Novel-Downloader](https://github.com/zhongbai2333/Tomato-Novel-Downloader) 的安卓 arm64 引擎二进制打包进 APK，App 启动时在后台拉起引擎，通过本地 HTTP JSON API 调用它。

**不 fork 官方源码，不自己编译 Rust，不用 WebView，界面全部原生实现。**

## 功能

- **搜索**：输入书名/作者（含热门搜索快捷按钮），展示带封面的结果列表
- **书籍详情页**：点搜索结果进入，展示封面/作者/分类/章节数/简介
- **章节范围下载**：详情页可指定下载范围
  - 留空 → 全部章节
  - `1-100` → 第 1 到 100 章
  - `50` → 从第 50 章下载到最后一章
- **下载进度**：任务列表实时显示状态徽标 + 章节进度条（每秒轮询），可取消
- **打开已下载的书**：完成后点「打开」或卡片，通过系统 Intent 交给微信读书 / 书城 / ES 等 App 打开
- **检查更新**：扫描本地书库看哪些书有新章节

实测跑通：搜索 → 详情 → 指定 1-50 章 → 下载完成 → 微信读书成功导入 EPUB。

## 架构

```
┌─────────────────────────────────────────┐
│  Kotlin + Jetpack Compose (原生 UI)      │
│  搜索页 / 详情页 / 下载页                 │
└──────────────┬──────────────────────────┘
               │ OkHttp（HTTP JSON，127.0.0.1:18423）
               ▼
┌─────────────────────────────────────────┐
│  引擎进程（官方安卓 arm64 二进制）        │
│  /system/bin/linker64 <engine> --server  │
└─────────────────────────────────────────┘
```

## 六个关键坑（都踩过并已修复）

### 1. 引擎不能用 jniLibs 分发

最初把引擎改名 `libtomato_engine.so` 放进 `jniLibs/arm64-v8a/`，期望安装时解压到 `nativeLibraryDir`（可 exec）。**实测安装后该目录是空的**——安装器会校验文件是否为合法共享库（需要 `DT_SONAME` 等），裸 ELF 可执行文件即使改名 `.so` 也会被丢弃。

**方案**：引擎放 `assets/tomato_engine`（assets 不校验、原样打包），首次启动复制到 `filesDir` 并 chmod +x。

### 2. 必须用 linker64 间接执行

安卓 10+ 对 `targetSdk >= 29` 的 App 禁止直接 exec 私有目录文件（SELinux W^X），`ProcessBuilder(enginePath)` 会被拒。

**方案**：termux 同款机制——`/system/bin/linker64 <path> --server`。内核/SELinux 只看到受信任的 linker，**非 root 可运行**。

### 3. 不要用 redirectOutput(File)

`ProcessBuilder.redirectOutput(File)` 在本机 realme 上会让引擎进程**卡住不监听端口**（进程 alive 但什么都不做，日志文件也是空的，极难排查）。

**方案**：用管道 + 后台线程持续读走 stdout/stderr，同时写日志文件。

### 4. 必须放行明文 HTTP

引擎监听 `127.0.0.1:18423` 用 HTTP。Android 9+ 默认禁明文流量，会导致 OkHttp 请求全部失败（但 TCP 端口的 ping 探测仍然成功，所以引擎显示"已启动"却调不通 API，很容易误判）。

**方案**：`res/xml/network_security_config.xml` 对回环地址放行明文。

### 5. innerPadding 必须由外层统一处理

把 Scaffold 的 `innerPadding` 加在子页面的 `fillMaxSize()` 上会导致**高度计算出 0、内容完全不渲染**（页面一片空白但无报错，极难排查）。

**方案**：外层用 `Box(Modifier.fillMaxSize().padding(innerPadding))` 承载，子页面只管自己的内容布局。

### 6. 引擎启动必须放 IO 线程

引擎启动包含 9MB 文件复制 + 阻塞式端口轮询（最长 60s），放主线程会 ANR 被杀。

## 引擎接口的关键字段（踩坑记录）

| 项目 | 结论 |
|------|------|
| 搜索结果的**书名** | 在顶层 `title` 字段，**不是 `book_name`**（后者只在 raw 子对象里）；封面也从 raw 取 |
| 章节范围参数 | `POST /api/jobs` 的 `range_start` / `range_end`，**必须成对出现**，都不传=全部 |
| `POST /api/config` | 只接受部分字段，**`save_path` 要发 `/api/config/full`** |
| `/api/library`、`/api/updates` | 异步扫描，需轮询到 `running=false`；本机实测 `/api/updates` 在 App 外部私有目录下会卡住不返回，已加"无进展超时"提示 |

## 目录结构

```
tomato-android-shell/
├── app/src/main/
│   ├── java/com/tomato/shell/
│   │   ├── MainActivity.kt              # Compose 入口 + 底部导航 + 启动加载态
│   │   ├── engine/EngineManager.kt      # 引擎准备(assets→files) + linker64 启动 + 日志
│   │   ├── data/
│   │   │   ├── ApiModels.kt             # 数据模型（含 search title / 下载范围）
│   │   │   └── TomatoApi.kt             # OkHttp 封装引擎 API
│   │   └── ui/
│   │       ├── AppViewModel.kt          # 状态管理 + 任务轮询 + 打开文件 + 检查更新
│   │       ├── screens/SearchScreen.kt  # 搜索页
│   │       ├── screens/DetailScreen.kt  # 详情页 + 章节范围解析
│   │       └── screens/DownloadScreen.kt# 下载任务页
│   ├── assets/tomato_engine             # 官方安卓引擎二进制
│   └── res/xml/
│       ├── network_security_config.xml  # 放行回环明文 HTTP
│       └── file_paths.xml               # FileProvider（打开下载的书）
├── keystore/tomato.keystore             # 固定签名（密码 tomato123456，别名 tomato）
├── scripts/fetch_engine.sh              # 拉取/更新引擎二进制
└── vendor/                              # 引擎原始二进制备份
```

## 构建

```bash
bash scripts/fetch_engine.sh          # 拉取（或更新）官方引擎二进制
./gradlew :app:assembleRelease        # 打 release 包
# 产物：app/build/outputs/apk/release/app-release.apk
```

## 引擎 API（本项目用到的）

| 接口 | 说明 |
|------|------|
| `GET /api/status` | 引擎状态/版本 |
| `GET /api/search?q=<关键词>` | 搜索书籍 |
| `GET /api/preview/<book_id>` | 书籍详情/目录 |
| `POST /api/jobs` body `{"book_id":"..."}` | 创建下载任务 |
| `GET /api/jobs` | 任务列表（含 progress 进度） |
| `POST /api/jobs/<id>/cancel` | 取消任务 |
| `POST /api/config/full` body `{"save_path":"..."}` | 设置保存目录 |

## 已知限制

- **仅支持 arm64-v8a**：引擎只有 arm64 二进制。
- **旁载安装**：运行时执行了 APK 内的原生二进制，不符合 Google Play 政策，只能旁载。
- **部分手机需在 UI 上点「继续安装」**：OPPO/realme 等 ROM 的安装器会做病毒扫描，`adb install` 可能报 `Failure [-99]`，需在手机弹出的安装引导页点「继续安装」（同一签名后续可覆盖安装）。
- **首次启动稍慢**：需复制 9MB 引擎并等其监听端口（约 10-20 秒），顶部显示"引擎启动中…"。
- 下载的书存于 `Android/data/com.tomato.shell/files/books/`（EPUB 格式）。
- 引擎日志：`files/tomato_engine.log`；启动失败时界面会直接显示该日志。
