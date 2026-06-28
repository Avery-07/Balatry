package model.game.player;

import model.cards.DeckCard;
import model.cards.DeckCard.Rank;
import model.cards.DeckCard.Suit;
import model.cards.jokers.JokerCard;
import model.cards.jokers.JokerSpec;
import model.cards.jokers.Rarity;
import model.game.Blind;
import model.modifiers.Enhancement;
import model.modifiers.Sticker;

import java.util.ArrayList;
import java.util.List;

/** Run-as-main harness for {@link RoundSettlement}: win economy, interest cap, gold, rental, and inert loss. */
public final class SettlementTests {

    private static int failures = 0;

    public static void main(String[] args) {
        // --- win economy: reward(3) + hands(3) + interest(20/5=4) on a Small blind ---
        Run run = standardRun(1L);
        run.addMoney(20);
        Round round = run.beginRound(1L);
        win(round);
        checkInt("hands left on win", round.getHandsRemaining(), 3);
        BlindResult r = run.endRound(Blind.SMALL);
        check("cleared", r.cleared());
        checkInt("earned 3+3+4", r.moneyEarned(), 10);
        checkInt("money 20 -> 30", run.getMoney(), 30);

        // --- interest is capped at $5 ---
        Run rich = standardRun(2L);
        rich.addMoney(100);
        win(rich.beginRound(1L));
        BlindResult cap = rich.endRound(Blind.SMALL);
        checkInt("interest capped: 3+3+5", cap.moneyEarned(), 11);

        // --- Gold card held at end of round pays $3 ---
        Run gold = new Run(3L);
        for (int i = 0; i < 7; i++) gold.getDeck().add(new DeckCard(Rank.TWO, Suit.SPADES));
        DeckCard goldCard = new DeckCard(Rank.ACE, Suit.HEARTS);
        goldCard.apply(Enhancement.GOLD);
        gold.getDeck().add(goldCard);                     // 8-card deck, fully dealt
        Round goldRound = gold.beginRound(1L);
        List<DeckCard> nonGold = new ArrayList<>();
        for (DeckCard c : goldRound.getHand()) if (c != goldCard && nonGold.size() < 5) nonGold.add(c);
        goldRound.play(nonGold);                          // gold stays in hand
        check("gold still in hand", goldRound.getHand().contains(goldCard));
        BlindResult gr = gold.endRound(Blind.SMALL);
        checkInt("reward 3 + hands 3 + gold 3", gr.moneyEarned(), 9);

        // --- Rental joker costs $3 at cash-out ---
        Run rental = standardRun(4L);
        rental.addMoney(20);
        JokerCard rented = new JokerCard(JokerSpec.named("Test", Rarity.COMMON).build(), 4);
        rented.apply(Sticker.RENTAL);
        rental.getJokers().add(rented);
        win(rental.beginRound(1L));
        BlindResult rr = rental.endRound(Blind.SMALL);
        checkInt("3+3+4-3 rental", rr.moneyEarned(), 7);

        // --- a failed blind is economically inert ---
        Run loser = standardRun(5L);
        loser.addMoney(20);
        Round losing = loser.beginRound(1_000_000_000L);
        for (int i = 0; i < 4; i++) losing.play(new ArrayList<>(losing.getHand().subList(0, 1)));
        BlindResult lr = loser.endRound(Blind.SMALL);
        check("not cleared", !lr.cleared());
        checkInt("loss earns nothing", lr.moneyEarned(), 0);
        checkInt("money untouched on loss", loser.getMoney(), 20);

        // --- Big/Boss base rewards differ ---
        check("blind rewards 3/4/5",
                Blind.SMALL.getReward() == 3 && Blind.BIG.getReward() == 4 && Blind.BOSS.getReward() == 5);

        System.out.println(failures == 0 ? "\nALL PASS" : "\n" + failures + " FAILURE(S)");
        if (failures != 0) System.exit(1);
    }

    private static void win(Round round) {
        round.play(new ArrayList<>(round.getHand().subList(0, 5)));
    }

    private static Run standardRun(long seed) {
        Run run = new Run(seed);
        for (Suit suit : Suit.values())
            for (Rank rank : Rank.values())
                run.getDeck().add(new DeckCard(rank, suit));
        return run;
    }

    private static void check(String label, boolean ok) {
        if (!ok) failures++;
        System.out.printf("%-34s %s%n", label, ok ? "PASS" : "FAIL");
    }

    private static void checkInt(String label, int actual, int expected) {
        check(label + " (" + actual + ")", actual == expected);
    }
}