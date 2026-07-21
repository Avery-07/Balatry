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
    // Readiness itself lives on the Match (the snapshot has to show it); the host still owns crossing the barrier.

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
            case Action.ReadyForNext a -> { requireReadyPhase(a.actor()); match.setReady(a.actor(), true);  result = null; }
            case Action.NotReady a     -> { requireReadyPhase(a.actor()); match.setReady(a.actor(), false); result = null; }
            default -> {
                result = match.apply(action);
                if (match.getPhase() == MatchPhase.SHOP && !(action instanceof Action.SubmitSinChoice))
                    match.setReady(action.actor(), false);   // acting implies not done
            }
        }
        log.add(action);
        advanceIfBarrierMet();
        return result;
    }

    private void requireReadyPhase(PlayerId actor) {
        match.getRun(actor);   // seat validation
        if (match.hasDeparted(actor))
            throw new IllegalStateException("seat " + actor.seat() + " has left the match");
        MatchPhase phase = match.getPhase();
        if (phase != MatchPhase.RESULT && phase != MatchPhase.SHOP)
            throw new IllegalStateException("readiness applies to the result or shop phase; phase is " + phase);
    }

    /**
     * Crosses every barrier that just became passable: all seats chose, all rounds resolved, all done shopping.
     * Every barrier is measured against the seats <em>still playing</em> — a departed seat must never hold the
     * table up, which is what makes a mid-match disconnect survivable rather than a deadlock.
     */
    private void advanceIfBarrierMet() {
        boolean advanced = true;
        while (advanced) {
            advanced = false;
            List<PlayerId> active = match.getActiveSeats();
            switch (match.getPhase()) {
                case SELECTION -> { if (match.allChosen())          { match.enterBlind();                             advanced = true; } }
                case BLIND     -> { if (allRoundsResolved(active))  { match.toResult();         match.clearReady();   advanced = true; } }
                case RESULT    -> { if (match.allReady())           { match.openShopsOrFinish(); match.clearReady();  advanced = true; } }
                case SHOP      -> { if (match.allReady())           { match.nextBlind();        match.clearReady();   advanced = true; } }
                default -> { }
            }
        }
    }

    private boolean allRoundsResolved(List<PlayerId> active) {
        for (PlayerId id : active) {
            var round = match.getRun(id).getRound();
            if (round == null || round.getOutcome() == RoundOutcome.IN_PROGRESS) return false;
        }
        return true;
    }
}
