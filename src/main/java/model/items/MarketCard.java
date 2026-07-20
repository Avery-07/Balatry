package model.items;


/** A card that can be both bought and sold (jokers, consumables). */
public abstract class MarketCard extends Card {

    private int sellValue;

    /** Sets the shop price and derives sell value as half of it — the shared default for jokers and consumables. */
    protected void price(int shopValue) {
        setShopValue(shopValue);
        setSellValue(shopValue / 2);
    }

    public int getSellValue()           { return sellValue; }
    public void setSellValue(int value)  { sellValue = value; }
}
