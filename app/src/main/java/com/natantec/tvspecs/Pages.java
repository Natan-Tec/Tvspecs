package com.natantec.tvspecs;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.UiModeManager;
import android.content.Context;
import android.content.pm.ConfigurationInfo;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.TrafficStats;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.InputDevice;

import java.io.File;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/** Monta o conteúdo (lista de linhas) de cada painel do app. */
final class Pages {
    interface Host {
        void saveReport();
    }

    static final String[] TITLES = {
            "Resumo", "Sistema", "Processador", "Memória", "Armazenamento", "Tela e GPU",
            "Rede", "Sensores", "Codecs e DRM", "Recursos", "Propriedades", "Relatório"
    };
    static final int COUNT = TITLES.length;
    static final int REPORT = 11;

    static volatile String lastReport = "";

    private final Activity ctx;
    private final SensorStore store;
    private final Host host;

    Pages(Activity ctx, SensorStore store, Host host) {
        this.ctx = ctx;
        this.store = store;
        this.host = host;
    }

    /** Painéis que mudam sozinhos a cada segundo. */
    static boolean isLive(int i) {
        return i <= 4 || i == 6 || i == 7;
    }

    List<Row> build(int i) {
        try {
            switch (i) {
                case 0:
                    return summary();
                case 1:
                    return system();
                case 2:
                    return cpu();
                case 3:
                    return memory();
                case 4:
                    return storage();
                case 5:
                    return screen();
                case 6:
                    return network();
                case 7:
                    return sensors();
                case 8:
                    return codecs();
                case 9:
                    return features();
                case 10:
                    return properties();
                case 11:
                    return report();
                default:
                    return new ArrayList<Row>();
            }
        } catch (Throwable t) {
            Rows r = new Rows();
            r.header("Erro ao montar este painel");
            r.kv("Detalhe", t.getClass().getSimpleName() + ": " + t.getMessage());
            return r.list;
        }
    }

    // =====================================================================
    // Auxiliares
    // =====================================================================

    private static String pct1(double v) {
        return String.format(Locale.getDefault(), "%.1f%%", v);
    }

    private static int pctLevel(double p) {
        if (p >= 90) return Row.BAD;
        if (p >= 80) return Row.WARN;
        return Row.NORMAL;
    }

    private static int tempLevel(double c) {
        if (c >= 85) return Row.BAD;
        if (c >= 70) return Row.WARN;
        return Row.NORMAL;
    }

    private ActivityManager.MemoryInfo memInfo() {
        ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        return mi;
    }

    private String abis() {
        List<String> l = new ArrayList<String>();
        for (String a : Build.SUPPORTED_ABIS) l.add(a);
        return Sys.join(l, ", ");
    }

    private String socName() {
        String s = "";
        if (Build.VERSION.SDK_INT >= 31) {
            String m = Build.SOC_MANUFACTURER;
            String n = Build.SOC_MODEL;
            if (m != null && n != null && !"unknown".equalsIgnoreCase(n) && n.length() > 0) {
                s = m + " " + n;
            }
        }
        String plat = Info.prop("ro.board.platform");
        if (s.length() == 0) {
            s = plat.length() > 0 ? plat + " (" + Build.HARDWARE + ")" : Build.HARDWARE;
        } else if (plat.length() > 0) {
            s = s + "  [" + plat + "]";
        }
        return s;
    }

    private String coresSummary() {
        return Runtime.getRuntime().availableProcessors() + " núcleos  •  " + abis();
    }

    private void storageBar(Rows r, String label, File dir) {
        try {
            StatFs s = new StatFs(dir.getAbsolutePath());
            long bs = s.getBlockSizeLong();
            long total = s.getBlockCountLong() * bs;
            long free = s.getAvailableBlocksLong() * bs;
            long used = total - free;
            double pct = total > 0 ? used * 100.0 / total : 0;
            r.bar(label, Sys.bytes(used) + " usados de " + Sys.bytes(total) + "  •  livres " + Sys.bytes(free),
                    (float) (pct / 100.0), pctLevel(pct));
        } catch (Throwable t) {
            r.kv(label, "indisponível");
        }
    }

    // =====================================================================
    // 0 — Resumo
    // =====================================================================

    private List<Row> summary() {
        Rows r = new Rows();
        r.header("Dispositivo");
        r.kv("Modelo", Sys.cap(Build.MANUFACTURER) + " " + Build.MODEL);
        r.kv("Android", Build.VERSION.RELEASE + "  (API " + Build.VERSION.SDK_INT + ")");
        r.kv("SoC / plataforma", socName());
        r.kv("Processador", coresSummary());
        r.kv("Ligado há", Sys.uptime());

        r.header("Uso em tempo real");
        ActivityManager.MemoryInfo mi = memInfo();
        double total = mi.totalMem;
        double used = mi.totalMem - mi.availMem;
        double ramPct = total > 0 ? used * 100.0 / total : 0;
        r.bar("Memória RAM", Sys.bytes(used) + " de " + Sys.bytes(total) + "  •  " + pct1(ramPct),
                (float) (ramPct / 100.0), pctLevel(ramPct));
        SensorStore.Snap ram = store.snap("ram_pct");
        if (ram != null) {
            r.graph("RAM em uso — últimos 2 min", pct1(ram.cur), ram.series, 100f, pctLevel(ram.cur));
        }

        SensorStore.Snap load = store.snap("cpu_load");
        if (load != null) {
            r.bar("CPU", pct1(load.cur), (float) (load.cur / 100.0), pctLevel(load.cur));
            r.graph("CPU — últimos 2 min", pct1(load.cur), load.series, 100f, pctLevel(load.cur));
        } else {
            SensorStore.Snap fa = store.snap("cpu_favg");
            if (fa != null) {
                r.graph("Frequência média da CPU", fa.curText(), fa.series, 0f, Row.NORMAL);
            }
        }

        SensorStore.Snap dn = store.snap("net_down");
        SensorStore.Snap up = store.snap("net_up");
        if (dn != null) r.graph("Download", dn.curText(), dn.series, 0f, Row.NORMAL);
        if (up != null) r.graph("Upload", up.curText(), up.series, 0f, Row.NORMAL);

        double hot = -1000;
        String hotName = "";
        for (SensorStore.Snap s : store.withPrefix("temp_")) {
            if (s.cur > hot) {
                hot = s.cur;
                hotName = s.name.replace(" [°C]", "");
            }
        }
        if (hot > -1000) {
            r.kv("Temperatura mais alta (" + hotName + ")",
                    String.format(Locale.getDefault(), "%.1f °C", hot), tempLevel(hot));
        }

        storageBar(r, "Armazenamento interno (dados)", Environment.getDataDirectory());

        r.header("Tela");
        Display d = ctx.getWindowManager().getDefaultDisplay();
        DisplayMetrics dm = new DisplayMetrics();
        d.getRealMetrics(dm);
        r.kv("Resolução", dm.widthPixels + " × " + dm.heightPixels + " @ "
                + String.format(Locale.getDefault(), "%.0f Hz", d.getRefreshRate()));

        r.header("Rede");
        r.kv("Conexão", connType());
        r.kv("Endereço IP", ipv4());
        return r.list;
    }

    private String connType() {
        ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return "indisponível";
        try {
            if (Build.VERSION.SDK_INT >= 23) {
                Network n = cm.getActiveNetwork();
                if (n == null) return "Sem conexão";
                NetworkCapabilities nc = cm.getNetworkCapabilities(n);
                if (nc == null) return "Sem conexão";
                if (nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return "Ethernet (cabo)";
                if (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return "Wi-Fi";
                if (nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return "Dados móveis";
                if (nc.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) return "Bluetooth";
                if (nc.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return "VPN";
                return "Outra";
            } else {
                NetworkInfo ni = cm.getActiveNetworkInfo();
                return (ni != null && ni.isConnected()) ? ni.getTypeName() : "Sem conexão";
            }
        } catch (Throwable t) {
            return "indisponível";
        }
    }

    private String ipv4() {
        try {
            Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
            while (en != null && en.hasMoreElements()) {
                NetworkInterface ni = en.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                Enumeration<InetAddress> ad = ni.getInetAddresses();
                while (ad.hasMoreElements()) {
                    InetAddress a = ad.nextElement();
                    if (a instanceof Inet4Address && !a.isLoopbackAddress()) {
                        return a.getHostAddress() + "  (" + ni.getName() + ")";
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return "—";
    }

    // =====================================================================
    // 1 — Sistema
    // =====================================================================

    private List<Row> system() {
        Rows r = new Rows();
        UiModeManager um = (UiModeManager) ctx.getSystemService(Context.UI_MODE_SERVICE);
        boolean tv = um != null && um.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION;

        r.header("Dispositivo");
        String devName = null;
        try {
            devName = Settings.Global.getString(ctx.getContentResolver(), "device_name");
        } catch (Throwable ignored) {
        }
        r.kv("Nome do aparelho", devName);
        r.kv("Fabricante", Sys.cap(Build.MANUFACTURER));
        r.kv("Marca", Sys.cap(Build.BRAND));
        r.kv("Modelo", Build.MODEL);
        r.kv("Dispositivo / produto", Build.DEVICE + " / " + Build.PRODUCT);
        r.kv("Placa", Build.BOARD);
        r.kv("Hardware", Build.HARDWARE);
        r.kv("Tipo de aparelho", tv ? "Android TV / Google TV" : "Android (modo comum)",
                tv ? Row.GOOD : Row.NORMAL);

        r.header("Android");
        r.kv("Versão", Build.VERSION.RELEASE);
        r.kv("Nível da API", String.valueOf(Build.VERSION.SDK_INT));
        if (Build.VERSION.SDK_INT >= 23) r.kv("Patch de segurança", Build.VERSION.SECURITY_PATCH);
        r.kv("Build", Build.DISPLAY);
        r.kv("ID do build", Build.ID);
        r.kv("Compilado em", new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                .format(new Date(Build.TIME)));
        r.kv("Tipo / tags", Build.TYPE + " / " + Build.TAGS);
        r.kv("Fingerprint", Build.FINGERPRINT);
        r.kv("Bootloader", Build.BOOTLOADER);
        r.kv("Kernel", System.getProperty("os.version"));
        r.kv("Versão do ART", System.getProperty("java.vm.version"));
        String first = Info.prop("ro.product.first_api_level");
        if (first.length() > 0) r.kv("API de lançamento do aparelho", first);
        String treble = Info.prop("ro.treble.enabled");
        if (treble.length() > 0) r.kv("Project Treble", "true".equals(treble) ? "Sim" : "Não");

        r.header("Sessão");
        r.kv("Ligado há", Sys.uptime());
        r.kv("Idioma", Locale.getDefault().toString());
        r.kv("Fuso horário", TimeZone.getDefault().getID());

        r.header("Limites de memória por app");
        ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        r.kv("Heap padrão", am.getMemoryClass() + " MB");
        r.kv("Heap grande", am.getLargeMemoryClass() + " MB");
        boolean low = am.isLowRamDevice();
        r.kv("Aparelho de pouca RAM", low ? "Sim (modo Android Go)" : "Não", low ? Row.WARN : Row.NORMAL);
        return r.list;
    }

    // =====================================================================
    // 2 — Processador
    // =====================================================================

    private static final class CpuInfo {
        String model = "";
        String features = "";
        String arch = "";
        final List<String> coreNames = new ArrayList<String>();

        static CpuInfo read() {
            CpuInfo c = new CpuInfo();
            boolean have = false;
            String imp = "";
            String part = "";
            for (String l : Sys.readLines("/proc/cpuinfo", 800)) {
                int k = l.indexOf(':');
                if (k < 0) continue;
                String key = l.substring(0, k).trim();
                String val = l.substring(k + 1).trim();
                if (key.equals("processor")) {
                    if (have) c.coreNames.add(coreName(imp, part));
                    have = true;
                    imp = "";
                    part = "";
                } else if (key.equals("CPU implementer")) {
                    imp = val;
                } else if (key.equals("CPU part")) {
                    part = val;
                } else if (key.equals("model name") && c.model.length() == 0) {
                    c.model = val;
                } else if (key.equals("Features") && c.features.length() == 0) {
                    c.features = val;
                } else if (key.equals("flags") && c.features.length() == 0) {
                    c.features = val;
                } else if (key.equals("CPU architecture") && c.arch.length() == 0) {
                    c.arch = val;
                }
            }
            if (have) c.coreNames.add(coreName(imp, part));
            if (c.features.length() > 300) c.features = c.features.substring(0, 300) + "…";
            return c;
        }

        private static String coreName(String imp, String part) {
            if (part == null || part.length() == 0) return "";
            if ("0x41".equals(imp)) {
                switch (part.toLowerCase(Locale.US)) {
                    case "0xc05":
                        return "Cortex-A5";
                    case "0xc07":
                        return "Cortex-A7";
                    case "0xc08":
                        return "Cortex-A8";
                    case "0xc09":
                        return "Cortex-A9";
                    case "0xc0d":
                        return "Cortex-A12";
                    case "0xc0e":
                        return "Cortex-A17";
                    case "0xc0f":
                        return "Cortex-A15";
                    case "0xd01":
                        return "Cortex-A32";
                    case "0xd03":
                        return "Cortex-A53";
                    case "0xd04":
                        return "Cortex-A35";
                    case "0xd05":
                        return "Cortex-A55";
                    case "0xd07":
                        return "Cortex-A57";
                    case "0xd08":
                        return "Cortex-A72";
                    case "0xd09":
                        return "Cortex-A73";
                    case "0xd0a":
                        return "Cortex-A75";
                    case "0xd0b":
                        return "Cortex-A76";
                    case "0xd0d":
                        return "Cortex-A77";
                    case "0xd41":
                        return "Cortex-A78";
                    case "0xd44":
                        return "Cortex-X1";
                    default:
                        break;
                }
            }
            return "ARM part " + part;
        }
    }

    private List<Row> cpu() {
        Rows r = new Rows();
        int cores = Runtime.getRuntime().availableProcessors();
        CpuInfo ci = CpuInfo.read();

        r.header("Processador");
        r.kv("SoC / plataforma", socName());
        if (ci.model.length() > 0) r.kv("Modelo (cpuinfo)", ci.model);
        r.kv("ABIs suportadas", abis());
        if (ci.arch.length() > 0) r.kv("Arquitetura ARM", "ARMv" + ci.arch);

        String online = Sys.readLine("/sys/devices/system/cpu/online");
        r.kv("Núcleos", cores + (online != null ? "  (online: " + online + ")" : ""));

        Map<String, Integer> kinds = new LinkedHashMap<String, Integer>();
        for (String n : ci.coreNames) {
            if (n.length() == 0) continue;
            Integer c = kinds.get(n);
            kinds.put(n, c == null ? 1 : c + 1);
        }
        if (!kinds.isEmpty()) {
            List<String> parts = new ArrayList<String>();
            for (Map.Entry<String, Integer> e : kinds.entrySet()) {
                parts.add(e.getValue() + "× " + e.getKey());
            }
            r.kv("Tipos de núcleo", Sys.join(parts, " + "));
        }
        String gov = Sys.readLine("/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor");
        if (gov != null) r.kv("Governador de frequência", gov);
        if (ci.features.length() > 0) r.kv("Recursos (instruções)", ci.features);

        SensorStore.Snap load = store.snap("cpu_load");
        if (load != null) {
            r.header("Uso");
            r.bar("Uso total da CPU", pct1(load.cur), (float) (load.cur / 100.0), pctLevel(load.cur));
            r.graph("Histórico", pct1(load.cur), load.series, 100f, pctLevel(load.cur));
        }

        r.header("Frequência por núcleo");
        boolean anyFreq = false;
        for (int i = 0; i < cores; i++) {
            String base = "/sys/devices/system/cpu/cpu" + i + "/cpufreq/";
            long cur = Sys.readLong(base + "scaling_cur_freq", -1);
            long max = Sys.readLong(base + "cpuinfo_max_freq", -1);
            long min = Sys.readLong(base + "cpuinfo_min_freq", -1);
            String name = (i < ci.coreNames.size() && ci.coreNames.get(i).length() > 0)
                    ? "  (" + ci.coreNames.get(i) + ")" : "";
            if (cur > 0) {
                anyFreq = true;
                float pct = max > 0 ? (float) cur / (float) max : 0f;
                String range = max > 0 ? "  •  faixa " + (min > 0 ? min / 1000 : 0) + "–" + (max / 1000) : "";
                r.bar("Núcleo " + i + name, (cur / 1000) + " MHz" + range, pct, Row.NORMAL);
            } else {
                r.kv("Núcleo " + i + name, "offline ou sem leitura");
            }
        }
        if (!anyFreq) {
            r.note("Esta TV não permite ler a frequência da CPU para apps comuns.");
        }
        SensorStore.Snap favg = store.snap("cpu_favg");
        if (favg != null) {
            r.graph("Frequência média — últimos 2 min", favg.curText(), favg.series, 0f, Row.NORMAL);
        }
        return r.list;
    }

    // =====================================================================
    // 3 — Memória
    // =====================================================================

    private static Map<String, Long> meminfo() {
        Map<String, Long> m = new LinkedHashMap<String, Long>();
        for (String l : Sys.readLines("/proc/meminfo", 100)) {
            int c = l.indexOf(':');
            if (c < 0) continue;
            String k = l.substring(0, c).trim();
            String v = l.substring(c + 1).trim();
            int sp = v.indexOf(' ');
            if (sp > 0) v = v.substring(0, sp);
            try {
                m.put(k, Long.parseLong(v));
            } catch (NumberFormatException ignored) {
            }
        }
        return m;
    }

    private List<Row> memory() {
        Rows r = new Rows();
        ActivityManager.MemoryInfo mi = memInfo();
        double total = mi.totalMem;
        double avail = mi.availMem;
        double used = total - avail;
        double pct = total > 0 ? used * 100.0 / total : 0;

        r.header("RAM");
        r.bar("Em uso", Sys.bytes(used) + " de " + Sys.bytes(total) + "  •  " + pct1(pct),
                (float) (pct / 100.0), pctLevel(pct));
        SensorStore.Snap ram = store.snap("ram_pct");
        if (ram != null) r.graph("Histórico de uso — últimos 2 min", pct1(ram.cur), ram.series, 100f, pctLevel(ram.cur));
        r.kv("Total", Sys.bytes(total));
        r.kv("Disponível", Sys.bytes(avail));
        r.kv("Limite de pouca memória", Sys.bytes(mi.threshold));
        r.kv("Sistema com pouca memória?", mi.lowMemory ? "SIM" : "Não", mi.lowMemory ? Row.BAD : Row.GOOD);

        Map<String, Long> m = meminfo();
        Long swapTotal = m.get("SwapTotal");
        Long swapFree = m.get("SwapFree");
        if (swapTotal != null && swapTotal > 0 && swapFree != null) {
            double st = swapTotal * 1024.0;
            double su = (swapTotal - swapFree) * 1024.0;
            double sp = st > 0 ? su * 100.0 / st : 0;
            r.header("Swap / zRAM");
            r.bar("Em uso", Sys.bytes(su) + " de " + Sys.bytes(st) + "  •  " + pct1(sp),
                    (float) (sp / 100.0), pctLevel(sp));
        }

        if (!m.isEmpty()) {
            r.header("Detalhes do kernel (/proc/meminfo)");
            String[][] keys = {
                    {"MemTotal", "Total"}, {"MemFree", "Livre"}, {"MemAvailable", "Disponível"},
                    {"Buffers", "Buffers"}, {"Cached", "Cache de arquivos"}, {"SwapCached", "Cache do swap"},
                    {"SwapTotal", "Swap total"}, {"SwapFree", "Swap livre"},
                    {"Active", "Ativa"}, {"Inactive", "Inativa"}, {"Dirty", "Pendente de gravação"},
                    {"Shmem", "Compartilhada (shmem)"}, {"Slab", "Slab (kernel)"},
                    {"KernelStack", "Pilha do kernel"}, {"PageTables", "Tabelas de páginas"},
                    {"Mapped", "Mapeada"}, {"CmaTotal", "CMA total"}, {"CmaFree", "CMA livre"}
            };
            for (String[] k : keys) {
                Long v = m.get(k[0]);
                if (v != null) r.kv(k[1], Sys.bytes(v * 1024.0));
            }
        }

        Runtime rt = Runtime.getRuntime();
        r.header("Este app (TV Specs)");
        r.kv("Heap em uso", Sys.bytes(rt.totalMemory() - rt.freeMemory()));
        r.kv("Heap máximo", Sys.bytes(rt.maxMemory()));
        return r.list;
    }

    // =====================================================================
    // 4 — Armazenamento
    // =====================================================================

    private List<Row> storage() {
        Rows r = new Rows();
        r.header("Partições do sistema");
        storageBar(r, "Dados do usuário (/data)", Environment.getDataDirectory());
        storageBar(r, "Sistema (/system)", Environment.getRootDirectory());

        r.header("Armazenamento compartilhado e externo");
        List<String> seen = new ArrayList<String>();
        boolean any = false;
        File[] ext = ctx.getExternalFilesDirs(null);
        if (ext != null) {
            for (int i = 0; i < ext.length; i++) {
                File f = ext[i];
                if (f == null) continue;
                String p = f.getAbsolutePath();
                int k = p.indexOf("/Android/");
                String root = k > 0 ? p.substring(0, k) : p;
                if (seen.contains(root)) continue;
                seen.add(root);
                boolean removable = false;
                try {
                    removable = Environment.isExternalStorageRemovable(f);
                } catch (Throwable ignored) {
                }
                String kind = i == 0 ? "Interno compartilhado" : (removable ? "Removível (USB/SD)" : "Externo");
                storageBar(r, kind + "  " + root, new File(root));
                any = true;
            }
        }
        if (!any) r.kv("Armazenamento externo", "não disponível");

        if (Build.VERSION.SDK_INT >= 24) {
            try {
                StorageManager sm = (StorageManager) ctx.getSystemService(Context.STORAGE_SERVICE);
                if (sm != null) {
                    List<StorageVolume> vols = sm.getStorageVolumes();
                    if (vols != null && !vols.isEmpty()) {
                        r.header("Volumes reconhecidos");
                        for (StorageVolume v : vols) {
                            r.kv(v.getDescription(ctx),
                                    (v.isPrimary() ? "primário • " : "")
                                            + (v.isRemovable() ? "removível" : "fixo")
                                            + " • " + v.getState());
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return r.list;
    }

    // =====================================================================
    // 5 — Tela e GPU
    // =====================================================================

    private static String resName(int w, int h) {
        int big = Math.max(w, h);
        if (big >= 7600) return "8K";
        if (big >= 3800) return "4K UHD";
        if (big >= 2500) return "QHD 1440p";
        if (big >= 1900) return "Full HD 1080p";
        if (big >= 1250) return "HD 720p";
        return "baixa resolução";
    }

    private static String hdrName(int t) {
        switch (t) {
            case 1:
                return "Dolby Vision";
            case 2:
                return "HDR10";
            case 3:
                return "HLG";
            case 4:
                return "HDR10+";
            default:
                return "tipo " + t;
        }
    }

    private List<Row> screen() {
        Rows r = new Rows();
        Display d = ctx.getWindowManager().getDefaultDisplay();
        DisplayMetrics dm = new DisplayMetrics();
        d.getRealMetrics(dm);

        r.header("Tela");
        r.kv("Resolução", dm.widthPixels + " × " + dm.heightPixels + "  (" + resName(dm.widthPixels, dm.heightPixels) + ")");
        r.kv("Taxa de atualização", String.format(Locale.getDefault(), "%.2f Hz", d.getRefreshRate()));
        r.kv("Densidade", dm.densityDpi + " dpi  (×" + String.format(Locale.getDefault(), "%.2f", dm.density) + ")");
        r.kv("Tamanho lógico", Math.round(dm.widthPixels / dm.density) + " × "
                + Math.round(dm.heightPixels / dm.density) + " dp");
        r.kv("Nome do display", d.getName());

        if (Build.VERSION.SDK_INT >= 24) {
            try {
                Display.HdrCapabilities hc = d.getHdrCapabilities();
                if (hc != null) {
                    int[] types = hc.getSupportedHdrTypes();
                    List<String> names = new ArrayList<String>();
                    if (types != null) {
                        for (int t : types) names.add(hdrName(t));
                    }
                    r.kv("HDR", names.isEmpty() ? "Não suportado" : Sys.join(names, ", "),
                            names.isEmpty() ? Row.WARN : Row.GOOD);
                    if (!names.isEmpty() && hc.getDesiredMaxLuminance() > 0) {
                        r.kv("Brilho máximo declarado", Math.round(hc.getDesiredMaxLuminance()) + " nits");
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                r.kv("Gama de cores ampla", d.isWideColorGamut() ? "✔ Sim" : "Não",
                        d.isWideColorGamut() ? Row.GOOD : Row.NORMAL);
            } catch (Throwable ignored) {
            }
        }

        if (Build.VERSION.SDK_INT >= 23) {
            try {
                Display.Mode cur = d.getMode();
                Display.Mode[] modes = d.getSupportedModes();
                if (modes != null && modes.length > 0) {
                    Arrays.sort(modes, new Comparator<Display.Mode>() {
                        @Override
                        public int compare(Display.Mode a, Display.Mode b) {
                            long aa = (long) a.getPhysicalWidth() * a.getPhysicalHeight();
                            long bb = (long) b.getPhysicalWidth() * b.getPhysicalHeight();
                            if (aa != bb) return aa < bb ? 1 : -1;
                            return Float.compare(b.getRefreshRate(), a.getRefreshRate());
                        }
                    });
                    r.header("Modos de vídeo suportados (" + modes.length + ")");
                    for (Display.Mode m : modes) {
                        boolean isCur = cur != null && m.getModeId() == cur.getModeId();
                        r.kv(m.getPhysicalWidth() + " × " + m.getPhysicalHeight(),
                                String.format(Locale.getDefault(), "%.2f Hz", m.getRefreshRate())
                                        + (isCur ? "   ◀ em uso" : ""),
                                isCur ? Row.GOOD : Row.NORMAL);
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        r.header("GPU / gráficos");
        String[] g = Info.gpu;
        if (g == null || g.length < 6) {
            r.kv("GPU", Info.ready ? "indisponível" : "Carregando…");
        } else {
            r.kv("GPU (renderer)", g[0]);
            r.kv("Fabricante", g[1]);
            r.kv("OpenGL ES", g[2]);
            r.kv("GLSL", g[3]);
            r.kv("Textura máxima", g[4]);
            r.kv("Extensões OpenGL", g[5]);
        }
        try {
            ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            ConfigurationInfo cfg = am.getDeviceConfigurationInfo();
            r.kv("OpenGL ES (declarado)", (cfg.reqGlEsVersion >> 16) + "." + (cfg.reqGlEsVersion & 0xFFFF));
        } catch (Throwable ignored) {
        }
        try {
            for (FeatureInfo fi : ctx.getPackageManager().getSystemAvailableFeatures()) {
                if ("android.hardware.vulkan.version".equals(fi.name)) {
                    int v = fi.version;
                    r.kv("Vulkan", "✔ " + (v >> 22) + "." + ((v >> 12) & 0x3FF), Row.GOOD);
                }
            }
        } catch (Throwable ignored) {
        }
        return r.list;
    }

    // =====================================================================
    // 6 — Rede
    // =====================================================================

    private static String bandText(int mhz) {
        if (mhz <= 0) return "";
        if (mhz < 3000) return "2,4 GHz";
        if (mhz < 5925) return "5 GHz";
        return "6 GHz";
    }

    private static String signalText(int rssi) {
        if (rssi >= -50) return "excelente";
        if (rssi >= -60) return "bom";
        if (rssi >= -70) return "regular";
        return "fraco";
    }

    private static String wifiStd(int s) {
        switch (s) {
            case 1:
                return "802.11 legado";
            case 4:
                return "Wi-Fi 4 (802.11n)";
            case 5:
                return "Wi-Fi 5 (802.11ac)";
            case 6:
                return "Wi-Fi 6 (802.11ax)";
            case 8:
                return "Wi-Fi 7 (802.11be)";
            default:
                return "";
        }
    }

    private List<Row> network() {
        Rows r = new Rows();
        ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);

        r.header("Conexão ativa");
        String type = connType();
        boolean connected = !type.startsWith("Sem") && !type.startsWith("indisp");
        r.kv("Tipo", type, connected ? Row.GOOD : Row.BAD);
        if (cm != null && Build.VERSION.SDK_INT >= 23) {
            try {
                Network n = cm.getActiveNetwork();
                NetworkCapabilities nc = n == null ? null : cm.getNetworkCapabilities(n);
                if (nc != null) {
                    boolean net = nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
                    boolean ok = nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
                    r.kv("Internet", net ? (ok ? "✔ Funcionando" : "Conectado, sem validação") : "Sem acesso à internet",
                            net && ok ? Row.GOOD : Row.WARN);
                    int dn = nc.getLinkDownstreamBandwidthKbps();
                    int up = nc.getLinkUpstreamBandwidthKbps();
                    if (dn > 0) {
                        r.kv("Banda estimada pelo sistema", "↓ " + (dn / 1000) + " Mbps   ↑ " + (up / 1000) + " Mbps");
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        SensorStore.Snap sd = store.snap("net_down");
        SensorStore.Snap su = store.snap("net_up");
        if (sd != null) r.graph("Download agora", sd.curText(), sd.series, 0f, Row.NORMAL);
        if (su != null) r.graph("Upload agora", su.curText(), su.series, 0f, Row.NORMAL);
        long rx = TrafficStats.getTotalRxBytes();
        long tx = TrafficStats.getTotalTxBytes();
        if (rx != TrafficStats.UNSUPPORTED) {
            r.kv("Total recebido desde o boot", Sys.bytes(rx));
            r.kv("Total enviado desde o boot", Sys.bytes(tx));
        }

        r.header("Interfaces de rede");
        try {
            Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
            boolean any = false;
            while (en != null && en.hasMoreElements()) {
                NetworkInterface ni = en.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                StringBuilder sb = new StringBuilder();
                int v6 = 0;
                for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                    InetAddress a = ia.getAddress();
                    if (a instanceof Inet4Address) {
                        sb.append(a.getHostAddress()).append('/').append(ia.getNetworkPrefixLength()).append("  ");
                    } else if (a instanceof Inet6Address) {
                        v6++;
                    }
                }
                if (v6 > 0) sb.append("(+").append(v6).append(" IPv6)  ");
                try {
                    sb.append("MTU ").append(ni.getMTU());
                } catch (Throwable ignored) {
                }
                r.kv(ni.getName(), sb.toString().trim());
                any = true;
            }
            if (!any) r.kv("Interfaces", "nenhuma ativa");
        } catch (Throwable t) {
            r.kv("Interfaces", "indisponível");
        }

        WifiManager wm = (WifiManager) ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wm != null) {
            r.header("Wi-Fi");
            try {
                if (!wm.isWifiEnabled()) {
                    r.kv("Wi-Fi", "Desligado");
                } else {
                    WifiInfo wi = wm.getConnectionInfo();
                    int rssi = wi == null ? -127 : wi.getRssi();
                    if (wi == null || rssi <= -127) {
                        r.kv("Wi-Fi", "Ligado, sem conexão");
                    } else {
                        String ssid = wi.getSSID();
                        if (ssid != null && !ssid.contains("unknown ssid")) {
                            r.kv("Rede (SSID)", ssid.replace("\"", ""));
                        }
                        r.kv("Sinal", rssi + " dBm  (" + signalText(rssi) + ")",
                                rssi >= -60 ? Row.GOOD : (rssi >= -70 ? Row.NORMAL : Row.WARN));
                        if (wi.getLinkSpeed() > 0) r.kv("Velocidade do link", wi.getLinkSpeed() + " Mbps");
                        int f = wi.getFrequency();
                        if (f > 0) r.kv("Frequência", f + " MHz  (" + bandText(f) + ")");
                        if (Build.VERSION.SDK_INT >= 30) {
                            String std = wifiStd(wi.getWifiStandard());
                            if (std.length() > 0) r.kv("Padrão", std);
                        }
                    }
                }
            } catch (Throwable t) {
                r.kv("Wi-Fi", "indisponível");
            }
        }
        return r.list;
    }

    // =====================================================================
    // 7 — Sensores (estilo HWiNFO)
    // =====================================================================

    private static int sensorLevel(SensorStore.Snap s) {
        if ("°C".equals(s.unit)) return tempLevel(s.cur);
        if ("%".equals(s.unit)) return pctLevel(s.cur);
        return Row.NORMAL;
    }

    private List<Row> sensors() {
        Rows r = new Rows();
        long s = store.elapsedSec();
        r.note(String.format(Locale.US, "Coletando há %02d:%02d  •  atualiza a cada 1 s  •  mín/máx/média desde o início",
                s / 60, s % 60));
        r.sensorHead();

        String[] groups = {"CPU", "Memória", "Rede", "Wi-Fi", "Temperaturas"};
        for (String g : groups) {
            List<SensorStore.Snap> list = store.group(g);
            if (list.isEmpty()) continue;
            r.header(g);
            for (SensorStore.Snap sn : list) {
                r.sensor(sn.name, sn.fmt(sn.cur), sn.fmt(sn.min), sn.fmt(sn.max), sn.fmt(sn.avg), sensorLevel(sn));
            }
        }

        if (store.withPrefix("temp_").isEmpty()) {
            r.note("Temperaturas: esta TV não libera os sensores térmicos para apps comuns (restrição do Android/fabricante).");
        }
        if (store.snap("cpu_load") == null) {
            r.note("Uso total da CPU: o Android 8+ bloqueia essa leitura. Mostramos a frequência de cada núcleo e a CPU usada por este app.");
        }
        r.action("Zerar mín / máx / média", new Runnable() {
            @Override
            public void run() {
                store.reset();
            }
        });
        return r.list;
    }

    // =====================================================================
    // 8 — Codecs e DRM
    // =====================================================================

    private List<Row> codecs() {
        if (!Info.ready) {
            Rows r = new Rows();
            r.header("Codecs e DRM");
            r.kv("Status", "Carregando informações…");
            return r.list;
        }
        return Info.codecRows;
    }

    // =====================================================================
    // 9 — Recursos
    // =====================================================================

    private static final String[][] KEYFEAT = {
            {"android.software.leanback", "Android TV (Leanback)"},
            {"android.software.live_tv", "TV ao vivo (Live TV)"},
            {"android.hardware.type.television", "Aparelho do tipo televisão"},
            {"android.hardware.hdmi.cec", "HDMI-CEC"},
            {"android.hardware.wifi", "Wi-Fi"},
            {"android.hardware.wifi.direct", "Wi-Fi Direct"},
            {"android.hardware.ethernet", "Ethernet"},
            {"android.hardware.bluetooth", "Bluetooth"},
            {"android.hardware.bluetooth_le", "Bluetooth LE"},
            {"android.hardware.usb.host", "USB host (pendrive, teclado...)"},
            {"android.hardware.microphone", "Microfone"},
            {"android.hardware.audio.output", "Saída de áudio"},
            {"android.hardware.camera", "Câmera"},
            {"android.hardware.touchscreen", "Tela sensível ao toque"},
            {"android.hardware.nfc", "NFC"},
            {"android.software.picture_in_picture", "Picture-in-Picture"}
    };

    private static String sourcesText(int src) {
        List<String> l = new ArrayList<String>();
        if ((src & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD) l.add("gamepad");
        if ((src & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) l.add("joystick");
        if ((src & InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD) l.add("direcional");
        if ((src & InputDevice.SOURCE_KEYBOARD) == InputDevice.SOURCE_KEYBOARD) l.add("teclas");
        if ((src & InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE) l.add("mouse");
        if ((src & InputDevice.SOURCE_TOUCHSCREEN) == InputDevice.SOURCE_TOUCHSCREEN) l.add("toque");
        if ((src & InputDevice.SOURCE_TOUCHPAD) == InputDevice.SOURCE_TOUCHPAD) l.add("touchpad");
        return l.isEmpty() ? "outro" : Sys.join(l, ", ");
    }

    private List<Row> features() {
        Rows r = new Rows();
        PackageManager pm = ctx.getPackageManager();

        r.header("Recursos principais");
        for (String[] f : KEYFEAT) {
            boolean has = pm.hasSystemFeature(f[0]);
            r.kv(f[1], has ? "✔ Sim" : "✘ Não", has ? Row.GOOD : Row.NORMAL);
        }

        r.header("Dispositivos de entrada");
        boolean anyInput = false;
        try {
            for (int id : InputDevice.getDeviceIds()) {
                InputDevice d = InputDevice.getDevice(id);
                if (d == null || d.isVirtual()) continue;
                r.kv(d.getName(), sourcesText(d.getSources()));
                anyInput = true;
            }
        } catch (Throwable ignored) {
        }
        if (!anyInput) r.kv("Entradas", "nenhuma detectada");

        r.header("Sensores de hardware");
        try {
            SensorManager sm = (SensorManager) ctx.getSystemService(Context.SENSOR_SERVICE);
            List<Sensor> sensors = sm == null ? null : sm.getSensorList(Sensor.TYPE_ALL);
            if (sensors == null || sensors.isEmpty()) {
                r.kv("Sensores", "nenhum (normal em TVs)");
            } else {
                for (Sensor s : sensors) r.kv(s.getName(), s.getVendor());
            }
        } catch (Throwable t) {
            r.kv("Sensores", "indisponível");
        }

        FeatureInfo[] all = pm.getSystemAvailableFeatures();
        List<String> names = new ArrayList<String>();
        if (all != null) {
            for (FeatureInfo fi : all) {
                if (fi.name != null) names.add(fi.name + (fi.version > 0 ? "  (v" + fi.version + ")" : ""));
            }
        }
        Collections.sort(names);
        r.header("Todas as features do sistema (" + names.size() + ")");
        for (String n : names) r.kv(n, "✔");
        return r.list;
    }

    // =====================================================================
    // 10 — Propriedades
    // =====================================================================

    private List<Row> properties() {
        Rows r = new Rows();
        Map<String, String> p = Info.props;
        if (p.isEmpty()) {
            r.header("Propriedades do sistema");
            r.kv("Status", Info.ready ? "indisponível nesta TV" : "Carregando…");
            return r.list;
        }
        r.header("Propriedades do sistema (" + p.size() + ")");
        for (Map.Entry<String, String> e : p.entrySet()) {
            r.kv(e.getKey(), e.getValue());
        }
        return r.list;
    }

    // =====================================================================
    // 11 — Relatório
    // =====================================================================

    private List<Row> report() {
        Rows r = new Rows();
        r.header("Relatório completo");
        r.note("Salva um arquivo .txt com todas as informações desta TV: sistema, CPU, memória, tela, rede, "
                + "codecs e propriedades. No Android 10+ ele vai para a pasta Download.");
        r.action("Salvar relatório agora", new Runnable() {
            @Override
            public void run() {
                host.saveReport();
            }
        });
        String last = lastReport;
        if (last.length() > 0) {
            r.kv("Resultado", last, last.startsWith("Salvo") ? Row.GOOD : Row.WARN);
        }
        return r.list;
    }
}
