package model.game.net;

import model.items.DeckType;
import model.game.player.SeatConfig;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;

/**
 * Hosting from inside the client: starts a {@link MatchServer} in this process and joins it as seat 0. This is
 * what lets a player pick who they play with — they host, read their address off the lobby screen, and hand it
 * to whoever they want; those players {@link MatchClient#join} it. No separate server process, no out-of-band
 * agreement on the roster.
 *
 * <p>The returned handle owns both halves: closing it closes the client and shuts the server down.
 */
public record HostedMatch(MatchServer server, MatchClient client) implements AutoCloseable {

    /** The default port; anything is fine as long as the guests are told the same one. */
    public static final int DEFAULT_PORT = 5555;

    /**
     * Starts a lobby on {@code port} and joins it as the host. {@code port} may be 0 for an ephemeral one, which
     * {@link MatchServer#getPort()} then reports.
     */
    public static HostedMatch start(int port, long seed, DeckType deck, int seatCap,
                                    SeatConfig me, MatchClient.Callbacks callbacks) throws IOException {
        MatchServer server = new MatchServer(port, seed, deck, seatCap, false);
        server.listen();
        try {
            return new HostedMatch(server, MatchClient.join("localhost", server.getPort(), me, callbacks));
        } catch (IOException e) {
            server.close();   // never leave a listening socket behind a failed join
            throw e;
        }
    }

    /**
     * A best-effort LAN address to read out to other players, or {@code "localhost"} if none can be determined.
     * Purely cosmetic — the lobby screen shows it so the host has something to share.
     */
    public static String lanAddress() {
        try {
            for (NetworkInterface nif : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nif.isUp() || nif.isLoopback() || nif.isVirtual()) continue;
                for (InetAddress addr : Collections.list(nif.getInetAddresses()))
                    if (addr instanceof java.net.Inet4Address && !addr.isLoopbackAddress())
                        return addr.getHostAddress();
            }
        } catch (Exception ignored) {
            // no usable interface (offline, or locked down) — localhost is still correct for a same-machine test
        }
        return "localhost";
    }

    @Override
    public void close() throws IOException {
        try { client.close(); } finally { server.close(); }
    }
}
