package client.engine;

/**
 * A fade-to-black screen transition for a single-canvas renderer. A canvas cannot crossfade two scenes it hasn't
 * drawn yet, but it can fade to black, switch what it draws while nothing is visible, and fade back — which reads
 * as a clean transition and costs one rectangle. {@link #start} begins the fade and holds the switch action;
 * {@link #advance} runs the clock and fires the action exactly once at full black. Pure and unit-tested; the
 * renderer just draws a black overlay at {@link #alpha()}.
 */
public final class Fader {

    private static final double FADE_SECONDS = 0.22;   // each half; the whole transition is twice this

    private enum Phase { IDLE, IN, OUT }

    private Phase phase = Phase.IDLE;
    private double t;
    private Runnable atBlack;

    /**
     * Begins a transition; {@code atBlack} runs once, at full black — that is where the caller switches screens.
     * Starting while one is already running replaces the pending switch but keeps the current darkness, so a
     * double-click cannot strobe.
     */
    public void start(Runnable atBlack) {
        this.atBlack = atBlack;
        if (phase == Phase.IDLE) { phase = Phase.IN; t = 0; }
        else if (phase == Phase.OUT) { phase = Phase.IN; t = 1 - t; }   // re-darken from the current level
    }

    public void advance(double dt) {
        if (phase == Phase.IDLE || dt <= 0) return;
        t = Math.min(1, t + dt / FADE_SECONDS);
        if (t >= 1) {
            if (phase == Phase.IN) {
                if (atBlack != null) { atBlack.run(); atBlack = null; }
                phase = Phase.OUT;
                t = 0;
            } else {
                phase = Phase.IDLE;
            }
        }
    }

    /** The black overlay's opacity right now: 0 when idle, 1 at the switch point. */
    public double alpha() {
        return switch (phase) { case IDLE -> 0; case IN -> t; case OUT -> 1 - t; };
    }

    public boolean active() { return phase != Phase.IDLE; }
}
