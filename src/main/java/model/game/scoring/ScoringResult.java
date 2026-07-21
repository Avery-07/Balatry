package model.game.scoring;

import model.items.DeckCard;

import java.math.BigDecimal;
import java.util.List;

/**
 * Outcome of scoring one hand: the final {@code score}, the {@code chips} and {@code mult} that produced it (for
 * the UI's chips×mult readout), the cards destroyed (caller removes them from the deck), and {@code events} —
 * the ordered timeline of how the score was reached, which the client replays as the scoring animation.
 */
public record ScoringResult(BigDecimal score, BigDecimal chips, BigDecimal mult,
                            List<DeckCard> destroyed, List<ScoringEvent> events) {

    /** A result with no recorded timeline — for callers that only need the numbers. */
    public ScoringResult(BigDecimal score, BigDecimal chips, BigDecimal mult, List<DeckCard> destroyed) {
        this(score, chips, mult, destroyed, List.of());
    }
}
