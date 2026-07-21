package client.engine;

/**
 * The permanent, ambient motion every card carries — a slow breathing sway so nothing on the table ever sits
 * frozen. Pure functions of (time, seed): no state, no allocation, trivially testable. The seed (a card id, a
 * slot index) de-phases neighbours so a row of cards ripples instead of marching in lockstep.
 */
public final class Idle {

    private Idle() { }

    /** A gentle rotation in degrees, within ±{@code amplitudeDeg}. */
    public static double swayDeg(double time, int seed, double amplitudeDeg) {
        return Math.sin(time * 1.1 + seed * 2.399) * amplitudeDeg;
    }

    /** A vertical bob in pixels, within ±{@code amplitudePx}, deliberately off-tempo from the sway. */
    public static double bobPx(double time, int seed, double amplitudePx) {
        return Math.sin(time * 0.9 + seed * 1.731) * amplitudePx;
    }
}
