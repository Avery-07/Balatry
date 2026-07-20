package model.game.player;

import model.cards.jokers.JokerCard;
import model.game.scoring.Trigger;
import model.modifiers.Edition;
import model.modifiers.Sticker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.random.RandomGenerator;

/** A seat's joker board — the single chokepoint for every joker inventory mutation, so the invariants live in exactly one place and cannot be bypassed: the slot limit (NEGATIVE jokers are free), Eternal ("can not be sold or destroyed": {@link #sell} rejects loudly since selling is a deliberate player action, {@link #destroy} skips silently since effects hitting an Eternal joker simply fail), and the sale side effects (ON_SOLD fires while the joker is still on the board, sell value banks, Verdant Leaf's debuff lifts). */
public final class Board {

    private final Run run;
    private final List<JokerCard> jokers = new ArrayList<>();
    private int slots = 5;

    Board(Run run) { this.run = run; }

    // --- reads ---

    /** Unmodifiable live view of the board, in order. Snapshot before firing effects while iterating. */
    public List<JokerCard> view() { return Collections.unmodifiableList(jokers); }

    public int size()                { return jokers.size(); }
    public boolean isEmpty()         { return jokers.isEmpty(); }
    public JokerCard get(int index)  { return jokers.get(index); }

    // --- slot accounting (NEGATIVE jokers don't consume a slot) ---

    public int getSlots()      { return slots; }
    public void setSlots(int n) { slots = n; }

    /** Slot-consuming jokers currently on the board. */
    public int usedSlots() {
        int used = 0;
        for (JokerCard j : jokers) if (consumesSlot(j)) used++;
        return used;
    }

    /** Whether {@code joker} fits right now. */
    public boolean hasRoomFor(JokerCard joker) {
        return !consumesSlot(joker) || usedSlots() < slots;
    }

    /** Whether replacing the joker at {@code index} with {@code incoming} keeps the board within its slots. */
    public boolean canReplaceAt(int index, JokerCard incoming) {
        JokerCard outgoing = jokers.get(index);
        return usedSlots() - (consumesSlot(outgoing) ? 1 : 0) + (consumesSlot(incoming) ? 1 : 0) <= slots;
    }

    private static boolean consumesSlot(JokerCard joker) { return joker.getEdition() != Edition.NEGATIVE; }

    // --- mutation ---

    /** Adds {@code joker} if there is room, reporting whether it landed (effect-created jokers fizzle quietly). */
    public boolean add(JokerCard joker) {
        if (!hasRoomFor(joker)) return false;
        jokers.add(joker);
        return true;
    }

    /** Sells the joker at {@code index}: rejects Eternal, fires ON_SOLD while the joker is still on the board (Luchador reacts to its own sale), banks the sell value, and lifts Verdant Leaf's debuff for the round. */
    public int sell(int index) {
        JokerCard joker = jokers.get(index);
        if (joker.hasSticker(Sticker.ETERNAL))
            throw new IllegalStateException("an Eternal joker cannot be sold: " + joker.getSpec().getName());

        // Sticky charges a toll to part with, and that toll grows every round it stays. Take it before the sale
        // value is banked, and refuse outright if the seat cannot cover it — a half-paid sale has no meaning.
        int toll = joker.getStickySellCost();
        if (toll > 0) {
            if (run.getMoney() - toll < run.minBalance())
                throw new IllegalStateException("cannot afford the $" + toll + " Sticky fee on "
                        + joker.getSpec().getName());
            run.spend(toll);
        }
        joker.trigger(Trigger.ON_SOLD, run);
        jokers.remove(index);
        int value = joker.getSellValue();
        run.addMoney(value);
        run.getStats().recordCardSold();
        run.getBossState().noteJokerSold();
        return value;
    }

    /** Destroys {@code joker} (by identity): Eternal jokers survive; reports whether anything was removed. */
    public boolean destroy(JokerCard joker) {
        if (joker == null || joker.hasSticker(Sticker.ETERNAL)) return false;
        return jokers.removeIf(j -> j == joker);
    }

    /** Reorders the board: the joker at {@code from} is reinserted at {@code to} (adjacency-sensitive effects follow it). */
    public void move(int from, int to) {
        if (from < 0 || from >= jokers.size() || to < 0 || to >= jokers.size())
            throw new IllegalArgumentException("move " + from + " -> " + to + " out of range (board size " + jokers.size() + ")");
        jokers.add(to, jokers.remove(from));
    }

    /** The swap primitive: replaces the joker at {@code index} with {@code incoming}, slot-checked. */
    public JokerCard replaceAt(int index, JokerCard incoming) {
        if (!canReplaceAt(index, incoming))
            throw new IllegalStateException("no joker slot for the incoming joker at index " + index);
        return jokers.set(index, incoming);
    }

    /** Amber Acorn: randomizes the board order (Fisher-Yates on the given stream). */
    public void shuffle(RandomGenerator r) {
        for (int i = jokers.size() - 1; i > 0; i--) {
            int j = r.nextInt(i + 1);
            JokerCard tmp = jokers.get(i);
            jokers.set(i, jokers.get(j));
            jokers.set(j, tmp);
        }
    }
}
