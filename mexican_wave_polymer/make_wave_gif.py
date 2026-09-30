#!/usr/bin/env python3
"""
Combine the OVITO renders of the PP and PC runs into one short clip for sharing:
title, atom-type legend per chain, and an animated map of the applied field
Ey(x,t) = E0 * env(t) * sin(k x - w t) on top, synchronised with the frames.

usage:  python3 make_wave_gif.py PP_wave.mp4 PC_wave.mp4 [outdir]
needs:  ffmpeg, numpy, matplotlib (gifsicle optional)
writes: PP_PC_field_wave.mp4 (1200 px, 13 s) and PP_PC_field_wave.gif (15 s, < 5 MB)
"""
import os
import subprocess
import sys
import shutil
import numpy as np
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.colors import LinearSegmentedColormap
from matplotlib.patches import Ellipse

# ---------------- simulation settings behind the videos (edit if you changed them) ----------
FS_PER_FRAME = 25.0      # Ndump * dt  = 100 * 0.25 fs
TPER = 1000.0            # field period (fs)
NRAMP = 2                # field switched on over this many periods
E0 = 0.25                # V/Angstrom
SHOW_FIELD = False       # animated field map on top (runs at the true 1 THz pace, far faster than
                         # the visible chain motion, so it is off by default)
NWAVE = 1                # wavelengths in the box
DIRECTION = +1           # +1: wave travels towards +x (to the right in the render)

# ---------------- output settings ----------------
GIF_FRAMES, GIF_SECONDS, GIF_WIDTH = 250, 15.0, 960     # LinkedIn photo post: <= 5 MB, <= 250 frames
MP4_STEP, MP4_FPS = 5, 30                                # 2001 frames -> 401 frames -> 13.4 s
LEGEND_CUT = 420         # source rows above this belong to OVITO's own legend/title (dropped)

W = 1200                 # canvas width (px)
X0, X1 = 90, 1150        # the periodic box (chain) spans these canvas columns
COLORS = {"C": "#0000E6", "H": "#E10000", "O": "#E100E1"}          # OVITO colours used
POS, NEG = np.array([240, 127, 19]) / 255, np.array([27, 158, 158]) / 255   # +Ey / -Ey
CMAP = LinearSegmentedColormap.from_list("field", [NEG, (1, 1, 1), POS])


def ffprobe_frames(video):
    out = subprocess.run(["ffprobe", "-v", "error", "-select_streams", "v:0", "-count_frames",
                          "-show_entries", "stream=nb_read_frames,width,height", "-of", "csv=p=0", video],
                         capture_output=True, text=True, check=True).stdout.strip().split(",")
    w, h, n = map(int, out)
    return w, h, n


def chain_bbox(video, w, h, n):
    """union bounding box of the chain over ~40 sampled frames (below the OVITO legend)"""
    step = max(1, n // 40)
    p = subprocess.Popen(["ffmpeg", "-v", "error", "-i", video, "-vf", f"select=not(mod(n\\,{step}))",
                          "-vsync", "vfr", "-f", "rawvideo", "-pix_fmt", "rgb24", "-"], stdout=subprocess.PIPE)
    x0 = y0 = 10 ** 9
    x1 = y1 = 0
    while True:
        buf = p.stdout.read(w * h * 3)
        if len(buf) < w * h * 3:
            break
        a = np.frombuffer(buf, np.uint8).reshape(h, w, 3).astype(int)
        m = (a.sum(-1) < 600) | (a.max(-1) - a.min(-1) > 120)
        m[:LEGEND_CUT] = False
        ys, xs = np.where(m)
        x0, x1, y0, y1 = min(x0, xs.min()), max(x1, xs.max()), min(y0, ys.min()), max(y1, ys.max())
    p.wait()
    return x0, x1, y0, y1


class Band:
    """reads the chain band of one video, cropped and scaled so the box spans X0..X1"""

    def __init__(self, video, step, nmax):
        w, h, n = ffprobe_frames(video)
        bx0, bx1, by0, by1 = chain_bbox(video, w, h, n)
        s = (X1 - X0) / (bx1 - bx0)
        pad = 18                                            # source pixels around the bbox
        cx0, cx1 = max(0, bx0 - pad), min(w, bx1 + pad)
        cy0, cy1 = max(LEGEND_CUT, by0 - pad), min(h, by1 + pad)
        self.w = int(round((cx1 - cx0) * s)) // 2 * 2
        self.h = int(round((cy1 - cy0) * s)) // 2 * 2
        self.x = int(round(X0 - (bx0 - cx0) * s))           # canvas column of the crop's left edge
        vf = (f"select=not(mod(n\\,{step}))*lt(n\\,{nmax}),crop={cx1 - cx0}:{cy1 - cy0}:{cx0}:{cy0},"
              f"scale={self.w}:{self.h}:flags=lanczos")
        self.p = subprocess.Popen(["ffmpeg", "-v", "error", "-i", video, "-vf", vf, "-vsync", "vfr",
                                   "-f", "rawvideo", "-pix_fmt", "rgb24", "-"], stdout=subprocess.PIPE)
        self.n = n

    def next(self):
        buf = self.p.stdout.read(self.w * self.h * 3)
        if len(buf) < self.w * self.h * 3:
            return None
        a = np.frombuffer(buf, np.uint8).reshape(self.h, self.w, 3).astype(float) / 255.0
        a[a.min(-1) > 0.92] = 1.0                              # clean video-compression noise
        return a


def field(xn, t):
    env = np.sin(0.5 * np.pi * min(1.0, t / (NRAMP * TPER))) ** 2
    return env * np.sin(2 * np.pi * NWAVE * xn - DIRECTION * 2 * np.pi * t / TPER)


def field_panel(fig, fy0, fieldh, xs, tint_on, H, font):
    """animated Ey(x,t) map above the chains"""
    axf = fig.add_axes([X0 / W, fy0 / H, (X1 - X0) / W, (fieldh - 26) / H])
    axf.set_xlim(0, 1)
    axf.set_ylim(-1.35, 1.35)
    axf.set_facecolor("#f6f7f9")
    for sp in axf.spines.values():
        sp.set_visible(False)
    axf.set_xticks([])
    axf.set_yticks([-1, 0, 1])
    axf.set_yticklabels([f"−{E0:g} V/Å", "0", f"+{E0:g} V/Å"], fontsize=10, color="#4a5160")
    axf.tick_params(length=0, pad=4)
    axf.axhline(0, color="#9aa0aa", lw=0.8)
    for yy in (-1, 1):
        axf.axhline(yy, color="#d7dae0", lw=0.6, ls="--")
    nx, ny = len(xs), 160
    ys = np.linspace(-1.35, 1.35, ny)[:, None]
    fill = axf.imshow(np.zeros((ny, nx, 4)), extent=(0, 1, -1.35, 1.35), origin="lower",
                      aspect="auto", interpolation="bilinear", zorder=1)
    line, = axf.plot(xs, 0 * xs, color="#1d2433", lw=2.0, zorder=3)
    arrow_txt = "wave direction  ⟶" if DIRECTION > 0 else "⟵  wave direction"
    axf.text(0.008 if DIRECTION > 0 else 0.992, 1.2, arrow_txt, ha="left" if DIRECTION > 0 else "right",
             va="center", fontsize=11, color="#1d2433", weight="bold", zorder=4)
    tlabel = axf.text(0.992, 1.2, "", ha="right" if DIRECTION > 0 else "left", va="center", fontsize=11,
                      color="#1d2433", zorder=4, family="DejaVu Sans Mono")
    fig.text(X0 / W, (fy0 - 12) / H, "box: x = 0", fontsize=9, color="#8a909a", va="center", **font)
    fig.text(X1 / W, (fy0 - 12) / H, "x = Lx (periodic)", fontsize=9, color="#8a909a", va="center",
             ha="right", **font)
    note = "   (chain background tinted by the local field)" if tint_on else ""
    fig.text(0.5, (fy0 - 12) / H, "orange = +E$_y$     teal = −E$_y$" + note,
             fontsize=9, color="#8a909a", va="center", ha="center", **font)

    return fill, line, tlabel, ys


def render(pp_video, pc_video, step, nframes, framedir, tint_on=True, solid=False, show_field=SHOW_FIELD):
    os.makedirs(framedir, exist_ok=True)
    tint_on = tint_on and show_field
    nmax = step * nframes
    bands = {"PP": Band(pp_video, step, nmax), "PC": Band(pc_video, step, nmax)}

    head, fieldh = (92, 170) if show_field else (118, 0)
    title_h, gap, foot = 44, 14, 20
    H = head + fieldh + gap + sum(title_h + b.h + gap for b in bands.values()) + foot
    H += H % 2
    fig = plt.figure(figsize=(W / 100, H / 100), dpi=100, facecolor="white")
    font = dict(family="DejaVu Sans")

    fig.text(0.5, 1 - 38 / H, "A travelling electric-field wave along single polymer chains",
             ha="center", va="center", fontsize=21, weight="bold", color="#1d2433", **font)
    fig.text(0.5, 1 - 70 / H, r"Reactive MD (ReaxFF + QEq, LAMMPS)   ·   transverse field  "
             r"$E_y(x,t)=E_0\,\sin(kx-\omega t)$  running through the periodic box",
             ha="center", va="center", fontsize=12.5, color="#4a5160", **font)
    info = fig.text(0.5, 1 - 97 / H, "", ha="center", va="center", fontsize=11.5, color="#4a5160",
                    family="DejaVu Sans Mono")
    info.set_visible(not show_field)
    fy0 = H - head - fieldh
    xs = np.linspace(0, 1, 700)
    if show_field:
        fill, line, tlabel, ys = field_panel(fig, fy0, fieldh, xs, tint_on, H, font)

    # ---- chain panels ----
    # ---- chain panels ----
    names = {"PP": ("Polypropylene (PP)", ["C", "H"]), "PC": ("Polycarbonate (PC)", ["C", "H", "O"])}
    ytop = fy0 - gap
    imgs = {}
    for key, b in bands.items():
        ttl, els = names[key]
        yt = ytop - title_h / 2
        fig.text(X0 / W, yt / H, ttl, fontsize=15, weight="bold", color="#1d2433", va="center", **font)
        xl = X1 - 62 * len(els)
        for i, e in enumerate(els):
            fig.patches.append(Ellipse(((xl + 62 * i) / W, yt / H), 17 / W, 17 / H, transform=fig.transFigure,
                                       fc=COLORS[e], ec="#333", lw=0.5))
            fig.text((xl + 62 * i + 14) / W, yt / H, e, fontsize=13, va="center", color="#1d2433", **font)
        yb = ytop - title_h - b.h
        imgs[key] = (fig.figimage(np.ones((b.h, b.w, 3)), xo=b.x, yo=yb, origin="upper", zorder=2), b)
        ytop = yb - gap

    # per-column normalised box coordinate for each band (for the background tint)
    xnorm = {k: (b.x + np.arange(b.w) - X0) / (X1 - X0) for k, b in bands.items()}

    k = 0
    while k < nframes:
        frames = {key: b.next() for key, b in bands.items()}
        if any(f is None for f in frames.values()):
            break
        t = k * step * FS_PER_FRAME
        if show_field:
            v = field(xs, t)
            c = CMAP(0.5 + 0.5 * (np.sign(v) if solid else v))  # solid fill compresses better in GIF
            inside = ((ys >= 0) & (ys <= v[None, :])) | ((ys <= 0) & (ys >= v[None, :]))
            rgba = np.zeros((len(ys), len(xs), 4))
            rgba[..., :3] = c[None, :, :3]
            rgba[..., 3] = np.where(inside, 0.9, 0.0)
            fill.set_data(rgba)
            line.set_ydata(v)
            tlabel.set_text(f"t = {t / 1000:5.2f} ps")
        info.set_text(f"E0 = {E0:g} V/Å   ·   period {TPER / 1000:g} ps   ·   wavelength = box length"
                      f"   ·   t = {t / 1000:5.2f} ps")
        for key, (im, b) in imgs.items():
            if tint_on:
                tint = CMAP(0.5 + 0.5 * field(xnorm[key], t))[:, :3]
                tint = 1.0 - 0.28 * (1.0 - tint)              # light tint
                im.set_data(np.clip(frames[key] * tint[None, :, :], 0, 1))
            else:
                im.set_data(frames[key])
        fig.savefig(os.path.join(framedir, f"{k:04d}.png"), dpi=100, facecolor="white")
        k += 1
    for b in bands.values():
        b.p.kill()
    plt.close(fig)
    return k


def main():
    pp, pc = sys.argv[1], sys.argv[2]
    out = sys.argv[3] if len(sys.argv) > 3 else "."
    n = min(ffprobe_frames(pp)[2], ffprobe_frames(pc)[2])
    tmp = os.path.join(out, "_frames")

    # MP4: smoother, for a LinkedIn video post
    nm = (n - 1) // MP4_STEP + 1
    got = render(pp, pc, MP4_STEP, nm, tmp)
    mp4 = os.path.join(out, "PP_PC_field_wave.mp4")
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-framerate", str(MP4_FPS), "-i", f"{tmp}/%04d.png",
                    "-c:v", "libx264", "-pix_fmt", "yuv420p", "-crf", "20", "-movflags", "+faststart", mp4],
                   check=True)
    print(f"{mp4}: {got} frames, {got / MP4_FPS:.1f} s")
    shutil.rmtree(tmp)

    # GIF: <= 250 frames, <= 15 s, for a LinkedIn photo post
    step = int(np.ceil((n - 1) / GIF_FRAMES))
    ng = min(GIF_FRAMES, (n - 1) // step + 1)
    got = render(pp, pc, step, ng, tmp, tint_on=False, solid=True)   # lighter design -> fits 5 MB
    fps = got / GIF_SECONDS
    gif = os.path.join(out, "PP_PC_field_wave.gif")
    pal = os.path.join(out, "_palette.png")
    for width in (GIF_WIDTH, 880, 800, 720, 640):               # largest width under 5 MB
        vf = f"fps={fps:.4f},scale={width}:-1:flags=area"
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-framerate", f"{fps:.4f}", "-i", f"{tmp}/%04d.png",
                        "-vf", vf + ",palettegen=max_colors=64:stats_mode=full", pal], check=True)
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-framerate", f"{fps:.4f}", "-i", f"{tmp}/%04d.png",
                        "-i", pal, "-lavfi", vf + " [x]; [x][1:v] paletteuse=dither=none:diff_mode=rectangle",
                        "-loop", "0", gif], check=True)
        if shutil.which("gifsicle"):
            subprocess.run(["gifsicle", "-O3", "--batch", gif], check=True)
        if os.path.getsize(gif) < 4.9e6:
            break
    os.remove(pal)
    shutil.rmtree(tmp)
    print(f"{gif}: {got} frames, {got / fps:.1f} s, {os.path.getsize(gif) / 1e6:.1f} MB")


if __name__ == "__main__":
    main()
