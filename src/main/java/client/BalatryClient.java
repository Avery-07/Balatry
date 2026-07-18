package client;

import debug.Log;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.Scene;
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
 * The Balatry client. Connects to a {@link MatchServer} and renders the local seat's {@link MatchSnapshot} as a
 * persistent HUD (always showing hands, discards, money, ante, round, jokers and consumables) plus one
 * phase-appropriate panel:
 * <ul>
 *   <li><b>Selection</b> — the ante's three blinds side by side, each with its type/effect, chip target and cash
 *       reward; Select and Skip are live only on the current blind.</li>
 *   <li><b>Blind</b> — the dealt hand as clickable cards, sort-by-rank / sort-by-suit, and play / discard /
 *       finish.</li>
 *   <li><b>Shop</b> — clickable item tiles (cards, packs, vouchers) with name and price; hover for details.
 *       Click an item, then Buy. Reroll and Ready need no selection.</li>
 * </ul>
 * Every gesture routes through {@link MatchViewModel} and returns as a broadcast frame that repaints the panel.
 * Connection setup from system properties, matching {@code ServerMain}: {@code -Dbalatry.host -Dbalatry.port
 * -Dbalatry.seed -Dbalatry.players}.
 */
public final class BalatryClient extends Application {

    private static final int MAX_SELECTION = 5;

    private MatchViewModel vm;
    private MatchSnapshot snapshot;      // latest published snapshot; read by gesture handlers on the FX thread
    private MatchPhase lastPhase;
    private boolean resultContinued;     // this seat has hit Continue on the current result screen
    private int handSort;                // 0 = dealt order, 1 = by rank, 2 = by suit (kept across repaints)

    // Shop selection: which offered item is armed for the next Buy. kind is "slot" | "pack" | "voucher".
    private String shopSelKind;
    private int shopSelIndex = -1;

    // --- persistent nodes updated on every snapshot ---
    private final Label hudTop = new Label();
    private final Label hudStats = new Label();
    private final Label hudJokers = new Label();
    private final Label hudConsumables = new Label();
    private final Label hudOpponents = new Label();
    private final Label status = new Label();

    private final FlowPane selectionTiles = new FlowPane(10, 10);
    private final FlowPane handRow = new FlowPane(6, 6);
    private final FlowPane shopSlots = new FlowPane(8, 8);
    private final FlowPane shopPacks = new FlowPane(8, 8);
    private final FlowPane shopVouchers = new FlowPane(8, 8);
    private final List<ToggleButton> cards = new ArrayList<>();

    @Override
    public void start(Stage stage) {
        String host = System.getProperty("balatry.host", "localhost");
        int port = Integer.getInteger("balatry.port", 5555);
        long seed = Long.parseLong(System.getProperty("balatry.seed", "42"));
        List<String> players = List.of(System.getProperty("balatry.players", "P0,P1").split(","));

        hudTop.setStyle("-fx-font-family: monospace; -fx-font-size: 14px; -fx-font-weight: bold;");
        hudStats.setStyle("-fx-font-family: monospace; -fx-font-size: 13px;");
        hudJokers.setStyle("-fx-font-family: monospace; -fx-font-size: 12px;");
        hudConsumables.setStyle("-fx-font-family: monospace; -fx-font-size: 12px;");
        hudOpponents.setStyle("-fx-font-family: monospace; -fx-font-size: 11px;");
        status.setStyle("-fx-font-family: monospace; -fx-font-size: 12px;");
        status.setWrapText(true);
        for (Label l : List.of(hudJokers, hudConsumables, hudOpponents)) l.setWrapText(true);

        VBox hud = new VBox(3, hudTop, hudStats, hudJokers, hudConsumables);
        hud.setStyle("-fx-padding: 8; -fx-border-color: #888; -fx-border-width: 1;");

        // --- SELECTION panel ---
        VBox selectionPanel = titled("Blind Selection", selectionTiles);

        // --- BLIND panel ---
        Button sortRank = new Button("Sort by Rank");
        Button sortSuit = new Button("Sort by Suit");
        Button play = new Button("Play");
        Button discard = new Button("Discard");
        Button finish = new Button("Finish Round");
        HBox sortButtons = new HBox(8, sortRank, sortSuit);
        HBox blindButtons = new HBox(8, play, discard, finish);
        VBox blindPanel = titled("Blind", new VBox(8, handRow, sortButtons, blindButtons));

        // --- RESULT panel ---
        Label resultText = new Label();
        resultText.setStyle("-fx-font-family: monospace; -fx-font-size: 13px;");
        Button continueToShop = new Button("Continue");
        VBox resultPanel = titled("Result", new VBox(8, resultText, continueToShop));

        // --- SHOP panel ---
        Button buy = new Button("Buy");
        Button reroll = new Button("Reroll");
        Button ready = new Button("Ready");
        Button notReady = new Button("Not Ready");
        HBox shopButtons = new HBox(8, buy, reroll, ready, notReady);
        Label rerollLabel = new Label();
        rerollLabel.setStyle("-fx-font-family: monospace; -fx-font-size: 12px;");
        VBox shopPanel = titled("Shop", new VBox(8,
                new Label("Cards"), shopSlots,
                new Label("Packs"), shopPacks,
                new Label("Vouchers"), shopVouchers,
                rerollLabel, shopButtons));

        // --- INVENTORY actions (use / sell a held consumable or relic; usable in every playable phase) ---
        TextField itemIndex = new TextField();
        itemIndex.setPromptText("item #");
        TextField relicSeat = new TextField();
        relicSeat.setPromptText("seat # (aimed items)");
        TextField relicChoice = new TextField();
        relicChoice.setPromptText("rank / suit / slot / hand");
        Button useItem = new Button("Use");
        Button sellItem = new Button("Sell");
        TextField moveFrom = new TextField();
        moveFrom.setPromptText("from");
        TextField moveTo = new TextField();
        moveTo.setPromptText("to");
        Button moveJoker = new Button("Move Joker");
        VBox inventoryPanel = titled("Inventory Actions", new VBox(8,
                new HBox(8, itemIndex, relicSeat, relicChoice, useItem, sellItem),
                new HBox(8, moveFrom, moveTo, moveJoker)));

        VBox root = new VBox(10, hud, selectionPanel, blindPanel, resultPanel, shopPanel, inventoryPanel, hudOpponents, status);
        root.setPadding(new Insets(12));

        ScrollPane scroller = new ScrollPane(root);
        scroller.setFitToWidth(true);
        stage.setTitle("Balatry — client");
        stage.setScene(new Scene(scroller, 720, 900));
        stage.show();

        try {
            MatchClient client = MatchClient.connect(
                    host, port, seed, players,
                    err -> { Log.error(err); Platform.runLater(() -> hint("ERR: " + err)); },
                    a -> { if (vm != null) { Log.recv(a, vm.seat()); vm.onFrameApplied(); } });

            vm = new MatchViewModel(client);

            // Blind phase.
            sortRank.setOnAction(e -> { handSort = 1; rebuildHand(); });
            sortSuit.setOnAction(e -> { handSort = 2; rebuildHand(); });
            play.setOnAction(e -> { List<Integer> s = selectedIndices(); if (guardSel(s, "play")) vm.playHand(s); });
            discard.setOnAction(e -> { List<Integer> s = selectedIndices(); if (guardSel(s, "discard")) vm.discard(s); });
            finish.setOnAction(e -> vm.finishRound());

            // Result phase.
            continueToShop.setOnAction(e -> { resultContinued = true; vm.readyForNext(); });

            // Shop phase.
            buy.setOnAction(e -> buySelectedShopItem());
            reroll.setOnAction(e -> vm.rerollShop());
            ready.setOnAction(e -> vm.readyForNext());
            notReady.setOnAction(e -> vm.notReady());

            // Inventory actions.
            useItem.setOnAction(e -> withIndex(itemIndex, i -> {
                MatchSnapshot.ItemView item = itemAt(i);
                if (item == null) return;
                if (item.isRelic()) {
                    RelicTarget target = buildRelicTarget(item, relicSeat.getText(), relicChoice.getText());
                    if (target != null) vm.useRelic(item.modelIndex(), target);
                } else {
                    vm.useConsumable(item.modelIndex(), selectedIndices());   // selected hand cards are the targets
                }
            }));
            sellItem.setOnAction(e -> withIndex(itemIndex, i -> {
                MatchSnapshot.ItemView item = itemAt(i);
                if (item == null) return;
                if (item.isRelic()) vm.sellRelic(item.modelIndex());
                else vm.sellConsumable(item.modelIndex());
            }));
            moveJoker.setOnAction(e -> withIndex(moveFrom, from -> withIndex(moveTo, to -> vm.moveJoker(from, to))));

            vm.snapshotProperty().addListener((obs, old, snap) -> {
                snapshot = snap;
                if (snap == null) return;

                MatchPhase phase = snap.phase();
                if (phase != lastPhase) { Log.phase(lastPhase, phase); lastPhase = phase; handSort = 0; }
                if (phase != MatchPhase.RESULT) resultContinued = false;

                updateHud(snap);
                rebuildSelection(snap);
                rebuildHand();
                rebuildShop(snap, rerollLabel);
                resultText.setText(resultSummary(snap));

                boolean inSelection = phase == MatchPhase.SELECTION;
                boolean inBlind = phase == MatchPhase.BLIND && snap.round() != null;
                boolean inResult = phase == MatchPhase.RESULT;
                boolean inShop = phase == MatchPhase.SHOP;
                boolean playable = inSelection || inBlind || inResult || inShop;

                show(selectionPanel, inSelection);
                show(blindPanel, inBlind);
                show(resultPanel, inResult);
                show(shopPanel, inShop);
                show(inventoryPanel, playable);

                continueToShop.setDisable(!inResult || resultContinued);
                play.setDisable(!inBlind);
                discard.setDisable(!inBlind);
                finish.setDisable(!inBlind);
                sortRank.setDisable(!inBlind);
                sortSuit.setDisable(!inBlind);
                buy.setDisable(!inShop);
                reroll.setDisable(!inShop);
                ready.setDisable(!inShop);
                notReady.setDisable(!inShop);
            });
            vm.refresh();
        } catch (Exception e) {
            hint("Failed to connect: " + e.getMessage());
        }
    }

    // --- HUD ------------------------------------------------------------------

    private void updateHud(MatchSnapshot s) {
        hudTop.setText("SEAT " + s.seat() + " (" + s.name() + ")   —   " + s.phase());
        hudStats.setText(String.format("Ante %d/%d   Round %d   $%d   Hands %d   Discards %d   Sin: %s",
                s.ante(), s.anteCount(), s.roundNumber(), s.money(), s.hands(), s.discards(), s.activeSin()));
        hudJokers.setText("Jokers: " + (s.jokers().isEmpty() ? "(none)" : String.join(", ", s.jokers())));

        StringBuilder cons = new StringBuilder("Consumables: ");
        if (s.inventory().isEmpty()) cons.append("(none)");
        else for (int i = 0; i < s.inventory().size(); i++) {
            MatchSnapshot.ItemView it = s.inventory().get(i);
            if (i > 0) cons.append("   ");
            cons.append('[').append(i).append("] ").append(it.label());
            if (it.isRelic()) {
                cons.append(" — ").append(targeting(it.kind()));
                if (!"NONE".equals(it.selector()))
                    cons.append(", ").append(it.selector().toLowerCase().replace('_', ' '));
            }
        }
        hudConsumables.setText(cons.toString());

        StringBuilder opp = new StringBuilder();
        for (MatchSnapshot.OpponentView o : s.opponents()) {
            if (opp.length() > 0) opp.append("    ");
            opp.append('#').append(o.seat()).append(' ').append(o.name())
               .append(" — ").append(o.points()).append(" pts (rank ")
               .append(o.rank() < 0 ? "-" : (o.rank() + 1)).append(')');
        }
        hudOpponents.setText(s.phase() == MatchPhase.FINISHED ? standingsSummary(s)
                : "opponents:  " + (opp.length() == 0 ? "(none)" : opp));
    }

    private static String standingsSummary(MatchSnapshot s) {
        StringBuilder b = new StringBuilder("=== MATCH OVER ===\n");
        for (MatchSnapshot.StandingView v : s.standings())
            b.append(v.rank() == 0 ? "  * " : "    ")
             .append(v.rank() + 1).append(". ").append(v.name()).append(v.isMe() ? " (you)" : "")
             .append(" — ").append(v.points()).append(" pts\n");
        MatchSnapshot.StandingView top = s.standings().isEmpty() ? null : s.standings().get(0);
        if (top != null) b.append(top.isMe() ? "You win!" : top.name() + " wins.");
        return b.toString();
    }

    // --- SELECTION ------------------------------------------------------------

    private void rebuildSelection(MatchSnapshot s) {
        selectionTiles.getChildren().clear();
        for (MatchSnapshot.BlindOption b : s.blinds()) {
            VBox tile = new VBox(4);
            tile.setPadding(new Insets(8));
            tile.setStyle("-fx-border-color: " + (b.current() ? "#3a7" : "#999")
                    + "; -fx-border-width: " + (b.current() ? 2 : 1) + ";");
            Label type = new Label(b.type());
            type.setStyle("-fx-font-weight: bold;");
            tile.getChildren().add(type);
            if (b.bossName() != null) {
                Label boss = new Label(b.bossName());
                Label eff = new Label(b.bossEffect());
                eff.setWrapText(true);
                eff.setMaxWidth(200);
                eff.setStyle("-fx-font-size: 11px;");
                tile.getChildren().addAll(boss, eff);
            }
            tile.getChildren().addAll(
                    new Label("Target: " + b.target()),
                    new Label("Reward: $" + b.reward()));

            boolean actionable = b.current() && !s.hasChosen();
            Button select = new Button("Select");
            select.setDisable(!actionable);
            select.setOnAction(e -> vm.playBlind());
            Button skip = new Button("Skip" + (b.skipTag() != null ? " (" + b.skipTag() + ")" : ""));
            skip.setDisable(!actionable);
            skip.setOnAction(e -> vm.skipBlind());
            tile.getChildren().add(new HBox(6, select, skip));

            if (b.current() && s.hasChosen())
                tile.getChildren().add(new Label("chosen — waiting…"));
            selectionTiles.getChildren().add(tile);
        }
    }

    // --- BLIND (hand) ---------------------------------------------------------

    /** Rebuilds the hand toggles from the latest snapshot, honoring the current sort choice. */
    private void rebuildHand() {
        cards.clear();
        handRow.getChildren().clear();
        if (snapshot == null || snapshot.phase() != MatchPhase.BLIND) return;
        List<MatchSnapshot.HandCardView> hand = snapshot.hand();

        // Display order is a permutation of model indices; each toggle remembers its model index for play/discard.
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < hand.size(); i++) order.add(i);
        if (handSort == 1)
            order.sort(Comparator.comparingInt((Integer i) -> hand.get(i).rank()).reversed()
                    .thenComparingInt(i -> hand.get(i).suit()));
        else if (handSort == 2)
            order.sort(Comparator.comparingInt((Integer i) -> hand.get(i).suit())
                    .thenComparing(Comparator.comparingInt((Integer i) -> hand.get(i).rank()).reversed()));

        for (int modelIndex : order) {
            ToggleButton tb = new ToggleButton(hand.get(modelIndex).label());
            tb.setUserData(modelIndex);
            tb.setStyle("-fx-font-family: monospace;");
            tb.setOnAction(e -> {
                if (tb.isSelected() && selectedIndices().size() > MAX_SELECTION) {
                    tb.setSelected(false);
                    hint("At most " + MAX_SELECTION + " cards.");
                }
            });
            cards.add(tb);
            handRow.getChildren().add(tb);
        }
    }

    /** The model hand-indices of the currently selected cards, in display order. */
    private List<Integer> selectedIndices() {
        List<Integer> out = new ArrayList<>();
        for (ToggleButton tb : cards) if (tb.isSelected()) out.add((Integer) tb.getUserData());
        return out;
    }

    // --- SHOP -----------------------------------------------------------------

    private void rebuildShop(MatchSnapshot s, Label rerollLabel) {
        shopSlots.getChildren().clear();
        shopPacks.getChildren().clear();
        shopVouchers.getChildren().clear();
        shopSelKind = null; shopSelIndex = -1;     // a fresh shop frame clears any armed selection
        MatchSnapshot.ShopView shop = s.shop();
        if (shop == null) { rerollLabel.setText(""); return; }

        for (int i = 0; i < shop.slots().size(); i++) {
            MatchSnapshot.ShopItem it = shop.slots().get(i);
            shopSlots.getChildren().add(it == null ? soldTile() : shopTile("slot", i, it, true));
        }
        for (int i = 0; i < shop.packs().size(); i++) {
            MatchSnapshot.ShopItem it = shop.packs().get(i);
            shopPacks.getChildren().add(it == null ? soldTile() : shopTile("pack", i, it, true));
        }
        for (int i = 0; i < shop.vouchers().size(); i++) {
            MatchSnapshot.VoucherItem v = shop.vouchers().get(i);
            if (v == null) { shopVouchers.getChildren().add(soldTile()); continue; }
            shopVouchers.getChildren().add(
                    shopTile("voucher", i, new MatchSnapshot.ShopItem(v.label(), v.price(), v.tooltip()), v.redeemable()));
        }
        rerollLabel.setText("reroll $" + shop.rerollCost() + "   purchases left "
                + (shop.purchasesRemaining() == Integer.MAX_VALUE ? "∞" : shop.purchasesRemaining()));
    }

    /** A spent slot (bought card/pack or redeemed voucher): a disabled placeholder that keeps the row aligned. */
    private static ToggleButton soldTile() {
        ToggleButton t = new ToggleButton("(sold)");
        t.setStyle("-fx-font-family: monospace;");
        t.setDisable(true);
        return t;
    }

    /** A selectable shop tile showing "name $price" with a hover tooltip; disabled tiles can't be armed. */
    private ToggleButton shopTile(String kind, int index, MatchSnapshot.ShopItem item, boolean enabled) {
        ToggleButton t = new ToggleButton(item.label() + "  $" + item.price());
        t.setStyle("-fx-font-family: monospace;");
        t.setTooltip(new Tooltip(item.tooltip()));
        t.setDisable(!enabled);
        t.setOnAction(e -> {
            if (t.isSelected()) {
                clearShopSelectionExcept(t);
                shopSelKind = kind; shopSelIndex = index;
            } else { shopSelKind = null; shopSelIndex = -1; }
        });
        return t;
    }

    private void clearShopSelectionExcept(ToggleButton keep) {
        for (FlowPane group : List.of(shopSlots, shopPacks, shopVouchers))
            for (var node : group.getChildren())
                if (node instanceof ToggleButton other && other != keep) other.setSelected(false);
    }

    private void buySelectedShopItem() {
        if (shopSelKind == null || shopSelIndex < 0) { hint("Click a shop item first, then Buy."); return; }
        switch (shopSelKind) {
            case "slot"    -> vm.buyCard(shopSelIndex);
            case "pack"    -> vm.buyPack(shopSelIndex);
            case "voucher" -> vm.redeemVoucher(shopSelIndex);
            default        -> hint("Nothing selected.");
        }
    }

    // --- RESULT ---------------------------------------------------------------

    private static String resultSummary(MatchSnapshot s) {
        if (s.phase() != MatchPhase.RESULT || s.lastResult() == null) return "";
        MatchSnapshot.ResultView r = s.lastResult();
        return "outcome : " + r.outcome() + "\n"
                + "score   : " + r.score() + " / " + r.target() + "\n"
                + "best    : " + r.bestHand() + "\n"
                + "earned  : $" + r.moneyEarned() + "\n"
                + ("BOSS".equals(s.blind()) ? "Continue to finish the ante." : "Continue to the shop.");
    }

    // --- inventory-action helpers --------------------------------------------

    /** The inventory item at merged index {@code i}, or {@code null} (after explaining) if there is none. */
    private MatchSnapshot.ItemView itemAt(int i) {
        if (snapshot == null || i < 0 || i >= snapshot.inventory().size()) {
            hint("No item at index " + i + ".");
            return null;
        }
        return snapshot.inventory().get(i);
    }

    /**
     * Assembles a {@link RelicTarget} from what the relic itself says it needs. Only relics the caster aims by
     * hand read the seat box; the standings-driven ones ignore it. Returns {@code null} (after explaining) when
     * the caster has not supplied what the relic asks for.
     */
    private RelicTarget buildRelicTarget(MatchSnapshot.ItemView relic, String seatText, String choiceText) {
        PlayerId seat = null;
        if (relic.needsSeat()) {
            if (seatText == null || seatText.trim().isEmpty()) {
                hint(relic.label() + " must be aimed: enter a seat number.");
                return null;
            }
            try {
                seat = vm.seatAt(Integer.parseInt(seatText.trim()));
            } catch (RuntimeException ex) {
                hint("Bad seat '" + seatText.trim() + "': " + ex.getMessage());
                return null;
            }
        }
        String choice = choiceText == null ? "" : choiceText.trim();
        boolean needsChoice = !"NONE".equals(relic.selector());
        if (needsChoice && choice.isEmpty()) {
            hint(relic.label() + " needs a " + relic.selector().toLowerCase().replace('_', ' ') + ".");
            return null;
        }
        try {
            return switch (relic.selector()) {
                case "RANK"       -> RelicTarget.rank(seat, Rank.valueOf(choice.toUpperCase()));
                case "SUIT"       -> RelicTarget.suit(seat, Suit.valueOf(choice.toUpperCase()));
                case "JOKER_SLOT" -> RelicTarget.joker(seat, Integer.parseInt(choice));
                case "HAND_TYPE"  -> RelicTarget.hand(seat, HandType.valueOf(choice.toUpperCase()));
                default           -> seat == null ? RelicTarget.none() : RelicTarget.on(seat);
            };
        } catch (RuntimeException ex) {
            hint("'" + choice + "' is not a valid " + relic.selector().toLowerCase().replace('_', ' ') + ".");
            return null;
        }
    }

    /** Plain-language description of who a relic lands on, so the panel never implies a choice that isn't real. */
    private static String targeting(String kind) {
        return switch (kind) {
            case "OPPONENT" -> "aim at any seat";
            case "RIVAL"    -> "aim at a seat above you";
            case "RIVALS"   -> "hits everyone above you";
            case "SELF"     -> "affects you";
            case "GLOBAL"   -> "affects the whole table";
            default          -> kind;
        };
    }

    // --- small UI helpers -----------------------------------------------------

    private static VBox titled(String title, javafx.scene.Node body) {
        Label t = new Label(title);
        t.setStyle("-fx-font-weight: bold;");
        VBox box = new VBox(6, t, body);
        box.setStyle("-fx-padding: 6; -fx-border-color: #bbb; -fx-border-width: 1;");
        return box;
    }

    private static void show(javafx.scene.Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }

    private boolean guardSel(List<Integer> sel, String verb) {
        if (sel.isEmpty()) { hint("Select 1-" + MAX_SELECTION + " cards to " + verb + "."); return false; }
        return true;
    }

    private void withIndex(TextField field, java.util.function.IntConsumer action) {
        String raw = field.getText();
        if (raw == null || raw.trim().isEmpty()) { hint("Enter an index first."); return; }
        try {
            action.accept(Integer.parseInt(raw.trim()));
        } catch (NumberFormatException nfe) {
            hint("Bad index: '" + raw.trim() + "'");
        }
    }

    private void hint(String msg) {
        Log.ui(msg);
        status.setText(msg);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
