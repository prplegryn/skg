package com.prplegryn.skg;

import android.content.Context;
import android.util.SparseArray;

import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

final class PlayerCoordinator {
    private final Context context;
    private final SparseArray<ExoPlayer> players = new SparseArray<>();
    private final SparseArray<PlayerView> views = new SparseArray<>();
    private List<VideoItem> items = new ArrayList<>();
    private int currentPosition = RecyclerView.NO_POSITION;

    PlayerCoordinator(Context context) {
        this.context = context.getApplicationContext();
    }

    void setItems(List<VideoItem> videos) {
        releaseAll();
        items = new ArrayList<>(videos);
    }

    void attach(int position, PlayerView view) {
        views.put(position, view);
        ExoPlayer player = players.get(position);
        if (player != null) {
            view.setPlayer(player);
        }
    }

    void detach(int position, PlayerView view) {
        PlayerView currentView = views.get(position);
        if (currentView == view) {
            views.remove(position);
        }
        if (view.getPlayer() != null) {
            view.setPlayer(null);
        }
    }

    void play(int position) {
        if (position < 0 || position >= items.size()) {
            return;
        }
        currentPosition = position;
        prepareAround(position);
        for (int i = 0; i < players.size(); i++) {
            int key = players.keyAt(i);
            ExoPlayer player = players.valueAt(i);
            if (key == position) {
                player.setPlayWhenReady(true);
                player.play();
            } else {
                player.setPlayWhenReady(false);
                player.pause();
            }
        }
    }

    void pause() {
        for (int i = 0; i < players.size(); i++) {
            ExoPlayer player = players.valueAt(i);
            player.setPlayWhenReady(false);
            player.pause();
        }
    }

    void releaseAll() {
        for (int i = 0; i < views.size(); i++) {
            PlayerView view = views.valueAt(i);
            view.setPlayer(null);
        }
        for (int i = 0; i < players.size(); i++) {
            players.valueAt(i).release();
        }
        players.clear();
        currentPosition = RecyclerView.NO_POSITION;
    }

    int currentPosition() {
        return currentPosition;
    }

    private void prepareAround(int position) {
        int first = Math.max(0, position - 1);
        int last = Math.min(items.size() - 1, position + 1);

        for (int i = players.size() - 1; i >= 0; i--) {
            int key = players.keyAt(i);
            if (key < first || key > last) {
                PlayerView view = views.get(key);
                if (view != null) {
                    view.setPlayer(null);
                }
                players.valueAt(i).release();
                players.removeAt(i);
            }
        }

        for (int i = first; i <= last; i++) {
            ensurePlayer(i);
        }
    }

    private ExoPlayer ensurePlayer(int position) {
        ExoPlayer existing = players.get(position);
        if (existing != null) {
            return existing;
        }

        ExoPlayer player = new ExoPlayer.Builder(context).build();
        player.setRepeatMode(Player.REPEAT_MODE_ONE);
        player.setMediaItem(MediaItem.fromUri(items.get(position).uri));
        player.prepare();
        players.put(position, player);

        PlayerView view = views.get(position);
        if (view != null) {
            view.setPlayer(player);
        }
        return player;
    }
}
