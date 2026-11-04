package com.example.regiondemo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Content-agnostic view for marking rectangular regions on an image. */
public final class GenericRegionCalibrationView extends View {
    public static final class Region {
        public final float left, top, right, bottom;

        public Region(float left, float top, float right, float bottom) {
            this.left = clamp01(Math.min(left, right));
            this.top = clamp01(Math.min(top, bottom));
            this.right = clamp01(Math.max(left, right));
            this.bottom = clamp01(Math.max(top, bottom));
        }

        private static float clamp01(float value) {
            return Math.max(0f, Math.min(1f, value));
        }

        public float width() { return right - left; }
        public float height() { return bottom - top; }
    }

    public interface OnRegionsChangedListener {
        void onRegionsChanged(int count);
    }

    private final Bitmap image;
    private final ArrayList<Region> regions = new ArrayList<>();
    private final Paint imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint regionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF imageBounds = new RectF();

    private int maxRegions = Integer.MAX_VALUE;
    private float startX, startY;
    private RectF draft;
    private OnRegionsChangedListener listener;

    public GenericRegionCalibrationView(Context context, Bitmap image) {
        super(context);
        this.image = image;
        regionPaint.setColor(Color.rgb(0, 170, 90));
        regionPaint.setStyle(Paint.Style.STROKE);
        regionPaint.setStrokeWidth(4f);
        labelPaint.setColor(Color.rgb(255, 190, 0));
        labelPaint.setTextSize(30f);
        labelPaint.setFakeBoldText(true);
    }

    public void setMaxRegions(int maximum) {
        maxRegions = Math.max(1, maximum);
        while (regions.size() > maxRegions) regions.remove(regions.size() - 1);
        invalidate();
    }

    public void setOnRegionsChangedListener(OnRegionsChangedListener value) {
        listener = value;
    }

    public int getRegionCount() { return regions.size(); }

    public List<Region> getNormalizedRegions() {
        return Collections.unmodifiableList(new ArrayList<>(regions));
    }

    public void setNormalizedRegions(List<Region> values) {
        regions.clear();
        if (values != null) {
            for (Region r : values) {
                if (r != null && r.width() > 0f && r.height() > 0f
                        && regions.size() < maxRegions) {
                    regions.add(new Region(r.left, r.top, r.right, r.bottom));
                }
            }
        }
        notifyChanged();
        invalidate();
    }

    public void undoLastRegion() {
        if (!regions.isEmpty()) {
            regions.remove(regions.size() - 1);
            notifyChanged();
            invalidate();
        }
    }

    public void clearRegions() {
        regions.clear();
        draft = null;
        notifyChanged();
        invalidate();
    }

    private void notifyChanged() {
        if (listener != null) listener.onRegionsChanged(regions.size());
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Color.rgb(28, 30, 34));
        if (image == null || image.isRecycled()) return;

        float scale = Math.min(getWidth() / (float) image.getWidth(),
                getHeight() / (float) image.getHeight());
        float w = image.getWidth() * scale;
        float h = image.getHeight() * scale;
        imageBounds.set((getWidth() - w) / 2f, (getHeight() - h) / 2f,
                (getWidth() + w) / 2f, (getHeight() + h) / 2f);
        canvas.drawBitmap(image, null, imageBounds, imagePaint);

        for (int i = 0; i < regions.size(); i++) {
            RectF r = toViewRect(regions.get(i));
            canvas.drawRect(r, regionPaint);
            canvas.drawText(String.valueOf(i + 1), r.left + 5f, r.top + 30f, labelPaint);
        }
        if (draft != null) canvas.drawRect(draft, regionPaint);
    }

    private RectF toViewRect(Region r) {
        return new RectF(imageBounds.left + r.left * imageBounds.width(),
                imageBounds.top + r.top * imageBounds.height(),
                imageBounds.left + r.right * imageBounds.width(),
                imageBounds.top + r.bottom * imageBounds.height());
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (image == null || image.isRecycled() || regions.size() >= maxRegions) return true;

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (!imageBounds.contains(event.getX(), event.getY())) return true;
                startX = event.getX();
                startY = event.getY();
                draft = new RectF(startX, startY, startX, startY);
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (draft != null) {
                    draft.set(Math.min(startX, event.getX()), Math.min(startY, event.getY()),
                            Math.max(startX, event.getX()), Math.max(startY, event.getY()));
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (draft != null) {
                    float l = clamp(draft.left, imageBounds.left, imageBounds.right);
                    float t = clamp(draft.top, imageBounds.top, imageBounds.bottom);
                    float r = clamp(draft.right, imageBounds.left, imageBounds.right);
                    float b = clamp(draft.bottom, imageBounds.top, imageBounds.bottom);
                    if (r - l >= 8f && b - t >= 8f) {
                        regions.add(new Region(
                                (l - imageBounds.left) / imageBounds.width(),
                                (t - imageBounds.top) / imageBounds.height(),
                                (r - imageBounds.left) / imageBounds.width(),
                                (b - imageBounds.top) / imageBounds.height()));
                        notifyChanged();
                    }
                    draft = null;
                    invalidate();
                }
                return true;
        }
        return true;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
