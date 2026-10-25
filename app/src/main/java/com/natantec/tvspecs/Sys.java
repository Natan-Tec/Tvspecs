package com.natantec.tvspecs;

import android.os.SystemClock;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Leitura de arquivos do sistema e formatadores. */
final class Sys {
    private Sys() {
    }

    // ---------- arquivos ----------

    static String readLine(String path) {
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(path), 512);
            String l = br.readLine();
            return l == null ? null : l.trim();
        } catch (Throwable t) {
            return null;
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    static long readLong(String path, long def) {
        String s = readLine(path);
        if (s == null) return def;
        try {
            return Long.parseLong(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    static List<String> readLines(String path, int max) {
        List<String> out = new ArrayList<String>();
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(path), 8192);
            String l;
            while (out.size() < max && (l = br.readLine()) != null) {
                out.add(l);
            }
        } catch (Throwable ignored) {
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (Throwable ignored) {
                }
            }
        }
        return out;
    }

    // ---------- formatadores ----------

    static String cap(String s) {
        if (s == null || s.length() == 0) return "";
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    static String bytes(double b) {
        Locale l = Locale.getDefault();
        if (b >= 1073741824.0) return String.format(l, "%.2f GB", b / 1073741824.0);
        if (b >= 1048576.0) return String.format(l, "%.0f MB", b / 1048576.0);
        if (b >= 1024.0) return String.format(l, "%.0f KB", b / 1024.0);
        return String.format(l, "%.0f B", b);
    }

    static String uptime() {
        long s = SystemClock.elapsedRealtime() / 1000;
        long d = s / 86400;
        s = s % 86400;
        String hms = String.format(Locale.US, "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
        return d > 0 ? d + "d " + hms : hms;
    }

    static String join(List<String> items, String sep) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(sep);
            sb.append(items.get(i));
        }
        return sb.toString();
    }

    // ---------- zonas térmicas ----------

    static final class Zone {
        final String name;
        final String path;

        Zone(String name, String path) {
            this.name = name;
            this.path = path;
        }
    }

    private static List<Zone> zones;

    /** Zonas térmicas legíveis pelo app (muitas TVs bloqueiam; então a lista pode vir vazia). */
    static synchronized List<Zone> thermalZones() {
        if (zones != null) return zones;
        List<Zone> out = new ArrayList<Zone>();
        File[] fs = new File("/sys/class/thermal").listFiles();
        if (fs != null) {
            Arrays.sort(fs, new Comparator<File>() {
                @Override
                public int compare(File a, File b) {
                    return Integer.compare(number(a.getName()), number(b.getName()));
                }
            });
            List<String> names = new ArrayList<String>();
            List<String> paths = new ArrayList<String>();
            List<String> ids = new ArrayList<String>();
            Map<String, Integer> count = new HashMap<String, Integer>();
            for (File f : fs) {
                String n = f.getName();
                if (!n.startsWith("thermal_zone")) continue;
                String tp = f.getPath() + "/temp";
                if (readLine(tp) == null) continue;
                String type = readLine(f.getPath() + "/type");
                String name = (type == null || type.length() == 0) ? n : type;
                names.add(name);
                paths.add(tp);
                ids.add(n.substring("thermal_zone".length()));
                Integer c = count.get(name);
                count.put(name, c == null ? 1 : c + 1);
            }
            for (int i = 0; i < names.size(); i++) {
                String name = names.get(i);
                Integer c = count.get(name);
                if (c != null && c > 1) name = name + " #" + ids.get(i);
                out.add(new Zone(name, paths.get(i)));
            }
        }
        zones = out;
        return out;
    }

    private static int number(String s) {
        try {
            return Integer.parseInt(s.replaceAll("\\D+", ""));
        } catch (Exception e) {
            return 0;
        }
    }
}
