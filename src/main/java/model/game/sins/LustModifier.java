package model.game.sins;

import model.game.Match;
import model.game.player.Run;
import model.game.rng.Rng;
import model.game.rng.RngSource;
import model.game.scoring.HandType;
import model.game.shop.ShopSetup;

import java.math.BigDecimal;

/**
 * Lust. Two mechanics:
 *
 * <p><b>The diversity multiplier</b> — each unique hand type played this round grants a persistent +0.5x to the
 * <em>final score</em> of hands played after it (x1 for the round's first hand, x1.5 once one type is played,
 * x2 at two, ...). The unlocking hand itself does not benefit: {@link #adjustHandScore} multiplies by the
 * already-unlocked count first and records the hand's type after. Repeats keep whatever is unlocked but add
 * nothing. The set lives round-scoped in {@link SinState}, so every blind starts back at x1.
 *
 * <p><b>The crowded shop</b> — every shop grows {@value #EXTRA_ITEMS} extra items, each landing in the card or
 * pack row (never the voucher row) by a table-level seeded roll, so all seats' shops grow the same shape and
 * stay mirrored. Only one item may be bought per shop roll state; a reroll grants a fresh allowance (the
 * {@link ShopSetup#setMaxPurchasesPerReroll purchase cap} built in the shop-modifier pass).
 */
public final class LustModifier implements SinModifier {

    /** The per-unlocked-type score bonus. */
    static final BigDecimal STEP = new BigDecimal("0.5");
    /** How many extra items each Lust shop carries. */
    public static final int EXTRA_ITEMS = 2;

    @Override
    public BigDecimal adjustHandScore(Run run, HandType type, BigDecimal handScore) {
        SinState s = run.getSinState();
        BigDecimal adjusted = handScore.multiply(s.lustMultiplier());   // types unlocked before this hand
        s.recordLustType(type);                                         // this hand's type pays out later, not now
        return adjusted;
    }

    @Override
    public void configureShop(Run run, ShopSetup setup) {
        Match match = run.getMatch();
        if (match != null) {
            // Table-level rolls salted by (ante, blind): every seat derives the same row split, keeping shops
            // mirrored. Each extra item lands in the card or pack row - never the voucher row.
            long salt = Rng.combine(match.getAnte(), match.getBlind().ordinal());
            for (int i = 0; i < EXTRA_ITEMS; i++) {
                boolean pack = match.getRng().nextInt(RngSource.LUST_SHOP_EXTRAS, Rng.combine(salt, i), 2) == 1;
                if (pack) setup.addPacks(1);
                else setup.addSlots(1);
            }
        }
        setup.setMaxPurchasesPerReroll(1);
    }
}
