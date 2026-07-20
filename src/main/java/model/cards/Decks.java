package model.cards;

import model.cards.DeckCard.Rank;
import model.cards.DeckCard.Suit;
import model.game.rng.Rng;
import model.game.rng.RngSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Factory for starting decks, one per {@link DeckType}. Only the composition variants are built here — the
 * behavioural types (Bazaar, Ghost, Anaglyph, Plasma, Eclipse) deal a standard 52 and take effect elsewhere.
 *
 * <p>{@link DeckType#ERRATIC} is the one type that needs randomness. It draws from {@link RngSource#DECK_BUILD}
 * salted by card index, so the deck is a pure function of the seed: every seat and every replay builds the same
 * erratic deck, which is what determinism requires.
 */
public final class Decks {

    private Decks() { }

    /** A fresh standard 52-card deck of distinct cards, no enhancements/editions/seals. */
    public static List<DeckCard> standard() {
        List<DeckCard> cards = new ArrayList<>(52);
        for (Suit suit : Suit.values())
            for (Rank rank : Rank.values())
                cards.add(new DeckCard(rank, suit));
        return cards;
    }

    /** The starting deck for {@code type}. {@code rng} may be null for every type except Erratic. */
    public static List<DeckCard> of(DeckType type, Rng rng) {
        if (type == null) return standard();
        return switch (type) {
            case ABANDONED -> withoutFaces();
            case CROWDED   -> withExtraFaces();
            case CHECKERED -> checkered();
            case ERRATIC   -> erratic(rng);
            default        -> standard();   // STANDARD + the behavioural decks all open with a plain 52
        };
    }

    /** Abandoned: the 40 non-face cards. */
    private static List<DeckCard> withoutFaces() {
        List<DeckCard> cards = new ArrayList<>(40);
        for (DeckCard c : standard()) if (!c.isFace()) cards.add(c);
        return cards;
    }

    /** Crowded: a standard 52 plus a second copy of every face card (64 cards). */
    private static List<DeckCard> withExtraFaces() {
        List<DeckCard> cards = standard();
        for (Suit suit : Suit.values())
            for (Rank rank : List.of(Rank.JACK, Rank.QUEEN, Rank.KING))
                cards.add(new DeckCard(rank, suit));
        return cards;
    }

    /** Checkered: the standard 52 with Clubs folded into Spades and Diamonds into Hearts (26 of each). */
    private static List<DeckCard> checkered() {
        List<DeckCard> cards = new ArrayList<>(52);
        for (DeckCard c : standard()) {
            Suit s = switch (c.getSuit()) {
                case CLUBS    -> Suit.SPADES;
                case DIAMONDS -> Suit.HEARTS;
                case SPADES, HEARTS -> c.getSuit();
            };
            cards.add(new DeckCard(c.getRank(), s));
        }
        return cards;
    }

    /** Erratic: 52 cards whose rank and suit are each drawn independently, keyed by card index. */
    private static List<DeckCard> erratic(Rng rng) {
        if (rng == null) throw new IllegalArgumentException("the Erratic deck needs an Rng");
        Rank[] ranks = Rank.values();
        Suit[] suits = Suit.values();
        List<DeckCard> cards = new ArrayList<>(52);
        for (int i = 0; i < 52; i++) {
            Rank rank = ranks[rng.nextInt(RngSource.DECK_BUILD, Rng.combine(i, 0), ranks.length)];
            Suit suit = suits[rng.nextInt(RngSource.DECK_BUILD, Rng.combine(i, 1), suits.length)];
            cards.add(new DeckCard(rank, suit));
        }
        return cards;
    }
}
