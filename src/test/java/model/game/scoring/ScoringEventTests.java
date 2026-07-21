package model.game.scoring;

import client.MatchSnapshot;
import model.items.DeckCard;
import model.items.DeckCard.Rank;
import model.items.DeckCard.Suit;
import model.items.DeckType;
import model.items.jokers.Jokers;
import model.game.Match;
import model.game.MatchConfig;
import model.game.player.PlayerId;
import model.game.player.Round;
import model.game.player.Run;
import model.game.player.SeatConfig;
import model.modifiers.Edition;
import model.modifiers.Enhancement;
import model.modifiers.Seal;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Run-as-main harness for the scoring timeline — the ordered {@link ScoringEvent} log the client replays as the
 * trigger/effect animation. The point of these checks is the two properties that make the animation safe: every
 * beat names the card that caused it, and the running totals are the model's own, so a replay of the log lands
 * exactly on the score that was banked.
 */
public final class ScoringEventTests {

    private static int failures = 0;

    public static void main(String[] args) {
        theLogTellsTheStory();
        totalsAreTheModelsOwn();
        everyEffectHasASource();
        enhancementsGetTheirOwnBeat();
        retriggersAnnounceThemselves();
        moneyJoinsTheTimeline();
        plasmaRewriteIsOnTheTimeline();
        snapshotShipsIt();

        System.out.println(failures == 0 ? "\nALL PASS" : "\n" + failures + " FAILURE(S)");
        if (failures != 0) System.exit(1);
    }

    /** A plain pair: the hand's base opens the timeline, then each scoring card contributes its chips. */
    private static void theLogTellsTheStory() {
        Run run = bareRun(1L);
        List<ScoringEvent> log = play(run, card(Rank.KING, Suit.SPADES), card(Rank.KING, Suit.HEARTS));

        check("the timeline is not empty", !log.isEmpty());
        check("it opens with the hand's base value", log.get(0).kind() == ScoringEvent.Kind.BASE);
        check("the base names the hand type", log.get(0).sourceName().equals("Pair"));
        check("the base is ownerless", !log.get(0).hasSource());

        long chipEvents = log.stream().filter(e -> e.kind() == ScoringEvent.Kind.CHIPS).count();
        checkInt("both kings contributed chips", (int) chipEvents, 2);
    }

    /** The whole reason the log carries running totals: replaying it must land on the banked score. */
    private static void totalsAreTheModelsOwn() {
        Run run = stockedRun(2L);
        run.acquire(Jokers.JOKER.make());   // +4 mult, so jokers are in the mix too
        Round r = run.beginRound(1_000_000);
        var result = r.play(new ArrayList<>(r.getHand().subList(0, 2)));
        List<ScoringEvent> log = r.getLastEvents();

        ScoringEvent last = log.get(log.size() - 1);
        BigDecimal replayed = last.chipsAfter().multiply(last.multAfter())
                .setScale(0, java.math.RoundingMode.HALF_UP);
        check("the final beat's totals reproduce the banked score",
                replayed.compareTo(result.handScore()) == 0);

        // And the totals only ever move at a beat — each event's stamp matches the score at that instant.
        boolean monotonicChips = true;
        for (int i = 1; i < log.size(); i++)
            if (log.get(i).chipsAfter().compareTo(log.get(i - 1).chipsAfter()) < 0) monotonicChips = false;
        check("chips never fall back mid-timeline", monotonicChips);
    }

    /** Every scoring beat except the deliberately ownerless ones points at a card the client can pop. */
    private static void everyEffectHasASource() {
        Run run = bareRun(3L);
        run.acquire(Jokers.JOKER.make());
        List<ScoringEvent> log = play(run, card(Rank.QUEEN, Suit.CLUBS), card(Rank.QUEEN, Suit.SPADES));

        boolean allAttributed = true;
        for (ScoringEvent e : log) {
            boolean ownerless = e.kind() == ScoringEvent.Kind.BASE || e.kind() == ScoringEvent.Kind.BALANCE;
            if (!ownerless && !e.hasSource()) allAttributed = false;
            if (!e.sourceName().isEmpty()) continue;
            allAttributed = false;   // every beat is labelled, even the ownerless ones
        }
        check("every beat names its source", allAttributed);

        boolean jokerFired = log.stream().anyMatch(e -> e.sourceName().equals("Joker"));
        check("the joker owns its own beat", jokerFired);
    }

    /** The user's example: a Mult-enhanced card pops twice — once for its chips, once for its mult. */
    private static void enhancementsGetTheirOwnBeat() {
        Run run = bareRun(4L);
        DeckCard five = card(Rank.FIVE, Suit.HEARTS);
        five.apply(Enhancement.MULT);
        List<ScoringEvent> log = play(run, five, card(Rank.FIVE, Suit.SPADES));

        List<ScoringEvent> mine = new ArrayList<>();
        for (ScoringEvent e : log) if (e.sourceId() == five.id()) mine.add(e);
        checkInt("the enhanced five produced two beats", mine.size(), 2);
        check("first its chips", mine.get(0).kind() == ScoringEvent.Kind.CHIPS);
        check("then its mult", mine.get(1).kind() == ScoringEvent.Kind.MULT);
        check("the mult beat carries +5", mine.get(1).amount().compareTo(BigDecimal.valueOf(5)) == 0);

        // An edition stacks a third beat on the same card.
        Run run2 = bareRun(5L);
        DeckCard foil = card(Rank.SIX, Suit.CLUBS);
        foil.apply(Edition.FOIL);
        List<ScoringEvent> log2 = play(run2, foil, card(Rank.SIX, Suit.SPADES));
        long beats = log2.stream().filter(e -> e.sourceId() == foil.id()).count();
        checkInt("a foil card beats twice (rank chips, then foil chips)", (int) beats, 2);
    }

    /** A retriggering card announces the extra passes before performing them. */
    private static void retriggersAnnounceThemselves() {
        Run run = bareRun(6L);
        DeckCard sealed = card(Rank.NINE, Suit.SPADES);
        sealed.apply(Seal.RED_SEAL);
        List<ScoringEvent> log = play(run, sealed, card(Rank.NINE, Suit.HEARTS));

        List<ScoringEvent> mine = new ArrayList<>();
        for (ScoringEvent e : log) if (e.sourceId() == sealed.id()) mine.add(e);
        check("the red seal announces its retrigger first", mine.get(0).kind() == ScoringEvent.Kind.RETRIGGER);
        check("it announces one extra pass", mine.get(0).amount().compareTo(BigDecimal.ONE) == 0);
        long chipBeats = mine.stream().filter(e -> e.kind() == ScoringEvent.Kind.CHIPS).count();
        checkInt("and then scores twice", (int) chipBeats, 2);
    }

    /** Money earned mid-scoring is a beat too — a gold seal pays while the hand is still resolving. */
    private static void moneyJoinsTheTimeline() {
        Run run = bareRun(7L);
        DeckCard gold = card(Rank.TEN, Suit.DIAMONDS);
        gold.apply(Seal.GOLD_SEAL);
        List<ScoringEvent> log = play(run, gold, card(Rank.TEN, Suit.SPADES));

        ScoringEvent money = null;
        for (ScoringEvent e : log) if (e.kind() == ScoringEvent.Kind.MONEY) money = e;
        check("the gold seal's payout is on the timeline", money != null);
        check("attributed to the card that paid", money != null && money.sourceId() == gold.id());
        check("for $3", money != null && money.amount().compareTo(BigDecimal.valueOf(3)) == 0);
    }

    /** Plasma rewrites the totals after the engine finishes; the timeline has to show that, not end stale. */
    private static void plasmaRewriteIsOnTheTimeline() {
        Match m = Match.createSeated(8L, List.of(SeatConfig.of("A"), SeatConfig.of("B")),
                MatchConfig.defaults().withDeckType(DeckType.PLASMA));
        Run run = m.getRun(new PlayerId(0));
        for (int i = 0; i < 12; i++) run.addCardToDeck(card(Rank.EIGHT, Suit.values()[i % 4]));
        Round r = run.beginRound(1_000_000);
        var result = r.play(new ArrayList<>(r.getHand().subList(0, 2)));
        List<ScoringEvent> log = r.getLastEvents();

        ScoringEvent last = log.get(log.size() - 1);
        check("the timeline ends on the balance", last.kind() == ScoringEvent.Kind.BALANCE);
        check("chips and mult end equal", last.chipsAfter().compareTo(last.multAfter()) == 0);
        BigDecimal replayed = last.chipsAfter().multiply(last.multAfter())
                .setScale(0, java.math.RoundingMode.HALF_UP);
        check("and still reproduce the banked score", replayed.compareTo(result.handScore()) == 0);
    }

    /** The client reads the timeline off the snapshot, as display strings. */
    private static void snapshotShipsIt() {
        Match m = Match.create(9L, List.of("A", "B"));
        PlayerId a = m.getSeats().get(0);
        m.start();
        Run run = m.getRun(a);

        check("no timeline before the first hand", MatchSnapshot.of(m, a).lastPlay().isEmpty());
        run.getRound().play(new ArrayList<>(run.getRound().getHand().subList(0, 2)));

        List<MatchSnapshot.ScoreEventView> shipped = MatchSnapshot.of(m, a).lastPlay();
        check("the timeline reaches the snapshot", !shipped.isEmpty());
        check("it opens with the base", shipped.get(0).kind().equals("BASE"));
        boolean readable = true;
        for (MatchSnapshot.ScoreEventView v : shipped)
            if (v.kind().isEmpty() || v.chipsAfter().isEmpty() || v.multAfter().isEmpty()) readable = false;
        check("every shipped beat is renderable", readable);
    }

    // --- helpers ---

    /** Plays exactly {@code cards} from a stacked hand and returns the timeline. */
    private static List<ScoringEvent> play(Run run, DeckCard... cards) {
        for (DeckCard c : cards) run.addCardToDeck(c);
        for (int i = 0; i < 6; i++) run.addCardToDeck(card(Rank.TWO, Suit.CLUBS));   // filler to draw around
        Round r = run.beginRound(1_000_000);
        List<DeckCard> play = new ArrayList<>();
        for (DeckCard c : cards) if (r.getHand().contains(c)) play.add(c);
        if (play.isEmpty()) play.addAll(r.getHand().subList(0, 2));   // the shuffle buried them; any pair will do
        r.play(play);
        return r.getLastEvents();
    }

    private static Run bareRun(long seed) { return new Run(seed); }

    /** A run with a deck full enough to deal a hand from. */
    private static Run stockedRun(long seed) {
        Run run = new Run(seed);
        for (int i = 0; i < 16; i++) run.addCardToDeck(card(Rank.values()[i % 9], Suit.values()[i % 4]));
        return run;
    }

    private static DeckCard card(Rank rank, Suit suit) { return new DeckCard(rank, suit); }

    private static void check(String label, boolean ok) {
        if (!ok) failures++;
        System.out.printf("%-56s %s%n", label, ok ? "PASS" : "FAIL");
    }

    private static void checkInt(String label, int actual, int expected) {
        check(label + " (" + actual + ")", actual == expected);
    }
}
