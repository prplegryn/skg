package com.prplegryn.skg;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;
import androidx.viewpager2.widget.ViewPager2;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int REQUEST_VIDEO_PERMISSION = 41;
    private final ExecutorService scanExecutor = Executors.newSingleThreadExecutor();
    private final List<VideoItem> videos = new ArrayList<>();

    private FrameLayout root;
    private RecyclerView grid;
    private FrameLayout playerLayer;
    private ViewPager2 pager;
    private ContainerTransitionView transitionView;
    private ThumbnailLoader thumbnailLoader;
    private VideoGridAdapter gridAdapter;
    private PlayerPagerAdapter pagerAdapter;
    private PlayerCoordinator playerCoordinator;
    private StaggeredGridLayoutManager gridLayoutManager;
    private int currentIndex;
    private boolean playerOpen;
    private boolean animating;
    private boolean destroyed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureWindow();

        thumbnailLoader = new ThumbnailLoader(this);
        playerCoordinator = new PlayerCoordinator(this);

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        setContentView(root);

        setupGrid();
        setupPlayer();

        if (hasVideoPermission()) {
            loadVideos();
        } else {
            requestVideoPermission();
        }
    }

    private void setupGrid() {
        grid = new RecyclerView(this);
        grid.setBackgroundColor(Color.BLACK);
        grid.setOverScrollMode(View.OVER_SCROLL_NEVER);
        grid.setItemAnimator(null);
        gridLayoutManager = new StaggeredGridLayoutManager(3, StaggeredGridLayoutManager.VERTICAL);
        gridLayoutManager.setGapStrategy(StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS);
        grid.setLayoutManager(gridLayoutManager);

        gridAdapter = new VideoGridAdapter(thumbnailLoader, this::openPlayer);
        grid.setAdapter(gridAdapter);
        root.addView(grid, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
    }

    private void setupPlayer() {
        playerLayer = new FrameLayout(this);
        playerLayer.setBackgroundColor(Color.BLACK);
        playerLayer.setVisibility(View.GONE);

        pager = new ViewPager2(this);
        pager.setOrientation(ViewPager2.ORIENTATION_VERTICAL);
        pager.setOffscreenPageLimit(1);
        pagerAdapter = new PlayerPagerAdapter(playerCoordinator);
        pager.setAdapter(pagerAdapter);
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                currentIndex = position;
                if (playerLayer.getVisibility() == View.VISIBLE) {
                    playerCoordinator.play(position);
                }
            }
        });
        playerLayer.addView(pager, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        root.addView(playerLayer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        transitionView = new ContainerTransitionView(this);
        root.addView(transitionView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
    }

    private void configureWindow() {
        Window window = getWindow();
        window.setStatusBarColor(Color.BLACK);
        window.setNavigationBarColor(Color.BLACK);
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
        }
        hideSystemBars();
    }

    private void hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                );
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            );
        }
    }

    private boolean hasVideoPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        return checkSelfPermission(readVideoPermission()) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestVideoPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(startupPermissions(), REQUEST_VIDEO_PERMISSION);
        }
    }

    private String readVideoPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return Manifest.permission.READ_MEDIA_VIDEO;
        }
        return Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    private String[] startupPermissions() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            return new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            };
        }
        return new String[]{readVideoPermission()};
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_VIDEO_PERMISSION
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadVideos();
        }
    }

    private void loadVideos() {
        scanExecutor.execute(() -> {
            List<VideoItem> loaded = VideoRepository.load(this);
            runOnUiThread(() -> {
                if (destroyed) {
                    return;
                }
                videos.clear();
                videos.addAll(loaded);
                playerCoordinator.setItems(videos);
                gridAdapter.setItems(videos);
                pagerAdapter.setItems(videos);
            });
        });
    }

    private void openPlayer(int position, View anchor) {
        if (animating || position < 0 || position >= videos.size()) {
            return;
        }

        hideSystemBars();
        animating = true;
        playerOpen = true;
        currentIndex = position;

        VideoItem item = videos.get(position);
        RectF start = boundsInRoot(anchor);
        RectF end = fullBounds();
        Bitmap frame = thumbnailLoader.getCached(item.id);
        if (frame == null) {
            frame = snapshot(anchor);
        }

        playerLayer.setAlpha(0f);
        playerLayer.setVisibility(View.VISIBLE);
        pager.setCurrentItem(position, false);
        Bitmap transitionFrame = frame;
        playerLayer.post(() -> {
            playerCoordinator.play(position);
            transitionView.play(start, end, transitionFrame, true, () -> {
                playerLayer.setAlpha(1f);
                animating = false;
            });
        });
    }

    private void closePlayer() {
        if (!playerOpen || animating) {
            return;
        }
        hideSystemBars();
        animating = true;
        currentIndex = Math.max(0, Math.min(currentIndex, videos.size() - 1));
        playerCoordinator.pause();
        grid.stopScroll();
        gridLayoutManager.scrollToPositionWithOffset(currentIndex, 0);
        grid.post(() -> waitForTargetAndClose(0));
    }

    private void waitForTargetAndClose(int attempt) {
        View target = findGridItemView(currentIndex);
        if (target != null || attempt >= 4) {
            animateClose(target);
        } else {
            grid.postDelayed(() -> waitForTargetAndClose(attempt + 1), 16L);
        }
    }

    private void animateClose(View target) {
        VideoItem item = videos.get(currentIndex);
        RectF start = fullBounds();
        RectF end = target == null ? fallbackGridBounds() : boundsInRoot(target);
        Bitmap frame = thumbnailLoader.getCached(item.id);
        if (frame == null && target != null) {
            frame = snapshot(target);
        }

        playerLayer.setAlpha(0f);
        Bitmap transitionFrame = frame;
        transitionView.play(start, end, transitionFrame, false, () -> {
            playerLayer.setVisibility(View.GONE);
            playerLayer.setAlpha(1f);
            playerCoordinator.releaseAll();
            playerOpen = false;
            animating = false;
            hideSystemBars();
        });
    }

    private View findGridItemView(int position) {
        RecyclerView.ViewHolder holder = grid.findViewHolderForAdapterPosition(position);
        return holder == null ? null : holder.itemView;
    }

    private RectF boundsInRoot(View view) {
        int[] rootLocation = new int[2];
        int[] viewLocation = new int[2];
        root.getLocationOnScreen(rootLocation);
        view.getLocationOnScreen(viewLocation);
        float left = viewLocation[0] - rootLocation[0];
        float top = viewLocation[1] - rootLocation[1];
        return new RectF(left, top, left + view.getWidth(), top + view.getHeight());
    }

    private RectF fullBounds() {
        int width = root.getWidth() > 0 ? root.getWidth() : getResources().getDisplayMetrics().widthPixels;
        int height = root.getHeight() > 0 ? root.getHeight() : getResources().getDisplayMetrics().heightPixels;
        return new RectF(0f, 0f, width, height);
    }

    private RectF fallbackGridBounds() {
        int width = root.getWidth() > 0 ? root.getWidth() : getResources().getDisplayMetrics().widthPixels;
        float cellWidth = width / 3f;
        int column = currentIndex % 3;
        return new RectF(column * cellWidth, 0f, (column + 1) * cellWidth, cellWidth * 16f / 9f);
    }

    private Bitmap snapshot(View view) {
        if (view.getWidth() <= 0 || view.getHeight() <= 0) {
            return null;
        }
        try {
            Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            view.draw(canvas);
            return bitmap;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    @Override
    public void onBackPressed() {
        if (playerOpen) {
            closePlayer();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemBars();
        if (playerOpen && !animating) {
            playerCoordinator.play(currentIndex);
        }
    }

    @Override
    protected void onPause() {
        if (playerOpen) {
            playerCoordinator.pause();
        }
        super.onPause();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemBars();
        }
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        playerCoordinator.releaseAll();
        thumbnailLoader.shutdown();
        scanExecutor.shutdownNow();
        super.onDestroy();
    }
}
