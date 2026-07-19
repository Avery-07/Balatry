package client.engine;

import java.util.function.DoubleUnaryOperator;

/**
 * A scalar animated toward a target over a fixed duration with an easing curve. The game loop calls
 * {@link #advance(double)} each frame with the elapsed seconds; {@link #value()} reads the current position.
 * {@link #retarget(double)} redirects the animation from wherever it currently is — so a card interrupted
 * mid-flight glides to the new spot instead of snapping. Pure and unit-tested; no rendering.
 */
public final class Tween {

    private double from;
    private double to;
    private final double duration;      // seconds
    private double elapsed;
    private final DoubleUnaryOperator ease;

    public Tween(double value, double durationSeconds, DoubleUnaryOperator ease) {
        this.from = value;
        this.to = value;
        this.duration = Math.max(0, durationSeconds);
        this.elapsed = this.duration;   // starts settled
        this.ease = ease;
    }

    /** Redirects toward {@code target} starting from the current value; a no-op if already heading there. */
    public void retarget(double target) {
        if (target == to) return;
        this.from = value();
        this.to = target;
        this.elapsed = 0;
    }

    /** Jumps immediately to {@code v} with no animation. */
    public void snap(double v) { this.from = v; this.to = v; this.elapsed = duration; }

    public void advance(double dt) { if (dt > 0) elapsed = Math.min(duration, elapsed + dt); }

    public double value() {
        if (duration <= 0 || elapsed >= duration) return to;
        return from + (to - from) * ease.applyAsDouble(elapsed / duration);
    }

    public double target()  { return to; }
    public boolean done()   { return elapsed >= duration; }
}
