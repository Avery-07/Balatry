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
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import model.game.MatchPhase;
import model.game.net.HostedMatch;
import model.game.net.MatchClient;
import model.game.net.MatchServer;

import java.util.EnumMap;
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

    private final Menu menu = new Menu();
    private MatchClient client;
    private HostedMatch hosted;      // non-null when this client is the one hosting
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
        canvas.setOnMouseMoved(e -> { ui.mouseX = e.getX(); ui.mouseY = e.getY(); });

        screens.put(MatchPhase.SELECTION, new SelectionScreen());
        screens.put(MatchPhase.BLIND, new BlindScreen());
        screens.put(MatchPhase.SHOP, new ShopScreen());
        screens.put(MatchPhase.RESULT, new ResultScreen());
        screens.put(MatchPhase.FINISHED, new FinishedScreen());

        menu.onHost = this::hostGame;
        menu.onJoin = this::joinGame;
        menu.onBegin = () -> { if (client != null) client.begin(); };
        menu.onLeave = this::leaveLobby;
        ui.onLeaveMatch = this::leaveLobby;   // the same teardown; from a finished match it just returns to the menu
        menu.onDeckChange = d -> { if (client != null) client.chooseDeck(d); };
        menu.onLoadoutChange = () -> { if (client != null) client.setLoadout(menu.sleeve, menu.stake); };

        Scene scene = new Scene(new StackPane(canvas), Ui.W, Ui.H);
        scene.setOnKeyTyped(e -> { if (!inMatch()) menu.onChar(e.getCharacter()); });
        scene.setOnKeyPressed(e -> { if (!inMatch() && e.getCode() == KeyCode.BACK_SPACE) menu.onBackspace(); });
        stage.setScene(scene);
        stage.setTitle("Balatry");
        stage.setOnCloseRequest(e -> shutdown());
        stage.show();

        new AnimationTimer() {
            @Override public void handle(long now) {
                double dt = lastNanos == 0 ? 0 : (now - lastNanos) / 1e9;
                lastNanos = now;
                hand.advance(dt);
                hud.advance(dt);
                render();
            }
        }.start();
    }

    /** Whether a match is live; until then the menu owns the screen, the keyboard and the clicks. */
    private boolean inMatch() { return client != null && client.isStarted(); }

    /** Opens a lobby in this process and joins it as seat 0, so this player picks who plays and when to start. */
    private void hostGame() {
        connect(callbacks -> {
            hosted = HostedMatch.start(HostedMatch.DEFAULT_PORT, new java.util.Random().nextLong(),
                    menu.deck, MatchServer.MAX_SEATS, menu.seatConfig(), callbacks);
            menu.host = true;
            menu.hostAddress = HostedMatch.lanAddress() + ":" + hosted.server().getPort();
            return hosted.client();
        });
    }

    /** Joins someone else's lobby at the typed address. */
    private void joinGame() {
        connect(callbacks -> {
            String[] hostPort = menu.address.trim().split(":");
            int port = hostPort.length > 1 ? Integer.parseInt(hostPort[1].trim()) : HostedMatch.DEFAULT_PORT;
            menu.host = false;
            return MatchClient.join(hostPort[0].trim(), port, menu.seatConfig(), callbacks);
        });
    }

    /** Shared connect path: builds the callbacks, runs the supplied opener, and lands the client in the lobby. */
    private void connect(Opener opener) {
        if (client != null) return;   // already connected; ignore a double click
        MatchClient.Callbacks callbacks = new MatchClient.Callbacks(
                setup   -> Platform.runLater(() -> menu.applyLobby(setup)),
                ()      -> Platform.runLater(this::onMatchStarted),
                action  -> {
                    if (vm == null) return;
                    Log.recv(action, vm.seat());
                    if (action instanceof model.game.actions.Action.PlayerLeft left) announceDeparture(left);
                    vm.onFrameApplied();
                },
                err     -> { Log.error(err); Platform.runLater(() -> { menu.status = "ERR: " + err; ui.status = "ERR: " + err; }); },
                reason  -> Platform.runLater(() -> onConnectionClosed(reason)));
        try {
            client = opener.open(callbacks);
            menu.seat = client.getSeat().seat();
            menu.mode = Menu.Mode.LOBBY;
            menu.status = "";
        } catch (Exception e) {
            client = null;
            menu.status = "ERR: could not connect — " + (e.getMessage() == null ? e : e.getMessage());
        }
    }

    /**
     * FX thread: the connection ended. In the lobby that means the host closed it — there is nothing left to
     * wait for, so guests are returned to the menu with the reason. In a live match the local model is still a
     * complete, finished replay, so the screen is left alone and the loss is reported in the status line.
     */
    private void onConnectionClosed(String reason) {
        if (client == null) return;             // we tore the connection down ourselves; nothing to report
        if (inMatch()) { ui.status = reason; return; }
        leaveLobby();
        menu.status = reason;
    }

    /**
     * Names the seat that just left in the status line. Read off the local model, which has already replayed the
     * departure — the action itself carries only a seat index.
     */
    private void announceDeparture(model.game.actions.Action.PlayerLeft left) {
        String who = client.getLocalHost().getMatch().getPlayer(left.actor()).name();
        Platform.runLater(() -> ui.status = who + " left the match.");
    }

    /** FX thread: the server closed the lobby and everyone has built the same match. */
    private void onMatchStarted() {
        vm = new MatchViewModel(client);
        ui.vm = vm;
        vm.snapshotProperty().addListener((o, old, snap) -> { if (snap != null) onSnapshot(snap); });
        vm.refresh();
    }

    /** Opens a connection; separated so hosting and joining share the callback wiring and error handling. */
    @FunctionalInterface
    private interface Opener { MatchClient open(MatchClient.Callbacks callbacks) throws Exception; }

    /**
     * Back to the main menu: drops the connection (and the hosted lobby with it) and clears all match state.
     * Closing the socket is what tells the server we left — it turns that into the broadcast {@code PlayerLeft}
     * everyone else replays, so there is no separate "goodbye" message to get lost.
     */
    private void leaveLobby() {
        shutdown();
        client = null;
        hosted = null;
        vm = null;
        ui.vm = null;
        ui.s = null;
        ui.status = "";
        menu.mode = Menu.Mode.MAIN;
        menu.lobby = null;
        menu.host = false;
        menu.seat = -1;
        menu.status = "";
    }

    private void shutdown() {
        try {
            if (hosted != null) hosted.close();
            else if (client != null) client.close();
        } catch (Exception ignored) {
            // shutting down anyway; a failed close has nothing left to affect
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
        if (!inMatch()) { menu.render(ui); return; }          // menu and lobby own the screen until the match begins
        if (ui.s == null) { r.textCenter("Dealing…", Ui.W / 2.0, Ui.H / 2.0, 22, Palette.INK); return; }

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

        // Hover layers, last so they sit above everything: the deck's contents, then any tooltip.
        if (ui.hovered(ui.deckRect)) overlays.deckContents(ui);
        else overlays.tooltip(ui);
    }

    private void paintFelt() {
        r.gc().setFill(new javafx.scene.paint.RadialGradient(0, 0, 0.5, 0.1, 1.1, true,
                javafx.scene.paint.CycleMethod.NO_CYCLE,
                new javafx.scene.paint.Stop(0, Palette.FELT_A), new javafx.scene.paint.Stop(1, Palette.FELT_B)));
        r.gc().fillRect(0, 0, Ui.W, Ui.H);
    }

    private void handleClick(double x, double y) {
        if (!inMatch()) {   // menu/lobby: only the widgets it registered this frame are live
            for (Ui.Btn b : ui.buttons) if (b.rect().contains(x, y)) { b.action().run(); return; }
            menu.focus = Menu.Focus.NONE;   // clicked outside every field — drop the caret
            return;
        }
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
