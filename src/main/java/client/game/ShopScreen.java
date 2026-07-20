package client.game;

import client.MatchSnapshot;
import client.engine.Layout;

import static client.game.Palette.*;

/** The shop: Next Round / Reroll, a card shelf, and separate voucher and pack shelves. Items are click-to-select. */
final class ShopScreen implements Screen {

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
        double px = ix;
        for (int i = 0; i < shop.slots().size(); i++) {
            MatchSnapshot.ShopItem it = shop.slots().get(i);
            tile(ui, px, cy, it == null ? null : it.label(), it == null ? 0 : it.price(), javafx.scene.paint.Color.web("#c0392b"), it != null, "shopSlot", i);
            // The drawback strip: a stickered/editioned card announces it on the tile, before the purchase.
            if (it != null && !it.badge().isEmpty()) {
                r.panel(px, cy + 16 + 130 - 18, 108, 18, javafx.scene.paint.Color.web("#000a"), null, 6, 0);
                r.textCenter(it.badge(), px + 54, cy + 16 + 130 - 9, 9, GOLD);
            }
            px += 118;
        }
        cy += 150;

        r.textLeftBold("Voucher", ix, cy, 12, FAINT);
        r.textLeftBold("Booster Packs", ix + iw / 2, cy, 12, FAINT); cy += 18;
        for (int i = 0; i < shop.vouchers().size(); i++) {
            MatchSnapshot.VoucherItem v = shop.vouchers().get(i);
            tile(ui, ix + i * 118, cy, v == null ? null : v.label(), v == null ? 0 : v.price(), BLUE, v != null && v.redeemable(), "shopVoucher", i);
        }
        double ppx = ix + iw / 2;
        for (int i = 0; i < shop.packs().size(); i++) {
            MatchSnapshot.ShopItem p = shop.packs().get(i);
            tile(ui, ppx, cy, p == null ? null : p.label(), p == null ? 0 : p.price(), PURPLE, p != null, "shopPack", i);
            ppx += 118;
        }
    }

    private void tile(Ui ui, double x, double y, String label, int price, javafx.scene.paint.Color c, boolean enabled, String kind, int index) {
        Renderer r = ui.r;
        Layout.Rect rr = new Layout.Rect(x, y + 16, 108, 130);
        r.panel(rr.x(), rr.y(), rr.w(), rr.h(), c, javafx.scene.paint.Color.web("#0006"), 8, 2);
        if (label != null) {
            r.textCenter(label, x + 54, y + 70, 11, INK);
            r.panel(x + 30, y + 2, 48, 22, PANEL, GOLD, 8, 2);
            r.textCenterBold("$" + price, x + 54, y + 13, 12, ORANGE);
            if (enabled) ui.selectables.add(new Ui.Sel(rr, kind, index));
        } else {
            r.textCenter("(sold)", x + 54, y + 80, 11, FAINT);
        }
    }
}
