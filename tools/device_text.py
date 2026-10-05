"""Dump the on-screen text of a connected Android device, sorted top-to-bottom.

Why this exists: inlining Python in PowerShell keeps mangling quotes. Keeping it
in a file makes the workflow reliable.

Usage: device_text.py [serial] [--dump-raw]
"""
import argparse
import os
import re
import subprocess
import sys
import tempfile

ADB = os.path.join(os.environ.get("TEMP", "."), "android-sdk", "platform-tools", "adb.exe")
NODE_RE = re.compile(r"<node[^>]*?/?>")
TEXT_RE = re.compile(r'text="([^"]*)"')
DESC_RE = re.compile(r'content-desc="([^"]*)"')
BOUNDS_RE = re.compile(r'bounds="\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]"')


def adb(serial, *args):
    cmd = [ADB]
    if serial:
        cmd += ["-s", serial]
    cmd += list(args)
    return subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8",
                          errors="replace")


def pick_serial(serial):
    if serial:
        return serial
    out = adb(None, "devices").stdout
    ready = []
    for line in out.splitlines()[1:]:
        parts = line.split()
        if len(parts) >= 2 and parts[1] == "device":
            ready.append(parts[0])
    if not ready:
        raise SystemExit("没有可用设备")
    return ready[0]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("serial", nargs="?", default=None)
    ap.add_argument("--dump-raw", action="store_true", help="只保存 hierarchy.xml 路径")
    ap.add_argument("--switches", action="store_true",
                    help="只列出 Switch/CheckBox 的中心坐标与 checked 状态（用于 UI 自动化点击）")
    ap.add_argument("--find", default=None, help="只列出 text 或 content-desc 包含该子串的节点")
    args = ap.parse_args()

    serial = pick_serial(args.serial)

    # adb pull 不能写中文路径，所以先落到 TEMP
    xml_path = os.path.join(tempfile.gettempdir(), "dsh_ui_dump.xml")
    if os.path.exists(xml_path):
        os.remove(xml_path)

    adb(serial, "shell", "uiautomator", "dump", "/sdcard/dsh_ui.xml")
    res = adb(serial, "pull", "/sdcard/dsh_ui.xml", xml_path)
    adb(serial, "shell", "rm", "-f", "/sdcard/dsh_ui.xml")

    if not os.path.exists(xml_path):
        print("dump 失败:", res.stdout, res.stderr)
        return 1

    if args.dump_raw:
        print(xml_path)
        return 0

    xml = open(xml_path, encoding="utf-8", errors="replace").read()

    # --switches：给 UI 自动化用，输出可直接拿来 input tap 的坐标
    if args.switches:
        found = False
        for m in NODE_RE.finditer(xml):
            tag = m.group(0)
            if "Switch" not in tag and "CheckBox" not in tag:
                continue
            b = BOUNDS_RE.search(tag)
            if not b:
                continue
            x1, y1, x2, y2 = (int(b.group(i)) for i in range(1, 5))
            checked = re.search(r'checked="(\w+)"', tag)
            desc = DESC_RE.search(tag)
            print("center=({},{}) checked={} size={}x{} desc={}".format(
                (x1 + x2) // 2, (y1 + y2) // 2,
                checked.group(1) if checked else "?",
                x2 - x1, y2 - y1,
                desc.group(1) if desc else ""))
            found = True
        if not found:
            print("当前界面没有 Switch/CheckBox")
        return 0

    # --find：定位某个文字/描述的坐标
    if args.find:
        needle = args.find
        for m in NODE_RE.finditer(xml):
            tag = m.group(0)
            t = TEXT_RE.search(tag)
            d = DESC_RE.search(tag)
            text = (t.group(1) if t else "")
            desc = (d.group(1) if d else "")
            if needle not in text and needle not in desc:
                continue
            b = BOUNDS_RE.search(tag)
            if not b:
                continue
            x1, y1, x2, y2 = (int(b.group(i)) for i in range(1, 5))
            print("center=({},{}) text={!r} desc={!r} size={}x{}".format(
                (x1 + x2) // 2, (y1 + y2) // 2, text, desc, x2 - x1, y2 - y1))
        return 0

    screen = None
    m = re.search(r'bounds="\[0,0\]\[(\d+),(\d+)\]"', xml)
    if m:
        screen = (int(m.group(1)), int(m.group(2)))

    rows = []
    for m in NODE_RE.finditer(xml):
        tag = m.group(0)
        t = TEXT_RE.search(tag)
        d = DESC_RE.search(tag)
        b = BOUNDS_RE.search(tag)
        if not b:
            continue
        text = (t.group(1) if t else "").strip()
        if not text and d:
            text = "(" + d.group(1).strip() + ")"
        if not text:
            continue
        x1, y1, x2, y2 = (int(b.group(i)) for i in range(1, 5))
        rows.append({
            "y": y1, "x": x1, "text": text,
            "w": x2 - x1, "h": y2 - y1, "right": x2,
        })

    rows.sort(key=lambda r: (r["y"], r["x"]))
    if screen:
        print("屏幕 {}x{}".format(*screen))
    print("--- 屏幕文字（从上到下）---")
    for r in rows:
        print("  y={:<5} x={:<5} {}x{:<5} {}".format(r["y"], r["x"], r["w"], r["h"], r["text"]))

    # 排版异常自查
    if screen:
        sw, sh = screen
        area = sw * sh
        kids = [r for r in rows if (r["w"] * r["h"]) < area * 0.8]
        problems = []
        for r in kids:
            if r["x"] < 0 or r["right"] > sw:
                problems.append("[越界] {} 右={} 屏宽={}".format(r["text"], r["right"], sw))
            if r["w"] < 24 and len(r["text"]) > 1:
                problems.append("[挤压] {} 宽={}".format(r["text"], r["w"]))
        print()
        if problems:
            print("--- 排版异常 ---")
            for p in problems:
                print("  " + p)
        else:
            print("--- 未发现越界/挤压 ---")
    return 0


if __name__ == "__main__":
    sys.exit(main())
