package client.engine;

import java.util.function.DoubleUnaryOperator;

/**
 * A push/slide screen transition for the single-canvas renderer. Over a fixed duration the outgoing panel slides
 * off toward one edge while the incoming panel slides in from another — both moving at once, no fade to black. The
 * two directions are independent, so different panels can leave and arrive along different axes (a shop can push out
 * to the right while the next screen drops in from the top).
 *
 * <p>Pure math and unit-tested: it only computes normalized offsets from an eased clock. The renderer supplies the
 * travel distance (usually a screen dimension) and blits its captured panels at {@link #outX}/{@link #outY} (the
 * outgoing image) and {@link #inX}/{@link #inY} (the incoming one). No rendering, no canvas, no model.
 */
public final class SlideTransition {

    /** A screen edge a panel slides toward (exit) or arrives from (enter). */
    public enum Dir {
        NONE(0, 0), LEFT(-1, 0), RIGHT(1, 0), UP(0, -1), DOWN(0, 1);
        public final int dx, dy;
        Dir(int dx, int dy) { this.dx = dx; this.dy = dy; }
    }

    private final double duration;               // seconds
    private final DoubleUnaryOperator ease;
    private double elapsed;
    private boolean running;
    private Dir exitDir = Dir.NONE, enterDir = Dir.NONE;

    public SlideTransition(double durationSeconds, DoubleUnaryOperator ease) {
        this.duration = Math.max(1e-4, durationSeconds);
        this.ease = ease;
    }

    /**
     * Begins a transition: the outgoing panel leaves toward {@code exit}, the incoming one arrives from {@code enter}.
     * Starting while one runs restarts the clock with the new directions (a fast re-transition does not stack).
     */
    public void start(Dir exit, Dir enter) {
        this.exitDir = exit == null ? Dir.NONE : exit;
        this.enterDir = enter == null ? Dir.NONE : enter;
        this.elapsed = 0;
        this.running = true;
    }

    /** Runs the clock; the transition finishes (and {@link #active()} turns false) once the duration elapses. */
    public void advance(double dt) {
        if (!running || dt <= 0) return;
        elapsed = Math.min(duration, elapsed + dt);
        if (elapsed >= duration) running = false;
    }

    public boolean active() { return running; }

    /** Eased progress, 0 at the start and 1 at the end (and 1 once idle). */
    public double progress() { return running ? ease.applyAsDouble(elapsed / duration) : 1; }

    // The outgoing panel starts home (offset 0) and ends fully off toward exitDir; the incoming panel starts fully
    // off from enterDir and ends home. {@code dist} is how far "off" is — the caller passes the screen dimension.
    public double outX(double dist) { return exitDir.dx * dist * progress(); }
    public double outY(double dist) { return exitDir.dy * dist * progress(); }
    public double inX(double dist)  { return enterDir.dx * dist * (1 - progress()); }
    public double inY(double dist)  { return enterDir.dy * dist * (1 - progress()); }
}
