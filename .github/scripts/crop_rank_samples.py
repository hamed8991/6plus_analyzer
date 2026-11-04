from pathlib import Path
from PIL import Image
import csv
import zipfile

ROOT = Path(".")
OUT = Path("rank-crops")
OUT.mkdir(exist_ok=True)

SLOTS = [
    ("dealer_1", .052, .316, .132, .363),
    ("dealer_2", .133, .316, .213, .363),
    ("split_1", .286, .316, .364, .363),
    ("split_2", .365, .316, .443, .363),
    ("split_3", .444, .316, .522, .363),
    ("split_4", .523, .316, .601, .363),
    ("split_5", .602, .316, .680, .363),
    ("player_1", .785, .316, .864, .363),
    ("player_2", .865, .316, .944, .363),
]

images = sorted(ROOT.glob("Screenshot_*.jpg")) + sorted(ROOT.glob("Screenshot_*.jpeg")) + sorted(ROOT.glob("Screenshot_*.png"))
if not images:
    raise SystemExit("No Screenshot_*.jpg/png files found in repository root")

rows = []
for image_path in images:
    image = Image.open(image_path).convert("RGB")
    w, h = image.size
    image_dir = OUT / image_path.stem
    image_dir.mkdir(parents=True, exist_ok=True)

    for slot, l, t, r, b in SLOTS:
        x1, y1 = round(l*w), round(t*h)
        x2, y2 = round(r*w), round(b*h)
        card = image.crop((x1, y1, x2, y2))
        # Keep complete ROI for visual audit; rank patch isolates the upper-left
        # index area while retaining a little margin for anti-aliased edges.
        card.save(image_dir / f"{slot}_card.png")
        rank = card.crop((0, 0, max(1, round(card.width*.58)), max(1, round(card.height*.62))))
        rank.save(image_dir / f"{slot}_rank.png")
        rows.append([image_path.name, w, h, slot, x1, y1, x2, y2, rank.width, rank.height])

with (OUT / "manifest.csv").open("w", newline="", encoding="utf-8") as f:
    writer = csv.writer(f)
    writer.writerow(["source", "image_width", "image_height", "slot", "left", "top", "right", "bottom", "rank_crop_width", "rank_crop_height"])
    writer.writerows(rows)

with zipfile.ZipFile("rank-crops.zip", "w", zipfile.ZIP_DEFLATED) as z:
    for p in OUT.rglob("*"):
        if p.is_file():
            z.write(p, p.relative_to(OUT.parent))

print(f"Processed {len(images)} screenshots; created {len(rows)} card crops and {len(rows)} rank patches.")
