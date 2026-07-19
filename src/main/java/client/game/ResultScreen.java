package client.game;

import client.MatchSnapshot;

import static client.game.Palette.*;

/** The Balatry-specific result: a points-first hero (total points + standings position), then score and cash-out. */
final class ResultScreen implements Screen {

    @Override
    public void render(Ui ui, double x, double y, double w, double h) {
        Renderer r = ui.r;
        MatchSnapshot.ResultView v = ui.s.lastResult();
        double pw = Math.min(460, w), px = x + (w - pw) / 2, py = y + 40, ph = 360;
        r.panel(px, py, pw, ph, javafx.scene.paint.Color.web("#141517"), GREEN, 12, 3);
        r.textCenterBold(Fmt.blindName(ui.s.blind()) + " Defeated", px + pw / 2, py + 30, 22, GREEN);

        long pts = 0; int rank = 1, total = ui.s.standings().size();
        for (MatchSnapshot.StandingView sv : ui.s.standings()) if (sv.isMe()) { pts = sv.points(); rank = sv.rank() + 1; }
        double hy = py + 60, hw = pw - 60;
        r.panel(px + 30, hy, hw, 120, javafx.scene.paint.Color.web("#231a08"), GOLD, 12, 3);
        r.textCenterBold("TOTAL POINTS", px + pw / 2, hy + 24, 14, DIM);
        r.textCenterBold(String.valueOf(pts), px + pw / 2, hy + 66, 54, ORANGE);
        r.textCenterBold("Now " + Fmt.ordinal(rank) + " of " + total, px + pw / 2, hy + 102, 16, javafx.scene.paint.Color.web("#d89bec"));

        if (v != null) {
            r.textLeft("Score", px + 30, py + 210, 15, DIM);
            r.textCenterBold(v.score() + " / " + v.target(), px + pw - 80, py + 217, 15, GREEN);
            r.textLeft("Cash out", px + 30, py + 240, 15, DIM);
            r.textCenterBold("+$" + v.moneyEarned(), px + pw - 80, py + 247, 15, ORANGE);
        }
        ui.button(px + 30, py + ph - 56, pw - 60, 44, "Continue", GREEN, INK, () -> ui.vm.readyForNext(), true);
    }
}
