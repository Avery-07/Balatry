package client;

import debug.Log;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import model.cards.DeckCard.Rank;
import model.cards.DeckCard.Suit;
import model.cards.relics.RelicTarget;
import model.game.MatchPhase;
import model.game.net.MatchClient;
import model.game.player.PlayerId;
import model.game.scoring.HandType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The Balatry client — a Balatro-flavored JavaFX front end over the local {@link MatchClient}. One persistent
 * frame (felt background, left sidebar, top joker/consumable slot rows, right deck pile) with a center that
 * swaps per phase: blind selection, the hand, the shop, and the (Balatry-specific) points-first result. The
 * multiplayer standings live in a Run Info overlay.
 *
 * <p>Assets load with graceful fallbacks (see {@code resources/README-assets.md}): a bundled pixel font, else
 * the platform Monospaced font; a card sprite sheet at {@code /cards/deck.png}, else vector 4-color cards.
 */
public final class BalatryClient extends Application {

    private static final int MAX_SELECTION = 5;
    private static final String[] RANKS = {"2","3","4","5","6","7","8","9","10","J","Q","K","A"};
    private static final double CARD_W = 84, CARD_H = 120;

    private MatchViewModel vm;
    private MatchSnapshot snapshot;
    private MatchPhase lastPhase;
    private boolean resultContinued;
    private int handSort;                 // 0 dealt, 1 rank, 2 suit
    private String shopSelKind; private int shopSelIndex = -1;

    private Image cardSheet; private double cellW, cellH;

    // persistent nodes
    private final VBox sidebar = new VBox();
    private final StackPane headerSlot = new StackPane();
    private final Label roundScoreVal = new Label("0");
    private final Label chipsLbl = new Label("0"), multLbl = new Label("0");
    private final Label moneyLbl = new Label("$0");
    private final Label handsVal = new Label("0"), discardsVal = new Label("0");
    private final Label anteVal = new Label("0/0"), roundVal = new Label("0");
    private final Label sinChip = new Label();

    private final Label jokerCount = new Label("0/5"), consCount = new Label("0/2");
    private final HBox jokerRow = new HBox(8), consRow = new HBox(8);
    private final Label deckCount = new Label("0/0");

    private final StackPane centerStack = new StackPane();
    private final HBox selectionTiles = new HBox(18);
    private final VBox blindPane = new VBox(20);
    private final HBox handRow = new HBox(6);
    private final VBox shopPane = new VBox();
    private final VBox resultPane = new VBox();
    private final List<StackPane> handCards = new ArrayList<>();

    private final StackPane runInfoOverlay = new StackPane();
    private final VBox runInfoBody = new VBox(10);
    private final StackPane itemOverlay = new StackPane();
    private final VBox itemBody = new VBox(10);
    private final Label status = new Label();

    // blind-phase buttons kept for enable/disable
    private Button playBtn, discardBtn, finishBtn, sortRankBtn, sortSuitBtn;

    @Override
    public void start(Stage stage) {
        String host = System.getProperty("balatry.host", "localhost");
        int port = Integer.getInteger("balatry.port", 5555);
        long seed = Long.parseLong(System.getProperty("balatry.seed", "42"));
        List<String> players = List.of(System.getProperty("balatry.players", "P0,P1").split(","));

        loadCardSheet();

        StackPane root = new StackPane();
        root.getStyleClass().add("root-felt");
        root.getChildren().addAll(buildScreen(), buildOverlay(runInfoOverlay, runInfoBody, "RUN INFO"),
                buildOverlay(itemOverlay, itemBody, "ITEM"));

        Scene scene = new Scene(root, 1320, 820);
        applyStylesheet(scene);
        applyFont(root);
        stage.setTitle("Balatry");
        stage.setScene(scene);
        stage.show();

        try {
            MatchClient client = MatchClient.connect(host, port, seed, players,
                    err -> { Log.error(err); Platform.runLater(() -> hint("ERR: " + err)); },
                    a -> { if (vm != null) { Log.recv(a, vm.seat()); vm.onFrameApplied(); } });
            vm = new MatchViewModel(client);
            vm.snapshotProperty().addListener((obs, old, snap) -> { if (snap != null) render(snap); });
            vm.refresh();
        } catch (Exception e) {
            hint("Failed to connect: " + (e.getMessage() == null ? e : e.getMessage()));
        }
    }

    // ================= layout scaffolding =================

    private GridPane buildScreen() {
        GridPane g = new GridPane();
        g.getStyleClass().add("screen");
        g.setHgap(18); g.setVgap(18);
        ColumnConstraints c0 = new ColumnConstraints(); c0.setMinWidth(288); c0.setPrefWidth(288);
        ColumnConstraints c1 = new ColumnConstraints(); c1.setHgrow(Priority.ALWAYS);
        ColumnConstraints c2 = new ColumnConstraints(); c2.setMinWidth(120); c2.setPrefWidth(120);
        g.getColumnConstraints().addAll(c0, c1, c2);
        RowConstraints r0 = new RowConstraints();
        RowConstraints r1 = new RowConstraints(); r1.setVgrow(Priority.ALWAYS);
        g.getRowConstraints().addAll(r0, r1);

        buildSidebar();
        g.add(sidebar, 0, 0, 1, 2);
        g.add(buildTopSlots(), 1, 0, 2, 1);
        g.add(buildCenter(), 1, 1);
        g.add(buildDeck(), 2, 1);
        return g;
    }

    private void buildSidebar() {
        sidebar.getStyleClass().add("sidebar");
        headerSlot.setAlignment(Pos.CENTER);

        HBox scoreRow = statRow("Round score", roundScoreVal, true);
        roundScoreVal.getStyleClass().add("stat-v");

        chipsLbl.getStyleClass().add("cm-chips"); multLbl.getStyleClass().add("cm-mult");
        HBox.setHgrow(chipsLbl, Priority.ALWAYS); HBox.setHgrow(multLbl, Priority.ALWAYS);
        chipsLbl.setMaxWidth(Double.MAX_VALUE); multLbl.setMaxWidth(Double.MAX_VALUE);
        chipsLbl.setAlignment(Pos.CENTER); multLbl.setAlignment(Pos.CENTER);
        Label x = new Label("X"); x.getStyleClass().add("cm-x");
        HBox cm = new HBox(6, chipsLbl, x, multLbl); cm.setAlignment(Pos.CENTER);

        Button runInfoBtn = btn("Run\nInfo", "btn-red");
        runInfoBtn.setOnAction(e -> showOverlay(runInfoOverlay, true));
        HBox row2 = new HBox(8, runInfoBtn, cell("Hands", handsVal, "blue"), cell("Discards", discardsVal, "red"));
        HBox.setHgrow(runInfoBtn, Priority.ALWAYS); runInfoBtn.setMaxWidth(Double.MAX_VALUE); runInfoBtn.setMaxHeight(Double.MAX_VALUE);

        moneyLbl.getStyleClass().add("money"); moneyLbl.setMaxWidth(Double.MAX_VALUE);

        Button optionsBtn = btn("Options", "btn-orange");
        optionsBtn.setOnAction(e -> hint("Options — not wired yet."));
        HBox row3 = new HBox(8, optionsBtn, cell("Ante", anteVal, "gold"), cell("Round", roundVal, "gold"));
        HBox.setHgrow(optionsBtn, Priority.ALWAYS); optionsBtn.setMaxWidth(Double.MAX_VALUE); optionsBtn.setMaxHeight(Double.MAX_VALUE);

        sinChip.getStyleClass().add("sin-chip"); sinChip.setMaxWidth(Double.MAX_VALUE); sinChip.setWrapText(true);
        status.getStyleClass().add("status-line"); status.setMaxWidth(Double.MAX_VALUE);

        Region spring = new Region(); VBox.setVgrow(spring, Priority.ALWAYS);
        sidebar.getChildren().addAll(headerSlot, scoreRow, cm, row2, moneyLbl, row3, sinChip, spring, status);
    }

    private Node buildTopSlots() {
        VBox left = new VBox(4, jokerCount, jokerRow);
        jokerCount.getStyleClass().add("slot-count");
        VBox right = new VBox(4, consCount, consRow);
        right.setAlignment(Pos.TOP_RIGHT); consRow.setAlignment(Pos.CENTER_RIGHT);
        consCount.getStyleClass().add("slot-count");
        Region spring = new Region(); HBox.setHgrow(spring, Priority.ALWAYS);
        HBox box = new HBox(left, spring, right);
        box.setAlignment(Pos.TOP_LEFT);
        return box;
    }

    private Node buildCenter() {
        selectionTiles.setAlignment(Pos.TOP_CENTER);
        blindPane.setAlignment(Pos.CENTER);
        handRow.setAlignment(Pos.CENTER);
        shopPane.setAlignment(Pos.TOP_CENTER);
        resultPane.setAlignment(Pos.CENTER);

        // blind pane content (hand + buttons) built once; hand rebuilt per frame
        playBtn = btn("Play Hand", "btn-red");
        playBtn.setOnAction(e -> { List<Integer> sel = selectedIndices();
            if (sel.isEmpty()) hint("Select 1-" + MAX_SELECTION + " cards to play."); else vm.playHand(sel); });
        discardBtn = btn("Discard", "btn-grey");
        discardBtn.setOnAction(e -> { List<Integer> sel = selectedIndices();
            if (sel.isEmpty()) hint("Select 1-" + MAX_SELECTION + " cards to discard."); else vm.discard(sel); });
        finishBtn = btn("Finish Round", "btn-grey");
        finishBtn.setOnAction(e -> vm.finishRound());
        sortRankBtn = btn("Rank", "btn-orange"); sortSuitBtn = btn("Suit", "btn-orange");
        sortRankBtn.setOnAction(e -> { handSort = 1; if (snapshot != null) rebuildHand(snapshot); });
        sortSuitBtn.setOnAction(e -> { handSort = 2; if (snapshot != null) rebuildHand(snapshot); });
        Label sortK = new Label("Sort Hand"); sortK.getStyleClass().add("cell-k");
        VBox sortBox = new VBox(4, sortK, new HBox(6, sortRankBtn, sortSuitBtn));
        sortBox.getStyleClass().add("panel"); sortBox.setAlignment(Pos.CENTER);
        HBox blindButtons = new HBox(14, playBtn, sortBox, finishBtn, discardBtn);
        blindButtons.setAlignment(Pos.CENTER);
        Label handCountLbl = new Label(); handCountLbl.getStyleClass().add("cell-k");
        blindPane.getChildren().addAll(handRow, handCountLbl, blindButtons);
        blindPane.setUserData(handCountLbl);

        centerStack.getChildren().addAll(selectionTiles, blindPane, shopPane, resultPane);
        return centerStack;
    }

    private Node buildDeck() {
        Region deckCard = new Region(); deckCard.getStyleClass().add("deck-card");
        VBox box = new VBox(6, deckCard, deckCount);
        box.setAlignment(Pos.BOTTOM_CENTER);
        deckCount.getStyleClass().add("deck-count");
        return box;
    }

    private StackPane buildOverlay(StackPane overlay, VBox body, String title) {
        overlay.getStyleClass().add("overlay-scrim");
        overlay.setVisible(false); overlay.setManaged(false);
        VBox panel = new VBox();
        panel.getStyleClass().add("runinfo-panel");
        Label t = new Label(title); t.getStyleClass().add("runinfo-title");
        Button close = btn("Close", "btn-red");
        close.setOnAction(e -> showOverlay(overlay, false));
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        HBox head = new HBox(t, sp, close); head.getStyleClass().add("runinfo-head"); head.setAlignment(Pos.CENTER_LEFT);
        body.getStyleClass().add("runinfo-body");
        panel.getChildren().addAll(head, body);
        overlay.getChildren().add(panel);
        overlay.setOnMouseClicked(e -> { if (e.getTarget() == overlay) showOverlay(overlay, false); });
        return overlay;
    }

    // ================= per-frame render =================

    private void render(MatchSnapshot s) {
        snapshot = s;
        MatchPhase phase = s.phase();
        if (phase != lastPhase) { Log.phase(lastPhase, phase); lastPhase = phase; handSort = 0; }
        if (phase != MatchPhase.RESULT) resultContinued = false;

        updateSidebar(s);
        updateTopSlots(s);
        deckCount.setText(s.deckRemaining() + " / " + s.deckTotal());
        rebuildRunInfo(s);

        boolean inSel = phase == MatchPhase.SELECTION;
        boolean inBlind = phase == MatchPhase.BLIND && s.round() != null;
        boolean inResult = phase == MatchPhase.RESULT;
        boolean inShop = phase == MatchPhase.SHOP;

        if (inSel) rebuildSelection(s);
        if (inBlind) rebuildHand(s);
        if (inShop) rebuildShop(s);
        if (inResult) rebuildResult(s);

        showOnly(inSel ? selectionTiles : inBlind ? blindPane : inShop ? shopPane : inResult ? resultPane : null);

        playBtn.setDisable(!inBlind); discardBtn.setDisable(!inBlind); finishBtn.setDisable(!inBlind);
        sortRankBtn.setDisable(!inBlind); sortSuitBtn.setDisable(!inBlind);
    }

    private void showOnly(Node shown) {
        for (Node n : centerStack.getChildren()) { boolean on = n == shown; n.setVisible(on); n.setManaged(on); }
    }

    private void updateSidebar(MatchSnapshot s) {
        // accent + header per phase
        sidebar.getStyleClass().removeAll("accent-blue", "accent-red", "accent-green", "accent-orange");
        switch (s.phase()) {
            case BLIND -> sidebar.getStyleClass().add("accent-blue");
            case SHOP  -> sidebar.getStyleClass().add("accent-red");
            case RESULT-> sidebar.getStyleClass().add("accent-green");
            default    -> sidebar.getStyleClass().add("accent-orange");
        }
        headerSlot.getChildren().setAll(sidebarHeader(s));

        roundScoreVal.setText(s.round() != null ? s.round().score() : "0");
        chipsLbl.setText(s.chips()); multLbl.setText(s.mult());
        moneyLbl.setText("$" + s.money());
        handsVal.setText(String.valueOf(s.hands())); discardsVal.setText(String.valueOf(s.discards()));
        anteVal.setText(s.ante() + "/" + s.anteCount()); roundVal.setText(String.valueOf(s.roundNumber()));
        sinChip.setText("Ante sin — " + s.activeSin());
    }

    private Node sidebarHeader(MatchSnapshot s) {
        switch (s.phase()) {
            case SHOP -> {
                Label m = new Label("SHOP"); m.getStyleClass().add("shop-marquee");
                Label sub = new Label("Improve your run!"); sub.getStyleClass().add("shop-sub");
                VBox v = new VBox(2, m, sub); v.getStyleClass().addAll("side-header", "shop"); v.setAlignment(Pos.CENTER);
                return v;
            }
            case RESULT -> {
                Label l = new Label(blindName(s.blind()) + "\nDefeated!"); l.getStyleClass().add("header-strong");
                l.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
                VBox v = new VBox(l); v.getStyleClass().addAll("side-header", "win"); v.setAlignment(Pos.CENTER);
                return v;
            }
            case BLIND -> {
                StackPane tok = token(s.blind());
                Label meta = new Label("Score at least\n" + s.target());
                meta.getStyleClass().add("stat-v"); meta.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
                HBox v = new HBox(10, tok, meta); v.getStyleClass().addAll("side-header", "blind"); v.setAlignment(Pos.CENTER_LEFT);
                return v;
            }
            default -> {
                Label l = new Label("Choose your\nnext Blind"); l.getStyleClass().add("side-title");
                return l;
            }
        }
    }

    private void updateTopSlots(MatchSnapshot s) {
        jokerCount.setText(s.jokerSlotsUsed() + "/" + s.jokerSlotsMax());
        consCount.setText(s.consumableSlotsUsed() + "/" + s.consumableSlotsMax());

        jokerRow.getChildren().clear();
        for (String j : s.jokers()) jokerRow.getChildren().add(mini(j, "joker-alt", null));
        for (int i = s.jokers().size(); i < s.jokerSlotsMax(); i++) jokerRow.getChildren().add(mini("", "empty", null));

        consRow.getChildren().clear();
        for (MatchSnapshot.ItemView it : s.inventory())
            consRow.getChildren().add(mini(it.label(), it.isRelic() ? "joker" : "tarot", it));
        for (int i = s.inventory().size(); i < s.consumableSlotsMax(); i++)
            consRow.getChildren().add(mini("", "empty", null));
    }

    // ================= selection =================

    private void rebuildSelection(MatchSnapshot s) {
        selectionTiles.getChildren().clear();
        for (MatchSnapshot.BlindOption b : s.blinds()) selectionTiles.getChildren().add(blindTile(b, s.hasChosen()));
    }

    private Node blindTile(MatchSnapshot.BlindOption b, boolean chosen) {
        VBox tile = new VBox(); tile.getStyleClass().add("blind-tile");
        boolean current = b.current();
        String tclass = current ? "current" : b.bossName() != null ? "boss" : "big";
        tile.getStyleClass().add(tclass);

        if (!current) { Label up = new Label("Upcoming"); up.getStyleClass().add("bt-up"); tile.getChildren().add(up); }
        Label name = new Label(b.bossName() != null ? b.bossName() : b.type());
        name.getStyleClass().addAll("bt-name", tclass); name.setMaxWidth(Double.MAX_VALUE);
        tile.getChildren().add(name);

        tile.getChildren().add(token(tokenKind(b)));

        if (b.bossEffect() != null) { Label eff = new Label(b.bossEffect()); eff.getStyleClass().add("bt-eff"); eff.setMaxWidth(180); tile.getChildren().add(eff); }

        Label req = new Label("Score at least"); req.getStyleClass().add("bt-req");
        Label reqN = new Label(String.valueOf(b.target())); reqN.getStyleClass().add("bt-req-n");
        VBox reqBox = new VBox(2, req, reqN); reqBox.setAlignment(Pos.CENTER);
        tile.getChildren().add(reqBox);

        if (!current) { Label rw = new Label("Reward: " + dollars(b.reward())); rw.getStyleClass().add("bt-reward"); tile.getChildren().add(rw); }

        if (current) {
            Button select = btn("Select", "btn-orange"); select.setMaxWidth(Double.MAX_VALUE);
            select.setDisable(chosen); select.setOnAction(e -> vm.playBlind());
            Label or = new Label("— or —"); or.getStyleClass().add("bt-or");
            Button skip = btn("Skip Blind" + (b.skipTag() != null ? "  (" + b.skipTag() + ")" : ""), "btn-red");
            skip.setMaxWidth(Double.MAX_VALUE); skip.setDisable(chosen); skip.setOnAction(e -> vm.skipBlind());
            tile.getChildren().addAll(select, or, skip);
            if (chosen) { Label w = new Label("chosen — waiting…"); w.getStyleClass().add("bt-or"); tile.getChildren().add(w); }
        } else if (b.bossName() != null) {
            Label ap = new Label("Raise all Blinds · Refresh Blinds"); ap.getStyleClass().add("bt-ante"); ap.setMaxWidth(180);
            Label aph = new Label("Up the Ante"); aph.getStyleClass().add("bt-ante-h");
            tile.getChildren().addAll(aph, ap);
        }
        return tile;
    }

    // ================= blind (hand) =================

    private void rebuildHand(MatchSnapshot s) {
        handCards.clear(); handRow.getChildren().clear();
        List<MatchSnapshot.HandCardView> hand = s.hand();
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < hand.size(); i++) order.add(i);
        if (handSort == 1)
            order.sort(Comparator.comparingInt((Integer i) -> hand.get(i).rank()).reversed()
                    .thenComparingInt(i -> hand.get(i).suit()));
        else if (handSort == 2)
            order.sort(Comparator.comparingInt((Integer i) -> hand.get(i).suit())
                    .thenComparing(Comparator.comparingInt((Integer i) -> hand.get(i).rank()).reversed()));

        for (int modelIndex : order) {
            MatchSnapshot.HandCardView c = hand.get(modelIndex);
            StackPane card = cardNode(c.rank(), c.suit());
            card.setUserData(modelIndex);
            card.setOnMouseClicked(e -> toggleCard(card));
            handCards.add(card);
            handRow.getChildren().add(card);
        }
        Label hc = (Label) blindPane.getUserData();
        hc.setText(hand.size() + " / " + hand.size());
    }

    private void toggleCard(StackPane card) {
        boolean sel = card.getStyleClass().contains("selected");
        if (!sel && selectedIndices().size() >= MAX_SELECTION) { hint("At most " + MAX_SELECTION + " cards."); return; }
        if (sel) { card.getStyleClass().remove("selected"); card.setTranslateY(0); }
        else { card.getStyleClass().add("selected"); card.setTranslateY(-18); }
    }

    private List<Integer> selectedIndices() {
        List<Integer> out = new ArrayList<>();
        for (StackPane c : handCards) if (c.getStyleClass().contains("selected")) out.add((Integer) c.getUserData());
        return out;
    }

    // ================= shop =================

    private void rebuildShop(MatchSnapshot s) {
        shopSelKind = null; shopSelIndex = -1;
        MatchSnapshot.ShopView shop = s.shop();
        shopPane.getChildren().clear();
        if (shop == null) return;

        Button nextRound = btn("Next Round", "btn-red");
        nextRound.setOnAction(e -> vm.readyForNext());
        Button reroll = btn("Reroll\n$" + shop.rerollCost(), "btn-green");
        reroll.setOnAction(e -> vm.rerollShop());
        Button buy = btn("Buy", "btn-orange");
        buy.setOnAction(e -> buySelected());
        VBox controls = new VBox(10, nextRound, reroll, buy);
        for (Node n : controls.getChildren()) { ((Region) n).setMaxWidth(Double.MAX_VALUE); }

        HBox jokerShelf = new HBox(16); jokerShelf.getStyleClass().add("shelf"); jokerShelf.setAlignment(Pos.CENTER);
        for (int i = 0; i < shop.slots().size(); i++) {
            MatchSnapshot.ShopItem it = shop.slots().get(i);
            jokerShelf.getChildren().add(it == null ? soldSlot() : shopCard("slot", i, it, i % 2 == 0 ? "a" : "b", true));
        }
        HBox top = new HBox(16, controls, jokerShelf); HBox.setHgrow(jokerShelf, Priority.ALWAYS);

        VBox voucherShelf = new VBox(10); voucherShelf.getStyleClass().add("shelf");
        Label vl = new Label("Voucher"); vl.getStyleClass().add("shelf-label");
        voucherShelf.getChildren().add(vl);
        for (int i = 0; i < shop.vouchers().size(); i++) {
            MatchSnapshot.VoucherItem v = shop.vouchers().get(i);
            voucherShelf.getChildren().add(v == null ? soldSlot()
                    : shopPack("voucher", i, v.label(), v.price(), v.tooltip(), "vou", v.redeemable()));
        }
        VBox packShelf = new VBox(10); packShelf.getStyleClass().add("shelf");
        Label pl = new Label("Booster Packs"); pl.getStyleClass().add("shelf-label");
        HBox packRow = new HBox(16); packRow.setAlignment(Pos.CENTER);
        for (int i = 0; i < shop.packs().size(); i++) {
            MatchSnapshot.ShopItem p = shop.packs().get(i);
            packRow.getChildren().add(p == null ? soldSlot()
                    : shopPack("pack", i, p.label(), p.price(), p.tooltip(), i == 0 ? "buf" : "arc", true));
        }
        packShelf.getChildren().addAll(pl, packRow);
        HBox bottom = new HBox(14, voucherShelf, packShelf); HBox.setHgrow(packShelf, Priority.ALWAYS);

        VBox wrap = new VBox(14, top, bottom); wrap.getStyleClass().add("shop-wrap");
        shopPane.getChildren().add(wrap);
    }

    private Node shopCard(String kind, int index, MatchSnapshot.ShopItem it, String variant, boolean enabled) {
        VBox card = new VBox(6); card.getStyleClass().addAll("jcard", variant); card.setAlignment(Pos.CENTER);
        Label name = new Label(it.label()); name.getStyleClass().add("jcard-label"); name.setWrapText(true);
        name.setMaxWidth(90); name.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        Label ph = new Label("art tbd"); ph.getStyleClass().add("jcard-ph");
        card.getChildren().addAll(name, ph);
        return priced(kind, index, card, it.price(), it.tooltip(), enabled);
    }

    private Node shopPack(String kind, int index, String label, int price, String tip, String variant, boolean enabled) {
        Label name = new Label(label.replace(" ", "\n")); name.getStyleClass().add("jcard-label");
        name.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        VBox pack = new VBox(name); pack.getStyleClass().addAll("pack", variant); pack.setAlignment(Pos.BOTTOM_CENTER);
        return priced(kind, index, pack, price, tip, enabled);
    }

    /** Wraps a shop item with a price tag above it and click-to-arm selection. */
    private Node priced(String kind, int index, Region item, int price, String tip, boolean enabled) {
        Label price$ = new Label("$" + price); price$.getStyleClass().add("price-tag");
        VBox col = new VBox(4, price$, item); col.setAlignment(Pos.CENTER); col.getStyleClass().add("shop-item");
        if (tip != null) Tooltip.install(col, new Tooltip(tip));
        col.setOpacity(enabled ? 1 : 0.5);
        if (enabled) col.setOnMouseClicked(e -> armShopItem(col, kind, index));
        return col;
    }

    private void armShopItem(VBox col, String kind, int index) {
        for (Node n : allShopItems()) n.getStyleClass().remove("selected");
        col.getStyleClass().add("selected");
        shopSelKind = kind; shopSelIndex = index;
    }

    private List<Node> allShopItems() {
        List<Node> out = new ArrayList<>();
        collectByClass(shopPane, "shop-item", out);
        return out;
    }

    private static void collectByClass(Node root, String cls, List<Node> out) {
        if (root.getStyleClass().contains(cls)) out.add(root);
        if (root instanceof javafx.scene.Parent p) for (Node c : p.getChildrenUnmodifiable()) collectByClass(c, cls, out);
    }

    private void buySelected() {
        if (shopSelKind == null) { hint("Click a shop item first, then Buy."); return; }
        switch (shopSelKind) {
            case "slot" -> vm.buyCard(shopSelIndex);
            case "pack" -> vm.buyPack(shopSelIndex);
            case "voucher" -> vm.redeemVoucher(shopSelIndex);
            default -> hint("Nothing selected.");
        }
    }

    private Node soldSlot() {
        Label l = new Label("(sold)"); l.getStyleClass().add("jcard-ph");
        VBox v = new VBox(l); v.getStyleClass().addAll("jcard", "a"); v.setAlignment(Pos.CENTER); v.setOpacity(0.4);
        return v;
    }

    // ================= result =================

    private void rebuildResult(MatchSnapshot s) {
        resultPane.getChildren().clear();
        MatchSnapshot.ResultView r = s.lastResult();
        VBox panel = new VBox(10); panel.getStyleClass().add("result-panel"); panel.setAlignment(Pos.CENTER);
        Label title = new Label(blindName(s.blind()) + " Defeated"); title.getStyleClass().add("rtitle");
        panel.getChildren().add(title);

        int myRank = 1, total = s.standings().size();
        for (MatchSnapshot.StandingView v : s.standings()) if (v.isMe()) myRank = v.rank() + 1;
        long points = 0;
        for (MatchSnapshot.StandingView v : s.standings()) if (v.isMe()) points = v.points();

        VBox hero = new VBox(2); hero.getStyleClass().add("points-hero"); hero.setAlignment(Pos.CENTER);
        Label hl = new Label("Total points"); hl.getStyleClass().add("ph-lab");
        Label hv = new Label(String.valueOf(points)); hv.getStyleClass().add("ph-val");
        Label hs = new Label("Now " + ordinal(myRank) + " of " + total); hs.getStyleClass().add("ph-sub");
        hero.getChildren().addAll(hl, hv, hs);
        panel.getChildren().add(hero);

        if (r != null) {
            panel.getChildren().add(resultLine("Score", r.score() + " / " + r.target(), "good"));
            panel.getChildren().add(resultLine("Cash out", "+$" + r.moneyEarned(), "gold"));
        }
        Label hint = new Label("Open Run Info for the full standings table."); hint.getStyleClass().add("stand-hint"); hint.setMaxWidth(400);
        Button cont = btn("Continue", "btn-green"); cont.setMaxWidth(Double.MAX_VALUE);
        cont.setDisable(resultContinued);
        cont.setOnAction(e -> { resultContinued = true; cont.setDisable(true); vm.readyForNext(); });
        panel.getChildren().addAll(hint, cont);
        resultPane.getChildren().add(panel);
    }

    private Node resultLine(String k, String v, String vclass) {
        Label kl = new Label(k); kl.getStyleClass().add("rline-k");
        Label vl = new Label(v); vl.getStyleClass().addAll("rline-v", vclass);
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        HBox row = new HBox(kl, sp, vl); row.getStyleClass().add("rline"); row.setMaxWidth(380); row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    // ================= run info (standings) =================

    private void rebuildRunInfo(MatchSnapshot s) {
        runInfoBody.getChildren().clear();
        for (MatchSnapshot.StandingView v : s.standings()) {
            Label pos = new Label(String.valueOf(v.rank() + 1)); pos.getStyleClass().add("rank-pos");
            if (v.rank() == 0) pos.getStyleClass().add("p1");
            Label name = new Label(v.name() + (v.isMe() ? "" : "")); name.getStyleClass().add("rank-name");
            Label you = new Label(v.isMe() ? "◄ you" : ""); you.getStyleClass().add("rank-you");
            Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
            VBox pts = metric(String.valueOf(v.points()), "points", false);
            HBox row = new HBox(14, pos, name, you, sp, pts);
            row.getStyleClass().add("rank-row"); if (v.isMe()) row.getStyleClass().add("me");
            row.setAlignment(Pos.CENTER_LEFT);
            runInfoBody.getChildren().add(row);
        }
        Label note = new Label("Opponents show only points & rank across the information boundary.");
        note.getStyleClass().add("runinfo-note"); note.setMaxWidth(500);
        runInfoBody.getChildren().add(note);
    }

    private VBox metric(String value, String lab, boolean money) {
        Label v = new Label(value); v.getStyleClass().add("rank-metric"); if (money) v.getStyleClass().add("money");
        Label l = new Label(lab); l.getStyleClass().add("rank-metric-lab");
        VBox box = new VBox(v, l); box.setAlignment(Pos.CENTER_RIGHT); return box;
    }

    // ================= held-item action overlay =================

    private void openItemAction(MatchSnapshot.ItemView item) {
        itemBody.getChildren().clear();
        Label name = new Label(item.label()); name.getStyleClass().add("header-strong");
        itemBody.getChildren().add(name);

        TextField seat = new TextField(); seat.setPromptText("seat # (aimed relics)");
        TextField choice = new TextField(); choice.setPromptText("rank / suit / slot / hand");
        if (item.isRelic() && !"NONE".equals(item.selector()) || item.isRelic() && item.needsSeat())
            itemBody.getChildren().add(new HBox(8, seat, choice));

        Button use = btn("Use", "btn-orange");
        use.setOnAction(e -> {
            if (item.isRelic()) {
                RelicTarget t = buildRelicTarget(item, seat.getText(), choice.getText());
                if (t != null) { vm.useRelic(item.modelIndex(), t); showOverlay(itemOverlay, false); }
            } else { vm.useConsumable(item.modelIndex(), selectedIndices()); showOverlay(itemOverlay, false); }
        });
        Button sell = btn("Sell", "btn-red");
        sell.setOnAction(e -> {
            if (item.isRelic()) vm.sellRelic(item.modelIndex()); else vm.sellConsumable(item.modelIndex());
            showOverlay(itemOverlay, false);
        });
        itemBody.getChildren().add(new HBox(10, use, sell));
        showOverlay(itemOverlay, true);
    }

    private RelicTarget buildRelicTarget(MatchSnapshot.ItemView relic, String seatText, String choiceText) {
        PlayerId seat = null;
        if (relic.needsSeat()) {
            if (seatText == null || seatText.isBlank()) { hint(relic.label() + " must be aimed: enter a seat number."); return null; }
            try { seat = vm.seatAt(Integer.parseInt(seatText.trim())); }
            catch (RuntimeException ex) { hint("Bad seat '" + seatText.trim() + "'."); return null; }
        }
        String choice = choiceText == null ? "" : choiceText.trim();
        boolean needsChoice = !"NONE".equals(relic.selector());
        if (needsChoice && choice.isEmpty()) { hint(relic.label() + " needs a " + relic.selector().toLowerCase() + "."); return null; }
        try {
            return switch (relic.selector()) {
                case "RANK" -> RelicTarget.rank(seat, Rank.valueOf(choice.toUpperCase()));
                case "SUIT" -> RelicTarget.suit(seat, Suit.valueOf(choice.toUpperCase()));
                case "JOKER_SLOT" -> RelicTarget.joker(seat, Integer.parseInt(choice));
                case "HAND_TYPE" -> RelicTarget.hand(seat, HandType.valueOf(choice.toUpperCase()));
                default -> seat == null ? RelicTarget.none() : RelicTarget.on(seat);
            };
        } catch (RuntimeException ex) { hint("'" + choice + "' is not a valid " + relic.selector().toLowerCase() + "."); return null; }
    }

    // ================= small builders =================

    private Node mini(String label, String variant, MatchSnapshot.ItemView item) {
        VBox m = new VBox(); m.getStyleClass().addAll("mini", variant); m.setAlignment(Pos.CENTER);
        if (!label.isEmpty()) {
            Label l = new Label(shortName(label)); l.getStyleClass().add("mini-label"); l.setWrapText(true);
            Label ph = new Label("art tbd"); ph.getStyleClass().add("mini-ph");
            m.getChildren().addAll(l, ph);
        }
        if (item != null) m.setOnMouseClicked(e -> openItemAction(item));
        return m;
    }

    private StackPane token(String kind) {
        StackPane t = new StackPane(); t.getStyleClass().addAll("token", kind);
        Label l = new Label(tokenLabel(kind)); l.getStyleClass().add("token-label");
        l.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        t.getChildren().add(l); return t;
    }

    private HBox statRow(String k, Label v, boolean chip) {
        Label kl = new Label(k); kl.getStyleClass().add("stat-k");
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        HBox right = new HBox(6); right.setAlignment(Pos.CENTER_RIGHT);
        if (chip) { Region c = new Region(); c.getStyleClass().add("score-chip"); right.getChildren().add(c); }
        right.getChildren().add(v);
        HBox row = new HBox(kl, sp, right); row.getStyleClass().add("stat"); row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private VBox cell(String k, Label v, String vClass) {
        Label kl = new Label(k); kl.getStyleClass().add("cell-k");
        v.getStyleClass().setAll("cell-v", vClass);
        VBox box = new VBox(kl, v); box.getStyleClass().add("cell"); box.setAlignment(Pos.CENTER);
        HBox.setHgrow(box, Priority.ALWAYS); box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    // ---- cards ----

    private StackPane cardNode(int rankOrd, int suitOrd) {
        StackPane card = new StackPane();
        card.setMinSize(CARD_W, CARD_H); card.setMaxSize(CARD_W, CARD_H);
        if (cardSheet != null) {
            ImageView iv = new ImageView(cardSheet);
            iv.setViewport(new Rectangle2D(rankOrd * cellW, spriteRow(suitOrd) * cellH, cellW, cellH));
            iv.setFitWidth(CARD_W); iv.setFitHeight(CARD_H); iv.setPreserveRatio(false);
            card.getChildren().add(iv);
        } else {
            card.getStyleClass().add("card");
            String color = suitColor(suitOrd);
            Label corner = new Label(RANKS[rankOrd] + suitSymbol(suitOrd)); corner.getStyleClass().add("card-rank");
            corner.setStyle("-fx-text-fill:" + color + ";");
            Label pip = new Label(suitSymbol(suitOrd)); pip.getStyleClass().add("card-pip");
            pip.setStyle("-fx-text-fill:" + color + ";");
            StackPane.setAlignment(corner, Pos.TOP_LEFT); StackPane.setMargin(corner, new Insets(6, 0, 0, 8));
            card.getChildren().addAll(pip, corner);
        }
        return card;
    }

    private static int spriteRow(int suit) { return switch (suit) { case 1 -> 0; case 2 -> 1; case 3 -> 2; default -> 3; }; }
    private static String suitSymbol(int suit) { return switch (suit) { case 1 -> "♥"; case 2 -> "♣"; case 3 -> "♦"; default -> "♠"; }; }
    private static String suitColor(int suit) { return switch (suit) { case 1 -> "#e0392c"; case 2 -> "#2f6fb0"; case 3 -> "#e0861e"; default -> "#33373f"; }; }

    // ---- misc mappers ----

    private static String blindName(String blind) {
        return switch (blind == null ? "" : blind) { case "SMALL" -> "Small Blind"; case "BIG" -> "Big Blind"; case "BOSS" -> "Boss Blind"; default -> "Blind"; };
    }
    private static String tokenKind(MatchSnapshot.BlindOption b) {
        if (b.bossName() != null) return "boss";
        return "Big Blind".equals(b.type()) ? "big" : "small";
    }
    private static String tokenLabel(String kind) {
        return switch (kind) { case "big" -> "BIG\nBLIND"; case "boss" -> "BOSS"; case "small" -> "SMALL\nBLIND";
            case "SMALL" -> "SMALL\nBLIND"; case "BIG" -> "BIG\nBLIND"; case "BOSS" -> "BOSS"; default -> ""; };
    }
    private static String dollars(int n) {
        StringBuilder b = new StringBuilder(); for (int i = 0; i < Math.min(n, 6); i++) b.append('$'); return b + "+";
    }
    private static String ordinal(int n) {
        return switch (n) { case 1 -> "1st"; case 2 -> "2nd"; case 3 -> "3rd"; default -> n + "th"; };
    }
    private static String shortName(String s) { int cut = s.indexOf('-'); return cut > 0 ? s.substring(0, cut) : s; }

    // ================= plumbing =================

    private Button btn(String text, String variant) {
        Button b = new Button(text); b.getStyleClass().addAll("btn", variant);
        b.setTextAlignment(javafx.scene.text.TextAlignment.CENTER); return b;
    }

    private void showOverlay(StackPane overlay, boolean on) { overlay.setVisible(on); overlay.setManaged(on); }

    private void hint(String msg) { Log.ui(msg); status.setText(msg); }

    private void loadCardSheet() {
        try {
            var in = getClass().getResourceAsStream("/cards/deck.png");
            if (in != null) {
                Image img = new Image(in);
                if (!img.isError() && img.getWidth() > 0) { cardSheet = img; cellW = img.getWidth() / 13.0; cellH = img.getHeight() / 4.0; }
            }
        } catch (RuntimeException ignored) { cardSheet = null; }
    }

    private void applyStylesheet(Scene scene) {
        var url = getClass().getResource("/style/balatry.css");
        if (url != null) scene.getStylesheets().add(url.toExternalForm());
    }

    private void applyFont(Node root) {
        String family = "Monospaced";
        try {
            var in = getClass().getResourceAsStream("/font/game.ttf");
            if (in != null) { Font f = Font.loadFont(in, 14); if (f != null) family = f.getFamily(); }
        } catch (RuntimeException ignored) { }
        root.setStyle("-fx-font-family: \"" + family + "\";");
    }

    public static void main(String[] args) { launch(args); }
}
