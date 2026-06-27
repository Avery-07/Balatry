package model.game.player;

import model.game.scoring.HandType;

import java.util.EnumMap;
import java.util.Map;

/** Per-player hand levels (each starts at level 1), raised by Planet cards; lives beside a {@link Run} like {@link PlayerStats}. */
public final class HandLevels {

    private final Map<HandType, Integer> levels = new EnumMap<>(HandType.class);

    /** Current level of {@code type} (>= 1). */
    public int levelOf(HandType type) {
        return levels.getOrDefault(type, 1);
    }

    /** Raises {@code type} by one level and returns the new level. */
    public int levelUp(HandType type) {
        int next = levelOf(type) + 1;
        levels.put(type, next);
        return next;
    }

    /** Chips {@code type} contributes as the hand's base, at its current level. */
    public long chipsFor(HandType type) {
        return type.getBaseChips() + (long) (levelOf(type) - 1) * type.getChipsPerLevel();
    }

    /** Mult {@code type} contributes as the hand's base, at its current level. */
    public long multFor(HandType type) {
        return type.getBaseMult() + (long) (levelOf(type) - 1) * type.getMultPerLevel();
    }
}
