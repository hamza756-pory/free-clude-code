"""Rasterize the game's canvas art with Pillow for an offline visual check.

Mirrors public/js/render.js closely enough to eyeball compositions (outfits,
landmarks, builds, loot, HUD bars).  Run:  python3 tools/render-preview.py
Then open neon-royale/preview.png
"""
import math
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..', '..', '.pylibs'))
from PIL import Image, ImageDraw, ImageFilter  # noqa: E402

W, H = 1280, 720
HORIZON = 0.46
FOCAL = W / 2

BODY = (19, 232, 200)
ACCENT = (11, 43, 69)
TRIM = (124, 255, 58)


def mix(c, a):
    return tuple(int(v) for v in c) + (a,)


def project(x, y, z, cam):
    cy, sy = math.cos(-cam['yaw']), math.sin(-cam['yaw'])
    rx, rz = x - cam['x'], z - cam['z']
    rxr = rx * cy - rz * sy
    rzr = rx * sy + rz * cy
    if rzr < 12:
        rzr = 12
    s = FOCAL / rzr
    return (W / 2 + rxr * s, H * HORIZON - (y - cam['y']) * s, s, rzr)


def draw_outfit(d, cx, cy, scale, colors=(BODY, ACCENT, TRIM), visor=True):
    def rr(x, y, w, h, r, fill):
        d.rounded_rectangle([cx + x * scale, cy + y * scale,
                             cx + (x + w) * scale, cy + (y + h) * scale],
                            radius=r * scale, fill=fill)

    body, accent, trim = colors
    # shadow
    d.ellipse([cx - 20 * scale, cy + 45 * scale, cx + 20 * scale, cy + 56 * scale], fill=(0, 0, 0, 70))
    # legs
    rr(-13, 22, 10, 27, 3, accent)
    rr(3, 22, 10, 27, 3, accent)
    rr(-14, 44, 12, 6, 2, trim)
    rr(2, 44, 12, 6, 2, trim)
    # torso + arms
    rr(-16, -6, 32, 31, 7, body)
    rr(-12, 2, 24, 9, 4, accent)
    rr(-23, -4, 9, 26, 4, body)
    rr(14, -4, 9, 26, 4, body)
    rr(-23, 18, 9, 6, 3, trim)
    rr(14, 18, 9, 6, 3, trim)
    # head
    rr(-11, -25, 22, 20, 8, trim)
    rr(-9.5, -23.5, 19, 17, 7, body)
    if visor:
        rr(-8, -18, 16, 5, 2.5, mix(TRIM, 255))
    else:
        for ex in (-4, 4):
            d.ellipse([cx + (ex - 1.9) * scale, cy + (-17.4) * scale,
                       cx + (ex + 1.9) * scale, cy + (-13.6) * scale], fill=(27, 33, 48))
    # helmet
    d.pieslice([cx - 13.5 * scale, cy - 31.5 * scale, cx + 13.5 * scale, cy - 4.5 * scale],
               180, 360, fill=accent)
    rr(-13.5, -20, 27, 3.5, 1.5, trim)


def draw_pick(d, cx, cy, scale, c0=(19, 232, 200), c1=(11, 43, 69)):
    d.rounded_rectangle([cx - 3 * scale, cy - 6 * scale, cx + 3 * scale, cy + 38 * scale],
                        radius=3 * scale, fill=c1)
    d.polygon([(cx, cy - 30 * scale), (cx + 9 * scale, cy - 14 * scale),
               (cx, cy - 18 * scale), (cx - 9 * scale, cy - 14 * scale)], fill=c0)


def rounded_rect(d, x0, y0, x1, y1, r, fill):
    d.rounded_rectangle([x0, y0, x1, y1], radius=r, fill=fill)


def main():
    img = Image.new('RGBA', (W, H), (5, 9, 20, 255))
    # sky gradient
    sky = Image.new('RGBA', (1, H))
    for y in range(H):
        t = y / H
        if t < 0.45:
            c = (int(10 + 26 * t), int(16 + 42 * t), int(48 + 66 * t))
        elif t < 0.62:
            c = (36, 58, 114)
        else:
            c = (18, 32, 63)
        sky.putpixel((0, y), c + (255,))
    img = sky.resize((W, H))
    overlay = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(overlay)

    # ground
    hy = int(H * HORIZON)
    for y in range(hy, H):
        t = (y - hy) / (H - hy)
        d.line([(0, y), (W, y)], fill=(int(15 + 13 * t), int(42 + 27 * t), int(29 + 9 * t), 255))

    # sun glow
    glow = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    gd.ellipse([W * 0.28, H * 0.10, W * 0.72, H * 0.46], fill=(255, 214, 140, 90))
    glow = glow.filter(ImageFilter.GaussianBlur(90))
    overlay = Image.alpha_composite(overlay, glow)
    d = ImageDraw.Draw(overlay)

    cam = {'x': 0, 'y': 190, 'z': 0, 'yaw': 0}

    # ground grid
    step = 150
    for wx in range(-1800, 1801, step):
        a = project(wx, 0, -1800, cam)
        b = project(wx, 0, 1800, cam)
        if a[3] < 14 and b[3] < 14:
            continue
        d.line([(a[0], a[1]), (b[0], b[1])], fill=(120, 220, 160, 26))
    for wz in range(-1800, 1801, step):
        a = project(-1800, 0, wz, cam)
        b = project(1800, 0, wz, cam)
        if a[3] < 14 and b[3] < 14:
            continue
        d.line([(a[0], a[1]), (b[0], b[1])], fill=(120, 220, 160, 26))

    # landmarks
    for gx in range(-1500, 1500, 300):
        for gz in range(-1500, 1500, 300):
            h = ((gx * 374761393 + gz * 668265263) & 0xFFFFFFFF) / 0xFFFFFFFF
            if h < 0.55:
                continue
            base = project(gx + h * 120, 0, gz + (1 - h) * 120, cam)
            if base[3] < 40:
                continue
            fade = max(0.25, 1 - base[3] / 1700)
            pillar = h > 0.85
            top = project(gx + h * 120, 260 if pillar else 170, gz + (1 - h) * 120, cam)
            w = max(2, 34 * base[2])
            col = (90, 100, 120, int(255 * fade)) if pillar else (47, 107, 63, int(255 * fade))
            if pillar:
                d.rectangle([base[0] - w / 2, top[1], base[0] + w / 2, base[1]], fill=col)
            else:
                d.line([(base[0], base[1]), (top[0], top[1] + 60 * base[2])],
                       fill=(59, 42, 28, int(255 * fade)), width=max(2, int(8 * base[2])))
                rx, ry = 46 * base[2], 34 * base[2]
                d.ellipse([top[0] - rx, top[1] + 55 * base[2] - ry,
                           top[0] + rx, top[1] + 55 * base[2] + ry], fill=col)

    # horizon fog
    fog = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    fd = ImageDraw.Draw(fog)
    for i in range(int(H * 0.24)):
        a = int(140 * (1 - i / (H * 0.24)))
        fd.line([(0, hy - 14 + i), (W, hy - 14 + i)], fill=(74, 111, 165, a))
    overlay = Image.alpha_composite(overlay, fog)
    d = ImageDraw.Draw(overlay)

    # storm ring
    pts = []
    for i in range(65):
        a = i / 64 * math.tau
        p = project(math.cos(a) * 400, 0, math.sin(a) * 400, cam)
        pts.append((p[0], p[1]))
    d.line(pts, fill=(190, 90, 255, 200), width=4)

    # builds
    for bx, bz, kind in [(-120, 300, 'wall'), (150, 340, 'wall'), (30, 420, 'ramp')]:
        base = project(bx, 0, bz, cam)
        top = project(bx, 70, bz, cam)
        w = max(4, (120 if kind == 'wall' else 90) * base[2])
        col = (201, 154, 99, 235)
        if kind == 'ramp':
            d.polygon([(base[0] - w / 2, base[1]), (base[0] + w / 2, base[1]),
                       (top[0] + w / 2, top[1]), (top[0] - w / 2, top[1])], fill=col)
        else:
            d.rectangle([base[0] - w / 2, top[1], base[0] + w / 2, base[1]], fill=col,
                        outline=(80, 50, 25, 200), width=2)

    # loot diamonds
    for lx, lz in [(-90, 220), (110, 260), (10, 190), (200, 300)]:
        p = project(lx, 30, lz, cam)
        r = max(4, 16 * p[2])
        d.polygon([(p[0], p[1] - r), (p[0] + r * 0.7, p[1]),
                   (p[0], p[1] + r), (p[0] - r * 0.7, p[1])], fill=(255, 208, 138, 255))

    # enemies (far to near)
    for ex, ez, sc, cols in [(-330, 1000, 0.55, ((255, 107, 53), (74, 29, 15), (255, 209, 102))),
                             (300, 620, 0.75, ((169, 231, 255), (47, 111, 168), (255, 255, 255))),
                             (-90, 380, 1.0, ((180, 92, 255), (35, 27, 58), (201, 170, 255)))]:
        p = project(ex, 0, ez, cam)
        draw_outfit(d, p[0], p[1], max(0.10, min(2.6, p[2] * 0.62)), cols)



    img = Image.alpha_composite(img, overlay)

    # vignette + HUD
    hud = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(hud)
    # first-person viewmodel (rifle silhouette, bottom right)
    vx, vy = W * 0.80, H + 8
    d.rounded_rectangle([vx - 26, vy - 140, vx + 6, vy], radius=16, fill=(42, 53, 80, 255))
    d.rounded_rectangle([vx - 16, vy - 214, vx - 2, vy - 60], radius=8, fill=(255, 208, 138, 255))
    d.rounded_rectangle([vx - 12, vy - 240, vx + 12, vy - 208], radius=7, fill=(255, 208, 138, 255))
    d.rounded_rectangle([vx - 22, vy - 250, vx + 24, vy - 208], radius=26, fill=(255, 214, 150, 60))

    # crosshair
    cx, cy = W // 2, H // 2
    d.ellipse([cx - 11, cy - 11, cx + 11, cy + 11], outline=(255, 255, 255, 225), width=2)
    for dx, dy in [(-18, 0), (18, 0), (0, -18), (0, 18)]:
        d.line([(cx + dx * 0.4, cy + dy * 0.4), (cx + dx, cy + dy)], fill=(255, 255, 255, 225), width=2)
    # bottom bars
    def bar(x, y, w, h, frac, c0, c1):
        rounded_rect(d, x, y, x + w, y + h, h / 2, (0, 0, 0, 150))
        rounded_rect(d, x, y, x + w * frac, y + h, h / 2, c0)
    bar(W / 2 - 320, H - 78, 300, 18, 0.72, (58, 160, 255, 255), None)
    bar(W / 2 - 320, H - 54, 300, 18, 0.86, (43, 212, 111, 255), None)
    # minimap
    mx, my, mr = W - 100, 100, 78
    d.ellipse([mx - mr, my - mr, mx + mr, my + mr], fill=(8, 14, 26, 185),
              outline=(120, 200, 255, 90), width=2)
    d.ellipse([mx - 30, my - 30, mx + 30, my + 30], outline=(190, 90, 255, 230), width=2)
    d.ellipse([mx - 4, my - 4, mx + 4, my + 4], fill=(77, 227, 255, 255))
    for i in range(9):
        a = i * 0.7
        d.ellipse([mx + math.cos(a) * 50 - 2, my + math.sin(a) * 45 - 2,
                   mx + math.cos(a) * 50 + 2, my + math.sin(a) * 45 + 2], fill=(255, 90, 110, 220))

    img = Image.alpha_composite(img, hud)
    out = os.path.join(os.path.dirname(__file__), '..', 'preview.png')
    img.convert('RGB').save(out, quality=92)
    print('wrote', os.path.abspath(out))


if __name__ == '__main__':
    main()
