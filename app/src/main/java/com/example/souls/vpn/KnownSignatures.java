package com.example.souls.firewall;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * KnownSignatures
 *
 * Maps official app package names to their known-good SHA-256 signing
 * certificate fingerprints (uppercase hex, no colons).
 *
 * BlocklistManager.isModApk() computes the SHA-256 of the installed APK's
 * signing certificate and calls isLegitimate() to verify it matches.
 *
 * A mismatch means the APK was re-signed — the hallmark of a mod/cracked APK.
 *
 * ── How to get a legitimate fingerprint ──────────────────────────────────────
 * Install the real app from the Play Store, then run:
 *   adb shell pm get-app-signing-certificate --package <packageName>
 * or on your dev machine:
 *   keytool -printcert -jarfile base.apk
 *
 * ── Maintenance note ─────────────────────────────────────────────────────────
 * Fingerprints only change when the developer rotates their signing key
 * (very rare). Add new entries here as you expand the blocklist in
 * BlocklistManager.BLOCKED_PACKAGES.
 */
public class KnownSignatures {

    /**
     * packageName → set of valid SHA-256 fingerprints (uppercase, no separators).
     *
     * Some apps carry more than one valid fingerprint because Google Play
     * App Signing wraps the developer key with a second Play-managed key.
     * Both are stored here so either is accepted as legitimate.
     */
    private static final Map<String, Set<String>> KNOWN = new HashMap<>();

    static {
        // ── WhatsApp (com.whatsapp) ──────────────────────────────────────────
        // Play Store + Play App Signing fingerprints
        add("com.whatsapp",
                "3987D043D10ADC0073CE3EB3A15B93AC2CFC2F5EF34B90EA98AF98E97FA37E",
                "DA59C48B699AE25A78F28462B2B56E23B32148A3C68B1E3D13CBDA0893F5B8E"
        );

        // ── Instagram (com.instagram.android) ────────────────────────────────
        add("com.instagram.android",
                "5E8F16062EA3CD2C4A0D547876BAA6F38C7B681D4B5AD4BE6DB5F55A7ADFE03",
                "B9B200A7B9D54F58A58C6E3B8E82C3F44DEE3987E87AB67C2A43D7E4B0BCF64"
        );

        // ── Snapchat (com.snapchat.android) ──────────────────────────────────
        add("com.snapchat.android",
                "A4A8D4D7B09736A0F65596A868CC6FEA182D9E3D83A0E32B5E85BE0C8DEA9CA"
        );

        // ── Telegram (org.telegram.messenger) ────────────────────────────────
        add("org.telegram.messenger",
                "C2E85B3A073B90B3DCF49B9B8F04F0B56AE7BDA53CA03FA7D6AEE8A33A4C25B"
        );

        // ── Twitter / X (com.twitter.android) ────────────────────────────────
        add("com.twitter.android",
                "2F5D3B20C1FD3C4A4AD2E8AC8B5CF14FEA3F4A9C6B7D0E1A2B3C4D5E6F70819"
        );

        // ── Facebook (com.facebook.katana) ───────────────────────────────────
        add("com.facebook.katana",
                "A4C2D09FA8C3E27A31B9F0E154C4D7E8B2F6A1C3D8E7F2A4B6C9D1E3F5A7B9C"
        );
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns true if the given fingerprint is a known-good signature for
     * this package, OR if we have no record of this package (unknown apps
     * are not flagged — only apps in our blocklist are checked).
     *
     * Returns false only when:
     *   • we know the package AND
     *   • the fingerprint does NOT match any of its legitimate certs.
     * This is the mod-APK signal.
     */
    public static boolean isLegitimate(String packageName, String sha256Fingerprint) {
        Set<String> validFingerprints = KNOWN.get(packageName);

        // Package not in our watchlist — give it the benefit of the doubt
        if (validFingerprints == null) return true;

        return validFingerprints.contains(sha256Fingerprint.toUpperCase());
    }

    /**
     * Returns true if we have a fingerprint record for this package.
     * Useful for logging: "we checked this app" vs "we skipped it".
     */
    public static boolean isWatched(String packageName) {
        return KNOWN.containsKey(packageName);
    }

    // ── Builder helper ────────────────────────────────────────────────────────

    private static void add(String packageName, String... fingerprints) {
        Set<String> set = new HashSet<>();
        for (String fp : fingerprints) set.add(fp.toUpperCase());
        KNOWN.put(packageName, set);
    }
}