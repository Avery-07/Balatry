package model.game;

import model.cards.Decks;
import model.cards.jokers.JokerCard;
import model.game.player.BlindResult;
import model.game.player.Player;
import model.game.player.PlayerId;
import model.game.player.Round;
import model.game.player.RoundOutcome;
import model.game.rng.DeterministicRng;
import model.game.rng.Rng;
import model.game.player.Run;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    private MatchPhase phase = MatchPhase.LOBBY;
    private int ante = 0;                         // 0 until started
    private Blind blind = Blind.SMALL;
    private Sin activeSin;                         // null until started
    private Map<PlayerId, BlindResult> lastResults = Map.of();   // most recent blind's outcomes

    private Match(long seed, SinSelector sinSelector) {
        this.seed = seed;
        this.rng = new DeterministicRng(seed);
        this.sinSelector = sinSelector;
        this.players = new LinkedHashMap<>();
    }

    /** Creates a seated match (in {@link MatchPhase#LOBBY}) with the default sin policy. */
    public static Match create(long seed, List<String> playerNames) {
        return create(seed, playerNames, SinSelector.SEEDED_UNIFORM);
    }

    /** Creates a seated match; each player's {@link Run} is built from {@code seed}. Seats follow name order. */
    public static Match create(long seed, List<String> playerNames, SinSelector sinSelector) {
        if (playerNames == null || playerNames.size() < 2 || playerNames.size() > 4)
            throw new IllegalArgumentException("a match needs 2-4 players, got "
                    + (playerNames == null ? 0 : playerNames.size()));

        Match match = new Match(seed, sinSelector);
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

    /** Chips required to clear the current blind. */
    public long getCurrentTarget()  { return BlindTargets.target(ante, blind); }

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
        for (Player p : players.values()) results.put(p.id(), p.run().endRound(blind));
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
            }
        }
        phase = MatchPhase.BLIND;
        dealBlind();
    }

    /** Ends the match. */
    public void finish() { phase = MatchPhase.FINISHED; }

    /** Deals every seat into the current blind on its own seed. */
    private void dealBlind() {
        long target = getCurrentTarget();
        for (Player p : players.values()) p.run().beginRound(target);
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

    // Greed's shared shop will hang here as a single table-level Shop instance. Seam until the Shop type exists.

    private void require(MatchPhase expected, String op) {
        if (phase != expected)
            throw new IllegalStateException(op + " requires phase " + expected + " but was " + phase);
    }

    /** Snapshot of seats in order. */
    public List<PlayerId> getSeats() {
        return new ArrayList<>(players.keySet());
    }
}