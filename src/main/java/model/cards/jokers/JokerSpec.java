package model.cards.jokerHelpers;

import java.util.EnumMap;
import java.util.Map;

public record JokerSpec(String name, Rarity rarity, Map<JokerTrigger, JokerEffect> effects) {
    public JokerSpec { effects = Map.copyOf(effects); }   // frozen defensive copy

    public JokerEffect effectFor(JokerTrigger t) {
        return effects.getOrDefault(t, JokerEffect.NO_OP);
    }

    public static JokerSpec.Builder named(String name, Rarity rarity) { return new JokerSpec.Builder(name, rarity); }

    public static final class Builder {
        private final String name;
        private final Rarity rarity;
        private final Map<JokerTrigger, JokerEffect> effects = new EnumMap<>(JokerTrigger.class);
        private Builder(String name, Rarity rarity) { this.name = name; this.rarity = rarity; }
        public JokerSpec.Builder on(JokerTrigger t, JokerEffect e) { effects.put(t, e); return this; }
        public JokerSpec build() { return new JokerSpec(name, rarity, effects); }
    }
}