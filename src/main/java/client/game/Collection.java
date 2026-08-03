package client.game;

import client.engine.Idle;
import client.engine.Layout;
import javafx.scene.paint.Color;

import static client.game.Palette.*;

/**
 * The browsable joker collection: every joker, five-by-three across paged tiles, each carrying the same idle
 * sway/bob the in-game tiles do, with the shared hover tooltip. Drawn as a full-screen modal; it owns input,
 * clearing the frame's buttons so only its own page/Back controls are live (nothing clicks through behind it).
 *
 * <p>Shared by the main menu and the in-game Options overlay so both show the exact same grid — the one collection
 * system, with the caller only owning the "is it open" flag and supplying the frame clock and the Back action.
 */
final class Collection {

    private int page;

    /** Resets to the first page; call when opening. */
    void open() { page = 0; }

    /** Draws the modal. {@code time} drives the idle animation; {@code onBack} is the Back button's action. */
    void render(Ui ui, double time, Runnable onBack) {
        Renderer r = ui.r;
        ui.buttons.clear();   // the modal owns input — drop the widgets behind it
        ui.tips.clear();      // and their hover tips, so only the grid's own tooltips show
        r.gc().setFill(Color.web("#04060a", 0.74));
        r.gc().fillRect(0, 0, Ui.W, Ui.H);

        double pw = 1140, ph = 724, px = (Ui.W - pw) / 2, py = (Ui.H - ph) / 2;
        r.panel(px, py, pw, ph, Color.web("#141517"), EDGE, 16, 3);
        r.textCenterBold("COLLECTION — Jokers", Ui.W / 2.0, py + 40, 30, ORANGE);

        model.items.jokers.Jokers[] all = model.items.jokers.Jokers.values();
        int cols = 5, rows = 3, perPage = cols * rows;
        int pages = (all.length + perPage - 1) / perPage;
        page = Math.max(0, Math.min(page, pages - 1));
        int start = page * perPage;

        double cardW = 120, cardH = 162, gapX = 44, gapY = 22;
        double gridW = cols * cardW + (cols - 1) * gapX;
        double gx = px + (pw - gridW) / 2, gy = py + 78;

        for (int i = 0; i < perPage && start + i < all.length; i++) {
            model.items.jokers.Jokers jk = all[start + i];
            int seed = start + i;
            double cx = gx + (i % cols) * (cardW + gapX) + cardW / 2;
            double bob = Idle.bobPx(time, seed, 1.8);
            double sway = Idle.swayDeg(time, seed, 1.4);
            double baseTy = gy + (i / cols) * (cardH + gapY);   // the static tile, for a hover target that doesn't bob
            double tx = cx - cardW / 2, ty = baseTy + bob;
            r.rotated(cx, ty + cardH / 2, sway, () -> {
                javafx.scene.image.Image tex = r.jokerTexture(jk.spec().getName());
                if (tex != null) {
                    r.imageFit(tex, tx, ty, cardW, cardH);
                } else {   // no PNG yet: the same vector tile the HUD falls back to
                    r.panel(tx, ty, cardW, cardH, Color.web("#c0392b"), Color.web("#0006"), 8, 2);
                    r.textCenter(Fmt.shortName(jk.spec().getName()), cx, ty + cardH / 2, 11, INK);
                }
            });
            // The same hover system every card uses: name + effect, drawn by Overlays.tooltip.
            String desc = jk.spec().getDescription();
            ui.tip(new Layout.Rect(tx, baseTy, cardW, cardH),
                    desc == null || desc.isEmpty() ? jk.spec().getName() : jk.spec().getName() + "\n" + desc);
        }

        // Page nav and Back.
        double navY = py + ph - 108;
        int lastPage = pages - 1;
        ui.button(Ui.W / 2.0 - 170, navY, 44, 44, "◀", RED, INK, () -> page = Math.max(0, page - 1), true);
        r.panel(Ui.W / 2.0 - 120, navY, 240, 44, RED, RED.darker(), 8, 2);
        r.textCenterBold("Page " + (page + 1) + " / " + pages, Ui.W / 2.0, navY + 22, 18, INK);
        ui.button(Ui.W / 2.0 + 126, navY, 44, 44, "▶", RED, INK, () -> page = Math.min(lastPage, page + 1), true);
        ui.button(px + 40, py + ph - 52, pw - 80, 40, "Back", ORANGE, DARK, onBack, true);
    }
}
