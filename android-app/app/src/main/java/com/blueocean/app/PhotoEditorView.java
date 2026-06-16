package com.blueocean.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

final class PhotoEditorView extends View {
    static final int MODE_DRAW = 1;
    static final int MODE_ARROW = 2;
    static final int MODE_TEXT = 3;

    private final Bitmap source;
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF imageRect = new RectF();
    private final Matrix imageMatrix = new Matrix();
    private final List<DrawPath> drawPaths = new ArrayList<>();
    private final List<Arrow> arrows = new ArrayList<>();
    private final List<Label> labels = new ArrayList<>();
    private Path currentPath;
    private Arrow currentArrow;
    private int mode = MODE_DRAW;
    private int markupColor = Color.rgb(255, 204, 82);
    private String pendingLabel = "";
    private float lastX;
    private float lastY;

    PhotoEditorView(Context context, Bitmap bitmap) {
        super(context);
        source = bitmap;
        setBackgroundColor(Color.BLACK);

        strokePaint.setColor(markupColor);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        strokePaint.setStrokeJoin(Paint.Join.ROUND);
        strokePaint.setStrokeWidth(9f);

        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(48f);
        textPaint.setShadowLayer(5f, 2f, 2f, Color.BLACK);
    }

    void setMode(int newMode) {
        mode = newMode;
    }

    void setMarkupColor(int color) {
        markupColor = color;
        invalidate();
    }

    void setPendingLabel(String text) {
        pendingLabel = text == null ? "" : text.trim();
    }

    void addLabel(String text) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) {
            return;
        }
        labels.add(new Label(value, getWidth() * 0.12f, getHeight() * 0.20f + labels.size() * 62f, markupColor));
        invalidate();
    }

    void clearMarkup() {
        drawPaths.clear();
        arrows.clear();
        labels.clear();
        currentPath = null;
        currentArrow = null;
        invalidate();
    }

    byte[] exportJpeg() {
        Bitmap out = Bitmap.createBitmap(source.getWidth(), source.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        canvas.drawBitmap(source, 0f, 0f, bitmapPaint);

        canvas.save();
        canvas.concat(viewToBitmapMatrix());
        for (DrawPath path : drawPaths) {
            drawPath(canvas, path);
        }
        for (Arrow arrow : arrows) {
            drawArrow(canvas, arrow);
        }
        for (Label label : labels) {
            drawLabel(canvas, label);
        }
        canvas.restore();

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        out.compress(Bitmap.CompressFormat.JPEG, 92, bytes);
        out.recycle();
        return bytes.toByteArray();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        updateImageMatrix();
        canvas.drawBitmap(source, imageMatrix, bitmapPaint);

        for (DrawPath path : drawPaths) {
            drawPath(canvas, path);
        }
        if (currentPath != null) {
            strokePaint.setColor(markupColor);
            canvas.drawPath(currentPath, strokePaint);
        }
        for (Arrow arrow : arrows) {
            drawArrow(canvas, arrow);
        }
        if (currentArrow != null) {
            drawArrow(canvas, currentArrow);
        }
        for (Label label : labels) {
            drawLabel(canvas, label);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        if (event.getAction() == MotionEvent.ACTION_DOWN && getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(true);
        }
        if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
            if (getParent() != null) {
                getParent().requestDisallowInterceptTouchEvent(false);
            }
        }

        if (mode == MODE_TEXT) {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                addLabelAt(pendingLabel, x, y);
            }
            return true;
        }

        if (mode == MODE_ARROW) {
            handleArrow(event, x, y);
            return true;
        }

        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            currentPath = new Path();
            currentPath.moveTo(x, y);
            lastX = x;
            lastY = y;
            invalidate();
            return true;
        }
        if (event.getAction() == MotionEvent.ACTION_MOVE && currentPath != null) {
            for (int i = 0; i < event.getHistorySize(); i++) {
                addSmoothPoint(event.getHistoricalX(i), event.getHistoricalY(i));
            }
            addSmoothPoint(x, y);
            invalidate();
            return true;
        }
        if (event.getAction() == MotionEvent.ACTION_UP && currentPath != null) {
            addSmoothPoint(x, y);
            drawPaths.add(new DrawPath(currentPath, markupColor));
            currentPath = null;
            invalidate();
            return true;
        }
        return true;
    }

    private void addSmoothPoint(float x, float y) {
        float dx = Math.abs(x - lastX);
        float dy = Math.abs(y - lastY);
        if (dx >= 1f || dy >= 1f) {
            currentPath.quadTo(lastX, lastY, (x + lastX) / 2f, (y + lastY) / 2f);
            lastX = x;
            lastY = y;
        }
    }

    private void addLabelAt(String text, float x, float y) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) {
            return;
        }
        labels.add(new Label(value, x, y, markupColor));
        invalidate();
    }

    private void handleArrow(MotionEvent event, float x, float y) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            currentArrow = new Arrow(x, y, x, y, markupColor);
            invalidate();
            return;
        }
        if (event.getAction() == MotionEvent.ACTION_MOVE && currentArrow != null) {
            currentArrow.endX = x;
            currentArrow.endY = y;
            invalidate();
            return;
        }
        if (event.getAction() == MotionEvent.ACTION_UP && currentArrow != null) {
            currentArrow.endX = x;
            currentArrow.endY = y;
            arrows.add(currentArrow);
            currentArrow = null;
            invalidate();
        }
    }

    private void drawArrow(Canvas canvas, Arrow arrow) {
        strokePaint.setColor(arrow.color);
        canvas.drawLine(arrow.startX, arrow.startY, arrow.endX, arrow.endY, strokePaint);
        double angle = Math.atan2(arrow.endY - arrow.startY, arrow.endX - arrow.startX);
        float length = 34f;
        float leftX = (float) (arrow.endX - length * Math.cos(angle - Math.PI / 6));
        float leftY = (float) (arrow.endY - length * Math.sin(angle - Math.PI / 6));
        float rightX = (float) (arrow.endX - length * Math.cos(angle + Math.PI / 6));
        float rightY = (float) (arrow.endY - length * Math.sin(angle + Math.PI / 6));
        canvas.drawLine(arrow.endX, arrow.endY, leftX, leftY, strokePaint);
        canvas.drawLine(arrow.endX, arrow.endY, rightX, rightY, strokePaint);
    }

    private void drawPath(Canvas canvas, DrawPath path) {
        strokePaint.setColor(path.color);
        canvas.drawPath(path.path, strokePaint);
    }

    private void drawLabel(Canvas canvas, Label label) {
        textPaint.setColor(label.color);
        canvas.drawText(label.text, label.x, label.y, textPaint);
    }

    private void updateImageMatrix() {
        imageMatrix.reset();
        imageRect.set(0, 0, source.getWidth(), source.getHeight());
        RectF viewRect = new RectF(0, 0, getWidth(), getHeight());
        imageMatrix.setRectToRect(imageRect, viewRect, Matrix.ScaleToFit.CENTER);
    }

    private Matrix viewToBitmapMatrix() {
        updateImageMatrix();
        Matrix inverse = new Matrix();
        imageMatrix.invert(inverse);
        return inverse;
    }

    private static final class DrawPath {
        final Path path;
        final int color;

        DrawPath(Path path, int color) {
            this.path = path;
            this.color = color;
        }
    }

    private static final class Arrow {
        final float startX;
        final float startY;
        float endX;
        float endY;
        final int color;

        Arrow(float startX, float startY, float endX, float endY, int color) {
            this.startX = startX;
            this.startY = startY;
            this.endX = endX;
            this.endY = endY;
            this.color = color;
        }
    }

    private static final class Label {
        final String text;
        final float x;
        final float y;
        final int color;

        Label(String text, float x, float y, int color) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.color = color;
        }
    }
}
