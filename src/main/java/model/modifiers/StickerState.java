package model.modifiers;

/**
 * Per-applied-sticker state. Most stickers are flat; two carry a number that moves as rounds pass —
 * {@link Sticker#PERISHABLE} counts down to its expiry, and {@link Sticker#STICKY} counts <em>up</em> the toll it
 * charges to sell the card.
 */
public record StickerState(int roundsRemaining, int sellCost) {

    public static final int NO_TIMER = -1;
    public static final int PERISHABLE_ROUNDS = 5;

    /** What a Sticky card costs to sell when the sticker lands, and what each round end adds to that. */
    public static final int STICKY_BASE_COST = 2, STICKY_COST_PER_ROUND = 2;

    /** State for a sticker that carries no timer and no toll. */
    public static StickerState flat() { return new StickerState(NO_TIMER, 0); }

    /** State for a timer-bearing sticker. */
    public static StickerState withTimer(int rounds) { return new StickerState(rounds, 0); }

    /** State for a toll-bearing sticker. */
    public static StickerState withCost(int cost) { return new StickerState(NO_TIMER, cost); }

    /** The default state a sticker gets when first applied. */
    public static StickerState forNewly(Sticker sticker) {
        return switch (sticker) {
            case PERISHABLE -> withTimer(PERISHABLE_ROUNDS);
            case STICKY     -> withCost(STICKY_BASE_COST);
            default         -> flat();
        };
    }

    public boolean hasTimer() { return roundsRemaining != NO_TIMER; }
    public boolean expired()  { return hasTimer() && roundsRemaining <= 0; }

    /** One round elapses: a timer counts down, a sell toll grows. Flat stickers are untouched. */
    public StickerState tick() {
        if (hasTimer()) return new StickerState(roundsRemaining - 1, sellCost);
        if (sellCost > 0) return new StickerState(NO_TIMER, sellCost + STICKY_COST_PER_ROUND);
        return this;
    }
}
