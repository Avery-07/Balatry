package client.engine;

import java.util.stream.IntStream;

/**
 * The Balatro-style swirling paint backdrop, as pure math: for each pixel a polar swirl, an iterated domain warp,
 * then three-colour banding. Rendered small and stretched, so the chunkiness is the look.
 *
 * <p>It is a per-pixel fragment shader run on the CPU, which is expensive, so two things keep it affordable:
 * <ul>
 *   <li>the per-pixel setup (the {@code sqrt} and {@code atan2}) depends only on pixel position and two rarely-
 *       changing knobs, so it is precomputed once into {@link #baseAngle}/{@link #rZoom} tables and reused every
 *       frame — rebuilt only when {@code zoom}/{@code spinAmount}/the resolution actually change; and</li>
 *   <li>{@link #renderParallel} splits the rows across cores and takes an immutable {@link Config} snapshot, so the
 *       caller can run it off the JavaFX thread without racing the live knobs the transition lerp is writing.</li>
 * </ul>
 * The public knobs remain for the reference {@link #render(int[], double)} path (and the tests); the threaded path
 * reads only its {@code Config}. Purely cosmetic — it never reads the model or snapshot.
 */
public final class PaintField {

    private final int w, h;
    private final double norm;
    private final double invNorm;

    // --- tuning (the reference path and callers read/write these; the threaded path snapshots them into a Config) ---

    public int warpSteps = 4;
    public double spinSpeed = 0.025;
    public double paintSpeed = 1;
    public double spinAmount = 0.22;
    public double zoom = 26;
    public double contrast = 2.1;
    public int colour1 = 0x750000;
    public int colour2 = 0x003375;
    public int colour3 = 0x220826;

    // --- precomputed per-pixel setup, keyed by the (zoom, spinAmount) it was built for; only ever touched by the
    //     single in-flight render (see the class note), so it needs no synchronisation of its own ---
    private double[] baseAngle, rZoom;
    private double tblZoom = Double.NaN, tblSpinAmount = Double.NaN;

    public PaintField(int width, int height) {
        this.w = Math.max(1, width);
        this.h = Math.max(1, height);
        this.norm = Math.sqrt((double) w * w + (double) h * h);
        this.invNorm = 1.0 / norm;
    }

    public int width()  { return w; }
    public int height() { return h; }

    /**
     * An immutable snapshot of the per-pixel tuning knobs — taken on the caller's thread so a worker never reads them
     * live. The animation <em>phase</em> ({@code spin}/{@code churn}) is not here: it is integrated frame-by-frame by
     * the caller and passed in, so a speed change never retroactively rescales the elapsed phase (which would twirl).
     */
    public record Config(int warpSteps, double spinAmount, double zoom, double contrast,
                         int colour1, int colour2, int colour3) { }

    /** Snapshots the current knobs; call on the thread that owns them (never mid-render on a worker). */
    public Config config() {
        return new Config(warpSteps, spinAmount, zoom, contrast, colour1, colour2, colour3);
    }

    // --- reference path (single-threaded, field-driven): kept for callers and the unit tests ---

    /** Renders at the given animation phase ({@code spin} = accumulated rotation, {@code churn} = accumulated warp). */
    public void render(int[] out, double spin, double churn) {
        Config cfg = config();
        double halfW = w * 0.5, halfH = h * 0.5;
        for (int py = 0; py < h; py++) {
            int index = py * w;
            double y = (py + 0.5 - halfH) * invNorm;
            for (int px = 0; px < w; px++) {
                double x = (px + 0.5 - halfW) * invNorm;
                out[index + px] = shade(x, y, spin, churn, cfg);
            }
        }
    }

    /** The reference shade: the full per-pixel swirl (with the inline {@code sqrt}/{@code atan2}), then the warp+colour. */
    public int shade(double x, double y, double spin, double churn, Config cfg) {
        double radius = Math.sqrt(x * x + y * y);
        double angle = Math.atan2(y, x) - spin + cfg.spinAmount() * 6.0 * radius;
        double ux = radius * Math.cos(angle) * cfg.zoom();
        double uy = radius * Math.sin(angle) * cfg.zoom();
        return warpColour(ux, uy, churn, cfg);
    }

    // --- threaded, precomputed path (what the live backdrop uses) ---

    /**
     * Renders a full frame into {@code out} for {@code cfg}, rows split across cores. Rebuilds the per-pixel setup
     * tables first if {@code zoom}/{@code spinAmount} changed. Run this off the JavaFX thread, one at a time (the
     * tables and {@code out} assume a single in-flight render); the row split then uses the common ForkJoin pool.
     */
    public void renderParallel(int[] out, double spin, double churn, Config cfg) {
        ensureTables(cfg);
        IntStream.range(0, h).parallel().forEach(py -> {
            int row = py * w;
            for (int px = 0; px < w; px++) out[row + px] = shadeFast(row + px, spin, churn, cfg);
        });
    }

    /** The fast shade: the polar setup comes from the tables, only the time-varying rotation stays per-frame. */
    private int shadeFast(int idx, double spin, double churn, Config cfg) {
        double angle = baseAngle[idx] - spin;
        double rz = rZoom[idx];
        return warpColour(rz * Math.cos(angle), rz * Math.sin(angle), churn, cfg);
    }

    /** Rebuilds the {@code sqrt}/{@code atan2}-derived tables when the resolution or the (zoom, spinAmount) they depend on change. */
    private void ensureTables(Config cfg) {
        if (baseAngle != null && cfg.zoom() == tblZoom && cfg.spinAmount() == tblSpinAmount) return;
        if (baseAngle == null) { baseAngle = new double[w * h]; rZoom = new double[w * h]; }
        double halfW = w * 0.5, halfH = h * 0.5;
        for (int py = 0; py < h; py++) {
            int row = py * w;
            double y = (py + 0.5 - halfH) * invNorm;
            for (int px = 0; px < w; px++) {
                double x = (px + 0.5 - halfW) * invNorm;
                double radius = Math.sqrt(x * x + y * y);
                baseAngle[row + px] = Math.atan2(y, x) + cfg.spinAmount() * 6.0 * radius;
                rZoom[row + px] = radius * cfg.zoom();
            }
        }
        tblZoom = cfg.zoom();
        tblSpinAmount = cfg.spinAmount();
    }

    // --- the shared warp + colour math (identical for both paths, given ux/uy) ---

    private int warpColour(double ux, double uy, double churn, Config cfg) {
        double vx = ux + uy;
        double churnA = churn * 0.131;
        double churnB = -0.113 * churn;

        for (int i = 0; i < cfg.warpSteps(); i++) {
            vx += Math.sin(Math.max(ux, uy)) + ux;
            double nx = ux + 0.5 * Math.cos(5.112 + 0.353 * vx + churnA);
            double ny = uy + 0.5 * Math.sin(vx + churnB);
            double fold = Math.cos(nx + ny) - Math.sin(nx * 0.711 - ny);
            ux = nx - fold;
            uy = ny - fold;
        }

        double contrast = cfg.contrast();
        double paint = Math.sqrt(ux * ux + uy * uy) * 0.035 * contrast;
        if (paint < 0) paint = 0; else if (paint > 2) paint = 2;

        double b1 = 1 - contrast * Math.abs(1 - paint);
        if (b1 < 0) b1 = 0;
        double b2 = 1 - contrast * Math.abs(paint);
        if (b2 < 0) b2 = 0;
        double b3 = 1 - Math.min(1, b1 + b2);

        double floor = 0.3 / contrast;
        double body = 1 - floor;
        double glow = 0.30 * Math.max(0, b1 * 5 - 4) + 0.40 * Math.max(0, b2 * 5 - 4);

        double r1 = ((cfg.colour1() >> 16) & 0xff) / 255.0, g1 = ((cfg.colour1() >> 8) & 0xff) / 255.0, b1c = (cfg.colour1() & 0xff) / 255.0;
        double r2 = ((cfg.colour2() >> 16) & 0xff) / 255.0, g2 = ((cfg.colour2() >> 8) & 0xff) / 255.0, b2c = (cfg.colour2() & 0xff) / 255.0;
        double r3 = ((cfg.colour3() >> 16) & 0xff) / 255.0, g3 = ((cfg.colour3() >> 8) & 0xff) / 255.0, b3c = (cfg.colour3() & 0xff) / 255.0;

        double r = floor * r1 + body * (r1 * b1 + r2 * b2 + r3 * b3) + glow;
        double g = floor * g1 + body * (g1 * b1 + g2 * b2 + g3 * b3) + glow;
        double b = floor * b1c + body * (b1c * b1 + b2c * b2 + b3c * b3) + glow;

        return 0xff000000 | (channel(r) << 16) | (channel(g) << 8) | channel(b);
    }

    private static int channel(double value) {
        if (value < 0) value = 0; else if (value > 1) value = 1;
        return (int) (value * 255 + 0.5);
    }
}
