package com.prplegryn.skg;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.widget.ImageView;

final class AspectThumbnailView extends ImageView {
    private int videoWidth = 9;
    private int videoHeight = 16;

    AspectThumbnailView(Context context) {
        super(context);
        init();
    }

    AspectThumbnailView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setBackgroundColor(Color.BLACK);
        setScaleType(ScaleType.CENTER_CROP);
    }

    void setVideoSize(int width, int height) {
        if (width > 0 && height > 0 && (width != videoWidth || height != videoHeight)) {
            videoWidth = width;
            videoHeight = height;
            requestLayout();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        if (width <= 0) {
            width = getResources().getDisplayMetrics().widthPixels / 3;
        }
        int height = Math.max(1, Math.round(width * (videoHeight / (float) videoWidth)));
        setMeasuredDimension(width, height);
    }
}
