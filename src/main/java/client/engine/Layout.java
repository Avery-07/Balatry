package client.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure layout + hit-testing math for the Canvas renderer. A {@link Rect} is an axis-aligned box that can be
 * hit-tested against a point; {@link #fan} computes the gentle overlapping arc a hand of cards sits in. Keeping
 * this rendering-agnostic means the parts that decide "where things are" and "what did I click" are unit-tested,
 * while only the drawing itself is left to the eye.
 */
public final class Layout {

    private Layout() { }

    /** An axis-aligned box (top-left origin). */
    public record Rect(double x, double y, double w, double h) {
        public boolean contains(double px, double py) { return px >= x && px <= x + w && py >= y && py <= y + h; }
        public double centerX() { return x + w / 2; }
        public double centerY() { return y + h / 2; }
    }

    /** Where one card sits in the fan: top-left {@code (x,y)} and a small {@code rotationDeg} tilt. */
    public record Placement(double x, double y, double rotationDeg) { }

    /**
     * Slot centers for a centered, count-scaled row — the one "a tile's position depends on how many there are"
     * rule shared by every slot line (jokers, consumables, the shop shelves, a pack's cards, the deck-hover view).
     * The {@code n} tiles are centered on {@code centerX}; they sit a full {@code gap} apart until the row would
     * exceed {@code maxWidth}, past which the step shrinks so they overlap evenly and always fit. Returns the
     * center x of each slot (a single tile sits exactly on {@code centerX}); pure, so the spacing is unit-tested.
     */
    public static double[] slots(int n, double centerX, double tileW, double gap, double maxWidth) {
        double[] xs = new double[Math.max(0, n)];
        if (n <= 0) return xs;
        if (n == 1) { xs[0] = centerX; return xs; }
        double step = Math.min(tileW + gap, (maxWidth - tileW) / (n - 1));
        double rowW = tileW + step * (n - 1);
        double firstCenter = centerX - rowW / 2 + tileW / 2;
        for (int i = 0; i < n; i++) xs[i] = firstCenter + i * step;
        return xs;
    }

    /**
     * A hand of {@code n} cards centered on {@code centerX}, resting with their bottoms near {@code baseY},
     * each {@code cardW} wide and overlapping its neighbour by {@code overlap}px. Cards tilt from
     * {@code -maxSpreadDeg/2} to {@code +maxSpreadDeg/2}, and the middle of the hand lifts up to {@code arcLift}px
     * so the row reads as a gentle arc rather than a straight line.
     */
    public static List<Placement> fan(int n, double centerX, double baseY, double cardW,
                                      double overlap, double maxSpreadDeg, double arcLift) {
        List<Placement> out = new ArrayList<>();
        if (n <= 0) return out;
        double step = cardW - overlap;
        double totalWidth = (n - 1) * step + cardW;
        double startLeft = centerX - totalWidth / 2;
        for (int i = 0; i < n; i++) {
            double t = (n == 1) ? 0 : ((double) i / (n - 1)) * 2 - 1;   // -1 .. +1 across the hand
            double x = startLeft + i * step;
            double rot = t * (maxSpreadDeg / 2);
            double lift = arcLift * (1 - t * t);                        // parabola: centre cards sit higher
            out.add(new Placement(x, baseY - lift, rot));
        }
        return out;
    }
}
