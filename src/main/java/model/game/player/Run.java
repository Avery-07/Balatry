package model.game.player;

import model.cards.Card;
import model.cards.consumables.ConsumableCard;
import model.cards.DeckCard;
import model.cards.jokers.JokerCard;
import model.cards.jokers.JokerTrait;
import model.game.sins.SinState;
import model.cards.consumables.ConsumableSpec;
import model.cards.relics.RelicCard;
import model.cards.relics.RelicSpec;
import model.modifiers.Sticker;
import model.cards.consumables.ConsumableType;
import model.cards.consumables.Tarots;
import model.cards.vouchers.Voucher;
import model.cards.vouchers.VoucherSpec;
import model.game.*;
import model.game.bosses.SharedDiscardPool;
import model.game.rng.DeterministicRng;
import model.game.rng.Rng;
import model.game.rng.RngSource;
import model.game.scoring.HandType;
import model.game.scoring.ScoringSession;
import model.game.scoring.Trigger;
import model.game.shop.CatalogPackPool;
import model.game.shop.CatalogShopPool;
import model.game.shop.CatalogVoucherPool;
import model.game.shop.Shop;
import model.modifiers.Edition;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.random.RandomGenerator;

/** One player's private state: money, jokers, consumables, deck, the active scoring session, and keyed randomness. */
public final class Run {
    private static final RoundSettlement SETTLEMENT = new RoundSettlement();

    private int money;
    private int lastInDebtSpend;   // dollars of the last spend made while below $0 (read by ON_SPEND)
    private boolean purchaseFree;  // transient: set true during a buy to waive its cost
    private final Rng rng;
    private final PlayerStats stats = new PlayerStats();
    private final HandLevels handLevels = new HandLevels();
    private Match match;          // null for standalone / headless runs
    private PlayerId playerId;    // null for standalone / headless runs
    private final List<JokerCard> jokers = new ArrayList<>();
    private final List<ConsumableCard> consumables = new ArrayList<>();
    private final List<RelicCard> relics = new ArrayList<>();   // held, single-use multiplayer cards
    private final List<DeckCard> deck = new ArrayList<>();   // persistent; reshuffled each round
    private final List<DeckCard> bossDebuffedCards = new ArrayList<>();   // cards The Quartz debuffed this round, restored at round end
    private final Set<DeckCard> antePlayedCards = new HashSet<>();   // The Pillar: cards played in this ante's non-boss blinds (identity set)
    private JokerCard crimsonJoker;            // Crimson Heart: the joker currently disabled, or null
    private boolean crimsonStickerAdded;       // whether the DEBUFFED sticker on crimsonJoker came from here
    private final Afflictions afflictions = new Afflictions();   // relic-imposed debuffs/shields on this seat
    private final SinState sinState = new SinState();            // per-player, round-scoped state owned by the active sin
    private int handSize = 8;
    private int baseHands = 4;
    private int baseDiscards = 3;
    private int interestCap = 5;      // max $ of interest per round (raised by To the Moon / Seed Money)
    private int jokerSlots = 5;       // capacity for slot-consuming jokers (NEGATIVE jokers are free)
    private int consumableSlots = 2;  // capacity for slot-consuming consumables (NEGATIVE are free)
    private int relicSlots = 2;        // capacity for slot-consuming relics (NEGATIVE are free)
    private int shopSlots = 3;        // card slots offered per shop
    private int packSlots = 3;        // booster-pack slots offered per shop
    private int voucherSlots = 2;     // voucher slots offered per shop (one redeemable per ante)
    private int baseRerollCost = 5;   // first reroll cost; +$1 per reroll within a shop
    private int shopDiscount;         // % off shop prices (Clearance Sale / Liquidation)
    private int packOptionBonus;      // extra options shown per booster pack (Sampler)
    private int packMegaPickBonus;    // extra cards kept from Mega packs (Connoisseur)
    private Round round;              // non-null only during a blind
    private Shop shop;                // non-null only during a shop
    private ScoringSession scoring;   // non-null only during a hand
    private int shuffleIndex;         // per-round shuffle salt (Nth shuffle on this run)
    private List<Card> consumableTargets = List.of();       // transient: the active consumable's selected cards
    private List<DeckCard> lastDiscarded = List.of();       // transient: cards of the discard currently being broadcast
    private ConsumableSpec lastTarotOrPlanet;               // last Tarot/Planet used this run (The Fool excluded)
    private BossBlind activeBoss;          // the boss for the current blind, or null (small/big blinds, headless rounds)
    private boolean luchadorDisable;       // per-round: Luchador was sold, disabling the boss for this player
    private boolean verdantSold;           // per-round: a joker was sold (lifts Verdant Leaf's all-cards debuff)
    private boolean bossTriggered;         // per-play: the boss ability fired this hand (read by Matador)

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

    /**
     * Resolves one chance on {@code source}, salted by that source's next per-player occurrence counter.
     * <p>Salting rule: use this counter form only for <em>emergent-timing</em> events that have no stable
     * coordinate (glass shatter, lucky procs, effect rolls). For <em>positioned</em> draws (shop slot, ante,
     * hand/card index) hash the coordinates with {@link Rng#combine} instead — it mirrors across players for
     * free and needs no mutable state.
     */
    public boolean roll(RngSource source, int numerator, int denominator) {
        return rng.chance(source, stats.nextSalt(source), numerator, denominator);
    }

    public ScoringSession getScoring()        { return scoring; }
    public int getMoney()                     { return money; }
    public void addMoney(int amount) {
        money += amount;
        if (amount > 0) fire(Trigger.ON_EARN);
    }

    /** Lowest balance this run may reach: $0 minus every owned joker's debt allowance (e.g. Credit Card). */
    public int minBalance() {
        int floor = 0;
        for (JokerCard j : jokers) if (!j.isDebuffed()) floor -= j.getSpec().getDebtAllowance();
        return floor;
    }

    /** Spends {@code amount} (may go negative, down to {@link #minBalance()}); fires ON_SPEND so debt jokers can react. */
    public void spend(int amount) {
        lastInDebtSpend = Math.max(0, amount - Math.max(0, money));   // portion spent while below $0
        money -= amount;
        fire(Trigger.ON_SPEND);
    }

    /** Dollars of the most recent {@link #spend} made while in debt; read by ON_SPEND effects. */
    public int getLastInDebtSpend() { return lastInDebtSpend; }

    /** Transient per-purchase flags, driven by {@link Shop#buy} around the ON_PURCHASE_PRICING dispatch. */
    public void beginPurchase()     { purchaseFree = false; }
    public void makePurchaseFree()  { purchaseFree = true; }
    public boolean isPurchaseFree() { return purchaseFree; }
    public List<JokerCard> getJokers()        { return jokers; }
    public List<ConsumableCard> getConsumables() { return consumables; }
    public List<DeckCard> getDeck()           { return deck; }
    public void levelUpHand(HandType h)       { handLevels.levelUp(h); }

    /** Adds a fresh card for {@code spec} to the consumable area, if there is room (NEGATIVE cards are free). */
    public void createConsumable(ConsumableSpec spec) {
        ConsumableCard card = new ConsumableCard(spec);
        if (canAddConsumable(card)) consumables.add(card);
    }

    /** Adds {@code joker} to the board, if there is room (NEGATIVE jokers are free). */
    public void createJoker(JokerCard joker) {
        if (canAddJoker(joker)) jokers.add(joker);
    }

    /** Removes {@code joker} (by identity) from the board; used by destructive spectrals (Ankh, Hex). */
    public void destroyJoker(JokerCard joker) { jokers.removeIf(j -> j == joker); }

    /** This seat's held relics. */
    public List<RelicCard> getRelics() { return relics; }

    /** Relic-imposed debuffs and shields placed on this seat by relics (Anathema, Limos, Aegis, ...). */
    public Afflictions getAfflictions() { return afflictions; }

    /** Per-player, round-scoped state for the active sin (Pride's chosen multiplier, ...). */
    public SinState getSinState() { return sinState; }

    /** Whether {@code card} is debuffed by an active rank/suit relic this round; consulted by the scoring engine. */
    public boolean relicDebuffs(DeckCard card) { return afflictions.debuffs(card); }

    /** Whether the first slot of the open shop is debuffed by Limos; read by the shop while filling. */
    public boolean isFirstShopSlotDebuffed() { return afflictions.isFirstSlotDebuffed(); }

    /** Adds a fresh card for {@code spec} to the relic area, if there is room (NEGATIVE relics are free). */
    public void createRelic(RelicSpec spec) {
        RelicCard card = new RelicCard(spec);
        if (canAddRelic(card)) relics.add(card);
    }

    /** Adds {@code card} to the deck and, if a round is active, to the current hand (Familiar, Cryptid, ...). */
    public void addCardToHand(DeckCard card) {
        deck.add(card);
        stats.recordCardAdded();
        if (round != null) round.addToHand(card);
    }

    /** The cards the active consumable is being applied to; empty outside {@link #useConsumable}. */
    public List<Card> getConsumableTargets() { return consumableTargets; }

    /** The selected targets that are playing cards — the common case for card-modifying consumables. */
    public List<DeckCard> getDeckCardTargets() {
        List<DeckCard> out = new ArrayList<>();
        for (Card c : consumableTargets) if (c instanceof DeckCard d) out.add(d);
        return out;
    }

    /** The last Tarot or Planet spec used this run (The Fool excluded), or {@code null}. Read by The Fool. */
    public ConsumableSpec getLastTarotOrPlanet() { return lastTarotOrPlanet; }

    /** Salt for the next draw on {@code source}; advances that source's per-player occurrence counter. */
    public long nextSalt(RngSource source) { return stats.nextSalt(source); }

    /** Permanently removes each card (by identity) from the deck and, if a round is active, from the hand. */
    public void destroyDeckCards(List<DeckCard> cards) {
        for (DeckCard c : cards) {
            deck.removeIf(d -> d == c);
            if (round != null) round.removeFromHand(c);
        }
        stats.recordCardsDestroyed(cards.size());
    }

    // --- inventory: acquisition routing and slot accounting (NEGATIVE cards don't consume a slot) ---

    /** Whether {@code card} can be taken into inventory right now (deck cards always can). */
    public boolean canAcquire(Card card) {
        if (card instanceof JokerCard joker)        return canAddJoker(joker);
        if (card instanceof ConsumableCard consumable) return canAddConsumable(consumable);
        if (card instanceof RelicCard relic)        return canAddRelic(relic);
        return true;
    }

    /** Routes {@code card} into the matching inventory; callers should check {@link #canAcquire} first. */
    public void acquire(Card card) {
        if (card instanceof JokerCard joker)              jokers.add(joker);
        else if (card instanceof ConsumableCard consumable) consumables.add(consumable);
        else if (card instanceof RelicCard relic)         relics.add(relic);
        else if (card instanceof DeckCard deckCard)       { deck.add(deckCard); stats.recordCardAdded(); }
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

    /** Relics occupying a slot (NEGATIVE relics are free). */
    public int usedRelicSlots() {
        int n = 0;
        for (RelicCard r : relics) if (r.getEdition() != Edition.NEGATIVE) n++;
        return n;
    }

    public boolean canAddJoker(JokerCard joker) {
        return joker.getEdition() == Edition.NEGATIVE || usedJokerSlots() < jokerSlots;
    }

    public boolean canAddConsumable(ConsumableCard consumable) {
        return consumable.getEdition() == Edition.NEGATIVE || usedConsumableSlots() < consumableSlots;
    }

    public boolean canAddRelic(RelicCard relic) {
        return relic.getEdition() == Edition.NEGATIVE || usedRelicSlots() < relicSlots;
    }

    /** Sells the joker at {@code index}, banking its sell value and freeing its slot. */
    public int sellJoker(int index) {
        JokerCard joker = jokers.get(index);
        joker.trigger(Trigger.ON_SOLD, this);   // the joker reacts to its own sale (e.g. Luchador) while still on the board
        jokers.remove(index);
        int value = joker.getSellValue();
        addMoney(value);
        stats.recordCardSold();
        verdantSold = true;                      // Verdant Leaf: selling a joker lifts its all-cards debuff this round
        return value;
    }

    /** Sells the consumable at {@code index}, banking its sell value and freeing its slot. */
    public int sellConsumable(int index) {
        ConsumableCard consumable = consumables.remove(index);
        int value = consumable.getSellValue();
        addMoney(value);
        stats.recordCardSold();
        return value;
    }

    /** Sells the relic at {@code index}, banking its sell value and freeing its slot. */
    public int sellRelic(int index) {
        RelicCard relic = relics.remove(index);
        int value = relic.getSellValue();
        addMoney(value);
        stats.recordCardSold();
        return value;
    }

    /** Uses the consumable at {@code index} with no selected targets. */
    public void useConsumable(int index) { useConsumable(index, List.of()); }

    /** Uses the consumable at {@code index}, applying its effect to {@code targets}, then removes it from inventory. */
    public void useConsumable(int index, List<? extends Card> targets) {
        ConsumableCard consumable = consumables.get(index);
        ConsumableSpec spec = consumable.getSpec();
        consumables.remove(consumable);   // free its slot before the effect runs, so creation effects (Emperor, High Priestess) can fill it
        consumableTargets = List.copyOf(targets);
        consumable.consume(this);
        consumableTargets = List.of();
        stats.recordConsumableUsed(spec);
        if (match != null) match.recordConsumableUsed(spec);   // Mimesis: the table's last-used consumable
        if (spec.getType() != ConsumableType.SPECTRAL && spec != Tarots.THE_FOOL.spec())
            lastTarotOrPlanet = spec;     // The Fool later recreates the last Tarot/Planet used
    }

    public int getJokerSlots()         { return jokerSlots; }
    public void setJokerSlots(int n)   { jokerSlots = n; }
    public int getConsumableSlots()    { return consumableSlots; }
    public void setConsumableSlots(int n) { consumableSlots = n; }
    public int getRelicSlots()         { return relicSlots; }
    public void setRelicSlots(int n)   { relicSlots = n; }
    public int getShopSlots()          { return shopSlots; }
    public void setShopSlots(int n)    { shopSlots = n; }
    public int getPackSlots()          { return packSlots; }
    public void setPackSlots(int n)    { packSlots = n; }
    public int getVoucherSlots()       { return voucherSlots; }
    public void setVoucherSlots(int n) { voucherSlots = n; }
    public int getBaseRerollCost()     { return baseRerollCost; }
    public void setBaseRerollCost(int n) { baseRerollCost = n; }
    public int getShopDiscount()       { return shopDiscount; }
    public void setShopDiscount(int pct) { shopDiscount = pct; }
    public int getPackOptionBonus()    { return packOptionBonus; }
    public void setPackOptionBonus(int n) { packOptionBonus = n; }
    public int getPackMegaPickBonus()  { return packMegaPickBonus; }
    public void setPackMegaPickBonus(int n) { packMegaPickBonus = n; }

    /** Whether {@code spec} has been redeemed on this run (used for upgrade prerequisites). */
    public boolean hasRedeemed(VoucherSpec spec) { return stats.hasRedeemed(spec); }

    /** Whether {@code voucher} may be redeemed now: not already redeemed, base satisfied, and none used yet this ante. */
    public boolean canRedeem(Voucher voucher) { return stats.canRedeem(voucher); }

    /** Applies {@code voucher}'s effect and records it; consumes this ante's single redemption. */
    public void redeemVoucher(Voucher voucher) {
        if (!stats.canRedeem(voucher)) throw new IllegalStateException("voucher not redeemable: " + voucher.getSpec().getName());
        voucher.getSpec().getEffect().apply(this);
        stats.markRedeemed(voucher.getSpec());
    }

    /** Resets this run's per-ante allowances; call at the start of each ante. */
    public void beginAnte() { stats.beginAnte(); afflictions.beginAnte(); antePlayedCards.clear(); }

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
    public Round beginRound(long target) { return beginRound(target, null); }

    /** Starts a round against {@code boss} (null on small/big blinds); applies the boss's hand/discard/hand-size changes. */
    public Round beginRound(long target, BossBlind boss) {
        activeBoss = boss;
        luchadorDisable = false;
        verdantSold = false;
        bossTriggered = false;
        stats.beginRound();             // fresh round-scoped tallies before any round-start joker reads them
        sinState.beginRound();          // reset per-player sin state before the active sin sets it via onRoundBegin
        afflictions.beginRound(jokers); // promote pending relic debuffs (rank/suit/joker) for this round
        fire(Trigger.ON_ROUND_START);   // jokers react to blind selection before the deal, so deck/joker mutations land in this round
        RandomGenerator shuffle = rng.streamFor(RngSource.DECK_SHUFFLE, shuffleIndex++);
        BossBlind eff = effectiveBoss();   // Chicot disables effects at build time; Luchador can only disable later in the round
        int hands    = (eff != null && eff.oneHandOnly()) ? 1 : baseHands;
        int discards = Math.max(0, baseDiscards + (eff != null ? eff.discardDelta() : 0));   // The Water: -1
        int hsize    = Math.max(1, handSize + (eff != null ? eff.handSizeDelta() : 0));
        round = new Round(this, target, hsize, hands, discards, shuffle);
        if (eff != null && eff.randomDebuffOneIn() > 0) applyQuartzDebuff(eff.randomDebuffOneIn());   // The Quartz
        if (eff != null && eff.shufflesJokers()) shuffleJokerBoard();                                 // Amber Acorn
        rollCrimsonHeart();                                                                           // Crimson Heart: first hand's disabled joker
        return round;
    }

    /** The Quartz: debuffs roughly 1-in-{@code oneIn} of this round's cards, tracked so they are restored at round end. */
    private void applyQuartzDebuff(int oneIn) {
        RandomGenerator r = rng.streamFor(RngSource.BOSS_EFFECT, stats.nextSalt(RngSource.BOSS_EFFECT));
        List<DeckCard> cards = new ArrayList<>(round.getHand());
        cards.addAll(round.getDrawPile());                 // hand + draw pile == this round's full deck (same instances)
        for (DeckCard card : cards) {
            if (!card.isDebuffed() && r.nextInt(oneIn) == 0) {
                card.apply(Sticker.DEBUFFED);
                bossDebuffedCards.add(card);
            }
        }
    }

    /** The Pillar: records {@code cards} as played this ante; only non-boss blinds count ("earlier blinds"). */
    void recordAntePlayed(List<DeckCard> cards) {
        if (activeBoss == null) antePlayedCards.addAll(cards);
    }

    /** Amber Acorn: randomizes the joker board order (Fisher-Yates on the boss-effect stream). */
    private void shuffleJokerBoard() {
        RandomGenerator r = rng.streamFor(RngSource.BOSS_EFFECT, stats.nextSalt(RngSource.BOSS_EFFECT));
        for (int i = jokers.size() - 1; i > 0; i--) {
            int j = r.nextInt(i + 1);
            JokerCard tmp = jokers.get(i);
            jokers.set(i, jokers.get(j));
            jokers.set(j, tmp);
        }
    }

    /**
     * Crimson Heart: re-picks the disabled joker for the coming hand — a different one than last hand when the
     * board allows it. Called at the deal and after every play; a disabled boss (Luchador mid-round) or an empty
     * board clears the disable and picks nothing. Only a sticker this run added is stripped.
     */
    void rollCrimsonHeart() {
        JokerCard previous = crimsonJoker;
        clearCrimsonHeart();
        BossBlind eff = effectiveBoss();
        if (eff == null || !eff.disablesJokerPerHand() || jokers.isEmpty()) return;
        RandomGenerator r = rng.streamFor(RngSource.BOSS_EFFECT, stats.nextSalt(RngSource.BOSS_EFFECT));
        JokerCard pick = jokers.get(r.nextInt(jokers.size()));
        while (jokers.size() > 1 && pick == previous) pick = jokers.get(r.nextInt(jokers.size()));   // "a different joker each hand"
        if (!pick.isDebuffed()) {   // a genuinely debuffed joker stays debuffed; we just don't own its sticker
            pick.apply(Sticker.DEBUFFED);
            crimsonJoker = pick;
            crimsonStickerAdded = true;
        }
    }

    private void clearCrimsonHeart() {
        if (crimsonStickerAdded && crimsonJoker != null) crimsonJoker.remove(Sticker.DEBUFFED);
        crimsonJoker = null;
        crimsonStickerAdded = false;
    }

    /** Crimson Heart's currently disabled joker, or null. */
    public JokerCard getCrimsonDisabledJoker() { return crimsonJoker; }

    /** A fresh boss-effect stream draw (occurrence-salted); package tools for in-round boss randomness. */
    RandomGenerator bossEffectStream() {
        return rng.streamFor(RngSource.BOSS_EFFECT, stats.nextSalt(RngSource.BOSS_EFFECT));
    }

    public BossBlind getActiveBoss() { return activeBoss; }

    /** The active boss after per-player disabling (owns Chicot, or sold Luchador this round), or null. */
    public BossBlind effectiveBoss() { return bossDisabled() ? null : activeBoss; }

    /** The Commons' shared discard pool this seat draws from, or {@code null} when no boss imposes one. */
    public SharedDiscardPool sharedDiscardPool() {
        return match == null ? null : match.getBossBehavior().sharedDiscards(this);
    }

    /** Whether the active boss is disabled for this player. */
    public boolean bossDisabled() {
        if (luchadorDisable) return true;
        for (JokerCard j : jokers) if (j.hasActiveTrait(JokerTrait.DISABLES_BOSS)) return true;   // Chicot (inert while debuffed)
        return false;
    }

    /** Luchador's sacrifice: disables the active boss for the rest of this round. */
    public void disableBossForRound() { luchadorDisable = true; }

    /** Whether {@code card} is debuffed by the active (non-disabled) boss; consulted by the scoring engine. */
    public boolean bossDebuffs(DeckCard card) {
        BossBlind eff = effectiveBoss();
        if (eff == null) return false;
        if (eff.debuffsUntilJokerSold()) return !verdantSold;   // Verdant Leaf: all cards until a joker is sold
        if (eff.debuffsAntePlayed() && antePlayedCards.contains(card)) return true;   // The Pillar
        return eff.debuffs(card);
    }

    /** Whether the boss ability fired on the hand currently scoring (read by Matador). */
    public boolean bossTriggeredThisPlay()  { return bossTriggered; }
    public void setBossTriggered(boolean v) { bossTriggered = v; }

    /** Mr. Bones: if owned, prevents a blind loss (two charges, then self-destructs). True if the loss was prevented. */
    public boolean tryPreventLoss() {
        for (JokerCard j : jokers) if (j.hasActiveTrait(JokerTrait.PREVENTS_LOSS)) {   // inert while debuffed
            j.addCounter(1);
            if (j.getCounter() >= 2) destroyJoker(j);
            return true;
        }
        return false;
    }

    /** Fires {@code trigger} on every non-debuffed joker, in board order. Iterates a snapshot so an effect may add or remove jokers. */
    public void fire(Trigger trigger) {
        for (JokerCard joker : List.copyOf(jokers))
            if (!joker.isDebuffed()) joker.trigger(trigger, this);
    }

    /** Records the just-discarded cards and fires ON_HAND_DISCARDED so jokers can react to them, then clears the channel. */
    public void fireDiscard(List<DeckCard> discarded) {
        lastDiscarded = List.copyOf(discarded);
        fire(Trigger.ON_HAND_DISCARDED);
        lastDiscarded = List.of();
    }

    /** The cards of the discard currently being broadcast; empty outside an ON_HAND_DISCARDED fire. */
    public List<DeckCard> getLastDiscarded() { return lastDiscarded; }

    /** Settles and ends the active round against {@code blind}, returning its competition result. */
    public BlindResult endRound(Blind blind) {
        if (round == null) throw new IllegalStateException("no active round");
        if (blind == Blind.BOSS && round.getOutcome() == RoundOutcome.WON)
            fire(Trigger.ON_BOSS_DEFEATED);   // Rocket payout / Campfire reset, before cash-out
        BlindResult result = SETTLEMENT.settle(this, round, blind);
        afflictions.endRound();   // clear round-scoped relic debuffs; strip any joker sticker this seat's afflictions added
        for (DeckCard card : bossDebuffedCards) card.remove(Sticker.DEBUFFED);   // The Quartz: restore debuffed deck cards
        bossDebuffedCards.clear();
        clearCrimsonHeart();                                                      // Crimson Heart: re-enable the disabled joker
        round = null;
        activeBoss = null;
        return result;
    }

    /** The active shop, or {@code null} outside the shop phase. */
    public Shop getShop() { return shop; }

    /** Opens this run's shop for the SHOP phase, with seed-mirrored card, pack, and voucher rows. */
    public Shop openShop() {
        afflictions.beginShop();   // promote a pending Limos debuff before the shop fills its first slot
        shop = new Shop(this, stats.nextShopIndex(),
                shopSlots, CatalogShopPool.INSTANCE,
                packSlots, CatalogPackPool.INSTANCE,
                voucherSlots, CatalogVoucherPool.INSTANCE);
        fire(Trigger.ON_SHOP_START);
        return shop;
    }

    /** Closes the active shop. */
    public void closeShop() {
        fire(Trigger.ON_SHOP_END);
        afflictions.endShop();   // a Limos debuff lasts only for this shop visit
        shop = null;
    }

    /** Begins a scoring session; intended for the {@link model.game.scoring.ScoringEngine} only. */
    public ScoringSession beginScoring(long baseChips, long baseMult) {
        return scoring = new ScoringSession(this, baseChips, baseMult);
    }

    /** Ends the current scoring session; intended for the {@link model.game.scoring.ScoringEngine} only. */
    public void endScoring() { scoring = null; }
}