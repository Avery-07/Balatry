package model.game.player;

import model.cards.consumables.ConsumableCard;
import model.cards.DeckCard;
import model.cards.jokers.JokerCard;
import model.cards.consumables.ConsumableSpec;
import model.game.*;
import model.game.rng.DeterministicRng;
import model.game.rng.LuckEvent;
import model.game.rng.Rng;
import model.game.rng.RngSource;
import model.game.scoring.HandType;
import model.game.scoring.ScoringSession;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/** One player's private state: money, jokers, consumables, deck, the active scoring session, and keyed randomness. */
public final class Run {
    private static final RoundSettlement SETTLEMENT = new RoundSettlement();

    private int money;
    private final Rng rng;
    private final PlayerStats stats = new PlayerStats();
    private final HandLevels handLevels = new HandLevels();
    private Match match;          // null for standalone / headless runs
    private PlayerId playerId;    // null for standalone / headless runs
    private final List<JokerCard> jokers = new ArrayList<>();
    private final List<ConsumableCard> consumables = new ArrayList<>();
    private final List<DeckCard> deck = new ArrayList<>();   // persistent; reshuffled each round
    private int handSize = 8;
    private int baseHands = 4;
    private int baseDiscards = 3;
    private int interestCap = 5;      // max $ of interest per round (raised by To the Moon / Seed Money)
    private Round round;              // non-null only during a blind
    private ScoringSession scoring;   // non-null only during a hand
    private int shuffleIndex;         // per-round shuffle salt (Nth shuffle on this run)

    /** Builds a run from the match seed; every player's run uses the same seed. */
    public Run(long seed) { this(new DeterministicRng(seed)); }

    /** Injection point for tests, which can supply a stub Rng with scripted outcomes. */
    public Run(Rng rng) { this.rng = rng; }

    /** Links this run to its owning match. Called once by {@link Match} during assembly; not for general use. */
    public void joinMatch(Match match, PlayerId playerId) {
        this.match = match;
        this.playerId = playerId;
    }

    /** Owning match, or {@code null} for a standalone/headless run. */
    public Match getMatch() { return match; }

    /** This run's seat, or {@code null} for a standalone/headless run. */
    public PlayerId getPlayerId() { return playerId; }

    /** Keyed randomness for this run. */
    public Rng getRng() { return rng; }

    /** Per-player counters (luck occurrences, future stats). */
    public PlayerStats getStats() { return stats; }

    /** Per-player hand levels (raised by Planet cards). */
    public HandLevels getHandLevels() { return handLevels; }

    /** Resolves one luck roll: draws {@code numerator/denominator} on {@code event}'s stream using its next occurrence salt. */
    public boolean roll(LuckEvent event, int numerator, int denominator) {
        return rng.chance(event.getSource(), stats.nextSalt(event), numerator, denominator);
    }

    public ScoringSession getScoring()        { return scoring; }
    public int getMoney()                     { return money; }
    public void addMoney(int amount)          { money += amount; }
    public List<JokerCard> getJokers()        { return jokers; }
    public List<ConsumableCard> getConsumables() { return consumables; }
    public List<DeckCard> getDeck()           { return deck; }
    public void levelUpHand(HandType h)       { handLevels.levelUp(h); }
    public void createConsumable(ConsumableSpec s) { /* ... */ }

    /** The cards currently in hand, or empty outside a round. */
    public List<DeckCard> getHeld() { return round == null ? List.of() : round.getHand(); }

    public int getHandSize()          { return handSize; }
    public void setHandSize(int n)    { handSize = n; }
    public int getBaseHands()         { return baseHands; }
    public void setBaseHands(int n)   { baseHands = n; }
    public int getBaseDiscards()      { return baseDiscards; }
    public void setBaseDiscards(int n){ baseDiscards = n; }
    public int getInterestCap()       { return interestCap; }
    public void setInterestCap(int n) { interestCap = n; }

    /** The active round, or {@code null} outside a blind. */
    public Round getRound() { return round; }

    /** Starts a round against a blind requiring {@code target} chips; shuffles the deck on this run's seed. */
    public Round beginRound(long target) {
        RandomGenerator shuffle = rng.streamFor(RngSource.DECK_SHUFFLE, shuffleIndex++);
        return round = new Round(this, target, handSize, baseHands, baseDiscards, shuffle);
    }

    /** Settles and ends the active round against {@code blind}, returning its competition result. */
    public BlindResult endRound(Blind blind) {
        if (round == null) throw new IllegalStateException("no active round");
        BlindResult result = SETTLEMENT.settle(this, round, blind);
        round = null;
        return result;
    }

    /** Begins a scoring session; intended for the {@link model.game.scoring.ScoringEngine} only. */
    public ScoringSession beginScoring(long baseChips, long baseMult) {
        return scoring = new ScoringSession(this, baseChips, baseMult);
    }

    /** Ends the current scoring session; intended for the {@link model.game.scoring.ScoringEngine} only. */
    public void endScoring() { scoring = null; }
}