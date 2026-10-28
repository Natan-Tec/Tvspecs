package com.natantec.tvspecs;

import android.app.UiModeManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;

/** Descobre se o aparelho é uma TV (controle remoto) ou um celular/tablet (toque). */
final class Device {
    private Device() {
    }

    private static Boolean tv;

    static boolean isTv(Context c) {
        if (tv == null) {
            boolean r = false;
            UiModeManager um = (UiModeManager) c.getSystemService(Context.UI_MODE_SERVICE);
            if (um != null && um.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION) {
                r = true;
            }
            PackageManager pm = c.getPackageManager();
            if (!r && pm.hasSystemFeature("android.software.leanback")) r = true;
            if (!r && pm.hasSystemFeature("android.hardware.type.television")) r = true;
            tv = r;
        }
        return tv;
    }
}
