# 数据层架构说明

`data` 为独立逻辑模块（`moe.huiguang.pixiv.data.*`），仅依赖 Kotlin、协程、OkHttp、Android `SharedPreferences` 与 `MediaStore`，不引用任何 Compose 或 UI 类型。

## 核心入口

| 类型 | 位置 | 说明 |
| --- | --- | --- |
| `PixivApi` | `net/PixivApi.kt` | Pixiv Web Ajax 客户端，支持注入 `baseUrl` 与 `cookieProvider` 以便测试与隔离 |
| `PixivException` | `net/PixivApi.kt` | 传输或服务端异常（资源不存在时返回 `null`） |
| `PixivisionParser` | `net/PixivisionParser.kt` | Pixivision 特辑列表与详情页 HTML 解析器 |
| `AppUpdateService` | `net/AppUpdateService.kt` | GitHub Releases 匿名版本检查与 APK 下载服务（仅限官方 GitHub 域名，零 Cookie/Referer） |
| `Session` / `SessionHolder` | `net/Session.kt` | 会话凭证与登录态持有器 |
| `EngagementRepository` | `repo/EngagementRepository.kt` | 收藏与关注状态双向同步仓库及事件流 |
| `SettingsStore` | `store/SettingsStore.kt` | 加密会话存储、R-18、语言、外观、自定义下载目录与分享格式偏好、历史记录管理 |
| `ReadingStore` | `store/ReadingStore.kt` | 小说阅读进度与书签持久化（最多保留 60 部） |
| `FavoriteStore` | `store/FavoriteStore.kt` | 本地与云端收藏 / 关注状态辅助存储 |
| `HomeFeedCache` | `store/HomeFeedCache.kt` | 首页离线首屏缓存（按账号隔离） |
| `KeyValueStore` | `store/KeyValueStore.kt` | 键值存储抽象（生产实现支持注入加密委托，测试使用内存实现） |
| `ImageDownloader` | `media/ImageDownloader.kt` | 原图与小说 TXT 导出（默认写入系统 `Download` 目录，或经 SAF 写入用户自选目录） |
| `ShareFormat` | `model/ShareFormat.kt` | 作品与作者自定义分享文本模板及变量渲染器 |
| `SearchIntentParser` | `search/SearchIntent.kt` | PID、Pixiv 链接、作者链接与关键词解析分流器 |
| `HomeFeed` | `model/Feed.kt` | 首页推荐、榜单与关注流数据模型 |
| `NovelBlocks` | `model/NovelContent.kt` | 小说正文语法切块与内嵌插图提取 |

## 首页推荐接口聚合机制

已登录状态下 `GET /ajax/top/illust` 返回体中同时包含作品索引与分区 ID 引用：

| 字段 | 内容 |
| --- | --- |
| `body.thumbnails.illust` / `novel` | 作品与小说实体字典 |
| `page.recommend` | 推荐作品 ID 列表与推荐元数据 |
| `page.ranking.items` | 榜单排序与 ID 列表 |
| `page.follow` / `page.newPost` | 关注作者最新作品 ID 列表 |
| `page.tags` / `page.trendingTags` | 精选标签与趋势标签列表 |

解析时优先构建 `thumbnails` 的 `id → SearchItem` 映射缓存，再按各分区 ID 顺序装配模型，以单次请求完成首页首屏聚合。

## 网络传输约定

- **图床防盗链**：对 `pixiv.net` 与 `pximg.net` 图片及 API 请求统一附加 `Referer: https://www.pixiv.net/`。
- **Cookie 域名边界**：仅向 Pixiv / Pixivision 官方 HTTPS 目标主域附加会话 Cookie，重定向至第三方域名时剥离凭证。
- **容错重试**：对服务端 5xx 响应及偶发 `IOException` 执行一次自动重试（间隔 700ms）。
- **超时策略**：连接 20s、读取 60s、单次完整请求 120s（适配大型小说正文响应）。

## 离线单元测试

```powershell
.\gradlew.bat :data:testDebugUnitTest
```

测试套件使用 JDK 内置 `com.sun.net.httpserver` 在本地环回地址验证真实 OkHttp 请求链、JSON/HTML 解析、CSRF/Cookie 边界、重试机制与存储读写，无需外网即可离线执行。
