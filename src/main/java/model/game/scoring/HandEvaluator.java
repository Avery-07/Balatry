package model.game.scoring;

import model.cards.DeckCard;
import model.cards.DeckCard.Rank;
import model.modifiers.Enhancement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Classifies a played hand into a {@link HandType} and selects the cards that score. */
public final class HandEvaluator {

    private final int flushSize;
    private final int straightSize;

    /** Vanilla evaluator: flush and straight each need five cards. */
    public HandEvaluator() { this(5, 5); }

    /** Thresholds jokers will later vary (e.g. Four Fingers lowers both to 4). */
    public HandEvaluator(int flushSize, int straightSize) {
        if (flushSize < 1 || straightSize < 1)
            throw new IllegalArgumentException("thresholds must be >= 1");
        this.flushSize = flushSize;
        this.straightSize = straightSize;
    }

    /** Evaluates the played cards (1-5 in vanilla). Stone cards are ignored for classification but always score. */
    public HandEvaluation evaluate(List<DeckCard> played) {
        if (played == null || played.isEmpty())
            throw new IllegalArgumentException("a hand needs at least one card");

        List<DeckCard> live = new ArrayList<>();
        for (DeckCard c : played) if (!isStone(c)) live.add(c);

        Map<Rank, List<DeckCard>> groups = groupByRank(live);
        int[] sizes = groupSizesDescending(groups);    // e.g. {3,2} for a full house
        boolean flush = hasFlush(live);
        boolean straight = hasStraight(live);

        HandType type = classify(sizes, flush, straight);
        HandContext context = context(type, sizes, flush, straight, played.size());
        Set<DeckCard> selected = selectLive(type, live, groups);

        List<DeckCard> scoring = new ArrayList<>();
        for (DeckCard c : played) if (isStone(c) || selected.contains(c)) scoring.add(c);

        return new HandEvaluation(scoring, context);
    }

    /** Builds the trait view joker effects read during scoring. "Contains X" follows vanilla: a group of {@code n} satisfies every smaller-group query. */
    private static HandContext context(HandType type, int[] sizes, boolean flush, boolean straight, int playedCount) {
        int top = sizes.length > 0 ? sizes[0] : 0;
        int second = sizes.length > 1 ? sizes[1] : 0;
        return new HandContext(
                type,
                playedCount,
                top >= 2,                 // hasPair
                top >= 2 && second >= 2,  // hasTwoPair (full house qualifies)
                top >= 3,                 // hasThreeOfAKind
                top >= 4,                 // hasFourOfAKind
                straight,
                flush);
    }

    /** Resolves the highest-ranking hand the cards satisfy, in {@link HandType} order. */
    private HandType classify(int[] sizes, boolean flush, boolean straight) {
        int top = sizes.length > 0 ? sizes[0] : 0;
        int second = sizes.length > 1 ? sizes[1] : 0;
        boolean fullHouse = top >= 3 && second >= 2;

        if (top >= 5 && flush)       return HandType.FLUSH_FIVE;
        if (fullHouse && flush)      return HandType.FLUSH_HOUSE;
        if (top >= 5)                return HandType.FIVE_OF_A_KIND;
        if (straight && flush)       return HandType.STRAIGHT_FLUSH;
        if (top >= 4)                return HandType.FOUR_OF_A_KIND;
        if (fullHouse)               return HandType.FULL_HOUSE;
        if (flush)                   return HandType.FLUSH;
        if (straight)                return HandType.STRAIGHT;
        if (top >= 3)                return HandType.THREE_OF_A_KIND;
        if (top >= 2 && second >= 2) return HandType.TWO_PAIR;
        if (top >= 2)                return HandType.PAIR;
        return HandType.HIGH_CARD;
    }

    /** The live cards that score for this hand type; stones are added by the caller. */
    private Set<DeckCard> selectLive(HandType type, List<DeckCard> live, Map<Rank, List<DeckCard>> groups) {
        Set<DeckCard> set = new HashSet<>();
        switch (type) {
            // Whole-hand types: every live card scores. With reduced thresholds (Four Fingers/Shortcut)
            // this should narrow to the qualifying cards; vanilla plays exactly the hand, so all score.
            case FLUSH_FIVE, FLUSH_HOUSE, FIVE_OF_A_KIND, STRAIGHT_FLUSH, FULL_HOUSE, FLUSH, STRAIGHT ->
                    set.addAll(live);
            case FOUR_OF_A_KIND, THREE_OF_A_KIND, PAIR ->
                    set.addAll(largestGroup(groups));
            case TWO_PAIR -> {
                for (List<DeckCard> g : groups.values()) if (g.size() >= 2) set.addAll(g);
            }
            case HIGH_CARD -> {
                DeckCard high = highest(live);
                if (high != null) set.add(high);
            }
        }
        return set;
    }

    /** True if any one suit reaches {@link #flushSize}; WILD counts toward every suit. */
    private boolean hasFlush(List<DeckCard> live) {
        int spades = 0, hearts = 0, clubs = 0, diamonds = 0;
        for (DeckCard card : live) {
            if (card.isSpade())   spades++;
            if (card.isHeart())   hearts++;
            if (card.isClub())    clubs++;
            if (card.isDiamond()) diamonds++;
        }
        return spades >= flushSize || hearts >= flushSize || clubs >= flushSize || diamonds >= flushSize;
    }

    /** True if {@link #straightSize} cards form a consecutive run; Ace plays high or low. */
    private boolean hasStraight(List<DeckCard> live) {
        if (live.size() < straightSize) return false;

        Set<Integer> values = new HashSet<>();
        boolean hasAce = false;
        for (DeckCard card : live) {
            int v = sequence(card.getRank());
            values.add(v);
            if (v == 14) hasAce = true;
        }
        if (hasAce) values.add(1);   // Ace-low wheel: A-2-3-4-5

        for (int start : values) {
            boolean run = true;
            for (int k = 1; k < straightSize; k++) {
                if (!values.contains(start + k)) { run = false; break; }
            }
            if (run) return true;
        }
        return false;
    }

    private static Map<Rank, List<DeckCard>> groupByRank(List<DeckCard> live) {
        Map<Rank, List<DeckCard>> groups = new LinkedHashMap<>();
        for (DeckCard card : live) groups.computeIfAbsent(card.getRank(), r -> new ArrayList<>()).add(card);
        return groups;
    }

    private static int[] groupSizesDescending(Map<Rank, List<DeckCard>> groups) {
        int[] sizes = groups.values().stream().mapToInt(List::size).toArray();
        Arrays.sort(sizes);
        for (int i = 0, j = sizes.length - 1; i < j; i++, j--) {
            int tmp = sizes[i]; sizes[i] = sizes[j]; sizes[j] = tmp;
        }
        return sizes;
    }

    private static List<DeckCard> largestGroup(Map<Rank, List<DeckCard>> groups) {
        List<DeckCard> best = List.of();
        for (List<DeckCard> g : groups.values()) if (g.size() > best.size()) best = g;
        return best;
    }

    private static DeckCard highest(List<DeckCard> live) {
        DeckCard best = null;
        for (DeckCard card : live)
            if (best == null || sequence(card.getRank()) > sequence(best.getRank())) best = card;
        return best;
    }

    private static boolean isStone(DeckCard card) { return card.getEnhancement() == Enhancement.STONE; }

    /** Straight-ordering value for a rank (Ace high = 14; the wheel handles Ace low separately). */
    private static int sequence(Rank rank) {
        return switch (rank) {
            case TWO -> 2;   case THREE -> 3; case FOUR -> 4;  case FIVE -> 5;
            case SIX -> 6;   case SEVEN -> 7; case EIGHT -> 8; case NINE -> 9;
            case TEN -> 10;  case JACK -> 11; case QUEEN -> 12; case KING -> 13; case ACE -> 14;
        };
    }
}