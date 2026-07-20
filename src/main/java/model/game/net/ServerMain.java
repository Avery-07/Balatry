package model.game.net;

/**
 * A bare launcher for a headless {@link MatchServer}, wired to {@code mvn exec:java@server}. It opens a lobby and
 * auto-starts the match as soon as the seats fill — useful for a dedicated server or a scripted test, but not the
 * usual path any more: a player normally hosts from inside the client (see {@code HostedMatch}), which lets them
 * pick their own loadout and decide when to start.
 *
 * <p>Each client announces its own name, sleeve and stake when it joins, so the roster is <em>not</em> configured
 * here — only the table-wide settings are. Overridable properties (with defaults):
 *
 * <pre>
 *   -Dbalatry.port=5555
 *   -Dbalatry.seed=42
 *   -Dbalatry.deck=STANDARD
 *   -Dbalatry.seats=2          (2-4; the match starts when this many players have joined)
 * </pre>
 *
 * Deliberately minimal: no reconnection, no shutdown handling beyond process exit.
 */
public final class ServerMain {

    private ServerMain() { }

    public static void main(String[] args) throws Exception {
        int port = Integer.getInteger("balatry.port", HostedMatch.DEFAULT_PORT);
        long seed = Long.parseLong(System.getProperty("balatry.seed", "42"));
        var deck = MatchSetup.parseDeck(System.getProperty("balatry.deck", "STANDARD"));
        int seats = Integer.getInteger("balatry.seats", 2);

        try (MatchServer server = new MatchServer(port, seed, deck, seats, true)) {
            server.listen();
            System.out.printf("Balatry server on port %d — seed %d, %s, waiting for %d player(s).%n",
                    server.getPort(), seed, deck.displayName(), seats);
            while (!server.isStarted()) Thread.sleep(100);
            System.out.println("All seats filled; match started: " + server.getSetup().seats());
            System.out.println("Relaying actions. Ctrl-C to stop.");
            Thread.currentThread().join();
        }
    }
}
