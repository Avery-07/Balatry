package model.game.scoring;

import model.items.DeckCard;
import model.items.DeckCard.Rank;
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
    private final int straightGap;    // extra rank a straight may skip per step: 0 vanilla, 1 for Shortcut
    private final boolean smeared;    // Smeared Joker: red suits count as one, black suits as one, for a flush
    private final boolean splash;     // Splash: every played card scores, not just the ones forming the hand
    private final boolean dyscalculia; // Dyscalculie: every card also counts as the rank above (Ace as 2), for classification

    /** Vanilla evaluator: flush and straight each need five cards, no gaps, distinct suits, only the hand scores. */
    public HandEvaluator() { this(5, 5); }

    /** Threshold-only variant (Four Fingers lowers both to 4); no gaps, no smearing, no splash. */
    public HandEvaluator(int flushSize, int straightSize) { this(flushSize, straightSize, 0, false, false); }

    /**
     * An evaluator configured by the hand-shaping joker traits — the one place the mapping from traits to options
     * lives, so the model's real scoring ({@code Round}) and the client's play preview ({@code Hud}) agree.
     */
    public static HandEvaluator forTraits(boolean fourFingers, boolean shortcut, boolean smeared, boolean splash, boolean dyscalculia) {
        return new HandEvaluator(fourFingers ? 4 : 5, fourFingers ? 4 : 5, shortcut ? 1 : 0, smeared, splash, dyscalculia);
    }

    public HandEvaluator(int flushSize, int straightSize, int straightGap, boolean smeared, boolean splash) {
        this(flushSize, straightSize, straightGap, smeared, splash, false);
    }

    /** Full options, derived from the run's jokers by the caller — see {@link model.game.player.Run#hasActiveTrait}. */
    public HandEvaluator(int flushSize, int straightSize, int straightGap, boolean smeared, boolean splash, boolean dyscalculia) {
        if (flushSize < 1 || straightSize < 1)
            throw new IllegalArgumentException("thresholds must be >= 1");
        if (straightGap < 0) throw new IllegalArgumentException("straightGap must be >= 0");
        this.flushSize = flushSize;
        this.straightSize = straightSize;
        this.straightGap = straightGap;
        this.smeared = smeared;
        this.splash = splash;
        this.dyscalculia = dyscalculia;
    }

    /** Evaluates the played cards (1-5 in vanilla). Stone cards are ignored for classification but always score. */
    public HandEvaluation evaluate(List<DeckCard> played) {
        if (played == null || played.isEmpty())
            throw new IllegalArgumentException("a hand needs at least one card");

        List<DeckCard> live = new ArrayList<>();
        for (DeckCard c : played) if (!isStone(c)) live.add(c);

        boolean flush = hasFlush(live);   // suit-based, so independent of any rank re-reading
        Best best = dyscalculia ? bestDyscalculia(live, flush) : bestByRealRank(live, flush);

        HandContext context = context(best.type, best.sizes, flush, best.straight, played.size());

        List<DeckCard> scoring = new ArrayList<>();
        for (DeckCard c : played) if (splash || isStone(c) || best.selected.contains(c)) scoring.add(c);   // Splash: every card scores

        return new HandEvaluation(scoring, context);
    }

    /** The chosen classification of a hand: its type, group-size profile, whether it runs, and the live cards that score. */
    private record Best(HandType type, int[] sizes, boolean straight, Set<DeckCard> selected) { }

    /** Vanilla classification: group the cards by their real rank. */
    private Best bestByRealRank(List<DeckCard> live, boolean flush) {
        Map<Rank, List<DeckCard>> groups = groupByRank(live);
        int[] sizes = groupSizesDescending(groups);
        boolean straight = hasStraight(live);
        HandType type = classify(sizes, flush, straight);
        return new Best(type, sizes, straight, selectLive(type, live, groups));
    }

    /**
     * Dyscalculie: every card may be read as its own rank or the rank above (Ace as 2). Each assignment is one
     * concrete hand, so try all of them (at most 2^5) and keep the best-ranking — correct by construction, and
     * every card still keeps its real rank for chip value (this shifts classification, not scoring amounts).
     */
    private Best bestDyscalculia(List<DeckCard> live, boolean flush) {
        int n = live.size();
        Best best = null;
        for (int mask = 0; mask < (1 << n); mask++) {
            Map<Rank, List<DeckCard>> groups = new LinkedHashMap<>();
            java.util.TreeSet<Integer> values = new java.util.TreeSet<>();
            boolean hasAce = false;
            for (int i = 0; i < n; i++) {
                DeckCard c = live.get(i);
                Rank above = c.getRank().numberedAbove();   // null for face cards and Ten — they never shift
                Rank r = (((mask >> i) & 1) == 1 && above != null) ? above : c.getRank();
                groups.computeIfAbsent(r, k -> new ArrayList<>()).add(c);
                int v = sequence(r);
                values.add(v);
                if (v == 14) hasAce = true;
            }
            if (hasAce) values.add(1);   // an assigned Ace can still play the low wheel
            int[] sizes = groupSizesDescending(groups);
            boolean straight = straightInValues(values);
            HandType type = classify(sizes, flush, straight);
            if (best == null || type.ordinal() < best.type.ordinal())
                best = new Best(type, sizes, straight, selectLive(type, live, groups));
        }
        return best;
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

    /** True if any one suit reaches {@link #flushSize}; WILD counts toward every suit. Smeared merges the two colours. */
    private boolean hasFlush(List<DeckCard> live) {
        if (smeared) {   // Smeared Joker: hearts+diamonds are one suit, spades+clubs are one suit
            int red = 0, black = 0;
            for (DeckCard card : live) {
                if (card.isHeart() || card.isDiamond()) red++;
                if (card.isSpade() || card.isClub())    black++;
            }
            return red >= flushSize || black >= flushSize;
        }
        int spades = 0, hearts = 0, clubs = 0, diamonds = 0;
        for (DeckCard card : live) {
            if (card.isSpade())   spades++;
            if (card.isHeart())   hearts++;
            if (card.isClub())    clubs++;
            if (card.isDiamond()) diamonds++;
        }
        return spades >= flushSize || hearts >= flushSize || clubs >= flushSize || diamonds >= flushSize;
    }

    /**
     * True if {@link #straightSize} cards form a run; Ace plays high or low. Each step normally advances one rank,
     * but {@link #straightGap} lets it skip that many extra ranks per step (Shortcut allows a single-rank gap).
     */
    private boolean hasStraight(List<DeckCard> live) {
        if (live.size() < straightSize) return false;

        java.util.TreeSet<Integer> values = new java.util.TreeSet<>();
        boolean hasAce = false;
        for (DeckCard card : live) {
            int v = sequence(card.getRank());
            values.add(v);
            if (v == 14) hasAce = true;
        }
        if (hasAce) values.add(1);   // Ace-low wheel: A-2-3-4-5
        return straightInValues(values);
    }

    /** Whether the sorted distinct straight-values contain a run of {@link #straightSize}; a step of 1..(1+gap) extends it. */
    private boolean straightInValues(java.util.SortedSet<Integer> values) {
        int maxStep = 1 + straightGap, runLen = 1, prev = Integer.MIN_VALUE;
        for (int v : values) {
            int diff = v - prev;
            if (prev != Integer.MIN_VALUE && diff >= 1 && diff <= maxStep) runLen++;
            else runLen = 1;
            if (runLen >= straightSize) return true;
            prev = v;
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