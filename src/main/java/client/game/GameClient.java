package client.game;

import client.MatchSnapshot;
import client.MatchViewModel;
import client.engine.CardEntity;
import client.engine.TileRow;
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

    private final Menu menu = new Menu();
    private final client.engine.Fader fader = new client.engine.Fader();
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
        buildDragTargets();
        loadAssets();
        canvas.setOnMouseClicked(e -> handleClick(e.getX(), e.getY()));
        canvas.setOnMouseMoved(e -> { ui.mouseX = e.getX(); ui.mouseY = e.getY(); });
        canvas.setOnMousePressed(e -> handlePress(e.getX(), e.getY()));
        canvas.setOnMouseDragged(e -> { ui.mouseX = e.getX(); ui.mouseY = e.getY(); handleDrag(e.getX(), e.getY()); });
        canvas.setOnMouseReleased(e -> handleRelease(e.getX(), e.getY()));

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
                menu.advance(dt);
                fader.advance(dt);
                ui.jokerRow.advance(dt);
                ui.itemRow.advance(dt);
                ui.shopSlotRow.advance(dt);
                ui.shopVoucherRow.advance(dt);
                ui.shopPackRow.advance(dt);
                boolean wasPlaying = ui.reel.playing();
                ui.reel.advance(dt);
                if (wasPlaying && !ui.reel.playing()) hand.releaseStaged();   // reel drained: let the cards fly
                render();
            }
        }.start();
    }

    /** Whether a match is live; until then the menu owns the screen, the keyboard and the clicks. */
    private boolean inMatch() { return client != null && client.isStarted(); }

    // --- drag & drop --------------------------------------------------------
    // One grammar for the whole table: hand cards, jokers, inventory items and shop tiles all lift with the
    // cursor and glide. The hand manages its own entities; every other draggable surface registers itself as a
    // DragTarget — the row, whether it is live right now, and what a landed drop does — so adding a new
    // draggable area is one list entry, not a set of string-keyed switch cases.

    private static final double DRAG_THRESHOLD = 7;   // pixels of travel before a press becomes a drag

    /** What a drop that landed in the row's band does; shop shelves land nowhere and pass NOWHERE. */
    @FunctionalInterface private interface Drop { void land(int from, int slot); }
    private static final Drop NOWHERE = (from, slot) -> { };

    /** One draggable surface: its row, whether it can be grabbed right now, and its drop policy. */
    private record DragTarget(TileRow row, java.util.function.BooleanSupplier active, Drop onDrop) { }

    private List<DragTarget> dragTargets;   // built in start(), once ui exists

    private double pressX, pressY;
    private boolean pressOnHand;
    private DragTarget pressTarget;        // the row the press landed on, or null
    private boolean draggingHand;
    private DragTarget dragTarget;         // the active row drag, once the threshold is crossed
    private int dragFrom = -1;             // the dragged tile's model index, set at beginDrag
    private boolean suppressClick;         // a completed drag must not also fire as a click

    private void buildDragTargets() {
        dragTargets = List.of(
                new DragTarget(ui.jokerRow, () -> true,
                        (from, slot) -> { if (slot != from) vm.moveJoker(from, slot); }),
                new DragTarget(ui.itemRow, () -> true, this::dropItem),
                new DragTarget(ui.shopSlotRow, () -> ui.s.inShop(), NOWHERE),
                new DragTarget(ui.shopVoucherRow, () -> ui.s.inShop(), NOWHERE),
                new DragTarget(ui.shopPackRow, () -> ui.s.inShop(), NOWHERE));
    }

    /** Records what the press landed on; nothing moves until the cursor travels past the threshold. */
    private void handlePress(double x, double y) {
        pressX = x; pressY = y;
        pressOnHand = false; pressTarget = null;
        if (!inMatch() || ui.s == null || ui.s.opening() != null || ui.showRunInfo) return;

        if (ui.s.phase() == MatchPhase.BLIND && hand.cardAt(x, y) != null) { pressOnHand = true; return; }
        for (DragTarget t : dragTargets)
            if (t.active().getAsBoolean() && t.row().tileAt(x, y) != -1) { pressTarget = t; return; }
    }

    /** Once the cursor travels far enough, the press becomes a drag; from then on the drag owns the cursor. */
    private void handleDrag(double x, double y) {
        boolean pending = pressOnHand || pressTarget != null;
        if (!draggingHand && dragTarget == null && pending
                && Math.hypot(x - pressX, y - pressY) > DRAG_THRESHOLD) {
            if (pressOnHand) {
                draggingHand = hand.beginDrag(pressX, pressY);
            } else {
                dragFrom = pressTarget.row().beginDrag(pressX, pressY);
                if (dragFrom != -1) dragTarget = pressTarget;
            }
            pressOnHand = false; pressTarget = null;
        }
        if (draggingHand) hand.dragTo(x, y);
        else if (dragTarget != null) dragTarget.row().dragTo(x, y);
    }

    /**
     * Ends a drag: the hand commits its new order; a row asks its drop policy what a landed slot means. An
     * invalid or nowhere drop needs no handling — the next layout glides the tile back where it belongs.
     */
    private void handleRelease(double x, double y) {
        if (!draggingHand && dragTarget == null) { pressOnHand = false; pressTarget = null; return; }
        suppressClick = true;
        if (draggingHand) {
            hand.endDrag();
        } else {
            int slot = dragTarget.row().endDrag(x, y);
            if (slot != -1) dragTarget.onDrop().land(dragFrom, slot);
        }
        draggingHand = false; dragTarget = null; dragFrom = -1;
    }

    /**
     * An inventory drop: consumables reorder among consumables, relics among relics — the two share the display
     * row but live in different model lists, so a cross-class drop just glides back with a hint.
     */
    private void dropItem(int from, int slot) {
        if (slot == from || slot >= ui.s.inventory().size()) return;
        MatchSnapshot.ItemView src = ui.s.inventory().get(from);
        MatchSnapshot.ItemView dst = ui.s.inventory().get(slot);
        if (dst.isRelic() != src.isRelic()) { ui.status = "Consumables and relics keep to their own areas."; return; }
        if (src.isRelic()) vm.moveRelic(src.modelIndex(), dst.modelIndex());
        else vm.moveConsumable(src.modelIndex(), dst.modelIndex());
    }

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
            fader.start(() -> menu.enterMode(Menu.Mode.LOBBY));
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

    /** FX thread: the server closed the lobby and everyone has built the same match — fade into it. */
    private void onMatchStarted() {
        fader.start(() -> {
            vm = new MatchViewModel(client);
            ui.vm = vm;
            vm.snapshotProperty().addListener((o, old, snap) -> { if (snap != null) onSnapshot(snap); });
            vm.refresh();
        });
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
        fader.start(() -> {
            shutdown();
            client = null;
            hosted = null;
            vm = null;
            ui.vm = null;
            ui.s = null;
            ui.status = "";
            menu.enterMode(Menu.Mode.MAIN);
            menu.lobby = null;
            menu.host = false;
            menu.seat = -1;
            menu.status = "";
        });
    }

    private void shutdown() {
        try {
            if (hosted != null) hosted.close();
            else if (client != null) client.close();
        } catch (Exception ignored) {
            // shutting down anyway; a failed close has nothing left to affect
        }
    }

    /**
     * FX thread: a new frame — reconcile the hand into retained entities (keeps selection + motion). A frame
     * carrying a <em>new</em> scoring timeline starts the reel: the model has already banked the score and dealt
     * fresh cards, so the client holds the played cards centre-screen and replays how that score was reached.
     */
    private void onSnapshot(MatchSnapshot snap) {
        MatchSnapshot previous = ui.s;
        ui.s = snap;
        hand.reconcile(snap.hand(), Ui.W - 90, Ui.H * 0.55);

        boolean newTimeline = !snap.lastPlay().isEmpty()
                && (previous == null || !snap.lastPlay().equals(previous.lastPlay()));
        if (newTimeline) {
            ui.reelEvents = snap.lastPlay();
            ui.reel.play(snap.lastPlay().size());
        }
    }

    private void render() {
        ui.newFrame();
        paintFelt();
        if (!inMatch()) { menu.render(ui); drawFade(); return; }   // menu and lobby own the screen until the match begins
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

        // The scoring reel: the played cards stand centre-screen and the acting card's effect square floats.
        if (hand.hasStaged()) hand.renderStaged(ui, cx, cTop + cH * 0.34, cW);
        if (ui.reel.playing()) overlays.scoreEffect(ui);

        // Hover layers, last so they sit above everything: the deck's contents, then any tooltip.
        if (ui.hovered(ui.deckRect)) overlays.deckContents(ui);
        else overlays.tooltip(ui);

        drawFade();
    }

    /** The fade-to-black transition overlay; the screen switch itself happens inside the Fader at full black. */
    private void drawFade() {
        double a = fader.alpha();
        if (a <= 0) return;
        r.gc().setFill(javafx.scene.paint.Color.web("#000000", a));
        r.gc().fillRect(0, 0, Ui.W, Ui.H);
    }

    /** The table felt — a constant gradient, built once (this paints 60x/sec; no per-frame allocation). */
    private static final javafx.scene.paint.RadialGradient FELT = new javafx.scene.paint.RadialGradient(
            0, 0, 0.5, 0.1, 1.1, true, javafx.scene.paint.CycleMethod.NO_CYCLE,
            new javafx.scene.paint.Stop(0, Palette.FELT_A), new javafx.scene.paint.Stop(1, Palette.FELT_B));

    private void paintFelt() {
        r.gc().setFill(FELT);
        r.gc().fillRect(0, 0, Ui.W, Ui.H);
    }

    private void handleClick(double x, double y) {
        if (suppressClick) { suppressClick = false; return; }   // this click was the tail end of a drag
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
