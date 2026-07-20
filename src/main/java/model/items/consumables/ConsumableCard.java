package model.items.consumables;

import model.items.MarketCard;
import model.game.player.Run;

public final class ConsumableCard extends MarketCard {
    private final ConsumableSpec spec;

    public ConsumableCard(ConsumableSpec spec) {
        this.spec = spec;
        price(spec.getCost());
    }

    public void consume(Run run) { spec.getEffect().apply(run, this); }

    public ConsumableSpec getSpec() { return spec; }
}
