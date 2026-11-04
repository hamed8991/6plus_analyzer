#!/usr/bin/env python3
"""Prototype classifier using the FULL card crop; rank and suit are independent.
Run: python tools/full_card_classifier.py rank-crops.zip --labels tools/card_labels.csv
CSV columns: image,rank,suit. Only visually verified cards should be labeled.
"""
import argparse,csv,io,zipfile
from pathlib import Path
from collections import defaultdict
import numpy as np
from PIL import Image
RANKS={"6","7","8","9","10","J","Q","K","A"}
SUITS={"clubs","diamonds","hearts","spades"}
SLOTS={"player_1","player_2","split_1","split_2","split_3","split_4","split_5"}
def feature(raw):
 im=Image.open(io.BytesIO(raw)).convert("RGB"); im.thumbnail((72,96))
 canvas=Image.new("RGB",(72,96),(110,75,55)); canvas.paste(im,((72-im.width)//2,(96-im.height)//2))
 a=np.asarray(canvas,dtype=np.float32)/255
 # Whole-crop spatial color descriptor: RGB and grayscale pooled over 4x4 cells.
 g=(.299*a[:,:,0]+.587*a[:,:,1]+.114*a[:,:,2])[:,:,None]
 v=np.concatenate([np.concatenate([x[y:y+4,z:z+4].mean((0,1)) for z in range(0,72,4)]) for x in (a,g) for y in range(0,96,4)])
 return v/(np.linalg.norm(v)+1e-8)
def main():
 p=argparse.ArgumentParser(); p.add_argument("archive"); p.add_argument("--labels",default="tools/card_labels.csv"); p.add_argument("--out",default="full-card-results.csv"); a=p.parse_args()
 labels={}
 with open(a.labels,newline="",encoding="utf-8-sig") as f:
  for r in csv.DictReader(f):
   if r.get("rank") in RANKS and r.get("suit") in SUITS: labels[r["image"]]=(r["rank"],r["suit"])
 with zipfile.ZipFile(a.archive) as z:
  data=[]
  for n in z.namelist():
   if n.endswith("_card.png") and Path(n).stem[:-5] in SLOTS:
    data.append((n,n.split("/")[-2],feature(z.read(n))))
 rows=[]
 for n,grp,v in data:
  pool=[(float(v@q),labels[k]) for k,g,q in data if k in labels and g!=grp]
  scores=[defaultdict(list),defaultdict(list)]
  for sim,(rank,suit) in pool: scores[0][rank].append(sim); scores[1][suit].append(sim)
  def pred(s):
   x=sorted(((sum(v)/len(v),k) for k,v in s.items()),reverse=True)
   return x[0][1] if x and (len(x)==1 or x[0][0]-x[1][0]>=.012) else "?"
  pr,ps=pred(scores[0]),pred(scores[1]); gt=labels.get(n)
  rows.append({"image":n,"rank":pr,"suit":ps,"known_rank":gt[0] if gt else "","known_suit":gt[1] if gt else "","rank_ok":str(pr==gt[0]).lower() if gt else "","suit_ok":str(ps==gt[1]).lower() if gt else ""})
 with open(a.out,"w",newline="",encoding="utf-8") as f:
  w=csv.DictWriter(f,fieldnames=list(rows[0])); w.writeheader(); w.writerows(rows)
 test=[r for r in rows if r["known_rank"]]
 print("Full-card crops:",len(data)," verified labels:",len(labels)," evaluated:",len(test))
 if test:
  print("Rank:",sum(r["rank_ok"]=="true" for r in test),"/",len(test)," Suit:",sum(r["suit_ok"]=="true" for r in test),"/",len(test))
 else: print("No verified labels: populate labels CSV before reporting accuracy.")
 print("Results:",a.out)
if __name__=="__main__": main()
