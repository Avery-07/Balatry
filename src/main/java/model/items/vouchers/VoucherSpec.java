package model.items.vouchers;

/** Immutable voucher definition: name, cost, optional prerequisite voucher, and persistent effect. */
public final class VoucherSpec {
    private final String name;
    private final String description;
    private final int cost;
    private final VoucherSpec base;     // must be redeemed first; null for base-tier vouchers
    private final VoucherEffect effect;

    public VoucherSpec(String name, int cost, VoucherSpec base, VoucherEffect effect) {
        this(name, cost, base, "", effect);
    }

    public VoucherSpec(String name, int cost, VoucherSpec base, String description, VoucherEffect effect) {
        this.name = name;
        this.description = description;
        this.cost = cost;
        this.base = base;
        this.effect = effect;
    }

    public String getName()       { return name; }
    /** Human-readable effect text for the UI; empty when none has been authored yet. */
    public String getDescription() { return description; }
    public int getCost()          { return cost; }
    public VoucherSpec getBase()  { return base; }     // nullable
    public VoucherEffect getEffect() { return effect; }

    @Override public String toString() { return "VoucherSpec[" + name + "]"; }
}
