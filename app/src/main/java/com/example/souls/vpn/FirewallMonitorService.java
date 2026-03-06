package com.example.souls.vpn;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

import com.example.souls.R;
import com.example.souls.vpn.AppFirewallVpnService;  // ← fixed import

public class FirewallMonitorService extends Service {

    private static final String CHANNEL_ID = "souls_firewall";
    private static final int    NOTIF_ID   = 1001;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIF_ID, buildNotification());

        // Start VPN
        Intent vpnIntent = new Intent(this, AppFirewallVpnService.class);
        vpnIntent.setAction(AppFirewallVpnService.ACTION_START);
        startService(vpnIntent);

        // Scan installed packages for known mods
        scanInstalledApps();
    }

    private void scanInstalledApps() {
        new Thread(() -> {
            PackageManager pm = getPackageManager();
            for (android.content.pm.ApplicationInfo app :
                    pm.getInstalledApplications(PackageManager.GET_META_DATA)) {
                if (isPackageBlocked(app.packageName)) {
                    notifyModDetected(app.packageName);
                }
            }
        }).start();
    }

    /** Checks if a package name is in the VPN blocklist. */
    private boolean isPackageBlocked(String packageName) {
        return AppFirewallVpnService.BLOCKED_PACKAGES.contains(packageName);
    }

    private void notifyModDetected(String packageName) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        Notification n = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_warning)
                .setContentTitle("⚠ Mod APK Detected")
                .setContentText("Blocked: " + packageName)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build();
        nm.notify(packageName.hashCode(), n);
    }

    private Notification buildNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_shield)
                .setContentTitle("Souls Firewall Active")
                .setContentText("Monitoring for mod APKs…")
                .setOngoing(true)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "Firewall", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }

    @Override
    public IBinder onBind(Intent i) { return null; }
}