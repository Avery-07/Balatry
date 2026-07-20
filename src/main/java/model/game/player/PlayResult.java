package model.game.player;

import model.items.DeckCard;
import model.game.scoring.HandType;

import java.math.BigDecimal;
import java.util.List;

/** Outcome of one play: the hand type, this hand's score, the new running total, and any cards destroyed. */
public record PlayResult(HandType type, BigDecimal handScore, BigDecimal totalScore, List<DeckCard> destroyed) {
    public PlayResult {
        destroyed = List.copyOf(destroyed);
    }
}