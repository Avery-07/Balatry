package client.game;

import client.engine.PaintField;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;

public final class Background {

    // --- Dynamic Configuration ---
    private int bufferW;
    private double updateHz;

    // --- State ---
    private PaintField field;
    private int bufferH;
    private int[] pixels;
    private int[] sharpened;
    private WritableImage image;

    private double time;
    private double sinceUpdate = Double.MAX_VALUE;
    private boolean checkerPhase;

    // --- Lerping / Transitions ---
    private BackgroundTheme currentTheme;
    private BackgroundTheme targetTheme;
    private double transitionTimeLeft = 0;


    public Background() {
        // Initialize with your original defaults
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

    /** Smoothly morphs to a new theme over X seconds. */
    public void transitionTo(BackgroundTheme theme, double durationSeconds) {
        this.targetTheme = theme;
        this.transitionTimeLeft = durationSeconds;
    }

    /** Adjust background rendering update rate dynamically. */
    public void setUpdateHz(double hz) {
        this.updateHz = Math.max(1.0, hz);
    }

    /** Triggers a safe reallocation of buffers to adjust rendering quality. */
    public void setResolution(int newWidth) {
        this.bufferW = Math.max(64, newWidth);

        this.bufferH = Math.max(
                1,
                (int) Math.round(bufferW * (double) Ui.H / Ui.W)
        );

        this.field = new PaintField(bufferW, bufferH);
        this.pixels = new int[bufferW * bufferH];
        this.sharpened = new int[bufferW * bufferH];
        this.image = new WritableImage(bufferW, bufferH);

        applyThemeToField(currentTheme);
    }

    public double time() {
        return time;
    }


    // =========================================================================
    // LIFECYCLE
    // =========================================================================

    public void advance(double dt) {
        time += dt;
        sinceUpdate += dt;

        System.out.println(time);
        // Process active smooth transitions
        if (transitionTimeLeft > 0) {
            transitionTimeLeft -= dt;

            if (transitionTimeLeft <= 0) {
                // Transition complete
                currentTheme = targetTheme;
                applyThemeToField(currentTheme);
            } else {
                // Step toward target. By dividing dt by timeLeft, we get mathematically
                // perfect linear interpolation without storing the start state.
                double t = Math.min(1.0, dt / (transitionTimeLeft + dt));
                currentTheme = lerpTheme(currentTheme, targetTheme, t);
                applyThemeToField(currentTheme);
            }
        }
    }


    public void paint(GraphicsContext g, double time, double w, double h) {
        if (sinceUpdate >= 1.0 / updateHz) {

            field.renderCheckerboard(
                    pixels,
                    time,
                    checkerPhase
            );

            checkerPhase = !checkerPhase;

            sharpen(
                    pixels,
                    sharpened,
                    bufferW,
                    bufferH,
                    currentTheme.sharpenStrength()
            );

            image.getPixelWriter().setPixels(
                    0, 0, bufferW, bufferH,
                    PixelFormat.getIntArgbInstance(),
                    sharpened, 0, bufferW
            );

            sinceUpdate = 0;
        }

        g.setImageSmoothing(true);
        g.drawImage(image, 0, 0, w, h);
        g.setImageSmoothing(false);
    }


    // =========================================================================
    // INTERNALS & POST-PROCESSING
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
                t > 0.5 ? b.warpSteps() : a.warpSteps(), // Snap integers halfway
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
        int r1 = (c1 >> 16) & 0xff;
        int g1 = (c1 >> 8) & 0xff;
        int b1 = c1 & 0xff;

        int r2 = (c2 >> 16) & 0xff;
        int g2 = (c2 >> 8) & 0xff;
        int b2 = c2 & 0xff;

        int r = (int) (r1 + (r2 - r1) * t);
        int g = (int) (g1 + (g2 - g1) * t);
        int b = (int) (b1 + (b2 - b1) * t);

        return (r << 16) | (g << 8) | b;
    }

    private static void sharpen(int[] src, int[] dst, int width, int height, double strength) {
        // Dynamically calculate weights based on strength.
        // A strength of 1.0 yields your original 120 / 5 configuration.
        // A strength of 0.0 yields 100 / 0 (effectively no sharpening).
        int centerWeight = (int) (100 + 20 * strength);
        int neighborWeight = (int) (5 * strength);

        // If sharpening is turned off completely, just copy and return early for speed
        if (strength <= 0.01) {
            System.arraycopy(src, 0, dst, 0, src.length);
            return;
        }

        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {

                int index = y * width + x;
                int center = src[index];

                int r = ((center >> 16) & 255) * centerWeight;
                int g = ((center >> 8) & 255) * centerWeight;
                int b = (center & 255) * centerWeight;

                int neighbors = src[index - 1]
                        + src[index + 1]
                        + src[index - width]
                        + src[index + width];

                r -= ((neighbors >> 16) & 255) * neighborWeight;
                g -= ((neighbors >> 8) & 255) * neighborWeight;
                b -= (neighbors & 255) * neighborWeight;

                r /= 100;
                g /= 100;
                b /= 100;

                if (r < 0) r = 0; else if (r > 255) r = 255;
                if (g < 0) g = 0; else if (g > 255) g = 255;
                if (b < 0) b = 0; else if (b > 255) b = 255;

                dst[index] = 0xff000000 | (r << 16) | (g << 8) | b;
            }
        }
    }
}