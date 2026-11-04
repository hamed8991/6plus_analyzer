package com.sixplus.analyzer;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.google.android.gms.tasks.Tasks;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Full-card rank recognizer.
 * Unlike the previous corner-template matcher, this sends the complete card ROI
 * to ML Kit OCR after enlarging it. Unknown/ambiguous readings stay '?'.
 */
public final class RankVisionRecognizer implements AutoCloseable {
    private static final Pattern TOKEN = Pattern.compile("(?<![A-Z0-9])(10|[6789AJQK])(?![A-Z0-9])");
    private final TextRecognizer recognizer =
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

    public String recognize(Bitmap screen, CardVisionEngine.DetectedCard card) {
        if (screen == null || card == null || screen.isRecycled()) return "?";
        int left = Math.max(0, card.left);
        int top = Math.max(0, card.top);
        int width = Math.min(card.width, screen.getWidth() - left);
        int height = Math.min(card.height, screen.getHeight() - top);
        if (width < 2 || height < 2) return "?";

        Bitmap fullCard = null;
        Bitmap enlarged = null;
        try {
            // Use the ENTIRE detected card rectangle, not only its upper-left corner.
            fullCard = Bitmap.createBitmap(screen, left, top, width, height);
            Matrix matrix = new Matrix();
            matrix.postScale(3.0f, 3.0f);
            enlarged = Bitmap.createBitmap(fullCard, 0, 0,
                    fullCard.getWidth(), fullCard.getHeight(), matrix, true);

            Text result = Tasks.await(
                    recognizer.process(InputImage.fromBitmap(enlarged, 0)),
                    2500, TimeUnit.MILLISECONDS);
            return extractRank(result);
        } catch (Exception ignored) {
            return "?";
        } finally {
            if (enlarged != null && !enlarged.isRecycled()) enlarged.recycle();
            if (fullCard != null && !fullCard.isRecycled()) fullCard.recycle();
        }
    }

    private String extractRank(Text result) {
        if (result == null) return "?";
        StringBuilder all = new StringBuilder();
        for (Text.TextBlock block : result.getTextBlocks()) {
            all.append(' ').append(block.getText());
            for (Text.Line line : block.getLines()) all.append(' ').append(line.getText());
        }
        String normalized = all.toString().toUpperCase(Locale.ROOT)
                .replace('Ｏ', '0')
                .replace('Ｉ', '1')
                .replace('|', '1')
                .replace('Ｇ', '6')
                .replace('Ｓ', '5')
                .replaceAll("[^A-Z0-9]", " ");

        Matcher matcher = TOKEN.matcher(normalized);
        String found = null;
        while (matcher.find()) {
            String candidate = matcher.group(1);
            if (found != null && !found.equals(candidate)) return "?";
            found = candidate;
        }
        return found == null ? "?" : found;
    }

    @Override
    public void close() {
        recognizer.close();
    }
}
