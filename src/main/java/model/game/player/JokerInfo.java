package model.game.player;

import model.items.DeckCard;
import model.items.consumables.ConsumableType;
import model.items.jokers.JokerCard;
import model.modifiers.Enhancement;

/**
 * A <strong>read-only</strong> window on a {@link Run}, for a joker's tooltip descriptor to compute its current
 * effect from — "Erosion: +12 Mult (12 cards removed)", "Stone Joker: +70 Chips (2 Stone cards)". It exposes
 * only queries: there is deliberately no way back to the {@code Run} and no mutator, so a {@code state}
 * descriptor <em>cannot</em> change game state. That matters because a tooltip is drawn on the FX thread from a
 * purely local hover — anything it could mutate would desync the lockstep replay across seats.
 *
 * <p>This replaces the idea of an {@code ON_HOVERED} trigger: a trigger runs a full {@link model.items.jokers.JokerEffect}
 * with unrestricted {@code Run} access, which is exactly the mutation surface we must keep away from a
 * client-only, per-seat event. A descriptor that returns a {@code String} from this view has none.
 *
 * <p>Curated on purpose: it exposes the handful of reads descriptors actually need today, and grows a method at
 * a time as more jokers want to describe themselves. Add readers here, never a setter.
 */
public interface JokerInfo {

    /** Cards currently in the deck. */
    int deckSize();

    /** How many deck cards carry {@code enhancement}. */
    int deckCountWithEnhancement(Enhancement enhancement);

    /** The seat's money right now. */
    int money();

    /** The current hand size. */
    int handSize();

    /** How many consumables of {@code type} this seat has used this run. */
    int consumablesUsed(ConsumableType type);

    /** Discards available right now: the round's remaining during a blind, the base outside one. */
    int discardsRemaining();

    /** How many jokers are on the board. */
    int jokerCount();

    /** The summed sell value of every joker on the board except {@code self}. */
    int otherJokersSellValue(JokerCard self);

    /** Cards sold this run (Campfire measures its multiplier against this). */
    int cardsSold();

    /** A live view onto {@code run}; reads lazily, so it computes nothing until a descriptor asks. */
    static JokerInfo of(Run run) {
        return new JokerInfo() {
            @Override public int deckSize() { return run.getDeck().size(); }
            @Override public int deckCountWithEnhancement(Enhancement e) {
                int n = 0;
                for (DeckCard c : run.getDeck()) if (c.getEnhancement() == e) n++;
                return n;
            }
            @Override public int money()     { return run.getMoney(); }
            @Override public int handSize()   { return run.getHandSize(); }
            @Override public int consumablesUsed(ConsumableType t) { return run.getStats().getConsumablesUsed(t); }
            @Override public int discardsRemaining() {
                return run.getRound() != null ? run.getRound().getDiscardsRemaining() : run.getBaseDiscards();
            }
            @Override public int jokerCount() { return run.getJokers().size(); }
            @Override public int otherJokersSellValue(JokerCard self) {
                int sum = 0;
                for (JokerCard j : run.getJokers()) if (j != self) sum += j.getSellValue();
                return sum;
            }
            @Override public int cardsSold() { return run.getStats().getCardsSold(); }
        };
    }

    /** A view backed by nothing, returning zeros — for a descriptor evaluated without a run (tests, previews). */
    JokerInfo EMPTY = new JokerInfo() {
        @Override public int deckSize() { return 0; }
        @Override public int deckCountWithEnhancement(Enhancement e) { return 0; }
        @Override public int money()    { return 0; }
        @Override public int handSize() { return 0; }
        @Override public int consumablesUsed(ConsumableType t) { return 0; }
        @Override public int discardsRemaining() { return 0; }
        @Override public int jokerCount() { return 0; }
        @Override public int otherJokersSellValue(JokerCard self) { return 0; }
        @Override public int cardsSold() { return 0; }
    };
}
