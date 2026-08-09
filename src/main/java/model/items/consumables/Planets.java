package model.items.consumables;

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
        // Spell out exactly what a level buys for this hand, the way Balatro's planet cards do: its own per-level
        // Mult and Chips (pulled from HandType so the text can never drift from the numbers that actually apply).
        String desc = "Levels up " + hand.displayName() + ": +" + hand.getMultPerLevel() + " Mult and +"
                + hand.getChipsPerLevel() + " Chips per level.";
        this.spec = new ConsumableSpec(displayName, ConsumableType.PLANET, COST, desc,
                (run, self) -> run.levelUpHand(hand));
    }

    public HandType hand()       { return hand; }
    public ConsumableSpec spec() { return spec; }

    /** A fresh card for this planet at its spec's price. */
    public ConsumableCard make() { return new ConsumableCard(spec); }

    /** A uniformly random planet (planets carry no appearance weights). */
    public static Planets random(java.util.random.RandomGenerator stream) {
        Planets[] all = values();
        return all[stream.nextInt(all.length)];
    }

    /** The planet that levels {@code hand}, or {@code null} if none does (Telescope / Observatory look-up). */
    public static Planets forHand(HandType hand) {
        for (Planets p : values()) if (p.hand == hand) return p;
        return null;
    }

    /** The planet backing a held consumable {@code spec}, or {@code null} if it is not a Planet (Observatory look-up). */
    public static Planets forSpec(ConsumableSpec spec) {
        for (Planets p : values()) if (p.spec == spec) return p;
        return null;
    }
}