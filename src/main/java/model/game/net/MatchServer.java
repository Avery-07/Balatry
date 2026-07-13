package model.game.net;

import model.game.actions.Action;
import model.game.host.MatchHost;
import model.game.player.PlayerId;

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
 * A minimal thread-per-client TCP server around a {@link MatchHost}: accepts a fixed number of players, assigns
 * each the next seat, then relays every submitted {@link Action} line into the host and broadcasts the accepted
 * action back to all clients (so each runs the same deterministic replay). Thread-per-client is more than
 * enough for a 2-8 seat game; the host's own synchronization makes submission the serialization point.
 *
 * <p>Wire protocol, newline-delimited UTF-8: on connect the server sends {@code SEAT<n>}; thereafter clients
 * send action lines ({@link ActionCodec}) and receive {@code SEQ<n>\t<action-line>} for every accepted action
 * plus {@code ERR\t<reason>} for their own rejections. State is not pushed — clients derive it by replay.
 */
public final class MatchServer implements AutoCloseable {

    private final MatchHost host;
    private final ServerSocket serverSocket;
    private final List<ClientLink> clients = new ArrayList<>();
    private volatile boolean running = true;

    public MatchServer(MatchHost host, int port) throws IOException {
        this.host = host;
        this.serverSocket = new ServerSocket(port);
    }

    /** The bound port (useful when constructed with port 0 to get an ephemeral one). */
    public int getPort() { return serverSocket.getLocalPort(); }

    /**
     * Accepts exactly {@code seatCount} clients, assigning seats in connection order, then starts the match.
     * Blocks until every seat is filled. Each accepted client gets a reader thread relaying its lines.
     */
    public void awaitPlayersAndStart(int seatCount) throws IOException {
        for (int seat = 0; seat < seatCount; seat++) {
            Socket socket = serverSocket.accept();
            ClientLink link = new ClientLink(new PlayerId(seat), socket);
            clients.add(link);
            link.send("SEAT" + seat);
            link.startReader();
        }
        host.start();
    }

    /** Submits one action to the host under a global lock, broadcasting on success or replying ERR on rejection. */
    private synchronized void handle(ClientLink from, String line) {
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
            for (ClientLink c : clients) c.send(framed);
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

    /** One connected client: its seat, socket, writer, and reader thread. */
    private final class ClientLink implements AutoCloseable {
        final PlayerId id;
        final Socket socket;
        final PrintWriter out;

        ClientLink(PlayerId id, Socket socket) throws IOException {
            this.id = id;
            this.socket = socket;
            this.out = new PrintWriter(new java.io.OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        }

        void startReader() {
            Thread t = new Thread(() -> {
                try (BufferedReader in = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while (running && (line = in.readLine()) != null) handle(this, line);
                } catch (IOException ignored) {
                    // client dropped; a fuller build would surface a disconnect action to the host
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
