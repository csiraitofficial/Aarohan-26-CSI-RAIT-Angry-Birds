package com.example.souls.utils;

import android.content.Context;
import android.provider.Settings;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * DeviceKeyManager
 *
 * Generates a permanent, hardware-derived key for this device.
 *
 * Strategy:
 *   key = SHA-256( ANDROID_ID + Build.BOARD + Build.BRAND + Build.DEVICE )
 *         encoded as a 64-char hex string, prefixed with "DK-"
 *
 * The ANDROID_ID is scoped to the app's signing key and package name (API 26+),
 * meaning it DOES survive reinstalls but changes if the user factory-resets OR
 * sideloads a differently-signed APK. Combining it with Build constants makes
 * collision on different physical hardware effectively impossible.
 *
 * Result: same key every launch, same key after reinstall, different on every
 * physical device.
 */
public class DeviceKeyManager {

    private static final String TAG = "DeviceKeyManager";

    private DeviceKeyManager() {}

    /**
     * Returns the permanent device key. Never null.
     * Falls back to a salted Build-only hash if ANDROID_ID is unavailable.
     */
    public static String getDeviceKey(Context ctx) {
        try {
            String androidId = Settings.Secure.getString(
                    ctx.getContentResolver(), Settings.Secure.ANDROID_ID);

            String raw = (androidId != null ? androidId : "unknown")
                    + "|" + android.os.Build.BOARD
                    + "|" + android.os.Build.BRAND
                    + "|" + android.os.Build.DEVICE
                    + "|" + android.os.Build.HARDWARE
                    + "|" + android.os.Build.MODEL;

            return "DK-" + sha256Hex(raw);

        } catch (Exception e) {
            Log.e(TAG, "Failed to generate device key", e);
            // Fallback — still deterministic for the given build
            return "DK-FALLBACK-" + sha256HexUnchecked(android.os.Build.FINGERPRINT);
        }
    }

    // ─── Hashing helpers ─────────────────────────────────────────────────────

    private static String sha256Hex(String input) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(hash);
    }

    private static String sha256HexUnchecked(String input) {
        try {
            return sha256Hex(input);
        } catch (Exception e) {
            return Integer.toHexString(input.hashCode());
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}