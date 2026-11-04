package com.sixplus.analyzer;

import android.app.*;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.*;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;

public class CaptureService extends Service {

    public static final String ACTION_STOP =
            "com.sixplus.analyzer.STOP_CAPTURE";

    public static final String EXTRA_RESULT_CODE = "result_code";
    public static final String EXTRA_RESULT_DATA = "result_data";

    private static final String CHANNEL = "screen_capture";

    private MediaProjection projection;
    private VirtualDisplay display;
    private ImageReader reader;
    private long lastSaved;
    
private TextRecognizer textRecognizer;
private final CardVisionEngine cardVisionEngine = new CardVisionEngine();
private TextView recognitionLine;
    private TextView playerCardsLine;
    private TextView splitCardsLine;
    private TextView dealerCardsLine;
private boolean isRecognizing = false;
    private WindowManager windowManager;
    private View overlayView;
    private LinearLayout overlayBody;
    private boolean collapsed = false;

    @Override
    public void onCreate() {
        super.onCreate();
        textRecognizer = TextRecognition.getClient(
        TextRecognizerOptions.DEFAULT_OPTIONS
);

        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL,
                    "Screen capture",
                    NotificationManager.IMPORTANCE_LOW
            );

            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
                    .createNotificationChannel(channel);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopCapture();
            stopSelf();
            return START_NOT_STICKY;
        }

        startForeground(7, notification());

        if (intent == null || !intent.hasExtra(EXTRA_RESULT_DATA)) {
            stopSelf();
            return START_NOT_STICKY;
        }

        if (Settings.canDrawOverlays(this)) {
            showOverlay();
        }

        int code = intent.getIntExtra(EXTRA_RESULT_CODE, 0);

        Intent data;

        if (Build.VERSION.SDK_INT >= 33) {
            data = intent.getParcelableExtra(
                    EXTRA_RESULT_DATA,
                    Intent.class
            );
        } else {
            data = intent.getParcelableExtra(EXTRA_RESULT_DATA);
        }

        if (data == null) {
            stopCapture();
            stopSelf();
            return START_NOT_STICKY;
        }

        projection = ((MediaProjectionManager)
                getSystemService(MEDIA_PROJECTION_SERVICE))
                .getMediaProjection(code, data);

        if (projection == null) {
            stopCapture();
            stopSelf();
            return START_NOT_STICKY;
        }

        DisplayMetrics metrics = new DisplayMetrics();

        ((WindowManager) getSystemService(WINDOW_SERVICE))
                .getDefaultDisplay()
                .getRealMetrics(metrics);

        reader = ImageReader.newInstance(
                metrics.widthPixels,
                metrics.heightPixels,
                PixelFormat.RGBA_8888,
                2
        );

        reader.setOnImageAvailableListener(
                this::saveFrame,
                new Handler(Looper.getMainLooper())
        );

        projection.registerCallback(
                new MediaProjection.Callback() {
                    @Override
                    public void onStop() {
                        stopCapture();
                        stopSelf();
                    }
                },
                new Handler(Looper.getMainLooper())
        );

        display = projection.createVirtualDisplay(
                "SixPlusCapture",
                metrics.widthPixels,
                metrics.heightPixels,
                metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.getSurface(),
                null,
                null
        );

        return START_NOT_STICKY;
    }

    private void showOverlay() {

        if (overlayView != null || !Settings.canDrawOverlays(this)) {
            return;
        }

        windowManager = (WindowManager)
                getSystemService(WINDOW_SERVICE);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(12, 10, 12, 10);

        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.argb(238, 22, 28, 38));
        background.setCornerRadius(22);
        background.setStroke(2, Color.argb(220, 110, 150, 190));

        panel.setBackground(background);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("6+ Analyzer  •  زنده");
        title.setTextColor(Color.WHITE);
        title.setTextSize(13);

        header.addView(
                title,
                new LinearLayout.LayoutParams(0, -2, 1)
        );

        Button fold = new Button(this);
        fold.setText("−");
        fold.setMinWidth(42);
        fold.setMinimumWidth(42);
        fold.setPadding(0, 0, 0, 0);

        header.addView(
                fold,
                new LinearLayout.LayoutParams(48, 42)
        );

        panel.addView(header);

        overlayBody = new LinearLayout(this);
        overlayBody.setOrientation(LinearLayout.VERTICAL);
        overlayBody.setPadding(4, 8, 4, 2);

        addOverlayLine("وضعیت: دریافت تصویر فعال");
        playerCardsLine = addOverlayLine("بازیکن: [ ؟ ] [ ؟ ]");
        splitCardsLine = addOverlayLine("اسپلیت: [ ؟ ] [ ؟ ] [ ؟ ] [ ؟ ] [ ؟ ]");
        dealerCardsLine = addOverlayLine("دیلر: [ ؟ ] [ ؟ ]");
        recognitionLine = addOverlayLine("وضعیت تشخیص: در انتظار تصویر");
        addOverlayLine("پیشنهاد شرط: هنوز محاسبه نمی‌شود");

        panel.addView(overlayBody);

        fold.setOnClickListener(v -> {
            collapsed = !collapsed;

            overlayBody.setVisibility(
                    collapsed ? View.GONE : View.VISIBLE
            );

            fold.setText(collapsed ? "+" : "−");
        });

        header.setOnTouchListener(new View.OnTouchListener() {

            private int startX;
            private int startY;
            private float touchX;
            private float touchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {

                WindowManager.LayoutParams params =
                        (WindowManager.LayoutParams)
                                panel.getLayoutParams();

                if (event.getAction() == MotionEvent.ACTION_DOWN) {

                    startX = params.x;
                    startY = params.y;

                    touchX = event.getRawX();
                    touchY = event.getRawY();

                    return true;
                }

                if (event.getAction() == MotionEvent.ACTION_MOVE) {

                    params.x = startX +
                            (int) (event.getRawX() - touchX);

                    params.y = startY +
                            (int) (event.getRawY() - touchY);

                    windowManager.updateViewLayout(panel, params);

                    return true;
                }

                return event.getAction() == MotionEvent.ACTION_UP;
            }
        });

        WindowManager.LayoutParams params =
                new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.WRAP_CONTENT,

                        Build.VERSION.SDK_INT >= 26
                                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                                : WindowManager.LayoutParams.TYPE_PHONE,

                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,

                        PixelFormat.TRANSLUCENT
                );

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 18;
        params.y = 180;

        overlayView = panel;

        try {
            windowManager.addView(overlayView, params);
        } catch (Exception e) {
            overlayView = null;
        }
    }

    private TextView addOverlayLine(String value) {

        TextView line = new TextView(this);
        line.setText(value);
        line.setTextColor(Color.WHITE);
        line.setTextSize(12);
        line.setPadding(2, 5, 2, 5);

        overlayBody.addView(line);
        return line;
    }

    private void saveFrame(ImageReader imageReader) {
        Image image = null;
        Bitmap padded = null;
        Bitmap crop = null;
        Bitmap cardArea = null;
        Bitmap scaledCardArea = null;
        boolean recognitionStarted = false;

        try {
            image = imageReader.acquireLatestImage();
            if (image == null) return;

            long now = System.currentTimeMillis();
            if (now - lastSaved < 2000) return;
            lastSaved = now;

            Image.Plane plane = image.getPlanes()[0];
            ByteBuffer buffer = plane.getBuffer();
            int pixelStride = plane.getPixelStride();
            int rowStride = plane.getRowStride();
            int paddedWidth = image.getWidth()
                    + (rowStride - pixelStride * image.getWidth()) / pixelStride;

            padded = Bitmap.createBitmap(
                    paddedWidth, image.getHeight(), Bitmap.Config.ARGB_8888
            );
            buffer.rewind();
            padded.copyPixelsFromBuffer(buffer);

            crop = Bitmap.createBitmap(
                    padded, 0, 0, image.getWidth(), image.getHeight()
            );

            File directory = getExternalFilesDir(null);
            if (directory == null) {
                throw new IllegalStateException("فضای ذخیره‌سازی برنامه در دسترس نیست");
            }
            File file = new File(directory, "latest-screen.png");
            try (FileOutputStream output = new FileOutputStream(file)) {
                if (!crop.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    throw new IllegalStateException("ذخیره تصویر ناموفق بود");
                }
            }

            // First-stage visual detection: locate card-shaped light regions
            // and persist their screen coordinates for calibration/classification.
            CardVisionEngine.Result visualResult = cardVisionEngine.analyze(crop);
            File recordsFile = new File(directory, "detected-cards.csv");
            try (FileOutputStream records = new FileOutputStream(recordsFile, false)) {
                records.write(visualResult.csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            if (recognitionLine != null) {
                recognitionLine.setText("مکان‌یابی ثابت: "
                        + visualResult.cards.size() + " جایگاه از ۹ | رتبه و خال: مرحله بعد");
            }

            // Recognize the rank from the upper-left index area of each fixed card ROI.
            recognizeCardRanks(crop, visualResult);

        } catch (Exception e) {
            if (recognitionStarted) isRecognizing = false;
            if (recognitionLine != null) {
                String message = e.getMessage();
                String detail = message == null || message.trim().isEmpty()
                        ? e.getClass().getSimpleName() : message;
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (recognitionLine != null) {
                        recognitionLine.setText("خطای دریافت تصویر: " + detail);
                    }
                });
            }
        } finally {
            if (cardArea != null && !cardArea.isRecycled()) cardArea.recycle();
            if (scaledCardArea != null && !scaledCardArea.isRecycled()) scaledCardArea.recycle();
            if (crop != null && !crop.isRecycled()) crop.recycle();
            if (padded != null && !padded.isRecycled()) padded.recycle();
            if (image != null) image.close();
        }
    }

    private void recognizeCardRanks(Bitmap screen, CardVisionEngine.Result result) {
        if (screen == null || result == null || result.cards.isEmpty()) return;

        final String[] ranks = new String[result.cards.size()];
        final int[] completed = {0};
        final int total = result.cards.size();

        for (int i = 0; i < total; i++) {
            final int index = i;
            CardVisionEngine.DetectedCard card = result.cards.get(i);
            Bitmap indexArea = null;
            Bitmap enlarged = null;
            try {
                // Rank glyphs are printed in the upper-left corner of each card.
                int areaWidth = Math.max(1, Math.round(card.width * 0.46f));
                int areaHeight = Math.max(1, Math.round(card.height * 0.44f));
                indexArea = Bitmap.createBitmap(screen, card.left, card.top,
                        Math.min(areaWidth, screen.getWidth() - card.left),
                        Math.min(areaHeight, screen.getHeight() - card.top));
                enlarged = Bitmap.createScaledBitmap(indexArea,
                        indexArea.getWidth() * 5, indexArea.getHeight() * 5, true);
                if (indexArea != enlarged) indexArea.recycle();

                final Bitmap ocrBitmap = enlarged;
                textRecognizer.process(InputImage.fromBitmap(ocrBitmap, 0))
                        .addOnCompleteListener(task -> {
                            String rank = "?";
                            if (task.isSuccessful() && task.getResult() != null) {
                                rank = parseRank(task.getResult().getText());
                            }
                            ranks[index] = rank;
                            if (!ocrBitmap.isRecycled()) ocrBitmap.recycle();

                            completed[0]++;
                            if (completed[0] == total) {
                                updateRecognizedRanks(ranks);
                            }
                        });
            } catch (Exception ignored) {
                if (indexArea != null && !indexArea.isRecycled()) indexArea.recycle();
                if (enlarged != null && !enlarged.isRecycled()) enlarged.recycle();
                ranks[index] = "?";
                completed[0]++;
                if (completed[0] == total) updateRecognizedRanks(ranks);
            }
        }
    }

    private String parseRank(String raw) {
        if (raw == null) return "?";
        String value = raw.toUpperCase(java.util.Locale.US)
                .replace(" ", "").replace("\\n", "")
                .replace("|", "I").replace("O", "0");
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("10|[6-9]|[AJQK]").matcher(value);
        return matcher.find() ? matcher.group() : "?";
    }

    private void updateRecognizedRanks(String[] ranks) {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (playerCardsLine == null || splitCardsLine == null || dealerCardsLine == null) return;
            // Render each table group from the player's right toward the left.
            playerCardsLine.setText("بازیکن: [ " + ranks[8] + " ] [ " + ranks[7] + " ]");
            splitCardsLine.setText("اسپلیت: [ " + ranks[6] + " ] [ " + ranks[5]
                    + " ] [ " + ranks[4] + " ] [ " + ranks[3] + " ] [ " + ranks[2] + " ]");
            dealerCardsLine.setText("دیلر: [ " + ranks[1] + " ] [ " + ranks[0] + " ]");
            if (recognitionLine != null) {
                recognitionLine.setText("تشخیص رتبه انجام شد | خال: مرحله بعد");
            }
        });
    }

    private Notification notification() {

        if (Build.VERSION.SDK_INT >= 26) {

            return new Notification.Builder(this, CHANNEL)
                    .setContentTitle("6+ Poker Analyzer")
                    .setContentText("دریافت تصویر صفحه فعال است")
                    .setSmallIcon(android.R.drawable.ic_menu_view)
                    .setOngoing(true)
                    .build();
        }

        return new Notification.Builder(this)
                .setContentTitle("6+ Poker Analyzer")
                .setContentText("دریافت تصویر صفحه فعال است")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .build();
    }

    private void stopCapture() {

        if (display != null) {
            display.release();
            display = null;
        }

        if (reader != null) {
            reader.close();
            reader = null;
        }

        if (projection != null) {
            MediaProjection p = projection;
            projection = null;
            p.stop();
        }

        if (overlayView != null && windowManager != null) {

            try {
                windowManager.removeView(overlayView);
            } catch (Exception ignored) {
            }

            overlayView = null;
        }

        stopForeground(true);
    }

    @Override
    public void onDestroy() {
        stopCapture();
        if (textRecognizer != null) {
            textRecognizer.close();
            textRecognizer = null;
        }
        super.onDestroy();
    }

    @Override
    public android.os.IBinder onBind(Intent intent) {
        return null;
    }
}
