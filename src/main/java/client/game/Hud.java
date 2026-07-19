package client.game;

import client.MatchSnapshot;
import client.engine.Layout;
import javafx.scene.paint.Color;
import model.game.MatchPhase;

import static client.game.Palette.*;

/** The persistent frame chrome, drawn every phase: left sidebar, top joker/consumable slots, right deck pile. */
final class Hud {

    void render(Ui ui) {
        drawSidebar(ui, Ui.PAD, Ui.PAD, Ui.SIDEBAR, Ui.H - 2 * Ui.PAD);
        double cx = Ui.PAD + Ui.SIDEBAR + 18;
        drawTopSlots(ui, cx, Ui.PAD, Ui.W - Ui.PAD - Ui.DECK_W - 18 - cx, Ui.SLOT_H);
        double cTop = Ui.PAD + Ui.SLOT_H + 12;
        drawDeck(ui, Ui.W - Ui.PAD - Ui.DECK_W, cTop, Ui.DECK_W, Ui.H - Ui.PAD - cTop);
    }

    private void drawSidebar(Ui ui, double x, double y, double w, double h) {
        Renderer r = ui.r;
        MatchSnapshot s = ui.s;
        Color accent = switch (s.phase()) { case BLIND -> BLUE; case SHOP -> RED; case RESULT -> GREEN; default -> GOLD; };
        r.panel(x, y, w, h, PANEL, accent, 12, 3);
        double ix = x + 14, iw = w - 28, cy = y + 16;

        String hdr = switch (s.phase()) {
            case SHOP -> "SHOP"; case RESULT -> Fmt.blindName(s.blind()) + " Defeated!";
            case BLIND -> "Score at least " + s.target(); default -> "Choose your next Blind"; };
        r.panel(ix, cy, iw, 52, PANEL2, EDGE, 9, 2);
        r.textCenterBold(hdr, ix + iw / 2, cy + 26, s.phase() == MatchPhase.SHOP ? 26 : 15, s.phase() == MatchPhase.SHOP ? ORANGE : INK);
        cy += 64;

        cy = statBox(r, ix, cy, iw, "Round score", s.round() != null ? s.round().score() : "0");

        double half = (iw - 30) / 2;
        r.panel(ix, cy, half, 46, BLUE, null, 8, 0); r.textCenterBold(s.chips(), ix + half / 2, cy + 23, 24, INK);
        r.textCenterBold("X", ix + half + 15, cy + 23, 18, RED);
        r.panel(ix + half + 30, cy, half, 46, RED, null, 8, 0); r.textCenterBold(s.mult(), ix + half + 30 + half / 2, cy + 23, 24, INK);
        cy += 58;

        ui.button(ix, cy, 88, 60, "Run Info", RED, INK, () -> ui.showRunInfo = true, true);
        cell(r, ix + 98, cy, (iw - 98 - 10) / 2, 60, "Hands", String.valueOf(s.hands()), BLUE);
        cell(r, ix + 98 + (iw - 98 - 10) / 2 + 10, cy, (iw - 98 - 10) / 2, 60, "Discards", String.valueOf(s.discards()), RED);
        cy += 72;

        r.panel(ix, cy, iw, 44, PANEL, EDGE, 8, 2); r.textCenterBold("$" + s.money(), ix + iw / 2, cy + 22, 26, ORANGE);
        cy += 56;

        ui.button(ix, cy, 88, 56, "Options", ORANGE, DARK, () -> ui.status = "Options — not wired.", true);
        cell(r, ix + 98, cy, (iw - 98 - 10) / 2, 56, "Ante", s.ante() + "/" + s.anteCount(), ORANGE);
        cell(r, ix + 98 + (iw - 98 - 10) / 2 + 10, cy, (iw - 98 - 10) / 2, 56, "Round", String.valueOf(s.roundNumber()), ORANGE);
        cy += 68;

        r.panel(ix, cy, iw, 34, Color.web("#2a1030"), PURPLE, 8, 2);
        r.textCenter("Ante sin — " + s.activeSin(), ix + iw / 2, cy + 17, 12, Color.web("#ecd7f5"));
    }

    private double statBox(Renderer r, double x, double y, double w, String k, String v) {
        r.panel(x, y, w, 40, PANEL, EDGE, 8, 2);
        r.textLeftBold(k, x + 10, y + 13, 14, DIM);
        r.textCenterBold(v, x + w - 30, y + 20, 20, INK);
        return y + 52;
    }

    private void cell(Renderer r, double x, double y, double w, double h, String k, String v, Color vc) {
        r.panel(x, y, w, h, PANEL, EDGE, 8, 2);
        r.textCenter(k, x + w / 2, y + 15, 12, DIM);
        r.textCenterBold(v, x + w / 2, y + h - 18, 22, vc);
    }

    private void drawTopSlots(Ui ui, double x, double y, double w, double h) {
        Renderer r = ui.r;
        MatchSnapshot s = ui.s;
        r.textLeftBold(s.jokerSlotsUsed() + "/" + s.jokerSlotsMax(), x, y, 18, INK);
        double jx = x;
        for (int j = 0; j < s.jokers().size(); j++) {
            Layout.Rect rr = new Layout.Rect(jx, y + 22, 54, Ui.SLOT_H - 30);
            mini(r, rr, Color.web("#c0392b"), Fmt.shortName(s.jokers().get(j)));
            if (j == ui.jokerTarget) r.panel(rr.x() - 3, rr.y() - 3, rr.w() + 6, rr.h() + 6, null, ORANGE, 10, 3);
            ui.jokerSel.add(new Ui.Sel(rr, "joker", j));
            jx += 62;
        }
        r.textLeftBold(s.consumableSlotsUsed() + "/" + s.consumableSlotsMax(), x + w - 40, y, 18, INK);
        double kx = x + w - 54;
        for (int i = s.inventory().size() - 1; i >= 0; i--) {
            Layout.Rect rr = new Layout.Rect(kx, y + 22, 54, Ui.SLOT_H - 30);
            mini(r, rr, Color.web("#3d3357"), Fmt.shortName(s.inventory().get(i).label()));
            ui.selectables.add(new Ui.Sel(rr, "item", i));
            kx -= 62;
        }
    }

    private void mini(Renderer r, Layout.Rect rr, Color c, String label) {
        r.panel(rr.x(), rr.y(), rr.w(), rr.h(), c, Color.web("#0006"), 8, 2);
        r.textCenter(label, rr.centerX(), rr.centerY(), 10, INK);
    }

    private void drawDeck(Ui ui, double x, double y, double w, double h) {
        Renderer r = ui.r;
        double cardH = 140, cardY = y + h - cardH - 24;
        r.panel(x + 5, cardY, w - 10, cardH, Color.web("#d3c3a2"), Color.web("#7a5a3a"), 8, 3);
        r.textCenter(ui.s.deckRemaining() + " / " + ui.s.deckTotal(), x + w / 2, y + h - 12, 14, INK);
    }
}
