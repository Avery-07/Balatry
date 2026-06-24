package model.game.player;

import model.game.scoring.HandType;

/** Per-player hand levels (each starts at level 1), raised by Planet cards; lives beside a {@link Run} like {@link PlayerStats}. */
public final class HandLevels {

    private int flushFive     = 1;
    private int flushHouse    = 1;
    private int fiveOfAKind   = 1;
    private int straightFlush = 1;
    private int fourOfAKind   = 1;
    private int fullHouse     = 1;
    private int flush         = 1;
    private int straight      = 1;
    private int threeOfAKind  = 1;
    private int twoPair       = 1;
    private int pair          = 1;
    private int highCard      = 1;

    /** Current level of {@code type} (>= 1). */
    public int levelOf(HandType type) {
        return switch (type) {
            case FLUSH_FIVE      -> flushFive;
            case FLUSH_HOUSE     -> flushHouse;
            case FIVE_OF_A_KIND  -> fiveOfAKind;
            case STRAIGHT_FLUSH  -> straightFlush;
            case FOUR_OF_A_KIND  -> fourOfAKind;
            case FULL_HOUSE      -> fullHouse;
            case FLUSH           -> flush;
            case STRAIGHT        -> straight;
            case THREE_OF_A_KIND -> threeOfAKind;
            case TWO_PAIR        -> twoPair;
            case PAIR            -> pair;
            case HIGH_CARD       -> highCard;
        };
    }

    /** Raises {@code type} by one level and returns the new level. */
    public int levelUp(HandType type) {
        return switch (type) {
            case FLUSH_FIVE      -> ++flushFive;
            case FLUSH_HOUSE     -> ++flushHouse;
            case FIVE_OF_A_KIND  -> ++fiveOfAKind;
            case STRAIGHT_FLUSH  -> ++straightFlush;
            case FOUR_OF_A_KIND  -> ++fourOfAKind;
            case FULL_HOUSE      -> ++fullHouse;
            case FLUSH           -> ++flush;
            case STRAIGHT        -> ++straight;
            case THREE_OF_A_KIND -> ++threeOfAKind;
            case TWO_PAIR        -> ++twoPair;
            case PAIR            -> ++pair;
            case HIGH_CARD       -> ++highCard;
        };
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