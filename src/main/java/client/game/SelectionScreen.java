package client.game;

import client.MatchSnapshot;

import java.util.List;

import static client.game.Palette.*;

/** Blind selection: the ante's three blind tiles; the current one carries live Select / Skip buttons. */
final class SelectionScreen implements Screen {

    @Override
    public void render(Ui ui, double x, double y, double w, double h) {
        Renderer r = ui.r;
        List<MatchSnapshot.BlindOption> blinds = ui.s.blinds();
        int n = Math.max(1, blinds.size());
        double gap = 18, tw = (w - gap * (n - 1)) / n, th = Math.min(h, 470);
        double ty = y + (h - th) / 2;
        for (int i = 0; i < blinds.size(); i++) {
            MatchSnapshot.BlindOption b = blinds.get(i);
            double tx = x + i * (tw + gap);
            boolean cur = b.current();
            boolean boss = b.bossName() != null;
            javafx.scene.paint.Color accent = cur ? BLUE : boss ? PURPLE : GOLD;
            r.panel(tx, ty, tw, th, PANEL, accent, 12, 3);
            double my = ty + 14, mc = tx + tw / 2;
            if (!cur) { r.textCenter("Upcoming", mc, my, 13, FAINT); my += 20; }
            r.panel(tx + 12, my, tw - 24, 34, accent, null, 8, 0);
            r.textCenterBold(boss ? b.bossName() : b.type(), mc, my + 17, 15, cur || boss ? INK : DARK);
            my += 48;
            if (b.bossEffect() != null) { r.textCenter(b.bossEffect(), mc, my + 10, 12, javafx.scene.paint.Color.web("#e7c7f0")); my += 44; }
            r.textCenter("Score at least", mc, my, 12, DIM);
            r.textCenterBold(String.valueOf(b.target()), mc, my + 24, 24, RED); my += 46;
            if (!cur) { r.textCenterBold("Reward: " + Fmt.dollars(b.reward()), mc, my, 13, GOLD); my += 26; }
            if (cur) {
                ui.button(tx + 16, my, tw - 32, 40, "Select", ORANGE, DARK, () -> ui.vm.playBlind(), !ui.s.hasChosen());
                my += 50;
                String sk = "Skip" + (b.skipTag() != null ? "  (" + b.skipTag() + ")" : "");
                ui.button(tx + 16, my, tw - 32, 40, sk, RED, INK, () -> ui.vm.skipBlind(), !ui.s.hasChosen());
                if (ui.s.hasChosen()) r.textCenter("chosen — waiting…", mc, my + 60, 12, FAINT);
            }
        }
    }
}
