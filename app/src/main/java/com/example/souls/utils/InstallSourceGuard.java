package com.example.souls.utils;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.InstallSourceInfo;
import android.content.pm.PackageManager;
import android.os.Build;

/**
 * InstallSourceGuard
 *
 * Detects whether this APK was installed from an untrusted source
 * (sideloaded via file manager, ADB, unknown APK store, etc.)
 * instead of the Google Play Store.
 *
 * Call InstallSourceGuard.verify(activity) as the very first thing
 * in SplashActivity.onCreate() — before any navigation logic.
 *
 * On a legitimate Play Store install  → does nothing, returns true.
 * On a sideloaded / unofficial install → shows a blocking, non-dismissible
 *   dialog and returns false; the caller must not proceed.
 */
public final class InstallSourceGuard {

    /** The only trusted installer package. */
    private static final String PLAY_STORE = "com.android.vending";

    private InstallSourceGuard() {}

    /**
     * @return true  — app was installed from the Play Store; safe to continue.
     *         false — untrusted source detected; a blocking dialog is shown.
     */
    public static boolean verify(Activity activity) {
        if (isTrustedInstall(activity)) {
            return true;
        }
        showBlockingDialog(activity);
        return false;
    }

    // ── Detection ────────────────────────────────────────────────────────────

    private static boolean isTrustedInstall(Context context) {
        PackageManager pm = context.getPackageManager();
        String pkg = context.getPackageName();

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // API 30+: use the richer InstallSourceInfo API
                InstallSourceInfo info = pm.getInstallSourceInfo(pkg);
                String initiating  = info.getInitiatingPackageName();
                String originating = info.getOriginatingPackageName();
                String installing  = info.getInstallingPackageName();

                // Allow the Play Store itself or a Play-managed auto-update
                return PLAY_STORE.equals(initiating)
                        || PLAY_STORE.equals(originating)
                        || PLAY_STORE.equals(installing);

            } else {
                // API < 30: legacy method
                @SuppressWarnings("deprecation")
                String installer = pm.getInstallerPackageName(pkg);
                return PLAY_STORE.equals(installer);
            }

        } catch (PackageManager.NameNotFoundException e) {
            // Should never happen — we are asking about ourselves.
            // Treat as untrusted to be safe.
            return false;
        }
    }

    // ── Dialog ───────────────────────────────────────────────────────────────

    private static void showBlockingDialog(Activity activity) {
        new AlertDialog.Builder(activity)
                .setTitle("⚠️ Unofficial Version Detected")
                .setMessage(
                        "This app was not installed from the Google Play Store.\n\n"
                                + "Installing apps from unknown sources is dangerous:\n"
                                + "  • The APK may be tampered with or contain malware\n"
                                + "  • Your Soul ID and wallet data could be compromised\n"
                                + "  • Bot and replay attacks are significantly easier\n\n"
                                + "Please uninstall this version and download the official "
                                + "app from the Play Store to continue safely."
                )
                .setPositiveButton("Open Play Store", (dialog, which) -> {
                    openPlayStore(activity);
                    activity.finish();
                })
                .setNegativeButton("Close App", (dialog, which) -> {
                    activity.finishAffinity();
                })
                // Prevent the user from dismissing by tapping outside or back button
                .setCancelable(false)
                .show();
    }

    private static void openPlayStore(Activity activity) {
        String pkg = activity.getPackageName();
        try {
            activity.startActivity(
                    new android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("market://details?id=" + pkg)
                    )
            );
        } catch (android.content.ActivityNotFoundException e) {
            // Device has no Play Store app — fall back to browser
            activity.startActivity(
                    new android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(
                                    "https://play.google.com/store/apps/details?id=" + pkg)
                    )
            );
        }
    }
}