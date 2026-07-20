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
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * A minimal thread-per-client TCP server with two phases: a <em>lobby</em>, where it collects who is playing and
 * with what, and a <em>match</em>, where it relays actions into a {@link MatchHost} and broadcasts the accepted
 * stream so every client replays it identically.
 *
 * <p>The lobby exists because lockstep replay demands an identical starting match everywhere. Rather than every
 * process independently guessing the roster (the old design, which desynced silently on any disagreement), the
 * server is the single authority: each client announces its own name/sleeve/stake on connect, the server assigns
 * seats in arrival order, and when the host starts the match it broadcasts the assembled {@link MatchSetup} that
 * everyone — server included — builds their {@link Match} from.
 *
 * <p>Wire protocol, newline-delimited UTF-8. Lobby:
 * <pre>
 *   C-&gt;S  JOIN&lt;TAB&gt;name:SLEEVE:STAKE     announce this seat's loadout (first line a client sends)
 *   S-&gt;C  SEAT&lt;TAB&gt;n                     your seat index; n=0 is the host
 *   S-&gt;*  LOBBY&lt;TAB&gt;&lt;setup&gt;              the roster so far, re-sent on every change
 *   C-&gt;S  LOADOUT&lt;TAB&gt;SLEEVE:STAKE       change your own sleeve/stake; lobby only
 *   C-&gt;S  DECK&lt;TAB&gt;NAME                  host only: change the table's deck
 *   C-&gt;S  BEGIN                          host only: start the match with whoever is seated
 *   S-&gt;*  START&lt;TAB&gt;&lt;setup&gt;              build this match and start; the lobby is closed
 *   S-&gt;*  CLOSED&lt;TAB&gt;reason              the lobby is gone (the host left); everyone is disconnected
 * </pre>
 * Match: clients send action lines ({@link ActionCodec}) and receive {@code SEQ<n>\t<action-line>} for every
 * accepted action, plus {@code ERR\t<reason>} for their own rejections. State is never pushed — clients derive
 * it by replay. A late or surplus connection is refused with {@code ERR} and closed.
 */
public final class MatchServer implements AutoCloseable {

    /** A match seats 2-4; the lobby refuses the fifth arrival. */
    public static final int MAX_SEATS = 4, MIN_SEATS = 2;

    private final ServerSocket serverSocket;
    private final List<ClientLink> clients = new ArrayList<>();
    private final long seed;
    private final int seatCap;
    private final boolean autoStart;   // dedicated-server mode: begin as soon as the cap is reached

    private DeckType deck;
    private MatchHost host;            // null until the match starts — the lobby has no model yet
    private volatile boolean running = true;

    /**
     * A server in the lobby phase. {@code seatCap} is how many seats the lobby holds; with {@code autoStart} the
     * match begins the moment it fills (headless hosting), otherwise the host client starts it with {@code BEGIN}.
     */
    public MatchServer(int port, long seed, DeckType deck, int seatCap, boolean autoStart) throws IOException {
        if (seatCap < MIN_SEATS || seatCap > MAX_SEATS)
            throw new IllegalArgumentException("a match seats " + MIN_SEATS + "-" + MAX_SEATS + ", got " + seatCap);
        this.serverSocket = new ServerSocket(port);
        this.seed = seed;
        this.deck = deck == null ? DeckType.STANDARD : deck;
        this.seatCap = seatCap;
        this.autoStart = autoStart;
    }

    /** The bound port (useful when constructed with port 0 to get an ephemeral one). */
    public int getPort() { return serverSocket.getLocalPort(); }

    /** The match host, or {@code null} while the lobby is still open. */
    public MatchHost getHost() { return host; }

    /** Whether the match has begun (the lobby is closed and actions are being relayed). */
    public synchronized boolean isStarted() { return host != null; }

    /** The roster as it stands, in seat order. */
    public synchronized MatchSetup getSetup() {
        List<SeatConfig> seats = new ArrayList<>(clients.size());
        for (ClientLink c : clients) seats.add(c.config);
        return new MatchSetup(seed, deck, seats);
    }

    /** Starts the accept loop on a daemon thread and returns; callers stay free to run their own UI. */
    public void listen() {
        Thread t = new Thread(this::acceptLoop, "match-server-accept");
        t.setDaemon(true);
        t.start();
    }

    private void acceptLoop() {
        while (running) {
            try {
                accept(serverSocket.accept());
            } catch (IOException e) {
                if (running) continue;   // a closed socket during shutdown is expected
                return;
            }
        }
    }

    /** Seats one arrival, or refuses it if the lobby is full or the match has already begun. */
    private void accept(Socket socket) throws IOException {
        ClientLink link;
        synchronized (this) {
            if (host != null || clients.size() >= seatCap) {
                try (PrintWriter out = new PrintWriter(
                        new java.io.OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {
                    out.println("ERR\t" + (host != null ? "the match has already started" : "the lobby is full"));
                }
                socket.close();
                return;
            }
            link = new ClientLink(new PlayerId(clients.size()), socket);
            clients.add(link);
        }
        link.startReader();   // the seat is announced once the client's JOIN arrives
    }

    /** Lobby lines: a seat's loadout, the host's deck pick, the host's start. Returns false if not a lobby line. */
    private synchronized boolean handleLobby(ClientLink from, String line) {
        if (line.startsWith("JOIN\t")) {
            from.config = MatchSetup.parseSeats(line.substring("JOIN\t".length())).get(0);
            from.send("SEAT\t" + from.id.seat());
            broadcastLobby();
            if (autoStart && clients.size() >= seatCap) begin();
            return true;
        }
        if (line.startsWith("LOADOUT\t")) {
            // A seat may re-pick its own sleeve and stake right up until the match starts, but never its name
            // or anyone else's loadout — the seat it edits is the one the line arrived on.
            try {
                SeatConfig picked = MatchSetup.parseSeats(from.config.name() + ":" + line.substring("LOADOUT\t".length())).get(0);
                from.config = picked;
                broadcastLobby();
            } catch (IllegalArgumentException e) {
                from.send("ERR\t" + e.getMessage());
            }
            return true;
        }
        if (line.startsWith("DECK\t")) {
            if (from.id.seat() != 0) { from.send("ERR\tonly the host chooses the deck"); return true; }
            try {
                deck = MatchSetup.parseDeck(line.substring("DECK\t".length()));
                broadcastLobby();
            } catch (IllegalArgumentException e) {
                from.send("ERR\t" + e.getMessage());
            }
            return true;
        }
        if (line.equals("BEGIN")) {
            if (from.id.seat() != 0) from.send("ERR\tonly the host can start the match");
            else if (clients.size() < MIN_SEATS) from.send("ERR\tneed at least " + MIN_SEATS + " players");
            else begin();
            return true;
        }
        return false;
    }

    /** Closes the lobby: builds the canonical match, then tells everyone to build the same one. */
    private synchronized void begin() {
        MatchSetup setup = getSetup();
        host = new MatchHost(Match.createSeated(setup.seed(), setup.seats(), setup.config()));
        host.start();
        broadcast("START\t" + setup.encode());
    }

    private synchronized void broadcastLobby() { broadcast("LOBBY\t" + getSetup().encode()); }

    private void broadcast(String line) { for (ClientLink c : clients) c.send(line); }

    /**
     * Handles a client's stream ending, however it ended. During the lobby a guest is simply removed and the
     * roster re-broadcast — but the <em>host</em> leaving takes the lobby with it, since the lobby only exists
     * inside the host's process; the guests are told and sent back to their menus rather than left staring at a
     * roster that can never start.
     *
     * <p>Once the match is running the seat cannot be removed — seat indices are baked into every logged action
     * — so the departure is submitted as a {@link Action.PlayerLeft} and broadcast like any other action. That is
     * what keeps the replays identical: every client learns of the drop at the same point in the same log,
     * rather than each noticing its own socket at its own time.
     */
    private synchronized void depart(ClientLink link) {
        if (!link.seated) return;   // already departed
        link.seated = false;

        if (host == null) {
            if (link.id.seat() == 0) { closeLobby("the host left"); return; }
            clients.remove(link);
            reseat();
            broadcastLobby();
            return;
        }
        try {
            Action left = new Action.PlayerLeft(link.id);
            host.submit(left);
            broadcast("SEQ" + (host.getLog().size() - 1) + "\t" + ActionCodec.encode(left));
        } catch (RuntimeException ignored) {
            // already recorded as gone, or the match is over — either way there is nothing to announce
        }
        link.close();
    }

    /** Tears the lobby down, telling every remaining guest why before dropping them. */
    private synchronized void closeLobby(String reason) {
        for (ClientLink c : clients) {
            if (c.seated) c.send("CLOSED\t" + reason);
            c.seated = false;
        }
        for (ClientLink c : clients) c.close();
        clients.clear();
    }

    /** Lobby only: re-indexes the remaining seats so they stay 0..n-1 with the earliest arrival hosting. */
    private void reseat() {
        for (int i = 0; i < clients.size(); i++) {
            ClientLink c = clients.get(i);
            if (c.id.seat() == i) continue;
            c.id = new PlayerId(i);
            c.send("SEAT\t" + i);   // the client's seat moved up; tell it before the roster arrives
        }
    }

    /** Submits one action to the host under a global lock, broadcasting on success or replying ERR on rejection. */
    private synchronized void handle(ClientLink from, String line) {
        if (handleLobby(from, line)) return;
        if (host == null) { from.send("ERR\tthe match has not started"); return; }

        Action action;
        try {
            action = ActionCodec.decode(line);
        } catch (RuntimeException e) {
            from.send("ERR\tmalformed: " + e.getMessage());
            return;
        }
        if (action.actor().seat() != from.id.seat()) {
            from.send("ERR\tseat mismatch: you are " + from.id.seat());
            return;
        }
        try {
            host.submit(action);
            String framed = "SEQ" + (host.getLog().size() - 1) + "\t" + line;
            broadcast(framed);
        } catch (RuntimeException e) {
            from.send("ERR\t" + e.getMessage());   // a rejection is private to the submitter
        }
    }

    @Override
    public void close() throws IOException {
        running = false;
        for (ClientLink c : clients) c.close();
        serverSocket.close();
    }

    /** One connected client: its seat, chosen loadout, socket, writer, and reader thread. */
    private final class ClientLink implements AutoCloseable {
        PlayerId id;         // reassigned only in the lobby, when an earlier seat leaves and the rest move up
        final Socket socket;
        final PrintWriter out;
        SeatConfig config;   // set by the client's JOIN; a placeholder until then
        boolean seated = true;   // cleared once this link has departed, so a drop is announced exactly once

        ClientLink(PlayerId id, Socket socket) throws IOException {
            this.id = id;
            this.socket = socket;
            this.config = SeatConfig.of("Player " + (id.seat() + 1));
            this.out = new PrintWriter(new java.io.OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        }

        void startReader() {
            Thread t = new Thread(() -> {
                try (BufferedReader in = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while (running && (line = in.readLine()) != null) handle(this, line);
                } catch (IOException ignored) {
                    // a dropped socket is just another way for the stream to end; departure is handled below
                } finally {
                    if (running) depart(this);
                }
            }, "client-" + id.seat());
            t.setDaemon(true);
            t.start();
        }

        synchronized void send(String line) { out.println(line); }

        @Override public void close() {
            try { socket.close(); } catch (IOException ignored) { }
        }
    }
}
