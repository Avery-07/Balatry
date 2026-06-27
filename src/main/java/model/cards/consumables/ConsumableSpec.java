package model.cards.consumables;

/** Immutable definition of a consumable: its name, type, base shop cost, and one-shot effect. */
public final class ConsumableSpec {
    private final String name;
    private final ConsumableType type;
    private final int cost;
    private final ConsumableEffect effect;

    public ConsumableSpec(String name, ConsumableType type, int cost, ConsumableEffect effect) {
        this.name = name;
        this.type = type;
        this.cost = cost;
        this.effect = effect;
    }

    public String getName() { return name; }
    public ConsumableType getType() { return type; }
    public int getCost() { return cost; }            // single source of truth for this card's price
    public ConsumableEffect getEffect() { return effect; }

    @Override
    public String toString() {
        return "ConsumableSpec[name=" + name + ", type=" + type + "]";
    }
}
