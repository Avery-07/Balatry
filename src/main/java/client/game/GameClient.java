package client.game;

import client.MatchSnapshot;
import client.MatchViewModel;
import client.engine.CardEntity;
import client.engine.Easing;
import client.engine.Layout;
import client.engine.Reconciler;
import debug.Log;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import model.cards.DeckCard;
import model.cards.relics.RelicTarget;
import model.game.MatchPhase;
import model.game.net.MatchClient;
import model.game.scoring.HandEvaluator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The shippable Balatry client: a JavaFX {@link Canvas} redrawn every frame by an {@link AnimationTimer}, over
 * the tested {@code client.engine}. Cards are retained {@link CardEntity}s reconciled from each {@link MatchSnapshot}
 * (so selection and in-flight motion survive incoming frames), animated with tweens, and hit-tested for input.
 * The layout follows the approved mockup: felt frame, left sidebar, top joker/consumable slots, right deck pile,
 * and a phase-swapped center; standings open in a Run Info overlay.
 */
public final class GameClient extends Application {

    private static final int W = 1320, H = 820, PAD = 24, SIDEBAR = 300, DECK_W = 110, SLOT_H = 118;
    private static final double CARD_W = 90, CARD_H = 126;
    private static final int MAX_SELECTION = 5;

    private static final Color FELT_A = Color.web("#2c6a58"), FELT_B = Color.web("#12291f");
    private static final Color PANEL = Color.web("#1b1c1e"), PANEL2 = Color.web("#26272b"), EDGE = Color.web("#4a4d54");
    private static final Color INK = Color.web("#f4f1e8"), DIM = Color.web("#b9c4bd"), FAINT = Color.web("#7f8d86");
    private static final Color ORANGE = Color.web("#f0a92b"), RED = Color.web("#d1442f"), GREEN = Color.web("#37a862");
    private static final Color BLUE = Color.web("#2f6fb0"), GOLD = Color.web("#b8912c"), PURPLE = Color.web("#8a4f9e");

    private Canvas canvas;
    private Renderer r;
    private MatchViewModel vm;
    private MatchSnapshot s;
    private String fontFamily = "Monospaced";

    private final List<CardEntity> hand = new ArrayList<>();
    private List<CardEntity> orderedHand = new ArrayList<>();
    private int handSort;                 // 0 dealt, 1 rank, 2 suit
    private boolean showRunInfo;
    private String status = "";

    private final List<Btn> buttons = new ArrayList<>();
    private final List<Sel> selectables = new ArrayList<>();   // items that can be selected to reveal contextual actions
    private String selKind;                                    // currently-selected item's kind, or null
    private int selIndex;
    private long lastNanos;

    private record Btn(Layout.Rect rect, Runnable action) { }
    private record Sel(Layout.Rect rect, String kind, int index) { }
    private record Act(String label, Color color, Color text, Runnable run) { }

    @Override
    public void start(Stage stage) {
        canvas = new Canvas(W, H);
        r = new Renderer(canvas.getGraphicsContext2D());
        loadAssets();
        canvas.setOnMouseClicked(e -> handleClick(e.getX(), e.getY()));

        StackPane root = new StackPane(canvas);
        stage.setScene(new Scene(root, W, H));
        stage.setTitle("Balatry");
        stage.show();

        connect();

        new AnimationTimer() {
            @Override public void handle(long now) {
                double dt = lastNanos == 0 ? 0 : (now - lastNanos) / 1e9;
                lastNanos = now;
                for (CardEntity c : hand) c.advance(dt);
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
                    err -> { Log.error(err); Platform.runLater(() -> status = "ERR: " + err); },
                    a -> { if (vm != null) { Log.recv(a, vm.seat()); vm.onFrameApplied(); } });
            vm = new MatchViewModel(client);
            vm.snapshotProperty().addListener((o, old, snap) -> { if (snap != null) onSnapshot(snap); });
            vm.refresh();
        } catch (Exception e) {
            status = "Failed to connect: " + (e.getMessage() == null ? e : e.getMessage());
        }
    }

    /** FX thread: a new frame — reconcile the hand into retained entities (keeps selection + motion). */
    private void onSnapshot(MatchSnapshot snap) {
        s = snap;
        List<Reconciler.Desired> desired = new ArrayList<>();
        for (MatchSnapshot.HandCardView c : snap.hand()) desired.add(new Reconciler.Desired(c.id(), c.rank(), c.suit(), c.label()));
        List<CardEntity> next = Reconciler.reconcile(hand, desired, W - 90, H * 0.55, 0.35, Easing.EASE_OUT_CUBIC);
        hand.clear(); hand.addAll(next);
    }

    // ===================== render =====================

    private void render() {
        buttons.clear();
        selectables.clear();
        paintFelt();
        if (s == null) { r.textCenter(status.isEmpty() ? "Connecting…" : status, W / 2.0, H / 2.0, 22, INK); return; }

        double cx = PAD + SIDEBAR + 18;
        double cTop = PAD + SLOT_H + 12;
        double cW = W - PAD - DECK_W - 18 - cx;
        double cH = H - PAD - cTop;

        drawSidebar(PAD, PAD, SIDEBAR, H - 2 * PAD);
        drawTopSlots(cx, PAD, W - PAD - DECK_W - 18 - cx, SLOT_H);
        drawDeck(W - PAD - DECK_W, cTop, DECK_W, cH);

        switch (s.phase()) {
            case SELECTION -> drawSelection(cx, cTop, cW, cH);
            case BLIND -> drawBlind(cx, cTop, cW, cH);
            case SHOP -> drawShop(cx, cTop, cW, cH);
            case RESULT -> drawResult(cx, cTop, cW, cH);
            default -> r.textCenter(String.valueOf(s.phase()), cx + cW / 2, cTop + cH / 2, 20, DIM);
        }
        drawItemActions();
        if (!status.isEmpty()) r.textLeft(status, PAD + 8, H - 20, 12, DIM);
        if (showRunInfo) drawRunInfo();
    }

    private void paintFelt() {
        r.gc().setFill(new javafx.scene.paint.RadialGradient(0, 0, 0.5, 0.1, 1.1, true,
                javafx.scene.paint.CycleMethod.NO_CYCLE,
                new javafx.scene.paint.Stop(0, FELT_A), new javafx.scene.paint.Stop(1, FELT_B)));
        r.gc().fillRect(0, 0, W, H);
    }

    private void drawSidebar(double x, double y, double w, double h) {
        Color accent = switch (s.phase()) { case BLIND -> BLUE; case SHOP -> RED; case RESULT -> GREEN; default -> GOLD; };
        r.panel(x, y, w, h, PANEL, accent, 12, 3);
        double ix = x + 14, iw = w - 28, cy = y + 16;

        // header
        String hdr = switch (s.phase()) {
            case SHOP -> "SHOP"; case RESULT -> blindName() + " Defeated!";
            case BLIND -> "Score at least " + s.target(); default -> "Choose your next Blind"; };
        r.panel(ix, cy, iw, 52, PANEL2, EDGE, 9, 2);
        r.textCenterBold(hdr, ix + iw / 2, cy + 26, s.phase() == MatchPhase.SHOP ? 26 : 15, s.phase() == MatchPhase.SHOP ? ORANGE : INK);
        cy += 64;

        cy = statBox(ix, cy, iw, "Round score", s.round() != null ? s.round().score() : "0", INK);

        // chips x mult
        double half = (iw - 30) / 2;
        r.panel(ix, cy, half, 46, BLUE, null, 8, 0); r.textCenterBold(s.chips(), ix + half / 2, cy + 23, 24, INK);
        r.textCenterBold("X", ix + half + 15, cy + 23, 18, RED);
        r.panel(ix + half + 30, cy, half, 46, RED, null, 8, 0); r.textCenterBold(s.mult(), ix + half + 30 + half / 2, cy + 23, 24, INK);
        cy += 58;

        // Run Info + Hands + Discards
        button(ix, cy, 88, 60, "Run Info", RED, INK, () -> showRunInfo = true, true);
        cell(ix + 98, cy, (iw - 98 - 10) / 2, 60, "Hands", String.valueOf(s.hands()), BLUE);
        cell(ix + 98 + (iw - 98 - 10) / 2 + 10, cy, (iw - 98 - 10) / 2, 60, "Discards", String.valueOf(s.discards()), RED);
        cy += 72;

        r.panel(ix, cy, iw, 44, PANEL, EDGE, 8, 2); r.textCenterBold("$" + s.money(), ix + iw / 2, cy + 22, 26, ORANGE);
        cy += 56;

        button(ix, cy, 88, 56, "Options", ORANGE, Color.web("#241a05"), () -> status = "Options — not wired.", true);
        cell(ix + 98, cy, (iw - 98 - 10) / 2, 56, "Ante", s.ante() + "/" + s.anteCount(), ORANGE);
        cell(ix + 98 + (iw - 98 - 10) / 2 + 10, cy, (iw - 98 - 10) / 2, 56, "Round", String.valueOf(s.roundNumber()), ORANGE);
        cy += 68;

        r.panel(ix, cy, iw, 34, Color.web("#2a1030"), PURPLE, 8, 2);
        r.textCenter("Ante sin — " + s.activeSin(), ix + iw / 2, cy + 17, 12, Color.web("#ecd7f5"));
    }

    private double statBox(double x, double y, double w, String k, String v, Color vc) {
        r.panel(x, y, w, 40, PANEL, EDGE, 8, 2);
        r.textLeftBold(k, x + 10, y + 13, 14, DIM);
        r.textCenterBold(v, x + w - 30, y + 20, 20, vc);
        return y + 52;
    }

    private void cell(double x, double y, double w, double h, String k, String v, Color vc) {
        r.panel(x, y, w, h, PANEL, EDGE, 8, 2);
        r.textCenter(k, x + w / 2, y + 15, 12, DIM);
        r.textCenterBold(v, x + w / 2, y + h - 18, 22, vc);
    }

    private void drawTopSlots(double x, double y, double w, double h) {
        r.textLeftBold(s.jokerSlotsUsed() + "/" + s.jokerSlotsMax(), x, y, 18, INK);
        double jx = x;
        for (int j = 0; j < s.jokers().size(); j++) {
            Layout.Rect rr = new Layout.Rect(jx, y + 22, 54, SLOT_H - 30);
            mini(rr, Color.web("#c0392b"), shortName(s.jokers().get(j)));
            selectables.add(new Sel(rr, "jokerHeld", j));
            jx += 62;
        }
        r.textLeftBold(s.consumableSlotsUsed() + "/" + s.consumableSlotsMax(), x + w - 40, y, 18, INK);
        double kx = x + w - 54;
        for (int i = s.inventory().size() - 1; i >= 0; i--) {
            Layout.Rect rr = new Layout.Rect(kx, y + 22, 54, SLOT_H - 30);
            mini(rr, Color.web("#3d3357"), shortName(s.inventory().get(i).label()));
            selectables.add(new Sel(rr, "item", i));
            kx -= 62;
        }
    }

    private void mini(Layout.Rect rr, Color c, String label) {
        r.panel(rr.x(), rr.y(), rr.w(), rr.h(), c, Color.web("#0006"), 8, 2);
        r.textCenter(label, rr.centerX(), rr.centerY(), 10, INK);
    }

    private void drawDeck(double x, double y, double w, double h) {
        double cardH = 140, cardY = y + h - cardH - 24;
        r.panel(x + 5, cardY, w - 10, cardH, Color.web("#d3c3a2"), Color.web("#7a5a3a"), 8, 3);
        r.textCenter(s.deckRemaining() + " / " + s.deckTotal(), x + w / 2, y + h - 12, 14, INK);
    }

    // ---- selection ----
    private void drawSelection(double x, double y, double w, double h) {
        List<MatchSnapshot.BlindOption> blinds = s.blinds();
        int n = Math.max(1, blinds.size());
        double gap = 18, tw = (w - gap * (n - 1)) / n, th = Math.min(h, 470);
        double ty = y + (h - th) / 2;
        for (int i = 0; i < blinds.size(); i++) {
            MatchSnapshot.BlindOption b = blinds.get(i);
            double tx = x + i * (tw + gap);
            boolean cur = b.current();
            boolean boss = b.bossName() != null;
            Color accent = cur ? BLUE : boss ? PURPLE : GOLD;
            r.panel(tx, ty, tw, th, PANEL, accent, 12, 3);
            double my = ty + 14, mc = tx + tw / 2;
            if (!cur) { r.textCenter("Upcoming", mc, my, 13, FAINT); my += 20; }
            r.panel(tx + 12, my, tw - 24, 34, accent, null, 8, 0);
            r.textCenterBold(boss ? b.bossName() : b.type(), mc, my + 17, 15, cur || boss ? INK : Color.web("#241a05"));
            my += 48;
            if (b.bossEffect() != null) { r.textCenter(b.bossEffect(), mc, my + 10, 12, Color.web("#e7c7f0")); my += 44; }
            r.textCenter("Score at least", mc, my, 12, DIM);
            r.textCenterBold(String.valueOf(b.target()), mc, my + 24, 24, RED); my += 46;
            if (!cur) { r.textCenterBold("Reward: " + dollars(b.reward()), mc, my, 13, GOLD); my += 26; }
            if (cur) {
                button(tx + 16, my, tw - 32, 40, "Select", ORANGE, Color.web("#241a05"), () -> vm.playBlind(), !s.hasChosen());
                my += 50;
                String sk = "Skip" + (b.skipTag() != null ? "  (" + b.skipTag() + ")" : "");
                button(tx + 16, my, tw - 32, 40, sk, RED, INK, () -> vm.skipBlind(), !s.hasChosen());
                if (s.hasChosen()) r.textCenter("chosen — waiting…", mc, my + 60, 12, FAINT);
            }
        }
    }

    // ---- blind (animated hand) ----
    private void drawBlind(double x, double y, double w, double h) {
        orderedHand = new ArrayList<>(hand);
        if (handSort == 1) orderedHand.sort((a, b) -> b.rank() != a.rank() ? b.rank() - a.rank() : a.suit() - b.suit());
        else if (handSort == 2) orderedHand.sort((a, b) -> a.suit() != b.suit() ? a.suit() - b.suit() : b.rank() - a.rank());

        double baseTop = y + h - CARD_H - 120;
        List<Layout.Placement> fan = Layout.fan(orderedHand.size(), x + w / 2, baseTop, CARD_W, 34, 12, 16);
        for (int k = 0; k < orderedHand.size(); k++) {
            CardEntity e = orderedHand.get(k);
            Layout.Placement p = fan.get(k);
            double tx = p.x() + CARD_W / 2, tyc = p.y() + CARD_H / 2 - (e.selected() ? 30 : 0);
            e.moveTo(tx, tyc);
        }
        for (int k = 0; k < orderedHand.size(); k++) {
            CardEntity e = orderedHand.get(k);
            r.card(e.rank(), e.suit(), e.x(), e.y(), CARD_W, CARD_H, fan.get(k).rotationDeg(), e.selected());
        }
        r.textCenter(hand.size() + " / " + hand.size(), x + w / 2, baseTop + CARD_H + 20, 14, DIM);

        double by = y + h - 56, bw = 130, bx = x + w / 2 - (bw * 2 + 30 + 130) / 2;
        button(bx, by, bw, 44, "Play Hand", RED, INK, this::playSelected, true);
        r.panel(bx + bw + 10, by - 6, 120, 56, PANEL2, Color.web("#63676f"), 8, 2);
        r.textCenter("Sort Hand", bx + bw + 70, by + 4, 11, DIM);
        button(bx + bw + 20, by + 14, 44, 30, "Rank", ORANGE, Color.web("#241a05"), () -> handSort = 1, true);
        button(bx + bw + 70, by + 14, 44, 30, "Suit", ORANGE, Color.web("#241a05"), () -> handSort = 2, true);
        button(bx + bw + 140, by, bw, 44, "Discard", Color.web("#303237"), INK, this::discardSelected, true);
        button(bx + bw + 140 + bw + 10, by, 120, 44, "Finish", Color.web("#303237"), INK, () -> vm.finishRound(), true);
    }

    // ---- shop ----
    private void drawShop(double x, double y, double w, double h) {
        MatchSnapshot.ShopView shop = s.shop();
        if (shop == null) return;
        r.panel(x, y, w, h, Color.web("#141517"), RED, 12, 3);
        double ix = x + 16, iw = w - 32, cy = y + 16;

        button(ix, cy, 120, 44, "Next Round", RED, INK, () -> vm.readyForNext(), true);
        button(ix + 130, cy, 120, 44, "Reroll $" + shop.rerollCost(), GREEN, INK, () -> vm.rerollShop(), true);
        cy += 60;

        r.textLeftBold("Cards", ix, cy, 12, FAINT); cy += 18;
        double px = ix;
        for (int i = 0; i < shop.slots().size(); i++) {
            MatchSnapshot.ShopItem it = shop.slots().get(i);
            shopTile(px, cy, it == null ? null : it.label(), it == null ? 0 : it.price(), Color.web("#c0392b"), it != null, "shopSlot", i);
            px += 118;
        }
        cy += 150;

        r.textLeftBold("Voucher", ix, cy, 12, FAINT);
        r.textLeftBold("Booster Packs", ix + iw / 2, cy, 12, FAINT); cy += 18;
        for (int i = 0; i < shop.vouchers().size(); i++) {
            MatchSnapshot.VoucherItem v = shop.vouchers().get(i);
            shopTile(ix + i * 118, cy, v == null ? null : v.label(), v == null ? 0 : v.price(), BLUE, v != null && v.redeemable(), "shopVoucher", i);
        }
        double ppx = ix + iw / 2;
        for (int i = 0; i < shop.packs().size(); i++) {
            MatchSnapshot.ShopItem p = shop.packs().get(i);
            shopTile(ppx, cy, p == null ? null : p.label(), p == null ? 0 : p.price(), PURPLE, p != null, "shopPack", i);
            ppx += 118;
        }
    }

    private void shopTile(double x, double y, String label, int price, Color c, boolean enabled, String kind, int index) {
        Layout.Rect rr = new Layout.Rect(x, y + 16, 108, 130);
        r.panel(rr.x(), rr.y(), rr.w(), rr.h(), c, Color.web("#0006"), 8, 2);
        if (label != null) {
            r.textCenter(label, x + 54, y + 70, 11, INK);
            r.panel(x + 30, y + 2, 48, 22, PANEL, GOLD, 8, 2);
            r.textCenterBold("$" + price, x + 54, y + 13, 12, ORANGE);
            if (enabled) selectables.add(new Sel(rr, kind, index));
        } else {
            r.textCenter("(sold)", x + 54, y + 80, 11, FAINT);
        }
    }

    // ---- result ----
    private void drawResult(double x, double y, double w, double h) {
        MatchSnapshot.ResultView v = s.lastResult();
        double pw = Math.min(460, w), px = x + (w - pw) / 2, py = y + 40, ph = 360;
        r.panel(px, py, pw, ph, Color.web("#141517"), GREEN, 12, 3);
        r.textCenterBold(blindName() + " Defeated", px + pw / 2, py + 30, 22, GREEN);

        long pts = 0; int rank = 1, total = s.standings().size();
        for (MatchSnapshot.StandingView sv : s.standings()) if (sv.isMe()) { pts = sv.points(); rank = sv.rank() + 1; }
        double hy = py + 60, hw = pw - 60;
        r.panel(px + 30, hy, hw, 120, Color.web("#231a08"), GOLD, 12, 3);
        r.textCenterBold("TOTAL POINTS", px + pw / 2, hy + 24, 14, DIM);
        r.textCenterBold(String.valueOf(pts), px + pw / 2, hy + 66, 54, ORANGE);
        r.textCenterBold("Now " + ordinal(rank) + " of " + total, px + pw / 2, hy + 102, 16, Color.web("#d89bec"));

        if (v != null) {
            r.textLeft("Score", px + 30, py + 210, 15, DIM); r.textCenterBold(v.score() + " / " + v.target(), px + pw - 80, py + 217, 15, GREEN);
            r.textLeft("Cash out", px + 30, py + 240, 15, DIM); r.textCenterBold("+$" + v.moneyEarned(), px + pw - 80, py + 247, 15, ORANGE);
        }
        button(px + 30, py + ph - 56, pw - 60, 44, "Continue", GREEN, INK, () -> vm.readyForNext(), true);
    }

    private void drawRunInfo() {
        r.gc().setFill(Color.web("#040a08", 0.66)); r.gc().fillRect(0, 0, W, H);
        double pw = 560, ph = 420, px = (W - pw) / 2, py = (H - ph) / 2;
        r.panel(px, py, pw, ph, Color.web("#1a1b20"), ORANGE, 14, 3);
        r.textLeftBold("RUN INFO — Standings", px + 20, py + 16, 22, ORANGE);
        button(px + pw - 90, py + 12, 70, 34, "Close", RED, INK, () -> showRunInfo = false, true);
        double ry = py + 66;
        for (MatchSnapshot.StandingView v : s.standings()) {
            r.panel(px + 20, ry, pw - 40, 46, v.isMe() ? Color.web("#221d10") : Color.web("#1a1b1f"), v.isMe() ? ORANGE : EDGE, 10, 2);
            r.textCenterBold(String.valueOf(v.rank() + 1), px + 44, ry + 23, 20, v.rank() == 0 ? Color.web("#ffd45e") : DIM);
            r.textLeftBold(v.name() + (v.isMe() ? "  ◄ you" : ""), px + 74, ry + 15, 16, INK);
            r.textCenterBold(v.points() + " pts", px + pw - 70, ry + 23, 16, GREEN);
            ry += 54;
        }
        r.textCenter("Opponents show only points & rank across the information boundary.", px + pw / 2, py + ph - 20, 12, FAINT);
    }

    /** Rings the selected item and floats its contextual actions (Buy / Use / Sell) just below it. */
    private void drawItemActions() {
        if (selKind == null) return;
        Sel sel = null;
        for (Sel se : selectables) if (se.kind().equals(selKind) && se.index() == selIndex) { sel = se; break; }
        if (sel == null) { selKind = null; return; }   // the item was bought/sold/used, or the phase changed

        Layout.Rect rr = sel.rect();
        r.panel(rr.x() - 4, rr.y() - 4, rr.w() + 8, rr.h() + 8, null, ORANGE, 12, 3);   // selection ring

        List<Act> acts = actionsFor(sel);
        if (acts.isEmpty()) return;
        double bw = 68, gap = 8, total = acts.size() * bw + (acts.size() - 1) * gap;
        double bx = rr.centerX() - total / 2, by = rr.y() + rr.h() + 8;
        for (Act a : acts) {
            button(bx, by, bw, 30, a.label(), a.color(), a.text(), () -> { a.run().run(); selKind = null; }, true);
            bx += bw + gap;
        }
    }

    /** The actions a selected item offers: shop items Buy/Redeem; held jokers Sell; held consumables Use/Sell. */
    private List<Act> actionsFor(Sel sel) {
        List<Act> out = new ArrayList<>();
        Color dark = Color.web("#241a05");
        switch (sel.kind()) {
            case "shopSlot"    -> out.add(new Act("Buy", ORANGE, dark, () -> vm.buyCard(sel.index())));
            case "shopPack"    -> out.add(new Act("Buy", ORANGE, dark, () -> vm.buyPack(sel.index())));
            case "shopVoucher" -> out.add(new Act("Redeem", ORANGE, dark, () -> vm.redeemVoucher(sel.index())));
            case "jokerHeld"   -> out.add(new Act("Sell", RED, INK, () -> vm.sellJoker(sel.index())));
            case "item" -> {
                if (sel.index() >= s.inventory().size()) break;
                MatchSnapshot.ItemView it = s.inventory().get(sel.index());
                if (it.isRelic()) {
                    // Relics are used like tarots: a rank/suit/hand relic reads your selected card(s); seat-aimed
                    // relics (Pyre/Limos/Harpax) still need a seat picker, which is the next step.
                    if (!it.needsSeat())
                        out.add(new Act("Use", ORANGE, dark, () -> {
                            RelicTarget t = relicTargetFromSelection(it);
                            if (t != null) vm.useRelic(it.modelIndex(), t);
                        }));
                    else
                        out.add(new Act("Aim…", Color.web("#303237"), DIM, () -> status = it.label() + " must be aimed at a seat — seat targeting is coming."));
                    out.add(new Act("Sell", RED, INK, () -> vm.sellRelic(it.modelIndex())));
                } else {
                    out.add(new Act("Use", ORANGE, dark, () -> vm.useConsumable(it.modelIndex(), selectedModelIndices())));
                    out.add(new Act("Sell", RED, INK, () -> vm.sellConsumable(it.modelIndex())));
                }
            }
            default -> { }
        }
        return out;
    }

    /** Builds a relic's target from the current hand-card selection — its rank, its suit, or the hand it forms. */
    private RelicTarget relicTargetFromSelection(MatchSnapshot.ItemView relic) {
        List<Integer> sel = selectedModelIndices();
        switch (relic.selector()) {
            case "RANK" -> {
                if (sel.isEmpty()) { status = "Select a card, then Use " + relic.label() + "."; return null; }
                return RelicTarget.rank(null, DeckCard.Rank.values()[s.hand().get(sel.get(0)).rank()]);
            }
            case "SUIT" -> {
                if (sel.isEmpty()) { status = "Select a card, then Use " + relic.label() + "."; return null; }
                return RelicTarget.suit(null, DeckCard.Suit.values()[s.hand().get(sel.get(0)).suit()]);
            }
            case "HAND_TYPE" -> {
                if (sel.isEmpty()) { status = "Select the cards forming a hand, then Use " + relic.label() + "."; return null; }
                List<DeckCard> cards = new ArrayList<>();
                for (int i : sel) {
                    MatchSnapshot.HandCardView c = s.hand().get(i);
                    cards.add(new DeckCard(DeckCard.Rank.values()[c.rank()], DeckCard.Suit.values()[c.suit()]));
                }
                return RelicTarget.hand(null, new HandEvaluator().evaluate(cards).type());
            }
            case "JOKER_SLOT" -> { status = relic.label() + " targets a joker slot — selection for that is coming."; return null; }
            default -> {
                if (relic.needsSeat()) { status = relic.label() + " must be aimed at a seat — targeting UI is coming."; return null; }
                return RelicTarget.none();   // SELF / GLOBAL relics (Aegis, Metabole, Mimesis)
            }
        }
    }

    // ===================== input =====================

    private void handleClick(double x, double y) {
        if (s == null) return;
        if (showRunInfo) {
            for (Btn b : buttons) if (b.rect().contains(x, y)) { b.action().run(); return; }
            showRunInfo = false; return;
        }
        if (s.phase() == MatchPhase.BLIND) {
            for (int k = orderedHand.size() - 1; k >= 0; k--) {   // topmost first
                CardEntity e = orderedHand.get(k);
                if (new Layout.Rect(e.x() - CARD_W / 2, e.y() - CARD_H / 2, CARD_W, CARD_H).contains(x, y)) {
                    if (!e.selected() && selectedCount() >= MAX_SELECTION) { status = "At most " + MAX_SELECTION + " cards."; return; }
                    e.toggleSelected(); return;
                }
            }
        }
        for (Btn b : buttons) if (b.rect().contains(x, y)) { b.action().run(); return; }   // buttons incl. contextual actions
        for (Sel se : selectables) if (se.rect().contains(x, y)) {                          // click an item to (de)select it
            if (selKind != null && selKind.equals(se.kind()) && selIndex == se.index()) selKind = null;
            else { selKind = se.kind(); selIndex = se.index(); }
            return;
        }
        selKind = null;   // clicked empty space — clear the selection
    }

    private int selectedCount() { int n = 0; for (CardEntity e : hand) if (e.selected()) n++; return n; }

    private List<Integer> selectedModelIndices() {
        Set<Integer> ids = new HashSet<>();
        for (CardEntity e : hand) if (e.selected()) ids.add(e.id());
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < s.hand().size(); i++) if (ids.contains(s.hand().get(i).id())) out.add(i);
        return out;
    }

    private void playSelected() {
        List<Integer> sel = selectedModelIndices();
        if (sel.isEmpty()) status = "Select 1-" + MAX_SELECTION + " cards to play."; else vm.playHand(sel);
    }

    private void discardSelected() {
        List<Integer> sel = selectedModelIndices();
        if (sel.isEmpty()) status = "Select 1-" + MAX_SELECTION + " cards to discard."; else vm.discard(sel);
    }

    // ===================== helpers =====================

    private void button(double x, double y, double w, double h, String label, Color fill, Color text, Runnable action, boolean enabled) {
        r.panel(x, y, w, h, enabled ? fill : fill.darker().darker(), fill.darker(), 8, 2);
        r.textCenterBold(label, x + w / 2, y + h / 2, 14, enabled ? text : DIM);
        if (enabled) buttons.add(new Btn(new Layout.Rect(x, y, w, h), action));
    }

    private String blindName() {
        return switch (s.blind() == null ? "" : s.blind()) { case "SMALL" -> "Small Blind"; case "BIG" -> "Big Blind"; case "BOSS" -> "Boss Blind"; default -> "Blind"; };
    }
    private static String dollars(int n) { StringBuilder b = new StringBuilder(); for (int i = 0; i < Math.min(n, 6); i++) b.append('$'); return b + "+"; }
    private static String ordinal(int n) { return switch (n) { case 1 -> "1st"; case 2 -> "2nd"; case 3 -> "3rd"; default -> n + "th"; }; }
    private static String shortName(String s) { int cut = s.indexOf('-'); return cut > 0 ? s.substring(0, cut) : (s.length() > 7 ? s.substring(0, 7) : s); }

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
