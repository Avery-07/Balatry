package model.cards.jokers;

import model.cards.DeckCard;
import model.cards.DeckCard.Rank;
import model.cards.consumables.ConsumableCard;
import model.game.rng.RngSource;
import model.game.scoring.ScoringSession;
import model.game.scoring.Trigger;
import model.modifiers.Enhancement;
import model.modifiers.Seal;
import model.modifiers.Sticker;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * A representative slice of the joker catalog, exercising each effect pattern:
 * independent mult, per-scored-card conditions, hand-type containment, played-card count, and run-state reads.
 * The full 150-joker catalog fills in here behind the same registry.
 */
public enum Jokers {

    // Jokers 1-5
    JOKER("Joker", Rarity.COMMON, 2, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> run.getScoring().addMult(4))),
    GREEDY_JOKER("Greedy Joker", Rarity.COMMON, 5, b -> b.on(Trigger.ON_SCORED_CARD,
            (run, self) -> { if (isSuit(run.getScoring(), Suit.DIAMOND)) run.getScoring().addMult(3); })),
    LUSTY_JOKER("Lusty Joker", Rarity.COMMON, 5, b -> b.on(Trigger.ON_SCORED_CARD,
            (run, self) -> { if (isSuit(run.getScoring(), Suit.HEART)) run.getScoring().addMult(3); })),
    WRATHFUL_JOKER("Wrathful Joker", Rarity.COMMON, 5, b -> b.on(Trigger.ON_SCORED_CARD,
            (run, self) -> { if (isSuit(run.getScoring(), Suit.SPADE)) run.getScoring().addMult(3); })),
    GLUTTONOUS_JOKER("Gluttonous Joker", Rarity.COMMON, 5, b -> b.on(Trigger.ON_SCORED_CARD,
            (run, self) -> { if (isSuit(run.getScoring(), Suit.CLUB)) run.getScoring().addMult(3); })),
    // Jokers 6-10
    JOLLY_JOKER("Jolly Joker", Rarity.COMMON, 3, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> { if (run.getScoring().getHand().hasPair()) run.getScoring().addMult(8); })),
    ZANY_JOKER("Zany Joker", Rarity.COMMON, 4, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> { if (run.getScoring().getHand().hasThreeOfAKind()) run.getScoring().addMult(12); })),
    MAD_JOKER("Mad Joker", Rarity.COMMON, 4, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> { if (run.getScoring().getHand().hasTwoPair()) run.getScoring().addMult(10); })),
    CRAZY_JOKER("Crazy Joker", Rarity.COMMON, 4, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> { if (run.getScoring().getHand().hasStraight()) run.getScoring().addMult(12); })),
    DROLL_JOKER("Droll Joker", Rarity.COMMON, 4, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> { if (run.getScoring().getHand().hasFlush()) run.getScoring().addMult(10); })),
    // Jokers 11-15
    SLY_JOKER("Sly Joker", Rarity.COMMON, 3, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> { if (run.getScoring().getHand().hasPair()) run.getScoring().addChips(50); })),
    WILY_JOKER("Wily Joker", Rarity.COMMON, 4, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> { if (run.getScoring().getHand().hasThreeOfAKind()) run.getScoring().addChips(100); })),
    CLEVER_JOKER("Clever Joker", Rarity.COMMON, 4, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> { if (run.getScoring().getHand().hasTwoPair()) run.getScoring().addChips(80); })),
    DEVIOUS_JOKER("Devious Joker", Rarity.COMMON, 4, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> { if (run.getScoring().getHand().hasStraight()) run.getScoring().addChips(100); })),
    CRAFTY_JOKER("Crafty Joker", Rarity.COMMON, 4, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> { if (run.getScoring().getHand().hasFlush()) run.getScoring().addChips(80); })),
    // Jokers 16-20
    HALF_JOKER("Half Joker", Rarity.COMMON, 5, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> { if (run.getScoring().getHand().playedCount() <= 3) run.getScoring().addMult(20); })),
    JOKER_STENCIL("Joker Stencil", Rarity.UNCOMMON, 8, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> {
                int jokers = 0;
                for (JokerCard j : run.getJokers()) if (j.getSpec() != self.getSpec()) jokers++;
                int empty = run.getJokerSlots() - jokers;
                if (empty > 0) run.getScoring().multiplyMult(BigDecimal.valueOf(empty));
            })),
    //FOUR_FINGERS("", , , ), //To Be Implemented
    MIME("Mime", Rarity.UNCOMMON, 5, b -> b.retriggerHeld((run, self, card) -> 1)),
    CREDIT_CARD("Credit Card", Rarity.COMMON, 2, b -> b
            .debtAllowance(20)
            .on(Trigger.ON_SPEND, (run, self) -> self.setCounter(self.getCounter() + run.getLastInDebtSpend()))
            .on(Trigger.ON_EARN, (run, self) -> { if (run.getMoney() >= 0) self.setCounter(0); })
            .on(Trigger.ON_HAND_PLAYED, (run, self) -> run.getScoring().addMult(self.getCounter()))),
    // Jokers 21-25
    CEREMONIAL_DAGGER("Ceremonial Dagger", Rarity.UNCOMMON, 6, b -> b
            .on(Trigger.ON_ROUND_START, (run, self) -> {
                List<JokerCard> jokers = run.getJokers();
                int i = jokers.indexOf(self);
                if (i >= 0 && i + 1 < jokers.size()) {
                    JokerCard right = jokers.get(i + 1);
                    if (!right.hasSticker(Sticker.ETERNAL)) {
                        self.setCounter(self.getCounter() + 3 * right.getSellValue());
                        jokers.remove(i + 1);
                    }
                }
            })
            .on(Trigger.ON_HAND_PLAYED, (run, self) -> run.getScoring().addMult(self.getCounter()))),
    BANNER("Banner", Rarity.COMMON, 5, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> { if (run.getRound() != null) run.getScoring().addChips(30L * run.getRound().getDiscardsRemaining()); })),
    MYSTIC_SUMMIT("Mystic Summit", Rarity.COMMON, 5, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> {
                if (run.getRound() != null && run.getRound().getDiscardsRemaining() == 0)
                    run.getScoring().addMult(15);
            })),
    MARBLE_JOKER("Marble Joker", Rarity.UNCOMMON, 6, b -> b.on(Trigger.ON_ROUND_START,
            (run, self) -> {
                DeckCard stone = new DeckCard(Rank.ACE, DeckCard.Suit.SPADES);
                stone.apply(Enhancement.STONE);
                long salt = run.getDeck().size();
                if (run.getRng().chance(RngSource.MISC, salt, 1, 2)) {
                    Seal[] seals = Seal.values();
                    stone.apply(seals[run.getRng().nextInt(RngSource.CARD_SEAL, salt, seals.length)]);
                }
                run.getDeck().add(stone);
            })),
    LOYALTY_CARD("Loyalty Card", Rarity.UNCOMMON, 5, b -> b.on(Trigger.ON_BOUGHT,
            (run, self) -> {
                self.setCounter(self.getCounter() + 1);
                if (self.getCounter() % 4 == 0) run.makePurchaseFree();
            })),
    // Jokers 26-30
    EIGHT_BALL("8_Ball", Rarity.COMMON, 5, b -> b.on(Trigger.ON_SCORED_CARD,
            (run, self) -> {
                if(run.getScoring().getCurrentScoredCard().getRank() == Rank.EIGHT) {
                    long salt = run.getDeck().size();
                    if(run.getRng().chance(RngSource.MISC, salt, 1, 4)) {
                        // Must create tarot cards to finish implementation
                    }
                }
            })),
    MISPRINT("Misprint", Rarity.COMMON, 5, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> {
            long salt = run.getDeck().size();
            run.getScoring().addMult((int) (run.getRng().nextDouble(RngSource.MISC, salt) * 16));
                run.getScoring().multiplyMult((long) (run.getRng().nextDouble(RngSource.MISC, salt) * 1.26 + 0.75));
            })),
    // Jokers 31-35
    SCARY_FACE("Scary Face", Rarity.COMMON, 4, b -> b.on(Trigger.ON_SCORED_CARD,
            (run, self) -> { DeckCard c = scored(run); if (c != null && c.isFace()) run.getScoring().addChips(30); })),
    ABSTRACT_JOKER("Abstract Joker", Rarity.COMMON, 4, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> run.getScoring().addMult(3L * run.getJokers().size()))),
    // Jokers 36-40
    EVEN_STEVEN("Even Steven", Rarity.COMMON, 4, b -> b.on(Trigger.ON_SCORED_CARD,
            (run, self) -> { DeckCard c = scored(run); if (c != null && isEven(c.getRank())) run.getScoring().addMult(4); })),
    ODD_TODD("Odd Todd", Rarity.COMMON, 4, b -> b.on(Trigger.ON_SCORED_CARD,
            (run, self) -> { DeckCard c = scored(run); if (c != null && isOdd(c.getRank())) run.getScoring().addChips(31); })),



    GREEN_JOKER("Green Joker", Rarity.COMMON, 4, b -> b
            .on(Trigger.ON_HAND_PLAYED,    (run, self) -> {
                self.addCounter(1);
                run.getScoring().addMult(self.getCounter());
            })
            .on(Trigger.ON_HAND_DISCARDED, (run, self) ->
                    self.setCounter(Math.max(0, self.getCounter() - 1))));



    private final Rarity rarity;
    private final JokerSpec spec;

    Jokers(String displayName, Rarity rarity, int cost, UnaryOperator<JokerSpec.Builder> define) {
        this.rarity = rarity;
        this.spec = define.apply(JokerSpec.named(displayName, rarity).cost(cost)).build();
    }

    public Rarity rarity()  { return rarity; }
    public JokerSpec spec() { return spec; }

    // rarity split for shop/pack draws (cumulative, out of 100): Common 70 / Uncommon 25 / Rare 5
    private static final int COMMON_WEIGHT = 70, UNCOMMON_WEIGHT = 95;

    /** A random joker of {@code rarity}; falls back to a rarity-weighted pick while that bucket is unimplemented. */
    public static Jokers randomOfRarity(Rarity rarity, java.util.random.RandomGenerator stream) {
        Jokers[] all = values();
        int matching = 0;
        for (Jokers j : all) if (j.rarity() == rarity) matching++;
        if (matching == 0) return weightedRandom(stream);
        int nth = stream.nextInt(matching);
        for (Jokers j : all) if (j.rarity() == rarity && nth-- == 0) return j;
        return all[0];   // unreachable
    }

    /** A random joker weighted by rarity; falls back to any joker while a rarity bucket is unimplemented. */
    public static Jokers weightedRandom(java.util.random.RandomGenerator stream) {
        int r = stream.nextInt(100);
        Rarity target = r < COMMON_WEIGHT ? Rarity.COMMON : r < UNCOMMON_WEIGHT ? Rarity.UNCOMMON : Rarity.RARE;
        Jokers[] all = values();
        int matching = 0;
        for (Jokers j : all) if (j.rarity() == target) matching++;
        if (matching == 0) return all[stream.nextInt(all.length)];
        int nth = stream.nextInt(matching);
        for (Jokers j : all) if (j.rarity() == target && nth-- == 0) return j;
        return all[0];   // unreachable
    }

    /** A fresh card for this joker at its shop price. */
    public JokerCard make() { return new JokerCard(spec); }

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