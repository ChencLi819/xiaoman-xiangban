# 小满 · 相伴 — 桌面小组件接入文档

> 原型预览：`docs/prototype/widgets.html`（浏览器直接打开）
> 视觉基准：`ui/theme/Color.kt` 双色身份系统，本文所有色值与之一致。
> 所有尺寸按 **1dp = 2px** 换算，原型中的像素值 ÷ 2 即为实现 dp 值。

---

## 0. 现役实现状态（v1.6.7）

本文 §1–§8 是**最初的设计稿**，落地时有两处偏离，看代码前先读这段：

| 项 | 设计稿 | 实际实现 |
| --- | --- | --- |
| 技术方案 | Glance（Compose for AppWidget） | **传统 `RemoteViews` + XML 布局**（`widget/XmWidgets.kt` + `res/layout/widget_*.xml`）。工程里没有引入 Glance 依赖 |
| 组件数量 | 8 个 | **14 个**：v1.6 八件套 + v1.5 六件套兼容层（`widget/LegacyWidgets.kt`） |

**为什么保留 v1.5 六件套**：v1.6.3 曾把旧六件套的 6 个 provider 从清单里整组删掉，系统重新扫描清单后按平台规则把桌面上对应的已放置实例一并移除（`AppWidgetServiceImpl` 的内建行为，App 侧拦不住），用户升级后发现卡片消失。v1.6.7 把 6 个 provider **原样注册回来**——

- **类名必须与 v1.5 逐字一致**（`WidgetSmall` / `WidgetLarge` / `WidgetLists` / `WidgetCountdown` / `WidgetMemos` / `WidgetQuote`）。系统按 **ComponentName（包名 + 类名）** 认领实例，改名即视为新部件，旧实例照样找不回来。
- 布局与视觉按 v1.6 的统一规范重做（纸面底图 `w_board_*`、单一强调色 `w_accent`、字号阶梯），所以恢复回来的卡片与八件套并排不割裂。
- 两代卡片共用刷新入口：`WidgetUpdater.updateAll()` 与每日 `WidgetDailyWorker` 都同时调 `renderV2()` + `renderLegacy()`。

> **改 provider 类名或删 `<receiver>` 前务必想清楚**：任何一次「清理旧部件」的动作都会让用户桌面上已有的实例消失，且无法通过 App 侧代码恢复。真要下线某个部件，宁可在 UI 上不引导用户添加，也不要删清单声明。

## 1. 组件清单

### 1.1 八件套（v1.6 起，现役主力）

| # | 组件 | 尺寸(dp) | AppWidget minWidth×minHeight | 数据来源 | 点击行为 |
|---|------|---------|------------------------------|----------|----------|
| ① | 恋爱计时 | 2×2 | 110×110 | 在一起起始日（SettingsStore） | → 首页 HomeScreen |
| ② | 见面倒计时 | 2×2 | 110×110 | 计划/倒数日（PlansScreen 数据） | → 计划页 PlansScreen |
| ③ | 心情打卡 | 2×2 | 110×110 | 当日日记心情 | 五档心情直接写入当日心情 |
| ④ | 情话小卡 | 2×2 | 110×110 | 情话池（109 条 + pool_overrides） | 点卡片 → 互动库；「换一句」刷新文本 |
| ⑤ | 每日情话 | 4×2 | 250×110 | 同 ④ | 同 ④ |
| ⑥ | 快速记一笔 | 4×1 | 250×70 | — | → 编辑页 EditorScreen（预填文本） |
| ⑦ | 雨过翻一篇 | 4×1 | 250×70 | 雨过池（2600 条） | → 雨过浏览 PoolBrowseScreen |
| ⑧ | 相伴大卡 | 4×3 | 250×180 | ①②④聚合 | 四个入口分别路由到写日记 / 记心情 / 抽情话 / 雨过 |

### 1.2 六件套兼容层（v1.5 遗留，v1.6.7 恢复注册）

| # | 组件 | 类名 | 尺寸 | minWidth×minHeight | 说明 |
|---|------|------|------|--------------------|------|
| ⑨ | 今天 | `WidgetSmall` | 2×1 | 110×40 | TA 今天写了没 / 我写了多少；40dp 高时收起副行 |
| ⑩ | 要一起做的 | `WidgetLarge` | 4×2 | 250×110 | 共同待办三行 + 周期相位；110dp 高时收起按钮 |
| ⑪ | 说好的 | `WidgetLists` | 4×2 | 250×110 | 清单进度 + 前几件没勾的事 |
| ⑫ | 还有几天 | `WidgetCountdown` | 2×2 | 110×110 | 最近的约定还剩几天 |
| ⑬ | 点滴 | `WidgetMemos` | 4×2 | 250×110 | 最近记下的三条 + 「＋ 记一条」 |
| ⑭ | 今日一句 | `WidgetQuote` | 2×1 | 110×40 | 每天换一句情话（两行内截断） |

> 六件套**只做兼容，不再新增**：新功能一律进八件套。它们的布局与渲染在 `widget/LegacyWidgets.kt`，
> 与 `XmWidgets.kt` 共用 `squareSide` / `fitScale` / `route` / `WidgetData`。

> minWidth/minHeight 按系统公式 `70×n − 30` 估算，`targetCellWidth/Height`（API 31+）直接填 n×m。
> `previewLayout`（API 31+）指向对应布局实现选择器实时预览；低版本用 `previewImage`。
> 六件套用的是 `previewLayout`（不是 `previewImage`），所以恢复它们不存在「缺预览图」的风险。

## 2. 视觉规范

### 2.1 色彩令牌

| 令牌 | 色值 | 用途 |
|---|---|---|
| `xm_bg` | `#FBF7F4` | 页面底色（卡片外的容斥色，不直接用） |
| `xm_card` | `#FFFFFF` | 全部卡面底色（情话小卡用 accent-soft） |
| `xm_line` | `#F0EAE5` | 卡片描边 1.5dp |
| `xm_ink` | `#2C2733` | 主文字 |
| `xm_ink2` | `#8A8494` | 次级文字 |
| `xm_ink3` | `#B4AEB9` | 弱化文字/占位符 |
| `xm_accent` | `#E86A7C` | 品牌强调（天数、发送按钮） |
| `xm_accent_soft` | `#FDE7E4` | 强调底（情话卡、图标圆底） |
| `xm_accent_deep` | `#C9526A` | 强调底上的文字/图标 |
| `xm_her_soft / her_ink` | `#FDEDEA / #B06863` | 她侧头像、抽情话入口 |
| `xm_him_soft / him_ink` | `#EBF2FB / #5A80B8` | 他侧头像、倒数日 |
| `xm_gold_soft / gold_ink` | `#FFF6E8 / #B98A38` | 记心情入口 |
| `xm_q_soft / q_ink` | `#F3EFF7 / #7A6B85` | 雨过入口 |
| `xm_tag_bg` | `#F5F0EE` | 输入条底 |

深色模式（可选二期）：取 `DarkXm`（`bg #141316 / card #1E1D22 / accent #EFB8C8`…），通过 `AppWidgetManager` + configuration 切换两套 RemoteViews 配色。

### 2.2 造型

- 卡片圆角 **20dp**；描边 1.5dp `xm_line`；投影用系统 `ElevationOverlay` 即可（原型阴影仅示意）。
- 字体跟随系统默认（`sans-serif`），中文自动回退，无需打包字体。
- 头像圆形 26dp（小卡）/ 24dp（大卡），内文字 10dp Medium。
- 大数字：恋爱计时 32dp Bold / 倒计时 38dp Bold。
- 图标圆底 28dp，内图标 12dp。

## 3. 技术方案

推荐 **Glance（Jetpack Compose for AppWidget）**：工程已是 Compose，Glance 可直接复用 `XmColors` 常量，避免手写 RemoteViews XML。最低要求 compileSdk 34，Glance 1.1.x 兼容 minSdk 21。

若不想引入 Glance 依赖，退路是传统 `RemoteViews` + XML 布局，本文规格同样适用（RemoteViews 不支持自定义 View，全部用 Frame/Linear/Text/ImageView 即可满足）。

### 3.1 文件结构建议

```
android/app/src/main/java/com/xiaoman/memo/widget/
├── XmWidgetTheme.kt        # 色彩令牌常量（与 ui/theme/Color.kt 对齐）
├── DayCounterWidget.kt     # ① 恋爱计时
├── MeetCountdownWidget.kt  # ② 见面倒计时
├── MoodCheckWidget.kt      # ③ 心情打卡
├── LoveQuoteWidget.kt      # ④⑤ 情话（小/中两个 receiver 复用同一渲染）
├── QuickMemoWidget.kt      # ⑥ 快速记一笔
├── RainPassWidget.kt       # ⑦ 雨过翻一篇
├── ComboWidget.kt          # ⑧ 相伴大卡
└── WidgetData.kt           # 统一读数：天数/倒数/情话/心情（见 §4）
```

### 3.2 Manifest 注册（每个组件一条 receiver）

```xml
<receiver android:name=".widget.DayCounterWidget" android:exported="false">
    <intent-filter>
        <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
    </intent-filter>
    <meta-data android:name="android.appwidget.provider"
        android:resource="@xml/widget_day_counter" />
</receiver>
```

`res/xml/widget_day_counter.xml` 示例（其余 7 个同理改尺寸）：

```xml
<appwidget-provider
    android:minWidth="110dp" android:minHeight="110dp"
    android:targetCellWidth="2" android:targetCellHeight="2"
    android:resizeMode="horizontal|vertical"
    android:widgetCategory="home_screen"
    android:updatePeriodMillis="1800000"
    android:initialLayout="@layout/glance_default_loading_layout"
    android:description="@string/widget_day_counter_desc" />
```

## 4. 数据接入（WidgetData.kt 统一出数）

| 字段 | 来源 | 说明 |
|---|---|---|
| `daysTogether` | SettingsStore 存的在一起日期 | `ChronoUnit.DAYS.between(start, LocalDate.now()) + 1` |
| `nextMeetDate` / `meetPlace` | Plans 相关表 | 取最近的未来见面计划；无计划时组件显示「添加见面计划」 |
| `todayMood` | 当日日记条目 | 五档枚举：甜/想/累/丧/气 |
| `loveQuote` | 情话池（Seed 109 条 + `pool_overrides` 用户自定义） | 每日一换：`LocalDate.toEpochDay() % size` 做稳定轮换，点击「换一句」用随机索引并写入 `DataStore`（key = `widget_quote_index`） |
| `rainPassText` | 雨过池 2600 条 | 同上轮换逻辑 |

- 所有读取走 App 现有 Room 单例，**注意在组件进程中不要重复建库**（复用 `XiaomanApp` 的 database 入口）。
- `pool_overrides` 以文本为键的覆盖逻辑已在 `AppViewModel` 收口，`WidgetData` 只读查询即可，不走 vm 写接口。

## 5. 点击路由

App 为单 Activity + Compose 导航，统一走 `PendingIntent` 打开 MainActivity 并带 extras：

```kotlin
fun route(context: Context, target: String): PendingIntent =
    PendingIntent.getActivity(
        context, target.hashCode(),
        Intent(context, MainActivity::class.java)
            .putExtra("widget_route", target)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
```

MainActivity 读 `widget_route` 后导航：

| route 值 | 目标 |
|---|---|
| `home` / `plans` | HomeScreen / PlansScreen |
| `editor` | EditorScreen（快速记一笔可把草稿文本一并传入） |
| `mood:<甜/想/累/丧/气>` | 心情打卡直接落库当日心情，然后 Toast「已记下」 |
| `pool:quote` / `pool:rain` | 互动库情话池 / 雨过 PoolBrowseScreen |

心情打卡是唯一「无界面写库」的组件：Glance `actionRunCallback<MoodAction>()` 里用 `goAsync` 写 Room 后调用自身刷新。

## 6. 刷新策略

| 时机 | 方式 |
|---|---|
| 天数翻日 | WorkManager 每日 00:05 `updateAll`（勿依赖 `updatePeriodMillis`，最小 30 分钟且休眠不准） |
| 打开 App / 写日记 / 改情话池 | App 内所有写操作后调用 `XmWidgets.updateAll(context)` |
| 点「换一句」 | 点击回调内换索引 + 单组件刷新 |
| 见面日变更 | Plans 增删改时定向刷新 ② 与 ⑧ |

> 注意：不要在组件里放协程作用域常驻；每次 `provideGlance` 内新建、结束即弃。

## 7. 实现步骤 Checklist

1. `gradle/libs.versions.toml` 加 `androidx.glance:glance-appwidget:1.1.x`，`compose` 已具备。
2. 建 `widget/` 包 + `XmWidgetTheme.kt` 令牌。
3. 先实现 ①恋爱计时（最简，验证 receiver/尺寸/刷新链路）。
4. 补齐 ②–⑦，复用 `WidgetData`。
5. 实现 ⑧大卡（四入口 PendingIntent + 三段布局）。
6. `MainActivity` 接 `widget_route` 导航分发。
7. WorkManager 日更任务注册（`XiaomanApp.onCreate`）。
8. 深色配色（可选）。
9. 真机验证：添加到桌面、拖拽改尺寸（resizeMode）、深色壁纸下观感、勿杀后台后刷新。

## 8. 边界与约束

- **异地恋约束**：所有文案不出现同城才成立的行为（沿用内容基线）。
- 情话条目保持 109 条基线，不因组件扩充；用户自定义条目来自 `pool_overrides`，已天然生效。
- 组件内文案从简：只留可执行短句与数据，不放解释性文字（项目既定偏好）。
- 快速记一笔的输入条在桌面端**不可输入**（RemoteViews/Glance 均不支持桌面端 EditText 输入），点击整条跳编辑页——原型中的占位文案即点击前静态展示。
