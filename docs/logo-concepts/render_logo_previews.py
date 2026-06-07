from __future__ import annotations

from pathlib import Path

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
    image = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    pixels = image.load()
    s = color(start)
    e = color(end)
    for y in range(W):
        for x in range(W):
            t = (x + y) / (2 * (W - 1))
            pixels[x, y] = tuple(round(s[i] * (1 - t) + e[i] * t) for i in range(4))

    mask = Image.new("L", (W, W), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, W, W), radius=p(220), fill=255)
    rounded = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    rounded.alpha_composite(image)
    rounded.putalpha(mask)
    return rounded


def draw_shadowed_round(
    image: Image.Image,
    box: tuple[int, int, int, int],
    radius: int,
    fill: tuple[int, int, int, int],
    shadow: tuple[int, int, int, int] = (65, 54, 84, 52),
) -> ImageDraw.ImageDraw:
    shadow_layer = Image.new("RGBA", image.size, (0, 0, 0, 0))
    sd = ImageDraw.Draw(shadow_layer)
    offset = p(22)
    shifted = (box[0], box[1] + offset, box[2], box[3] + offset)
    sd.rounded_rectangle(shifted, radius=radius, fill=shadow)
    shadow_layer = shadow_layer.filter(ImageFilter.GaussianBlur(p(18)))
    image.alpha_composite(shadow_layer)
    d = ImageDraw.Draw(image)
    d.rounded_rectangle(box, radius=radius, fill=fill)
    return d


def heart_points(cx: float, cy: float, s: float) -> list[tuple[int, int]]:
    points = []
    for i in range(120):
        t = 2 * 3.141592653589793 * i / 120
        x = 16 * (pow(__import__("math").sin(t), 3))
        y = (
            13 * __import__("math").cos(t)
            - 5 * __import__("math").cos(2 * t)
            - 2 * __import__("math").cos(3 * t)
            - __import__("math").cos(4 * t)
        )
        points.append((p(cx + x * s / 32), p(cy - y * s / 32)))
    return points


def draw_heart(d: ImageDraw.ImageDraw, cx: float, cy: float, size: float, fill: tuple[int, int, int, int]) -> None:
    d.polygon(heart_points(cx, cy, size), fill=fill)


def save(name: str, image: Image.Image) -> None:
    image = image.resize((SIZE, SIZE), Image.Resampling.LANCZOS)
    image.save(ROOT / name)


def option_a() -> None:
    image = gradient_icon(0xFFE1C7, 0xD8D2FF)
    d = draw_shadowed_round(image, xywh(282, 220, 462, 592), p(128), color(0x8173D2))
    d.rounded_rectangle(xywh(326, 272, 374, 172), radius=p(52), fill=color(0x252C63))
    d.rounded_rectangle(xywh(342, 492, 342, 226), radius=p(58), fill=color(0xBBB7FF))
    d.ellipse(xywh(360, 300, 84, 84), fill=color(0xFFFDF7))
    d.ellipse(xywh(580, 300, 84, 84), fill=color(0xFFFDF7))
    d.rounded_rectangle(xywh(356, 360, 92, 56), radius=p(26), fill=color(0xFF875B))
    d.rounded_rectangle(xywh(576, 360, 92, 56), radius=p(26), fill=color(0x4FC8A5))
    draw_heart(d, 512, 382, 170, color(0xFF6593))
    d.ellipse(xywh(404, 586, 28, 28), fill=color(0x2E366E))
    d.ellipse(xywh(588, 586, 28, 28), fill=color(0x2E366E))
    d.arc(xywh(446, 612, 134, 110), start=20, end=160, fill=color(0x2E366E), width=p(20))
    d.ellipse(xywh(342, 760, 54, 54), fill=color(0xFFD66B))
    d.ellipse(xywh(628, 760, 54, 54), fill=color(0xFFD66B))
    d.rounded_rectangle(xywh(282, 220, 462, 592), radius=p(128), outline=color(0xFFFFFF, 130), width=p(10))
    save("logo-option-a-warm-bus.png", image)


def option_b() -> None:
    image = gradient_icon(0xFFF2C2, 0xC7F0E8)
    d = ImageDraw.Draw(image)
    shadow = Image.new("RGBA", image.size, (0, 0, 0, 0))
    sd = ImageDraw.Draw(shadow)
    roof = [(p(260), p(420)), (p(512), p(202)), (p(764), p(420))]
    sd.polygon([(x, y + p(20)) for x, y in roof], fill=color(0x4B5C5B, 44))
    shadow = shadow.filter(ImageFilter.GaussianBlur(p(18)))
    image.alpha_composite(shadow)
    d.polygon(roof, fill=color(0x393F73))
    house = [(p(318), p(410)), (p(512), p(240)), (p(706), p(410)), (p(706), p(672)), (p(318), p(672))]
    d.polygon(house, fill=color(0xFF8A6A))
    d.rounded_rectangle(xywh(444, 462, 140, 210), radius=p(42), fill=color(0xFFF5E7))
    draw_heart(d, 512, 560, 120, color(0xFF638D))
    draw_shadowed_round(image, xywh(220, 570, 584, 248), p(92), color(0x63C5C0), color(0x4B5C5B, 45))
    d = ImageDraw.Draw(image)
    d.rounded_rectangle(xywh(286, 616, 280, 86), radius=p(34), fill=color(0x263064))
    d.rounded_rectangle(xywh(594, 616, 110, 86), radius=p(34), fill=color(0x263064))
    d.ellipse(xywh(330, 770, 50, 50), fill=color(0x293262))
    d.ellipse(xywh(672, 770, 50, 50), fill=color(0x293262))
    d.ellipse(xywh(274, 680, 20, 20), fill=color(0xFFF1A7))
    d.ellipse(xywh(750, 680, 20, 20), fill=color(0xFFF1A7))
    d.rounded_rectangle(xywh(220, 570, 584, 248), radius=p(92), outline=color(0xFFFFFF, 128), width=p(9))
    save("logo-option-b-home-route.png", image)


def option_c() -> None:
    image = gradient_icon(0xD4F4FF, 0xFFD7E4)
    d = ImageDraw.Draw(image)
    d.rounded_rectangle(xywh(312, 258, 74, 194), radius=p(24), fill=color(0xFFFFFF, 130))
    d.rounded_rectangle(xywh(406, 208, 90, 244), radius=p(24), fill=color(0xFFFFFF, 130))
    d.rounded_rectangle(xywh(520, 286, 74, 166), radius=p(24), fill=color(0xFFFFFF, 130))
    d.ellipse(xywh(644, 294, 54, 54), fill=color(0xFFFFFF, 130))
    draw_shadowed_round(image, xywh(218, 344, 588, 410), p(100), color(0xFFC74C), color(0x5F4650, 48))
    d = ImageDraw.Draw(image)
    d.rounded_rectangle(xywh(276, 398, 448, 112), radius=p(36), fill=color(0x2D356B))
    d.rounded_rectangle(xywh(276, 546, 448, 112), radius=p(36), fill=color(0xFFF5D7))
    for x in (394, 512, 630):
        d.line((p(x), p(398), p(x), p(658)), fill=color(0xFFC74C), width=p(22))
    draw_heart(d, 512, 612, 130, color(0xFF638D))
    d.ellipse(xywh(340, 740, 58, 58), fill=color(0x31386E))
    d.ellipse(xywh(682, 740, 58, 58), fill=color(0x31386E))
    d.arc(xywh(572, 670, 120, 92), start=20, end=160, fill=color(0x31386E), width=p(18))
    d.rounded_rectangle(xywh(218, 344, 588, 410), radius=p(100), outline=color(0xFFFFFF, 122), width=p(9))
    save("logo-option-c-hk-bus.png", image)


if __name__ == "__main__":
    ROOT.mkdir(parents=True, exist_ok=True)
    option_a()
    option_b()
    option_c()
