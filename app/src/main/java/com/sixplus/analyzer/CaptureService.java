package com.sixplus.analyzer;

import android.app.*;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.*;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;

public class CaptureService extends Service {
    public static final String ACTION_STOP = "com.sixplus.analyzer.STOP_CAPTURE";
    public static final String EXTRA_RESULT_CODE = "result_code", EXTRA_RESULT_DATA = "result_data";
    private static final String CHANNEL = "screen_capture";
    private MediaProjection projection; private VirtualDisplay display; private ImageReader reader; private long lastSaved;
    @Override public void onCreate() { super.onCreate(); if (Build.VERSION.SDK_INT >= 26) { NotificationChannel c = new NotificationChannel(CHANNEL, "Screen capture", NotificationManager.IMPORTANCE_LOW); ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(c); } }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) { stopCapture(); stopSelf(); return START_NOT_STICKY; }
        startForeground(7, notification());
        if (intent == null || !intent.hasExtra(EXTRA_RESULT_DATA)) { stopSelf(); return START_NOT_STICKY; }
        int code = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
        Intent data = Build.VERSION.SDK_INT >= 33 ? intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent.class) : intent.getParcelableExtra(EXTRA_RESULT_DATA);
        projection = ((MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE)).getMediaProjection(code, data);
        if (projection == null) { stopSelf(); return START_NOT_STICKY; }
        DisplayMetrics m = new DisplayMetrics(); ((WindowManager)getSystemService(WINDOW_SERVICE)).getDefaultDisplay().getRealMetrics(m);
        reader = ImageReader.newInstance(m.widthPixels, m.heightPixels, PixelFormat.RGBA_8888, 2);
        reader.setOnImageAvailableListener(this::saveFrame, new Handler(Looper.getMainLooper()));
        projection.registerCallback(new MediaProjection.Callback() { @Override public void onStop() { stopCapture(); stopSelf(); } }, new Handler(Looper.getMainLooper()));
        display = projection.createVirtualDisplay("SixPlusCapture", m.widthPixels, m.heightPixels, m.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.getSurface(), null, null);
        return START_NOT_STICKY;
    }
    private void saveFrame(ImageReader r) {
        Image im = null;
        try {
            im = r.acquireLatestImage(); if (im == null) return;
            long now = System.currentTimeMillis(); if (now - lastSaved < 2000) return; lastSaved = now;
            Image.Plane p = im.getPlanes()[0]; ByteBuffer b = p.getBuffer(); int px = p.getPixelStride(), row = p.getRowStride();
            Bitmap padded = Bitmap.createBitmap(im.getWidth() + (row - px * im.getWidth()) / px, im.getHeight(), Bitmap.Config.ARGB_8888);
            b.rewind(); padded.copyPixelsFromBuffer(b); Bitmap crop = Bitmap.createBitmap(padded, 0, 0, im.getWidth(), im.getHeight());
            File f = new File(getExternalFilesDir(null), "latest-screen.png"); try (FileOutputStream out = new FileOutputStream(f)) { crop.compress(Bitmap.CompressFormat.PNG, 100, out); }
            crop.recycle(); padded.recycle();
        } catch (Exception ignored) { } finally { if (im != null) im.close(); }
    }
    private Notification notification() { if (Build.VERSION.SDK_INT >= 26) return new Notification.Builder(this, CHANNEL).setContentTitle("6+ Poker Analyzer").setContentText("دریافت تصویر صفحه فعال است").setSmallIcon(android.R.drawable.ic_menu_view).setOngoing(true).build(); return new Notification.Builder(this).setContentTitle("6+ Poker Analyzer").setContentText("دریافت تصویر صفحه فعال است").setSmallIcon(android.R.drawable.ic_menu_view).build(); }
    private void stopCapture() { if (display != null) { display.release(); display = null; } if (reader != null) { reader.close(); reader = null; } if (projection != null) { MediaProjection p = projection; projection = null; p.stop(); } stopForeground(true); }
    @Override public void onDestroy() { stopCapture(); super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }
}
