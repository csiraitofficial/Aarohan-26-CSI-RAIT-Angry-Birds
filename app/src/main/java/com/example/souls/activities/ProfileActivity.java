package com.example.souls.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.souls.R;
import com.example.souls.utils.SessionManager;

/**
 * ProfileActivity
 *
 * Displays the user's Soul profile using data aligned with API fields:
 *   • Name        — local only (not an API field)
 *   • Soul ID     — from /soul/finalize → soulId
 *   • Soul Hash   — from /soul/finalize → soulHash
 *   • Block Hash  — from /soul/finalize → blockHash
 *   • Block Index — from /soul/finalize → blockIndex
 *   • Verified At — from /session/:id   → verifiedAt
 *   • Device Key  — generated locally by DeviceKeyManager
 *   • LAMP ID     — from /session/start → lampId
 *
 * No phone number, email, or password fields — these are not in the API.
 */
public class ProfileActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        SessionManager sm = SessionManager.getInstance(this);

        ImageView ivBack = findViewById(R.id.iv_back);
        ivBack.setOnClickListener(v -> onBackPressed());

        // Name (local only)
        TextView tvName = findViewById(R.id.tv_name);
        tvName.setText(sm.getUserName().isEmpty() ? "—" : sm.getUserName());

        // Soul ID — tappable → full Soul ID screen
        TextView tvSoulId = findViewById(R.id.tv_soul_id);
        tvSoulId.setText(sm.getSoulId().isEmpty() ? "Pending" : sm.getSoulId());
        tvSoulId.setOnClickListener(v -> {
            startActivity(new Intent(this, SoulIdActivity.class));
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        });

        // Soul Hash
        TextView tvSoulHash = findViewById(R.id.tv_soul_hash);
        tvSoulHash.setText(truncate(sm.getSoulHash()));

        // Block Hash
        TextView tvBlockHash = findViewById(R.id.tv_block_hash);
        tvBlockHash.setText(truncate(sm.getBlockHash()));

        // Block Index
        TextView tvBlockIndex = findViewById(R.id.tv_block_index);
        tvBlockIndex.setText(sm.getBlockIndex() >= 0 ? "Block #" + sm.getBlockIndex() : "—");

        // Verified At
        TextView tvVerifiedAt = findViewById(R.id.tv_verified_at);
        tvVerifiedAt.setText(sm.getVerifiedAt().isEmpty() ? "—" : sm.getVerifiedAt());

        // Device Key (truncated for display)
        TextView tvDeviceKey = findViewById(R.id.tv_device_key);
        tvDeviceKey.setText(truncate(sm.getDeviceKey()));

        // LAMP ID used during registration
        TextView tvLampId = findViewById(R.id.tv_lamp_id);
        tvLampId.setText(sm.getLampId().isEmpty() ? "—" : sm.getLampId());
    }

    private String truncate(String s) {
        if (s == null || s.isEmpty()) return "—";
        if (s.length() <= 16) return s;
        return s.substring(0, 8) + "…" + s.substring(s.length() - 4);
    }
}