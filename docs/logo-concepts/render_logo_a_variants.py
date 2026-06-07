from __future__ import annotations

from pathlib import Path
from typing import Callable

from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parent
SIZE = 1024
SCALE = 3
W = SIZE * SCALE


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


def gradient_icon(start: int, end: int) -> Image.Image:
    low = 384
    image = Image.new("RGBA", (low, low), (0, 0, 0, 0))
    pixels = image.load()
    s = color(start)
    e = color(end)
    for y in range(low):
        for x in range(low):
            t = (x + y) / (2 * (low - 1))
            pixels[x, y] = tuple(round(s[i] * (1 - t) + e[i] * t) for i in range(4))
    image = image.resize((W, W), Image.Resampling.BICUBIC)

    mask = Image.new("L", (W, W), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, W, W), radius=p(220), fill=255)
    image.putalpha(mask)
    return image


def draw_shadowed_round(
    image: Image.Image,
    box: tuple[int, int, int, int],
    radius: int,
    fill: tuple[int, int, int, int],
    shadow: tuple[int, int, int, int] = (65, 54, 84, 50),
) -> ImageDraw.ImageDraw:
    shadow_layer = Image.new("RGBA", image.size, (0, 0, 0, 0))
    sd = ImageDraw.Draw(shadow_layer)
    shifted = (box[0], box[1] + p(20), box[2], box[3] + p(20))
    sd.rounded_rectangle(shifted, radius=radius, fill=shadow)
    shadow_layer = shadow_layer.filter(ImageFilter.GaussianBlur(p(18)))
    image.alpha_composite(shadow_layer)
    d = ImageDraw.Draw(image)
    d.rounded_rectangle(box, radius=radius, fill=fill)
    return d


def draw_heart(d: ImageDraw.ImageDraw, cx: float, cy: float, size: float, fill: tuple[int, int, int, int]) -> None:
    import math

    points = []
    for i in range(120):
        t = 2 * math.pi * i / 120
        x = 16 * math.sin(t) ** 3
        y = 13 * math.cos(t) - 5 * math.cos(2 * t) - 2 * math.cos(3 * t) - math.cos(4 * t)
        points.append((p(cx + x * size / 32), p(cy - y * size / 32)))
    d.polygon(points, fill=fill)


def draw_smile(d: ImageDraw.ImageDraw, box: tuple[int, int, int, int], width: float, fill: tuple[int, int, int, int]) -> None:
    d.arc(box, start=20, end=160, fill=fill, width=p(width))


def save(name: str, image: Image.Image) -> None:
    image = image.resize((SIZE, SIZE), Image.Resampling.LANCZOS)
    image.save(ROOT / name)


def draw_window_family(
    d: ImageDraw.ImageDraw,
    *,
    left: float,
    top: float,
    width: float,
    height: float,
    radius: float,
    heart_size: float,
    heart_y: float,
) -> None:
    d.rounded_rectangle(xywh(left, top, width, height), radius=p(radius), fill=color(0x252C63))
    d.ellipse(xywh(left + 54, top + 28, 94, 94), fill=color(0xFFFDF7))
    d.ellipse(xywh(left + width - 148, top + 28, 94, 94), fill=color(0xFFFDF7))
    d.rounded_rectangle(xywh(left + 44, top + 96, 116, 60), radius=p(30), fill=color(0xFF875B))
    d.rounded_rectangle(xywh(left + width - 160, top + 96, 116, 60), radius=p(30), fill=color(0x4FC8A5))
    draw_heart(d, left + width / 2, heart_y, heart_size, color(0xFF6593))


def draw_bus_face(
    d: ImageDraw.ImageDraw,
    *,
    x: float,
    y: float,
    w: float,
    h: float,
    eye_size: float,
    cheek: bool,
    smile_box: tuple[float, float, float, float],
) -> None:
    d.rounded_rectangle(xywh(x, y, w, h), radius=p(70), fill=color(0xC2BEFF))
    d.ellipse(xywh(x + 102, y + 78, eye_size, eye_size), fill=color(0x2E366E))
    d.ellipse(xywh(x + w - 102 - eye_size, y + 78, eye_size, eye_size), fill=color(0x2E366E))
    if cheek:
        d.ellipse(xywh(x + 66, y + 138, 42, 30), fill=color(0xFF8FB2, 120))
        d.ellipse(xywh(x + w - 108, y + 138, 42, 30), fill=color(0xFF8FB2, 120))
    draw_smile(d, tuple(p(v) for v in smile_box), 20, color(0x2E366E))


def option_a1() -> None:
    image = gradient_icon(0xFFE2CF, 0xDAD6FF)
    d = draw_shadowed_round(image, xywh(238, 258, 548, 512), p(154), color(0x8276D6))
    draw_window_family(d, left=296, top=304, width=432, height=162, radius=56, heart_size=144, heart_y=392)
    draw_bus_face(d, x=318, y=506, w=388, h=196, eye_size=38, cheek=True, smile_box=(448, 596, 576, 700))
    d.ellipse(xywh(326, 724, 58, 58), fill=color(0xFFD66B))
    d.ellipse(xywh(640, 724, 58, 58), fill=color(0xFFD66B))
    d.rounded_rectangle(xywh(238, 258, 548, 512), radius=p(154), outline=color(0xFFFFFF, 145), width=p(11))
    save("logo-option-a1-round-face.png", image)


def option_a2() -> None:
    image = gradient_icon(0xFFE8C9, 0xD7D4FF)
    d = draw_shadowed_round(image, xywh(248, 286, 528, 472), p(172), color(0x8A79DB))
    d.rounded_rectangle(xywh(306, 326, 412, 148), radius=p(62), fill=color(0x252C63))
    d.ellipse(xywh(340, 350, 88, 88), fill=color(0xFFFDF7))
    d.ellipse(xywh(596, 350, 88, 88), fill=color(0xFFFDF7))
    d.rounded_rectangle(xywh(342, 426, 92, 48), radius=p(24), fill=color(0xFF875B))
    d.rounded_rectangle(xywh(592, 426, 92, 48), radius=p(24), fill=color(0x4FC8A5))
    draw_heart(d, 512, 430, 124, color(0xFF6593))
    draw_bus_face(d, x=318, y=508, w=388, h=168, eye_size=44, cheek=True, smile_box=(448, 584, 576, 698))
    d.ellipse(xywh(336, 704, 56, 56), fill=color(0xFFD66B))
    d.ellipse(xywh(632, 704, 56, 56), fill=color(0xFFD66B))
    d.rounded_rectangle(xywh(248, 286, 528, 472), radius=p(172), outline=color(0xFFFFFF, 148), width=p(11))
    save("logo-option-a2-chubby-bus.png", image)


def option_a3() -> None:
    image = gradient_icon(0xFFE0C6, 0xDCD8FF)
    d = draw_shadowed_round(image, xywh(260, 246, 504, 526), p(150), color(0x7F72D2))
    d.rounded_rectangle(xywh(318, 294, 388, 160), radius=p(60), fill=color(0x252C63))
    d.ellipse(xywh(354, 318, 92, 92), fill=color(0xFFFDF7))
    d.ellipse(xywh(578, 318, 92, 92), fill=color(0xFFFDF7))
    d.rounded_rectangle(xywh(354, 398, 98, 56), radius=p(28), fill=color(0xFF875B))
    d.rounded_rectangle(xywh(572, 398, 98, 56), radius=p(28), fill=color(0x4FC8A5))
    draw_heart(d, 512, 418, 136, color(0xFF6593))
    d.rounded_rectangle(xywh(326, 500, 372, 200), radius=p(78), fill=color(0xC5C2FF))
    d.ellipse(xywh(408, 580, 42, 42), fill=color(0x2E366E))
    d.ellipse(xywh(574, 580, 42, 42), fill=color(0x2E366E))
    d.ellipse(xywh(372, 636, 38, 28), fill=color(0xFF8FB2, 120))
    d.ellipse(xywh(614, 636, 38, 28), fill=color(0xFF8FB2, 120))
    draw_smile(d, tuple(p(v) for v in (454, 616, 570, 710)), 19, color(0x2E366E))
    d.ellipse(xywh(334, 724, 56, 56), fill=color(0xFFD66B))
    d.ellipse(xywh(634, 724, 56, 56), fill=color(0xFFD66B))
    d.rounded_rectangle(xywh(260, 246, 504, 526), radius=p(150), outline=color(0xFFFFFF, 142), width=p(11))
    save("logo-option-a3-family-cute.png", image)


def make_contact_sheet(files: list[tuple[str, str]], out_name: str) -> None:
    width = 1500
    height = 640
    sheet = Image.new("RGBA", (width, height), (247, 248, 251, 255))
    d = ImageDraw.Draw(sheet)
    positions = [(60, 74), (540, 74), (1020, 74)]
    for (file_name, label), (x, y) in zip(files, positions):
        icon = Image.open(ROOT / file_name).resize((380, 380), Image.Resampling.LANCZOS)
        sheet.alpha_composite(icon, (x, y))
        d.text((x, y + 420), label, fill=(48, 49, 61, 255))
    sheet.save(ROOT / out_name)


if __name__ == "__main__":
    generators: list[Callable[[], None]] = [option_a1, option_a2, option_a3]
    for generator in generators:
        generator()
    make_contact_sheet(
        [
            ("logo-option-a1-round-face.png", "A1 Round Face"),
            ("logo-option-a2-chubby-bus.png", "A2 Chubby Bus"),
            ("logo-option-a3-family-cute.png", "A3 Family Cute"),
        ],
        "logo-option-a-cute-variants.png",
    )
