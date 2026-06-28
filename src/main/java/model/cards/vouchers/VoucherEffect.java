package model.cards.vouchers;

import model.game.player.Run;

/** A voucher's persistent effect on a run when redeemed. */
@FunctionalInterface
public interface VoucherEffect {
    void apply(Run run);

    /** For vouchers whose effect needs a system that isn't built yet (edition rates, appearance weights). */
    VoucherEffect NONE = run -> { };
}
