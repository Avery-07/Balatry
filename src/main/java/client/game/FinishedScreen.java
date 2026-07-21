package client.game;

import client.MatchSnapshot;
import javafx.scene.paint.Color;

import static client.game.Palette.*;

/**
 * The match-over panel: the local seat's verdict (win / placing), then the final ranked standings — the same
 * points-and-rank table the information boundary allows, now as the definitive result. Reached when the final
 * ante's boss settles the match to {@code FINISHED}; there is nothing left to act on, so it registers no buttons
 * beyond the Run Info the HUD already offers.
 */
final class FinishedScreen implements Screen {

    /** Rank colors for the podium places; everyone below shares the dim row treatment. */
    private static final Color[] MEDAL = { PODIUM_GOLD, Color.web("#c8ccd2"), Color.web("#c98a4b") };

    @Override
    public void render(Ui ui, double x, double y, double w, double h) {
        Renderer r = ui.r;
        MatchSnapshot s = ui.s;

        double pw = Math.min(560, w), px = x + (w - pw) / 2, py = y + 20, ph = Math.min(h - 40, 620);
        r.panel(px, py, pw, ph, Color.web("#141517"), GOLD, 14, 3);
        r.textCenterBold("MATCH OVER", px + pw / 2, py + 34, 26, GOLD);

        MatchSnapshot.StandingView me = s.myStanding();
        int myRank = me.rank() + 1, total = Math.max(1, s.standings().size());
        long myPoints = me.points();
        boolean won = myRank == 1;

        double hy = py + 58, hh = 104;
        r.panel(px + 26, hy, pw - 52, hh, won ? Color.web("#0f2617") : Color.web("#231617"), won ? GREEN : RED, 12, 3);
        r.textCenterBold(won ? "VICTORY" : "DEFEAT", px + pw / 2, hy + 30, 30, won ? GREEN : RED);
        r.textCenter(Fmt.ordinal(myRank) + " of " + total + "  ·  " + myPoints + " pts", px + pw / 2, hy + 66, 15, INK);
        r.textCenter("Ante " + s.ante() + "/" + s.anteCount() + "  ·  Round " + s.roundNumber() + "  ·  $" + s.money(),
                px + pw / 2, hy + 88, 12, DIM);

        r.textLeftBold("FINAL STANDINGS", px + 26, hy + hh + 14, 13, DIM);

        double ry = hy + hh + 38, rowH = 44, gap = 8;
        double listBottom = py + ph - 34;
        for (MatchSnapshot.StandingView v : s.standings()) {
            if (ry + rowH > listBottom) break;   // more seats than the panel can hold; Run Info shows the rest
            Color accent = v.rank() < MEDAL.length ? MEDAL[v.rank()] : EDGE;
            r.panel(px + 26, ry, pw - 52, rowH, v.isMe() ? Color.web("#221d10") : Color.web("#1a1b1f"),
                    v.isMe() ? ORANGE : accent, 10, 2);
            r.textCenterBold(String.valueOf(v.rank() + 1), px + 50, ry + rowH / 2, 20, accent);
            String tag = v.isMe() ? "  ◄ you" : (v.departed() ? "  (left)" : "");
            r.textLeftBold(v.name() + tag, px + 78, ry + rowH / 2 - 8, 16, v.departed() ? FAINT : INK);
            r.textCenterBold(v.points() + " pts", px + pw - 76, ry + rowH / 2, 16,
                    v.departed() ? FAINT : (v.rank() == 0 ? GOLD : GREEN));
            ry += rowH + gap;
        }

        ui.button(px + pw / 2 - 90, py + ph - 46, 180, 36, "Back to menu", GREEN, INK, ui.onLeaveMatch, true);
    }
}
