package com.guardiantrack.app;
import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.os.BatteryManager;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import com.google.android.gms.location.*;
import okhttp3.*;
import java.util.concurrent.TimeUnit;

public class LocationService extends Service {
    private FusedLocationProviderClient fused;
    private LocationCallback cb;
    private OkHttpClient http;
    private String phone="", token="";

    @Override public void onCreate() {
        super.onCreate();
        fused = LocationServices.getFusedLocationProviderClient(this);
        http = new OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).build();
    }

    @Override public int onStartCommand(Intent i, int f, int s) {
        if (i!=null) { phone=i.getStringExtra("phone")!=null?i.getStringExtra("phone"):""; token=i.getStringExtra("token")!=null?i.getStringExtra("token"):""; }
        startForeground(1, new Notification.Builder(this, MainActivity.CH_GPS)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("GuardianTrack").setContentText("Sending location every 30s").setOngoing(true).build());
        LocationRequest req = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 30000L).setMinUpdateIntervalMillis(15000L).build();
        cb = new LocationCallback() {
            @Override public void onLocationResult(LocationResult r) {
                if (r==null) return;
                Location l = r.getLastLocation();
                if (l!=null) sendLoc(l.getLatitude(), l.getLongitude(), l.getSpeed()*3.6f);
            }
        };
        try { fused.requestLocationUpdates(req, cb, Looper.getMainLooper()); } catch(SecurityException e) { Log.e("GT","No location perm"); }
        return START_STICKY;
    }

    private void sendLoc(double lat, double lng, float spd) {
        if (token.isEmpty()||phone.isEmpty()) return;
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent bi = registerReceiver(null, ifilter);
        float batt = 100;
        if (bi!=null) { int lv=bi.getIntExtra(BatteryManager.EXTRA_LEVEL,-1); int sc=bi.getIntExtra(BatteryManager.EXTRA_SCALE,-1); if(lv>=0&&sc>0) batt=(lv*100f)/sc; }
        final float battery = batt;
        new Thread(() -> {
            try {
                String body = String.format("{"lat":%f,"lng":%f,"speed":%f,"battery":%.0f}", lat, lng, spd, battery);
                Request req = new Request.Builder()
                    .url("https://guardiantrack-backend.onrender.com/api/location/update")
                    .addHeader("Authorization","Bearer "+token)
                    .addHeader("Content-Type","application/json")
                    .post(RequestBody.create(body, MediaType.parse("application/json")))
                    .build();
                http.newCall(req).execute().close();
            } catch(Exception e) { Log.e("GT","Send failed",e); }
        }).start();
    }

    @Override public void onDestroy() { super.onDestroy(); if(fused!=null&&cb!=null) fused.removeLocationUpdates(cb); }
    @Override public IBinder onBind(Intent i) { return null; }
}
