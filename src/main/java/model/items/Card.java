package model.items;

import model.modifiers.Edition;
import model.modifiers.Sticker;
import model.modifiers.StickerState;

import java.util.EnumMap;
import java.util.Map;

/** Common state for every card: edition, stickers (with per-sticker state), and shop value. */
public abstract class Card {

    private Edition edition;
    private final Map<Sticker, StickerState> stickers = new EnumMap<>(Sticker.class);
    private int shopValue;
    private boolean suppressed;   // transient silence owned by whoever set it; see isSuppressed()

    public Edition getEdition() { return edition; }

    /** Applies an edition, replacing any existing one (a card has at most one). */
    public void apply(Edition edition) { this.edition = edition; }

    /** Removes the edition, if it is the given one. */
    public void remove(Edition edition) { if (this.edition == edition) this.edition = null; }

    /** Immutable snapshot of applied stickers and their state. */
    public Map<Sticker, StickerState> getStickers() { return Map.copyOf(stickers); }

    public boolean hasSticker(Sticker sticker)        { return stickers.containsKey(sticker); }
    public StickerState getStickerState(Sticker sticker) { return stickers.get(sticker); }

    /** Applies a sticker with its default state (a card may carry several at once). */
    public void apply(Sticker sticker) { stickers.put(sticker, StickerState.forNewly(sticker)); }

    /** Applies a sticker with an explicit state. */
    public void apply(Sticker sticker, StickerState state) { stickers.put(sticker, state); }

    /** Removes the given sticker, if present. */
    public void remove(Sticker sticker) { stickers.remove(sticker); }

    /**
     * Whether this card's effects are nullified — the single gate the whole engine consults before letting a
     * card do anything. It covers both the lasting {@link Sticker#DEBUFFED} and the transient suppression below.
     */
    public boolean isDebuffed() { return suppressed || stickers.containsKey(Sticker.DEBUFFED); }

    /**
     * Transient, scope-owned silence — distinct from the DEBUFFED sticker, which is a lasting mark a player can
     * see and cure. Whoever sets it is responsible for clearing it ({@link Sticker#DELAYED} is silenced for a
     * round's first hand and freed after it), and because it feeds {@link #isDebuffed()} it needs no separate
     * check anywhere in the engine.
     */
    public boolean isSuppressed()             { return suppressed; }
    public void setSuppressed(boolean value)  { suppressed = value; }

    /** Money removed at end of round while RENTAL is attached. */
    public int getRentalCost() { return hasSticker(Sticker.RENTAL) ? 3 : 0; }

    /** What selling this card costs while STICKY is attached; $0 when it is not. */
    public int getStickySellCost() {
        StickerState sticky = stickers.get(Sticker.STICKY);
        return sticky == null ? 0 : sticky.sellCost();
    }

    /**
     * Advances per-round sticker state; call once per card at end of round. An expired PERISHABLE self-applies
     * DEBUFFED and NEGATIVE; a STICKY sticker's sell toll grows.
     */
    public void tickStickers() {
        StickerState sticky = stickers.get(Sticker.STICKY);
        if (sticky != null) stickers.put(Sticker.STICKY, sticky.tick());

        StickerState perishable = stickers.get(Sticker.PERISHABLE);
        if (perishable == null || !perishable.hasTimer() || perishable.expired()) return;

        StickerState next = perishable.tick();
        stickers.put(Sticker.PERISHABLE, next);
        if (next.expired()) {
            apply(Sticker.DEBUFFED);
            apply(Edition.NEGATIVE);
        }
    }

    public int getShopValue()        { return shopValue; }
    public void setShopValue(int value) { shopValue = value; }
}