package client.game;

import client.MatchSnapshot;
import client.engine.CardEntity;
import client.engine.Easing;
import client.engine.Idle;
import client.engine.Layout;
import client.engine.Reconciler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The player's on-screen hand: retained {@link CardEntity}s reconciled from each snapshot (so selection and
 * in-flight motion survive incoming frames), fanned out via the tested {@link Layout}, animated, idle-swaying,
 * hit-tested, and manually reorderable by drag. All the state that the old view kept in the node tree — and lost
 * every frame — lives here instead.
 */
final class Hand {

    // The hand only ever draws in-round and under the pack modal, so these size both of those contexts — bigger
    // than the old 90x126 so the cards you actually play with read large (Balatro-scale).
    static final double CARD_W = 108, CARD_H = 151;
    private static final double EXIT_SECONDS = 0.45;   // how long a leaving card stays visible while flying out

    /** Why the next batch of cards will leave the hand — it decides where they fly. */
    enum Exit { PLAYED, DISCARDED }

    /** A card on its way out: it keeps animating but is no longer part of the live hand. */
    private static final class Exiting {
        final CardEntity card;
        double age;
        Exiting(CardEntity card) { this.card = card; }
    }

    private final List<CardEntity> cards = new ArrayList<>();
    private final List<Exiting> exiting = new ArrayList<>();
    private final List<CardEntity> staged = new ArrayList<>();   // just-played cards, held while scoring replays
    private List<CardEntity> ordered = new ArrayList<>();   // last drawn order (for topmost-first hit-testing)
    private int sort;                                        // 0 dealt/manual, 1 rank, 2 suit
    private final Map<Integer, Integer> manualRank = new LinkedHashMap<>();   // id -> drag-chosen position
    private Exit nextExit = Exit.DISCARDED;                  // set by the Play/Discard gesture, read at reconcile
    private CardEntity dragged;                              // the card the player is holding, or null
    private double time;                                     // frame clock for the idle sway

    /** Called by the Play/Discard gesture so the next reconcile knows where its missing cards went. */
    void expectExit(Exit reason) { nextExit = reason; }

    /**
     * A new frame: match the entities to {@code snapHand}, spawning new cards at the deck to be glided in.
     * Entities the snapshot no longer wants are not dropped — they move to the exit list and fly out: up and
     * away for a played hand, off to the side for a discard (the reconciler leaves them out of the live set,
     * which is exactly what lets us keep animating them here). Face-down state rides in with the frame, so a
     * boss hiding a card is what triggers its flip.
     */
    void reconcile(List<MatchSnapshot.HandCardView> snapHand, double spawnX, double spawnY) {
        List<Reconciler.Desired> desired = new ArrayList<>();
        Set<Integer> wantedIds = new HashSet<>();
        for (MatchSnapshot.HandCardView c : snapHand) {
            desired.add(new Reconciler.Desired(c.id(), c.rank(), c.suit(), c.enhancement(), c.seal(), c.edition(), c.label()));
            wantedIds.add(c.id());
        }
        for (CardEntity e : cards)
            if (!wantedIds.contains(e.id())) beginExit(e, nextExit);
        // Stage the just-played cards left-to-right by where they sat on screen, so the scoring reel sweeps them in
        // the same order the model scores — the submitted (visual) order, which a manual reorder or sort can change.
        if (nextExit == Exit.PLAYED) staged.sort(java.util.Comparator.comparingDouble(CardEntity::x));
        List<CardEntity> next = Reconciler.reconcile(cards, desired, spawnX, spawnY, 0.35, Easing.EASE_OUT_BACK_SOFT);
        cards.clear(); cards.addAll(next);
        nextExit = Exit.DISCARDED;   // consumed; anything else that vanishes (a tarot ate it) slides out quietly

        for (int i = 0; i < snapHand.size(); i++) cards.get(i).setFaceDown(snapHand.get(i).faceDown());
        manualRank.keySet().retainAll(wantedIds);   // departed cards release their drag-chosen positions
    }

    private void beginExit(CardEntity e, Exit reason) {
        e.setSelected(false);
        e.endDrag();
        if (dragged == e) dragged = null;
        if (reason == Exit.PLAYED) {
            staged.add(e);   // played cards stand centre-screen while the scoring reel runs, then fly off
        } else {
            e.moveTo(Ui.W + CARD_W, e.y() + 40);   // discards leave straight away, off the right edge
            exiting.add(new Exiting(e));
        }
    }

    /** Lays the staged (just-played) cards out in a row centre-screen and draws them; call while scoring plays. */
    void renderStaged(Ui ui, double x, double y, double w) {
        if (staged.isEmpty()) return;
        double step = Math.min(CARD_W + 14, (w - CARD_W) / Math.max(1, staged.size()));
        double rowW = CARD_W + step * (staged.size() - 1);
        double sx = x + (w - rowW) / 2 + CARD_W / 2;
        for (int i = 0; i < staged.size(); i++) {
            CardEntity e = staged.get(i);
            e.moveTo(sx + i * step, y);
            double pop = ui.scorePop(e.id());   // the trigger animation: this card's beat is live
            double size = 1 + 0.18 * pop;
            double cx = e.x(), cy = e.y() - 18 * pop;
            double cw = CARD_W * size * e.stretchX(), ch = CARD_H * size * e.stretchY();   // squash & stretch from its motion
            ui.r.card(e.rank(), e.suit(), e.enhancement(), e.seal(), e.edition(), cx, cy, cw, ch, 0, pop > 0.05, e.flipT());
            ui.noteSourceRect(e.id(), new Layout.Rect(cx - cw / 2, cy - ch / 2, cw, ch));
        }
    }

    /** Releases the staged cards to fly away — called when the scoring reel finishes. */
    void releaseStaged() {
        for (CardEntity e : staged) {
            e.moveTo(e.x(), e.y() - 220);
            exiting.add(new Exiting(e));
        }
        staged.clear();
    }

    boolean hasStaged() { return !staged.isEmpty(); }

    void advance(double dt) {
        time += dt;
        for (CardEntity c : cards) c.advance(dt);
        for (CardEntity c : staged) c.advance(dt);
        for (int i = exiting.size() - 1; i >= 0; i--) {
            Exiting ex = exiting.get(i);
            ex.card.advance(dt);
            ex.age += dt;
            if (ex.age >= EXIT_SECONDS) exiting.remove(i);
        }
    }

    void setSort(int s) { this.sort = s; if (s != 0) manualRank.clear(); }   // a sort discards manual order

    /** Fans the hand out (honoring the sort, manual drag order, and a lift on selected cards), and draws it. */
    void render(Ui ui, double x, double y, double w, double h) {
        Renderer r = ui.r;
        // Leaving cards draw first (under the live hand), fading as they age.
        for (int i = 0; i < exiting.size(); i++) {
            CardEntity e = exiting.get(i).card;
            double alpha = Math.max(0, 1 - exiting.get(i).age / EXIT_SECONDS);
            r.gc().setGlobalAlpha(alpha);
            r.card(e.rank(), e.suit(), e.enhancement(), e.seal(), e.edition(), e.x(), e.y(),
                    CARD_W * e.stretchX(), CARD_H * e.stretchY(), 0, false, e.flipT());   // a leaving card streaks as it flies
            r.gc().setGlobalAlpha(1);
        }

        ordered = new ArrayList<>(cards);
        if (sort == 1) ordered.sort((a, b) -> b.rank() != a.rank() ? b.rank() - a.rank() : a.suit() - b.suit());
        else if (sort == 2) ordered.sort((a, b) -> a.suit() != b.suit() ? a.suit() - b.suit() : b.rank() - a.rank());
        else if (!manualRank.isEmpty())
            ordered.sort((a, b) -> Integer.compare(orderOf(a), orderOf(b)));

        // While a card is held, it occupies the slot nearest the cursor so the row parts to make room.
        if (dragged != null && ordered.remove(dragged))
            ordered.add(slotIndexFor(dragged.x(), x, w, ordered.size() + 1), dragged);

        double baseTop = y + h - CARD_H - 120;
        List<Layout.Placement> fan = Layout.fan(ordered.size(), x + w / 2, baseTop, CARD_W, 34, 12, 16);
        for (int k = 0; k < ordered.size(); k++) {
            CardEntity e = ordered.get(k);
            Layout.Placement p = fan.get(k);
            e.moveTo(p.x() + CARD_W / 2, p.y() + CARD_H / 2 - (e.selected() ? 30 : 0));
        }
        for (int k = 0; k < ordered.size(); k++) {
            CardEntity e = ordered.get(k);
            if (e == dragged) continue;   // the held card draws last, above everything
            double sway = Idle.swayDeg(time, e.id(), 1.4);
            double bob = Idle.bobPx(time, e.id(), 2.0);
            // The trigger animation: a held card whose scoring beat is live (Steel's X1.5) swells and lifts,
            // and its screen rect is captured so the effect square can anchor over the card rather than centre-screen.
            double pop = ui.scorePop(e.id());
            double size = 1 + 0.18 * pop;
            double drawY = e.y() + bob - 18 * pop;
            double cw = CARD_W * size * e.stretchX(), ch = CARD_H * size * e.stretchY();   // squash & stretch from its motion
            r.card(e.rank(), e.suit(), e.enhancement(), e.seal(), e.edition(), e.x(), drawY, cw, ch,
                    fan.get(k).rotationDeg() + sway, e.selected() || pop > 0.05, e.flipT());
            ui.noteSourceRect(e.id(), new Layout.Rect(e.x() - cw / 2, drawY - ch / 2, cw, ch));
            if (!e.showsBack())
                ui.tip(new Layout.Rect(e.x() - CARD_W / 2, e.y() - CARD_H / 2, CARD_W, CARD_H),
                        Fmt.cardTip(e.label(), e.rank(), e.suit()));
        }
        if (dragged != null)
            r.card(dragged.rank(), dragged.suit(), dragged.enhancement(), dragged.seal(), dragged.edition(), dragged.x(), dragged.y(), CARD_W, CARD_H,
                    0, dragged.selected(), dragged.flipT());

        int sel = selectedCount();
        r.textCenter(sel > 0 ? sel + " selected" : cards.size() + " cards",
                x + w / 2, baseTop + CARD_H + 20, 14, Palette.DIM);
    }

    private int orderOf(CardEntity e) {
        Integer o = manualRank.get(e.id());
        return o != null ? o : cards.indexOf(e) + 1000;   // unranked cards keep dealt order, after ranked ones
    }

    /** Which fan slot a card centered at {@code cx} belongs to, given {@code n} slots across the hand area. */
    private int slotIndexFor(double cx, double areaX, double areaW, int n) {
        if (n <= 1) return 0;
        double rel = (cx - areaX) / areaW;
        return Math.max(0, Math.min(n - 1, (int) Math.round(rel * (n - 1))));
    }

    // --- drag ---------------------------------------------------------------

    /** Picks up the card under the cursor for a manual reorder; returns whether one was grabbed. */
    boolean beginDrag(double x, double y) {
        CardEntity e = cardAt(x, y);
        if (e == null) return false;
        dragged = e;
        e.beginDrag();
        return true;
    }

    void dragTo(double x, double y) { if (dragged != null) dragged.dragTo(x, y); }

    /**
     * Releases the held card, committing the visual order it was dropped into as the manual order. A drop
     * anywhere is legal for the hand (reordering is client-side arrangement); the card glides into its slot.
     */
    void endDrag() {
        if (dragged == null) return;
        dragged.endDrag();
        dragged = null;
        sort = 0;   // a manual arrangement overrides any active sort
        manualRank.clear();
        for (int k = 0; k < ordered.size(); k++) manualRank.put(ordered.get(k).id(), k);
    }


    /** The topmost card under {@code (x,y)}, or null. */
    CardEntity cardAt(double x, double y) {
        for (int k = ordered.size() - 1; k >= 0; k--) {
            CardEntity e = ordered.get(k);
            if (new Layout.Rect(e.x() - CARD_W / 2, e.y() - CARD_H / 2, CARD_W, CARD_H).contains(x, y)) return e;
        }
        return null;
    }

    int selectedCount() { int n = 0; for (CardEntity e : cards) if (e.selected()) n++; return n; }

    /**
     * The selected entities in the left-to-right order the player sees — honoring a rank/suit sort and any manual
     * drag reorder, not the dealt/model order. Card order is meaningful: consumables like Sigil/Ouija/The World read
     * "the first selected card", and a played hand scores its cards in this order, so both must follow the arrangement
     * on screen. Falls back to dealt order before the first render has populated the visual order.
     */
    private List<CardEntity> selectedVisual() {
        List<CardEntity> base = ordered.isEmpty() ? cards : ordered;
        List<CardEntity> out = new ArrayList<>();
        for (CardEntity e : base) if (e.selected()) out.add(e);
        return out;
    }

    /** The selected entities in on-screen order — what the HUD's play preview evaluates. */
    List<CardEntity> selectedCards() { return selectedVisual(); }

    /**
     * The model hand-indices of the selected cards, in on-screen order — so the model receives them arranged as the
     * player sees them. The reorder is client-side (a card's model index never moves), so this is where the visual
     * order is mapped back onto model indices; {@code resolveHand} then preserves it, making the leftmost selected
     * card "the first" for a consumable and the first to score in a played hand.
     */
    List<Integer> selectedModelIndices(List<MatchSnapshot.HandCardView> snapHand) {
        Map<Integer, Integer> indexById = new HashMap<>();
        for (int i = 0; i < snapHand.size(); i++) indexById.put(snapHand.get(i).id(), i);
        List<Integer> out = new ArrayList<>();
        for (CardEntity e : selectedVisual()) {
            Integer idx = indexById.get(e.id());
            if (idx != null) out.add(idx);
        }
        return out;
    }
}
