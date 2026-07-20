package model.items.jokers;

import model.items.DeckCard;
import model.items.DeckCard.Rank;
import model.items.DeckCard.Suit;
import model.game.player.Run;
import model.game.scoring.HandEvaluation;
import model.game.scoring.HandEvaluator;
import model.game.scoring.ScoringEngine;
import model.game.scoring.Trigger;
import model.game.shop.Shop;
import model.game.shop.ShopPool;
import model.modifiers.Enhancement;
import model.modifiers.Sticker;

import java.util.List;

/** Run-as-main harness for jokers. */
public final class JokerTests {

    private static int failures = 0;
    private static final HandEvaluator EVAL = new HandEvaluator();
    private static final ScoringEngine ENGINE = new ScoringEngine();

    public static void main(String[] args) {
        checkInt("catalog: 137 base + 27 new Balatry jokers", Jokers.values().length, 164);
        for (Jokers j : Jokers.values())
            check("every joker has a description: " + j.name(), !j.spec().getDescription().isEmpty());
        scoringDeltas();
        slotAndRoundStartHooks();
        retriggerAndEconomy();
        specialTraits();

        boardInvariants();

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
        Run a = new Run(0L); a.board().add(Jokers.JOKER_STENCIL.make());
        checkScore("Stencil X5 lone", score(a, kings()), 300);
        // With the board full (5 used), only its own slot counts -> X1 -> 60.
        Run full = new Run(0L);
        full.board().add(Jokers.JOKER_STENCIL.make());
        for (int i = 0; i < 4; i++) full.board().add(Jokers.JOKER.make());
        // (Joker also adds +4 mult x4 = +16; isolate Stencil by checking it's not X-ing: 30 x (2+16) x1 = 540.)
        checkScore("Stencil X1 on full board", score(full, kings()), 540);

        // Mystic Summit: +15 mult only when discards == 0.
        Run zero = new Run(0L); zero.setBaseDiscards(0); zero.board().add(Jokers.MYSTIC_SUMMIT.make());
        zero.beginRound(1_000_000);   // round present, 0 discards, target high enough not to auto-win
        checkScore("Mystic +15 at 0 discards", score(zero, kings()), 510);   // 30 x (2+15)
        Run some = new Run(0L); some.setBaseDiscards(3); some.board().add(Jokers.MYSTIC_SUMMIT.make());
        some.beginRound(1_000_000);
        checkScore("Mystic inert with discards", score(some, kings()), 60);

        // Ceremonial Dagger: at round start, eats the joker to its right and banks 3x its sell value.
        Run d = new Run(0L);
        d.board().add(Jokers.CEREMONIAL_DAGGER.make());   // cost 6 -> sell 3
        JokerCard victim = Jokers.JOKER.make();                // cost 2 -> sell 1
        d.board().add(victim);
        d.beginRound(300);
        JokerCard dagger = d.getJokers().get(0);
        check("dagger ate its neighbour", d.getJokers().size() == 1);
        checkInt("dagger banked 3x sell (3*1)", dagger.getCounter(), 3);

        // Marble Joker: at round start, adds a Stone card to the deck.
        Run m = new Run(0L); m.board().add(Jokers.MARBLE_JOKER.make());
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
        Run mime = new Run(0L); mime.board().add(Jokers.MIME.make());
        checkScore("Mime retriggers held steel", score(mime, kings(), List.of(steel)), 135); // 30 x (2*1.5*1.5)

        // --- Joker retrigger primitive: a Blueprint-like joker re-fires the joker to its right immediately. ---
        JokerSpec blueprint = JokerSpec.named("TestRetrigger", Rarity.RARE)
                .on(Trigger.ON_HAND_PLAYED, (run, self) -> {
                    List<JokerCard> js = run.getJokers();
                    int i = js.indexOf(self);
                    if (i + 1 < js.size()) run.getScoring().retriggerJoker(js.get(i + 1));
                }).build();
        Run plain = new Run(0L); plain.board().add(Jokers.JOKER.make());          // +4 mult once
        checkScore("lone +4 joker", score(plain, kings(), List.of()), 180);            // 30 x (2+4)
        Run bp = new Run(0L);
        bp.board().add(new JokerCard(blueprint, 10));
        bp.board().add(Jokers.JOKER.make());                                       // +4 mult, fired twice
        checkScore("retriggered +4 joker", score(bp, kings(), List.of()), 300);        // 30 x (2+4+4)

        // --- Loyalty Card: every 4th purchase is free. ---
        Run shopper = new Run(0L);
        shopper.board().add(Jokers.LOYALTY_CARD.make());
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
        debt.board().add(Jokers.CREDIT_CARD.make());
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

    /** Chicot (DISABLES_BOSS) and Mr. Bones (PREVENTS_LOSS): present-and-not-debuffed activates; debuffed is inert. */
    private static void specialTraits() {
        // Chicot disables the boss while owned; a debuffed Chicot must not (debuffed = no effect).
        Run withChicot = new Run(0L);
        withChicot.board().add(Jokers.CHICOT.make());
        check("Chicot owned -> bossDisabled", withChicot.bossDisabled());
        withChicot.getJokers().get(0).apply(Sticker.DEBUFFED);
        check("Chicot debuffed -> boss NOT disabled", !withChicot.bossDisabled());

        // A run with no boss-disabling joker is unaffected.
        check("no Chicot -> boss not disabled", !new Run(0L).bossDisabled());

        // Mr. Bones prevents a loss while owned; charges twice then self-destructs; a debuffed Bones is inert.
        Run withBones = new Run(0L);
        withBones.board().add(Jokers.MR_BONES.make());
        check("Mr. Bones 1st save", withBones.tryPreventLoss());
        check("Mr. Bones survives first save", withBones.getJokers().size() == 1);
        check("Mr. Bones 2nd save", withBones.tryPreventLoss());
        check("Mr. Bones self-destructs after 2nd", withBones.getJokers().isEmpty());

        Run debuffedBones = new Run(0L);
        debuffedBones.board().add(Jokers.MR_BONES.make());
        debuffedBones.getJokers().get(0).apply(Sticker.DEBUFFED);
        check("Mr. Bones debuffed -> no save", !debuffedBones.tryPreventLoss());
    }

    private static long scoreWith(List<DeckCard> cards, Jokers joker) {
        Run run = new Run(0L);
        run.board().add(joker.make());
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

    /** The Board's invariants: the slot limit, Eternal on sell/destroy, and atomic swap validation. */
    private static void boardInvariants() {
        model.game.player.Run run = new model.game.player.Run(7L);
        var board = run.board();

        // Slot limit: adds fizzle once full; NEGATIVE jokers are free.
        for (int i = 0; i < 5; i++) check("slot " + i + " accepted", board.add(Jokers.JOKER.make()));
        check("6th slot-consuming joker fizzles", !board.add(Jokers.JOKER.make()));
        JokerCard negative = Jokers.JOKER.make();
        negative.apply(model.modifiers.Edition.NEGATIVE);
        check("NEGATIVE joker lands on a full board", board.add(negative));

        // Eternal: sell rejects loudly, destroy skips silently.
        JokerCard eternal = board.get(0);
        eternal.apply(model.modifiers.Sticker.ETERNAL);
        boolean sellRejected = false;
        try { run.sellJoker(0); } catch (IllegalStateException e) { sellRejected = true; }
        check("selling an Eternal joker is rejected", sellRejected);
        check("destroying an Eternal joker fails silently", !run.destroyJoker(eternal));
        check("the Eternal joker is still on the board", board.view().contains(eternal));
        check("a normal joker still destroys", run.destroyJoker(board.get(1)));

        // The view is unmodifiable: the old free-for-all is closed.
        boolean viewSealed = false;
        try { run.getJokers().add(Jokers.JOKER.make()); } catch (UnsupportedOperationException e) { viewSealed = true; }
        check("getJokers() view rejects mutation", viewSealed);
    }

    private static void check(String label, boolean ok) {
        if (!ok) failures++;
        System.out.printf("%-42s %s%n", label, ok ? "PASS" : "FAIL");
    }
}