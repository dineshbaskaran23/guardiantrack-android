package com.guardiantrack.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;

        SharedPreferences prefs = context.getSharedPreferences("gt_prefs", Context.MODE_PRIVATE);
        String token = prefs.getString("gt_token", "");
        String phone = prefs.getString("gt_active_phone", "");

        if (!token.isEmpty() && !phone.isEmpty()) {
            Intent serviceIntent = new Intent(context, LocationService.class);
            serviceIntent.putExtra("token", token);
            serviceIntent.putExtra("phone", phone);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        }
    }
}
