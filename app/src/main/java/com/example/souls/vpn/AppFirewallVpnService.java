package com.example.souls.vpn;

import android.content.pm.PackageManager;
import android.net.VpnService;
import android.content.Intent;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class AppFirewallVpnService extends VpnService {

    private static final String TAG = "SoulsFirewall";

    private ParcelFileDescriptor vpnInterface;
    private Thread vpnThread;
    private volatile boolean running = false;

    public static final String ACTION_START = "com.example.souls.firewall.START";
    public static final String ACTION_STOP  = "com.example.souls.firewall.STOP";

    /**
     * ONLY these packages are routed through the VPN tunnel.
     * All other apps (including official Instagram, WhatsApp, Brave, Chrome)
     * bypass the VPN entirely and have full unthrottled internet access.
     *
     * Keep this in sync with BlocklistManager.BLOCKED_PACKAGES.
     */
    public static final Set<String> BLOCKED_PACKAGES = new HashSet<>(Arrays.asList(
            "com.gbwhatsapp",
            "com.whatsapp.mod",
            "com.instagram.lite.mod",
            "org.telegram.plus",
            "com.snapchat.mod",
            "com.instamod",
            "com.gbinsta",
            "com.aero.whatsapp",
            "com.yowhatsapp",
            "com.fouad.whatsapp",
            "com.dfinstagram.android"
    ));

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopVpn();
            return START_NOT_STICKY;
        }
        startVpn();
        return START_STICKY;
    }

    private void startVpn() {
        Builder builder = new Builder();
        builder.setSession("SoulsFirewall")
                .addAddress("10.0.0.2", 32)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("8.8.8.8")
                .setMtu(1500);

        // ── KEY FIX ───────────────────────────────────────────────────────────
        // Instead of routing ALL apps through the VPN, only route the
        // blocked/mod packages. Every other app (official Instagram, WhatsApp,
        // Brave, Chrome, etc.) bypasses this VPN completely and gets normal
        // internet access. This is the correct approach — no packet inspection
        // needed at all because Android handles the per-app routing for us.
        boolean anyAdded = false;
        for (String pkg : BLOCKED_PACKAGES) {
            try {
                builder.addAllowedApplication(pkg);
                anyAdded = true;
                Log.d(TAG, "Routing through VPN (will be blocked): " + pkg);
            } catch (PackageManager.NameNotFoundException e) {
                // App not installed — skip silently
                Log.d(TAG, "Not installed, skipping: " + pkg);
            }
        }

        if (!anyAdded) {
            // None of the blocked packages are installed — no point running
            Log.d(TAG, "No blocked packages installed. VPN not needed.");
            stopSelf();
            return;
        }

        try {
            vpnInterface = builder.establish();
        } catch (Exception e) {
            Log.e(TAG, "Failed to establish VPN", e);
            stopSelf();
            return;
        }

        if (vpnInterface == null) {
            Log.e(TAG, "VPN interface is null — permission not granted?");
            stopSelf();
            return;
        }

        running = true;
        vpnThread = new Thread(this::runPacketLoop, "VpnPacketLoop");
        vpnThread.start();

        Log.d(TAG, "Firewall VPN started. Blocking " + BLOCKED_PACKAGES.size() + " packages.");
    }

    /**
     * Packet loop — reads packets from blocked apps and simply drops them all.
     *
     * Since addAllowedApplication() already restricts the tunnel to only
     * blocked apps, we don't need to inspect IPs at all — every packet that
     * arrives here is from a mod/blocked app and should be dropped.
     *
     * We intentionally do NOT write packets back to the tun interface,
     * which causes all connections from these apps to time out / fail.
     */
    private void runPacketLoop() {
        try (FileInputStream in = new FileInputStream(vpnInterface.getFileDescriptor())) {
            ByteBuffer packet = ByteBuffer.allocate(32767);
            while (running) {
                packet.clear();
                int len = in.read(packet.array());
                if (len <= 0) continue;
                // Drop — do not forward. Mod app gets no response.
                Log.v(TAG, "Dropped packet (" + len + " bytes) from blocked app.");
            }
        } catch (Exception e) {
            if (running) Log.e(TAG, "Packet loop error", e);
        }
    }

    private void stopVpn() {
        running = false;
        try {
            if (vpnThread != null) vpnThread.interrupt();
            if (vpnInterface != null) vpnInterface.close();
        } catch (Exception ignored) {}
        stopSelf();
        Log.d(TAG, "Firewall VPN stopped.");
    }

    @Override
    public void onDestroy() {
        stopVpn();
        super.onDestroy();
    }
}