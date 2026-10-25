package com.natantec.tvspecs;

import android.content.Context;
import android.util.TypedValue;

/** Cores e utilidades de interface. */
final class Ui {
    private Ui() {
    }

    static final int BG = 0xFF0D1218;
    static final int HEAD = 0xFF1B2634;
    static final int HEAD2 = 0xFF222F40;
    static final int ACCENT = 0xFF4FC3F7;
    static final int TEXT = 0xFFE8EEF4;
    static final int MUTED = 0xFF8D9BA9;
    static final int GOOD = 0xFF66BB6A;
    static final int WARN = 0xFFFFB74D;
    static final int BAD = 0xFFEF5350;

    static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics()));
    }

    static int textColor(int level) {
        switch (level) {
            case Row.GOOD:
                return GOOD;
            case Row.WARN:
                return WARN;
            case Row.BAD:
                return BAD;
            default:
                return TEXT;
        }
    }

    static int fillColor(int level) {
        switch (level) {
            case Row.GOOD:
                return GOOD;
            case Row.WARN:
                return WARN;
            case Row.BAD:
                return BAD;
            default:
                return ACCENT;
        }
    }
}
