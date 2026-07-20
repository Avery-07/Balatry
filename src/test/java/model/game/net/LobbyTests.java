package model.game.net;

import model.cards.DeckType;
import model.game.Stake;
import model.game.player.SeatConfig;
import model.game.player.Sleeve;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Run-as-main harness for the lobby: the phase that replaced the old "every process guesses the roster" setup.
 * It asserts what that change has to buy us — each seat announces its own loadout, the server is the single
 * authority on the roster, and the match every side builds comes from one broadcast setup rather than from
 * separately-configured guesses.
 */
public final class LobbyTests {

    private static int failures = 0;

    public static void main(String[] args) throws Exception {
        setupCodec();
        nameSanitizing();
        hostDrivenLobby();
        lobbyGuards();

        System.out.println(failures == 0 ? "\nALL PASS" : "\n" + failures + " FAILURE(S)");
        if (failures != 0) System.exit(1);
    }

    /** The setup is the one thing every side must agree on byte-for-byte, so it has to survive a round trip. */
    private static void setupCodec() {
        MatchSetup setup = new MatchSetup(1234L, DeckType.ERRATIC, List.of(
                new SeatConfig("Ann", Sleeve.RED_BLUE, Stake.GOLD),
                new SeatConfig("Bo", Sleeve.FRACTURE, Stake.WHITE)));
        MatchSetup back = MatchSetup.decode(setup.encode());
        check("the setup round-trips", back.equals(setup));
        check("a re-encode is byte-identical", back.encode().equals(setup.encode()));

        MatchSetup empty = new MatchSetup(1L, DeckType.STANDARD, List.of());
        check("an empty lobby round-trips", MatchSetup.decode(empty.encode()).seats().isEmpty());
    }

    /** A typed name reaches the wire, so the separators it could otherwise smuggle in must be stripped. */
    private static void nameSanitizing() {
        check("commas are stripped", MatchSetup.sanitize("A,B", "x").equals("AB"));
        check("colons are stripped", MatchSetup.sanitize("A:GOLD", "x").equals("AGOLD"));
        check("tabs are stripped", MatchSetup.sanitize("A\tB", "x").equals("AB"));
        check("an empty name falls back", MatchSetup.sanitize("  ", "Player").equals("Player"));
        check("a null name falls back", MatchSetup.sanitize(null, "Player").equals("Player"));

        // The real guarantee: a hostile name cannot forge extra seats or a better stake.
        MatchSetup setup = new MatchSetup(1L, DeckType.STANDARD,
                List.of(new SeatConfig(MatchSetup.sanitize("Ann,Mallory:BLACK:GOLD", "P"), Sleeve.STANDARD, Stake.WHITE)));
        List<SeatConfig> decoded = MatchSetup.decode(setup.encode()).seats();
        checkInt("a smuggled roster stays one seat", decoded.size(), 1);
        check("a smuggled stake is ignored", decoded.get(0).stake() == Stake.WHITE);
    }

    /** The host flow: two players join with their own loadouts, the host picks the deck and starts. */
    private static void hostDrivenLobby() throws Exception {
        try (MatchServer server = new MatchServer(0, 77L, DeckType.STANDARD, 4, false)) {
            server.listen();
            int port = server.getPort();

            AtomicReference<MatchSetup> hostView = new AtomicReference<>();
            CountDownLatch bothStarted = new CountDownLatch(2);

            MatchClient host = MatchClient.join("localhost", port,
                    new SeatConfig("Ann", Sleeve.LEGACY, Stake.WHITE),
                    new MatchClient.Callbacks(hostView::set, bothStarted::countDown, null, null));
            check("the first to join is the host", host.isHost());
            waitUntil(() -> hostView.get() != null && hostView.get().seats().size() == 1, 3000);
            checkInt("the lobby shows the host alone", hostView.get().seats().size(), 1);

            MatchClient guest = MatchClient.join("localhost", port,
                    new SeatConfig("Bo", Sleeve.BLACK, Stake.PURPLE),
                    new MatchClient.Callbacks(null, bothStarted::countDown, null, null));
            check("a later joiner is not the host", !guest.isHost());
            waitUntil(() -> hostView.get().seats().size() == 2, 3000);
            checkInt("the roster reaches the host", hostView.get().seats().size(), 2);
            check("each seat announced its own sleeve",
                    hostView.get().seats().get(1).sleeve() == Sleeve.BLACK);
            check("each seat announced its own stake",
                    hostView.get().seats().get(1).stake() == Stake.PURPLE);

            // The deck is the host's to pick, and the change propagates before anyone starts.
            host.chooseDeck(DeckType.CHECKERED);
            waitUntil(() -> hostView.get().deck() == DeckType.CHECKERED, 3000);
            check("the host's deck pick propagates", hostView.get().deck() == DeckType.CHECKERED);

            check("nothing starts until the host says so", !server.isStarted());
            host.begin();
            check("both clients started", bothStarted.await(5, TimeUnit.SECONDS));

            // What everyone built must be one and the same match, taken from the broadcast setup.
            var hostMatch = host.getLocalHost().getMatch();
            var guestMatch = guest.getLocalHost().getMatch();
            check("the table's deck reached the match", hostMatch.getDeckType() == DeckType.CHECKERED);
            check("the guest built the same deck", guestMatch.getDeckType() == DeckType.CHECKERED);
            check("seat 1's stake survived the lobby",
                    hostMatch.getRun(hostMatch.getSeats().get(1)).getStake() == Stake.PURPLE);
            check("seat 0's sleeve survived the lobby",
                    guestMatch.getRun(guestMatch.getSeats().get(0)).getSleeve() == Sleeve.LEGACY);
            check("both sides agree on the roster",
                    hostMatch.getSeats().size() == guestMatch.getSeats().size());

            host.close();
            guest.close();
        }
    }

    /** The lobby's refusals: only the host may start, a match needs two, and a full lobby turns arrivals away. */
    private static void lobbyGuards() throws Exception {
        try (MatchServer server = new MatchServer(0, 5L, DeckType.STANDARD, 2, false)) {
            server.listen();
            int port = server.getPort();
            List<String> hostErrors = new ArrayList<>(), guestErrors = new ArrayList<>();

            MatchClient host = MatchClient.join("localhost", port, SeatConfig.of("Ann"),
                    new MatchClient.Callbacks(null, null, null, hostErrors::add));

            host.begin();   // alone in the lobby
            waitUntil(() -> !hostErrors.isEmpty(), 3000);
            check("a one-player start is refused", !hostErrors.isEmpty() && !server.isStarted());

            MatchClient guest = MatchClient.join("localhost", port, SeatConfig.of("Bo"),
                    new MatchClient.Callbacks(null, null, null, guestErrors::add));
            guest.begin();   // not the host
            waitUntil(() -> !guestErrors.isEmpty(), 3000);
            check("a guest cannot start the match", !guestErrors.isEmpty() && !server.isStarted());

            guest.chooseDeck(DeckType.PLASMA);
            waitUntil(() -> guestErrors.size() > 1, 3000);
            check("a guest cannot change the deck",
                    guestErrors.size() > 1 && server.getSetup().deck() == DeckType.STANDARD);

            // The lobby is capped at two: a third arrival is turned away rather than silently seated.
            boolean refused = false;
            try {
                MatchClient.join("localhost", port, SeatConfig.of("Cy"), MatchClient.Callbacks.none());
            } catch (java.io.IOException e) {
                refused = true;
            }
            check("a full lobby refuses the next arrival", refused);

            host.close();
            guest.close();
        }
    }

    // --- helpers ---

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
}
