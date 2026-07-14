package client;

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import model.game.net.MatchClient;

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

    /** FX thread only: rebuild the whole snapshot from the settled local model and publish it. */
    public void refresh() {
        snapshot.set(MatchSnapshot.of(client));
    }

    /** Receive-thread entry point: the single hand-off from the socket thread to the FX thread. */
    public void onFrameApplied() {
        Platform.runLater(this::refresh);
    }
}
