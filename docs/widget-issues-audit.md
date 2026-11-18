# 桌面小工具问题清单（2026-10-25 代码审查）

> 审查范围：`android/app/src/main` 下 `widget/`（Kotlin）、`res/layout/widget_*.xml`（当时 14 张）、
> `res/xml/widget_*_info.xml`（当时 14 份）、`AndroidManifest.xml` 注册、`XiaomanApp.kt` 调度。
>
> **v1.6.3 状态**：第 2、3 项已执行 —— 旧六件套整组删除，现役 8 张卡 / 8 份 info / 8 个 receiver；
> `WidgetUpdater.updateAll()` 只调 `renderV2()`，不再有无条件更新。第 1 项（`onUpdate` 里 `runBlocking`）
> 仍未处理，八件套同样存在主线程查库的 ANR 风险。
> 结论分三档：**P0 会崩/功能不生效**、**P1 明显体验缺陷**、**P2 打磨项**。

---

## P0 — 必须修

### 1. `onUpdate` 在主线程 `runBlocking` 查库渲染（ANR 风险）

`XmWidgets.kt:37-82` 八个 provider 全部是：

```kotlin
override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = runBlocking { renderV2(ctx) }
override fun onAppWidgetOptionsChanged(...) = runBlocking { renderV2(ctx) }
```

`renderV2` 里会 `WidgetData.snapshot(ctx)`（Room 查询 + DataStore `first()` + 多次 DAO 调用）。
`onUpdate` / `onAppWidgetOptionsChanged` 都跑在 **主线程**，`runBlocking` 会把主线程阻塞住直到所有
IO 完成。桌面拖动缩放会连续触发 `onAppWidgetOptionsChanged`，等于连续在主线程做全量查库——
在低端机或库较大时就是 **ANR**（"小满没有响应"）。

旧六件套 `XiaomanWidgets.kt:37-56` 是同一写法（`render(ctx)` 内部也是 `runBlocking`）。

**改法**：`onUpdate` 只做 `goAsync()` + 协程（照抄 `MoodActionReceiver` 的写法），或用
`AppWidgetProvider` 里启动一个 `CoroutineScope(SupervisorJob() + Dispatchers.IO)`。
`runBlocking` 在广播回调里是反模式，必须清掉。

### 2. 旧六件套（v1.5）与新八件套（v1.6）**并存**，桌面出现 14 个入口  ✅ v1.6.3 已修

`AndroidManifest.xml` 里两套 widget 全部注册，且 `XiaomanApp` 的 `WidgetUpdater.updateAll`
每次数据变更都 `render()` + `renderV2()` 两遍（`XiaomanWidgets.kt:65-70`）：

- 旧六件套：今天 / 要一起做的 / 说好的 / 还有几天 / 点滴 / 今日一句
- 新八件套：恋爱计时 / 见面倒计时 / 心情打卡 / 情话小卡 / 每日情话 / 快速记一笔 / 雨过翻一篇 / 相伴

**功能重叠**：`今日一句`(2×1) 与 `情话小卡`(2×2)/`每日情话`(4×2) 都是情话；`还有几天`(2×2) 与
`见面倒计时`(2×2) 都是倒计时。用户在小部件选择器里看到 14 个、语义还打架。

更实际的代价：每次数据变更要构建并 `updateAppWidget` **14 个 RemoteViews**，其中一半用户
根本没放到桌面（`getAppWidgetIds` 返回空数组，但 `render()` 里那 6 个旧卡是**无条件**
`mgr.updateAppWidget(ComponentName(...), rv)`，即使一个实例都没有也会执行构建 + IPC）。

**处置（v1.6.3）**：按建议整组下线 —— 删 manifest 6 个 `<receiver>`、删 `XiaomanWidgets.kt`（含 `render()`）、
删 6 个 layout / 6 份 info / 6 张 preview / 4 条 `w_desc_*` / 20 个只服务旧卡的 drawable；
`WidgetUpdater` 独立成文件且只调 `renderV2()`。

### 3. 旧六件套无条件更新（实例为 0 也走一遍）  ✅ v1.6.3 随旧卡一并消失

见上。`XiaomanWidgets.kt:237-242`：

```kotlin
mgr.updateAppWidget(ComponentName(ctx, WidgetSmall::class.java), small)
```

`updateAppWidget(ComponentName, RemoteViews)` 这个重载会**更新该 provider 的所有实例**，
即使一个都没放也会白做一次构建 + IPC。应改为先 `getAppWidgetIds(cn)` 判断非空。

---

## P1 — 明显体验缺陷

### 4. 旧六件套缺 `onAppWidgetOptionsChanged`，但声明了 `resizeMode`

`widget_small_info` / `widget_large_info` / `widget_lists_info` / `widget_memos_info` /
`widget_quote_info` 五份都写了 `resizeMode="horizontal|vertical"`，但对应 provider
（`WidgetSmall` / `WidgetLarge` / `WidgetLists` / `WidgetMemos` / `WidgetQuote`）**没有重写
`onAppWidgetOptionsChanged`**（只有 `WidgetCountdown` 重写了）。用户拉大这些卡之后不会重绘，
多出来的空间是死的。新八件套在 v1.6.2 已经补上这个回调，旧套件漏了。

### 5. `widget_large_info.xml` 与 `widget_small_info.xml` 缺 `android:description`

其余 12 份 info 都有 `android:description="@string/w_desc_*"`，这两份没有。
小部件选择器里这两张卡会显示成**空白描述**（Android 12+ 的选择器会露出来）。
对应字符串 `w_desc_small` / `w_desc_large` 在 `values/strings.xml` 里也不存在，需要一并补。

### 6. `updatePeriodMillis="1800000"` 是无效值

14 份 info 全部写了 `android:updatePeriodMillis="1800000"`（30 分钟）。
Android 的硬性下限是 **30 分钟**，但更重要的是：系统**不会**按这个周期精确唤醒，
而且在很多 OEM ROM 上会被省电策略压掉。项目实际靠两个 WorkManager 任务驱动
（`scheduleDaily` 12h + `scheduleWidgetDaily` 24h），`updatePeriodMillis` 基本是个摆设。
建议明确注释「真实刷新靠 WorkManager」，或直接设成 0 关掉系统轮询，避免误导。

### 7. 正方形卡在 Android 12 以下**没有**正方形逻辑

`squareSide()` 开头就 `if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null`
（`XmWidgets.kt:144`），`applySquareSize` 拿到 null 直接 return。也就是说
**Android 11 及以下的六张正方形卡会铺满格子**，而桌面网格格子并非正方形 → 渲染成长方形。
v1.6.1 的 changelog 写了「Android 12 以下回退铺满格子」，属于已知降级，但用户量大的是
Android 10–13，Android 12 以下仍有可观占比，建议至少在 layout 里给一个**近似正方形的
兜底约束**（比如 `minHeight` 与 `minWidth` 一致 + 卡片内 `layout_gravity="center"`）。

### 8. `moodViews` 的 `artSide` 上限 92dp 与卡片边长脱钩

```kotlin
val artSide = ((side ?: 148f) * 0.30f).coerceIn(26f, 92f)
```

3×3 的相伴卡边长可达 222dp，但心情卡最大边长通常也就 ~180dp；`0.30f` 在 148dp 是 44dp，
到 180dp 是 54dp。问题在于 `side` 用的是 `min(宽,高)`，当卡片被拉成**扁的**（比如 2×2 但
用户拉成 2×1 高）时，`side` 会取到很小的高度值，插画缩到 26dp 下限，视觉上会显得空。
这一档值得按 `min(side)` 之外的策略再想想（例如插画用 `weight` 而不是固定 dp）。

---

## P2 — 打磨项

### 9. `renderV2` 里 `MoodCheckWidget` 无实例也会构建 RemoteViews

`renderV2` 的 `for (id in mgr.getAppWidgetIds(sp.cn))` 循环本身是**逐实例**的，实例为空自然
不执行——这点是好的。但 `sp.build(side)` 在循环内，`side` 是 `squareSide(mgr, id)` 按实例算的，
逻辑正确。**唯一小问题**：`QuickMemoWidget` / `RainPassWidget` / `LoveQuoteWideWidget` 的
`Spec.squareRoot = null`，但它们同样声明了 `resizeMode` 且需要 `fitScale`，
`side` 走的是 `squareSide()`（`min(宽,高)`）——横向卡用 min 取高是对的，
但 `fitScale(side, 74f)` 的参考值对 4×2 情话卡（ref 148）和 4×1 卡（ref 74）不一致，
`LoveQuoteWideWidget` 传的是 `side` 但 `qwViews` 里 ref 写 148，而它的最小高度是 110dp——
拉高到 4×3 时 `side=min(宽,高)` 可能反而变小，`fitScale` 夹到 0.78 反而**缩小**字体。

> 具体：4×2 卡宽 250dp 高 110dp → `side=110` → `fitScale(110,148)=0.78`，
> 情话正文字号 14sp × 0.78 ≈ 10.9sp，比设计规范的 12sp 还小。**建议**横向卡不要用
> `min(宽,高)`，改用**高度**单独判断（横向卡的字号主要受高度约束）。

### 10. `clip()` 按字符数截断，中文与 emoji 混排会数错

`XiaomanWidgets.kt:91` `clip(s, n)` 用 `s.length`（UTF-16 code unit）。emoji（代理对）会被
算成 2，`take(n-1)` 可能**切断代理对**产生半个字符（乱码方块）。备忘录正文里 emoji 很常见。
建议用 `codePointCount` 或直接 `BreakIterator`。

### 11. `QuoteRefreshReceiver` 每次「换一句」都全量重建所有部件

`refreshPending` → `renderV2(ctx)` 会重渲染 **8 个部件**。点一次「换一句」却刷新了全部卡片，
代价偏大（虽然是 IO 线程）。可以只刷新情话相关的两个 ComponentName。

### 12. `WidgetDailyWorker` 24h 周期 + `ExistingPeriodicWorkPolicy.KEEP`

`XiaomanApp.kt:95-108`：`PeriodicWorkRequestBuilder(24h)` + `KEEP`。
WorkManager 的周期任务**不保证准点**（有 doze / 批处理延迟），
实际可能在 00:05 后延迟几十分钟甚至几小时才跑。日期翻转（天数 +1、情话轮换）会**迟到**。
建议接受这个现实并在卡面用「相对日期」而非绝对天数，或改用 `AlarmManager.setExactAndAllowWhileIdle`
（需要 `SCHEDULE_EXACT_ALARM` 权限，代价是用户授权）。

---

## 已核对无问题的部分（供参考）

- 14 份 `widget_*_info.xml` 的 `@string/w_desc_*` 引用**全部存在**（除 P1#5 那两份根本没引用）。
- 14 张 layout 引用的 `@drawable/w_board_*`（6 张卡面位图）**全部存在**。
- layout 引用的 `@color/w_*` **全部存在**于 `values/widget_colors.xml`。
- `applySquareSize` 对 `w_qs_card` / `w_cb_card` / `w_root` 的根节点 id 引用**都存在**于对应 layout。
- `MoodActionReceiver` / `QuoteRefreshReceiver` 在 manifest 已注册且 `exported="false"`，正确。
- 新旧 layout 共用 `w_row1/w_row2/w_row3`、`w_progress`、`w_action`、`w_root` 等 id —— 因为
  RemoteViews 是按 **layout 文件** 绑定的，不同 layout 复用同名 id 不会串台，**没问题**。

---

## 建议的修复顺序

| 顺序 | 项 | 理由 |
|---|---|---|
| 1 | P0#1 `runBlocking` → 协程 | 唯一会导致 ANR 的项 |
| 2 | P0#2/#3 旧六件套下线或条件更新 | 影响所有用户的刷新开销与选择器噪音 |
| 3 | P1#4/#5 旧卡补回调 + 补 description | 低风险、用户可见 |
| 4 | P1#7 Android 12 以下正方形兜底 | 覆盖老机型 |
| 5 | P2#9 横向卡 fitScale 口径 | 情话卡字体偏小的根因 |
| 6 | P2#10/#11/#12 | 打磨 |
