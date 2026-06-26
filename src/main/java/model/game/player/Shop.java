package model.game.player;

import model.cards.Card;
import model.game.rng.Rng;
import model.game.rng.RngSource;

import java.util.ArrayList;
import java.util.List;

/** A per-run, seed-mirrored card shop for one SHOP phase: seeded slots, reroll, and purchases. */
public final class Shop {

    private final Run run;
    private final int shopIndex;     // structural coordinate: the Nth shop opened on this run
    private final ShopPool pool;
    private final List<Card> slots;  // null entries are sold/empty
    private int rerolls;

    Shop(Run run, int shopIndex, int slotCount, ShopPool pool) {
        this.run = run;
        this.shopIndex = shopIndex;
        this.pool = pool;
        this.slots = new ArrayList<>(slotCount);
        for (int i = 0; i < slotCount; i++) slots.add(null);
        fill();
    }

    /** Items on offer; null entries are sold or empty. */
    public List<Card> getSlots() { return new ArrayList<>(slots); }

    public int getSlotCount()  { return slots.size(); }
    public Card getSlot(int i) { return slots.get(i); }
    public int getRerolls()    { return rerolls; }

    /** Cost of the next reroll: the run's base reroll cost plus $1 per reroll already done this shop. */
    public int rerollCost() { return run.getBaseRerollCost() + rerolls; }

    /** Buys the item in {@code slotIndex}, charging the run and routing it into inventory. */
    public Card buy(int slotIndex) {
        Card item = slots.get(slotIndex);
        if (item == null) throw new IllegalStateException("slot " + slotIndex + " is empty");
        int price = item.getShopValue();
        if (run.getMoney() < price)
            throw new IllegalStateException("cannot afford " + price + " (have " + run.getMoney() + ")");
        if (!run.canAcquire(item))
            throw new IllegalStateException("no inventory slot for " + item);
        run.addMoney(-price);
        run.acquire(item);
        slots.set(slotIndex, null);
        return item;
    }

    /** Rerolls every slot for a fresh, still-seeded set of offerings; charges the reroll cost. */
    public void reroll() {
        int cost = rerollCost();
        if (run.getMoney() < cost) throw new IllegalStateException("cannot afford reroll " + cost);
        run.addMoney(-cost);
        rerolls++;
        fill();
    }

    /** (Re)generates every slot from the run's seed at the current reroll count. */
    private void fill() {
        for (int i = 0; i < slots.size(); i++) {
            long salt = Rng.combine(shopIndex, rerolls, i);
            slots.set(i, pool.roll(run.getRng().streamFor(RngSource.SHOP_CONTENTS, salt)));
        }
    }
}