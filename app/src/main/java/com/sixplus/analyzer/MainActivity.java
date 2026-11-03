package com.sixplus.analyzer;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final int REQUEST_CAPTURE = 4102;
    private TextView status;
    private MediaProjectionManager projectionManager;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 48, 32, 32);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView title = new TextView(this); title.setText("6+ Poker Analyzer"); title.setTextSize(25); title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));
        TextView intro = new TextView(this); intro.setText("نسخه آزمایشی دریافت تصویر صفحه\nبرای شروع، اجازه ضبط صفحه را در پنجره اندروید تأیید کن."); intro.setTextSize(16); intro.setPadding(0, 24, 0, 20); root.addView(intro);
        Button start = new Button(this); start.setText("شروع دریافت تصویر صفحه"); root.addView(start, new LinearLayout.LayoutParams(-1, -2));
        Button stop = new Button(this); stop.setText("توقف دریافت تصویر"); root.addView(stop, new LinearLayout.LayoutParams(-1, -2));
        status = new TextView(this); status.setText("وضعیت: آماده"); status.setPadding(0, 20, 0, 0); root.addView(status);
        TextView note = new TextView(this); note.setText("این نسخه فقط زیرساخت دریافت تصویر را آزمایش می‌کند. تشخیص کارت‌ها و محاسبه احتمال هنوز اضافه نشده است."); note.setPadding(0, 24, 0, 0); root.addView(note);
        setContentView(root);
        start.setOnClickListener(v -> { status.setText("در انتظار اجازه اندروید..."); startActivityForResult(projectionManager.createScreenCaptureIntent(), REQUEST_CAPTURE); });
        stop.setOnClickListener(v -> { Intent i = new Intent(this, CaptureService.class); i.setAction(CaptureService.ACTION_STOP); startService(i); status.setText("درخواست توقف ارسال شد"); });
    }
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CAPTURE) {
            if (resultCode == RESULT_OK && data != null) {
                Intent service = new Intent(this, CaptureService.class); service.putExtra(CaptureService.EXTRA_RESULT_CODE, resultCode); service.putExtra(CaptureService.EXTRA_RESULT_DATA, data);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(service); else startService(service);
                status.setText("اجازه داده شد؛ دریافت تصویر فعال شد.");
            } else status.setText("اجازه ضبط صفحه داده نشد.");
        }
    }
}
