package model.game.net;

import model.cards.DeckCard.Rank;
import model.cards.DeckCard.Suit;
import model.cards.relics.RelicTarget;
import model.game.Match;
import model.game.MatchPhase;
import model.game.actions.Action;
import model.game.host.MatchHost;
import model.game.player.PlayerId;
import model.game.player.Run;
import model.game.player.RoundOutcome;
import model.game.scoring.HandType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Run-as-main harness for the transport: codec round-trip over every action type (including relic-target selectors), and a full match played across real loopback sockets with server and both clients asserted to identical state. */
public final class NetTests {

    private static int failures = 0;

    public static void main(String[] args) throws Exception {
        codecRoundTrip();
        codecRejectsGarbage();
        endToEndMatch();

        System.out.println(failures == 0 ? "\nALL PASS" : "\n" + failures + " FAILURE(S)");
        if (failures != 0) System.exit(1);
    }

    /** Every action encodes to a newline-free line and decodes back to an equal record. */
    private static void codecRoundTrip() {
        PlayerId a = new PlayerId(0), b = new PlayerId(1);
        List<Action> samples = List.of(
                new Action.PlayHand(a, List.of(0, 2, 4)),
                new Action.DiscardCards(a, List.of()),
                new Action.FinishRound(a),
                new Action.SkipBlind(b),
                new Action.UseConsumable(a, 1, List.of(3)),
                new Action.UseRelic(a, 0, RelicTarget.none()),
                new Action.UseRelic(a, 2, RelicTarget.rank(b, Rank.KING)),
                new Action.UseRelic(a, 2, RelicTarget.suit(b, Suit.SPADES)),
                new Action.UseRelic(a, 2, RelicTarget.joker(b, 3)),
                new Action.UseRelic(a, 2, RelicTarget.hand(b, HandType.FLUSH)),
                new Action.SellJoker(a, 1),
                new Action.SellConsumable(a, 0),
                new Action.SellRelic(a, 2),
                new Action.MoveJoker(a, 3, 0),
                new Action.OpenPack(a, 0),
                new Action.PickFromPack(a, 1, RelicTarget.none()),
                new Action.PickFromPack(a, 1, RelicTarget.on(b)),
                new Action.BuyCard(a, 2),
                new Action.BuyPack(a, 0),
                new Action.RedeemVoucher(a, 1),
                new Action.RerollShop(a),
                new Action.PrideBid(a, 12),
                new Action.EnvyCopy(b, 0),
                new Action.EnvySwap(a, 1, b, 2),
                new Action.WrathDestroy(a, 0),
                new Action.GluttonyEat(a, 3),
                new Action.SubmitSinChoice(a, 2),
                new Action.ReadyForNext(a),
                new Action.NotReady(b));

        boolean allOk = true;
        for (Action action : samples) {
            String line = ActionCodec.encode(action);
            if (line.contains("\n")) { allOk = false; System.out.println("  has newline: " + action); }
            Action back = ActionCodec.decode(line);
            if (!action.equals(back)) { allOk = false; System.out.println("  mismatch: " + action + " -> " + back); }
        }
        check("every action round-trips through the codec", allOk);
        checkInt("all action types are covered", samples.size(), 29);
    }

    private static void codecRejectsGarbage() {
        checkThrows("an unknown tag is rejected", () -> ActionCodec.decode("BOGUS\t0"));
    }

    /** A full match over localhost: two socket clients play to FINISHED; all three models must match. */
    private static void endToEndMatch() throws Exception {
        long seed = 400L;
        List<String> names = List.of("A", "B");
        MatchHost serverHost = MatchHost.create(seed, names);

        try (MatchServer server = new MatchServer(serverHost, 0)) {
            int port = server.getPort();
            CountDownLatch ready = new CountDownLatch(1);
            Thread accept = new Thread(() -> {
                try { server.awaitPlayersAndStart(2); ready.countDown(); }
                catch (Exception e) { e.printStackTrace(); }
            });
            accept.start();

            List<String> errors = new ArrayList<>();
            MatchClient c0 = MatchClient.connect("localhost", port, seed, names, errors::add);
            MatchClient c1 = MatchClient.connect("localhost", port, seed, names, errors::add);
            check("clients received distinct seats", c0.getSeat().seat() == 0 && c1.getSeat().seat() == 1);
            ready.await(5, TimeUnit.SECONDS);

            MatchClient[] clients = { c0, c1 };
            int guard = 0;
            while (serverHost.getMatch().getPhase() != MatchPhase.FINISHED && guard++ < 500) {
                Match m = serverHost.getMatch();
                if (m.getPhase() == MatchPhase.BLIND) {
                    for (int seat = 0; seat < 2; seat++) {
                        var round = m.getRun(m.getSeats().get(seat)).getRound();
                        if (round == null || round.getOutcome() != RoundOutcome.IN_PROGRESS) continue;
                        PlayerId id = m.getSeats().get(seat);
                        int cards = Math.min(5, round.getHand().size());
                        List<Integer> idx = new ArrayList<>();
                        for (int i = 0; i < cards; i++) idx.add(i);
                        submitAndSettle(clients[seat], new Action.PlayHand(id, idx), serverHost);
                        var after = m.getRun(id).getRound();
                        if (after != null && after.getOutcome() == RoundOutcome.IN_PROGRESS)
                            submitAndSettle(clients[seat], new Action.FinishRound(id), serverHost);
                    }
                } else if (m.getPhase() == MatchPhase.SHOP) {
                    for (int seat = 0; seat < 2; seat++)
                        submitAndSettle(clients[seat], new Action.ReadyForNext(m.getSeats().get(seat)), serverHost);
                }
            }

            check("the match finished over the wire", serverHost.getMatch().getPhase() == MatchPhase.FINISHED);
            check("no errors were reported", errors.isEmpty());
            // Let the final broadcasts drain into both clients.
            waitUntil(() -> c0.getLocalHost().getMatch().getPhase() == MatchPhase.FINISHED
                    && c1.getLocalHost().getMatch().getPhase() == MatchPhase.FINISHED, 5000);

            String serverFp = fingerprint(serverHost.getMatch());
            check("client 0 matches the server", fingerprint(c0.getLocalHost().getMatch()).equals(serverFp));
            check("client 1 matches the server", fingerprint(c1.getLocalHost().getMatch()).equals(serverFp));
            check("the logs are identical",
                    serverHost.getLog().equals(c0.getLocalHost().getLog())
                    && serverHost.getLog().equals(c1.getLocalHost().getLog()));

            c0.close();
            c1.close();
        }
    }

    /** Submits an action, then waits until the server host has logged it (the broadcast has been processed). */
    private static void submitAndSettle(MatchClient client, Action action, MatchHost serverHost) {
        int before = serverHost.getLog().size();
        client.submit(action);
        waitUntil(() -> serverHost.getLog().size() > before, 5000);
    }

    private static void waitUntil(java.util.function.BooleanSupplier cond, long millis) {
        long deadline = System.currentTimeMillis() + millis;
        while (!cond.getAsBoolean() && System.currentTimeMillis() < deadline) {
            try { Thread.sleep(2); } catch (InterruptedException e) { return; }
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
