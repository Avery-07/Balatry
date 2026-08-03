package client.game;

import client.engine.PaintField;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The animated backdrop's plumbing: it owns the {@link PaintField}, the pixel buffers and the on-screen image, and
 * decides when to recompute. The paint field is a per-pixel CPU shader, so — to keep it off the render thread — the
 * expensive frame is computed on a background worker (rows split across cores by {@link PaintField#renderParallel}),
 * into one of two ping-pong buffers; the JavaFX thread only ever uploads a finished buffer and blits the image. A
 * frame is recomputed at most {@code updateHz} times a second; the blit itself happens every frame from the cache.
 */
public final class Background {

    // --- configuration ---
    private int bufferW;
    private int bufferH;
    private double updateHz;

    // --- paint state ---
    private PaintField field;
    private int[] bufA, bufB;          // two compute buffers, ping-ponged so the worker and the uploader never share one
    private int nextBuf;               // JavaFX-thread only: which buffer the next render targets (0 or 1)
    private volatile int[] readyBuffer;   // a freshly-rendered buffer awaiting upload, or null
    private volatile boolean busy;        // a render is in flight on the worker
    private WritableImage image;
    private double time;
    private double sinceUpdate = Double.MAX_VALUE;

    // The worker: one daemon thread orchestrates renders (so it never blocks JVM shutdown); the row split inside
    // renderParallel fans out across the common ForkJoin pool.
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "bg-paint");
        t.setDaemon(true);
        return t;
    });

    // --- transitions ---
    private BackgroundTheme currentTheme;
    private BackgroundTheme targetTheme;
    private double transitionTimeLeft = 0;

    public Background() {
        this.updateHz = 20.0;
        this.currentTheme = BackgroundTheme.DEFAULT;
        this.targetTheme = BackgroundTheme.DEFAULT;
        setResolution((int) (384 * 1.5));
    }

    // =========================================================================
    // PUBLIC API
    // =========================================================================

    /** Instantly snaps to a new theme. */
    public void setTheme(BackgroundTheme theme) {
        this.currentTheme = theme;
        this.targetTheme = theme;
        this.transitionTimeLeft = 0;
        applyThemeToField(currentTheme);
    }

    /** Smoothly morphs to a new theme over {@code durationSeconds}. */
    public void transitionTo(BackgroundTheme theme, double durationSeconds) {
        this.targetTheme = theme;
        this.transitionTimeLeft = durationSeconds;
    }

    /** Adjusts how often the backdrop is recomputed (the blit stays every-frame). */
    public void setUpdateHz(double hz) {
        this.updateHz = Math.max(1.0, hz);
    }

    /** Reallocates the buffers/image for a new render width (height follows the screen aspect). Construction-time only. */
    public void setResolution(int newWidth) {
        this.bufferW = Math.max(64, newWidth);
        this.bufferH = Math.max(1, (int) Math.round(bufferW * (double) Ui.H / Ui.W));
        this.field = new PaintField(bufferW, bufferH);
        this.bufA = new int[bufferW * bufferH];
        this.bufB = new int[bufferW * bufferH];
        this.readyBuffer = null;
        this.image = new WritableImage(bufferW, bufferH);
        applyThemeToField(currentTheme);
    }

    public double time() {
        return time;
    }

    /** The current (transition-lerped) theme's two main colours as 0xRRGGBB — what the floating motes are tinted with. */
    public int currentColour1() { return currentTheme.colour1(); }
    public int currentColour2() { return currentTheme.colour2(); }

    // =========================================================================
    // LIFECYCLE
    // =========================================================================

    public void advance(double dt) {
        time += dt;
        sinceUpdate += dt;

        if (transitionTimeLeft > 0) {
            transitionTimeLeft -= dt;
            if (transitionTimeLeft <= 0) {
                currentTheme = targetTheme;
                applyThemeToField(currentTheme);
            } else {
                // Step toward target: dividing dt by the time left gives exact linear interpolation with no start state.
                double t = Math.min(1.0, dt / (transitionTimeLeft + dt));
                currentTheme = lerpTheme(currentTheme, targetTheme, t);
                applyThemeToField(currentTheme);
            }
        }
    }

    /**
     * JavaFX thread: upload a finished frame if one is ready, kick a fresh render if it's time and none is running,
     * then blit. The compute never happens here — only the (cheap) pixel upload and the scaled draw.
     */
    public void paint(GraphicsContext g, double time, double w, double h) {
        int[] ready = readyBuffer;
        if (ready != null) {
            image.getPixelWriter().setPixels(0, 0, bufferW, bufferH,
                    PixelFormat.getIntArgbInstance(), ready, 0, bufferW);
            readyBuffer = null;   // uploaded; the buffer is free to reuse
        }

        if (!busy && readyBuffer == null && sinceUpdate >= 1.0 / updateHz) {
            sinceUpdate = 0;
            busy = true;
            final int[] target = (nextBuf == 0) ? bufA : bufB;
            nextBuf = 1 - nextBuf;
            final double frameTime = time;
            final PaintField.Config cfg = field.config();   // snapshot the knobs here (FX thread), so the worker never reads them live
            worker.submit(() -> {
                try {
                    field.renderParallel(target, frameTime, cfg);
                    readyBuffer = target;
                } finally {
                    busy = false;
                }
            });
        }

        g.setImageSmoothing(true);
        g.drawImage(image, 0, 0, w, h);
        g.setImageSmoothing(false);
    }

    // =========================================================================
    // INTERNALS
    // =========================================================================

    private void applyThemeToField(BackgroundTheme theme) {
        field.zoom = theme.zoom();
        field.warpSteps = theme.warpSteps();
        field.spinSpeed = theme.spinSpeed();
        field.paintSpeed = theme.paintSpeed();
        field.spinAmount = theme.spinAmount();
        field.contrast = theme.contrast();
        field.colour1 = theme.colour1();
        field.colour2 = theme.colour2();
        field.colour3 = theme.colour3();
    }

    private BackgroundTheme lerpTheme(BackgroundTheme a, BackgroundTheme b, double t) {
        return new BackgroundTheme(
                a.zoom() + (b.zoom() - a.zoom()) * t,
                t > 0.5 ? b.warpSteps() : a.warpSteps(),   // snap the integer step halfway
                a.spinSpeed() + (b.spinSpeed() - a.spinSpeed()) * t,
                a.paintSpeed() + (b.paintSpeed() - a.paintSpeed()) * t,
                a.spinAmount() + (b.spinAmount() - a.spinAmount()) * t,
                a.contrast() + (b.contrast() - a.contrast()) * t,
                a.sharpenStrength() + (b.sharpenStrength() - a.sharpenStrength()) * t,
                lerpColor(a.colour1(), b.colour1(), t),
                lerpColor(a.colour2(), b.colour2(), t),
                lerpColor(a.colour3(), b.colour3(), t)
        );
    }

    private static int lerpColor(int c1, int c2, double t) {
        int r1 = (c1 >> 16) & 0xff, g1 = (c1 >> 8) & 0xff, b1 = c1 & 0xff;
        int r2 = (c2 >> 16) & 0xff, g2 = (c2 >> 8) & 0xff, b2 = c2 & 0xff;
        int r = (int) (r1 + (r2 - r1) * t);
        int g = (int) (g1 + (g2 - g1) * t);
        int b = (int) (b1 + (b2 - b1) * t);
        return (r << 16) | (g << 8) | b;
    }
}
