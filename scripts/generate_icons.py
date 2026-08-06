#!/usr/bin/env python3
"""Generate the hub icon (icon.png, 48x72) and the sidebar nav icon
(panel_icon.png, 16x16) as original pixel-art of a red chinchompa.

Pure-python PNG writer (zlib + struct) so it runs without Pillow. The art is
ours (drawn here from a palette grid), evoking the chinchompa: round rust
body, cream belly, ears, big curled tail. No Jagex assets are copied into
the repo - in-game item sprites are loaded at RUNTIME from the player's own
client via ItemManager.
"""
import os
import struct
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# Palette: '.' transparent, body/shade/belly/outline/tail-tip/feet/eye
P = {
    '.': None,
    'o': (0x46, 0x28, 0x1a, 255),   # outline, dark brown
    'b': (0xc2, 0x67, 0x3a, 255),   # body rust
    'B': (0x9c, 0x4e, 0x28, 255),   # body shade
    'w': (0xee, 0xd9, 0xbd, 255),   # belly cream
    'k': (0xe8, 0xb4, 0x7a, 255),   # tail tip, light
    'K': (0x7a, 0x3f, 0x1c, 255),   # feet / dark accents
    'e': (0x1a, 0x1a, 0x1a, 255),   # eye
}

# 16x13 chinchompa: tail curling up the left, face to the right.
CHIN = [
    ".....oo..oo.....",
    "....obboobbbo...",
    ".oooBbbbbbbbbo..",
    "okkoBbbbbbbbbbo.",
    "okboBbbbbebbebo.",
    "okboBbbbbbbbbbo.",
    "obboobbbwwbbbbo.",
    ".oo.obbwwwwbbo..",
    "....obwwwwwbbo..",
    "....obbwwwbbbo..",
    ".....obbbbbbo...",
    "......oKKKKo....",
    ".......KK.KK....",
]

# 16x16 nav icon: the same chin centred with padding rows.
NAV = ["................", "................"] + CHIN + ["................"]


def write_png(path, width, height, get_rgba):
    raw = b''
    for y in range(height):
        raw += b'\x00'
        for x in range(width):
            px = get_rgba(x, y)
            raw += bytes(px if px else (0, 0, 0, 0))

    def chunk(tag, data):
        c = tag + data
        return struct.pack('>I', len(data)) + c + struct.pack('>I', zlib.crc32(c))

    png = (b'\x89PNG\r\n\x1a\n'
           + chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0))
           + chunk(b'IDAT', zlib.compress(raw, 9))
           + chunk(b'IEND', b''))
    with open(path, 'wb') as f:
        f.write(png)
    print(f"wrote {os.path.relpath(path, ROOT)} ({width}x{height})")


def grid_sampler(grid, scale, ox=0, oy=0):
    h, w = len(grid), len(grid[0])

    def get(x, y):
        gx, gy = (x - ox) // scale, (y - oy) // scale
        if 0 <= gy < h and 0 <= gx < w:
            return P[grid[gy][gx]]
        return None
    return get


def main():
    # Hub icon: 48x72 canvas, chin at 3x (48x39) centred vertically.
    write_png(os.path.join(ROOT, 'icon.png'), 48, 72,
              grid_sampler(CHIN, 3, ox=0, oy=16))
    # Sidebar nav icon: 16x16 at 1x.
    write_png(os.path.join(ROOT, 'src/main/resources/com/trappychinchompa/panel_icon.png'),
              16, 16, grid_sampler(NAV, 1))
    # The playable Pixel Chin: the same art at 2x, item-sprite sized.
    write_png(os.path.join(ROOT, 'src/main/resources/com/trappychinchompa/chin_sprite.png'),
              32, 26, grid_sampler(CHIN, 2))


if __name__ == '__main__':
    main()
