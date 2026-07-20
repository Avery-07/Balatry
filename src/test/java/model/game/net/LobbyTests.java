package model.game.net;

import model.items.DeckType;
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

    /**
     * How long to wait on a condition that should become true almost immediately. Generous on purpose: the
     * harness runner starts every {@code *Tests} class in its own JVM, so under that load a socket round trip
     * can take far longer than it does when this class runs alone. These are all "eventually" checks, so a
     * larger bound costs nothing when things work and only buys tolerance when the machine is busy.
     */
    private static final long WAIT_MS = 15_000;

    public static void main(String[] args) throws Exception {
        setupCodec();
        nameSanitizing();
        hostDrivenLobby();
        lobbyGuards();
        loadoutChangesInLobby();
        hostLeavingClosesTheLobby();
        guestLeavingReseats();
        fourPlayerLobby();

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
            waitUntil(() -> hostView.get() != null && hostView.get().seats().size() == 1, WAIT_MS);
            checkInt("the lobby shows the host alone", hostView.get().seats().size(), 1);

            MatchClient guest = MatchClient.join("localhost", port,
                    new SeatConfig("Bo", Sleeve.BLACK, Stake.PURPLE),
                    new MatchClient.Callbacks(null, bothStarted::countDown, null, null));
            check("a later joiner is not the host", !guest.isHost());
            waitUntil(() -> hostView.get().seats().size() == 2, WAIT_MS);
            checkInt("the roster reaches the host", hostView.get().seats().size(), 2);
            check("each seat announced its own sleeve",
                    hostView.get().seats().get(1).sleeve() == Sleeve.BLACK);
            check("each seat announced its own stake",
                    hostView.get().seats().get(1).stake() == Stake.PURPLE);

            // The deck is the host's to pick, and the change propagates before anyone starts.
            host.chooseDeck(DeckType.CHECKERED);
            waitUntil(() -> hostView.get().deck() == DeckType.CHECKERED, WAIT_MS);
            check("the host's deck pick propagates", hostView.get().deck() == DeckType.CHECKERED);

            check("nothing starts until the host says so", !server.isStarted());
            host.begin();
            check("both clients started", bothStarted.await(WAIT_MS, TimeUnit.MILLISECONDS));

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
            waitUntil(() -> !hostErrors.isEmpty(), WAIT_MS);
            check("a one-player start is refused", !hostErrors.isEmpty() && !server.isStarted());

            MatchClient guest = MatchClient.join("localhost", port, SeatConfig.of("Bo"),
                    new MatchClient.Callbacks(null, null, null, guestErrors::add));
            guest.begin();   // not the host
            waitUntil(() -> !guestErrors.isEmpty(), WAIT_MS);
            check("a guest cannot start the match", !guestErrors.isEmpty() && !server.isStarted());

            guest.chooseDeck(DeckType.PLASMA);
            waitUntil(() -> guestErrors.size() > 1, WAIT_MS);
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

    /** The loadout is picked in the lobby, so a seat must be able to re-pick it — and only its own. */
    private static void loadoutChangesInLobby() throws Exception {
        try (MatchServer server = new MatchServer(0, 21L, DeckType.STANDARD, 4, false)) {
            server.listen();
            int port = server.getPort();
            AtomicReference<MatchSetup> view = new AtomicReference<>();
            CountDownLatch started = new CountDownLatch(2);

            MatchClient host = MatchClient.join("localhost", port, SeatConfig.of("Ann"),
                    new MatchClient.Callbacks(view::set, started::countDown, null, null));
            MatchClient guest = MatchClient.join("localhost", port, SeatConfig.of("Bo"),
                    new MatchClient.Callbacks(null, started::countDown, null, null));
            waitUntil(() -> view.get() != null && view.get().seats().size() == 2, WAIT_MS);
            check("both seats open on the defaults",
                    view.get().seats().get(1).sleeve() == Sleeve.STANDARD
                    && view.get().seats().get(1).stake() == Stake.WHITE);

            guest.setLoadout(Sleeve.COLORFUL, Stake.GOLD);
            waitUntil(() -> view.get().seats().get(1).sleeve() == Sleeve.COLORFUL, WAIT_MS);
            check("a guest's new sleeve reaches everyone", view.get().seats().get(1).sleeve() == Sleeve.COLORFUL);
            check("a guest's new stake reaches everyone", view.get().seats().get(1).stake() == Stake.GOLD);
            check("it changed only that seat", view.get().seats().get(0).sleeve() == Sleeve.STANDARD);
            check("the name is not editable this way", view.get().seats().get(1).name().equals("Bo"));

            // The last pick before the start is what the match is built with.
            guest.setLoadout(Sleeve.BLACK, Stake.GREEN);
            waitUntil(() -> view.get().seats().get(1).sleeve() == Sleeve.BLACK, WAIT_MS);
            host.begin();
            check("the match started", started.await(WAIT_MS, TimeUnit.MILLISECONDS));
            var match = host.getLocalHost().getMatch();
            check("the final pick built the run",
                    match.getRun(match.getSeats().get(1)).getSleeve() == Sleeve.BLACK
                    && match.getRun(match.getSeats().get(1)).getStake() == Stake.GREEN);

            // Once the match exists the loadout is baked into every run; a late change must not take effect.
            guest.setLoadout(Sleeve.SILK, Stake.WHITE);
            waitUntil(() -> match.getRun(match.getSeats().get(1)).getSleeve() != Sleeve.BLACK, 500);
            check("a loadout change after the start is ignored",
                    match.getRun(match.getSeats().get(1)).getSleeve() == Sleeve.BLACK);

            host.close();
            guest.close();
        }
    }

    /** The lobby lives in the host's process, so the host leaving must send the guests home, not orphan them. */
    private static void hostLeavingClosesTheLobby() throws Exception {
        try (MatchServer server = new MatchServer(0, 31L, DeckType.STANDARD, 4, false)) {
            server.listen();
            int port = server.getPort();
            AtomicReference<String> guestClosed = new AtomicReference<>();
            AtomicReference<MatchSetup> guestView = new AtomicReference<>();

            MatchClient host = MatchClient.join("localhost", port, SeatConfig.of("Ann"),
                    MatchClient.Callbacks.none());
            MatchClient guest = MatchClient.join("localhost", port, SeatConfig.of("Bo"),
                    new MatchClient.Callbacks(guestView::set, null, null, null, guestClosed::set));
            waitUntil(() -> guestView.get() != null && guestView.get().seats().size() == 2, WAIT_MS);

            host.close();
            waitUntil(() -> guestClosed.get() != null, WAIT_MS);
            check("the guest was told the lobby closed", guestClosed.get() != null);
            check("the reason names the host",
                    guestClosed.get() != null && guestClosed.get().contains("host"));
            check("the lobby is empty", server.getSetup().seats().isEmpty());
            check("nothing was started", !server.isStarted());

            guest.close();
        }
    }

    /** A guest leaving is survivable, and the lobby seats up to four. */
    private static void fourPlayerLobby() throws Exception {
        try (MatchServer server = new MatchServer(0, 41L, DeckType.STANDARD, 4, false)) {
            server.listen();
            int port = server.getPort();
            AtomicReference<MatchSetup> view = new AtomicReference<>();
            CountDownLatch started = new CountDownLatch(4);

            MatchClient host = MatchClient.join("localhost", port, SeatConfig.of("Ann"),
                    new MatchClient.Callbacks(view::set, started::countDown, null, null));
            MatchClient b = MatchClient.join("localhost", port, SeatConfig.of("Bo"),
                    new MatchClient.Callbacks(null, started::countDown, null, null));
            MatchClient c = MatchClient.join("localhost", port, SeatConfig.of("Cy"),
                    new MatchClient.Callbacks(null, started::countDown, null, null));
            MatchClient d = MatchClient.join("localhost", port, SeatConfig.of("Di"),
                    new MatchClient.Callbacks(null, started::countDown, null, null));

            waitUntil(() -> view.get() != null && view.get().seats().size() == 4, WAIT_MS);
            checkInt("four players seat", view.get().seats().size(), 4);

            host.begin();
            check("a four-player match starts", started.await(WAIT_MS, TimeUnit.MILLISECONDS));
            checkInt("the match has four seats", host.getLocalHost().getMatch().getSeats().size(), 4);

            host.close(); b.close(); c.close(); d.close();
        }
    }

    /** A guest leaving before the start frees its seat and moves the rest up. */
    private static void guestLeavingReseats() throws Exception {
        try (MatchServer server = new MatchServer(0, 51L, DeckType.STANDARD, 4, false)) {
            server.listen();
            int port = server.getPort();
            AtomicReference<MatchSetup> view = new AtomicReference<>();

            MatchClient host = MatchClient.join("localhost", port, SeatConfig.of("Ann"),
                    new MatchClient.Callbacks(view::set, null, null, null));
            MatchClient b = MatchClient.join("localhost", port, SeatConfig.of("Bo"), MatchClient.Callbacks.none());
            MatchClient c = MatchClient.join("localhost", port, SeatConfig.of("Cy"), MatchClient.Callbacks.none());
            waitUntil(() -> view.get() != null && view.get().seats().size() == 3, WAIT_MS);

            b.close();
            waitUntil(() -> view.get().seats().size() == 2, WAIT_MS);
            checkInt("the roster shrank", view.get().seats().size(), 2);
            check("the remaining guest moved up", view.get().seats().get(1).name().equals("Cy"));
            check("the host is unaffected", host.isHost() && !server.isStarted());

            host.close(); c.close();
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
