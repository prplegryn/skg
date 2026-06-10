package com.prplegryn.skg;

import android.net.Uri;

final class VideoItem {
    final long id;
    final Uri uri;
    final String path;
    final long sortTime;
    private int width;
    private int height;

    VideoItem(long id, Uri uri, String path, int width, int height, long sortTime) {
        this.id = id;
        this.uri = uri;
        this.path = path == null ? "" : path;
        this.sortTime = sortTime;
        updateSize(width, height);
    }

    synchronized void updateSize(int newWidth, int newHeight) {
        if (newWidth > 0 && newHeight > 0) {
            width = newWidth;
            height = newHeight;
        }
    }

    synchronized int width() {
        return width;
    }

    synchronized int height() {
        return height;
    }

    synchronized boolean hasSize() {
        return width > 0 && height > 0;
    }

    synchronized float heightToWidthRatio() {
        if (width <= 0 || height <= 0) {
            return 16f / 9f;
        }
        return Math.max(0.25f, Math.min(4f, height / (float) width));
    }
}
