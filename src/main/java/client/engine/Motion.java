package client.engine;

import java.util.function.DoubleUnaryOperator;

/**
 * An animated 2D position — two {@link Tween}s advanced together. Entities (cards, tiles) hold a Motion and
 * ask it to {@link #moveTo} a target; the game loop {@link #advance}s it each frame and reads {@link #x}/{@link #y}.
 */
public final class Motion {

    private final Tween x;
    private final Tween y;

    public Motion(double x0, double y0, double durationSeconds, DoubleUnaryOperator ease) {
        this.x = new Tween(x0, durationSeconds, ease);
        this.y = new Tween(y0, durationSeconds, ease);
    }

    public void moveTo(double tx, double ty) { x.retarget(tx); y.retarget(ty); }
    public void snap(double px, double py)   { x.snap(px); y.snap(py); }
    public void advance(double dt)           { x.advance(dt); y.advance(dt); }

    public double x() { return x.value(); }
    public double y() { return y.value(); }
    public double targetX() { return x.target(); }
    public double targetY() { return y.target(); }
    public boolean settled() { return x.done() && y.done(); }
}
