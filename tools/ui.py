# -*- coding: utf-8 -*-
"""小满 App 无视觉 UI 验收辅助：
用法:
  python tools/ui.py dump                 -> dump UI, 打印 text 节点(含 bounds 中心)
  python tools/ui.py text <子串>          -> 查找包含子串的节点(坐标)
  python tools/ui.py tap <子串>           -> 重新 dump 后 tap 第一个匹配节点中心
  python tools/ui.py tapr <子串>          -> 同 tap, 但 tap 最后一个匹配(右侧/底部)
  python tools/ui.py type <文本>          -> input text (空格用 %s)
  python tools/ui.py key <KEYCODE>        -> input keyevent
  python tools/ui.py swipe x1 y1 x2 y2 ms
  python tools/ui.py shot <name>          -> screencap 到 build/shots/<name>.png
  python tools/ui.py fg                   -> 当前前台 Activity
  python tools/ui.py clear_text           -> 清空输入框(长按全选删除近似: 3次全选删除)
  python tools/ui.py back
"""
import re, subprocess, sys, os

PKG = "com.xiaoman.memo"
def _find_adb():
    env = os.environ.get("XM_ADB")
    if env:
        return env
    home = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if home:
        for name in ("adb.exe", "adb"):
            p = os.path.join(home, "platform-tools", name)
            if os.path.exists(p):
                return p
    return "adb"
ADB = _find_adb()
SERIAL = os.environ.get("XM_ADB_SERIAL", "emulator-5556")
SHOT_DIR = os.path.join("build", "shots")

def sh(*args, timeout=30):
    return subprocess.run([ADB, "-s", SERIAL, *args], capture_output=True, text=True, timeout=timeout,
                          encoding="utf-8", errors="replace")

def ensure_device():
    r = subprocess.run([ADB, "-s", SERIAL, "get-state"], capture_output=True, text=True)
    if "device" not in (r.stdout or ""):
        subprocess.run([ADB, "kill-server"], capture_output=True)
        subprocess.run([ADB, "start-server"], capture_output=True)
        subprocess.run([ADB, "-s", SERIAL, "wait-for-device"], timeout=60)

def dump_xml():
    ensure_device()
    sh("shell", "rm", "-f", "/sdcard/ui.xml")
    for _ in range(2):
        r = sh("shell", "uiautomator", "dump", "/sdcard/ui.xml", timeout=40)
        if "dumped" in (r.stdout or ""):
            break
        import time; time.sleep(1.5)
    sh("pull", "/sdcard/ui.xml", os.path.join(SHOT_DIR, "_cur.xml"), timeout=40)
    p = os.path.join(SHOT_DIR, "_cur.xml")
    if not os.path.exists(p):
        return ""
    with open(p, encoding="utf-8", errors="replace") as f:
        return f.read()

NODE_RE = re.compile(r"<node[^>]*>")

def _attr(tag, name):
    m = re.search(rf'{name}="([^"]*)"', tag)
    return m.group(1) if m else ""

def nodes(xml):
    out = []
    for tag in NODE_RE.findall(xml):
        text, desc = _attr(tag, "text"), _attr(tag, "content-desc")
        x1, y1, x2, y2 = map(int, _attr(tag, "bounds").replace("][", ",").replace("[", "").replace("]", "").split(","))
        if text or desc:
            out.append({"text": text, "desc": desc, "cls": _attr(tag, "class"), "rid": _attr(tag, "resource-id"),
                        "cx": (x1+x2)//2, "cy": (y1+y2)//2, "w": x2-x1, "h": y2-y1})
    return out

def find_all(sub):
    xml = dump_xml()
    ns = nodes(xml)
    hits = [n for n in ns if sub in n["text"] or sub in n["desc"]]
    # 子串匹配会把「删除这次记录？」这种标题也算命中，导致 tap 删除 点到标题上（对话框不关）。
    # 只要存在完全相同的节点，就优先用它。
    exact = [n for n in hits if n["text"] == sub or n["desc"] == sub]
    return (exact if exact else hits), ns

def do_tap(sub, last=False):
    hits, ns = find_all(sub)
    if not hits:
        print("NOTFOUND:", sub)
        print("--- 当前屏幕节点 ---")
        for n in ns[:40]:
            print(f"  [{n['cx']},{n['cy']}] t={n['text'][:30]!r} d={n['desc'][:20]!r} c={n['cls'].split('.')[-1]}")
        return 1
    n = hits[-1] if last else hits[0]
    # EditText 中心点可能被键盘挡住 -> 仍 tap 中心
    sh("shell", "input", "tap", str(n["cx"]), str(n["cy"]))
    print(f"TAP {sub!r} @({n['cx']},{n['cy']}) text={n['text'][:40]!r}")
    return 0

def main():
    cmd = sys.argv[1] if len(sys.argv) > 1 else "dump"
    os.makedirs(SHOT_DIR, exist_ok=True)
    if cmd == "dump":
        xml = dump_xml()
        ns = nodes(xml)
        print(f"{len(ns)} nodes")
        for n in ns:
            print(f"  [{n['cx']:4},{n['cy']:4}] t={n['text'][:40]!r} d={n['desc'][:24]!r} c={n['cls'].split('.')[-1]}")
    elif cmd == "text":
        hits, ns = find_all(sys.argv[2])
        for n in hits:
            print(f"  [{n['cx']},{n['cy']}] t={n['text']!r} c={n['cls'].split('.')[-1]}")
        if not hits: print("NOTFOUND"); sys.exit(1)
    elif cmd == "tap":
        sys.exit(do_tap(sys.argv[2]))
    elif cmd == "tapr":
        sys.exit(do_tap(sys.argv[2], last=True))
    elif cmd == "type":
        txt = sys.argv[2].replace(" ", "%s")
        sh("shell", "input", "text", txt)
        print("TYPED", sys.argv[2])
    elif cmd == "key":
        sh("shell", "input", "keyevent", sys.argv[2])
    elif cmd == "back":
        sh("shell", "input", "keyevent", "4"); print("BACK")
    elif cmd == "swipe":
        sh("shell", "input", "swipe", *sys.argv[2:7]); print("SWIPE", sys.argv[2:7])
    elif cmd == "shot":
        name = sys.argv[2]
        sh("shell", "screencap", "-p", f"/sdcard/{name}.png")
        sh("pull", f"/sdcard/{name}.png", os.path.join(SHOT_DIR, f"{name}.png"))
        print("SHOT", name)
    elif cmd == "fg":
        r = sh("shell", "dumpsys", "activity", "activities")
        for line in (r.stdout or "").splitlines():
            if "topResumedActivity" in line:
                print(line.strip()); break
    elif cmd == "clear_text":
        # ctrl+A 全选 + 删除 (模拟器支持 keyevent 27/28? 用移动光标法: 67 连发)
        sh("shell", "input", "keyevent", "--longpress", "123")  # END
        for _ in range(60):
            sh("shell", "input", "keyevent", "67")
        print("CLEARED")
    else:
        print(__doc__); sys.exit(2)

if __name__ == "__main__":
    main()
