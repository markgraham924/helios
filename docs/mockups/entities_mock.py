"""Local typography preview for the Odczyty card; uses the app's fonts and 800x480 geometry."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'app/src/main/assets'
OUT = Path(__file__).with_name('entities-card.png')
BG, SURFACE, TEXT, MUTED = '#181c24', '#262d38', '#f7f4ee', '#c1c7d0'


def font(size):
    return ImageFont.truetype(str(ASSETS / 'Geist.ttf'), round(size))


def clipped(draw, text, face, width):
    if draw.textlength(text, font=face) <= width:
        return text
    while text and draw.textlength(text + '…', font=face) > width:
        text = text[:-1]
    return text + '…'


def text(draw, box, value, size, color, right=False):
    face = font(size)
    value = clipped(draw, value, face, box[2] - box[0])
    bbox = face.getbbox(value)
    x = box[2] - draw.textlength(value, font=face) if right else box[0]
    y = (box[1] + box[3] - bbox[1] - bbox[3]) / 2
    draw.text((x, y), value, font=face, fill=color)


def card(draw, x, y, width, height, title, rows, offline=False):
    draw.rounded_rectangle((x, y, x + width, y + height), radius=18, fill=SURFACE)
    codes = dict(line.split() for line in (ASSETS / 'mdi-codepoints.txt').read_text().splitlines() if len(line.split()) == 2)
    glyph = chr(int(codes['format-list-bulleted'], 16))
    icon_font = ImageFont.truetype(str(ASSETS / 'materialdesignicons-webfont.ttf'), 22)
    row_height = min(36, (height - 52) / len(rows))
    group_height = 22 + 6 + row_height * len(rows)
    top = y + (height - group_height) / 2
    draw.text((x + 12, top), glyph, font=icon_font, fill=MUTED)
    text(draw, (x + 42, top, x + width - 12, top + 22), title + (' (offline)' if offline else ''), 17, MUTED)
    for n, (label, value) in enumerate(rows):
        start = top + 28 + n * row_height
        max_width = (width - 24) * .60
        maximum = min(22, row_height * .75)
        value_size = int(maximum)
        while value_size > 14 and draw.textlength(value, font=font(value_size)) > max_width:
            value_size -= 1
        value_width = min(max_width, draw.textlength(value, font=font(value_size)) + 2)
        text(draw, (x + 12, start, x + width - 12 - value_width - 8, start + row_height), label, min(16, row_height * .60), MUTED)
        text(draw, (x + width - 12 - value_width, start, x + width - 12, start + row_height), value, value_size, MUTED if offline else TEXT, right=True)


if __name__ == '__main__':
    image = Image.new('RGB', (800, 480), BG)
    draw = ImageDraw.Draw(image)
    draw.text((16, 16), 'H E L I O S', font=font(17), fill=MUTED)
    fixtures = [
        ('Pomiary', [('Temp.', '22,4 °C'), ('Wilgotność', '48 %'), ('Pobór', '312 W')]),
        ('Brak odczytu', [('Temp.', '—'), ('Wilgotność', '48 %'), ('Pobór', '312 W')]),
        ('Liczby i stany', [('Poziom', '21,5'), ('Światło', 'Włączone'), ('Ruch', 'Brak')]),
        ('Długie wartości', [('Temperatura w salonie', '-1234,5 °C'), ('Energia dzisiaj', '12345,7 kWh'), ('Pomiar', '—')]),
    ]
    for n, (title, rows) in enumerate(fixtures):
        card(draw, 8 + 198 * n, 60, 190, 132, title, rows)
    card(draw, 8, 200, 190, 272, 'Pomiary', fixtures[0][1])
    card(draw, 206, 200, 388, 132, 'Pomiary — większa karta', fixtures[0][1])
    card(draw, 602, 200, 190, 132, 'Pomiary', fixtures[0][1], offline=True)
    image.save(OUT)
    print(OUT)
