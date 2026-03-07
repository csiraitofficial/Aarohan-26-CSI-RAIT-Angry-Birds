package com.example.souls.parental;

import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ParentalControlManager
 *
 * Central brain for the Souls parental control system.
 *
 * ── Features ──────────────────────────────────────────────────────────────────
 *  • PIN-protected parental lock (SHA-256 hashed, never stored in plaintext)
 *  • Per-app blocking — parent marks apps as blocked, system enforces via
 *    UsageStatsManager + foreground detection service
 *  • Daily screen-time limits (minutes) per child profile
 *  • Scheduled bedtime — define a window during which the device is locked
 *  • Usage stats read — today's per-app screen time pulled from system
 *  • Child mode toggle — when ON, the ParentalLockService is active
 *
 * ── How enforcement works ─────────────────────────────────────────────────────
 *  Android does not allow apps to kill other apps directly. Instead:
 *  1. ParentalLockService polls the foreground app every second.
 *  2. If the foreground app is blocked, it launches ParentalBlockedActivity
 *     which fills the screen and prevents the child from continuing.
 *  3. Bedtime mode works identically — any non-whitelisted app triggers the
 *     lock screen during the curfew window.
 *
 * ── Permissions required ─────────────────────────────────────────────────────
 *  • PACKAGE_USAGE_STATS (already declared for firewall)
 *  • FOREGROUND_SERVICE    (already declared for VPN)
 *
 * ── Thread safety ─────────────────────────────────────────────────────────────
 *  All SharedPreferences writes use apply(). Instance is a singleton obtained
 *  on the main thread; individual methods are thread-safe for reads.
 */
public class ParentalControlManager {

    private static final String TAG   = "ParentalControl";
    private static final String PREFS = "souls_parental";

    // Keys
    private static final String K_ENABLED          = "pc_enabled";
    private static final String K_PIN_HASH         = "pc_pin_hash";
    private static final String K_BLOCKED_APPS     = "pc_blocked_apps";      // JSON array
    private static final String K_SCREEN_LIMIT_MIN = "pc_screen_limit_min";  // 0 = disabled
    private static final String K_BEDTIME_ENABLED  = "pc_bedtime_enabled";
    private static final String K_BEDTIME_START_H  = "pc_bedtime_start_h";
    private static final String K_BEDTIME_START_M  = "pc_bedtime_start_m";
    private static final String K_BEDTIME_END_H    = "pc_bedtime_end_h";
    private static final String K_BEDTIME_END_M    = "pc_bedtime_end_m";
    private static final String K_USAGE_TODAY      = "pc_usage_today_date";  // date string for cache

    // ── Singleton ─────────────────────────────────────────────────────────────

    private static ParentalControlManager instance;
    private final SharedPreferences prefs;
    private final Context appCtx;

    private ParentalControlManager(Context ctx) {
        appCtx = ctx.getApplicationContext();
        prefs  = appCtx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static synchronized ParentalControlManager getInstance(Context ctx) {
        if (instance == null) instance = new ParentalControlManager(ctx);
        return instance;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Enable / disable
    // ─────────────────────────────────────────────────────────────────────────

    public boolean isEnabled() {
        return prefs.getBoolean(K_ENABLED, false);
    }

    /** Enable parental controls. Returns false if no PIN is set yet. */
    public boolean enable() {
        if (!isPinSet()) return false;
        prefs.edit().putBoolean(K_ENABLED, true).apply();
        return true;
    }

    public void disable() {
        prefs.edit().putBoolean(K_ENABLED, false).apply();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PIN management
    // ─────────────────────────────────────────────────────────────────────────

    public boolean isPinSet() {
        String h = prefs.getString(K_PIN_HASH, "");
        return h != null && !h.isEmpty();
    }

    /**
     * Sets a new PIN. The raw value is never stored; only its SHA-256 hash.
     * @return false if pin is shorter than 4 digits
     */
    public boolean setPin(String rawPin) {
        if (rawPin == null || rawPin.length() < 4) return false;
        prefs.edit().putString(K_PIN_HASH, sha256(rawPin)).apply();
        return true;
    }

    /** Returns true if the supplied PIN matches the stored hash. */
    public boolean verifyPin(String rawPin) {
        if (rawPin == null) return false;
        String stored = prefs.getString(K_PIN_HASH, "");
        return stored != null && stored.equals(sha256(rawPin));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Blocked apps
    // ─────────────────────────────────────────────────────────────────────────

    /** Returns the current set of blocked package names. */
    public List<String> getBlockedApps() {
        List<String> list = new ArrayList<>();
        try {
            String json = prefs.getString(K_BLOCKED_APPS, "[]");
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) list.add(arr.getString(i));
        } catch (Exception e) {
            Log.w(TAG, "getBlockedApps parse error: " + e.getMessage());
        }
        return list;
    }

    public boolean isAppBlocked(String packageName) {
        return getBlockedApps().contains(packageName);
    }

    public void blockApp(String packageName) {
        List<String> list = getBlockedApps();
        if (!list.contains(packageName)) {
            list.add(packageName);
            saveBlockedApps(list);
        }
    }

    public void unblockApp(String packageName) {
        List<String> list = getBlockedApps();
        list.remove(packageName);
        saveBlockedApps(list);
    }

    private void saveBlockedApps(List<String> list) {
        JSONArray arr = new JSONArray();
        for (String s : list) arr.put(s);
        prefs.edit().putString(K_BLOCKED_APPS, arr.toString()).apply();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Screen-time limit
    // ─────────────────────────────────────────────────────────────────────────

    /** Returns daily limit in minutes. 0 means no limit. */
    public int getScreenLimitMinutes() {
        return prefs.getInt(K_SCREEN_LIMIT_MIN, 0);
    }

    /** Set daily limit. Pass 0 to disable. */
    public void setScreenLimitMinutes(int minutes) {
        prefs.edit().putInt(K_SCREEN_LIMIT_MIN, Math.max(0, minutes)).apply();
    }

    /**
     * Returns today's total foreground usage in minutes for all user-installed
     * apps combined (excludes system launcher, Souls itself, etc.).
     * Requires PACKAGE_USAGE_STATS permission — returns -1 if not granted.
     */
    public int getTodayUsageMinutes() {
        if (!hasUsagePermission()) return -1;
        try {
            UsageStatsManager usm = (UsageStatsManager)
                    appCtx.getSystemService(Context.USAGE_STATS_SERVICE);
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            long startOfDay = cal.getTimeInMillis();
            long now = System.currentTimeMillis();

            Map<String, UsageStats> stats = usm.queryAndAggregateUsageStats(startOfDay, now);
            long totalMs = 0;
            for (UsageStats us : stats.values()) {
                if (shouldCountApp(us.getPackageName())) {
                    totalMs += us.getTotalTimeInForeground();
                }
            }
            return (int) (totalMs / 60_000L);
        } catch (Exception e) {
            Log.w(TAG, "getTodayUsageMinutes: " + e.getMessage());
            return -1;
        }
    }

    /**
     * Returns per-app usage for today as a list of AppUsageStat objects,
     * sorted descending by time. Useful for the usage report screen.
     */
    public List<AppUsageStat> getPerAppUsageToday() {
        List<AppUsageStat> result = new ArrayList<>();
        if (!hasUsagePermission()) return result;
        try {
            UsageStatsManager usm = (UsageStatsManager)
                    appCtx.getSystemService(Context.USAGE_STATS_SERVICE);
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            long start = cal.getTimeInMillis();
            long now   = System.currentTimeMillis();

            Map<String, UsageStats> stats = usm.queryAndAggregateUsageStats(start, now);
            PackageManager pm = appCtx.getPackageManager();

            for (UsageStats us : stats.values()) {
                long ms = us.getTotalTimeInForeground();
                if (ms < 10_000) continue; // ignore < 10 s
                if (!shouldCountApp(us.getPackageName())) continue;
                try {
                    ApplicationInfo ai = pm.getApplicationInfo(us.getPackageName(), 0);
                    String label = pm.getApplicationLabel(ai).toString();
                    result.add(new AppUsageStat(us.getPackageName(), label, ms));
                } catch (PackageManager.NameNotFoundException ignored) { }
            }

            result.sort((a, b) -> Long.compare(b.totalMs, a.totalMs));
        } catch (Exception e) {
            Log.w(TAG, "getPerAppUsageToday: " + e.getMessage());
        }
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Bedtime / schedule
    // ─────────────────────────────────────────────────────────────────────────

    public boolean isBedtimeEnabled() {
        return prefs.getBoolean(K_BEDTIME_ENABLED, false);
    }

    public void setBedtimeEnabled(boolean enabled) {
        prefs.edit().putBoolean(K_BEDTIME_ENABLED, enabled).apply();
    }

    /** Returns {hour, minute} for bedtime start. Default 21:00. */
    public int[] getBedtimeStart() {
        return new int[]{ prefs.getInt(K_BEDTIME_START_H, 21), prefs.getInt(K_BEDTIME_START_M, 0) };
    }

    /** Returns {hour, minute} for bedtime end. Default 07:00. */
    public int[] getBedtimeEnd() {
        return new int[]{ prefs.getInt(K_BEDTIME_END_H, 7), prefs.getInt(K_BEDTIME_END_M, 0) };
    }

    public void setBedtimeStart(int hour, int minute) {
        prefs.edit().putInt(K_BEDTIME_START_H, hour).putInt(K_BEDTIME_START_M, minute).apply();
    }

    public void setBedtimeEnd(int hour, int minute) {
        prefs.edit().putInt(K_BEDTIME_END_H, hour).putInt(K_BEDTIME_END_M, minute).apply();
    }

    /**
     * Returns true if the current time falls within the bedtime window.
     * Handles overnight ranges (e.g. 21:00 → 07:00 next morning).
     */
    public boolean isCurrentlyBedtime() {
        if (!isBedtimeEnabled()) return false;
        Calendar now = Calendar.getInstance();
        int nowMins = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);

        int[] start = getBedtimeStart();
        int[] end   = getBedtimeEnd();
        int startMins = start[0] * 60 + start[1];
        int endMins   = end[0]   * 60 + end[1];

        if (startMins <= endMins) {
            // same-day window (e.g. 09:00 – 17:00)
            return nowMins >= startMins && nowMins < endMins;
        } else {
            // overnight window (e.g. 21:00 – 07:00)
            return nowMins >= startMins || nowMins < endMins;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Permission helpers
    // ─────────────────────────────────────────────────────────────────────────

    public boolean hasUsagePermission() {
        try {
            AppOpsManager aom = (AppOpsManager) appCtx.getSystemService(Context.APP_OPS_SERVICE);
            int mode = aom.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(), appCtx.getPackageName());
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            return false;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Returns true if this package should be counted in screen-time totals. */
    private boolean shouldCountApp(String pkg) {
        if (pkg == null || pkg.isEmpty()) return false;
        if (pkg.equals(appCtx.getPackageName())) return false;      // skip self
        // Skip common system packages
        if (pkg.startsWith("com.android.") || pkg.startsWith("android")) return false;
        if (pkg.equals("com.google.android.inputmethod.latin")) return false;
        return true;
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return input; // should never fail
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Data classes
    // ─────────────────────────────────────────────────────────────────────────

    public static class AppUsageStat {
        public final String packageName;
        public final String appLabel;
        public final long   totalMs;

        public AppUsageStat(String packageName, String appLabel, long totalMs) {
            this.packageName = packageName;
            this.appLabel    = appLabel;
            this.totalMs     = totalMs;
        }

        /** Returns usage as a human-readable string like "1h 24m" or "45m". */
        public String formattedTime() {
            long totalSeconds = totalMs / 1000;
            long h = totalSeconds / 3600;
            long m = (totalSeconds % 3600) / 60;
            if (h > 0) return h + "h " + m + "m";
            return m + "m";
        }
    }
}