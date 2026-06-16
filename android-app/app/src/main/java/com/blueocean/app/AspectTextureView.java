package com.blueocean.app;

import android.content.Context;
import android.util.AttributeSet;
import android.view.TextureView;

final class AspectTextureView extends TextureView {
    private int ratioWidth;
    private int ratioHeight;

    AspectTextureView(Context context) {
        super(context);
    }

    public AspectTextureView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    void setAspectRatio(int width, int height) {
        if (width <= 0 || height <= 0) {
            ratioWidth = 0;
            ratioHeight = 0;
        } else {
            ratioWidth = width;
            ratioHeight = height;
        }
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(width, height);
    }
}
