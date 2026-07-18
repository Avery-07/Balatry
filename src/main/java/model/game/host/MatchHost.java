package model.game.host;

import model.game.Match;
import model.game.MatchConfig;
import model.game.MatchPhase;
import model.game.actions.Action;
import model.game.actions.RecordedChoiceProvider;
import model.game.player.PlayerId;
import model.game.player.RoundOutcome;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The deterministic sequencer a transport wraps: owns a {@link Match}, serializes submitted {@link Action}s
 * (synchronized — arrival order at this object <em>is</em> canonical order), keeps the accepted-action log that
 * doubles as the replay format, and drives the barrier transitions the model deliberately leaves to a host.
 *
 * <p>Barriers: the blind phase advances by itself the moment every seat's round is resolved (the model already
 * encodes blind-readiness as a round outcome); the shop phase advances when every seat has declared
 * {@link Action.ReadyForNext}. Submitting a shop action revokes the actor's readiness (acting implies not
 * done), except {@link Action.SubmitSinChoice}, which prepares the next round rather than continuing this shop.
 * Rejected actions throw before logging and mutate nothing, so a log replayed onto a same-seed host reproduces
 * the match exactly.
 */
public final class MatchHost {

    private final Match match;
    private final List<Action> log = new ArrayList<>();
    private final Set<PlayerId> readySeats = new HashSet<>();   // ready-to-continue; used for both RESULT and SHOP

    public MatchHost(Match match) { this.match = match; }

    /** A host over a fresh match with the networked defaults (action-driven sin choices). */
    public static MatchHost create(long seed, List<String> playerNames) {
        return new MatchHost(Match.create(seed, playerNames, networkedConfig()));
    }

    /** The standard config for action-driven play: defaults plus a {@link RecordedChoiceProvider}. */
    public static MatchConfig networkedConfig() {
        return MatchConfig.defaults().withSinChoiceProvider(new RecordedChoiceProvider()).withBlindSelection(true);
    }

    /** Rebuilds a match by replaying an accepted-action log onto a fresh same-seed host. */
    public static MatchHost replay(long seed, List<String> playerNames, List<Action> log) {
        MatchHost host = create(seed, playerNames);
        host.start();
        for (Action action : log) host.submit(action);
        return host;
    }

    public Match getMatch() { return match; }

    /** The accepted actions so far, in canonical order; the list index is the sequence number. */
    public synchronized List<Action> getLog() { return List.copyOf(log); }

    public synchronized void start() { match.start(); }

    /**
     * Applies one player action: readiness signals are handled here, everything else goes through
     * {@code Match#apply}. On acceptance the action is logged and any barrier that became passable is crossed
     * before returning; a rejection propagates as an exception and leaves the log and match untouched.
     */
    public synchronized Object submit(Action action) {
        Object result;
        switch (action) {
            case Action.ReadyForNext a -> { requireReadyPhase(a.actor()); readySeats.add(a.actor()); result = null; }
            case Action.NotReady a     -> { requireReadyPhase(a.actor()); readySeats.remove(a.actor()); result = null; }
            default -> {
                result = match.apply(action);
                if (match.getPhase() == MatchPhase.SHOP && !(action instanceof Action.SubmitSinChoice))
                    readySeats.remove(action.actor());   // acting implies not done
            }
        }
        log.add(action);
        advanceIfBarrierMet();
        return result;
    }

    private void requireReadyPhase(PlayerId actor) {
        match.getRun(actor);   // seat validation
        MatchPhase phase = match.getPhase();
        if (phase != MatchPhase.RESULT && phase != MatchPhase.SHOP)
            throw new IllegalStateException("readiness applies to the result or shop phase; phase is " + phase);
    }

    /** Crosses every barrier that just became passable: all seats chose, all rounds resolved, all done shopping. */
    private void advanceIfBarrierMet() {
        boolean advanced = true;
        while (advanced) {
            advanced = false;
            switch (match.getPhase()) {
                case SELECTION -> { if (match.allChosen())                        { match.enterBlind();          advanced = true; } }
                case BLIND     -> { if (allRoundsResolved())                      { match.toResult(); readySeats.clear(); advanced = true; } }
                case RESULT    -> { if (readySeats.containsAll(match.getSeats()))  { match.openShopsOrFinish(); readySeats.clear(); advanced = true; } }
                case SHOP      -> { if (readySeats.containsAll(match.getSeats()))  { match.nextBlind(); readySeats.clear(); advanced = true; } }
                default -> { }
            }
        }
    }

    private boolean allRoundsResolved() {
        for (PlayerId id : match.getSeats()) {
            var round = match.getRun(id).getRound();
            if (round == null || round.getOutcome() == RoundOutcome.IN_PROGRESS) return false;
        }
        return true;
    }
}
