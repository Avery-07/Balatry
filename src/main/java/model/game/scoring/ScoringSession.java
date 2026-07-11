package model.game.scoring;

import model.cards.DeckCard;
import model.cards.jokers.JokerCard;
import model.game.player.Run;
import model.modifiers.Edition;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Accumulates chips and mult for a single played hand using exact arithmetic ({@code long} adds, {@code BigDecimal} multipliers). */
public final class ScoringSession {
    private final Run run;
    private BigDecimal chips;
    private BigDecimal mult;
    private static final BigDecimal X1_5 = new BigDecimal("1.5");
    private static final int MAX_RETRIGGER_DEPTH = 64;

    private DeckCard currentCard;   // the card being scored right now, or null
    private HandContext hand;       // the hand being scored (its traits); set once per session by the engine
    private int retriggerDepth;     // guards against retrigger cycles (e.g. two jokers retriggering each other)

    /** Intended for {@link Run#beginScoring} only. */
    public ScoringSession(Run run, long baseChips, long baseMult) {
        this.run = run;
        this.chips = BigDecimal.valueOf(baseChips);
        this.mult = BigDecimal.valueOf(baseMult);
    }

    public void addChips(long c)           { chips = chips.add(BigDecimal.valueOf(c)); }
    public void addMult(long m)            { mult = mult.add(BigDecimal.valueOf(m)); }
    public void addMult(BigDecimal m)      { mult = mult.add(m); }
    public void multiplyMult(long f)       { mult = mult.multiply(BigDecimal.valueOf(f)); }
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

    /** Applies an edition's scoring contribution (NEGATIVE contributes nothing here). */
    public void applyEdition(Edition edition) {
        if (edition == null) return;
        switch (edition) {
            case FOIL        -> addChips(50);
            case HOLOGRAPHIC -> addMult(10);
            case POLYCHROME  -> multiplyMult(X1_5);
            case NEGATIVE    -> { }
        }
    }

    /** Immediately re-fires a joker's played-hand effect and edition. */
    public void retriggerJoker(JokerCard joker) {
        if (joker == null || joker.isDebuffed() || retriggerDepth >= MAX_RETRIGGER_DEPTH) return;
        retriggerDepth++;
        try {
            joker.trigger(Trigger.ON_HAND_PLAYED, run);
            applyEdition(joker.getEdition());
        } finally {
            retriggerDepth--;
        }
    }

    /** Final score for this hand (chips x mult), rounded to an integer. */
    public BigDecimal finalScore() {
        return chips.multiply(mult).setScale(0, RoundingMode.HALF_UP);
    }
}