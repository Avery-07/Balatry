package model.engine;

import client.engine.CardEntity;
import client.engine.Easing;
import client.engine.Layout;
import client.engine.Motion;
import client.engine.Reconciler;
import client.engine.Tween;

import java.util.ArrayList;
import java.util.List;

/**
 * Harness for the Canvas renderer's pure engine core (easing, tweening, 2D motion, hand-fan layout, hit-testing).
 * These are the parts a game loop leans on every frame; keeping them rendering-agnostic means the animation feel
 * and the "what did I click" math are verified here rather than eyeballed in a window.
 */
public final class EngineTests {

    private static int failures = 0;
    private static final double EPS = 1e-9;

    public static void main(String[] args) {
        easing();
        tween();
        motion();
        fanLayout();
        hitTest();
        reconcile();

        System.out.println(failures == 0 ? "\nALL PASS" : "\n" + failures + " FAILURE(S)");
        if (failures != 0) System.exit(1);
    }

    private static void easing() {
        for (var f : List.of(Easing.EASE_OUT_CUBIC, Easing.EASE_IN_OUT, Easing.EASE_OUT_BACK, Easing.LINEAR)) {
            near("easing anchors at 0", f.applyAsDouble(0), 0);
            near("easing anchors at 1", f.applyAsDouble(1), 1);
        }
        check("easeOutCubic is past halfway at t=0.5", Easing.easeOutCubic(0.5) > 0.5);
        check("easeOutBack overshoots above 1 before settling", Easing.easeOutBack(0.85) > 1.0);
        check("clamp guards the low end", Easing.linear(-3) == 0);
        check("clamp guards the high end", Easing.easeInOutQuad(4) == 1);
    }

    private static void tween() {
        Tween t = new Tween(10, 1.0, Easing.LINEAR);
        near("a fresh tween sits at its value", t.value(), 10);
        check("a fresh tween is done", t.done());
        t.retarget(20);
        check("retargeting reopens the tween", !t.done());
        near("still at the start right after retarget", t.value(), 10);
        t.advance(0.5);
        near("linear tween is halfway at half duration", t.value(), 15);
        t.advance(0.5);
        near("tween lands exactly on target", t.value(), 20);
        check("tween is done at duration", t.done());
        t.advance(1.0);
        near("advancing past the end stays on target", t.value(), 20);
        t.snap(3);
        near("snap jumps immediately", t.value(), 3);
    }

    private static void motion() {
        Motion m = new Motion(0, 0, 1.0, Easing.LINEAR);
        m.moveTo(100, -40);
        m.advance(0.25);
        near("motion x eases toward target", m.x(), 25);
        near("motion y eases toward target", m.y(), -10);
        m.advance(1.0);
        check("motion settles", m.settled());
        near("motion x settled", m.x(), 100);
        near("motion y settled", m.y(), -40);
    }

    private static void fanLayout() {
        double centerX = 500, baseY = 400, cardW = 84;
        List<Layout.Placement> hand = Layout.fan(8, centerX, baseY, cardW, 24, 14, 18);
        check("a card per hand slot", hand.size() == 8);
        Layout.Placement first = hand.get(0), last = hand.get(7);
        near("the fan is centered on centerX", (first.x() + last.x() + cardW) / 2, centerX);
        check("the fan tilts symmetrically", Math.abs(first.rotationDeg() + last.rotationDeg()) < EPS);
        check("first card tilts left, last tilts right", first.rotationDeg() < 0 && last.rotationDeg() > 0);
        check("middle cards lift above the ends", hand.get(4).y() < first.y());
        check("empty hand yields no placements", Layout.fan(0, centerX, baseY, cardW, 24, 14, 18).isEmpty());

        List<Layout.Placement> one = Layout.fan(1, centerX, baseY, cardW, 24, 14, 18);
        near("a single card centers exactly", one.get(0).x() + cardW / 2, centerX);
        near("a single card is upright", one.get(0).rotationDeg(), 0);
    }

    private static void hitTest() {
        Layout.Rect r = new Layout.Rect(10, 20, 100, 50);
        check("point inside is a hit", r.contains(50, 40));
        check("the top-left corner counts", r.contains(10, 20));
        check("a point left of the box misses", !r.contains(5, 40));
        check("a point below the box misses", !r.contains(50, 90));
        near("center is where expected", r.centerX(), 60);
        near("center is where expected", r.centerY(), 45);
    }

    /** The bug the whole renderer migration exists to kill: an incoming frame must not wipe selection or motion. */
    private static void reconcile() {
        List<CardEntity> hand = new ArrayList<>();
        hand = Reconciler.reconcile(hand, List.of(
                new Reconciler.Desired(1, 0, 0, "2-SPADES"),
                new Reconciler.Desired(2, 5, 1, "7-HEARTS"),
                new Reconciler.Desired(3, 12, 2, "ACE-CLUBS")), 900, 700, 0.3, Easing.EASE_OUT_CUBIC);
        checkInt("first reconcile deals three cards", hand.size(), 3);

        // The player selects the middle card and it slides to its fan slot.
        CardEntity picked = hand.get(1);
        picked.setSelected(true);
        picked.moveTo(400, 300); picked.advance(1.0);

        // A new frame arrives (an opponent acted): same three cards, plus a fourth dealt in.
        List<CardEntity> next = Reconciler.reconcile(hand, List.of(
                new Reconciler.Desired(1, 0, 0, "2-SPADES"),
                new Reconciler.Desired(2, 5, 1, "7-HEARTS"),
                new Reconciler.Desired(3, 12, 2, "ACE-CLUBS"),
                new Reconciler.Desired(4, 8, 3, "10-DIAMONDS")), 900, 700, 0.3, Easing.EASE_OUT_CUBIC);
        checkInt("the new frame keeps four cards", next.size(), 4);
        check("the selected card is the SAME entity across the frame", next.get(1) == picked);
        check("selection survived the incoming frame", next.get(1).selected());
        near("the selected card kept its animated position", next.get(1).x(), 400);
        check("the freshly dealt card spawns at the deck", next.get(3).x() == 900 && next.get(3).y() == 700);

        // A card leaves (played): it drops out of the live set, the rest persist.
        List<CardEntity> after = Reconciler.reconcile(next, List.of(
                new Reconciler.Desired(1, 0, 0, "2-SPADES"),
                new Reconciler.Desired(3, 12, 2, "ACE-CLUBS"),
                new Reconciler.Desired(4, 8, 3, "10-DIAMONDS")), 900, 700, 0.3, Easing.EASE_OUT_CUBIC);
        checkInt("a played card leaves the live set", after.size(), 3);
        check("survivors keep their identity", after.get(0) == next.get(0));
    }

    private static void checkInt(String label, int actual, int expected) { check(label + " (" + actual + ")", actual == expected); }

    private static void near(String label, double actual, double expected) {
        check(label + " (" + actual + ")", Math.abs(actual - expected) < 1e-6);
    }

    private static void check(String label, boolean ok) {
        if (!ok) failures++;
        System.out.printf("%-52s %s%n", label, ok ? "PASS" : "FAIL");
    }
}
