package client.engine;

/**
 * The swirling "liquid paint" field behind the whole client — the effect Balatro made famous, as pure math. No
 * textures, no particles: every pixel is computed from its own position and the clock, which is why it never
 * repeats and costs nothing to store.
 *
 * <p>Four stages per pixel:
 * <ol>
 *   <li><em>Swirl.</em> The pixel is read in polar coordinates around centre and its angle advanced by an amount
 *       that grows with radius and with time, winding the field into a slowly turning spiral.</li>
 *   <li><em>Warp.</em> The swirled coordinate is folded through itself a few times, each pass offsetting it by
 *       sines and cosines of its own components plus time. This iterated domain warping produces the organic,
 *       marbled turbulence.</li>
 *   <li><em>Band.</em> The warped distance from the origin selects between three paint colours through
 *       overlapping bands, so they blend rather than step.</li>
 *   <li><em>Glow.</em> Where a band peaks it is boosted, giving the bright veins that read as light catching
 *       wet paint.</li>
 * </ol>
 *
 * <p>Rendered into a small buffer and stretched by the caller: the resulting chunkiness is the look, not a
 * compromise. Pure and unit-tested (no JavaFX) — {@code client.game.Background} owns the buffer and the drawing.
 */
public final class PaintField {

    private final int w, h;
    private final double norm;

    // --- tuning (all safe to change; nothing else depends on these) ---

    /** Domain-warping passes: the turbulence's complexity, and the most expensive dial (4 trig calls each). */
    public int warpSteps = 4;

    /** How fast the field spirals, and how fast the paint churns. */
    public double spinSpeed = 0.025, paintSpeed = 1;

    /** How strongly the swirl's angle grows with radius — the spiral's tightness. */
    public double spinAmount = 0.22;

    /** Zoom into the paint: larger means finer, busier marbling. */
    public double zoom = 26;

    /** Band sharpness. Higher pushes the colours apart into harder-edged veins. */
    public double contrast = 2.1;

    /** The three paint colours as 0xRRGGBB: body, veins, shadowed troughs. */
    public int colour1 = 0x750000, colour2 = 0x003375, colour3 = 0x220826;
    // public int colour1 = 0x1d5a49, colour2 = 0x2f8f6b, colour3 = 0x0d1f1a;

    public PaintField(int width, int height) {
        this.w = Math.max(1, width);
        this.h = Math.max(1, height);
        this.norm = Math.hypot(this.w, this.h);
    }

    public int width()  { return w; }
    public int height() { return h; }

    /**
     * Renders one frame into {@code out} (length {@code width*height}) as packed ARGB, fully opaque.
     *
     * @param time seconds since start; the field's only clock
     */
    public void render(int[] out, double time) {
        double spin = time * spinSpeed, churn = time * paintSpeed;
        double halfW = w / 2.0, halfH = h / 2.0;
        for (int py = 0; py < h; py++) {
            for (int px = 0; px < w; px++) {
                // Centre and normalise by the diagonal, so the field keeps its shape at any aspect ratio.
                out[py * w + px] = shade((px + 0.5 - halfW) / norm, (py + 0.5 - halfH) / norm, spin, churn);
            }
        }
    }

    /** One pixel: swirl, warp, band, glow. Packed ARGB. */
    public int shade(double x, double y, double spin, double churn) {
        double radius = Math.hypot(x, y);
        double angle = Math.atan2(y, x) - spin + spinAmount * 6.0 * radius;
        double ux = radius * Math.cos(angle) * zoom;
        double uy = radius * Math.sin(angle) * zoom;

        double vx = ux + uy;
        for (int i = 0; i < warpSteps; i++) {
            vx += Math.sin(Math.max(ux, uy)) + ux;
            double nx = ux + 0.5 * Math.cos(5.112 + 0.353 * vx + churn * 0.131);
            double ny = uy + 0.5 * Math.sin(vx - 0.113 * churn);
            double fold = Math.cos(nx + ny) - Math.sin(nx * 0.711 - ny);
            ux = nx - fold;
            uy = ny - fold;
        }

        double paint = clamp(Math.hypot(ux, uy) * 0.035 * contrast, 0, 2);
        double b1 = Math.max(0, 1 - contrast * Math.abs(1 - paint));
        double b2 = Math.max(0, 1 - contrast * Math.abs(paint));
        double b3 = 1 - Math.min(1, b1 + b2);

        // A flat floor of colour1 keeps the darkest troughs from going muddy; the peaks get the highlight boost.
        double floor = 0.3 / contrast, body = 1 - floor;
        double glow = 0.30 * Math.max(0, b1 * 5 - 4) + 0.40 * Math.max(0, b2 * 5 - 4);

        double r = mix(red(colour1),   red(colour2),   red(colour3),   b1, b2, b3, floor, body, glow);
        double g = mix(green(colour1), green(colour2), green(colour3), b1, b2, b3, floor, body, glow);
        double b = mix(blue(colour1),  blue(colour2),  blue(colour3),  b1, b2, b3, floor, body, glow);
        return 0xff000000 | (channel(r) << 16) | (channel(g) << 8) | channel(b);
    }

    private static double mix(double c1, double c2, double c3,
                              double b1, double b2, double b3,
                              double floor, double body, double glow) {
        return floor * c1 + body * (c1 * b1 + c2 * b2 + c3 * b3) + glow;
    }

    private static double red(int rgb)   { return ((rgb >> 16) & 0xff) / 255.0; }
    private static double green(int rgb) { return ((rgb >> 8) & 0xff) / 255.0; }
    private static double blue(int rgb)  { return (rgb & 0xff) / 255.0; }

    private static int channel(double v) { return (int) (clamp(v, 0, 1) * 255 + 0.5); }

    private static double clamp(double v, double lo, double hi) { return v < lo ? lo : Math.min(v, hi); }
}
