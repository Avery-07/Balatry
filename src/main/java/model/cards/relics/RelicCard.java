package model.cards.relics;

import model.cards.MarketCard;

/** A held, single-use relic card. Bought/found like a consumable; priced from its spec (sell value is half). */
public final class RelicCard extends MarketCard {

    private final RelicSpec spec;

    public RelicCard(RelicSpec spec) {
        this.spec = spec;
        price(spec.getCost());
    }

    public RelicSpec getSpec() { return spec; }
}
