package model.game.player;

import model.cards.DeckType;
import model.cards.jokers.Jokers;
import model.cards.vouchers.Vouchers;

/**
 * The starting grants a {@link DeckType} hands each seat beyond the cards themselves. Deck <em>composition</em>
 * lives in {@code Decks}; this is the part that needs a {@link Run} to exist first, so it runs during match
 * assembly right after the deck is dealt and the opening money is credited.
 *
 * <p>Only the Eclipse deck grants anything today. The other behavioural decks (Bazaar, Ghost, Anaglyph, Plasma)
 * change shop or scoring behaviour rather than opening inventory, and are still inert — see {@link DeckType}.
 */
public final class DeckSetup {

    private DeckSetup() { }

    /** Applies {@code type}'s opening grants to {@code run}. A null or non-granting type is a no-op. */
    public static void applyStartingGrants(Run run, DeckType type) {
        if (type == DeckType.ECLIPSE) {
            run.grantVoucher(Vouchers.SHOWMAN.make());
            run.acquire(Jokers.INVISIBLE_JOKER.make());
        }
    }
}
