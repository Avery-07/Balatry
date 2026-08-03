package client.engine;

import java.util.function.DoubleUnaryOperator;

/**
 * An animated 2D position — two {@link Tween}s advanced together. Entities (cards, tiles) hold a Motion and
 * ask it to {@link #moveTo} a target; the game loop {@link #advance}s it each frame and reads {@link #x}/{@link #y}.
 */
public final class Motion {

    private final Tween x;
    private final Tween y;
    private double vx, vy;   // px/sec over the last advance — what squash & stretch reads to deform the moving thing

    public Motion(double x0, double y0, double durationSeconds, DoubleUnaryOperator ease) {
        this.x = new Tween(x0, durationSeconds, ease);
        this.y = new Tween(y0, durationSeconds, ease);
    }

    public void moveTo(double tx, double ty) { x.retarget(tx); y.retarget(ty); }
    public void snap(double px, double py)   { x.snap(px); y.snap(py); vx = 0; vy = 0; }

    /** Advances both tweens and measures the frame's velocity (the delta this step, per second) for squash & stretch. */
    public void advance(double dt) {
        double px = x.value(), py = y.value();
        x.advance(dt);
        y.advance(dt);
        if (dt > 0) { vx = (x.value() - px) / dt; vy = (y.value() - py) / dt; }
    }

    public double x() { return x.value(); }
    public double y() { return y.value(); }
    public double targetX() { return x.target(); }
    public double targetY() { return y.target(); }
    public double vx() { return vx; }
    public double vy() { return vy; }
    public double speed() { return Math.hypot(vx, vy); }
    public boolean settled() { return x.done() && y.done(); }
}
