package model.items;

import model.modifiers.Edition;
import model.modifiers.Enhancement;
import model.modifiers.Seal;

import java.util.random.RandomGenerator;

/**
 * Rolls playing cards for booster packs and — with the Magic Trick voucher — the shop. Each card independently rolls
 * an enhancement, a seal and an edition, so it can carry any subset or all three at once. The Illusion voucher
 * selects the BOOSTED chances, raising every modifier's odds (for shop and pack cards alike). Every roll runs on the
 * caller's seeded stream, so generation stays deterministic and seat-mirrored.
 *
 * <p>All rates are out of 100 and gathered here to tune in one place.
 */
public final class PlayingCards {
    private PlayingCards() { }

    private static final int ENH_BASE = 40, ENH_BOOST = 60;         // chance the card is enhanced
    private static final int SEAL_BASE = 10, SEAL_BOOST = 20;       // chance it carries a seal
    private static final int EDITION_BASE = 15, EDITION_BOOST = 30; // chance it has a shiny edition
    private static final int FOIL_UP_TO = 55, HOLO_UP_TO = 90;      // edition split: Foil / Holographic / (rest) Polychrome

    /** A random rank+suit playing card with rolled modifiers; {@code illusion} selects the boosted rates. */
    public static DeckCard rolled(RandomGenerator stream, boolean illusion) {
        DeckCard card = new DeckCard(
                DeckCard.Rank.values()[stream.nextInt(DeckCard.Rank.values().length)],
                DeckCard.Suit.values()[stream.nextInt(DeckCard.Suit.values().length)]);
        applyModifiers(card, stream, illusion);
        return card;
    }

    /** Rolls an enhancement, seal and edition onto {@code card} independently — any subset, possibly all three. */
    public static void applyModifiers(DeckCard card, RandomGenerator stream, boolean illusion) {
        if (stream.nextInt(100) < (illusion ? ENH_BOOST : ENH_BASE))
            card.apply(Enhancement.values()[stream.nextInt(Enhancement.values().length)]);
        if (stream.nextInt(100) < (illusion ? SEAL_BOOST : SEAL_BASE))
            card.apply(Seal.values()[stream.nextInt(Seal.values().length)]);
        if (stream.nextInt(100) < (illusion ? EDITION_BOOST : EDITION_BASE))
            card.apply(edition(stream));
    }

    private static Edition edition(RandomGenerator stream) {
        int r = stream.nextInt(100);
        if (r < FOIL_UP_TO) return Edition.FOIL;
        if (r < HOLO_UP_TO) return Edition.HOLOGRAPHIC;
        return Edition.POLYCHROME;   // NEGATIVE never lands on a playing card
    }
}
