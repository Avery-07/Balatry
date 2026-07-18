package model.cards.vouchers;

/** The voucher catalog, as base -> upgrade pairs (an upgrade can only be redeemed once its base has been). */
public enum Vouchers {

    OVERSTOCK("Overstock", null, "+1 card slot in the shop.", r -> r.setShopSlots(r.getShopSlots() + 1)),
    OVERSTOCK_PLUS("Overstock Plus", OVERSTOCK, "+1 card slot in the shop.", r -> r.setShopSlots(r.getShopSlots() + 1)),

    CLEARANCE_SALE("Clearance Sale", null, "25% off all shop prices.", r -> r.setShopDiscount(25)),
    LIQUIDATION("Liquidation", CLEARANCE_SALE, "50% off all shop prices.", r -> r.setShopDiscount(50)),

    HONE("Hone", null, "Shiny editions appear more often.", VoucherEffect.NONE),   // edition rates: needs editions in the pool
    GLOW_UP("Glow Up", HONE, "Shiny editions appear much more often.", VoucherEffect.NONE),

    REROLL_SURPLUS("Reroll Surplus", null, "Rerolls cost $2 less.", r -> r.setBaseRerollCost(Math.max(0, r.getBaseRerollCost() - 2))),
    REROLL_GLUT("Reroll Glut", REROLL_SURPLUS, "Rerolls cost $2 less.", r -> r.setBaseRerollCost(Math.max(0, r.getBaseRerollCost() - 2))),

    CRYSTAL_BALL("Crystal Ball", null, "+1 consumable slot.", r -> r.setConsumableSlots(r.getConsumableSlots() + 1)),
    OMEN_GLOBE("Omen Globe", CRYSTAL_BALL, "Spectral cards may appear in Arcana Packs.", VoucherEffect.NONE),   // spectral in Arcana packs: needs Spectral catalog

    TELESCOPE("Telescope", null, "Celestial Packs contain your most-played hand's Planet.", VoucherEffect.NONE),   // needs hand-tracking hook
    OBSERVATORY("Observatory", TELESCOPE, "Planets you hold apply their hand's Mult when scoring.", VoucherEffect.NONE),

    GRABBER("Grabber", null, "+1 hand per round.", r -> r.setBaseHands(r.getBaseHands() + 1)),
    NACHO_TONG("Nacho Tong", GRABBER, "+1 hand per round.", r -> r.setBaseHands(r.getBaseHands() + 1)),

    WASTEFUL("Wasteful", null, "+1 discard per round.", r -> r.setBaseDiscards(r.getBaseDiscards() + 1)),
    RECYCLOMANCY("Recyclomancy", WASTEFUL, "+1 discard per round.", r -> r.setBaseDiscards(r.getBaseDiscards() + 1)),

    TAROT_MERCHANT("Tarot Merchant", null, "Tarot cards appear more often in the shop.", VoucherEffect.NONE),   // needs a weight knob
    TAROT_TYCOON("Tarot Tycoon", TAROT_MERCHANT, "Tarot cards appear much more often in the shop.", VoucherEffect.NONE),

    PLANET_MERCHANT("Planet Merchant", null, "Planet cards appear more often in the shop.", VoucherEffect.NONE),
    PLANET_TYCOON("Planet Tycoon", PLANET_MERCHANT, "Planet cards appear much more often in the shop.", VoucherEffect.NONE),

    SEED_MONEY("Seed Money", null, "Raises the interest cap to $10 per round.", r -> r.setInterestCap(10)),
    MONEY_TREE("Money Tree", SEED_MONEY, "Raises the interest cap to $20 per round.", r -> r.setInterestCap(20)),

    BLANK("Blank", null, "Does nothing (a prerequisite for Antimatter).", VoucherEffect.NONE),
    ANTIMATTER("Antimatter", BLANK, "+1 joker slot.", r -> r.setJokerSlots(r.getJokerSlots() + 1)),

    MAGIC_TRICK("Magic Trick", null, "Playing cards appear for purchase in the shop.", VoucherEffect.NONE),   // out of card-row scope for now
    ILLUSION("Illusion", MAGIC_TRICK, "Shop playing cards may have enhancements, editions and seals.", VoucherEffect.NONE),

    PAINT_BRUSH("Paint Brush", null, "+1 hand size.", r -> r.setHandSize(r.getHandSize() + 1)),
    PALETTE("Palette", PAINT_BRUSH, "+1 hand size.", r -> r.setHandSize(r.getHandSize() + 1)),

    SAMPLER("Sampler", null, "+1 card option shown in every booster pack.", r -> r.setPackOptionBonus(r.getPackOptionBonus() + 1)),
    CONNOISSEUR("Connoisseur", null, "+1 card kept from Mega packs.", r -> r.setPackMegaPickBonus(r.getPackMegaPickBonus() + 1)),

    RELIC_MERCHANT("Relic Merchant", null, "Relics appear more often in the shop.", VoucherEffect.NONE),   // needs Relic weight knob
    RELIC_TYCOON("Relic Tycoon", RELIC_MERCHANT, "Relics appear much more often in the shop.", VoucherEffect.NONE);

    private static final int COST = 10;

    private final VoucherSpec spec;

    Vouchers(String displayName, Vouchers base, String description, VoucherEffect effect) {
        this.spec = new VoucherSpec(displayName, COST, base == null ? null : base.spec(), description, effect);
    }

    public VoucherSpec spec() { return spec; }

    /** A fresh voucher card at its spec's price. */
    public Voucher make() { return new Voucher(spec); }
}
