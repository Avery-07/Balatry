package model.game.player;

import model.cards.DeckCard;
import model.cards.vouchers.Vouchers;
import model.game.rng.Rng;
import model.game.rng.RngSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Applies a {@link Sleeve}'s starting adjustments to a freshly-built {@link Run}. Called once during match
 * assembly, after the deck is dealt and the opening money is credited, so a sleeve can see and edit both.
 *
 * <p>Anything random here (Fracture's cuts) draws from {@link RngSource#DECK_BUILD} keyed by the seat, so two
 * seats on Fracture lose different cards while every replay of a given seed loses the same ones.
 */
public final class Sleeves {

    /** Cards the Fracture sleeve removes from the starting deck. */
    private static final int FRACTURE_CUTS = 10;

    private Sleeves() { }

    /** Applies {@code sleeve} to {@code run}. {@code seat} keys the sleeve's randomness; null sleeve is a no-op. */
    public static void apply(Run run, Sleeve sleeve, int seat) {
        if (sleeve == null) return;
        switch (sleeve) {
            case STANDARD -> { }
            case RED_BLUE -> {
                run.setBaseHands(run.getBaseHands() + 1);
                run.setBaseDiscards(run.getBaseDiscards() + 1);
            }
            case LEGACY -> run.addMoney(10);
            case BLACK -> {
                run.setJokerSlots(run.getJokerSlots() + 1);
                run.setBaseHands(run.getBaseHands() - 1);
            }
            case COLORFUL -> {
                run.setHandSize(run.getHandSize() + 2);
                run.setBaseHands(run.getBaseHands() - 1);
            }
            case SILK -> {
                run.grantVoucher(Vouchers.PLANET_MERCHANT.make());
                run.grantVoucher(Vouchers.TAROT_MERCHANT.make());
                run.grantVoucher(Vouchers.RELIC_MERCHANT.make());
                run.grantVoucher(Vouchers.OVERSTOCK.make());
            }
            case FRACTURE -> fracture(run, seat);
            case FRUGAL, CELESTIAL -> { }   // described, not yet wired — see Sleeve
        }
    }

    /** Removes {@link #FRACTURE_CUTS} cards from the deck, drawing each index from the remaining cards. */
    private static void fracture(Run run, int seat) {
        List<DeckCard> deck = new ArrayList<>(run.getDeck());
        for (int i = 0; i < FRACTURE_CUTS && !deck.isEmpty(); i++) {
            int pick = run.getRng().nextInt(RngSource.DECK_BUILD, Rng.combine(seat, 100 + i), deck.size());
            deck.remove(pick);
        }
        run.resetDeck(deck);
    }
}
