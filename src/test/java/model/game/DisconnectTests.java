package model.game;

import model.items.DeckType;
import model.game.actions.Action;
import model.game.host.MatchHost;
import model.game.net.MatchClient;
import model.game.net.MatchServer;
import model.game.player.PlayerId;
import model.game.player.RoundOutcome;
import model.game.player.SeatConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Run-as-main harness for mid-match departures. The property that matters is that a drop never strands the
 * remaining players: every barrier is measured against the seats still playing, so a table of three keeps going
 * when one leaves, and a table of two ends rather than waiting forever on a seat that will never act again.
 *
 * <p>Departure is modelled as a logged {@link Action.PlayerLeft} rather than out-of-band transport state,
 * precisely so it replays identically everywhere — the socket-level test at the bottom asserts exactly that.
 */
public final class DisconnectTests {

    private static int failures = 0;

    public static void main(String[] args) throws Exception {
        departureFreesTheBarrier();
        departureForfeitsTheRound();
        departedSeatsCannotAct();
        lastPlayerStandingEndsTheMatch();
        leavingForfeitsTheWin();
        leaverSinksBelowActiveSeats();
        departureIsDeterministic();
        socketDropReachesTheOtherClient();

        System.out.println(failures == 0 ? "\nALL PASS" : "\n" + failures + " FAILURE(S)");
        if (failures != 0) System.exit(1);
    }

    /** The core guarantee: a seat that left stops holding up the selection barrier. */
    private static void departureFreesTheBarrier() {
        MatchHost host = threeSeatHost();
        List<PlayerId> seats = host.getMatch().getSeats();

        check("the match opens in selection", host.getMatch().getPhase() == MatchPhase.SELECTION);
        host.submit(new Action.PlayBlind(seats.get(0)));
        host.submit(new Action.PlayBlind(seats.get(1)));
        check("two of three chosen leaves us waiting", host.getMatch().getPhase() == MatchPhase.SELECTION);

        host.submit(new Action.PlayerLeft(seats.get(2)));
        check("the departure crossed the barrier", host.getMatch().getPhase() == MatchPhase.BLIND);
        check("the seat is marked departed", host.getMatch().hasDeparted(seats.get(2)));
        checkInt("two seats are still playing", host.getMatch().getActiveSeats().size(), 2);
    }

    /** A seat that leaves mid-blind forfeits: its round resolves as a loss, so settlement is not blocked. */
    private static void departureForfeitsTheRound() {
        MatchHost host = threeSeatHost();
        List<PlayerId> seats = host.getMatch().getSeats();
        for (PlayerId id : seats) host.submit(new Action.PlayBlind(id));
        check("everyone is in the blind", host.getMatch().getPhase() == MatchPhase.BLIND);

        PlayerId quitter = seats.get(2);
        check("the quitter's round is live",
                host.getMatch().getRun(quitter).getRound().getOutcome() == RoundOutcome.IN_PROGRESS);
        host.submit(new Action.PlayerLeft(quitter));
        check("the abandoned round resolved as a loss",
                host.getMatch().getRun(quitter).getRound().getOutcome() == RoundOutcome.LOST);

        // The remaining two finishing is now enough to settle the blind.
        host.submit(new Action.FinishRound(seats.get(0)));
        check("one finisher is not enough", host.getMatch().getPhase() == MatchPhase.BLIND);
        host.submit(new Action.FinishRound(seats.get(1)));
        check("the blind settled without the quitter", host.getMatch().getPhase() == MatchPhase.RESULT);
    }

    /** Once gone, a seat is done: no play, no readiness, no shopping. Leaving twice is a harmless no-op. */
    private static void departedSeatsCannotAct() {
        MatchHost host = threeSeatHost();
        List<PlayerId> seats = host.getMatch().getSeats();
        PlayerId gone = seats.get(2);
        host.submit(new Action.PlayerLeft(gone));

        checkThrows("a departed seat cannot choose a blind", () -> host.submit(new Action.PlayBlind(gone)));
        checkThrows("a departed seat cannot declare readiness", () -> host.submit(new Action.ReadyForNext(gone)));

        int logged = host.getLog().size();
        host.submit(new Action.PlayerLeft(gone));   // a duplicate drop notice
        check("leaving twice is accepted as a no-op", host.getLog().size() == logged + 1);
        checkInt("still exactly one seat gone", host.getMatch().getActiveSeats().size(), 2);
    }

    /** A two-player match cannot continue as one: the second departure ends it. */
    private static void lastPlayerStandingEndsTheMatch() {
        MatchHost host = twoSeatHost();
        List<PlayerId> seats = host.getMatch().getSeats();

        host.submit(new Action.PlayerLeft(seats.get(1)));
        check("one leaving a two-player match ends it", host.getMatch().getPhase() == MatchPhase.FINISHED);
        checkInt("the survivor is the only active seat", host.getMatch().getActiveSeats().size(), 1);
        check("the survivor is not marked departed", !host.getMatch().hasDeparted(seats.get(0)));
    }

    /** Leaving forfeits the win: the seat that stays outranks a departed seat even one that banked more points. */
    private static void leavingForfeitsTheWin() {
        MatchHost host = twoSeatHost();
        Match match = host.getMatch();
        List<PlayerId> seats = match.getSeats();
        PlayerId stayer = seats.get(0), leaver = seats.get(1);

        match.getStandings().record(java.util.Map.of(leaver, 100L));   // the leaver is well ahead on points
        check("the leaver leads on points", match.getStandings().getPoints(leaver) > match.getStandings().getPoints(stayer));

        host.submit(new Action.PlayerLeft(leaver));
        check("the two-player match ended", match.getPhase() == MatchPhase.FINISHED);

        List<PlayerId> ranking = match.displayRanking();
        check("the seat that stayed wins", ranking.get(0).equals(stayer));
        check("the seat that left places last", ranking.get(ranking.size() - 1).equals(leaver));
        check("points order is untouched for in-match mechanics", match.getStandings().ranking().get(0).equals(leaver));
    }

    /** In a bigger match a leaver sinks below everyone still in, while the active seats keep their points order. */
    private static void leaverSinksBelowActiveSeats() {
        MatchHost host = threeSeatHost();
        Match match = host.getMatch();
        List<PlayerId> seats = match.getSeats();
        match.getStandings().record(java.util.Map.of(seats.get(2), 100L, seats.get(0), 50L, seats.get(1), 10L));

        host.submit(new Action.PlayerLeft(seats.get(2)));   // the points leader leaves; two are still in
        check("two still playing keeps the match live", match.getPhase() != MatchPhase.FINISHED);

        List<PlayerId> ranking = match.displayRanking();
        check("the top active seat ranks first", ranking.get(0).equals(seats.get(0)));
        check("the other active seat ranks second", ranking.get(1).equals(seats.get(1)));
        check("the leaver is last despite the most points", ranking.get(2).equals(seats.get(2)));
    }

    /** Departure is in the log, so a replay of that log has to reproduce the same match — the lockstep contract. */
    private static void departureIsDeterministic() {
        MatchHost live = threeSeatHost();
        List<PlayerId> seats = live.getMatch().getSeats();
        live.submit(new Action.PlayBlind(seats.get(0)));
        live.submit(new Action.PlayerLeft(seats.get(2)));
        live.submit(new Action.PlayBlind(seats.get(1)));

        MatchHost replayed = threeSeatHost();
        for (Action a : live.getLog()) replayed.submit(a);

        check("the replay reached the same phase", replayed.getMatch().getPhase() == live.getMatch().getPhase());
        check("the replay lost the same seat", replayed.getMatch().hasDeparted(seats.get(2)));
        check("the replay's active seats match",
                replayed.getMatch().getActiveSeats().equals(live.getMatch().getActiveSeats()));
        check("the logs are identical", replayed.getLog().equals(live.getLog()));
    }

    /** End to end: closing one client's socket must surface as a departure in every other client's model. */
    private static void socketDropReachesTheOtherClient() throws Exception {
        try (MatchServer server = new MatchServer(0, 900L, DeckType.STANDARD, 3, true)) {
            server.listen();
            int port = server.getPort();
            CountDownLatch started = new CountDownLatch(3);
            List<String> errors = new ArrayList<>();

            MatchClient c0 = MatchClient.join("localhost", port, SeatConfig.of("A"),
                    new MatchClient.Callbacks(null, started::countDown, null, errors::add));
            MatchClient c1 = MatchClient.join("localhost", port, SeatConfig.of("B"),
                    new MatchClient.Callbacks(null, started::countDown, null, errors::add));
            MatchClient c2 = MatchClient.join("localhost", port, SeatConfig.of("C"),
                    new MatchClient.Callbacks(null, started::countDown, null, errors::add));
            check("the three-seat match started", started.await(5, TimeUnit.SECONDS));

            PlayerId gone = c2.getSeat();
            c2.close();

            waitUntil(() -> server.getHost().getMatch().hasDeparted(gone), 5000);
            check("the server recorded the drop", server.getHost().getMatch().hasDeparted(gone));
            waitUntil(() -> c0.getLocalHost().getMatch().hasDeparted(gone)
                    && c1.getLocalHost().getMatch().hasDeparted(gone), 5000);
            check("client 0 saw the drop", c0.getLocalHost().getMatch().hasDeparted(gone));
            check("client 1 saw the drop", c1.getLocalHost().getMatch().hasDeparted(gone));
            check("the surviving logs still match", c0.getLocalHost().getLog().equals(c1.getLocalHost().getLog()));

            // And the table still works: the two survivors can cross a barrier on their own.
            var m = c0.getLocalHost().getMatch();
            c0.submit(new Action.PlayBlind(c0.getSeat()));
            c1.submit(new Action.PlayBlind(c1.getSeat()));
            waitUntil(() -> m.getPhase() == MatchPhase.BLIND, 5000);
            check("the survivors crossed the selection barrier", m.getPhase() == MatchPhase.BLIND);
            check("no errors reached the survivors", errors.isEmpty());

            c0.close();
            c1.close();
        }
    }

    // --- helpers ---

    private static MatchHost threeSeatHost() { return startedHost(List.of("A", "B", "C")); }
    private static MatchHost twoSeatHost()   { return startedHost(List.of("A", "B")); }

    private static MatchHost startedHost(List<String> names) {
        // Sins off: these tests exercise departure/barrier mechanics, not a random pack-granting sin (Wrath's
        // per-round pack would otherwise hold the barrier and change what "resolved" means here).
        MatchHost host = new MatchHost(Match.create(31L, names,
                MatchHost.networkedConfig().withSinSelector(model.game.SinSelector.NONE)));
        host.start();
        return host;
    }

    private static void waitUntil(java.util.function.BooleanSupplier cond, long millis) {
        long deadline = System.currentTimeMillis() + millis;
        while (!cond.getAsBoolean() && System.currentTimeMillis() < deadline) {
            try { Thread.sleep(2); } catch (InterruptedException e) { return; }
        }
    }

    private static void check(String label, boolean ok) {
        if (!ok) failures++;
        System.out.printf("%-52s %s%n", label, ok ? "PASS" : "FAIL");
    }

    private static void checkInt(String label, int actual, int expected) {
        check(label + " (" + actual + ")", actual == expected);
    }

    private static void checkThrows(String label, Runnable r) {
        try { r.run(); check(label, false); }
        catch (RuntimeException e) { check(label, true); }
    }
}
