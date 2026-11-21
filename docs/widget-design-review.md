# 桌面卡片设计图检查（v1.6.1）

模拟器的屏幕尺寸与真机差异大（本机模拟器 1080×2400 / 420dpi，桌面网格也与各家 launcher 不同），
所以卡片的排版 / 尺寸问题**不靠模拟器判定**，改用离线渲染：

```bash
python tools/render_widget_designs.py                 # 全部卡片 × 三档尺寸（v1.6.3 起；v1.6.7 起 14 张）
python tools/render_widget_designs.py --only combo    # 只渲某一张
python tools/render_widget_designs.py --scale 8       # 放大细看
```

卡名**自动扫描** `res/layout/widget_*.xml`，新增布局不必改脚本。

- 直接解析 `res/layout/widget_*.xml` 与 `res/xml/widget_*_info.xml`，用真实字体度量做
  measure / layout（LinearLayout 权重与 gravity、baselineAligned、FrameLayout 居中、
  RelativeLayout 两端对齐），按 dp 画到 PNG；
- 尺寸按「格子 × 格数」推，正方形卡取 `min(宽,高)`，与 `applySquareSize` 的口径一致；
- 三档格子边长：**55dp**（老版本 aapt 最小值）、**74dp**（现代手机常见）、**92dp**（大屏 / 平板）；
- 同时打印排版诊断：内容超框、文字被 `maxLines` 截断、按实例缩放后的字号。
- 输出：`build/widget-designs/<widget>_<档位>.png`、`<widget>_declared.png`、`<widget>_all.png`（三档横排）；
  **排版诊断直接打印到 stdout，不写 `report.txt`**。

## 查出并修掉的问题

| # | 位置 | 现象（设计图） | 修法 |
|---|---|---|---|
| 1 | 心情打卡 2×2 | 五枚 26dp 圆点在 110dp/148dp 边长下总宽 154dp，溢出换行；缩到 16dp 后字又几乎填满圆点、点击区域过小 | 改成**按权重均分的胶囊**（`layout_weight=1`、高 26dp），宽度自适应卡片，任何边长都不溢出 |
| 2 | 相伴大卡 3×3 | 顶栏「她♥他 第 N 天 + 距见面 pill」在 222dp 边长下横向 222.5dp > 194dp，`她` 与 pill 被裁 | 顶栏只留「她♥他 第 N 天」并居中；「距见面」下沉到情话卡底部左侧，与「换一句」同一行两端对齐 |
| 3 | 恋爱计时 2×2 | 110dp 边长时数字行 104dp > 90dp，「天」被裁到卡片外 | 圆形 26→22dp、单位 10sp，并按实例边长等比缩字号（0.78~1.15） |
| 4 | 见面倒计时 2×2 | 110dp 边长时纵向 94dp > 88dp，pill 溢出 | 内边距 12→11、行距收紧，字号随边长缩放 |
| 5 | 情话小卡 2×2 | 正文要求 60.5dp 高、只给 55.5dp，第三行被裁半行；引号与正文之间空一大块 | 引号 16sp 与正文**成组垂直居中**；正文改 `wrap_content`；边长 < 140dp 时正文收成 2 行（代码 `setMaxLines`） |
| 6 | 还有几天 2×2 | 空态时 30sp 粗体「—」渲染成一条红杠 | 空态数字留空，交给说明文案 |
| 7 | 今日一句 2×1 | 标题 / 正文 / 尾注三行贴死，正文没有垂直余量 | 正文 `gravity=center_vertical`，内边距 12→10 |
| 8 | 小满 · 今天 2×1 | 竖排「标题 + 两行正文 + 按钮」在 74dp 高度下必然重叠（41.5dp 固定内容 > 33dp） | 改**横向两栏**：左正文两行，右 26dp 按钮；去掉装饰性标题，正文断成「TA 还没写 / 你 N 条」 |
| 9 | 要一起做的 4×2 | 148dp 高度下 124.5dp > 122dp，按钮压到第三行 | 行距 8/5/5→6/4/4，按钮 32→30dp |
| 10 | 相伴大卡 顶栏 / 各卡说明文字 | 顶栏挤在左侧，右侧大片留白 | 顶栏 `gravity=center` |

## 已知边界（未改，属于尺寸本身的下限）

- **相伴大卡**：边长 < 200dp（3 格 × 67dp 以下）时情话区放不下 3 行，正文会被裁；
  现代手机 3 格实测约 220–280dp，正常不会触发。若要在 165dp 也完整显示，需要在该尺寸下隐藏四入口行。
- ~~4×2 / 2×1 遗留卡（v1.5 五件套）：格子 55dp 时内容放不下~~ —— **v1.6.7 已消除**：六件套的布局
  按 v1.6 规范重做（顶栏收进进度 / 提示，三行列表 + 可选按钮），并加了 `HIDE_BELOW` 规则
  （`w_sub < 48`、`w_action < 120` 时按 `GONE` 收起）。现在 56 档全 ok，上面这条边界不再成立。

## 复现与验收

```bash
D:/python/python tools/render_widget_designs.py > /d/tmp/render.txt 2>&1
# 除上面「已知边界」两条外，输出里不应再出现 [overflow] / [clip]
grep -c '^== ' /d/tmp/render.txt       # 档数
grep -c -E '^   \[' /d/tmp/render.txt  # 必须为 0
```

> `python` 与 `D:/python/python` 的区别：系统默认的 `python` **没有 Pillow**，
> 渲染脚本必须用后者跑。

## 卡面与圆角（v1.6.2 补充）

- **背景图**：AI 生成图（`generated-images/Warm_cream_ivory_card_backgrou_*.png`）里的干净纸面
  被 `tools/gen_widget_boards.py` 裁出、镜像平铺成 `res/drawable-nodpi/w_board_*.webp`，
  布局直接用它当 `android:background`——不是摆设，卡面本身就是这张底图。
- **圆角**：圆角是**烘进位图 alpha** 的（短边 11%），不是靠 shape。之前那版把 `w_board_*.webp`
  删掉、换成了 `res/drawable/w_board_*.xml` 纯色 shape；shape 本身有 `corners`，但只要有任何一张
  旧的方角位图或缓存进到 `drawable-nodpi`（同名资源优先级），真机上就会看到直角。
  现在只有一个来源：位图，圆角在像素里，不会被其它资源覆盖。
- **人物插画不进底图**：焊在底图角落会被底部按钮挡住（一排按钮后面露出半张脸），
  改为只作为卡内头像（顶栏「她 / 他」）出现。
- 白纱不透明度 138/255：纸面平均亮度 235 → 罩纱后约 246，主墨 `#1B1721` 对比度 ≈ 13:1，
  次级 `#6C6576` ≈ 5:1，彩色 ≥ 4.3:1；纹理看得见但不干扰字。

## v1.6.2 三项调整

1. **文字更深**：`values/widget_colors.xml` 全色板再降一档（主墨 `#12101A`、次级 `#55505F`、
   主强调 `#B02F49`、深强调 `#93263E`）。纸面罩纱后亮度约 246，主文字对比度 ≈ 17:1、次级 ≈ 6:1，
   10sp 的小字也不发灰。
2. **圆角**：`RADIUS_RATIO` 11% → **14%**（148dp 卡 ≈ 21dp），遮罩改 4 倍超采样 + LANCZOS 缩回，
   圆弧没有锯齿；细线框内收到 5%。位图缩放后圆角仍是圆的，不会退化成直角。
3. **「今日心情」翻页卡**：`widget_mood.xml` 上半区是心情页（`w_mood_art` 插画 / `w_mood_name`
   心情名 / `w_mood_sub` 一句话），下半区是五枚翻页按钮（`w_mood0..4`）。
   点击 → `MoodActionReceiver` 写 `moods` 表 → `renderV2` 重渲染 → 页面翻到该档。
   插画按本机身份取 `mood_her_*` / `mood_him_*`，边长 = 实例边长 × 0.32（28–96dp）；
   边长 < 130dp 时 `w_mood_sub` 收成 `GONE`（只留插画 + 名字 + 翻页条）。
   心情卡底图用 `w_board_plain`（无人物），避免和页内插画重复。
