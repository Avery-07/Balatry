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

    // Dev/cheat overlay for local testing, toggled with '*'. Gated so it can't be opened in a real match by accident:
    // set the BALATRY_DEV env var (inherited by the forked app JVM) or -Dbalatry.dev=true.
    private static final boolean DEV = "true".equalsIgnoreCase(System.getenv("BALATRY_DEV")) || Boolean.getBoolean("balatry.dev");
    private final DevPanel dev = new DevPanel();
    private final AnteBanner anteBanner = new AnteBanner();
    private MatchPhase lastPhase;   // for logging phase transitions
    private final Background background = new Background();   // the looping animated backdrop
    private final client.engine.Particles particles = new client.engine.Particles(52, Ui.W, Ui.H, 20260803L);   // floating motes over it
    private String bgKey = "";                                // the game-state key the current backdrop theme reflects
    private MatchClient client;
    private HostedMatch hosted;      // non-null when this client is the one hosting
    private boolean leaving;         // set while we tear our own connection down, so an involuntary-drop handler can tell the two apart
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
        menu.onSinsChange = on -> { if (client != null) client.chooseSins(on); };
        menu.onLoadoutChange = () -> { if (client != null) client.setLoadout(menu.sleeve, menu.stake); };

        Scene scene = new Scene(new StackPane(canvas), Ui.W, Ui.H);
        scene.setOnKeyTyped(e -> {
            if (DEV && "*".equals(e.getCharacter())) { dev.toggle(); return; }   // dev overlay toggle, any phase
            if (!inMatch()) menu.onChar(e.getCharacter());
        });
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
                anteBanner.advance(dt);
                fader.advance(dt);
                background.advance(dt);
                particles.advance(dt);
                ui.jokerRow.advance(dt);
                ui.itemRow.advance(dt);
                ui.shopSlotRow.advance(dt);
                ui.shopVoucherRow.advance(dt);
                ui.shopPackRow.advance(dt);
                ui.packRow.advance(dt);
                ui.now += dt;   // shared idle clock for the tile sway/bob
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
    private DragTarget packDrag;            // the open pack's card row — draggable only while the modal is up, lands nowhere

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
        packDrag = new DragTarget(ui.packRow, () -> true, NOWHERE);   // handled specially: only during a pack opening
    }

    /** Records what the press landed on; nothing moves until the cursor travels past the threshold. */
    private void handlePress(double x, double y) {
        pressX = x; pressY = y;
        pressOnHand = false; pressTarget = null;
        if (!inMatch() || ui.s == null || ui.showRunInfo) return;
        if (ui.atBlindBarrier() && ui.s.opening() == null) return;   // the barrier blocks input, except while opening a pack

        boolean pack = ui.s.opening() != null;
        if (pack && ui.packRow.tileAt(x, y) != -1) { pressTarget = packDrag; return; }   // a pack option lifts & glides
        // Hand cards reorder in the blind, and in a pack's dealt targeting hand.
        if ((ui.s.phase() == MatchPhase.BLIND || pack) && hand.cardAt(x, y) != null) { pressOnHand = true; return; }
        // Jokers and consumables reorder at any time (including during a pack); the shop rows only in the shop, and
        // are never reachable through the pack modal.
        for (DragTarget t : dragTargets) {
            if (pack && t.row() != ui.jokerRow && t.row() != ui.itemRow) continue;
            if (t.active().getAsBoolean() && t.row().tileAt(x, y) != -1) { pressTarget = t; return; }
        }
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
            Log.error("connect failed: " + e);
            menu.status = "ERR: could not connect — " + (e.getMessage() == null ? e : e.getMessage());
        }
    }

    /**
     * FX thread: the connection ended. In the lobby that means the host closed it — there is nothing left to
     * wait for, so guests are returned to the menu with the reason. In a live match the local model is still a
     * complete, finished replay, so the screen is left alone and the loss is reported in the status line.
     */
    private void onConnectionClosed(String reason) {
        Log.phase("CONNECTED", "CLOSED: " + reason);
        if (client == null || leaving) return;   // we tore the connection down ourselves; nothing to report
        if (inMatch()) {
            // An involuntary mid-match drop (the host surrendered / the server died). With no reconnect, resolve it
            // locally as a forfeit by everyone we can no longer hear: mark the other seats departed so this seat is
            // the last one standing and the finished screen reads VICTORY (Match.displayRanking sinks the departed).
            var host = client.getLocalHost();
            if (host != null && host.getMatch().getPhase() != MatchPhase.FINISHED) {
                model.game.Match m = host.getMatch();
                for (model.game.player.PlayerId id : new java.util.ArrayList<>(m.getActiveSeats()))
                    if (!id.equals(client.getSeat())) m.markDeparted(id);
                if (vm != null) vm.refresh();
            }
            ui.status = reason;
            return;
        }
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
        Log.phase("LOBBY", "MATCH");
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
        leaving = true;   // set synchronously so an involuntary-drop callback racing the teardown knows this was us
        fader.start(() -> {
            shutdown();
            client = null;
            hosted = null;
            vm = null;
            ui.vm = null;
            ui.s = null;
            ui.status = "";
            ui.showOptions = false;
            ui.showCollection = false;
            ui.confirmSurrender = false;
            background.transitionTo(BackgroundTheme.DEFAULT, 1.0);   // back to the neutral menu field
            bgKey = "";
            particles.speedScale = 1.0;
            menu.enterMode(Menu.Mode.MAIN);
            menu.lobby = null;
            menu.host = false;
            menu.seat = -1;
            menu.status = "";
            leaving = false;
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
        updateBackground(snap);

        // A new ante took over: announce the sin handover (old -> new), unless sins are off.
        if (previous != null && snap.ante() > previous.ante() && !"None".equals(snap.activeSin())) {
            anteBanner.trigger(snap.ante(), previous.activeSin(), snap.activeSin(), snap.activeSinDesc());
            Log.phase("ante " + previous.ante() + " (" + previous.activeSin() + ")", "ante " + snap.ante() + " (" + snap.activeSin() + ")");
        }

        boolean newTimeline = !snap.lastPlay().isEmpty()
                && (previous == null || !snap.lastPlay().equals(previous.lastPlay()));
        if (newTimeline) {
            ui.reelEvents = snap.lastPlay();
            // The round total as it stood before this play; the score readout stays here while the reel climbs and
            // only tallies up to the new total once the boxes have finished (see Hud).
            ui.reelBaseScore = roundScore(previous);
            ui.reel.play(snap.lastPlay().size());
        }
    }

    /**
     * Steers the backdrop and its motes to match the game state: each phase (and each blind kind) has its own
     * {@link BackgroundTheme}, morphed to over ~1.2s when the state changes, and the motes are recoloured and — on
     * a boss blind — stirred up. Keyed so a transition fires once per real change, not every frame.
     */
    private void updateBackground(MatchSnapshot s) {
        boolean boss = s.phase() == MatchPhase.BLIND && "BOSS".equals(s.blind());
        String key = s.phase() + (s.phase() == MatchPhase.BLIND ? "/" + s.blind() : "");
        if (key.equals(bgKey)) return;
        bgKey = key;
        background.transitionTo(themeFor(s), 1.2);
        particles.speedScale = boss ? 1.8 : 1.0;
    }

    /** The backdrop theme for a game state. */
    private static BackgroundTheme themeFor(MatchSnapshot s) {
        return switch (s.phase()) {
            case BLIND -> "BOSS".equals(s.blind()) ? BackgroundTheme.BOSS_PHASE
                        : "BIG".equals(s.blind())  ? BackgroundTheme.BIG_BLIND
                        :                            BackgroundTheme.SMALL_BLIND;
            case SHOP     -> BackgroundTheme.SHOP;
            case RESULT   -> BackgroundTheme.RESULT;
            case FINISHED -> BackgroundTheme.FINISHED;
            default       -> BackgroundTheme.DEFAULT;   // SELECTION and the pre-match lobby: the neutral field
        };
    }

    /** The round score a snapshot carried, as a number; 0 outside a round or when unparseable. */
    private static double roundScore(MatchSnapshot snap) {
        if (snap == null || snap.round() == null) return 0;
        try { return Double.parseDouble(snap.round().score()); } catch (NumberFormatException e) { return 0; }
    }

    private void render() {
        ui.newFrame();
        r.clock(ui.now);   // the edition shimmers animate on this
        paintBackground();
        if (!inMatch()) { menu.render(ui); overlays.tooltip(ui); drawFade(); return; }   // menu owns the screen; its hover tips draw last
        if (ui.s == null) { r.textCenter("Dealing…", Ui.W / 2.0, Ui.H / 2.0, 22, Palette.INK); return; }
        if (lastPhase != ui.s.phase()) { Log.phase(lastPhase, ui.s.phase()); lastPhase = ui.s.phase(); }

        double cx = Ui.PAD + Ui.SIDEBAR + 18;
        double cTop = Ui.PAD + Ui.SLOT_H + 12;
        double cW = Ui.W - Ui.PAD - Ui.DECK_W - 18 - cx;
        double cH = Ui.H - Ui.PAD - cTop;

        hud.render(ui);
        boolean blindWait = ui.atBlindBarrier();   // this seat is done with the blind, waiting on the others
        boolean packOpen = ui.s.opening() != null;
        // A skipped round can still owe the seat the free pack its tag granted: the barrier waits for it, and the
        // seat gets an "Open" gate here rather than the dead waiting popup.
        boolean packToOpen = blindWait && ui.blindSkipped() && !packOpen && !ui.s.pendingPacks().isEmpty();
        if (!packOpen) ui.packSel = -1;   // no pack open: drop any stale preview selection
        if (packOpen) {
            overlays.pack(ui);   // the pack panel sits up top until its picks are spent
            if (!ui.s.hand().isEmpty())   // a targeted pick needs the hand shown below the panel to aim at
                ui.hand.render(ui, 0, cTop, Ui.W, cH);   // centered on the screen, under the pack panel — not the play-area center
        } else if (blindWait && ui.blindSkipped()) {
            // Skipped: the seat stays on the blind-selection tiles (read-only) behind the popup, not the board.
            screens.get(MatchPhase.SELECTION).render(ui, cx, cTop, cW, cH);
        } else {
            Screen screen = screens.get(ui.s.phase());
            if (screen != null) screen.render(ui, cx, cTop, cW, cH);   // a finished blind keeps the round board behind the popup
            else r.textCenter(String.valueOf(ui.s.phase()), cx + cW / 2, cTop + cH / 2, 20, Palette.DIM);
            if (!blindWait) overlays.contextActions(ui);   // no contextual buy/use/sell while waiting at the barrier
        }
        if (packToOpen) overlays.pendingPackPrompt(ui);   // the "Open" gate for a free skip/Wrath pack, over the barrier
        if (!ui.status.isEmpty()) r.textLeft(ui.status, Ui.PAD + 8, Ui.H - 20, 12, Palette.DIM);
        if (ui.showRunInfo) overlays.runInfo(ui);

        // The scoring reel: the played cards stand centre-screen and the acting card's effect square floats.
        if (hand.hasStaged()) hand.renderStaged(ui, cx, cTop + cH * 0.34, cW);
        if (ui.reel.playing()) overlays.scoreEffect(ui);

        if (blindWait && !packOpen && !packToOpen) {
            overlays.blindWait(ui);   // the irremovable modal — only once there is no pack left to open
        } else if (blindWait) {
            overlays.tooltip(ui);     // a pack is open (or waiting to be opened) at the barrier: keep its tooltips
        } else if (!ui.showOptions) {
            // Hover layers, last so they sit above everything: the deck's contents, then any tooltip.
            if (ui.hovered(ui.deckRect)) overlays.deckContents(ui);
            else overlays.tooltip(ui);
        }
        // The Options/pause modal sits above the table and its hover layers; it draws its own tooltip on top.
        if (ui.showOptions) { overlays.options(ui, ui.now); overlays.tooltip(ui); }

        if (anteBanner.active()) anteBanner.render(ui);   // the ante-change sin handover banner
        if (DEV) dev.render(ui, localRun());   // cheat overlay on top of everything but the fade
        drawFade();
    }

    /** The local seat's live {@link model.game.player.Run} for the dev panel to mutate, or null before the match starts. */
    private model.game.player.Run localRun() {
        return (client != null && client.isStarted()) ? client.getLocalHost().getMatch().getRun(client.getSeat()) : null;
    }

    /** The fade-to-black transition overlay; the screen switch itself happens inside the Fader at full black. */
    private void drawFade() {
        double a = fader.alpha();
        if (a <= 0) return;
        r.gc().setFill(javafx.scene.paint.Color.web("#000000", a));
        r.gc().fillRect(0, 0, Ui.W, Ui.H);
    }

    /**
     * Paints the animated backdrop under everything else. The context is saved/restored around the call so the
     * background's own state changes (alpha, blend mode, transforms) can never leak into the game's drawing.
     */
    private void paintBackground() {
        r.gc().save();
        background.paint(r.gc(), background.time(), Ui.W, Ui.H);
        r.gc().restore();
        // Motes wear the backdrop's two main colours (transition-lerped), so they stay in palette as the state shifts.
        javafx.scene.paint.Color c1 = rgb(background.currentColour1()), c2 = rgb(background.currentColour2());
        for (int i = 0; i < particles.count(); i++)   // floating motes over the backdrop, under all UI
            r.square(particles.x(i), particles.y(i), particles.size(i), particles.angleDeg(i),
                    particles.colourIndex(i) == 0 ? c1 : c2, particles.alpha(i));
    }

    /** A 0xRRGGBB int as an opaque JavaFX colour (the square's own alpha is applied separately). */
    private static javafx.scene.paint.Color rgb(int c) {
        return javafx.scene.paint.Color.rgb((c >> 16) & 0xff, (c >> 8) & 0xff, c & 0xff);
    }

    private void handleClick(double x, double y) {
        if (suppressClick) { suppressClick = false; return; }   // this click was the tail end of a drag
        for (Ui.Btn b : ui.devButtons) if (b.rect().contains(x, y)) { b.action().run(); return; }   // cheat controls win any phase
        if (!inMatch()) {   // menu/lobby: only the widgets it registered this frame are live
            for (Ui.Btn b : ui.buttons) if (b.rect().contains(x, y)) { b.action().run(); return; }
            menu.focus = Menu.Focus.NONE;   // clicked outside every field — drop the caret
            return;
        }
        if (ui.s == null) return;
        if (ui.showOptions) {   // the Options/pause modal owns input: only its own controls are live (it cleared the rest)
            for (Ui.Btn b : ui.buttons) if (b.rect().contains(x, y)) { b.action().run(); return; }
            return;
        }
        if (ui.s.opening() != null) {   // pack modal (live even at the blind barrier): pick an option, select hand cards / a joker
            CardEntity e = hand.cardAt(x, y);
            if (e != null) {
                if (!e.selected() && hand.selectedCount() >= Ui.MAX_SELECTION) ui.status = "At most " + Ui.MAX_SELECTION + " cards.";
                else e.toggleSelected();
                return;
            }
            for (Ui.Btn b : ui.packButtons) if (b.rect().contains(x, y)) { b.action().run(); return; }
            for (Ui.Sel se : ui.jokerSel) if (se.rect().contains(x, y)) {   // a JOKER_SLOT relic (Katadesmos) aims at a joker
                ui.jokerTarget = (ui.jokerTarget == se.index()) ? -1 : se.index();
                return;
            }
            return;
        }
        if (ui.atBlindBarrier()) {   // the barrier: the only live control is Open, on a free pending pack; else a dead wait
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
            var in = getClass().getResourceAsStream("/sprites/cards/cards.png");
            if (in != null) { Image img = new Image(in); if (!img.isError() && img.getWidth() > 0) r.cardSheet(img); }
        } catch (RuntimeException ignored) { }
        try {
            var in = getClass().getResourceAsStream("/sprites/cards/Enhancements.png");
            if (in != null) { Image img = new Image(in); if (!img.isError() && img.getWidth() > 0) r.enhancementSheet(img); }
        } catch (RuntimeException ignored) { }
        try {
            var in = getClass().getResourceAsStream("/sprites/cards/Seals.png");
            if (in != null) { Image img = new Image(in); if (!img.isError() && img.getWidth() > 0) r.sealSheet(img); }
        } catch (RuntimeException ignored) { }
        try {
            var in = getClass().getResourceAsStream("/sprites/consumables/Planets.png");
            if (in != null) { Image img = new Image(in); if (!img.isError() && img.getWidth() > 0) r.planetSheet(img); }
        } catch (RuntimeException ignored) { }
        try {
            var in = getClass().getResourceAsStream("/sprites/consumables/Tarots.png");
            if (in != null) { Image img = new Image(in); if (!img.isError() && img.getWidth() > 0) r.tarotSheet(img); }
        } catch (RuntimeException ignored) { }
        try {
            var in = getClass().getResourceAsStream("/sprites/consumables/Spectrals.png");
            if (in != null) { Image img = new Image(in); if (!img.isError() && img.getWidth() > 0) r.spectralSheet(img); }
        } catch (RuntimeException ignored) { }
        loadSheet("/sprites/packs/ArcanaPacks.png",    img -> r.packSheet(model.items.packs.PackKind.ARCANA, img));
        loadSheet("/sprites/packs/BuffoonPacks.png",   img -> r.packSheet(model.items.packs.PackKind.BUFFOON, img));
        loadSheet("/sprites/packs/CelestialPacks.png", img -> r.packSheet(model.items.packs.PackKind.CELESTIAL, img));
        loadSheet("/sprites/packs/SpectralPacks.png",  img -> r.packSheet(model.items.packs.PackKind.SPECTRAL, img));
        loadSheet("/sprites/packs/StandardPacks.png",  img -> r.packSheet(model.items.packs.PackKind.STANDARD, img));
        loadSheet("/sprites/vouchers/Vouchers.png",    r::voucherSheet);
        loadSheet("/sprites/cards/Decks.png",          r::deckSheet);
        loadSheet("/sprites/difficultyStake/Stakes.png", r::stakeSheet);
        try {
            var in = getClass().getResourceAsStream("/font/game.ttf");
            if (in != null) { Font f = Font.loadFont(in, 14); if (f != null) fontFamily = f.getFamily(); }
        } catch (RuntimeException ignored) { }
        r.font(fontFamily);
    }

    /** Loads an optional classpath image and hands it to {@code apply}; a missing or broken file is silently skipped. */
    private void loadSheet(String path, java.util.function.Consumer<Image> apply) {
        try {
            var in = getClass().getResourceAsStream(path);
            if (in != null) { Image img = new Image(in); if (!img.isError() && img.getWidth() > 0) apply.accept(img); }
        } catch (RuntimeException ignored) { }
    }

    public static void main(String[] args) { launch(args); }
}
