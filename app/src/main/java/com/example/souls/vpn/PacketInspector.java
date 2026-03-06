package com.example.souls.firewall;

import android.content.Context;
import android.net.ConnectivityManager;
import java.io.BufferedReader;
import java.io.FileReader;

public class PacketInspector {

    // Read /proc/net/tcp to map local port → UID → package
    public static int getUidForPort(int localPort) {
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/net/tcp"))) {
            String line;
            br.readLine(); // skip header
            while ((line = br.readLine()) != null) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length < 8) continue;
                // local_address is parts[1]: hex_ip:hex_port
                String hexPort = parts[1].split(":")[1];
                int port = Integer.parseInt(hexPort, 16);
                if (port == localPort) {
                    return Integer.parseInt(parts[7]); // UID
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        return -1;
    }

    public static String getPackageForUid(Context ctx, int uid) {
        String[] packages = ctx.getPackageManager().getPackagesForUid(uid);
        return (packages != null && packages.length > 0) ? packages[0] : null;
    }
}