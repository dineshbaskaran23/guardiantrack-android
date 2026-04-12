package com.guardiantrack.app;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context ctx, Intent i) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(i.getAction())) return;
        SharedPreferences p = ctx.getSharedPreferences("gt_prefs", Context.MODE_PRIVATE);
        String token = p.getString("gt_token","");
        String phone = p.getString("gt_active_phone","");
        if (!token.isEmpty() && !phone.isEmpty()) {
            Intent s = new Intent(ctx, LocationService.class);
            s.putExtra("token",token); s.putExtra("phone",phone);
            ctx.startForegroundService(s);
        }
    }
}
