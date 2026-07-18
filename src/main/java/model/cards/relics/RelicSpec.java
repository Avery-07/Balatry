package model.cards.relics;

/** Immutable definition of a relic: its name, targeting kind, base shop cost, and one-shot effect. */
public final class RelicSpec {

    private final String name;
    private final RelicKind kind;
    private final RelicSelector selector;
    private final int cost;
    private final RelicEffect effect;

    public RelicSpec(String name, RelicKind kind, RelicSelector selector, int cost, RelicEffect effect) {
        this.name = name;
        this.kind = kind;
        this.selector = selector;
        this.cost = cost;
        this.effect = effect;
    }

    public String getName()     { return name; }
    public RelicKind getKind()  { return kind; }
    public RelicSelector getSelector() { return selector; }   // what the caster must choose besides a seat
    public int getCost()        { return cost; }            // single source of truth for this card's price
    public RelicEffect getEffect() { return effect; }

    @Override
    public String toString() {
        return "RelicSpec[name=" + name + ", kind=" + kind + "]";
    }
}
