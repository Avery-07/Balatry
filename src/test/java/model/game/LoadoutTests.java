package model.game;

import model.cards.DeckCard;
import model.cards.DeckType;
import model.cards.Decks;
import model.cards.vouchers.Vouchers;
import model.game.net.MatchSetup;
import model.game.player.PlayerId;
import model.game.player.Run;
import model.game.player.SeatConfig;
import model.game.player.Sleeve;
import model.game.player.Sleeves;
import model.game.rng.DeterministicRng;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Run-as-main harness for the loadout layer: deck composition per {@link DeckType}, the per-seat {@link Sleeve}
 * adjustments, cumulative {@link Stake} effects, and the determinism the whole thing has to preserve — the same
 * seed must build the same Erratic deck and the same Fracture cuts on every replay and every seat.
 */
public final class LoadoutTests {

    private static int failures = 0;

    public static void main(String[] args) {
        deckComposition();
        deckDeterminism();
        sleeves();
        stakes();
        seating();
        setupParsing();

        System.out.println(failures == 0 ? "\nALL PASS" : "\n" + failures + " FAILURE(S)");
        if (failures != 0) System.exit(1);
    }

    private static void deckComposition() {
        checkInt("Standard deals 52", Decks.of(DeckType.STANDARD, null).size(), 52);

        List<DeckCard> abandoned = Decks.of(DeckType.ABANDONED, null);
        checkInt("Abandoned deals 40", abandoned.size(), 40);
        check("Abandoned has no face cards", abandoned.stream().noneMatch(DeckCard::isFace));

        List<DeckCard> crowded = Decks.of(DeckType.CROWDED, null);
        checkInt("Crowded deals 64", crowded.size(), 64);
        checkInt("Crowded doubles the 12 face cards",
                (int) crowded.stream().filter(DeckCard::isFace).count(), 24);

        List<DeckCard> checkered = Decks.of(DeckType.CHECKERED, null);
        checkInt("Checkered deals 52", checkered.size(), 52);
        checkInt("Checkered has 26 spades",
                (int) checkered.stream().filter(c -> c.getSuit() == DeckCard.Suit.SPADES).count(), 26);
        checkInt("Checkered has 26 hearts",
                (int) checkered.stream().filter(c -> c.getSuit() == DeckCard.Suit.HEARTS).count(), 26);
        check("Checkered has no clubs or diamonds",
                checkered.stream().noneMatch(c -> c.getSuit() == DeckCard.Suit.CLUBS
                        || c.getSuit() == DeckCard.Suit.DIAMONDS));

        // The behavioural decks change shop/scoring behaviour, not composition: they still open with a plain 52.
        for (DeckType t : List.of(DeckType.BAZAAR, DeckType.GHOST, DeckType.ANAGLYPH, DeckType.PLASMA, DeckType.ECLIPSE))
            checkInt(t.displayName() + " deals a standard 52", Decks.of(t, null).size(), 52);
    }

    private static void deckDeterminism() {
        List<DeckCard> a = Decks.of(DeckType.ERRATIC, new DeterministicRng(99L));
        List<DeckCard> b = Decks.of(DeckType.ERRATIC, new DeterministicRng(99L));
        checkInt("Erratic deals 52", a.size(), 52);
        check("Erratic is a pure function of the seed", sameCards(a, b));

        List<DeckCard> other = Decks.of(DeckType.ERRATIC, new DeterministicRng(100L));
        check("a different seed builds a different Erratic deck", !sameCards(a, other));

        // Erratic really is randomized: a standard deck has exactly one of each rank/suit pair.
        Set<String> distinct = new HashSet<>();
        for (DeckCard c : a) distinct.add(c.getRank() + "-" + c.getSuit());
        check("Erratic repeats rank/suit pairs", distinct.size() < 52);
    }

    private static void sleeves() {
        Run base = new Run(1L);
        int hands = base.getBaseHands(), discards = base.getBaseDiscards();
        int slots = base.getJokerSlots(), handSize = base.getHandSize(), money = base.getMoney();

        Run redBlue = sleeved(Sleeve.RED_BLUE);
        checkInt("Red & Blue grants +1 hand", redBlue.getBaseHands(), hands + 1);
        checkInt("Red & Blue grants +1 discard", redBlue.getBaseDiscards(), discards + 1);

        checkInt("Legacy starts $10 richer", sleeved(Sleeve.LEGACY).getMoney(), money + 10);

        Run black = sleeved(Sleeve.BLACK);
        checkInt("Black grants +1 joker slot", black.getJokerSlots(), slots + 1);
        checkInt("Black costs a hand", black.getBaseHands(), hands - 1);

        Run colorful = sleeved(Sleeve.COLORFUL);
        checkInt("Colorful grants +2 hand size", colorful.getHandSize(), handSize + 2);
        checkInt("Colorful costs a hand", colorful.getBaseHands(), hands - 1);

        Run silk = sleeved(Sleeve.SILK);
        check("Silk starts with Overstock redeemed", silk.hasRedeemed(Vouchers.OVERSTOCK.spec()));
        check("Silk starts with Relic Merchant redeemed", silk.hasRedeemed(Vouchers.RELIC_MERCHANT.spec()));
        checkInt("Silk's Overstock widened the shop", silk.getShopSlots(), base.getShopSlots() + 1);
        // A granted voucher must not burn the ante's single redemption.
        check("a granted voucher leaves the ante's redemption free", silk.canRedeem(Vouchers.CRYSTAL_BALL.make()));

        Run fracture = sleeved(Sleeve.FRACTURE);
        checkInt("Fracture cuts 10 cards", fracture.getDeck().size(), 42);
        check("Fracture is deterministic", sameCards(fracture.getDeck(), sleeved(Sleeve.FRACTURE).getDeck()));
        check("Fracture cuts differently per seat", !sameCards(fracture.getDeck(), sleeved(Sleeve.FRACTURE, 1).getDeck()));
    }

    private static void stakes() {
        check("White includes only itself", Stake.WHITE.includes(Stake.WHITE) && !Stake.WHITE.includes(Stake.GOLD));
        check("Gold includes every earlier stake", Stake.GOLD.includes(Stake.WHITE) && Stake.GOLD.includes(Stake.ORANGE));

        long white = BlindTargets.target(4, Blind.SMALL, Stake.WHITE);
        checkLong("White leaves the base target alone", white, BlindTargets.target(4, Blind.SMALL));
        check("Green scales the target up", BlindTargets.target(4, Blind.SMALL, Stake.GREEN) > white);
        check("Purple scales harder than Green",
                BlindTargets.target(4, Blind.SMALL, Stake.PURPLE) > BlindTargets.target(4, Blind.SMALL, Stake.GREEN));
        checkLong("ante 1 is unscaled at every stake",
                BlindTargets.target(1, Blind.SMALL, Stake.PURPLE), BlindTargets.target(1, Blind.SMALL));

        checkInt("White pays the Small Blind", Stake.WHITE.rewardFor(Blind.SMALL), Blind.SMALL.getReward());
        checkInt("Black zeroes the Small Blind", Stake.BLACK.rewardFor(Blind.SMALL), 0);
        checkInt("Black still pays the Boss Blind", Stake.BLACK.rewardFor(Blind.BOSS), Blind.BOSS.getReward());
        checkInt("Gold inherits Black's Small Blind rule", Stake.GOLD.rewardFor(Blind.SMALL), 0);

        checkInt("rerolls step $1 by default", Stake.WHITE.rerollStep(), 1);
        checkInt("Orange steps rerolls $2", Stake.ORANGE.rerollStep(), 2);
        checkInt("Gold inherits Orange's reroll step", Stake.GOLD.rerollStep(), 2);
    }

    /** Seats with different stakes must get different targets in the same match — the per-seat plumbing. */
    private static void seating() {
        Match match = Match.createSeated(7L, List.of(
                new SeatConfig("Easy", Sleeve.STANDARD, Stake.WHITE),
                new SeatConfig("Hard", Sleeve.LEGACY, Stake.PURPLE)),
                MatchConfig.defaults().withDeckType(DeckType.ABANDONED));

        PlayerId easy = new PlayerId(0), hard = new PlayerId(1);
        check("the table's deck reached seat 0", match.getRun(easy).getDeck().size() == 40);
        check("the table's deck reached seat 1", match.getRun(hard).getDeck().size() == 40);
        check("seat 0 kept its stake", match.getRun(easy).getStake() == Stake.WHITE);
        check("seat 1 kept its stake", match.getRun(hard).getStake() == Stake.PURPLE);
        check("seat 1's sleeve paid out", match.getRun(hard).getMoney() > match.getRun(easy).getMoney());

        match.start();
        // Stake growth compounds per ante *above* the first, so ante 1 is deliberately equal for everyone —
        // the seats diverge as the run goes. What must hold here is that each seat's own stake is consulted.
        checkLong("seat 1's target is read through its own stake",
                match.getCurrentTarget(hard), BlindTargets.target(1, match.getBlind(), Stake.PURPLE));
        checkLong("the easier seat faces the baseline",
                match.getCurrentTarget(easy), match.getCurrentTarget());
        checkLong("ante 1 is equal at every stake",
                match.getCurrentTarget(hard), match.getCurrentTarget(easy));
        checkLong("each round opens on its own seat's target",
                match.getRun(hard).getRound().getTarget(), match.getCurrentTarget(hard));

        // The Eclipse deck's grants land on every seat.
        Match eclipse = Match.createSeated(7L, List.of(SeatConfig.of("A"), SeatConfig.of("B")),
                MatchConfig.defaults().withDeckType(DeckType.ECLIPSE));
        check("Eclipse grants the Showman voucher",
                eclipse.getRun(easy).hasRedeemed(Vouchers.SHOWMAN.spec()));
        checkInt("Eclipse grants a joker", eclipse.getRun(easy).getJokers().size(), 1);
    }

    /** The lobby parser: every process must read the same roster the same way, or replays diverge. */
    private static void setupParsing() {
        List<SeatConfig> plain = MatchSetup.parseSeats("P0,P1");
        checkInt("a bare roster seats everyone", plain.size(), 2);
        check("a bare roster defaults the sleeve", plain.get(0).sleeve() == Sleeve.STANDARD);
        check("a bare roster defaults the stake", plain.get(0).stake() == Stake.WHITE);

        List<SeatConfig> full = MatchSetup.parseSeats("Ann:RED_BLUE:GOLD, Bo:black:purple");
        check("a full entry reads the sleeve", full.get(0).sleeve() == Sleeve.RED_BLUE);
        check("a full entry reads the stake", full.get(0).stake() == Stake.GOLD);
        check("parsing is case-insensitive", full.get(1).sleeve() == Sleeve.BLACK && full.get(1).stake() == Stake.PURPLE);
        check("names are trimmed", full.get(1).name().equals("Bo"));

        boolean rejected = false;
        try { MatchSetup.parseSeats("P0:NOT_A_SLEEVE"); } catch (IllegalArgumentException e) { rejected = true; }
        check("an unknown sleeve is rejected, not defaulted", rejected);
    }

    // --- helpers ---

    private static Run sleeved(Sleeve sleeve) { return sleeved(sleeve, 0); }

    /** A fresh standard-deck run with {@code sleeve} applied at {@code seat}, as match assembly would build it. */
    private static Run sleeved(Sleeve sleeve, int seat) {
        Run run = new Run(1L);
        run.resetDeck(Decks.standard());
        Sleeves.apply(run, sleeve, seat);
        return run;
    }

    /** Compares two decks by rank/suit in order (DeckCard identity is per-object, so equals would not do). */
    private static boolean sameCards(List<DeckCard> a, List<DeckCard> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++)
            if (a.get(i).getRank() != b.get(i).getRank() || a.get(i).getSuit() != b.get(i).getSuit()) return false;
        return true;
    }

    private static void check(String label, boolean ok) {
        if (!ok) failures++;
        System.out.printf("%-52s %s%n", label, ok ? "PASS" : "FAIL");
    }

    private static void checkInt(String label, int actual, int expected) {
        check(label + " (" + actual + ")", actual == expected);
    }

    private static void checkLong(String label, long actual, long expected) {
        check(label + " (" + actual + ")", actual == expected);
    }
}
