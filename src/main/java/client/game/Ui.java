package client.game;

import client.MatchSnapshot;
import client.MatchViewModel;
import client.engine.Layout;
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

    static final int W = 1320, H = 820, PAD = 24, SIDEBAR = 300, DECK_W = 110, SLOT_H = 118, MAX_SELECTION = 5;

    final Renderer r;
    final Hand hand;
    MatchSnapshot s;
    MatchViewModel vm;
    String status = "";
    boolean showRunInfo;

    final List<Btn> buttons = new ArrayList<>();
    final List<Btn> packButtons = new ArrayList<>();   // pack-opening option picks (modal: only these are live during a pack)
    final List<Sel> selectables = new ArrayList<>();   // shop/consumable items selectable for contextual actions
    final List<Sel> jokerSel = new ArrayList<>();      // held jokers (own selection: Sell, and Katadesmos's target)
    final List<Tip> tips = new ArrayList<>();          // hover regions; the topmost under the mouse draws a tooltip
    String selKind;                                    // currently-selected item's kind, or null
    int selIndex;
    int jokerTarget = -1;                              // targeted joker index (Sell / Katadesmos), -1 = none
    Runnable onLeaveMatch = () -> { };                 // set by GameClient: quit the match and return to the menu

    double mouseX = -1, mouseY = -1;                   // last mouse position, canvas coordinates
    Layout.Rect deckRect;                              // the deck pile's screen area; hovering it opens the deck view

    Ui(Renderer r, Hand hand) { this.r = r; this.hand = hand; }

    record Btn(Layout.Rect rect, Runnable action) { }
    record Sel(Layout.Rect rect, String kind, int index) { }
    record Act(String label, Color color, Color text, Runnable run) { }
    record Tip(Layout.Rect rect, String text) { }

    void newFrame() { buttons.clear(); packButtons.clear(); selectables.clear(); jokerSel.clear(); tips.clear(); deckRect = null; }

    /** Registers a hover region; the topmost one under the mouse draws its text as a tooltip after everything else. */
    void tip(Layout.Rect rect, String text) {
        if (text != null && !text.isBlank()) tips.add(new Tip(rect, text));
    }

    /** Whether the mouse is currently inside {@code rect}. */
    boolean hovered(Layout.Rect rect) { return rect != null && rect.contains(mouseX, mouseY); }

    /** Draws a chunky button and, if enabled, registers it for clicking. */
    void button(double x, double y, double w, double h, String label, Color fill, Color text, Runnable action, boolean enabled) {
        r.panel(x, y, w, h, enabled ? fill : fill.darker().darker(), fill.darker(), 8, 2);
        r.textCenterBold(label, x + w / 2, y + h / 2, 14, enabled ? text : Palette.DIM);
        if (enabled) buttons.add(new Btn(new Layout.Rect(x, y, w, h), action));
    }
}
