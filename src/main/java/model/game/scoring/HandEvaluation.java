package model.game.scoring;

import model.cards.DeckCard;

import java.util.List;

/** Result of evaluating a played hand: its {@link HandType} and the cards that score, in play order. */
public record HandEvaluation(HandType type, List<DeckCard> scoringCards) {
    public HandEvaluation {
        scoringCards = List.copyOf(scoringCards);
    }
}