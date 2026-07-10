package model.game.shop;

import model.cards.Card;

import java.util.ArrayList;
import java.util.List;

/**
 * The mutable configuration a {@link Shop} is built from: row sizes, pools, pricing rules, injected items, and
 * purchase limits. Assembled by {@link model.game.player.Run#openShop} from the run's defaults, then handed to
 * the two shop-modifying parties in order — first the active {@link model.game.sins.SinModifier#configureShop
 * sin} (the ante's ambient environment), then the run's pending NEXT_SHOP {@link model.game.tags.SkipTag skip
 * tags} (player-earned boosts layered on top). The Shop reads it once at construction; later mutation has no
 * effect.
 *
 * <p>This is the single seam for shop customisation. Greed (pool swap, purchase reactions), Lust (extra items,
 * {@link #setMaxPurchasesPerReroll one purchase per reroll}), Gluttony (all-consumable pool), and the shop tags
 * (Coupon, D6, Uncommon, Rare, Negative, Voucher) all express themselves as setup mutations, so none of them
 * needs an edit inside {@link Shop} itself.
 */
public final class ShopSetup {

    private int slotCount;
    private int packCount;
    private int voucherCount;
    private ShopPool cardPool;
    private PackPool packPool;
    private VoucherPool voucherPool;
    private boolean initialItemsFree;                        // Coupon Tag: initial card + pack rows cost $0
    private boolean rerollsFromZero;                         // D6 Tag: reroll cost starts at $0 (still +$1 per reroll)
    private int maxPurchasesPerReroll = Integer.MAX_VALUE;   // Lust: 1 item per reroll state
    private int negativeJokerGrants;                         // Negative Tags: next base-edition rolled jokers become Negative + free
    private final List<Card> injectedFreeCards = new ArrayList<>();   // Uncommon/Rare Tags: free leading card slots

    /** Seeds the setup with a run's default dimensions and the catalog pools. */
    public ShopSetup(int slotCount, ShopPool cardPool,
                     int packCount, PackPool packPool,
                     int voucherCount, VoucherPool voucherPool) {
        this.slotCount = slotCount;
        this.cardPool = cardPool;
        this.packCount = packCount;
        this.packPool = packPool;
        this.voucherCount = voucherCount;
        this.voucherPool = voucherPool;
    }

    // --- row sizes ---

    public int getSlotCount()    { return slotCount; }
    public int getPackCount()    { return packCount; }
    public int getVoucherCount() { return voucherCount; }

    public void setSlotCount(int n)    { slotCount = Math.max(0, n); }
    public void setPackCount(int n)    { packCount = Math.max(0, n); }
    public void setVoucherCount(int n) { voucherCount = Math.max(0, n); }

    public void addSlots(int n)    { setSlotCount(slotCount + n); }
    public void addPacks(int n)    { setPackCount(packCount + n); }
    public void addVouchers(int n) { setVoucherCount(voucherCount + n); }

    // --- pools ---

    public ShopPool getCardPool()       { return cardPool; }
    public PackPool getPackPool()       { return packPool; }
    public VoucherPool getVoucherPool() { return voucherPool; }

    public void setCardPool(ShopPool pool)       { cardPool = pool; }
    public void setPackPool(PackPool pool)       { packPool = pool; }
    public void setVoucherPool(VoucherPool pool) { voucherPool = pool; }

    // --- pricing rules ---

    /** Coupon Tag: the initial fill of the card and pack rows is free; rerolled contents are full price. */
    public boolean isInitialItemsFree()      { return initialItemsFree; }
    public void setInitialItemsFree(boolean b) { initialItemsFree = b; }

    /** D6 Tag: this shop's reroll cost starts at $0 instead of the run's base, still climbing $1 per reroll. */
    public boolean isRerollsFromZero()      { return rerollsFromZero; }
    public void setRerollsFromZero(boolean b) { rerollsFromZero = b; }

    // --- purchase limits ---

    /** How many items (any row) may be bought per reroll state; rerolling grants a fresh allowance. */
    public int getMaxPurchasesPerReroll() { return maxPurchasesPerReroll; }
    public void setMaxPurchasesPerReroll(int n) { maxPurchasesPerReroll = Math.max(1, n); }

    // --- injections and transforms ---

    /**
     * Adds a free card occupying its own leading slot in the initial card row (Uncommon/Rare Tags). Injected
     * slots are extra — the rolled offering behind them keeps its own positions and salts, so a seat with tag
     * boosts still mirrors the seeded contents of a seat without. Injected cards vanish on reroll, like any
     * other shop content.
     */
    public void injectFreeCard(Card card) { injectedFreeCards.add(card); }

    /** The cards injected so far, in injection order. */
    public List<Card> getInjectedFreeCards() { return new ArrayList<>(injectedFreeCards); }

    /** Negative Tag: each grant turns the next base-edition joker rolled into a free Negative one (persists across rerolls). */
    public int getNegativeJokerGrants() { return negativeJokerGrants; }
    public void addNegativeJokerGrant() { negativeJokerGrants++; }
}
