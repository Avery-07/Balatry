package client.game;

import client.MatchSnapshot;
import client.engine.Easing;
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

    private final Collection collection = new Collection();   // the in-game view of the same joker grid the menu shows

    /**
     * The in-game Options/pause modal, opened from the sidebar Options button: Resume, Collection (the shared joker
     * grid), and Surrender (forfeit → main menu, behind a confirm). A full-screen scrim gates the table behind it,
     * and it clears the frame's buttons so only its own controls are live.
     */
    void options(Ui ui, double now) {
        if (ui.showCollection) { collection.render(ui, now, () -> ui.showCollection = false); return; }

        Renderer r = ui.r;
        ui.buttons.clear();   // the modal owns input
        ui.tips.clear();      // no background tooltip bleeds over the modal
        r.gc().setFill(Color.web("#04060a", 0.7)); r.gc().fillRect(0, 0, Ui.W, Ui.H);

        double pw = 420, ph = 312, px = (Ui.W - pw) / 2, py = (Ui.H - ph) / 2;
        r.panel(px, py, pw, ph, Color.web("#161719"), ORANGE, 16, 3);
        r.textCenterBold("OPTIONS", px + pw / 2, py + 40, 26, ORANGE);

        double bw = pw - 80, bx = px + 40;
        if (!ui.confirmSurrender) {
            r.textCenter("Match paused", px + pw / 2, py + 70, 12, DIM);
            ui.button(bx, py + 92,  bw, 48, "Resume",     GREEN,  DARK, () -> ui.showOptions = false, true);
            ui.button(bx, py + 150, bw, 48, "Collection", PURPLE, INK,  () -> { ui.showCollection = true; collection.open(); }, true);
            ui.button(bx, py + 208, bw, 48, "Surrender",  RED,    INK,  () -> ui.confirmSurrender = true, true);
        } else {
            r.textCenter("Leave the match?", px + pw / 2, py + 92, 16, INK);
            r.textCenter("You forfeit — the other players win.", px + pw / 2, py + 116, 12, DIM);
            ui.button(bx, py + 140, bw, 48, "Leave to Main Menu", RED, INK,
                    () -> { ui.showOptions = false; ui.confirmSurrender = false; ui.onLeaveMatch.run(); }, true);
            ui.button(bx, py + 202, bw, 48, "Cancel", Color.web("#3a3d44"), INK, () -> ui.confirmSurrender = false, true);
        }
    }

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
                            RelicTarget t = relicTargetFromSelection(ui, it.selector(), it.needsSeat(), it.label());
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

    /**
     * Builds a relic's target from the current selection — a card's rank/suit, the hand it forms, or a joker.
     * Shared by the held-relic Use flow and the use-immediately pack pick, so both derive a target the same way;
     * returns null (and sets a hint) when the selection cannot satisfy the relic's {@code selector} yet.
     */
    private RelicTarget relicTargetFromSelection(Ui ui, String selector, boolean needsSeat, String label) {
        MatchSnapshot s = ui.s;
        List<Integer> sel = ui.hand.selectedModelIndices(s.hand());
        switch (selector) {
            case "RANK" -> {
                if (sel.isEmpty()) { ui.status = "Select a card, then use " + label + "."; return null; }
                return RelicTarget.rank(null, DeckCard.Rank.values()[s.hand().get(sel.get(0)).rank()]);
            }
            case "SUIT" -> {
                if (sel.isEmpty()) { ui.status = "Select a card, then use " + label + "."; return null; }
                return RelicTarget.suit(null, DeckCard.Suit.values()[s.hand().get(sel.get(0)).suit()]);
            }
            case "HAND_TYPE" -> {
                if (sel.isEmpty()) { ui.status = "Select the cards forming a hand, then use " + label + "."; return null; }
                List<DeckCard> cards = new ArrayList<>();
                for (int i : sel) {
                    MatchSnapshot.HandCardView c = s.hand().get(i);
                    cards.add(new DeckCard(DeckCard.Rank.values()[c.rank()], DeckCard.Suit.values()[c.suit()]));
                }
                MatchSnapshot.EvalFlags f = s.evalFlags();   // derive the hand type with this seat's joker traits
                return RelicTarget.hand(null,
                        HandEvaluator.forTraits(f.fourFingers(), f.shortcut(), f.smeared(), f.splash(), f.dyscalculia()).evaluate(cards).type());
            }
            case "JOKER_SLOT" -> {
                if (ui.jokerTarget < 0) { ui.status = "Select one of your jokers, then use " + label + "."; return null; }
                return RelicTarget.joker(null, ui.jokerTarget);
            }
            default -> {
                if (needsSeat) { ui.status = label + " must be aimed at a seat — targeting UI is coming."; return null; }
                return RelicTarget.none();   // SELF / GLOBAL relics (Aegis, Metabole, Mimesis)
            }
        }
    }

    /**
     * The booster-pack overlay: the offered options as tiles, picked at once. It sits in the upper screen so the
     * hand (drawn beneath it by {@code GameClient}) stays visible for aiming a targeted pick — every pick is used
     * immediately, so a card needing selected cards is gated until enough are chosen.
     */
    void pack(Ui ui) {
        MatchSnapshot.PackOpeningView p = ui.s.opening();
        if (p == null) return;
        Renderer r = ui.r;
        r.gc().setFill(Color.web("#040a08", 0.62)); r.gc().fillRect(0, 0, Ui.W, Ui.H);
        // Sits below the top joker/consumable row and above the hand, so both stay visible and selectable for a
        // targeted pick (hand cards for tarots/relic rank-suit-hand, a joker for Katadesmos).
        double pw = 760, ph = 270, px = (Ui.W - pw) / 2, py = Ui.PAD + Ui.SLOT_H + 14;
        r.panel(px, py, pw, ph, Color.web("#241a3a"), PURPLE, 14, 3);
        r.textCenterBold(p.packName(), px + pw / 2, py + 26, 22, ORANGE);
        r.textCenter("Choose " + p.picksLeft() + " — click a card to preview, then Use", px + pw / 2, py + 50, 13, DIM);

        // Skip abandons the rest of the picks (Balatro-style) — registered as a pack button so the modal routes it.
        double skW = 92, skH = 34, skX = px + pw - skW - 16, skY = py + 12;
        r.panel(skX, skY, skW, skH, Color.web("#3a2c2c"), RED, 8, 2);
        r.textCenterBold("Skip", skX + skW / 2, skY + skH / 2, 14, INK);
        ui.packButtons.add(new Ui.Btn(new Layout.Rect(skX, skY, skW, skH), () -> ui.vm.skipPack()));

        int n = p.options().size();
        if (ui.packSel >= n || (ui.packSel >= 0 && p.options().get(ui.packSel).label() == null)) ui.packSel = -1;
        int selected = ui.hand.selectedModelIndices(ui.s.hand()).size();

        // The offered cards ride the same retained, count-scaled, draggable row the shop shelves use: a taken option
        // is a static "(taken)" hole, live ones lift and glide. Click previews (packSel); the Use button commits.
        double tw = 108, th = 110, oy = py + 64, tileCY = oy + th / 2;
        double[] all = Layout.slots(n, px + pw / 2, tw, 12, pw - 48);
        List<Integer> liveIds = new ArrayList<>();
        List<Double> liveX = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            MatchSnapshot.PackOption opt = p.options().get(i);
            if (opt.label() == null) {
                r.panel(all[i] - tw / 2, oy, tw, th, Color.web("#00000040"), EDGE, 8, 2);
                r.textCenter("(taken)", all[i], tileCY, 11, FAINT);
                continue;
            }
            liveIds.add(opt.id()); liveX.add(all[i]);
        }
        ui.packRow.reconcile(liveIds);
        double[] xs = new double[liveX.size()];
        for (int i = 0; i < xs.length; i++) xs[i] = liveX.get(i);
        ui.packRow.layout(xs, tileCY);

        for (int pass = 0; pass < 2; pass++)   // pass 0 the settled tiles, pass 1 the dragged one on top
            for (int i = 0; i < n; i++) {
                MatchSnapshot.PackOption opt = p.options().get(i);
                boolean held = ui.packRow.isDragged(opt.id());
                if (opt.label() == null || held != (pass == 1)) continue;
                double bob = held ? 0 : client.engine.Idle.bobPx(ui.now, opt.id(), 1.6);
                double sway = held ? 0 : client.engine.Idle.swayDeg(ui.now, opt.id(), 1.4);
                double tx = ui.packRow.x(opt.id()) - tw / 2, ty = ui.packRow.y(opt.id()) - th / 2 + bob;
                boolean chosen = ui.packSel == i;
                Layout.Rect rr = new Layout.Rect(tx, ty, tw, th);
                MatchSnapshot.CardFace cf = opt.card();   // a Standard-pack playing card draws as a real card
                javafx.scene.image.Image tex = cf == null ? r.jokerTexture(opt.label()) : null;   // else a joker or consumable face
                r.rotated(rr.centerX(), rr.centerY(), sway, () -> {   // the same idle sway/bob a card carries
                    boolean textured = false;
                    if (cf != null) {
                        double ch = Math.min(th, tw * 95.0 / 71.0), cw = ch * 71.0 / 95.0;
                        r.card(cf.rank(), cf.suit(), cf.enhancement(), cf.seal(), cf.edition(), tx + tw / 2, ty + th / 2, cw, ch, 0, false);
                        textured = true;
                    } else if (tex != null) { r.imageFit(tex, tx, ty, tw, th); textured = true; }
                    else textured = r.consumableFace(opt.label(), tx, ty, tw, th);
                    if (textured && cf == null) r.editionEffect(opt.edition(), tx, ty, tw, th, 8);   // shimmer over a joker/consumable face
                    if (textured) {
                        if (chosen) r.panel(tx, ty, tw, th, null, ORANGE, 8, 3);
                    } else {
                        r.panel(tx, ty, tw, th, Color.web("#2b2c30"), chosen ? ORANGE : EDGE, 8, chosen ? 3 : 2);
                        r.textCenterBold(opt.label(), tx + tw / 2, ty + th / 2, 12, INK);
                    }
                });
                ui.tip(rr, packTip(opt));   // hover explains the card
                int idx = i;
                ui.packButtons.add(new Ui.Btn(rr, () -> ui.packSel = idx));   // click previews; never commits
            }

        // The selected option's Use button, so a pick is a deliberate two-step (no accidental close). The effect
        // text is not repeated here — the hover tooltip already shows it.
        if (ui.packSel >= 0) {
            MatchSnapshot.PackOption opt = p.options().get(ui.packSel);
            double dy = oy + th + 12;
            r.textCenterBold(opt.label(), px + pw / 2, dy, 14, ORANGE);
            String need = optionNeed(opt);
            boolean ready = optionReady(ui, opt, selected);
            if (!need.isEmpty()) r.textCenter(need, px + pw / 2, dy + 18, 11, ready ? GREEN : ORANGE);
            // Drawn and registered as a pack button (not ui.button) because only packButtons are live during the modal.
            String verb = opt.isRelic() || opt.minTargets() > 0 ? "Use" : "Take";
            double bw = 120, bh = 36, bx = px + pw / 2 - bw / 2, by = py + ph - 52;
            r.panel(bx, by, bw, bh, ready ? ORANGE : Color.web("#3a3a3a"), ready ? ORANGE.darker() : EDGE, 8, 2);
            r.textCenterBold(verb, bx + bw / 2, by + bh / 2, 14, ready ? DARK : DIM);
            int sel = ui.packSel;
            ui.packButtons.add(new Ui.Btn(new Layout.Rect(bx, by, bw, bh), () -> pickOption(ui, sel, opt)));
        } else {
            r.textCenter("Click a card to see what it does.", px + pw / 2, py + ph - 30, 12, FAINT);
        }
    }

    /**
     * The blind-barrier free-pack gate: a skip tag (or the Wrath sin) granted a pack that hasn't been opened. The
     * barrier waits for it, so instead of the dead "waiting" popup the seat gets an Open button that routes into the
     * normal pack modal. Shown one pack at a time (opening removes it from the pending list; the next then appears).
     */
    void pendingPackPrompt(Ui ui) {
        java.util.List<MatchSnapshot.PendingPackView> packs = ui.s.pendingPacks();
        if (packs.isEmpty()) return;
        Renderer r = ui.r;
        r.gc().setFill(Color.web("#040a08", 0.66)); r.gc().fillRect(0, 0, Ui.W, Ui.H);
        MatchSnapshot.PendingPackView next = packs.get(0);
        double pw = 440, ph = 172, px = (Ui.W - pw) / 2, py = 250;
        r.panel(px, py, pw, ph, Color.web("#241a3a"), PURPLE, 14, 3);
        r.textCenterBold("Free Booster Pack", px + pw / 2, py + 38, 20, ORANGE);
        r.textCenter(next.label(), px + pw / 2, py + 70, 15, INK);
        if (packs.size() > 1) r.textCenter(packs.size() + " packs to open", px + pw / 2, py + 92, 11, DIM);
        double bw = 170, bh = 44, bx = px + pw / 2 - bw / 2, by = py + ph - 60;
        r.panel(bx, by, bw, bh, ORANGE, ORANGE.darker(), 8, 2);
        r.textCenterBold("Open", bx + bw / 2, by + bh / 2, 16, DARK);
        int idx = next.index();
        ui.packButtons.add(new Ui.Btn(new Layout.Rect(bx, by, bw, bh), () -> ui.vm.openPack(idx)));
    }

    /** A pack option's hover text: name, effect, and what a targeted pick still needs. */
    private static String packTip(MatchSnapshot.PackOption o) {
        StringBuilder t = new StringBuilder(o.label());
        if (!o.description().isEmpty()) t.append('\n').append(o.description());
        String need = optionNeed(o);
        if (!need.isEmpty()) t.append('\n').append(need);
        return t.toString();
    }

    /** Commits the previewed pick: a relic derives its target from the selection (like a held relic), a targeted consumable passes its selected cards. */
    private void pickOption(Ui ui, int idx, MatchSnapshot.PackOption opt) {
        if (opt.isRelic()) {
            RelicTarget t = relicTargetFromSelection(ui, opt.selector(), opt.needsSeat(), opt.label());
            if (t == null) return;   // relicTargetFromSelection set the hint
            ui.vm.pickFromPack(idx, t, List.of());
            ui.packSel = -1;
            return;
        }
        if (ui.hand.selectedModelIndices(ui.s.hand()).size() < opt.minTargets()) {
            ui.status = opt.label() + " needs " + opt.minTargets() + " selected card"
                    + (opt.minTargets() > 1 ? "s" : "") + " — select in your hand first.";
            return;
        }
        ui.vm.pickFromPack(idx, null, ui.hand.selectedModelIndices(ui.s.hand()));
        ui.packSel = -1;
    }

    /** The short "what this pick still needs" label under an option tile, or empty when it is ready to take as-is. */
    private static String optionNeed(MatchSnapshot.PackOption o) {
        if (!o.isRelic()) return o.minTargets() > 0 ? "needs " + o.minTargets() + " card" + (o.minTargets() > 1 ? "s" : "") : "";
        return switch (o.selector()) {
            case "RANK", "SUIT" -> "select a card";
            case "HAND_TYPE"    -> "select a hand";
            case "JOKER_SLOT"   -> "select a joker";
            default             -> "";
        };
    }

    /** Whether the current selection already satisfies what {@code o} needs (drives the ready colouring). */
    private static boolean optionReady(Ui ui, MatchSnapshot.PackOption o, int selected) {
        if (!o.isRelic()) return selected >= o.minTargets();
        return switch (o.selector()) {
            case "RANK", "SUIT", "HAND_TYPE" -> selected >= 1;
            case "JOKER_SLOT"                -> ui.jokerTarget >= 0;
            default                          -> !o.needsSeat();
        };
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
                r.card(c.rank(), c.suit(), c.enhancement(), c.seal(), c.edition(), x + cw / 2, y + ch / 2, cw, ch, 0, false);
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
        // The chip pops in with an overshoot over the first third of the beat, holds, then fades out over the last
        // ~45% — so it lands with a snap and lingers long enough to read, instead of appearing full-size and fading.
        double in = Easing.easeOutBack(Easing.clamp01(t / 0.33));
        double alpha = 1 - Easing.clamp01((t - 0.55) / 0.45);
        if (alpha <= 0) return;

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
                : (above ? src.y() - 30 : src.y() + src.h() + 26);
        double cy = baseY - 16 * t;   // gentle rise as it settles/fades

        // Kind styling: XMult is the payoff, so it is the biggest and boldest; chips the calm baseline.
        Color fill;
        double emphasis;
        int font;
        switch (e.kind()) {
            case "XMULT"         -> { fill = RED;                 emphasis = 1.34; font = 17; }
            case "MULT"          -> { fill = RED;                 emphasis = 1.12; font = 15; }
            case "CHIPS", "BASE" -> { fill = BLUE;                emphasis = 1.00; font = 14; }
            case "MONEY"         -> { fill = GOLD;                emphasis = 1.06; font = 14; }
            case "DESTROYED"     -> { fill = Color.web("#6b6f78"); emphasis = 1.00; font = 13; }
            default              -> { fill = PURPLE;             emphasis = 1.06; font = 14; }   // RETRIGGER, BALANCE
        }
        double scale = in * emphasis;
        double w = (Math.max(60, text.length() * 8.4 + 20)) * scale, h = 30 * scale;
        double x = cx - w / 2, y = cy - h / 2;

        var g = r(ui).gc();
        g.setGlobalAlpha(alpha * 0.32);
        r(ui).panel(x - 7, y - 7, w + 14, h + 14, fill, null, 13, 0);          // soft glow halo
        g.setGlobalAlpha(alpha);
        r(ui).panel(x, y, w, h, fill, Color.web("#000a"), 9, 2.5);             // the chip
        r(ui).textCenterBold(text, cx, cy, font * scale, INK);
        g.setGlobalAlpha(1);
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

    /**
     * The irremovable "your blind is over — waiting on the others" modal, shown while this seat sits at the blind
     * barrier. It has no close control by design: the only way past is for the other players to finish, at which
     * point the table advances on its own. What sits behind it is chosen by the caller — the blind-selection tiles
     * for a seat that skipped, the played-out round board for one that finished.
     */
    void blindWait(Ui ui) {
        Renderer r = ui.r;
        r.gc().setFill(Color.web("#03060a", 0.5)); r.gc().fillRect(0, 0, Ui.W, Ui.H);
        double pw = 540, ph = 168, px = (Ui.W - pw) / 2, py = (Ui.H - ph) / 2;
        r.panel(px, py, pw, ph, Color.web("#1a1b20"), ORANGE, 14, 3);
        boolean skipped = ui.blindSkipped();
        r.textCenterBold(skipped ? "Blind Skipped" : "Blind Complete", Ui.W / 2.0, py + 36, 22, ORANGE);
        r.textCenter("Waiting for the other players to finish the blind…", Ui.W / 2.0, py + 80, 14, INK);
        r.textCenter("The next phase opens once everyone is done.", Ui.W / 2.0, py + 106, 12, DIM);
        int total = ui.s.activeSeats();
        if (total > 1) r.textCenter(ui.s.blindDoneCount() + " of " + total + " finished", Ui.W / 2.0, py + 138, 12, FAINT);
    }

    /**
     * The Run Info overlay: standings and this seat's redeemed vouchers on the left, its poker-hand levels and
     * usage on the right. Standings honor the information boundary (opponents show only points and rank); the
     * hand levels and vouchers are the local seat's own.
     */
    void runInfo(Ui ui) {
        Renderer r = ui.r;
        MatchSnapshot s = ui.s;
        r.gc().setFill(Color.web("#040a08", 0.66)); r.gc().fillRect(0, 0, Ui.W, Ui.H);
        double pw = 960, ph = 660, px = (Ui.W - pw) / 2, py = (Ui.H - ph) / 2;
        r.panel(px, py, pw, ph, Color.web("#1a1b20"), ORANGE, 14, 3);
        r.textLeftBold("RUN INFO", px + 20, py + 16, 22, ORANGE);
        // The loadout: the deck is the table's, the sleeve and stake are this seat's own.
        r.textLeft(s.deckType() + "  ·  " + s.sleeve() + "  ·  " + s.stake(), px + 150, py + 22, 12, DIM);
        ui.button(px + pw - 90, py + 12, 70, 34, "Close", RED, INK, () -> ui.showRunInfo = false, true);

        double colGap = 28, leftX = px + 20, colW = (pw - 40 - colGap) / 2, rightX = leftX + colW + colGap;
        double top = py + 62;

        // --- Left column: standings, then redeemed vouchers ---
        r.textLeftBold("Standings", leftX, top, 16, INK);
        double ry = top + 26;
        for (MatchSnapshot.StandingView v : s.standings()) {
            r.panel(leftX, ry, colW, 44, v.isMe() ? Color.web("#221d10") : Color.web("#1a1b1f"), v.isMe() ? ORANGE : EDGE, 10, 2);
            r.textCenterBold(String.valueOf(v.rank() + 1), leftX + 24, ry + 22, 20, v.rank() == 0 ? PODIUM_GOLD : DIM);
            String tag = v.isMe() ? "  ◄ you" : (v.departed() ? "  (left)" : "");
            r.textLeftBold(v.name() + tag, leftX + 52, ry + 14, 16, v.departed() ? FAINT : INK);
            r.textCenterBold(v.points() + " pts", leftX + colW - 54, ry + 22, 16, v.departed() ? FAINT : GREEN);
            ry += 50;
        }

        ry += 14;
        r.textLeftBold("Redeemed Vouchers", leftX, ry, 16, INK);
        ry += 26;
        if (s.vouchers().isEmpty()) {
            r.textLeft("None redeemed yet.", leftX + 4, ry, 12, FAINT);
        } else for (MatchSnapshot.VoucherView v : s.vouchers()) {
            r.textLeftBold(v.name(), leftX + 4, ry, 13, GOLD);
            if (!v.description().isEmpty()) r.textLeft(v.description(), leftX + 4, ry + 16, 11, DIM);
            ry += 38;
        }

        // --- Right column: poker-hand levels and how often each has been played this run ---
        r.textLeftBold("Poker Hands", rightX, top, 16, INK);
        r.textLeft("level · played", rightX + colW - 150, top + 2, 11, DIM);
        double hy = top + 26;
        for (MatchSnapshot.HandLevelView h : s.handLevels()) {
            r.panel(rightX, hy, colW, 30, PANEL, EDGE, 8, 1);
            r.textLeftBold(Fmt.handName(h.type()), rightX + 10, hy + 8, 13, INK);
            r.textLeft(h.chips() + " × " + h.mult(), rightX + 150, hy + 9, 11, DIM);
            r.textCenterBold("lv." + h.level(), rightX + colW - 108, hy + 15, 13, BLUE);
            r.textCenterBold("×" + h.plays(), rightX + colW - 38, hy + 15, 13, h.plays() > 0 ? GOLD : FAINT);
            hy += 34;
        }

        r.textCenter("Opponents show only points & rank across the information boundary.", px + pw / 2, py + ph - 18, 12, FAINT);
    }
}
