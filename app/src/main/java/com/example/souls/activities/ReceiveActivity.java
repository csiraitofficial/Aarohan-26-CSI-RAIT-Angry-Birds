package com.example.souls.activities;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.souls.R;
import com.example.souls.utils.SessionManager;

/**
 * ReceiveActivity
 *
 * Displays the user's Soul ID and Block Hash for sharing/verification.
 * Both values come from POST /soul/finalize and are stored in SessionManager.
 *
 * No wallet address — identity only.
 */
public class ReceiveActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receive);

        ImageView ivBack = findViewById(R.id.iv_back);
        ivBack.setOnClickListener(v -> onBackPressed());

        SessionManager session = SessionManager.getInstance(this);

        TextView tvSoulId    = findViewById(R.id.tv_soul_id);
        TextView tvBlockHash = findViewById(R.id.tv_block_hash);

        tvSoulId.setText(session.getSoulId().isEmpty()
                ? "Soul not registered" : session.getSoulId());

        tvBlockHash.setText(session.getBlockHash().isEmpty()
                ? "—" : session.getBlockHash());

        // Copy Soul ID on tap
        tvSoulId.setOnClickListener(v -> copyToClipboard("Soul ID", session.getSoulId()));

        // Copy Block Hash on tap
        tvBlockHash.setOnClickListener(v -> copyToClipboard("Block Hash", session.getBlockHash()));
    }

    private void copyToClipboard(String label, String value) {
        if (value == null || value.isEmpty()) return;
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText(label, value));
        Toast.makeText(this, label + " copied", Toast.LENGTH_SHORT).show();
    }
}