package com.example.souls.vpn;  // ← must be vpn, not firewall

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.example.souls.utils.SessionManager;
import com.example.souls.vpn.FirewallMonitorService;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            if (SessionManager.getInstance(context).isFirewallEnabled()) {
                Intent service = new Intent(context, FirewallMonitorService.class);
                context.startForegroundService(service);
            }
        }
    }
}