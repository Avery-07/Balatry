package client;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.Scene;
import javafx.stage.Stage;
import model.game.MatchPhase;
import model.game.net.MatchClient;

import java.util.ArrayList;
import java.util.List;

/**
 * The Balatry debug client. Connects to a {@link MatchServer}, renders the local seat's {@link MatchSnapshot}
 * as a text status panel, and exposes the BLIND-phase gestures — play, discard, finish — with the hand shown as
 * clickable toggle cards. Selecting cards and pressing Play or Discard submits the chosen hand indices; the
 * result returns as a broadcast frame and repaints through the normal refresh loop.
 *
 * <p>Selection rules mirror Balatro: one shared selection feeds both Play and Discard, capped at five cards (a
 * sixth toggle is refused), and it clears on every refresh because the hand redraws after each play or discard.
 * A rejected action (out-of-range, wrong phase) surfaces as an {@code ERR} line and changes nothing. This is a
 * debug surface, not the finished game UI.
 *
 * <p>Connection setup from system properties, matching {@code ServerMain}:
 * {@code -Dbalatry.host -Dbalatry.port -Dbalatry.seed -Dbalatry.players}. Seed and roster must match the
 * server's. Launch via {@code mvn javafx:run}, once per seat.
 */
public final class BalatryClient extends Application {

    private static final int MAX_SELECTION = 5;

    @Override
    public void start(Stage stage) {
        String host = System.getProperty("balatry.host", "localhost");
        int port = Integer.getInteger("balatry.port", 5555);
        long seed = Long.parseLong(System.getProperty("balatry.seed", "42"));
        List<String> players = List.of(System.getProperty("balatry.players", "P0,P1").split(","));

        Label status = new Label("Connecting to " + host + ":" + port + " \u2026");
        status.setStyle("-fx-font-family: monospace; -fx-font-size: 13px;");
        status.setWrapText(true);

        FlowPane handRow = new FlowPane(6, 6);
        final List<ToggleButton> cards = new ArrayList<>();

        Button play = new Button("Play");
        Button discard = new Button("Discard");
        Button finish = new Button("Finish Round");
        play.setDisable(true);
        discard.setDisable(true);
        finish.setDisable(true);

        HBox buttons = new HBox(8);
        buttons.getChildren().addAll(play, discard, finish);

        VBox root = new VBox(10);
        root.setStyle("-fx-padding: 12;");
        root.getChildren().addAll(status, handRow, buttons);

        ScrollPane scroller = new ScrollPane(root);
        scroller.setFitToWidth(true);

        stage.setTitle("Balatry \u2014 debug view");
        stage.setScene(new Scene(scroller, 560, 780));
        stage.show();

        final MatchViewModel[] vmRef = new MatchViewModel[1];
        try {
            MatchClient client = MatchClient.connect(
                    host, port, seed, players,
                    err -> Platform.runLater(() -> status.setText("ERR: " + err + "\n\n" + status.getText())),
                    () -> { MatchViewModel v = vmRef[0]; if (v != null) v.onFrameApplied(); });

            MatchViewModel vm = new MatchViewModel(client);
            vmRef[0] = vm;

            play.setOnAction(e -> {
                List<Integer> sel = selectedIndices(cards);
                if (sel.isEmpty()) { hint(status, "Select 1-5 cards to play."); return; }
                vm.playHand(sel);
            });
            discard.setOnAction(e -> {
                List<Integer> sel = selectedIndices(cards);
                if (sel.isEmpty()) { hint(status, "Select 1-5 cards to discard."); return; }
                vm.discard(sel);
            });
            finish.setOnAction(e -> vm.finishRound());

            vm.snapshotProperty().addListener((obs, old, snap) -> {
                status.setText(render(snap));
                rebuildHand(handRow, cards, snap, status);
                boolean inBlind = snap != null && snap.phase() == MatchPhase.BLIND && snap.round() != null;
                play.setDisable(!inBlind);
                discard.setDisable(!inBlind);
                finish.setDisable(!inBlind);
            });
            vm.refresh();   // on the FX thread already (start()): seed the initial connected state
        } catch (Exception e) {
            status.setText("Failed to connect: " + e.getMessage());
        }
    }

    /** Rebuilds the hand as fresh toggle cards, clearing any prior selection. Toggle position == hand index. */
    private static void rebuildHand(FlowPane row, List<ToggleButton> cards, MatchSnapshot snap, Label status) {
        cards.clear();
        row.getChildren().clear();
        if (snap == null) return;
        List<String> hand = snap.hand();
        for (int i = 0; i < hand.size(); i++) {
            ToggleButton tb = new ToggleButton(i + ": " + hand.get(i));
            tb.setStyle("-fx-font-family: monospace;");
            tb.setOnAction(e -> {
                if (tb.isSelected() && selectedIndices(cards).size() > MAX_SELECTION) {
                    tb.setSelected(false);
                    hint(status, "At most " + MAX_SELECTION + " cards.");
                }
            });
            cards.add(tb);
            row.getChildren().add(tb);
        }
    }

    /** The hand indices whose toggle is currently selected, in hand order. */
    private static List<Integer> selectedIndices(List<ToggleButton> cards) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < cards.size(); i++) if (cards.get(i).isSelected()) out.add(i);
        return out;
    }

    private static void hint(Label status, String msg) {
        status.setText(msg + "\n\n" + status.getText());
    }

    /** Renders a snapshot as a plain, monospace-friendly text dump. The hand itself is shown as toggle cards. */
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

        b.append("\njokers      : ").append(s.jokers()).append("\n");
        b.append("consumables : ").append(s.consumables()).append("\n");
        b.append("relics      : ").append(s.relics()).append("\n");
        if (s.inShop()) b.append("(in shop)\n");

        b.append("\n--- opponents (visible only) ---\n");
        for (MatchSnapshot.OpponentView o : s.opponents()) {
            b.append("  #").append(o.seat()).append(' ').append(o.name())
             .append("  points ").append(o.points())
             .append("  rank ").append(o.rank() < 0 ? "-" : (o.rank() + 1)).append("\n");
        }
        b.append("\nSelect cards above, then Play or Discard.");
        return b.toString();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
