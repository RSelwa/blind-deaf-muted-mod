#!/usr/bin/env python3
"""Single source of truth for the mod's custom-model UV layouts.

For every code-built ModelPart accessory it (1) writes the native-resolution
texture template into the mod resources (filled with the current placeholder
colour so the in-game look is unchanged until you repaint) and (2) writes a big
labelled UV guide into ``uv-guides/`` so you can see which texture pixels map to
which face of which box.

The UV offsets here MUST stay in sync with the ``.uv(...)`` calls and
``TexturedModelData.of(..)`` sizes in the Java feature renderers. Change one,
change the other.

Minecraft box unwrap for a box of size (w,h,d) at texture offset (u,v):
    up     = (u+d,      v,     w, d)
    down   = (u+d+w,    v,     w, d)
    east   = (u,        v+d,   d, h)   # +X / right
    north  = (u+d,      v+d,   w, h)   # -Z / the face you look at
    west   = (u+d+w,    v+d,   d, h)   # -X / left
    south  = (u+2d+w,   v+d,   w, h)   # +Z / back
"""
import math
import os

from PIL import Image, ImageDraw, ImageFont

RES = "mod/src/main/resources/assets/blind-deaf-muted/textures"
GUIDES = "uv-guides"

# (r,g,b,a) placeholder fills — match the current flat-colour PNGs.
GLASSES = (38, 38, 45, 255)
BANDAGE = (254, 85, 61, 255)
ORANGE = (255, 75, 55, 255)      # headset cups + megaphone body
BLACK = (10, 10, 10, 255)        # headset band

# Each model: output texture path, size (w,h), fill colour, and its boxes.
# A box = (name, w, h, d, u, v)  — dims in px, (u,v) is the UV offset.
MODELS = {
    "bandage": {
        "tex": f"{RES}/entity/bandage.png", "size": (16, 16), "fill": BANDAGE,
        "boxes": [
            ("strip_a", 5, 1, 1, 0, 0),   # depth 0.6 -> 1px footprint
            ("strip_b", 5, 1, 1, 0, 3),
        ],
    },
    "headset_cups": {
        "tex": f"{RES}/entity/headset_cups.png", "size": (16, 32), "fill": ORANGE,
        "boxes": [
            ("left_cup", 2, 5, 5, 0, 0),
            ("right_cup", 2, 5, 5, 0, 11),
        ],
    },
    "headset_band": {
        "tex": f"{RES}/entity/headset_band.png", "size": (32, 16), "fill": BLACK,
        "boxes": [
            ("band", 12, 2, 3, 0, 0),      # height 1.5 -> 2px footprint
            ("left_connector", 1, 1, 2, 0, 6),
            ("right_connector", 1, 1, 2, 7, 6),
        ],
    },
}

# Distinct outline colour per box in the guide.
BOX_COLOURS = [
    (231, 76, 60), (46, 204, 113), (52, 152, 219),
    (241, 196, 15), (155, 89, 182), (26, 188, 156),
]


def faces(w, h, d, u, v):
    """The six (name, x, y, fw, fh) face rects for a box at (u,v)."""
    return [
        ("up", u + d, v, w, d),
        ("down", u + d + w, v, w, d),
        ("east", u, v + d, d, h),
        ("north", u + d, v + d, w, h),
        ("west", u + d + w, v + d, d, h),
        ("south", u + 2 * d + w, v + d, w, h),
    ]


def write_texture(cfg):
    if cfg["tex"] is None:
        return
    os.makedirs(os.path.dirname(cfg["tex"]), exist_ok=True)
    img = Image.new("RGBA", cfg["size"], (0, 0, 0, 0))
    px = img.load()
    # Fill only the pixels covered by a face so unused texture stays transparent
    # (keeps the atlas tidy and shows the layout even in the native file).
    for (_n, w, h, d, u, v) in cfg["boxes"]:
        for (_fn, x, y, fw, fh) in faces(w, h, d, u, v):
            for yy in range(int(y), int(math.ceil(y + fh))):
                for xx in range(int(x), int(math.ceil(x + fw))):
                    if 0 <= xx < cfg["size"][0] and 0 <= yy < cfg["size"][1]:
                        px[xx, yy] = cfg["fill"]
    img.save(cfg["tex"])
    print("texture", cfg["tex"], cfg["size"])


def write_guide(name, cfg):
    os.makedirs(GUIDES, exist_ok=True)
    scale = 28
    W, H = cfg["size"]
    pad = 90
    img = Image.new("RGBA", (W * scale + pad, H * scale + pad + 30), (250, 250, 250, 255))
    d = ImageDraw.Draw(img)
    try:
        font = ImageFont.truetype("arial.ttf", 13)
        big = ImageFont.truetype("arial.ttf", 18)
    except Exception:
        font = ImageFont.load_default()
        big = font
    ox, oy = pad // 2, pad // 2 + 20
    d.text((ox, 8), f"{name}  ({W}x{H})  1 cell = 1 texel", fill=(20, 20, 20), font=big)

    # texel grid
    for gx in range(W + 1):
        d.line([(ox + gx * scale, oy), (ox + gx * scale, oy + H * scale)], fill=(225, 225, 225))
    for gy in range(H + 1):
        d.line([(ox, oy + gy * scale), (ox + W * scale, oy + gy * scale)], fill=(225, 225, 225))
    # ruler numbers every 2
    for gx in range(0, W + 1, 2):
        d.text((ox + gx * scale - 4, oy - 16), str(gx), fill=(150, 150, 150), font=font)
    for gy in range(0, H + 1, 2):
        d.text((ox - 20, oy + gy * scale - 7), str(gy), fill=(150, 150, 150), font=font)

    for i, (bn, w, h, dd, u, v) in enumerate(cfg["boxes"]):
        col = BOX_COLOURS[i % len(BOX_COLOURS)]
        for (fn, x, y, fw, fh) in faces(w, h, dd, u, v):
            x0, y0 = ox + x * scale, oy + y * scale
            x1, y1 = ox + (x + fw) * scale, oy + (y + fh) * scale
            fillc = col + (55,) if fn == "north" else col + (28,)
            d.rectangle([x0, y0, x1, y1], fill=fillc, outline=col, width=2)
            cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
            d.text((cx - 12, cy - 7), fn[0].upper(), fill=col, font=font)
        # box label at its footprint top-left
        lx = ox + (u) * scale
        ly = oy + v * scale
        d.text((lx + 2, ly + 2), f"{bn} {w}x{h}x{dd}", fill=col, font=font)

    # legend: face-letter key
    key = "U=up  D=down  N=north(front, faces -Z)  S=south(back)  E=east(+X)  W=west(-X)"
    d.text((ox, oy + H * scale + 12), key, fill=(80, 80, 80), font=font)
    out = f"{GUIDES}/{name}.png"
    img.save(out)
    print("guide  ", out)


for nm, c in MODELS.items():
    write_texture(c)
    write_guide(nm, c)
