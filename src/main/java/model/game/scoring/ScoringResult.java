package model.game.scoring;

import model.cards.DeckCard;

import java.math.BigDecimal;
import java.util.List;

/**
 * Outcome of scoring one hand: the final {@code score}, the {@code chips} and {@code mult} that produced it (for
 * the UI's chips×mult readout), and the cards destroyed (caller removes them from the deck).
 */
public record ScoringResult(BigDecimal score, BigDecimal chips, BigDecimal mult, List<DeckCard> destroyed) {}