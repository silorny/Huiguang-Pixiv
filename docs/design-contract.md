# 绘光 · COUI 设计契约（P0）

> 状态：P0 交付物，待验收。后续所有 UI 代码以本文件为准；改动先改这里，再改代码。
> 参考实现：`refs/coui-kit`（只读克隆，`git clone --depth 1`，对应 0.0.1-alpha22）。

---

## 1. 版本矩阵（锁定，不随库升级自动漂移）

| 项 | 值 | 依据 |
| --- | --- | --- |
| COUI | `com.github.itoseo.coui-kit:coui:0.0.1-alpha22` | JitPack release |
| COUI 图标 | `com.github.itoseo.coui-kit:coui-icons:0.0.1-alpha22` | 同版本发布 |
| 不用 | `coui-material3` | 纯 COUI，不引 Material 3 |
| AGP / Gradle | 9.4.1 / 9.8.0 | 库要求 AGP ≥ 9.1.0 |
| Kotlin / Compose 编译器 | 2.4.20 / 2.4.20 | 与库基线一致 |
| Compose BOM | `androidx.compose:compose-bom-alpha:2026.08.01` | 宿主基线，COUI-Kit 传递对齐 compose 1.13.0 |
| foundation / runtime | 1.13.0 | 随 COUI-Kit 对齐 |
| compileSdk / targetSdk | 37.1 / 37 | 库 `minCompileSdk = 37.1` |
| minSdk | **34** | 底栏、FAB、顶栏按钮、ChipTab、普通阴影都要求 34 |
| Java 字节码 | 17 | 库配置 |
| 其它 | OkHttp 5.5.0、Coil 3.6.3、coroutines 1.11.0、activity-compose 1.13.0、lifecycle 2.11.0 | 沿用已验证组合 |

**版本升级规则**：COUI 处于 alpha，公开 API 会破坏性变更。升级时必须先读 `CHANGELOG.md`，在 `design/` 层一次性改完，再跑全量验收；不做「顺手升级」。

---

## 2. 令牌

### 2.1 颜色（来自 `CouiColor` / `CouiPresets`，不自行发明）

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

品牌接入只允许一处：`CouiTheme(colorScheme = preset.copy(accent = brandAccent))`。v0.1 保持 COUI 蓝；「品牌色切换」放 P8，候选：COUI 蓝 `#0080FF` / Pixiv 蓝 `#0096FA` / 樱粉 `#F2789F`。

### 2.2 排版（`CouiTextKind`，不是自定义 TextStyle）

| kind | 字号 / 字重 | 用在哪 |
| --- | --- | --- |
| `HomeTitle` | 40 / 700 | 首页大标题展开态 |
| `TopBar` | 18 / 550 | 二级页顶栏标题 |
| `ItemTitle` | 16 / 500 | 列表行主文字、详情字段名 |
| `ItemSummary` | 13 / 400 | 行副文字、作者名、数据 |
| `ItemValue` | 14 / 400 | 行尾值 |
| `GroupTitle` | 12 / 500 | 分组标题 |
| `GroupFooter` | 12 / 400 | 分组脚注 |
| `Search` | 15 / 400 | 搜索输入与占位 |

> 正文阅读（小说）是**例外**：阅读器用 `BasicText` + 自定义 `TextStyle`（字号 13–23 可调、行高 1.9、`PlatformTextStyle(includeFontPadding = true)`），因为 `CouiTextKind` 的字号是固定的。

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
| 图片卡圆角 | `RoundedCornerShape(18.dp)`（高频滚动卡片/缩略图/角标使用硬件 `Outline.Rounded`，避免 Skia `Threaded SW Clip Mask Render`） |

**不自造圆角体系**：按钮、底栏、FAB、搜索胶囊全部用组件 Defaults 自带的 `CouiShape` / `CouiCapsuleShape`。

---

## 3. 组件清单与调用约束

| 组件 | 包 | 关键调用/约束 |
| --- | --- | --- |
| `Scaffold` | `com.itos.coui.layout` | 自动录制正文源并提供给 `topBar`/`bottomBar`/`floatingActionButton`；正文要 `padding(padding).consumeWindowInsets(padding)`；含输入框的页面在外层 `imePadding()` |
| `Group` / `Item` / `Divider` / `Footer` | 同上 | `Item` 有 `onClick` 才整行可点；整行开关用 `toggleable` 且**不传** `onClick`；`Group` 自带水平 16dp 留白，外部 `padding` 是**累加** |
| `CouiPageDefaults.themeColors()` | 同上 | 取 `background / card / title / summary / divider / accent`；`Group(colors=…)` 只管卡片，不管内容槽 |
| `TopAppBar` / `LargeTopAppBar` | `com.itos.coui.topappbar` | 放 Scaffold 槽位可省 `backdrop`；标题用 `BasicText(title, style = LocalCouiTextStyle.current)`；按钮需 API 34（已满足） |
| `CollapsingTopAppBar` | 同上 | 配合 `rememberCouiCollapsingAppBarState` + `nestedScroll` + `state.contentPadding(padding, density)` |
| `TopAppBarButton` / `TopAppBarButtonGroup` | 同上 | 图标按钮默认；`text = true` 文字按钮；显隐用 `TopAppBarActionVisibility` |
| `NavigationBar` | `com.itos.coui.navigation` | `items` 不能为空、`selectedIndex` 必须合法；`selectedIcon` 省略即复用 `icon`；左侧附件可选，首页不放附件 |
| `Fab` | `com.itos.coui.floatingactionbutton` | 必须传 `onClick`；默认圆形，圆角方形用 `CouiFabDefaults.roundedSquareShape` |
| `SearchBar` | `com.itos.coui.search` | `rememberCouiSearchBarState(expanded)` 位置要稳定；自身 `BackHandler`；API 34+ 才有模糊材质 |
| `SegmentedControl` | `com.itos.coui.segmentbutton` | 放 `Group` 内走普通填色、**不传** `backdrop`；放页面底色上要传 `backdrop` |
| `ChipTabRow` / `CouiChipTabItem` | `com.itos.coui.chip` | key 唯一且稳定；`selectedKey` 必须属于列表；需 API 34 |
| `Switch` / `Checkbox` / `RadioButton` | `com.itos.coui.switch` 等 | 受控；整行点击交给 Item，控件自身不要再装一次点击 |
| `Slider` | `com.itos.coui.slider` | 用于阅读进度、字号等连续值 |
| `Dialog` / `InputDialog` / `LoadingDialog` | `com.itos.coui.dialog` | 传 `visible` 时**不要**用 `if (visible)` 包裹（会立刻销毁退场动画） |
| `Menu` | `com.itos.coui.menu` | 更多操作、语言选择；窗口源按需 `ProvideCouiMenuBackdrop(isolateContent = false)` |
| `Text` / `rememberCouiTextStyle` | `com.itos.coui.text` | `Text(text, kind = …)`；`kind` 必填，不要传自定义字号 |
| `CouiIcons.Filled/Outlined` | `com.itos.coui.icons` | 只有 ColorOS 系统图标（68 个）。**缺** `Home`/`Heart`/`Download`/`Bookmark`/`Share`/`Refresh`/`Trash`，这 7 个自绘成 `res/drawable/ic_*.xml`，规格 24dp 线性、`?attr` 染色 |
| `rememberCouiBackdrop` / `Modifier.couiBackdrop` | `com.itos.coui.backdrop` | 只有「正文自己需要模糊」或「第三方容器」时才手动建源；Scaffold 页面不需要 |
| `couiVerticalScroll` | `com.itos.coui.scroll` | 列表滚动统一用它拿 COUI 的回弹与惯性 |
| `CouiShape` / `CouiCapsuleShape` | `com.itos.coui.shape` / `design/shape` | 媒体卡与自定义容器用（列表/瀑布流优先使用缓存单角曲率的 `CachedCouiShape`） |
| `PrimaryButton`（本项目补） | `design/components` | COUI 没有通用按钮，主操作（开始阅读 / 登录）用 46dp 强调色胶囊；无阴影、白字 |
| `ArtworkCard`（本项目补） | `design/components` | 媒体卡：20dp/14dp/8dp `CachedCouiShape` 平滑圆角、卡片底、标题两行、作者一行、R-18 / 小说角标；瀑布流与横向行共用 |
| `TagChip`（本项目补） | `design/components` | 标签快捷胶囊（卡片底色 + `CouiCapsuleShape` + 按下缩放）；`ChipTabRow` 是单选控件，不适合「点标签去搜索」 |
| `GlowSurface`（本项目补） | `design/components` | 自定义悬浮件的凝光外壳：COUI 浮动材质 + 投影/caustic + 轮廓/跟手光 + 触摸/按压回弹；`backdrop` 必须是**不透明兄弟源**，平坦底色场景可传 `staticSurface = true` 冻结底图；深浅效果用 `isDarkBackdrop()` 按所在背景亮度选 |
| `GlowChip`（本项目补） | `design/components` | 32dp 凝光胶囊；内部走 `GlowSurface`（`SpotlightPreset.ChipTab`，未选中白磨砂、选中着强调色，平坦底色启用 `staticSurface = true`） |
| `CircleIconButton`（本项目补） | `design/components` | 媒体页顶栏圆钮，内部走 `GlowSurface`，采样页面底色以免随封面变色 |
| `GlowStatCapsule`（本项目补） | `design/components` | 详情/作者/账号数据胶囊：白色磨砂浮动表面 + 投影/caustic + 轮廓，**无分割线**；小说分「字数/时长」「收藏/浏览/按赞」两段 |
| `FloatingActionNavBar`（本项目补） | `design/components` | 详情页悬浮底栏：基于 `CouiFloatingRenderer.pair()` + `couiFloatingPair`，左侧胶囊 3 个连续触发项（图标+文字，无 tab 选中态，收藏触发态切换实心/空心爱心）+ 右侧独立蓝色圆形悬浮主按钮（仅图标） |
| `ExpandableText`（本项目补） | `ui/common` | 4 行简介 + 展开/收起，图片与小说详情共用 |
| `LinkedWorkRow`（本项目补） | `ui/common` | 简介提取的关联作品卡：类型块 + 锚点标签 + 类型/ PID + 跳转 |
| `SeriesSection`（本项目补） | `ui/common` | 官方系列块：系列名 / 第 N 话 / 上一篇 / 下一篇 / 查看系列 |

**明确禁止**

1. 引入 `androidx.compose.material3`、Material 图标、`MaterialTheme`。验收会 grep。
2. 在 Scaffold 的 `content` 里直接放 `ChipTabRow` / `NavigationBar` / `SearchBar`（正文不提供采样源，会崩或材质异常）；正文内需要时自建独立源。
3. 给 `Text(kind = …)` 传 `fontSize`（它不接受，别绕）。
4. 自行改写 COUI 的模糊、阴影、轮廓参数（算法参数归库）。
5. 用 `remember { mutableStateOf }` 保存跨旋转状态（一律 `rememberSaveable` 或 ViewModel）。
6. 在 `:app` 里直接 `new OkHttpClient()` 或另建图片加载器（必须共用 `PixivApi.client`，Referer 是图床的硬要求）。

---

## 4. 页面蓝图（15 页）

### 4.1 外壳
- `NavigationBar` 三项：**首页**（`CouiIcons.Filled.Album`）、**搜索**（`CouiIcons.Outlined.Search`）、**我的**（`CouiIcons.Filled.UserCircle`，选中态 `Outlined.UserCircle` 缺失时复用同一图形）；不放左侧附件，亮/暗切换只在设置页。
- 外壳是单个 `MainActivity`：三个 Tab 由 `selectedIndex` 切换，各自用 `SaveableStateHolder` 保留输入与滚动；二级页是同一任务内的真实 Activity（`PageActivity`，标准 Intent 启动、`finish()` 退出），返回栈由系统任务栈持有，返回手势走 ROM 的预测性返回。
- 页面底色统一 `colors.background`，卡片 `colors.card`。

### 4.2 首页
`Scaffold` + 悬浮头。头部为**一行**：左侧「绘光」（20sp）+ 右侧栏目 `SegmentedControl`（推荐 / 排行榜 / 关注新作）；第二行是作品分类 `SegmentedControl`（全部 / 插画 / 漫画 / 小说）。
首屏并行请求 `/ajax/top/illust?mode=all` 与 `/ajax/top/novel?mode=all` 并按 3:1 混排装配「全部」混合流；触底自动翻页加载更多（推荐走 `/ajax/illust/discovery` + `/ajax/novel/discovery`，排行榜走 `/ranking.php?format=json` + 小说发现，关注新作走 `/ajax/follow_latest/{illust|novel}`），实现插画/漫画/小说无缝瀑布流。
两个 `SegmentedControl` 都悬浮在网格之上，采样网格 backdrop 的透明模糊；分类筛选同时作用于三个栏目，12 个「栏目 × 分类」组合各自保留滚动位置。向下滚动时分类控件向上滑出并折叠隐藏，回到顶部恢复；栏目控件与标题保留在悬浮头内。
「关注新作」的小说段优先使用 `page.newNovels`；Pixiv 未返回时用小说榜单兜底，并在网格顶部注明「Pixiv 暂无小说关注流，以下为小说榜单」；若榜单也为空才显示「关注的小说作者暂无新作」。
未登录或接口 400 时降级为公开发现流与精选标签聚合，并在顶部给一条「登录后按你的口味推荐」的 `Item`。

> 标签胶囊用自绘 `TagChip`，**不用** `ChipTabRow`：后者是单选控件，重复点当前项不会再触发回调，拿来当「点标签去搜索」的快捷入口会失灵。视觉保持 32dp 胶囊 + 卡片底色。

### 4.3 搜索
`Scaffold` + 悬浮头 + 常驻搜索胶囊（`SearchPill`，`GlowSurface` 凝光外壳）+ 关键词 / 标签 `GlowChip` + 历史 `Group`（可清空）。
输入 300ms 防抖；纯数字 → 「PID 直达」卡；pixiv 链接 → 「打开作品」卡；其余 → 回车进结果页。

### 4.4 搜索结果
`TopAppBar`（返回 + 关键词）+ `SegmentedControl`（全部 / 插画 / 漫画 / 小说 / 作者，放 `Group` 内普通填色）+ 双列 `LazyVerticalStaggeredGrid`。
卡片：封面 + 标题（`ItemSummary` 两行省略）+ 作者；R-18 角标；触底加载下一页；切换分类保留各自滚动位置。

### 4.5 插画 / 漫画详情
`TopAppBarSurface`（封面模糊、返回、右上角 `…` 玻璃按钮展开 COUI `Menu` 含收藏/下载/浏览器打开/复制链接）+ 正文：封面 `HorizontalPager` + `GlowSurface` 页码胶囊 → 类型/PID → 标题 → 作者行 → `GlowStatCapsule`（浏览 / 收藏 / 点赞）→ 简介（可展开）→ 官方系列 → 简介关联作品 → 标签。
封面从状态栏 + 52dp 顶栏之下开始，不压住悬浮按钮。底部操作区替换为 `FloatingActionNavBar`：左侧悬浮胶囊含「下载」「收藏（空心/实心触发态）」「浏览器打开」3 个连续功能（图标+文字），右侧独立蓝色圆形悬浮按钮为「查看大图」（仅图标）。

### 4.6 大图查看器
全屏随主题底色（亮色模式白底、暗色模式黑底，状态栏图标亮度同步）；单图居中展示，多图默认**纵向无缝**（`LazyColumn` 顶部预留悬浮顶栏安全间距，滚入顶栏下方呈现模糊效果），顶栏最右侧按钮（下载按钮右侧）切换左右翻页；双击/双指缩放；缩放时暂停翻页/滚动。
顶部采用 `TopAppBarSurface` 凝光渐变模糊顶栏（去除黑色半透明遮罩），返回 / 页码胶囊 / 下载 / 模式切换统一用 `GlowSurface`（按当前画布亮暗切换玻璃质感）。

### 4.7 小说详情
悬浮顶栏：`TopAppBarSurface` + 白色玻璃返回钮与右上角 `…` 玻璃菜单钮（COUI `Menu` 含收藏/导出 TXT/浏览器打开/复制链接）；初始状态顶栏不显示标题，随页面向上滚动封面右侧标题平滑上移、长标题经单行省略过渡后无缝吸顶至顶栏，回滚顶部时平滑退回封面右侧。正文：封面（120×170，与标题顶部对齐）+ 标题 → 「小说 · PID」（封面下方）→ 作者行（头像 + 名字 + UID，可点进作者页）→ 两段 `GlowStatCapsule`（字数 / 时长；收藏 / 浏览 / 按赞）→ 简介（`ExpandableText`，4 行）→ 官方系列（系列名 / 第 N 话 / 上一篇 / 下一篇 / 查看系列，上下篇用 `WorkRow` 同款缩略图行）→ 简介关联作品（`WorkRow` 同款缩略图行，按系列 PID 去重）→ 标签（无白卡，`FlowRow` 换行）。底部操作区替换为 `FloatingActionNavBar`：左侧悬浮胶囊含「导出」「收藏（空心/实心触发态）」「浏览器打开」3 个连续功能（图标+文字），右侧独立蓝色圆形悬浮按钮为「阅读」（仅图标）。收藏写本机 `FavoriteStore`（`kind = "novel"`）；小说系列列表走 `/ajax/novel/series_content/{id}` 一次取回（30 条/页，失败回退 prev/next）。

### 4.8 小说阅读器
全屏、`CouiTheme` 之外再套一层纸面底色（支持 `PaperTheme`：跟随系统 / 象牙纸 / 护眼绿 / 暗夜，保持屏幕常亮与状态栏图标亮度同步）；支持**上下滚动**与**左右翻页**（`HorizontalPager` + `TextMeasurer` 行级分页与 `[newpage]` 断页，左右边缘点击与滑动翻页）双模式切换并持久化。正文 `SelectionContainer` + `BasicText`，支持 `[chapter:]`、`[newpage]`、`[uploadedimage:]`/`[pixivimage:]` 与 `[[rb:]]` 注音解析；点击小说附图打开与大图查看器一致的 `ImageViewerContent`（支持双指缩放、双击放大、保存原图与多附图切换）。顶部（`TopAppBarSurface` + 凝光标题胶囊 + 书签胶囊 + 右上角 `…` 玻璃按钮展开 COUI `Menu`，含蓝色当前模式指示项「翻页阅读/滚动阅读」、触发项「导出」与「排版」）与底栏（`GlowSurface` + `couiPressMotion`/`couiToolbarFeedback`/跟手光）采样正文内容提供实时模糊与完整凝光视效，单击正文平滑显隐。
右侧边缘常规滑动仅显示加长的 `4dp × 360dp` 凝光极细指示条（不遮挡正文）；仅当手指直接按住并拖拽或长按右侧边缘指示条（`touchSlop × 2` 或 `200ms`）时，以当前进度为锚点无缝衔接手指滑动展开为 `440dp` 高 COUI Continuous slider 样式凝光悬浮导轨（胶囊轨道 + 白色滑块与软阴影 + 跟手光 + 插图锚点 + 百分比），松手保留 1.8s 缓冲期后自动收起。
排版 `Dialog`（常驻组合 `visible` + 窗口模糊材质）：纸面主题、字号 13–23、行距 1.6×/1.9×/2.2×、字重四档、首行缩进开关，改完即时生效并持久化。退出/滚动防抖自动保存进度。

### 4.9 系列
`TopAppBar` + `WorkRow` 列表（与历史 / 收藏同款：缩略图 + 标题 + 作者 + 「第 N 话 · 类型 · PID」，当前话标题用强调色）；点击跳转；20 话后给「继续加载」。

### 4.10 作者页
悬浮顶栏（`TopAppBarSurface` + `CircleIconButton` 玻璃返回钮，不裁剪下方内容）；初始状态顶栏不显示作者名，个人资料区显示头像、作者名与 UID；随页面向上滚动，个人资料区作者名与 UID 平滑上移交接至顶栏吸顶显示，向下回滚至顶部时反向交接回个人资料区。下方为简介 + `GlowStatCapsule`（关注 / 好友）+ `SegmentedControl`（插画 / 漫画 / 小说）+ 瀑布流网格；插画/漫画走 `/profile/all` + 详情，小说走 `/profile/novels?ids[]=…` 批量封面；切换分类保留各自滚动位置。

### 4.11 我的
`Scaffold` + `LargeTopAppBar`；未登录显示 `Group` + 主按钮「登录」；登录后 `Group`（头像 / 昵称 / UID）+ `GlowStatCapsule`（关注 / 好 p 友 / 作品）+ 三个入口 `Group`（历史 / 收藏 / 设置）。

### 4.12 设置
全 `Group`/`Item` 的设置页：账号、内容（R-18 `Switch` + 需登录的脚注）、语言（简中 / 繁中香港，`RadioButton`）、外观（跟随系统 / 浅色 / 深色，`RadioButton`）、交互（「惯用左手」`Switch`，开启后将图片与小说详情页悬浮底栏的独立蓝色主按钮移至左侧）、存储（清缓存）、关于。外观与交互偏好即时生效并持久化；底栏不再放日夜附件。

### 4.13 历史 / 收藏
`TopAppBar` + `Group` 行（类型胶囊 + 标题 + 作者）；删除走**长按 → `Dialog` 确认**（库只提供 `CouiSwipeRevealDefaults` 常量，没有现成滑动组件；滑动删除放 P8 可选，用 `AnchoredDraggable` + 该预设）；收藏分「插画 / 小说」`SegmentedControl`。

### 4.14 登录
`WebView`（pixiv 登录页）+ 顶部说明卡；成功后 `LoadingDialog` → 校验 `/ajax/user/self` → 关闭；失败 `Dialog` 给原因（无网络 / 未完成登录）。

### 4.15 关于
`Group` 行：版本、开源许可（COUI 为 MPL-2.0，`NOTICE` 必须保留）、隐私说明、项目主页。

---

## 5. 交互与动效口径

- 点击反馈统一用 COUI 自带的 `couiButtonFeedback`（`Item` 已内置）；自定义可点容器必须接同一套反馈 + 触感。
- 触感：轻点击 `TextHandleMove`，确认/收藏 `LongPress`，错误 `Reject`。不自造震动节奏。
- 页面转场：二级页统一交给 ROM 的 Activity 转场（不覆盖 `windowAnimationStyle`、不注册根级返回回调、不自绘 `AnimatedContent`）；Tab 切换瞬时换页，只保留 `NavigationBar` 自身的按压/拖拽反馈；页内分段控件仍用 `SlideSwitcher`。
- 悬浮件：搜索胶囊、模式胶囊、详情/大图圆钮与页码胶囊统一用 `GlowSurface`（浮动材质 + 投影/caustic + 轮廓/跟手光 + 触摸/按压回弹）；采样源必须是不透明的**兄弟** backdrop，深浅效果按所在背景亮度（`isDarkBackdrop`）选择，不跟随应用主题。
- 滚动：`LazyColumn` 用 `couiVerticalScroll` 的 fling 行为；不做自定义 overscroll。
- 加载：首屏骨架（3–6 个占位卡，灰阶 `outlineVariant`），翻页用底部 `LoadingDialog` 之外的轻量转圈。

---

## 6. 验收口径

| 检查 | 方式 |
| --- | --- |
| 无 Material 残留 | `grep -rn "androidx.compose.material3\|MaterialTheme(" app/` 为空 |
| 令牌未私改 | `design/Tokens.kt` 之外不出现 `Color(0xFF…` 的语义色（媒体卡描边除外） |
| 源装配正确 | 顶栏/底栏/FAB 在 Scaffold 槽位内；自定义悬浮件的 backdrop 必须是不透明兄弟源，不得挂在消费者的祖先上 |
| 状态可恢复 | 旋转 / 进程重建后：搜索词、滚动位置、Activity 返回栈、阅读进度都在 |
| 深浅色 | 每页截图两张，文字对比度 ≥ 4.5:1（正文） |
| 字号 1.3× | 无截断、无重叠，`Item` 行高自适应 |

---

## 7. P1 起手清单（按此开工）

1. `huiguang/` 建 Gradle 工程：`settings.gradle.kts`（`:app`、`:data`）、`libs.versions.toml`（本文件第 1 节）、`gradle.properties`、wrapper 9.8.0。
2. `:app` 的 `build.gradle.kts`：`com.android.application` + `kotlin-compose` 插件，`compileSdk 37`、`minSdk 34`、`namespace moe.huiguang.pixiv`，依赖 `coui` / `coui-icons` / Compose BOM / Coil / OkHttp。
3. `App.kt` 里 `CouiTheme(darkTheme = settings.dark) { … }`，不调 `Coui.initialize`（那是 M3 适配用的）。
4. 组件画廊页（Debug 入口）：把第 3 节每个组件各放一个真实用例，作为视觉回归基准。
5. 首次构建需联网下载依赖（沙箱内需提权一次），之后 `--offline` 可复现。

---

## 8. 已确认（2026-11-16）

1. 视觉基调按本契约执行：COUI 蓝 + 大圆角分组 + 模糊顶栏/底栏。
2. 首页改为**登录账户的推荐**（`/ajax/top/illust` + `/ajax/top/novel`），精选标签只作未登录兜底。
3. `minSdk 34` 确认，不为 Android 12/13 做降级分支。

---

## 9. P1 实现备注（真机 PLK110 / Android 17 实测）

### 9.1 `LargeTopAppBar` 在 native 模式下不可用

- 现象：alpha11 + 纯 COUI（未接 M3）时，大标题会**重复绘制**（40sp 与缩小后两份叠在一起）；滚动收起后标题变成一团模糊残影，顶栏右侧还出现蓝色光斑。
- 对照：`TopAppBar`（18sp 普通顶栏）完全正常，渐进模糊与标题清晰度都对。
- 处理：外壳暂用 `TopAppBar`；大标题在 P4 单独立项——先试 `CollapsingTopAppBar`，再不行就按 `CouiTopAppBarDefaults` 自绘大标题，不阻塞功能。
- 另注：`BasicText` 不传 `style` 时标题不可见（槽位没有默认 `LocalCouiTextStyle`），必须显式传。

### 9.2 组件实测结论

| 组件 | 结论 |
| --- | --- |
| `Scaffold` + `TopAppBar` + `NavigationBar` | 正常；底栏融合材质、选中态、顶栏模糊均生效 |
| `ChipTabRow`（正文内） | 需要自建 sibling 背景源，按文档示例装配后正常 |
| `SegmentedControl`（Group 内） | 正常，普通填色无需 backdrop |
| `Switch` / `RadioButton` / `Slider` / `Group` / `Item` / `Divider` | 全部正常 |
| `Dialog`（底部） | 正常，遮罩与按钮预设符合预期 |
| `SearchBar`（常驻） | 正常，收起态胶囊与点击展开可用 |

### 9.3 构建事实

- 首次构建耗时约 9.5 分钟（下载 Gradle 9.8 + 依赖）；后续增量 5–10 秒。
- `--offline` 在 `aapt2` 首次下载前会失败一次，缓存后可离线。
- 安装包 36 MB（debug，未开 R8）；图标为 108dp 自适应矢量，安全区内绘制。

### 9.4 版本记录

- P1 交付：`versionName = 0.1.0-dev`，`versionCode = 1`。
- 截图：`docs/screenshots/`（home-dark / home-scrolled / search-dark / profile-dark / profile-light / dialog-dark）。

---

## 10. 导航与转场口径（2026-11-17 修订）

预测性返回、二级页侧滑转场、底栏切换的基准不是自绘动画，而是 `refs/coui-kit` 示例 App 的做法：

- **导航**：`MainActivity` 只装三个 Tab；每个二级页是一个真实 Activity（本项目统一为 `PageActivity`，路由走 Intent extra），`finish()` 即返回。返回栈由系统任务栈管理，进程回收后由系统恢复。
- **预测性返回**：应用不注册根级返回回调、不覆盖 `windowAnimationStyle`，由 ROM 播放跨 Activity 的预测性返回动画。`PredictiveBackHandler` 与手写边缘手势一律不使用。
- **转场**：二级页的入场/退场就是 ROM 的 Activity 转场；应用不控制时长、视差或缩放。
- **底栏**：与示例一致，`selectedIndex` 变化即换页，没有页面级横滑或转场；动效只来自 `NavigationBar` 自身的按压、拖拽选中与图标状态。
- **页面内**：分段控件（首页分区、历史/收藏筛选）继续用 `SlideSwitcher`，它不属于导航。

依据：示例的 `DemoSecondaryActivity` 用标准 Intent + `finish()` 打开全部二级页，`DemoApp` 用 `rememberSaveable` 的页码 + `SaveableStateHolder` 换页，全仓没有 `PredictiveBackHandler` / `AnimatedContent` 页面转场；Manifest 只开 `enableOnBackInvokedCallback="true"`。
