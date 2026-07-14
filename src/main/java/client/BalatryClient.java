package client;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.stage.Stage;
import model.game.net.MatchClient;

import java.util.List;

/**
 * The read-only debug client: the safest possible first FX surface. It connects to a {@link MatchServer}, wraps
 * the connection in a {@link MatchViewModel}, and renders the local seat's {@link MatchSnapshot} as a plain
 * text dump that refreshes whenever a frame arrives. There is <em>no input</em> — a view that only reads cannot
 * corrupt the model, and it doubles as a live diagnostic for the whole stack beneath it: if the
 * receive-thread → {@code Platform.runLater} → property → label path is wrong, this is where it shows,
 * with nothing else to blame.
 *
 * <p>Connection setup (the out-of-band lobby) comes from system properties, matching {@code ServerMain}:
 * <pre>
 *   -Dbalatry.host=localhost  -Dbalatry.port=5555  -Dbalatry.seed=42  -Dbalatry.players=P0,P1
 * </pre>
 * The seed and player list <em>must</em> match the server's, since the lockstep model requires an identical
 * starting match on every side. Launch via {@code mvn javafx:run} once per seat.
 */
public final class BalatryClient extends Application {

    @Override
    public void start(Stage stage) {
        String host = System.getProperty("balatry.host", "localhost");
        int port = Integer.getInteger("balatry.port", 5555);
        long seed = Long.parseLong(System.getProperty("balatry.seed", "42"));
        List<String> players = List.of(System.getProperty("balatry.players", "P0,P1").split(","));

        Label view = new Label("Connecting to " + host + ":" + port + " …");
        view.setStyle("-fx-font-family: monospace; -fx-font-size: 13px; -fx-padding: 12;");
        view.setWrapText(true);

        ScrollPane root = new ScrollPane(view);
        root.setFitToWidth(true);

        stage.setTitle("Balatry — debug view");
        stage.setScene(new Scene(root, 540, 720));
        stage.show();

        // vmRef lets the receive callback (wired inside connect) reach the view-model that wraps the client.
        // A frame arriving before the ref is set is simply skipped; the seeding refresh() below and the next
        // frame's full rebuild converge the view regardless.
        final MatchViewModel[] vmRef = new MatchViewModel[1];
        try {
            MatchClient client = MatchClient.connect(
                    host, port, seed, players,
                    err -> Platform.runLater(() -> view.setText("ERR: " + err + "\n\n" + view.getText())),
                    () -> { MatchViewModel v = vmRef[0]; if (v != null) v.onFrameApplied(); });

            MatchViewModel vm = new MatchViewModel(client);
            vmRef[0] = vm;
            vm.snapshotProperty().addListener((obs, old, snap) -> view.setText(render(snap)));
            vm.refresh();   // on the FX thread already (start()): seed the initial connected state
        } catch (Exception e) {
            view.setText("Failed to connect: " + e.getMessage());
        }
    }

    /** Renders a snapshot as a plain, monospace-friendly text dump. Read-only; no interaction. */
    private static String render(MatchSnapshot s) {
        if (s == null) return "(no state yet)";
        StringBuilder b = new StringBuilder();
        b.append("SEAT ").append(s.seat()).append("  (").append(s.name()).append(")\n");
        b.append("phase   : ").append(s.phase()).append("\n");
        b.append("ante    : ").append(s.ante()).append(" / ").append(s.anteCount()).append("\n");
        b.append("blind   : ").append(s.blind()).append("   target ").append(s.target()).append("\n");
        b.append("sin     : ").append(s.activeSin()).append("\n");
        if (s.boss() != null) b.append("boss    : ").append(s.boss()).append("\n");
        b.append("tag     : ").append(s.skipTag()).append("\n");
        b.append("money   : $").append(s.money()).append("\n");

        if (s.round() != null) {
            MatchSnapshot.RoundView r = s.round();
            b.append("round   : hands ").append(r.handsRemaining())
             .append(", discards ").append(r.discardsRemaining())
             .append(", score ").append(r.score())
             .append(" / ").append(r.roundTarget()).append("\n");
        }

        b.append("\nhand        : ").append(s.hand()).append("\n");
        b.append("jokers      : ").append(s.jokers()).append("\n");
        b.append("consumables : ").append(s.consumables()).append("\n");
        b.append("relics      : ").append(s.relics()).append("\n");
        if (s.inShop()) b.append("(in shop)\n");

        b.append("\n--- opponents (visible only) ---\n");
        for (MatchSnapshot.OpponentView o : s.opponents()) {
            b.append("  #").append(o.seat()).append(' ').append(o.name())
             .append("  points ").append(o.points())
             .append("  rank ").append(o.rank() < 0 ? "-" : (o.rank() + 1)).append("\n");
        }
        return b.toString();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
