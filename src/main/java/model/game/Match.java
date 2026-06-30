package model.game;

import model.cards.Decks;
import model.cards.consumables.ConsumableSpec;
import model.cards.jokers.JokerCard;
import model.cards.relics.RelicCard;
import model.cards.relics.RelicContext;
import model.cards.relics.RelicTarget;
import model.game.player.BlindResult;
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final Function<Sin, SinModifier> sinResolver;   // Sin -> behaviour (injectable for tests)
    private final SinChoiceProvider sinChoiceProvider;      // resolves sin player-choices (Pride's multiplier, ...)

    private MatchPhase phase = MatchPhase.LOBBY;
    private int ante = 0;                         // 0 until started
    private Blind blind = Blind.SMALL;
    private Sin activeSin;                         // null until started
    private SinModifier sinModifier = SinModifier.NONE;   // behaviour for activeSin; refreshed when the sin changes
    private Map<PlayerId, BlindResult> lastResults = Map.of();   // most recent blind's outcomes
    private BossBlind currentBoss;                 // the boss for the current BOSS blind, else null
    private ConsumableSpec lastConsumableUsed;     // last consumable any seat used (Mimesis)
    private int rerollBossFromAnte = Integer.MAX_VALUE;   // Metabole: reroll the table boss from this ante onward

    private Match(long seed, MatchConfig config) {
        this.seed = seed;
        this.rng = new DeterministicRng(seed);
        this.sinSelector = config.sinSelector();
        this.sinResolver = config.sinResolver();
        this.sinChoiceProvider = config.sinChoiceProvider();
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
            run.getDeck().addAll(Decks.standard());
            run.joinMatch(match, id);
            match.players.put(id, new Player(id, name, run));
        }
        return match;
    }

    // --- shared state accessors ---

    public long getSeed()           { return seed; }
    public Rng getRng()             { return rng; }          // table-level draws (shared shop, boss blind, sin)
    public MatchPhase getPhase()    { return phase; }
    public int getAnte()            { return ante; }
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

    /** BLIND -> SHOP: settles every seat's finished round and records the results. */
    public void toShop() {
        require(MatchPhase.BLIND, "toShop");
        for (Player p : players.values()) {                         // barrier: everyone must be done
            Round round = p.run().getRound();
            if (round == null || round.getOutcome() == RoundOutcome.IN_PROGRESS)
                throw new IllegalStateException("seat " + p.id() + " has not finished the blind");
        }
        Map<PlayerId, BlindResult> results = new LinkedHashMap<>();
        for (Player p : players.values()) {
            BlindResult result = p.run().endRound(blind);
            sinModifier.onRoundSettled(p.run(), result);
            results.put(p.id(), result);
        }
        lastResults = results;
        for (Player p : players.values()) p.run().openShop();   // seed-mirrored shop per seat
        phase = MatchPhase.SHOP;
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
    }

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
        BossBlind boss = BossBlind.select(rng.streamFor(RngSource.BOSS_BLIND, ante), ante);
        if (ante >= rerollBossFromAnte) {
            boss = BossBlind.select(rng.streamFor(RngSource.BOSS_BLIND, Rng.combine(ante, 1L)), ante, boss);
            rerollBossFromAnte = Integer.MAX_VALUE;
        }
        return boss;
    }

    // --- cross-player operations ---

    /** Envy: exchange one joker between two seats. */
    public void swapJokers(PlayerId a, int indexA, PlayerId b, int indexB) {
        List<JokerCard> ja = getRun(a).getJokers();
        List<JokerCard> jb = getRun(b).getJokers();
        JokerCard cardA = ja.get(indexA);
        JokerCard cardB = jb.get(indexB);
        ja.set(indexA, cardB);
        jb.set(indexB, cardA);
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

        caster.getRelics().remove(relicIndex);
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