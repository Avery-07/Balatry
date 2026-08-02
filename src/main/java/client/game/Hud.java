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
        double ix = x + 14, iw = w - 28;

        // The play preview: with cards selected, the chips×mult boxes price the hand the selection would score
        // (at this seat's levels) and its name appears above them; with nothing selected they show the last play.
        MatchSnapshot.HandLevelView preview = detectSelection(ui);

        // The juicy readouts: each counter chases its value and pops when it grows. While the scoring reel runs,
        // chips and mult follow the timeline's running totals instead of the snapshot's finished ones — that is
        // what makes the numbers climb beat by beat rather than jumping straight to the answer.
        MatchSnapshot.ScoreEventView beat = ui.liveEvent();
        // The round total waits for the boxes: while a beat is live the readout holds at the pre-play total, then
        // tallies up to the banked score once the reel settles — so chips×mult resolve first, then the score adds.
        score.retarget(beat != null ? ui.reelBaseScore : numeric(s.round() != null ? s.round().score() : "0"));
        if (beat != null) {
            chips.retarget(numeric(beat.chipsAfter()));
            mult.retarget(numeric(beat.multAfter()));
        } else {
            chips.retarget(preview != null ? preview.chips() : numeric(s.chips()));
            mult.retarget(preview != null ? preview.mult() : numeric(s.mult()));
        }
        money.retarget(s.money());

        // Lay the sections out to span the whole sidebar top-to-bottom: fixed section heights, with the slack split
        // into equal gaps, so the lower half is never empty. The readouts are sized big — this is the juicy panel.
        double hHeader = 64, hScore = 64, hName = 24, hCM = 92, hStats = 96, hMoney = 74, hAnte = 96, hSin = 46;
        double sum = hHeader + hScore + hName + hCM + hStats + hMoney + hAnte + hSin;
        double top = y + 16, bottom = y + h - 16;
        double gap = Math.max(8, (bottom - top - sum) / 7);
        double cy = top;

        String hdr = switch (s.phase()) {
            case SHOP -> "SHOP"; case RESULT -> Fmt.blindName(s.blind()) + " Defeated!";
            case BLIND -> "Score at least " + s.target(); default -> "Choose your next Blind"; };
        r.panel(ix, cy, iw, hHeader, PANEL2, EDGE, 9, 2);
        r.textCenterBold(hdr, ix + iw / 2, cy + hHeader / 2, s.phase() == MatchPhase.SHOP ? 26 : 16, s.phase() == MatchPhase.SHOP ? ORANGE : INK);
        cy += hHeader + gap;

        // Round score — a counting, popping readout.
        r.panel(ix, cy, iw, hScore, PANEL, EDGE, 8, 2);
        r.textLeftBold("Round score", ix + 12, cy + 14, 14, DIM);
        r.textCenterBold(whole(score.displayed()), ix + iw - 34, cy + hScore - 22, 26 * score.popScale(), INK);
        cy += hScore + gap;

        r.textCenterBold(preview == null ? " " : Fmt.handName(preview.type()) + "  lv." + preview.level(),
                ix + iw / 2, cy + hName / 2, 15, preview == null ? DIM : ORANGE);
        cy += hName + gap;

        double half = (iw - 30) / 2;
        r.panel(ix, cy, half, hCM, BLUE, null, 8, 0);
        r.textCenterBold(whole(chips.displayed()), ix + half / 2, cy + hCM / 2, 30 * chips.popScale(), INK);
        r.textCenterBold("X", ix + half + 15, cy + hCM / 2, 22, RED);
        r.panel(ix + half + 30, cy, half, hCM, RED, null, 8, 0);
        r.textCenterBold(whole(mult.displayed()), ix + half + 30 + half / 2, cy + hCM / 2, 30 * mult.popScale(), INK);
        // The home for ownerless scoring beats (base hand-type, sin transform, Plasma balance): they land on the
        // chips×mult they reshape rather than dead-centre. Overlays.scoreEffect reads this when a beat has no card.
        ui.scoreAnchor = new Layout.Rect(ix, cy, iw, hCM);
        cy += hCM + gap;

        double cellW = (iw - 102 - 10) / 2;
        ui.button(ix, cy, 92, hStats, "Run Info", RED, INK, () -> ui.showRunInfo = true, true);
        cell(r, ix + 102, cy, cellW, hStats, "Hands", String.valueOf(s.hands()), BLUE);
        cell(r, ix + 102 + cellW + 10, cy, cellW, hStats, "Discards", String.valueOf(s.discards()), RED);
        cy += hStats + gap;

        r.panel(ix, cy, iw, hMoney, PANEL, EDGE, 8, 2);
        r.textCenterBold("$" + whole(money.displayed()), ix + iw / 2, cy + hMoney / 2, 30 * money.popScale(), ORANGE);
        cy += hMoney + gap;

        ui.button(ix, cy, 92, hAnte, "Options", ORANGE, DARK, () -> ui.status = "Options — not wired.", true);
        cell(r, ix + 102, cy, cellW, hAnte, "Ante", s.ante() + "/" + s.anteCount(), ORANGE);
        cell(r, ix + 102 + cellW + 10, cy, cellW, hAnte, "Round", String.valueOf(s.roundNumber()), ORANGE);
        cy += hAnte + gap;

        r.panel(ix, cy, iw, hSin, Color.web("#2a1030"), PURPLE, 8, 2);
        r.textCenter("Ante sin — " + s.activeSin(), ix + iw / 2, cy + hSin / 2, 12, Color.web("#ecd7f5"));
        // Hover the sin to read its effect.
        if (!s.activeSinDesc().isEmpty())
            ui.tip(new Layout.Rect(ix, cy, iw, hSin), s.activeSin() + "\n" + s.activeSinDesc());
    }

    /**
     * The hand the current selection would score, at this seat's levels — or null when nothing is selected (or
     * outside a blind). Evaluation runs client-side on the selected ranks/suits ({@link HandEvaluator} is pure
     * and deterministic, the same evaluator the model scores with), and the pricing comes from the snapshot's
     * {@code handLevels} table, so the preview always matches what a play would actually earn.
     */
    // The preview is memoized: the selection only changes on a click, so re-running the evaluator every frame
    // in between is pure waste. Keyed by the selected ids plus the snapshot the levels came from.
    private List<Integer> previewKey = List.of();
    private MatchSnapshot previewSnap;
    private MatchSnapshot.HandLevelView previewCached;

    private MatchSnapshot.HandLevelView detectSelection(Ui ui) {
        if (ui.s.phase() != MatchPhase.BLIND) return null;
        List<CardEntity> selected = ui.hand.selectedCards();
        List<Integer> key = new ArrayList<>(selected.size());
        for (CardEntity e : selected) key.add(e.id());
        if (ui.s == previewSnap && key.equals(previewKey)) return previewCached;

        previewSnap = ui.s;
        previewKey = key;
        previewCached = evaluateSelection(ui, selected);
        return previewCached;
    }

    private static MatchSnapshot.HandLevelView evaluateSelection(Ui ui, List<CardEntity> selected) {
        List<DeckCard> cards = new ArrayList<>();
        for (CardEntity e : selected) {
            if (e.rank() < 0) return null;   // a face-down card is selected: its identity is hidden, so no preview
            cards.add(new DeckCard(DeckCard.Rank.values()[e.rank()], DeckCard.Suit.values()[e.suit()]));
        }
        if (cards.isEmpty()) return null;
        MatchSnapshot.EvalFlags f = ui.s.evalFlags();   // preview classifies with the same joker traits the model scores with
        String type = HandEvaluator.forTraits(f.fourFingers(), f.shortcut(), f.smeared(), f.splash(), f.dyscalculia()).evaluate(cards).type().name();
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
        r.textCenter(k, x + w / 2, y + h * 0.30, 12, DIM);
        r.textCenterBold(v, x + w / 2, y + h * 0.66, 24, vc);
    }

    private void drawTopSlots(Ui ui, double x, double y, double w, double h) {
        Renderer r = ui.r;
        MatchSnapshot s = ui.s;
        double tileW = Ui.SLOT_TILE_W, tileH = Ui.SLOT_TILE_H, tileCY = y + 24 + tileH / 2;

        // Jokers: a retained, draggable row — reconcile by card id, lay slots out, draw at animated positions. The
        // slots are centered and count-scaled (Layout.slots) within a left zone, so the row spreads and compresses
        // with its size instead of marching from a fixed origin — the deck-hover rule, shared by every slot line.
        r.textLeftBold(s.jokerSlotsUsed() + "/" + s.jokerSlotsMax(), x, y, 18, INK);
        List<Integer> jokerIds = new ArrayList<>();
        for (MatchSnapshot.JokerView jv : s.jokers()) jokerIds.add(jv.id());
        ui.jokerRow.reconcile(jokerIds);
        ui.jokerRow.layout(Layout.slots(jokerIds.size(), x + w * 0.33, tileW, 8, w * 0.62), tileCY);

        // Pass 0 draws the settled tiles (with their idle bob), pass 1 the held one on top of everything.
        for (int pass = 0; pass < 2; pass++)
            for (int j = 0; j < s.jokers().size(); j++) {
                MatchSnapshot.JokerView jv = s.jokers().get(j);
                if (ui.jokerRow.isDragged(jv.id()) != (pass == 1)) continue;
                drawJokerTile(ui, jv, j, tileW, tileH, pass == 0 ? client.engine.Idle.bobPx(time, j, 1.6) : 0);
            }

        // Consumables/relics: same treatment, centered and count-scaled within a right zone (model order left→right).
        r.textLeftBold(s.consumableSlotsUsed() + "/" + s.consumableSlotsMax(), x + w - 40, y, 18, INK);
        List<Integer> itemIds = new ArrayList<>();
        for (MatchSnapshot.ItemView it : s.inventory()) itemIds.add(it.id());
        ui.itemRow.reconcile(itemIds);
        ui.itemRow.layout(Layout.slots(itemIds.size(), x + w * 0.84, tileW, 8, w * 0.30), tileCY);

        for (int pass = 0; pass < 2; pass++)
            for (int i = 0; i < s.inventory().size(); i++) {
                MatchSnapshot.ItemView it = s.inventory().get(i);
                if (ui.itemRow.isDragged(it.id()) != (pass == 1)) continue;
                drawItemTile(ui, it, i, tileW, tileH, pass == 0 ? client.engine.Idle.bobPx(time, 40 + i, 1.6) : 0);
            }
    }

    private void drawJokerTile(Ui ui, MatchSnapshot.JokerView jv, int index, double tileW, double tileH, double bob) {
        Renderer r = ui.r;
        // The trigger animation: a joker whose beat is live swells and lifts while its effect square floats.
        double pop = ui.scorePop(jv.id());
        double w = tileW * (1 + 0.22 * pop), h = tileH * (1 + 0.22 * pop);
        Layout.Rect rr = new Layout.Rect(ui.jokerRow.x(jv.id()) - w / 2,
                ui.jokerRow.y(jv.id()) - h / 2 + bob - 10 * pop, w, h);
        ui.noteSourceRect(jv.id(), rr);
        tileW = w; tileH = h;
        // The same idle sway a card carries; the held tile follows the cursor instead, so it never sways.
        double sway = ui.jokerRow.isDragged(jv.id()) ? 0 : client.engine.Idle.swayDeg(ui.now, jv.id(), 1.4);
        javafx.scene.image.Image tex = r.jokerTexture(jv.name());
        r.rotated(rr.centerX(), rr.centerY(), sway, () -> {
            // A debuffed joker greys out; a badge (edition/stickers) draws as a footer strip on the tile.
            if (tex != null) {
                r.imageFit(tex, rr.x(), rr.y(), rr.w(), rr.h());
                if (jv.debuffed()) r.panel(rr.x(), rr.y(), rr.w(), rr.h(), Color.web("#1a1a1eaa"), null, 8, 0);
            } else {
                mini(r, rr, jv.debuffed() ? Color.web("#4a4a4f") : Color.web("#c0392b"), Fmt.shortName(jv.name()));
            }
            r.editionEffect(jv.edition(), rr.x(), rr.y(), rr.w(), rr.h(), 8);   // Foil/Holo/Poly shimmer or Negative
            if (!jv.badge().isEmpty()) {
                r.panel(rr.x(), rr.y() + rr.h() - 16, rr.w(), 16, Color.web("#000a"), null, 6, 0);
                r.textCenter(jv.badge(), rr.centerX(), rr.y() + rr.h() - 8, 8, GOLD);
            }
            if (index == ui.jokerTarget) r.panel(rr.x() - 3, rr.y() - 3, rr.w() + 6, rr.h() + 6, null, ORANGE, 10, 3);
        });
        ui.jokerSel.add(new Ui.Sel(rr, "joker", index));
        ui.tip(rr, jokerTip(jv));
    }

    private void drawItemTile(Ui ui, MatchSnapshot.ItemView it, int index, double tileW, double tileH, double bob) {
        Renderer r = ui.r;
        Layout.Rect rr = new Layout.Rect(ui.itemRow.x(it.id()) - tileW / 2, ui.itemRow.y(it.id()) - tileH / 2 + bob, tileW, tileH);
        double sway = ui.itemRow.isDragged(it.id()) ? 0 : client.engine.Idle.swayDeg(ui.now, it.id(), 1.4);
        r.rotated(rr.centerX(), rr.centerY(), sway, () -> {   // a consumable shows its planet/tarot/spectral face; a relic keeps the tile
            if (!r.consumableFace(it.label(), rr.x(), rr.y(), rr.w(), rr.h()))
                mini(r, rr, Color.web("#3d3357"), Fmt.shortName(it.label()));
            r.editionEffect(it.edition(), rr.x(), rr.y(), rr.w(), rr.h(), 8);   // Negative etc. on a consumable/relic
        });
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
