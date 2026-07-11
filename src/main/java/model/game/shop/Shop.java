package model.game.shop;
import model.game.player.Run;

import model.cards.Card;
import model.cards.jokers.JokerCard;
import model.cards.packs.BoosterPack;
import model.cards.vouchers.Voucher;
import model.game.rng.Rng;
import model.game.rng.RngSource;
import model.game.scoring.Trigger;
import model.modifiers.Edition;

import java.util.ArrayList;
import java.util.List;

/**
 * A per-run, seed-mirrored shop for one SHOP phase. Three rows: a rerollable card row, plus fixed booster-pack
 * and voucher rows (only the card row rerolls, as in the base game). Shop prices reflect the run's discount.
 *
 * <p>All customisation flows through the {@link ShopSetup} captured at construction: row sizes and pools, the
 * Coupon/D6 pricing rules, injected free cards (extra leading slots on the initial fill only), pending Negative
 * transforms (the next base-edition jokers rolled become free Negatives, across rerolls, until exhausted), and
 * the per-reroll purchase cap. Rolled slots are salted by their <em>rolled</em> position, independent of any
 * injected slots in front of them, so seats with different tag boosts still mirror the seeded offering.
 *
 * <p>Per-slot price overrides sit beside the rows: {@code null} means "derive from the card's shop value and
 * the run's discount"; a number is the final price (injected and Negative-transformed items are $0, Coupon
 * zeroes the initial card and pack rows).
 */
public final class Shop {

    private final Run run;
    private final int shopIndex;            // structural coordinate: the Nth shop opened on this run
    private final ShopSetup setup;
    private final List<Card> slots;         // card row; null entries are sold/empty
    private final List<Integer> slotPrices;         // parallel to slots; null = derive from card value
    private final List<BoosterPack> packs;  // fixed for the visit; null entries are bought
    private final List<Integer> packPrices;         // parallel to packs; null = derive from pack value
    private final List<Voucher> vouchers;   // fixed for the visit; null entries are redeemed
    private int rerolls;
    private int negativeGrantsLeft;         // Negative Tag transforms not yet consumed
    private int purchasesThisRoll;          // buys since the last reroll (any row); capped by the setup

    /** Card-only shop (used directly by tests and simple callers). */
    public Shop(Run run, int shopIndex, int slotCount, ShopPool pool) {
        this(run, shopIndex, new ShopSetup(slotCount, pool, 0, null, 0, null));
    }

    /** Full three-row shop with default rules. */
    public Shop(Run run, int shopIndex, int slotCount, ShopPool pool,
         int packCount, PackPool packPool, int voucherCount, VoucherPool voucherPool) {
        this(run, shopIndex, new ShopSetup(slotCount, pool, packCount, packPool, voucherCount, voucherPool));
    }

    /** Builds a shop from a fully assembled {@link ShopSetup} (the modifier-pass path). */
    public Shop(Run run, int shopIndex, ShopSetup setup) {
        this.run = run;
        this.shopIndex = shopIndex;
        this.setup = setup;
        this.negativeGrantsLeft = setup.getNegativeJokerGrants();
        List<Card> injected = setup.getInjectedFreeCards();
        this.slots = nullList(injected.size() + setup.getSlotCount());
        this.slotPrices = nullList(slots.size());
        this.packs = nullList(setup.getPackCount());
        this.packPrices = nullList(packs.size());
        this.vouchers = nullList(setup.getVoucherCount());
        initialFill(injected);
        fillPacks();
        fillVouchers();
    }

    // --- card row ---

    /** Items on offer in the card row; null entries are sold or empty. */
    public List<Card> getSlots() { return new ArrayList<>(slots); }
    public int getSlotCount()  { return slots.size(); }
    public Card getSlot(int i) { return slots.get(i); }
    public int getRerolls()    { return rerolls; }

    /** The final price of the card in {@code slotIndex} (override if set, else value less the run's discount). */
    public int slotPrice(int slotIndex) {
        Card item = require(slots, slotIndex);
        Integer override = slotPrices.get(slotIndex);
        return override != null ? override : priced(item.getShopValue());
    }

    /** How many more purchases this roll state allows (Lust caps at 1 per roll; unlimited otherwise). */
    public int purchasesRemaining() {
        int cap = setup.getMaxPurchasesPerReroll();
        return cap == Integer.MAX_VALUE ? Integer.MAX_VALUE : Math.max(0, cap - purchasesThisRoll);
    }

    /** Cost of the next reroll: the base (the run's, or $0 under a D6 Tag) plus $1 per reroll already done. */
    public int rerollCost() {
        int base = setup.isRerollsFromZero() ? 0 : run.getBaseRerollCost();
        return base + rerolls;
    }

    /** Buys the card in {@code slotIndex}, charging the run and routing it into inventory. */
    public Card buy(int slotIndex) {
        Card item = require(slots, slotIndex);
        if (!run.canAcquire(item)) throw new IllegalStateException("no inventory slot for " + item);
        charge(slotPrice(slotIndex), item);
        run.acquire(item);
        run.getStats().recordPurchase();
        slots.set(slotIndex, null);
        slotPrices.set(slotIndex, null);
        notifyPurchase(item);
        return item;
    }

    /** Rerolls the card row for a fresh, still-seeded set; packs and vouchers are unaffected. */
    public void reroll() {
        int cost = rerollCost();
        if (run.getMoney() - cost < run.minBalance()) throw new IllegalStateException("cannot afford reroll " + cost);
        run.spend(cost);
        rerolls++;
        purchasesThisRoll = 0;   // a reroll grants a fresh purchase allowance (Lust's cap is per roll state)
        run.getStats().recordReroll();
        rerollFill();
        run.fire(Trigger.ON_SHOP_REROLL);
        if (run.getMatch() != null)
            run.getMatch().getSinModifier().onShopRerolled(run, this);   // Greed: re-debuff claimed reappearances
    }

    // --- pack row ---

    public List<BoosterPack> getPacks() { return new ArrayList<>(packs); }
    public int getPackCount()           { return packs.size(); }
    public BoosterPack getPack(int i)   { return packs.get(i); }

    /** The final price of the pack in {@code packIndex} (override if set, else value less the run's discount). */
    public int packPrice(int packIndex) {
        BoosterPack pack = require(packs, packIndex);
        Integer override = packPrices.get(packIndex);
        return override != null ? override : priced(pack.getShopValue());
    }

    /** Buys the pack in {@code packIndex}, charging the run; the caller opens it via {@link BoosterPack#open}. */
    public BoosterPack buyPack(int packIndex) {
        BoosterPack pack = require(packs, packIndex);
        // A debuffed pack (Greed's claim) could never be opened, so the purchase itself is refused: unlike a
        // debuffed joker (sellable, Exorcism-curable), a dead pack purchase would be pure loss with no decision.
        if (pack.isDebuffed())
            throw new IllegalStateException("a debuffed pack cannot be purchased: " + pack);
        charge(packPrice(packIndex), pack);
        run.getStats().recordPurchase();
        packs.set(packIndex, null);
        packPrices.set(packIndex, null);
        notifyPurchase(pack);
        return pack;
    }

    // --- voucher row ---

    public List<Voucher> getVouchers() { return new ArrayList<>(vouchers); }
    public int getVoucherCount()       { return vouchers.size(); }
    public Voucher getVoucher(int i)   { return vouchers.get(i); }

    /** Redeems the voucher in {@code voucherIndex}: enforces eligibility, charges the run, applies the effect. */
    public void redeemVoucher(int voucherIndex) {
        Voucher voucher = require(vouchers, voucherIndex);
        if (voucher.isDebuffed())   // Greed's claim: a debuffed voucher would apply no effect, so refuse before charging
            throw new IllegalStateException("a debuffed voucher cannot be redeemed: " + voucher.getSpec().getName());
        if (!run.canRedeem(voucher)) throw new IllegalStateException("voucher not redeemable: " + voucher.getSpec().getName());
        requirePurchaseAllowance();
        int price = priced(voucher.getShopValue());
        if (run.getMoney() - price < run.minBalance()) throw new IllegalStateException("cannot afford voucher " + price);
        run.spend(price);
        purchasesThisRoll++;
        run.redeemVoucher(voucher);
        vouchers.set(voucherIndex, null);
        notifyPurchase(voucher);
    }

    // --- internals ---

    /**
     * Charges {@code price} in three steps: price (ON_PURCHASE_PRICING may grant a free purchase), validate
     * affordability, then pay and fire ON_BOUGHT. The ordering guarantees a failed purchase has no side effects:
     * pricing effects must be pure grants, and counting/reacting effects (ON_BOUGHT) only ever see completed buys.
     */
    private void charge(int price, Card item) {
        requirePurchaseAllowance();
        run.beginPurchase();
        run.fire(Trigger.ON_PURCHASE_PRICING);
        // Wrath: a banked destroy-grant makes the next JOKER purchase free — peeked here, consumed only on
        // completion (the Loyalty-Card lesson: a failed buy must never burn a grant). Joker effects that already
        // waived the cost (Loyalty's free 4th) take precedence, so the grant is saved for a paid purchase.
        boolean wrathGrant = !run.isPurchaseFree() && item instanceof JokerCard
                && run.getSinState().getWrathFreeJokers() > 0;
        if (wrathGrant) run.makePurchaseFree();
        int effective = run.isPurchaseFree() ? 0 : price;
        if (run.getMoney() - effective < run.minBalance())
            throw new IllegalStateException("cannot afford " + effective + " (have " + run.getMoney() + ", floor " + run.minBalance() + ")");
        run.spend(effective);
        if (wrathGrant) run.getSinState().consumeWrathFreeJoker();
        purchasesThisRoll++;
        run.fire(Trigger.ON_BOUGHT);
    }

    /** Notifies the active sin of a completed purchase (Greed's claim propagation); never fires for a failed buy. */
    private void notifyPurchase(Card item) {
        if (run.getMatch() != null) run.getMatch().getSinModifier().onPurchase(run, item);
    }

    /** Enforces the setup's per-reroll purchase cap before any charge or spend happens. */
    private void requirePurchaseAllowance() {
        if (purchasesThisRoll >= setup.getMaxPurchasesPerReroll())
            throw new IllegalStateException("purchase limit reached for this shop roll ("
                    + setup.getMaxPurchasesPerReroll() + "); reroll for a fresh allowance");
    }

    /** Applies the run's shop discount to a price. */
    private int priced(int shopValue) {
        return shopValue - shopValue * run.getShopDiscount() / 100;
    }

    /** The initial card row: injected free cards in front, then the seeded rolled offering. */
    private void initialFill(List<Card> injected) {
        for (int i = 0; i < injected.size(); i++) {
            slots.set(i, injected.get(i));
            slotPrices.set(i, 0);
        }
        for (int rolled = 0; rolled < setup.getSlotCount(); rolled++) {
            int i = injected.size() + rolled;
            slots.set(i, rollSlot(rolled));
            slotPrices.set(i, setup.isInitialItemsFree() ? Integer.valueOf(0) : null);
        }
        applyNegativeGrants(injected.size());
        // Limos: this seat's literal first slot is debuffed for the visit, whatever occupies it.
        if (!slots.isEmpty() && slots.get(0) != null && run.isFirstShopSlotDebuffed())
            slots.get(0).apply(model.modifiers.Sticker.DEBUFFED);
    }

    /** A reroll: injected slots are gone, the row shrinks to its rolled size, contents are freshly seeded. */
    private void rerollFill() {
        slots.clear();
        slotPrices.clear();
        for (int rolled = 0; rolled < setup.getSlotCount(); rolled++) {
            slots.add(rollSlot(rolled));
            slotPrices.add(null);
        }
        applyNegativeGrants(0);
        if (!slots.isEmpty() && slots.get(0) != null && run.isFirstShopSlotDebuffed())
            slots.get(0).apply(model.modifiers.Sticker.DEBUFFED);
    }

    /**
     * Rolls the card for rolled position {@code rolled}. The salt uses the rolled coordinate — not the final
     * slot index — so injected slots in front never shift the seeded offering out of mirror with other seats.
     */
    private Card rollSlot(int rolled) {
        long salt = Rng.combine(shopIndex, rerolls, rolled);
        return setup.getCardPool().roll(run.getRng().streamFor(RngSource.SHOP_CONTENTS, salt));
    }

    /**
     * Negative Tag: turns base-edition jokers just rolled (from {@code firstRolledIndex} on) into free Negative
     * ones, consuming one grant each, in slot order, until grants run out. Unconsumed grants persist to later
     * rerolls — "the next base-edition shop joker", however long it takes to appear.
     */
    private void applyNegativeGrants(int firstRolledIndex) {
        for (int i = firstRolledIndex; i < slots.size() && negativeGrantsLeft > 0; i++) {
            Card c = slots.get(i);
            if (c instanceof JokerCard && c.getEdition() == null) {
                c.apply(Edition.NEGATIVE);
                slotPrices.set(i, 0);
                negativeGrantsLeft--;
            }
        }
    }

    private void fillPacks() {
        if (setup.getPackPool() == null) return;
        for (int i = 0; i < packs.size(); i++) {
            long salt = Rng.combine(shopIndex, i);
            packs.set(i, setup.getPackPool().roll(run.getRng().streamFor(RngSource.SHOP_PACKS, salt)));
            packPrices.set(i, setup.isInitialItemsFree() ? Integer.valueOf(0) : null);
        }
    }

    private void fillVouchers() {
        if (setup.getVoucherPool() == null) return;
        for (int i = 0; i < vouchers.size(); i++) {
            long salt = Rng.combine(shopIndex, i);
            vouchers.set(i, setup.getVoucherPool().roll(run, run.getRng().streamFor(RngSource.SHOP_VOUCHERS, salt)));
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
