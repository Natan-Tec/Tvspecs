package com.natantec.tvspecs;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/** Guarda valor atual, mínimo, máximo, média e histórico de cada sensor (estilo HWiNFO). */
final class SensorStore {
    static final int HIST = 120;

    /** Cópia imutável de um sensor, segura para ler na thread da interface. */
    static final class Snap {
        final String key;
        final String group;
        final String name;
        final String unit;
        final int decimals;
        final double cur;
        final double min;
        final double max;
        final double avg;
        final float[] series;

        Snap(String key, String group, String name, String unit, int decimals,
             double cur, double min, double max, double avg, float[] series) {
            this.key = key;
            this.group = group;
            this.name = name;
            this.unit = unit;
            this.decimals = decimals;
            this.cur = cur;
            this.min = min;
            this.max = max;
            this.avg = avg;
            this.series = series;
        }

        String fmt(double v) {
            return String.format(Locale.getDefault(), "%." + decimals + "f", v);
        }

        String curText() {
            return fmt(cur) + (unit.length() == 0 ? "" : " " + unit);
        }
    }

    private static final class Stat {
        final String key;
        final String group;
        final String name;
        final String unit;
        final int decimals;
        double cur;
        double min;
        double max;
        double sum;
        long n;
        final float[] hist = new float[HIST];
        int written;

        Stat(String key, String group, String name, String unit, int decimals) {
            this.key = key;
            this.group = group;
            this.name = name;
            this.unit = unit;
            this.decimals = decimals;
        }

        Snap snap() {
            int count = Math.min(written, HIST);
            float[] s = new float[count];
            int start = written > HIST ? written % HIST : 0;
            for (int i = 0; i < count; i++) {
                s[i] = hist[(start + i) % HIST];
            }
            return new Snap(key, group, name, unit, decimals, cur, min, max,
                    n > 0 ? sum / n : 0, s);
        }
    }

    private final LinkedHashMap<String, Stat> map = new LinkedHashMap<String, Stat>();
    private long startMs = System.currentTimeMillis();

    synchronized void put(String key, String group, String name, String unit, int decimals, double v) {
        Stat s = map.get(key);
        if (s == null) {
            s = new Stat(key, group, name, unit, decimals);
            s.min = v;
            s.max = v;
            map.put(key, s);
        }
        s.cur = v;
        if (v < s.min) s.min = v;
        if (v > s.max) s.max = v;
        s.sum += v;
        s.n++;
        s.hist[s.written % HIST] = (float) v;
        s.written++;
    }

    synchronized Snap snap(String key) {
        Stat s = map.get(key);
        return s == null ? null : s.snap();
    }

    synchronized List<Snap> group(String group) {
        List<Snap> out = new ArrayList<Snap>();
        for (Stat s : map.values()) {
            if (s.group.equals(group)) out.add(s.snap());
        }
        return out;
    }

    synchronized List<Snap> withPrefix(String prefix) {
        List<Snap> out = new ArrayList<Snap>();
        for (Stat s : map.values()) {
            if (s.key.startsWith(prefix)) out.add(s.snap());
        }
        return out;
    }

    synchronized void reset() {
        map.clear();
        startMs = System.currentTimeMillis();
    }

    synchronized long elapsedSec() {
        return (System.currentTimeMillis() - startMs) / 1000;
    }
}
