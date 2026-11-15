#!/usr/bin/env python3
"""Превью неба для меню: рендерит вид «из камеры» теми же функциями, что и кубические карты."""
import os, sys, numpy as np
sys.path.insert(0, os.path.dirname(__file__))
import gen_skies as G
from PIL import Image
OUT = os.path.join(os.path.dirname(__file__), "..", "src/main/resources/assets/nizay/textures/gui")
os.makedirs(OUT, exist_ok=True)
LOOK = {"deep_space": (.45, .42, -.78), "nebula": (.2, .3, .9), "aurora": (.0, .18, 1.0), "eclipse": (.18, .5, .85),
        "crystal_dusk": (.2, .22, 1.0), "sunset": (.3, .08, .95), "dawn_haze": (.2, .3, 1.0), "midnight": (.2, .2, 1.0), "mono": (.2, .3, 1.0)}
W, H = 256, 128
for name, (F, mk, variants) in G.SKIES.items():
    fwd = G.norm(np.array(LOOK[name], np.float32)); up0 = np.array([0, 1, 0], np.float32)
    right = G.norm(np.cross(fwd, up0)); up = np.cross(right, fwd)
    xs = (np.arange(W) + .5) / W * 2 - 1; ys = 1 - (np.arange(H) + .5) / H * 2
    X, Y = np.meshgrid(xs, ys); fov = np.tan(np.radians(52))
    d = G.norm((fwd[None, None] + X[..., None] * fov * right + Y[..., None] * fov * (H / W) * up).reshape(-1, 3).astype(np.float32))
    col = mk(F, "base")(d)
    if "fx1" in variants: col = col + mk(F, "fx1")(d) * .7
    img = (np.clip(col.reshape(H, W, 3), 0, 1) * 255 + .5).astype(np.uint8)
    im = Image.fromarray(img, "RGB").convert("RGBA")
    from PIL import ImageDraw
    m = Image.new("L", (W * 4, H * 4), 0); ImageDraw.Draw(m).rounded_rectangle([0, 0, W * 4 - 1, H * 4 - 1], radius=15 * 4, fill=255)
    im.putalpha(m.resize((W, H), Image.LANCZOS))
    im.save(os.path.join(OUT, f"sky_{name}.png"), optimize=True)
    print("ok", name)
