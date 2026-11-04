package com.sixplus.analyzer;

import android.graphics.Bitmap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Fixed-layout locator for the mobile 6+ Poker table.
 *
 * The nine normalized regions are based on the user-provided 832x1856
 * game screenshot. Coordinates are normalized so they scale with capture size.
 * This stage only locates card slots; rank and suit intentionally remain unknown.
 */
public final class CardVisionEngine {
    public static final String[] SLOT_NAMES = {
            "Dealer 1", "Dealer 2",
            "Split 1", "Split 2", "Split 3", "Split 4", "Split 5",
            "Player 1", "Player 2"
    };

    // left, top, right, bottom as fractions of the captured screen.
    // Bounds tightly cover each card face and exclude the payout labels below.
    private static final float[][] SLOT_BOUNDS = {
            {0.052f, 0.316f, 0.132f, 0.363f},
            {0.133f, 0.316f, 0.213f, 0.363f},
            {0.286f, 0.316f, 0.364f, 0.363f},
            {0.365f, 0.316f, 0.443f, 0.363f},
            {0.444f, 0.316f, 0.522f, 0.363f},
            {0.523f, 0.316f, 0.601f, 0.363f},
            {0.602f, 0.316f, 0.680f, 0.363f},
            {0.785f, 0.316f, 0.864f, 0.363f},
            {0.865f, 0.316f, 0.944f, 0.363f}
    };

    public static final class DetectedCard {
        public final int index;
        public final String slot;
        public final int left, top, width, height;
        public final String rank = "?";
        public final String suit = "?";
        public final float confidence;

        DetectedCard(int i, String name, int l, int t, int w, int h) {
            index = i;
            slot = name;
            left = l;
            top = t;
            width = w;
            height = h;
            confidence = 1.0f; // fixed ROI position, not a card-face recognition score
        }

        public String toRecord() {
            return index + "," + slot + "," + left + "," + top + ","
                    + width + "," + height + ",?,?";
        }
    }

    public static final class Result {
        public final List<DetectedCard> cards;
        public final String csv;

        Result(List<DetectedCard> found) {
            cards = Collections.unmodifiableList(found);
            StringBuilder out = new StringBuilder(
                    "index,slot,left,top,width,height,rank,suit\n");
            for (DetectedCard card : found) out.append(card.toRecord()).append('\n');
            csv = out.toString();
        }
    }

    public Result analyze(Bitmap source) {
        List<DetectedCard> found = new ArrayList<>();
        if (source == null || source.isRecycled()) return new Result(found);

        int screenW = source.getWidth();
        int screenH = source.getHeight();
        for (int i = 0; i < SLOT_BOUNDS.length; i++) {
            float[] b = SLOT_BOUNDS[i];
            int left = clamp(Math.round(b[0] * screenW), 0, screenW - 1);
            int top = clamp(Math.round(b[1] * screenH), 0, screenH - 1);
            int right = clamp(Math.round(b[2] * screenW), left + 1, screenW);
            int bottom = clamp(Math.round(b[3] * screenH), top + 1, screenH);
            found.add(new DetectedCard(i, SLOT_NAMES[i], left, top,
                    right - left, bottom - top));
        }
        return new Result(found);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }
}
