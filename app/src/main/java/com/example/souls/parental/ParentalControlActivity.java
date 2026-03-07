package com.example.souls.parental;

import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.souls.R;

import java.util.Locale;

public class ParentalControlActivity extends AppCompatActivity {

    private ParentalControlManager pcm;

    // Child mode
    private SwitchCompat switchChildMode;
    private TextView     tvChildModeStatus;

    // Screen time
    private SeekBar  seekScreenTime;
    private TextView tvScreenTimeValue;
    private TextView tvTodayUsage;

    // Bedtime
    private SwitchCompat switchBedtime;
    private LinearLayout layoutBedtimeConfig;
    private TextView     tvBedtimeStart;
    private TextView     tvBedtimeEnd;

    // App list
    private RecyclerView       rvApps;
    private BlockedAppsAdapter appsAdapter;

    // Guard — true only after initUi() completes
    private boolean uiReady = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_parental_control);

        pcm = ParentalControlManager.getInstance(this);

        if (!pcm.isPinSet()) {
            showSetPinDialog(false);
        } else {
            requirePinToEnter();
        }
    }

    /** Called after PIN is verified — safe to render the full UI. */
    private void initUi() {
        // ── Back ─────────────────────────────────────────────────────────────
        findViewById(R.id.iv_back).setOnClickListener(v -> onBackPressed());

        // ── Change PIN ────────────────────────────────────────────────────────
        findViewById(R.id.btn_change_pin).setOnClickListener(v -> showSetPinDialog(true));

        // ── Child mode toggle ─────────────────────────────────────────────────
        switchChildMode   = findViewById(R.id.switch_child_mode);
        tvChildModeStatus = findViewById(R.id.tv_child_mode_status);

        switchChildMode.setChecked(pcm.isEnabled());
        updateChildModeStatus();

        switchChildMode.setOnCheckedChangeListener((btn, checked) -> {
            if (checked) {
                if (!pcm.hasUsagePermission()) {
                    switchChildMode.setChecked(false);
                    showUsagePermissionDialog();
                    return;
                }
                boolean ok = pcm.enable();
                if (ok) {
                    ParentalLockService.start(this);
                    Toast.makeText(this, "Child mode enabled", Toast.LENGTH_SHORT).show();
                } else {
                    switchChildMode.setChecked(false);
                    Toast.makeText(this, "Set a PIN first", Toast.LENGTH_SHORT).show();
                }
            } else {
                pcm.disable();
                ParentalLockService.stop(this);
                Toast.makeText(this, "Child mode disabled", Toast.LENGTH_SHORT).show();
            }
            updateChildModeStatus();
        });

        // ── Screen time limit ─────────────────────────────────────────────────
        seekScreenTime    = findViewById(R.id.seek_screen_time);
        tvScreenTimeValue = findViewById(R.id.tv_screen_time_value);
        tvTodayUsage      = findViewById(R.id.tv_today_usage);

        seekScreenTime.setMax(480);
        seekScreenTime.setProgress(pcm.getScreenLimitMinutes());
        updateScreenTimeLabel(pcm.getScreenLimitMinutes());
        updateTodayUsage();

        seekScreenTime.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean user) {
                updateScreenTimeLabel(progress);
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {
                pcm.setScreenLimitMinutes(sb.getProgress());
                Toast.makeText(ParentalControlActivity.this,
                        "Screen time limit saved", Toast.LENGTH_SHORT).show();
            }
        });

        // ── Bedtime ───────────────────────────────────────────────────────────
        switchBedtime       = findViewById(R.id.switch_bedtime);
        layoutBedtimeConfig = findViewById(R.id.layout_bedtime_config);
        tvBedtimeStart      = findViewById(R.id.tv_bedtime_start);
        tvBedtimeEnd        = findViewById(R.id.tv_bedtime_end);

        switchBedtime.setChecked(pcm.isBedtimeEnabled());
        layoutBedtimeConfig.setVisibility(pcm.isBedtimeEnabled() ? View.VISIBLE : View.GONE);
        refreshBedtimeLabels();

        switchBedtime.setOnCheckedChangeListener((btn, checked) -> {
            pcm.setBedtimeEnabled(checked);
            layoutBedtimeConfig.setVisibility(checked ? View.VISIBLE : View.GONE);
        });

        tvBedtimeStart.setOnClickListener(v -> {
            int[] t = pcm.getBedtimeStart();
            new TimePickerDialog(this, (tp, h, m) -> {
                pcm.setBedtimeStart(h, m);
                refreshBedtimeLabels();
            }, t[0], t[1], true).show();
        });

        tvBedtimeEnd.setOnClickListener(v -> {
            int[] t = pcm.getBedtimeEnd();
            new TimePickerDialog(this, (tp, h, m) -> {
                pcm.setBedtimeEnd(h, m);
                refreshBedtimeLabels();
            }, t[0], t[1], true).show();
        });

        // ── App blocking RecyclerView ─────────────────────────────────────────
        rvApps      = findViewById(R.id.rv_blocked_apps);
        appsAdapter = new BlockedAppsAdapter(this, pcm);
        rvApps.setLayoutManager(new LinearLayoutManager(this));
        rvApps.setAdapter(appsAdapter);

        // ── Usage report button ───────────────────────────────────────────────
        findViewById(R.id.btn_view_usage).setOnClickListener(v -> {
            startActivity(new Intent(this, UsageReportActivity.class));
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        });

        uiReady = true; // mark UI as fully initialised
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Guard: views don't exist yet if the PIN dialog is still showing
        if (!uiReady) return;
        updateTodayUsage();
    }

    // ── UI updates ────────────────────────────────────────────────────────────

    private void updateChildModeStatus() {
        if (pcm.isEnabled()) {
            tvChildModeStatus.setText("● Active — monitoring enabled");
            tvChildModeStatus.setTextColor(0xFF4CAF50);
        } else {
            tvChildModeStatus.setText("○ Inactive");
            tvChildModeStatus.setTextColor(0xFF888888);
        }
    }

    private void updateScreenTimeLabel(int minutes) {
        if (minutes == 0) {
            tvScreenTimeValue.setText("No limit");
        } else {
            int h = minutes / 60, m = minutes % 60;
            tvScreenTimeValue.setText(h > 0 ? h + "h " + m + "m" : m + "m");
        }
    }

    private void updateTodayUsage() {
        if (tvTodayUsage == null) return; // extra safety
        int used  = pcm.getTodayUsageMinutes();
        int limit = pcm.getScreenLimitMinutes();
        if (used < 0) {
            tvTodayUsage.setText("Usage permission not granted");
            return;
        }
        int h = used / 60, m = used % 60;
        String usedStr = (h > 0 ? h + "h " : "") + m + "m";
        if (limit > 0) {
            int lh = limit / 60, lm = limit % 60;
            String limitStr = (lh > 0 ? lh + "h " : "") + lm + "m";
            tvTodayUsage.setText("Today: " + usedStr + " of " + limitStr + " used");
        } else {
            tvTodayUsage.setText("Today: " + usedStr + " used");
        }
    }

    private void refreshBedtimeLabels() {
        int[] start = pcm.getBedtimeStart();
        int[] end   = pcm.getBedtimeEnd();
        tvBedtimeStart.setText(String.format(Locale.US, "%02d:%02d", start[0], start[1]));
        tvBedtimeEnd.setText(String.format(Locale.US,   "%02d:%02d", end[0],   end[1]));
    }

    // ── PIN dialogs ───────────────────────────────────────────────────────────

    private void requirePinToEnter() {
        EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        et.setHint("Enter parent PIN");

        new AlertDialog.Builder(this)
                .setTitle("Parental Controls")
                .setMessage("Enter your PIN to access settings")
                .setView(et)
                .setCancelable(false)
                .setPositiveButton("Unlock", (d, w) -> {
                    if (pcm.verifyPin(et.getText().toString().trim())) {
                        initUi();
                    } else {
                        Toast.makeText(this, "Incorrect PIN", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                })
                .setNegativeButton("Cancel", (d, w) -> finish())
                .show();
    }

    private void showSetPinDialog(boolean isChange) {
        View v = getLayoutInflater().inflate(R.layout.dialog_set_pin, null);
        EditText etNew     = v.findViewById(R.id.et_new_pin);
        EditText etConfirm = v.findViewById(R.id.et_confirm_pin);

        new AlertDialog.Builder(this)
                .setTitle(isChange ? "Change PIN" : "Set Parental PIN")
                .setMessage("Choose a 4–8 digit PIN that your child won't know.")
                .setView(v)
                .setCancelable(false)
                .setPositiveButton("Save", (d, w) -> {
                    String p1 = etNew.getText().toString().trim();
                    String p2 = etConfirm.getText().toString().trim();
                    if (p1.length() < 4) {
                        Toast.makeText(this, "PIN must be at least 4 digits", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!p1.equals(p2)) {
                        Toast.makeText(this, "PINs do not match", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    pcm.setPin(p1);
                    Toast.makeText(this, "PIN saved", Toast.LENGTH_SHORT).show();
                    if (!isChange) initUi();
                })
                .setNegativeButton("Cancel", (d, w) -> {
                    if (!isChange) finish();
                })
                .show();
    }

    // ── Permission dialog ─────────────────────────────────────────────────────

    private void showUsagePermissionDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Permission required")
                .setMessage("Parental controls need the \"Usage Access\" permission to monitor "
                        + "which apps are open. Tap Open Settings, then enable access for Souls.")
                .setPositiveButton("Open Settings", (d, w) ->
                        startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)))
                .setNegativeButton("Cancel", null)
                .show();
    }
}