package client;

import debug.Log;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import model.game.actions.Action;
import model.game.net.MatchClient;

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

    // --- shop-phase gestures -----------------------------------------------

    public void buyCard(int slotIndex) {
        submit(new Action.BuyCard(client.getSeat(), slotIndex));
    }

    public void buyPack(int packIndex) {
        submit(new Action.BuyPack(client.getSeat(), packIndex));
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
