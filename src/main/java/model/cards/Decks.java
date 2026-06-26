package model.cards;

import model.cards.DeckCard.Rank;
import model.cards.DeckCard.Suit;

import java.util.ArrayList;
import java.util.List;

/** Factory for starting decks. Standard 52 for now; deck-type variants (Red, Blue, ...) will come later. */
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
}