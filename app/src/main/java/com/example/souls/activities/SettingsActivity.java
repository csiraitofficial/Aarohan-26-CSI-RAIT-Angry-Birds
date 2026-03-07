package com.example.souls.activities;

import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.example.souls.R;
import com.example.souls.vpn.FirewallMonitorService;
import com.example.souls.network.ApiCallback;
import com.example.souls.network.ApiManager;
import com.example.souls.utils.SessionManager;

import org.json.JSONObject;

public class SettingsActivity extends AppCompatActivity {

    private static final int REQUEST_VPN_PERMISSION = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // ── Back ─────────────────────────────────────────────────────────────
        ImageView ivBack = findViewById(R.id.iv_back);
        ivBack.setOnClickListener(v -> onBackPressed());

        // ── Firewall toggle ──────────────────────────────────────────────────
        SwitchCompat switchFirewall = findViewById(R.id.switch_firewall);
        switchFirewall.setChecked(SessionManager.getInstance(this).isFirewallEnabled());

        switchFirewall.setOnCheckedChangeListener((btn, checked) -> {
            SessionManager.getInstance(this).setFirewallEnabled(checked);
            if (checked) {
                Intent vpnIntent = VpnService.prepare(this);
                if (vpnIntent != null) {
                    startActivityForResult(vpnIntent, REQUEST_VPN_PERMISSION);
                } else {
                    startFirewall();
                }
            } else {
                stopService(new Intent(this, FirewallMonitorService.class));
            }
        });

        // ── Chain status ─────────────────────────────────────────────────────
        TextView tvChainStatus = findViewById(R.id.tv_chain_status);
        ApiManager.get().validateChain(new ApiCallback() {
            @Override
            public void onSuccess(JSONObject data) {
                boolean valid = data.optBoolean("valid", false);
                tvChainStatus.setText(valid ? "✓ Chain intact" : "⚠ Chain tampered!");
                tvChainStatus.setTextColor(
                        getColor(valid ? R.color.souls_success : R.color.souls_error));
            }

            @Override
            public void onError(String message) {
                tvChainStatus.setText("Offline");
            }
        });

        // ── Profile row ──────────────────────────────────────────────────────
        LinearLayout llProfile = findViewById(R.id.ll_profile);
        llProfile.setOnClickListener(v -> {
            startActivity(new Intent(this, ProfileActivity.class));
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        });

        // ── Soul ID row ──────────────────────────────────────────────────────
        LinearLayout llSoulId = findViewById(R.id.ll_soul_id);
        llSoulId.setOnClickListener(v -> {
            startActivity(new Intent(this, SoulIdActivity.class));
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        });
    }

    // ── VPN permission result ────────────────────────────────────────────────

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_VPN_PERMISSION && resultCode == RESULT_OK) {
            startFirewall();
        } else if (requestCode == REQUEST_VPN_PERMISSION) {
            SwitchCompat sw = findViewById(R.id.switch_firewall);
            if (sw != null) sw.setChecked(false);
            SessionManager.getInstance(this).setFirewallEnabled(false);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void startFirewall() {
        Intent intent = new Intent(this, FirewallMonitorService.class);
        startForegroundService(intent);
    }
}