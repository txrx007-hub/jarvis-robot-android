"""Builds the app's splash image from the user's photo: hand + robot sharp, background blurred,
zoomed in 30% on the subject, 1080x1920 (9:16) for phones.

The u2net cut-out finds the robot but treats the palm as background, so the hand is added from a
skin-colour mask (YCbCr), keeping only the big skin region that touches the bottom edge (the arm),
then the two masks are merged, holes filled and the edge softened."""
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter, ImageOps
from rembg import new_session, remove
from scipy import ndimage

src = Path(sys.argv[1])
out_dir = Path(sys.argv[2])
out_dir.mkdir(parents=True, exist_ok=True)

img = ImageOps.exif_transpose(Image.open(src)).convert("RGB")
W, H = img.size
print("source", W, H)

# --- robot: u2net salient-object mask
session = new_session("u2net")
robot = np.array(remove(img, session=session, only_mask=True).convert("L")) > 128

# --- hand: skin colour in YCbCr, cleaned up, largest region touching the bottom edge
ycc = np.array(img.convert("YCbCr")).astype(np.int16)
Y, Cb, Cr = ycc[..., 0], ycc[..., 1], ycc[..., 2]
skin = (Cr >= 135) & (Cr <= 180) & (Cb >= 80) & (Cb <= 135) & (Y >= 55)
k = max(3, W // 150)
skin = ndimage.binary_opening(skin, structure=np.ones((k, k)))
skin = ndimage.binary_closing(skin, structure=np.ones((k * 3, k * 3)))
skin = ndimage.binary_fill_holes(skin)
labels, n = ndimage.label(skin)
hand = np.zeros_like(skin)
if n:
    bottom_labels = set(np.unique(labels[-max(4, H // 100):, :])) - {0}
    sizes = ndimage.sum(skin, labels, index=range(1, n + 1))
    candidates = [(sizes[i - 1], i) for i in bottom_labels]
    if candidates:
        _, best = max(candidates)
        hand = labels == best
print("robot px", int(robot.sum()), "hand px", int(hand.sum()))

# --- merge, fill gaps between hand and robot, soften the edge
subject = robot | hand
subject = ndimage.binary_closing(subject, structure=np.ones((k * 2, k * 2)))
subject = ndimage.binary_fill_holes(subject)
mask = Image.fromarray((subject * 255).astype(np.uint8), "L")
mask.resize((W // 4, H // 4)).save(out_dir / "debug_mask.png")
mask_soft = mask.filter(ImageFilter.GaussianBlur(radius=max(2, W // 400)))

# --- blurred, slightly darkened background
background = img.filter(ImageFilter.GaussianBlur(radius=max(20, W // 45)))
background = Image.blend(background, Image.new("RGB", img.size, (8, 14, 16)), 0.18)
composite = Image.composite(img, background, mask_soft)

# --- zoom in 30% on the robot (the hand fills the bottom anyway)
rb = Image.fromarray((robot * 255).astype(np.uint8), "L").getbbox()
cx = (rb[0] + rb[2]) / 2
cy = (rb[1] + rb[3]) / 2 + (H * 0.06)  # a bit lower so more palm shows under the robot
cw, ch = W / 1.3, H / 1.3
left = min(max(cx - cw / 2, 0), W - cw)
top = min(max(cy - ch / 2, 0), H - ch)
crop_box = (round(left), round(top), round(left + cw), round(top + ch))
print("crop", crop_box)
zoomed = composite.crop(crop_box).resize((1080, 1920), Image.LANCZOS)
zoomed.save(out_dir / "splash.jpg", quality=88, optimize=True, progressive=True)
print("saved", out_dir / "splash.jpg", (out_dir / "splash.jpg").stat().st_size, "bytes")
