package client.game;

import client.MatchSnapshot;
import client.MatchViewModel;
import client.engine.Layout;
import client.engine.TileRow;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;

/**
 * The per-frame frame context shared by the HUD, screens and overlays: the renderer, the current snapshot, the
 * view-model, the transient status line, and the click registries. Screens draw through it and register their
 * clickable regions ({@link Btn}, {@link Sel}) here; {@code GameClient}'s input handler reads them back. The
 * selection state (which shop/held item is armed, which joker is targeted) also lives here since it is shared.
 */
final class Ui {

    static final int W = 1320, H = 820, PAD = 24, SIDEBAR = 300, DECK_W = 110, MAX_SELECTION = 5;
    // The top joker/consumable bar: taller than the old 118 so the tiles read big, which also lowers the shop
    // (the center region starts below it). SLOT_TILE_* is the tile size, ~71:95 so joker textures fit unstretched.
    static final int SLOT_H = 190, SLOT_TILE_W = 108, SLOT_TILE_H = 146;

    final Renderer r;
    final Hand hand;
    MatchSnapshot s;
    MatchViewModel vm;
    String status = "";
    boolean showRunInfo;

    final List<Btn> buttons = new ArrayList<>();
    final List<Btn> devButtons = new ArrayList<>();    // dev-mode cheat controls; handled before anything else, any phase
    final List<Btn> packButtons = new ArrayList<>();   // pack-opening option picks (modal: only these are live during a pack)
    final List<Sel> selectables = new ArrayList<>();   // shop/consumable items selectable for contextual actions
    final List<Sel> jokerSel = new ArrayList<>();      // held jokers (own selection: Sell, and Katadesmos's target)
    final List<Tip> tips = new ArrayList<>();          // hover regions; the topmost under the mouse draws a tooltip
    String selKind;                                    // currently-selected item's kind, or null
    int selIndex;
    int jokerTarget = -1;                              // targeted joker index (Sell / Katadesmos), -1 = none
    int packSel = -1;                                  // selected pack option (previewed, not yet taken), -1 = none
    double now;                                        // frame clock (seconds), advanced by GameClient; drives the shared idle sway/bob
    Runnable onLeaveMatch = () -> { };                 // set by GameClient: quit the match and return to the menu

    double mouseX = -1, mouseY = -1;                   // last mouse position, canvas coordinates
    Layout.Rect deckRect;                              // the deck pile's screen area; hovering it opens the deck view

    // Every tile row is retained and draggable — the same part-and-glide treatment the hand gets. The rows live
    // here because two parties share them: Hud/ShopScreen lay them out and draw, GameClient routes the input.
    final TileRow jokerRow      = new TileRow(Ui.SLOT_TILE_W, Ui.SLOT_TILE_H);
    final TileRow itemRow       = new TileRow(Ui.SLOT_TILE_W, Ui.SLOT_TILE_H);
    final TileRow shopSlotRow   = new TileRow(116, 156);   // shop drags have nowhere to land; they lift and glide home
    final TileRow shopVoucherRow = new TileRow(116, 156);
    final TileRow shopPackRow   = new TileRow(116, 156);
    final TileRow packRow       = new TileRow(108, 130);   // a pack's offered cards: shop-style tiles that lift and glide too

    // --- scoring animation ---------------------------------------------------
    // The reel walks the snapshot's lastPlay() timeline one beat at a time; scorePop tells whichever card owns
    // the live beat how hard to pop, and the source's screen position is captured as each tile draws so the
    // effect square can be anchored under (or over) it.

    final client.engine.ScoreReel reel = new client.engine.ScoreReel();
    List<MatchSnapshot.ScoreEventView> reelEvents = List.of();   // the timeline being played
    double reelBaseScore;   // the round total before this play; the score readout holds here until the reel resolves
    private final java.util.Map<Integer, Layout.Rect> sourceRects = new java.util.HashMap<>();

    /**
     * Where an <em>ownerless</em> scoring beat's effect square lands — the base hand-type beat, the ante sin's
     * transform, the Plasma balance: contributions no card owns, which reshape the running chips×mult rather than
     * belonging to a tile. The Hud points this at its chips×mult readout each frame so those beats sit on the
     * numbers they change instead of floating dead-centre.
     */
    Layout.Rect scoreAnchor;

    /** How hard the card with {@code cardId} should pop right now: 1 at the start of its beat, fading to 0. */
    double scorePop(int cardId) {
        MatchSnapshot.ScoreEventView e = liveEvent();
        if (e == null || e.sourceId() != cardId) return 0;
        return 1 - reel.beatProgress();
    }

    /**
     * The event being shown this instant, or null when the reel is idle or past its last beat. The draining
     * tail is a settle pause, not a replay: without this guard the final beat's effect square and pop fade in a
     * second time as {@code beatProgress} restarts over the tail (the visible "it happened twice" on the last
     * beat, most obvious as the Plasma balance).
     */
    MatchSnapshot.ScoreEventView liveEvent() {
        if (reel.draining()) return null;
        int i = reel.currentIndex();
        return i >= 0 && i < reelEvents.size() ? reelEvents.get(i) : null;
    }

    /** Records where a card drew this frame, so the effect square knows what to point at. */
    void noteSourceRect(int cardId, Layout.Rect rect) { sourceRects.put(cardId, rect); }

    /** Where the live beat's source last drew, or null if it is off screen (or the beat is ownerless). */
    Layout.Rect liveSourceRect() {
        MatchSnapshot.ScoreEventView e = liveEvent();
        return e == null ? null : sourceRects.get(e.sourceId());
    }

    /**
     * True when this seat has finished its blind (won, lost or skipped) but the table is still in the BLIND phase —
     * it is waiting on the other players at the blind barrier, and gets the irremovable "waiting" popup.
     */
    boolean atBlindBarrier() {
        return s != null && s.phase() == model.game.MatchPhase.BLIND && s.round() != null && s.round().done();
    }

    /** True when the seat reached the blind barrier by skipping, rather than by playing the blind out. */
    boolean blindSkipped() {
        return s != null && s.round() != null && s.round().skipped();
    }

    Ui(Renderer r, Hand hand) { this.r = r; this.hand = hand; }

    record Btn(Layout.Rect rect, Runnable action) { }
    record Sel(Layout.Rect rect, String kind, int index) { }
    record Act(String label, Color color, Color text, Runnable run) { }
    record Tip(Layout.Rect rect, String text) { }

    /**
     * A vertical offset applied to every region registered while it is non-zero. A screen drawing under a
     * {@code gc.translate} (the menu's slide-in) sets this to the same offset, so hitboxes match what the eye
     * sees — for every region type, by construction, rather than by patching selected lists afterward.
     */
    double regionOffsetY;

    void newFrame() {
        buttons.clear(); devButtons.clear(); packButtons.clear(); selectables.clear(); jokerSel.clear(); tips.clear();
        deckRect = null;
        scoreAnchor = null;
        // Rects are recorded as tiles draw and read by the effect square later in the same frame, so they are
        // per-frame state: clearing keeps them honest (a sold joker stops being an anchor) and bounded.
        sourceRects.clear();
    }

    /** Registers a hover region; the topmost one under the mouse draws its text as a tooltip after everything else. */
    void tip(Layout.Rect rect, String text) {
        if (text != null && !text.isBlank()) tips.add(new Tip(shifted(rect), text));
    }

    private Layout.Rect shifted(Layout.Rect rect) {
        return regionOffsetY == 0 ? rect : new Layout.Rect(rect.x(), rect.y() + regionOffsetY, rect.w(), rect.h());
    }

    /** Whether the mouse is currently inside {@code rect}. */
    boolean hovered(Layout.Rect rect) { return rect != null && rect.contains(mouseX, mouseY); }

    /** Draws a chunky button and, if enabled, registers it for clicking. */
    void button(double x, double y, double w, double h, String label, Color fill, Color text, Runnable action, boolean enabled) {
        r.panel(x, y, w, h, enabled ? fill : fill.darker().darker(), fill.darker(), 8, 2);
        r.textCenterBold(label, x + w / 2, y + h / 2, 14, enabled ? text : Palette.DIM);
        if (enabled) buttons.add(new Btn(shifted(new Layout.Rect(x, y, w, h)), action));
    }
}
