package model.cards.jokers;

import model.cards.DeckCard;
import model.cards.DeckCard.Rank;
import model.game.scoring.ScoringSession;
import model.game.scoring.Trigger;

import java.util.function.UnaryOperator;

/**
 * A representative slice of the joker catalog, exercising each effect pattern:
 * independent mult, per-scored-card conditions, hand-type containment, played-card count, and run-state reads.
 * The full 150-joker catalog fills in here behind the same registry.
 */
public enum Jokers {

    // --- independent: fire once per played hand (Trigger.ON_HAND_PLAYED) ---
    JOKER("Joker", Rarity.COMMON, 2, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> run.getScoring().addMult(4))),

    ABSTRACT_JOKER("Abstract Joker", Rarity.COMMON, 4, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> run.getScoring().addMult(3L * run.getJokers().size()))),

    HALF_JOKER("Half Joker", Rarity.COMMON, 5, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> { if (run.getScoring().getHand().playedCount() <= 3) run.getScoring().addMult(20); })),

    BANNER("Banner", Rarity.COMMON, 5, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> { if (run.getRound() != null) run.getScoring().addChips(30L * run.getRound().getDiscardsRemaining()); })),

    // --- hand-type containment (Trigger.ON_HAND_PLAYED) ---
    JOLLY_JOKER("Jolly Joker", Rarity.COMMON, 3, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> { if (run.getScoring().getHand().hasPair()) run.getScoring().addMult(8); })),

    SLY_JOKER("Sly Joker", Rarity.COMMON, 3, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> { if (run.getScoring().getHand().hasPair()) run.getScoring().addChips(50); })),

    DROLL_JOKER("Droll Joker", Rarity.COMMON, 4, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> { if (run.getScoring().getHand().hasFlush()) run.getScoring().addMult(10); })),

    CRAFTY_JOKER("Crafty Joker", Rarity.COMMON, 4, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> { if (run.getScoring().getHand().hasFlush()) run.getScoring().addChips(80); })),

    // --- per-scored-card by suit (Trigger.ON_SCORED_CARD) ---
    GREEDY_JOKER("Greedy Joker", Rarity.COMMON, 5, b -> b.on(Trigger.ON_SCORED_CARD,
            (run, self) -> { if (isSuit(run.getScoring(), Suit.DIAMOND)) run.getScoring().addMult(3); })),

    LUSTY_JOKER("Lusty Joker", Rarity.COMMON, 5, b -> b.on(Trigger.ON_SCORED_CARD,
            (run, self) -> { if (isSuit(run.getScoring(), Suit.HEART)) run.getScoring().addMult(3); })),

    WRATHFUL_JOKER("Wrathful Joker", Rarity.COMMON, 5, b -> b.on(Trigger.ON_SCORED_CARD,
            (run, self) -> { if (isSuit(run.getScoring(), Suit.SPADE)) run.getScoring().addMult(3); })),

    GLUTTONOUS_JOKER("Gluttonous Joker", Rarity.COMMON, 5, b -> b.on(Trigger.ON_SCORED_CARD,
            (run, self) -> { if (isSuit(run.getScoring(), Suit.CLUB)) run.getScoring().addMult(3); })),

    // --- per-scored-card by rank/face (Trigger.ON_SCORED_CARD) ---
    SCARY_FACE("Scary Face", Rarity.COMMON, 4, b -> b.on(Trigger.ON_SCORED_CARD,
            (run, self) -> { DeckCard c = scored(run); if (c != null && c.isFace()) run.getScoring().addChips(30); })),

    EVEN_STEVEN("Even Steven", Rarity.COMMON, 4, b -> b.on(Trigger.ON_SCORED_CARD,
            (run, self) -> { DeckCard c = scored(run); if (c != null && isEven(c.getRank())) run.getScoring().addMult(4); })),

    ODD_TODD("Odd Todd", Rarity.COMMON, 4, b -> b.on(Trigger.ON_SCORED_CARD,
            (run, self) -> { DeckCard c = scored(run); if (c != null && isOdd(c.getRank())) run.getScoring().addChips(31); }));

    private final Rarity rarity;
    private final int cost;
    private final JokerSpec spec;

    Jokers(String displayName, Rarity rarity, int cost, UnaryOperator<JokerSpec.Builder> define) {
        this.rarity = rarity;
        this.cost = cost;
        this.spec = define.apply(JokerSpec.named(displayName, rarity)).build();
    }

    public Rarity rarity()  { return rarity; }
    public int cost()       { return cost; }
    public JokerSpec spec() { return spec; }

    /** A fresh card for this joker at its shop price. */
    public JokerCard make() { return new JokerCard(spec, cost); }

    // --- effect helpers ---

    private enum Suit { SPADE, HEART, CLUB, DIAMOND }

    private static DeckCard scored(model.game.player.Run run) {
        return run.getScoring().getCurrentScoredCard();
    }

    private static boolean isSuit(ScoringSession s, Suit suit) {
        DeckCard c = s.getCurrentScoredCard();
        if (c == null) return false;
        return switch (suit) {
            case SPADE   -> c.isSpade();
            case HEART   -> c.isHeart();
            case CLUB    -> c.isClub();
            case DIAMOND -> c.isDiamond();
        };
    }

    private static boolean isEven(Rank r) {
        return switch (r) { case TWO, FOUR, SIX, EIGHT, TEN -> true; default -> false; };
    }

    private static boolean isOdd(Rank r) {
        return switch (r) { case ACE, THREE, FIVE, SEVEN, NINE -> true; default -> false; };
    }
}