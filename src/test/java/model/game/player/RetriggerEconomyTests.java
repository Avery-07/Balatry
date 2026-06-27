package model.game.player;

import model.cards.Card;
import model.cards.DeckCard;
import model.cards.DeckCard.Rank;
import model.cards.DeckCard.Suit;
import model.cards.jokers.JokerCard;
import model.cards.jokers.JokerSpec;
import model.cards.jokers.Jokers;
import model.cards.jokers.Rarity;
import model.modifiers.Enhancement;
import model.game.scoring.HandEvaluation;
import model.game.scoring.HandEvaluator;
import model.game.scoring.ScoringEngine;
import model.game.scoring.Trigger;

import java.util.List;

/** Harness for the retrigger primitive (cards + jokers) and the shop/debt economy hooks. */
public final class RetriggerEconomyTests {

    private static int failures = 0;
    private static final HandEvaluator EVAL = new HandEvaluator();
    private static final ScoringEngine ENGINE = new ScoringEngine();

    public static void main(String[] args) {
        // --- Mime: retriggers held cards. A held Steel card (X1.5) applied twice -> X2.25. ---
        DeckCard steel = new DeckCard(Rank.FOUR, Suit.CLUBS); steel.apply(Enhancement.STEEL);
        Run noMime = new Run(0L);
        checkScore("held steel once (X1.5)", score(noMime, kings(), List.of(steel)), 90);   // 30 x (2*1.5)
        Run mime = new Run(0L); mime.getJokers().add(Jokers.MIME.make());
        checkScore("Mime retriggers held steel", score(mime, kings(), List.of(steel)), 135); // 30 x (2*1.5*1.5)

        // --- Joker retrigger primitive: a Blueprint-like joker re-fires the joker to its right immediately. ---
        JokerSpec blueprint = JokerSpec.named("TestRetrigger", Rarity.RARE)
                .on(Trigger.ON_HAND_PLAYED, (run, self) -> {
                    List<JokerCard> js = run.getJokers();
                    int i = js.indexOf(self);
                    if (i + 1 < js.size()) run.getScoring().retriggerJoker(js.get(i + 1));
                }).build();
        Run plain = new Run(0L); plain.getJokers().add(Jokers.JOKER.make());          // +4 mult once
        checkScore("lone +4 joker", score(plain, kings(), List.of()), 180);            // 30 x (2+4)
        Run bp = new Run(0L);
        bp.getJokers().add(new JokerCard(blueprint, 10));
        bp.getJokers().add(Jokers.JOKER.make());                                       // +4 mult, fired twice
        checkScore("retriggered +4 joker", score(bp, kings(), List.of()), 300);        // 30 x (2+4+4)

        // --- Loyalty Card: every 4th purchase is free. ---
        Run shopper = new Run(0L);
        shopper.getJokers().add(Jokers.LOYALTY_CARD.make());
        shopper.addMoney(100 - shopper.getMoney());
        ShopPool dollarCards = stream -> { DeckCard d = new DeckCard(Rank.ACE, Suit.SPADES); d.setShopValue(1); return d; };
        Shop shop = new Shop(shopper, 0, 4, dollarCards);
        int before = shopper.getMoney();
        int deckBefore = shopper.getDeck().size();
        for (int i = 0; i < 4; i++) shop.buy(i);
        checkInt("4 buys, 4th free -> paid 3", before - shopper.getMoney(), 3);
        checkInt("4 cards acquired", shopper.getDeck().size() - deckBefore, 4);

        // --- Credit Card: debt floor, +1 Mult per in-debt dollar, reset at $0+. ---
        Run debt = new Run(0L);
        checkInt("no floor without Credit Card", debt.minBalance(), 0);
        debt.getJokers().add(Jokers.CREDIT_CARD.make());
        checkInt("Credit Card lowers floor to -20", debt.minBalance(), -20);
        debt.addMoney(5 - debt.getMoney());     // set balance to 5
        debt.spend(10);                          // 5 of those dollars spent while in debt
        checkInt("balance went to -5", debt.getMoney(), -5);
        JokerCard cc = debt.getJokers().get(0);
        checkInt("banked +1 Mult per in-debt $ (5)", cc.getCounter(), 5);
        checkScore("Credit Card +5 Mult", score(debt, kings(), List.of()), 210);   // 30 x (2+5)
        debt.addMoney(5);                        // back to $0
        checkInt("counter resets out of debt", cc.getCounter(), 0);

        System.out.println(failures == 0 ? "\nALL PASS" : "\n" + failures + " FAILURE(S)");
        if (failures != 0) System.exit(1);
    }

    private static long score(Run run, List<DeckCard> played, List<DeckCard> held) {
        HandEvaluation e = EVAL.evaluate(played);
        long bc = run.getHandLevels().chipsFor(e.type());
        long bm = run.getHandLevels().multFor(e.type());
        return ENGINE.score(run, e.context(), bc, bm, e.scoringCards(), held).score().longValueExact();
    }

    private static List<DeckCard> kings() {
        return List.of(new DeckCard(Rank.KING, Suit.SPADES), new DeckCard(Rank.KING, Suit.HEARTS));
    }

    private static void checkScore(String l, long a, long e) { check(l + " (" + a + ")", a == e); }
    private static void checkInt(String l, int a, int e)     { check(l + " (" + a + ")", a == e); }
    private static void check(String l, boolean ok) {
        if (!ok) failures++;
        System.out.printf("%-40s %s%n", l, ok ? "PASS" : "FAIL");
    }
}
