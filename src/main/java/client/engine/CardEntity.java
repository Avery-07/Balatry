package client.engine;

import java.util.function.DoubleUnaryOperator;

/**
 * A retained on-screen card: stable {@code id}, its rank/suit/label, an animated {@link Motion}, and client-only
 * UI state (selection). Because entities persist across snapshots (see {@link Reconciler}), their motion and
 * selection survive incoming frames — which is exactly what the old rebuild-every-frame view could not do. Pure
 * (no JavaFX), so entity behaviour is unit-tested; the renderer only reads {@link #x()}/{@link #y()} to draw.
 */
public final class CardEntity {

    /** How long the face-to-back turn takes; fast enough to read as a flick, slow enough to see. */
    private static final double FLIP_SECONDS = 0.28;

    private final int id;
    private int rank;
    private int suit;
    private int enhancement = -1;   // Enhancement.ordinal(), or -1 for a plain card; drives the card's background art
    private String label;
    private final Motion motion;
    private final Tween flip;   // 0 = face up, 1 = face down; mid-flight values are the turn itself
    private boolean selected;
    private boolean dragging;   // while true the position is the player's hand, not the layout's

    public CardEntity(int id, int rank, int suit, String label,
                      double x, double y, double durationSeconds, DoubleUnaryOperator ease) {
        this(id, rank, suit, label, -1, x, y, durationSeconds, ease);
    }

    public CardEntity(int id, int rank, int suit, String label, int enhancement,
                      double x, double y, double durationSeconds, DoubleUnaryOperator ease) {
        this.id = id;
        this.rank = rank;
        this.suit = suit;
        this.enhancement = enhancement;
        this.label = label;
        this.motion = new Motion(x, y, durationSeconds, ease);
        this.flip = new Tween(0, FLIP_SECONDS, Easing.EASE_IN_OUT);
    }

    /** Retargets the layout position — ignored while the card is being dragged (the drag owns it). */
    public void moveTo(double x, double y) { if (!dragging) motion.moveTo(x, y); }

    public void advance(double dt) { motion.advance(dt); flip.advance(dt); }

    public int id()          { return id; }
    public int rank()        { return rank; }
    public int suit()        { return suit; }
    public int enhancement() { return enhancement; }
    public String label()    { return label; }
    public double x()        { return motion.x(); }
    public double y()        { return motion.y(); }
    public boolean settled() { return motion.settled(); }

    public boolean selected()             { return selected; }
    public void setSelected(boolean s)    { this.selected = s; }
    public void toggleSelected()          { this.selected = !this.selected; }

    // --- flip (face-down cards wear the deck's back) ---

    /** Turns the card over (animated); feeding the current state every frame is a no-op. */
    public void setFaceDown(boolean down) { flip.retarget(down ? 1 : 0); }

    /** Flip progress in [0,1]: 0 face up, 1 face down, in between mid-turn. */
    public double flipT() { return flip.value(); }

    /** The horizontal squash of the turn: 1 at rest either way, 0 edge-on at the halfway point. */
    public double flipScaleX() { return Math.abs(1 - 2 * flip.value()); }

    /** Whether the renderer should draw the card's back right now (past the edge-on point). */
    public boolean showsBack() { return flip.value() > 0.5; }

    // --- drag (the player's hand overrides the layout while held) ---

    public boolean dragging() { return dragging; }

    /** Picks the card up: it stops following the layout and snaps to wherever {@link #dragTo} says. */
    public void beginDrag() { dragging = true; }

    /** Moves the held card; a no-op unless {@link #beginDrag} was called. */
    public void dragTo(double x, double y) { if (dragging) motion.snap(x, y); }

    /** Releases the card: the layout owns it again and the next {@link #moveTo} glides it into place. */
    public void endDrag() { dragging = false; }

    void update(int rank, int suit, int enhancement, String label) { this.rank = rank; this.suit = suit; this.enhancement = enhancement; this.label = label; }
}
