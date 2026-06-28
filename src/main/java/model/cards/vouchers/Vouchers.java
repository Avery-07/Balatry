package model.cards.vouchers;

/**
 * The voucher catalog, as base -> upgrade pairs (an upgrade can only be redeemed once its base has been).
 * Effects are implemented where they map to existing {@link model.game.player.Run} knobs; vouchers needing
 * unbuilt systems (edition rates, shop appearance weights, playing cards in the shop) carry
 * {@link VoucherEffect#NONE} for now. Removed vouchers (Hieroglyph, Petroglyph, Director's Cut, Retcon) are absent.
 */
public enum Vouchers {

    OVERSTOCK("Overstock", null, r -> r.setShopSlots(r.getShopSlots() + 1)),
    OVERSTOCK_PLUS("Overstock Plus", OVERSTOCK, r -> r.setShopSlots(r.getShopSlots() + 1)),

    CLEARANCE_SALE("Clearance Sale", null, r -> r.setShopDiscount(25)),
    LIQUIDATION("Liquidation", CLEARANCE_SALE, r -> r.setShopDiscount(50)),

    HONE("Hone", null, VoucherEffect.NONE),                 // edition rates: needs editions in the pool
    GLOW_UP("Glow Up", HONE, VoucherEffect.NONE),

    REROLL_SURPLUS("Reroll Surplus", null, r -> r.setBaseRerollCost(Math.max(0, r.getBaseRerollCost() - 2))),
    REROLL_GLUT("Reroll Glut", REROLL_SURPLUS, r -> r.setBaseRerollCost(Math.max(0, r.getBaseRerollCost() - 2))),

    CRYSTAL_BALL("Crystal Ball", null, r -> r.setConsumableSlots(r.getConsumableSlots() + 1)),
    OMEN_GLOBE("Omen Globe", CRYSTAL_BALL, VoucherEffect.NONE),   // spectral in Arcana packs: needs Spectral catalog

    TELESCOPE("Telescope", null, VoucherEffect.NONE),       // most-played planet in Celestial packs: needs hand-tracking hook
    OBSERVATORY("Observatory", TELESCOPE, VoucherEffect.NONE),

    GRABBER("Grabber", null, r -> r.setBaseHands(r.getBaseHands() + 1)),
    NACHO_TONG("Nacho Tong", GRABBER, r -> r.setBaseHands(r.getBaseHands() + 1)),

    WASTEFUL("Wasteful", null, r -> r.setBaseDiscards(r.getBaseDiscards() + 1)),
    RECYCLOMANCY("Recyclomancy", WASTEFUL, r -> r.setBaseDiscards(r.getBaseDiscards() + 1)),

    TAROT_MERCHANT("Tarot Merchant", null, VoucherEffect.NONE),   // shop appearance weights: needs a weight knob
    TAROT_TYCOON("Tarot Tycoon", TAROT_MERCHANT, VoucherEffect.NONE),

    PLANET_MERCHANT("Planet Merchant", null, VoucherEffect.NONE),
    PLANET_TYCOON("Planet Tycoon", PLANET_MERCHANT, VoucherEffect.NONE),

    SEED_MONEY("Seed Money", null, r -> r.setInterestCap(10)),
    MONEY_TREE("Money Tree", SEED_MONEY, r -> r.setInterestCap(20)),

    BLANK("Blank", null, VoucherEffect.NONE),               // intentionally does nothing
    ANTIMATTER("Antimatter", BLANK, r -> r.setJokerSlots(r.getJokerSlots() + 1)),

    MAGIC_TRICK("Magic Trick", null, VoucherEffect.NONE),   // playing cards in the shop: out of the card-row scope for now
    ILLUSION("Illusion", MAGIC_TRICK, VoucherEffect.NONE),

    PAINT_BRUSH("Paint Brush", null, r -> r.setHandSize(r.getHandSize() + 1)),
    PALETTE("Palette", PAINT_BRUSH, r -> r.setHandSize(r.getHandSize() + 1)),

    SAMPLER("Sampler", null, r -> r.setPackOptionBonus(r.getPackOptionBonus() + 1)),
    CONNOISSEUR("Connoisseur", null, r -> r.setPackMegaPickBonus(r.getPackMegaPickBonus() + 1)),

    RELIC_MERCHANT("Relic Merchant", null, VoucherEffect.NONE),   // relic appearance: needs Relic catalog + weight knob
    RELIC_TYCOON("Relic Tycoon", RELIC_MERCHANT, VoucherEffect.NONE);

    private static final int COST = 10;

    private final VoucherSpec spec;

    Vouchers(String displayName, Vouchers base, VoucherEffect effect) {
        this.spec = new VoucherSpec(displayName, COST, base == null ? null : base.spec(), effect);
    }

    public VoucherSpec spec() { return spec; }

    /** A fresh voucher card at its spec's price. */
    public Voucher make() { return new Voucher(spec); }
}
