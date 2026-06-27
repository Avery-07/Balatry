package model.cards;

import model.cards.capability.Sellable;

/**
 * A card that can be both bought and sold (jokers, consumables). Adds sell value on top of {@link Card};
 * playing cards extend {@link Card} directly since they are never sold.
 */
public abstract class MarketCard extends Card implements Sellable {

    private int sellValue;

    /** Sets the shop price and derives sell value as half of it — the shared default for jokers and consumables. */
    protected void price(int shopValue) {
        setShopValue(shopValue);
        setSellValue(shopValue / 2);
    }

    @Override public int getSellValue()           { return sellValue; }
    @Override public void setSellValue(int value)  { sellValue = value; }
}
