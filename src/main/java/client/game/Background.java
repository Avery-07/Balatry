package client.game;

import javafx.scene.canvas.GraphicsContext;

/**
 * The looping animated backdrop, painted by hand every frame beneath everything else.
 *
 * <p>This is the seam, not the art: {@link GameClient} owns one {@code Background}, advances its clock each
 * frame and calls {@link #paint} first, so anything drawn here sits under the whole UI. To write the actual
 * animation, replace the body of {@link #paint} — everything it needs is already in hand:
 *
 * <ul>
 *   <li>{@code g} — the raw {@link GraphicsContext}. Any canvas call is fair game: {@code fillRect},
 *       {@code setFill} with a gradient, per-pixel work via {@code getPixelWriter()}, {@code setGlobalAlpha},
 *       blend modes. The context is saved/restored around the call, so state changes here cannot leak into the
 *       game's own drawing.</li>
 *   <li>{@code time} — seconds since the client started, monotonically increasing. Drive the loop from this
 *       (e.g. {@code Math.sin(time)}, or {@code time % PERIOD} for a hard loop).</li>
 *   <li>{@code w}, {@code h} — the canvas size in pixels ({@link Ui#W} × {@link Ui#H} today, passed rather than
 *       read so the animation keeps working if the window ever becomes resizable).</li>
 * </ul>
 *
 * <p>Two practical notes for whatever goes in here. It runs ~60×/second across the full canvas, so per-pixel
 * loops want to be cheap or cached — a {@code WritableImage} rebuilt at a lower rate and stretched is usually
 * the right shape if the effect is expensive. And it is purely cosmetic: it must never read the model or the
 * snapshot, so it can never affect determinism or leak hidden information.
 */
final class Background {

    /** Seconds since the client started; the animation's only clock. */
    private double time;

    /** Advanced once per frame from the game loop, before {@link #paint}. */
    void advance(double dt) { time += dt; }

    /** The elapsed clock, if a caller wants to phase something else against the backdrop. */
    double time() { return time; }

    /**
     * Paints one frame of the backdrop, covering the whole canvas. Called before any UI drawing.
     *
     * <p><strong>Replace this body with the real animation.</strong> The placeholder is the felt gradient the
     * client used before this seam existed, plus a slow breathing tint so it is visibly alive and obvious that
     * the clock is wired up.
     */
    void paint(GraphicsContext g, double time, double w, double h) {
        g.setFill(FELT);
        g.fillRect(0, 0, w, h);

        // Placeholder "it's animating" proof: a slow, barely-there brightening that breathes over ~7s.
        double breath = 0.5 + 0.5 * Math.sin(time * (2 * Math.PI / 7));
        g.setGlobalAlpha(0.05 + 0.05 * breath);
        g.setFill(Palette.FELT_A.brighter());
        g.fillRect(0, 0, w, h);
        g.setGlobalAlpha(1);
    }

    /** The pre-existing table felt, kept as the placeholder base (built once — this paints every frame). */
    private static final javafx.scene.paint.RadialGradient FELT = new javafx.scene.paint.RadialGradient(
            0, 0, 0.5, 0.1, 1.1, true, javafx.scene.paint.CycleMethod.NO_CYCLE,
            new javafx.scene.paint.Stop(0, Palette.FELT_A), new javafx.scene.paint.Stop(1, Palette.FELT_B));
}
