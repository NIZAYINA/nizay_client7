#!/usr/bin/env python3
"""Рисует все спрайты частиц с нуля (без PIL, только stdlib).
Запуск: python3 tools/gen_textures.py  -> src/main/resources/assets/nizay/{textures/particle,particles}"""
import math, os, struct, zlib, json, colorsys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "nizay")
TEX = os.path.join(ROOT, "textures", "particle")
PAR = os.path.join(ROOT, "particles")
os.makedirs(TEX, exist_ok=True); os.makedirs(PAR, exist_ok=True)
N = 64

def write_png(path, rows):
    raw = b"".join(b"\x00" + bytes(r) for r in rows)
    def chunk(t, d):
        c = struct.pack(">I", len(d)) + t + d
        return c + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)
    data = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", N, N, 8, 6, 0, 0, 0))
    data += chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    open(path, "wb").write(data)

def clamp(x, a=0.0, b=1.0): return max(a, min(b, x))
def smooth(e0, e1, x):
    t = clamp((x - e0) / (e1 - e0)); return t * t * (3 - 2 * t)
def glow(d, k): return math.exp(-d * d * k)
def hsv(h, s=1.0, v=1.0): return colorsys.hsv_to_rgb(h % 1.0, s, v)

def hsh(ix, iy):
    n = (ix * 374761393 + iy * 668265263) & 0xffffffff
    n = ((n ^ (n >> 13)) * 1274126177) & 0xffffffff
    return ((n ^ (n >> 16)) & 0xffff) / 65535.0
def vnoise(x, y):
    ix, iy = math.floor(x), math.floor(y); fx, fy = x - ix, y - iy
    fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    a, b = hsh(ix, iy), hsh(ix + 1, iy); c, d = hsh(ix, iy + 1), hsh(ix + 1, iy + 1)
    return (a + (b - a) * fx) * (1 - fy) + (c + (d - c) * fx) * fy
def fbm(x, y):
    return vnoise(x, y) * .5 + vnoise(x * 2, y * 2) * .3 + vnoise(x * 4, y * 4) * .2

def seg_dist(px, py, ax, ay, bx, by):
    dx, dy = bx - ax, by - ay
    t = clamp(((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy + 1e-9))
    return math.hypot(px - (ax + dx * t), py - (ay + dy * t))

def poly_dist(px, py, pts):
    return min(seg_dist(px, py, *pts[i], *pts[i + 1]) for i in range(len(pts) - 1))

def tri_pts(rot, r):
    return [(r * math.cos(rot + i * 2 * math.pi / 3), r * math.sin(rot + i * 2 * math.pi / 3)) for i in range(4)]

# ---------------------------------------------------------------- sprites (u,v in [-1,1]) -> (r,g,b,a)
def stardust(u, v):
    r = math.hypot(u, v); ex = math.exp(-abs(u * v) * 70) * clamp(1 - r)
    a = clamp(glow(r, 22) + ex); return (1, .92 + .06 * ex, .68, a)
def prism(u, v):
    r = math.hypot(u, v); a = glow(r - .72, 380) + .25 * glow(r - .72, 40)
    c = hsv(math.atan2(v, u) / (2 * math.pi)); return (*c, clamp(a))
def aether(u, v):
    r = math.hypot(u, v); disc = smooth(.62, .55, r)
    sh = clamp(1 - math.hypot(u + .22, v + .22) * 1.3)
    return (.45 + .5 * sh, .75 + .22 * sh, 1, clamp(disc * (.75 + .25 * sh) + glow(r, 7) * .55))
def ember(u, v):
    a_ = math.radians(35); x = u * math.cos(a_) + v * math.sin(a_); y = -u * math.sin(a_) + v * math.cos(a_)
    d = (x / .28) ** 2 + (y / .8) ** 2; core = glow(d ** .5, 5)
    return (1, .35 + .5 * core, .08 + .5 * core * core, clamp(core * 1.2))
def frost(u, v):
    r = math.hypot(u, v); th = math.atan2(v, u); seg = math.pi / 3
    f = (th % seg) - seg / 2; px, py = r * math.cos(f), abs(r * math.sin(f))
    arm = smooth(.07, .03, py) * (r < .92)
    br = 0
    for t0 in (.35, .6):
        bx, by = t0, 0; ex_, ey = t0 + .2, .2
        br = max(br, smooth(.06, .025, seg_dist(px, py, bx, by, ex_, ey)))
    a = clamp(max(arm, br) + glow(r, 12) * .3)
    return (.85, .95, 1, a)
def petal(u, v):
    y = (v + .9) / 1.8
    if y < 0 or y > 1: return (1, .7, .8, 0)
    w = .5 * (math.sin(math.pi * y ** .8) ** .8); d = w - abs(u)
    a = smooth(0, .06, d) * (1 - .25 * abs(u) / (w + 1e-3))
    return (1, .66 + .2 * y, .8 + .1 * y, clamp(a))
def pulse(u, v):
    r = math.hypot(u, v); return (1, 1, 1, clamp(glow(r - .78, 500) + glow(r - .78, 40) * .3))
def void(u, v):
    r = math.hypot(u, v); th = math.atan2(v, u) / (2 * math.pi)
    d = abs(((th * 2 + r * 2.4) % 1) - .5) * 2
    a = glow(d - .0, 14) * smooth(1, .6, r) * smooth(0, .12, r)
    return (.55 + .3 * r, .3 + .2 * r, 1, clamp(a))
def silk(u, v):
    band = .5 + .5 * math.sin(v * 22 + u * 5)
    a = smooth(1, .5, abs(u)) * smooth(.65, .2, abs(v)) * (.35 + .65 * band)
    return (.72 + .2 * band, .7 + .1 * band, 1, clamp(a))
def firefly(u, v):
    r = math.hypot(u, v); return (.75, 1, .35, clamp(glow(r, 16) + glow(r, 90) * .6))
def crystal(u, v):
    d = abs(u) * 1.9 + abs(v) * .95
    if d > .9: return (.5, .9, 1, clamp(glow(math.hypot(u, v), 20) * .25))
    sh = .6 + .4 * (u > 0) - .25 * (v > 0)
    return (.45 * sh + .3, .85 * sh + .1, 1, smooth(.9, .82, d))
def comet(u, v):
    t = clamp((u + 1) / 2); w = .06 + .22 * t * t
    a = glow(v / w, 3) * t ** 1.3 + glow(math.hypot(u - .85, v), 60)
    return (1, .72 + .27 * t, .5 + .45 * t, clamp(a))
def halo(u, v):
    d = math.sqrt((u / .95) ** 2 + (v / .32) ** 2) - 1
    return (1, .86, .5, clamp(glow(d, 900) + .4 * glow(d, 70)))
def rune(u, v):
    r = math.hypot(u, v)
    a = glow(r - .9, 800) + glow(r - .74, 900) * .8
    for rot in (math.pi / 2, -math.pi / 2):
        a = max(a, glow(poly_dist(u, v, tri_pts(rot, .72)), 1400))
    th = math.atan2(v, u)
    if .76 < r < .88 and int((th + math.pi) / (2 * math.pi) * 18) % 2 == 0: a = max(a, .55 * smooth(.88, .84, r) * smooth(.76, .8, r))
    return (1, .88, .52, clamp(a))
def mist(u, v):
    r = math.hypot(u, v); n = fbm(u * 3 + 4, v * 3 + 4)
    return (.62, .68, .82, clamp(n * smooth(1, .15, r) * 1.2))
def helix(u, v):
    r = math.hypot(u, v); return (.35, 1, .95, clamp(glow(r, 20) + glow(r, 120)))
def spark(u, v):
    pts = [(.35, -1), (-.1, -.45), (.25, -.15), (-.3, .45), (.1, .55), (-.2, 1)]
    d = poly_dist(u, v, pts)
    return (1, .97, .6, clamp(glow(d, 1400) + glow(d, 90) * .5))
def lava(u, v):
    r = math.hypot(u, v); c = glow(r, 12)
    return (1, .25 + .6 * c, .05 + .4 * c * c, clamp(smooth(.7, .5, r) * .9 + glow(r, 6) * .4))
def star(u, v):
    r = math.hypot(u, v); th = math.atan2(v, u)
    ray = (abs(math.cos(th * 2.5)) ** 18) * clamp(1 - r)
    return (.85, .92, 1, clamp(glow(r, 30) + ray))
def wisp(u, v):
    d = (u / .35) ** 2 + (((v - .15 * (1 - v)) + .1) / .75) ** 2
    tail = glow(u + .12 * math.sin(v * 5), 90) * smooth(.9, -.6, v) * (v < .3)
    return (.45, 1, .9, clamp(glow(d ** .5, 4) + tail * .5))
def bubble(u, v):
    r = math.hypot(u, v); a = glow(r - .72, 500) * .9 + smooth(.72, .2, r) * .12
    hl = glow(math.hypot(u + .3, v + .3) - .0, 80) * .9
    return (.8 + .2 * hl, .95, 1, clamp(a + hl * .8))
def gravity(u, v):
    r = math.hypot(u, v); return (.8, .85, .95, clamp(glow(r - .8, 900) + glow(r - .8, 80) * .25))
def pixel(u, v):
    inside = smooth(.62, .5, max(abs(u), abs(v))); return (.3, 1, .45, inside)
def heart(u, v):
    x, y = u * 1.25, -v * 1.25 + .15
    f = (x * x + y * y - 1) ** 3 - x * x * y ** 3
    k = clamp(-f * 4); sh = clamp(1 - math.hypot(u + .25, v + .3))
    return (1, .3 + .35 * sh, .45 + .3 * sh, smooth(0, .15, k) if f < 0 else 0)
def smoke(u, v):
    r = math.hypot(u, v); n = fbm(u * 2.5 + 9, v * 2.5 + 3)
    return (.13, .1, .17, clamp(n * smooth(1, .2, r) * 1.3))
def rainbow(u, v):
    r = math.hypot(u, v); return (1, 1, 1, clamp(glow(r, 14) + glow(r, 80) * .6))
def flare(u, v):
    r = math.hypot(u, v); th = math.atan2(v, u)
    ray = (abs(math.cos(th * 4)) ** 30) * clamp(1 - r) * .9
    return (1, .75, .35, clamp(glow(r, 26) + ray))
def moon(u, v):
    a = smooth(.62, .55, math.hypot(u, v)) * (1 - smooth(.55, .62, math.hypot(u - .3, v - .08)))
    return (.9, .93, 1, clamp(a + glow(math.hypot(u, v), 9) * .15))
def wind(u, v):
    r = math.hypot(u, v); th = math.atan2(v, u)
    arc = glow(r - .6, 260) * (th > -2.4) * (th < 1.4) * smooth(-2.4, -1.5, th) * smooth(1.4, .6, th)
    return (1, 1, 1, clamp(arc))
def confetti(u, v):
    return (1, 1, 1, smooth(.52, .44, abs(u)) * smooth(.78, .7, abs(v)))
def feather(u, v):
    # перо: вертикальное, острый кончик внизу (v=+1), стержень по центру
    y = (v + .95) / 1.9
    if y < 0 or y > 1: return (.8, .8, 1, 0)
    w = .34 * math.sin(math.pi * min(1, y * .92 + .04)) ** .7 * (1 - .15 * y)
    body = smooth(0, .05, w - abs(u))
    rib = smooth(.05, .01, abs(u)) * .8
    barb = .12 * math.sin((y * 38) + abs(u) * 30)
    t = y
    col = (.93 - .28 * t + barb, .93 - .30 * t + barb, 1 - .02 * t)
    edge = glow(w - abs(u), 600) * .8
    return (clamp(col[0] - edge * .35 + rib * .1), clamp(col[1] - edge * .3), clamp(col[2]), clamp(body))

SPRITES = {
 "stardust": stardust, "prism": prism, "aether": aether, "ember": ember, "frost": frost,
 "petal": petal, "pulse": pulse, "void": void, "silk": silk, "firefly": firefly,
 "crystal": crystal, "comet": comet, "halo": halo, "rune": rune, "mist": mist,
 "helix": helix, "spark": spark, "lava": lava, "star": star, "wisp": wisp,
 "bubble": bubble, "gravity": gravity, "pixel": pixel, "heart": heart, "smoke": smoke,
 "rainbow": rainbow, "flare": flare, "moon": moon, "wind": wind, "confetti": confetti,
 "feather": feather,
}

SS = 2  # supersampling
for name, fn in SPRITES.items():
    rows = []
    for j in range(N):
        row = []
        for i in range(N):
            acc = [0.0, 0.0, 0.0, 0.0]
            for sj in range(SS):
                for si in range(SS):
                    u = ((i + (si + .5) / SS) / N) * 2 - 1
                    v = ((j + (sj + .5) / SS) / N) * 2 - 1
                    r, g, b, a = fn(u, v)
                    acc[0] += r * a; acc[1] += g * a; acc[2] += b * a; acc[3] += a
            a = acc[3] / (SS * SS)
            if acc[3] > 1e-6: r, g, b = acc[0] / acc[3], acc[1] / acc[3], acc[2] / acc[3]
            else: r = g = b = 0
            row += [int(clamp(r) * 255), int(clamp(g) * 255), int(clamp(b) * 255), int(clamp(a) * 255)]
        rows.append(row)
    write_png(os.path.join(TEX, name + ".png"), rows)
    json.dump({"textures": ["nizay:" + name]}, open(os.path.join(PAR, name + ".json"), "w"))
print("ok:", len(SPRITES), "sprites")
