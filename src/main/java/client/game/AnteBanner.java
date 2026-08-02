package client.game;

import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;

import static client.game.Palette.*;

/**
 * The ante-change banner: a brief animated overlay when the table advances to a new ante, naming the sin that was
 * active and the one now taking over (old → new) with the new sin's effect. Purely a client flourish — it slides in
 * and fades, holds, then fades out over a few seconds. {@link #trigger} arms it; {@link #advance} runs its clock.
 */
final class AnteBanner {

    private static final double IN = 0.45, HOLD = 2.6, OUT = 0.7, TOTAL = IN + HOLD + OUT;

    private double t = -1;   // seconds since trigger; < 0 means idle
    private int ante;
    private String oldSin = "", newSin = "", newDesc = "";

    void trigger(int ante, String oldSin, String newSin, String newDesc) {
        this.ante = ante;
        this.oldSin = oldSin;
        this.newSin = newSin;
        this.newDesc = newDesc;
        this.t = 0;
    }

    void advance(double dt) { if (t >= 0) { t += dt; if (t > TOTAL) t = -1; } }

    boolean active() { return t >= 0; }

    void render(Ui ui) {
        if (t < 0) return;
        double alpha = t < IN ? t / IN : (t > IN + HOLD ? Math.max(0, 1 - (t - IN - HOLD) / OUT) : 1);
        double rise = (1 - Math.min(1, t / IN)) * 22;   // slides up into place as it fades in

        Renderer r = ui.r;
        List<String> lines = wrap(newDesc, 70);
        double pw = 780, ph = 98 + lines.size() * 18 + 14, px = (Ui.W - pw) / 2, py = 132 - rise;
        r.gc().setGlobalAlpha(alpha);
        r.panel(px, py, pw, ph, Color.web("#1a0f26"), PURPLE, 14, 3);
        r.textCenterBold("ANTE " + ante, Ui.W / 2.0, py + 28, 18, ORANGE);
        r.textCenterBold(oldSin + "   →   " + newSin, Ui.W / 2.0, py + 64, 26, Color.web("#ecd7f5"));
        double ly = py + 98;
        for (String line : lines) { r.textCenter(line, Ui.W / 2.0, ly, 13, DIM); ly += 18; }
        r.gc().setGlobalAlpha(1);
    }

    /** Greedy word wrap to lines of at most {@code max} characters, so a long sin effect reads on its own rows. */
    private static List<String> wrap(String s, int max) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : s.split(" ")) {
            if (line.length() > 0 && line.length() + 1 + word.length() > max) { out.add(line.toString()); line.setLength(0); }
            if (line.length() > 0) line.append(' ');
            line.append(word);
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }
}
