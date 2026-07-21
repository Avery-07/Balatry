package model.game;

import model.items.DeckType;
import model.items.Decks;
import model.items.consumables.ConsumableSpec;
import model.items.Card;
import model.items.DeckCard;
import model.items.consumables.ConsumableCard;
import model.items.packs.PackOpening;
import model.game.actions.Action;
import model.game.actions.RecordedChoiceProvider;
import model.game.player.Round;
import model.game.shop.Shop;
import model.items.packs.BoosterPack;
import model.items.jokers.JokerCard;
import model.items.relics.RelicCard;
import model.items.relics.RelicContext;
import model.items.relics.RelicKind;
import model.items.relics.RelicTarget;
import model.game.bosses.BossBehavior;
import model.game.bosses.BossBehaviors;
import model.game.player.BlindResult;
import model.game.player.Board;
import model.game.player.DeckSetup;
import model.game.player.Player;
import model.game.player.PlayerId;
import model.game.player.SeatConfig;
import model.game.player.Sleeves;
import model.game.player.RoundOutcome;
import model.game.rng.DeterministicRng;
import model.game.rng.Rng;
import model.game.rng.RngSource;
import model.game.player.Run;
import model.game.sins.SinChoiceProvider;
import model.game.sins.SinModifier;
import model.game.sins.SinTableState;
import model.game.tags.SkipTag;

import java.math.BigDecimal;
import java.math.RoundingMode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** Aggregate root for one competitive game. */
public final class Match {

    /** Every seat's opening balance when a match is dealt. */
    public static final int STARTING_MONEY = 4;

    /** Below this many players still connected there is nothing left to compete over, so the match ends. */
    public static final int MIN_ACTIVE_SEATS = 2;

    private final long seed;
    private final Rng rng;                       // table-level randomness
    private final Map<PlayerId, Player> players; // insertion-ordered by seat
    private final SinSelector sinSelector;
    private final BossSelector bossSelector;                // which boss closes an ante (injectable for tests)
    private final Function<Sin, SinModifier> sinResolver;   // Sin -> behaviour (injectable for tests)
    private final SinChoiceProvider sinChoiceProvider;      // resolves sin player-choices (Pride's multiplier, ...)
    private final PointsPolicy pointsPolicy;                // converts settled results into competition points
    private Standings standings;                            // cumulative points; built in create() once seats exist
    private final int anteCount;                            // match length; the ante-anteCount boss is the final blind
    private final boolean blindSelection;                   // when true, a SELECTION phase precedes every blind

    private MatchPhase phase = MatchPhase.LOBBY;
    private int ante = 0;                         // 0 until started
    private Blind blind = Blind.SMALL;
    private Sin activeSin;                         // null until started
    private SinModifier sinModifier = SinModifier.NONE;   // behaviour for activeSin; refreshed when the sin changes
    private final SinTableState sinTableState = new SinTableState();   // table-level, ante-scoped state owned by the active sin
    private Map<PlayerId, BlindResult> lastResults = Map.of();   // most recent blind's outcomes
    private DeckType deckType = DeckType.STANDARD; // the table's starting deck (shared; sleeve/stake are per-seat)
    private BossBlind currentBoss;                 // the boss for the current BOSS blind, else null
    private BossBlind anteBoss;                     // the boss that will close the current ante; locked at ante start so its effect shows during blind selection
    private BossBehavior bossBehavior = BossBehavior.NONE;   // the boss's Match-level behaviour; NONE outside boss rounds
    private SkipTag currentTag;                    // the tag this blind offers for skipping (table-level, seeded)
    private ConsumableSpec lastConsumableUsed;     // last consumable any seat used (Mimesis)
    private int rerollBossFromAnte = Integer.MAX_VALUE;   // Metabole: reroll the table boss from this ante onward
    private final Map<PlayerId, Boolean> blindChoice = new LinkedHashMap<>();   // SELECTION: seat -> play(true)/skip(false)
    private final java.util.Set<PlayerId> readySeats = new java.util.LinkedHashSet<>();   // RESULT/SHOP: signalled ready
    private final Set<PlayerId> departed = new LinkedHashSet<>();               // seats whose players have left

    private Match(long seed, MatchConfig config) {
        this.seed = seed;
        this.rng = new DeterministicRng(seed);
        this.sinSelector = config.sinSelector();
        this.bossSelector = config.bossSelector();
        this.sinResolver = config.sinResolver();
        this.sinChoiceProvider = config.sinChoiceProvider();
        this.pointsPolicy = config.pointsPolicy();
        this.anteCount = config.anteCount();
        this.blindSelection = config.blindSelection();
        this.players = new LinkedHashMap<>();
    }

    /** Creates a seated match (in {@link MatchPhase#LOBBY}) with default policies. */
    public static Match create(long seed, List<String> playerNames) {
        return create(seed, playerNames, MatchConfig.defaults());
    }

    /** Creates a seated match with a chosen sin policy and otherwise-default policies. */
    public static Match create(long seed, List<String> playerNames, SinSelector sinSelector) {
        return create(seed, playerNames, MatchConfig.defaults().withSinSelector(sinSelector));
    }

    /** Creates a seated match; every seat takes the default sleeve and stake. Seats follow name order. */
    public static Match create(long seed, List<String> playerNames, MatchConfig config) {
        if (playerNames == null) throw new IllegalArgumentException("a match needs 2-4 players, got 0");
        return createSeated(seed, SeatConfig.defaults(playerNames), config);
    }

    /**
     * Creates a seated match from explicit per-seat configs; each player's {@link Run} is built from {@code seed}.
     * The table's {@link DeckType} comes from {@code config} and is dealt to everyone; each seat's own sleeve then
     * adjusts its run. Seats follow list order.
     */
    public static Match createSeated(long seed, List<SeatConfig> seats, MatchConfig config) {
        if (seats == null || seats.size() < 2 || seats.size() > 4)
            throw new IllegalArgumentException("a match needs 2-4 players, got "
                    + (seats == null ? 0 : seats.size()));

        Match match = new Match(seed, config);
        int seat = 0;
        for (SeatConfig sc : seats) {
            PlayerId id = new PlayerId(seat++);
            Run run = new Run(seed);          // same seed -> identical luck per action
            run.setStake(sc.stake());
            run.setSleeve(sc.sleeve());
            run.resetDeck(Decks.of(config.deckType(), run.getRng()));   // the table's deck, identical for every seat
            run.addMoney(STARTING_MONEY);     // every seat opens with $4
            DeckSetup.applyStartingGrants(run, config.deckType());      // Eclipse's opening voucher + joker
            Sleeves.apply(run, sc.sleeve(), id.seat());                 // then the seat's own sleeve
            run.joinMatch(match, id);
            match.players.put(id, new Player(id, sc.name(), run));
        }
        match.deckType = config.deckType();
        match.standings = new Standings(match.players.keySet());
        return match;
    }

    // --- shared state accessors ---

    public long getSeed()           { return seed; }
    public Rng getRng()             { return rng; }          // table-level draws (shared shop, boss blind, sin)
    public MatchPhase getPhase()    { return phase; }
    public int getAnte()            { return ante; }
    public int getAnteCount()       { return anteCount; }
    public Blind getBlind()         { return blind; }
    public Sin getActiveSin()       { return activeSin; }

    /** The starting deck the whole table played. Per-seat sleeve and stake live on each {@link Run}. */
    public DeckType getDeckType()   { return deckType; }

    /**
     * Chips required to clear the current blind at the White Stake (boss target multipliers applied). Stakes are
     * per-seat, so gameplay should ask {@link #getCurrentTarget(PlayerId)}; this form is the table-level baseline.
     */
    public long getCurrentTarget() {
        long base = BlindTargets.target(ante, blind);
        return (blind == Blind.BOSS && currentBoss != null) ? base * currentBoss.targetMultiplier() : base;
    }

    /**
     * Chips {@code id} must score to clear the current blind: the baseline scaled by that seat's own stake, then
     * by the table's deck (Plasma doubles every blind) and the boss's own multiplier.
     */
    public long getCurrentTarget(PlayerId id) {
        long base = BlindTargets.target(ante, blind, getRun(id).getStake()) * deckType.blindMultiplier();
        return (blind == Blind.BOSS && currentBoss != null) ? base * currentBoss.targetMultiplier() : base;
    }

    /** The boss for the current BOSS blind, or {@code null} on small/big blinds. */
    public BossBlind getCurrentBoss() { return currentBoss; }

    /** The boss that will close the current ante, locked at ante start (visible on every blind's selection screen). */
    public BossBlind getAnteBoss() { return anteBoss; }

    /** Cumulative blind count across the match: ante 1 Small = 1, Big = 2, Boss = 3, ante 2 Small = 4, and so on. */
    public int getRoundNumber() { return ante < 1 ? 0 : (ante - 1) * 3 + blind.ordinal() + 1; }

    /** The most recent blind's results by seat, empty before the first cash-out. */
    public Map<PlayerId, BlindResult> getResults() { return Map.copyOf(lastResults); }

    /** The cumulative competition standings (points, last award, ranking). */
    public Standings getStandings() { return standings; }

    /** This seat's most recent blind result, or {@code null} if none yet. */
    public BlindResult getResult(PlayerId id) { return lastResults.get(id); }

    public Collection<Player> getPlayers() { return List.copyOf(players.values()); }

    /** The player at the given seat, or throws if there is none. */
    public Player getPlayer(PlayerId id) {
        Player p = players.get(id);
        if (p == null) throw new IllegalArgumentException("no such player: " + id);
        return p;
    }

    public Run getRun(PlayerId id) { return getPlayer(id).run(); }

    // --- synchronized progression ---

    /** LOBBY -> first blind of ante 1. Selects the opening sin and deals every seat in. */
    public void start() {
        require(MatchPhase.LOBBY, "start");
        ante = 1;
        blind = Blind.SMALL;
        anteBoss = selectBoss();   // lock this ante's boss up front so its effect is visible during blind selection
        activeSin = sinSelector.selectFor(ante, rng);
        for (Player p : players.values()) p.run().beginAnte();
        refreshSinForAnte();
        enterSelectionOrBlind();
    }

    /**
     * Enters the next blind: with blind selection enabled, sets up the blind context (boss, tag) and parks in
     * {@link MatchPhase#SELECTION} awaiting each seat's play-or-skip choice; otherwise deals straight into
     * {@link MatchPhase#BLIND} as before. A host crosses SELECTION -> BLIND via {@link #enterBlind()} once all
     * seats have chosen.
     */
    private void enterSelectionOrBlind() {
        if (blindSelection) {
            setupBlind();
            blindChoice.clear();
            phase = MatchPhase.SELECTION;
        } else {
            phase = MatchPhase.BLIND;
            dealBlind();
        }
    }

    /** BLIND -> SHOP: settles every seat's finished round and records the results. */
    /**
     * Settles the just-finished blind and lands in {@link MatchPhase#RESULT}: every seat's round is scored into a
     * {@link BlindResult}, boss end-effects and sin settlement run, and points are awarded. The shops are not yet
     * open; a host crosses RESULT -> SHOP (or -> FINISHED after the final boss) via {@link #openShopsOrFinish()}
     * once every seat has continued. Barriered: rejects until every seat's round is resolved.
     */
    public void toResult() {
        require(MatchPhase.BLIND, "toResult");
        for (Player p : players.values()) {                         // barrier: everyone must be done
            Round round = p.run().getRound();
            if (round == null || round.getOutcome() == RoundOutcome.IN_PROGRESS)
                throw new IllegalStateException("seat " + p.id() + " has not finished the blind");
        }
        // Seats whose boss is still active at the barrier (Chicot/Luchador seats are exempt from boss adjustments).
        // Captured before settlement because endRound clears the run's boss state.
        Set<PlayerId> participants = new LinkedHashSet<>();
        for (Player p : players.values())
            if (currentBoss != null && p.run().effectiveBoss() == currentBoss) participants.add(p.id());

        Map<PlayerId, BlindResult> results = new LinkedHashMap<>();
        for (Player p : players.values()) results.put(p.id(), p.run().endRound(blind));

        results = bossBehavior.adjustResults(this, results, participants);   // The Shave, before anything reads scores
        bossBehavior.onBossEnd(this);                                        // The Bandwagon strips its stickers
        bossBehavior = BossBehavior.NONE;

        for (Player p : players.values()) sinModifier.onRoundSettled(p.run(), results.get(p.id()));
        lastResults = results;
        awardPoints(results);
        if (blind == Blind.BOSS)
            for (Player p : players.values())
                if (results.get(p.id()).cleared())   // Investment Tag pays $25 per copy when the next boss falls
                    while (p.run().consumePendingTag(SkipTag.INVESTMENT_TAG))
                        p.run().addMoney(SkipTag.INVESTMENT_PAYOUT);
        if (blind == Blind.BOSS)
            sinModifier.onAnteSettled(this);   // end-of-ante sin resolution (Gluttony's payout) before shops open
        phase = MatchPhase.RESULT;
    }

    /**
     * RESULT -> SHOP (or FINISHED after the final boss): opens each seat's seed-mirrored shop, unless the just
     * settled blind was the final ante's boss, in which case the match ends with no post-match shop.
     */
    public void openShopsOrFinish() {
        require(MatchPhase.RESULT, "openShopsOrFinish");
        if (blind == Blind.BOSS && ante >= anteCount) {   // final boss settled: the match is over
            phase = MatchPhase.FINISHED;
            return;
        }
        for (Player p : players.values()) p.run().openShop();   // seed-mirrored shop per seat
        phase = MatchPhase.SHOP;
    }

    /** Settles the blind and opens the shops (or finishes) in one step: the direct BLIND -> SHOP path for tests. */
    public void toShop() {
        toResult();
        openShopsOrFinish();
    }

    /** Converts one settled blind's results into standings points: the policy computes the base award, then each seat's Pride point multiplier is applied on top (a met Pride wager mints points above the round's nominal pot). */
    private void awardPoints(Map<PlayerId, BlindResult> results) {
        Map<PlayerId, Long> base = pointsPolicy.award(ante, blind, results);
        Map<PlayerId, Long> adjusted = new LinkedHashMap<>();
        for (Map.Entry<PlayerId, Long> e : base.entrySet()) {
            BigDecimal factor = getRun(e.getKey()).getSinState().pridePointMultiplier();
            long points = BigDecimal.valueOf(e.getValue()).multiply(factor)
                    .setScale(0, RoundingMode.HALF_UP).longValueExact();
            adjusted.put(e.getKey(), points);
        }
        standings.record(adjusted);
    }

    /** SHOP -> next BLIND, advancing the blind (and ante + sin when a boss is cleared), then dealing in. */
    public void nextBlind() {
        require(MatchPhase.SHOP, "nextBlind");
        for (Player p : players.values()) p.run().closeShop();
        sinModifier.onShopPhaseEnd(this);   // table-level shop-phase resolution (Pride's auction)
        switch (blind) {
            case SMALL -> blind = Blind.BIG;
            case BIG   -> blind = Blind.BOSS;
            case BOSS  -> {
                ante++;
                blind = Blind.SMALL;
                anteBoss = selectBoss();   // lock the new ante's boss up front (after any Metabole reroll armed last ante)
                activeSin = sinSelector.selectFor(ante, rng);
                for (Player p : players.values()) p.run().beginAnte();
                refreshSinForAnte();
            }
        }
        enterSelectionOrBlind();
    }

    /** Ends the match. */
    public void finish() { phase = MatchPhase.FINISHED; }

    /** Deals every seat into the current blind on its own seed. */
    private void dealBlind() {
        setupBlind();
        dealRounds();
    }

    /** Picks the blind's table-level context (boss, tag) so its target and reward are known before play. */
    private void setupBlind() {
        currentBoss = (blind == Blind.BOSS) ? anteBoss : null;       // table-level: this ante's boss, locked at ante start
        currentTag = selectTag();                                    // table-level: the same skip reward for every seat
    }

    /** Begins each seat's round on its own seed and its own stake-scaled target; boss setup runs once every round exists. */
    private void dealRounds() {
        for (Player p : players.values()) {
            p.run().beginRound(getCurrentTarget(p.id()), currentBoss);
            sinModifier.onRoundBegin(p.run());
        }
        bossBehavior = BossBehaviors.behaviorFor(currentBoss);   // NONE outside boss rounds
        bossBehavior.onBossBegin(this);                          // after every seat's round exists
    }

    /**
     * SELECTION -> BLIND: deals every seat into the blind, then resolves the seats that chose to skip (their
     * rounds settle as SKIPPED and grant the tag). A host calls this once {@link #allChosen()} is true. If every
     * seat skipped, all rounds are immediately resolved and the host's blind barrier carries on to the shop.
     */
    public void enterBlind() {
        require(MatchPhase.SELECTION, "enterBlind");
        dealRounds();
        for (Player p : players.values())
            if (Boolean.FALSE.equals(blindChoice.get(p.id()))) applySkip(p.run());
        phase = MatchPhase.BLIND;
    }

    /** The current boss's Match-level behaviour ({@link BossBehavior#NONE} outside boss rounds). */
    public BossBehavior getBossBehavior() { return bossBehavior; }

    /** The skip tag this blind carries: seeded on the table stream, keyed by ante and blind, same for all seats. */
    private SkipTag selectTag() {
        var stream = rng.streamFor(RngSource.SKIP_TAG, Rng.combine(ante, blind.ordinal()));
        return SkipTag.values()[stream.nextInt(SkipTag.values().length)];
    }

    /** The tag skipping the current blind would grant. */
    public SkipTag getCurrentTag() { return currentTag; }

    /** Skips the current blind for one seat: legal only before the seat has played or discarded. */
    public void skipBlind(PlayerId id) {
        require(MatchPhase.BLIND, "skipBlind");
        Run run = getRun(id);
        Round round = run.getRound();
        if (round == null || round.getOutcome() != RoundOutcome.IN_PROGRESS)
            throw new IllegalStateException("seat " + id + " has no round to skip");
        applySkip(run);
    }

    /** Settles one seat's round as skipped and grants the blind's tag(s). Assumes an in-progress round exists. */
    private void applySkip(Run run) {
        run.getRound().skip();
        run.getStats().recordBlindSkipped();
        for (int i = 0; i < sinModifier.tagsPerSkip(); i++) run.grantTag(currentTag);
    }

    /** Records a seat's blind-selection choice (play or skip); legal once per seat during SELECTION. */
    private void recordChoice(PlayerId id, boolean play) {
        getRun(id);   // seat validation
        if (!blindSelection || phase != MatchPhase.SELECTION)
            throw new IllegalStateException("blind selection is not open (phase " + phase + ")");
        if (blindChoice.containsKey(id))
            throw new IllegalStateException("seat " + id + " has already chosen");
        blindChoice.put(id, play);
    }

    /** SkipBlind routing: a selection choice when selection is on (SELECTION only), else the legacy in-blind skip. */
    private void chooseSkip(PlayerId id) {
        if (blindSelection) recordChoice(id, false);   // recordChoice enforces the SELECTION phase
        else skipBlind(id);
    }

    /** Whether every seat still playing has made its blind-selection choice (only meaningful during SELECTION). */
    public boolean allChosen() {
        if (phase != MatchPhase.SELECTION) return false;
        for (PlayerId id : getActiveSeats()) if (!blindChoice.containsKey(id)) return false;
        return true;
    }

    /** Whether {@code id} has already made its blind-selection choice this SELECTION. */
    public boolean hasChosenBlind(PlayerId id) { return blindChoice.containsKey(id); }

    // --- ready-to-continue (RESULT and SHOP) --------------------------------
    // The host drives the barrier, but the state lives here for the same reason blindChoice does: it is
    // per-seat match state the snapshot has to show, so a player can see their "Next Round" registered and
    // know they are waiting on someone else rather than on a dead button.

    /** Marks (or clears) a seat's ready-to-continue signal. Host-facing; the barrier check stays in MatchHost. */
    public void setReady(PlayerId id, boolean ready) {
        getRun(id);   // seat validation
        if (ready) readySeats.add(id); else readySeats.remove(id);
    }

    /** Whether {@code id} has signalled ready to leave the current RESULT/SHOP screen. */
    public boolean isReady(PlayerId id) { return readySeats.contains(id); }

    /** How many still-playing seats have signalled ready. */
    public int readyCount() {
        int n = 0;
        for (PlayerId id : getActiveSeats()) if (readySeats.contains(id)) n++;
        return n;
    }

    /** Whether every seat still playing has signalled ready — the RESULT/SHOP barrier. */
    public boolean allReady() {
        for (PlayerId id : getActiveSeats()) if (!readySeats.contains(id)) return false;
        return true;
    }

    /** Drops every ready signal; called as each barrier is crossed. */
    public void clearReady() { readySeats.clear(); }

    /** Refreshes the active sin's behaviour after the sin changes and runs its once-per-ante table setup. */
    private void refreshSinForAnte() {
        sinTableState.beginAnte();   // no sin ever reads another's leftovers
        sinModifier = sinResolver.apply(activeSin);
        sinModifier.onAnteBegin(this);
    }

    /** The sin behaviour active this ante ({@link SinModifier#NONE} before {@link #start}). */
    public SinModifier getSinModifier() { return sinModifier; }

    /** Table-level, ante-scoped state owned by the active sin (Gluttony's gauge and tallies). */
    public SinTableState getSinTableState() { return sinTableState; }

    /** The provider that resolves sin player-choices (e.g. Pride's multiplier); injected via {@link MatchConfig}. */
    public SinChoiceProvider getSinChoiceProvider() { return sinChoiceProvider; }

    /** This ante's boss, rerolled to a different one if a Metabole armed the reroll for this ante. */
    private BossBlind selectBoss() {
        BossBlind boss = bossSelector.select(ante, rng.streamFor(RngSource.BOSS_BLIND, ante), null);
        if (ante >= rerollBossFromAnte) {
            boss = bossSelector.select(ante, rng.streamFor(RngSource.BOSS_BLIND, Rng.combine(ante, 1L)), boss);
            rerollBossFromAnte = Integer.MAX_VALUE;
        }
        return boss;
    }

    // --- cross-player operations ---

    /** Envy: exchange one joker between two seats. */
    public void swapJokers(PlayerId a, int indexA, PlayerId b, int indexB) {
        require(MatchPhase.SHOP, "swapJokers");
        if (activeSin != Sin.ENVY)
            throw new IllegalStateException("joker swaps are an Envy mechanic; active sin is " + activeSin);
        if (a.equals(b)) throw new IllegalArgumentException("cannot swap a seat with itself");

        Board boardA = getRun(a).board();
        Board boardB = getRun(b).board();
        JokerCard cardA = boardCard(boardA, indexA, a);
        JokerCard cardB = boardCard(boardB, indexB, b);
        // Validate both sides before mutating either, so a rejected swap leaves both boards untouched.
        if (!boardA.canReplaceAt(indexA, cardB))
            throw new IllegalStateException("seat " + a + " has no joker slot for the incoming joker");
        if (!boardB.canReplaceAt(indexB, cardA))
            throw new IllegalStateException("seat " + b + " has no joker slot for the incoming joker");

        boardA.replaceAt(indexA, cardB);
        boardB.replaceAt(indexB, cardA);
    }

    /** The joker at {@code index} on {@code seat}'s board, with a clear error for a bad index. */
    private static JokerCard boardCard(Board board, int index, PlayerId seat) {
        if (index < 0 || index >= board.size())
            throw new IllegalArgumentException("seat " + seat + " has no joker at index " + index
                    + " (board size " + board.size() + ")");
        return board.get(index);
    }

    /** Spends {@code casterId}'s held relic at {@code relicIndex}, resolving its effect against {@code target}. */
    public void useRelic(PlayerId casterId, int relicIndex, RelicTarget target) {
        Run caster = getRun(casterId);
        castRelic(casterId, caster.getRelics().get(relicIndex), target);
        caster.consumeRelic(relicIndex);
    }

    /** Casts a relic not held in the relic area — a Myth-pack pick, used immediately (Wrath's free pack). */
    public void useRelicCard(PlayerId casterId, RelicCard relic, RelicTarget target) {
        castRelic(casterId, relic, target);
    }

    /**
     * The relic cast core. The target set follows from the relic's {@link RelicKind} and the standings, not from
     * the effect: {@code SELF}/{@code GLOBAL} resolve once untargeted, {@code OPPONENT} takes any chosen seat, and
     * {@code RIVAL}/{@code RIVALS} hit seats strictly above the caster. Each targeted seat is resolved
     * independently, so Anger counts one targeting per victim and each victim's own Aegis absorbs only its own
     * copy. A standings relic cast from the top of the table hits nobody and simply fizzles: the card is spent.
     */
    private void castRelic(PlayerId casterId, RelicCard relic, RelicTarget target) {
        if (relic.isDebuffed())   // Greed's claim: a debuffed relic is dead until the sticker is removed
            throw new IllegalStateException("a debuffed relic cannot be cast: " + relic.getSpec().getName());
        if (phase == MatchPhase.LOBBY || phase == MatchPhase.FINISHED)
            throw new IllegalStateException("relics cannot be used in phase " + phase);
        Run caster = getRun(casterId);
        RelicKind kind = relic.getSpec().getKind();

        if (kind == RelicKind.SELF || kind == RelicKind.GLOBAL) {
            relic.getSpec().getEffect().resolve(new RelicContext(this, caster, casterId, null, null, target));
            sinModifier.onConsumableUsed(caster);
            return;
        }

        for (PlayerId victim : resolveTargets(casterId, kind, target)) {
            Run victimRun = getRun(victim);
            victimRun.getStats().recordTargeted();                    // Anger: the targeting attempt counts
            if (victimRun.getAfflictions().consumeAegis()) continue;   // Aegis: absorbed for this seat only
            relic.getSpec().getEffect().resolve(
                    new RelicContext(this, caster, casterId, victimRun, victim, target));
        }
        sinModifier.onConsumableUsed(caster);   // a relic is a consumable used, even if every effect was absorbed
    }

    /** The seats a hostile relic lands on, given its kind and the caster's chosen seat (empty means it fizzles). */
    private List<PlayerId> resolveTargets(PlayerId casterId, RelicKind kind, RelicTarget target) {
        PlayerId chosen = target.opponent();
        return switch (kind) {
            case OPPONENT -> {
                if (chosen == null || chosen.equals(casterId))
                    throw new IllegalStateException("this relic needs an opponent to aim at");
                getRun(chosen);   // seat validation
                yield List.of(chosen);
            }
            case RANDOM_RIVAL -> {
                List<PlayerId> above = seatsAbove(casterId);
                if (above.isEmpty()) yield List.of();       // nobody outranks the caster: the cast fizzles
                long salt = getRun(casterId).nextSalt(RngSource.RELIC_EFFECT);   // deterministic, per-cast random victim
                yield List.of(above.get(rng.nextInt(RngSource.RELIC_EFFECT, salt, above.size())));
            }
            case RIVALS -> seatsAbove(casterId);            // the caster picks no seat; may be empty and fizzle
            default -> List.of();
        };
    }

    /** Seats with strictly more points than {@code id}; ties never count as above, so seating cannot decide targets. */
    public List<PlayerId> seatsAbove(PlayerId id) {
        long mine = standings.getPoints(id);
        List<PlayerId> above = new ArrayList<>();
        for (PlayerId other : getSeats())
            if (!other.equals(id) && standings.getPoints(other) > mine) above.add(other);
        return above;
    }

    /** Wrath: destroys the caster's own joker at {@code index} — no money, but the next joker purchase is free (grants stack and expire with the ante). */
    public void wrathDestroyJoker(PlayerId id, int index) {
        if (activeSin != Sin.WRATH)
            throw new IllegalStateException("destroying for a grant is a Wrath mechanic; active sin is " + activeSin);
        if (phase == MatchPhase.LOBBY || phase == MatchPhase.FINISHED)
            throw new IllegalStateException("jokers cannot be destroyed in phase " + phase);
        Run run = getRun(id);
        JokerCard joker = boardCard(run.board(), index, id);
        if (!run.board().destroy(joker))
            throw new IllegalStateException("an Eternal joker cannot be destroyed: " + joker.getSpec().getName());
        run.getSinState().grantWrathFreeJoker();
    }

    /**
     * The single entry point for player-submitted commands: validates the actor, resolves index payloads
     * against live state, enforces phase gates, and delegates to the model. Rejections are exceptions and
     * mutate nothing. Returns the delegate's natural result (a PlayResult, a PackOpening, a sale price, ...)
     * for the submitting client; lockstep clients derive everything else from replaying the same action.
     */
    public Object apply(Action action) {
        Run run = getRun(action.actor());
        // A departed seat may do nothing further — except leave again, which is simply a no-op.
        if (departed.contains(action.actor()) && !(action instanceof Action.PlayerLeft))
            throw new IllegalStateException("seat " + action.actor().seat() + " has left the match");
        return switch (action) {
            case Action.PlayHand a      -> requireRound(run).play(resolveHand(run, a.handIndices()));
            case Action.DiscardCards a  -> { requireRound(run).discard(resolveHand(run, a.handIndices())); yield null; }
            case Action.FinishRound a   -> { requireRound(run).finish(); yield null; }
            case Action.SkipBlind a     -> { chooseSkip(a.actor()); yield null; }
            case Action.PlayBlind a     -> { recordChoice(a.actor(), true); yield null; }

            case Action.UseConsumable a -> { run.useConsumable(a.consumableIndex(), resolveHand(run, a.targetHandIndices())); yield null; }
            case Action.UseRelic a      -> { useRelic(a.actor(), a.relicIndex(), a.target() != null ? a.target() : RelicTarget.none()); yield null; }
            case Action.SellJoker a     -> run.sellJoker(a.index());
            case Action.SellConsumable a-> run.sellConsumable(a.index());
            case Action.SellRelic a     -> run.sellRelic(a.index());
            case Action.MoveJoker a     -> { run.board().move(a.from(), a.to()); yield null; }
            case Action.MoveConsumable a -> { run.moveConsumable(a.from(), a.to()); yield null; }
            case Action.MoveRelic a     -> { run.moveRelic(a.from(), a.to()); yield null; }
            case Action.OpenPack a      -> { run.beginOpening(run.openPendingPack(a.pendingIndex())); yield run.getCurrentOpening(); }
            case Action.PickFromPack a  -> applyPick(run, a);

            case Action.BuyCard a       -> requireShop(run).buy(a.slotIndex());
            case Action.BuyPack a       -> {
                run.grantPack(requireShop(run).buyPack(a.packIndex()));   // reuse the seeded pending-pack open path
                run.beginOpening(run.openPendingPack(run.getPendingPacks().size() - 1));
                yield run.getCurrentOpening();
            }
            case Action.RedeemVoucher a -> { requireShop(run).redeemVoucher(a.voucherIndex()); yield null; }
            case Action.RerollShop a    -> { requireShop(run).reroll(); yield null; }

            case Action.PrideBid a      -> { prideBid(a.actor(), a.amount()); yield null; }
            case Action.EnvyCopy a      -> envyCopyPurchase(a.actor(), a.logIndex());
            case Action.EnvySwap a      -> { swapJokers(a.actor(), a.myIndex(), a.other(), a.theirIndex()); yield null; }
            case Action.WrathDestroy a  -> { wrathDestroyJoker(a.actor(), a.jokerIndex()); yield null; }
            case Action.GluttonyEat a   -> gluttonyEatJoker(a.actor(), a.jokerIndex());
            case Action.SubmitSinChoice a -> { recordSinChoice(a.actor(), a.optionIndex()); yield null; }
            case Action.ReadyForNext a -> throw new IllegalStateException("readiness is table-level; submit it to a MatchHost");
            case Action.NotReady a     -> throw new IllegalStateException("readiness is table-level; submit it to a MatchHost");
            case Action.PlayerLeft a   -> { markDeparted(a.actor()); yield null; }
        };
    }

    /** Records {@code id}'s answer for the next sin choice; requires an action-driven choice provider. */
    public void recordSinChoice(PlayerId id, int option) {
        getRun(id);
        if (!(sinChoiceProvider instanceof RecordedChoiceProvider recorded))
            throw new IllegalStateException("this match's sin choices are not action-driven");
        recorded.record(id, option);
    }

    /** The actor's live round, only during the BLIND phase. */
    private Round requireRound(Run run) {
        if (phase != MatchPhase.BLIND || run.getRound() == null)
            throw new IllegalStateException("no round in progress (phase " + phase + ")");
        return run.getRound();
    }

    /** The actor's open shop, only during the SHOP phase. */
    private Shop requireShop(Run run) {
        if (phase != MatchPhase.SHOP || run.getShop() == null)
            throw new IllegalStateException("no shop open (phase " + phase + ")");
        return run.getShop();
    }

    /** Resolves distinct hand indices into the round's live cards; any bad index rejects the whole action. */
    private List<DeckCard> resolveHand(Run run, List<Integer> indices) {
        if (indices == null || indices.isEmpty()) return List.of();
        if (indices.stream().distinct().count() != indices.size())
            throw new IllegalArgumentException("duplicate hand indices: " + indices);
        List<DeckCard> hand = requireRound(run).getHand();
        List<DeckCard> cards = new java.util.ArrayList<>(indices.size());
        for (int i : indices) {
            if (i < 0 || i >= hand.size())
                throw new IllegalArgumentException("hand index " + i + " out of range (hand size " + hand.size() + ")");
            cards.add(hand.get(i));
        }
        return cards;
    }

    /**
     * Picks from the actor's current opening, routing by kind: playing cards join the deck, jokers and
     * consumables are stored (room-checked before the pick is spent), relics cast immediately with the
     * action's target. Storing pack consumables instead of use-immediately is a deliberate interim until
     * targeted use-from-pack has a design. The opening clears itself when its last pick is spent.
     */
    private Object applyPick(Run run, Action.PickFromPack a) {
        PackOpening opening = run.getCurrentOpening();
        if (opening == null) throw new IllegalStateException("no pack is open");
        Card option = opening.getOptions().get(a.optionIndex());
        if (option == null) throw new IllegalStateException("option " + a.optionIndex() + " was already picked");
        if ((option instanceof JokerCard || option instanceof ConsumableCard) && !run.canAcquire(option))
            throw new IllegalStateException("no inventory room for " + option);
        Card picked = opening.pick(a.optionIndex());
        if (opening.getPicksLeft() == 0) run.clearOpening();
        if (picked instanceof RelicCard relic)
            useRelicCard(run.getPlayerId(), relic, a.relicTarget() != null ? a.relicTarget() : RelicTarget.none());
        else if (picked instanceof DeckCard card) run.addCardToDeck(card);
        else run.acquire(picked);
        return picked;
    }

    /** Pride: sets or raises {@code id}'s standing bid on this shop phase's legendary; resolved at phase end. */
    public void prideBid(PlayerId id, int amount) {
        if (activeSin != Sin.PRIDE)
            throw new IllegalStateException("bidding is a Pride mechanic; active sin is " + activeSin);
        if (phase != MatchPhase.SHOP)
            throw new IllegalStateException("bids can only be placed during the shop; phase is " + phase);
        if (amount < 1) throw new IllegalArgumentException("a bid must be at least $1");
        Run run = getRun(id);
        if (run.getMoney() - amount < run.minBalance())
            throw new IllegalStateException("cannot bid " + amount + " with " + run.getMoney());
        sinTableState.recordPrideBid(id, amount);
    }

    /**
     * Envy: copies entry {@code logIndex} of this phase's purchase log for {@code copierId}, at twice the price
     * the original buyer paid. Packs arrive unopened as a pending pack; everything else routes through normal
     * inventory acquisition. Own purchases cannot be copied, and a copy never enters the log itself.
     */
    public Card envyCopyPurchase(PlayerId copierId, int logIndex) {
        if (activeSin != Sin.ENVY)
            throw new IllegalStateException("copying purchases is an Envy mechanic; active sin is " + activeSin);
        if (phase != MatchPhase.SHOP)
            throw new IllegalStateException("purchases can only be copied during the shop; phase is " + phase);
        List<SinTableState.EnvyPurchase> log = sinTableState.getEnvyLog();
        if (logIndex < 0 || logIndex >= log.size())
            throw new IllegalArgumentException("no purchase log entry " + logIndex);
        SinTableState.EnvyPurchase entry = log.get(logIndex);
        if (entry.buyer().equals(copierId))
            throw new IllegalStateException("cannot copy your own purchase");
        Run copier = getRun(copierId);
        int cost = entry.pricePaid() * 2;
        if (copier.getMoney() - cost < copier.minBalance())
            throw new IllegalStateException("cannot afford the copy at " + cost);
        Card copy = copyOf(entry.item());
        if (copy instanceof BoosterPack pack) {
            copier.spend(cost);
            copier.grantPack(pack);
        } else {
            if (!copier.canAcquire(copy)) throw new IllegalStateException("no inventory room for " + copy);
            copier.spend(cost);
            copier.acquire(copy);
        }
        copier.getStats().recordPurchase();
        return copy;
    }

    /** A fresh instance of the same item: spec (and edition, for jokers) preserved, per-card state not. */
    private static Card copyOf(Card item) {
        if (item instanceof JokerCard j) {
            JokerCard copy = new JokerCard(j.getSpec(), j.getShopValue());
            if (j.getEdition() != null) copy.apply(j.getEdition());
            return copy;
        }
        if (item instanceof model.items.consumables.ConsumableCard c)
            return new model.items.consumables.ConsumableCard(c.getSpec());
        if (item instanceof RelicCard r) return new RelicCard(r.getSpec());
        if (item instanceof BoosterPack p) return new BoosterPack(p.kind(), p.size());
        if (item instanceof model.items.DeckCard d) return new model.items.DeckCard(d.getRank(), d.getSuit());
        throw new IllegalStateException("uncopyable item: " + item);
    }

    /** Gluttony: eats the joker at {@code index} on {@code id}'s board — destroys it for its sell value plus the ${@value model.game.sins.GluttonyModifier#EAT_BONUS} eat bonus, counting as a consumable use for the communal gauge. */
    public int gluttonyEatJoker(PlayerId id, int index) {
        if (activeSin != Sin.GLUTTONY)
            throw new IllegalStateException("eating jokers is a Gluttony mechanic; active sin is " + activeSin);
        if (phase == MatchPhase.LOBBY || phase == MatchPhase.FINISHED)
            throw new IllegalStateException("jokers cannot be eaten in phase " + phase);
        Run run = getRun(id);
        JokerCard joker = boardCard(run.board(), index, id);
        if (!run.board().destroy(joker))
            throw new IllegalStateException("an Eternal joker cannot be eaten: " + joker.getSpec().getName());
        int gained = joker.getSellValue() + model.game.sins.GluttonyModifier.EAT_BONUS;
        run.addMoney(gained);
        sinModifier.onConsumableUsed(run);
        return gained;
    }

    /** Records {@code spec} as the table's most recently used consumable (read by Mimesis). */
    public void recordConsumableUsed(ConsumableSpec spec) { lastConsumableUsed = spec; }

    /** The last consumable any seat used, or {@code null} if none yet (Mimesis copies it). */
    public ConsumableSpec getLastConsumableUsed() { return lastConsumableUsed; }

    /** Metabole: arms a reroll of the shared boss blind for the next ante. */
    public void rerollNextBoss() { rerollBossFromAnte = Math.min(rerollBossFromAnte, ante + 1); }

    private void require(MatchPhase expected, String op) {
        if (phase != expected)
            throw new IllegalStateException(op + " requires phase " + expected + " but was " + phase);
    }

    /** Snapshot of seats in order, including seats whose players have left. */
    public List<PlayerId> getSeats() {
        return new ArrayList<>(players.keySet());
    }

    // --- departures --------------------------------------------------------

    /** Whether {@code id}'s player has left the match. A departed seat keeps its points but stops playing. */
    public boolean hasDeparted(PlayerId id) { return departed.contains(id); }

    /** The seats still playing, in order — the set every barrier must be measured against, not {@link #getSeats()}. */
    public List<PlayerId> getActiveSeats() {
        List<PlayerId> out = new ArrayList<>();
        for (PlayerId id : players.keySet()) if (!departed.contains(id)) out.add(id);
        return out;
    }

    /**
     * Records that {@code id} has left. The seat forfeits any live round, is counted as having made its blind
     * choice, and is excluded from every barrier from here on, so the remaining players never wait on it. Its
     * standings entry is left alone: the points it earned while playing are real and still rank it.
     *
     * <p>When this drops the table below two players the match ends immediately — a solo table has nothing left
     * to compete over, and whoever remains is the winner by the standings as they stand.
     */
    public void markDeparted(PlayerId id) {
        getRun(id);   // seat validation
        if (!departed.add(id)) return;   // already gone; a duplicate is a no-op, not an error

        Round round = getRun(id).getRound();
        if (round != null) round.abandon();
        blindChoice.remove(id);   // it no longer counts either way; allChosen() ignores departed seats

        if (phase != MatchPhase.LOBBY && phase != MatchPhase.FINISHED && getActiveSeats().size() < MIN_ACTIVE_SEATS)
            phase = MatchPhase.FINISHED;
    }
}