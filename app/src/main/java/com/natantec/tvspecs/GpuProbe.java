package com.natantec.tvspecs;

import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;

/** Descobre o modelo da GPU criando um contexto OpenGL ES mínimo (sem tela). */
final class GpuProbe {
    private GpuProbe() {
    }

    /** Retorna {renderer, vendor, versão GL, versão GLSL, textura máx, nº de extensões} ou null. */
    static String[] probe() {
        EGLDisplay dpy = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (dpy == null || dpy.equals(EGL14.EGL_NO_DISPLAY)) return null;
        int[] ver = new int[2];
        if (!EGL14.eglInitialize(dpy, ver, 0, ver, 1)) return null;

        EGLContext ctx = EGL14.EGL_NO_CONTEXT;
        EGLSurface surf = EGL14.EGL_NO_SURFACE;
        try {
            int[] attr = {
                    EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                    EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                    EGL14.EGL_NONE
            };
            EGLConfig[] cfg = new EGLConfig[1];
            int[] n = new int[1];
            if (!EGL14.eglChooseConfig(dpy, attr, 0, cfg, 0, 1, n, 0) || n[0] < 1) return null;

            int[] ctxAttr = {EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE};
            ctx = EGL14.eglCreateContext(dpy, cfg[0], EGL14.EGL_NO_CONTEXT, ctxAttr, 0);
            int[] surfAttr = {EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE};
            surf = EGL14.eglCreatePbufferSurface(dpy, cfg[0], surfAttr, 0);
            if (!EGL14.eglMakeCurrent(dpy, surf, surf, ctx)) return null;

            String renderer = GLES20.glGetString(GLES20.GL_RENDERER);
            String vendor = GLES20.glGetString(GLES20.GL_VENDOR);
            String version = GLES20.glGetString(GLES20.GL_VERSION);
            String glsl = GLES20.glGetString(GLES20.GL_SHADING_LANGUAGE_VERSION);
            String ext = GLES20.glGetString(GLES20.GL_EXTENSIONS);
            int[] tex = new int[1];
            GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, tex, 0);
            int extCount = (ext == null || ext.trim().length() == 0) ? 0 : ext.trim().split("\\s+").length;
            return new String[]{
                    renderer == null ? "" : renderer,
                    vendor == null ? "" : vendor,
                    version == null ? "" : version,
                    glsl == null ? "" : glsl,
                    tex[0] + " px",
                    String.valueOf(extCount)
            };
        } finally {
            EGL14.eglMakeCurrent(dpy, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
            if (surf != null && !surf.equals(EGL14.EGL_NO_SURFACE)) EGL14.eglDestroySurface(dpy, surf);
            if (ctx != null && !ctx.equals(EGL14.EGL_NO_CONTEXT)) EGL14.eglDestroyContext(dpy, ctx);
        }
    }
}
