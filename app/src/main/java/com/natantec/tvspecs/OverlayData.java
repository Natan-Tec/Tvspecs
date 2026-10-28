package com.natantec.tvspecs;

/**
 * PREPARAÇÃO PARA O OVERLAY (versão futura, só celular).
 * Reúne em um só lugar os valores que o overlay vai mostrar, lidos do SensorStore
 * que o Sampler já alimenta: temperatura, FPS, CPU, GPU e RAM.
 * O serviço de overlay (janela flutuante, permissão "Sobrepor a outros apps") ainda
 * não existe; quando for criado, basta chamar OverlayData.read(store) a cada tick.
 */
final class OverlayData {
    /** NaN = ainda sem leitura / não suportado neste aparelho. */
    double tempC = Double.NaN;
    double fps = Double.NaN;      // TODO: medir via Choreographer/SurfaceFlinger no serviço de overlay
    double cpuPct = Double.NaN;
    double gpuPct = Double.NaN;   // TODO: ler /sys/class/kgsl ou /sys/devices/.../gpu (varia por SoC)
    double ramPct = Double.NaN;

    static OverlayData read(SensorStore s) {
        OverlayData d = new OverlayData();
        d.cpuPct = val(s.snap("cpu_load"));
        d.ramPct = val(s.snap("ram_pct"));
        d.gpuPct = val(s.snap("gpu_load"));
        d.fps = val(s.snap("fps"));
        // Maior temperatura entre as zonas térmicas disponíveis.
        for (SensorStore.Snap t : s.withPrefix("temp_")) {
            if (Double.isNaN(d.tempC) || t.cur > d.tempC) d.tempC = t.cur;
        }
        return d;
    }

    private static double val(SensorStore.Snap n) {
        return n == null ? Double.NaN : n.cur;
    }
}
