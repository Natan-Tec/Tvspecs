package com.natantec.tvspecs;

import android.content.Context;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Informações pesadas, carregadas uma vez em segundo plano (getprop, GPU, codecs, DRM). */
final class Info {
    private Info() {
    }

    static volatile boolean ready = false;
    static volatile Map<String, String> props = new TreeMap<String, String>();
    static volatile String[] gpu = new String[0];
    static volatile List<Row> codecRows = new ArrayList<Row>();

    static String prop(String key) {
        String v = props.get(key);
        return v == null ? "" : v;
    }

    static void load(Context ctx) {
        try {
            props = readProps();
        } catch (Throwable ignored) {
        }
        try {
            String[] g = GpuProbe.probe();
            if (g != null) gpu = g;
        } catch (Throwable ignored) {
        }
        try {
            codecRows = CodecProbe.build(ctx);
        } catch (Throwable t) {
            Rows r = new Rows();
            r.header("Codecs e DRM");
            r.kv("Erro ao consultar", String.valueOf(t));
            codecRows = r.list;
        }
        ready = true;
    }

    /** Lê as propriedades do sistema executando o comando "getprop". */
    private static Map<String, String> readProps() {
        Map<String, String> m = new TreeMap<String, String>();
        Process p = null;
        BufferedReader br = null;
        try {
            p = new ProcessBuilder("getprop").redirectErrorStream(true).start();
            br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            Pattern pat = Pattern.compile("^\\[(.*?)\\]: \\[(.*)\\]$");
            String line;
            while ((line = br.readLine()) != null) {
                Matcher mt = pat.matcher(line);
                if (mt.matches()) m.put(mt.group(1), mt.group(2));
            }
        } catch (Throwable ignored) {
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (Throwable ignored) {
                }
            }
            if (p != null) p.destroy();
        }
        return m;
    }
}
