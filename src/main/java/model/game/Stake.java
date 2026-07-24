package model.game;

/**
 * A seat's difficulty. Stakes are <em>per-seat</em>: two players in the same match may sit at different stakes, so
 * everything a stake touches (blind targets, cash-out, reroll pricing) must be asked per seat rather than read off
 * the table.
 *
 * <p>Stakes are cumulative in declaration order — each one "applies all previous effects", which {@link #includes}
 * expresses. That holds for the sticker stakes too: the pool a shop card rolls from is every pair up to and
 * including the seat's stake (see {@code Stickers.poolFor}).
 */
public enum Stake {

    WHITE ("White Stake",  "Base difficulty.\nStandard score scaling and rewards."),
    RED   ("Red Stake",    "Shop jokers may be Floating or Sticky.\nApplies all previous stakes."),
    GREEN ("Green Stake",  "Required score scales faster each ante.\nApplies all previous stakes."),
    BLACK ("Black Stake",  "The Small Blind pays no reward money.\nApplies all previous stakes."),
    BLUE  ("Blue Stake",   "Shop jokers may also be Fragile or Eternal.\nApplies all previous stakes."),
    PURPLE("Purple Stake", "Required score scales significantly faster.\nApplies all previous stakes."),
    ORANGE("Orange Stake", "Shop rerolls get $1 more expensive each time.\nApplies all previous stakes."),
    GOLD  ("Gold Stake",   "Shop jokers may also be Rental or Perishable.\nApplies all previous stakes.");

    /**
     * Per-ante target growth contributed by this stake alone, as a numerator over {@link #SCALE_DEN}. The effective
     * growth at a given stake is the product of every contribution up to and including it, so Purple compounds on
     * top of Green exactly as "applies all previous effects" demands.
     */
    private static final int SCALE_DEN = 100;

    private final String displayName;
    private final String description;

    Stake(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String displayName() { return displayName; }
    public String description() { return description; }

    /** Whether this stake carries {@code other}'s effects — true when {@code other} is this stake or milder. */
    public boolean includes(Stake other) { return other != null && other.ordinal() <= ordinal(); }

    /** This stake's own per-ante growth numerator over {@link #SCALE_DEN}; 100 means "no extra growth". */
    private int growthNumerator() {
        return switch (this) {
            case GREEN  -> 110;   // +10% per ante above the first
            case PURPLE -> 115;   // a further +15%, compounding with Green's
            default     -> SCALE_DEN;
        };
    }

    /**
     * Scales a white-stake chip target for this stake at {@code ante}. Growth compounds per ante above the first,
     * across every contributing stake at or below this one, in long arithmetic so replays stay bit-identical.
     */
    public long scaleTarget(long whiteTarget, int ante) {
        long target = whiteTarget;
        for (Stake s : values()) {
            if (!includes(s)) break;
            int num = s.growthNumerator();
            if (num == SCALE_DEN) continue;
            for (int a = 1; a < ante; a++) target = target * num / SCALE_DEN;
        }
        return target;
    }

    /** Cash-out reward for clearing {@code blind} at this stake; Black and above zero out the Small Blind. */
    public int rewardFor(Blind blind) {
        if (blind == Blind.SMALL && includes(BLACK)) return 0;
        return blind.getReward();
    }

    /** Dollars each reroll adds to the next reroll's price: $1 normally, $2 from Orange up. */
    public int rerollStep() { return includes(ORANGE) ? 2 : 1; }

    @Override public String toString() { return displayName; }
}
