package client.game;

import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;

/**
 * Per-edition overlay patterns, one small (card-sized) buffer each, rebuilt a few times a second on the frame clock
 * and blitted over each card by {@link Renderer#editionEffect} with an edition-specific blend. JavaFX Canvas has no
 * fragment shader and cards are drawn under rotation transforms (so their pixels can't be cheaply read back), so
 * each effect is expressed as a pattern-over-the-card blend rather than a true per-pixel transform of the art:
 *
 * <ul>
 *   <li><b>Foil</b> — a lighter foil-blue streak sweeping from centre to edge like a clock hand; SCREEN (additive).</li>
 *   <li><b>Holographic</b> — a non-uniform hue laid out in a triangular grid, static (no animation); OVERLAY.</li>
 *   <li><b>Polychrome</b> — a smooth, spatially-varying hue that flows over time; OVERLAY.</li>
 *   <li><b>Negative</b> — a bright, gently drifting field blitted with DIFFERENCE, which flips the card toward its
 *       near-complement (so it reads as an inverted card rather than a tint).</li>
 * </ul>
 *
 * Poly/Holo are OVERLAY colourisations (they read as a shifting hue) rather than a literal hue rotation of the art;
 * a true rotation would need the card rendered off-screen first (see the class note).
 */
final class EditionArt {

    private static final int W = 71, H = 95;   // one card cell; blitted scaled onto every card
    private static final double REBUILD_HZ = 24;

    private final WritableImage foil = new WritableImage(W, H);
    private final WritableImage holo = new WritableImage(W, H);
    private final WritableImage poly = new WritableImage(W, H);
    private final WritableImage neg  = new WritableImage(W, H);
    private final int[] fBuf = new int[W * H], hBuf = new int[W * H], pBuf = new int[W * H], nBuf = new int[W * H];
    private double nextBuild = -1;
    private boolean holoBuilt;   // Holographic is static — build its buffer once

    Image foil() { return foil; }
    Image holo() { return holo; }
    Image poly() { return poly; }
    Image neg()  { return neg; }

    /** Rebuilds the animated buffers if enough frame time has passed since the last (throttled to {@link #REBUILD_HZ}). */
    void ensure(double t) {
        if (!holoBuilt) { buildHolo(); holoBuilt = true; }
        if (t < nextBuild) return;
        nextBuild = t + 1.0 / REBUILD_HZ;
        build(t);
    }

    private void build(double t) {
        var fmt = PixelFormat.getIntArgbInstance();
        for (int py = 0; py < H; py++) {
            for (int px = 0; px < W; px++) {
                double u = (px + 0.5) / W, v = (py + 0.5) / H;
                double ax = u - 0.5, ay = v - 0.5;
                double r = clamp(Math.hypot(ax, ay) * 2.0, 0, 1);   // 0 centre -> 1 edge

                // FOIL — a lighter streak sweeping around like a clock hand, brighter toward the edge. A wider,
                // brighter beam than a pin-thin one so it actually reads as it sweeps.
                double beam = t * 0.9;                               // the hand's rotation
                double da = angleDiff(Math.atan2(ay, ax), beam);
                double streak = Math.pow(Math.max(0, Math.cos(da)), 4);
                double fi = clamp(streak * (0.45 + 1.15 * r), 0, 1);
                fBuf[py * W + px] = argb(255, fi * 0.6, fi * 0.82, fi);

                // POLYCHROME — a smooth non-uniform hue that flows with time.
                double ph = u * 0.6 + v * 0.35 + 0.15 * Math.sin((u + v) * 6.0 + t) + t * 0.10;
                pBuf[py * W + px] = hsb(ph, 0.95, 0.78, 255);

                // NEGATIVE — a NEAR-WHITE, slowly drifting field: DIFFERENCE against it flips the card toward its
                // complement (a photo-negative), the faint hue giving it just a tint rather than a colour wash.
                double nh = u * 0.5 + v * 0.4 + t * 0.06;
                nBuf[py * W + px] = hsb(nh, 0.10, 1.0, 255);
            }
        }
        foil.getPixelWriter().setPixels(0, 0, W, H, fmt, fBuf, 0, W);
        poly.getPixelWriter().setPixels(0, 0, W, H, fmt, pBuf, 0, W);
        neg.getPixelWriter().setPixels(0, 0, W, H, fmt, nBuf, 0, W);
    }

    /** HOLOGRAPHIC — a non-uniform hue per triangle of a triangular grid; static, so built once. */
    private void buildHolo() {
        for (int py = 0; py < H; py++) {
            for (int px = 0; px < W; px++) {
                double u = (px + 0.5) / W, v = (py + 0.5) / H;
                hBuf[py * W + px] = hsb(triangleHue(u, v), 0.9, 0.85, 255);
            }
        }
        holo.getPixelWriter().setPixels(0, 0, W, H, PixelFormat.getIntArgbInstance(), hBuf, 0, W);
    }

    /** A scrambled-but-fixed hue that is constant within each triangle of a triangular tiling of the card. */
    private static double triangleHue(double u, double v) {
        int n = 5;
        double gx = u * n, gy = v * n;
        int cx = (int) gx, cy = (int) gy;
        int tri = (gx - cx) + (gy - cy) < 1.0 ? 0 : 1;   // the cell's lower-left vs upper-right triangle
        return cx * 0.17 + cy * 0.11 + tri * 0.37;        // wraps in hsb; adjacent triangles differ
    }

    /** Signed smallest angle from {@code b} to {@code a}, in radians, wrapped to [-pi, pi]. */
    private static double angleDiff(double a, double b) {
        return Math.atan2(Math.sin(a - b), Math.cos(a - b));
    }

    private static double clamp(double x, double lo, double hi) { return Math.max(lo, Math.min(hi, x)); }

    private static int argb(int a, double r, double g, double b) {
        return (a << 24) | (ch(r) << 16) | (ch(g) << 8) | ch(b);
    }

    private static int ch(double v) { return (int) (clamp(v, 0, 1) * 255 + 0.5); }

    /** {@code h} wraps to [0,1); s,v in [0,1], alpha in [0,255]. A small inline HSV→RGB (per-pixel, no allocation). */
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
