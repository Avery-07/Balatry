package model.cards.jokers;

import model.game.scoring.Trigger;

import java.util.EnumMap;
import java.util.Map;

public final class JokerSpec {
    private final String name;
    private final Rarity rarity;
    private final Map<Trigger, JokerEffect> effects;

    private JokerSpec(String name, Rarity rarity, Map<Trigger, JokerEffect> effects) {
        this.name = name;
        this.rarity = rarity;
        this.effects = Map.copyOf(effects);
    }

    public JokerEffect effectFor(Trigger t) {
        return effects.getOrDefault(t, JokerEffect.NO_OP);
    }

    public static Builder named(String name, Rarity rarity) {
        return new Builder(name, rarity);
    }

    public static final class Builder {
        private final String name;
        private final Rarity rarity;
        private final Map<Trigger, JokerEffect> effects = new EnumMap<>(Trigger.class);
        private Builder(String name, Rarity rarity) { this.name = name; this.rarity = rarity; }
        public Builder on(Trigger t, JokerEffect e) { effects.put(t, e); return this; }
        public JokerSpec build() { return new JokerSpec(name, rarity, effects); }
    }

    public String getName() { return name; }
    public Rarity getRarity() { return rarity; }
    public Map<Trigger, JokerEffect> getEffects() { return effects; }

    @Override
    public String toString() {
        return "JokerSpec[name=" + name + ", rarity=" + rarity + "]";
    }
}