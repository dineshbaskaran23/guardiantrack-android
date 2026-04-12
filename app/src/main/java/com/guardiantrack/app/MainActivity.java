package com.guardiantrack.app;
import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

public class MainActivity extends AppCompatActivity {
    private WebView wv;
    private SharedPreferences prefs;
    public static final String CH_SOS = "sos_ch";
    public static final String CH_GPS = "gps_ch";

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_main);
        prefs = getSharedPreferences("gt_prefs", MODE_PRIVATE);
        createChannels();
        requestPerms();
        setupWebView();
    }

    private void createChannels() {
        NotificationChannel sos = new NotificationChannel(CH_SOS, "SOS Alerts", NotificationManager.IMPORTANCE_HIGH);
        sos.setVibrationPattern(new long[]{0,500,200,500,200,500});
        NotificationChannel gps = new NotificationChannel(CH_GPS, "GPS Tracking", NotificationManager.IMPORTANCE_LOW);
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(sos);
        nm.createNotificationChannel(gps);
    }

    private void requestPerms() {
        String[] p = {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION};
        ActivityCompat.requestPermissions(this, p, 101);
    }

    private void setupWebView() {
        wv = findViewById(R.id.webView);
        WebSettings ws = wv.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setGeolocationEnabled(true);
        ws.setAllowFileAccess(false);
        ws.setCacheMode(WebSettings.LOAD_DEFAULT);
        wv.addJavascriptInterface(new Bridge(), "AndroidBridge");
        wv.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback cb) {
                cb.invoke(origin, true, false);
            }
        });
        wv.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView v, String url) {
                String token = prefs.getString("gt_token","");
                String user = prefs.getString("gt_user","null");
                String people = prefs.getString("gt_people","[]");
                if (!token.isEmpty()) {
                    wv.evaluateJavascript(
                        "localStorage.setItem('gt_token','"+token+"');" +
                        "localStorage.setItem('gt_user','"+user+"');" +
                        "localStorage.setItem('gt_people','"+people+"');", null);
                }
            }
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, String url) {
                if (url.startsWith("tel:")) { startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse(url))); return true; }
                return false;
            }
        });
        wv.loadUrl("https://guardiantrack-backend.onrender.com/dashboard.html");
    }

    public class Bridge {
        @JavascriptInterface
        public void saveToken(String t, String u, String p) {
            prefs.edit().putString("gt_token",t).putString("gt_user",u).putString("gt_people",p).apply();
        }
        @JavascriptInterface
        public void triggerSOS(String name, String lat, String lng) {
            runOnUiThread(() -> {
                Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
                long[] pat = {0,200,100,200,100,200,100,600,100,600,100,600,100,200,100,200,100,200};
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    v.vibrate(VibrationEffect.createWaveform(pat,-1));
                android.app.Notification n = new android.app.Notification.Builder(MainActivity.this, CH_SOS)
                    .setSmallIcon(android.R.drawable.ic_dialog_alert)
                    .setContentTitle("SOS ALERT - " + name)
                    .setContentText("Location: " + lat + ", " + lng)
                    .setAutoCancel(true).build();
                getSystemService(NotificationManager.class).notify(999, n);
            });
        }
        @JavascriptInterface
        public void startTracking(String phone) {
            Intent i = new Intent(MainActivity.this, LocationService.class);
            i.putExtra("phone", phone);
            i.putExtra("token", prefs.getString("gt_token",""));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i);
            else startService(i);
        }
        @JavascriptInterface
        public void stopTracking() { stopService(new Intent(MainActivity.this, LocationService.class)); }
        @JavascriptInterface
        public void logout() { prefs.edit().clear().apply(); stopService(new Intent(MainActivity.this, LocationService.class)); }
        @JavascriptInterface
        public void vibrate(int ms) {
            Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
        }
    }

    @Override
    public void onBackPressed() { if (wv.canGoBack()) wv.goBack(); else super.onBackPressed(); }
}
