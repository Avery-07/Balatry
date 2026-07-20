package model.items.consumables;

/**
 * Immutable definition of a consumable: its name, type, base shop cost, one-shot effect, and how many selected
 * cards the effect needs. {@code minTargets} exists so a targeted card (Strength, The Hierophant, …) cannot be
 * wasted on nothing: the model refuses the use, and the client can grey the button and say why.
 */
public final class ConsumableSpec {
    private final String name;
    private final String description;
    private final ConsumableType type;
    private final int cost;
    private final int minTargets;
    private final ConsumableEffect effect;

    public ConsumableSpec(String name, ConsumableType type, int cost, ConsumableEffect effect) {
        this(name, type, cost, "", 0, effect);
    }

    public ConsumableSpec(String name, ConsumableType type, int cost, String description, ConsumableEffect effect) {
        this(name, type, cost, description, 0, effect);
    }

    public ConsumableSpec(String name, ConsumableType type, int cost, String description,
                          int minTargets, ConsumableEffect effect) {
        this.name = name;
        this.description = description;
        this.type = type;
        this.cost = cost;
        this.minTargets = minTargets;
        this.effect = effect;
    }

    public String getName() { return name; }
    /** Human-readable effect text for the UI; empty when none has been authored yet. */
    public String getDescription() { return description; }
    public ConsumableType getType() { return type; }
    public int getCost() { return cost; }            // single source of truth for this card's price
    /** Selected cards the effect requires; using it with fewer is refused rather than wasted. */
    public int getMinTargets() { return minTargets; }
    public ConsumableEffect getEffect() { return effect; }

    @Override
    public String toString() {
        return "ConsumableSpec[name=" + name + ", type=" + type + "]";
    }
}
