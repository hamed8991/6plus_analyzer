#!/usr/bin/env python3
"""Offline 6+ Poker card-crop inspection and evaluation harness.

This is deliberately separate from the Android app. It reads the ZIP produced by
.github/workflows/extract-rank-crops.yml, keeps only player_1/player_2 and
split_1..split_5, and writes a contact sheet plus predictions/evaluation files.

A real accuracy score requires ground-truth labels. Create labels.csv with columns:
image,rank,suit
where image is the relative path inside the ZIP (for example
Screenshot_.../player_1_card.png), rank is one of 6,7,8,9,10,J,Q,K,A,
and suit is one of clubs,diamonds,hearts,spades. Leave unknown cards out.
"""
import argparse
import csv
import io
import os
import zipfile
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont, ImageOps

RANKS = {"6", "7", "8", "9", "10", "J", "Q", "K", "A"}
SUITS = {"clubs", "diamonds", "hearts", "spades"}
SLOTS = {"player_1", "player_2", "split_1", "split_2", "split_3", "split_4", "split_5"}

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("zipfile", help="rank-crops.zip from GitHub Actions")
    ap.add_argument("--labels", default="labels.csv", help="CSV ground truth file")
    ap.add_argument("--out", default="card-test-output")
    args = ap.parse_args()
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)

    with zipfile.ZipFile(args.zipfile) as zf:
        names = [n for n in zf.namelist()
                 if n.endswith("_card.png")
                 and Path(n).parent.name != "rank-crops"
                 and Path(n).split if False else True]
        # Filter to the seven requested positions; ignore all dealer crops.
        names = [n for n in names if Path(n).name[:-9] in SLOTS]
        names.sort()

        labels = {}
        if os.path.exists(args.labels):
            with open(args.labels, newline="", encoding="utf-8-sig") as f:
                for row in csv.DictReader(f):
                    if row.get("image") and row.get("rank") in RANKS and row.get("suit") in SUITS:
                        labels[row["image"]] = (row["rank"], row["suit"])

        thumbs = []
        predictions = []
        for name in names:
            im = Image.open(io.BytesIO(zf.read(name))).convert("RGB")
            # Preserve the complete card crop; only resize for the contact sheet.
            thumb = ImageOps.contain(im, (180, 230))
            tile = Image.new("RGB", (210, 280), "white")
            tile.paste(thumb, ((210-thumb.width)//2, 8))
            slot = Path(name).name[:-9]
            tile_name = Path(name).parent.name
            d = ImageDraw.Draw(tile)
            d.text((8, 244), slot, fill="black")
            d.text((8, 260), tile_name[-24:], fill="black")
            thumbs.append(tile)
            gt = labels.get(name)
            predictions.append({"image": name, "slot": slot,
                                "ground_truth_rank": gt[0] if gt else "",
                                "ground_truth_suit": gt[1] if gt else "",
                                "status": "labeled" if gt else "needs_label"})

        cols = 4
        rows = (len(thumbs) + cols - 1)//cols
        sheet = Image.new("RGB", (cols*210, rows*280), "#dddddd")
        for i, tile in enumerate(thumbs):
            sheet.paste(tile, ((i%cols)*210, (i//cols)*280))
        sheet.save(out/"seven_card_contact_sheet.jpg", quality=92)

        with open(out/"predictions.csv", "w", newline="", encoding="utf-8") as f:
            w = csv.DictWriter(f, fieldnames=["image","slot","ground_truth_rank","ground_truth_suit","status"])
            w.writeheader()
            w.writerows(predictions)

        total = len(predictions)
        labeled = sum(p["status"] == "labeled" for p in predictions)
        (out/"README.txt").write_text(
            f"Seven-position crop set: {total} cards across all screenshots.\n"
            f"Ground-truth labels supplied: {labeled}.\n"
            "This first-stage tool prepares and audits the real card images; it does NOT invent predictions.\n"
            "Add verified rank and suit labels to labels.csv, then run the classifier/evaluator stage.\n"
            "Dealer positions are excluded.\n", encoding="utf-8")
    print(f"Prepared {total} player/split card crops; {labeled} labeled.")
    print(f"Open {out/'seven_card_contact_sheet.jpg'} and {out/'predictions.csv'}.")

if __name__ == "__main__":
    main()
