package model.modifiers;

/** Per-applied-sticker state. Most stickers are flat; PERISHABLE carries a round countdown. */
public record StickerState(int roundsRemaining) {

    public static final int NO_TIMER = -1;
    public static final int PERISHABLE_ROUNDS = 5;

    /** State for a sticker that carries no timer. */
    public static StickerState flat() { return new StickerState(NO_TIMER); }

    /** State for a timer-bearing sticker. */
    public static StickerState withTimer(int rounds) { return new StickerState(rounds); }

    /** The default state a sticker gets when first applied. */
    public static StickerState forNewly(Sticker sticker) {
        return sticker == Sticker.PERISHABLE ? withTimer(PERISHABLE_ROUNDS) : flat();
    }

    public boolean hasTimer() { return roundsRemaining != NO_TIMER; }
    public boolean expired()  { return hasTimer() && roundsRemaining <= 0; }

    /** One round elapses. No-op for flat stickers. */
    public StickerState tick() {
        return hasTimer() ? new StickerState(roundsRemaining - 1) : this;
    }
}