package client.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * Plays a scoring timeline back one beat at a time — the clock behind the trigger/effect animation. Feed it the
 * model's event list and {@code dt}; it reports which beat is live, how far into that beat it is, and when the
 * whole reel has drained. Pure (it knows nothing about what an event <em>means</em> or how it draws), so the
 * pacing is unit-tested rather than eyeballed.
 *
 * <p>The reel accelerates as it runs: every {@link #SPEED_UP_EVERY} beats the step shortens by
 * {@link #SPEED_UP_FACTOR}, down to a floor. A five-joker chain would otherwise plod, and this keeps a long hand
 * exciting instead of tedious without ever skipping a beat — every contribution is still shown.
 */
public final class ScoreReel {

    /** How long the first beat lasts, and the fastest a beat may ever get. */
    private static final double FIRST_STEP = 0.34, MIN_STEP = 0.07;

    /** After this many beats the step is divided by the factor below. */
    private static final int SPEED_UP_EVERY = 2;
    private static final double SPEED_UP_FACTOR = 1.1;

    /** A beat's own duration is followed by this much settle time before the reel reports itself done. */
    private static final double TAIL = 0.35;

    private List<Integer> beats = List.of();   // indices into the caller's event list
    private int index = -1;
    private double elapsed;      // seconds into the current beat
    private double step;         // the current beat's duration
    private int played;          // beats completed, drives the acceleration
    private boolean draining;    // past the last beat, running out the tail

    /** Starts a fresh playback of {@code eventCount} beats. A count of 0 leaves the reel idle. */
    public void play(int eventCount) {
        beats = new ArrayList<>(eventCount);
        for (int i = 0; i < eventCount; i++) beats.add(i);
        index = eventCount == 0 ? -1 : 0;
        elapsed = 0;
        played = 0;
        draining = false;
        step = FIRST_STEP;
    }

    /** Abandons any playback; the reel goes idle immediately. */
    public void stop() {
        beats = List.of();
        index = -1;
        draining = false;
    }

    public void advance(double dt) {
        if (index < 0 || dt <= 0) return;
        elapsed += dt;
        while (elapsed >= step) {
            elapsed -= step;
            if (draining) { stop(); return; }
            played++;
            if (played % SPEED_UP_EVERY == 0) step = Math.max(MIN_STEP, step / SPEED_UP_FACTOR);
            if (index + 1 < beats.size()) {
                index++;
            } else {
                draining = true;   // last beat shown; hold the final numbers briefly before releasing the screen
                step = TAIL;
            }
        }
    }

    /** Whether a playback is running (including its settle tail). */
    public boolean playing() { return index >= 0; }

    /** The event index being shown right now, or -1 when idle. */
    public int currentIndex() { return index; }

    /** How many beats have fully played — the client counts its readouts up to this beat's totals. */
    public int playedCount() { return played; }

    /** Progress through the current beat, 0..1 — drives the trigger pop and the effect square's rise. */
    public double beatProgress() { return step <= 0 ? 1 : Math.min(1, elapsed / step); }

    /** Whether the reel is past its last beat, showing the settled result. */
    public boolean draining() { return draining; }
}
