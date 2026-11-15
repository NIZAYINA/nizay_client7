#!/usr/bin/env python3
"""Рисует иконку Nizay (монограмма N на тёмной плитке) -> launcher/icon.png и launcher/icon.ico (16/32/48/64/256)."""
import math, os, struct, zlib

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "launcher")

def clamp(x, a=0.0, b=1.0): return max(a, min(b, x))
def seg(px, py, ax, ay, bx, by):
    dx, dy = bx - ax, by - ay
    t = clamp(((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy))
    return math.hypot(px - ax - dx * t, py - ay - dy * t)
def rrect(px, py, hw, hh, r):  # signed distance скруглённого прямоугольника
    qx, qy = abs(px) - (hw - r), abs(py) - (hh - r)
    return math.hypot(max(qx, 0), max(qy, 0)) + min(max(qx, qy), 0) - r

def pixel(u, v):
    """u,v in [-1,1] -> (r,g,b,a) 0..1"""
    d = rrect(u, v, .92, .92, .42)
    a_tile = clamp(.5 - d * 32)
    if a_tile <= 0: return (0, 0, 0, 0)
    g = .075 + .09 * clamp(1 - (v + 1) / 2 * 1.4)           # вертикальный градиент плитки
    col = [g, g, g + .005]
    edge = clamp(1 - abs(d + .02) * 40) * .10               # тонкая обводка
    col = [c + edge for c in col]
    w = .115                                                # толщина штриха
    L = (-.4, -.5, -.4, .5); R = (.4, -.5, .4, .5); D = (-.4, -.5, .4, .5)
    dl, dr, dd = seg(u, v, *L) - w, seg(u, v, *R) - w, seg(u, v, *D) - w
    # диагональ серая и под вертикалями, вертикали белые поверх
    ad = clamp(.5 - dd * 40); al = clamp(.5 - dl * 40); ar = clamp(.5 - dr * 40)
    for i, c in enumerate((.50, .50, .55)): col[i] = col[i] * (1 - ad) + c * ad
    for i, c in enumerate((.93, .93, .95)): col[i] = col[i] * (1 - max(al, ar)) + c * max(al, ar)
    return (col[0], col[1], col[2], a_tile)

def render(n, ss=3):
    rows = []
    for j in range(n):
        row = []
        for i in range(n):
            acc = [0, 0, 0, 0]
            for sj in range(ss):
                for si in range(ss):
                    r, g, b, a = pixel(((i + (si + .5) / ss) / n) * 2 - 1, ((j + (sj + .5) / ss) / n) * 2 - 1)
                    acc[0] += r * a; acc[1] += g * a; acc[2] += b * a; acc[3] += a
            if acc[3] > 1e-6: rgb = [acc[k] / acc[3] for k in range(3)]
            else: rgb = [0, 0, 0]
            row += [int(clamp(rgb[0]) * 255), int(clamp(rgb[1]) * 255), int(clamp(rgb[2]) * 255), int(clamp(acc[3] / (ss * ss)) * 255)]
        rows.append(row)
    return rows

def png_bytes(n, rows):
    raw = b"".join(b"\x00" + bytes(r) for r in rows)
    def ch(t, d):
        c = struct.pack(">I", len(d)) + t + d
        return c + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)
    return b"\x89PNG\r\n\x1a\n" + ch(b"IHDR", struct.pack(">IIBBBBB", n, n, 8, 6, 0, 0, 0)) + ch(b"IDAT", zlib.compress(raw, 9)) + ch(b"IEND", b"")

sizes = [16, 32, 48, 64, 256]
imgs = {n: png_bytes(n, render(n)) for n in sizes}
open(os.path.join(OUT, "icon.png"), "wb").write(imgs[256])
# ICO с PNG-кадрами (поддерживается Windows Vista+)
hdr = struct.pack("<HHH", 0, 1, len(sizes)); off = 6 + 16 * len(sizes); ent = b""; data = b""
for n in sizes:
    b = imgs[n]; ent += struct.pack("<BBBBHHII", n % 256, n % 256, 0, 0, 1, 32, len(b), off + len(data)); data += b
open(os.path.join(OUT, "icon.ico"), "wb").write(hdr + ent + data)
print("ok")
