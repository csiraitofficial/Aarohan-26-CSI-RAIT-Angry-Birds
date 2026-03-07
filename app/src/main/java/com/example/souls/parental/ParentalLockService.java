package com.example.souls.parental;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.souls.R;

import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * ParentalLockService
 *
 * A foreground service that polls the device's foreground app every second.
 * When the active app is in the blocked list (or bedtime is active and the
 * current app isn't whitelisted), it launches ParentalBlockedActivity to
 * cover the screen.
 *
 * ── Lifecycle ─────────────────────────────────────────────────────────────────
 *  Started by ParentalControlActivity when controls are enabled.
 *  Stopped (via stopService) when the parent disables controls or disables
 *  child mode.
 *
 * ── Why foreground? ───────────────────────────────────────────────────────────
 *  Background services are aggressively killed on Android 8+. A foreground
 *  service with a persistent notification keeps us alive. The notification
 *  is subtle ("Child mode active").
 *
 * ── UsageStatsManager vs ActivityManager ─────────────────────────────────────
 *  ActivityManager.getRunningTasks() was restricted in API 21. The correct
 *  modern approach is UsageStatsManager.queryUsageStats() with a very short
 *  interval (we use the last 3 seconds). The most recently used app whose
 *  lastTimeUsed is within that window is considered foreground.
 */
public class ParentalLockService extends Service {

    public  static final String TAG            = "ParentalLockSvc";
    private static final int    NOTIF_ID       = 7001;
    private static final String CHANNEL_ID     = "souls_parental";
    private static final long   POLL_INTERVAL  = 1_000L; // 1 second

    private ParentalControlManager pcm;
    private Handler                handler;
    private Runnable               pollRunnable;
    private String                 lastBlockedPkg = "";

    // ── Service lifecycle ─────────────────────────────────────────────────────

    @Override
    public void onCreate() {
        super.onCreate();
        pcm     = ParentalControlManager.getInstance(this);
        handler = new Handler(Looper.getMainLooper());
        createNotificationChannel();
        startForeground(NOTIF_ID, buildNotification());
        startPolling();
        Log.d(TAG, "Service started");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY; // restart automatically if killed
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopPolling();
        Log.d(TAG, "Service stopped");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ── Polling ───────────────────────────────────────────────────────────────

    private void startPolling() {
        pollRunnable = new Runnable() {
            @Override
            public void run() {
                checkForegroundApp();
                handler.postDelayed(this, POLL_INTERVAL);
            }
        };
        handler.post(pollRunnable);
    }

    private void stopPolling() {
        if (pollRunnable != null) handler.removeCallbacks(pollRunnable);
    }

    private void checkForegroundApp() {
        if (!pcm.isEnabled()) return;

        String foreground = getForegroundPackage();
        if (foreground == null || foreground.equals(getPackageName())) return;

        boolean shouldBlock = pcm.isAppBlocked(foreground) || pcm.isCurrentlyBedtime();

        if (shouldBlock && !foreground.equals(lastBlockedPkg)) {
            lastBlockedPkg = foreground;
            launchBlockScreen(foreground);
        } else if (!shouldBlock) {
            lastBlockedPkg = "";
        }
    }

    /**
     * Uses UsageStatsManager to determine which app is currently in the
     * foreground. Looks back 3 seconds and returns the most recently active
     * package, or null if indeterminate.
     */
    private String getForegroundPackage() {
        try {
            UsageStatsManager usm = (UsageStatsManager)
                    getSystemService(Context.USAGE_STATS_SERVICE);
            long now = System.currentTimeMillis();
            List<UsageStats> stats = usm.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY, now - 3_000, now);

            if (stats == null || stats.isEmpty()) return null;

            UsageStats recent = null;
            for (UsageStats us : stats) {
                if (recent == null || us.getLastTimeUsed() > recent.getLastTimeUsed()) {
                    recent = us;
                }
            }
            return recent != null ? recent.getPackageName() : null;
        } catch (Exception e) {
            Log.w(TAG, "getForegroundPackage: " + e.getMessage());
            return null;
        }
    }

    private void launchBlockScreen(String blockedPackage) {
        Intent intent = new Intent(this, ParentalBlockedActivity.class);
        intent.putExtra(ParentalBlockedActivity.EXTRA_BLOCKED_PKG, blockedPackage);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID,
                    "Parental Controls",
                    NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Souls child mode is active");
            ch.setShowBadge(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    private Notification buildNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Child mode active")
                .setContentText("Parental controls are monitoring this device")
                .setSmallIcon(R.drawable.ic_shield)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();
    }

    // ── Static helpers ────────────────────────────────────────────────────────

    public static void start(Context ctx) {
        Intent i = new Intent(ctx, ParentalLockService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.startForegroundService(i);
        } else {
            ctx.startService(i);
        }
    }

    public static void stop(Context ctx) {
        ctx.stopService(new Intent(ctx, ParentalLockService.class));
    }
}