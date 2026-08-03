package client.engine;

import java.util.function.DoubleUnaryOperator;

/**
 * Easing curves, each mapping a normalized time {@code t} in [0,1] to an eased progress. Pure and
 * rendering-agnostic so the animation feel is unit-tested rather than eyeballed. {@code easeOutBack}
 * deliberately overshoots past 1 before settling — the little pop that gives cards their "juice".
 */
public final class Easing {

    private Easing() { }

    public static final DoubleUnaryOperator LINEAR             = Easing::linear;
    public static final DoubleUnaryOperator EASE_OUT_CUBIC     = Easing::easeOutCubic;
    public static final DoubleUnaryOperator EASE_IN_OUT         = Easing::easeInOutQuad;
    public static final DoubleUnaryOperator EASE_OUT_BACK       = Easing::easeOutBack;
    public static final DoubleUnaryOperator EASE_OUT_BACK_SOFT  = Easing::easeOutBackSoft;

    public static double linear(double t) { return clamp01(t); }

    public static double easeOutCubic(double t) {
        t = clamp01(t);
        double u = 1 - t;
        return 1 - u * u * u;
    }

    public static double easeInOutQuad(double t) {
        t = clamp01(t);
        return t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2;
    }

    /** Overshoots above 1 near the end, then settles to exactly 1 — the card "pop". */
    public static double easeOutBack(double t) {
        t = clamp01(t);
        double c1 = 1.70158, c3 = c1 + 1, u = t - 1;
        return 1 + c3 * u * u * u + c1 * u * u;
    }

    /** {@link #easeOutBack} with a gentler overshoot (~5%) — the subtle settle bounce for a card or tile landing. */
    public static double easeOutBackSoft(double t) {
        t = clamp01(t);
        double c1 = 0.9, c3 = c1 + 1, u = t - 1;
        return 1 + c3 * u * u * u + c1 * u * u;
    }

    public static double clamp01(double t) { return t < 0 ? 0 : t > 1 ? 1 : t; }
}
