package com.prplegryn.skg;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.PathInterpolator;

final class ContainerTransitionView extends View {
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint rectPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF start = new RectF();
    private final RectF end = new RectF();
    private final RectF current = new RectF();
    private final Matrix matrix = new Matrix();
    private Bitmap bitmap;
    private boolean entering;
    private float progress;
    private ValueAnimator animator;

    ContainerTransitionView(Context context) {
        super(context);
        rectPaint.setColor(Color.BLACK);
        setVisibility(GONE);
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    void play(RectF startBounds, RectF endBounds, Bitmap frame, boolean isEntering, Runnable endAction) {
        if (animator != null) {
            animator.cancel();
        }
        start.set(startBounds);
        end.set(endBounds);
        current.set(startBounds);
        bitmap = frame;
        entering = isEntering;
        progress = 0f;
        setVisibility(VISIBLE);

        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(isEntering ? 320L : 240L);
        animator.setInterpolator(new PathInterpolator(0.4f, 0f, 0.2f, 1f));
        animator.addUpdateListener(animation -> {
            progress = (float) animation.getAnimatedValue();
            interpolateBounds(progress);
            invalidate();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                setVisibility(GONE);
                bitmap = null;
                animator = null;
                if (!cancelled && endAction != null) {
                    endAction.run();
                }
            }
        });
        animator.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int scrimAlpha = entering
                ? Math.round(255f * progress)
                : Math.round(255f * (1f - progress));
        if (scrimAlpha > 0) {
            canvas.drawColor(Color.argb(scrimAlpha, 0, 0, 0));
        }

        if (bitmap == null || bitmap.isRecycled()) {
            canvas.drawRect(current, rectPaint);
            return;
        }

        canvas.save();
        canvas.clipRect(current);
        float scale = Math.max(
                current.width() / bitmap.getWidth(),
                current.height() / bitmap.getHeight()
        );
        float dx = current.left + (current.width() - bitmap.getWidth() * scale) * 0.5f;
        float dy = current.top + (current.height() - bitmap.getHeight() * scale) * 0.5f;
        matrix.reset();
        matrix.setScale(scale, scale);
        matrix.postTranslate(dx, dy);
        canvas.drawBitmap(bitmap, matrix, bitmapPaint);
        canvas.restore();
    }

    private void interpolateBounds(float t) {
        current.set(
                lerp(start.left, end.left, t),
                lerp(start.top, end.top, t),
                lerp(start.right, end.right, t),
                lerp(start.bottom, end.bottom, t)
        );
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
