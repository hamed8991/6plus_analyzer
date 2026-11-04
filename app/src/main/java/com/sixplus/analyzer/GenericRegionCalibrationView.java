package com.sixplus.analyzer;

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

/**
 * Reusable, content-agnostic image-region calibration view.
 *
 * This class only lets a user mark rectangular regions on an image and
 * retrieve their normalized coordinates. It does not identify or classify
 * anything in those regions and is intentionally not connected to an analyzer.
 */
public final class GenericRegionCalibrationView extends View {
    public static final class Region {
        public final float left;
        public final float top;
        public final float right;
        public final float bottom;

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
        void onRegionsChanged(int regionCount);
    }

    private final Bitmap image;
    private final ArrayList<Region> regions = new ArrayList<>();
    private final Paint imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint regionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF imageBounds = new RectF();

    private int maxRegions = Integer.MAX_VALUE;
    private float startX;
    private float startY;
    private RectF draft;
    private OnRegionsChangedListener listener;

    public GenericRegionCalibrationView(Context context, Bitmap image) {
        super(context);
        this.image = image;
        regionPaint.setColor(Color.GREEN);
        regionPaint.setStyle(Paint.Style.STROKE);
        regionPaint.setStrokeWidth(3f);
        labelPaint.setColor(Color.YELLOW);
        labelPaint.setTextSize(28f);
        labelPaint.setFakeBoldText(true);
        setFocusable(true);
    }

    public void setMaxRegions(int maximum) {
        maxRegions = Math.max(1, maximum);
        if (regions.size() > maxRegions) {
            while (regions.size() > maxRegions) regions.remove(regions.size() - 1);
            notifyChanged();
        }
        invalidate();
    }

    public void setOnRegionsChangedListener(OnRegionsChangedListener value) {
        listener = value;
    }

    public int getRegionCount() {
        return regions.size();
    }

    /** Returns a defensive, read-only copy of normalized region coordinates. */
    public List<Region> getNormalizedRegions() {
        return Collections.unmodifiableList(new ArrayList<>(regions));
    }

    /** Replaces the current regions with normalized coordinates in the 0..1 range. */
    public void setNormalizedRegions(List<Region> values) {
        regions.clear();
        if (values != null) {
            for (Region region : values) {
                if (region != null && region.width() > 0f && region.height() > 0f
                        && regions.size() < maxRegions) {
                    regions.add(new Region(region.left, region.top, region.right, region.bottom));
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

        if (image == null || image.isRecycled() || image.getWidth() == 0 || image.getHeight() == 0) {
            labelPaint.setColor(Color.WHITE);
            canvas.drawText("No image available", 24f, getHeight() / 2f, labelPaint);
            labelPaint.setColor(Color.YELLOW);
            return;
        }

        float scale = Math.min(getWidth() / (float) image.getWidth(),
                getHeight() / (float) image.getHeight());
        float width = image.getWidth() * scale;
        float height = image.getHeight() * scale;
        imageBounds.set((getWidth() - width) / 2f, (getHeight() - height) / 2f,
                (getWidth() + width) / 2f, (getHeight() + height) / 2f);

        canvas.drawBitmap(image, null, imageBounds, imagePaint);

        for (int i = 0; i < regions.size(); i++) {
            RectF bounds = toViewRect(regions.get(i));
            canvas.drawRect(bounds, regionPaint);
            canvas.drawText(String.valueOf(i + 1), bounds.left + 5f,
                    Math.max(bounds.top + 28f, 28f), labelPaint);
        }
        if (draft != null) canvas.drawRect(draft, regionPaint);
    }

    private RectF toViewRect(Region region) {
        return new RectF(
                imageBounds.left + region.left * imageBounds.width(),
                imageBounds.top + region.top * imageBounds.height(),
                imageBounds.left + region.right * imageBounds.width(),
                imageBounds.top + region.bottom * imageBounds.height()
        );
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
                    float left = clamp(draft.left, imageBounds.left, imageBounds.right);
                    float top = clamp(draft.top, imageBounds.top, imageBounds.bottom);
                    float right = clamp(draft.right, imageBounds.left, imageBounds.right);
                    float bottom = clamp(draft.bottom, imageBounds.top, imageBounds.bottom);

                    if (right - left >= 8f && bottom - top >= 8f) {
                        float nl = (left - imageBounds.left) / imageBounds.width();
                        float nt = (top - imageBounds.top) / imageBounds.height();
                        float nr = (right - imageBounds.left) / imageBounds.width();
                        float nb = (bottom - imageBounds.top) / imageBounds.height();
                        regions.add(new Region(nl, nt, nr, nb));
                        notifyChanged();
                    }
                    draft = null;
                    invalidate();
                }
                return true;
        }
        return true;
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }
}
