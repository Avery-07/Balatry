package client.game;

import client.MatchSnapshot;
import client.MatchViewModel;
import client.engine.CardEntity;
import debug.Log;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import model.game.MatchPhase;
import model.game.net.MatchClient;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The shippable Balatry client: a JavaFX {@link Canvas} redrawn every frame by an {@link AnimationTimer}. It is a
 * thin orchestrator over focused components — {@link Renderer} draws, {@link Hand} holds the animated cards,
 * {@link Ui} is the shared per-frame context, {@link Hud} is the persistent frame chrome, one {@link Screen} per
 * phase draws the center, and {@link Overlays} floats the contextual actions and standings. All layout, animation
 * and hit-testing live in the tested {@code client.engine}; only the {@code Renderer}'s drawing is unverified.
 */
public final class GameClient extends Application {

    private Renderer r;
    private final Hand hand = new Hand();
    private Ui ui;
    private final Hud hud = new Hud();
    private final Overlays overlays = new Overlays();
    private final Map<MatchPhase, Screen> screens = new EnumMap<>(MatchPhase.class);

    private MatchViewModel vm;
    private String fontFamily = "Monospaced";
    private long lastNanos;

    @Override
    public void start(Stage stage) {
        Canvas canvas = new Canvas(Ui.W, Ui.H);
        r = new Renderer(canvas.getGraphicsContext2D());
        ui = new Ui(r, hand);
        loadAssets();
        canvas.setOnMouseClicked(e -> handleClick(e.getX(), e.getY()));

        screens.put(MatchPhase.SELECTION, new SelectionScreen());
        screens.put(MatchPhase.BLIND, new BlindScreen());
        screens.put(MatchPhase.SHOP, new ShopScreen());
        screens.put(MatchPhase.RESULT, new ResultScreen());

        stage.setScene(new Scene(new StackPane(canvas), Ui.W, Ui.H));
        stage.setTitle("Balatry");
        stage.show();

        connect();

        new AnimationTimer() {
            @Override public void handle(long now) {
                double dt = lastNanos == 0 ? 0 : (now - lastNanos) / 1e9;
                lastNanos = now;
                hand.advance(dt);
                render();
            }
        }.start();
    }

    private void connect() {
        String host = System.getProperty("balatry.host", "localhost");
        int port = Integer.getInteger("balatry.port", 5555);
        long seed = Long.parseLong(System.getProperty("balatry.seed", "42"));
        List<String> players = List.of(System.getProperty("balatry.players", "P0,P1").split(","));
        try {
            MatchClient client = MatchClient.connect(host, port, seed, players,
                    err -> { Log.error(err); Platform.runLater(() -> ui.status = "ERR: " + err); },
                    a -> { if (vm != null) { Log.recv(a, vm.seat()); vm.onFrameApplied(); } });
            vm = new MatchViewModel(client);
            ui.vm = vm;
            vm.snapshotProperty().addListener((o, old, snap) -> { if (snap != null) onSnapshot(snap); });
            vm.refresh();
        } catch (Exception e) {
            ui.status = "Failed to connect: " + (e.getMessage() == null ? e : e.getMessage());
        }
    }

    /** FX thread: a new frame — reconcile the hand into retained entities (keeps selection + motion). */
    private void onSnapshot(MatchSnapshot snap) {
        ui.s = snap;
        hand.reconcile(snap.hand(), Ui.W - 90, Ui.H * 0.55);
    }

    private void render() {
        ui.newFrame();
        paintFelt();
        if (ui.s == null) { r.textCenter(ui.status.isEmpty() ? "Connecting…" : ui.status, Ui.W / 2.0, Ui.H / 2.0, 22, Palette.INK); return; }

        double cx = Ui.PAD + Ui.SIDEBAR + 18;
        double cTop = Ui.PAD + Ui.SLOT_H + 12;
        double cW = Ui.W - Ui.PAD - Ui.DECK_W - 18 - cx;
        double cH = Ui.H - Ui.PAD - cTop;

        hud.render(ui);
        if (ui.s.opening() != null) {
            overlays.pack(ui);   // a pack takes over the center until its picks are spent
        } else {
            Screen screen = screens.get(ui.s.phase());
            if (screen != null) screen.render(ui, cx, cTop, cW, cH);
            else r.textCenter(String.valueOf(ui.s.phase()), cx + cW / 2, cTop + cH / 2, 20, Palette.DIM);
            overlays.contextActions(ui);
        }
        if (!ui.status.isEmpty()) r.textLeft(ui.status, Ui.PAD + 8, Ui.H - 20, 12, Palette.DIM);
        if (ui.showRunInfo) overlays.runInfo(ui);
    }

    private void paintFelt() {
        r.gc().setFill(new javafx.scene.paint.RadialGradient(0, 0, 0.5, 0.1, 1.1, true,
                javafx.scene.paint.CycleMethod.NO_CYCLE,
                new javafx.scene.paint.Stop(0, Palette.FELT_A), new javafx.scene.paint.Stop(1, Palette.FELT_B)));
        r.gc().fillRect(0, 0, Ui.W, Ui.H);
    }

    private void handleClick(double x, double y) {
        if (ui.s == null) return;
        if (ui.s.opening() != null) {   // modal pack: only its option picks are live
            for (Ui.Btn b : ui.packButtons) if (b.rect().contains(x, y)) { b.action().run(); return; }
            return;
        }
        if (ui.showRunInfo) {
            for (Ui.Btn b : ui.buttons) if (b.rect().contains(x, y)) { b.action().run(); return; }
            ui.showRunInfo = false; return;
        }
        if (ui.s.phase() == MatchPhase.BLIND) {
            CardEntity e = hand.cardAt(x, y);
            if (e != null) {
                if (!e.selected() && hand.selectedCount() >= Ui.MAX_SELECTION) ui.status = "At most " + Ui.MAX_SELECTION + " cards.";
                else e.toggleSelected();
                return;
            }
        }
        for (Ui.Btn b : ui.buttons) if (b.rect().contains(x, y)) { b.action().run(); return; }   // buttons incl. contextual actions
        for (Ui.Sel se : ui.jokerSel) if (se.rect().contains(x, y)) {                             // click a joker to (de)select it
            ui.jokerTarget = (ui.jokerTarget == se.index()) ? -1 : se.index();
            return;
        }
        for (Ui.Sel se : ui.selectables) if (se.rect().contains(x, y)) {                          // click an item to (de)select it
            if (ui.selKind != null && ui.selKind.equals(se.kind()) && ui.selIndex == se.index()) ui.selKind = null;
            else { ui.selKind = se.kind(); ui.selIndex = se.index(); }
            return;
        }
        ui.selKind = null; ui.jokerTarget = -1;   // clicked empty space — clear item/joker selection
    }

    private void loadAssets() {
        try {
            var in = getClass().getResourceAsStream("/cards/deck.png");
            if (in != null) { Image img = new Image(in); if (!img.isError() && img.getWidth() > 0) r.cardSheet(img); }
        } catch (RuntimeException ignored) { }
        try {
            var in = getClass().getResourceAsStream("/font/game.ttf");
            if (in != null) { Font f = Font.loadFont(in, 14); if (f != null) fontFamily = f.getFamily(); }
        } catch (RuntimeException ignored) { }
        r.font(fontFamily);
    }

    public static void main(String[] args) { launch(args); }
}
