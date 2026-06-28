package model.cards.vouchers;

import model.cards.Card;

/** A buyable (not sellable) voucher card; redemption rules live on the run. */
public final class Voucher extends Card {
    private final VoucherSpec spec;

    public Voucher(VoucherSpec spec) {
        this.spec = spec;
        setShopValue(spec.getCost());
    }

    public VoucherSpec getSpec() { return spec; }
}
