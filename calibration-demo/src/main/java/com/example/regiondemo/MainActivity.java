package com.example.regiondemo;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final String PREFS = "region_demo";
    private static final String COUNT = "region_count";

    private GenericRegionCalibrationView calibrationView;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(14, 14, 14, 14);
        root.setBackgroundColor(Color.rgb(245, 246, 248));

        TextView heading = new TextView(this);
        heading.setText("آزمایش کالیبراسیون عمومی");
        heading.setTextSize(20);
        heading.setTextColor(Color.rgb(25, 35, 48));
        heading.setGravity(Gravity.CENTER);
        root.addView(heading);

        TextView hint = new TextView(this);
        hint.setText("روی تصویر نمونه مستطیل بکش؛ مختصات به‌صورت نسبی ذخیره می‌شوند.");
        hint.setTextSize(14);
        hint.setPadding(4, 10, 4, 10);
        root.addView(hint);

        calibrationView = new GenericRegionCalibrationView(this, createSampleDocument());
        calibrationView.setMaxRegions(12);
        root.addView(calibrationView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        status = new TextView(this);
        status.setTextSize(14);
        status.setPadding(4, 8, 4, 8);
        root.addView(status);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setGravity(Gravity.CENTER);
        Button undo = new Button(this);
        undo.setText("برگشت");
        Button clear = new Button(this);
        clear.setText("پاک‌کردن");
        Button save = new Button(this);
        save.setText("ذخیره");
        buttons.addView(undo, new LinearLayout.LayoutParams(0, -2, 1));
        buttons.addView(clear, new LinearLayout.LayoutParams(0, -2, 1));
        buttons.addView(save, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(buttons);

        setContentView(root);

        calibrationView.setOnRegionsChangedListener(count ->
                status.setText("تعداد ناحیه‌ها: " + count + " از ۱۲"));

        undo.setOnClickListener(v -> calibrationView.undoLastRegion());
        clear.setOnClickListener(v -> calibrationView.clearRegions());
        save.setOnClickListener(v -> saveRegions());

        loadRegions();
        status.setText("تعداد ناحیه‌ها: " + calibrationView.getRegionCount() + " از ۱۲");
    }

    private void saveRegions() {
        List<GenericRegionCalibrationView.Region> regions =
                calibrationView.getNormalizedRegions();
        android.content.SharedPreferences.Editor editor =
                getSharedPreferences(PREFS, MODE_PRIVATE).edit();
        editor.putInt(COUNT, regions.size());
        for (int i = 0; i < regions.size(); i++) {
            GenericRegionCalibrationView.Region r = regions.get(i);
            String key = "region_" + i + "_";
            editor.putFloat(key + "left", r.left);
            editor.putFloat(key + "top", r.top);
            editor.putFloat(key + "right", r.right);
            editor.putFloat(key + "bottom", r.bottom);
        }
        editor.apply();
        Toast.makeText(this, "مختصات نسبی ذخیره شد.", Toast.LENGTH_SHORT).show();
    }

    private void loadRegions() {
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        int count = Math.min(12, Math.max(0, prefs.getInt(COUNT, 0)));
        List<GenericRegionCalibrationView.Region> regions = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String key = "region_" + i + "_";
            regions.add(new GenericRegionCalibrationView.Region(
                    prefs.getFloat(key + "left", 0f),
                    prefs.getFloat(key + "top", 0f),
                    prefs.getFloat(key + "right", 0f),
                    prefs.getFloat(key + "bottom", 0f)));
        }
        calibrationView.setNormalizedRegions(regions);
    }

    // A generic document-like sample image, not a game or playing-card layout.
    private Bitmap createSampleDocument() {
        Bitmap bitmap = Bitmap.createBitmap(900, 1400, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.rgb(232, 235, 239));

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.WHITE);
        canvas.drawRoundRect(55, 45, 845, 1355, 24, 24, p);

        p.setColor(Color.rgb(38, 58, 78));
        canvas.drawRect(95, 100, 805, 205, p);

        p.setColor(Color.WHITE);
        p.setTextSize(38);
        canvas.drawText("SAMPLE DOCUMENT", 125, 165, p);

        p.setColor(Color.rgb(75, 88, 102));
        p.setTextSize(24);
        canvas.drawText("Section A  |  Example text and fields", 105, 275, p);

        p.setColor(Color.rgb(215, 221, 228));
        for (int i = 0; i < 5; i++) {
            float y = 325 + i * 58;
            canvas.drawRoundRect(105, y, 790, y + 22, 8, 8, p);
        }

        p.setColor(Color.rgb(225, 239, 232));
        canvas.drawRoundRect(105, 660, 790, 850, 18, 18, p);
        p.setColor(Color.rgb(50, 105, 75));
        p.setTextSize(28);
        canvas.drawText("Highlighted information area", 135, 720, p);

        p.setColor(Color.rgb(232, 235, 242));
        canvas.drawRoundRect(105, 920, 430, 1240, 16, 16, p);
        canvas.drawRoundRect(465, 920, 790, 1240, 16, 16, p);

        p.setColor(Color.rgb(95, 108, 122));
        p.setTextSize(22);
        canvas.drawText("Panel 1", 135, 980, p);
        canvas.drawText("Panel 2", 495, 980, p);
        return bitmap;
    }
}
