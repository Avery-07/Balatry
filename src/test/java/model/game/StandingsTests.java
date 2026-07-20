package model.game;

import model.items.DeckCard;
import model.items.DeckCard.Rank;
import model.items.DeckCard.Suit;
import model.game.player.BlindResult;
import model.game.player.PlayerId;
import model.game.player.RoundOutcome;
import model.game.player.Run;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Run-as-main harness for the points layer: the g(x) = 50x + 50 reward curve, the chip-proportional split (failed seats excluded from the denominator, nobody-cleared awards nothing, identical scores split equally), cumulative {@link Standings} with ranking, and the Match integration including Pride's point multiplier. */
public final class StandingsTests {

    private static int failures = 0;

    private static final PlayerId A = new PlayerId(0);
    private static final PlayerId B = new PlayerId(1);
    private static final PlayerId C = new PlayerId(2);

    public static void main(String[] args) {
        policyChecks();
        standingsChecks();
        matchIntegration();

        System.out.println(failures == 0 ? "\nALL PASS" : "\n" + failures + " FAILURE(S)");
        if (failures != 0) System.exit(1);
    }

    /** The reward curve and the proportional split, in isolation. */
    private static void policyChecks() {
        // g(x) = 50x + 50 on the 1-based global round index
        checkLong("round 1 (ante 1 Small) = 100", ProportionalPointsPolicy.reward(1, Blind.SMALL), 100);
        checkLong("round 2 (ante 1 Big) = 150", ProportionalPointsPolicy.reward(1, Blind.BIG), 150);
        checkLong("round 3 (ante 1 Boss) = 200", ProportionalPointsPolicy.reward(1, Blind.BOSS), 200);
        checkLong("round 21 (ante 7 Boss) = 1100", ProportionalPointsPolicy.reward(7, Blind.BOSS), 1100);

        PointsPolicy policy = PointsPolicy.PROPORTIONAL;

        // chip-proportional split among clearers: 1000 of 5000 -> a fifth of the reward
        Map<PlayerId, BlindResult> results = new LinkedHashMap<>();
        results.put(A, won(1000));
        results.put(B, won(4000));
        Map<PlayerId, Long> award = policy.award(1, Blind.SMALL, results);   // reward 100
        checkLong("1000/5000 chips -> a fifth (20)", award.get(A), 20);
        checkLong("4000/5000 chips -> four fifths (80)", award.get(B), 80);

        // a failed seat is out of both numerator and denominator
        results = new LinkedHashMap<>();
        results.put(A, won(1000));
        results.put(B, lost(9000));   // outscored everyone but missed the target
        results.put(C, won(3000));
        award = policy.award(1, Blind.SMALL, results);
        check("failed seat earns nothing", !award.containsKey(B));
        checkLong("clearer A: 1000/4000 of 100", award.get(A), 25);
        checkLong("clearer C: 3000/4000 of 100", award.get(C), 75);

        // nobody cleared -> no points, pot neither split nor rolled forward
        results = new LinkedHashMap<>();
        results.put(A, lost(500));
        results.put(B, lost(700));
        check("nobody cleared -> empty award", policy.award(3, Blind.BOSS, results).isEmpty());

        // identical scores (same seed makes this real, not theoretical) -> identical fractions
        results = new LinkedHashMap<>();
        results.put(A, won(2500));
        results.put(B, won(2500));
        award = policy.award(1, Blind.SMALL, results);
        check("tied scores split equally", award.get(A) == 50 && award.get(B) == 50);

        // per-seat HALF_UP rounding: thirds of 100 pay 33 / 33 / 33 (paid total may drift from the pot)
        results = new LinkedHashMap<>();
        results.put(A, won(1000));
        results.put(B, won(1000));
        results.put(C, won(1000));
        award = policy.award(1, Blind.SMALL, results);
        checkLong("a third of 100 rounds to 33", award.get(A), 33);
        checkLong("paid total may drift from the pot", award.values().stream().mapToLong(Long::longValue).sum(), 99);
    }

    /** Cumulative totals, last award, and ranking. */
    private static void standingsChecks() {
        Standings s = new Standings(List.of(A, B, C));
        checkLong("all seats start at 0", s.getPoints(A) + s.getPoints(B) + s.getPoints(C), 0);
        check("ranking starts in seat order", s.ranking().equals(List.of(A, B, C)));

        s.record(Map.of(A, 60L, B, 40L));
        s.record(Map.of(B, 100L, C, 30L));
        checkLong("A accumulated 60", s.getPoints(A), 60);
        checkLong("B accumulated 140", s.getPoints(B), 140);
        checkLong("C accumulated 30", s.getPoints(C), 30);
        check("last award reflects the latest round only", !s.getLastAward().containsKey(A)
                && s.getLastAward().get(B) == 100L);
        check("ranking orders by points desc", s.ranking().equals(List.of(B, A, C)));

        // ties keep seat order (stable)
        Standings tied = new Standings(List.of(A, B));
        tied.record(Map.of(A, 50L, B, 50L));
        check("tied ranking keeps seat order", tied.ranking().equals(List.of(A, B)));
    }

    /** Match wiring: awards land in standings at the settlement barrier; Pride multiplies a seat's award. */
    private static void matchIntegration() {
        // Both seats clear ante-1 Small with mirrored decks -> identical scores -> 50/50 of 100.
        Match match = Match.create(555L, List.of("X", "Y"),
                MatchConfig.defaults().withSinSelector((ante, rng) -> Sin.SLOTH));   // no point-side effects
        PlayerId x = match.getSeats().get(0);
        PlayerId y = match.getSeats().get(1);
        stackFlushFives(match.getRun(x));
        stackFlushFives(match.getRun(y));
        match.start();
        clearAndFinish(match, x);
        clearAndFinish(match, y);
        match.toShop();
        checkLong("mirrored clears split round 1 evenly", match.getStandings().getPoints(x), 50);
        checkLong("both seats at 50", match.getStandings().getPoints(y), 50);
        check("last award recorded on the match", match.getStandings().getLastAward().size() == 2);

        // Pride: a met wager multiplies the seat's award above the nominal pot.
        // Seat X wagers x2 (choice index 2) via the choice seam; seat Y stays at the x1 default.
        Match pride = Match.create(556L, List.of("X", "Y"),
                MatchConfig.defaults()
                        .withSinSelector((ante, rng) -> Sin.PRIDE)
                        .withSinChoiceProvider((run, request) -> run.getPlayerId().seat() == 0 ? 2 : 0));
        PlayerId px = pride.getSeats().get(0);
        PlayerId py = pride.getSeats().get(1);
        stackFlushFives(pride.getRun(px));
        stackFlushFives(pride.getRun(py));
        pride.start();
        clearAndFinish(pride, px);   // a Flush Five of aces massively exceeds 2x the ante-1 small target
        clearAndFinish(pride, py);
        pride.toShop();
        check("Pride threshold resolved for the wagering seat", pride.getRun(px).getSinState().isPrideThresholdMet());
        checkLong("met Pride wager doubles the seat's award", pride.getStandings().getPoints(px), 100);
        checkLong("non-wagering seat keeps its base share", pride.getStandings().getPoints(py), 50);
    }

    // --- helpers ---

    private static BlindResult won(long score)  { return new BlindResult(RoundOutcome.WON,  BigDecimal.valueOf(score), BigDecimal.ZERO, 300, 0, 0); }
    private static BlindResult lost(long score) { return new BlindResult(RoundOutcome.LOST, BigDecimal.valueOf(score), BigDecimal.ZERO, 300, 0, 0); }

    /** Replaces the seat's deck with aces of spades so every 5-card play is an identical Flush Five. */
    private static void stackFlushFives(Run run) {
        run.resetDeck(java.util.List.of());
        for (int i = 0; i < 8; i++) run.addCardToDeck(new DeckCard(Rank.ACE, Suit.SPADES));
    }

    /** Plays one clearing hand and finishes the seat's round voluntarily. */
    private static void clearAndFinish(Match match, PlayerId id) {
        var round = match.getRun(id).getRound();
        round.play(new ArrayList<>(round.getHand().subList(0, 5)));
        round.finish();
    }

    private static void check(String label, boolean ok) {
        if (!ok) failures++;
        System.out.printf("%-48s %s%n", label, ok ? "PASS" : "FAIL");
    }

    private static void checkLong(String label, long actual, long expected) {
        check(label + " (" + actual + ")", actual == expected);
    }
}
