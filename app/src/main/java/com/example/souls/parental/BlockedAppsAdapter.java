package com.example.souls.parental;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.souls.R;

import java.util.ArrayList;
import java.util.List;

/**
 * BlockedAppsAdapter
 *
 * Populates a RecyclerView with all user-installed apps.
 * Each row shows the app icon, name, package name, and a toggle switch.
 * Toggling on adds the package to ParentalControlManager's blocked list;
 * toggling off removes it.
 *
 * App enumeration is done off the main thread to avoid jank. While loading,
 * the RecyclerView shows a single "Loading apps…" placeholder row.
 */
public class BlockedAppsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_LOADING = 0;
    private static final int TYPE_APP     = 1;

    private final Context                context;
    private final PackageManager         pm;
    private final ParentalControlManager pcm;
    private       List<AppItem>          apps = new ArrayList<>();
    private       boolean                loading = true;

    public BlockedAppsAdapter(Context ctx, ParentalControlManager pcm) {
        this.context = ctx;
        this.pm      = ctx.getPackageManager();
        this.pcm     = pcm;
        loadAppsAsync();
    }

    // ── Loading ───────────────────────────────────────────────────────────────

    private void loadAppsAsync() {
        new Thread(() -> {
            List<AppItem> items = new ArrayList<>();
            List<ApplicationInfo> installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
            String selfPkg = context.getPackageName();

            for (ApplicationInfo ai : installedApps) {
                // Only show user-installed apps (exclude system apps)
                if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
                if (ai.packageName.equals(selfPkg)) continue;

                String label = pm.getApplicationLabel(ai).toString();
                Drawable icon;
                try { icon = pm.getApplicationIcon(ai.packageName); }
                catch (PackageManager.NameNotFoundException e) { icon = pm.getDefaultActivityIcon(); }

                items.add(new AppItem(ai.packageName, label, icon, pcm.isAppBlocked(ai.packageName)));
            }

            // Sort: blocked first, then alphabetical
            items.sort((a, b) -> {
                if (a.blocked != b.blocked) return a.blocked ? -1 : 1;
                return a.label.compareToIgnoreCase(b.label);
            });

            // Post to main thread
            android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
            main.post(() -> {
                apps    = items;
                loading = false;
                notifyDataSetChanged();
            });
        }, "AppLoader").start();
    }

    // ── RecyclerView ──────────────────────────────────────────────────────────

    @Override
    public int getItemViewType(int position) {
        return loading ? TYPE_LOADING : TYPE_APP;
    }

    @Override
    public int getItemCount() {
        return loading ? 1 : apps.size();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(context);
        if (viewType == TYPE_LOADING) {
            View v = inf.inflate(R.layout.item_loading, parent, false);
            return new LoadingHolder(v);
        }
        View v = inf.inflate(R.layout.item_blocked_app, parent, false);
        return new AppHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof AppHolder) {
            AppItem item = apps.get(position);
            AppHolder h  = (AppHolder) holder;

            h.ivIcon.setImageDrawable(item.icon);
            h.tvLabel.setText(item.label);
            h.tvPackage.setText(item.packageName);

            // Detach listener before setting state
            h.switchBlock.setOnCheckedChangeListener(null);
            h.switchBlock.setChecked(item.blocked);

            h.switchBlock.setOnCheckedChangeListener((btn, checked) -> {
                item.blocked = checked;
                if (checked) pcm.blockApp(item.packageName);
                else pcm.unblockApp(item.packageName);
            });
        }
    }

    // ── ViewHolders ───────────────────────────────────────────────────────────

    static class AppHolder extends RecyclerView.ViewHolder {
        ImageView   ivIcon;
        TextView    tvLabel, tvPackage;
        SwitchCompat switchBlock;

        AppHolder(View v) {
            super(v);
            ivIcon      = v.findViewById(R.id.iv_app_icon);
            tvLabel     = v.findViewById(R.id.tv_app_label);
            tvPackage   = v.findViewById(R.id.tv_app_package);
            switchBlock = v.findViewById(R.id.switch_block_app);
        }
    }

    static class LoadingHolder extends RecyclerView.ViewHolder {
        LoadingHolder(View v) { super(v); }
    }

    // ── Data class ────────────────────────────────────────────────────────────

    static class AppItem {
        String   packageName;
        String   label;
        Drawable icon;
        boolean  blocked;

        AppItem(String pkg, String label, Drawable icon, boolean blocked) {
            this.packageName = pkg;
            this.label       = label;
            this.icon        = icon;
            this.blocked     = blocked;
        }
    }
}