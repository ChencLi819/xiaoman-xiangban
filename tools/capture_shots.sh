#!/usr/bin/env bash
# 带前台校验的截图采集：逐屏进入 → 确认前台仍是本 App → 截图
# 依赖：adb 在 PATH 中（或设 ANDROID_HOME / ANDROID_SDK_ROOT）
# 输出：build/shots/*.png
set -u
: "${ANDROID_HOME:=}"
[ -n "$ANDROID_HOME" ] && export PATH="$ANDROID_HOME/platform-tools:$PATH"
cd "$(dirname "$0")/.." || exit 1
PY="${PYTHON:-python}"
mkdir -p build/shots

fg() { adb shell dumpsys activity activities 2>/dev/null | grep topResumedActivity; }
dump() {
  adb shell rm -f /sdcard/ui.xml >/dev/null 2>&1
  adb shell uiautomator dump /sdcard/ui.xml 2>&1 | head -1
  sleep 0.8
  adb pull /sdcard/ui.xml build/shots/latest.xml >/dev/null 2>&1
  [ -f build/shots/latest.xml ] || echo DUMP_FAIL
}
coord() {
  $PY - "$1" << 'PYEOF'
import re, sys
x = open('build/shots/latest.xml', encoding='utf-8', errors='replace').read()
t = sys.argv[1]
for txt, x1, y1, x2, y2 in re.findall(r'text="([^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', x):
    if t in txt:
        print((int(x1)+int(x2))//2, (int(y1)+int(y2))//2); sys.exit(0)
print('')
PYEOF
}
shot() { adb exec-out screencap -p > "build/shots/$1.png"; sleep 0.5; }

adb shell am force-stop com.xiaoman.memo; sleep 1
adb shell am start -n com.xiaoman.memo/.MainActivity >/dev/null; sleep 9
fg
dump && $PY -c "
import re
x=open('build/shots/latest.xml',encoding='utf-8',errors='replace').read()
print('HAS_HOME:', '此刻' in x or '随手记' in x)
"

# 依次处理各屏：tab 坐标固定（底部均分），进入后校验前台再截图
declare -A TABX=( [02_lists]=324 [03_memos]=540 [04_plans]=756 [05_us]=972 )
for name in 02_lists 03_memos 04_plans 05_us; do
  adb shell input tap ${TABX[$name]} 2256; sleep 3
  if fg | grep -q xiaoman; then
    shot $name
  else
    echo "FG_LOST:$name"
    adb shell am start -n com.xiaoman.memo/.MainActivity >/dev/null; sleep 6
  fi
done

# 我们俩 → 周期 / 互动（按 dump 坐标点击）
dump
C=$(coord "周期"); [ -n "$C" ] && { adb shell input tap $C; sleep 3; shot 06_cycle; adb shell input keyevent 4; sleep 2; } || echo NO_CYCLE
dump
C=$(coord "互动"); [ -n "$C" ] && { adb shell input tap $C; sleep 3; shot 07_interact; adb shell input keyevent 4; sleep 2; } || echo NO_INTERACT

ls -la build/shots/*.png
echo DONE
