package client.engine;

/**
 * A 1D damped spring: the value chases a target under a stiffness and damping, carrying its velocity through so it
 * can overshoot and settle — the organic, interruption-friendly alternative to a fixed-duration {@link Tween}.
 * Underdamped it bounces (the "juice" on a pop); critically or over-damped it eases in without overshoot. It snaps
 * exactly onto the target once it comes to rest, so a settled spring reads as truly done rather than asymptotically
 * close. Pure and unit-tested; no rendering.
 */
public final class Spring {

    // Below these, the spring is treated as at rest and snapped exactly onto the target (a real spring is only ever
    // asymptotically close; callers want an exact resting value so "settled" can be a clean equality).
    private static final double REST_X = 1e-4, REST_V = 1e-3;

    private double value;
    private double velocity;
    private double target;
    private final double stiffness;
    private final double damping;

    public Spring(double value, double stiffness, double damping) {
        this.value = value;
        this.target = value;
        this.stiffness = stiffness;
        this.damping = damping;
    }

    /** Points the spring at a new rest value; the current value and velocity carry, so it glides (or bounces) there. */
    public void setTarget(double t) { this.target = t; }

    /** Jumps value and target to {@code v} with no motion — kills any in-flight bounce. */
    public void snap(double v) { this.value = v; this.target = v; this.velocity = 0; }

    /** Instantly displaces the value by {@code delta} (a pop kick); the spring then pulls it back toward the target. */
    public void push(double delta) { this.value += delta; }

    /** Injects velocity without moving the value — the other way to excite the spring. */
    public void kick(double v) { this.velocity += v; }

    /**
     * Integrates one frame. Sub-stepped at a fixed inner tick so a large {@code dt} (a stutter, a slow frame) stays
     * stable rather than exploding — semi-implicit Euler is only conditionally stable, and a stiff spring at a big
     * step would otherwise diverge.
     */
    public void advance(double dt) {
        if (dt <= 0) return;
        int steps = Math.max(1, (int) Math.ceil(dt / 0.008));
        double h = dt / steps;
        for (int i = 0; i < steps; i++) {
            double accel = -stiffness * (value - target) - damping * velocity;
            velocity += accel * h;
            value += velocity * h;
        }
        if (Math.abs(value - target) < REST_X && Math.abs(velocity) < REST_V) { value = target; velocity = 0; }
    }

    public double value()    { return value; }
    public double velocity() { return velocity; }
    public double target()   { return target; }

    /** True once the spring has come to rest exactly on its target (see the auto-snap in {@link #advance}). */
    public boolean settled() { return value == target && velocity == 0; }
}
