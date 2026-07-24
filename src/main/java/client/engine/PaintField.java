package client.engine;

public final class PaintField {

    private final int w, h;
    private final double norm;
    private final double invNorm;

    // --- tuning ---

    public int warpSteps = 4;

    public double spinSpeed = 0.025;
    public double paintSpeed = 1;

    public double spinAmount = 0.22;

    public double zoom = 26;

    public double contrast = 2.1;

    public int colour1 = 0x750000;
    public int colour2 = 0x003375;
    public int colour3 = 0x220826;


    public PaintField(int width, int height) {
        this.w = Math.max(1, width);
        this.h = Math.max(1, height);

        this.norm = Math.sqrt((double) w * w + (double) h * h);
        this.invNorm = 1.0 / norm;
    }


    public int width() {
        return w;
    }

    public int height() {
        return h;
    }


    public void render(int[] out, double time) {

        double spin = time * spinSpeed;
        double churn = time * paintSpeed;

        double halfW = w * 0.5;
        double halfH = h * 0.5;

        for (int py = 0; py < h; py++) {

            int index = py * w;

            double y = (py + 0.5 - halfH) * invNorm;

            for (int px = 0; px < w; px++) {

                double x = (px + 0.5 - halfW) * invNorm;

                out[index + px] = shade(x, y, spin, churn);
            }
        }
    }


    public int shade(double x, double y, double spin, double churn) {

        double radius = Math.sqrt(x * x + y * y);

        double angle =
                Math.atan2(y, x)
                        - spin
                        + spinAmount * 6.0 * radius;


        double ux = radius * Math.cos(angle) * zoom;
        double uy = radius * Math.sin(angle) * zoom;


        double vx = ux + uy;


        double churnA = churn * 0.131;
        double churnB = -0.113 * churn;


        for (int i = 0; i < warpSteps; i++) {

            vx += Math.sin(Math.max(ux, uy)) + ux;

            double nx =
                    ux + 0.5 * Math.cos(5.112 + 0.353 * vx + churnA);

            double ny =
                    uy + 0.5 * Math.sin(vx + churnB);


            double fold =
                    Math.cos(nx + ny)
                            - Math.sin(nx * 0.711 - ny);


            ux = nx - fold;
            uy = ny - fold;
        }


        double paint =
                Math.sqrt(ux * ux + uy * uy)
                        * 0.035
                        * contrast;


        if (paint < 0)
            paint = 0;
        else if (paint > 2)
            paint = 2;


        double b1 = 1 - contrast * Math.abs(1 - paint);
        if (b1 < 0)
            b1 = 0;


        double b2 = 1 - contrast * Math.abs(paint);
        if (b2 < 0)
            b2 = 0;


        double b3 = 1 - Math.min(1, b1 + b2);


        double floor = 0.3 / contrast;
        double body = 1 - floor;


        double glow =
                0.30 * Math.max(0, b1 * 5 - 4)
                        + 0.40 * Math.max(0, b2 * 5 - 4);


        double r1 = ((colour1 >> 16) & 0xff) / 255.0;
        double g1 = ((colour1 >> 8) & 0xff) / 255.0;
        double b1c = (colour1 & 0xff) / 255.0;

        double r2 = ((colour2 >> 16) & 0xff) / 255.0;
        double g2 = ((colour2 >> 8) & 0xff) / 255.0;
        double b2c = (colour2 & 0xff) / 255.0;

        double r3 = ((colour3 >> 16) & 0xff) / 255.0;
        double g3 = ((colour3 >> 8) & 0xff) / 255.0;
        double b3c = (colour3 & 0xff) / 255.0;


        double r =
                floor * r1
                        + body * (r1 * b1 + r2 * b2 + r3 * b3)
                        + glow;


        double g =
                floor * g1
                        + body * (g1 * b1 + g2 * b2 + g3 * b3)
                        + glow;


        double b =
                floor * b1c
                        + body * (b1c * b1 + b2c * b2 + b3c * b3)
                        + glow;


        int red = channel(r);
        int green = channel(g);
        int blue = channel(b);


        return 0xff000000
                | (red << 16)
                | (green << 8)
                | blue;
    }


    private static int channel(double value) {

        if (value < 0)
            value = 0;
        else if (value > 1)
            value = 1;

        return (int) (value * 255 + 0.5);
    }

    public void renderCheckerboard(
            int[] out,
            double time,
            boolean phase
    ) {

        double spin = time * spinSpeed;
        double churn = time * paintSpeed;

        double halfW = w * 0.5;
        double halfH = h * 0.5;


        for (int py = 0; py < h; py++) {

            int row = py * w;

            double y =
                    (py + 0.5 - halfH) / norm;


            for (int px = 0; px < w; px++) {


                if (((px + py) & 1) != (phase ? 1 : 0))
                    continue;


                double x =
                        (px + 0.5 - halfW) / norm;


                out[row + px] =
                        shade(
                                x,
                                y,
                                spin,
                                churn
                        );
            }
        }
    }
}