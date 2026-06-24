package model.game.player;

import model.cards.consumables.ConsumableCard;
import model.cards.DeckCard;
import model.cards.jokers.JokerCard;
import model.cards.consumables.ConsumableSpec;
import model.game.*;
import model.game.rng.DeterministicRng;
import model.game.rng.LuckEvent;
import model.game.rng.Rng;
import model.game.scoring.HandType;
import model.game.scoring.ScoringSession;

import java.util.ArrayList;
import java.util.List;

/** One player's private state: money, jokers, consumables, deck, the active scoring session, and keyed randomness. */
public final class Run {
    private int money;
    private final Rng rng;
    private final PlayerStats stats = new PlayerStats();
    private Match match;          // null for standalone / headless runs
    private PlayerId playerId;    // null for standalone / headless runs
    private final List<JokerCard> jokers = new ArrayList<>();
    private final List<ConsumableCard> consumables = new ArrayList<>();
    private final List<DeckCard> held = new ArrayList<>();
    private final List<DeckCard> selected = new ArrayList<>();
    private ScoringSession scoring;   // non-null only during a hand

    /** Builds a run from the match seed; every player's run uses the same seed. */
    public Run(long seed) { this(new DeterministicRng(seed)); }

    /** Injection point for tests, which can supply a stub Rng with scripted outcomes. */
    public Run(Rng rng) { this.rng = rng; }

    /** Links this run to its owning match. Called once by {@link Match} during assembly. */
    public void joinMatch(Match match, PlayerId playerId) {
        this.match = match;
        this.playerId = playerId;
    }

    /** Replaces the joker at {@code index}. Lets cross-seat swaps mutate without relying on getJokers() leaking the backing list. */
    public void replaceJoker(int index, JokerCard joker) { jokers.set(index, joker); }

    /** Resolves one luck roll: draws {@code numerator/denominator} on {@code event}'s stream using its next occurrence salt. */
    public boolean roll(LuckEvent event, int numerator, int denominator) {
        return rng.chance(event.getSource(), stats.nextSalt(event), numerator, denominator);
    }

    public Match getMatch() { return match; }
    public PlayerId getPlayerId() { return playerId; }
    public Rng getRng() { return rng; }
    public PlayerStats getStats() { return stats; }
    public ScoringSession getScoring()        { return scoring; }
    public int getMoney()                     { return money; }
    public void addMoney(int amount)          { money += amount; }
    public List<JokerCard> getJokers()        { return jokers; }
    public List<ConsumableCard> getConsumables() { return consumables; }
    public List<DeckCard> getHeld()           { return held; }
    public List<DeckCard> getSelected()       { return selected; }
    public void levelUpHand(HandType h)       { /* ... */ }
    public void createConsumable(ConsumableSpec s) { /* ... */ }

    public ScoringSession beginScoring(long baseChips, long baseMult) {     // package-private: engine only
        return scoring = new ScoringSession(this, baseChips, baseMult);
    }
    public void endScoring() { scoring = null; }
}