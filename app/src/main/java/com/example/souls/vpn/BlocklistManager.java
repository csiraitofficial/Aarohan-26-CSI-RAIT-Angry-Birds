package com.example.souls.firewall;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;

import com.example.souls.vpn.ResolvedIpCache;

import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class BlocklistManager {

    // Known mod APK package names (Instagram mods, WhatsApp mods, etc.)
    private static final Set<String> BLOCKED_PACKAGES = new HashSet<>(Arrays.asList(
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
            // Add more as needed
    ));

    // Social media domains to block for suspicious apps
    private static final Set<String> SOCIAL_MEDIA_DOMAINS = new HashSet<>(Arrays.asList(
            ""
    ));

    public static boolean isPackageBlocked(String packageName) {
        return BLOCKED_PACKAGES.contains(packageName);
    }

    public static boolean isBlockedDestination(String ip) {
        // Compare against cached resolved IPs for social media domains
        return ResolvedIpCache.getInstance().isBlocked(ip);
    }

    // Detect if installed app is a mod by checking signature mismatch
    public static boolean isModApk(Context ctx, String packageName) {
        try {
            PackageInfo info = ctx.getPackageManager()
                    .getPackageInfo(packageName, PackageManager.GET_SIGNATURES);
            Signature sig = info.signatures[0];
            String fingerprint = getSha256(sig.toByteArray());

            // Compare against known legitimate app fingerprints
            return !com.example.souls.firewall.KnownSignatures.isLegitimate(packageName, fingerprint);
        } catch (Exception e) {
            return false;
        }
    }

    private static String getSha256(byte[] data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(data);
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) sb.append(String.format("%02X", b));
        return sb.toString();
    }
}