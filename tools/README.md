# tools

仓库辅助脚本，**不参与 Gradle 构建**，也不打进 APK。都是命令行小工具，需要 `adb`（Android SDK platform-tools，设 `ANDROID_HOME` 或 `XM_ADB` 指向 `adb`）。

> 部分脚本会向模拟器/真机注入输入或写入外部存储，属于**仅供开发调试使用**，不要在生产设备上运行，也不要用来测试他人设备。

## gen_seed.js

从设计原型 [`../docs/prototype/情侣备忘录-原型.html`](../docs/prototype/情侣备忘录-原型.html) 提取种子内容，生成 `android/app/src/main/java/com/xiaoman/memo/data/SeedData.kt`（内置 520 条翻篇方式 + 520 条互动库 + 分寸种子）。

```bash
node tools/gen_seed.js
```

`SeedData.kt` 是生成文件，**不要手改**；要改内容请改原型后重新生成。运行结束会打印各分组条数与等级分布，便于核对。

## gen_content.py

按「句式族 + 异地场景词库」扩充内容，生成两个**生成文件**：

- `android/app/src/main/java/com/xiaoman/memo/data/SeedActsExtra.kt`
  —— 互动四项（真心话 / 大冒险 / 情侣任务 / 默契问答）的扩充条目，与 `SeedData.kt` 合并后各 520 条；
- `android/app/src/main/java/com/xiaoman/memo/data/SeedPoolExtra.kt`
  —— 雨过「翻篇方式」按 1..5 级的扩充条目，与原有条目合并后每级 520 条。

```bash
python tools/gen_content.py
```

规则：候选句按句式族展开 → 逐条去重（跨分类、跨等级、与原有内容）→ 句式族之间**轮流取样**，
避免某一族刷屏；同时过滤量词/虚词重复这类模板拼接病句。候选不足会直接报错而不是拿凑数句顶上。
改内容请改词库或句式族后重新生成，不要手改这两个 Kotlin 文件。

## sync_snapshot.js

按与 App 内 `SyncCrypto` 完全相同的算法（`SHA-256("xiaoman-sync|" + 配对码)` → AES-256-GCM）解密/加密同步快照文件。

```bash
node tools/sync_snapshot.js dec <加密文件> <配对码> [输出.json]   # 解密查看
node tools/sync_snapshot.js enc <明文.json> <配对码> <输出文件>   # 生成对端快照（构造测试数据）
node tools/sync_snapshot.js memos <加密文件> <配对码>            # 只列 memos 的 text / privateOnly
```

用于验证 [`docs/sync.md`](../docs/sync.md#加密) 的协议假设，或构造「对端有墓碑 / 有冲突」这类难以手工触发的合并场景。

## mini_dav.py

最小 WebDAV 桩服务器（`MKCOL` / `PUT` / `GET` + Basic Auth），在没有真实网盘时验证同步链路。

```bash
python tools/mini_dav.py        # 监听 0.0.0.0:8790，根目录 build/davroot，账号 xiaoman / xm-pass
```

测试凭据是硬编码的公开占位值，服务器**不带任何访问控制且监听所有网卡**，只可在本机或可信内网临时运行，用完即停。

## render_widget_designs.py

**离线渲染桌面卡片设计图**，不连模拟器 / 不装 APK。直接解析 `res/layout/widget_*.xml` 与
`res/xml/widget_*_info.xml`，用字体真实度量做 measure/layout（LinearLayout 权重与 gravity、
baselineAligned、FrameLayout 居中、RelativeLayout 两端对齐），按 dp 画成 PNG，并输出排版诊断
（内容超框、文字被 maxLines 截断）。

```bash
python tools/render_widget_designs.py                 # 8 张卡 × 三档尺寸（55 / 74 / 92dp 格子）+ 声明下限档
python tools/render_widget_designs.py --only combo    # 只渲某一张
python tools/render_widget_designs.py --scale 8       # 放大细看
```

产物在 `build/widget-designs/`：`<widget>_<档位>.png`、`<widget>_all.png`（三档横排）、`report.txt`。
模拟器屏幕尺寸与真机、各家 launcher 的网格差异大，**卡片排版/尺寸问题以这套设计图为准**，
结论见 [`docs/widget-design-review.md`](../docs/widget-design-review.md)。
**出正式包前 `report.txt` 必须清零（含可达下限档）**；id 类规则（`FIT` / `HIDE_BELOW` / `maxLines`）
一旦失效会静默变成一派假告警，改渲染器后务必抽查条数。

## gen_widget_boards.py

生成 8 张卡片的**卡面位图**与「添加小工具」面板的预览图，共 15 个文件（全部落在
`res/drawable-nodpi/`）：

| 产出 | 数量 | 用途 |
| --- | --- | --- |
| `w_board_{square,big,wide,plain,slim2}.webp` | 5 | 布局里的卡面底图（AI 纸面纹理 + 白纱 + 细线框 + 圆角烘进 alpha） |
| `w_face_{her,him}.webp` | 2 | 3×3 顶栏的圆形头像 |
| `widget_preview_*.webp` | 8 | 「添加小工具」面板预览图 |

```bash
python tools/gen_widget_boards.py
```

**不要再改回 XML shape 纯色底**（会丢纹理且易与同名位图冲突）。圆角是百分比烘进 alpha，
刻意压到 `RADIUS_RATIO = 0.035` 以小于系统 widget 圆角，避免四角露出系统底色；改色板时
脚本里的颜色常量必须与 `res/values/widget_colors.xml` 逐值同步。

## ui.py

无视觉 UI 探针：没有图像识别，靠 `uiautomator dump` 的控件树定位并操作，用于回归验收。

```bash
python tools/ui.py dump                 # 打印所有 text 节点及中心坐标
python tools/ui.py text <子串>           # 查找节点坐标
python tools/ui.py tap <子串>            # dump 后点击第一个匹配节点
python tools/ui.py tapr <子串>           # 点击最后一个匹配（右侧/底部）
python tools/ui.py type <文本>           # input text（空格用 %s）
python tools/ui.py key <KEYCODE>         # input keyevent
python tools/ui.py swipe x1 y1 x2 y2 ms
python tools/ui.py shot <name>           # 截图到 build/shots/<name>.png
python tools/ui.py fg                    # 当前前台 Activity
python tools/ui.py back                  # 返回键
python tools/ui.py clear_text            # 清空输入框
```

设备序列号用 `XM_ADB_SERIAL` 指定（默认 `emulator-5556`）。

## capture_shots.sh

逐 Tab 采集界面截图，带前台校验（点击后确认仍在小满前台才截图，掉出则重新拉起）。

```bash
tools/capture_shots.sh      # 需要 adb 在 PATH 中
```

产物在 `build/shots/*.png`。README「界面」一节用的截图可由这里人工挑选后放进 `docs/screenshots/`。

脚本里的 Tab 坐标是按特定分辨率写死的，换设备需要改 `TABX` 与点击 y 值。
