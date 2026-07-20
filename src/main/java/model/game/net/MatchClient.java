package model.game.net;

import model.cards.DeckType;
import model.game.Match;
import model.game.actions.Action;
import model.game.host.MatchHost;
import model.game.player.PlayerId;
import model.game.player.SeatConfig;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * A client's connection to a {@link MatchServer}, across both of the server's phases.
 *
 * <p><strong>Lobby.</strong> Connecting announces this player's own {@link SeatConfig} and returns as soon as the
 * server has assigned a seat — there is no model yet. The roster arrives (and re-arrives on every change) through
 * {@code onLobby}; the host — seat 0 — may {@link #chooseDeck} and {@link #begin}. This is the fix for the old
 * design's central hazard: the roster is learned from the server, never guessed, so seats cannot disagree.
 *
 * <p><strong>Match.</strong> On {@code START} the client builds its local {@link MatchHost} from the broadcast
 * setup and fires {@code onStarted}. From then on every broadcast {@code SEQ} line replays into that host, so the
 * client's model is a deterministic replay of the canonical action stream, always in step with the server.
 * Outbound: the caller submits {@link Action}s, which are encoded and sent. Rejections ({@code ERR}) surface via
 * {@code onError}.
 *
 * <p>This is the lockstep model that trades hidden information for architectural simplicity: every client
 * simulates the whole match. A server-authoritative variant that pushes per-seat filtered views would reuse this
 * class's framing and swap the local host for received state.
 */
public final class MatchClient implements AutoCloseable {

    private final Socket socket;
    private final PrintWriter out;
    private final Callbacks callbacks;

    private volatile PlayerId seat;
    private volatile MatchSetup lobby;      // the roster as last broadcast; null until the first LOBBY frame
    private volatile MatchHost localHost;   // null until START

    /**
     * The client's outward hooks, all fired on the socket-receive thread. A UI layer supplies bodies that marshal
     * onto its own thread (e.g. {@code Platform.runLater}); this is the single place where the background thread
     * hands work off, so it is the only point where model mutation crosses into view-update territory.
     *
     * @param onLobby   the roster changed (a seat joined, or the host changed the deck)
     * @param onStarted the match was built and started; the local host now exists
     * @param onApplied an accepted action was replayed into the local host
     * @param onError   the server rejected something this client sent, or refused the connection
     */
    public record Callbacks(Consumer<MatchSetup> onLobby, Runnable onStarted,
                            Consumer<Action> onApplied, Consumer<String> onError) {

        public static Callbacks none() { return new Callbacks(null, null, null, null); }
    }

    private MatchClient(Socket socket, Callbacks callbacks) throws IOException {
        this.socket = socket;
        this.callbacks = callbacks == null ? Callbacks.none() : callbacks;
        this.out = new PrintWriter(new java.io.OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
    }

    /**
     * Connects and joins the lobby as {@code me}, blocking only until the server assigns a seat. The returned
     * client has no model yet — wait for {@code onStarted}, or poll {@link #isStarted()}.
     */
    public static MatchClient join(String hostName, int port, SeatConfig me, Callbacks callbacks) throws IOException {
        Socket socket = new Socket(hostName, port);
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        MatchClient client = new MatchClient(socket, callbacks);
        client.send("JOIN\t" + me.name() + ":" + me.sleeve().name() + ":" + me.stake().name());

        String seatLine = in.readLine();
        if (seatLine == null) throw new IOException("the server closed the connection");
        if (seatLine.startsWith("ERR\t")) throw new IOException(seatLine.substring("ERR\t".length()));
        if (!seatLine.startsWith("SEAT\t")) throw new IOException("expected a seat assignment, got: " + seatLine);
        client.seat = new PlayerId(Integer.parseInt(seatLine.substring("SEAT\t".length()).trim()));

        client.readFrom(in);
        return client;
    }

    private void readFrom(BufferedReader alreadyOpen) {
        Thread t = new Thread(() -> {
            try {
                String line;
                while ((line = alreadyOpen.readLine()) != null) receive(line);
            } catch (IOException ignored) { }
        }, "client-recv-" + seat.seat());
        t.setDaemon(true);
        t.start();
    }

    /** Applies one server frame: lobby updates, the start signal, replayed actions, or a rejection. */
    private synchronized void receive(String line) {
        if (line.startsWith("LOBBY\t")) {
            lobby = MatchSetup.decode(line.substring("LOBBY\t".length()));
            fire(callbacks.onLobby(), lobby);
        } else if (line.startsWith("START\t")) {
            MatchSetup setup = MatchSetup.decode(line.substring("START\t".length()));
            lobby = setup;
            localHost = new MatchHost(Match.createSeated(setup.seed(), setup.seats(), setup.config()));
            localHost.start();
            if (callbacks.onStarted() != null) callbacks.onStarted().run();
        } else if (line.startsWith("SEQ")) {
            int tab = line.indexOf('\t');
            Action action = ActionCodec.decode(line.substring(tab + 1));
            localHost.submit(action);
            fire(callbacks.onApplied(), action);
        } else if (line.startsWith("ERR")) {
            int tab = line.indexOf('\t');
            fire(callbacks.onError(), tab >= 0 ? line.substring(tab + 1) : line);
        }
    }

    private static <T> void fire(Consumer<T> hook, T value) { if (hook != null) hook.accept(value); }

    // --- lobby -------------------------------------------------------------

    /** The roster as last broadcast, or {@code null} before the first lobby frame arrives. */
    public MatchSetup getLobby() { return lobby; }

    /** Whether this client is the host — seat 0, the only seat that may pick the deck or start the match. */
    public boolean isHost() { return seat != null && seat.seat() == 0; }

    /** Host only: change the table's deck. Ignored by the server if sent from any other seat. */
    public void chooseDeck(DeckType deck) { send("DECK\t" + deck.name()); }

    /** Host only: close the lobby and start the match with whoever is seated. */
    public void begin() { send("BEGIN"); }

    // --- match -------------------------------------------------------------

    /** Whether the match has started and {@link #getLocalHost()} is usable. */
    public boolean isStarted() { return localHost != null; }

    /** Sends an action to the server; the resulting state change arrives via the broadcast, not a return value. */
    public void submit(Action action) { send(ActionCodec.encode(action)); }

    public PlayerId getSeat() { return seat; }

    /** The local, replay-driven model — always in step with the server's accepted-action log. Null before START. */
    public MatchHost getLocalHost() { return localHost; }

    private void send(String line) { out.println(line); }

    @Override
    public void close() throws IOException { socket.close(); }
}
