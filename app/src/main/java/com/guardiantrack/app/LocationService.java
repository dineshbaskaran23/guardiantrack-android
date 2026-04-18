package com.guardiantrack.app;

import android.Manifest;
import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import androidx.core.app.ActivityCompat;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import java.util.concurrent.TimeUnit;

public class LocationService extends Service {

    private static final String TAG = "GuardianTrack";
    private static final String BACKEND = "https://guardiantrack-backend.onrender.com";
    private static final long INTERVAL_MS = 30000; // 30 seconds
    private static final float MIN_DISTANCE_M = 10;

    private LocationManager locationManager;
    private OkHttpClient httpClient;
    private Handler handler;
    private String phone = "";
    private String token = "";
    private Location lastLocation = null;

    private LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            lastLocation = location;
            sendLocation(location);
        }
        @Override public void onStatusChanged(String p, int s, Bundle e) {}
        @Override public void onProviderEnabled(String p) {}
        @Override public void onProviderDisabled(String p) {}
    };

    @Override
    public void onCreate() {
        super.onCreate();
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        httpClient = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();
        handler = new Handler(Looper.getMainLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            phone = intent.getStringExtra("phone") != null ? intent.getStringExtra("phone") : "";
            token = intent.getStringExtra("token") != null ? intent.getStringExtra("token") : "";
        }

        // Start foreground notification so Android doesn't kill the service
        Notification notification = new Notification.Builder(this, MainActivity.CH_GPS)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("GuardianTrack Active")
            .setContentText("Sending location every 30 seconds")
            .setOngoing(true)
            .build();
        startForeground(1, notification);

        startLocationUpdates();
        return START_STICKY; // Restart if killed by system
    }

    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "No location permission");
            return;
        }

        // Use GPS provider for best accuracy
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, INTERVAL_MS, MIN_DISTANCE_M, locationListener);
        }

        // Also use network for faster first fix
        if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER, INTERVAL_MS, MIN_DISTANCE_M, locationListener);
        }

        Log.d(TAG, "Location updates started for phone: " + phone);
    }

    private void sendLocation(Location loc) {
        if (token.isEmpty() || phone.isEmpty()) return;

        float battery = getBatteryLevel();

        new Thread(() -> {
            try {
                String body = String.format(
                    "{"lat":%f,"lng":%f,"speed":%f,"accuracy":%f,"battery":%.0f,"phone":"%s"}",
                    loc.getLatitude(), loc.getLongitude(),
                    loc.getSpeed() * 3.6f, // convert m/s to km/h
                    loc.getAccuracy(),
                    battery,
                    phone);

                Request request = new Request.Builder()
                    .url(BACKEND + "/api/location/update")
                    .addHeader("Authorization", "Bearer " + token)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(body, MediaType.parse("application/json")))
                    .build();

                okhttp3.Response response = httpClient.newCall(request).execute();
                Log.d(TAG, "Location sent: " + loc.getLatitude() + "," + loc.getLongitude() +
                    " Status: " + response.code());
                response.close();

                // Alert if battery is low
                if (battery < 20) {
                    sendBatteryAlert(battery);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to send location: " + e.getMessage());
            }
        }).start();
    }

    private void sendBatteryAlert(float battery) {
        new Thread(() -> {
            try {
                String body = String.format(
                    "{"type":"battery","message":"Battery low: %.0f%%","phone":"%s"}",
                    battery, phone);
                Request request = new Request.Builder()
                    .url(BACKEND + "/api/alerts")
                    .addHeader("Authorization", "Bearer " + token)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(body, MediaType.parse("application/json")))
                    .build();
                httpClient.newCall(request).execute().close();
            } catch (Exception e) { Log.e(TAG, "Battery alert failed"); }
        }).start();
    }

    private float getBatteryLevel() {
        Intent batteryIntent = registerReceiver(null,
            new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (batteryIntent == null) return 100f;
        int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        if (level >= 0 && scale > 0) return (level * 100f) / scale;
        return 100f;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (locationManager != null) {
            locationManager.removeUpdates(locationListener);
        }
        Log.d(TAG, "LocationService stopped");
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
