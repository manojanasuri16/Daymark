#!/usr/bin/env python3
"""Generate Play Store assets (icon.png, featureGraphic.png) for Daymark.

Usage: python3 tools/store_assets.py   (requires Pillow)
Art coordinates mirror app/src/main/res/drawable/ic_launcher_foreground.xml.
"""
import os
from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "fastlane/metadata/android/en-US/images")
SS = 4  # supersampling factor
TOP, BOTTOM = (0x5A, 0x63, 0xDA), (0x25, 0x2A, 0x73)
SUN = (0xF2, 0xB4, 0x8C)
# Art bounds in the 108-unit viewport (incl. stroke); centre is mapped to canvas centre.
ART_CX, ART_CY = 54.0, 52.7


def vgradient(w, h, top, bottom):
    col = Image.new("RGB", (1, h))
    for y in range(h):
        t = y / max(h - 1, 1)
        col.putpixel((0, y), tuple(round(a + (b - a) * t) for a, b in zip(top, bottom)))
    return col.resize((w, h))


def line(d, p1, p2, width, fill):
    d.line([p1, p2], fill=fill, width=round(width))
    r = width / 2
    for x, y in (p1, p2):
        d.ellipse([x - r, y - r, x + r, y + r], fill=fill)


def draw_art(size, scale_frac=0.64):
    """Return RGBA layer (size x size, transparent) with the sun/horizon art."""
    S = size * SS
    k = S * scale_frac / 52.0  # 52 units = art width incl. strokes

    def P(x, y):
        return (S / 2 + (x - ART_CX) * k, S / 2 + (y - ART_CY) * k)

    layer = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    # sun: half disc centre (54,60) r18
    cx, cy = P(54, 60)
    r = 18 * k
    d.pieslice([cx - r, cy - r, cx + r, cy + r], 180, 360, fill=SUN + (255,))
    # rays
    for a, b in [((54, 32), (54, 37)), ((38.4, 38.4), (42, 42)), ((69.6, 38.4), (66, 42)),
                 ((30, 52), (35, 52)), ((78, 52), (73, 52))]:
        line(d, P(*a), P(*b), 3.2 * k, SUN + (255,))
    # horizon
    line(d, P(30, 64), P(78, 64), 4 * k, (255, 255, 255, 255))
    # day-mark tick (55% alpha), on its own layer to avoid overlap artefacts
    tick = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    line(ImageDraw.Draw(tick), P(44, 73), P(64, 73), 4 * k, (255, 255, 255, round(255 * 0.55)))
    return Image.alpha_composite(layer, tick)


def make_icon(size=512):
    S = size * SS
    bg = vgradient(S, S, TOP, BOTTOM).convert("RGBA")
    img = Image.alpha_composite(bg, draw_art(size))
    return img.resize((size, size), Image.LANCZOS)


def find_font(size, bold=True):
    cands = [
        ("/usr/share/fonts/inter/InterVariable.ttf", "Bold" if bold else "Regular"),
        ("/usr/share/fonts/noto/NotoSans-Bold.ttf" if bold else "/usr/share/fonts/noto/NotoSans-Regular.ttf", None),
        ("/usr/share/fonts/TTF/DejaVuSans-Bold.ttf" if bold else "/usr/share/fonts/TTF/DejaVuSans.ttf", None),
    ]
    for path, var in cands:
        if os.path.exists(path):
            f = ImageFont.truetype(path, size)
            if var:
                try:
                    f.set_variation_by_name(var)
                except Exception:
                    pass
            return f
    return ImageFont.load_default()


def make_feature(w=1024, h=500):
    S = SS
    bg = vgradient(w * S, h * S, (0x4A, 0x52, 0xC4), (0x1C, 0x20, 0x5C)).convert("RGBA")
    # warm dawn glow, lower-left behind icon / towards bottom
    glow = Image.new("RGBA", (w * S, h * S), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    gx, gy, gr = int(0.62 * w * S), int(1.05 * h * S), int(0.55 * w * S)
    steps = 40
    for i in range(steps):
        t = i / steps
        rr = int(gr * (1 - t))
        gd.ellipse([gx - rr, gy - rr, gx + rr, gy + rr], fill=(0xF2, 0xB4, 0x8C, int(5 + 3 * t)))
    bg = Image.alpha_composite(bg, glow)
    img = bg.resize((w, h), Image.LANCZOS)

    # icon: rounded square 260px
    isz = 260
    icon = make_icon(512).resize((isz * 2, isz * 2), Image.LANCZOS)
    m = Image.new("L", (isz * 2, isz * 2), 0)
    ImageDraw.Draw(m).rounded_rectangle([0, 0, isz * 2 - 1, isz * 2 - 1], radius=isz * 2 * 0.22, fill=255)
    icon.putalpha(m)
    icon = icon.resize((isz, isz), Image.LANCZOS)
    ix, iy = 90, (h - isz) // 2
    shadow = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    sm = Image.new("L", (isz, isz), 0)
    ImageDraw.Draw(sm).rounded_rectangle([0, 0, isz - 1, isz - 1], radius=isz * 0.22, fill=70)
    shadow.paste((0, 0, 0, 255), (ix, iy + 8), sm)
    from PIL import ImageFilter
    shadow = shadow.filter(ImageFilter.GaussianBlur(14))
    img = Image.alpha_composite(img, shadow)
    img.alpha_composite(icon, (ix, iy))

    d = ImageDraw.Draw(img)
    tx = ix + isz + 60
    f1 = find_font(96, True)
    f2 = find_font(30, False)
    d.text((tx, 175), "Daymark", font=f1, fill=(255, 255, 255, 255), anchor="ls")
    d.text((tx, 235), "Every day you mark", font=f2, fill=(255, 255, 255, 225), anchor="ls")
    d.text((tx, 278), "is a day you made.", font=f2, fill=(255, 255, 255, 225), anchor="ls")
    # small sun-coloured accent line
    d.rounded_rectangle([tx, 305, tx + 64, 309], radius=2, fill=SUN + (255,))
    return img.convert("RGB")


def main():
    os.makedirs(OUT, exist_ok=True)
    icon = make_icon(512)
    icon.save(os.path.join(OUT, "icon.png"))
    make_feature().save(os.path.join(OUT, "featureGraphic.png"))
    for n in ("icon.png", "featureGraphic.png"):
        im = Image.open(os.path.join(OUT, n))
        print(n, im.size, im.mode)


if __name__ == "__main__":
    main()
