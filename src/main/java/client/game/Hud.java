package client.game;

import client.MatchSnapshot;
import client.engine.CardEntity;
import client.engine.Counter;
import client.engine.Easing;
import client.engine.Layout;
import javafx.scene.paint.Color;
import model.items.DeckCard;
import model.game.MatchPhase;
import model.game.scoring.HandEvaluator;

import java.util.ArrayList;
import java.util.List;

import static client.game.Palette.*;

/**
 * The persistent frame chrome, drawn every phase: left sidebar, top joker/consumable slots, right deck pile.
 * The score, chips×mult and money readouts are {@link Counter}s — they count up toward the model's value and
 * thump when it grows, which is most of what makes scoring feel like scoring.
 */
final class Hud {

    private final Counter score = new Counter(0, 0.6, Easing.EASE_OUT_CUBIC);
    private final Counter chips = new Counter(0, 0.3, Easing.EASE_OUT_CUBIC);
    private final Counter mult  = new Counter(0, 0.3, Easing.EASE_OUT_CUBIC);
    private final Counter money = new Counter(0, 0.4, Easing.EASE_OUT_CUBIC);

    private double time;   // frame clock for the tiles' idle bob

    /** Advanced from the game loop alongside the hand. */
    void advance(double dt) {
        time += dt;
        score.advance(dt); chips.advance(dt); mult.advance(dt); money.advance(dt);
    }

    void render(Ui ui) {
        drawSidebar(ui, Ui.PAD, Ui.PAD, Ui.SIDEBAR, Ui.H - 2 * Ui.PAD);
        double cx = Ui.PAD + Ui.SIDEBAR + 18;
        drawTopSlots(ui, cx, Ui.PAD, Ui.W - Ui.PAD - Ui.DECK_W - 18 - cx, Ui.SLOT_H);
        double cTop = Ui.PAD + Ui.SLOT_H + 12;
        drawDeck(ui, Ui.W - Ui.PAD - Ui.DECK_W, cTop, Ui.DECK_W, Ui.H - Ui.PAD - cTop);
    }

    private void drawSidebar(Ui ui, double x, double y, double w, double h) {
        Renderer r = ui.r;
        MatchSnapshot s = ui.s;
        Color accent = switch (s.phase()) { case BLIND -> BLUE; case SHOP -> RED; case RESULT -> GREEN; default -> GOLD; };
        r.panel(x, y, w, h, PANEL, accent, 12, 3);
        double ix = x + 14, iw = w - 28, cy = y + 16;

        String hdr = switch (s.phase()) {
            case SHOP -> "SHOP"; case RESULT -> Fmt.blindName(s.blind()) + " Defeated!";
            case BLIND -> "Score at least " + s.target(); default -> "Choose your next Blind"; };
        r.panel(ix, cy, iw, 52, PANEL2, EDGE, 9, 2);
        r.textCenterBold(hdr, ix + iw / 2, cy + 26, s.phase() == MatchPhase.SHOP ? 26 : 15, s.phase() == MatchPhase.SHOP ? ORANGE : INK);
        cy += 64;

        // The play preview: with cards selected, the chips×mult boxes price the hand the selection would score
        // (at this seat's levels) and its name appears above them; with nothing selected they show the last play.
        MatchSnapshot.HandLevelView preview = detectSelection(ui);

        // The juicy readouts: each counter chases its value and pops when it grows.
        score.retarget(numeric(s.round() != null ? s.round().score() : "0"));
        chips.retarget(preview != null ? preview.chips() : numeric(s.chips()));
        mult.retarget(preview != null ? preview.mult() : numeric(s.mult()));
        money.retarget(s.money());

        cy = statBoxPopped(r, ix, cy, iw, "Round score", score);

        r.textCenterBold(preview == null ? " "
                        : Fmt.handName(preview.type()) + "  lv." + preview.level(),
                ix + iw / 2, cy + 6, 14, preview == null ? DIM : ORANGE);
        cy += 16;

        double half = (iw - 30) / 2;
        r.panel(ix, cy, half, 46, BLUE, null, 8, 0);
        r.textCenterBold(whole(chips.displayed()), ix + half / 2, cy + 23, 24 * chips.popScale(), INK);
        r.textCenterBold("X", ix + half + 15, cy + 23, 18, RED);
        r.panel(ix + half + 30, cy, half, 46, RED, null, 8, 0);
        r.textCenterBold(whole(mult.displayed()), ix + half + 30 + half / 2, cy + 23, 24 * mult.popScale(), INK);
        cy += 58;

        ui.button(ix, cy, 88, 60, "Run Info", RED, INK, () -> ui.showRunInfo = true, true);
        cell(r, ix + 98, cy, (iw - 98 - 10) / 2, 60, "Hands", String.valueOf(s.hands()), BLUE);
        cell(r, ix + 98 + (iw - 98 - 10) / 2 + 10, cy, (iw - 98 - 10) / 2, 60, "Discards", String.valueOf(s.discards()), RED);
        cy += 72;

        r.panel(ix, cy, iw, 44, PANEL, EDGE, 8, 2);
        r.textCenterBold("$" + whole(money.displayed()), ix + iw / 2, cy + 22, 26 * money.popScale(), ORANGE);
        cy += 56;

        ui.button(ix, cy, 88, 56, "Options", ORANGE, DARK, () -> ui.status = "Options — not wired.", true);
        cell(r, ix + 98, cy, (iw - 98 - 10) / 2, 56, "Ante", s.ante() + "/" + s.anteCount(), ORANGE);
        cell(r, ix + 98 + (iw - 98 - 10) / 2 + 10, cy, (iw - 98 - 10) / 2, 56, "Round", String.valueOf(s.roundNumber()), ORANGE);
        cy += 68;

        r.panel(ix, cy, iw, 34, Color.web("#2a1030"), PURPLE, 8, 2);
        r.textCenter("Ante sin — " + s.activeSin(), ix + iw / 2, cy + 17, 12, Color.web("#ecd7f5"));
    }

    private double statBox(Renderer r, double x, double y, double w, String k, String v) {
        r.panel(x, y, w, 40, PANEL, EDGE, 8, 2);
        r.textLeftBold(k, x + 10, y + 13, 14, DIM);
        r.textCenterBold(v, x + w - 30, y + 20, 20, INK);
        return y + 52;
    }

    /** A statBox whose value is a counting, popping readout. */
    private double statBoxPopped(Renderer r, double x, double y, double w, String k, Counter c) {
        r.panel(x, y, w, 40, PANEL, EDGE, 8, 2);
        r.textLeftBold(k, x + 10, y + 13, 14, DIM);
        r.textCenterBold(whole(c.displayed()), x + w - 30, y + 20, 20 * c.popScale(), INK);
        return y + 52;
    }

    /**
     * The hand the current selection would score, at this seat's levels — or null when nothing is selected (or
     * outside a blind). Evaluation runs client-side on the selected ranks/suits ({@link HandEvaluator} is pure
     * and deterministic, the same evaluator the model scores with), and the pricing comes from the snapshot's
     * {@code handLevels} table, so the preview always matches what a play would actually earn.
     */
    private static MatchSnapshot.HandLevelView detectSelection(Ui ui) {
        if (ui.s.phase() != MatchPhase.BLIND) return null;
        List<DeckCard> cards = new ArrayList<>();
        for (CardEntity e : ui.hand.selectedCards()) {
            if (e.rank() < 0) return null;   // a face-down card is selected: its identity is hidden, so no preview
            cards.add(new DeckCard(DeckCard.Rank.values()[e.rank()], DeckCard.Suit.values()[e.suit()]));
        }
        if (cards.isEmpty()) return null;
        String type = new HandEvaluator().evaluate(cards).type().name();
        for (MatchSnapshot.HandLevelView hv : ui.s.handLevels())
            if (hv.type().equals(type)) return hv;
        return null;
    }

    /** Parses a model-supplied numeric string for animation; a value too exotic to parse just snaps to 0 pop. */
    private static double numeric(String s) {
        try { return Double.parseDouble(s); } catch (NumberFormatException e) { return 0; }
    }

    /** Formats a counter's in-flight value as the integer the player should read. */
    private static String whole(double v) { return String.valueOf(Math.round(v)); }

    private void cell(Renderer r, double x, double y, double w, double h, String k, String v, Color vc) {
        r.panel(x, y, w, h, PANEL, EDGE, 8, 2);
        r.textCenter(k, x + w / 2, y + 15, 12, DIM);
        r.textCenterBold(v, x + w / 2, y + h - 18, 22, vc);
    }

    private void drawTopSlots(Ui ui, double x, double y, double w, double h) {
        Renderer r = ui.r;
        MatchSnapshot s = ui.s;
        double tileW = 54, tileH = Ui.SLOT_H - 30, tileCY = y + 22 + tileH / 2;

        // Jokers: a retained, draggable row — reconcile by card id, lay slots out, draw at animated positions.
        r.textLeftBold(s.jokerSlotsUsed() + "/" + s.jokerSlotsMax(), x, y, 18, INK);
        List<Integer> jokerIds = new ArrayList<>();
        for (MatchSnapshot.JokerView jv : s.jokers()) jokerIds.add(jv.id());
        ui.jokerRow.reconcile(jokerIds);
        double[] jokerSlots = new double[jokerIds.size()];
        for (int j = 0; j < jokerSlots.length; j++) jokerSlots[j] = x + tileW / 2 + j * 62;
        ui.jokerRow.layout(jokerSlots, tileCY);

        for (int j = 0; j < s.jokers().size(); j++) {
            MatchSnapshot.JokerView jv = s.jokers().get(j);
            if (ui.jokerRow.isDragged(jv.id())) continue;   // the held tile draws last, above its row
            drawJokerTile(ui, jv, j, tileW, tileH, client.engine.Idle.bobPx(time, j, 1.6));
        }
        for (int j = 0; j < s.jokers().size(); j++)   // at most one: the dragged tile, on top
            if (ui.jokerRow.isDragged(s.jokers().get(j).id())) drawJokerTile(ui, s.jokers().get(j), j, tileW, tileH, 0);

        // Consumables/relics: same treatment, right-aligned (slot i's center ascends left→right in model order).
        r.textLeftBold(s.consumableSlotsUsed() + "/" + s.consumableSlotsMax(), x + w - 40, y, 18, INK);
        List<Integer> itemIds = new ArrayList<>();
        for (MatchSnapshot.ItemView it : s.inventory()) itemIds.add(it.id());
        ui.itemRow.reconcile(itemIds);
        double[] itemSlots = new double[itemIds.size()];
        for (int i = 0; i < itemSlots.length; i++)
            itemSlots[i] = x + w - 54 + tileW / 2 - (itemSlots.length - 1 - i) * 62;
        ui.itemRow.layout(itemSlots, tileCY);

        for (int i = 0; i < s.inventory().size(); i++) {
            MatchSnapshot.ItemView it = s.inventory().get(i);
            if (ui.itemRow.isDragged(it.id())) continue;
            drawItemTile(ui, it, i, tileW, tileH, client.engine.Idle.bobPx(time, 40 + i, 1.6));
        }
        for (int i = 0; i < s.inventory().size(); i++)
            if (ui.itemRow.isDragged(s.inventory().get(i).id())) drawItemTile(ui, s.inventory().get(i), i, tileW, tileH, 0);
    }

    private void drawJokerTile(Ui ui, MatchSnapshot.JokerView jv, int index, double tileW, double tileH, double bob) {
        Renderer r = ui.r;
        Layout.Rect rr = new Layout.Rect(ui.jokerRow.x(jv.id()) - tileW / 2, ui.jokerRow.y(jv.id()) - tileH / 2 + bob, tileW, tileH);
        // A debuffed joker greys out; a badge (edition/stickers) draws as a footer strip on the tile.
        mini(r, rr, jv.debuffed() ? Color.web("#4a4a4f") : Color.web("#c0392b"), Fmt.shortName(jv.name()));
        if (!jv.badge().isEmpty()) {
            r.panel(rr.x(), rr.y() + rr.h() - 16, rr.w(), 16, Color.web("#000a"), null, 6, 0);
            r.textCenter(jv.badge(), rr.centerX(), rr.y() + rr.h() - 8, 8, GOLD);
        }
        if (index == ui.jokerTarget) r.panel(rr.x() - 3, rr.y() - 3, rr.w() + 6, rr.h() + 6, null, ORANGE, 10, 3);
        ui.jokerSel.add(new Ui.Sel(rr, "joker", index));
        ui.tip(rr, jokerTip(jv));
    }

    private void drawItemTile(Ui ui, MatchSnapshot.ItemView it, int index, double tileW, double tileH, double bob) {
        Renderer r = ui.r;
        Layout.Rect rr = new Layout.Rect(ui.itemRow.x(it.id()) - tileW / 2, ui.itemRow.y(it.id()) - tileH / 2 + bob, tileW, tileH);
        mini(r, rr, Color.web("#3d3357"), Fmt.shortName(it.label()));
        ui.selectables.add(new Ui.Sel(rr, "item", index));
        ui.tip(rr, itemTip(it));
    }

    /**
     * A joker's hover text: name, effect, its live variable (the hidden pick or accumulated bonus the player
     * otherwise cannot see), badge (edition/stickers), and whether it is dead right now.
     */
    private static String jokerTip(MatchSnapshot.JokerView jv) {
        StringBuilder tip = new StringBuilder(jv.name());
        if (!jv.description().isEmpty()) tip.append('\n').append(jv.description());
        if (!jv.state().isEmpty()) tip.append('\n').append(jv.state());
        if (!jv.badge().isEmpty()) tip.append('\n').append(jv.badge());
        if (jv.debuffed()) tip.append('\n').append("DEBUFFED — no effect right now");
        return tip.toString();
    }

    /** A held item's hover text: name, effect, edition/stickers, and — for targeted cards — what it needs selected. */
    private static String itemTip(MatchSnapshot.ItemView it) {
        StringBuilder tip = new StringBuilder(it.label());
        if (!it.description().isEmpty()) tip.append('\n').append(it.description());
        if (!it.badge().isEmpty()) tip.append('\n').append(it.badge());
        if (it.minTargets() > 0)
            tip.append('\n').append("Needs ").append(it.minTargets())
               .append(" selected card").append(it.minTargets() > 1 ? "s" : "");
        return tip.toString();
    }

    private void mini(Renderer r, Layout.Rect rr, Color c, String label) {
        r.panel(rr.x(), rr.y(), rr.w(), rr.h(), c, Color.web("#0006"), 8, 2);
        r.textCenter(label, rr.centerX(), rr.centerY(), 10, INK);
    }

    private void drawDeck(Ui ui, double x, double y, double w, double h) {
        Renderer r = ui.r;
        double cardH = 140, cardY = y + h - cardH - 24;
        r.panel(x + 5, cardY, w - 10, cardH, Color.web("#d3c3a2"), Color.web("#7a5a3a"), 8, 3);
        r.textCenter(ui.s.deckRemaining() + " / " + ui.s.deckTotal(), x + w / 2, y + h - 12, 14, INK);
        // Hovering the pile opens the full deck view (GameClient checks this rect each frame).
        ui.deckRect = new Layout.Rect(x + 5, cardY, w - 10, cardH);
    }
}
