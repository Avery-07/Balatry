package model.cards;

import model.cards.capability.Buyable;
import model.cards.jokers.JokerSpec;
import model.cards.jokers.JokerTrigger;
import model.cards.capability.Sellable;
import model.modifiers.Sticker;

public final class JokerCard extends Card implements Sellable, Buyable {
    private final JokerSpec spec;
    private int counter; // per-instance scaling variable
    private int shopValue, sellValue;

    public JokerCard(JokerSpec spec) {
        this.spec = spec;
    }

    public void trigger(JokerTrigger trigger, GameContext ctx) {
        if (getSticker().contains(Sticker.DEBUFFED)) return;
        spec.effectFor(trigger).apply(ctx, this);
    }

    public JokerSpec getSpec() { return spec; }
    public int getCounter() { return counter; }
    public void setCounter(int value) { counter = value; }
    @Override
    public int getShopValue() { return shopValue; }
    @Override
    public void setShopValue(int value) { shopValue = value; }
    @Override
    public int getSellValue() { return sellValue; }
    @Override
    public void setSellValue(int value) { sellValue = value; }
}
