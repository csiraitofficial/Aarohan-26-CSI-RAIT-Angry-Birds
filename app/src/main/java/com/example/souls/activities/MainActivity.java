package com.example.souls.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.souls.R;
import com.example.souls.network.ApiCallback;
import com.example.souls.network.ApiManager;
import com.example.souls.utils.IdentityPdfExporter;
import com.example.souls.utils.SessionManager;

import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {

    private TextView tvName, tvSoulId, tvBlockHash, tvBlockIndex, tvVerifiedAt, tvChainStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvName        = findViewById(R.id.tv_name);
        tvSoulId      = findViewById(R.id.tv_soul_id);
        tvBlockHash   = findViewById(R.id.tv_block_hash);
        tvBlockIndex  = findViewById(R.id.tv_block_index);
        tvVerifiedAt  = findViewById(R.id.tv_verified_at);
        tvChainStatus = findViewById(R.id.tv_chain_status);

        // ── Top bar navigation ────────────────────────────────────────────────
        findViewById(R.id.iv_profile).setOnClickListener(v -> {
            startActivity(new Intent(this, ProfileActivity.class));
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        });

        findViewById(R.id.iv_settings).setOnClickListener(v -> {
            startActivity(new Intent(this, SettingsActivity.class));
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        });

        // ── Soul ID card → full identity screen ───────────────────────────────
        findViewById(R.id.ll_soul_id_card).setOnClickListener(v -> {
            startActivity(new Intent(this, SoulIdActivity.class));
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        });

        // ── Action: Verify a Person ────────────────────────────────────────────
        findViewById(R.id.btn_verify_person).setOnClickListener(v -> {
            startActivity(new Intent(this, SendActivity.class));
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        });

        // ── Action: Share My Soul ID ───────────────────────────────────────────
        findViewById(R.id.btn_share_soul).setOnClickListener(v -> {
            startActivity(new Intent(this, ReceiveActivity.class));
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        });

        // ── Action: Export Identity Certificate ───────────────────────────────
        findViewById(R.id.btn_export_cert).setOnClickListener(v -> {
            SessionManager sm = SessionManager.getInstance(this);
            if (!sm.isRegistered()) {
                Toast.makeText(this,
                        "Complete your Soul registration first.",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            Toast.makeText(this, "Generating certificate…", Toast.LENGTH_SHORT).show();
            IdentityPdfExporter.export(this, sm);
        });

        bindCache();
        refreshFromChain();
        checkChainValidity();
    }

    @Override
    protected void onResume() {
        super.onResume();
        bindCache();
    }

    // ── Bind from local cache ─────────────────────────────────────────────────

    private void bindCache() {
        SessionManager sm = SessionManager.getInstance(this);
        String name = sm.getUserName();
        tvName.setText((name != null && !name.isEmpty()) ? "Welcome, " + name : "Welcome");
        tvSoulId.setText(sm.getSoulId().isEmpty()    ? "Pending…"  : sm.getSoulId());
        tvBlockHash.setText(truncate(sm.getBlockHash()));
        tvBlockIndex.setText(sm.getBlockIndex() >= 0 ? "Block #" + sm.getBlockIndex() : "—");
        tvVerifiedAt.setText(formatDate(sm.getVerifiedAt()));
    }

    // ── Live refresh ──────────────────────────────────────────────────────────

    private void refreshFromChain() {
        String deviceKey = SessionManager.getInstance(this).getDeviceKey();
        ApiManager.get().getSoulByDevice(deviceKey, new ApiCallback() {
            @Override public void onSuccess(JSONObject data) {
                if (!data.optBoolean("ok", false)) return;
                JSONObject block = data.optJSONObject("block");
                if (block == null) return;
                SessionManager.getInstance(MainActivity.this).persistBlock(block);
                bindCache();
            }
            @Override public void onError(String message) { /* silent */ }
        });
    }

    private void checkChainValidity() {
        ApiManager.get().validateChain(new ApiCallback() {
            @Override public void onSuccess(JSONObject data) {
                boolean valid = data.optBoolean("valid", false);
                String  msg   = data.optString("message", valid ? "Chain intact" : "Chain tampered");
                tvChainStatus.setText(msg);
                tvChainStatus.setTextColor(android.graphics.Color.parseColor(
                        valid ? "#4CAF50" : "#FF5555"));
            }
            @Override public void onError(String message) {
                tvChainStatus.setText("Chain status unavailable");
            }
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String truncate(String s) {
        if (s == null || s.isEmpty()) return "—";
        if (s.length() <= 16) return s;
        return s.substring(0, 8) + "…" + s.substring(s.length() - 4);
    }

    private String formatDate(String iso) {
        if (iso == null || iso.isEmpty()) return "—";
        try {
            String clean   = iso.endsWith("Z") ? iso.substring(0, iso.length() - 1) : iso;
            String pattern = clean.contains(".") ? "yyyy-MM-dd'T'HH:mm:ss.SSS"
                    : "yyyy-MM-dd'T'HH:mm:ss";
            java.text.SimpleDateFormat in = new java.text.SimpleDateFormat(
                    pattern, java.util.Locale.US);
            in.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            java.util.Date date = in.parse(clean);
            java.text.SimpleDateFormat out = new java.text.SimpleDateFormat(
                    "dd MMM yyyy, HH:mm 'UTC'", java.util.Locale.US);
            out.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            return out.format(date);
        } catch (Exception e) {
            return iso;
        }
    }
}