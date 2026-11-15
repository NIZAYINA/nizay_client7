#!/usr/bin/env python3
"""Рисует кубические карты неба (раскладка 3x2 как у Nuit) для Nizay client.
Каждая точка текстуры считается по направлению взгляда, поэтому швы между гранями бесшовные.
Запуск: python3 tools/gen_skies.py   (нужны numpy и Pillow)
"""
import os, math
import numpy as np
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "nizay", "textures", "sky")
os.makedirs(OUT, exist_ok=True)

# ---------- геометрия граней: те же матрицы, что рисует мод (Utils.getMatrixForRotatedFace) ----------
def rx(a):
    c, s = math.cos(a), math.sin(a); return np.array([[1,0,0],[0,c,-s],[0,s,c]], np.float64)
def ry(a):
    c, s = math.cos(a), math.sin(a); return np.array([[c,0,s],[0,1,0],[-s,0,c]], np.float64)
def rz(a):
    c, s = math.cos(a), math.sin(a); return np.array([[c,-s,0],[s,c,0],[0,0,1]], np.float64)
D = math.radians
FACE_M = [np.eye(3), rx(D(90)), rx(D(-90)) @ ry(D(180)), rx(D(180)), rz(D(90)) @ ry(D(-90)), rz(D(-90)) @ ry(D(90))]
# (колонка, строка) плитки в атласе 3x2: bottom, north, south, top, east, west
FACE_TILE = [(0,0), (1,1), (2,0), (1,0), (2,1), (0,1)]

def face_dirs(F):
    u = (np.arange(F) + .5) / F * 2 - 1
    X, Z = np.meshgrid(u, u)                  # строка = z (v), колонка = x (u)
    P = np.stack([X, -np.ones_like(X), Z], -1).reshape(-1, 3)
    return P

# ---------- шум ----------
def hash3(ix, iy, iz, seed=0):
    h = (ix.astype(np.int64) * 374761393 + iy.astype(np.int64) * 668265263 + iz.astype(np.int64) * 2147483647 + seed * 1274126177) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    h = h ^ (h >> 16)
    return (h & 0xFFFFFF).astype(np.float32) / float(0x1000000)

def vnoise(p, seed=0):
    i = np.floor(p).astype(np.int64); f = (p - i).astype(np.float32)
    f = f * f * (3 - 2 * f)
    out = 0
    for dx in (0, 1):
        for dy in (0, 1):
            for dz in (0, 1):
                w = (f[:,0] if dx else 1-f[:,0]) * (f[:,1] if dy else 1-f[:,1]) * (f[:,2] if dz else 1-f[:,2])
                out = out + w * hash3(i[:,0]+dx, i[:,1]+dy, i[:,2]+dz, seed)
    return out

def fbm(p, oct=5, seed=0, lac=2.0, gain=.5):
    a, s, tot = 1.0, 0.0, 0.0
    for o in range(oct):
        s = s + a * vnoise(p * (lac ** o), seed + o * 17); tot += a; a *= gain
    return s / tot

def smooth(a, b, x):
    t = np.clip((x - a) / (b - a), 0, 1); return t * t * (3 - 2 * t)

def norm(v):
    return v / np.linalg.norm(v, axis=-1, keepdims=True)

def stars(d, R=40.0, prob=.05, seed=1, sig=.33, bright=1.0, tint=True, mask=None):
    """Звёзды: ячейки 3D-сетки, проецируем на сферу. Возвращает (N,3)."""
    p = d * R
    base = np.floor(p).astype(np.int64)
    col = np.zeros((len(d), 3), np.float32)
    for ox in (-1, 0, 1):
        for oy in (-1, 0, 1):
            for oz in (-1, 0, 1):
                cx, cy, cz = base[:,0]+ox, base[:,1]+oy, base[:,2]+oz
                h0 = hash3(cx, cy, cz, seed)
                has = h0 < prob
                if not has.any(): continue
                idx = np.nonzero(has)[0]
                cxi, cyi, czi = cx[idx], cy[idx], cz[idx]
                sp = np.stack([cxi + hash3(cxi, cyi, czi, seed+11), cyi + hash3(cxi, cyi, czi, seed+12), czi + hash3(cxi, cyi, czi, seed+13)], -1).astype(np.float32)
                shell = np.abs(np.linalg.norm(sp, axis=-1) - R) < .35     # только звёзды у самой оболочки -> без обрезанных форм
                if not shell.any(): continue
                idx = idx[shell]; cxi, cyi, czi, sp = cxi[shell], cyi[shell], czi[shell], sp[shell]
                q = sp / np.linalg.norm(sp, axis=-1, keepdims=True) * R
                dist2 = ((p[idx] - q) ** 2).sum(-1)
                b = hash3(cxi, cyi, czi, seed+21)
                size = min(sig, .22) * (.55 + .6 * b ** 3)
                inten = np.exp(-dist2 / (2 * size * size)) * (.22 + 1.3 * b ** 2.5) * bright
                if mask is not None: inten = inten * mask[idx]
                t = hash3(cxi, cyi, czi, seed+31)
                if tint:
                    c = np.stack([1 - .25 * t, 1 - .1 * np.abs(t - .5), .8 + .35 * t], -1)
                else:
                    c = np.ones((len(idx), 3), np.float32)
                col[idx] += c * inten[:, None]
    return col

def grad(e, stops):
    """Вертикальный градиент по высоте e (−1..1). stops = [(e, (r,g,b)), ...]."""
    es = [s[0] for s in stops]; out = np.zeros((len(e), 3), np.float32)
    for k in range(3):
        out[:, k] = np.interp(e, es, [s[1][k] for s in stops])
    return out

def tonemap(c):
    return 1 - np.exp(-c * 1.35) if False else np.clip(c, 0, 1)

def save(name, F, fn, dither=False):
    img = np.zeros((2 * F, 3 * F, 3), np.float32)
    P = face_dirs(F)
    for fi in range(6):
        d = norm((P @ FACE_M[fi].T)).astype(np.float32)
        c = fn(d).reshape(F, F, 3)
        tx, ty = FACE_TILE[fi]
        img[ty*F:(ty+1)*F, tx*F:(tx+1)*F] = c
    if dither:
        rng = np.random.default_rng(5)
        img = img + (rng.random(img.shape, dtype=np.float32) - .5) / 255.0
    arr = (np.clip(img, 0, 1) * 255 + .5).astype(np.uint8)
    im = Image.fromarray(arr, "RGB").convert("RGBA")
    im.save(os.path.join(OUT, name + ".png"), optimize=True)
    with open(os.path.join(OUT, name + ".png.mcmeta"), "w") as f:
        f.write('{"texture":{"blur":true,"clamp":true}}')
    print("ok", name, os.path.getsize(os.path.join(OUT, name + ".png")) // 1024, "KB")

# ---------- небеса ----------
def milky(d, axis, seed):
    n = norm(np.array(axis, np.float32))
    h = d @ n
    w = np.exp(-(h / .2) ** 2)
    f = fbm(d * 3.0, 5, seed); g = fbm(d * 9.0 + 5, 4, seed + 3)
    dust = smooth(.45, .75, fbm(d * 5.5 + 11, 5, seed + 9))
    return w, f, g, dust

def sky_deep_space(F, variant):
    seed = 100
    def fn(d):
        e = d[:, 1]
        w, f, g, dust = milky(d, (0.28, 0.55, 0.78), seed)
        if variant == "base":
            bg = grad(e, [(-1, (.012,.012,.03)), (0, (.03,.025,.06)), (1, (.012,.012,.035))])
            band = (w * (.35 + .9 * f) * (1 - .78 * dust) * (.55 + .6 * g))[:, None] * np.array([.9, .82, 1.0], np.float32)
            col = bg + band * .85
            col += stars(d, 46, .075, 1, .30, .9) * .75
            col += stars(d, 90, .06, 2, .28, .8, mask=None) * (.35 + 1.5 * w)[:, None] * .65
            # планета с кольцом
            cp = norm(np.array([.62, .36, -.70], np.float32)); r0 = math.tan(.085)
            up = np.array([0, 1, 0], np.float32); e1 = norm(np.cross(up, cp)); e2 = np.cross(cp, e1)
            dc = d @ cp; ok = dc > 0.05
            a = np.where(ok, (d @ e1) / np.where(ok, dc, 1), 9); b = np.where(ok, (d @ e2) / np.where(ok, dc, 1), 9)
            rad = np.sqrt(a*a + b*b)
            th = math.radians(-18)
            ra = a * math.cos(th) + b * math.sin(th); rb = (-a * math.sin(th) + b * math.cos(th)) / .26
            rr = np.sqrt(ra*ra + rb*rb) / r0
            ring_pat = (.55 + .45 * np.sin(rr * 38)) * smooth(1.32, 1.42, rr) * (1 - smooth(2.1, 2.2, rr)) * (1 - .8 * ((rr > 1.78) & (rr < 1.86)))
            ring_col = np.array([.78, .70, .56], np.float32)
            disc = rad < r0
            # задняя часть кольца (rb < 0) под планетой
            back = (rb < 0) & (~disc)
            col = np.where(back[:, None], col * (1 - ring_pat[:, None] * .85) + ring_col * ring_pat[:, None] * .85, col)
            nx, ny = a / r0, b / r0; nz = np.sqrt(np.clip(1 - nx*nx - ny*ny, 0, 1))
            L = norm(np.array([-.55, .5, .65], np.float32))
            lam = np.clip(nx*L[0] + ny*L[1] + nz*L[2], 0, 1) * .95 + .05
            bands = .5 + .5 * np.sin(ny * 11 + fbm(np.stack([nx*3, ny*3, np.zeros_like(nx)], -1), 3, 7) * 3.5)
            pc = np.array([.82, .66, .42], np.float32) * bands[:, None] + np.array([.52, .38, .26], np.float32) * (1 - bands[:, None])
            pc = pc * lam[:, None]
            edge = smooth(r0, r0 * .96, rad)
            col = np.where(disc[:, None], pc, col)
            front = (rb > 0)
            col = np.where(front[:, None], col * (1 - ring_pat[:, None] * .9) + ring_col * ring_pat[:, None] * .9, col)
            return tonemap(col)
        # слои мерцания: только звёзды
        s = 3 if variant == "fx1" else 4
        col = stars(d, 52, .09, s * 10, .34, 1.0) * .9
        return tonemap(col)
    return fn

def sky_nebula(F, variant):
    def fn(d):
        e = d[:, 1]
        if variant == "base":
            bg = grad(e, [(-1, (.01,.005,.025)), (0, (.02,.01,.04)), (1, (.015,.008,.035))])
            n1 = fbm(d * 2.2, 6, 21); n2 = fbm(d * 2.2 + 7.3, 6, 33); n3 = fbm(d * 3.4 + 3.1, 5, 47)
            vio = smooth(.46, .74, n1)[:, None] * np.array([.62, .22, .82], np.float32)
            teal = smooth(.5, .78, n2)[:, None] * np.array([.08, .52, .56], np.float32)
            rose = smooth(.52, .8, n3)[:, None] * np.array([.95, .38, .62], np.float32)
            gas = vio * 1.05 + teal * 1.0 + rose * .85
            col = bg + gas * (.55 + .7 * fbm(d * 7, 4, 5))[:, None]
            col += stars(d, 46, .075, 5, .30, .9) * .85 + stars(d, 90, .05, 6, .27, .8) * .5
            return tonemap(col)
        off = 4.0 if variant == "fx1" else 9.0
        n1 = fbm(d * 2.6 + off, 6, 61 + int(off)); n2 = fbm(d * 3.0 + off * 1.7, 5, 71)
        col = (smooth(.5, .8, n1)[:, None] * np.array([.55, .25, .85], np.float32) + smooth(.55, .85, n2)[:, None] * np.array([.1, .6, .7], np.float32)) * .45
        return tonemap(col)
    return fn

def aurora_layer(d, seed, shift):
    phi = np.arctan2(d[:, 2], d[:, 0]); e = np.arcsin(np.clip(d[:, 1], -1, 1))
    e0 = .30 + .13 * np.sin(2 * phi + 1.2 + shift) + .06 * np.sin(5 * phi + shift * 1.7) + .03 * np.sin(11 * phi)
    rel = e - e0
    up = np.exp(-np.clip(rel, 0, None) / .22) * (rel > 0)
    low = np.exp(-(np.clip(-rel, 0, None) / .035) ** 2) * (rel <= 0)
    prof = up + low
    p = np.stack([np.cos(phi) * 14, np.sin(phi) * 14, e * 1.2], -1)
    stri = .55 + .6 * fbm(np.stack([phi * 22 + shift, e * 3.0, np.zeros_like(e)], -1) , 4, seed)
    stri2 = .6 + .5 * fbm(np.stack([phi * 7 + shift * 2, e * 1.5, np.ones_like(e)], -1), 3, seed + 5)
    amp = prof * stri * stri2 * smooth(-.25, .1, e) * (1 - smooth(.7, 1.1, e))
    t = np.clip(rel / .5, 0, 1)
    c0 = np.array([.12, 1.0, .55], np.float32); c1 = np.array([.1, .75, .85], np.float32); c2 = np.array([.55, .25, .9], np.float32)
    col = (c0 * (1 - t)[:, None] + c1 * smooth(0, .5, t)[:, None]) * (1 - smooth(.5, 1, t))[:, None] + c2 * smooth(.45, 1, t)[:, None]
    return col * amp[:, None] * 1.15

def sky_aurora(F, variant):
    def fn(d):
        e = d[:, 1]
        if variant == "base":
            bg = grad(e, [(-1, (.005,.02,.03)), (0, (.01,.045,.06)), (.5, (.008,.02,.045)), (1, (.004,.008,.025))])
            col = bg + stars(d, 46, .075, 8, .30, .9) * .85 + stars(d, 90, .04, 9, .27, .8) * .5
            return tonemap(col)
        return tonemap(aurora_layer(d, 3 if variant == "fx1" else 8, 0.0 if variant == "fx1" else 1.1))
    return fn

def sky_eclipse(F, variant):
    s = norm(np.array([.18, .56, .81], np.float32)); r0 = .075
    def fn(d):
        e = d[:, 1]; ang = np.arccos(np.clip(d @ s, -1, 1))
        if variant == "base":
            bg = grad(e, [(-1, (.015,.012,.03)), (0, (.05,.04,.085)), (1, (.012,.01,.03))])
            col = bg + stars(d, 44, .08, 12, .30, .9) * .8
            col += np.exp(-np.clip(ang - r0, 0, None) / .02)[:, None] * np.array([1., .9, .7], np.float32) * (ang > r0)[:, None] * .5
            col = np.where((ang < r0)[:, None], 0, col)
            return tonemap(col)
        # корона: радиальные лучи + ореол
        up = np.array([0, 1, 0], np.float32); e1 = norm(np.cross(up, s)); e2 = np.cross(s, e1)
        az = np.arctan2(d @ e2, d @ e1)
        sh = 0 if variant == "fx1" else 2.2
        rays = .55 + .45 * np.sin(az * (14 if variant == "fx1" else 9) + sh + 2 * fbm(np.stack([az * 3, np.zeros_like(az), np.full_like(az, sh)], -1), 3, 5))
        glow = np.exp(-np.clip(ang - r0, 0, None) / (.05 if variant == "fx1" else .075)) * (ang > r0)
        halo = np.exp(-np.clip(ang - r0, 0, None) / .22) * (ang > r0)
        k = (glow * (.6 + .5 * rays) + halo * .22)
        col = k[:, None] * np.array([1., .88, .66], np.float32)
        return tonemap(col)
    return fn

def sky_crystal_dusk(F, variant):
    def fn(d):
        e = d[:, 1]
        phi = np.arctan2(d[:, 2], d[:, 0])
        if variant == "base":
            bg = grad(e, [(-1, (.55,.33,.5)), (-.02, (.72,.45,.55)), (.12, (.55,.36,.6)), (.4, (.23,.2,.52)), (1, (.1,.1,.34))])
            q = np.stack([d[:, 0] * 2.2, e * 16, d[:, 2] * 2.2], -1)
            streak = smooth(.52, .8, fbm(q, 5, 81)) * smooth(-.05, .25, e) * (1 - smooth(.6, .95, e))
            col = bg + streak[:, None] * np.array([.5, .42, .62], np.float32) * .75
            col += stars(d, 48, .06, 14, .30, .8) * smooth(-.05, .3, e)[:, None] * .8
            return tonemap(col)
        s = 15 if variant == "fx1" else 16
        col = stars(d, 56, .07, s, .42, 1.6, tint=True) * smooth(0, .35, e)[:, None]
        return tonemap(col)
    return fn

def sky_sunset(F, variant):
    sdir = norm(np.array([.3, .04, .95], np.float32))
    def fn(d):
        e = d[:, 1]
        phi = np.arctan2(d[:, 2], d[:, 0])
        bg = grad(e, [(-1, (.95,.55,.3)), (-.02, (1.0,.62,.3)), (.05, (1.0,.5,.3)), (.22, (.9,.36,.42)), (.5, (.5,.2,.55)), (1, (.26,.12,.42))])
        q = np.stack([d[:, 0] * 1.6, e * 12, d[:, 2] * 1.6], -1)
        cl = smooth(.5, .72, fbm(q, 5, 91)) * smooth(-.02, .12, e) * (1 - smooth(.35, .7, e))
        shade = np.array([.42, .2, .38], np.float32)
        col = bg * (1 - cl[:, None] * .55) + shade * cl[:, None] * .55
        ang = np.arccos(np.clip(d @ sdir, -1, 1))
        sun = smooth(.075, .06, ang)[:, None] * np.array([1., .93, .72], np.float32)
        glow = np.exp(-ang / .22)[:, None] * np.array([1., .65, .3], np.float32) * .55
        col = col + glow
        col = np.where((ang < .07)[:, None], sun * (1 - cl[:, None] * .6) + col * (cl[:, None] * .6), col)
        return tonemap(col)
    return fn

def sky_dawn_haze(F, variant):
    def fn(d):
        e = d[:, 1]; phi = np.arctan2(d[:, 2], d[:, 0])
        bg = grad(e, [(-1, (1.0,.9,.82)), (0, (1.0,.88,.84)), (.2, (.93,.8,.9)), (.55, (.74,.74,.96)), (1, (.58,.66,.95))])
        q = np.stack([d[:, 0] * 2.0, e * 5.0, d[:, 2] * 2.0], -1)
        cl = smooth(.42, .72, fbm(q, 6, 101)) * smooth(-.1, .15, e)
        col = bg * (1 - cl[:, None] * .6) + np.array([1.0, .96, .97], np.float32) * cl[:, None] * .6
        return tonemap(col)
    return fn

def sky_midnight(F, variant):
    def fn(d):
        e = d[:, 1]
        col = grad(e, [(-1, (.02,.04,.12)), (-.05, (.03,.07,.2)), (.2, (.012,.025,.09)), (1, (.004,.008,.04))])
        return tonemap(col)
    return fn

def sky_mono(F, variant):
    def fn(d):
        e = d[:, 1]
        col = grad(e, [(-1, (.18,.18,.2)), (0, (.15,.15,.17)), (.5, (.06,.06,.07)), (1, (.03,.03,.035))])
        col += stars(d, 40, .03, 20, .26, .5, tint=False) * .5
        return tonemap(col)
    return fn

SKIES = {
    "deep_space":   (768, sky_deep_space, ("base", "fx1", "fx2")),
    "nebula":       (768, sky_nebula, ("base", "fx1", "fx2")),
    "aurora":       (768, sky_aurora, ("base", "fx1", "fx2")),
    "eclipse":      (640, sky_eclipse, ("base", "fx1", "fx2")),
    "crystal_dusk": (768, sky_crystal_dusk, ("base", "fx1", "fx2")),
    "sunset":       (512, sky_sunset, ("base",)),
    "dawn_haze":    (256, sky_dawn_haze, ("base",)),
    "midnight":     (128, sky_midnight, ("base",)),
    "mono":         (512, sky_mono, ("base",)),
}

if __name__ == "__main__":
    import sys
    only = sys.argv[1:] or list(SKIES)
    for name in only:
        F, mk, variants = SKIES[name]
        for v in variants:
            save(name if v == "base" else f"{name}_{v}", F, mk(F, v), dither=name in ("sunset", "dawn_haze", "midnight", "crystal_dusk") and v == "base")
