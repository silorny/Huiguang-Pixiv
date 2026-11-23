# 绘光 · COUI 设计契约

本文档约定「绘光」Android 客户端的 UI 组件基线、视觉令牌、页面结构及交互规范，作为界面开发与视觉一致性的技术基准。

---

## 1. 依赖与环境基线

| 项 | 值 | 依据 |
| --- | --- | --- |
| COUI | `io.github.itoseo:coui:0.0.1-alpha15` | 原生 Compose COUI 组件与渲染库 |
| COUI 图标 | `io.github.itoseo:coui-icons:0.0.1-alpha15` | 系统配套线性与填充图标集 |
| 不用 | `coui-material3` | 纯 COUI 模式，不引入 Material 3 |
| AGP / Gradle | 9.4.1 / 9.8.0 | 库要求 AGP ≥ 9.1.0 |
| Kotlin / Compose 编译器 | 2.4.20 / 2.4.20 | 与库基线一致 |
| Compose BOM | `androidx.compose:compose-bom-alpha:2026.08.01` | 宿主显式对齐 |
| compileSdk / targetSdk | 37.1 / 37 | 库 `minCompileSdk = 37.1` |
| minSdk | **34** | 融合底栏、FAB、模糊顶栏及阴影特性均依赖 API 34+ |
| Java 字节码 | 17 | 编译目标版本 |
| 核心依赖 | OkHttp 5.5.0、Coil 3.6.3、coroutines 1.11.0、activity-compose 1.13.0、lifecycle 2.11.0 | — |

**版本升级原则**：COUI 处于 alpha 阶段，升级前需在 `design/` 适配层集中适配变更并完成界面回归验证。

---

## 2. 视觉令牌

### 2.1 颜色（来自 `CouiColor` / `CouiPresets`）

| 角色 | 日间 | 夜间 | 用途 |
| --- | --- | --- | --- |
| accent | `#0080FF` | `#1A8CFF` | 主按钮、选中态、链接、进度 |
| onAccent | `#FFFFFF` | `#FFFFFF` | 强调色上的内容 |
| background | `#F0F1F2` | `#000000` | 页面底色（`Scaffold.containerColor`） |
| surface | `#FFFFFF` | `#1E1E1E` | 组件表面 |
| container / card | `#FFFFFF` | `#333333` / `#1AFFFFFF` | 分组卡片 |
| onSurface | `#E6000000` | `#E6FFFFFF` | 主文字 |
| onSurfaceVariant | `#8A000000` | `#8AFFFFFF` | 次要文字、分组标题 |
| outlineVariant | `#1F000000` | `#33FFFFFF` | 分隔线、描边 |

品牌色接入统一集中在 `CouiTheme(colorScheme = ...)` 一处控制。

### 2.2 排版（`CouiTextKind`）

| kind | 字号 / 字重 | 用途 |
| --- | --- | --- |
| `HomeTitle` | 40 / 700 | 首页大标题展开态 |
| `TopBar` | 18 / 550 | 二级页顶栏标题 |
| `ItemTitle` | 16 / 500 | 列表行主文字、详情字段名 |
| `ItemSummary` | 13 / 400 | 行副文字、作者名、数据 |
| `ItemValue` | 14 / 400 | 行尾值 |
| `GroupTitle` | 12 / 500 | 分组标题 |
| `GroupFooter` | 12 / 400 | 分组脚注 |
| `Search` | 15 / 400 | 搜索输入与占位 |

> 小说阅读器正文采用 `BasicText` + 可调 `TextStyle`（字号 13–23sp、行距 1.6×–2.2×、`PlatformTextStyle(includeFontPadding = true)`），以支持用户自由排版定制。

### 2.3 尺寸与形状（`CouiPageDefaults`）

| 令牌 | 值 |
| --- | --- |
| 页面水平边距 | 16dp |
| 行内边距 | 16dp；行最小高 48dp |
| 图标 | 24dp，图标与文字间距 16dp |
| 分组圆角 | `CouiShape(24.dp, cornerWeight = 3f)` |
| 分组间距 | 16dp（有标题时顶部 12dp） |
| 顶栏高度 | 52dp |
| 分隔线 | 0.33dp（至少 1 物理像素） |
| 图片卡圆角 | `CachedCouiShape(20.dp / 18.dp, cornerWeight = 3f)` |

按钮、底栏、FAB 与胶囊组件均使用组件 Defaults 自带的 `CouiShape` / `CouiCapsuleShape`；瀑布流与列表媒体卡通过 `CachedCouiShape`（缓存单角曲率、轮廓与 `g2CornerMask` 四角遮罩）保留连续曲率。

---

## 3. 组件清单与调用约束

| 组件 | 包 | 关键调用/约束 |
| --- | --- | --- |
| `Scaffold` | `com.itos.coui.layout` | 为 `topBar`/`bottomBar`/`floatingActionButton` 自动供给内容 backdrop；正文使用 `padding(padding).consumeWindowInsets(padding)` |
| `Group` / `Item` / `Divider` / `Footer` | 同上 | `Item` 传 `onClick` 整行可点；开关行用 `toggleable`；`Group` 自带水平 16dp 留白 |
| `CouiPageDefaults.themeColors()` | 同上 | 获取 `background / card / title / summary / divider / accent` |
| `TopAppBar` / `TopAppBarSurface` | `com.itos.coui.topappbar` | 标题显式传入 `TextStyle`；支持背景渐进模糊采样 |
| `NavigationBar` | `com.itos.coui.navigation` | `items` 非空、`selectedIndex` 合法；无左侧附件 |
| `SegmentedControl` | `com.itos.coui.segmentbutton` | 放 `Group` 内走普通填色、不传 `backdrop`；悬浮于内容上时传入不透明兄弟 `backdrop` |
| `Switch` / `RadioButton` | `com.itos.coui.switch` 等 | 状态受控，整行点击由容器承载 |
| `Dialog` / `LoadingDialog` | `com.itos.coui.dialog` | 传入 `visible` 控制显隐，不使用 `if (visible)` 包裹以免切断退场动画 |
| `Menu` | `com.itos.coui.menu` | 窗口级采样配合 `ProvideCouiMenuBackdrop(isolateContent = false)` |
| `Text` | `com.itos.coui.text` | 使用预设 `CouiTextKind` |
| `CouiIcons.Filled/Outlined` | `com.itos.coui.icons` | 系统矢量图标库；缺失图标以 `res/drawable/ic_*.xml`（24dp 线性）补齐 |
| `rememberCouiBackdrop` / `Modifier.couiBackdrop` | `com.itos.coui.backdrop` | 自定义悬浮件须使用不透明兄弟背景源，严禁采样祖先节点造成渲染环；主壳层将顶栏 `topBackdrop` 与底栏 `bottomBackdrop` 拆分采样并与前景直出绘制解耦 |
| `couiVerticalScroll` | `com.itos.coui.scroll` | 统一列表/内容滚动与回弹手势 |
| `PrimaryButton` | `design/components` | 主操作 46dp 强调色胶囊按钮 |
| `ArtworkCard` | `design/components` | `CachedCouiShape`（20dp/14dp/8dp）平滑圆角媒体卡（瀑布流与横向列表共用） |
| `TagChip` | `design/components` | `CouiCapsuleShape` 标签跳转胶囊 |
| `GlowSurface` | `design/components` | 自定义凝光悬浮外壳（浮动材质 + 投影/caustic + 轮廓/跟手光 + 按压回弹；平坦底色启用 `staticSurface = true` 底图缓存） |
| `GlowChip` | `design/components` | 32dp 凝光状态胶囊 |
| `CircleIconButton` | `design/components` | 详情页与查看器顶栏悬浮圆钮 |
| `GlowStatCapsule` | `design/components` | 详情/作者/账号统计数据胶囊（无分割线） |
| `FloatingActionNavBar` | `design/components` | 详情页悬浮底栏：左侧三联功能胶囊 + 右侧独立圆形主操作悬浮按钮 |
| `ExpandableText` | `ui/common` | 可展开简介卡片（图片与小说详情共用） |
| `LinkedWorkRow` | `ui/common` | 简介站内链接提取的关联作品行 |
| `SeriesSection` | `ui/common` | 官方系列信息及上/下一话导航卡片 |

**工程约束**

1. 禁止引入 `androidx.compose.material3`、Material 图标或 `MaterialTheme`。
2. 禁止给 `Text(kind = …)` 传入自定义 `fontSize`。
3. 跨配置变更的状态统一采用 `rememberSaveable` 或 `ViewModel`。
4. Pixiv 图床请求须共用经 Referer 与主机边界加固的 `PixivApi.client`；非官方站外请求须隔离在独立无状态客户端中。

---

## 4. 页面结构规范

- **主外壳（`MainActivity`）**：包含「首页」「搜索」「我的」三个 Tab，由 `selectedIndex` + `SaveableStateHolder` 保存独立状态；二级页全部使用同一任务栈内的 `PageActivity`（Intent 路由，`finish()` 返回），由系统负责预测性返回与跨 Activity 转场动效。
- **首页（`HomeScreen`）**：悬浮收缩顶栏（「绘光」标题与「推荐 / 排行榜 / 关注新作」`SegmentedControl` 随滚动对齐过渡），排行榜顶部集成 Pixivision 横向特辑卡片；未登录回退为公开发现流与登录引导卡片。
- **搜索与结果（`SearchTab` / `SearchResultsScreen`）**：`SearchPill` 凝光搜索栏、关键词/标签模式切换、PID/链接解析直达、本地搜索历史管理；结果页支持全部/插画/漫画/小说/作者五分页及独立滚动位置保持。
- **插画 / 漫画详情与看图器（`DetailScreen` / `ViewerScreen`）**：封面多页浏览、数据统计胶囊、可展开简介、系列与关联作品、标签跳转及批量原图下载；全屏看图器支持纵向无缝滚动与左右翻页切换、双指缩放、双击放大与单张保存。
- **小说详情、系列与阅读器（`NovelDetailScreen` / `SeriesScreen` / `ReaderScreen`）**：标题上滑吸顶过渡、字数与时长估算、系列连读、TXT 导出；阅读器支持四套纸面主题、滚动/分页双模式、目录抽屉、内嵌插图查看大图、右侧边栏 Continuous slider 进度导轨及自动进度恢复。
- **个人域、设置与关于（`ProfileTab` / `AuthorScreen` / `HistoryScreen` / `BookmarksScreen` / `SettingsScreen` / `AboutScreen`）**：登录态本地 Keystore 加密存储与退出全量清理；R-18 展示开关、应用内语言实时切换（跟随系统 / 简中 / 繁中香港 / English）、主题切换（跟随系统 / 浅色 / 深色）、惯用左手适配、缓存清理及开源许可/隐私声明查看。

---

## 5. 交互与验收标准

- 点击反馈与触感复用 COUI `couiButtonFeedback`、边缘回弹采用 `couiVerticalScroll` / `rememberCouiOverscrollEffect`。
- 静态验收标准：
  - `app/` 下无任何 `androidx.compose.material3` / `MaterialTheme(` 残留。
  - `design/Tokens.kt` 之外不私改语义主题色令牌。
  - 旋转屏幕、切换 Tab 与后台恢复后，搜索词、页码堆栈与阅读进度不丢失。
  - 系统浅色 / 深色及 1.3× 字体缩放下无文本截断或组件重叠。
