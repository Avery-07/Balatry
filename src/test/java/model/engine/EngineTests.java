package model.engine;

import client.engine.CardEntity;
import client.engine.Counter;
import client.engine.Easing;
import client.engine.Fader;
import client.engine.Idle;
import client.engine.Layout;
import client.engine.Motion;
import client.engine.Reconciler;
import client.engine.ScoreReel;
import client.engine.TileRow;
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
        counter();
        flip();
        drag();
        fader();
        idle();
        tileRow();
        scoreReel();

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

    /** The count-up readout: it chases its target, pops on an increase, and stays quiet on a decrease. */
    private static void counter() {
        Counter c = new Counter(0, 1.0, Easing.LINEAR);
        near("a fresh counter reads its initial value", c.displayed(), 0);
        near("a fresh counter is at rest", c.popScale(), 1);

        c.retarget(100);
        check("an increase pops", c.popScale() > 1);
        c.advance(0.5);
        near("halfway through it reads the midpoint", c.displayed(), 50);
        check("still counting", !c.settled());
        c.advance(2.0);
        near("it lands on the target", c.displayed(), 100);
        check("the pop has decayed", c.popScale() == 1 && c.settled());

        c.retarget(100);
        check("re-feeding the same value is a no-op", c.popScale() == 1);

        c.retarget(20);
        near("a decrease does not pop", c.popScale(), 1);
        c.advance(5);
        near("but it still glides down", c.displayed(), 20);

        c.retarget(40);
        c.snap(0);
        near("snap jumps with no glide", c.displayed(), 0);
        near("snap kills the pop", c.popScale(), 1);
    }

    /** The card flip: face-up at rest, edge-on at the midpoint, showing its back past it. */
    private static void flip() {
        CardEntity e = new CardEntity(1, 5, 2, "x", 0, 0, 0.2, Easing.LINEAR);
        near("a card starts face up", e.flipT(), 0);
        check("and shows its face", !e.showsBack());
        near("its squash is full width at rest", e.flipScaleX(), 1);

        e.setFaceDown(true);
        e.advance(0.17);   // clearly past the flip's midpoint (0.28s total, eased)
        check("past the midpoint it shows the back", e.showsBack());
        check("and is squashed thin", e.flipScaleX() < 0.5);
        e.advance(1);
        near("it lands fully face down", e.flipT(), 1);
        near("at full width again", e.flipScaleX(), 1);

        e.setFaceDown(false);
        e.advance(1);
        check("and flips back up", !e.showsBack() && e.flipT() == 0);
    }

    /** Dragging: the player's hand overrides the layout, and release hands control back. */
    private static void drag() {
        CardEntity e = new CardEntity(2, 0, 0, "x", 100, 100, 0.2, Easing.LINEAR);
        e.beginDrag();
        e.dragTo(300, 250);
        near("a held card snaps to the cursor", e.x(), 300);
        e.moveTo(50, 50);   // the layout tries to place it — and must be ignored
        near("the layout cannot move a held card", e.x(), 300);

        e.endDrag();
        e.moveTo(50, 50);
        e.advance(5);
        near("after release the layout owns it again", e.x(), 50);
    }

    /** The fade-to-black transition: dark, switch exactly once at full black, then back to clear. */
    private static void fader() {
        Fader f = new Fader();
        near("idle is transparent", f.alpha(), 0);

        int[] switched = {0};
        f.start(() -> switched[0]++);
        check("starting activates it", f.active());
        f.advance(0.11);   // halfway into the 0.22s fade-in
        check("it darkens", f.alpha() > 0.3);
        checkInt("the switch has not fired yet", switched[0], 0);
        f.advance(0.2);    // past full black: switch fires, fade-out begins
        checkInt("the switch fired exactly once", switched[0], 1);
        f.advance(0.5);
        check("it ends transparent and idle", f.alpha() == 0 && !f.active());
        f.advance(1);
        checkInt("and never fires again", switched[0], 1);
    }

    /** The idle sway is bounded, seed-de-phased, and deterministic in time. */
    private static void idle() {
        boolean bounded = true;
        for (double t = 0; t < 20; t += 0.37)
            if (Math.abs(Idle.swayDeg(t, 7, 1.5)) > 1.5 || Math.abs(Idle.bobPx(t, 7, 2)) > 2) bounded = false;
        check("the sway never exceeds its amplitude", bounded);
        check("the same instant repeats exactly", Idle.swayDeg(3.2, 5, 1) == Idle.swayDeg(3.2, 5, 1));
        check("different seeds de-phase", Idle.swayDeg(3.2, 5, 1) != Idle.swayDeg(3.2, 6, 1));
    }

    /** The retained tile row: slot layout, identity across reorders, drag parting, and drop resolution. */
    private static void tileRow() {
        TileRow row = new TileRow(50, 80);
        row.reconcile(List.of(10, 20, 30));
        double[] slots = { 100, 160, 220 };
        row.layout(slots, 400);
        near("a fresh tile spawns in its slot", row.x(10), 100);
        near("slots assign in model order", row.x(30), 220);

        // A model reorder keeps the entities: the same tile is told a new slot and glides there.
        row.reconcile(List.of(30, 10, 20));
        row.layout(slots, 400);
        row.advance(5);
        near("a reordered tile glides to its new slot", row.x(30), 100);
        near("the displaced tiles shift over", row.x(10), 160);

        // Dragging: the held tile follows the cursor and the display order parts around it.
        int from = row.beginDrag(160, 400);   // grab the middle slot's tile (id 10 after the reorder)
        checkInt("beginDrag reports the model index", from, 1);
        row.dragTo(230, 380);
        near("the held tile snaps to the cursor", row.x(10), 230);
        check("display order parts toward the cursor", row.displayOrder().get(2) == 10);
        row.layout(slots, 400);
        row.advance(5);
        near("the layout cannot move a held tile", row.x(10), 230);

        // Release inside the band resolves to the nearest slot; the tile itself just glides wherever next.
        int slot = row.endDrag(230, 380);
        checkInt("a drop in the band lands on the nearest slot", slot, 2);

        // Release far outside the band resolves to nowhere — and the next layout glides the tile home.
        row.beginDrag(100, 400);
        row.dragTo(600, 700);
        checkInt("a drop outside the band lands nowhere", row.endDrag(600, 700), -1);
        row.layout(slots, 400);
        row.advance(5);
        near("the refused tile glides back to its slot", row.x(30), 100);

        // A tile the model removed leaves the row; the survivors glide into the tightened slots.
        row.reconcile(List.of(30, 20));
        row.layout(new double[] { 100, 160 }, 400);
        row.advance(5);   // hit-testing tracks animated positions, so let the survivors arrive first
        checkInt("a removed tile leaves the row", row.count(), 2);
        checkInt("nothing is under its old spot", row.tileAt(220, 400), -1);
    }

    /** The scoring reel: every beat plays, in order, accelerating — and it always drains to idle. */
    private static void scoreReel() {
        ScoreReel reel = new ScoreReel();
        check("a fresh reel is idle", !reel.playing() && reel.currentIndex() == -1);

        reel.play(0);
        check("an empty timeline leaves it idle", !reel.playing());

        reel.play(4);
        check("playing starts on the first beat", reel.playing() && reel.currentIndex() == 0);
        near("and at the start of it", reel.beatProgress(), 0);

        reel.advance(0.17);   // half of the 0.34s first step
        check("progress rises within a beat", reel.beatProgress() > 0.4 && reel.beatProgress() < 0.6);
        checkInt("still the first beat", reel.currentIndex(), 0);

        reel.advance(0.17);
        checkInt("it steps to the next beat", reel.currentIndex(), 1);
        checkInt("and counts the one it finished", reel.playedCount(), 1);

        // Every beat is shown — none is ever skipped, however long the chain.
        ScoreReel serial = new ScoreReel();
        serial.play(30);
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (int i = 0; i < 4000 && serial.playing(); i++) { seen.add(serial.currentIndex()); serial.advance(0.01); }
        checkInt("every beat of a long chain was shown", seen.size(), 30);
        check("and the reel drained to idle", !serial.playing());

        // The acceleration: a long chain must cost less than a naive constant-rate one.
        ScoreReel fast = new ScoreReel();
        fast.play(30);
        double t = 0;
        while (fast.playing() && t < 60) { fast.advance(0.01); t += 0.01; }
        check("a 30-beat chain accelerates well under the un-sped time", t < 30 * 0.34);
        check("but still takes real time", t > 2.0);

        // Stopping mid-flight releases the screen at once.
        ScoreReel halted = new ScoreReel();
        halted.play(10);
        halted.advance(0.5);
        halted.stop();
        check("stop makes it idle immediately", !halted.playing() && halted.currentIndex() == -1);
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
