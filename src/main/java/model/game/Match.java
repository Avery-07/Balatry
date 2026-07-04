package model.game;

import model.cards.Decks;
import model.cards.consumables.ConsumableSpec;
import model.cards.jokers.JokerCard;
import model.cards.relics.RelicCard;
import model.cards.relics.RelicContext;
import model.cards.relics.RelicTarget;
import model.game.bosses.BossBehavior;
import model.game.bosses.BossBehaviors;
import model.game.player.BlindResult;
import model.game.player.Board;
import model.game.player.Player;
import model.game.player.PlayerId;
import model.game.player.Round;
import model.game.player.RoundOutcome;
import model.game.rng.DeterministicRng;
import model.game.rng.Rng;
import model.game.rng.RngSource;
import model.game.player.Run;
import model.game.shop.Shop;
import model.game.sins.SinChoiceProvider;
import model.game.sins.SinModifier;

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

/**
 * Aggregate root for one competitive game. Owns the shared, authoritative state
 * (seed, roster, ante/blind/phase progression, active sin) and the cross-player
 * operations; each {@link Run} owns one player's private state beneath it.
 */
public final class Match {

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

    private MatchPhase phase = MatchPhase.LOBBY;
    private int ante = 0;                         // 0 until started
    private Blind blind = Blind.SMALL;
    private Sin activeSin;                         // null until started
    private SinModifier sinModifier = SinModifier.NONE;   // behaviour for activeSin; refreshed when the sin changes
    private Map<PlayerId, BlindResult> lastResults = Map.of();   // most recent blind's outcomes
    private BossBlind currentBoss;                 // the boss for the current BOSS blind, else null
    private BossBehavior bossBehavior = BossBehavior.NONE;   // the boss's Match-level behaviour; NONE outside boss rounds
    private ConsumableSpec lastConsumableUsed;     // last consumable any seat used (Mimesis)
    private int rerollBossFromAnte = Integer.MAX_VALUE;   // Metabole: reroll the table boss from this ante onward

    private Match(long seed, MatchConfig config) {
        this.seed = seed;
        this.rng = new DeterministicRng(seed);
        this.sinSelector = config.sinSelector();
        this.bossSelector = config.bossSelector();
        this.sinResolver = config.sinResolver();
        this.sinChoiceProvider = config.sinChoiceProvider();
        this.pointsPolicy = config.pointsPolicy();
        this.anteCount = config.anteCount();
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

    /** Creates a seated match; each player's {@link Run} is built from {@code seed}. Seats follow name order. */
    public static Match create(long seed, List<String> playerNames, MatchConfig config) {
        if (playerNames == null || playerNames.size() < 2 || playerNames.size() > 4)
            throw new IllegalArgumentException("a match needs 2-4 players, got "
                    + (playerNames == null ? 0 : playerNames.size()));

        Match match = new Match(seed, config);
        int seat = 0;
        for (String name : playerNames) {
            PlayerId id = new PlayerId(seat++);
            Run run = new Run(seed);          // same seed -> identical luck per action
            run.resetDeck(Decks.standard());
            run.joinMatch(match, id);
            match.players.put(id, new Player(id, name, run));
        }
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

    /** Chips required to clear the current blind (boss target multipliers applied). */
    public long getCurrentTarget() {
        long base = BlindTargets.target(ante, blind);
        return (blind == Blind.BOSS && currentBoss != null) ? base * currentBoss.targetMultiplier() : base;
    }

    /** The boss for the current BOSS blind, or {@code null} on small/big blinds. */
    public BossBlind getCurrentBoss() { return currentBoss; }

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
        activeSin = sinSelector.selectFor(ante, rng);
        phase = MatchPhase.BLIND;
        for (Player p : players.values()) p.run().beginAnte();
        refreshSinForAnte();
        dealBlind();
    }

    /**
     * BLIND -> SHOP: settles every seat's finished round and records the results. After the final ante's boss
     * (ante == anteCount) the match instead transitions straight to FINISHED — the results are still settled and
     * recorded, but no post-match shop opens.
     */
    public void toShop() {
        require(MatchPhase.BLIND, "toShop");
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
        if (blind == Blind.BOSS && ante >= anteCount) {   // final boss settled: the match is over
            phase = MatchPhase.FINISHED;
            return;
        }
        for (Player p : players.values()) p.run().openShop();   // seed-mirrored shop per seat
        phase = MatchPhase.SHOP;
    }

    /**
     * Converts one settled blind's results into standings points: the policy computes the base award, then each
     * seat's Pride point multiplier is applied on top (a met Pride wager mints points above the round's nominal
     * pot). Runs after {@code onRoundSettled} so sin state (Pride's threshold) is resolved first.
     */
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
        switch (blind) {
            case SMALL -> blind = Blind.BIG;
            case BIG   -> blind = Blind.BOSS;
            case BOSS  -> {
                ante++;
                blind = Blind.SMALL;
                activeSin = sinSelector.selectFor(ante, rng);
                for (Player p : players.values()) p.run().beginAnte();
                refreshSinForAnte();
            }
        }
        phase = MatchPhase.BLIND;
        dealBlind();
    }

    /** Ends the match. */
    public void finish() { phase = MatchPhase.FINISHED; }

    /** Deals every seat into the current blind on its own seed. */
    private void dealBlind() {
        currentBoss = (blind == Blind.BOSS) ? selectBoss() : null;   // table-level: same boss for every seat
        long target = getCurrentTarget();
        for (Player p : players.values()) {
            p.run().beginRound(target, currentBoss);
            sinModifier.onRoundBegin(p.run());
        }
        bossBehavior = BossBehaviors.behaviorFor(currentBoss);   // NONE outside boss rounds
        bossBehavior.onBossBegin(this);                          // after every seat's round exists
    }

    /** The current boss's Match-level behaviour ({@link BossBehavior#NONE} outside boss rounds). */
    public BossBehavior getBossBehavior() { return bossBehavior; }

    /** Refreshes the active sin's behaviour after the sin changes and runs its once-per-ante table setup. */
    private void refreshSinForAnte() {
        sinModifier = sinResolver.apply(activeSin);
        sinModifier.onAnteBegin(this);
    }

    /** The sin behaviour active this ante ({@link SinModifier#NONE} before {@link #start}). */
    public SinModifier getSinModifier() { return sinModifier; }

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

    /**
     * Envy: exchange one joker between two seats. Only legal in the SHOP phase (the between-rounds window where
     * Envy's swap happens) while Envy is the active sin, and only if neither seat ends up over its joker slots
     * (a swap is 1:1, but exchanging a NEGATIVE joker for a slot-consuming one is asymmetric).
     *
     * <p>Open design questions, deliberately not decided here: the consent mechanic, and whether Eternal or
     * Pinned stickers should block a swap (a swap is neither a sale nor a destruction, so today they do not).
     */
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

    /**
     * Spends {@code casterId}'s held relic at {@code relicIndex}, resolving its effect against {@code target}.
     * A hostile effect (one aimed at another seat) is recorded for Anger and, if that seat has an armed Aegis,
     * negated by consuming the shield. The relic is removed from the caster's relic area either way.
     */
    public void useRelic(PlayerId casterId, int relicIndex, RelicTarget target) {
        if (phase == MatchPhase.LOBBY || phase == MatchPhase.FINISHED)
            throw new IllegalStateException("relics cannot be used in phase " + phase);
        Run caster = getRun(casterId);
        RelicCard relic = caster.getRelics().get(relicIndex);

        PlayerId targetId = target.opponent();
        Run targetRun = (targetId == null) ? null : getRun(targetId);
        boolean hostile = targetRun != null && !targetId.equals(casterId);

        boolean negated = false;
        if (hostile) {
            targetRun.getStats().recordTargeted();              // Anger: the targeting attempt counts
            negated = targetRun.getAfflictions().consumeAegis(); // Aegis: absorb the next hostile effect this ante
        }
        if (!negated)
            relic.getSpec().getEffect().resolve(
                    new RelicContext(this, caster, casterId, targetRun, targetId, target));

        caster.consumeRelic(relicIndex);
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

    /** Snapshot of seats in order. */
    public List<PlayerId> getSeats() {
        return new ArrayList<>(players.keySet());
    }
}