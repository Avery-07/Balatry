package model.game.player;

import model.cards.Card;
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
import model.modifiers.Edition;

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
    private int jokerSlots = 5;       // capacity for slot-consuming jokers (NEGATIVE jokers are free)
    private int consumableSlots = 2;  // capacity for slot-consuming consumables (NEGATIVE are free)
    private int shopSlots = 2;        // card slots offered per shop
    private int baseRerollCost = 5;   // first reroll cost; +$1 per reroll within a shop
    private Round round;              // non-null only during a blind
    private Shop shop;                // non-null only during a shop
    private ScoringSession scoring;   // non-null only during a hand
    private int shuffleIndex;         // per-round shuffle salt (Nth shuffle on this run)
    private int shopIndex;            // per-shop salt coordinate (Nth shop on this run)

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

    // --- inventory: acquisition routing and slot accounting (NEGATIVE cards don't consume a slot) ---

    /** Whether {@code card} can be taken into inventory right now (deck cards always can). */
    public boolean canAcquire(Card card) {
        if (card instanceof JokerCard joker)        return canAddJoker(joker);
        if (card instanceof ConsumableCard consumable) return canAddConsumable(consumable);
        return true;
    }

    /** Routes {@code card} into the matching inventory; callers should check {@link #canAcquire} first. */
    public void acquire(Card card) {
        if (card instanceof JokerCard joker)              jokers.add(joker);
        else if (card instanceof ConsumableCard consumable) consumables.add(consumable);
        else if (card instanceof DeckCard deckCard)       deck.add(deckCard);
        else throw new IllegalArgumentException("cannot acquire " + card.getClass().getSimpleName());
    }

    /** Jokers occupying a slot (NEGATIVE jokers are free). */
    public int usedJokerSlots() {
        int n = 0;
        for (JokerCard j : jokers) if (j.getEdition() != Edition.NEGATIVE) n++;
        return n;
    }

    /** Consumables occupying a slot (NEGATIVE consumables are free). */
    public int usedConsumableSlots() {
        int n = 0;
        for (ConsumableCard c : consumables) if (c.getEdition() != Edition.NEGATIVE) n++;
        return n;
    }

    public boolean canAddJoker(JokerCard joker) {
        return joker.getEdition() == Edition.NEGATIVE || usedJokerSlots() < jokerSlots;
    }

    public boolean canAddConsumable(ConsumableCard consumable) {
        return consumable.getEdition() == Edition.NEGATIVE || usedConsumableSlots() < consumableSlots;
    }

    /** Sells the joker at {@code index}, banking its sell value and freeing its slot. */
    public int sellJoker(int index) {
        JokerCard joker = jokers.remove(index);
        int value = joker.getSellValue();
        addMoney(value);
        return value;
    }

    /** Sells the consumable at {@code index}, banking its sell value and freeing its slot. */
    public int sellConsumable(int index) {
        ConsumableCard consumable = consumables.remove(index);
        int value = consumable.getSellValue();
        addMoney(value);
        return value;
    }

    /** Uses the consumable at {@code index}: fires its effect, then removes it from inventory. */
    public void useConsumable(int index) {
        ConsumableCard consumable = consumables.get(index);
        consumable.consume(this);
        consumables.remove(consumable);   // by reference: safe if the effect mutated the list
    }

    public int getJokerSlots()         { return jokerSlots; }
    public void setJokerSlots(int n)   { jokerSlots = n; }
    public int getConsumableSlots()    { return consumableSlots; }
    public void setConsumableSlots(int n) { consumableSlots = n; }
    public int getShopSlots()          { return shopSlots; }
    public void setShopSlots(int n)    { shopSlots = n; }
    public int getBaseRerollCost()     { return baseRerollCost; }
    public void setBaseRerollCost(int n) { baseRerollCost = n; }

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

    /** The active shop, or {@code null} outside the shop phase. */
    public Shop getShop() { return shop; }

    /** Opens this run's shop for the SHOP phase, with seed-mirrored contents. */
    public Shop openShop() {
        return shop = new Shop(this, shopIndex++, shopSlots, ShopPool.PLACEHOLDER);
    }

    /** Closes the active shop. */
    public void closeShop() { shop = null; }

    /** Begins a scoring session; intended for the {@link model.game.scoring.ScoringEngine} only. */
    public ScoringSession beginScoring(long baseChips, long baseMult) {
        return scoring = new ScoringSession(this, baseChips, baseMult);
    }

    /** Ends the current scoring session; intended for the {@link model.game.scoring.ScoringEngine} only. */
    public void endScoring() { scoring = null; }
}