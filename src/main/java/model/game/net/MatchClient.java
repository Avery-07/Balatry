package model.game.net;

import model.game.MatchConfig;
import model.game.actions.Action;
import model.game.host.MatchHost;
import model.game.player.PlayerId;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;

/**
 * A client's connection to a {@link MatchServer}. It learns its seat, then keeps a local {@link MatchHost} on
 * the same seed and player list, feeding every broadcast {@code SEQ} line into it — so the client's model is a
 * deterministic replay of the canonical action stream, always in step with the server. Outbound: the caller
 * submits {@link Action}s, which are encoded and sent. Rejections ({@code ERR}) surface via a callback.
 *
 * <p>This is the lockstep model that trades hidden information for architectural simplicity: every client
 * simulates the whole match. A server-authoritative variant that pushes per-seat filtered views would reuse
 * this class's framing and swap the local host for received state.
 */
public final class MatchClient implements AutoCloseable {

    private final Socket socket;
    private final PrintWriter out;
    private final BufferedReader in;
    private final MatchHost localHost;
    private final PlayerId seat;
    private final Consumer<String> onError;
    private final Runnable onApplied;

    private MatchClient(Socket socket, PlayerId seat, MatchHost localHost,
                        Consumer<String> onError, Runnable onApplied) throws IOException {
        this.socket = socket;
        this.seat = seat;
        this.localHost = localHost;
        this.onError = onError;
        this.onApplied = onApplied;
        this.out = new PrintWriter(new java.io.OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
    }

    /**
     * Connects, reads the seat assignment, and builds a local host on the shared seed and roster. The caller
     * supplies these because they are match setup, agreed out of band (lobby), not per-connection state.
     */
    public static MatchClient connect(String hostName, int port, long seed, List<String> playerNames,
                                      Consumer<String> onError) throws IOException {
        return connect(hostName, port, seed, playerNames, onError, null);
    }

    /**
     * As {@link #connect(String, int, long, List, Consumer)}, plus an {@code onApplied} hook fired on the
     * receive thread after each accepted {@code SEQ} frame is replayed into the local host. A UI layer supplies
     * a body that marshals a refresh onto its own thread (e.g. {@code Platform.runLater(view::refresh)}); this
     * is the single point where the background receive thread hands work off, so it is the only place model
     * mutation crosses into view-update territory.
     */
    public static MatchClient connect(String hostName, int port, long seed, List<String> playerNames,
                                      Consumer<String> onError, Runnable onApplied) throws IOException {
        Socket socket = new Socket(hostName, port);
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        String seatLine = in.readLine();
        if (seatLine == null || !seatLine.startsWith("SEAT"))
            throw new IOException("expected a seat assignment, got: " + seatLine);
        PlayerId seat = new PlayerId(Integer.parseInt(seatLine.substring("SEAT".length())));
        MatchHost localHost = new MatchHost(model.game.Match.create(seed, playerNames, MatchHost.networkedConfig()));
        localHost.start();
        MatchClient client = new MatchClient(socket, seat, localHost, onError, onApplied);
        client.reopenReaderFrom(in);
        return client;
    }

    private void reopenReaderFrom(BufferedReader alreadyOpen) {
        Thread t = new Thread(() -> {
            try {
                String line;
                while ((line = alreadyOpen.readLine()) != null) receive(line);
            } catch (IOException ignored) { }
        }, "client-recv-" + seat.seat());
        t.setDaemon(true);
        t.start();
    }

    /** Applies one server frame: a SEQ line replays into the local host; an ERR line hits the callback. */
    private synchronized void receive(String line) {
        if (line.startsWith("SEQ")) {
            int tab = line.indexOf('\t');
            localHost.submit(ActionCodec.decode(line.substring(tab + 1)));
            if (onApplied != null) onApplied.run();
        } else if (line.startsWith("ERR") && onError != null) {
            int tab = line.indexOf('\t');
            onError.accept(tab >= 0 ? line.substring(tab + 1) : line);
        }
    }

    /** Sends an action to the server; the resulting state change arrives via the broadcast, not a return value. */
    public void submit(Action action) { out.println(ActionCodec.encode(action)); }

    public PlayerId getSeat() { return seat; }

    /** The local, replay-driven model — always in step with the server's accepted-action log. */
    public MatchHost getLocalHost() { return localHost; }

    @Override
    public void close() throws IOException { socket.close(); }
}
