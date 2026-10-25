package com.natantec.tvspecs;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Gera e salva o relatório em texto com todas as informações. */
final class Report {
    private Report() {
    }

    static String text(Pages pages) {
        StringBuilder sb = new StringBuilder();
        sb.append("TV Specs — relatório\n");
        sb.append("Gerado em ").append(new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
                .format(new Date())).append("\n");
        for (int i = 0; i < Pages.REPORT; i++) {
            sb.append("\n==================== ").append(Pages.TITLES[i].toUpperCase(Locale.getDefault()))
                    .append(" ====================\n");
            List<Row> rows = pages.build(i);
            for (Row r : rows) {
                switch (r.type) {
                    case Row.HEADER:
                        sb.append("\n-- ").append(r.label).append(" --\n");
                        break;
                    case Row.KV:
                    case Row.BAR:
                    case Row.GRAPH:
                        sb.append(r.label).append(": ").append(r.value.replace('\n', ' ')).append('\n');
                        break;
                    case Row.SENSOR:
                    case Row.SENSOR_HEAD:
                        sb.append(r.label).append(" | ").append(r.value).append(" | ").append(r.min)
                                .append(" | ").append(r.max).append(" | ").append(r.avg).append('\n');
                        break;
                    case Row.NOTE:
                        sb.append("(").append(r.label).append(")\n");
                        break;
                    default:
                        break;
                }
            }
        }
        return sb.toString();
    }

    /** Salva em Downloads (Android 10+) ou na pasta do app. Retorna uma descrição do local. */
    static String save(Context ctx, String text) throws Exception {
        String name = "TVSpecs_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".txt";
        byte[] data = text.getBytes("UTF-8");

        if (Build.VERSION.SDK_INT >= 29) {
            try {
                ContentValues v = new ContentValues();
                v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                v.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
                v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                Uri uri = ctx.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                if (uri != null) {
                    OutputStream os = ctx.getContentResolver().openOutputStream(uri);
                    if (os != null) {
                        try {
                            os.write(data);
                        } finally {
                            os.close();
                        }
                        return "Download/" + name;
                    }
                }
            } catch (Throwable ignored) {
                // cai para a pasta do app
            }
        }

        File dir = ctx.getExternalFilesDir(null);
        if (dir == null) dir = ctx.getFilesDir();
        File f = new File(dir, name);
        FileOutputStream fos = new FileOutputStream(f);
        try {
            fos.write(data);
        } finally {
            fos.close();
        }
        return f.getAbsolutePath();
    }
}
