package com.natantec.tvspecs;

/** Uma linha da lista de informações (cabeçalho, chave/valor, barra, sensor, gráfico...). */
final class Row {
    static final int HEADER = 0;
    static final int KV = 1;
    static final int BAR = 2;
    static final int SENSOR = 3;
    static final int GRAPH = 4;
    static final int SENSOR_HEAD = 5;
    static final int ACTION = 6;
    static final int NOTE = 7;
    static final int TYPES = 8;

    static final int NORMAL = 0;
    static final int GOOD = 1;
    static final int WARN = 2;
    static final int BAD = 3;

    final int type;
    String label = "";
    String value = "";
    String min = "";
    String max = "";
    String avg = "";
    float pct;
    float[] series;
    float graphMax;
    int level;
    Runnable action;

    Row(int type) {
        this.type = type;
    }
}
