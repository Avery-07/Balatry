package model.game;

import model.cards.jokers.JokerCard;
import model.game.player.Player;
import model.game.player.PlayerId;
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

    public Collection<Player> getPlayers() { return List.copyOf(players.values()); }

    /** The player at the given seat, or throws if there is none. */
    public Player getPlayer(PlayerId id) {
        Player p = players.get(id);
        if (p == null) throw new IllegalArgumentException("no such player: " + id);
        return p;
    }

    public Run getRun(PlayerId id) { return getPlayer(id).run(); }

    // --- synchronized progression ---

    /** LOBBY -> first blind of ante 1. Selects the opening sin. */
    public void start() {
        require(MatchPhase.LOBBY, "start");
        ante = 1;
        blind = Blind.SMALL;
        activeSin = sinSelector.selectFor(ante, rng);
        phase = MatchPhase.BLIND;
    }

    /** BLIND -> SHOP, once the current blind is resolved for all players. */
    public void toShop() {
        require(MatchPhase.BLIND, "toShop");
        phase = MatchPhase.SHOP;
    }

    /** SHOP -> next BLIND, advancing the blind (and ante + sin when a boss is cleared). */
    public void nextBlind() {
        require(MatchPhase.SHOP, "nextBlind");
        switch (blind) {
            case SMALL -> blind = Blind.BIG;
            case BIG   -> blind = Blind.BOSS;
            case BOSS  -> {
                ante++;
                blind = Blind.SMALL;
                activeSin = sinSelector.selectFor(ante, rng);
            }
        }
        phase = MatchPhase.BLIND;
    }

    /** Ends the match. */
    public void finish() { phase = MatchPhase.FINISHED; }

    // --- cross-player operations ---

    /** Envy: exchange one joker between two seats. */
    public void swapJokers(PlayerId a, int indexA, PlayerId b, int indexB) {
        Run runA = getRun(a);
        Run runB = getRun(b);
        JokerCard cardA = runA.getJokers().get(indexA);
        JokerCard cardB = runB.getJokers().get(indexB);
        runA.replaceJoker(indexA, cardB);
        runB.replaceJoker(indexB, cardA);
    }

    private void require(MatchPhase expected, String op) {
        if (phase != expected)
            throw new IllegalStateException(op + " requires phase " + expected + " but was " + phase);
    }

    /** Snapshot of seats in order. */
    public List<PlayerId> getSeats() {
        return new ArrayList<>(players.keySet());
    }
}