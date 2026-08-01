package client.game;

import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;

/**
 * The animated shimmer for card editions, computed the way Balatro's own {@code foil} shader does: a per-pixel
 * pattern of overlapping radial ripples, an angular streak, and two axis-aligned bands, combined into a single
 * intensity {@code maxfac}. That intensity drives three small (card-sized) buffers rebuilt a few times a second on
 * the frame clock — Foil (blue-shifted, exactly as the shader tints it), Holographic (the same pattern mapped to a
 * rainbow hue), and Polychrome (a smooth flowing spectrum). {@link Renderer} blits the right buffer over each card
 * with a SCREEN blend, so the pattern lightens the art without a fragment shader (JavaFX Canvas has none).
 *
 * <p>Reference: the community Godot port of Balatro's foil shader (CC0), godotshaders.com/shader/balatro-foil-card-effect.
 */
final class EditionArt {

    private static final int W = 71, H = 95;   // one card cell; blitted scaled onto every card
    private static final double REBUILD_HZ = 24;

    private final WritableImage foil = new WritableImage(W, H);
    private final WritableImage holo = new WritableImage(W, H);
    private final WritableImage poly = new WritableImage(W, H);
    private final int[] fBuf = new int[W * H], hBuf = new int[W * H], pBuf = new int[W * H];
    private double nextBuild = -1;

    Image foil() { return foil; }
    Image holo() { return holo; }
    Image poly() { return poly; }

    /** Rebuilds the buffers if enough frame time has passed since the last (throttled to {@link #REBUILD_HZ}). */
    void ensure(double t) {
        if (t < nextBuild) return;
        nextBuild = t + 1.0 / REBUILD_HZ;
        build(t);
    }

    private void build(double t) {
        for (int py = 0; py < H; py++) {
            for (int px = 0; px < W; px++) {
                double u = (px + 0.5) / W, v = (py + 0.5) / H;
                double ax = u - 0.5, ay = v - 0.5;   // adjusted_uv, the shader's centred coords
                double m = pattern(ax, ay, u, v, t);

                double fi = clamp(m * 0.16, 0, 1);   // Foil: the shader boosts blue hard, r/g gently
                fBuf[py * W + px] = argb(255, fi * 0.30, fi * 0.55, Math.min(1, fi * 1.5));

                double hi = clamp(m * 0.15, 0, 0.95);   // Holographic: same pattern, rainbow hue
                hBuf[py * W + px] = hsb(m * 0.10 + u * 0.5 + t * 0.15, 0.85, hi, 255);

                // Polychrome: a smooth diagonal spectrum flowing on the clock, independent of the ripple.
                pBuf[py * W + px] = hsb(u * 0.55 + v * 0.35 + t * 0.12, 0.9, 0.85, 150);
            }
        }
        var fmt = PixelFormat.getIntArgbInstance();
        foil.getPixelWriter().setPixels(0, 0, W, H, fmt, fBuf, 0, W);
        holo.getPixelWriter().setPixels(0, 0, W, H, fmt, hBuf, 0, W);
        poly.getPixelWriter().setPixels(0, 0, W, H, fmt, pBuf, 0, W);
    }

    /** The foil shader's {@code maxfac}: overlapping radial ripples + an angular streak + two axis bands. */
    private static double pattern(double ax, double ay, double u, double v, double t) {
        double l90 = Math.hypot(90 * ax, 90 * ay);
        double l113 = Math.hypot(113.1121 * ax, 113.1121 * ay);
        double fac = clamp(2 * Math.sin((l90 + t * 2) + 3 * (1 + 0.8 * Math.cos(l113 - t * 3.121))) - 1 - Math.max(5 - l90, 0), 0, 1);

        double rx = Math.cos(t * 0.1221), ry = Math.sin(t * 0.3512);
        double lauv = Math.hypot(ax, ay), lrot = Math.hypot(rx, ry);
        double angle = lauv < 1e-6 ? 0 : (rx * ax + ry * ay) / (lrot * lauv);
        double l20 = Math.hypot(20 * ax, 20 * ay);
        double fac2 = clamp(5 * Math.cos(t * 0.3 + angle * Math.PI * (2.2 + 0.9 * Math.sin(t * 1.65))) - 4 - Math.max(2 - l20, 0), 0, 1);

        double fac3 = 0.3 * clamp(2 * Math.sin(t * 5 + u * 3 + 3 * (1 + 0.5 * Math.cos(t * 7))) - 1, -1, 1);
        double fac4 = 0.3 * clamp(2 * Math.sin(t * 6.66 + v * 3.8 + 3 * (1 + 0.5 * Math.cos(t * 3.414))) - 1, -1, 1);

        double top = Math.max(Math.max(fac, Math.max(fac2, Math.max(fac3, Math.max(fac4, 0)))), 0);
        return Math.max(top + 2.2 * (fac + fac2 + fac3 + fac4), 0);
    }

    private static double clamp(double x, double lo, double hi) { return Math.max(lo, Math.min(hi, x)); }

    private static int argb(int a, double r, double g, double b) {
        return (a << 24) | (ch(r) << 16) | (ch(g) << 8) | ch(b);
    }

    private static int ch(double v) { return (int) (clamp(v, 0, 1) * 255 + 0.5); }

    /** {@code h} wraps to [0,1); s,v,alpha in [0,1] / [0,255]. A small inline HSV→RGB (per-pixel, so no allocation). */
    private static int hsb(double h, double s, double v, int alpha) {
        double hh = (h - Math.floor(h)) * 6.0;
        int i = (int) hh;
        double f = hh - i, p = v * (1 - s), q = v * (1 - s * f), tt = v * (1 - s * (1 - f));
        double r, g, b;
        switch (i) {
            case 0 -> { r = v; g = tt; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = tt; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = tt; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return argb(alpha, r, g, b);
    }
}
