package client.engine;

import java.util.function.DoubleUnaryOperator;

/**
 * A retained on-screen card: stable {@code id}, its rank/suit/label, an animated {@link Motion}, and client-only
 * UI state (selection). Because entities persist across snapshots (see {@link Reconciler}), their motion and
 * selection survive incoming frames — which is exactly what the old rebuild-every-frame view could not do. Pure
 * (no JavaFX), so entity behaviour is unit-tested; the renderer only reads {@link #x()}/{@link #y()} to draw.
 */
public final class CardEntity {

    private final int id;
    private int rank;
    private int suit;
    private String label;
    private final Motion motion;
    private boolean selected;

    public CardEntity(int id, int rank, int suit, String label,
                      double x, double y, double durationSeconds, DoubleUnaryOperator ease) {
        this.id = id;
        this.rank = rank;
        this.suit = suit;
        this.label = label;
        this.motion = new Motion(x, y, durationSeconds, ease);
    }

    public void moveTo(double x, double y) { motion.moveTo(x, y); }
    public void advance(double dt)         { motion.advance(dt); }

    public int id()          { return id; }
    public int rank()        { return rank; }
    public int suit()        { return suit; }
    public String label()    { return label; }
    public double x()        { return motion.x(); }
    public double y()        { return motion.y(); }
    public boolean settled() { return motion.settled(); }

    public boolean selected()             { return selected; }
    public void setSelected(boolean s)    { this.selected = s; }
    public void toggleSelected()          { this.selected = !this.selected; }

    void update(int rank, int suit, String label) { this.rank = rank; this.suit = suit; this.label = label; }
}
