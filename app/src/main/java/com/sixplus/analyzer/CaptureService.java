package com.sixplus.analyzer;

import android.app.*;
import android.content.Intent;
import android.graphics.*;
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
    public static final String ACTION_STOP = "com.sixplus.analyzer.STOP_CAPTURE";
    public static final String EXTRA_RESULT_CODE = "result_code";
    public static final String EXTRA_RESULT_DATA = "result_data";
    private static final String CHANNEL = "screen_capture";
    private MediaProjection projection;
    private VirtualDisplay display;
    private ImageReader reader;
    private long lastSaved;
    private TextView recognitionLine;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private WindowManager windowManager;
    private View overlayView;
    private LinearLayout overlayBody;
    private boolean collapsed = false;
    private boolean isRecognizing = false;

    @Override public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL, "Screen capture", NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) { stopCapture(); stopSelf(); return START_NOT_STICKY; }
        startForeground(7, notification());
        if (intent == null || !intent.hasExtra(EXTRA_RESULT_DATA)) { stopSelf(); return START_NOT_STICKY; }
        if (Settings.canDrawOverlays(this)) showOverlay();
        int code = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
        Intent data;
        if (Build.VERSION.SDK_INT >= 33) data = intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent.class);
        else data = intent.getParcelableExtra(EXTRA_RESULT_DATA);
        if (data == null) { stopCapture(); stopSelf(); return START_NOT_STICKY; }
        projection = ((MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE)).getMediaProjection(code, data);
        if (projection == null) { stopCapture(); stopSelf(); return START_NOT_STICKY; }
        DisplayMetrics metrics = new DisplayMetrics();
        ((WindowManager)getSystemService(WINDOW_SERVICE)).getDefaultDisplay().getRealMetrics(metrics);
        reader = ImageReader.newInstance(metrics.widthPixels, metrics.heightPixels, PixelFormat.RGBA_8888, 2);
        reader.setOnImageAvailableListener(this::saveFrame, new Handler(Looper.getMainLooper()));
        projection.registerCallback(new MediaProjection.Callback() {
            @Override public void onStop() { stopCapture(); stopSelf(); }
        }, new Handler(Looper.getMainLooper()));
        display = projection.createVirtualDisplay("SixPlusCapture", metrics.widthPixels, metrics.heightPixels,
                metrics.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.getSurface(), null, null);
        return START_NOT_STICKY;
    }

    private void showOverlay() {
        if (overlayView != null || !Settings.canDrawOverlays(this)) return;
        windowManager = (WindowManager)getSystemService(WINDOW_SERVICE);
        LinearLayout panel = new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL); panel.setPadding(12,10,12,10);
        GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.argb(238,22,28,38)); bg.setCornerRadius(22); bg.setStroke(2,Color.argb(220,110,150,190)); panel.setBackground(bg);
        LinearLayout header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this); title.setText("6+ Analyzer  •  زنده"); title.setTextColor(Color.WHITE); title.setTextSize(13);
        header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        Button fold = new Button(this); fold.setText("−"); fold.setMinWidth(42); fold.setMinimumWidth(42); fold.setPadding(0,0,0,0);
        header.addView(fold,new LinearLayout.LayoutParams(48,42)); panel.addView(header);
        overlayBody = new LinearLayout(this); overlayBody.setOrientation(LinearLayout.VERTICAL); overlayBody.setPadding(4,8,4,2);
        addOverlayLine("وضعیت: دریافت تصویر فعال");
        recognitionLine = addOverlayLine("تشخیص تصویری کارت‌ها: در انتظار تصویر");
        addOverlayLine("Player / Dealer / Split: —");
        addOverlayLine("پیشنهاد شرط: هنوز محاسبه نمی‌شود");
        panel.addView(overlayBody);
        fold.setOnClickListener(v -> { collapsed=!collapsed; overlayBody.setVisibility(collapsed?View.GONE:View.VISIBLE); fold.setText(collapsed?"+":"−"); });
        header.setOnTouchListener(new View.OnTouchListener() {
            int sx,sy; float tx,ty;
            public boolean onTouch(View v, MotionEvent e) {
                WindowManager.LayoutParams p=(WindowManager.LayoutParams)panel.getLayoutParams();
                if(e.getAction()==MotionEvent.ACTION_DOWN){sx=p.x;sy=p.y;tx=e.getRawX();ty=e.getRawY();return true;}
                if(e.getAction()==MotionEvent.ACTION_MOVE){p.x=sx+(int)(e.getRawX()-tx);p.y=sy+(int)(e.getRawY()-ty);windowManager.updateViewLayout(panel,p);return true;}
                return e.getAction()==MotionEvent.ACTION_UP;
            }
        });
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT>=26?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY:WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);
        p.gravity=Gravity.TOP|Gravity.START;p.x=18;p.y=180;overlayView=panel;
        try{windowManager.addView(overlayView,p);}catch(Exception e){overlayView=null;}
    }

    private TextView addOverlayLine(String value) {
        TextView line=new TextView(this);line.setText(value);line.setTextColor(Color.WHITE);line.setTextSize(12);line.setPadding(2,5,2,5);overlayBody.addView(line);return line;
    }

    private void saveFrame(ImageReader ir) {
        Image image=null;
        try {
            image=ir.acquireLatestImage(); if(image==null)return;
            long now=System.currentTimeMillis(); if(now-lastSaved<1500)return; lastSaved=now;
            Image.Plane plane=image.getPlanes()[0];ByteBuffer buffer=plane.getBuffer();
            int pixelStride=plane.getPixelStride(),rowStride=plane.getRowStride();
            Bitmap padded=Bitmap.createBitmap(image.getWidth()+(rowStride-pixelStride*image.getWidth())/pixelStride,image.getHeight(),Bitmap.Config.ARGB_8888);
            buffer.rewind();padded.copyPixelsFromBuffer(buffer);
            Bitmap frame=Bitmap.createBitmap(padded,0,0,image.getWidth(),image.getHeight());padded.recycle();
            File file=new File(getExternalFilesDir(null),"latest-screen.png");
            try(FileOutputStream out=new FileOutputStream(file)){frame.compress(Bitmap.CompressFormat.PNG,100,out);}
            if(!isRecognizing) recognizeCardSlots(frame); else frame.recycle();
        } catch(Exception ignored) {
        } finally { if(image!=null)image.close(); }
    }

    // Coordinates calibrated from the supplied 691x1536 game screenshot.
    // Each card is sampled separately; rank and suit are matched as image templates, not OCR.
    private void recognizeCardSlots(Bitmap screen) {
        isRecognizing=true;
        final int sw=screen.getWidth(), sh=screen.getHeight();
        final float[][] boxes={
            {0.790f,0.223f,0.047f,0.031f},{0.842f,0.223f,0.047f,0.031f},
            {0.712f,0.256f,0.047f,0.031f},{0.764f,0.256f,0.047f,0.031f},
            {0.816f,0.256f,0.047f,0.031f},{0.868f,0.256f,0.047f,0.031f},{0.920f,0.256f,0.047f,0.031f},
            {0.790f,0.290f,0.047f,0.031f},{0.842f,0.290f,0.047f,0.031f}
        };
        String[] out=new String[9];
        for(int i=0;i<9;i++) {
            int x=Math.max(0,Math.round(boxes[i][0]*sw)), y=Math.max(0,Math.round(boxes[i][1]*sh));
            int w=Math.min(sw-x,Math.round(boxes[i][2]*sw)), h=Math.min(sh-y,Math.round(boxes[i][3]*sh));
            if(w<8||h<8){out[i]="?";continue;}
            Bitmap card=Bitmap.createBitmap(screen,x,y,w,h);
            out[i]=matchCard(card);card.recycle();
        }
        screen.recycle();
        String dealer="Dealer: "+out[0]+"  "+out[1];
        StringBuilder board=new StringBuilder("Board: ");
        for(int i=2;i<=6;i++){if(i>2)board.append("  ");board.append(out[i]);}
        String player="Player: "+out[7]+"  "+out[8];
        mainHandler.post(()->{if(recognitionLine!=null)recognitionLine.setText(dealer+"\n"+board+"\n"+player);isRecognizing=false;});
    }

    private String matchCard(Bitmap card) {
        // Digital card tiles: rank at upper-left, suit immediately below.
        int cw=card.getWidth(), ch=card.getHeight();
        Bitmap rank=Bitmap.createBitmap(card,0,0,Math.max(1,cw*3/5),Math.max(1,ch*3/5));
        Bitmap suit=Bitmap.createBitmap(card,0,Math.max(0,ch*2/5),Math.max(1,cw*3/5),Math.max(1,ch*3/5));
        String r=templateGlyph(rank,"6789TJQKA");
        int color=dominantCardColor(suit);
        String s=templateSuit(suit,color);
        rank.recycle();suit.recycle();
        return r+s;
    }

    private String templateGlyph(Bitmap sample,String candidates) {
        String best="?";double bestScore=Double.MAX_VALUE;
        for(int n=0;n<candidates.length();n++) {
            Bitmap t=renderGlyph(String.valueOf(candidates.charAt(n)),24,32);
            double score=bitmapDistance(sample,t);t.recycle();
            if(score<bestScore){bestScore=score;best=String.valueOf(candidates.charAt(n));}
        }
        return bestScore<0.62?best:"?";
    }

    private String templateSuit(Bitmap sample,int color) {
        String[] suits={"♣","♦","♥","♠"};String best="?";double bestScore=Double.MAX_VALUE;
        for(String suit:suits) {
            Bitmap t=renderGlyph(suit,24,24);
            double score=bitmapDistance(sample,t);t.recycle();
            if(score<bestScore){bestScore=score;best=suit;}
        }
        if(bestScore>0.68) return color==Color.RED?"♥/♦":"♣/♠";
        if(color==Color.RED && (best.equals("♣")||best.equals("♠"))) return "♥/♦";
        if(color!=Color.RED && (best.equals("♥")||best.equals("♦"))) return "♣/♠";
        return best;
    }

    private Bitmap renderGlyph(String glyph,int w,int h) {
        Bitmap b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);
        c.drawColor(Color.BLACK);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(Color.WHITE);p.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));
        p.setTextSize(h*0.78f);p.setTextAlign(Paint.Align.CENTER);
        c.drawText(glyph,w/2f,h*0.78f,p);return b;
    }

    private double bitmapDistance(Bitmap a,Bitmap b) {
        double diff=0;int count=0;
        for(int y=0;y<16;y++)for(int x=0;x<12;x++) {
            int ax=Math.min(a.getWidth()-1,x*a.getWidth()/12), ay=Math.min(a.getHeight()-1,y*a.getHeight()/16);
            int bx=Math.min(b.getWidth()-1,x*b.getWidth()/12), by=Math.min(b.getHeight()-1,y*b.getHeight()/16);
            // Source tiles are light with dark/red ink; templates are dark with white ink.
            int ac=a.getPixel(ax,ay);
            int ar=Color.red(ac), ag=Color.green(ac), ab=Color.blue(ac);
            boolean ink=(ar>130 && ar>ag*1.22f && ar>ab*1.12f) || ((ar+ag+ab)/3<165);
            int av=ink?1:0;
            int bc=b.getPixel(bx,by);
            int bv=((Color.red(bc)+Color.green(bc)+Color.blue(bc))/3)>100?1:0;
            diff+=Math.abs(av-bv);count++;
        }
        return diff/count;
    }

    private int dominantCardColor(Bitmap b) {
        int red=0,dark=0;
        for(int y=0;y<b.getHeight();y++)for(int x=0;x<b.getWidth();x++){
            int c=b.getPixel(x,y);if(Color.red(c)>Color.green(c)*1.25f && Color.red(c)>Color.blue(c)*1.15f)red++;
            else if(Color.red(c)<150&&Color.green(c)<150&&Color.blue(c)<150)dark++;
        }
        return red>dark?Color.RED:Color.BLACK;
    }

    private Notification notification() {
        if(Build.VERSION.SDK_INT>=26)return new Notification.Builder(this,CHANNEL).setContentTitle("6+ Poker Analyzer").setContentText("دریافت تصویر صفحه فعال است").setSmallIcon(android.R.drawable.ic_menu_view).setOngoing(true).build();
        return new Notification.Builder(this).setContentTitle("6+ Poker Analyzer").setContentText("دریافت تصویر صفحه فعال است").setSmallIcon(android.R.drawable.ic_menu_view).build();
    }
    private void stopCapture() {
        if(display!=null){display.release();display=null;}if(reader!=null){reader.close();reader=null;}
        if(projection!=null){MediaProjection p=projection;projection=null;p.stop();}
        if(overlayView!=null&&windowManager!=null){try{windowManager.removeView(overlayView);}catch(Exception ignored){}overlayView=null;}
        stopForeground(true);
    }
    @Override public void onDestroy(){stopCapture();super.onDestroy();}
    @Override public android.os.IBinder onBind(Intent intent){return null;}
}