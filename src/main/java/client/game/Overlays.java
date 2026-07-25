package client.game;

import client.MatchSnapshot;
import client.engine.Layout;
import javafx.scene.paint.Color;
import model.items.DeckCard;
import model.items.relics.RelicTarget;
import model.game.scoring.HandEvaluator;

import java.util.ArrayList;
import java.util.List;

import static client.game.Palette.*;

/**
 * The floating layers: the contextual action buttons that appear beside a selected item or joker (Buy / Use /
 * Sell), the relic-as-tarot target derivation, and the Run Info standings overlay.
 */
final class Overlays {

    /** The contextual actions for the currently-selected shop/held item and the targeted joker. */
    void contextActions(Ui ui) {
        drawItemActions(ui);
        drawJokerActions(ui);
    }

    /** Rings the selected item and floats its actions (Buy / Use / Sell) just below it. */
    private void drawItemActions(Ui ui) {
        if (ui.selKind == null) return;
        Ui.Sel sel = null;
        for (Ui.Sel se : ui.selectables) if (se.kind().equals(ui.selKind) && se.index() == ui.selIndex) { sel = se; break; }
        if (sel == null) { ui.selKind = null; return; }   // the item was bought/sold/used, or the phase changed

        Layout.Rect rr = sel.rect();
        ui.r.panel(rr.x() - 4, rr.y() - 4, rr.w() + 8, rr.h() + 8, null, ORANGE, 12, 3);   // selection ring

        List<Ui.Act> acts = actionsFor(ui, sel);
        if (acts.isEmpty()) return;
        double bw = 68, gap = 8, total = acts.size() * bw + (acts.size() - 1) * gap;
        double bx = rr.centerX() - total / 2, by = rr.y() + rr.h() + 8;
        for (Ui.Act a : acts) {
            ui.button(bx, by, bw, 30, a.label(), a.color(), a.text(), () -> { a.run().run(); ui.selKind = null; }, true);
            bx += bw + gap;
        }
    }

    /** Shop items Buy/Redeem; held consumables Use/Sell; held relics Use (from selection) / Sell. */
    private List<Ui.Act> actionsFor(Ui ui, Ui.Sel sel) {
        List<Ui.Act> out = new ArrayList<>();
        MatchSnapshot s = ui.s;
        switch (sel.kind()) {
            case "shopSlot"    -> out.add(new Ui.Act("Buy", ORANGE, DARK, () -> ui.vm.buyCard(sel.index())));
            case "shopPack"    -> out.add(new Ui.Act("Buy", ORANGE, DARK, () -> ui.vm.buyPack(sel.index())));
            case "shopVoucher" -> out.add(new Ui.Act("Redeem", ORANGE, DARK, () -> ui.vm.redeemVoucher(sel.index())));
            case "item" -> {
                if (sel.index() >= s.inventory().size()) break;
                MatchSnapshot.ItemView it = s.inventory().get(sel.index());
                if (it.isRelic()) {
                    if (!it.needsSeat())
                        out.add(new Ui.Act("Use", ORANGE, DARK, () -> {
                            RelicTarget t = relicTargetFromSelection(ui, it);
                            if (t != null) ui.vm.useRelic(it.modelIndex(), t);
                        }));
                    else
                        out.add(new Ui.Act("Aim…", Color.web("#303237"), DIM, () -> ui.status = it.label() + " must be aimed at a seat — seat targeting is coming."));
                    out.add(new Ui.Act("Sell", RED, INK, () -> ui.vm.sellRelic(it.modelIndex())));
                } else {
                    // A targeted consumable (Strength, The Hierophant, …) refuses to fire into nothing: with too
                    // few cards selected the button turns into a hint instead of wasting the card. The model
                    // enforces the same rule, so this is a courtesy, not the protection.
                    int selected = ui.hand.selectedModelIndices(s.hand()).size();
                    if (selected < it.minTargets())
                        out.add(new Ui.Act("Use", Color.web("#303237"), DIM, () -> ui.status =
                                it.label() + " needs " + it.minTargets() + " selected card"
                                + (it.minTargets() > 1 ? "s" : "") + " — select in your hand first."));
                    else
                        out.add(new Ui.Act("Use", ORANGE, DARK, () -> ui.vm.useConsumable(it.modelIndex(), ui.hand.selectedModelIndices(s.hand()))));
                    out.add(new Ui.Act("Sell", RED, INK, () -> ui.vm.sellConsumable(it.modelIndex())));
                }
            }
            default -> { }
        }
        return out;
    }

    /** The targeted joker's Sell button (the same joker selection is what Katadesmos reads). */
    private void drawJokerActions(Ui ui) {
        if (ui.jokerTarget < 0) return;
        Ui.Sel sel = null;
        for (Ui.Sel se : ui.jokerSel) if (se.index() == ui.jokerTarget) { sel = se; break; }
        if (sel == null) { ui.jokerTarget = -1; return; }
        Layout.Rect rr = sel.rect();
        ui.button(rr.centerX() - 34, rr.y() + rr.h() + 8, 68, 30, "Sell", RED, INK,
                () -> { ui.vm.sellJoker(ui.jokerTarget); ui.jokerTarget = -1; }, true);
    }

    /** Builds a relic's target from the current selection — a card's rank/suit, the hand it forms, or a joker. */
    private RelicTarget relicTargetFromSelection(Ui ui, MatchSnapshot.ItemView relic) {
        MatchSnapshot s = ui.s;
        List<Integer> sel = ui.hand.selectedModelIndices(s.hand());
        switch (relic.selector()) {
            case "RANK" -> {
                if (sel.isEmpty()) { ui.status = "Select a card, then Use " + relic.label() + "."; return null; }
                return RelicTarget.rank(null, DeckCard.Rank.values()[s.hand().get(sel.get(0)).rank()]);
            }
            case "SUIT" -> {
                if (sel.isEmpty()) { ui.status = "Select a card, then Use " + relic.label() + "."; return null; }
                return RelicTarget.suit(null, DeckCard.Suit.values()[s.hand().get(sel.get(0)).suit()]);
            }
            case "HAND_TYPE" -> {
                if (sel.isEmpty()) { ui.status = "Select the cards forming a hand, then Use " + relic.label() + "."; return null; }
                List<DeckCard> cards = new ArrayList<>();
                for (int i : sel) {
                    MatchSnapshot.HandCardView c = s.hand().get(i);
                    cards.add(new DeckCard(DeckCard.Rank.values()[c.rank()], DeckCard.Suit.values()[c.suit()]));
                }
                return RelicTarget.hand(null, new HandEvaluator().evaluate(cards).type());
            }
            case "JOKER_SLOT" -> {
                if (ui.jokerTarget < 0) { ui.status = "Select one of your jokers, then Use " + relic.label() + "."; return null; }
                return RelicTarget.joker(null, ui.jokerTarget);
            }
            default -> {
                if (relic.needsSeat()) { ui.status = relic.label() + " must be aimed at a seat — targeting UI is coming."; return null; }
                return RelicTarget.none();   // SELF / GLOBAL relics (Aegis, Metabole, Mimesis)
            }
        }
    }

    /** The modal booster-pack overlay: the offered options as tiles; click one to pick it (spends a pick). */
    void pack(Ui ui) {
        MatchSnapshot.PackOpeningView p = ui.s.opening();
        if (p == null) return;
        Renderer r = ui.r;
        r.gc().setFill(Color.web("#040a08", 0.74)); r.gc().fillRect(0, 0, Ui.W, Ui.H);
        double pw = 760, ph = 380, px = (Ui.W - pw) / 2, py = (Ui.H - ph) / 2;
        r.panel(px, py, pw, ph, Color.web("#241a3a"), PURPLE, 14, 3);
        r.textCenterBold(p.packName(), px + pw / 2, py + 28, 22, ORANGE);
        r.textCenter("Pick " + p.picksLeft() + (p.picksLeft() == 1 ? " card" : " cards"), px + pw / 2, py + 54, 14, DIM);

        int n = p.options().size();
        double ow = 110, gap = 16, total = n * ow + Math.max(0, n - 1) * gap;
        double ox = px + (pw - total) / 2, oy = py + 90, oh = 168;
        for (int i = 0; i < n; i++) {
            String label = p.options().get(i);
            double x = ox + i * (ow + gap);
            if (label == null) {
                r.panel(x, oy, ow, oh, Color.web("#00000040"), EDGE, 8, 2);
                r.textCenter("(taken)", x + ow / 2, oy + oh / 2, 11, FAINT);
            } else {
                r.panel(x, oy, ow, oh, Color.web("#2b2c30"), EDGE, 8, 2);
                r.textCenter(label, x + ow / 2, oy + oh / 2, 11, INK);
                int idx = i;
                ui.packButtons.add(new Ui.Btn(new Layout.Rect(x, oy, ow, oh), () -> ui.vm.pickFromPack(idx)));
            }
        }
        r.textCenter("Choose your pick(s) — the pack closes once the budget is spent.", px + pw / 2, py + ph - 18, 11, FAINT);
    }

    /**
     * The hover tooltip: the topmost registered tip under the mouse, drawn as a panel beside the cursor. Screens
     * register their regions with {@link Ui#tip}; this runs after everything else so the panel sits on top.
     */
    void tooltip(Ui ui) {
        Ui.Tip hit = null;
        for (Ui.Tip t : ui.tips) if (t.rect().contains(ui.mouseX, ui.mouseY)) hit = t;   // last registered wins
        if (hit == null) return;

        String[] lines = hit.text().split("\n");
        double size = 12, lineH = size + 5, padX = 10, padY = 8;
        double w = 0;
        for (String line : lines) w = Math.max(w, line.length() * size * 0.62);
        w += 2 * padX;
        double h = lines.length * lineH + 2 * padY;
        double x = Math.min(ui.mouseX + 16, Ui.W - w - 8);
        double y = Math.min(ui.mouseY + 18, Ui.H - h - 8);

        Renderer r = ui.r;
        r.panel(x, y, w, h, Color.web("#101114", 0.96), ORANGE, 8, 2);
        for (int i = 0; i < lines.length; i++)
            if (i == 0) r.textLeftBold(lines[i], x + padX, y + padY + i * lineH, size, ORANGE);
            else        r.textLeft(lines[i], x + padX, y + padY + i * lineH, size, INK);
    }

    /**
     * The deck-pile hover: the deck <em>as it exists now</em>, one drawn card per physical card — a destroyed
     * card is simply absent, a created one appears, duplicates sit side by side (no counts). Each suit is a row,
     * sorted by rank; rows longer than the panel overlap their cards like a hand fan. A card is greyed once it
     * can no longer be drawn this round — played, discarded, or currently held in hand.
     */
    void deckContents(Ui ui) {
        Renderer r = ui.r;
        r.gc().setFill(Color.web("#040a08", 0.72)); r.gc().fillRect(0, 0, Ui.W, Ui.H);

        // Group the actual cards by suit row, then sort each row by rank (live ones draw the same as spent —
        // order must stay stable whatever happens to the cards, so the eye can track a rank column-ish).
        @SuppressWarnings("unchecked")
        List<MatchSnapshot.DeckCardView>[] rows = new List[4];
        for (int s = 0; s < 4; s++) rows[s] = new ArrayList<>();
        for (MatchSnapshot.DeckCardView c : ui.s.deckCards())
            if (c.suit() >= 0 && c.suit() < 4) rows[c.suit()].add(c);
        for (List<MatchSnapshot.DeckCardView> row : rows)
            row.sort((a, b) -> a.rank() != b.rank() ? a.rank() - b.rank() : 0);

        double cw = 62, ch = 88, rowGap = 12;
        double gw = Ui.W - 160, gh = 4 * ch + 3 * rowGap;
        double gx = (Ui.W - gw) / 2, gy = (Ui.H - gh) / 2 + 14;

        r.textCenterBold("DECK — " + ui.s.deckRemaining() + " of " + ui.s.deckTotal() + " still to come",
                Ui.W / 2.0, gy - 40, 20, ORANGE);
        r.textCenter("greyed cards can no longer be drawn this round (played, discarded, or in your hand)",
                Ui.W / 2.0, gy - 18, 12, DIM);

        for (int s = 0; s < 4; s++) {
            List<MatchSnapshot.DeckCardView> row = rows[s];
            if (row.isEmpty()) continue;
            double y = gy + s * (ch + rowGap);
            // Fit the row: full spacing when it fits, overlapping cards when the deck is fat (Crowded).
            double step = row.size() == 1 ? 0 : Math.min(cw + 6, (gw - cw) / (row.size() - 1));
            double rowW = cw + step * (row.size() - 1);
            double x = gx + (gw - rowW) / 2;
            for (MatchSnapshot.DeckCardView c : row) {
                r.gc().setGlobalAlpha(c.live() ? 1.0 : 0.28);
                r.card(c.rank(), c.suit(), x + cw / 2, y + ch / 2, cw, ch, 0, false);
                x += step;
            }
            r.gc().setGlobalAlpha(1.0);
        }
    }

    /**
     * The effect animation: a small square popping up beside whichever card is acting, naming what it just did
     * ("+5 Chips", "X1.5 Mult", "Retrigger"). Paired with the source's own trigger pop — one beat of the reel is
     * always both. The square rises and fades over the beat, so consecutive effects from one card never stack.
     */
    void scoreEffect(Ui ui) {
        MatchSnapshot.ScoreEventView e = ui.liveEvent();
        if (e == null) return;
        String text = effectText(e);
        if (text.isEmpty()) return;

        double t = ui.reel.beatProgress();
        // A card/joker beat anchors over its tile; an ownerless beat (base hand-type, sin transform, Plasma
        // balance) has no tile, so it lands on the chips×mult readout it reshapes. Only a wholly unplaceable
        // beat (no anchor registered this frame) falls back to centre-screen.
        Layout.Rect src = ui.liveSourceRect();
        boolean ownerless = src == null;
        if (ownerless) src = ui.scoreAnchor;
        double cx = src != null ? src.centerX() : Ui.W / 2.0;
        // Anchor below the source, except near the screen's bottom (the hand fan) or on the sidebar readout,
        // where below would collide with what sits under it — there the square rises above instead.
        boolean above = ownerless || (src != null && src.y() + src.h() > Ui.H - 200);
        double baseY = src == null ? Ui.H / 2.0 + 90
                : (above ? src.y() - 26 : src.y() + src.h() + 22);
        double cy = baseY - 18 * t;   // drifts as it fades

        Color fill = switch (e.kind()) {
            case "CHIPS", "BASE" -> BLUE;
            case "MULT", "XMULT" -> RED;
            case "MONEY"         -> GOLD;
            case "DESTROYED"     -> Color.web("#6b6f78");
            default              -> PURPLE;   // RETRIGGER, BALANCE
        };
        double w = Math.max(58, text.length() * 8.0 + 16), h = 26;
        r(ui).gc().setGlobalAlpha(Math.max(0, 1 - t * t));   // full early in the beat, gone by its end
        r(ui).panel(cx - w / 2, cy - h / 2, w, h, fill, Color.web("#0009"), 8, 2);
        r(ui).textCenterBold(text, cx, cy, 13, INK);
        r(ui).gc().setGlobalAlpha(1);
    }

    /** The words on an effect square. */
    private static String effectText(MatchSnapshot.ScoreEventView e) {
        return switch (e.kind()) {
            case "BASE"      -> e.sourceName();
            case "CHIPS"     -> "+" + e.amount() + " Chips";
            case "MULT"      -> "+" + e.amount() + " Mult";
            case "XMULT"     -> "X" + e.amount() + " Mult";
            case "MONEY"     -> (e.amount().startsWith("-") ? "" : "+") + "$" + e.amount();
            case "RETRIGGER" -> "Retrigger";
            case "DESTROYED" -> "Destroyed";
            case "BALANCE"   -> "Balanced";
            default          -> "";
        };
    }

    private static Renderer r(Ui ui) { return ui.r; }

    /** The Run Info overlay: the ranked standings table, honoring the information boundary. */
    void runInfo(Ui ui) {
        Renderer r = ui.r;
        r.gc().setFill(Color.web("#040a08", 0.66)); r.gc().fillRect(0, 0, Ui.W, Ui.H);
        double pw = 560, ph = 420, px = (Ui.W - pw) / 2, py = (Ui.H - ph) / 2;
        r.panel(px, py, pw, ph, Color.web("#1a1b20"), ORANGE, 14, 3);
        r.textLeftBold("RUN INFO — Standings", px + 20, py + 16, 22, ORANGE);
        ui.button(px + pw - 90, py + 12, 70, 34, "Close", RED, INK, () -> ui.showRunInfo = false, true);

        // The loadout: the deck is the table's, the sleeve and stake are this seat's own.
        r.textLeft(ui.s.deckType() + "  ·  " + ui.s.sleeve() + "  ·  " + ui.s.stake(), px + 20, py + 44, 12, DIM);

        double ry = py + 76;
        for (MatchSnapshot.StandingView v : ui.s.standings()) {
            r.panel(px + 20, ry, pw - 40, 46, v.isMe() ? Color.web("#221d10") : Color.web("#1a1b1f"), v.isMe() ? ORANGE : EDGE, 10, 2);
            r.textCenterBold(String.valueOf(v.rank() + 1), px + 44, ry + 23, 20, v.rank() == 0 ? PODIUM_GOLD : DIM);
            String tag = v.isMe() ? "  ◄ you" : (v.departed() ? "  (left)" : "");
            r.textLeftBold(v.name() + tag, px + 74, ry + 15, 16, v.departed() ? FAINT : INK);
            r.textCenterBold(v.points() + " pts", px + pw - 70, ry + 23, 16, v.departed() ? FAINT : GREEN);
            ry += 54;
        }
        r.textCenter("Opponents show only points & rank across the information boundary.", px + pw / 2, py + ph - 20, 12, FAINT);
    }
}
