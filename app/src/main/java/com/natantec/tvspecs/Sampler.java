package com.natantec.tvspecs;

import android.app.ActivityManager;
import android.content.Context;
import android.net.TrafficStats;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.PowerManager;
import android.os.SystemClock;

import java.util.List;

/** Coleta os sensores uma vez por segundo numa thread separada. */
final class Sampler {
    interface Listener {
        void onSampled();
    }

    private final Context ctx;
    private final SensorStore store;
    private final Listener listener;
    private final int cores = Runtime.getRuntime().availableProcessors();

    private volatile boolean running;
    private HandlerThread thread;
    private volatile Handler handler;

    private long lastRx = -1;
    private long lastTx = -1;
    private long lastNetT;
    private long[] lastCpu;
    private boolean procStatOk = true;
    private long lastProcCpu = -1;
    private long lastProcT;

    private ActivityManager am;
    private WifiManager wm;
    private PowerManager pm;

    Sampler(Context c, SensorStore store, Listener listener) {
        this.ctx = c.getApplicationContext();
        this.store = store;
        this.listener = listener;
    }

    synchronized void start() {
        if (running) return;
        running = true;
        am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        wm = (WifiManager) ctx.getSystemService(Context.WIFI_SERVICE);
        pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
        thread = new HandlerThread("tvspecs-sampler");
        thread.start();
        handler = new Handler(thread.getLooper());
        handler.post(tick);
    }

    synchronized void stop() {
        running = false;
        Handler h = handler;
        if (h != null) h.removeCallbacksAndMessages(null);
        handler = null;
        if (thread != null) {
            thread.quitSafely();
            thread = null;
        }
        // zera os contadores incrementais para não gerar picos ao voltar
        lastRx = -1;
        lastTx = -1;
        lastCpu = null;
        lastProcCpu = -1;
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            try {
                sample();
            } catch (Throwable ignored) {
            }
            listener.onSampled();
            Handler h = handler;
            if (h != null && running) h.postDelayed(this, 1000);
        }
    };

    private void sample() {
        long now = SystemClock.elapsedRealtime();

        // ---- Memória ----
        if (am != null) {
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            double total = mi.totalMem / 1048576.0;
            double avail = mi.availMem / 1048576.0;
            double used = total - avail;
            if (total > 0) {
                store.put("ram_pct", "Memória", "RAM em uso [%]", "%", 1, used * 100.0 / total);
                store.put("ram_used", "Memória", "RAM em uso [MB]", "MB", 0, used);
                store.put("ram_free", "Memória", "RAM disponível [MB]", "MB", 0, avail);
            }
        }

        // ---- Rede ----
        long rx = TrafficStats.getTotalRxBytes();
        long tx = TrafficStats.getTotalTxBytes();
        if (rx != TrafficStats.UNSUPPORTED && tx != TrafficStats.UNSUPPORTED) {
            if (lastRx >= 0 && now > lastNetT) {
                double dt = (now - lastNetT) / 1000.0;
                double down = Math.max(0, (rx - lastRx) / 1024.0 / dt);
                double up = Math.max(0, (tx - lastTx) / 1024.0 / dt);
                store.put("net_down", "Rede", "Download [KB/s]", "KB/s", 0, down);
                store.put("net_up", "Rede", "Upload [KB/s]", "KB/s", 0, up);
            }
            lastRx = rx;
            lastTx = tx;
            lastNetT = now;
        }

        // ---- CPU: frequência por núcleo ----
        double sum = 0;
        int cnt = 0;
        for (int i = 0; i < cores; i++) {
            long khz = Sys.readLong("/sys/devices/system/cpu/cpu" + i + "/cpufreq/scaling_cur_freq", -1);
            if (khz > 0) {
                double mhz = khz / 1000.0;
                store.put("cpu_f" + i, "CPU", "Frequência núcleo " + i + " [MHz]", "MHz", 0, mhz);
                sum += mhz;
                cnt++;
            }
        }
        if (cnt > 0) {
            store.put("cpu_favg", "CPU", "Frequência média [MHz]", "MHz", 0, sum / cnt);
        }

        // ---- CPU: uso total (o Android 8+ costuma bloquear /proc/stat) ----
        if (procStatOk) {
            long[] t = readProcStat();
            if (t == null) {
                procStatOk = false;
            } else {
                if (lastCpu != null) {
                    long dTotal = t[0] - lastCpu[0];
                    long dIdle = t[1] - lastCpu[1];
                    if (dTotal > 0) {
                        store.put("cpu_load", "CPU", "Uso total da CPU [%]", "%", 1,
                                (dTotal - dIdle) * 100.0 / dTotal);
                    }
                }
                lastCpu = t;
            }
        }

        // ---- CPU consumida por este app (sempre disponível) ----
        long procCpu = android.os.Process.getElapsedCpuTime();
        if (lastProcCpu >= 0 && now > lastProcT) {
            double pct = (procCpu - lastProcCpu) * 100.0 / (now - lastProcT) / Math.max(1, cores);
            store.put("cpu_app", "CPU", "CPU usada pelo TV Specs [%]", "%", 1, Math.max(0, pct));
        }
        lastProcCpu = procCpu;
        lastProcT = now;

        // ---- Carga média do sistema ----
        String la = Sys.readLine("/proc/loadavg");
        if (la != null) {
            try {
                String[] p = la.split("\\s+");
                store.put("cpu_loadavg", "CPU", "Carga média (1 min)", "", 2, Double.parseDouble(p[0]));
            } catch (Exception ignored) {
            }
        }

        // ---- Temperaturas ----
        List<Sys.Zone> zones = Sys.thermalZones();
        for (int i = 0; i < zones.size(); i++) {
            Sys.Zone z = zones.get(i);
            long raw = Sys.readLong(z.path, Long.MIN_VALUE);
            if (raw == Long.MIN_VALUE || raw == 0) continue;
            double c = (raw > 1000 || raw < -1000) ? raw / 1000.0 : raw;
            if (c < -40 || c > 200) continue;
            store.put("temp_" + i, "Temperaturas", z.name + " [°C]", "°C", 1, c);
        }
        if (Build.VERSION.SDK_INT >= 29 && pm != null) {
            store.put("thermal_status", "Temperaturas", "Estado térmico Android [0 a 6]", "", 0,
                    pm.getCurrentThermalStatus());
        }

        // ---- Wi-Fi ----
        if (wm != null) {
            try {
                if (wm.isWifiEnabled()) {
                    WifiInfo wi = wm.getConnectionInfo();
                    if (wi != null) {
                        int rssi = wi.getRssi();
                        if (rssi > -127 && rssi < 0) {
                            store.put("wifi_rssi", "Wi-Fi", "Sinal [dBm]", "dBm", 0, rssi);
                        }
                        int ls = wi.getLinkSpeed();
                        if (ls > 0) {
                            store.put("wifi_link", "Wi-Fi", "Velocidade do link [Mbps]", "Mbps", 0, ls);
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }
    }

    /** Retorna {total, ocioso} em jiffies, ou null se o Android bloquear a leitura. */
    private static long[] readProcStat() {
        String l = Sys.readLine("/proc/stat");
        if (l == null || !l.startsWith("cpu")) return null;
        try {
            String[] p = l.split("\\s+");
            long total = 0;
            for (int i = 1; i < Math.min(p.length, 9); i++) {
                total += Long.parseLong(p[i]);
            }
            long idle = Long.parseLong(p[4]) + (p.length > 5 ? Long.parseLong(p[5]) : 0);
            return new long[]{total, idle};
        } catch (Exception e) {
            return null;
        }
    }
}
