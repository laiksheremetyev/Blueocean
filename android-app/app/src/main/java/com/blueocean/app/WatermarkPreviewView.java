package com.blueocean.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

final class WatermarkPreviewView extends View {
    private final SharedPreferences prefs;
    private final String slot;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private Bitmap watermark;
    private boolean cropMode;
    private float lastX;
    private float lastY;
    private float lastDistance;
    private float lastAngle;
    private RectF lastImageRect = new RectF();

    WatermarkPreviewView(Context context, SharedPreferences prefs, String slot) {
        super(context);
        this.prefs = prefs;
        this.slot = slot;
        setBackgroundColor(Color.rgb(8, 18, 30));
    }

    void setWatermark(Bitmap bitmap) {
        watermark = bitmap;
        invalidate();
    }

    void setCropMode(boolean enabled) {
        cropMode = enabled;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        drawPhotoMock(canvas);
        if (watermark == null || watermark.isRecycled()) {
            paint.setColor(Color.rgb(160, 180, 196));
            paint.setTextSize(34f);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("Выберите изображение", getWidth() / 2f, getHeight() / 2f, paint);
            return;
        }
        if (cropMode) {
            drawCropSelection(canvas);
            return;
        }
        RectF dst = watermarkRect(getWidth(), getHeight());
        Rect src = prefs.getBoolean(key("Crop"), false) ? manualCropSource() : new Rect(0, 0, watermark.getWidth(), watermark.getHeight());
        paint.setAlpha(Math.max(0, Math.min(255, prefs.getInt(key("Opacity"), 70) * 255 / 100)));
        canvas.save();
        canvas.rotate(prefs.getFloat(key("Rotation"), 0f), dst.centerX(), dst.centerY());
        canvas.drawBitmap(watermark, src, dst, paint);
        canvas.restore();
        paint.setAlpha(255);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        paint.setColor(cropMode ? Color.argb(230, 255, 210, 80) : Color.argb(170, 255, 255, 255));
        canvas.drawRect(dst, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (watermark == null || watermark.isRecycled()) {
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            lastDistance = 0f;
        }
        if (event.getPointerCount() >= 2) {
            handlePinch(event);
            return true;
        }
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            lastX = event.getX();
            lastY = event.getY();
            return true;
        }
        if (event.getAction() == MotionEvent.ACTION_MOVE) {
            if (cropMode) {
                adjustCrop(event.getX() - lastX, event.getY() - lastY);
                lastX = event.getX();
                lastY = event.getY();
                invalidate();
                return true;
            }
            prefs.edit()
                    .putString(key("Position"), "Свободно")
                    .putFloat(key("FreeX"), event.getX() / Math.max(1f, getWidth()))
                    .putFloat(key("FreeY"), event.getY() / Math.max(1f, getHeight()))
                    .apply();
            invalidate();
            return true;
        }
        return true;
    }

    private void handlePinch(MotionEvent event) {
        float dx = event.getX(1) - event.getX(0);
        float dy = event.getY(1) - event.getY(0);
        float distance = (float) Math.hypot(dx, dy);
        float angle = (float) Math.toDegrees(Math.atan2(dy, dx));
        if (event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN || lastDistance <= 0f) {
            lastDistance = distance;
            lastAngle = angle;
            return;
        }
        float factor = distance / Math.max(1f, lastDistance);
        SharedPreferences.Editor editor = prefs.edit();
        if (cropMode) {
            float cropW = prefs.getFloat(key("CropW"), 0.85f);
            editor.putFloat(key("CropW"), Math.max(0.12f, Math.min(0.98f, cropW * factor)));
        } else {
            int scale = prefs.getInt(key("Scale"), 50);
            float rotation = prefs.getFloat(key("Rotation"), 0f);
            editor.putInt(key("Scale"), Math.max(10, Math.min(160, Math.round(scale * factor))))
                    .putFloat(key("Rotation"), rotation + (angle - lastAngle));
        }
        editor.apply();
        lastDistance = distance;
        lastAngle = angle;
        invalidate();
    }

    private void adjustCrop(float dx, float dy) {
        float x = prefs.getFloat(key("CropX"), 0.5f);
        float y = prefs.getFloat(key("CropY"), 0.5f);
        RectF image = lastImageRect.isEmpty() ? new RectF(0, 0, getWidth(), getHeight()) : lastImageRect;
        prefs.edit()
                .putFloat(key("CropX"), Math.max(0.02f, Math.min(0.98f, x + dx / Math.max(1f, image.width()))))
                .putFloat(key("CropY"), Math.max(0.02f, Math.min(0.98f, y + dy / Math.max(1f, image.height()))))
                .apply();
    }

    private void drawCropSelection(Canvas canvas) {
        RectF image = fitCenterRect(watermark.getWidth(), watermark.getHeight(), getWidth(), getHeight());
        lastImageRect.set(image);
        paint.setAlpha(255);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawBitmap(watermark, null, image, paint);

        RectF crop = cropRectOnView(image);
        paint.setColor(Color.argb(145, 0, 0, 0));
        canvas.drawRect(image.left, image.top, image.right, crop.top, paint);
        canvas.drawRect(image.left, crop.bottom, image.right, image.bottom, paint);
        canvas.drawRect(image.left, crop.top, crop.left, crop.bottom, paint);
        canvas.drawRect(crop.right, crop.top, image.right, crop.bottom, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4f);
        paint.setColor(Color.rgb(255, 213, 74));
        canvas.drawRect(crop, paint);
        paint.setStrokeWidth(1.5f);
        paint.setColor(Color.argb(220, 255, 255, 255));
        canvas.drawLine(crop.left + crop.width() / 3f, crop.top, crop.left + crop.width() / 3f, crop.bottom, paint);
        canvas.drawLine(crop.left + crop.width() * 2f / 3f, crop.top, crop.left + crop.width() * 2f / 3f, crop.bottom, paint);
        canvas.drawLine(crop.left, crop.top + crop.height() / 3f, crop.right, crop.top + crop.height() / 3f, paint);
        canvas.drawLine(crop.left, crop.top + crop.height() * 2f / 3f, crop.right, crop.top + crop.height() * 2f / 3f, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(255, 213, 74));
        canvas.drawCircle(crop.left, crop.top, 10f, paint);
        canvas.drawCircle(crop.right, crop.top, 10f, paint);
        canvas.drawCircle(crop.left, crop.bottom, 10f, paint);
        canvas.drawCircle(crop.right, crop.bottom, 10f, paint);
    }

    private RectF fitCenterRect(int sourceWidth, int sourceHeight, int targetWidth, int targetHeight) {
        float availableW = targetWidth * 0.9f;
        float availableH = targetHeight * 0.9f;
        float scale = Math.min(availableW / Math.max(1, sourceWidth), availableH / Math.max(1, sourceHeight));
        float w = sourceWidth * scale;
        float h = sourceHeight * scale;
        float left = (targetWidth - w) / 2f;
        float top = (targetHeight - h) / 2f;
        return new RectF(left, top, left + w, top + h);
    }

    private RectF cropRectOnView(RectF image) {
        float aspect = prefs.getFloat(key("CropAspect"), 0.32f);
        float cropW = prefs.getFloat(key("CropW"), 0.85f);
        float cropH = Math.max(0.08f, Math.min(0.95f, cropW * aspect));
        float cx = image.left + prefs.getFloat(key("CropX"), 0.5f) * image.width();
        float cy = image.top + prefs.getFloat(key("CropY"), 0.5f) * image.height();
        float w = cropW * image.width();
        float h = cropH * image.height();
        float left = Math.max(image.left, Math.min(image.right - w, cx - w / 2f));
        float top = Math.max(image.top, Math.min(image.bottom - h, cy - h / 2f));
        return new RectF(left, top, left + w, top + h);
    }

    private void drawPhotoMock(Canvas canvas) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(11, 45, 70));
        canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
        paint.setColor(Color.rgb(16, 88, 118));
        canvas.drawCircle(getWidth() * 0.82f, getHeight() * 0.25f, getWidth() * 0.18f, paint);
        paint.setColor(Color.rgb(5, 28, 45));
        canvas.drawRect(0, getHeight() * 0.65f, getWidth(), getHeight(), paint);
        paint.setColor(Color.rgb(36, 130, 160));
        canvas.drawOval(new RectF(-getWidth() * 0.1f, getHeight() * 0.55f, getWidth() * 0.7f, getHeight() * 0.9f), paint);
    }

    private RectF watermarkRect(int width, int height) {
        int scalePercent = Math.max(10, prefs.getInt(key("Scale"), 50));
        float targetWidth = width * (scalePercent / 100f);
        float ratio = targetWidth / Math.max(1, watermark.getWidth());
        float targetHeight = prefs.getBoolean(key("Crop"), false) ? targetWidth * prefs.getFloat(key("CropAspect"), 0.32f) : watermark.getHeight() * ratio;
        String pos = prefs.getString(key("Position"), defaultPosition());
        float margin = Math.max(12f, width * 0.04f);
        float left;
        float top;
        if ("Свободно".equals(pos)) {
            left = prefs.getFloat(key("FreeX"), 0.5f) * width - targetWidth / 2f;
            top = prefs.getFloat(key("FreeY"), 0.5f) * height - targetHeight / 2f;
        } else {
            left = pos.contains("слева") ? margin : pos.contains("центру") ? (width - targetWidth) / 2f : width - targetWidth - margin;
            top = pos.contains("Сверху") ? margin : pos.contains("центру") ? (height - targetHeight) / 2f : height - targetHeight - margin;
        }
        left = Math.max(margin, Math.min(width - targetWidth - margin, left));
        top = Math.max(margin, Math.min(height - targetHeight - margin, top));
        return new RectF(left, top, left + targetWidth, top + targetHeight);
    }

    private Rect manualCropSource() {
        float aspect = prefs.getFloat(key("CropAspect"), 0.32f);
        float cropW = prefs.getFloat(key("CropW"), 0.85f);
        float cropH = Math.max(0.08f, Math.min(0.95f, cropW * aspect));
        float cx = prefs.getFloat(key("CropX"), 0.5f);
        float cy = prefs.getFloat(key("CropY"), 0.5f);
        int left = Math.round((cx - cropW / 2f) * watermark.getWidth());
        int top = Math.round((cy - cropH / 2f) * watermark.getHeight());
        int right = Math.round((cx + cropW / 2f) * watermark.getWidth());
        int bottom = Math.round((cy + cropH / 2f) * watermark.getHeight());
        left = Math.max(0, Math.min(watermark.getWidth() - 1, left));
        top = Math.max(0, Math.min(watermark.getHeight() - 1, top));
        right = Math.max(left + 1, Math.min(watermark.getWidth(), right));
        bottom = Math.max(top + 1, Math.min(watermark.getHeight(), bottom));
        return new Rect(left, top, right, bottom);
    }

    private String key(String suffix) {
        return "watermark" + ("top".equals(slot) ? "Top" : "Bottom") + suffix;
    }

    private String defaultPosition() {
        return "top".equals(slot) ? "Сверху справа" : "Снизу справа";
    }
}
