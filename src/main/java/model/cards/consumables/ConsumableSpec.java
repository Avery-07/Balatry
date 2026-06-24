package model.cards.consumables;

public final class ConsumableSpec {
    private final String name;
    private final ConsumableType type;
    private final String description;
    private final ConsumableEffect effect;

    public ConsumableSpec(String name, ConsumableType type, String description, ConsumableEffect effect) {
        this.name = name;
        this.type = type;
        this.description = description;
        this.effect = effect;
    }

    public String getName() { return name; }
    public ConsumableType getType() { return type; }
    public String getDescription() { return description; }
    public ConsumableEffect getEffect() { return effect; }

    @Override
    public String toString() {
        return "ConsumableSpec[name=" + name + ", type=" + type + "]";
    }

}