package com.natantec.tvspecs;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaDrm;
import android.os.Build;
import android.util.Range;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Codecs de vídeo/áudio, DRM (Widevine etc.) e capacidades de áudio da saída HDMI. */
final class CodecProbe {
    private CodecProbe() {
    }

    private static final String[][] VIDEO = {
            {"video/avc", "H.264 / AVC"},
            {"video/hevc", "H.265 / HEVC"},
            {"video/x-vnd.on2.vp9", "VP9"},
            {"video/av01", "AV1"},
            {"video/x-vnd.on2.vp8", "VP8"},
            {"video/mpeg2", "MPEG-2"},
            {"video/mp4v-es", "MPEG-4 Parte 2"},
            {"video/3gpp", "H.263"},
            {"video/dolby-vision", "Dolby Vision"}
    };

    private static final String[][] AUDIO = {
            {"audio/mp4a-latm", "AAC"},
            {"audio/ac3", "Dolby Digital (AC-3)"},
            {"audio/eac3", "Dolby Digital Plus (E-AC-3)"},
            {"audio/eac3-joc", "Dolby Atmos (E-AC-3 JOC)"},
            {"audio/true-hd", "Dolby TrueHD"},
            {"audio/vnd.dts", "DTS"},
            {"audio/vnd.dts.hd", "DTS-HD"},
            {"audio/opus", "Opus"},
            {"audio/vorbis", "Vorbis"},
            {"audio/mpeg", "MP3"},
            {"audio/flac", "FLAC"}
    };

    static List<Row> build(Context ctx) {
        Rows r = new Rows();

        MediaCodecInfo[] infos;
        try {
            infos = new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos();
        } catch (Throwable t) {
            infos = new MediaCodecInfo[0];
        }
        int dec = 0;
        int enc = 0;
        for (MediaCodecInfo i : infos) {
            if (i.isEncoder()) enc++;
            else dec++;
        }

        r.header("Resumo");
        r.kv("Decodificadores", String.valueOf(dec));
        r.kv("Codificadores", String.valueOf(enc));

        r.header("Vídeo — decodificação");
        for (String[] m : VIDEO) {
            video(r, infos, m[0], m[1]);
        }

        r.header("Áudio — decodificação");
        for (String[] m : AUDIO) {
            audio(r, infos, m[0], m[1]);
        }

        drm(r);
        audioOutputs(r, ctx);
        return r.list;
    }

    // ---------- vídeo ----------

    private static boolean isHw(MediaCodecInfo info) {
        if (Build.VERSION.SDK_INT >= 29) return info.isHardwareAccelerated();
        String n = info.getName().toLowerCase();
        return !(n.startsWith("omx.google.") || n.startsWith("c2.android.") || n.contains(".sw."));
    }

    private static void video(Rows r, MediaCodecInfo[] infos, String mime, String label) {
        MediaCodecInfo bestHw = null;
        MediaCodecInfo bestSw = null;
        int bestHwW = -1;
        int bestSwW = -1;
        boolean tenBit = false;

        for (MediaCodecInfo info : infos) {
            if (info.isEncoder() || !supports(info, mime)) continue;
            int w = maxWidth(info, mime);
            if (isHw(info)) {
                if (w > bestHwW) {
                    bestHw = info;
                    bestHwW = w;
                }
            } else if (w > bestSwW) {
                bestSw = info;
                bestSwW = w;
            }
            if (has10Bit(info, mime)) tenBit = true;
        }

        if (bestHw != null) {
            String v = "✔ Hardware • até " + sizeText(bestHw, mime) + (tenBit ? " • 10 bits" : "")
                    + "\n" + bestHw.getName();
            r.kv(label, v, Row.GOOD);
        } else if (bestSw != null) {
            String v = "Só software • até " + sizeText(bestSw, mime) + "\n" + bestSw.getName();
            r.kv(label, v, Row.WARN);
        } else {
            r.kv(label, "✘ Não suportado");
        }
    }

    private static boolean supports(MediaCodecInfo info, String mime) {
        for (String t : info.getSupportedTypes()) {
            if (t.equalsIgnoreCase(mime)) return true;
        }
        return false;
    }

    private static int maxWidth(MediaCodecInfo info, String mime) {
        try {
            MediaCodecInfo.VideoCapabilities vc = info.getCapabilitiesForType(mime).getVideoCapabilities();
            if (vc != null) {
                Range<Integer> w = vc.getSupportedWidths();
                return w.getUpper();
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }

    private static String sizeText(MediaCodecInfo info, String mime) {
        try {
            MediaCodecInfo.VideoCapabilities vc = info.getCapabilitiesForType(mime).getVideoCapabilities();
            if (vc != null) {
                int w = vc.getSupportedWidths().getUpper();
                int h = vc.getSupportedHeights().getUpper();
                return w + "×" + h;
            }
        } catch (Throwable ignored) {
        }
        return "?";
    }

    /** Detecta perfil de 10 bits / HDR10 (valores numéricos dos perfis do MediaCodecInfo). */
    private static boolean has10Bit(MediaCodecInfo info, String mime) {
        try {
            MediaCodecInfo.CodecCapabilities cap = info.getCapabilitiesForType(mime);
            if (cap.profileLevels == null) return false;
            for (MediaCodecInfo.CodecProfileLevel pl : cap.profileLevels) {
                int p = pl.profile;
                if ("video/hevc".equals(mime) && (p == 0x2 || p == 0x1000 || p == 0x2000)) return true;
                if ("video/x-vnd.on2.vp9".equals(mime) && (p == 0x4 || p == 0x8 || p == 0x1000 || p == 0x2000)) return true;
                if ("video/av01".equals(mime) && p == 0x2) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    // ---------- áudio ----------

    private static void audio(Rows r, MediaCodecInfo[] infos, String mime, String label) {
        String found = null;
        for (MediaCodecInfo info : infos) {
            if (info.isEncoder() || !supports(info, mime)) continue;
            found = info.getName();
            break;
        }
        if (found != null) r.kv(label, "✔ " + found, Row.GOOD);
        else r.kv(label, "✘ Não suportado");
    }

    // ---------- DRM ----------

    private static void drm(Rows r) {
        r.header("DRM (proteção de conteúdo)");
        UUID widevine = new UUID(0xEDEF8BA979D64ACEL, 0xA3C827DCD51D21EDL);
        UUID playready = new UUID(0x9A04F07998404286L, 0xAB92E65BE0885F95L);
        UUID clearkey = new UUID(0xE2719D58A985B3C9L, 0x781AB030AF78D30EL);

        boolean wv = false;
        try {
            wv = MediaDrm.isCryptoSchemeSupported(widevine);
        } catch (Throwable ignored) {
        }
        r.kv("Widevine", wv ? "✔ Suportado" : "✘ Não suportado", wv ? Row.GOOD : Row.WARN);

        if (wv) {
            MediaDrm m = null;
            try {
                m = new MediaDrm(widevine);
                String level = safeProp(m, "securityLevel");
                String lv = level.length() == 0 ? "?" : level;
                int lvl = "L1".equalsIgnoreCase(level) ? Row.GOOD : Row.WARN;
                r.kv("Nível de segurança", lv
                        + ("L1".equalsIgnoreCase(level) ? "  (permite HD/4K em Netflix, etc.)"
                        : "L3".equalsIgnoreCase(level) ? "  (streaming limitado a SD/HD baixo)" : ""), lvl);
                r.kv("Versão do Widevine", safeProp(m, MediaDrm.PROPERTY_VERSION));
                r.kv("Fornecedor", safeProp(m, MediaDrm.PROPERTY_VENDOR));
                String hdcp = safeProp(m, "hdcpLevel");
                if (hdcp.length() > 0) r.kv("HDCP atual", hdcp);
                String maxHdcp = safeProp(m, "maxHdcpLevel");
                if (maxHdcp.length() > 0) r.kv("HDCP máximo", maxHdcp);
            } catch (Throwable t) {
                r.kv("Detalhes", "indisponíveis: " + t.getClass().getSimpleName());
            } finally {
                if (m != null) {
                    try {
                        m.release();
                    } catch (Throwable ignored) {
                    }
                }
            }
        }

        boolean pr = false;
        boolean ck = false;
        try {
            pr = MediaDrm.isCryptoSchemeSupported(playready);
        } catch (Throwable ignored) {
        }
        try {
            ck = MediaDrm.isCryptoSchemeSupported(clearkey);
        } catch (Throwable ignored) {
        }
        r.kv("PlayReady", pr ? "✔ Suportado" : "✘ Não suportado", pr ? Row.GOOD : Row.NORMAL);
        r.kv("ClearKey", ck ? "✔ Suportado" : "✘ Não suportado", ck ? Row.GOOD : Row.NORMAL);
    }

    private static String safeProp(MediaDrm m, String name) {
        try {
            String v = m.getPropertyString(name);
            return v == null ? "" : v;
        } catch (Throwable t) {
            return "";
        }
    }

    // ---------- saídas de áudio (HDMI etc.) ----------

    private static void audioOutputs(Rows r, Context ctx) {
        r.header("Saídas de áudio");
        if (Build.VERSION.SDK_INT < 23) {
            r.kv("Saídas", "requer Android 6.0 ou superior");
            return;
        }
        AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) {
            r.kv("Saídas", "indisponível");
            return;
        }
        AudioDeviceInfo[] devs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
        if (devs == null || devs.length == 0) {
            r.kv("Saídas", "nenhuma encontrada");
            return;
        }
        for (AudioDeviceInfo d : devs) {
            String name = typeName(d.getType());
            CharSequence prod = d.getProductName();
            String label = name + (prod != null && prod.length() > 0 ? " (" + prod + ")" : "");

            List<String> parts = new ArrayList<String>();
            int[] ch = d.getChannelCounts();
            if (ch != null && ch.length > 0) {
                StringBuilder sb = new StringBuilder("canais ");
                for (int i = 0; i < ch.length; i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(ch[i]);
                }
                parts.add(sb.toString());
            }
            int[] enc = d.getEncodings();
            if (enc != null) {
                for (int e : enc) {
                    String en = encodingName(e);
                    if (en != null) parts.add(en);
                }
            }
            r.kv(label, parts.isEmpty() ? "PCM padrão" : Sys.join(parts, " • "));
        }
        r.note("As capacidades de áudio do HDMI vêm da TV ou do receiver conectado (EDID).");
    }

    private static String typeName(int t) {
        switch (t) {
            case 1: return "Fone de ouvido (earpiece)";
            case 2: return "Alto-falante";
            case 3: return "Headset com fio";
            case 4: return "Fones de ouvido";
            case 5: return "Saída analógica";
            case 6: return "Saída digital";
            case 7: return "Bluetooth SCO";
            case 8: return "Bluetooth A2DP";
            case 9: return "HDMI";
            case 10: return "HDMI ARC";
            case 11: return "USB (dispositivo)";
            case 12: return "USB (acessório)";
            case 13: return "Dock";
            case 22: return "USB headset";
            case 29: return "HDMI eARC";
            default: return "Saída tipo " + t;
        }
    }

    /** Só nomeia os formatos "interessantes" (surround/streaming); ignora PCM comum. */
    private static String encodingName(int e) {
        switch (e) {
            case 5: return "Dolby Digital";
            case 6: return "Dolby Digital Plus";
            case 7: return "DTS";
            case 8: return "DTS-HD";
            case 14: return "Dolby TrueHD";
            case 18: return "Dolby Atmos (JOC)";
            case 19: return "Dolby MAT";
            default: return null;
        }
    }
}
