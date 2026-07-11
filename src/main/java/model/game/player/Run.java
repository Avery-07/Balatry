package model.game.player;

import model.cards.Card;
import model.cards.consumables.ConsumableCard;
import model.cards.DeckCard;
import model.cards.jokers.JokerCard;
import model.cards.packs.BoosterPack;
import model.cards.packs.PackOpening;
import model.cards.jokers.JokerTrait;
import model.game.sins.SinState;
import model.game.tags.SkipTag;
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
import model.game.shop.ShopSetup;
import model.modifiers.Edition;

import java.util.ArrayList;
import java.util.Collections;
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
    private final Board board = new Board(this);   // the joker inventory and its invariants (slots, Eternal, sale)
    private final List<ConsumableCard> consumables = new ArrayList<>();
    private final List<RelicCard> relics = new ArrayList<>();   // held, single-use multiplayer cards
    private final List<DeckCard> deck = new ArrayList<>();   // persistent; reshuffled each round
    private final BossState bossState = new BossState();     // boss-imposed state (Quartz, Pillar, Crimson Heart, flags)
    private final List<BoosterPack> pendingPacks = new ArrayList<>();   // granted, unopened packs (Wrath, pack tags); cleared at round end
    private final List<SkipTag> pendingTags = new ArrayList<>();        // NEXT_SHOP / NEXT_BOSS / META tags awaiting their moment
    private final Afflictions afflictions = new Afflictions();   // relic-imposed debuffs/shields on this seat
    private final SinState sinState = new SinState();            // per-player, round-scoped state owned by the active sin
    private int handSize = 8;
    private int baseHands = 4;
    private int baseDiscards = 3;
    private int interestCap = 5;      // max $ of interest per round (raised by To the Moon / Seed Money)
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

    /** Resolves one chance on {@code source}, salted by that source's next per-player occurrence counter. */
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
        for (JokerCard j : board.view()) if (!j.isDebuffed()) floor -= j.getSpec().getDebtAllowance();
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
    /** The joker board — all joker mutation goes through it; this view is unmodifiable. */
    public Board board() { return board; }
    /** Unmodifiable view of the joker board, in order (see {@link Board#view()}). */
    public List<JokerCard> getJokers()        { return board.view(); }
    /** Unmodifiable view of the consumable area; mutation goes through create/add/sell/useConsumable. */
    public List<ConsumableCard> getConsumables() { return Collections.unmodifiableList(consumables); }
    /** Unmodifiable view of the full deck; mutation goes through addCardToHand/addCardToDeck/destroyDeckCards/resetDeck. */
    public List<DeckCard> getDeck()           { return Collections.unmodifiableList(deck); }
    /** Boss-imposed state on this seat (active boss, Quartz/Pillar/Crimson Heart bookkeeping). */
    public BossState getBossState() { return bossState; }
    public void levelUpHand(HandType h)       { handLevels.levelUp(h); }

    /** Adds a fresh card for {@code spec} to the consumable area, if there is room (NEGATIVE cards are free). */
    public void createConsumable(ConsumableSpec spec) {
        ConsumableCard card = new ConsumableCard(spec);
        if (canAddConsumable(card)) consumables.add(card);
    }

    /** Adds {@code joker} to the board, if there is room (NEGATIVE jokers are free). */
    public void createJoker(JokerCard joker) { board.add(joker); }

    /** Destroys {@code joker} (by identity); Eternal jokers survive (Ankh, Hex, Madness, Ceremonial Dagger). */
    public boolean destroyJoker(JokerCard joker) { return board.destroy(joker); }

    /** Grants a skip tag: IMMEDIATE tags resolve now; pending timings accumulate until their moment. */
    public void grantTag(SkipTag tag) {
        // Double Tag: each pending copy duplicates the next selected tag (Double itself excluded).
        // of X with n pending Doubles yields 1 + n copies of X, all resolved with X's own timing.
        int copies = 1;
        if (tag != SkipTag.DOUBLE_TAG)
            while (pendingTags.remove(SkipTag.DOUBLE_TAG)) copies++;
        for (int i = 0; i < copies; i++) {
            stats.recordTagGained();
            if (tag.getTiming() == SkipTag.Timing.IMMEDIATE) tag.resolve(this);
            else pendingTags.add(tag);
        }
    }

    /** Unmodifiable view of tags awaiting their moment (next shop, next boss, Double). */
    public List<SkipTag> getPendingTags() { return Collections.unmodifiableList(pendingTags); }

    /** Removes one pending {@code tag} if present (the Match consumes Investment at a defeated boss). */
    public boolean consumePendingTag(SkipTag tag) { return pendingTags.remove(tag); }

    /** Grants an unopened pack (Wrath's free Mega Myth Pack, pack tags); openable until the round ends. */
    public void grantPack(BoosterPack pack) { pendingPacks.add(pack); }

    /** Unmodifiable view of granted, unopened packs. */
    public List<BoosterPack> getPendingPacks() { return Collections.unmodifiableList(pendingPacks); }

    /** Opens the pending pack at {@code index} on the seeded pack stream; the pack leaves the pending area. */
    public PackOpening openPendingPack(int index) {
        BoosterPack pack = pendingPacks.remove(index);
        return pack.openFor(this, rng.streamFor(RngSource.PACK_CONTENTS, stats.nextSalt(RngSource.PACK_CONTENTS)));
    }

    /** Adds an existing relic card to the area, if there is room (NEGATIVE relics are free). */
    public void addRelic(RelicCard relic) {
        if (canAddRelic(relic)) relics.add(relic);
    }

    /** Destroys {@code consumable} (by identity); Eternal consumables survive, mirroring the joker rule. */
    public boolean destroyConsumable(ConsumableCard consumable) {
        if (consumable == null || consumable.hasSticker(Sticker.ETERNAL)) return false;
        return consumables.removeIf(c -> c == consumable);
    }

    /** Unmodifiable view of this seat's held relics; mutation goes through create/sell/consumeRelic. */
    public List<RelicCard> getRelics() { return Collections.unmodifiableList(relics); }

    /** Removes the cast relic at {@code index} after its effect resolves (called by Match.useRelic). */
    public RelicCard consumeRelic(int index) { return relics.remove(index); }

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

    /** Adds {@code card} to the deck only (Marble Joker); it enters play at the next shuffle. */
    public void addCardToDeck(DeckCard card) {
        deck.add(card);
        stats.recordCardAdded();
    }

    /** Adds an existing consumable card to the area, if there is room (8 Ball's editioned tarots, pack picks). */
    public void addConsumable(ConsumableCard card) {
        if (canAddConsumable(card)) consumables.add(card);
    }

    /** Setup/test API: replaces the deck wholesale, bypassing gameplay bookkeeping (no cards-added stat). */
    public void resetDeck(List<DeckCard> cards) {
        deck.clear();
        deck.addAll(cards);
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
        if (card instanceof JokerCard joker)              board.add(joker);
        else if (card instanceof ConsumableCard consumable) consumables.add(consumable);
        else if (card instanceof RelicCard relic)         relics.add(relic);
        else if (card instanceof DeckCard deckCard)       { deck.add(deckCard); stats.recordCardAdded(); }
        else throw new IllegalArgumentException("cannot acquire " + card.getClass().getSimpleName());
    }

    /** Jokers occupying a slot (NEGATIVE jokers are free). */
    public int usedJokerSlots() { return board.usedSlots(); }

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

    public boolean canAddJoker(JokerCard joker) { return board.hasRoomFor(joker); }

    public boolean canAddConsumable(ConsumableCard consumable) {
        return consumable.getEdition() == Edition.NEGATIVE || usedConsumableSlots() < consumableSlots;
    }

    public boolean canAddRelic(RelicCard relic) {
        return relic.getEdition() == Edition.NEGATIVE || usedRelicSlots() < relicSlots;
    }

    /** Sells the joker at {@code index}; Eternal jokers cannot be sold (see {@link Board#sell}). */
    public int sellJoker(int index) { return board.sell(index); }

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
        if (consumable.isDebuffed())   // Greed's claim: a debuffed consumable is dead until the sticker is removed
            throw new IllegalStateException("a debuffed consumable cannot be used: " + consumable.getSpec().getName());
        ConsumableSpec spec = consumable.getSpec();
        consumables.remove(consumable);   // free its slot before the effect runs, so creation effects (Emperor, High Priestess) can fill it
        consumableTargets = List.copyOf(targets);
        consumable.consume(this);
        consumableTargets = List.of();
        stats.recordConsumableUsed(spec);
        if (match != null) match.recordConsumableUsed(spec);   // Mimesis: the table's last-used consumable
        if (match != null) match.getSinModifier().onConsumableUsed(this);   // Gluttony: feed the communal gauge
        if (spec.getType() != ConsumableType.SPECTRAL && spec != Tarots.THE_FOOL.spec())
            lastTarotOrPlanet = spec;     // The Fool later recreates the last Tarot/Planet used
    }

    public int getJokerSlots()         { return board.getSlots(); }
    public void setJokerSlots(int n)   { board.setSlots(n); }
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
        if (voucher.isDebuffed())
            throw new IllegalStateException("a debuffed voucher cannot be redeemed: " + voucher.getSpec().getName());
        if (!stats.canRedeem(voucher)) throw new IllegalStateException("voucher not redeemable: " + voucher.getSpec().getName());
        voucher.getSpec().getEffect().apply(this);
        stats.markRedeemed(voucher.getSpec());
    }

    /** Resets this run's per-ante allowances; call at the start of each ante. */
    public void beginAnte() { stats.beginAnte(); afflictions.beginAnte(); bossState.beginAnte(); sinState.beginAnte(); }

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
        bossState.beginRound(boss);
        stats.beginRound();             // fresh round-scoped tallies before any round-start joker reads them
        sinState.beginRound();          // reset per-player sin state before the active sin sets it via onRoundBegin
        afflictions.beginRound(board.view()); // promote pending relic debuffs (rank/suit/joker) for this round
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
                bossState.noteQuartzDebuffed(card);
            }
        }
    }

    /** The Pillar: records {@code cards} as played this ante; only non-boss blinds count ("earlier blinds"). */
    void recordAntePlayed(List<DeckCard> cards) {
        bossState.recordAntePlayed(cards);
    }

    /** Amber Acorn: randomizes the joker board order on the boss-effect stream. */
    private void shuffleJokerBoard() {
        board.shuffle(rng.streamFor(RngSource.BOSS_EFFECT, stats.nextSalt(RngSource.BOSS_EFFECT)));
    }

    /** Crimson Heart: re-picks the disabled joker for the coming hand — a different one than last hand when the board allows it. */
    void rollCrimsonHeart() {
        JokerCard previous = bossState.clearCrimsonHeart();
        BossBlind eff = effectiveBoss();
        if (eff == null || !eff.disablesJokerPerHand() || board.isEmpty()) return;
        RandomGenerator r = rng.streamFor(RngSource.BOSS_EFFECT, stats.nextSalt(RngSource.BOSS_EFFECT));
        JokerCard pick = board.get(r.nextInt(board.size()));
        while (board.size() > 1 && pick == previous) pick = board.get(r.nextInt(board.size()));   // "a different joker each hand"
        bossState.disableJoker(pick);
    }

    /** Crimson Heart's currently disabled joker, or null. */
    public JokerCard getCrimsonDisabledJoker() { return bossState.getCrimsonDisabledJoker(); }

    /** A fresh boss-effect stream draw (occurrence-salted); package tools for in-round boss randomness. */
    RandomGenerator bossEffectStream() {
        return rng.streamFor(RngSource.BOSS_EFFECT, stats.nextSalt(RngSource.BOSS_EFFECT));
    }

    public BossBlind getActiveBoss() { return bossState.getActiveBoss(); }

    /** The active boss after per-player disabling (owns Chicot, or sold Luchador this round), or null. */
    public BossBlind effectiveBoss() { return bossDisabled() ? null : bossState.getActiveBoss(); }

    /** The Commons' shared discard pool this seat draws from, or {@code null} when no boss imposes one. */
    public SharedDiscardPool sharedDiscardPool() {
        return match == null ? null : match.getBossBehavior().sharedDiscards(this);
    }

    /** Whether the active boss is disabled for this player. */
    public boolean bossDisabled() {
        if (bossState.isLuchadorDisabled()) return true;
        for (JokerCard j : board.view()) if (j.hasActiveTrait(JokerTrait.DISABLES_BOSS)) return true;   // Chicot (inert while debuffed)
        return false;
    }

    /** Luchador's sacrifice: disables the active boss for the rest of this round. */
    public void disableBossForRound() { bossState.disableForRound(); }

    /** Whether {@code card} is debuffed by the active (non-disabled) boss; consulted by the scoring engine. */
    public boolean bossDebuffs(DeckCard card) {
        BossBlind eff = effectiveBoss();
        if (eff == null) return false;
        if (eff.debuffsUntilJokerSold()) return !bossState.jokerSoldThisRound();   // Verdant Leaf: all cards until a joker is sold
        if (eff.debuffsAntePlayed() && bossState.wasPlayedThisAnte(card)) return true;   // The Pillar
        return eff.debuffs(card);
    }

    /** Whether the boss ability fired on the hand currently scoring (read by Matador). */
    public boolean bossTriggeredThisPlay()  { return bossState.isBossTriggered(); }
    public void setBossTriggered(boolean v) { bossState.setBossTriggered(v); }

    /** Mr. Bones: if owned, prevents a blind loss (two charges, then self-destructs). True if the loss was prevented. */
    public boolean tryPreventLoss() {
        for (JokerCard j : board.view()) if (j.hasActiveTrait(JokerTrait.PREVENTS_LOSS)) {   // inert while debuffed
            j.addCounter(1);
            if (j.getCounter() >= 2) destroyJoker(j);
            return true;
        }
        return false;
    }

    /** Fires {@code trigger} on every non-debuffed joker, in board order. Iterates a snapshot so an effect may add or remove jokers. */
    public void fire(Trigger trigger) {
        for (JokerCard joker : List.copyOf(board.view()))
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
        bossState.endRound();     // restore Quartz debuffs, re-enable Crimson Heart's joker, drop the boss
        pendingPacks.clear();     // an unopened Wrath pack does not carry past its round
        round = null;
        return result;
    }

    /** The active shop, or {@code null} outside the shop phase. */
    public Shop getShop() { return shop; }

    /** Opens this run's shop for the SHOP phase: assembles a {@link ShopSetup} from the run's defaults, lets the active sin configure the ante's ambient shop environment, layers this seat's pending NEXT_SHOP tags on top, then builds the shop. */
    public Shop openShop() {
        afflictions.beginShop();   // promote a pending Limos debuff before the shop fills its first slot
        ShopSetup setup = new ShopSetup(
                shopSlots, CatalogShopPool.INSTANCE,
                packSlots, CatalogPackPool.INSTANCE,
                voucherSlots, CatalogVoucherPool.INSTANCE);
        if (match != null) match.getSinModifier().configureShop(this, setup);
        applyPendingShopTags(setup);
        shop = new Shop(this, stats.nextShopIndex(), setup);
        fire(Trigger.ON_SHOP_START);
        return shop;
    }

    /** Consumes every pending NEXT_SHOP tag into {@code setup}. */
    private void applyPendingShopTags(ShopSetup setup) {
        for (SkipTag tag : List.copyOf(pendingTags)) {
            if (tag.getTiming() != SkipTag.Timing.NEXT_SHOP) continue;
            pendingTags.remove(tag);
            switch (tag) {
                case COUPON_TAG   -> setup.setInitialItemsFree(true);
                case D6_TAG       -> setup.setRerollsFromZero(true);
                case VOUCHER_TAG  -> setup.addVouchers(1);
                case NEGATIVE_TAG -> setup.addNegativeJokerGrant();
                case RARE_TAG     -> setup.injectFreeCard(rolledTagJoker(model.cards.jokers.Rarity.RARE));
                case UNCOMMON_TAG -> {
                    setup.injectFreeCard(rolledTagJoker(model.cards.jokers.Rarity.UNCOMMON));
                    setup.injectFreeCard(rolledTagJoker(model.cards.jokers.Rarity.UNCOMMON));
                }
                default -> throw new IllegalStateException("unhandled NEXT_SHOP tag: " + tag);
            }
        }
    }

    /** A tag-granted free shop joker of {@code rarity}, drawn from the occurrence-salted generation stream. */
    private JokerCard rolledTagJoker(model.cards.jokers.Rarity rarity) {
        return model.cards.jokers.Jokers.randomOfRarity(rarity,
                rng.streamFor(RngSource.JOKER_GENERATION, stats.nextSalt(RngSource.JOKER_GENERATION))).make();
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