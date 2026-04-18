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
    private static final long INTERVAL_MS = 30000;
    private static final float MIN_DISTANCE_M = 10;

    private LocationManager locationManager;
    private OkHttpClient httpClient;
    private String phone = "";
    private String token = "";

    private LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
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
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            phone = intent.getStringExtra("phone") != null ? intent.getStringExtra("phone") : "";
            token = intent.getStringExtra("token") != null ? intent.getStringExtra("token") : "";
        }
        startForeground(1, new Notification.Builder(this, MainActivity.CH_GPS)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("GuardianTrack Active")
            .setContentText("Sending location every 30 seconds")
            .setOngoing(true).build());
        startLocationUpdates();
        return START_STICKY;
    }

    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "No location permission");
            return;
        }
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, INTERVAL_MS, MIN_DISTANCE_M, locationListener);
        }
        if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER, INTERVAL_MS, MIN_DISTANCE_M, locationListener);
        }
        Log.d(TAG, "Location updates started");
    }

    private void sendLocation(final Location loc) {
        if (token.isEmpty() || phone.isEmpty()) return;
        final float battery = getBatteryLevel();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // Build JSON using concatenation - avoids all escaping issues
                    String body = "{" +
                        "\"lat\":" + loc.getLatitude() + "," +
                        "\"lng\":" + loc.getLongitude() + "," +
                        "\"speed\":" + (loc.getSpeed() * 3.6f) + "," +
                        "\"accuracy\":" + loc.getAccuracy() + "," +
                        "\"battery\":" + (int) battery + "," +
                        "\"phone\":\"" + phone + "\"" +
                        "}";

                    Request request = new Request.Builder()
                        .url(BACKEND + "/api/location/update")
                        .addHeader("Authorization", "Bearer " + token)
                        .addHeader("Content-Type", "application/json")
                        .post(RequestBody.create(body, MediaType.parse("application/json")))
                        .build();

                    okhttp3.Response response = httpClient.newCall(request).execute();
                    Log.d(TAG, "Location sent. Status: " + response.code());
                    response.close();

                    if (battery < 20) {
                        sendBatteryAlert(battery);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to send location: " + e.getMessage());
                }
            }
        }).start();
    }

    private void sendBatteryAlert(final float battery) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String body = "{" +
                        "\"type\":\"battery\"," +
                        "\"message\":\"Battery low: " + (int) battery + "%\"," +
                        "\"phone\":\"" + phone + "\"" +
                        "}";
                    Request request = new Request.Builder()
                        .url(BACKEND + "/api/alerts")
                        .addHeader("Authorization", "Bearer " + token)
                        .addHeader("Content-Type", "application/json")
                        .post(RequestBody.create(body, MediaType.parse("application/json")))
                        .build();
                    httpClient.newCall(request).execute().close();
                } catch (Exception e) {
                    Log.e(TAG, "Battery alert failed: " + e.getMessage());
                }
            }
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
