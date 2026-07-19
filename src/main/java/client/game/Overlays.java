package client.game;

import client.MatchSnapshot;
import client.engine.Layout;
import javafx.scene.paint.Color;
import model.cards.DeckCard;
import model.cards.relics.RelicTarget;
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

    /** The Run Info overlay: the ranked standings table, honoring the information boundary. */
    void runInfo(Ui ui) {
        Renderer r = ui.r;
        r.gc().setFill(Color.web("#040a08", 0.66)); r.gc().fillRect(0, 0, Ui.W, Ui.H);
        double pw = 560, ph = 420, px = (Ui.W - pw) / 2, py = (Ui.H - ph) / 2;
        r.panel(px, py, pw, ph, Color.web("#1a1b20"), ORANGE, 14, 3);
        r.textLeftBold("RUN INFO — Standings", px + 20, py + 16, 22, ORANGE);
        ui.button(px + pw - 90, py + 12, 70, 34, "Close", RED, INK, () -> ui.showRunInfo = false, true);
        double ry = py + 66;
        for (MatchSnapshot.StandingView v : ui.s.standings()) {
            r.panel(px + 20, ry, pw - 40, 46, v.isMe() ? Color.web("#221d10") : Color.web("#1a1b1f"), v.isMe() ? ORANGE : EDGE, 10, 2);
            r.textCenterBold(String.valueOf(v.rank() + 1), px + 44, ry + 23, 20, v.rank() == 0 ? Color.web("#ffd45e") : DIM);
            r.textLeftBold(v.name() + (v.isMe() ? "  ◄ you" : ""), px + 74, ry + 15, 16, INK);
            r.textCenterBold(v.points() + " pts", px + pw - 70, ry + 23, 16, GREEN);
            ry += 54;
        }
        r.textCenter("Opponents show only points & rank across the information boundary.", px + pw / 2, py + ph - 20, 12, FAINT);
    }
}
