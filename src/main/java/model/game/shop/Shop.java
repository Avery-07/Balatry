package model.game.shop;
import model.game.player.Run;

import model.cards.Card;
import model.cards.packs.BoosterPack;
import model.cards.vouchers.Voucher;
import model.game.rng.Rng;
import model.game.rng.RngSource;
import model.game.scoring.Trigger;

import java.util.ArrayList;
import java.util.List;

/**
 * A per-run, seed-mirrored shop for one SHOP phase. Three rows: a rerollable card row, plus fixed booster-pack
 * and voucher rows (only the card row rerolls, as in the base game). Shop prices reflect the run's discount.
 */
public final class Shop {

    private final Run run;
    private final int shopIndex;            // structural coordinate: the Nth shop opened on this run
    private final ShopPool pool;
    private final List<Card> slots;         // card row; null entries are sold/empty
    private final List<BoosterPack> packs;  // fixed for the visit; null entries are bought
    private final List<Voucher> vouchers;   // fixed for the visit; null entries are redeemed
    private final PackPool packPool;
    private final VoucherPool voucherPool;
    private int rerolls;

    /** Card-only shop (used directly by tests and simple callers). */
    public Shop(Run run, int shopIndex, int slotCount, ShopPool pool) {
        this(run, shopIndex, slotCount, pool, 0, null, 0, null);
    }

    /** Full three-row shop. */
    public Shop(Run run, int shopIndex, int slotCount, ShopPool pool,
         int packCount, PackPool packPool, int voucherCount, VoucherPool voucherPool) {
        this.run = run;
        this.shopIndex = shopIndex;
        this.pool = pool;
        this.packPool = packPool;
        this.voucherPool = voucherPool;
        this.slots = nullList(slotCount);
        this.packs = nullList(packCount);
        this.vouchers = nullList(voucherCount);
        fill();
        fillPacks();
        fillVouchers();
    }

    // --- card row ---

    /** Items on offer in the card row; null entries are sold or empty. */
    public List<Card> getSlots() { return new ArrayList<>(slots); }
    public int getSlotCount()  { return slots.size(); }
    public Card getSlot(int i) { return slots.get(i); }
    public int getRerolls()    { return rerolls; }

    /** Cost of the next reroll: the run's base reroll cost plus $1 per reroll already done this shop. */
    public int rerollCost() { return run.getBaseRerollCost() + rerolls; }

    /** Buys the card in {@code slotIndex}, charging the run and routing it into inventory. */
    public Card buy(int slotIndex) {
        Card item = require(slots, slotIndex);
        if (!run.canAcquire(item)) throw new IllegalStateException("no inventory slot for " + item);
        charge(priced(item.getShopValue()));
        run.acquire(item);
        run.getStats().recordPurchase();
        slots.set(slotIndex, null);
        return item;
    }

    /** Rerolls the card row for a fresh, still-seeded set; packs and vouchers are unaffected. */
    public void reroll() {
        int cost = rerollCost();
        if (run.getMoney() - cost < run.minBalance()) throw new IllegalStateException("cannot afford reroll " + cost);
        run.spend(cost);
        rerolls++;
        run.getStats().recordReroll();
        fill();
        run.fire(Trigger.ON_SHOP_REROLL);
    }

    // --- pack row ---

    public List<BoosterPack> getPacks() { return new ArrayList<>(packs); }
    public int getPackCount()           { return packs.size(); }
    public BoosterPack getPack(int i)   { return packs.get(i); }

    /** Buys the pack in {@code packIndex}, charging the run; the caller opens it via {@link BoosterPack#open}. */
    public BoosterPack buyPack(int packIndex) {
        BoosterPack pack = require(packs, packIndex);
        charge(priced(pack.getShopValue()));
        run.getStats().recordPurchase();
        packs.set(packIndex, null);
        return pack;
    }

    // --- voucher row ---

    public List<Voucher> getVouchers() { return new ArrayList<>(vouchers); }
    public int getVoucherCount()       { return vouchers.size(); }
    public Voucher getVoucher(int i)   { return vouchers.get(i); }

    /** Redeems the voucher in {@code voucherIndex}: enforces eligibility, charges the run, applies the effect. */
    public void redeemVoucher(int voucherIndex) {
        Voucher voucher = require(vouchers, voucherIndex);
        if (!run.canRedeem(voucher)) throw new IllegalStateException("voucher not redeemable: " + voucher.getSpec().getName());
        int price = priced(voucher.getShopValue());
        if (run.getMoney() - price < run.minBalance()) throw new IllegalStateException("cannot afford voucher " + price);
        run.spend(price);
        run.redeemVoucher(voucher);
        vouchers.set(voucherIndex, null);
    }

    // --- internals ---

    /** Charges {@code price}, honoring a free-purchase grant and firing ON_BOUGHT, with an affordability gate. */
    private void charge(int price) {
        run.beginPurchase();
        run.fire(Trigger.ON_BOUGHT);
        int effective = run.isPurchaseFree() ? 0 : price;
        if (run.getMoney() - effective < run.minBalance())
            throw new IllegalStateException("cannot afford " + effective + " (have " + run.getMoney() + ", floor " + run.minBalance() + ")");
        run.spend(effective);
    }

    /** Applies the run's shop discount to a price. */
    private int priced(int shopValue) {
        return shopValue - shopValue * run.getShopDiscount() / 100;
    }

    private void fill() {
        for (int i = 0; i < slots.size(); i++) {
            long salt = Rng.combine(shopIndex, rerolls, i);
            Card item = pool.roll(run.getRng().streamFor(RngSource.SHOP_CONTENTS, salt));
            if (i == 0 && item != null && run.isFirstShopSlotDebuffed())
                item.apply(model.modifiers.Sticker.DEBUFFED);   // Limos: this seat's first slot is debuffed this visit
            slots.set(i, item);
        }
    }

    private void fillPacks() {
        if (packPool == null) return;
        for (int i = 0; i < packs.size(); i++) {
            long salt = Rng.combine(shopIndex, i);
            packs.set(i, packPool.roll(run.getRng().streamFor(RngSource.SHOP_PACKS, salt)));
        }
    }

    private void fillVouchers() {
        if (voucherPool == null) return;
        for (int i = 0; i < vouchers.size(); i++) {
            long salt = Rng.combine(shopIndex, i);
            vouchers.set(i, voucherPool.roll(run, run.getRng().streamFor(RngSource.SHOP_VOUCHERS, salt)));
        }
    }

    private static <T> List<T> nullList(int n) {
        List<T> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) list.add(null);
        return list;
    }

    private static <T> T require(List<T> row, int index) {
        T item = row.get(index);
        if (item == null) throw new IllegalStateException("slot " + index + " is empty");
        return item;
    }
}
