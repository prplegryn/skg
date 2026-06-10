package com.prplegryn.skg;

import android.graphics.Color;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

final class PlayerPagerAdapter extends RecyclerView.Adapter<PlayerPagerAdapter.PageHolder> {
    private final PlayerCoordinator coordinator;
    private final List<VideoItem> items = new ArrayList<>();

    PlayerPagerAdapter(PlayerCoordinator coordinator) {
        this.coordinator = coordinator;
        setHasStableIds(true);
    }

    void setItems(List<VideoItem> videos) {
        items.clear();
        items.addAll(videos);
        notifyDataSetChanged();
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).id;
    }

    @NonNull
    @Override
    public PageHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        FrameLayout page = new FrameLayout(parent.getContext());
        page.setBackgroundColor(Color.BLACK);
        page.setLayoutParams(new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        PlayerView playerView = new PlayerView(parent.getContext());
        playerView.setUseController(false);
        playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_ZOOM);
        playerView.setBackgroundColor(Color.BLACK);
        page.addView(playerView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        return new PageHolder(page, playerView);
    }

    @Override
    public void onBindViewHolder(@NonNull PageHolder holder, int position) {
        if (holder.boundPosition != RecyclerView.NO_POSITION && holder.boundPosition != position) {
            coordinator.detach(holder.boundPosition, holder.playerView);
        }
        holder.boundPosition = position;
        coordinator.attach(position, holder.playerView);
    }

    @Override
    public void onViewRecycled(@NonNull PageHolder holder) {
        if (holder.boundPosition != RecyclerView.NO_POSITION) {
            coordinator.detach(holder.boundPosition, holder.playerView);
            holder.boundPosition = RecyclerView.NO_POSITION;
        }
        super.onViewRecycled(holder);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class PageHolder extends RecyclerView.ViewHolder {
        final PlayerView playerView;
        int boundPosition = RecyclerView.NO_POSITION;

        PageHolder(@NonNull FrameLayout page, PlayerView playerView) {
            super(page);
            this.playerView = playerView;
        }
    }
}
