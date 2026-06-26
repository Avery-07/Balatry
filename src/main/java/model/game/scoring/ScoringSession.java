package model.game.scoring;

import model.cards.DeckCard;
import model.game.player.Run;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Accumulates chips and mult for a single played hand using exact arithmetic ({@code long} adds, {@code BigDecimal} multipliers). */
public final class ScoringSession {
    private final Run run;
    private BigDecimal chips;
    private BigDecimal mult;
    private DeckCard currentCard;   // the card being scored right now, or null
    private HandContext hand;       // the hand being scored (its traits); set once per session by the engine

    /** Intended for {@link Run#beginScoring} only. */
    public ScoringSession(Run run, long baseChips, long baseMult) {
        this.run = run;
        this.chips = BigDecimal.valueOf(baseChips);
        this.mult = BigDecimal.valueOf(baseMult);
    }

    public void addChips(long c)           { chips = chips.add(BigDecimal.valueOf(c)); }
    public void addMult(long m)            { mult = mult.add(BigDecimal.valueOf(m)); }
    public void addMult(BigDecimal m)      { mult = mult.add(m); }
    public void multiplyMult(BigDecimal f) { mult = mult.multiply(f); }

    public BigDecimal getChips() { return chips; }
    public BigDecimal getMult()  { return mult; }
    public Run getRun()          { return run; }

    /** The card currently being scored, or {@code null} outside a per-card step. */
    public DeckCard getCurrentScoredCard() { return currentCard; }

    void setCurrentCard(DeckCard card) { this.currentCard = card; }   // engine only

    /** Traits of the hand being scored, read by joker effects; {@code null} before the engine sets it. */
    public HandContext getHand() { return hand; }

    void setHand(HandContext hand) { this.hand = hand; }   // engine only

    /** Final score for this hand (chips x mult), rounded to an integer. */
    public BigDecimal finalScore() {
        return chips.multiply(mult).setScale(0, RoundingMode.HALF_UP);
    }
}