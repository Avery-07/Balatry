package client.engine;

import java.util.Random;

/**
 * Rolls the animated backdrop's colour palette: two vivid, backdrop-dark "main" colours guaranteed a wide hue
 * apart, plus a third dark colour that is a darkened blend of the two — the shadow/transition tone that sits between
 * them in the paint field. Purely cosmetic (the backdrop never syncs across seats), so it draws from a caller-owned
 * {@link Random} and is otherwise a pure function: seed it and the invariants below are unit-tested rather than
 * eyeballed. Also carries {@link #tension}, the 0..1 ramp the caller uses to agitate the field as the antes climb.
 */
public final class BackgroundPalette {

    private BackgroundPalette() { }

    /** Minimum hue separation (degrees) between the two main colours, so they always read as clearly different. */
    public static final double MIN_HUE_SEP = 100.0;
    private static final double MAX_HUE_SEP = 260.0;

    // Jewel-tone band: saturated but dark enough to sit behind the UI.
    private static final double SAT_LO = 0.65, SAT_SPAN = 0.20;
    private static final double BRI_LO = 0.40, BRI_SPAN = 0.15;

    /** How dark the third colour is relative to the blend of the two mains (a fraction of its brightness). */
    private static final double SHADOW = 0.28;

    /**
     * Rolls a palette as three 0xRRGGBB colours: {@code [main1, main2, shadow]}. The two mains are a random pair of
     * hues at least {@link #MIN_HUE_SEP} apart; the shadow is their darkened midpoint. Consumes only {@code rng}.
     */
    public static int[] roll(Random rng) {
        double hue1 = rng.nextDouble() * 360.0;
        double sep  = MIN_HUE_SEP + rng.nextDouble() * (MAX_HUE_SEP - MIN_HUE_SEP);
        double hue2 = (hue1 + sep) % 360.0;
        int c1 = hsb(hue1, SAT_LO + rng.nextDouble() * SAT_SPAN, BRI_LO + rng.nextDouble() * BRI_SPAN);
        int c2 = hsb(hue2, SAT_LO + rng.nextDouble() * SAT_SPAN, BRI_LO + rng.nextDouble() * BRI_SPAN);
        int c3 = darken(blend(c1, c2), SHADOW);
        return new int[] { c1, c2, c3 };
    }

    /**
     * The rising-tension ramp across a run: 0 at ante 1, 1 at the final ante, clamped (so endless antes past the
     * last stay pinned at full agitation). The caller lerps the field's churn/spin and the motes' speed by this.
     */
    public static double tension(int ante, int anteCount) {
        if (anteCount <= 1) return 0;
        double t = (ante - 1.0) / (anteCount - 1.0);
        return t < 0 ? 0 : t > 1 ? 1 : t;
    }

    // --- colour maths (all on 0xRRGGBB ints) ---------------------------------

    /** HSV/HSB to 0xRRGGBB: {@code h} in degrees (wrapped), {@code s} and {@code v} in [0,1]. */
    public static int hsb(double h, double s, double v) {
        h = ((h % 360) + 360) % 360;
        double c = v * s;
        double x = c * (1 - Math.abs((h / 60.0) % 2 - 1));
        double m = v - c;
        double r, g, b;
        if      (h <  60) { r = c; g = x; b = 0; }
        else if (h < 120) { r = x; g = c; b = 0; }
        else if (h < 180) { r = 0; g = c; b = x; }
        else if (h < 240) { r = 0; g = x; b = c; }
        else if (h < 300) { r = x; g = 0; b = c; }
        else              { r = c; g = 0; b = x; }
        return rgb((int) Math.round((r + m) * 255), (int) Math.round((g + m) * 255), (int) Math.round((b + m) * 255));
    }

    /** The channel-wise midpoint of two colours. */
    static int blend(int a, int b) {
        return rgb((((a >> 16) & 0xff) + ((b >> 16) & 0xff)) / 2,
                   (((a >> 8) & 0xff) + ((b >> 8) & 0xff)) / 2,
                   ((a & 0xff) + (b & 0xff)) / 2);
    }

    /** Scales a colour's channels toward black by {@code factor} (0 = black, 1 = unchanged). */
    static int darken(int c, double factor) {
        return rgb((int) (((c >> 16) & 0xff) * factor),
                   (int) (((c >> 8) & 0xff) * factor),
                   (int) ((c & 0xff) * factor));
    }

    private static int rgb(int r, int g, int b) { return (clamp8(r) << 16) | (clamp8(g) << 8) | clamp8(b); }
    private static int clamp8(int v) { return v < 0 ? 0 : v > 255 ? 255 : v; }
}
