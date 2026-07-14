package model.game.net;

import model.game.host.MatchHost;

import java.util.List;

/**
 * A bare launcher for a {@link MatchServer}, wired to {@code mvn exec:java@server}. It reads the match setup
 * (the out-of-band lobby: port, seed, roster) from system properties, builds a host on the networked config,
 * and blocks until every seat has connected — at which point the match starts and the server relays actions.
 *
 * <p>Deliberately minimal: no real lobby, no reconnection, no shutdown handling beyond process exit. The seed
 * and player names must match what each {@link MatchClient} passes to {@code connect}, because the lockstep
 * model requires an identical starting match on every side. Overridable properties (with defaults):
 *
 * <pre>
 *   -Dbalatry.port=5555
 *   -Dbalatry.seed=42
 *   -Dbalatry.players=P0,P1        (comma-separated; seat count is derived from this list)
 * </pre>
 *
 * Example: {@code mvn exec:java@server -Dexec.args=""} then launch one client per name.
 */
public final class ServerMain {

    private ServerMain() { }

    public static void main(String[] args) throws Exception {
        int port = Integer.getInteger("balatry.port", 5555);
        long seed = Long.parseLong(System.getProperty("balatry.seed", "42"));
        List<String> players = List.of(System.getProperty("balatry.players", "P0,P1").split(","));

        MatchHost host = new MatchHost(model.game.Match.create(seed, players, MatchHost.networkedConfig()));

        try (MatchServer server = new MatchServer(host, port)) {
            System.out.printf("Balatry server on port %d — seed %d, waiting for %d player(s): %s%n",
                    server.getPort(), seed, players.size(), players);
            server.awaitPlayersAndStart(players.size());
            System.out.println("All seats filled; match started. Relaying actions. Ctrl-C to stop.");
            // The accept loop has returned; reader threads (daemon) keep relaying. Park the main thread.
            Thread.currentThread().join();
        }
    }
}
