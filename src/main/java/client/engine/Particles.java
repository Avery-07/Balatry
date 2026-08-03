package client.engine;

import java.util.Random;

/**
 * A field of small square particles that drift, spin and wrap around the screen — the ambient motes floating over
 * the animated backdrop. Pure simulation (no rendering): seeded so it is deterministic and unit-testable, advanced
 * by {@code dt}, read back by index. {@link #speedScale} lets the caller agitate the whole field at once (faster
 * motes during a boss blind, say) without rebuilding it. The renderer draws each index as a rotated square.
 */
public final class Particles {

    /** How far a particle travels off an edge before it wraps back on the far side, so squares glide rather than blink. */
    public static final double MARGIN = 32;

    private final int n;
    private final double w, h;
    private final double[] x, y, vx, vy, ang, spin, size, alpha;

    /** A global multiplier on drift and spin — 1 at rest, higher to stir the field up. */
    public double speedScale = 1.0;

    public Particles(int count, double width, double height, long seed) {
        this.n = Math.max(0, count);
        this.w = Math.max(1, width);
        this.h = Math.max(1, height);
        x = new double[n]; y = new double[n]; vx = new double[n]; vy = new double[n];
        ang = new double[n]; spin = new double[n]; size = new double[n]; alpha = new double[n];
        Random rng = new Random(seed);
        for (int i = 0; i < n; i++) {
            x[i] = rng.nextDouble() * w;
            y[i] = rng.nextDouble() * h;
            double speed = 8 + rng.nextDouble() * 26;          // px/sec of gentle drift
            double dir = rng.nextDouble() * Math.PI * 2;
            vx[i] = Math.cos(dir) * speed;
            vy[i] = Math.sin(dir) * speed;
            ang[i] = rng.nextDouble() * 360;
            spin[i] = (rng.nextDouble() - 0.5) * 80;            // deg/sec, either direction
            size[i] = 10 + rng.nextDouble() * 22;
            alpha[i] = 0.05 + rng.nextDouble() * 0.14;          // subtle: motes, not confetti
        }
    }

    /** Advances every particle: drift, spin, and a toroidal wrap so the field is endless. */
    public void advance(double dt) {
        if (dt <= 0) return;
        for (int i = 0; i < n; i++) {
            x[i] = wrap(x[i] + vx[i] * speedScale * dt, -MARGIN, w + MARGIN);
            y[i] = wrap(y[i] + vy[i] * speedScale * dt, -MARGIN, h + MARGIN);
            ang[i] += spin[i] * speedScale * dt;
        }
    }

    /** Wraps {@code v} into [{@code lo}, {@code hi}) — modulo so even a huge step (a lag spike) lands in range. */
    private static double wrap(double v, double lo, double hi) {
        double range = hi - lo;
        double t = (v - lo) % range;
        if (t < 0) t += range;
        return t + lo;
    }

    public int count()             { return n; }
    public double x(int i)         { return x[i]; }
    public double y(int i)         { return y[i]; }
    public double angleDeg(int i)  { return ang[i]; }
    public double size(int i)      { return size[i]; }
    public double alpha(int i)     { return alpha[i]; }
}
