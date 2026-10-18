package id.unusa.cekbantal;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/** Menampilkan aplikasi Cek BANTAL (assets/index.html) dan menghubungkannya ke alarm Android. */
public class MainActivity extends Activity {

    private WebView web;
    private boolean loaded = false;
    private String pending = null;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setTextZoom(100);
        web.addJavascriptInterface(new Bridge(), "AndroidApp");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView v, String url) {
                loaded = true;
                deliver();
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if ("file".equals(u.getScheme())) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, u));
                } catch (Exception ignored) {
                }
                return true;
            }
        });
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        }
        handle(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handle(intent);
    }

    private void handle(Intent i) {
        if (i == null) return;
        String slot = i.getStringExtra("slot");
        if (slot == null) return;
        i.removeExtra("slot");
        pending = slot.replaceAll("[^a-z]", "");
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }
        getSystemService(NotificationManager.class).cancel(Scheduler.idx(pending) + 1);
        deliver();
    }

    private void deliver() {
        if (!loaded || pending == null) return;
        String id = pending;
        pending = null;
        web.evaluateJavascript("window.onNativeAlarm && window.onNativeAlarm('" + id + "')", null);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(false);
            setTurnScreenOn(false);
        }
    }

    @Override
    public void onBackPressed() {
        moveTaskToBack(true);
    }

    /** Dipanggil dari JavaScript sebagai window.AndroidApp */
    private class Bridge {
        @JavascriptInterface
        public void setSchedule(String json) {
            Scheduler.prefs(MainActivity.this).edit().putString("schedule", json).apply();
            Scheduler.scheduleAll(getApplicationContext());
        }

        @JavascriptInterface
        public void cancelNotifications() {
            getSystemService(NotificationManager.class).cancelAll();
        }
    }
}
