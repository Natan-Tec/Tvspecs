package com.natantec.tvspecs;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Gráfico de linha com histórico (últimos ~2 minutos). */
final class SparkView extends View {
    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path linePath = new Path();
    private final Path fillPath = new Path();
    private final RectF rect = new RectF();
    private float[] data = new float[0];
    private float max;
    private int color = Ui.ACCENT;

    SparkView(Context c) {
        super(c);
        float d = c.getResources().getDisplayMetrics().density;
        bg.setColor(0x14FFFFFF);
        grid.setColor(0x1AFFFFFF);
        grid.setStyle(Paint.Style.STROKE);
        grid.setStrokeWidth(Math.max(1f, d));
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(2f * d);
        line.setStrokeJoin(Paint.Join.ROUND);
        fill.setStyle(Paint.Style.FILL);
    }

    void set(float[] data, float max, int color) {
        this.data = data == null ? new float[0] : data;
        this.max = max;
        this.color = color;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float corner = 6f * getResources().getDisplayMetrics().density;
        rect.set(0, 0, w, h);
        canvas.drawRoundRect(rect, corner, corner, bg);
        canvas.drawLine(0, h / 2f, w, h / 2f, grid);

        int n = data.length;
        if (n < 2) return;

        float top = max;
        if (top <= 0f) {
            float m = 0f;
            for (float v : data) {
                if (v > m) m = v;
            }
            top = Math.max(1f, m * 1.15f);
        }

        float dx = w / (SensorStore.HIST - 1);
        float pad = 3f * getResources().getDisplayMetrics().density;
        float usable = h - 2 * pad;

        linePath.reset();
        fillPath.reset();
        float firstX = 0f;
        float lastX = 0f;
        for (int i = 0; i < n; i++) {
            float x = w - (n - 1 - i) * dx;
            float v = Math.max(0f, Math.min(top, data[i]));
            float y = pad + usable * (1f - v / top);
            if (i == 0) {
                linePath.moveTo(x, y);
                fillPath.moveTo(x, h);
                fillPath.lineTo(x, y);
                firstX = x;
            } else {
                linePath.lineTo(x, y);
                fillPath.lineTo(x, y);
            }
            lastX = x;
        }
        fillPath.lineTo(lastX, h);
        fillPath.lineTo(firstX, h);
        fillPath.close();

        fill.setColor((color & 0x00FFFFFF) | 0x33000000);
        canvas.drawPath(fillPath, fill);
        line.setColor(color);
        canvas.drawPath(linePath, line);
    }
}
