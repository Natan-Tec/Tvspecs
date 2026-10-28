package com.natantec.tvspecs;

import android.content.Context;
import android.os.Build;

/** Ponto de entrada do futuro overlay. Hoje só informa se o aparelho poderá usá-lo. */
final class OverlayController {
    private OverlayController() {
    }

    /** O overlay será só para celular/tablet (a TV não tem janelas flutuantes). */
    static boolean isSupported(Context c) {
        return !Device.isTv(c) && Build.VERSION.SDK_INT >= 23;
    }

    // Próxima versão: start(Context), stop(Context), canDraw(Context) e o Service com a janela.
}
