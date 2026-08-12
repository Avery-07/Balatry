package client.game;

import client.engine.Idle;
import client.engine.Layout;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;

import static client.game.Palette.*;

/**
 * The browsable collection: every joker, tarot, planet, spectral and relic, five-by-three across paged tiles, each
 * carrying the same idle sway/bob the in-game tiles do, with the shared hover tooltip. A tab bar switches category;
 * each category paginates on its own. Drawn as a full-screen modal; it owns input, clearing the frame's buttons so
 * only its own tab/page/Back controls are live (nothing clicks through behind it).
 *
 * <p>Shared by the main menu and the in-game Options overlay so both show the exact same grid — the one collection
 * system, with the caller only owning the "is it open" flag and supplying the frame clock and the Back action.
 */
final class Collection {

    private static final String[] TABS = { "Jokers", "Tarots", "Planets", "Spectrals", "Relics" };
    // The vector-tile colour per tab, used only when a texture is missing (mirrors the HUD's fallback tiles).
    private static final Color[] FALLBACK = {
            Color.web("#c0392b"), Color.web("#6a3d9a"), Color.web("#2f6fb0"), Color.web("#2f9b8f"), Color.web("#7a5a2a")
    };

    private int tab, page;

    /** How to draw one entry's face into a tile; returns false when no texture exists (caller draws the vector tile). */
    private interface Face { boolean draw(Renderer r, double x, double y, double w, double h); }

    private record Entry(String name, String desc, Face face) { }

    /** Resets to the first tab and page; call when opening. */
    void open() { tab = 0; page = 0; }

    /** Draws the modal. {@code time} drives the idle animation; {@code onBack} is the Back button's action. */
    void render(Ui ui, double time, Runnable onBack) {
        Renderer r = ui.r;
        ui.buttons.clear();   // the modal owns input — drop the widgets behind it
        ui.tips.clear();      // and their hover tips, so only the grid's own tooltips show
        r.gc().setFill(Color.web("#04060a", 0.74));
        r.gc().fillRect(0, 0, Ui.W, Ui.H);

        double pw = 1140, ph = 724, px = (Ui.W - pw) / 2, py = (Ui.H - ph) / 2;
        r.panel(px, py, pw, ph, Color.web("#141517"), EDGE, 16, 3);
        r.textCenterBold("COLLECTION — " + TABS[tab], Ui.W / 2.0, py + 34, 28, ORANGE);

        // Tab bar: one button per category, the active one lit. Selecting a tab resets to its first page.
        double tabW = 148, tabGap = 10, tabsW = TABS.length * tabW + (TABS.length - 1) * tabGap;
        double tbx = Ui.W / 2.0 - tabsW / 2, tby = py + 58, tbh = 32;
        for (int i = 0; i < TABS.length; i++) {
            double bx = tbx + i * (tabW + tabGap);
            boolean active = i == tab;
            r.panel(bx, tby, tabW, tbh, active ? ORANGE : Color.web("#2b2c30"), active ? ORANGE.darker() : EDGE, 8, 2);
            r.textCenterBold(TABS[i], bx + tabW / 2, tby + tbh / 2, 15, active ? DARK : INK);
            int t = i;
            ui.buttons.add(new Ui.Btn(new Layout.Rect(bx, tby, tabW, tbh), () -> { tab = t; page = 0; }));
        }

        List<Entry> all = entries(tab);
        Color fallback = FALLBACK[tab];
        int cols = 5, rows = 3, perPage = cols * rows;
        int pages = Math.max(1, (all.size() + perPage - 1) / perPage);
        page = Math.max(0, Math.min(page, pages - 1));
        int start = page * perPage;

        double cardW = 120, cardH = 150, gapX = 44, gapY = 16;
        double gridW = cols * cardW + (cols - 1) * gapX;
        double gx = px + (pw - gridW) / 2, gy = py + 100;

        for (int i = 0; i < perPage && start + i < all.size(); i++) {
            Entry e = all.get(start + i);
            int seed = start + i;
            double cx = gx + (i % cols) * (cardW + gapX) + cardW / 2;
            double bob = Idle.bobPx(time, seed, 1.8);
            double sway = Idle.swayDeg(time, seed, 1.4);
            double baseTy = gy + (i / cols) * (cardH + gapY);   // the static tile, for a hover target that doesn't bob
            double tx = cx - cardW / 2, ty = baseTy + bob;
            r.rotated(cx, ty + cardH / 2, sway, () -> {
                if (!e.face().draw(r, tx, ty, cardW, cardH)) {   // no texture yet: the same vector tile the HUD falls back to
                    r.panel(tx, ty, cardW, cardH, fallback, Color.web("#0006"), 8, 2);
                    r.textCenter(Fmt.shortName(e.name()), cx, ty + cardH / 2, 11, INK);
                }
            });
            // The same hover system every card uses: name + effect, drawn by Overlays.tooltip.
            ui.tip(new Layout.Rect(tx, baseTy, cardW, cardH),
                    e.desc() == null || e.desc().isEmpty() ? e.name() : e.name() + "\n" + e.desc());
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

    /** The entries for a tab, each knowing its own draw path (joker PNG, consumable atlas, or relic atlas). */
    private static List<Entry> entries(int tab) {
        List<Entry> out = new ArrayList<>();
        switch (tab) {
            case 0 -> {
                for (model.items.jokers.Jokers j : model.items.jokers.Jokers.values()) {
                    String name = j.spec().getName();
                    out.add(new Entry(name, j.spec().getDescription(), (r, x, y, w, h) -> r.jokerFace(name, x, y, w, h)));
                }
            }
            case 1 -> { for (var c : model.items.consumables.Tarots.values()) out.add(consumable(c.spec())); }
            case 2 -> { for (var c : model.items.consumables.Planets.values()) out.add(consumable(c.spec())); }
            case 3 -> { for (var c : model.items.consumables.Spectrals.values()) out.add(consumable(c.spec())); }
            case 4 -> {
                for (model.items.relics.Relics rel : model.items.relics.Relics.values()) {
                    String name = rel.spec().getName();
                    out.add(new Entry(name, rel.spec().getDescription(), (r, x, y, w, h) -> r.relicFace(name, x, y, w, h)));
                }
            }
            default -> { }
        }
        return out;
    }

    private static Entry consumable(model.items.consumables.ConsumableSpec s) {
        String name = s.getName();
        return new Entry(name, s.getDescription(), (r, x, y, w, h) -> r.consumableFace(name, x, y, w, h));
    }
}
