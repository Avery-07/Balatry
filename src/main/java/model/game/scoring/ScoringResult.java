package model.game.scoring;

import model.cards.DeckCard;

import java.math.BigDecimal;
import java.util.List;

/** Outcome of scoring one hand: the {@code score} and the cards destroyed (caller removes them from the deck). */
public record ScoringResult(BigDecimal score, List<DeckCard> destroyed) {}