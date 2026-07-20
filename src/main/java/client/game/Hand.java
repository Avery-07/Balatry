package client.game;

import client.MatchSnapshot;
import client.engine.CardEntity;
import client.engine.Easing;
import client.engine.Layout;
import client.engine.Reconciler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The player's on-screen hand: retained {@link CardEntity}s reconciled from each snapshot (so selection and
 * in-flight motion survive incoming frames), fanned out via the tested {@link Layout}, animated, and hit-tested.
 * All the state that the old view kept in the node tree — and lost every frame — lives here instead.
 */
final class Hand {

    static final double CARD_W = 90, CARD_H = 126;
    private static final double EXIT_SECONDS = 0.45;   // how long a leaving card stays visible while flying out

    /** Why the next batch of cards will leave the hand — it decides where they fly. */
    enum Exit { PLAYED, DISCARDED }

    /** A card on its way out: it keeps animating but is no longer part of the live hand. */
    private record Exiting(CardEntity card, Exit reason) { }

    private final List<CardEntity> cards = new ArrayList<>();
    private final List<Exiting> exiting = new ArrayList<>();
    private final List<Double> exitAges = new ArrayList<>();   // parallel to exiting
    private List<CardEntity> ordered = new ArrayList<>();   // last drawn order (for topmost-first hit-testing)
    private int sort;                                        // 0 dealt, 1 rank, 2 suit
    private Exit nextExit = Exit.DISCARDED;                  // set by the Play/Discard gesture, read at reconcile

    /** Called by the Play/Discard gesture so the next reconcile knows where its missing cards went. */
    void expectExit(Exit reason) { nextExit = reason; }

    /**
     * A new frame: match the entities to {@code snapHand}, spawning new cards at the deck to be glided in.
     * Entities the snapshot no longer wants are not dropped — they move to the exit list and fly out: up and
     * away for a played hand, off to the side for a discard (the reconciler leaves them out of the live set,
     * which is exactly what lets us keep animating them here).
     */
    void reconcile(List<MatchSnapshot.HandCardView> snapHand, double spawnX, double spawnY) {
        List<Reconciler.Desired> desired = new ArrayList<>();
        Set<Integer> wantedIds = new HashSet<>();
        for (MatchSnapshot.HandCardView c : snapHand) {
            desired.add(new Reconciler.Desired(c.id(), c.rank(), c.suit(), c.label()));
            wantedIds.add(c.id());
        }
        for (CardEntity e : cards)
            if (!wantedIds.contains(e.id())) beginExit(e, nextExit);
        List<CardEntity> next = Reconciler.reconcile(cards, desired, spawnX, spawnY, 0.35, Easing.EASE_OUT_CUBIC);
        cards.clear(); cards.addAll(next);
        nextExit = Exit.DISCARDED;   // consumed; anything else that vanishes (a tarot ate it) slides out quietly
    }

    private void beginExit(CardEntity e, Exit reason) {
        e.setSelected(false);
        if (reason == Exit.PLAYED) e.moveTo(e.x(), e.y() - 260);        // up toward the score
        else                       e.moveTo(Ui.W + CARD_W, e.y() + 40); // off the right edge
        exiting.add(new Exiting(e, reason));
        exitAges.add(0.0);
    }

    void advance(double dt) {
        for (CardEntity c : cards) c.advance(dt);
        for (int i = exiting.size() - 1; i >= 0; i--) {
            exiting.get(i).card().advance(dt);
            exitAges.set(i, exitAges.get(i) + dt);
            if (exitAges.get(i) >= EXIT_SECONDS) { exiting.remove(i); exitAges.remove(i); }
        }
    }

    void setSort(int s)     { this.sort = s; }

    /** Fans the hand out (honoring the sort and a lift on selected cards), animates toward it, and draws it. */
    void render(Renderer r, double x, double y, double w, double h) {
        // Leaving cards draw first (under the live hand), fading as they age.
        for (int i = 0; i < exiting.size(); i++) {
            CardEntity e = exiting.get(i).card();
            double alpha = Math.max(0, 1 - exitAges.get(i) / EXIT_SECONDS);
            r.gc().setGlobalAlpha(alpha);
            r.card(e.rank(), e.suit(), e.x(), e.y(), CARD_W, CARD_H, 0, false);
            r.gc().setGlobalAlpha(1);
        }

        ordered = new ArrayList<>(cards);
        if (sort == 1) ordered.sort((a, b) -> b.rank() != a.rank() ? b.rank() - a.rank() : a.suit() - b.suit());
        else if (sort == 2) ordered.sort((a, b) -> a.suit() != b.suit() ? a.suit() - b.suit() : b.rank() - a.rank());

        double baseTop = y + h - CARD_H - 120;
        List<Layout.Placement> fan = Layout.fan(ordered.size(), x + w / 2, baseTop, CARD_W, 34, 12, 16);
        for (int k = 0; k < ordered.size(); k++) {
            CardEntity e = ordered.get(k);
            Layout.Placement p = fan.get(k);
            e.moveTo(p.x() + CARD_W / 2, p.y() + CARD_H / 2 - (e.selected() ? 30 : 0));
        }
        for (int k = 0; k < ordered.size(); k++) {
            CardEntity e = ordered.get(k);
            r.card(e.rank(), e.suit(), e.x(), e.y(), CARD_W, CARD_H, fan.get(k).rotationDeg(), e.selected());
        }
        int sel = selectedCount();
        r.textCenter(sel > 0 ? sel + " selected" : cards.size() + " cards",
                x + w / 2, baseTop + CARD_H + 20, 14, Palette.DIM);
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

    /** The model hand-indices of the selected cards, mapped through the snapshot by stable id. */
    List<Integer> selectedModelIndices(List<MatchSnapshot.HandCardView> snapHand) {
        Set<Integer> ids = new HashSet<>();
        for (CardEntity e : cards) if (e.selected()) ids.add(e.id());
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < snapHand.size(); i++) if (ids.contains(snapHand.get(i).id())) out.add(i);
        return out;
    }
}
