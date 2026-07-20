package model.game.scoring;

import model.items.DeckCard;

import java.util.List;

/** Result of evaluating a played hand: the cards that score (in play order) and the {@link HandContext} traits read by joker effects. */
public record HandEvaluation(List<DeckCard> scoringCards, HandContext context) {
    public HandEvaluation {
        scoringCards = List.copyOf(scoringCards);
    }

    /** The classified hand type; shortcut for {@code context().type()}. */
    public HandType type() { return context.type(); }
}
