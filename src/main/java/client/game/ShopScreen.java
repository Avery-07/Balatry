package client.game;

import client.MatchSnapshot;
import client.engine.Layout;
import client.engine.TileRow;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

import static client.game.Palette.*;

/**
 * The shop, laid out like Balatro's: a header (Next Round / Reroll) over two framed shelf rows that fill the whole
 * panel — the card shelf on top, then Voucher and Booster Packs side by side. Each shelf is a retained
 * {@link TileRow}: shop tiles have nowhere to be dragged <em>to</em>, but they lift with the cursor and glide back
 * like everything else, so the whole table obeys one grammar. Sold slots stay as static "(sold)" holes.
 */
final class ShopScreen implements Screen {

    private static final double TILE_W = 116, TILE_H = 156, GAP = 12;
    private static final Color SHELF_BG = Color.web("#101113"), SOLD_BG = Color.web("#1a1b1f");

    @Override
    public void render(Ui ui, double x, double y, double w, double h) {
        MatchSnapshot.ShopView shop = ui.s.shop();
        if (shop == null) return;
        Renderer r = ui.r;
        r.panel(x, y, w, h, Color.web("#141517"), RED, 12, 3);
        double ix = x + 18, iw = w - 36, cy = y + 18;

        // Header: the leave-shop barrier and the reroll, sized to sit above the shelves.
        Waiting.button(ui, ix, cy, 176, 50, "Next Round");
        ui.button(ix + 190, cy, 150, 50, "Reroll $" + shop.rerollCost(), GREEN, INK, () -> ui.vm.rerollShop(), true);
        MatchSnapshot.AuctionView auction = ui.s.sin().auction();   // Pride's blind legendary auction fills the header's right
        if (auction != null) drawAuction(ui, ix + 350, cy, iw - 350, 50, auction);
        long copyable = ui.s.sin().envyLog().stream().filter(e -> !e.mine()).count();   // Envy: rivals' buys, copyable
        if (copyable > 0)
            ui.button(ix + 350, cy, 240, 50, "Rivals' buys (" + copyable + ")", PURPLE, INK, () -> ui.showEnvyLog = true, true);
        cy += 66;

        // The two shelf rows split everything below the header evenly, so the panel fills top to bottom — no
        // empty tail. Each row is a small section label over a framed inset that holds the tiles.
        double bottom = y + h - 18, rowGap = 16, labelH = 20;
        double rowH = (bottom - cy - rowGap) / 2;

        // Row 1 — the card shelf (the jokers and consumables the model rolled), one wide inset.
        r.textLeftBold("Cards", ix, cy, 13, FAINT);
        shelf(ui, ui.shopSlotRow, shop.slots().size(), ix, cy + labelH, iw, rowH - labelH,
                i -> {
                    MatchSnapshot.ShopItem it = shop.slots().get(i);
                    return it == null ? null : new Tile(it.id(), it.label(), it.price(),
                            Color.web("#c0392b"), true, "shopSlot", it.badge(), it.tooltip(), i, it.card(), it.edition());
                });
        cy += rowH + rowGap;

        // Row 2 — Voucher (left) and Booster Packs (right), each its own framed inset.
        double half = (iw - 20) / 2;
        r.textLeftBold("Voucher", ix, cy, 13, FAINT);
        r.textLeftBold("Booster Packs", ix + half + 20, cy, 13, FAINT);
        shelf(ui, ui.shopVoucherRow, shop.vouchers().size(), ix, cy + labelH, half, rowH - labelH,
                i -> {
                    MatchSnapshot.VoucherItem v = shop.vouchers().get(i);
                    return v == null ? null : new Tile(v.id(), v.label(), v.price(), BLUE, v.redeemable(), "shopVoucher", "",
                            v.tooltip() + (v.redeemable() ? "" : "\n(one voucher per ante — already redeemed)"), i, null, -1);
                });
        shelf(ui, ui.shopPackRow, shop.packs().size(), ix + half + 20, cy + labelH, half, rowH - labelH,
                i -> {
                    MatchSnapshot.ShopItem p = shop.packs().get(i);
                    return p == null ? null : new Tile(p.id(), p.label(), p.price(), PURPLE, true, "shopPack", "", p.tooltip(), i, p.card(), p.edition());
                });
    }

    /**
     * Pride's blind, all-pay legendary auction, tucked into the header beside Reroll. Shows the joker on the block
     * and only this seat's own running total (the auction is blind — rivals' bids never appear); the button pays one
     * {@code step} increment, spent immediately. A tie or a full board at close means the joker is simply lost.
     */
    private void drawAuction(Ui ui, double px, double py, double pw, double ph, MatchSnapshot.AuctionView a) {
        Renderer r = ui.r;
        r.panel(px, py, pw, ph, Color.web("#2a1c38"), PURPLE, 8, 2);
        double bw = 96, bh = 34, bx = px + pw - bw - 8, by = py + (ph - bh) / 2;
        r.textLeftBold("Pride auction — " + a.jokerName(), px + 12, py + 9, 12, GOLD);
        r.textLeft("your bid $" + a.myBid() + "  (blind)", px + 12, py + 28, 11, INK);
        ui.button(bx, by, bw, bh, "Bid +$" + a.step(), ORANGE, DARK, () -> ui.vm.prideBid(a.step()), true);
        ui.tip(new Layout.Rect(px, py, pw - bw - 12, ph),
                a.jokerName() + "\nBlind all-pay auction: each $" + a.step()
                        + " is spent at once. Highest total wins; a tie wins nobody.");
    }

    /** Everything one shelf tile needs to draw and register itself; {@code slotIndex} is the MODEL's slot. */
    private record Tile(int id, String label, int price, Color color,
                        boolean enabled, String kind, String badge, String tooltip, int slotIndex, MatchSnapshot.CardFace card, int edition) { }

    /**
     * Draws one framed shelf inset spanning (px,py,pw,ph) and lays its tiles out centered inside it — vertically on
     * the panel's mid-line, horizontally as one count-scaled line ({@link Layout#slots}). A sold (null) slot keeps
     * its computed position as a static hole; live tiles ride the retained row so a held one lifts and glides home.
     */
    private void shelf(Ui ui, TileRow row, int slotCount, double px, double py, double pw, double ph, IntFunction<Tile> tileAt) {
        ui.r.panel(px, py, pw, ph, SHELF_BG, EDGE, 10, 2);
        double centerY = py + ph / 2;
        double[] all = Layout.slots(slotCount, px + pw / 2, TILE_W, GAP, pw - 24);
        List<Integer> ids = new ArrayList<>();
        List<Tile> tiles = new ArrayList<>();
        List<Double> slotsX = new ArrayList<>();
        for (int i = 0; i < slotCount; i++) {
            Tile t = tileAt.apply(i);
            double sx = all[i];
            if (t == null) {   // a spent slot: a hole in the shelf, not a tile in the row
                ui.r.panel(sx - TILE_W / 2, centerY - TILE_H / 2, TILE_W, TILE_H, SOLD_BG, EDGE, 8, 2);
                ui.r.textCenter("(sold)", sx, centerY, 11, FAINT);
                continue;
            }
            ids.add(t.id()); tiles.add(t); slotsX.add(sx);
        }
        row.reconcile(ids);
        double[] xs = new double[slotsX.size()];
        for (int i = 0; i < xs.length; i++) xs[i] = slotsX.get(i);
        row.layout(xs, centerY);

        // Pass 0 draws the settled tiles, pass 1 the held one on top.
        for (int pass = 0; pass < 2; pass++)
            for (Tile t : tiles)
                if (row.isDragged(t.id()) == (pass == 1)) draw(ui, row, t);
    }

    private void draw(Ui ui, TileRow row, Tile t) {
        Renderer r = ui.r;
        // The same idle sway/bob a card carries; the held tile follows the cursor, so it neither sways nor bobs.
        boolean held = row.isDragged(t.id());
        double bob = held ? 0 : client.engine.Idle.bobPx(ui.now, t.id(), 1.6);
        double sway = held ? 0 : client.engine.Idle.swayDeg(ui.now, t.id(), 1.4);
        double sw = TILE_W * row.stretchX(t.id()), sh = TILE_H * row.stretchY(t.id());   // squash & stretch as it glides home
        double tx = row.x(t.id()) - sw / 2, ty = row.y(t.id()) - sh / 2 + bob;
        Layout.Rect rr = new Layout.Rect(tx, ty, sw, sh);
        // A shop tile shows its face when one exists — a playing card (Magic Trick), a joker PNG or a
        // planet/tarot/spectral cell (shopSlot), or a pack's art (shopPack); all fit, never stretched. Otherwise a tile.
        MatchSnapshot.CardFace cf = t.card();
        javafx.scene.image.Image jtex = (cf == null && "shopSlot".equals(t.kind())) ? r.jokerTexture(t.label()) : null;
        r.rotated(rr.centerX(), rr.centerY(), sway, () -> {
            boolean textured = false;
            if (cf != null) {
                double ch = Math.min(rr.h(), rr.w() * 95.0 / 71.0), cw = ch * 71.0 / 95.0;
                r.card(cf.rank(), cf.suit(), cf.enhancement(), cf.seal(), cf.edition(), rr.centerX(), rr.centerY(), cw, ch, 0, false);
                textured = true;
            }
            else if (jtex != null) { r.imageFit(jtex, rr.x(), rr.y(), rr.w(), rr.h()); textured = true; }
            else if ("shopSlot".equals(t.kind())) textured = r.consumableFace(t.label(), rr.x(), rr.y(), rr.w(), rr.h())
                    || r.relicFace(t.label(), rr.x(), rr.y(), rr.w(), rr.h());
            else if ("shopPack".equals(t.kind())) textured = r.packFace(t.label(), rr.x(), rr.y(), rr.w(), rr.h());
            else if ("shopVoucher".equals(t.kind())) textured = r.voucherFace(t.label(), rr.x(), rr.y(), rr.w(), rr.h());
            if (textured && cf == null) r.editionEffect(t.edition(), rr.x(), rr.y(), rr.w(), rr.h(), 8);   // shimmer over a joker/consumable/pack face
            if (!textured) {
                r.panel(rr.x(), rr.y(), rr.w(), rr.h(), t.color(), Color.web("#0006"), 8, 2);
                r.textCenter(t.label(), rr.centerX(), rr.centerY(), 12, INK);
            }
            // The price tag, centered above the card like Balatro's.
            r.panel(rr.centerX() - 28, ty - 16, 56, 24, PANEL, GOLD, 8, 2);
            r.textCenterBold("$" + t.price(), rr.centerX(), ty - 4, 13, ORANGE);
            if (!t.badge().isEmpty()) {   // the drawback strip: stickers/editions announce themselves pre-purchase
                r.panel(tx, ty + rr.h() - 20, rr.w(), 20, Color.web("#000a"), null, 6, 0);
                r.textCenter(t.badge(), rr.centerX(), ty + rr.h() - 10, 9, GOLD);
            }
        });
        if (t.enabled()) ui.selectables.add(new Ui.Sel(rr, t.kind(), t.slotIndex()));
        ui.tip(rr, t.tooltip());
    }
}
