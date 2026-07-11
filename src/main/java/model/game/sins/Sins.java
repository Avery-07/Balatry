package model.game.sins;

import model.game.Sin;

import java.util.EnumMap;
import java.util.Map;

/** The registry from a {@link Sin} to its {@link SinModifier} behaviour. */
public final class Sins {

    private static final Map<Sin, SinModifier> REGISTRY = new EnumMap<>(Sin.class);

    static {
        // Every sin starts at NONE; built sins are registered below.
        for (Sin sin : Sin.values()) REGISTRY.put(sin, SinModifier.NONE);
        register(Sin.PRIDE, new PrideModifier());
        register(Sin.WRATH, new WrathModifier());
        register(Sin.SLOTH, new SlothModifier());
        register(Sin.GLUTTONY, new GluttonyModifier());
        register(Sin.GREED, new GreedModifier());
        register(Sin.LUST, new LustModifier());
    }

    private Sins() { }

    /** The behaviour registered for {@code sin}, or {@link SinModifier#NONE} if none. */
    public static SinModifier modifierFor(Sin sin) {
        return REGISTRY.getOrDefault(sin, SinModifier.NONE);
    }

    /** Registers (or replaces) the behaviour for {@code sin}. Call once per built sin from this class's wiring. */
    static void register(Sin sin, SinModifier modifier) {
        REGISTRY.put(sin, modifier);
    }
}
