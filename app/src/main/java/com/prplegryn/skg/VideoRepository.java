package com.prplegryn.skg;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class VideoRepository {
    private static final String DIRECTORY_NAME = "skg";

    private VideoRepository() {
    }

    static List<VideoItem> load(Context context) {
        LinkedHashMap<String, VideoItem> items = new LinkedHashMap<>();
        loadFromMediaStore(context.getApplicationContext(), items);
        loadFromFiles(items);

        ArrayList<VideoItem> result = new ArrayList<>(items.values());
        Collections.sort(result, (left, right) -> Long.compare(right.sortTime, left.sortTime));
        return result;
    }

    private static void loadFromMediaStore(Context context, Map<String, VideoItem> items) {
        ContentResolver resolver = context.getContentResolver();
        Uri collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        String[] projection = new String[]{
                MediaStore.Video.Media._ID,
                MediaStore.MediaColumns.DATA,
                MediaStore.MediaColumns.WIDTH,
                MediaStore.MediaColumns.HEIGHT,
                MediaStore.MediaColumns.DATE_MODIFIED
        };

        String selection;
        String[] args;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            selection = MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ?";
            args = new String[]{DIRECTORY_NAME + "/%"};
        } else {
            selection = MediaStore.MediaColumns.DATA + " LIKE ?";
            args = new String[]{"%/" + DIRECTORY_NAME + "/%"};
        }

        try (Cursor cursor = resolver.query(
                collection,
                projection,
                selection,
                args,
                MediaStore.MediaColumns.DATE_MODIFIED + " DESC"
        )) {
            if (cursor == null) {
                return;
            }

            int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID);
            int dataColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DATA);
            int widthColumn = cursor.getColumnIndex(MediaStore.MediaColumns.WIDTH);
            int heightColumn = cursor.getColumnIndex(MediaStore.MediaColumns.HEIGHT);
            int modifiedColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED);

            while (cursor.moveToNext()) {
                long id = cursor.getLong(idColumn);
                Uri uri = ContentUris.withAppendedId(collection, id);
                String path = dataColumn >= 0 ? cursor.getString(dataColumn) : null;
                int width = widthColumn >= 0 ? cursor.getInt(widthColumn) : 0;
                int height = heightColumn >= 0 ? cursor.getInt(heightColumn) : 0;
                long modified = modifiedColumn >= 0 ? cursor.getLong(modifiedColumn) : 0L;
                String key = path == null || path.isEmpty() ? uri.toString() : path;
                items.put(key, new VideoItem(id, uri, path, width, height, modified));
            }
        } catch (RuntimeException ignored) {
            // Some vendor MediaStore implementations reject deprecated columns in scoped storage.
            loadFromMediaStoreWithoutDataColumn(context, items);
        }
    }

    private static void loadFromMediaStoreWithoutDataColumn(Context context, Map<String, VideoItem> items) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return;
        }

        ContentResolver resolver = context.getContentResolver();
        Uri collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        String[] projection = new String[]{
                MediaStore.Video.Media._ID,
                MediaStore.MediaColumns.WIDTH,
                MediaStore.MediaColumns.HEIGHT,
                MediaStore.MediaColumns.DATE_MODIFIED
        };

        try (Cursor cursor = resolver.query(
                collection,
                projection,
                MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ?",
                new String[]{DIRECTORY_NAME + "/%"},
                MediaStore.MediaColumns.DATE_MODIFIED + " DESC"
        )) {
            if (cursor == null) {
                return;
            }

            int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID);
            int widthColumn = cursor.getColumnIndex(MediaStore.MediaColumns.WIDTH);
            int heightColumn = cursor.getColumnIndex(MediaStore.MediaColumns.HEIGHT);
            int modifiedColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED);

            while (cursor.moveToNext()) {
                long id = cursor.getLong(idColumn);
                Uri uri = ContentUris.withAppendedId(collection, id);
                int width = widthColumn >= 0 ? cursor.getInt(widthColumn) : 0;
                int height = heightColumn >= 0 ? cursor.getInt(heightColumn) : 0;
                long modified = modifiedColumn >= 0 ? cursor.getLong(modifiedColumn) : 0L;
                items.put(uri.toString(), new VideoItem(id, uri, "", width, height, modified));
            }
        } catch (RuntimeException ignored) {
        }
    }

    private static void loadFromFiles(Map<String, VideoItem> items) {
        File root = new File(Environment.getExternalStorageDirectory(), DIRECTORY_NAME);
        if (!root.isDirectory()) {
            return;
        }
        collectFiles(root, items);
    }

    private static void collectFiles(File directory, Map<String, VideoItem> items) {
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                collectFiles(file, items);
            } else if (isVideo(file)) {
                String path = file.getAbsolutePath();
                if (items.containsKey(path)) {
                    continue;
                }
                int[] size = readVideoSize(file);
                long id = stableId(path);
                items.put(path, new VideoItem(id, Uri.fromFile(file), path, size[0], size[1], file.lastModified()));
            }
        }
    }

    private static boolean isVideo(File file) {
        String name = file.getName().toLowerCase(Locale.US);
        return name.endsWith(".mp4")
                || name.endsWith(".m4v")
                || name.endsWith(".mkv")
                || name.endsWith(".webm")
                || name.endsWith(".3gp")
                || name.endsWith(".mov")
                || name.endsWith(".avi")
                || name.endsWith(".ts");
    }

    private static int[] readVideoSize(File file) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(file.getAbsolutePath());
            return readSize(retriever);
        } catch (RuntimeException ignored) {
            return new int[]{0, 0};
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {
            }
        }
    }

    static int[] readSize(MediaMetadataRetriever retriever) {
        int width = parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
        int height = parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
        int rotation = parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION));
        if ((rotation == 90 || rotation == 270) && width > 0 && height > 0) {
            int oldWidth = width;
            width = height;
            height = oldWidth;
        }
        return new int[]{width, height};
    }

    private static int parseInt(String value) {
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static long stableId(String value) {
        long hash = 1125899906842597L;
        for (int i = 0; i < value.length(); i++) {
            hash = 31L * hash + value.charAt(i);
        }
        return hash;
    }
}
