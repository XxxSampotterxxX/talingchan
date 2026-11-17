package com.kokruk.tv;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.webkit.WebViewAssetLoader;

/**
 * Battle of Kokruk สำหรับ Android TV / Google TV
 * - เกมอยู่ในแอป (assets/game) โหลดผ่าน https://appassets.androidplatform.net
 * - รีโมท: ลูกศร = เลื่อนลูกศรบนจอ, OK = คลิก (กดค้าง = ดูรายละเอียดการ์ด),
 *   กด Back ค้าง หรือปุ่ม Menu = โหมดเลื่อนรายการ (ลูกศรขึ้น/ลง/ซ้าย/ขวา = เลื่อน), Back = ปิดหน้าต่าง/ย้อนกลับ
 * - เมาส์ USB/บลูทูธ ใช้ได้ตามปกติ (ลูกศรของรีโมทจะซ่อนเมื่อขยับเมาส์)
 */
public class MainActivity extends Activity {
    private static final String START = "https://appassets.androidplatform.net/assets/game/index.html";
    private WebView web;
    private CursorView cursor;
    private float cx, cy;
    private boolean scrollMode = false;
    private boolean okDown = false;
    private long okDownTime = 0;
    private long lastBack = 0;
    private float dens = 1f;
    private final Handler h = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_FULLSCREEN);
        dens = getResources().getDisplayMetrics().density;

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(5, 11, 26));
        web = new WebView(this);
        root.addView(web, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        cursor = new CursorView(this);
        root.addView(cursor, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);
        hideSystemUi();

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setTextZoom(100);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();
        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                return loader.shouldInterceptRequest(r.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                String u = r.getUrl().toString();
                if (u.startsWith("https://appassets.androidplatform.net/")) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, r.getUrl())); } catch (Exception e) { /* ไม่มีเบราว์เซอร์ */ }
                return true;
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                v.evaluateJavascript(TV_JS, null);
            }
        });
        web.setWebChromeClient(new WebChromeClient());
        web.setFocusable(true);
        web.setFocusableInTouchMode(true);
        web.requestFocus();
        // เมาส์จริงขยับ -> ซ่อนลูกศรของรีโมท
        web.setOnGenericMotionListener((v, e) -> {
            if (e.isFromSource(InputDevice.SOURCE_MOUSE)) cursor.setVisibility(View.GONE);
            return false;
        });

        if (state != null) web.restoreState(state);
        else web.loadUrl(START);

        root.post(() -> {
            cx = root.getWidth() / 2f;
            cy = root.getHeight() / 2f;
            cursor.move(cx, cy);
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUi();
        web.onResume();
    }

    @Override
    protected void onPause() {
        web.onPause();
        super.onPause();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    // ---------------- รีโมท ----------------
    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        switch (k) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN: {
                if (cursor.getVisibility() != View.VISIBLE) { cursor.setVisibility(View.VISIBLE); return true; }
                if (!down) return true;
                int dx = k == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : k == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : 0;
                int dy = k == KeyEvent.KEYCODE_DPAD_UP ? -1 : k == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : 0;
                if (scrollMode) { swipe(dx, dy); return true; }
                int rc = e.getRepeatCount();
                float step = Math.min(10 + rc * 5, 70) * dens;
                cx = clamp(cx + dx * step, 0, web.getWidth() - 1);
                cy = clamp(cy + dy * step, 0, web.getHeight() - 1);
                cursor.move(cx, cy);
                if (okDown) touch(MotionEvent.ACTION_MOVE, okDownTime);
                return true;
            }
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_BUTTON_A: {
                if (cursor.getVisibility() != View.VISIBLE) { cursor.setVisibility(View.VISIBLE); return true; }
                if (scrollMode) { if (down) setScroll(false); return true; }
                if (down && e.getRepeatCount() == 0 && !okDown) {
                    okDown = true;
                    okDownTime = SystemClock.uptimeMillis();
                    cursor.press(true);
                    touch(MotionEvent.ACTION_DOWN, okDownTime);
                } else if (!down && okDown) {
                    okDown = false;
                    cursor.press(false);
                    touch(MotionEvent.ACTION_UP, okDownTime);
                }
                return true;
            }
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_PROG_GREEN:
            case KeyEvent.KEYCODE_BUTTON_Y:
                if (down && e.getRepeatCount() == 0) setScroll(!scrollMode);
                return true;
            case KeyEvent.KEYCODE_CHANNEL_UP:
            case KeyEvent.KEYCODE_PAGE_UP:
                if (down) swipe(0, -1);
                return true;
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
            case KeyEvent.KEYCODE_PAGE_DOWN:
                if (down) swipe(0, 1);
                return true;
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE:
            case KeyEvent.KEYCODE_BUTTON_B:
                if (down && e.getRepeatCount() == 1) { setScroll(!scrollMode); return true; } // กดค้าง = โหมดเลื่อน
                if (!down && !e.isCanceled() && (e.getFlags() & KeyEvent.FLAG_LONG_PRESS) == 0 && e.getEventTime() - e.getDownTime() < 500) onBack();
                return true;
        }
        return super.dispatchKeyEvent(e);
    }

    private void onBack() {
        if (scrollMode) { setScroll(false); return; }
        web.evaluateJavascript("(window.tvBack&&window.tvBack())?'1':'0'", v -> {
            if (v != null && v.contains("1")) return;
            long now = SystemClock.uptimeMillis();
            if (now - lastBack < 2000) finish();
            else { lastBack = now; Toast.makeText(this, "กด Back อีกครั้งเพื่อออกจากเกม", Toast.LENGTH_SHORT).show(); }
        });
    }

    private void setScroll(boolean on) {
        scrollMode = on;
        cursor.setScroll(on);
        Toast.makeText(this, on ? "โหมดเลื่อนรายการ: กดลูกศรเพื่อเลื่อน • กด OK หรือ Back เพื่อออก" : "กลับสู่โหมดลูกศร", Toast.LENGTH_SHORT).show();
    }

    private static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }

    private void touch(int action, long downTime) {
        MotionEvent ev = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, cx, cy, 0);
        ev.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        web.dispatchTouchEvent(ev);
        ev.recycle();
    }

    /** ลากนิ้วจำลองที่ตำแหน่งลูกศร เพื่อเลื่อนรายการ/บันทึกเกม/หน้าต่างที่อยู่ใต้ลูกศร */
    private void swipe(int dx, int dy) {
        final float dist = 160 * dens;
        final long t0 = SystemClock.uptimeMillis();
        final float sx = cx, sy = cy;
        final int steps = 6;
        MotionEvent d = MotionEvent.obtain(t0, t0, MotionEvent.ACTION_DOWN, sx, sy, 0);
        d.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        web.dispatchTouchEvent(d); d.recycle();
        for (int i = 1; i <= steps; i++) {
            final int n = i;
            h.postDelayed(() -> {
                float x = sx - dx * dist * n / steps, y = sy - dy * dist * n / steps;
                MotionEvent m = MotionEvent.obtain(t0, SystemClock.uptimeMillis(), n == steps ? MotionEvent.ACTION_UP : MotionEvent.ACTION_MOVE, x, y, 0);
                m.setSource(InputDevice.SOURCE_TOUCHSCREEN);
                web.dispatchTouchEvent(m); m.recycle();
            }, 16L * i);
        }
    }

    // JS ที่ใส่ให้หน้าเกม: ปุ่ม Back ของรีโมท = ปิดหน้าต่าง/ย้อนกลับ
    private static final String TV_JS =
            "(function(){if(window.__tv)return;window.__tv=1;document.documentElement.classList.add('tvApp');" +
            "const vis=e=>e&&e.offsetParent!==null;" +
            "window.tvBack=function(){" +
            " const pop=document.getElementById('cardPop');if(pop&&pop.classList.contains('on')){pop.classList.remove('on','sheet');return true}" +
            " const ds=[...document.querySelectorAll('dialog[open]')];" +
            " if(ds.length){const d=ds[ds.length-1];const b=[...d.querySelectorAll('button')].filter(vis).find(x=>/ยกเลิก|ปิด|ไม่|ข้าม|กลับ|✕|×/.test(x.textContent));if(b){b.click();return true}return true}" +
            " const bk=['#back1','#lgBack','#rlBack','#dbBack','#tutBack'].map(s=>document.querySelector(s)).find(vis);if(bk){bk.click();return true}" +
            " const bb=[...document.querySelectorAll('.screen.on button,.screen.on [role=button]')].find(x=>vis(x)&&/^\\s*‹/.test(x.textContent));if(bb){bb.click();return true}" +
            " return false};})();";

    // ---------------- ลูกศรบนจอ ----------------
    static class CursorView extends View {
        private float x, y;
        private boolean pressed = false, scroll = false;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), stroke = new Paint(Paint.ANTI_ALIAS_FLAG), glow = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path arrow = new Path();
        private final float d;

        CursorView(Activity a) {
            super(a);
            d = a.getResources().getDisplayMetrics().density;
            setFocusable(false);
            setClickable(false);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(2 * d);
            stroke.setColor(Color.rgb(4, 16, 46));
            glow.setColor(Color.argb(90, 53, 224, 255));
        }

        void move(float nx, float ny) { x = nx; y = ny; invalidate(); }
        void press(boolean p) { pressed = p; invalidate(); }
        void setScroll(boolean s) { scroll = s; invalidate(); }

        @Override
        public boolean onTouchEvent(MotionEvent e) { return false; }

        @Override
        protected void onDraw(Canvas c) {
            float s = 26 * d;
            c.drawCircle(x, y, (pressed ? 10 : 7) * d, glow);
            if (scroll) {
                fill.setColor(Color.rgb(255, 207, 74));
                c.drawCircle(x, y, 14 * d, fill);
                c.drawCircle(x, y, 14 * d, stroke);
                Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
                t.setColor(Color.rgb(42, 26, 0));
                t.setTextSize(18 * d);
                t.setTextAlign(Paint.Align.CENTER);
                c.drawText("↕", x, y + 6 * d, t);
                return;
            }
            arrow.reset();
            arrow.moveTo(x, y);
            arrow.lineTo(x, y + s);
            arrow.lineTo(x + s * 0.28f, y + s * 0.75f);
            arrow.lineTo(x + s * 0.48f, y + s * 1.12f);
            arrow.lineTo(x + s * 0.62f, y + s * 1.05f);
            arrow.lineTo(x + s * 0.43f, y + s * 0.68f);
            arrow.lineTo(x + s * 0.78f, y + s * 0.68f);
            arrow.close();
            fill.setColor(pressed ? Color.rgb(255, 207, 74) : Color.WHITE);
            c.drawPath(arrow, fill);
            c.drawPath(arrow, stroke);
        }
    }
}
