package model.cards.consumables;

import model.game.scoring.HandType;

/** The twelve Planet cards; each levels its hand type when used. Per-level values live on {@link HandType}. */
public enum Planets {
    PLUTO    ("Pluto",    HandType.HIGH_CARD),
    MERCURY  ("Mercury",  HandType.PAIR),
    URANUS   ("Uranus",   HandType.TWO_PAIR),
    VENUS    ("Venus",    HandType.THREE_OF_A_KIND),
    SATURN   ("Saturn",   HandType.STRAIGHT),
    JUPITER  ("Jupiter",  HandType.FLUSH),
    EARTH    ("Earth",    HandType.FULL_HOUSE),
    MARS     ("Mars",     HandType.FOUR_OF_A_KIND),
    NEPTUNE  ("Neptune",  HandType.STRAIGHT_FLUSH),
    PLANET_X ("Planet X", HandType.FIVE_OF_A_KIND),
    CERES    ("Ceres",    HandType.FLUSH_HOUSE),
    ERIS     ("Eris",     HandType.FLUSH_FIVE);

    private static final int COST = 3;

    private final HandType hand;
    private final ConsumableSpec spec;

    Planets(String displayName, HandType hand) {
        this.hand = hand;
        this.spec = new ConsumableSpec(displayName, ConsumableType.PLANET,
                "Levels up " + hand, (run, self) -> run.levelUpHand(hand));
    }

    public HandType hand()       { return hand; }
    public ConsumableSpec spec() { return spec; }

    /** A fresh card for this planet at its shop price. */
    public ConsumableCard make() { return new ConsumableCard(spec, COST); }
}