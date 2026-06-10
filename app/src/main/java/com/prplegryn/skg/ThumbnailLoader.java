package com.prplegryn.skg;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class ThumbnailLoader {
    interface Callback {
        void onThumbnailLoaded(VideoItem item, Bitmap bitmap);
    }

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final LruCache<Long, Bitmap> cache;

    ThumbnailLoader(Context context) {
        this.context = context.getApplicationContext();
        int maxKb = (int) (Runtime.getRuntime().maxMemory() / 1024L);
        cache = new LruCache<Long, Bitmap>(Math.max(4096, maxKb / 8)) {
            @Override
            protected int sizeOf(Long key, Bitmap value) {
                return Math.max(1, value.getByteCount() / 1024);
            }
        };
    }

    Bitmap getCached(long id) {
        return cache.get(id);
    }

    void load(VideoItem item, int targetWidth, Callback callback) {
        Bitmap cached = cache.get(item.id);
        if (cached != null) {
            callback.onThumbnailLoaded(item, cached);
            return;
        }

        executor.execute(() -> {
            Bitmap bitmap = createFirstFrame(item, Math.max(240, targetWidth));
            if (bitmap != null) {
                cache.put(item.id, bitmap);
            }
            mainHandler.post(() -> callback.onThumbnailLoaded(item, bitmap));
        });
    }

    void shutdown() {
        executor.shutdownNow();
    }

    private Bitmap createFirstFrame(VideoItem item, int targetWidth) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            setDataSource(retriever, item.uri);
            if (!item.hasSize()) {
                int[] size = VideoRepository.readSize(retriever);
                item.updateSize(size[0], size[1]);
            }

            int targetHeight = Math.max(1, Math.round(targetWidth * item.heightToWidthRatio()));
            Bitmap bitmap = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                bitmap = retriever.getScaledFrameAtTime(
                        0L,
                        MediaMetadataRetriever.OPTION_CLOSEST,
                        targetWidth,
                        targetHeight
                );
            }
            if (bitmap == null) {
                bitmap = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST);
            }
            if (bitmap == null) {
                bitmap = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            }
            if (bitmap == null) {
                return null;
            }
            if (bitmap.getWidth() == targetWidth) {
                return bitmap;
            }
            return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true);
        } catch (RuntimeException ignored) {
            return null;
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {
            }
        }
    }

    private void setDataSource(MediaMetadataRetriever retriever, Uri uri) {
        if ("file".equals(uri.getScheme())) {
            retriever.setDataSource(uri.getPath());
        } else {
            retriever.setDataSource(context, uri);
        }
    }
}
