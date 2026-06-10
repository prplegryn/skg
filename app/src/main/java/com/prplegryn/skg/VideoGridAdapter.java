package com.prplegryn.skg;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

final class VideoGridAdapter extends RecyclerView.Adapter<VideoGridAdapter.VideoHolder> {
    interface OnVideoClickListener {
        void onVideoClick(int position, View view);
    }

    private final ThumbnailLoader thumbnailLoader;
    private final OnVideoClickListener clickListener;
    private final List<VideoItem> items = new ArrayList<>();

    VideoGridAdapter(ThumbnailLoader thumbnailLoader, OnVideoClickListener clickListener) {
        this.thumbnailLoader = thumbnailLoader;
        this.clickListener = clickListener;
        setHasStableIds(true);
    }

    void setItems(List<VideoItem> videos) {
        items.clear();
        items.addAll(videos);
        notifyDataSetChanged();
    }

    VideoItem getItem(int position) {
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).id;
    }

    @NonNull
    @Override
    public VideoHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        AspectThumbnailView image = new AspectThumbnailView(parent.getContext());
        image.setLayoutParams(new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return new VideoHolder(image);
    }

    @Override
    public void onBindViewHolder(@NonNull VideoHolder holder, int position) {
        VideoItem item = items.get(position);
        holder.image.setTag(item.id);
        holder.image.setVideoSize(item.width(), item.height());
        holder.image.setBackgroundColor(Color.BLACK);

        Bitmap cached = thumbnailLoader.getCached(item.id);
        if (cached != null) {
            holder.image.setImageBitmap(cached);
        } else {
            holder.image.setImageDrawable(null);
            int targetWidth = holder.image.getWidth();
            if (targetWidth <= 0) {
                targetWidth = holder.image.getResources().getDisplayMetrics().widthPixels / 3;
            }
            thumbnailLoader.load(item, targetWidth, (loadedItem, bitmap) -> {
                Object tag = holder.image.getTag();
                if (!(tag instanceof Long) || ((Long) tag) != loadedItem.id) {
                    return;
                }
                if (loadedItem.hasSize()) {
                    holder.image.setVideoSize(loadedItem.width(), loadedItem.height());
                }
                if (bitmap != null) {
                    holder.image.setImageBitmap(bitmap);
                }
            });
        }

        holder.image.setOnClickListener(view -> {
            int adapterPosition = holder.getBindingAdapterPosition();
            if (adapterPosition != RecyclerView.NO_POSITION) {
                clickListener.onVideoClick(adapterPosition, view);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class VideoHolder extends RecyclerView.ViewHolder {
        final AspectThumbnailView image;

        VideoHolder(@NonNull AspectThumbnailView image) {
            super(image);
            this.image = image;
        }
    }
}
