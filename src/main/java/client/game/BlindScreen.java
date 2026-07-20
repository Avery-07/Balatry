package client.game;

import java.util.List;

import static client.game.Palette.*;

/** The blind: the animated hand (drawn by {@link Hand}) plus Play / Sort / Discard / Finish. */
final class BlindScreen implements Screen {

    @Override
    public void render(Ui ui, double x, double y, double w, double h) {
        ui.hand.render(ui, x, y, w, h);

        double by = y + h - 56, bw = 130, bx = x + w / 2 - (bw * 2 + 30 + 130) / 2;
        ui.button(bx, by, bw, 44, "Play Hand", RED, INK, () -> play(ui), true);
        ui.r.panel(bx + bw + 10, by - 6, 120, 56, PANEL2, javafx.scene.paint.Color.web("#63676f"), 8, 2);
        ui.r.textCenter("Sort Hand", bx + bw + 70, by + 4, 11, DIM);
        ui.button(bx + bw + 20, by + 14, 44, 30, "Rank", ORANGE, DARK, () -> ui.hand.setSort(1), true);
        ui.button(bx + bw + 70, by + 14, 44, 30, "Suit", ORANGE, DARK, () -> ui.hand.setSort(2), true);
        ui.button(bx + bw + 140, by, bw, 44, "Discard", javafx.scene.paint.Color.web("#303237"), INK, () -> discard(ui), true);
        ui.button(bx + bw + 140 + bw + 10, by, 120, 44, "Finish", javafx.scene.paint.Color.web("#303237"), INK, () -> ui.vm.finishRound(), true);
    }

    private void play(Ui ui) {
        List<Integer> sel = ui.hand.selectedModelIndices(ui.s.hand());
        if (sel.isEmpty()) { ui.status = "Select 1-" + Ui.MAX_SELECTION + " cards to play."; return; }
        ui.hand.expectExit(Hand.Exit.PLAYED);   // the cards about to vanish fly up, not off
        ui.vm.playHand(sel);
    }

    private void discard(Ui ui) {
        List<Integer> sel = ui.hand.selectedModelIndices(ui.s.hand());
        if (sel.isEmpty()) { ui.status = "Select 1-" + Ui.MAX_SELECTION + " cards to discard."; return; }
        ui.hand.expectExit(Hand.Exit.DISCARDED);
        ui.vm.discard(sel);
    }
}
