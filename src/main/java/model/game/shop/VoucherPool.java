package model.game.shop;
import model.game.player.Run;

import model.items.vouchers.Voucher;

import java.util.random.RandomGenerator;

/** Supplies vouchers for the shop's voucher row, filtered by what the run is currently eligible to redeem. */
@FunctionalInterface
public interface VoucherPool {
    /** A voucher the run may currently redeem, or {@code null} if none are eligible. */
    Voucher roll(Run run, RandomGenerator stream);
}
