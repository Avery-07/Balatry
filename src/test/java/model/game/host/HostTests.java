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
        wrathPackHoldsPlayedBarrier();
        shopReadiness();
        rejectionHygiene();
        fullMatchReplay();

        System.out.println(failures == 0 ? "\nALL PASS" : "\n" + failures + " FAILURE(S)");
        if (failures != 0) System.exit(1);
    }

    /** The blind phase advances by itself the moment the last seat's round resolves. */
    private static void blindBarrier() {
        MatchHost host = sinless(300L);   // pin sins off: this is the pure barrier, no random pack-granting sin
        host.start();
        Match m = host.getMatch();
        PlayerId a = m.getSeats().get(0), b = m.getSeats().get(1);
        check("start parks in selection", m.getPhase() == MatchPhase.SELECTION);
        playInto(host);
        check("both choosing to play enters the blind", m.getPhase() == MatchPhase.BLIND);
        host.submit(new Action.FinishRound(a));
        check("one resolved round is not enough", m.getPhase() == MatchPhase.BLIND);
        host.submit(new Action.FinishRound(b));
        check("the last resolution shows the result", m.getPhase() == MatchPhase.RESULT);
        readyInto(host);
        check("continuing opens the shops", m.getPhase() == MatchPhase.SHOP
                && m.getRun(a).getShop() != null);
    }

    /** Wrath's per-round free pack holds the blind barrier on a PLAYED round too (not only a skip) until it is opened. */
    private static void wrathPackHoldsPlayedBarrier() {
        MatchHost host = new MatchHost(Match.create(310L, List.of("A", "B"),
                MatchHost.networkedConfig().withSinSelector((ante, rng) -> model.game.Sin.WRATH)));
        host.start();
        Match m = host.getMatch();
        PlayerId a = m.getSeats().get(0), b = m.getSeats().get(1);
        playInto(host);   // SELECTION -> BLIND; Wrath grants each seat a Mega Myth Pack at blind begin
        check("Wrath granted a pending pack at blind begin", !m.getRun(a).getPendingPacks().isEmpty());

        for (PlayerId id : List.of(a, b)) {   // play the round out (not skip), then finish -> a resolved, played round
            var round = m.getRun(id).getRound();
            List<Integer> idx = new ArrayList<>();
            for (int i = 0; i < Math.min(5, round.getHand().size()); i++) idx.add(i);
            host.submit(new Action.PlayHand(id, idx));
            if (m.getRun(id).getRound() != null && m.getRun(id).getRound().getOutcome() == RoundOutcome.IN_PROGRESS)
                host.submit(new Action.FinishRound(id));
        }
        check("both played rounds resolved, but the barrier holds for the unopened Wrath packs",
                m.getPhase() == MatchPhase.BLIND);

        host.submit(new Action.OpenPack(a, 0));
        host.submit(new Action.SkipPack(a));
        check("one seat clearing its pack is not enough", m.getPhase() == MatchPhase.BLIND);
        host.submit(new Action.OpenPack(b, 0));
        host.submit(new Action.SkipPack(b));
        check("both packs resolved crosses to the result", m.getPhase() == MatchPhase.RESULT);
    }

    /** Shop readiness: all seats ready advances; acting revokes; NotReady revokes; wrong phase rejects. */
    private static void shopReadiness() {
        MatchHost host = sinless(301L);
        host.start();
        Match m = host.getMatch();
        PlayerId a = m.getSeats().get(0), b = m.getSeats().get(1);
        checkThrows("readiness needs the result or shop phase", () -> host.submit(new Action.ReadyForNext(a)));
        playInto(host);
        host.submit(new Action.FinishRound(a));
        host.submit(new Action.FinishRound(b));
        readyInto(host);   // RESULT -> SHOP

        m.getRun(a).addMoney(20);
        host.submit(new Action.ReadyForNext(a));
        check("one ready seat is not enough", m.getPhase() == MatchPhase.SHOP);
        host.submit(new Action.ReadyForNext(a));
        check("readiness is idempotent", m.getPhase() == MatchPhase.SHOP);
        host.submit(new Action.RerollShop(a));
        host.submit(new Action.ReadyForNext(b));
        check("acting revoked A's readiness", m.getPhase() == MatchPhase.SHOP);
        host.submit(new Action.ReadyForNext(a));
        check("all ready crosses to the next selection", m.getPhase() == MatchPhase.SELECTION && m.getBlind().ordinal() == 1);

        // NotReady revokes explicitly; SubmitSinChoice does not revoke.
        playInto(host);
        host.submit(new Action.FinishRound(a));
        host.submit(new Action.FinishRound(b));
        readyInto(host);   // RESULT -> SHOP
        host.submit(new Action.ReadyForNext(b));
        host.submit(new Action.NotReady(b));
        host.submit(new Action.ReadyForNext(a));
        check("NotReady revoked B's readiness", m.getPhase() == MatchPhase.SHOP);
        host.submit(new Action.SubmitSinChoice(a, 0));
        host.submit(new Action.ReadyForNext(b));
        check("a sin choice does not revoke readiness", m.getPhase() == MatchPhase.SELECTION);
    }

    /** A rejected action never enters the log and never advances a barrier. */
    private static void rejectionHygiene() {
        MatchHost host = sinless(302L);
        host.start();
        PlayerId a = host.getMatch().getSeats().get(0);
        playInto(host);
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
            if (m.getPhase() == MatchPhase.SELECTION) {
                for (PlayerId id : m.getSeats()) host.submit(new Action.PlayBlind(id));
            } else if (m.getPhase() == MatchPhase.BLIND) {
                for (PlayerId id : m.getSeats()) {
                    var run = m.getRun(id);
                    var round = run.getRound();
                    if (round != null && round.getOutcome() == RoundOutcome.IN_PROGRESS) {
                        int cards = Math.min(5, round.getHand().size());
                        List<Integer> indices = new ArrayList<>();
                        for (int i = 0; i < cards; i++) indices.add(i);
                        host.submit(new Action.PlayHand(id, indices));
                        if (run.getRound() != null && run.getRound().getOutcome() == RoundOutcome.IN_PROGRESS)
                            host.submit(new Action.FinishRound(id));
                    } else if (run.getCurrentOpening() != null) {
                        host.submit(new Action.SkipPack(id));       // abandon a granted pack's picks
                    } else if (!run.getPendingPacks().isEmpty()) {
                        host.submit(new Action.OpenPack(id, 0));    // a Wrath/tag pack must be opened for the barrier to pass
                    }
                }
            } else if (m.getPhase() == MatchPhase.RESULT || m.getPhase() == MatchPhase.SHOP) {
                for (PlayerId id : m.getSeats()) host.submit(new Action.ReadyForNext(id));
            }
        }
    }

    /** A host with sins pinned off — for tests of pure barrier/shop mechanics that should not ride a random sin. */
    private static MatchHost sinless(long seed) {
        return new MatchHost(Match.create(seed, List.of("A", "B"),
                MatchHost.networkedConfig().withSinSelector(model.game.SinSelector.NONE)));
    }

    /** Every seat chooses to play, crossing SELECTION -> BLIND. */
    private static void playInto(MatchHost host) {
        for (PlayerId id : host.getMatch().getSeats()) host.submit(new Action.PlayBlind(id));
    }

    /** Every seat continues, crossing RESULT -> SHOP (or SHOP -> next selection). */
    private static void readyInto(MatchHost host) {
        for (PlayerId id : host.getMatch().getSeats()) host.submit(new Action.ReadyForNext(id));
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
