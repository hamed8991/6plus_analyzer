package com.sixplus.analyzer;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import java.util.LinkedHashMap;
import java.util.Map;

/** On-device shape/template matcher for the rank glyph in a card corner (not OCR). */
public final class RankVisionRecognizer {
    private static final int W=40, H=48;
    private static final String[] RANKS={"6","7","8","9","10","J","Q","K","A"};
    private final Map<String,boolean[]> templates=new LinkedHashMap<>();

    public RankVisionRecognizer() {
        for(String rank:RANKS) templates.put(rank, render(rank));
    }

    public String recognize(Bitmap screen, CardVisionEngine.DetectedCard card) {
        if(screen==null || card==null || screen.isRecycled()) return "?";
        int cw=Math.min(Math.max(2,Math.round(card.width*.48f)),screen.getWidth()-card.left);
        int ch=Math.min(Math.max(2,Math.round(card.height*.34f)),screen.getHeight()-card.top);
        if(cw<2 || ch<2) return "?";
        Bitmap crop=Bitmap.createBitmap(screen,card.left,card.top,cw,ch);
        try {
            boolean[] sample=normalize(crop);
            if(sample==null) return "?";
            String best="?"; double first=0, second=0;
            for(Map.Entry<String,boolean[]> e:templates.entrySet()) {
                double score=iou(sample,e.getValue());
                if(score>first){second=first;first=score;best=e.getKey();}
                else if(score>second) second=score;
            }
            return first>=.40 && first-second>=.02 ? best : "?";
        } finally { crop.recycle(); }
    }

    private boolean[] render(String text) {
        Bitmap b=Bitmap.createBitmap(W,H,Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(b); c.drawColor(Color.WHITE);
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG); p.setColor(Color.BLACK);
        p.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));
        p.setTextAlign(Paint.Align.CENTER); p.setTextSize(text.length()==2?29:36);
        Paint.FontMetrics fm=p.getFontMetrics();
        c.drawText(text,W/2f,(H-(fm.bottom-fm.top))/2f-fm.top,p);
        boolean[] out=normalize(b); b.recycle(); return out==null?new boolean[W*H]:out;
    }

    private boolean[] normalize(Bitmap b) {
        int w=b.getWidth(),h=b.getHeight(); int[] px=new int[w*h];
        b.getPixels(px,0,w,0,0,w,h);
        int minX=w,minY=h,maxX=-1,maxY=-1;
        for(int y=0;y<h;y++) for(int x=0;x<w;x++) if(ink(px[y*w+x])) {
            minX=Math.min(minX,x);maxX=Math.max(maxX,x);
            minY=Math.min(minY,y);maxY=Math.max(maxY,y);
        }
        if(maxX<minX || maxY<minY) return null;
        int bw=maxX-minX+1,bh=maxY-minY+1;
        float scale=Math.min((W-6f)/bw,(H-6f)/bh);
        int dw=Math.max(1,Math.round(bw*scale)),dh=Math.max(1,Math.round(bh*scale));
        int ox=(W-dw)/2,oy=(H-dh)/2; boolean[] out=new boolean[W*H];
        for(int y=0;y<dh;y++) for(int x=0;x<dw;x++) {
            int sx=minX+Math.min(bw-1,(int)(x/scale));
            int sy=minY+Math.min(bh-1,(int)(y/scale));
            out[(oy+y)*W+ox+x]=ink(px[sy*w+sx]);
        }
        return out;
    }

    private boolean ink(int color) {
        int r=Color.red(color),g=Color.green(color),b=Color.blue(color);
        int lum=(299*r+587*g+114*b)/1000;
        int max=Math.max(r,Math.max(g,b)),min=Math.min(r,Math.min(g,b));
        return lum<150 && (max-min>28 || lum<105);
    }

    private double iou(boolean[] a,boolean[] b) {
        int both=0,either=0;
        for(int i=0;i<a.length;i++){if(a[i]&&b[i])both++;if(a[i]||b[i])either++;}
        return either==0?0:(double)both/either;
    }
}