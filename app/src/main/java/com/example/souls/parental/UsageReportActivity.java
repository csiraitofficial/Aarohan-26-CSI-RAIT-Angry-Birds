package com.example.souls.parental;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.souls.R;

import java.util.List;

/**
 * UsageReportActivity
 *
 * Shows the parent today's per-app screen-time breakdown, plus the
 * total minutes used vs the daily limit (if set).
 *
 * Data is loaded off the main thread using ParentalControlManager
 * .getPerAppUsageToday(), which queries UsageStatsManager.
 *
 * Layout: dark card list. Each row has the app icon, name, formatted
 * time, and a progress bar relative to the longest session.
 */
public class UsageReportActivity extends AppCompatActivity {

    private RecyclerView    rvUsage;
    private TextView        tvTotalUsage;
    private TextView        tvLimitSummary;
    private ProgressBar     progressLoading;
    private ParentalControlManager pcm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_usage_report);

        pcm = ParentalControlManager.getInstance(this);

        findViewById(R.id.iv_back).setOnClickListener(v -> onBackPressed());

        rvUsage        = findViewById(R.id.rv_usage);
        tvTotalUsage   = findViewById(R.id.tv_total_usage);
        tvLimitSummary = findViewById(R.id.tv_limit_summary);
        progressLoading = findViewById(R.id.progress_loading);

        rvUsage.setLayoutManager(new LinearLayoutManager(this));

        loadData();
    }

    private void loadData() {
        progressLoading.setVisibility(View.VISIBLE);
        rvUsage.setVisibility(View.GONE);

        new Thread(() -> {
            List<ParentalControlManager.AppUsageStat> stats = pcm.getPerAppUsageToday();
            int totalMin = pcm.getTodayUsageMinutes();
            int limitMin = pcm.getScreenLimitMinutes();

            runOnUiThread(() -> {
                progressLoading.setVisibility(View.GONE);
                rvUsage.setVisibility(View.VISIBLE);

                // Total
                if (totalMin < 0) {
                    tvTotalUsage.setText("Usage permission not granted");
                } else {
                    int h = totalMin / 60, m = totalMin % 60;
                    tvTotalUsage.setText("Total today: " + (h > 0 ? h + "h " : "") + m + "m");
                }

                // Limit
                if (limitMin > 0) {
                    int lh = limitMin / 60, lm = limitMin % 60;
                    tvLimitSummary.setText("Daily limit: " + (lh > 0 ? lh + "h " : "") + lm + "m");
                    tvLimitSummary.setVisibility(View.VISIBLE);
                } else {
                    tvLimitSummary.setVisibility(View.GONE);
                }

                // App list
                long maxMs = stats.isEmpty() ? 1 : stats.get(0).totalMs;
                rvUsage.setAdapter(new UsageAdapter(stats, maxMs));
            });
        }, "UsageLoader").start();
    }

    // ── Adapter ───────────────────────────────────────────────────────────────

    private class UsageAdapter extends RecyclerView.Adapter<UsageAdapter.Holder> {

        private final List<ParentalControlManager.AppUsageStat> data;
        private final long maxMs;

        UsageAdapter(List<ParentalControlManager.AppUsageStat> data, long maxMs) {
            this.data  = data;
            this.maxMs = maxMs;
        }

        @Override
        public int getItemCount() { return data.size(); }

        @Override
        public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_usage_stat, parent, false);
            return new Holder(v);
        }

        @Override
        public void onBindViewHolder(Holder h, int pos) {
            ParentalControlManager.AppUsageStat stat = data.get(pos);
            h.tvLabel.setText(stat.appLabel);
            h.tvTime.setText(stat.formattedTime());
            h.progress.setProgress((int) ((stat.totalMs * 100) / maxMs));

            // App icon
            try {
                PackageManager pm = getPackageManager();
                Drawable icon = pm.getApplicationIcon(stat.packageName);
                h.ivIcon.setImageDrawable(icon);
            } catch (PackageManager.NameNotFoundException e) {
                h.ivIcon.setImageDrawable(getPackageManager().getDefaultActivityIcon());
            }

            // Blocked badge
            boolean blocked = pcm.isAppBlocked(stat.packageName);
            h.tvBlockedBadge.setVisibility(blocked ? View.VISIBLE : View.GONE);
        }

        class Holder extends RecyclerView.ViewHolder {
            ImageView  ivIcon;
            TextView   tvLabel, tvTime, tvBlockedBadge;
            ProgressBar progress;

            Holder(View v) {
                super(v);
                ivIcon        = v.findViewById(R.id.iv_app_icon);
                tvLabel       = v.findViewById(R.id.tv_app_label);
                tvTime        = v.findViewById(R.id.tv_usage_time);
                tvBlockedBadge = v.findViewById(R.id.tv_blocked_badge);
                progress      = v.findViewById(R.id.progress_usage);
            }
        }
    }
}