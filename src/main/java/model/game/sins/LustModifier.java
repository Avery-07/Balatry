package model.game.sins;

import model.game.player.Run;
import model.game.scoring.HandType;
import model.game.shop.ShopSetup;

import java.math.BigDecimal;

/** Lust: each unique hand type played this round grants +0.5x to the final score of later hands (the
 * unlocking hand excluded; resets every round), and shops gain {@value #EXTRA_ITEMS} extra items — one in the
 * card row and one in the pack row, capped so Lust never floods a single shelf — with a one-purchase-per-roll cap. */
public final class LustModifier implements SinModifier {

    /** The per-unlocked-type score bonus. */
    static final BigDecimal STEP = new BigDecimal("0.5");
    /** How many extra items each Lust shop carries (one card, one pack). */
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
        // One extra card and one extra pack — each row capped at a single Lust extra, so it enriches both shelves
        // without flooding either (the voucher row never grows). The split is fixed, so it is mirrored across seats
        // by construction and needs no roll.
        if (run.getMatch() != null) {
            setup.addSlots(1);
            setup.addPacks(1);
        }
        setup.setMaxPurchasesPerReroll(1);
    }
}
