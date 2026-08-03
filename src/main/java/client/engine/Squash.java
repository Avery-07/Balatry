package client.engine;

/**
 * Velocity-driven squash &amp; stretch: turns a moving thing's velocity into a pair of axis-aligned scale factors so
 * it stretches along the direction it travels and squashes across it — the deformation that makes cards feel like
 * they have weight rather than sliding like decals. Pure functions of velocity (no state), so the feel is tuned by
 * three constants and unit-tested rather than eyeballed.
 *
 * <p>The split is axis-aligned (it reads the x and y speed components independently) rather than truly rotated to
 * the velocity angle, because the draw layer scales a card's width and height directly — and almost all card motion
 * here is vertical (dealt in, played up) or horizontal (reordered, glided home), where the two agree. Both factors
 * return exactly 1 at rest, and {@link #CROSS} is tuned so a fast move very nearly preserves area.
 */
public final class Squash {

    private Squash() { }

    /** Speed (px/sec) at which the stretch saturates; faster than this looks the same. */
    public static final double REF_SPEED = 1500;
    /** Peak stretch (and squash) as a fraction — a card moving flat-out is at most this much longer along its travel. */
    public static final double MAX = 0.22;
    /** How strongly motion on one axis squashes the other; ~0.8 keeps a saturated stretch close to area-preserving. */
    public static final double CROSS = 0.8;

    /** The horizontal scale factor for a thing moving at ({@code vx},{@code vy}) px/sec — {@code 1} at rest. */
    public static double scaleX(double vx, double vy) { return axis(vx, vy); }

    /** The vertical scale factor for a thing moving at ({@code vx},{@code vy}) px/sec — {@code 1} at rest. */
    public static double scaleY(double vx, double vy) { return axis(vy, vx); }

    /** Stretch along {@code along}, squashed by motion on the perpendicular {@code cross}; clamped to [1-MAX, 1+MAX]. */
    private static double axis(double along, double cross) {
        double a = Math.min(1, Math.abs(along) / REF_SPEED);
        double c = Math.min(1, Math.abs(cross) / REF_SPEED);
        double s = 1 + MAX * a - MAX * CROSS * c;
        return s < 1 - MAX ? 1 - MAX : s > 1 + MAX ? 1 + MAX : s;
    }
}
