package com.example.souls.utils;

import android.app.Activity;
import android.app.AppOpsManager;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public final class UnknownSourcesGuard {

    private UnknownSourcesGuard() {}

    private static final String OWN_PACKAGE = "com.example.souls";

    private static volatile boolean waitingForSettingsReturn = false;
    private static volatile Runnable pendingProceed = null;

    // Colors
    private static final String COL_CARD = "#1E1E2E";
    private static final String COL_ROW = "#2A2A3D";
    private static final String COL_DANGER = "#FF4D4D";
    private static final String COL_BTN_SEC = "#2A2A3D";
    private static final String COL_WHITE = "#FFFFFF";
    private static final String COL_GREY = "#9999AA";
    private static final String COL_DIVIDER = "#2E2E42";

    // ─────────────────────────────────────
    // PUBLIC API
    // ─────────────────────────────────────

    public static void checkAndProceed(Activity activity, Runnable onProceed) {

        new Thread(() -> {

            List<String> enabledApps = getAppsWithUnknownSourcesOn(activity);

            activity.runOnUiThread(() -> {

                if (activity.isFinishing()) return;
                if (Build.VERSION.SDK_INT >= 17 && activity.isDestroyed()) return;

                if (enabledApps.isEmpty()) {
                    onProceed.run();
                } else {
                    showWarningDialog(activity, enabledApps, onProceed);
                }

            });

        }).start();
    }

    public static void resumeIfReturningFromSettings(Activity activity) {

        if (waitingForSettingsReturn && pendingProceed != null) {

            waitingForSettingsReturn = false;

            Runnable proceed = pendingProceed;
            pendingProceed = null;

            proceed.run();
        }
    }

    // ─────────────────────────────────────
    // DETECTION
    // ─────────────────────────────────────

    private static List<String> getAppsWithUnknownSourcesOn(Activity activity) {

        List<String> result = new ArrayList<>();
        PackageManager pm = activity.getPackageManager();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            AppOpsManager aom =
                    (AppOpsManager) activity.getSystemService(Context.APP_OPS_SERVICE);

            if (aom == null) return result;

            List<ApplicationInfo> apps;

            try {
                apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
            } catch (Exception e) {
                return result;
            }

            for (ApplicationInfo app : apps) {

                try {

                    if ((app.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
                    if ((app.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0) continue;
                    if (OWN_PACKAGE.equals(app.packageName)) continue;

                    int mode = aom.checkOpNoThrow(
                            "android:request_install_packages",
                            app.uid,
                            app.packageName
                    );

                    if (mode == AppOpsManager.MODE_ALLOWED) {

                        CharSequence label = pm.getApplicationLabel(app);

                        result.add(label != null ? label.toString() : app.packageName);
                    }

                } catch (Exception ignored) {}
            }

        } else {

            try {

                boolean enabled = Settings.Secure.getInt(
                        activity.getContentResolver(),
                        Settings.Secure.INSTALL_NON_MARKET_APPS,
                        0
                ) == 1;

                if (enabled) {
                    result.add("Unknown Sources (device setting ON)");
                }

            } catch (Exception ignored) {}

        }

        return result;
    }

    // ─────────────────────────────────────
    // WARNING DIALOG
    // ─────────────────────────────────────

    private static void showWarningDialog(Activity activity,
                                          List<String> enabledApps,
                                          Runnable onProceed) {

        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(false);

        Window win = dialog.getWindow();

        if (win != null) {

            win.setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));

            WindowManager.LayoutParams lp = win.getAttributes();

            lp.width = (int) (activity.getResources()
                    .getDisplayMetrics().widthPixels * 0.92f);

            lp.height = WindowManager.LayoutParams.WRAP_CONTENT;

            win.setAttributes(lp);
        }

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(activity, 20), dp(activity, 24),
                dp(activity, 20), dp(activity, 20));

        root.setBackground(roundRect(COL_CARD, 20, COL_DANGER, 1));

        // Title

        TextView title = new TextView(activity);
        title.setText("Unknown Sources Enabled");
        title.setTextSize(18f);
        title.setTextColor(Color.parseColor(COL_WHITE));
        title.setTypeface(null, Typeface.BOLD);

        root.addView(title, matchWidth());

        space(activity, root, 16, true);

        // App list

        ScrollView scroll = new ScrollView(activity);

        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);

        for (String name : enabledApps) {

            TextView tv = new TextView(activity);

            tv.setText("• " + name);
            tv.setTextSize(14f);
            tv.setTextColor(Color.parseColor(COL_DANGER));

            list.addView(tv);
        }

        scroll.addView(list);
        root.addView(scroll);

        space(activity, root, 20, true);

        // Buttons

        Button btnSettings = new Button(activity);
        btnSettings.setText("Disable in Settings");

        btnSettings.setBackground(roundRect(COL_DANGER, 12, null, 0));
        btnSettings.setTextColor(Color.WHITE);

        btnSettings.setOnClickListener(v -> {

            pendingProceed = onProceed;
            waitingForSettingsReturn = true;

            dialog.dismiss();

            openUnknownAppsSettings(activity);
        });

        root.addView(btnSettings, matchWidth());

        space(activity, root, 8, true);

        Button btnContinue = new Button(activity);

        btnContinue.setText("Continue Anyway");

        btnContinue.setBackground(roundRect(COL_BTN_SEC, 12, null, 0));
        btnContinue.setTextColor(Color.parseColor(COL_GREY));

        btnContinue.setOnClickListener(v -> {

            dialog.dismiss();
            onProceed.run();
        });

        root.addView(btnContinue, matchWidth());

        dialog.setContentView(root);
        dialog.show();
    }

    // ─────────────────────────────────────
    // SETTINGS
    // ─────────────────────────────────────

    private static void openUnknownAppsSettings(Activity activity) {

        try {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

                activity.startActivity(
                        new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));

            } else {

                activity.startActivity(
                        new Intent(Settings.ACTION_SECURITY_SETTINGS));
            }

        } catch (Exception e) {

            try {
                activity.startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (Exception ignored) {}
        }
    }

    // ─────────────────────────────────────
    // UI HELPERS
    // ─────────────────────────────────────

    private static void space(Context ctx, LinearLayout parent, int dp, boolean vertical) {

        View v = new View(ctx);

        if (vertical) {
            parent.addView(v,
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            dp(ctx, dp)));
        } else {
            parent.addView(v,
                    new LinearLayout.LayoutParams(
                            dp(ctx, dp),
                            LinearLayout.LayoutParams.MATCH_PARENT));
        }
    }

    private static GradientDrawable roundRect(String color,
                                              int radius,
                                              String stroke,
                                              int strokeDp) {

        GradientDrawable gd = new GradientDrawable();

        gd.setColor(Color.parseColor(color));
        gd.setCornerRadius(radius * 3f);

        if (stroke != null) {
            gd.setStroke(strokeDp * 3, Color.parseColor(stroke));
        }

        return gd;
    }

    private static LinearLayout.LayoutParams matchWidth() {

        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private static int dp(Context ctx, int val) {

        return Math.round(
                val * ctx.getResources().getDisplayMetrics().density);
    }
}