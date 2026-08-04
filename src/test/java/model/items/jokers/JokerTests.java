package model.items.jokers;

import model.items.DeckCard;
import model.items.DeckCard.Rank;
import model.items.DeckCard.Suit;
import model.game.player.JokerInfo;
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
        checkInt("catalog: 137 base + 28 new Balatry jokers", Jokers.values().length, 165);
        for (Jokers j : Jokers.values())
            check("every joker has a description: " + j.name(), !j.spec().getDescription().isEmpty());
        scoringDeltas();
        slotAndRoundStartHooks();
        retriggerAndEconomy();
        specialTraits();
        currentEffectDescriptors();
        newBalatryJokers();
        newBalatryEventJokers();
        handEvaluatorJokers();
        dyscalculiaRankJokers();
        chefOopsCopyrightJokers();
        singleSeatStubs();

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

    /**
     * A joker's current-effect tooltip: computed purely from a read-only view of the run, and — the property
     * that lets it be safe on a hover — it must never mutate the run. Replaces the ON_HOVERED trigger, which
     * did mutate (it wrote the counter), and so could have desynced the seats.
     */
    private static void currentEffectDescriptors() {
        Run run = new Run(0L);
        run.board().add(Jokers.BULL.make());       // +2 Chips per dollar
        run.board().add(Jokers.ABSTRACT_JOKER.make());  // +3 Mult per joker
        run.addMoney(10 - run.getMoney());
        JokerInfo info = JokerInfo.of(run);

        JokerCard bull = run.getJokers().get(0), abstractJoker = run.getJokers().get(1);
        check("Bull describes its current chips from money", bull.getSpec().stateOf(bull, info).contains("+20 Chips"));
        check("Abstract describes its current mult from joker count",
                abstractJoker.getSpec().stateOf(abstractJoker, info).contains("+6 Mult"));   // 3 x 2 jokers

        // The whole point: describing a joker changes nothing. Snapshot the run's mutable numbers, describe
        // every joker on the board, and confirm they are untouched — including the counters ON_HOVERED wrote.
        int money = run.getMoney(), deck = run.getDeck().size();
        int c0 = bull.getCounter(), c1 = abstractJoker.getCounter();
        for (JokerCard j : run.getJokers()) j.getSpec().stateOf(j, info);
        check("describing does not touch money", run.getMoney() == money);
        check("describing does not touch the deck", run.getDeck().size() == deck);
        check("describing does not touch counters", bull.getCounter() == c0 && abstractJoker.getCounter() == c1);

        // The read-only view has no seam to the run: EMPTY yields a safe, run-free description.
        Run erosionRun = new Run(0L);
        erosionRun.board().add(Jokers.EROSION.make());
        JokerCard erosion = erosionRun.getJokers().get(0);
        check("Erosion describes from deck size", erosion.getSpec().stateOf(erosion, JokerInfo.of(erosionRun)).contains("Mult"));
        check("a run-free description is safe (no NPE)", erosion.getSpec().stateOf(erosion, JokerInfo.EMPTY) != null);

        // The four counter-jokers that were missing a tooltip now describe their accumulated value.
        Run acc = new Run(0L);
        acc.board().add(Jokers.WEE_JOKER.make());
        JokerCard wee = acc.getJokers().get(0);
        wee.addCounter(24);
        check("Wee Joker shows its banked chips", wee.getSpec().stateOf(wee, JokerInfo.of(acc)).contains("+24 Chips"));

        Run camp = new Run(0L);
        camp.board().add(Jokers.CAMPFIRE.make());
        JokerCard campfire = camp.getJokers().get(0);
        check("Campfire shows its current Xmult", campfire.getSpec().stateOf(campfire, JokerInfo.of(camp)).contains("X1"));
    }

    /** Newly-wired stub jokers: Superposition creates a Tarot on an Ace-straight; Diet Cola self-destructs into a Double Tag. */
    private static void newBalatryJokers() {
        Run sup = new Run(0L);
        sup.board().add(Jokers.SUPERPOSITION.make());
        score(sup, List.of(
                new DeckCard(Rank.TEN, Suit.SPADES), new DeckCard(Rank.JACK, Suit.HEARTS),
                new DeckCard(Rank.QUEEN, Suit.CLUBS), new DeckCard(Rank.KING, Suit.DIAMONDS),
                new DeckCard(Rank.ACE, Suit.SPADES)));
        check("Superposition creates a Tarot on an Ace-straight", sup.getConsumables().size() == 1);

        Run supNoAce = new Run(0L);
        supNoAce.board().add(Jokers.SUPERPOSITION.make());
        score(supNoAce, List.of(
                new DeckCard(Rank.FIVE, Suit.SPADES), new DeckCard(Rank.SIX, Suit.HEARTS),
                new DeckCard(Rank.SEVEN, Suit.CLUBS), new DeckCard(Rank.EIGHT, Suit.DIAMONDS),
                new DeckCard(Rank.NINE, Suit.SPADES)));
        check("Superposition ignores an Ace-less straight", supNoAce.getConsumables().isEmpty());

        Run cola = new Run(0L);
        cola.board().add(Jokers.DIET_COLA.make());
        cola.getJokers().get(0).trigger(Trigger.ON_ROUND_END, cola);
        check("Diet Cola self-destructs after a round", cola.getJokers().isEmpty());
        check("Diet Cola leaves a Double Tag", cola.getPendingTags().contains(model.game.tags.SkipTag.DOUBLE_TAG));

        // Astronomer: Planet cards (and Celestial packs) buy free; other items stay priced.
        Run astro = new Run(0L);
        astro.board().add(Jokers.ASTRONOMER.make());
        astro.beginPurchase(new model.items.consumables.ConsumableCard(
                model.items.consumables.Planets.random(new java.util.Random(1)).spec()));
        astro.fire(Trigger.ON_PURCHASE_PRICING);
        check("Astronomer makes a Planet free", astro.isPurchaseFree());

        Run astroJoker = new Run(0L);
        astroJoker.board().add(Jokers.ASTRONOMER.make());
        astroJoker.beginPurchase(Jokers.JOKER.make());
        astroJoker.fire(Trigger.ON_PURCHASE_PRICING);
        check("Astronomer leaves a Joker priced", !astroJoker.isPurchaseFree());

        // Curator: Standard packs buy free.
        Run cur = new Run(0L);
        cur.board().add(Jokers.CURATOR.make());
        cur.beginPurchase(new model.items.packs.BoosterPack(
                model.items.packs.PackKind.STANDARD, model.items.packs.PackSize.NORMAL));
        cur.fire(Trigger.ON_PURCHASE_PRICING);
        check("Curator makes a Standard Pack free", cur.isPurchaseFree());
    }

    /** Stub jokers wired to new engine events: Canio (card destroyed), Red Joker (pack skipped), Lucky Cat (lucky triggered), Hallucination (pack opened). */
    private static void newBalatryEventJokers() {
        // Canio: +X1 per destroyed face card, +X0.25 per other (quarter-units), applied on the next hand.
        Run canioRun = new Run(0L);
        canioRun.board().add(Jokers.CANIO.make());
        JokerCard canio = canioRun.getJokers().get(0);
        canioRun.destroyDeckCards(List.of(new DeckCard(Rank.KING, Suit.SPADES), new DeckCard(Rank.FIVE, Suit.HEARTS)));
        check("Canio banks X1 (face) + X0.25 (other) as quarter-units", canio.getCounter() == 5);
        checkScore("Canio applies its banked Xmult", score(canioRun, kings()), 135);   // (10+20) x (2 x 2.25)

        // Red Joker: +3 Mult per skipped pack.
        Run red = new Run(0L);
        JokerCard redJoker = Jokers.RED_JOKER.make();
        red.board().add(redJoker);
        redJoker.trigger(Trigger.ON_PACK_SKIPPED, red);
        checkScore("Red Joker banks +3 Mult per skipped pack", score(red, ace()), 64);   // (5+11) x (1+3)

        // Lucky Cat: X0.25 per successful Lucky trigger.
        Run lucky = new Run(0L);
        JokerCard cat = Jokers.LUCKY_CAT.make();
        lucky.board().add(cat);
        cat.trigger(Trigger.ON_LUCKY_TRIGGERED, lucky);
        checkScore("Lucky Cat banks X0.25 per Lucky trigger", score(lucky, kings()), 75);   // (10+20) x (2 x 1.25)

        // Hallucination: a 1-in-2 chance at a Tarot each pack open — over many opens, some land, all Tarots.
        Run halluc = new Run(0L);
        halluc.setConsumableSlots(20);
        JokerCard h = Jokers.HALLUCINATION.make();
        halluc.board().add(h);
        for (int i = 0; i < 20; i++) h.trigger(Trigger.ON_PACK_OPENED, halluc);
        boolean allTarots = halluc.getConsumables().stream()
                .allMatch(c -> c.getSpec().getType() == model.items.consumables.ConsumableType.TAROT);
        check("Hallucination creates Tarots on pack open", !halluc.getConsumables().isEmpty() && allTarots);
    }

    /** HandEvaluator-flexibility jokers carry the traits Round reads; Pareidolia turns every card into a face card. */
    private static void handEvaluatorJokers() {
        check("Four Fingers carries its trait", Jokers.FOUR_FINGERS.make().hasActiveTrait(JokerTrait.FOUR_FINGERS));
        check("Shortcut carries its trait", Jokers.SHORTCUT.make().hasActiveTrait(JokerTrait.SHORTCUT));
        check("Smeared carries its trait", Jokers.SMEARED_JOKER.make().hasActiveTrait(JokerTrait.SMEARED));
        check("Splash carries its trait", Jokers.SPLASH.make().hasActiveTrait(JokerTrait.SPLASH));
        check("Dyscalculie carries its trait", Jokers.DYSCALCULIE.make().hasActiveTrait(JokerTrait.DYSCALCULIA));

        // Dyscalculie reaches rank-based jokers: a played 7 also counts as an 8, so Even Steven (+4 Mult on evens) fires.
        Run es = new Run(0L);
        es.board().add(Jokers.EVEN_STEVEN.make());
        checkScore("Even Steven ignores a lone 7 normally", score(es, List.of(new DeckCard(Rank.SEVEN, Suit.SPADES))), 12);
        Run esDys = new Run(0L);
        esDys.board().add(Jokers.EVEN_STEVEN.make());
        esDys.board().add(Jokers.DYSCALCULIE.make());
        checkScore("Dyscalculie makes a 7 count as even for Even Steven", score(esDys, List.of(new DeckCard(Rank.SEVEN, Suit.SPADES))), 60);

        DeckCard five = new DeckCard(Rank.FIVE, Suit.SPADES);
        check("without Pareidolia a 5 is not a face card", !new Run(0L).isFaceCard(five));
        Run par = new Run(0L);
        par.board().add(Jokers.PAREIDOLIA.make());
        check("with Pareidolia a 5 counts as a face card", par.isFaceCard(five));

        // Scary Face (+30 Chips per scored face) shows Pareidolia reaching a face-triggered joker.
        Run noPar = new Run(0L);
        noPar.board().add(Jokers.SCARY_FACE.make());
        checkScore("Scary Face ignores a non-face card", score(noPar, List.of(new DeckCard(Rank.FIVE, Suit.SPADES))), 10);
        Run scary = new Run(0L);
        scary.board().add(Jokers.SCARY_FACE.make());
        scary.board().add(Jokers.PAREIDOLIA.make());
        checkScore("Pareidolia makes a 5 a face for Scary Face", score(scary, List.of(new DeckCard(Rank.FIVE, Suit.SPADES))), 40);
    }

    /** Dyscalculie shifts rank for rank-reading jokers too: a scored card counts as the numbered rank above (Ten as Ace), and deck scans follow suit. */
    private static void dyscalculiaRankJokers() {
        // Scholar (+20 Chips, +4 Mult on an Ace): a Ten does nothing alone, but counts as an Ace under Dyscalculie.
        Run scholar = new Run(0L);
        scholar.board().add(Jokers.SCHOLAR.make());
        checkScore("Scholar ignores a lone Ten", score(scholar, List.of(new DeckCard(Rank.TEN, Suit.SPADES))), 15);
        Run scholarDys = new Run(0L);
        scholarDys.board().add(Jokers.SCHOLAR.make());
        scholarDys.board().add(Jokers.DYSCALCULIE.make());
        checkScore("Dyscalculie makes a Ten count as an Ace for Scholar",
                score(scholarDys, List.of(new DeckCard(Rank.TEN, Suit.SPADES))), 175);   // (5+10+20) x (1+4)

        // Cloud 9 ($1 per 9 in the deck): under Dyscalculie an 8 counts as a 9 too, but a 7 does not.
        List<DeckCard> deck = List.of(new DeckCard(Rank.NINE, Suit.SPADES), new DeckCard(Rank.NINE, Suit.HEARTS),
                new DeckCard(Rank.EIGHT, Suit.CLUBS), new DeckCard(Rank.SEVEN, Suit.DIAMONDS));
        Run cloud = new Run(0L);
        cloud.resetDeck(deck);
        cloud.board().add(Jokers.CLOUD_9.make());
        int before = cloud.getMoney();
        cloud.getJokers().get(0).trigger(Trigger.ON_ROUND_END, cloud);
        check("Cloud 9 pays for the two 9s", cloud.getMoney() - before == 2);
        Run cloudDys = new Run(0L);
        cloudDys.resetDeck(deck);
        cloudDys.board().add(Jokers.CLOUD_9.make());
        cloudDys.board().add(Jokers.DYSCALCULIE.make());
        int beforeDys = cloudDys.getMoney();
        cloudDys.getJokers().get(0).trigger(Trigger.ON_ROUND_END, cloudDys);
        check("Cloud 9 + Dyscalculie counts the 8 as a 9 (but not the 7)", cloudDys.getMoney() - beforeDys == 3);
    }

    /** Oops! All 6s (probability doubling), Chef Joker (food destroyed via ON_JOKER_DESTROYED), Copyright (cross-seat shared jokers). */
    private static void chefOopsCopyrightJokers() {
        Run oops = new Run(0L);
        oops.board().add(Jokers.OOPS_ALL_6S.make());
        boolean allTrue = true;
        for (int i = 0; i < 12; i++) if (!oops.roll(model.game.rng.RngSource.MISC, 1, 2)) allTrue = false;
        check("Oops! All 6s turns a 1-in-2 into a certainty", allTrue);
        Run noOops = new Run(0L);
        boolean anyFalse = false;
        for (int i = 0; i < 12; i++) if (!noOops.roll(model.game.rng.RngSource.MISC, 1, 2)) anyFalse = true;
        check("a 1-in-2 without Oops is not always true", anyFalse);

        Run chef = new Run(0L);
        chef.board().add(Jokers.CHEF_JOKER.make());
        JokerCard chefCard = chef.getJokers().get(0);
        JokerCard food = Jokers.GROS_MICHEL.make();
        chef.board().add(food);
        chef.destroyJoker(food);
        check("Chef feeds on a destroyed food joker", chefCard.getCounter() == 1);
        checkScore("Chef applies X2 after one food joker", score(chef, kings()), 120);   // (10+20) x (2 x 2)
        JokerCard plain = Jokers.JOKER.make();
        chef.board().add(plain);
        chef.destroyJoker(plain);
        check("Chef ignores a non-food joker", chefCard.getCounter() == 1);

        var m = model.game.Match.create(70L, List.of("A", "B"), model.game.MatchConfig.defaults());
        m.start();
        Run ra = m.getRun(m.getSeats().get(0)), rb = m.getRun(m.getSeats().get(1));
        ra.board().add(Jokers.COPYRIGHT.make());
        ra.board().add(Jokers.JOKER.make());   // a plain Joker, which the rival also owns
        rb.board().add(Jokers.JOKER.make());
        int before = ra.getMoney();
        ra.getJokers().get(0).trigger(Trigger.ON_ROUND_END, ra);
        check("Copyright pays $2 for a joker a rival also owns", ra.getMoney() - before == 2);
    }

    /** The single-seat stub jokers wired this session (To the Moon's interest is covered by SettlementTests). */
    private static void singleSeatStubs() {
        // Hiker: each scored card permanently gains +5 chips, counted the hand it is earned and stacking after.
        Run hiker = new Run(0L);
        hiker.board().add(Jokers.HIKER.make());
        DeckCard hk1 = new DeckCard(Rank.KING, Suit.SPADES), hk2 = new DeckCard(Rank.KING, Suit.HEARTS);
        List<DeckCard> pair = List.of(hk1, hk2);
        checkScore("Hiker +5/card on the first hand", score(hiker, pair), 80);   // (10 + 20 + 5+5) x 2
        checkInt("Hiker made the king +5 permanent", hk1.getBonusChips(), 5);
        checkScore("Hiker stacks on the next hand", score(hiker, pair), 100);    // (10 + 20 + 10bonus + 5+5) x 2
        checkInt("Hiker king now +10", hk1.getBonusChips(), 10);

        ShopPool dollars = stream -> { DeckCard d = new DeckCard(Rank.TWO, Suit.SPADES); d.setShopValue(1); return d; };

        // Scalper: gains X0.1 Mult each time the shop's card row is bought out.
        Run scalp = new Run(0L);
        JokerCard scalper = Jokers.SCALPER.make();
        scalp.board().add(scalper);
        scalp.addMoney(100 - scalp.getMoney());
        Shop sshop = new Shop(scalp, 0, 3, dollars);
        sshop.buy(0); sshop.buy(1);
        checkInt("Scalper inert until the row empties", scalper.getCounter(), 0);
        sshop.buy(2);   // the last card empties the row
        checkInt("Scalper gains on emptying the shop", scalper.getCounter(), 1);
        checkScore("Scalper X1.1 after one empty", score(scalp, kings()), 66);   // 60 x 1.1

        // Chaos the Clown: the first reroll of each shop is free while owned.
        Run chaos = new Run(0L);
        chaos.board().add(Jokers.CHAOS_THE_CLOWN.make());
        chaos.addMoney(100 - chaos.getMoney());
        Shop cshop = new Shop(chaos, 0, 3, dollars);
        checkInt("Chaos: first reroll is free", cshop.rerollCost(), 0);
        int before = chaos.getMoney();
        cshop.reroll();
        checkInt("the free reroll cost nothing", before - chaos.getMoney(), 0);
        check("Chaos: the second reroll is paid", cshop.rerollCost() > 0);
        Run plain = new Run(0L); plain.addMoney(100 - plain.getMoney());
        check("no Chaos -> the first reroll costs", new Shop(plain, 0, 3, dollars).rerollCost() > 0);
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