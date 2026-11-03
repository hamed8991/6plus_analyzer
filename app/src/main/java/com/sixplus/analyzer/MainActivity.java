package com.sixplus.analyzer;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {

    private static final int REQUEST_CAPTURE = 4102;
    private static final int REQUEST_OVERLAY = 4103;

    private TextView status;
    private MediaProjectionManager projectionManager;
    private boolean waitingForOverlayPermission = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        projectionManager = (MediaProjectionManager)
                getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 48, 32, 32);
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = new TextView(this);
        title.setText("6+ Poker Analyzer");
        title.setTextSize(25);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView intro = new TextView(this);
        intro.setText("برای نمایش پنجره شناور روی بازی، ابتدا مجوز نمایش روی برنامه‌های دیگر و سپس مجوز ضبط صفحه را تأیید کن.");
        intro.setTextSize(16);
        intro.setPadding(0, 24, 0, 20);
        root.addView(intro);

        Button start = new Button(this);
        start.setText("فعال‌کردن پنجره شناور و دریافت تصویر");
        root.addView(start);

        Button stop = new Button(this);
        stop.setText("توقف");
        root.addView(stop);

        status = new TextView(this);
        status.setText("وضعیت: آماده");
        status.setPadding(0, 20, 0, 0);
        root.addView(status);

        TextView note = new TextView(this);
        note.setText("این مرحله پنجره شناور و دریافت تصویر را راه‌اندازی می‌کند. تشخیص کارت‌ها در مرحله بعد اضافه می‌شود.");
        note.setPadding(0, 24, 0, 0);
        root.addView(note);

        setContentView(root);

        start.setOnClickListener(v -> beginSetup());

        stop.setOnClickListener(v -> {
            Intent i = new Intent(this, CaptureService.class);
            i.setAction(CaptureService.ACTION_STOP);
            startService(i);
            status.setText("درخواست توقف ارسال شد.");
        });
    }

    private void beginSetup() {
        if (!Settings.canDrawOverlays(this)) {
            waitingForOverlayPermission = true;
            status.setText("لطفاً مجوز نمایش روی برنامه‌های دیگر را فعال کن.");

            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())
            );

            startActivityForResult(intent, REQUEST_OVERLAY);
            return;
        }

        requestScreenCapture();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_OVERLAY) {
            waitingForOverlayPermission = false;

            if (Settings.canDrawOverlays(this)) {
                requestScreenCapture();
            } else {
                status.setText("مجوز پنجره شناور فعال نشد.");
            }
            return;
        }

        if (requestCode == REQUEST_CAPTURE) {
            if (resultCode == RESULT_OK && data != null) {

                Intent service = new Intent(this, CaptureService.class);

                service.putExtra(CaptureService.EXTRA_RESULT_CODE, resultCode);
                service.putExtra(CaptureService.EXTRA_RESULT_DATA, data);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(service);
                } else {
                    startService(service);
                }

                status.setText("دریافت تصویر و پنجره شناور فعال شد.");

            } else {
                status.setText("اجازه ضبط صفحه داده نشد.");
            }
        }
    }

    private void requestScreenCapture() {
        status.setText("در انتظار اجازه ضبط صفحه...");

        startActivityForResult(
                projectionManager.createScreenCaptureIntent(),
                REQUEST_CAPTURE
        );
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (waitingForOverlayPermission && Settings.canDrawOverlays(this)) {
            waitingForOverlayPermission = false;
            requestScreenCapture();
        }
    }
}
