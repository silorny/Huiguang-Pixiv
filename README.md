# 绘光 · Huiguang

一款基于原生 Jetpack Compose 与 [COUI-Kit](https://github.com/ItosEO/COUI-Kit)（`0.0.1-alpha15`）构建的第三方 Pixiv Android 客户端。全应用遵循纯 COUI 视觉与交互规范，不依赖 Material 3，提供插画、漫画、小说及 Pixivision 特辑的完整浏览、搜索、收藏与阅读体验。

- **当前版本**：`0.1.4`
- **项目仓库**：[silorny/Huiguang-Pixiv](https://github.com/silorny/Huiguang-Pixiv)（Apache-2.0）
- **应用包名**：`moe.huiguang.pixiv`
- **支持系统**：Android 14 及以上（`minSdk = 34`，`compileSdk = 37.1`，`targetSdk = 37`）
- **UI 与构建基线**：Kotlin `2.4.20` + AGP `9.4.1` + Gradle `9.8.0` + Jetpack Compose BOM `2026.08.01`

---

## 功能概览

- **首页推荐流**：支持「推荐」「排行榜」「关注新作」三大分区及顶栏悬浮多选分类菜单（插画 / 漫画 / 小说，默认插画与漫画），排行榜支持今日 / 本周 / 本月 / 新人维度切换与顶部 Pixivision 横向特辑区；瀑布流支持画质分级加载、预加载续载、底栏双击快速回顶与按账号隔离的本地离线首屏缓存（`HomeFeedCache`），未登录时提供精选发现流兜底。
- **搜索与标签专题浏览**：支持关键词、精确标签与作者搜索（登录态可用），识别 PID 与 Pixiv 作品/小说/作者网页链接直达；结果页通过顶栏悬浮菜单切换作品分类、发布排序（最新 / 最早）与内容分级（全部 / 全年龄 / R-18）；标签搜索顶部展示包含官方 Pixpedia 封面、英文译名、作品总数与「收藏标签」凝光按钮的同心圆角信息卡；本地搜索历史支持基于 COUI 双主体融球（`couiFloatingPair`）的液滴粘滞左滑删除、类别筛选与二次确认清空。
- **插画 / 漫画详情、作者主页与大图查看器**：统一作品、小说、特辑与作者副标题栏样式，支持点击复制 PID / UID 与发布时间展示；作者主页支持封面大图卡片与漫画独立分页加载；全屏大图查看器支持纵向连续浏览与左右翻页切换、双指/双击缩放，以及单张或全部原图保存至系统 `Download` 目录或用户通过系统文件选择器授权的自定义目录。
- **小说详情、系列与沉浸式阅读器**：支持字数与阅读时长估算、官方系列上下话连读与章节目录、`[chapter:]` / `[newpage]` / `[[rb:]]` / 内嵌插图排版；阅读器内置半屏排版设定面板，支持「系统 / 象牙纸 / 护眼绿 / 暗夜」四套纸面主题、滚动与精确分页双模式、A-/A+ 字号微调、字重/行距/首行缩进调节、进度自动保存与 TXT 全文导出。
- **个人域、设置与关于**：支持官方网页登录（WebView）、云端公开/非公开收藏切换、瀑布流/列表视图切换与左滑转私/公开管理，以及浏览历史管理；支持应用内实时切换语言（跟随系统 / 简体中文 / 繁體中文（香港） / English）、主题外观（跟随系统 / 浅色 / 深色）、「惯用左手」适配、自定义下载目录与自定义作品/作者分享格式模板；「关于」页面提供开发团队资料、版本更新检测与一键下载安装（GitHub Releases）、开源许可原文、用户协议、版权免责及社区交流入口。

---

## 隐私、安全与合规设计

- **启动隐私门禁**：首次启动须确认用户协议与隐私声明后才放行网络加载；拒绝则退出应用。
- **会话凭证加密与零备份**：通过官方 WebView 登录，应用不读取或保存账号密码；登录 Cookie 通过 `AndroidKeyStore`（AES-256-GCM）加密后存储在私有存储中，退出登录时同步清除本地 Session、WebView 会话、本地收藏与关注缓存、账号缓存及图片/更新缓存；关闭 `allowBackup` 与跨设备云端数据迁移（`dataExtractionRules`）。
- **网络域边界隔离**：Pixiv 会话 Cookie 仅发送至 Pixiv 官方 HTTPS 域名，跨站重定向时自动剥离 `Cookie`、`Referer` 与 `Accept-Language`；「关于」页展示开发者公开资料与点击「检查更新」时使用无状态独立客户端（`AppUpdateService.publicHttp`）匿名只读访问 GitHub（`api.github.com` / `github.com` / `*.githubusercontent.com`），不携带 Pixiv `Referer`、Cookie 或语言/设备标识头；标签收藏与分享模板等本地偏好仅保存在本机私有存储。
- **开源协议内置展示**：应用内「设置 → 关于 → 开放源代码许可」提供所有运行时第三方组件（包括 `COUI-Kit`、`Kotlin / Coroutines / Serialization`、`Compose Multiplatform`、`AndroidX`、`Accompanist`、`OkHttp`、`Okio`、`Coil`、`JSpecify`、`Guava`、`JetBrains Annotations` 与 `Material Icons (Vectors)`）的许可信息及协议原文，完整说明亦可见根目录 [`NOTICE`](NOTICE)。

---

## 环境与源码构建

### 环境要求

- **JDK**：17 及以上（构建前确保 `JAVA_HOME` 指向有效 JDK 17+ 安装目录）
- **Android SDK**：Platform 37、Build-Tools 36

### 调试构建与校验

在仓库根目录执行：

```powershell
$env:JAVA_HOME = "<path-to-jdk17>"

# 1. 运行单元测试
.\gradlew.bat :data:testDebugUnitTest :app:testDebugUnitTest

# 2. 运行 Release Android Lint 静态检查与 Debug 构建
.\gradlew.bat :app:lintRelease :app:assembleDebug
```

> 首次构建需要联网下载 Gradle 9.8.0、AGP 与依赖缓存；依赖缓存就绪后可附加 `--offline` 进行离线构建。

---

## Release 签名与正式包构建

`app/build.gradle.kts` 在 `release` 变体中默认开启 R8 代码压缩/混淆（`isMinifyEnabled = true`）与资源裁剪（`isShrinkResources = true`），并通过以下两种方式读取正式签名配置（均已在 [`.gitignore`](.gitignore) 中忽略）：

### 方式一：通过 `signing.properties`（推荐用于本地发布）

1. 使用 JDK 自带的 `keytool` 生成签名密钥库：

```powershell
New-Item -ItemType Directory -Force -Path "signing" | Out-Null
keytool -genkeypair `
  -v `
  -storetype PKCS12 `
  -keystore "signing\huiguang-release.jks" `
  -alias huiguang `
  -keyalg RSA `
  -keysize 2048 `
  -validity 10000
```

2. 复制 [`signing.properties.example`](signing.properties.example) 为根目录 `signing.properties`，并填入密钥路径与密码（Windows 路径请使用正斜杠 `/`）：

```properties
KEYSTORE_FILE=signing/huiguang-release.jks
KEYSTORE_PASSWORD=<your-keystore-password>
KEYSTORE_ALIAS=huiguang
KEYSTORE_ALIAS_PASSWORD=<your-key-password>
```

### 方式二：通过环境变量（适合 CI 环境）

设置以下四个环境变量后直接执行构建即可（环境变量优先级高于 `signing.properties`）：
- `KEYSTORE_FILE`
- `KEYSTORE_PASSWORD`
- `KEYSTORE_ALIAS`
- `KEYSTORE_ALIAS_PASSWORD`

### 生成正式 APK

```powershell
$env:JAVA_HOME = "<path-to-jdk17>"
.\gradlew.bat :app:assembleRelease
```

输出位置：
- 已签名正式安装包：`app/build/outputs/apk/release/app-release.apk`（未配置签名时将产出未签名包 `app-release-unsigned.apk`，不会回退到调试签名）
- R8 混淆映射文件：`app/build/outputs/mapping/release/mapping.txt`

---

## 工程结构

```text
huiguang/
├── app/                         # 应用壳层、Compose 设计层与各功能页面（:app）
│   ├── src/main/assets/licenses # 内置开源协议与 COUI-Kit NOTICE 原文
│   ├── src/main/java/moe/huiguang/pixiv/
│   │   ├── design/              # COUI 适配封装、GlowSurface 等设计层组件
│   │   ├── nav/                 # Activity 路由与返回栈辅助逻辑
│   │   └── ui/                  # 首页 / 搜索 / 详情 / 查看器 / 阅读器 / 个人域 / 设置 / 关于
│   └── proguard-rules.pro       # Release R8 混淆保留与日志裁剪规则
├── data/                        # 网络客户端、HTML/JSON 解析器与本地加密/缓存存储（:data）
├── docs/
│   ├── design-contract.md       # 视觉基线与组件设计契约
│   └── data-layer.md            # 数据层接口说明与网络约定
├── LICENSE                      # Apache License 2.0 项目级开源协议
├── NOTICE                       # 版权声明、免责声明与第三方开源组件清单
├── signing.properties.example   # 本地 Release 签名配置模板
└── app/build.gradle.kts         # 应用版本、R8 优化与签名加载配置
```

---

## 许可与免责声明

- 本项目自有源代码基于 [Apache License 2.0](LICENSE) 开源发布。
- 本项目为非官方第三方 Pixiv 客户端，仅供个人学习与技术研究使用，使用前请确认并遵守 Pixiv 社区服务条款。
- 本应用界面基于 [COUI-Kit](https://github.com/ItosEO/COUI-Kit)（MPL-2.0）构建，相关第三方组件的版权、许可协议与上游声明见 [`NOTICE`](NOTICE) 及 [`app/src/main/assets/licenses/`](app/src/main/assets/licenses/)。
