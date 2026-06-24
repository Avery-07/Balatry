package model.cards;

import model.cards.consumableHelpers.ConsumableSpec;
import model.cards.capability.Sellable;

public final class ConsumableCard extends Card implements Sellable {
    private final ConsumableSpec spec;
    private int shopValue;

    public ConsumableCard(ConsumableSpec spec, int shopValue) {
        this.spec = spec;
        this.shopValue = shopValue;
    }

    public void use(GameContext ctx) { spec.effect().use(ctx, this); }

    public ConsumableSpec getSpec() { return spec; }
    public int getShopValue()  { return shopValue; }

    @Override
    public int getSellValue() {
        return 0;
    }
}
