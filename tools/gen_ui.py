#!/usr/bin/env python3
"""Текстуры интерфейса: белые скруглённые прямоугольники (9-slice, окрашиваются в игре), стрелка, логотип, фон титульного экрана."""
import os, sys, numpy as np
from PIL import Image, ImageDraw
sys.path.insert(0, os.path.dirname(__file__))
R = os.path.join(os.path.dirname(__file__), "..", "src/main/resources/assets/nizay")
G = os.path.join(R, "textures/gui"); os.makedirs(G, exist_ok=True)
META = '{"texture":{"blur":true,"clamp":true}}'
def meta(path): open(path + ".mcmeta", "w").write(META)

for c in (12, 32, 96):
    n, ss = 2 * c, 8
    big = Image.new("L", (n * ss, n * ss), 0)
    ImageDraw.Draw(big).rounded_rectangle([0, 0, n * ss - 1, n * ss - 1], radius=c * ss, fill=255)
    a = big.resize((n, n), Image.LANCZOS)
    im = Image.new("RGBA", (n, n), (255, 255, 255, 0)); im.putalpha(a)
    p = os.path.join(G, f"rr{c}.png"); im.save(p, optimize=True); meta(p)

# стрелка (смотрит вверх)
S = 64; ss = 6
big = Image.new("L", (S * ss, S * ss), 0); d = ImageDraw.Draw(big)
d.polygon([(32 * ss, 6 * ss), (56 * ss, 56 * ss), (32 * ss, 44 * ss), (8 * ss, 56 * ss)], fill=255)
im = Image.new("RGBA", (S, S), (255, 255, 255, 0)); im.putalpha(big.resize((S, S), Image.LANCZOS))
p = os.path.join(G, "arrow.png"); im.save(p); meta(p)

# логотип
logo = Image.open(os.path.join(os.path.dirname(__file__), "..", "launcher", "icon.png")).convert("RGBA")
p = os.path.join(G, "logo.png"); logo.resize((128, 128), Image.LANCZOS).save(p, optimize=True); meta(p)
os.makedirs(os.path.join(R, "textures/font"), exist_ok=True)
logo.resize((32, 32), Image.LANCZOS).save(os.path.join(R, "textures/font/logo.png"))

# фон титульного экрана: тот же «Deep Space», вид на планету и галактику
import gen_skies as GS
W, H = 1280, 720
fwd = GS.norm(np.array([.40, .40, -.80], np.float32)); up0 = np.array([0, 1, 0], np.float32)
right = GS.norm(np.cross(fwd, up0)); up = np.cross(right, fwd)
xs = (np.arange(W) + .5) / W * 2 - 1; ys = 1 - (np.arange(H) + .5) / H * 2
X, Y = np.meshgrid(xs, ys); fov = np.tan(np.radians(48))
d = GS.norm((fwd[None, None] + X[..., None] * fov * right + Y[..., None] * fov * (H / W) * up).reshape(-1, 3).astype(np.float32))
col = GS.SKIES["deep_space"][1](768, "base")(d).reshape(H, W, 3)
vig = 1 - .55 * (X ** 2 * .6 + Y ** 2 * .9)
col = col * np.clip(vig, .25, 1)[..., None] * .85
Image.fromarray((np.clip(col, 0, 1) * 255 + .5).astype(np.uint8), "RGB").save(os.path.join(G, "title_bg.png"), optimize=True)
meta(os.path.join(G, "title_bg.png"))
print("ok")
