package model.game.player;

import model.cards.DeckCard;
import model.cards.DeckCard.Rank;
import model.cards.consumables.ConsumableSpec;
import model.cards.consumables.ConsumableType;
import model.cards.vouchers.Voucher;
import model.cards.vouchers.VoucherSpec;
import model.game.rng.RngSource;
import model.game.scoring.HandType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Per-player tracking that lives beside a {@link Run}: keyed RNG occurrence counters, the shop-open counter,
 * the voucher-redemption ledger, and the accumulated gameplay history that data-driven jokers read
 * (hand-type counts, consumable usage, discards, economy events). This is the home for accumulated run state;
 * {@link Run} keeps the core inventory, subsystems, and configuration. Effect application stays on {@code Run}
 * (it needs the run); here we only track what has happened.
 *
 * <p><b>Scope.</b> Most counters are run-scoped and persist for the whole game. A few are round-scoped and reset
 * in {@link #beginRound()} (their getters are named {@code ...ThisRound}); the per-ante voucher allowance resets
 * in {@link #beginAnte()}. Counters that reset on a game event with no fixed cadence (e.g. Campfire on a boss
 * defeat) expose an explicit {@code reset...} method rather than hooking a lifecycle call.
 *
 * <p><b>Recording order for hand plays.</b> {@link #recordHandPlayed} is invoked <em>before</em> the hand is
 * scored, so the current play is already counted when {@code ON_HAND_PLAYED} jokers run. Supernova therefore
 * reads {@link #getHandPlays} including the current hand, and Card Sharp checks
 * {@code getHandPlaysThisRound(type) > 1} ("played earlier this round too").
 */
public final class PlayerStats {

    /** How many of the most recent hand types to retain for windowed reads (e.g. Obelisk). */
    private static final int RECENT_HAND_WINDOW = 16;

    // --- keyed RNG counters / shop counter / voucher ledger (unchanged) ---

    private final Map<RngSource, Integer> occurrences = new EnumMap<>(RngSource.class);
    private int shopsOpened;
    private final Set<VoucherSpec> redeemedVouchers = new HashSet<>();
    private boolean voucherRedeemedThisAnte;

    // --- run-scoped gameplay history ---

    private final Map<HandType, Integer> handPlays = new EnumMap<>(HandType.class);   // times each hand type played this run
    private final Deque<HandType> recentHands = new ArrayDeque<>();                    // most-recent-first, capped at RECENT_HAND_WINDOW
    private int totalHandsPlayed;

    private int cardsDiscarded;     // playing cards discarded this run (Yorick)
    private int discardsUsed;       // discard actions taken this run

    private final Map<ConsumableType, Integer> consumablesUsed = new EnumMap<>(ConsumableType.class);
    private final Set<ConsumableSpec> distinctConsumablesUsed = new HashSet<>();      // for "unique ... used" reads (Satellite)

    private int rerolls;            // shop rerolls this run (Flash Card)
    private int purchases;          // shop purchases this run (Loyalty Card)
    private int cardsSold;          // cards sold this run (Campfire; resettable)
    private int cardsAdded;         // playing cards added to the deck this run (Hologram)
    private int cardsDestroyed;     // cards destroyed this run (Canio)
    private int glassDestroyed;     // Glass cards destroyed this run (Glass Joker)
    private int blindsSkipped;      // blinds skipped this run (Throwback / Speed Tag)
    private int unusedDiscards;   // discards unspent at settlement, run-cumulative (Garbage Tag)
    private int tagsGained;       // skip tags gained this run
    private int timesTargeted;      // opponent effects aimed at this player this run (Anger; multiplayer)

    // --- round-scoped gameplay history (reset in beginRound) ---

    private final Map<HandType, Integer> handPlaysThisRound = new EnumMap<>(HandType.class);
    private final Map<Rank, Integer> discardedRanksThisRound = new EnumMap<>(Rank.class);   // ranks discarded this round (Hit the Road)
    private int handsPlayedThisRound;
    private int cardsDiscardedThisRound;
    private int discardsUsedThisRound;

    // --- keyed RNG counters (salt the Nth occurrence of an emergent draw) ---

    /** Salt for the next draw on {@code source} (its 0-based occurrence index), then advances that source's counter. */
    public long nextSalt(RngSource source) {
        int n = occurrences.getOrDefault(source, 0);
        occurrences.put(source, n + 1);
        return n;
    }

    /** How many draws have been taken on {@code source} so far. Does not advance the counter. */
    public int getCount(RngSource source) {
        return occurrences.getOrDefault(source, 0);
    }

    // --- shop counter ---

    /** The structural coordinate for the next shop opened (its 0-based index), then advances the counter. */
    public int nextShopIndex() { return shopsOpened++; }

    // --- voucher ledger ---

    /** Whether {@code spec} has been redeemed on this run (used for upgrade prerequisites). */
    public boolean hasRedeemed(VoucherSpec spec) { return redeemedVouchers.contains(spec); }

    /** Whether {@code voucher} may be redeemed now: not already redeemed, base satisfied, and none used yet this ante. */
    public boolean canRedeem(Voucher voucher) {
        VoucherSpec spec = voucher.getSpec();
        if (voucherRedeemedThisAnte || redeemedVouchers.contains(spec)) return false;
        return spec.getBase() == null || redeemedVouchers.contains(spec.getBase());
    }

    /** Records {@code spec} as redeemed and consumes this ante's single redemption. */
    public void markRedeemed(VoucherSpec spec) {
        redeemedVouchers.add(spec);
        voucherRedeemedThisAnte = true;
    }

    // --- hand-type plays ---

    /** Records one play of {@code type}; call before scoring so the current hand is counted (see class doc). */
    public void recordHandPlayed(HandType type) {
        handPlays.merge(type, 1, Integer::sum);
        handPlaysThisRound.merge(type, 1, Integer::sum);
        totalHandsPlayed++;
        handsPlayedThisRound++;
        recentHands.addFirst(type);
        while (recentHands.size() > RECENT_HAND_WINDOW) recentHands.removeLast();
    }

    /** Times {@code type} has been played this run (Supernova). */
    public int getHandPlays(HandType type)          { return handPlays.getOrDefault(type, 0); }
    /** Times {@code type} has been played this round (Card Sharp: {@code > 1} means "played earlier too"). */
    public int getHandPlaysThisRound(HandType type) { return handPlaysThisRound.getOrDefault(type, 0); }
    /** Total hands played this run. */
    public int getTotalHandsPlayed()                { return totalHandsPlayed; }
    /** Total hands played this round. */
    public int getHandsPlayedThisRound()            { return handsPlayedThisRound; }

    /** Immutable snapshot of run hand-type play counts (absent types are zero). */
    public Map<HandType, Integer> getHandPlayCounts() { return Map.copyOf(handPlays); }

    /**
     * The most-played hand type this run, or {@code null} if none has been played. Ties break toward the
     * higher-ranking hand (Black Hole, Satellite, Telescope, Orbital Tag).
     */
    public HandType getMostPlayedHand() {
        HandType best = null;
        int bestCount = 0;
        for (HandType t : HandType.values()) {   // declaration order is highest-ranking first
            int c = getHandPlays(t);
            if (c > bestCount) { best = t; bestCount = c; }
        }
        return best;
    }

    /** The most recent hand type played, or {@code null} if none yet. */
    public HandType getLastHandType() { return recentHands.peekFirst(); }

    /**
     * Up to {@code n} most recently played hand types, most-recent-first (index 0 is the current/last hand).
     * Retained up to {@link #RECENT_HAND_WINDOW}; Obelisk reads indices 1 and 2 for its "last two hands".
     */
    public List<HandType> getRecentHands(int n) {
        List<HandType> out = new ArrayList<>(Math.min(n, recentHands.size()));
        for (HandType t : recentHands) {
            if (out.size() >= n) break;
            out.add(t);
        }
        return List.copyOf(out);
    }

    // --- discards ---

    /** Records a discard of {@code cards}: bumps run/round discard counts and the per-rank round tally. */
    public void recordDiscard(List<DeckCard> cards) {
        int n = cards.size();
        cardsDiscarded += n;
        cardsDiscardedThisRound += n;
        discardsUsed++;
        discardsUsedThisRound++;
        for (DeckCard c : cards) discardedRanksThisRound.merge(c.getRank(), 1, Integer::sum);
    }

    /** Playing cards discarded this run (Yorick: every 23). */
    public int getCardsDiscarded()           { return cardsDiscarded; }
    /** Discard actions taken this run. */
    public int getDiscardsUsed()             { return discardsUsed; }
    /** Playing cards discarded this round. */
    public int getCardsDiscardedThisRound()  { return cardsDiscardedThisRound; }
    /** Discard actions taken this round. */
    public int getDiscardsUsedThisRound()    { return discardsUsedThisRound; }
    /** Cards of {@code rank} discarded this round (Hit the Road: Jacks). */
    public int getDiscardedThisRound(Rank rank) { return discardedRanksThisRound.getOrDefault(rank, 0); }

    // --- consumable usage ---

    /** Records one use of {@code spec} (any consumable, The Fool included). */
    public void recordConsumableUsed(ConsumableSpec spec) {
        consumablesUsed.merge(spec.getType(), 1, Integer::sum);
        distinctConsumablesUsed.add(spec);
    }

    /** Consumables of {@code type} used this run (Constellation: PLANET; Fortune Teller: TAROT). */
    public int getConsumablesUsed(ConsumableType type) { return consumablesUsed.getOrDefault(type, 0); }
    /** Total consumables used this run. */
    public int getTotalConsumablesUsed() {
        int total = 0;
        for (int v : consumablesUsed.values()) total += v;
        return total;
    }
    /** Distinct consumables of {@code type} used this run (Satellite: unique planets). */
    public int getUniqueConsumablesUsed(ConsumableType type) {
        int n = 0;
        for (ConsumableSpec spec : distinctConsumablesUsed) if (spec.getType() == type) n++;
        return n;
    }

    // --- economy / card events ---

    /** Records a shop reroll (Flash Card). */
    public void recordReroll()        { rerolls++; }
    /** Records a shop purchase (Loyalty Card). */
    public void recordPurchase()      { purchases++; }
    /** Records a card sale (Campfire). */
    public void recordCardSold()      { cardsSold++; }
    /** Records a playing card added to the deck (Hologram). */
    public void recordCardAdded()     { cardsAdded++; }
    /** Records {@code n} cards destroyed (Canio). */
    public void recordCardsDestroyed(int n) { cardsDestroyed += n; }
    /** Records {@code n} Glass cards destroyed; also counts toward total cards destroyed (Glass Joker, Canio). */
    public void recordGlassDestroyed(int n) { glassDestroyed += n; cardsDestroyed += n; }
    /** Records a skipped blind (Throwback / Speed Tag). */
    public void recordBlindSkipped()  { blindsSkipped++; }

    /** Skip tags gained this run (any timing). */
    public int getTagsGained()        { return tagsGained; }
    public void recordTagGained()     { tagsGained++; }

    /** Discards left unspent at each played round's settlement, accumulated over the run (Garbage Tag). */
    public int getUnusedDiscards()            { return unusedDiscards; }
    public void recordUnusedDiscards(int n)   { unusedDiscards += n; }
    /** Records an opponent effect aimed at this player (Anger; multiplayer). */
    public void recordTargeted()      { timesTargeted++; }

    public int getRerolls()         { return rerolls; }
    public int getPurchases()       { return purchases; }
    public int getCardsSold()       { return cardsSold; }
    public int getCardsAdded()      { return cardsAdded; }
    public int getCardsDestroyed()  { return cardsDestroyed; }
    public int getGlassDestroyed()  { return glassDestroyed; }
    public int getBlindsSkipped()   { return blindsSkipped; }
    public int getTimesTargeted()   { return timesTargeted; }

    /** Clears the cards-sold tally (Campfire resets when a Boss Blind is defeated). */
    public void resetCardsSold()    { cardsSold = 0; }

    // --- lifecycle resets ---

    /** Resets round-scoped tallies; call at the start of each round. */
    public void beginRound() {
        handPlaysThisRound.clear();
        discardedRanksThisRound.clear();
        handsPlayedThisRound = 0;
        cardsDiscardedThisRound = 0;
        discardsUsedThisRound = 0;
    }

    /** Resets the per-ante voucher allowance; call at the start of each ante. */
    public void beginAnte() { voucherRedeemedThisAnte = false; }
}
