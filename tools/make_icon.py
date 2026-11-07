#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""生成消费券计算器的启动图标（各密度 PNG + Android 8+ 自适应图标前景）"""
import os
from PIL import Image, ImageDraw, ImageFont

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(BASE, "app", "res")

BLUE_L = (63, 125, 255)
BLUE_R = (44, 107, 237)


def gradient(size):
    w = h = size
    img = Image.new("RGB", (w, h), BLUE_R)
    d = ImageDraw.Draw(img)
    for x in range(w):
        t = x / max(1, w - 1)
        c = tuple(int(BLUE_L[i] + (BLUE_R[i] - BLUE_L[i]) * t) for i in range(3))
        d.line([(x, 0), (x, h)], fill=c)
    return img


def rounded_mask(size, radius):
    m = Image.new("L", (size, size), 0)
    ImageDraw.Draw(m).rounded_rectangle([0, 0, size - 1, size - 1], radius=radius, fill=255)
    return m


def ticket_mask(size):
    """票券遮罩：圆角矩形挖掉左右两个半圆缺口"""
    m = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(m)
    x0, y0, x1, y1 = int(size * 0.17), int(size * 0.30), int(size * 0.83), int(size * 0.70)
    d.rounded_rectangle([x0, y0, x1, y1], radius=int(size * 0.055), fill=255)
    r = int(size * 0.075)
    cy = (y0 + y1) // 2
    d.ellipse([x0 - r, cy - r, x0 + r, cy + r], fill=0)
    d.ellipse([x1 - r, cy - r, x1 + r, cy + r], fill=0)
    return m


def draw_glyph(img, size, cx, cy, txt_h, color):
    d = ImageDraw.Draw(img)
    try:
        font = ImageFont.truetype("C:/Windows/Fonts/arialbd.ttf", txt_h)
    except Exception:
        font = ImageFont.truetype("C:/Windows/Fonts/msyhbd.ttc", txt_h)
    d.text((cx, cy), "¥", font=font, fill=color, anchor="mm")


def legacy_icon(size=1024):
    bg = gradient(size)
    out = bg.convert("RGBA")
    out.putalpha(rounded_mask(size, int(size * 0.22)))
    tm = ticket_mask(size)
    white = Image.new("RGBA", (size, size), (255, 255, 255, 255))
    out.paste(white, (0, 0), tm)
    draw_glyph(out, size, size // 2, size // 2, int(size * 0.30), (44, 107, 237, 255))
    return out


def adaptive_fg(size=1024):
    """自适应图标前景：透明底 + 白色票券（位于安全区内）"""
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    tm = ticket_mask(size)
    white = Image.new("RGBA", (size, size), (255, 255, 255, 255))
    out.paste(white, (0, 0), tm)
    draw_glyph(out, size, size // 2, size // 2, int(size * 0.26), (44, 107, 237, 255))
    return out


DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}


def main():
    master = legacy_icon(1024)
    fg_master = adaptive_fg(1024)
    for name, scale in DENSITIES.items():
        folder = os.path.join(RES, "mipmap-" + name)
        os.makedirs(folder, exist_ok=True)
        px = int(48 * scale)
        master.resize((px, px), Image.LANCZOS).save(os.path.join(folder, "ic_launcher.png"))
        # 自适应图标前景尺寸：108dp
        fpx = int(108 * scale)
        fg_master.resize((fpx, fpx), Image.LANCZOS).save(os.path.join(folder, "ic_launcher_fg.png"))
        print("  %-8s icon %dpx / fg %dpx" % (name, px, fpx))
    # 商店/预览用大图
    preview = os.path.join(BASE, "out")
    os.makedirs(preview, exist_ok=True)
    master.resize((512, 512), Image.LANCZOS).save(os.path.join(preview, "icon-512.png"))
    print("done")


if __name__ == "__main__":
    main()
