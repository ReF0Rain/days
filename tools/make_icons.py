"""Generate legacy PNG launcher icons (mipmap-*) for the Countdown app.

Adaptive icons (API 26+) come from mipmap-anydpi-v26 XML. These raster
fallbacks keep every launcher/toolchain path happy.
"""
import os
import sys
from PIL import Image, ImageDraw

# 默认解析到 <项目根>/app/src/main/res ，也可用第一个命令行参数覆盖输出目录
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
DEFAULT_RES = os.path.join(os.path.dirname(SCRIPT_DIR), "app", "src", "main", "res")
BASE = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_RES
DENSITIES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}
BG = (21, 101, 192, 255)      # #1565C0
CARD = (255, 255, 255, 255)
HEADER = (255, 179, 0, 255)   # #FFB300
DOT = (21, 101, 192, 255)
DOT_LIGHT = (144, 202, 249, 255)


def draw_icon(size: int, round_icon: bool) -> Image.Image:
    ss = 4  # supersample for smooth edges
    s = size * ss
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    if round_icon:
        d.ellipse([0, 0, s - 1, s - 1], fill=BG)
    else:
        r = int(s * 0.18)
        d.rounded_rectangle([0, 0, s - 1, s - 1], radius=r, fill=BG)

    # calendar card
    cx0, cy0 = int(s * 0.20), int(s * 0.22)
    cx1, cy1 = int(s * 0.80), int(s * 0.80)
    d.rounded_rectangle([cx0, cy0, cx1, cy1], radius=int(s * 0.06), fill=CARD)

    # header band
    d.rounded_rectangle([cx0, cy0, cx1, cy0 + int(s * 0.13)],
                        radius=int(s * 0.06), fill=HEADER)
    d.rectangle([cx0, cy0 + int(s * 0.07), cx1, cy0 + int(s * 0.13)], fill=HEADER)

    # hangers
    d.rounded_rectangle([cx0 + int(s * 0.09), int(s * 0.15),
                         cx0 + int(s * 0.15), cy0 + int(s * 0.02)],
                        radius=int(s * 0.02), fill=CARD)
    d.rounded_rectangle([cx1 - int(s * 0.15), int(s * 0.15),
                         cx1 - int(s * 0.09), cy0 + int(s * 0.02)],
                        radius=int(s * 0.02), fill=CARD)

    # dots
    dot_r = int(s * 0.038)
    for row in range(2):
        for col in range(3):
            cxx = cx0 + int(s * 0.13) + col * int(s * 0.17)
            cyy = cy0 + int(s * 0.26) + row * int(s * 0.16)
            color = DOT if (row + col) % 2 == 0 else DOT_LIGHT
            d.ellipse([cxx - dot_r, cyy - dot_r, cxx + dot_r, cyy + dot_r], fill=color)

    return img.resize((size, size), Image.LANCZOS)


def main() -> None:
    for folder, size in DENSITIES.items():
        out = os.path.join(BASE, folder)
        os.makedirs(out, exist_ok=True)
        draw_icon(size, False).save(os.path.join(out, "ic_launcher.png"))
        draw_icon(size, True).save(os.path.join(out, "ic_launcher_round.png"))
        print("wrote", folder, size)


if __name__ == "__main__":
    main()
