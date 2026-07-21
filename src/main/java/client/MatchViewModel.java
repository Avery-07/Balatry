package client;

import debug.Log;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import model.game.actions.Action;
import model.game.net.MatchClient;
import model.game.player.PlayerId;
import model.items.relics.RelicTarget;

import java.util.List;

/**
 * The observable bridge between the networked model and the FX scene graph. It holds one FX property — the
 * current {@link MatchSnapshot} — that the UI binds to; nothing in the view ever reads the model directly.
 *
 * <p><strong>Threading contract.</strong> The model mutates on {@link MatchClient}'s socket-receive thread.
 * {@link #onFrameApplied()} is the callback wired into {@code connect}, invoked on that receive thread after
 * each accepted frame — and it does exactly one thing: marshal a rebuild onto the FX thread via
 * {@link Platform#runLater}. This is the sole point in the client where the background thread hands work to FX.
 * {@link #refresh()} then runs on the FX thread, reads the now-settled model, and publishes a fresh snapshot,
 * so the scene graph is only ever touched from the FX thread and never sees a half-mutated model.
 *
 * <p>Every gesture routes through {@link #submit}, which logs a {@code SEND} line and fires exactly one
 * {@code MatchClient.submit}; the result returns as a broadcast frame, never here. A rejection surfaces via the
 * connect-time {@code onError} callback and leaves the model (and view) unchanged.
 */
public final class MatchViewModel {

    private final MatchClient client;
    private final ObjectProperty<MatchSnapshot> snapshot = new SimpleObjectProperty<>();

    public MatchViewModel(MatchClient client) {
        this.client = client;
    }

    public ReadOnlyObjectProperty<MatchSnapshot> snapshotProperty() {
        return snapshot;
    }

    public MatchSnapshot getSnapshot() {
        return snapshot.get();
    }

    /** The local seat index. */
    public int seat() {
        return client.getSeat().seat();
    }

    /** FX thread only: rebuild the whole snapshot from the settled local model and publish it. */
    public void refresh() {
        snapshot.set(MatchSnapshot.of(client));
    }

    /** Receive-thread entry point: the single hand-off from the socket thread to the FX thread. */
    public void onFrameApplied() {
        Platform.runLater(this::refresh);
    }

    private void submit(Action a) {
        Log.send(a);
        client.submit(a);
    }

    // --- selection-phase gestures ------------------------------------------

    /** Choose to play this blind (SELECTION). */
    public void playBlind() {
        submit(new Action.PlayBlind(client.getSeat()));
    }

    // --- blind-phase gestures ----------------------------------------------

    /** Play the selected hand cards (1-5). */
    public void playHand(List<Integer> handIndices) {
        submit(new Action.PlayHand(client.getSeat(), handIndices));
    }

    /** Discard the selected hand cards. */
    public void discard(List<Integer> handIndices) {
        submit(new Action.DiscardCards(client.getSeat(), handIndices));
    }

    /** Voluntarily end the round, banking the current score. */
    public void finishRound() {
        submit(new Action.FinishRound(client.getSeat()));
    }

    /** Skip this blind for the tag; legal only before the seat has played or discarded. */
    public void skipBlind() {
        submit(new Action.SkipBlind(client.getSeat()));
    }

    // --- inventory gestures -------------------------------------------------

    /** Uses a held consumable, applying it to the given hand-card positions (empty when it needs no cards). */
    public void useConsumable(int index, List<Integer> targetHandIndices) {
        submit(new Action.UseConsumable(client.getSeat(), index, List.copyOf(targetHandIndices)));
    }

    /**
     * Casts a held relic. The caller supplies only what the relic asks for: the standings-driven relics take no
     * seat at all, so the target carries none and the model derives their victims from the standings.
     */
    public void useRelic(int index, RelicTarget target) {
        submit(new Action.UseRelic(client.getSeat(), index, target == null ? RelicTarget.none() : target));
    }

    /** Reorders the board, moving the joker at {@code from} to position {@code to}. */
    public void moveJoker(int from, int to) {
        submit(new Action.MoveJoker(client.getSeat(), from, to));
    }

    /** Reorders the consumable area (drag-and-drop): the card at {@code from} reinserts at {@code to}. */
    public void moveConsumable(int from, int to) {
        submit(new Action.MoveConsumable(client.getSeat(), from, to));
    }

    /** Reorders the relic area (drag-and-drop): the card at {@code from} reinserts at {@code to}. */
    public void moveRelic(int from, int to) {
        submit(new Action.MoveRelic(client.getSeat(), from, to));
    }

    /** The seat sitting at {@code index} in the match's seat order — for aiming a hand-targeted relic. */
    public PlayerId seatAt(int index) {
        List<PlayerId> seats = client.getLocalHost().getMatch().getSeats();
        if (index < 0 || index >= seats.size()) throw new IllegalArgumentException("no seat " + index);
        return seats.get(index);
    }

    // --- shop-phase gestures -----------------------------------------------

    public void buyCard(int slotIndex) {
        submit(new Action.BuyCard(client.getSeat(), slotIndex));
    }

    public void buyPack(int packIndex) {
        submit(new Action.BuyPack(client.getSeat(), packIndex));
    }

    /** Picks the option at {@code optionIndex} from the pack currently being opened. */
    public void pickFromPack(int optionIndex) {
        submit(new Action.PickFromPack(client.getSeat(), optionIndex, RelicTarget.none()));
    }

    public void redeemVoucher(int voucherIndex) {
        submit(new Action.RedeemVoucher(client.getSeat(), voucherIndex));
    }

    public void rerollShop() {
        submit(new Action.RerollShop(client.getSeat()));
    }

    public void sellJoker(int index) {
        submit(new Action.SellJoker(client.getSeat(), index));
    }

    public void sellConsumable(int index) {
        submit(new Action.SellConsumable(client.getSeat(), index));
    }

    public void sellRelic(int index) {
        submit(new Action.SellRelic(client.getSeat(), index));
    }

    public void readyForNext() {
        submit(new Action.ReadyForNext(client.getSeat()));
    }

    public void notReady() {
        submit(new Action.NotReady(client.getSeat()));
    }
}
