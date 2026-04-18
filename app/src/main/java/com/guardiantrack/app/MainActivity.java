package com.guardiantrack.app;

import android.Manifest;
import android.app.Notification;
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

    private WebView webView;
    private SharedPreferences prefs;
    public static final String CH_SOS = "sos_channel";
    public static final String CH_GPS = "gps_channel";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        prefs = getSharedPreferences("gt_prefs", MODE_PRIVATE);
        createNotificationChannels();
        requestPermissions();
        setupWebView();
    }

    private void createNotificationChannels() {
        NotificationChannel sos = new NotificationChannel(
            CH_SOS, "SOS Alerts", NotificationManager.IMPORTANCE_HIGH);
        sos.setDescription("Emergency SOS alerts from family members");
        sos.enableVibration(true);
        sos.setVibrationPattern(new long[]{0, 400, 200, 400, 200, 400});

        NotificationChannel gps = new NotificationChannel(
            CH_GPS, "GPS Tracking", NotificationManager.IMPORTANCE_LOW);
        gps.setDescription("Background location tracking service");

        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(sos);
        nm.createNotificationChannel(gps);
    }

    private void requestPermissions() {
        String[] perms;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms = new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.POST_NOTIFICATIONS
            };
        } else {
            perms = new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            };
        }
        ActivityCompat.requestPermissions(this, perms, 101);
    }

    private void setupWebView() {
        webView = findViewById(R.id.webView);
        WebSettings settings = webView.getSettings();

        // Enable all required features
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setGeolocationEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);

        // Inject Android bridge for JS to call native features
        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin,
                GeolocationPermissions.Callback callback) {
                // Auto-approve geolocation for our backend
                callback.invoke(origin, true, false);
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // Restore saved session into WebView localStorage
                String token = prefs.getString("gt_token", "");
                String user = prefs.getString("gt_user", "null");
                String people = prefs.getString("gt_people", "[]");
                if (!token.isEmpty()) {
                    String escaped_user = user.replace("'", "\'");
                    String escaped_people = people.replace("'", "\'");
                    webView.evaluateJavascript(
                        "localStorage.setItem('gt_token','" + token + "');" +
                        "localStorage.setItem('gt_user','" + escaped_user + "');" +
                        "localStorage.setItem('gt_people','" + escaped_people + "');",
                        null);
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url.startsWith("tel:")) {
                    startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse(url)));
                    return true;
                }
                // Block non-allowed URLs for security
                if (!url.contains("guardiantrack-backend.onrender.com") &&
                    !url.contains("openstreetmap.org") &&
                    !url.contains("open-meteo.com") &&
                    !url.contains("jsdelivr.net")) {
                    return true; // Block
                }
                return false;
            }
        });

        webView.loadUrl("https://guardiantrack-backend.onrender.com/dashboard.html");
    }

    // Bridge: JavaScript can call these methods from the WebView
    public class AndroidBridge {

        @JavascriptInterface
        public void saveToken(String token, String user, String people) {
            prefs.edit()
                .putString("gt_token", token)
                .putString("gt_user", user)
                .putString("gt_people", people)
                .apply();
        }

        @JavascriptInterface
        public void triggerSOS(String personName, String lat, String lng) {
            runOnUiThread(() -> {
                // SOS Morse code vibration: ... --- ...
                Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
                long[] pattern = {0,200,100,200,100,200,200,500,100,500,100,500,200,200,100,200,100,200};
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createWaveform(pattern, -1));
                }

                // Show SOS push notification
                Notification n = new Notification.Builder(MainActivity.this, CH_SOS)
                    .setSmallIcon(android.R.drawable.ic_dialog_alert)
                    .setContentTitle("SOS ALERT - " + personName)
                    .setContentText("Location: " + lat + ", " + lng)
                    .setStyle(new Notification.BigTextStyle()
                        .bigText("EMERGENCY! " + personName + " pressed SOS button!\nLocation: " + lat + ", " + lng))
                    .setAutoCancel(true)
                    .setPriority(Notification.PRIORITY_MAX)
                    .setCategory(Notification.CATEGORY_ALARM)
                    .build();

                getSystemService(NotificationManager.class).notify(999, n);
            });
        }

        @JavascriptInterface
        public void startTracking(String phone) {
            prefs.edit().putString("gt_active_phone", phone).apply();
            Intent intent = new Intent(MainActivity.this, LocationService.class);
            intent.putExtra("phone", phone);
            intent.putExtra("token", prefs.getString("gt_token", ""));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        }

        @JavascriptInterface
        public void stopTracking() {
            stopService(new Intent(MainActivity.this, LocationService.class));
        }

        @JavascriptInterface
        public String getToken() {
            return prefs.getString("gt_token", "");
        }

        @JavascriptInterface
        public void logout() {
            prefs.edit().clear().apply();
            stopService(new Intent(MainActivity.this, LocationService.class));
        }

        @JavascriptInterface
        public void vibrate(int duration) {
            Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE));
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Save localStorage to SharedPreferences when app goes background
        webView.evaluateJavascript(
            "(function(){" +
            "var t=localStorage.getItem('gt_token')||'';" +
            "var u=localStorage.getItem('gt_user')||'null';" +
            "var p=localStorage.getItem('gt_people')||'[]';" +
            "if(t) window.AndroidBridge.saveToken(t,u,p);" +
            "})()", null);
    }
}
