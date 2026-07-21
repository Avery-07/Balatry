package client.game;

import client.MatchSnapshot;
import client.engine.Layout;
import client.engine.TileRow;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

import static client.game.Palette.*;

/**
 * The shop: Next Round / Reroll, a card shelf, and separate voucher and pack shelves. Items are click-to-select,
 * and every shelf is a retained {@link TileRow} — shop tiles have nowhere to be dragged <em>to</em>, but they
 * lift with the cursor and glide back like everything else, so the whole table obeys one grammar. Sold slots
 * stay as static "(sold)" holes; a fresh reroll's tiles spawn in place.
 */
final class ShopScreen implements Screen {

    private static final double TILE_W = 108, TILE_H = 130, STEP = 118;

    @Override
    public void render(Ui ui, double x, double y, double w, double h) {
        MatchSnapshot.ShopView shop = ui.s.shop();
        if (shop == null) return;
        Renderer r = ui.r;
        r.panel(x, y, w, h, javafx.scene.paint.Color.web("#141517"), RED, 12, 3);
        double ix = x + 16, iw = w - 32, cy = y + 16;

        ui.button(ix, cy, 120, 44, "Next Round", RED, INK, () -> ui.vm.readyForNext(), true);
        ui.button(ix + 130, cy, 120, 44, "Reroll $" + shop.rerollCost(), GREEN, INK, () -> ui.vm.rerollShop(), true);
        cy += 60;

        r.textLeftBold("Cards", ix, cy, 12, FAINT); cy += 18;
        shelf(ui, ui.shopSlotRow, shop.slots().size(), ix, cy,
                i -> shop.slots().get(i) == null ? null : new Tile(
                        shop.slots().get(i).id(), shop.slots().get(i).label(), shop.slots().get(i).price(),
                        javafx.scene.paint.Color.web("#c0392b"), true, "shopSlot",
                        shop.slots().get(i).badge(), shop.slots().get(i).tooltip(), i));
        cy += 150;

        r.textLeftBold("Voucher", ix, cy, 12, FAINT);
        r.textLeftBold("Booster Packs", ix + iw / 2, cy, 12, FAINT); cy += 18;
        shelf(ui, ui.shopVoucherRow, shop.vouchers().size(), ix, cy,
                i -> {
                    MatchSnapshot.VoucherItem v = shop.vouchers().get(i);
                    return v == null ? null : new Tile(v.id(), v.label(), v.price(), BLUE, v.redeemable(), "shopVoucher", "",
                            v.tooltip() + (v.redeemable() ? "" : "\n(one voucher per ante — already redeemed)"), i);
                });
        shelf(ui, ui.shopPackRow, shop.packs().size(), ix + iw / 2, cy,
                i -> {
                    MatchSnapshot.ShopItem p = shop.packs().get(i);
                    return p == null ? null : new Tile(p.id(), p.label(), p.price(), PURPLE, true, "shopPack", "", p.tooltip(), i);
                });
    }

    /** Everything one shelf tile needs to draw and register itself; {@code slotIndex} is the MODEL's slot. */
    private record Tile(int id, String label, int price, javafx.scene.paint.Color color,
                        boolean enabled, String kind, String badge, String tooltip, int slotIndex) { }

    /**
     * Lays one shelf out on its retained row and draws it: sold (null) positions render as static holes and are
     * left out of the row, live tiles animate — which is what makes a dragged one lift and glide home.
     */
    private void shelf(Ui ui, TileRow row, int slotCount, double x, double y, IntFunction<Tile> tileAt) {
        List<Integer> ids = new ArrayList<>();
        List<Tile> tiles = new ArrayList<>();
        List<Double> slotsX = new ArrayList<>();
        for (int i = 0; i < slotCount; i++) {
            Tile t = tileAt.apply(i);
            double cx = x + i * STEP + TILE_W / 2;
            if (t == null) {   // a spent slot: a hole in the shelf, not a tile in the row
                ui.r.panel(x + i * STEP, y + 16, TILE_W, TILE_H, javafx.scene.paint.Color.web("#1a1b1f"), EDGE, 8, 2);
                ui.r.textCenter("(sold)", cx, y + 80, 11, FAINT);
                continue;
            }
            ids.add(t.id()); tiles.add(t); slotsX.add(cx);
        }
        row.reconcile(ids);
        double[] xs = new double[slotsX.size()];
        for (int i = 0; i < xs.length; i++) xs[i] = slotsX.get(i);
        double cy = y + 16 + TILE_H / 2;
        row.layout(xs, cy);

        for (int i = 0; i < tiles.size(); i++) if (!row.isDragged(tiles.get(i).id())) draw(ui, row, tiles.get(i), i);
        for (int i = 0; i < tiles.size(); i++) if (row.isDragged(tiles.get(i).id()))  draw(ui, row, tiles.get(i), i);
    }

    private void draw(Ui ui, TileRow row, Tile t, int index) {
        Renderer r = ui.r;
        double tx = row.x(t.id()) - TILE_W / 2, ty = row.y(t.id()) - TILE_H / 2;
        Layout.Rect rr = new Layout.Rect(tx, ty, TILE_W, TILE_H);
        r.panel(rr.x(), rr.y(), rr.w(), rr.h(), t.color(), javafx.scene.paint.Color.web("#0006"), 8, 2);
        r.textCenter(t.label(), rr.centerX(), ty + 54, 11, INK);
        r.panel(tx + 30, ty - 14, 48, 22, PANEL, GOLD, 8, 2);
        r.textCenterBold("$" + t.price(), rr.centerX(), ty - 3, 12, ORANGE);
        if (!t.badge().isEmpty()) {   // the drawback strip: stickers/editions announce themselves pre-purchase
            r.panel(tx, ty + TILE_H - 18, TILE_W, 18, javafx.scene.paint.Color.web("#000a"), null, 6, 0);
            r.textCenter(t.badge(), rr.centerX(), ty + TILE_H - 9, 9, GOLD);
        }
        if (t.enabled()) ui.selectables.add(new Ui.Sel(rr, t.kind(), t.slotIndex()));
        ui.tip(rr, t.tooltip());
    }
}
