package com.kokruk.game;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;
import androidx.webkit.WebViewAssetLoader;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Battle of Kokruk สำหรับมือถือ Android
 * - ตัวเกมติดมากับแอป (assets/game) และโหลดผ่าน https://appassets.androidplatform.net/game/
 * - อัพเดตในแอป ไม่ต้องติดตั้งใหม่:
 *   1) อัพเดตเกม: ดาวน์โหลด game.zip ไปไว้ใน files/ota แล้วเปิดเกมใหม่ทันที (ไฟล์ที่ไม่มีใน ota ใช้ของในแอป)
 *   2) อัพเดตแอป: ดาวน์โหลด APK ใหม่แล้วเปิดตัวติดตั้งของ Android ให้ติดตั้งทับ (ข้อมูลไม่หาย)
 */
public class MainActivity extends Activity {
    private static final String HOST = "appassets.androidplatform.net";
    private static final String START = "https://" + HOST + "/game/index.html";
    private static final String OTA = "https://github.com/XxxSampotterxxX/talingchan/releases/download/mobile/ota.json";

    private WebView web;
    private long lastBack = 0;
    private boolean checked = false;
    private String pendingApkUrl = null, pendingApkName = null;
    private final Handler h = new Handler(Looper.getMainLooper());

    private File otaDir() { return new File(getFilesDir(), "ota"); }

    private int readInt(InputStream in) {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            return Integer.parseInt(r.readLine().trim());
        } catch (Exception e) { return 0; }
    }
    private int bundledVer() { try { return readInt(getAssets().open("game/ver.txt")); } catch (IOException e) { return 0; } }
    private int otaVer() { try { return readInt(new FileInputStream(new File(otaDir(), "ver.txt"))); } catch (IOException e) { return 0; } }
    private int gameVer() { return Math.max(bundledVer(), otaVer()); }
    @SuppressWarnings("deprecation")
    private int appVer() {
        try { PackageInfo p = getPackageManager().getPackageInfo(getPackageName(), 0); return p.versionCode; } catch (Exception e) { return 0; }
    }

    private static void rm(File f) {
        if (f == null || !f.exists()) return;
        File[] k = f.listFiles();
        if (k != null) for (File c : k) rm(c);
        f.delete();
    }

    private static String mime(String p) {
        String s = p.toLowerCase();
        if (s.endsWith(".html")) return "text/html";
        if (s.endsWith(".js")) return "application/javascript";
        if (s.endsWith(".css")) return "text/css";
        if (s.endsWith(".json")) return "application/json";
        if (s.endsWith(".webp")) return "image/webp";
        if (s.endsWith(".png")) return "image/png";
        if (s.endsWith(".jpg") || s.endsWith(".jpeg")) return "image/jpeg";
        if (s.endsWith(".svg")) return "image/svg+xml";
        if (s.endsWith(".woff2")) return "font/woff2";
        if (s.endsWith(".woff")) return "font/woff";
        if (s.endsWith(".ttf")) return "font/ttf";
        if (s.endsWith(".mp3")) return "audio/mpeg";
        return "application/octet-stream";
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }
        // ถ้าติดตั้งแอปเวอร์ชันใหม่ทับแล้ว ตัวเกมในแอปใหม่กว่า -> ทิ้งตัวเกมที่อัพเดตไว้เดิม
        if (otaVer() <= bundledVer()) rm(otaDir());

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(5, 11, 46));
        web = new WebView(this);
        root.addView(web, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);
        hideSystemUi();

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setTextZoom(100);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);

        final File ota = otaDir();
        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/game/", path -> {
                    try {
                        File f = new File(ota, path);
                        if (f.isFile() && f.getCanonicalPath().startsWith(ota.getCanonicalPath() + File.separator))
                            return new WebResourceResponse(mime(path), "utf-8", new FileInputStream(f));
                    } catch (IOException e) { /* ใช้ของในแอปแทน */ }
                    try { return new WebResourceResponse(mime(path), "utf-8", getAssets().open("game/" + path)); }
                    catch (IOException e) { return null; }
                })
                .build();
        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                return loader.shouldInterceptRequest(r.getUrl());
            }
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                String u = r.getUrl().toString();
                if (u.startsWith("https://" + HOST + "/")) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, r.getUrl())); } catch (Exception e) { /* ไม่มีเบราว์เซอร์ */ }
                return true;
            }
            @Override
            public void onPageFinished(WebView v, String url) {
                if (!checked) { checked = true; h.postDelayed(MainActivity.this::checkUpdate, 2500); }
            }
        });
        web.setWebChromeClient(new WebChromeClient());

        if (state != null) web.restoreState(state);
        else web.loadUrl(START);
    }

    // ---------------- อัพเดต ----------------
    private void checkUpdate() {
        new Thread(() -> {
            try {
                JSONObject j = new JSONObject(httpText(OTA));
                final int game = j.optInt("game", 0), apk = j.optInt("apk", 0);
                final String name = j.optString("name", ""), apkName = j.optString("apkName", "");
                final String notes = j.optString("notes", "");
                final String gameUrl = j.optString("gameUrl", ""), apkUrl = j.optString("apkUrl", "");
                final long gameSize = j.optLong("gameSize", 0), apkSize = j.optLong("apkSize", 0);
                runOnUiThread(() -> {
                    if (isFinishing()) return;
                    if (apk > appVer() && !apkUrl.isEmpty()) askApk(apkName, name, notes, apkUrl, apkSize);
                    else if (game > gameVer() && !gameUrl.isEmpty()) askGame(game, name, notes, gameUrl, gameSize);
                });
            } catch (Exception e) { /* ไม่มีเน็ต หรือยังไม่มีอัพเดต */ }
        }).start();
    }

    private static String mb(long b) { return b > 0 ? String.format(java.util.Locale.US, " (ประมาณ %.1f MB)", b / 1048576.0) : ""; }

    private void askGame(int ver, String name, String notes, String url, long size) {
        new AlertDialog.Builder(this)
                .setTitle("มีอัพเดตเกม " + name)
                .setMessage((notes.isEmpty() ? "" : notes + "\n\n") + "อัพเดตในแอปได้เลย ไม่ต้องติดตั้งใหม่" + mb(size))
                .setPositiveButton("อัพเดตเลย", (d, w) -> downloadGame(ver, name, url))
                .setNegativeButton("ภายหลัง", null)
                .show();
    }

    private void askApk(String apkName, String name, String notes, String url, long size) {
        new AlertDialog.Builder(this)
                .setTitle("มีแอปเวอร์ชันใหม่ " + apkName)
                .setMessage((notes.isEmpty() ? "" : notes + "\n\n") + "ดาวน์โหลดแล้วกด \"ติดตั้ง\" ทับของเดิมได้เลย ข้อมูลและความคืบหน้าไม่หาย" + mb(size))
                .setPositiveButton("อัพเดตเลย", (d, w) -> startApk(url, apkName))
                .setNegativeButton("ภายหลัง", null)
                .show();
    }

    private interface Prog { void set(long done, long total); }

    private AlertDialog progressDialog(String title, final ProgressBar[] bar, final TextView[] txt) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad, pad, pad / 2);
        bar[0] = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar[0].setMax(1000);
        txt[0] = new TextView(this);
        txt[0].setGravity(Gravity.END);
        box.addView(bar[0]);
        box.addView(txt[0]);
        return new AlertDialog.Builder(this).setTitle(title).setView(box).setCancelable(false).show();
    }

    private void downloadGame(int ver, String name, String url) {
        final ProgressBar[] bar = new ProgressBar[1];
        final TextView[] txt = new TextView[1];
        final AlertDialog dlg = progressDialog("กำลังอัพเดตเกม " + name, bar, txt);
        new Thread(() -> {
            File zip = new File(getCacheDir(), "game.zip");
            File tmp = new File(getFilesDir(), "ota_new");
            try {
                download(url, zip, (a, t) -> runOnUiThread(() -> showProg(bar[0], txt[0], a, t)));
                rm(tmp);
                unzip(zip, tmp);
                try (OutputStream o = new FileOutputStream(new File(tmp, "ver.txt"))) { o.write(String.valueOf(ver).getBytes(StandardCharsets.UTF_8)); }
                if (!new File(tmp, "index.html").isFile()) throw new IOException("bad zip");
                rm(otaDir());
                if (!tmp.renameTo(otaDir())) throw new IOException("rename");
                zip.delete();
                runOnUiThread(() -> {
                    dlg.dismiss();
                    Toast.makeText(this, "อัพเดตเกมเป็น " + name + " แล้ว", Toast.LENGTH_LONG).show();
                    web.clearCache(true);
                    web.loadUrl(START);
                });
            } catch (Exception e) {
                rm(tmp);
                runOnUiThread(() -> {
                    dlg.dismiss();
                    Toast.makeText(this, "อัพเดตไม่สำเร็จ ลองใหม่ภายหลัง", Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private boolean canInstall() {
        if (Build.VERSION.SDK_INT < 26) return true;
        return getPackageManager().canRequestPackageInstalls();
    }

    private void startApk(String url, String apkName) {
        if (!canInstall()) {
            pendingApkUrl = url;
            pendingApkName = apkName;
            new AlertDialog.Builder(this)
                    .setTitle("ขออนุญาตติดตั้งอัพเดต")
                    .setMessage("เปิดสวิตช์ \"อนุญาตจากแหล่งที่มานี้\" ให้ Battle of Kokruk แล้วกดย้อนกลับมาที่เกม การอัพเดตจะเริ่มต่อเอง (ทำครั้งเดียว)")
                    .setPositiveButton("ไปที่ตั้งค่า", (d, w) -> {
                        try { startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName()))); }
                        catch (Exception e) { pendingApkUrl = null; }
                    })
                    .setNegativeButton("ยกเลิก", (d, w) -> pendingApkUrl = null)
                    .show();
            return;
        }
        downloadApk(url, apkName);
    }

    private void downloadApk(String url, String apkName) {
        final ProgressBar[] bar = new ProgressBar[1];
        final TextView[] txt = new TextView[1];
        final AlertDialog dlg = progressDialog("กำลังดาวน์โหลดแอป " + apkName, bar, txt);
        new Thread(() -> {
            File dir = new File(getCacheDir(), "apk");
            dir.mkdirs();
            File f = new File(dir, "BOK-Mobile.apk");
            try {
                download(url, f, (a, t) -> runOnUiThread(() -> showProg(bar[0], txt[0], a, t)));
                runOnUiThread(() -> {
                    dlg.dismiss();
                    Uri u = FileProvider.getUriForFile(this, getPackageName() + ".files", f);
                    Intent i = new Intent(Intent.ACTION_VIEW);
                    i.setDataAndType(u, "application/vnd.android.package-archive");
                    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                    try { startActivity(i); }
                    catch (Exception e) { Toast.makeText(this, "เปิดตัวติดตั้งไม่ได้", Toast.LENGTH_LONG).show(); }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    dlg.dismiss();
                    Toast.makeText(this, "ดาวน์โหลดไม่สำเร็จ ลองใหม่ภายหลัง", Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private static void showProg(ProgressBar b, TextView t, long a, long total) {
        if (total > 0) { b.setIndeterminate(false); b.setProgress((int) (a * 1000 / total)); }
        else b.setIndeterminate(true);
        t.setText(String.format(java.util.Locale.US, "%.1f / %.1f MB", a / 1048576.0, Math.max(total, a) / 1048576.0));
    }

    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setInstanceFollowRedirects(true);
        c.setConnectTimeout(10000);
        c.setReadTimeout(20000);
        c.setRequestProperty("User-Agent", "BOK-Mobile");
        c.setRequestProperty("Cache-Control", "no-cache");
        return c;
    }

    private static String httpText(String url) throws IOException {
        HttpURLConnection c = open(url + (url.contains("?") ? "&" : "?") + "t=" + System.currentTimeMillis());
        if (c.getResponseCode() != 200) throw new IOException("http " + c.getResponseCode());
        try (InputStream in = c.getInputStream()) {
            java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
            return o.toString("UTF-8");
        } finally { c.disconnect(); }
    }

    private static void download(String url, File out, Prog p) throws IOException {
        HttpURLConnection c = open(url);
        if (c.getResponseCode() != 200) throw new IOException("http " + c.getResponseCode());
        long total = c.getContentLength(), done = 0, last = 0;
        try (InputStream in = c.getInputStream(); OutputStream o = new FileOutputStream(out)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) {
                o.write(buf, 0, n);
                done += n;
                if (done - last > 131072 || done == total) { last = done; p.set(done, total); }
            }
        } finally { c.disconnect(); }
        if (total > 0 && done != total) throw new IOException("short");
    }

    private static void unzip(File zip, File dir) throws IOException {
        dir.mkdirs();
        String root = dir.getCanonicalPath() + File.separator;
        try (ZipInputStream z = new ZipInputStream(new FileInputStream(zip))) {
            ZipEntry e;
            byte[] buf = new byte[65536];
            while ((e = z.getNextEntry()) != null) {
                File f = new File(dir, e.getName());
                if (!f.getCanonicalPath().startsWith(root)) throw new IOException("bad entry");
                if (e.isDirectory()) { f.mkdirs(); continue; }
                f.getParentFile().mkdirs();
                try (OutputStream o = new FileOutputStream(f)) {
                    int n;
                    while ((n = z.read(buf)) > 0) o.write(buf, 0, n);
                }
            }
        }
    }

    // ---------------- หน้าจอ / ปุ่ม ----------------
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
        if (pendingApkUrl != null && canInstall()) {
            String u = pendingApkUrl, n = pendingApkName;
            pendingApkUrl = null;
            downloadApk(u, n);
        }
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

    @SuppressWarnings("deprecation")
    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        long now = System.currentTimeMillis();
        if (now - lastBack < 2000) finish();
        else { lastBack = now; Toast.makeText(this, "กดย้อนกลับอีกครั้งเพื่อออก", Toast.LENGTH_SHORT).show(); }
    }
}
