package com.natantec.tvspecs;

import java.util.ArrayList;
import java.util.List;

/** Construtor simples de listas de {@link Row}. */
final class Rows {
    final List<Row> list = new ArrayList<Row>();

    Rows header(String title) {
        Row r = new Row(Row.HEADER);
        r.label = title;
        list.add(r);
        return this;
    }

    Rows kv(String label, String value) {
        return kv(label, value, Row.NORMAL);
    }

    Rows kv(String label, String value, int level) {
        Row r = new Row(Row.KV);
        r.label = label;
        r.value = (value == null || value.length() == 0) ? "—" : value;
        r.level = level;
        list.add(r);
        return this;
    }

    Rows bar(String label, String value, float pct, int level) {
        Row r = new Row(Row.BAR);
        r.label = label;
        r.value = value;
        r.pct = pct;
        r.level = level;
        list.add(r);
        return this;
    }

    Rows graph(String label, String value, float[] series, float max, int level) {
        Row r = new Row(Row.GRAPH);
        r.label = label;
        r.value = value;
        r.series = series;
        r.graphMax = max;
        r.level = level;
        list.add(r);
        return this;
    }

    Rows sensorHead() {
        Row r = new Row(Row.SENSOR_HEAD);
        r.label = "Sensor";
        r.value = "Atual";
        r.min = "Mín";
        r.max = "Máx";
        r.avg = "Média";
        list.add(r);
        return this;
    }

    Rows sensor(String name, String cur, String min, String max, String avg, int level) {
        Row r = new Row(Row.SENSOR);
        r.label = name;
        r.value = cur;
        r.min = min;
        r.max = max;
        r.avg = avg;
        r.level = level;
        list.add(r);
        return this;
    }

    Rows action(String text, Runnable action) {
        Row r = new Row(Row.ACTION);
        r.label = text;
        r.action = action;
        list.add(r);
        return this;
    }

    Rows note(String text) {
        Row r = new Row(Row.NOTE);
        r.label = text;
        list.add(r);
        return this;
    }
}
