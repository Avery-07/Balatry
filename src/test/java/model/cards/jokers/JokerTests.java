package model.cards.jokers;

import model.cards.DeckCard;
import model.cards.DeckCard.Rank;
import model.cards.DeckCard.Suit;
import model.game.player.Run;
import model.game.scoring.HandEvaluation;
import model.game.scoring.HandEvaluator;
import model.game.scoring.ScoringEngine;
import model.game.scoring.Trigger;
import model.game.shop.Shop;
import model.game.shop.ShopPool;
import model.modifiers.Enhancement;

import java.util.List;

/**
 * Run-as-main harness for jokers. Covers three slices: scoring deltas (Mult/Chip contributions
 * routed through the scoring engine), slot and ON_ROUND_START hooks (Joker Stencil, Mystic Summit,
 * Ceremonial Dagger, Marble), and the retrigger primitive plus debt/shop economy (Mime, a
 * Blueprint-like joker retrigger, Loyalty Card, Credit Card).
 */
public final class JokerTests {

    private static int failures = 0;
    private static final HandEvaluator EVAL = new HandEvaluator();
    private static final ScoringEngine ENGINE = new ScoringEngine();

    public static void main(String[] args) {
        scoringDeltas();
        slotAndRoundStartHooks();
        retriggerAndEconomy();

        System.out.println(failures == 0 ? "\nALL PASS" : "\n" + failures + " FAILURE(S)");
        if (failures != 0) System.exit(1);
    }

    /** Mult/Chip deltas contributed by jokers through the scoring engine. */
    private static void scoringDeltas() {
        // Baselines (no jokers): single Ace high card, and a pair of Kings.
        checkScore("baseline high card (5+11)x1", score(new Run(0L), ace()), 16);
        checkScore("baseline pair of kings (10+20)x2", score(new Run(0L), kings()), 60);

        // Joker: +4 Mult -> (5+11) x (1+4) = 80
        checkScore("Joker +4 mult", scoreWith(ace(), Jokers.JOKER), 80);

        // Jolly: +8 Mult if contains a Pair -> (10+20) x (2+8) = 300
        checkScore("Jolly +8 on pair", scoreWith(kings(), Jokers.JOLLY_JOKER), 300);
        // Jolly does nothing on a high card.
        checkScore("Jolly inert on high card", scoreWith(ace(), Jokers.JOLLY_JOKER), 16);

        // Sly: +50 Chips if contains a Pair -> (10+20+50) x 2 = 160
        checkScore("Sly +50 chips on pair", scoreWith(kings(), Jokers.SLY_JOKER), 160);

        // Half Joker: +20 Mult if <= 3 cards -> pair is 2 cards -> (10+20) x (2+20) = 660
        checkScore("Half +20 mult on 2 cards", scoreWith(kings(), Jokers.HALF_JOKER), 660);

        // Abstract: +3 Mult per joker (itself = 1) -> (10+20) x (2+3) = 150
        checkScore("Abstract +3 per joker", scoreWith(kings(), Jokers.ABSTRACT_JOKER), 150);

        // Scary Face: +30 Chips per scored face (two kings) -> (10+20+60) x 2 = 180
        checkScore("Scary Face +30/face", scoreWith(kings(), Jokers.SCARY_FACE), 180);

        // Even Steven: +4 Mult per scored even card (two 4s) -> (10+8) x (2+8) = 180
        checkScore("Even Steven +4/even", scoreWith(fours(), Jokers.EVEN_STEVEN), 180);

        // Greedy: +3 Mult per scored Diamond (one scoring Ace of Diamonds) -> (5+11) x (1+3) = 64
        checkScore("Greedy +3/diamond", scoreWith(List.of(new DeckCard(Rank.ACE, Suit.DIAMONDS)), Jokers.GREEDY_JOKER), 64);
    }

    /** Jokers that read the slot count or fire on ON_ROUND_START. */
    private static void slotAndRoundStartHooks() {
        // Joker Stencil: lone in 5 slots -> 4 empty + itself = X5. Pair of kings (30 chips x 2 mult) -> 30 x (2*5) = 300.
        Run a = new Run(0L); a.getJokers().add(Jokers.JOKER_STENCIL.make());
        checkScore("Stencil X5 lone", score(a, kings()), 300);
        // With the board full (5 used), only its own slot counts -> X1 -> 60.
        Run full = new Run(0L);
        full.getJokers().add(Jokers.JOKER_STENCIL.make());
        for (int i = 0; i < 4; i++) full.getJokers().add(Jokers.JOKER.make());
        // (Joker also adds +4 mult x4 = +16; isolate Stencil by checking it's not X-ing: 30 x (2+16) x1 = 540.)
        checkScore("Stencil X1 on full board", score(full, kings()), 540);

        // Mystic Summit: +15 mult only when discards == 0.
        Run zero = new Run(0L); zero.setBaseDiscards(0); zero.getJokers().add(Jokers.MYSTIC_SUMMIT.make());
        zero.beginRound(1_000_000);   // round present, 0 discards, target high enough not to auto-win
        checkScore("Mystic +15 at 0 discards", score(zero, kings()), 510);   // 30 x (2+15)
        Run some = new Run(0L); some.setBaseDiscards(3); some.getJokers().add(Jokers.MYSTIC_SUMMIT.make());
        some.beginRound(1_000_000);
        checkScore("Mystic inert with discards", score(some, kings()), 60);

        // Ceremonial Dagger: at round start, eats the joker to its right and banks 3x its sell value.
        Run d = new Run(0L);
        d.getJokers().add(Jokers.CEREMONIAL_DAGGER.make());   // cost 6 -> sell 3
        JokerCard victim = Jokers.JOKER.make();                // cost 2 -> sell 1
        d.getJokers().add(victim);
        d.beginRound(300);
        JokerCard dagger = d.getJokers().get(0);
        check("dagger ate its neighbour", d.getJokers().size() == 1);
        checkInt("dagger banked 3x sell (3*1)", dagger.getCounter(), 3);

        // Marble Joker: at round start, adds a Stone card to the deck.
        Run m = new Run(0L); m.getJokers().add(Jokers.MARBLE_JOKER.make());
        int before = m.getDeck().size();
        m.beginRound(300);
        int after = m.getDeck().size();
        checkInt("marble grew the deck by 1", after - before, 1);
        DeckCard added = m.getDeck().get(m.getDeck().size() - 1);
        check("added card is Stone", added.getEnhancement() == Enhancement.STONE);
    }

    /** The retrigger primitive (held cards and jokers) plus the debt/shop economy hooks. */
    private static void retriggerAndEconomy() {
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
    }

    private static long score(Run run, List<DeckCard> played) {
        return score(run, played, List.of());
    }

    private static long score(Run run, List<DeckCard> played, List<DeckCard> held) {
        HandEvaluation e = EVAL.evaluate(played);
        long bc = run.getHandLevels().chipsFor(e.type());
        long bm = run.getHandLevels().multFor(e.type());
        return ENGINE.score(run, e.context(), bc, bm, e.scoringCards(), held).score().longValueExact();
    }

    private static long scoreWith(List<DeckCard> cards, Jokers joker) {
        Run run = new Run(0L);
        run.getJokers().add(joker.make());
        return score(run, cards);
    }

    private static List<DeckCard> ace()   { return List.of(new DeckCard(Rank.ACE, Suit.SPADES)); }
    private static List<DeckCard> kings() { return List.of(new DeckCard(Rank.KING, Suit.SPADES), new DeckCard(Rank.KING, Suit.HEARTS)); }
    private static List<DeckCard> fours() { return List.of(new DeckCard(Rank.FOUR, Suit.SPADES), new DeckCard(Rank.FOUR, Suit.HEARTS)); }

    private static void checkScore(String label, long actual, long expected) {
        check(label + " (" + actual + ")", actual == expected);
    }

    private static void checkInt(String label, int actual, int expected) {
        check(label + " (" + actual + ")", actual == expected);
    }

    private static void check(String label, boolean ok) {
        if (!ok) failures++;
        System.out.printf("%-42s %s%n", label, ok ? "PASS" : "FAIL");
    }
}
