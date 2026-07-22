package client.game;

import client.engine.PaintField;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;

/**
 * The looping animated backdrop: the swirling "liquid paint" field {@link PaintField} computes, stretched over
 * the whole canvas beneath everything else.
 *
 * <p>This class is only the plumbing — buffer, image, update cadence, blit. The effect itself (and every knob
 * worth turning: colours, spin, zoom, contrast, turbulence) lives in {@code PaintField}, which is pure and
 * unit-tested, because a backdrop bug is invisible from a headless sandbox.
 *
 * <p>Two costs are worth knowing. The field is computed into a small buffer and stretched, so the chunkiness is
 * the look rather than a compromise — {@link #BUFFER_W} trades crispness for CPU. And it is recomputed at most
 * {@link #UPDATE_HZ} times a second, independently of the frame rate, so a slow field can never eat the frame
 * budget; every frame in between simply re-blits the last one.
 *
 * <p>The compute runs on the FX thread. If it ever grows expensive enough to hitch (raising {@link #BUFFER_W}
 * or the field's {@code warpSteps} is the way that happens), the escape hatch is to move {@code field.render}
 * onto a daemon thread with a second buffer and blit whichever one is freshest — the animation is cosmetic and
 * does not need to be frame-synced.
 *
 * <p>Cosmetic only: it never reads the model or the snapshot, so it cannot affect determinism or leak hidden
 * information.
 */
final class Background {

    /**
     * Width of the compute buffer, stretched to fill the canvas. Raise for crispness, lower if it stutters —
     * measured on this machine at 4 warp steps: 128 ≈ 2.8ms/recompute, 160 ≈ 6.4ms, 192 ≈ 9.1ms. 128 keeps the
     * recompute comfortably inside a 16.6ms frame; the effect is deliberately chunky, so the resolution costs
     * less visually than the turbulence would.
     */
    private static final int BUFFER_W = 384;

    /** How often the field is recomputed, in Hz — decoupled from the frame rate. */
    private static final double UPDATE_HZ = 30;

    private final PaintField field;
    private final int[] pixels;
    private final WritableImage image;
    private double time;
    private double sinceUpdate = Double.MAX_VALUE;   // forces a compute on the very first frame

    Background() {
        int bufferH = Math.max(1, (int) Math.round(BUFFER_W * (double) Ui.H / Ui.W));
        this.field = new PaintField(BUFFER_W, bufferH);
        this.pixels = new int[BUFFER_W * bufferH];
        this.image = new WritableImage(BUFFER_W, bufferH);
    }

    /** The live field, so the look can be tuned in one place. */
    PaintField field() { return field; }

    /** Advanced once per frame from the game loop, before {@link #paint}. */
    void advance(double dt) { time += dt; sinceUpdate += dt; }

    /** The elapsed clock, if a caller wants to phase something else against the backdrop. */
    double time() { return time; }

    /** Paints one frame of the backdrop, covering the whole canvas. Called before any UI drawing. */
    void paint(GraphicsContext g, double time, double w, double h) {
        if (sinceUpdate >= 1.0 / UPDATE_HZ) {
            field.render(pixels, time);
            image.getPixelWriter().setPixels(0, 0, field.width(), field.height(),
                    PixelFormat.getIntArgbInstance(), pixels, 0, field.width());
            sinceUpdate = 0;
        }
        // Smooth the upscale so the blocks read as soft paint rather than hard mosaic, then hand the flag back:
        // the rest of the client draws pixel-art cards and wants it off.
        g.setImageSmoothing(true);
        g.drawImage(image, 0, 0, w, h);
        g.setImageSmoothing(false);
    }
}
