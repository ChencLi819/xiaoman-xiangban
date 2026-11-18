# screenshots

根 README「界面」一节使用的运行时截图。

当前 10 张：主流程 5 个 Tab（`home` / `lists` / `memos` / `plans` / `us`）+ 板块内部 5 张
（`quarrel` / `rules` / `interact` / `letter` / `cycle`）。

规格：Android 15 模拟器，1080×2424，浅色模式，等比缩到宽 420px 的 PNG。
界面内容全部是**演示数据**，不含真实用户记录。

## 重新采集

```bash
# 1. 装包并启动
adb -s <serial> install -r -g android/app/build/outputs/apk/sync/debug/xiaoman-sync-v<ver>-debug.apk
adb -s <serial> shell pm grant com.xiaoman.memo.sync android.permission.POST_NOTIFICATIONS
adb -s <serial> shell am start -n com.xiaoman.memo.sync/com.xiaoman.memo.MainActivity

# 2. 灌演示数据（可选，空库截图信息量低）
#    导入文件走 App 内「我们俩 → 数据导入」，需要 SAF 选择器交互

# 3. 采集：Tab 栏固定 y 坐标，子页从「我们俩」进入
adb -s <serial> exec-out screencap -p > shot.png
```

`tools/capture_shots.sh` 是一个可直接跑的批量采集脚本（含前台校验），产物落在 `build/shots/`；
换设备或分辨率时需要改其中的 Tab x 坐标与 y 值。

改图后请同步更新根 README 的 `<img>` 列表与说明文字（设备、系统版本、深浅色、是否演示数据）。

## 版本附加集

`v1.6/` 放的是 v1.6 新功能（心晴 / 周期页卡片换位）的开发验证截图，
含未打卡、大图页、已打卡、心晴月历（我 / 两人）、周期页三处关键状态，
用途是留档「这次改动在真机上长什么样」，不替代上表的展示图集。
