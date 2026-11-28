# 数据层（P2）

`data` 是纯逻辑模块：只依赖 Kotlin、协程、OkHttp 与 Android 的 `SharedPreferences` / `MediaStore`，不引用任何 Compose 或 UI 类型。

## 公开入口

| 类型 | 位置 | 说明 |
| --- | --- | --- |
| `PixivApi` | `net/PixivApi.kt` | Web Ajax 客户端。构造参数 `baseUrl` 与 `cookieProvider` 都可注入，替换掉旧项目的全局 `object` |
| `PixivException` | `net/PixivApi.kt` | 传输 / 服务端失败（与「不存在」区分：不存在返回 `null`） |
| `Session` / `SessionHolder` | `net/Session.kt` | 登录态；`SessionHolder` 是拦截器的默认 cookie 来源 |
| `SettingsStore` | `store/SettingsStore.kt` | 登录态、R-18、语言、历史；暴露 `StateFlow<SettingsState>` |
| `ReadingStore` | `store/ReadingStore.kt` | 小说阅读进度与书签，最多保留 60 部 |
| `KeyValueStore` | `store/KeyValueStore.kt` | 存储抽象；`SharedPreferencesStore` 生产用，`InMemoryKeyValueStore` 测试用 |
| `ImageDownloader` | `media/ImageDownloader.kt` | 原图 / TXT 写入系统 Download 目录（走 MediaStore，无需权限） |
| `SearchIntentParser` | `search/SearchIntent.kt` | PID、pixiv 链接、关键词三路分流 |
| `HomeFeed` / `homeFeed()` | `model/Feed.kt`、`net/PixivApi.kt` | 登录态首页推荐；`fallbackFeed()` 是未登录的精选标签兜底 |
| `NovelBlocks` | `model/NovelContent.kt` | 小说正文切块：段落 / `[uploadedimage:ID]` / 1000 字分片，纯函数可测 |

## 首页推荐的真实形状（P4 真机抓包）

`GET /ajax/top/illust?mode=all&lang=zh` 未登录返回 **400**，登录后返回约 1.5 MB：

| 字段 | 内容 |
| --- | --- |
| `body.thumbnails.illust` | 604 条作品对象（id / title / illustType / url / tags / userId / userName / 宽高 / pageCount / xRestrict） |
| `body.thumbnails.novel` | 40 条小说对象 |
| `page.recommend` | `{ ids: [18], details: { id: {methods, score, seedIllustIds} } }`——**details 里只有推荐理由，没有作品字段** |
| `page.ranking.items` | 100 条 `{rank, id}`，作品实体同样在 thumbnails 里 |
| `page.follow` / `page.newPost` | id 数组 |
| `page.tags` / `page.trendingTags` | 53 / 23 个 `{tag, ids}`，用作分类胶囊 |

所以解析顺序是：先把 `thumbnails.illust` 建成 `id → SearchItem` 缓存，再按各 section 的 id 列表解析。整个首页仍然是**一次请求**。

## 与旧项目（PixivQuickLook）的差异

1. **`PixivApi` 从 `object` 改成可构造的类**：`baseUrl` 与 `cookieProvider` 可注入，测试能指向本地 HTTP 服务；生产侧由 `AppContainer` 持有一个实例。
2. **存储抽象成 `KeyValueStore`**：`AppStore` 拆为 `SettingsStore`，不再持有 `Context` 单例；`ReadingStore` 同样去单例、去 `Context`。
3. **`ImageDownloader` 去掉 API 29 以下分支**：`minSdk 34`，统一走 `MediaStore.Downloads`，不需要 `WRITE_EXTERNAL_STORAGE`。
4. **`currentUserId` 兼容三种返回形状**（`userData` / `body.userData` / `body`）：旧代码只认第一种，登录校验一直没在真机跑通过。
5. **新增 `SearchIntentParser`**：旧原生项目只认纯数字，链接解析是这次新写的（规则见类注释）。
6. 包名统一为 `moe.huiguang.pixiv.data.*`，文件按 `net / model / store / media / search` 分层。

## 传输约定（沿用并已验证）

- 所有请求带 `Referer: https://www.pixiv.net/`——`i.pximg.net` 只认这个来源，否则 403。
- 登录后附加 `Cookie`；显式传入的 cookie 优先于 `SessionHolder`。
- 5xx 自动重试一次（间隔 700ms）：pixiv 偶发 503，重试即成功。
- 超时：连接 20s、读取 60s、整通 120s（约 1MB 的小说 JSON 曾经踩过 20s 线）。

## 测试

```powershell
.\gradlew.bat :data:testDebugUnitTest
```

36 项，全部离线：
| 测试类 | 覆盖 |
| --- | --- |
| `PixivApiHttpTest`(9) | 404→`null`、插画字段与系列、503 重试一次、Referer、Cookie 透传、小说内嵌图、搜索合并两类结果、未登录收藏降级为空、登录用户 id |
| `HomeFeedTest`(2) | 未登录 400→`null`；推荐 / 排行 / 关注 id 经 thumbnails 缓存解析，分类与去裁剪正确 |
| `SearchIntentTest`(12) | 纯数字、artworks / illusts / i / illust_id、novel 两种链接、users / u / member、纯文本、空白、无关链接 |
| `StoreTest`(10) | 设置持久化、登出、历史去重与 60 上限、同号插画/小说、繁中回退、阅读进度与书签、60 条裁剪 |
| `UrlTest`(3) | 去裁剪、已是原图、文件名清洗 |

HTTP 测试用 JDK 自带 `com.sun.net.httpserver`，不依赖 MockWebServer，也不需要联网。

## 已知限制

- `search()` 一次只取一页（`page` 参数已预留），聚合总数没有从接口读，UI 分页在 P4 处理。
- 系列列表仍靠 `prev`/`next` 走链（pixiv 没有列表接口），`cap` 默认 20。
- 解析用的 JSON 夹具按真实响应裁剪而来；完整真实数据将在 P4 接页面时于真机回归。
