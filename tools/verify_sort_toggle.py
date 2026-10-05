"""Tap the app's sort toggle and verify the list order actually changes.

This is a regression check for the reported "排序切换似乎也会出问题".
It reads the on-screen text, taps the sort icon, reads again, and compares.

Usage: verify_sort_toggle.py [serial]
"""
import os
import re
import subprocess
import sys
import tempfile
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from device_text import adb, pick_serial, NODE_RE, TEXT_RE, DESC_RE, BOUNDS_RE  # noqa: E402


def read_screen(serial, tag):
    xml_path = os.path.join(tempfile.gettempdir(), "dsh_ui_{}.xml".format(tag))
    if os.path.exists(xml_path):
        os.remove(xml_path)
    adb(serial, "shell", "uiautomator", "dump", "/sdcard/dsh_ui.xml")
    res = adb(serial, "pull", "/sdcard/dsh_ui.xml", xml_path)
    adb(serial, "shell", "rm", "-f", "/sdcard/dsh_ui.xml")
    if not os.path.exists(xml_path):
        raise SystemExit("dump 失败: " + res.stderr)

    xml = open(xml_path, encoding="utf-8", errors="replace").read()
    rows = []
    for m in NODE_RE.finditer(xml):
        t = m.group(0)
        tx = TEXT_RE.search(t)
        dc = DESC_RE.search(t)
        b = BOUNDS_RE.search(t)
        if not b:
            continue
        text = (tx.group(1) if tx else "").strip()
        desc = (dc.group(1) if dc else "").strip()
        x1, y1, x2, y2 = (int(b.group(i)) for i in range(1, 5))
        rows.append({"y": y1, "x": x1, "text": text, "desc": desc,
                     "w": x2 - x1, "h": y2 - y1})
    return rows


def sort_icon_center(rows):
    for r in rows:
        if "切换排序方式" in r["desc"]:
            return (r["x"] + r["w"] // 2, r["y"] + r["h"] // 2)
    raise SystemExit("没找到排序按钮")


def subtitle(rows):
    for r in rows:
        if r["text"] in ("按天数排序", "按创建时间"):
            return r["text"]
    return "?"


def titles_in_order(rows):
    """列表里的事件标题：跳过 Hero 区（y < 全部事件 的位置）"""
    section_y = None
    for r in rows:
        if r["text"] == "全部事件":
            section_y = r["y"]
            break
    out = []
    for r in rows:
        if section_y is not None and r["y"] < section_y:
            continue
        # 卡片标题的特征：宽度较大、字号较高、不是标签词
        if r["text"] and r["h"] >= 60 and r["w"] > 200:
            out.append((r["y"], r["text"]))
    out.sort()
    return [t for _, t in out]


def main():
    serial = pick_serial(sys.argv[1] if len(sys.argv) > 1 else None)
    print("设备:", serial)

    before = read_screen(serial, "before")
    cx, cy = sort_icon_center(before)
    print("排序按钮中心: ({}, {})".format(cx, cy))
    print("点击前  副标题={!r}  列表顺序={}".format(subtitle(before), titles_in_order(before)))

    adb(serial, "shell", "input", "tap", str(cx), str(cy))
    time.sleep(2)

    after = read_screen(serial, "after")
    print("点击后  副标题={!r}  列表顺序={}".format(subtitle(after), titles_in_order(after)))

    print()
    sub_changed = subtitle(before) != subtitle(after)
    order_changed = titles_in_order(before) != titles_in_order(after)
    print("副标题变化 :", "是" if sub_changed else "否")
    print("列表顺序变化:", "是" if order_changed else "否")

    if sub_changed or order_changed:
        print()
        print("结论: 排序切换有响应")
        return 0
    print()
    print("结论: 点击排序按钮后没有任何变化 —— 需要排查")
    return 1


if __name__ == "__main__":
    sys.exit(main())
