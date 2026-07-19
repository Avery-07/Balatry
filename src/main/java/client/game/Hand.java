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

    private final List<CardEntity> cards = new ArrayList<>();
    private List<CardEntity> ordered = new ArrayList<>();   // last drawn order (for topmost-first hit-testing)
    private int sort;                                        // 0 dealt, 1 rank, 2 suit

    /** A new frame: match the entities to {@code snapHand}, spawning new cards at the deck to be glided in. */
    void reconcile(List<MatchSnapshot.HandCardView> snapHand, double spawnX, double spawnY) {
        List<Reconciler.Desired> desired = new ArrayList<>();
        for (MatchSnapshot.HandCardView c : snapHand)
            desired.add(new Reconciler.Desired(c.id(), c.rank(), c.suit(), c.label()));
        List<CardEntity> next = Reconciler.reconcile(cards, desired, spawnX, spawnY, 0.35, Easing.EASE_OUT_CUBIC);
        cards.clear(); cards.addAll(next);
    }

    void advance(double dt) { for (CardEntity c : cards) c.advance(dt); }
    void setSort(int s)     { this.sort = s; }

    /** Fans the hand out (honoring the sort and a lift on selected cards), animates toward it, and draws it. */
    void render(Renderer r, double x, double y, double w, double h) {
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
        r.textCenter(cards.size() + " / " + cards.size(), x + w / 2, baseTop + CARD_H + 20, 14, Palette.DIM);
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
