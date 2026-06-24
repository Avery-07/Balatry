package model.game;

import model.cards.DeckCard;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.Deque;

public final class ScoringSession {
    private final Run run;
    private double chips;
    private BigDecimal mult;
    private DeckCard currentCard;
    private final Deque<DeckCard> retriggers = new ArrayDeque<>();

    ScoringSession(Run run, int baseChips, int baseMult) {
        this.run = run; this.chips = baseChips; this.mult = baseMult;
    }

    public void addChips(long c)      { chips += c; }
    public void addMult(double m)     { mult += BigDecimal.valueOf(m); }
    public void multiplyMult(double f)   { mult *= BigDecimal.valueOf(f); }
    public DeckCard currentCard()     { return currentCard; }
    public void retrigger(DeckCard c) { retriggers.add(c); }
    public Run run()                  { return run; }
    public long finalScore()          { return Math.round(chips * mult); }

    void setCurrentCard(DeckCard c)   { this.currentCard = c; }     // package-private: engine only
}