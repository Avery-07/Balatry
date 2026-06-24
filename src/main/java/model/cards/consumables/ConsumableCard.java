package model.cards.consumables;

import model.cards.Card;
import model.cards.capability.Sellable;
import model.game.player.Run;

public final class ConsumableCard extends Card implements Sellable {
    private final ConsumableSpec spec;
    private int sellValue;

    public ConsumableCard(ConsumableSpec spec, int shopValue) {
        this.spec = spec;
        setShopValue(shopValue);
        setSellValue(shopValue / 2);
    }

    public void consume(Run run) { spec.getEffect().consume(run, this); }

    public ConsumableSpec getSpec()            { return spec; }
    @Override public int getSellValue()        { return sellValue; }
    @Override public void setSellValue(int value) { sellValue = value; }
}
