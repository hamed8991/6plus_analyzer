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
private TextView recognitionLine;
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
        addOverlayLine("تشخیص کارت‌ها: در حال آماده‌سازی");
        addOverlayLine("Player / Dealer / Split: —");
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

    private void addOverlayLine(String value) {

        TextView line = new TextView(this);
        line.setText(value);
        line.setTextColor(Color.WHITE);
        line.setTextSize(12);
        line.setPadding(2, 5, 2, 5);

        overlayBody.addView(line);
    }

    private void saveFrame(ImageReader imageReader) {

        Image image = null;

        try {

            image = imageReader.acquireLatestImage();

            if (image == null) {
                return;
            }

            long now = System.currentTimeMillis();

            if (now - lastSaved < 2000) {
                return;
            }

            lastSaved = now;

            Image.Plane plane = image.getPlanes()[0];

            ByteBuffer buffer = plane.getBuffer();

            int pixelStride = plane.getPixelStride();
            int rowStride = plane.getRowStride();

            Bitmap padded = Bitmap.createBitmap(
                    image.getWidth() +
                            (rowStride - pixelStride * image.getWidth())
                                    / pixelStride,
                    image.getHeight(),
                    Bitmap.Config.ARGB_8888
            );

            buffer.rewind();
            padded.copyPixelsFromBuffer(buffer);

            Bitmap crop = Bitmap.createBitmap(
                    padded,
                    0,
                    0,
                    image.getWidth(),
                    image.getHeight()
            );

            File file = new File(
                    getExternalFilesDir(null),
                    "latest-screen.png"
            );

            try (FileOutputStream output =
                         new FileOutputStream(file)) {

                crop.compress(
                        Bitmap.CompressFormat.PNG,
                        100,
                        output
                );
            }

            crop.recycle();
            padded.recycle();

        } catch (Exception ignored) {

        } finally {

            if (image != null) {
                image.close();
            }
        }
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
        super.onDestroy();
    }

    @Override
    public android.os.IBinder onBind(Intent intent) {
        return null;
    }
}
