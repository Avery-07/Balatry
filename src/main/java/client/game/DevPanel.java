package client.game;

import client.engine.Layout;
import debug.Log;
import javafx.scene.paint.Color;
import model.game.player.Run;
import model.items.DeckCard;
import model.items.consumables.ConsumableSpec;
import model.items.consumables.Planets;
import model.items.consumables.Spectrals;
import model.items.consumables.Tarots;
import model.items.jokers.JokerCard;
import model.items.jokers.Jokers;
import model.modifiers.Edition;
import model.modifiers.Enhancement;
import model.modifiers.Seal;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static client.game.Palette.*;

/**
 * A purely functional dev/cheat overlay for testing, toggled with the {@code *} key (gated by {@code BALATRY_DEV} /
 * {@code -Dbalatry.dev}). It mutates the LOCAL model directly — money, slots, and summoning jokers/consumables/cards
 * with modifiers — so it is <strong>single-player / host testing only</strong>: nothing routes through the action
 * log, so a cheat neither syncs to other seats nor survives a determinism replay. Every reversible cheat pushes an
 * undo; Revert pops the last one. It registers its controls in {@link Ui#devButtons}, which the input dispatch
 * handles before anything else, so the panel works in every phase.
 */
final class DevPanel {

    private boolean visible;
    private final Deque<Runnable> undo = new ArrayDeque<>();

    private int jokerIdx, consumableIdx, enhIdx, sealIdx, edIdx;   // cyclable selections for the summon rows
    private final List<ConsumableSpec> consumables = buildConsumables();

    void toggle() { visible = !visible; }

    void render(Ui ui, Run run) {
        if (!visible) return;
        Renderer r = ui.r;
        double x = 12, y = 70, w = 290, pad = 12, bw = w - 2 * pad;
        r.panel(x, y, w, 440, Color.web("#0c0d10f2"), ORANGE, 10, 2);
        r.textLeftBold("DEV — cheats  ( * to close )", x + pad, y + 12, 13, ORANGE);
        if (run == null) { r.textLeft("no active match", x + pad, y + 40, 12, DIM); return; }

        double cy = y + 36, bx = x + pad;

        r.textLeftBold("Money  $" + run.getMoney(), bx, cy, 12, DIM); cy += 18;
        thirds(ui, bx, cy, bw, "-$10", () -> money(run, -10), "+$10", () -> money(run, 10), "+$50", () -> money(run, 50));
        cy += 34;

        r.textLeftBold("Slots", bx, cy, 12, DIM); cy += 18;
        thirds(ui, bx, cy, bw, "+Joker", () -> slot(run, 0), "+Consum.", () -> slot(run, 1), "+Hand", () -> slot(run, 2));
        cy += 34;

        r.textLeftBold("Joker", bx, cy, 12, DIM); cy += 18;
        cycler(ui, bx, cy, bw, Jokers.values()[jokerIdx].spec().getName(),
                () -> jokerIdx = wrap(jokerIdx - 1, Jokers.values().length),
                () -> jokerIdx = wrap(jokerIdx + 1, Jokers.values().length));
        cy += 30;
        button(ui, bx, cy, bw, 24, "Spawn joker", () -> spawnJoker(run)); cy += 32;

        r.textLeftBold("Consumable", bx, cy, 12, DIM); cy += 18;
        cycler(ui, bx, cy, bw, consumables.get(consumableIdx).getName(),
                () -> consumableIdx = wrap(consumableIdx - 1, consumables.size()),
                () -> consumableIdx = wrap(consumableIdx + 1, consumables.size()));
        cy += 30;
        button(ui, bx, cy, bw, 24, "Spawn consumable", () -> spawnConsumable(run)); cy += 32;

        r.textLeftBold("Card:  " + name(Enhancement.class, enhIdx) + " / " + name(Seal.class, sealIdx) + " / " + name(Edition.class, edIdx),
                bx, cy, 11, DIM); cy += 18;
        thirds(ui, bx, cy, bw,
                "Enh »", () -> enhIdx = wrap(enhIdx + 1, Enhancement.values().length + 1),
                "Seal »", () -> sealIdx = wrap(sealIdx + 1, Seal.values().length + 1),
                "Ed »", () -> edIdx = wrap(edIdx + 1, Edition.values().length + 1));
        cy += 30;
        button(ui, bx, cy, bw, 24, "Add card (Ace of Spades)", () -> spawnCard(run)); cy += 36;

        button(ui, bx, cy, bw, 28, undo.isEmpty() ? "Revert — nothing" : "Revert  (" + undo.size() + ")", this::revert);
    }

    // --- cheats (each mutates the local model; errors are logged, never thrown into the frame) ---

    private void money(Run run, int delta) {
        guard(() -> { run.addMoney(delta); push(() -> run.addMoney(-delta)); Log.dev("money " + (delta >= 0 ? "+" : "") + delta); });
    }

    private void slot(Run run, int which) {
        guard(() -> {
            switch (which) {
                case 0 -> { run.setJokerSlots(run.getJokerSlots() + 1); push(() -> run.setJokerSlots(run.getJokerSlots() - 1)); }
                case 1 -> { run.setConsumableSlots(run.getConsumableSlots() + 1); push(() -> run.setConsumableSlots(run.getConsumableSlots() - 1)); }
                default -> { run.setHandSize(run.getHandSize() + 1); push(() -> run.setHandSize(run.getHandSize() - 1)); }
            }
            Log.dev("slot +1 (" + which + ")");
        });
    }

    private void spawnJoker(Run run) {
        guard(() -> {
            JokerCard jc = Jokers.values()[jokerIdx].make();
            run.createJoker(jc);
            push(() -> run.destroyJoker(jc));
            Log.dev("spawn joker " + jc.getSpec().getName());
        });
    }

    private void spawnConsumable(Run run) {   // no clean removal API, so it is not undoable
        guard(() -> { run.createConsumable(consumables.get(consumableIdx)); Log.dev("spawn consumable " + consumables.get(consumableIdx).getName()); });
    }

    private void spawnCard(Run run) {
        guard(() -> {
            DeckCard d = new DeckCard(DeckCard.Rank.ACE, DeckCard.Suit.SPADES);
            if (enhIdx > 0) d.apply(Enhancement.values()[enhIdx - 1]);
            if (sealIdx > 0) d.apply(Seal.values()[sealIdx - 1]);
            if (edIdx > 0) d.apply(Edition.values()[edIdx - 1]);
            if (run.getRound() != null) run.addCardToHand(d); else run.addCardToDeck(d);
            push(() -> run.destroyDeckCards(List.of(d)));
            Log.dev("spawn card A-SPADES [" + name(Enhancement.class, enhIdx) + "/" + name(Seal.class, sealIdx) + "/" + name(Edition.class, edIdx) + "]");
        });
    }

    private void revert() {
        if (undo.isEmpty()) return;
        guard(() -> { undo.pop().run(); Log.dev("revert"); });
    }

    private void push(Runnable r) { undo.push(r); }

    private static void guard(Runnable r) {
        try { r.run(); } catch (RuntimeException e) { Log.error("dev cheat failed: " + e); }
    }

    // --- widgets ---

    private void button(Ui ui, double x, double y, double w, double h, String label, Runnable action) {
        ui.r.panel(x, y, w, h, Color.web("#26272b"), EDGE, 6, 1);
        ui.r.textCenterBold(label, x + w / 2, y + h / 2, 12, INK);
        ui.devButtons.add(new Ui.Btn(new Layout.Rect(x, y, w, h), action));
    }

    private void thirds(Ui ui, double x, double y, double w, String a, Runnable ra, String b, Runnable rb, String c, Runnable rc) {
        double g = 6, bw = (w - 2 * g) / 3;
        button(ui, x, y, bw, 24, a, ra);
        button(ui, x + bw + g, y, bw, 24, b, rb);
        button(ui, x + 2 * (bw + g), y, bw, 24, c, rc);
    }

    private void cycler(Ui ui, double x, double y, double w, String value, Runnable prev, Runnable next) {
        double aw = 28;
        button(ui, x, y, aw, 26, "◀", prev);
        ui.r.panel(x + aw + 4, y, w - 2 * aw - 8, 26, Color.web("#1a1b1f"), EDGE, 6, 1);
        ui.r.textCenter(value, x + w / 2, y + 13, 11, INK);
        button(ui, x + w - aw, y, aw, 26, "▶", next);
    }

    // --- helpers ---

    private static int wrap(int i, int n) { return ((i % n) + n) % n; }

    /** Cyclable modifier name: index 0 is "None", 1..N are the enum values (kept generic for enh/seal/edition). */
    private static String name(Class<? extends Enum<?>> type, int idx) {
        return idx == 0 ? "None" : Fmt.title(type.getEnumConstants()[idx - 1].name());
    }

    private static List<ConsumableSpec> buildConsumables() {
        List<ConsumableSpec> out = new ArrayList<>();
        for (Tarots t : Tarots.values()) out.add(t.spec());
        for (Planets p : Planets.values()) out.add(p.spec());
        for (Spectrals s : Spectrals.values()) out.add(s.spec());
        return out;
    }
}
