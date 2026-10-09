# tools

仓库辅助脚本，**不参与 Gradle 构建**，也不打进 APK。都是命令行小工具，需要 `adb`（Android SDK platform-tools，设 `ANDROID_HOME` 或 `XM_ADB` 指向 `adb`）。

> 部分脚本会向模拟器/真机注入输入或写入外部存储，属于**仅供开发调试使用**，不要在生产设备上运行，也不要用来测试他人设备。

## gen_seed.js

从设计原型 [`../docs/prototype/情侣备忘录-原型.html`](../docs/prototype/情侣备忘录-原型.html) 提取种子内容，生成 `android/app/src/main/java/com/xiaoman/memo/data/SeedData.kt`（内置 520 条翻篇方式 + 520 条互动库 + 分寸种子）。

```bash
node tools/gen_seed.js
```

`SeedData.kt` 是生成文件，**不要手改**；要改内容请改原型后重新生成。运行结束会打印各分组条数与等级分布，便于核对。

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
