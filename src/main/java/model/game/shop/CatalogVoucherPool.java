package model.game.shop;
import model.game.player.Run;

import model.items.vouchers.Voucher;
import model.items.vouchers.Vouchers;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/** Offers a voucher the run is eligible to redeem: not already redeemed, and (for an upgrade) its base already redeemed. */
public final class CatalogVoucherPool implements VoucherPool {

    public static final CatalogVoucherPool INSTANCE = new CatalogVoucherPool();

    private CatalogVoucherPool() { }

    @Override
    public Voucher roll(Run run, RandomGenerator stream) {
        List<Vouchers> eligible = new ArrayList<>();
        for (Vouchers v : Vouchers.values()) {
            if (run.hasRedeemed(v.spec())) continue;
            if (v.spec().getBase() != null && !run.hasRedeemed(v.spec().getBase())) continue;
            eligible.add(v);
        }
        if (eligible.isEmpty()) return null;
        return eligible.get(stream.nextInt(eligible.size())).make();
    }
}
