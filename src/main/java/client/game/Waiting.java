package client.game;

import static client.game.Palette.*;

/**
 * The "your move is in, we're waiting on the others" affordance for the two lockstep barriers a player can reach
 * by pressing a button — leaving the result screen and leaving the shop. (Blind selection has the same shape;
 * {@code SelectionScreen} says "chosen — waiting…" beside its own buttons.)
 *
 * <p>Without this the button simply stops responding and the table appears frozen, which reads as a bug rather
 * than as multiplayer. Once a seat has signalled, the button becomes an un-pressable acknowledgement and a line
 * underneath names who is still out — with a live count, so a four-player table can see progress.
 */
final class Waiting {

    private Waiting() { }

    /**
     * Draws the barrier button: pressable while this seat has not signalled, an acknowledgement once it has.
     * The tally underneath appears only while genuinely waiting on someone else.
     */
    static void button(Ui ui, double x, double y, double w, double h, String label) {
        boolean ready = ui.s.isReady();
        if (!ready) {
            ui.button(x, y, w, h, label, GREEN, INK, () -> ui.vm.readyForNext(), true);
            return;
        }
        // Registered: a flat, un-pressable panel — deliberately not ui.button(), which would imply it can be
        // clicked. Pressing again is also a no-op in the model, so there is nothing to arm.
        ui.r.panel(x, y, w, h, javafx.scene.paint.Color.web("#1f2a22"), GREEN, 8, 2);
        ui.r.textCenterBold("Ready — waiting for others…", x + w / 2, y + h / 2, 14, GREEN);
        ui.r.textCenter(tally(ui), x + w / 2, y + h + 14, 11, FAINT);
    }

    /** "2 of 3 players ready" — omitted in a solo-ish table where the count says nothing. */
    private static String tally(Ui ui) {
        int total = ui.s.activeSeats();
        if (total <= 1) return "";
        return ui.s.readyCount() + " of " + total + " players ready";
    }
}
