package model.cards.consumables;

import model.cards.DeckCard;
import model.cards.DeckCard.Rank;
import model.cards.DeckCard.Suit;
import model.cards.consumables.Planets;
import model.cards.jokers.Jokers;
import model.cards.relics.RelicTarget;
import model.cards.relics.Relics;
import model.game.Blind;
import model.game.Match;
import model.game.player.PlayerId;
import model.game.player.Run;
import model.game.scoring.HandEvaluation;
import model.game.scoring.HandEvaluator;
import model.game.scoring.HandType;
import model.game.scoring.ScoringEngine;
import model.game.shop.Shop;

import java.util.List;

/**
 * Run-as-main harness for the ten Relics. Three slices: round-scoped debuffs routed through the scoring engine
 * (Anathema rank, Miasma suit, Katadesmos joker, plus the round-boundary clear), immediate cross-player effects
 * resolved through {@link Match#useRelic} (Pyre, Katabasis, Harpax, Mimesis), and the multiplayer guards
 * (Aegis negation + Anger targeting, Limos shop debuff, Metabole boss reroll).
 */
public final class RelicTests {

    private static int failures = 0;
    private static final HandEvaluator EVAL = new HandEvaluator();
    private static final ScoringEngine ENGINE = new ScoringEngine();

    public static void main(String[] args) {
        roundScopedDebuffs();
        immediateCrossPlayer();
        multiplayerGuards();

        System.out.println(failures == 0 ? "\nALL PASS" : "\n" + failures + " FAILURE(S)");
        if (failures != 0) System.exit(1);
    }

    /** Anathema / Miasma / Katadesmos lodge on the target's Afflictions and are felt through scoring; cleared at round end. */
    private static void roundScopedDebuffs() {
        checkScore("baseline pair of kings", score(new Run(0L), kings()), 60);

        // Anathema (KING): both scored kings contribute no chips -> (10 base) x 2 = 20.
        Run anath = new Run(0L);
        anath.getAfflictions().armRankDebuff(Rank.KING);
        anath.beginRound(1_000_000);
        checkScore("Anathema voids the debuffed rank", score(anath, kings()), 20);

        // Miasma (SPADES): only the spade king is voided; the heart king still scores -> (10 + 10) x 2 = 40.
        Run miasma = new Run(0L);
        miasma.getAfflictions().armSuitDebuff(Suit.SPADES);
        miasma.beginRound(1_000_000);
        checkScore("Miasma voids the debuffed suit", score(miasma, kings()), 40);

        // Katadesmos (board position 0): the targeted Joker (+4 Mult) is debuffed for the round -> joker inert -> 60.
        Run kat = new Run(0L);
        kat.board().add(Jokers.JOKER.make());
        kat.getAfflictions().armJokerDebuff(0);
        kat.beginRound(1_000_000);
        check("Katadesmos debuffs the joker mid-round", kat.getJokers().get(0).isDebuffed());
        checkScore("Katadesmos neutralizes the joker", score(kat, kings()), 60);

        // Round boundary clears it: the joker is live again next round.
        kat.endRound(Blind.SMALL);
        check("Katadesmos sticker stripped at round end", !kat.getJokers().get(0).isDebuffed());
        kat.beginRound(1_000_000);
        checkScore("joker active again next round", score(kat, kings()), 180);   // 30 x (2+4)
    }

    /** Pyre / Katabasis / Harpax / Mimesis resolve immediately against another seat (or the table). */
    private static void immediateCrossPlayer() {
        // Harpax: steals 25% (floored) of the target's money.
        Match harpax = started(1L);
        PlayerId a = harpax.getSeats().get(0), b = harpax.getSeats().get(1);
        harpax.getRun(a).addMoney(3);
        harpax.getRun(b).addMoney(20);
        harpax.getRun(a).addRelic(Relics.HARPAX.make());
        harpax.useRelic(a, 0, RelicTarget.on(b));
        checkInt("Harpax: caster gains the cut", harpax.getRun(a).getMoney(), 8);
        checkInt("Harpax: target loses the cut", harpax.getRun(b).getMoney(), 15);

        // Pyre: destroys one of the target's consumables.
        Match pyre = started(2L);
        PlayerId pa = pyre.getSeats().get(0), pb = pyre.getSeats().get(1);
        pyre.getRun(pb).addConsumable(Planets.MERCURY.make());
        pyre.getRun(pb).addConsumable(Planets.VENUS.make());
        pyre.getRun(pa).addRelic(Relics.PYRE.make());
        pyre.useRelic(pa, 0, RelicTarget.on(pb));
        checkInt("Pyre destroys one consumable", pyre.getRun(pb).getConsumables().size(), 1);

        // Katabasis: levels a hand type down by one.
        Match kata = started(3L);
        PlayerId ka = kata.getSeats().get(0), kb = kata.getSeats().get(1);
        kata.getRun(kb).getHandLevels().levelUp(HandType.PAIR);   // -> level 2
        kata.getRun(ka).addRelic(Relics.KATABASIS.make());
        kata.useRelic(ka, 0, RelicTarget.hand(kb, HandType.PAIR));
        checkInt("Katabasis levels the hand down", kata.getRun(kb).getHandLevels().levelOf(HandType.PAIR), 1);

        // Mimesis: copies the last consumable any seat used.
        Match mim = started(4L);
        PlayerId ma = mim.getSeats().get(0), mb = mim.getSeats().get(1);
        mim.getRun(ma).addConsumable(Planets.MERCURY.make());
        mim.getRun(ma).useConsumable(0);                          // table now remembers Mercury
        mim.getRun(mb).addRelic(Relics.MIMESIS.make());
        mim.useRelic(mb, 0, RelicTarget.none());
        check("Mimesis copies the table's last consumable",
                mim.getRun(mb).getConsumables().size() == 1
                        && mim.getRun(mb).getConsumables().get(0).getSpec() == Planets.MERCURY.spec());
    }

    /** Aegis + Anger, Limos's shop debuff, and Metabole's table-boss reroll. */
    private static void multiplayerGuards() {
        // Aegis negates the first hostile hit this ante; Anger still counts every targeting.
        Match m = started(5L);
        PlayerId atk = m.getSeats().get(0), def = m.getSeats().get(1);
        m.getRun(def).addMoney(20);
        m.getRun(def).addRelic(Relics.AEGIS.make());
        m.useRelic(def, 0, RelicTarget.none());                  // defender arms its own shield (self, not a targeting)
        check("Aegis armed", m.getRun(def).getAfflictions().isAegisArmed());
        checkInt("arming Aegis is not a targeting", m.getRun(def).getStats().getTimesTargeted(), 0);

        m.getRun(atk).addRelic(Relics.HARPAX.make());
        m.getRun(atk).addRelic(Relics.HARPAX.make());
        m.useRelic(atk, 0, RelicTarget.on(def));                 // first hit -> absorbed
        checkInt("Aegis negates the hit: target intact", m.getRun(def).getMoney(), 20);
        checkInt("Aegis negates the hit: caster unchanged", m.getRun(atk).getMoney(), 0);
        checkInt("Anger counts the blocked targeting", m.getRun(def).getStats().getTimesTargeted(), 1);
        check("shield consumed", !m.getRun(def).getAfflictions().isAegisArmed());

        m.useRelic(atk, 0, RelicTarget.on(def));                 // second hit -> lands
        checkInt("next hit lands: target -5", m.getRun(def).getMoney(), 15);
        checkInt("next hit lands: caster +5", m.getRun(atk).getMoney(), 5);
        checkInt("Anger counts the second targeting", m.getRun(def).getStats().getTimesTargeted(), 2);

        // Limos: the first slot of the target's next shop is debuffed, for that visit only.
        Run shopper = new Run(7L);
        shopper.getAfflictions().armFirstSlotDebuff();
        Shop s1 = shopper.openShop();
        check("Limos debuffs the first slot", s1.getSlot(0) != null && s1.getSlot(0).isDebuffed());
        check("Limos leaves other slots alone", s1.getSlot(1) != null && !s1.getSlot(1).isDebuffed());
        shopper.closeShop();
        Shop s2 = shopper.openShop();
        check("Limos is one-shot: next shop is clean", !s2.getSlot(0).isDebuffed());

        // Metabole: rerolls the shared next-ante boss. Two identical matches diverge only by the reroll.
        Match plain = Match.create(99L, List.of("A", "B"));
        Match meta = Match.create(99L, List.of("A", "B"));
        runToAnte2Boss(plain, false);
        runToAnte2Boss(meta, true);
        check("both matches reached an ante-2 boss",
                plain.getCurrentBoss() != null && meta.getCurrentBoss() != null);
        check("Metabole changes the shared boss",
                plain.getCurrentBoss() != meta.getCurrentBoss());
    }

    // --- match helpers ---

    private static Match started(long seed) {
        Match m = Match.create(seed, List.of("A", "B"));
        m.start();
        return m;
    }

    /** Drives a match to the ante-2 boss deal; if {@code useMetabole}, a seat spends Metabole during the first shop. */
    private static void runToAnte2Boss(Match match, boolean useMetabole) {
        for (PlayerId id : match.getSeats()) stackToWin(match.getRun(id));   // each seat clears every blind in one hand
        match.start();                                   // ante 1 small
        winAll(match);
        match.toShop();
        if (useMetabole) {
            PlayerId caster = match.getSeats().get(0);
            match.getRun(caster).addRelic(Relics.METABOLE.make());
            match.useRelic(caster, 0, RelicTarget.none());   // arms a reroll for ante 2 (ante==1 here)
        }
        match.nextBlind();
        for (int i = 0; i < 4; i++) {                    // big, boss, ante2 small, ante2 big -> leaves us at ante2 boss
            winAll(match);
            match.toShop();
            match.nextBlind();
        }
    }

    /**
     * Stacks {@code run} so a single hand clears any blind under any boss: eight Aces in cycling suits (so any
     * 3- or 5-card window is a same-rank hand, never an accidental flush) and every hand type leveled far up.
     */
    private static void stackToWin(Run run) {
        run.resetDeck(java.util.List.of());
        Suit[] suits = Suit.values();
        for (int i = 0; i < 8; i++) run.addCardToDeck(new DeckCard(Rank.ACE, suits[i % suits.length]));
        for (HandType type : HandType.values())
            for (int i = 0; i < 100; i++) run.getHandLevels().levelUp(type);
    }

    /** Wins the current blind for every seat with one massive hand (robust to fixed-draw / must-play-5 bosses). */
    private static void winAll(Match match) {
        for (PlayerId id : match.getSeats()) {
            Run run = match.getRun(id);
            int n = Math.min(5, run.getRound().getHand().size());
            run.getRound().play(new java.util.ArrayList<>(run.getRound().getHand().subList(0, n)));
            run.getRound().finish();   // rounds no longer auto-end at the target
        }
    }

    // --- scoring helpers (mirrors JokerTests) ---

    private static long score(Run run, List<DeckCard> played) {
        HandEvaluation e = EVAL.evaluate(played);
        long bc = run.getHandLevels().chipsFor(e.type());
        long bm = run.getHandLevels().multFor(e.type());
        return ENGINE.score(run, e.context(), bc, bm, e.scoringCards(), List.of()).score().longValueExact();
    }

    private static List<DeckCard> kings() {
        return List.of(new DeckCard(Rank.KING, Suit.SPADES), new DeckCard(Rank.KING, Suit.HEARTS));
    }

    // --- assertions ---

    private static void checkScore(String label, long actual, long expected) {
        check(label + " (" + actual + ")", actual == expected);
    }

    private static void checkInt(String label, int actual, int expected) {
        check(label + " (" + actual + ")", actual == expected);
    }

    private static void check(String label, boolean ok) {
        if (!ok) failures++;
        System.out.printf("%-48s %s%n", label, ok ? "PASS" : "FAIL");
    }
}
