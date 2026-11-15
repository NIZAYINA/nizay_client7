#!/usr/bin/env python3
"""Делает текстуры частиц насыщеннее и ярче (однократно). Исходники сохраняются в tools/particles_orig/."""
import os, shutil, numpy as np
from PIL import Image
D = os.path.join(os.path.dirname(__file__), "..", "src/main/resources/assets/nizay/textures/particle")
O = os.path.join(os.path.dirname(__file__), "particles_orig")
os.makedirs(O, exist_ok=True)
SOFT = {"mist", "smoke", "wind"}
for f in sorted(os.listdir(D)):
    if not f.endswith(".png"): continue
    src = os.path.join(O, f)
    if not os.path.exists(src): shutil.copy(os.path.join(D, f), src)
    im = Image.open(src).convert("RGBA"); a = np.asarray(im).astype(np.float32) / 255
    rgb, al = a[..., :3], a[..., 3]
    mx = rgb.max(-1, keepdims=True); mn = rgb.min(-1, keepdims=True)
    gray = rgb.mean(-1, keepdims=True)
    k = 1.55 if f[:-4] not in ("stardust", "star", "moon", "pulse", "halo", "rune") else 1.2   # светлые оставляем почти белыми
    rgb = np.clip(gray + (rgb - gray) * k, 0, 1)
    rgb = np.clip(rgb * 1.18, 0, 1)
    if f[:-4] not in SOFT: al = al ** 0.72
    else: al = np.clip(al * 1.3, 0, 1)
    out = np.concatenate([rgb, al[..., None]], -1)
    Image.fromarray((out * 255 + .5).astype(np.uint8), "RGBA").save(os.path.join(D, f))
print("ok")
