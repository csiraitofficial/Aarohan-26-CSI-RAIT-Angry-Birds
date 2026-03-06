package com.example.souls.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.souls.R;
import com.example.souls.models.OnboardingModel;

import java.util.List;

/**
 * ViewPager2 adapter for the 3-page onboarding flow.
 */
public class OnboardingAdapter extends RecyclerView.Adapter<OnboardingAdapter.OnboardingViewHolder> {

    private final List<OnboardingModel> pages;

    public OnboardingAdapter(List<OnboardingModel> pages) {
        this.pages = pages;
    }

    @NonNull
    @Override
    public OnboardingViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_onboarding, parent, false);
        return new OnboardingViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull OnboardingViewHolder holder, int position) {
        OnboardingModel page = pages.get(position);
        holder.tvTitle.setText(page.getTitle());
        holder.tvDescription.setText(page.getDescription());
        holder.ivIllustration.setImageResource(page.getImageRes());
    }

    @Override
    public int getItemCount() {
        return pages.size();
    }

    static class OnboardingViewHolder extends RecyclerView.ViewHolder {
        ImageView ivIllustration;
        TextView  tvTitle;
        TextView  tvDescription;

        OnboardingViewHolder(@NonNull View itemView) {
            super(itemView);
            ivIllustration = itemView.findViewById(R.id.iv_illustration);
            tvTitle        = itemView.findViewById(R.id.tv_title);
            tvDescription  = itemView.findViewById(R.id.tv_description);
        }
    }
}