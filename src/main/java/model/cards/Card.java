package model.cards;

import model.cards.capability.Buyable;
import model.modifiers.Edition;
import model.modifiers.Sticker;
import model.modifiers.StickerState;

import java.util.EnumMap;
import java.util.Map;

/** Common state for every card: edition, stickers (with per-sticker state), and shop value. */
public abstract class Card implements Buyable {

    private Edition edition;
    private final Map<Sticker, StickerState> stickers = new EnumMap<>(Sticker.class);
    private int shopValue;

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

    /** Whether this card's effects are nullified. */
    public boolean isDebuffed() { return stickers.containsKey(Sticker.DEBUFFED); }

    /** Money removed at end of round while RENTAL is attached. */
    public int getRentalCost() { return hasSticker(Sticker.RENTAL) ? 3 : 0; }

    /** Advances per-round sticker timers; call once per card at end of round. Expired PERISHABLE self-applies DEBUFFED and NEGATIVE. */
    public void tickStickers() {
        StickerState perishable = stickers.get(Sticker.PERISHABLE);
        if (perishable == null || !perishable.hasTimer() || perishable.expired()) return;

        StickerState next = perishable.tick();
        stickers.put(Sticker.PERISHABLE, next);
        if (next.expired()) {
            apply(Sticker.DEBUFFED);
            apply(Edition.NEGATIVE);
        }
    }

    @Override public int getShopValue()        { return shopValue; }
    @Override public void setShopValue(int value) { shopValue = value; }
}