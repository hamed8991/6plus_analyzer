package com.sixplus.analyzer;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.util.ArrayList;
import java.util.Locale;

public class CalibrationActivity extends Activity {
    private CardSelectionView selectionView;
    private TextView status;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(16, 16, 16, 16);
        status = new TextView(this);
        status.setText("به ترتیب مستطیل کارت‌ها را بکش: بازیکن ۱ و ۲، اسپلیت ۱ تا ۵، دیلر ۱ و ۲");
        status.setTextSize(16);
        root.addView(status);
        File dir = getExternalFilesDir(null);
        File shot = dir == null ? null : new File(dir, "latest-screen.png");
        Bitmap bitmap = shot != null && shot.exists() ? BitmapFactory.decodeFile(shot.getAbsolutePath()) : null;
        selectionView = new CardSelectionView(bitmap);
        root.addView(selectionView, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout buttons = new LinearLayout(this);
        Button undo = new Button(this); undo.setText("برگشت");
        Button clear = new Button(this); clear.setText("پاک‌کردن");
        Button save = new Button(this); save.setText("ذخیره جایگاه‌ها");
        buttons.addView(undo, new LinearLayout.LayoutParams(0, -2, 1));
        buttons.addView(clear, new LinearLayout.LayoutParams(0, -2, 1));
        buttons.addView(save, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(buttons);
        setContentView(root);
        if (bitmap == null) status.setText("هنوز تصویری ذخیره نشده. اول دریافت تصویر را فعال کن و دوباره این صفحه را باز کن.");
        undo.setOnClickListener(v -> { selectionView.undo(); updateStatus(); });
        clear.setOnClickListener(v -> { selectionView.clear(); updateStatus(); });
        save.setOnClickListener(v -> {
            if (!selectionView.save(getPreferences(MODE_PRIVATE))) {
                Toast.makeText(this, "ابتدا هر ۹ مستطیل را انتخاب کن.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "۹ جایگاه ذخیره شد.", Toast.LENGTH_LONG).show();
                finish();
            }
        });
        selectionView.setOnCountChangedListener(this::updateStatus);
    }

    private void updateStatus() {
        String[] names = {"بازیکن ۱", "بازیکن ۲", "اسپلیت ۱", "اسپلیت ۲", "اسپلیت ۳",
                "اسپلیت ۴", "اسپلیت ۵", "دیلر ۱", "دیلر ۲"};
        int n = selectionView.count();
        status.setText("مستطیل " + (n + 1) + " از ۹: " + (n < 9 ? names[n] : "کامل شد")
                + "\\nترتیب: بازیکن ۲، اسپلیت ۵، دیلر ۲");
    }

    private class CardSelectionView extends View {
        private final Bitmap bitmap;
        private final ArrayList<RectF> rects = new ArrayList<>();
        private final Paint imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint boxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private RectF imageBounds = new RectF();
        private float downX, downY;
        private RectF current;
        private Runnable changed;

        CardSelectionView(Bitmap b) {
            super(CalibrationActivity.this);
            bitmap = b;
            boxPaint.setColor(Color.GREEN);
            boxPaint.setStyle(Paint.Style.STROKE);
            boxPaint.setStrokeWidth(4);
        }
        void setOnCountChangedListener(Runnable r) { changed = r; }
        int count() { return rects.size(); }
        void undo() { if (!rects.isEmpty()) rects.remove(rects.size()-1); invalidate(); if(changed!=null)changed.run(); }
        void clear() { rects.clear(); current=null; invalidate(); if(changed!=null)changed.run(); }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            c.drawColor(Color.rgb(25,25,25));
            if (bitmap == null) {
                Paint p = new Paint(); p.setColor(Color.WHITE); p.setTextSize(34);
                c.drawText("تصویر موجود نیست", 30, getHeight()/2f, p); return;
            }
            float scale = Math.min(getWidth()/(float)bitmap.getWidth(), getHeight()/(float)bitmap.getHeight());
            float w=bitmap.getWidth()*scale, h=bitmap.getHeight()*scale;
            imageBounds.set((getWidth()-w)/2f,(getHeight()-h)/2f,(getWidth()+w)/2f,(getHeight()+h)/2f);
            c.drawBitmap(bitmap, null, imageBounds, imagePaint);
            for(int i=0;i<rects.size();i++){ c.drawRect(toView(rects.get(i)), boxPaint); drawNumber(c,i+1,toView(rects.get(i))); }
            if(current!=null) c.drawRect(toView(current),boxPaint);
        }
        private RectF toView(RectF r) {
            float sx=imageBounds.width()/bitmap.getWidth(), sy=imageBounds.height()/bitmap.getHeight();
            return new RectF(imageBounds.left+r.left*sx,imageBounds.top+r.top*sy,
                    imageBounds.left+r.right*sx,imageBounds.top+r.bottom*sy);
        }
        private void drawNumber(Canvas c,int n,RectF r) {
            Paint p=new Paint(Paint.ANTI_ALIAS_FLAG); p.setColor(Color.YELLOW); p.setTextSize(30);
            c.drawText(String.valueOf(n),r.left+4,r.top+30,p);
        }
        @Override public boolean onTouchEvent(MotionEvent e) {
            if(bitmap==null || rects.size()>=9) return true;
            if(e.getAction()==MotionEvent.ACTION_DOWN) {
                if(!imageBounds.contains(e.getX(),e.getY())) return true;
                downX=e.getX(); downY=e.getY(); current=new RectF(downX,downY,downX,downY); invalidate(); return true;
            }
            if(e.getAction()==MotionEvent.ACTION_MOVE && current!=null) {
                current.set(Math.min(downX,e.getX()),Math.min(downY,e.getY()),Math.max(downX,e.getX()),Math.max(downY,e.getY())); invalidate(); return true;
            }
            if(e.getAction()==MotionEvent.ACTION_UP && current!=null) {
                current.set(Math.max(imageBounds.left,Math.min(current.left,imageBounds.right)),
                        Math.max(imageBounds.top,Math.min(current.top,imageBounds.bottom)),
                        Math.max(imageBounds.left,Math.min(current.right,imageBounds.right)),
                        Math.max(imageBounds.top,Math.min(current.bottom,imageBounds.bottom)));
                if(current.width()>12 && current.height()>12) {
                    float sx=bitmap.getWidth()/imageBounds.width(), sy=bitmap.getHeight()/imageBounds.height();
                    rects.add(new RectF((current.left-imageBounds.left)*sx,(current.top-imageBounds.top)*sy,
                            (current.right-imageBounds.left)*sx,(current.bottom-imageBounds.top)*sy));
                }
                current=null; invalidate(); if(changed!=null)changed.run(); return true;
            }
            return true;
        }
        boolean save(android.content.SharedPreferences prefs) {
            if(rects.size()!=9 || bitmap==null) return false;
            android.content.SharedPreferences.Editor ed=prefs.edit();
            ed.putInt("card_roi_count",9);
            for(int i=0;i<9;i++){
                RectF r=rects.get(i); String k="card_roi_"+i+"_";
                ed.putFloat(k+"x",r.left/bitmap.getWidth());
                ed.putFloat(k+"y",r.top/bitmap.getHeight());
                ed.putFloat(k+"w",r.width()/bitmap.getWidth());
                ed.putFloat(k+"h",r.height()/bitmap.getHeight());
            }
            ed.apply(); return true;
        }
    }
}