package com.natantec.tvspecs;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Barra de progresso fina (uso de RAM, armazenamento, frequência...). */
final class BarView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private float pct;
    private int color = Ui.ACCENT;

    BarView(Context c) {
        super(c);
    }

    void set(float pct, int color) {
        this.pct = Math.max(0f, Math.min(1f, pct));
        this.color = color;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float r = h / 2f;
        paint.setColor(0x26FFFFFF);
        rect.set(0, 0, w, h);
        canvas.drawRoundRect(rect, r, r, paint);
        if (pct > 0.001f) {
            paint.setColor(color);
            rect.set(0, 0, Math.max(h, w * pct), h);
            canvas.drawRoundRect(rect, r, r, paint);
        }
    }
}
