package client;

import debug.Log;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.util.Duration;
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
import java.util.List;

/**
 * The Balatry debug client. Connects to a {@link MatchServer} and renders the local seat's {@link MatchSnapshot}
 * as a status panel with phase-appropriate controls, mirroring the four game menus:
 * <ul>
 *   <li><b>Selection</b> ({@code SELECTION}) — Play Blind / Skip Blind, showing the target and skip tag; after
 *       choosing, the seat waits at the enter-blind barrier for the others.</li>
 *   <li><b>Blind</b> ({@code BLIND}) — clickable toggle cards with play / discard / finish.</li>
 *   <li><b>Shop</b> ({@code SHOP}) — indexed listing with buy / sell / reroll / ready, and the just-finished
 *       blind's result summary at the top (the Result menu is folded in here for now).</li>
 * </ul>
 * Every gesture routes through {@link MatchViewModel} and returns as a broadcast frame that repaints the panel;
 * the terminal {@link Log} shows the ordered action stream. Connection setup from system properties, matching
 * {@code ServerMain}: {@code -Dbalatry.host -Dbalatry.port -Dbalatry.seed -Dbalatry.players}.
 */
public final class BalatryClient extends Application {

    private static final int MAX_SELECTION = 5;
    private MatchPhase lastPhase;
    private MatchSnapshot snapshot;   // latest published snapshot; read by gesture handlers on the FX thread
    private boolean resultContinued;   // this seat has hit Continue on the current result screen

    @Override
    public void start(Stage stage) {
        String host = System.getProperty("balatry.host", "localhost");
        int port = Integer.getInteger("balatry.port", 5555);
        long seed = Long.parseLong(System.getProperty("balatry.seed", "42"));
        List<String> players = List.of(System.getProperty("balatry.players", "P0,P1").split(","));

        Label status = new Label("Connecting to " + host + ":" + port + " \u2026");
        status.setStyle("-fx-font-family: monospace; -fx-font-size: 13px;");
        status.setWrapText(true);

        // --- SELECTION panel ---
        Button playBlind = new Button("Play Blind");
        Button skipBlind = new Button("Skip Blind");
        HBox selectionButtons = new HBox(8);
        selectionButtons.getChildren().addAll(playBlind, skipBlind);
        VBox selectionPanel = new VBox(8);
        selectionPanel.getChildren().addAll(selectionButtons);

        // --- BLIND panel ---
        FlowPane handRow = new FlowPane(6, 6);
        final List<ToggleButton> cards = new ArrayList<>();
        Button play = new Button("Play");
        Button discard = new Button("Discard");
        Button finish = new Button("Finish Round");
        HBox blindButtons = new HBox(8);
        blindButtons.getChildren().addAll(play, discard, finish);
        VBox blindPanel = new VBox(8);
        blindPanel.getChildren().addAll(handRow, blindButtons);

        // --- RESULT panel ---
        Button continueToShop = new Button("Continue");
        VBox resultPanel = new VBox(8);
        resultPanel.getChildren().addAll(continueToShop);

        // --- INVENTORY panel (usable in every playable phase, not just the shop) ---
        TextField consIndex = new TextField();
        consIndex.setPromptText("consumable #");
        Button useCons = new Button("Use Consumable");
        HBox consRow = new HBox(8);
        consRow.getChildren().addAll(consIndex, useCons);

        TextField relicIndex = new TextField();
        relicIndex.setPromptText("relic #");
        TextField relicSeat = new TextField();
        relicSeat.setPromptText("seat # (only if aimed)");
        TextField relicChoice = new TextField();
        relicChoice.setPromptText("rank / suit / slot / hand");
        Button useRelic = new Button("Use Relic");
        HBox relicRow = new HBox(8);
        relicRow.getChildren().addAll(relicIndex, relicSeat, relicChoice, useRelic);

        FlowPane sellRow = new FlowPane(6, 6);

        TextField moveFrom = new TextField();
        moveFrom.setPromptText("from");
        TextField moveTo = new TextField();
        moveTo.setPromptText("to");
        Button moveJoker = new Button("Move Joker");
        HBox moveRow = new HBox(8);
        moveRow.getChildren().addAll(moveFrom, moveTo, moveJoker);

        VBox inventoryPanel = new VBox(8);
        inventoryPanel.getChildren().addAll(consRow, relicRow, moveRow, new Label("Sell"), sellRow);

        // --- PACK panel (opening a pack, then taking options one pick at a time) ---
        TextField packIndex = new TextField();
        packIndex.setPromptText("pending pack #");
        Button openPack = new Button("Open Pack");
        TextField optionIndex = new TextField();
        optionIndex.setPromptText("option #");
        Button takeOption = new Button("Take Option");
        HBox packRow = new HBox(8);
        packRow.getChildren().addAll(packIndex, openPack, optionIndex, takeOption);
        VBox packPanel = new VBox(8);
        packPanel.getChildren().addAll(packRow);

        // --- SHOP panel: one button per item, rebuilt each frame ---
        FlowPane shopCards = new FlowPane(6, 6);
        FlowPane shopPacks = new FlowPane(6, 6);
        FlowPane shopVouchers = new FlowPane(6, 6);
        Button reroll = new Button("Reroll");
        Button ready = new Button("Ready");
        Button notReady = new Button("Not Ready");
        HBox shopActions = new HBox(8);
        shopActions.getChildren().addAll(reroll, ready, notReady);
        VBox shopPanel = new VBox(6);
        shopPanel.getChildren().addAll(
                new Label("Cards"), shopCards,
                new Label("Packs"), shopPacks,
                new Label("Vouchers"), shopVouchers,
                shopActions);

        VBox root = new VBox(10);
        root.setStyle("-fx-padding: 12;");
        root.getChildren().addAll(status, selectionPanel, blindPanel, resultPanel,
                inventoryPanel, packPanel, shopPanel);

        ScrollPane scroller = new ScrollPane(root);
        scroller.setFitToWidth(true);
        stage.setTitle("Balatry \u2014 debug view");
        stage.setScene(new Scene(scroller, 620, 840));
        stage.show();

        final MatchViewModel[] vmRef = new MatchViewModel[1];
        try {
            MatchClient client = MatchClient.connect(
                    host, port, seed, players,
                    err -> { Log.error(err); Platform.runLater(() -> status.setText("ERR: " + err + "\n\n" + status.getText())); },
                    a -> { MatchViewModel v = vmRef[0]; if (v != null) { Log.recv(a, v.seat()); v.onFrameApplied(); } });

            MatchViewModel vm = new MatchViewModel(client);
            vmRef[0] = vm;

            playBlind.setOnAction(e -> vm.playBlind());
            skipBlind.setOnAction(e -> vm.skipBlind());
            continueToShop.setOnAction(e -> { resultContinued = true; vm.readyForNext(); });

            useCons.setOnAction(e -> withIndex(consIndex, status,
                    i -> vm.useConsumable(i, selectedIndices(cards))));   // selected hand cards are the targets
            useRelic.setOnAction(e -> withIndex(relicIndex, status, i -> {
                if (snapshot == null || i < 0 || i >= snapshot.relicCards().size()) {
                    hint(status, "No relic at index " + i + ".");
                    return;
                }
                RelicTarget target = buildRelicTarget(vm, snapshot.relicCards().get(i),
                        relicSeat.getText(), relicChoice.getText(), status);
                if (target != null) vm.useRelic(i, target);
            }));
            moveJoker.setOnAction(e -> withIndex(moveFrom, status,
                    from -> withIndex(moveTo, status, to -> vm.moveJoker(from, to))));

            openPack.setOnAction(e -> withIndex(packIndex, status, vm::openPack));
            takeOption.setOnAction(e -> withIndex(optionIndex, status, i -> {
                MatchSnapshot.PackView pack = snapshot == null ? null : snapshot.openPack();
                if (pack == null || i < 0 || i >= pack.options().size()) {
                    hint(status, "No option at index " + i + " — open a pack first.");
                    return;
                }
                MatchSnapshot.PackOptionView option = pack.options().get(i);
                if (option.taken()) { hint(status, "Option " + i + " has already been taken."); return; }
                if (option.relic() == null) { vm.pickFromPack(i, RelicTarget.none()); return; }
                RelicTarget target = buildRelicTarget(vm, option.relic(),          // a relic pick casts on the spot
                        relicSeat.getText(), relicChoice.getText(), status);
                if (target != null) vm.pickFromPack(i, target);
            }));

            play.setOnAction(e -> { List<Integer> s = selectedIndices(cards); if (guardSel(s, status, "play")) vm.playHand(s); });
            discard.setOnAction(e -> { List<Integer> s = selectedIndices(cards); if (guardSel(s, status, "discard")) vm.discard(s); });
            finish.setOnAction(e -> vm.finishRound());

            reroll.setOnAction(e -> vm.rerollShop());
            ready.setOnAction(e -> vm.readyForNext());
            notReady.setOnAction(e -> vm.notReady());

            vm.snapshotProperty().addListener((obs, old, snap) -> {
                snapshot = snap;
                status.setText(render(snap));
                rebuildHand(handRow, cards, snap, status);
                rebuildShop(shopCards, shopPacks, shopVouchers, snap, vm);
                rebuildSellRow(sellRow, snap, vm);

                MatchPhase phase = snap == null ? null : snap.phase();
                if (phase != lastPhase) { Log.phase(lastPhase, phase); lastPhase = phase; }

                if (phase != MatchPhase.RESULT) resultContinued = false;   // reset when the result screen closes

                boolean inSelection = phase == MatchPhase.SELECTION;
                boolean inBlind = phase == MatchPhase.BLIND && snap.round() != null;
                boolean inResult = phase == MatchPhase.RESULT;
                boolean inShop = phase == MatchPhase.SHOP;
                show(selectionPanel, inSelection);
                show(blindPanel, inBlind);
                show(resultPanel, inResult);
                show(shopPanel, inShop);

                continueToShop.setDisable(!inResult || resultContinued);

                boolean canUseItems = phase == MatchPhase.SELECTION || phase == MatchPhase.BLIND
                        || phase == MatchPhase.RESULT || phase == MatchPhase.SHOP;
                show(inventoryPanel, canUseItems);

                if (inShop && snap.shop() != null)
                    reroll.setDisable(snap.money() < snap.shop().rerollCost());

                boolean hasPacks = snap != null && (!snap.pendingPacks().isEmpty() || snap.openPack() != null);
                show(packPanel, hasPacks);
                openPack.setDisable(snap == null || snap.pendingPacks().isEmpty());
                takeOption.setDisable(snap == null || snap.openPack() == null);

                // In selection, buttons are live only until this seat has chosen.
                boolean canChoose = inSelection && !snap.hasChosen();
                playBlind.setDisable(!canChoose);
                skipBlind.setDisable(!canChoose);

                play.setDisable(!inBlind);
                discard.setDisable(!inBlind);
                finish.setDisable(!inBlind);
            });
            vm.refresh();
        } catch (Exception e) {
            status.setText("Failed to connect: " + e.getMessage());
        }
    }

    /**
     * Assembles a {@link RelicTarget} from what the relic itself says it needs. Only relics the caster aims by
     * hand read the seat box; the standings-driven ones ignore it entirely, so offering a seat there would be a
     * lie. Returns {@code null} (after explaining) when the caster has not supplied what the relic asks for.
     */
    private RelicTarget buildRelicTarget(MatchViewModel vm, MatchSnapshot.RelicView relic,
                                         String seatText, String choiceText, Label status) {
        PlayerId seat = null;
        if (relic.needsSeat()) {
            if (seatText == null || seatText.trim().isEmpty()) {
                hint(status, relic.name() + " must be aimed: enter a seat number.");
                return null;
            }
            try {
                seat = vm.seatAt(Integer.parseInt(seatText.trim()));
            } catch (RuntimeException ex) {
                hint(status, "Bad seat '" + seatText.trim() + "': " + ex.getMessage());
                return null;
            }
        }

        String choice = choiceText == null ? "" : choiceText.trim();
        boolean needsChoice = !"NONE".equals(relic.selector());
        if (needsChoice && choice.isEmpty()) {
            hint(status, relic.name() + " needs a " + relic.selector().toLowerCase().replace('_', ' ') + ".");
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
            hint(status, "'" + choice + "' is not a valid " + relic.selector().toLowerCase().replace('_', ' ') + ".");
            return null;
        }
    }

    private static void show(javafx.scene.Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }

    private static boolean guardSel(List<Integer> sel, Label status, String verb) {
        if (sel.isEmpty()) { hint(status, "Select 1-" + MAX_SELECTION + " cards to " + verb + "."); return false; }
        return true;
    }

    private static void withIndex(TextField field, Label status, java.util.function.IntConsumer action) {
        String raw = field.getText();
        if (raw == null || raw.trim().isEmpty()) { hint(status, "Enter an index first."); return; }
        try {
            action.accept(Integer.parseInt(raw.trim()));
        } catch (NumberFormatException nfe) {
            hint(status, "Bad index: '" + raw.trim() + "'");
        }
    }

    private static void rebuildHand(FlowPane row, List<ToggleButton> cards, MatchSnapshot snap, Label status) {
        cards.clear();
        row.getChildren().clear();
        if (snap == null || snap.phase() != MatchPhase.BLIND) return;
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

    /**
     * Rebuilds the shop as one button per item. Buttons address positions, so a sold slot keeps its place as a
     * disabled button rather than vanishing — otherwise the items after it would slide onto different indices.
     * A button is disabled when the item is sold or the seat cannot afford it, so an illegal buy is unclickable
     * rather than a rejection after the fact.
     */
    private static void rebuildShop(FlowPane cardsRow, FlowPane packsRow, FlowPane vouchersRow,
                                    MatchSnapshot snap, MatchViewModel vm) {
        cardsRow.getChildren().clear();
        packsRow.getChildren().clear();
        vouchersRow.getChildren().clear();
        if (snap == null || snap.shop() == null) return;
        MatchSnapshot.ShopView shop = snap.shop();

        for (int i = 0; i < shop.slots().size(); i++) {
            int index = i;
            cardsRow.getChildren().add(itemButton(shop.slots().get(i), snap.money(), () -> vm.buyCard(index)));
        }
        for (int i = 0; i < shop.packs().size(); i++) {
            int index = i;
            packsRow.getChildren().add(itemButton(shop.packs().get(i), snap.money(), () -> vm.buyPack(index)));
        }
        for (int i = 0; i < shop.vouchers().size(); i++) {
            int index = i;
            vouchersRow.getChildren().add(
                    itemButton(shop.vouchers().get(i), snap.money(), () -> vm.redeemVoucher(index)));
        }
    }

    /** Sell buttons for everything the seat holds, labelled by name so nothing is addressed by number. */
    private static void rebuildSellRow(FlowPane row, MatchSnapshot snap, MatchViewModel vm) {
        row.getChildren().clear();
        if (snap == null) return;
        for (int i = 0; i < snap.jokers().size(); i++) {
            int index = i;
            row.getChildren().add(sellButton("Joker: " + snap.jokers().get(i), () -> vm.sellJoker(index)));
        }
        for (int i = 0; i < snap.consumables().size(); i++) {
            int index = i;
            row.getChildren().add(sellButton("Consumable: " + snap.consumables().get(i), () -> vm.sellConsumable(index)));
        }
        for (int i = 0; i < snap.relicCards().size(); i++) {
            int index = i;
            row.getChildren().add(sellButton("Relic: " + snap.relicCards().get(i).name(), () -> vm.sellRelic(index)));
        }
    }

    private static Button sellButton(String label, Runnable action) {
        Button b = new Button(label);
        b.setStyle("-fx-font-family: monospace;");
        b.setOnAction(e -> action.run());
        return b;
    }

    /** One shop item: name and price on the face, the structured detail on hover. */
    private static Button itemButton(MatchSnapshot.ItemView item, long money, Runnable buy) {
        Button b = new Button(item.sold() ? item.label() : item.label() + "  $" + item.price());
        b.setStyle("-fx-font-family: monospace;");
        boolean affordable = money >= item.price();
        b.setDisable(item.sold() || !affordable);
        Tooltip tip = new Tooltip(item.sold()
                ? item.detail()
                : item.detail() + (affordable ? "" : "\nYou cannot afford this ($" + money + ")"));
        tip.setWrapText(true);
        tip.setShowDelay(Duration.millis(200));
        b.setTooltip(tip);
        b.setOnAction(e -> buy.run());
        return b;
    }

    private static List<Integer> selectedIndices(List<ToggleButton> cards) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < cards.size(); i++) if (cards.get(i).isSelected()) out.add(i);
        return out;
    }

    private static void hint(Label status, String msg) {
        Log.ui(msg);
        status.setText(msg + "\n\n" + status.getText());
    }

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

        if (s.phase() == MatchPhase.SELECTION) {
            b.append("\n=== BLIND SELECTION ===\n");
            b.append("Play the ").append(s.blind()).append(" blind (target ").append(s.target())
             .append("), or Skip for the ").append(s.skipTag()).append(".\n");
            if (s.hasChosen()) b.append("You've chosen \u2014 waiting for the other players\u2026\n");
        }

        if (s.round() != null) {
            MatchSnapshot.RoundView r = s.round();
            b.append("round   : hands ").append(r.handsRemaining())
             .append(", discards ").append(r.discardsRemaining())
             .append(", score ").append(r.score())
             .append(" / ").append(r.roundTarget()).append("\n");
        }

        appendIndexed(b, "\njokers", s.jokers());
        appendIndexed(b, "consumables", s.consumables());
        b.append("relics :");
        if (s.relicCards().isEmpty()) b.append(" (none)\n");
        else {
            b.append("\n");
            for (int i = 0; i < s.relicCards().size(); i++) {
                MatchSnapshot.RelicView r = s.relicCards().get(i);
                b.append("  [").append(i).append("] ").append(r.name())
                 .append("  \u2014 ").append(targeting(r.kind()));
                if (!"NONE".equals(r.selector()))
                    b.append(", choose a ").append(r.selector().toLowerCase().replace('_', ' '));
                b.append("\n");
            }
        }

        if (!s.pendingPacks().isEmpty()) {
            b.append("\npending packs (granted by tags/Wrath; buying a pack opens it at once):\n");
            for (int i = 0; i < s.pendingPacks().size(); i++)
                b.append("  [").append(i).append("] ").append(s.pendingPacks().get(i)).append("\n");
        }

        if (s.openPack() != null) {
            MatchSnapshot.PackView p = s.openPack();
            b.append("\n=== OPEN PACK: ").append(p.pack()).append(" ===   picks left ").append(p.picksLeft()).append("\n");
            for (int i = 0; i < p.options().size(); i++) {
                MatchSnapshot.PackOptionView o = p.options().get(i);
                b.append("  [").append(i).append("] ").append(o.label());
                if (o.relic() != null) {
                    b.append("  \u2014 casts on pick: ").append(targeting(o.relic().kind()));
                    if (!"NONE".equals(o.relic().selector()))
                        b.append(", choose a ").append(o.relic().selector().toLowerCase().replace('_', ' '));
                }
                b.append("\n");
            }
            b.append("Relic picks read the seat / choice boxes in the inventory row.\n");
        }

        if (s.phase() == MatchPhase.RESULT && s.lastResult() != null) {
            MatchSnapshot.ResultView r = s.lastResult();
            b.append("\n=== BLIND RESULT ===\n");
            b.append("outcome : ").append(r.outcome()).append("\n");
            b.append("score   : ").append(r.score()).append(" / ").append(r.target()).append("\n");
            b.append("best    : ").append(r.bestHand()).append("\n");
            b.append("earned  : $").append(r.moneyEarned()).append("\n");
            b.append(s.blind().equals("BOSS") ? "Continue to finish the ante.\n" : "Continue to the shop.\n");
        }

        if (s.shop() != null) {
            MatchSnapshot.ShopView sh = s.shop();
            b.append("\n=== SHOP ===   reroll $").append(sh.rerollCost())
             .append("   purchases left ").append(sh.purchasesRemaining() == Integer.MAX_VALUE ? "\u221e" : String.valueOf(sh.purchasesRemaining())).append("\n");
            b.append("cards    : ").append(names(sh.slots())).append("\n");
            b.append("packs    : ").append(names(sh.packs())).append("\n");
            b.append("vouchers : ").append(names(sh.vouchers())).append("\n");
        }

        if (s.phase() == MatchPhase.FINISHED) {
            b.append("\n=== MATCH OVER ===\n");
            for (MatchSnapshot.StandingView v : s.standings()) {
                boolean winner = v.rank() == 0;
                b.append(winner ? "  * " : "    ")
                 .append(v.rank() + 1).append(". ")
                 .append(v.name()).append(v.isMe() ? " (you)" : "")
                 .append("  \u2014 ").append(v.points()).append(" pts\n");
            }
            MatchSnapshot.StandingView top = s.standings().isEmpty() ? null : s.standings().get(0);
            if (top != null)
                b.append("\n").append(top.isMe() ? "You win!" : top.name() + " wins.").append("\n");
            return b.toString();
        }

        b.append("\n--- opponents (visible only) ---\n");
        for (MatchSnapshot.OpponentView o : s.opponents()) {
            b.append("  #").append(o.seat()).append(' ').append(o.name())
             .append("  points ").append(o.points())
             .append("  rank ").append(o.rank() < 0 ? "-" : (o.rank() + 1)).append("\n");
        }

        if (s.phase() == MatchPhase.BLIND)
            b.append("\nSelect cards, then Play or Discard \u2014 or Finish Round.");
        else if (s.phase() == MatchPhase.SHOP)
            b.append("\nClick an item to buy it; hover for details. Greyed items are sold or unaffordable.");
        return b.toString();
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

    /** A compact one-line summary of a shop row; the buttons carry the detail. */
    private static String names(List<MatchSnapshot.ItemView> items) {
        if (items.isEmpty()) return "(none)";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) b.append(", ");
            MatchSnapshot.ItemView it = items.get(i);
            b.append(it.label());
            if (!it.sold()) b.append(" $").append(it.price());
        }
        return b.toString();
    }

    private static void appendIndexed(StringBuilder b, String label, List<String> items) {
        b.append(label).append(" :");
        if (items.isEmpty()) { b.append(" (none)\n"); return; }
        b.append("\n");
        for (int i = 0; i < items.size(); i++) b.append("  [").append(i).append("] ").append(items.get(i)).append("\n");
    }

    public static void main(String[] args) {
        launch(args);
    }
}
