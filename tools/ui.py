#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
ADB UI 小工具：按 resource-id 点按控件、输入文字、截图、抓 dump
用法:
  python tools/ui.py dump                 # 打印关键控件坐标
  python tools/ui.py tap <id后缀>          # 点按控件中心
  python tools/ui.py tapxy x y            # 点按坐标
  python tools/ui.py text <文本>           # 当前焦点输入文字（空格用 %s）
  python tools/ui.py key <keycode>        # 发送按键，如 KEYCODE_DEL
  python tools/ui.py shot <文件名>         # 截图到 shots/
  python tools/ui.py wait <秒>             # 等待
"""
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ADB = r"C:\Users\Lynn\AppData\Local\Android\Sdk\platform-tools\adb.exe"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SHOTS = os.path.join(ROOT, "shots")
PKG = "com.smhesinger.couponcalc"


def adb(*args, **kw):
    return subprocess.run([ADB] + list(args), capture_output=True, text=True,
                          encoding="utf-8", errors="replace", **kw)


def dump_tree():
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    raw = adb("shell", "cat", "/sdcard/ui.xml").stdout
    start = raw.find("<?xml")
    if start < 0:
        start = raw.find("<hierarchy")
    if start < 0:
        raise SystemExit("dump 失败: " + raw[:300])
    return ET.fromstring(raw[start:])


def bounds_of(node):
    b = node.get("bounds", "")
    m = re.findall(r"\[(\d+),(\d+)\]", b)
    if len(m) != 2:
        return None
    (x1, y1), (x2, y2) = [(int(a), int(b2)) for a, b2 in m]
    return (x1 + x2) // 2, (y1 + y2) // 2, x1, y1, x2, y2


def find(root, short_id):
    hits = []
    for n in root.iter("node"):
        rid = n.get("resource-id", "")
        if rid and (rid == short_id or rid.endswith(":id/" + short_id) or rid.endswith("/" + short_id)):
            hits.append(n)
    return hits


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return
    cmd = sys.argv[1]

    if cmd == "dump":
        root = dump_tree()
        for n in root.iter("node"):
            rid = n.get("resource-id", "")
            if not rid:
                continue
            short = rid.split("/")[-1]
            c = bounds_of(n)
            if c:
                print("%-22s (%4d,%4d)  text=%r" % (short, c[0], c[1], (n.get("text") or "")[:24]))

    elif cmd == "tap":
        root = dump_tree()
        hits = find(root, sys.argv[2])
        if not hits:
            raise SystemExit("找不到控件: " + sys.argv[2])
        c = bounds_of(hits[0])
        adb("shell", "input", "tap", str(c[0]), str(c[1]))
        print("tap %s -> (%d,%d)" % (sys.argv[2], c[0], c[1]))

    elif cmd == "taptext":
        root = dump_tree()
        want = sys.argv[2]
        hit = None
        for n in root.iter("node"):
            if (n.get("text") or "") == want:
                hit = bounds_of(n)
                break
        if not hit:
            raise SystemExit("找不到文字: " + want)
        adb("shell", "input", "tap", str(hit[0]), str(hit[1]))
        print("tap text %s -> (%d,%d)" % (want, hit[0], hit[1]))

    elif cmd == "tapxy":
        adb("shell", "input", "tap", sys.argv[2], sys.argv[3])
        print("tap (%s,%s)" % (sys.argv[2], sys.argv[3]))

    elif cmd == "text":
        adb("shell", "input", "text", sys.argv[2])
        print("text " + sys.argv[2])

    elif cmd == "key":
        adb("shell", "input", "keyevent", sys.argv[2])
        print("key " + sys.argv[2])

    elif cmd == "shot":
        os.makedirs(SHOTS, exist_ok=True)
        name = sys.argv[2] if len(sys.argv) > 2 else "shot"
        adb("shell", "screencap", "-p", "/sdcard/_s.png")
        local = os.path.join(SHOTS, name + ".png")
        adb("pull", "/sdcard/_s.png", local)
        print("shot -> " + local)

    elif cmd == "wait":
        time.sleep(float(sys.argv[2]))

    elif cmd == "clear":
        adb("shell", "pm", "clear", PKG)
        print("data cleared")

    elif cmd == "start":
        adb("shell", "am", "start", "-n", PKG + "/.MainActivity")
        print("started")

    else:
        print(__doc__)


if __name__ == "__main__":
    main()
