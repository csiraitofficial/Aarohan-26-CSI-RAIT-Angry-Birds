package com.example.souls.parental;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.souls.R;

/**
 * ParentalBlockedActivity
 *
 * Full-screen wall shown to the child when they attempt to open a blocked app
 * or use the device during bedtime.
 *
 * ── Behaviour ─────────────────────────────────────────────────────────────────
 *  • Launched by ParentalLockService whenever a blocked package comes foreground.
 *  • Displayed with FLAG_ACTIVITY_NEW_TASK so it appears over anything.
 *  • Back button is consumed (child cannot press back to dismiss it).
 *  • Recent apps key is effectively neutralised: re-launching a blocked app
 *    would immediately trigger another block.
 *  • Parent can enter their PIN to dismiss and temporarily whitelist the app
 *    for the session (60-minute grace period) or permanently unblock it.
 *
 * ── Layout ────────────────────────────────────────────────────────────────────
 *  activity_parental_blocked.xml — dark lock-screen aesthetic consistent with
 *  the rest of the Souls UI.
 */
public class ParentalBlockedActivity extends AppCompatActivity {

    public static final String EXTRA_BLOCKED_PKG = "blocked_pkg";

    private ParentalControlManager pcm;
    private String blockedPackage;

    // PIN entry views (initially hidden)
    private View    pinSection;
    private EditText etPin;
    private TextView tvBlockedAppName;
    private TextView tvReason;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_parental_blocked);

        pcm = ParentalControlManager.getInstance(this);
        blockedPackage = getIntent().getStringExtra(EXTRA_BLOCKED_PKG);

        tvBlockedAppName = findViewById(R.id.tv_blocked_app_name);
        tvReason         = findViewById(R.id.tv_blocked_reason);
        pinSection       = findViewById(R.id.layout_pin_entry);
        etPin            = findViewById(R.id.et_parent_pin);

        // Show app name
        tvBlockedAppName.setText(resolveAppLabel(blockedPackage));

        // Reason
        if (pcm.isCurrentlyBedtime()) {
            tvReason.setText("It's bedtime. The device is locked for the night.");
        } else {
            tvReason.setText("This app has been blocked by your parent.");
        }

        // "Enter parent PIN" button
        findViewById(R.id.btn_enter_pin).setOnClickListener(v -> {
            pinSection.setVisibility(View.VISIBLE);
            etPin.requestFocus();
        });

        // Confirm PIN
        findViewById(R.id.btn_confirm_pin).setOnClickListener(v -> {
            String entered = etPin.getText().toString().trim();
            if (pcm.verifyPin(entered)) {
                showUnlockOptions();
            } else {
                etPin.setText("");
                Toast.makeText(this, "Incorrect PIN", Toast.LENGTH_SHORT).show();
            }
        });

        // Keep screen on (so the child can't wait for screen-off to bypass)
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    public void onBackPressed() {
        // Intentionally block back button
        // Do not call super — the child cannot dismiss this screen with back
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Block home key isn't possible via API, but consume other navigation
        if (keyCode == KeyEvent.KEYCODE_BACK) return true;
        return super.onKeyDown(keyCode, event);
    }

    // ── After correct PIN ─────────────────────────────────────────────────────

    private void showUnlockOptions() {
        pinSection.setVisibility(View.GONE);

        // Show option panel
        View optionPanel = findViewById(R.id.layout_unlock_options);
        optionPanel.setVisibility(View.VISIBLE);

        // Allow once (go back, unblock for this session only)
        findViewById(R.id.btn_allow_once).setOnClickListener(v -> {
            // Just close the block screen; the service will stop blocking
            // this package for 60 minutes via a temporary grace set
            pcm.unblockApp(blockedPackage);
            // Re-block after 60 minutes
            new android.os.Handler(android.os.Looper.getMainLooper())
                    .postDelayed(() -> pcm.blockApp(blockedPackage), 60 * 60 * 1_000L);
            finish();
        });

        // Permanently unblock
        findViewById(R.id.btn_unblock_permanent).setOnClickListener(v -> {
            pcm.unblockApp(blockedPackage);
            Toast.makeText(this,
                    resolveAppLabel(blockedPackage) + " has been unblocked.",
                    Toast.LENGTH_SHORT).show();
            finish();
        });

        // Cancel — stay on block screen
        findViewById(R.id.btn_cancel_unlock).setOnClickListener(v -> {
            optionPanel.setVisibility(View.GONE);
            etPin.setText("");
        });
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private String resolveAppLabel(String pkg) {
        if (pkg == null || pkg.isEmpty()) return "This app";
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            return pm.getApplicationLabel(ai).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }
}