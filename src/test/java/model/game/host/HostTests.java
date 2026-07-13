package model.game.host;

import model.game.Match;
import model.game.MatchPhase;
import model.game.actions.Action;
import model.game.player.PlayerId;
import model.game.player.Run;
import model.game.player.RoundOutcome;

import java.util.ArrayList;
import java.util.List;

/** Run-as-main harness for the MatchHost: automatic blind barriers, shop readiness (declare, revoke, revoke-by-acting), rejection hygiene, and a full match driven purely by actions replayed to identical final state. */
public final class HostTests {

    private static int failures = 0;

    public static void main(String[] args) {
        blindBarrier();
        shopReadiness();
        rejectionHygiene();
        fullMatchReplay();

        System.out.println(failures == 0 ? "\nALL PASS" : "\n" + failures + " FAILURE(S)");
        if (failures != 0) System.exit(1);
    }

    /** The blind phase advances by itself the moment the last seat's round resolves. */
    private static void blindBarrier() {
        MatchHost host = MatchHost.create(300L, List.of("A", "B"));
        host.start();
        Match m = host.getMatch();
        PlayerId a = m.getSeats().get(0), b = m.getSeats().get(1);
        host.submit(new Action.FinishRound(a));
        check("one resolved round is not enough", m.getPhase() == MatchPhase.BLIND);
        host.submit(new Action.FinishRound(b));
        check("the last resolution opens the shops", m.getPhase() == MatchPhase.SHOP
                && m.getRun(a).getShop() != null);
    }

    /** Shop readiness: all seats ready advances; acting revokes; NotReady revokes; wrong phase rejects. */
    private static void shopReadiness() {
        MatchHost host = MatchHost.create(301L, List.of("A", "B"));
        host.start();
        Match m = host.getMatch();
        PlayerId a = m.getSeats().get(0), b = m.getSeats().get(1);
        checkThrows("readiness is shop-phase only", () -> host.submit(new Action.ReadyForNext(a)));
        host.submit(new Action.FinishRound(a));
        host.submit(new Action.FinishRound(b));

        m.getRun(a).addMoney(20);
        host.submit(new Action.ReadyForNext(a));
        check("one ready seat is not enough", m.getPhase() == MatchPhase.SHOP);
        host.submit(new Action.ReadyForNext(a));
        check("readiness is idempotent", m.getPhase() == MatchPhase.SHOP);
        host.submit(new Action.RerollShop(a));
        host.submit(new Action.ReadyForNext(b));
        check("acting revoked A's readiness", m.getPhase() == MatchPhase.SHOP);
        host.submit(new Action.ReadyForNext(a));
        check("all ready crosses the barrier", m.getPhase() == MatchPhase.BLIND && m.getBlind().ordinal() == 1);

        // NotReady revokes explicitly; SubmitSinChoice does not revoke.
        host.submit(new Action.FinishRound(a));
        host.submit(new Action.FinishRound(b));
        host.submit(new Action.ReadyForNext(b));
        host.submit(new Action.NotReady(b));
        host.submit(new Action.ReadyForNext(a));
        check("NotReady revoked B's readiness", m.getPhase() == MatchPhase.SHOP);
        host.submit(new Action.SubmitSinChoice(a, 0));
        host.submit(new Action.ReadyForNext(b));
        check("a sin choice does not revoke readiness", m.getPhase() == MatchPhase.BLIND);
    }

    /** A rejected action never enters the log and never advances a barrier. */
    private static void rejectionHygiene() {
        MatchHost host = MatchHost.create(302L, List.of("A", "B"));
        host.start();
        PlayerId a = host.getMatch().getSeats().get(0);
        host.submit(new Action.FinishRound(a));
        int size = host.getLog().size();
        checkThrows("a bad action rejects", () -> host.submit(new Action.PlayHand(a, List.of(0))));
        checkInt("and never enters the log", host.getLog().size(), size);
        check("and the phase is unchanged", host.getMatch().getPhase() == MatchPhase.BLIND);
        checkThrows("Match.apply refuses table-level readiness",
                () -> host.getMatch().apply(new Action.ReadyForNext(a)));
    }

    /** A whole match driven only by submitted actions, replayed from its log to identical final state. */
    private static void fullMatchReplay() {
        long seed = 303L;
        List<String> names = List.of("A", "B");
        MatchHost live = MatchHost.create(seed, names);
        live.start();
        drive(live);
        check("the driven match finished", live.getMatch().getPhase() == MatchPhase.FINISHED);

        MatchHost replayed = MatchHost.replay(seed, names, live.getLog());
        check("the replay finished too", replayed.getMatch().getPhase() == MatchPhase.FINISHED);
        check("the replayed log is the log", replayed.getLog().equals(live.getLog()));
        check("an action log replays a whole match to identical state",
                fingerprint(live.getMatch()).equals(fingerprint(replayed.getMatch())));
    }

    /** Plays every blind (one full-hand play, then finish) and readies through every shop, actions only. */
    private static void drive(MatchHost host) {
        Match m = host.getMatch();
        int guard = 0;
        while (m.getPhase() != MatchPhase.FINISHED && guard++ < 500) {
            if (m.getPhase() == MatchPhase.BLIND) {
                for (PlayerId id : m.getSeats()) {
                    var round = m.getRun(id).getRound();
                    if (round == null || round.getOutcome() != RoundOutcome.IN_PROGRESS) continue;
                    int cards = Math.min(5, round.getHand().size());
                    List<Integer> indices = new ArrayList<>();
                    for (int i = 0; i < cards; i++) indices.add(i);
                    host.submit(new Action.PlayHand(id, indices));
                    if (m.getRun(id).getRound() != null
                            && m.getRun(id).getRound().getOutcome() == RoundOutcome.IN_PROGRESS)
                        host.submit(new Action.FinishRound(id));
                }
            } else if (m.getPhase() == MatchPhase.SHOP) {
                for (PlayerId id : m.getSeats()) host.submit(new Action.ReadyForNext(id));
            }
        }
    }

    private static String fingerprint(Match m) {
        StringBuilder sb = new StringBuilder();
        for (PlayerId id : m.getSeats()) {
            Run r = m.getRun(id);
            sb.append(id).append(':').append(r.getMoney())
              .append('/').append(r.getStats().getTotalHandsPlayed())
              .append('/').append(r.board().size())
              .append('/').append(m.getStandings().getPoints(id)).append('\n');
        }
        return sb.toString();
    }

    private static void check(String label, boolean ok) {
        System.out.printf("%-46s %s%n", label, ok ? "PASS" : "FAIL");
        if (!ok) failures++;
    }

    private static void checkInt(String label, int actual, int expected) {
        check(label + " (" + actual + ")", actual == expected);
    }

    private static void checkThrows(String label, Runnable r) {
        try { r.run(); check(label, false); }
        catch (RuntimeException e) { check(label, true); }
    }
}
