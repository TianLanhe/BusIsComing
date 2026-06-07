from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

PROJECT_ROOT = Path(__file__).resolve().parents[2]
RES_ROOT = PROJECT_ROOT / "app/src/main/res"
PREVIEW_ROOT = Path(__file__).resolve().parent

BASE_SIZE = 1024
SCALE = 3
CANVAS = BASE_SIZE * SCALE

LEGACY_SIZES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}

FOREGROUND_SIZES = {
    "mipmap-mdpi": 108,
    "mipmap-hdpi": 162,
    "mipmap-xhdpi": 216,
    "mipmap-xxhdpi": 324,
    "mipmap-xxxhdpi": 432,
}


def p(value: float) -> int:
    return round(value * SCALE)


def color(hex_value: int, alpha: int = 255) -> tuple[int, int, int, int]:
    return (
        (hex_value >> 16) & 0xFF,
        (hex_value >> 8) & 0xFF,
        hex_value & 0xFF,
        alpha,
    )


def xywh(x: float, y: float, w: float, h: float) -> tuple[int, int, int, int]:
    return (p(x), p(y), p(x + w), p(y + h))


def gradient(start: int = 0xFFE0C6, end: int = 0xDCD8FF) -> Image.Image:
    low = 384
    image = Image.new("RGBA", (low, low), color(start))
    pixels = image.load()
    s = color(start)
    e = color(end)
    for y in range(low):
        for x in range(low):
            t = (x + y) / (2 * (low - 1))
            pixels[x, y] = tuple(round(s[i] * (1 - t) + e[i] * t) for i in range(4))
    return image.resize((CANVAS, CANVAS), Image.Resampling.BICUBIC)


def rounded_mask(radius: float) -> Image.Image:
    mask = Image.new("L", (CANVAS, CANVAS), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, CANVAS, CANVAS), radius=p(radius), fill=255)
    return mask


def circle_mask() -> Image.Image:
    mask = Image.new("L", (CANVAS, CANVAS), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, CANVAS, CANVAS), fill=255)
    return mask


def heart_points(cx: float, cy: float, size: float) -> list[tuple[int, int]]:
    import math

    points = []
    for i in range(120):
        t = 2 * math.pi * i / 120
        x = 16 * math.sin(t) ** 3
        y = 13 * math.cos(t) - 5 * math.cos(2 * t) - 2 * math.cos(3 * t) - math.cos(4 * t)
        points.append((p(cx + x * size / 32), p(cy - y * size / 32)))
    return points


def draw_heart(draw: ImageDraw.ImageDraw, cx: float, cy: float, size: float, fill: tuple[int, int, int, int]) -> None:
    draw.polygon(heart_points(cx, cy, size), fill=fill)


def shadowed_bus_body(image: Image.Image) -> ImageDraw.ImageDraw:
    shadow = Image.new("RGBA", image.size, (0, 0, 0, 0))
    shadow_draw = ImageDraw.Draw(shadow)
    body = xywh(260, 246, 504, 526)
    shifted = (body[0], body[1] + p(20), body[2], body[3] + p(20))
    shadow_draw.rounded_rectangle(shifted, radius=p(150), fill=color(0x4B446D, 50))
    shadow = shadow.filter(ImageFilter.GaussianBlur(p(18)))
    image.alpha_composite(shadow)

    draw = ImageDraw.Draw(image)
    draw.rounded_rectangle(body, radius=p(150), fill=color(0x7F72D2))
    return draw


def draw_a3_bus() -> Image.Image:
    image = Image.new("RGBA", (CANVAS, CANVAS), (0, 0, 0, 0))
    draw = shadowed_bus_body(image)

    draw.rounded_rectangle(xywh(318, 294, 388, 160), radius=p(60), fill=color(0x252C63))
    draw.ellipse(xywh(354, 318, 92, 92), fill=color(0xFFFDF7))
    draw.ellipse(xywh(578, 318, 92, 92), fill=color(0xFFFDF7))
    draw.rounded_rectangle(xywh(354, 398, 98, 56), radius=p(28), fill=color(0xFF875B))
    draw.rounded_rectangle(xywh(572, 398, 98, 56), radius=p(28), fill=color(0x4FC8A5))
    draw_heart(draw, 512, 418, 136, color(0xFF6593))

    draw.rounded_rectangle(xywh(326, 500, 372, 200), radius=p(78), fill=color(0xC5C2FF))
    draw.ellipse(xywh(408, 580, 42, 42), fill=color(0x2E366E))
    draw.ellipse(xywh(574, 580, 42, 42), fill=color(0x2E366E))
    draw.ellipse(xywh(372, 636, 38, 28), fill=color(0xFF8FB2, 120))
    draw.ellipse(xywh(614, 636, 38, 28), fill=color(0xFF8FB2, 120))
    draw.arc(tuple(p(v) for v in (454, 616, 570, 710)), start=20, end=160, fill=color(0x2E366E), width=p(19))

    draw.ellipse(xywh(334, 724, 56, 56), fill=color(0xFFD66B))
    draw.ellipse(xywh(634, 724, 56, 56), fill=color(0xFFD66B))
    draw.rounded_rectangle(xywh(260, 246, 504, 526), radius=p(150), outline=color(0xFFFFFF, 142), width=p(11))
    return image


def composite_icon(mask: Image.Image | None = None) -> Image.Image:
    icon = gradient()
    icon.alpha_composite(draw_a3_bus())
    if mask is not None:
        icon.putalpha(mask)
    return icon


def resize(image: Image.Image, size: int) -> Image.Image:
    return image.resize((size, size), Image.Resampling.LANCZOS)


def save_png(path: Path, image: Image.Image) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path)


def save_webp(path: Path, image: Image.Image) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, format="WEBP", lossless=True, quality=100, method=6)


def main() -> None:
    foreground = draw_a3_bus().resize((BASE_SIZE, BASE_SIZE), Image.Resampling.LANCZOS)
    legacy = composite_icon(rounded_mask(220)).resize((BASE_SIZE, BASE_SIZE), Image.Resampling.LANCZOS)
    round_icon = composite_icon(circle_mask()).resize((BASE_SIZE, BASE_SIZE), Image.Resampling.LANCZOS)

    for folder, size in LEGACY_SIZES.items():
        for duplicate in ("ic_launcher.png", "ic_launcher_round.png"):
            (RES_ROOT / folder / duplicate).unlink(missing_ok=True)
        save_webp(RES_ROOT / folder / "ic_launcher.webp", resize(legacy, size))
        save_webp(RES_ROOT / folder / "ic_launcher_round.webp", resize(round_icon, size))

    for folder, size in FOREGROUND_SIZES.items():
        save_png(RES_ROOT / folder / "ic_launcher_foreground.png", resize(foreground, size))

    preview = composite_icon(rounded_mask(220)).resize((BASE_SIZE, BASE_SIZE), Image.Resampling.LANCZOS)
    save_png(PREVIEW_ROOT / "logo-final-a3-launcher-preview.png", preview)


if __name__ == "__main__":
    main()
