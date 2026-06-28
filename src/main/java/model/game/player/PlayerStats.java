package model.game.player;

import model.cards.vouchers.Voucher;
import model.cards.vouchers.VoucherSpec;
import model.game.rng.RngSource;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Per-player tracking that lives beside a {@link Run}: keyed RNG occurrence counters, the shop-open counter,
 * and the voucher-redemption ledger. This is the home for accumulated run state; {@link Run} keeps the
 * core inventory, subsystems, and configuration. Effect application stays on {@code Run} (it needs the run);
 * here we only track what has happened.
 */
public final class PlayerStats {

    private final Map<RngSource, Integer> occurrences = new EnumMap<>(RngSource.class);
    private int shopsOpened;
    private final Set<VoucherSpec> redeemedVouchers = new HashSet<>();
    private boolean voucherRedeemedThisAnte;

    // --- keyed RNG counters (salt the Nth occurrence of an emergent draw) ---

    /** Salt for the next draw on {@code source} (its 0-based occurrence index), then advances that source's counter. */
    public long nextSalt(RngSource source) {
        int n = occurrences.getOrDefault(source, 0);
        occurrences.put(source, n + 1);
        return n;
    }

    /** How many draws have been taken on {@code source} so far. Does not advance the counter. */
    public int getCount(RngSource source) {
        return occurrences.getOrDefault(source, 0);
    }

    // --- shop counter ---

    /** The structural coordinate for the next shop opened (its 0-based index), then advances the counter. */
    public int nextShopIndex() { return shopsOpened++; }

    // --- voucher ledger ---

    /** Whether {@code spec} has been redeemed on this run (used for upgrade prerequisites). */
    public boolean hasRedeemed(VoucherSpec spec) { return redeemedVouchers.contains(spec); }

    /** Whether {@code voucher} may be redeemed now: not already redeemed, base satisfied, and none used yet this ante. */
    public boolean canRedeem(Voucher voucher) {
        VoucherSpec spec = voucher.getSpec();
        if (voucherRedeemedThisAnte || redeemedVouchers.contains(spec)) return false;
        return spec.getBase() == null || redeemedVouchers.contains(spec.getBase());
    }

    /** Records {@code spec} as redeemed and consumes this ante's single redemption. */
    public void markRedeemed(VoucherSpec spec) {
        redeemedVouchers.add(spec);
        voucherRedeemedThisAnte = true;
    }

    /** Resets the per-ante voucher allowance; call at the start of each ante. */
    public void beginAnte() { voucherRedeemedThisAnte = false; }
}
