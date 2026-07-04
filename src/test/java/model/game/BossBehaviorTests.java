package model.game;

import model.cards.DeckCard;
import model.cards.DeckCard.Rank;
import model.cards.DeckCard.Suit;
import model.cards.jokers.Jokers;
import model.game.player.BlindResult;
import model.game.player.PlayerId;
import model.game.player.RoundOutcome;
import model.game.player.Run;
import model.game.scoring.HandType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Run-as-main harness for the Match-level boss behaviours: The Hivemind (cross-table hand-type debuff),
 * The Commons (shared discard pool), The Bandwagon (most-owned joker debuffed), The Mirage (own best hand
 * excluded from the settled score), The Shave (global best hand excluded, ties shave symmetrically), plus
 * per-player boss disabling (Chicot exemption) and the {@link BossSelector} policy seam.
 */
public final class BossBehaviorTests {

    private static int failures = 0;

    public static void main(String[] args) {
        hivemind();
        commons();
        bandwagon();
        mirage();
        shave();

        System.out.println(failures == 0 ? "\nALL PASS" : "\n" + failures + " FAILURE(S)");
        if (failures != 0) System.exit(1);
    }

    /** The table's most-played hand type is debuffed: zero base chips/mult. Chicot seats are exempt. */
    private static void hivemind() {
        Match match = bossMatch(11L, BossBlind.THE_HIVEMIND);
        PlayerId a = match.getSeats().get(0);
        PlayerId b = match.getSeats().get(1);
        match.getRun(b).board().add(Jokers.CHICOT.make());   // seat B disables bosses

        driveToBoss(match);   // small + big played as Flush Fives -> most played across the table

        check("debuffed type is the table's most played",
                match.getRun(a).getRound().getDebuffedHandType() == HandType.FLUSH_FIVE);
        check("Chicot seat has no debuffed type",
                match.getRun(b).getRound().getDebuffedHandType() == null);

        // With no mult jokers, zeroed base mult collapses the debuffed hand to zero; other types still score.
        BigDecimal debuffed = match.getRun(a).getRound().play(hand(match.getRun(a), 5)).handScore();
        check("debuffed type scores zero at the boss", debuffed.signum() == 0);
        BigDecimal highCard = match.getRun(a).getRound().play(hand(match.getRun(a), 1)).handScore();
        check("non-debuffed type still scores", highCard.signum() > 0);
        BigDecimal exempt = match.getRun(b).getRound().play(hand(match.getRun(b), 5)).handScore();
        check("Chicot seat's hand is unaffected", exempt.signum() > 0);
    }

    /** One shared pool sized as the sum of participants' discards; any seat can spend beyond its personal count. */
    private static void commons() {
        Match match = bossMatch(22L, BossBlind.THE_COMMONS);
        PlayerId a = match.getSeats().get(0);
        PlayerId b = match.getSeats().get(1);
        driveToBoss(match);

        Run ra = match.getRun(a);
        Run rb = match.getRun(b);
        checkInt("pool is the summed discards (3+3)", ra.getRound().getDiscardsRemaining(), 6);
        ra.getRound().discard(hand(ra, 1));
        checkInt("a discard drains the pool for everyone", rb.getRound().getDiscardsRemaining(), 5);
        for (int i = 0; i < 3; i++) ra.getRound().discard(hand(ra, 1));   // A has now spent 4 > its personal 3
        checkInt("a seat can spend past its personal count", ra.getRound().getDiscardsRemaining(), 2);
        rb.getRound().discard(hand(rb, 1));
        rb.getRound().discard(hand(rb, 1));
        checkThrows("empty pool blocks every seat", () -> rb.getRound().discard(hand(rb, 1)));
        checkThrows("empty pool blocks the other seat too", () -> ra.getRound().discard(hand(ra, 1)));
    }

    /** The joker owned by the most seats is debuffed on every participating board for the round. */
    private static void bandwagon() {
        Match match = bossMatch(33L, BossBlind.THE_BANDWAGON);
        PlayerId a = match.getSeats().get(0);
        PlayerId b = match.getSeats().get(1);
        var shared_a = Jokers.JOKER.make();
        var shared_b = Jokers.JOKER.make();
        var unique = Jokers.GREEDY_JOKER.make();
        match.getRun(a).board().add(shared_a);
        match.getRun(a).board().add(unique);
        match.getRun(b).board().add(shared_b);

        driveToBoss(match);
        check("shared joker debuffed on seat A", shared_a.isDebuffed());
        check("shared joker debuffed on seat B", shared_b.isDebuffed());
        check("unique joker untouched", !unique.isDebuffed());

        finishAll(match);
        match.toShop();
        check("stickers stripped at the barrier", !shared_a.isDebuffed() && !shared_b.isDebuffed());

        // No joker with two owners -> no bandwagon, nothing debuffed.
        Match none = bossMatch(34L, BossBlind.THE_BANDWAGON);
        var onlyA = Jokers.JOKER.make();
        var onlyB = Jokers.GREEDY_JOKER.make();
        none.getRun(none.getSeats().get(0)).board().add(onlyA);
        none.getRun(none.getSeats().get(1)).board().add(onlyB);
        driveToBoss(none);
        check("no shared joker -> nothing debuffed", !onlyA.isDebuffed() && !onlyB.isDebuffed());
    }

    /** The seat's own best hand is excluded from the settled score; clearing still uses the raw banked score. */
    private static void mirage() {
        Match match = bossMatch(44L, BossBlind.THE_MIRAGE);
        PlayerId a = match.getSeats().get(0);
        PlayerId b = match.getSeats().get(1);
        driveToBoss(match);

        // Each seat plays two identical Flush Fives: raw = 2s, best = s, settled = s.
        BigDecimal s = match.getRun(a).getRound().play(hand(match.getRun(a), 5)).handScore();
        match.getRun(a).getRound().play(hand(match.getRun(a), 5));
        match.getRun(a).getRound().finish();
        clearBoss(match, b);
        match.toShop();

        BlindResult ra = match.getResult(a);
        check("cleared on the raw banked score", ra.cleared());
        check("best hand carried on the result", ra.bestHand().compareTo(s) == 0);
        check("settled score excludes the best hand", ra.score().compareTo(s) == 0);
    }

    /** Only the single highest hand across the table is excluded; exact ties are shaved symmetrically. */
    private static void shave() {
        Match match = bossMatch(55L, BossBlind.THE_SHAVE);
        PlayerId a = match.getSeats().get(0);
        PlayerId b = match.getSeats().get(1);
        driveToBoss(match);

        match.getRun(a).getRound().play(hand(match.getRun(a), 5));   // the table's biggest hand
        match.getRun(a).getRound().finish();
        match.getRun(b).getRound().play(hand(match.getRun(b), 1));   // a small hand, misses the boss target
        match.getRun(b).getRound().finish();
        match.toShop();

        BlindResult ra = match.getResult(a);
        BlindResult rb = match.getResult(b);
        check("global best hand shaved from its owner", ra.score().signum() == 0);
        check("shaving never changes the outcome", ra.cleared());
        check("the other seat's score is untouched", rb.score().compareTo(rb.bestHand()) >= 0 && rb.score().signum() > 0);

        // Mirrored play -> identical best hands -> both shaved (the only symmetric resolution).
        Match tied = bossMatch(56L, BossBlind.THE_SHAVE);
        driveToBoss(tied);
        for (PlayerId id : tied.getSeats()) clearBoss(tied, id);
        tied.toShop();
        boolean bothShaved = true;
        for (PlayerId id : tied.getSeats()) bothShaved &= tied.getResult(id).score().signum() == 0;
        check("exact cross-seat ties shave every holder", bothShaved);
    }

    // --- helpers ---

    /** A 2-seat match with a pinned boss, an inert sin, and 8-ace decks (every 5-card play is a Flush Five). */
    private static Match bossMatch(long seed, BossBlind boss) {
        Match match = Match.create(seed, List.of("A", "B"), MatchConfig.defaults()
                .withSinSelector((ante, rng) -> Sin.SLOTH)
                .withBossSelector((ante, rng, exclude) -> boss));
        for (PlayerId id : match.getSeats()) {
            Run run = match.getRun(id);
            run.resetDeck(java.util.List.of());
            for (int i = 0; i < 16; i++) run.addCardToDeck(new DeckCard(Rank.ACE, Suit.SPADES));
        }
        return match;
    }

    /** Starts the match and clears Small and Big with one Flush Five per seat, landing on the boss deal. */
    private static void driveToBoss(Match match) {
        match.start();
        for (int i = 0; i < 2; i++) {   // SMALL, BIG
            finishAll(match);
            match.toShop();
            match.nextBlind();
        }
        check("landed on the pinned boss", match.getBlind() == Blind.BOSS && match.getCurrentBoss() != null);
    }

    /** Every seat plays one 5-card hand and finishes its round. */
    private static void finishAll(Match match) {
        for (PlayerId id : match.getSeats()) clearBoss(match, id);
    }

    /** One 5-card play then a voluntary finish for {@code id}. */
    private static void clearBoss(Match match, PlayerId id) {
        Run run = match.getRun(id);
        run.getRound().play(hand(run, 5));
        run.getRound().finish();
    }

    private static List<DeckCard> hand(Run run, int n) {
        return new ArrayList<>(run.getRound().getHand().subList(0, n));
    }

    private static void check(String label, boolean ok) {
        if (!ok) failures++;
        System.out.printf("%-48s %s%n", label, ok ? "PASS" : "FAIL");
    }

    private static void checkInt(String label, int actual, int expected) {
        check(label + " (" + actual + ")", actual == expected);
    }

    private static void checkThrows(String label, Runnable action) {
        boolean threw = false;
        try { action.run(); } catch (RuntimeException e) { threw = true; }
        check(label, threw);
    }
}
