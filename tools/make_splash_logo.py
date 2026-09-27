# -*- coding: utf-8 -*-
"""生成冷启动系统启动图的图标：琥珀光晕 + 应用 logo。

为什么不用 layer-list XML 拼：
    layer-list 的 `<item android:width/height>` 约束在 windowSplashScreenAnimatedIcon
    场景下不生效（实测 bitmap 会按 gravity="fill" 撑满整个 layer-list 的 bounds），
    结果系统启动图里的地球比 Compose 开屏页大出近一倍 —— 交接时肉眼可见地「缩一下」。
    所以这里直接合成一张固定尺寸的 PNG，尺寸算得死死的，不依赖任何约束行为。

尺寸依据（与 Compose 的 SplashOverlay 一一对应）：
    画布 240dp —— Android 12+ SplashScreen 的图标画布（内容安全区直径 160dp）
    光晕直径 132dp（radialGradient 半径 66dp）—— 对应 SplashOverlay 里 132dp 的 Box
    地球 84dp —— 对应 SplashOverlay 里 84dp 的 Image
    三者在同一个 240dp 画布里居中，系统原样显示时位置/大小与 Compose 开屏完全重合。

用法：python tools/make_splash_logo.py
产物：app/src/main/res/drawable-{hdpi,xhdpi,xxhdpi,xxxhdpi}/splash_logo.png
"""
import os
import struct
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, "..", "app", "src", "main", "res")
SRC_LOGO = os.path.join(RES, "drawable", "ic_app_logo.png")

# 画布与内容的 dp 尺寸（改这里就能整体调整）
CANVAS_DP = 240
HALO_DP = 132
GLOBE_DP = 84

# 光晕颜色（琥珀 #D4A373），alpha 从中心到边缘线性衰减到 0
HALO_RGB = (0xD4, 0xA3, 0x73)
HALO_ALPHA_CENTER = 0.16
HALO_ALPHA_MID = 0.05

# 目标密度目录 -> scale（1dp = scale px）
DENSITIES = {
    "drawable-hdpi": 1.5,
    "drawable-xhdpi": 2.0,
    "drawable-xxhdpi": 3.0,
    "drawable-xxxhdpi": 4.0,
}


# ----------------------------- PNG 读写 -----------------------------
def read_png(path):
    d = open(path, "rb").read()
    assert d[:8] == b"\x89PNG\r\n\x1a\n", "不是 PNG: " + path
    pos, idat = 8, b""
    w = h = ct = None
    while pos < len(d):
        ln = struct.unpack(">I", d[pos:pos + 4])[0]
        typ = d[pos + 4:pos + 8]
        data = d[pos + 8:pos + 8 + ln]
        pos += 12 + ln
        if typ == b"IHDR":
            w, h, bd, ct, comp, filt, inter = struct.unpack(">IIBBBBB", data)
            assert bd == 8 and inter == 0, "只支持 8bit 非隔行 PNG"
        elif typ == b"IDAT":
            idat += data
        elif typ == b"IEND":
            break
    raw = zlib.decompress(idat)
    ch = {0: 1, 2: 3, 4: 2, 6: 4}[ct]
    stride = w * ch
    out = bytearray(w * h * ch)
    prev = bytearray(stride)
    p = 0
    for y in range(h):
        f = raw[p]
        p += 1
        line = bytearray(raw[p:p + stride])
        p += stride
        if f == 1:
            for i in range(ch, stride):
                line[i] = (line[i] + line[i - ch]) & 255
        elif f == 2:
            for i in range(stride):
                line[i] = (line[i] + prev[i]) & 255
        elif f == 3:
            for i in range(stride):
                a = line[i - ch] if i >= ch else 0
                line[i] = (line[i] + ((a + prev[i]) >> 1)) & 255
        elif f == 4:
            for i in range(stride):
                a = line[i - ch] if i >= ch else 0
                b = prev[i]
                c = prev[i - ch] if i >= ch else 0
                pp = a + b - c
                pa, pb, pc = abs(pp - a), abs(pp - b), abs(pp - c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pr) & 255
        out[y * stride:(y + 1) * stride] = line
        prev = line
    # 统一转成 RGBA
    rgba = bytearray(w * h * 4)
    for i in range(w * h):
        if ch == 4:
            rgba[i * 4:i * 4 + 4] = out[i * 4:i * 4 + 4]
        elif ch == 3:
            rgba[i * 4:i * 4 + 3] = out[i * 3:i * 3 + 3]
            rgba[i * 4 + 3] = 255
        elif ch == 2:
            g = out[i * 2]
            rgba[i * 4:i * 4 + 3] = bytes((g, g, g))
            rgba[i * 4 + 3] = 255
        else:
            g = out[i]
            rgba[i * 4:i * 4 + 3] = bytes((g, g, g))
            rgba[i * 4 + 3] = out[i] if False else 255
    return w, h, rgba


def write_png(path, w, h, rgba):
    raw = bytearray()
    stride = w * 4
    for y in range(h):
        raw.append(0)  # filter: none
        raw += rgba[y * stride:(y + 1) * stride]
    def chunk(typ, data):
        return (struct.pack(">I", len(data)) + typ + data
                + struct.pack(">I", zlib.crc32(typ + data) & 0xFFFFFFFF))
    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
    png += chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(png)


def resize_rgba(src, sw, sh, dw, dh):
    """双线性插值缩放（RGBA，straight alpha 下直接对四通道插值足够用）。"""
    out = bytearray(dw * dh * 4)
    for y in range(dh):
        sy = (y + 0.5) * sh / dh - 0.5
        if sy < 0:
            y0 = y1 = 0
            fy = 0.0
        else:
            y0 = int(sy)
            y1 = min(sh - 1, y0 + 1)
            fy = sy - y0
            y0 = min(sh - 1, y0)
        for x in range(dw):
            sx = (x + 0.5) * sw / dw - 0.5
            if sx < 0:
                x0 = x1 = 0
                fx = 0.0
            else:
                x0 = int(sx)
                x1 = min(sw - 1, x0 + 1)
                fx = sx - x0
                x0 = min(sw - 1, x0)
            i00 = (y0 * sw + x0) * 4
            i01 = (y0 * sw + x1) * 4
            i10 = (y1 * sw + x0) * 4
            i11 = (y1 * sw + x1) * 4
            o = (y * dw + x) * 4
            w00 = (1 - fx) * (1 - fy)
            w01 = fx * (1 - fy)
            w10 = (1 - fx) * fy
            w11 = fx * fy
            for c in range(4):
                v = (src[i00 + c] * w00 + src[i01 + c] * w01
                     + src[i10 + c] * w10 + src[i11 + c] * w11)
                out[o + c] = int(v + 0.5)
    return out


def build(scale):
    size = int(round(CANVAS_DP * scale))
    cx = cy = size / 2.0
    halo_r = HALO_DP * scale / 2.0
    globe_px = int(round(GLOBE_DP * scale))

    buf = bytearray(size * size * 4)  # 全透明画布

    # ① 光晕：径向渐变，alpha 0.16 -> 0.05 -> 0（在 halo_r 处收为 0）
    x0 = max(0, int(cx - halo_r) - 2)
    x1 = min(size, int(cx + halo_r) + 3)
    y0 = max(0, int(cy - halo_r) - 2)
    y1 = min(size, int(cy + halo_r) + 3)
    for y in range(y0, y1):
        dy = y + 0.5 - cy
        for x in range(x0, x1):
            dx = x + 0.5 - cx
            dist = (dx * dx + dy * dy) ** 0.5
            if dist >= halo_r:
                continue
            t = dist / halo_r
            if t <= 0.5:
                a = HALO_ALPHA_CENTER + (HALO_ALPHA_MID - HALO_ALPHA_CENTER) * (t / 0.5)
            else:
                a = HALO_ALPHA_MID * (1 - (t - 0.5) / 0.5)
            o = (y * size + x) * 4
            buf[o] = HALO_RGB[0]
            buf[o + 1] = HALO_RGB[1]
            buf[o + 2] = HALO_RGB[2]
            buf[o + 3] = int(a * 255 + 0.5)

    # ② 地球 logo：缩放后居中叠加（source-over）
    sw, sh, src = read_png(SRC_LOGO)
    logo = resize_rgba(src, sw, sh, globe_px, globe_px) if globe_px != sw else src
    ox = int(round(cx - globe_px / 2.0))
    oy = int(round(cy - globe_px / 2.0))
    for y in range(globe_px):
        ty = y + oy
        if ty < 0 or ty >= size:
            continue
        for x in range(globe_px):
            tx = x + ox
            if tx < 0 or tx >= size:
                continue
            si = (y * globe_px + x) * 4
            sa = logo[si + 3]
            if sa == 0:
                continue
            o = (ty * size + tx) * 4
            if sa == 255:
                buf[o:o + 4] = logo[si:si + 4]
            else:
                inv = 255 - sa
                for c in range(3):
                    buf[o + c] = (logo[si + c] * sa + buf[o + c] * inv) // 255
                buf[o + 3] = min(255, sa + buf[o + 3] * inv // 255)
    return size, buf


if __name__ == "__main__":
    for folder, scale in DENSITIES.items():
        size, buf = build(scale)
        out_dir = os.path.join(RES, folder)
        os.makedirs(out_dir, exist_ok=True)
        out_path = os.path.join(out_dir, "splash_logo.png")
        write_png(out_path, size, size, buf)
        print("%-22s canvas=%3dpx (intrinsic %ddp)  globe=%ddp  %s"
              % (folder, size, CANVAS_DP, GLOBE_DP, os.path.relpath(out_path, RES)))
    print("done")
