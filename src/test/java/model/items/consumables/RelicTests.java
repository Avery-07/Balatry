package model.items.consumables;

import model.items.DeckCard;
import model.items.DeckCard.Rank;
import model.items.DeckCard.Suit;
import model.items.jokers.Jokers;
import model.items.relics.RelicTarget;
import model.items.relics.Relics;
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

/** Run-as-main harness for the ten Relics. */
public final class RelicTests {

    private static int failures = 0;
    private static final HandEvaluator EVAL = new HandEvaluator();
    private static final ScoringEngine ENGINE = new ScoringEngine();

    public static void main(String[] args) {
        roundScopedDebuffs();
        immediateCrossPlayer();
        multiplayerGuards();
        standingsTargeting();

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
        // Harpax: the underdog steals 20% (floored) from a seat above them.
        Match harpax = ranked(1L);
        PlayerId a = harpax.getSeats().get(1), b = harpax.getSeats().get(0);   // b outranks a
        setMoney(harpax.getRun(a), 3);
        setMoney(harpax.getRun(b), 20);
        harpax.getRun(a).addRelic(Relics.HARPAX.make());
        harpax.useRelic(a, 0, RelicTarget.none());   // random rival: b is the only seat above a
        checkInt("Harpax: caster gains the cut", harpax.getRun(a).getMoney(), 7);
        checkInt("Harpax: target loses the cut", harpax.getRun(b).getMoney(), 16);

        // Pyre: destroys a random consumable from every seat above the caster.
        Match pyre = ranked(2L);
        PlayerId pb = pyre.getSeats().get(0), pa = pyre.getSeats().get(1);   // pb outranks pa
        pyre.getRun(pb).addConsumable(Planets.MERCURY.make());
        pyre.getRun(pb).addConsumable(Planets.VENUS.make());
        pyre.getRun(pa).addRelic(Relics.PYRE.make());
        pyre.useRelic(pa, 0, RelicTarget.none());
        checkInt("Pyre destroys one consumable from the seat above", pyre.getRun(pb).getConsumables().size(), 1);

        // Katabasis: levels a hand type down for everyone above the caster; no seat is chosen.
        Match kata = ranked(3L);
        PlayerId ka = kata.getSeats().get(1), kb = kata.getSeats().get(0);   // kb outranks ka
        int before = kata.getRun(kb).getHandLevels().levelOf(HandType.PAIR);
        kata.getRun(ka).addRelic(Relics.KATABASIS.make());
        kata.useRelic(ka, 0, RelicTarget.hand(null, HandType.PAIR));
        checkInt("Katabasis levels the hand down",
                kata.getRun(kb).getHandLevels().levelOf(HandType.PAIR), before - 1);

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
        Match m = ranked(5L);
        PlayerId atk = m.getSeats().get(1), def = m.getSeats().get(0);   // def outranks atk
        setMoney(m.getRun(atk), 0);
        setMoney(m.getRun(def), 20);
        m.getRun(def).addRelic(Relics.AEGIS.make());
        m.useRelic(def, 0, RelicTarget.none());                  // defender arms its own shield (self, not a targeting)
        check("Aegis armed", m.getRun(def).getAfflictions().isAegisArmed());
        checkInt("arming Aegis is not a targeting", m.getRun(def).getStats().getTimesTargeted(), 0);

        m.getRun(atk).addRelic(Relics.HARPAX.make());
        m.getRun(atk).addRelic(Relics.HARPAX.make());
        m.useRelic(atk, 0, RelicTarget.none());                  // random rival: def is the only seat above -> first hit absorbed
        checkInt("Aegis negates the hit: target intact", m.getRun(def).getMoney(), 20);
        checkInt("Aegis negates the hit: caster unchanged", m.getRun(atk).getMoney(), 0);
        checkInt("Anger counts the blocked targeting", m.getRun(def).getStats().getTimesTargeted(), 1);
        check("shield consumed", !m.getRun(def).getAfflictions().isAegisArmed());

        m.useRelic(atk, 0, RelicTarget.none());                  // second hit -> lands
        checkInt("next hit lands: target -4", m.getRun(def).getMoney(), 16);
        checkInt("next hit lands: caster +4", m.getRun(atk).getMoney(), 4);
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

    /** Standings-driven targeting: who a relic can hit, who it fizzles against, and how Aegis applies per seat. */
    private static void standingsTargeting() {
        // A three-seat table ranked a > b > c, so every targeting case has a seat to exercise.
        Match m = ranked3(11L);
        PlayerId a = m.getSeats().get(0), b = m.getSeats().get(1), c = m.getSeats().get(2);
        check("fixture is strictly ranked a > b > c",
                m.seatsAbove(c).size() == 2 && m.seatsAbove(b).size() == 1 && m.seatsAbove(a).isEmpty());

        // RIVALS: the bottom seat's Anathema lands on BOTH seats above it, and never on itself.
        m.getRun(c).addRelic(Relics.ANATHEMA.make());
        m.useRelic(c, 0, RelicTarget.rank(null, Rank.KING));
        m.nextBlind();                                        // deal rounds so the armed debuffs go live
        DeckCard king = new DeckCard(Rank.KING, Suit.SPADES);
        check("Anathema hits the first seat above", m.getRun(a).getAfflictions().debuffs(king));
        check("Anathema hits the second seat above", m.getRun(b).getAfflictions().debuffs(king));
        check("Anathema spares the caster", !m.getRun(c).getAfflictions().debuffs(king));

        // Fizzle: cast from the top of the standings, the card is spent but nobody is touched.
        Match top = ranked3(12L);
        PlayerId ta = top.getSeats().get(0), tb = top.getSeats().get(1);
        top.getRun(ta).addRelic(Relics.ANATHEMA.make());
        top.useRelic(ta, 0, RelicTarget.rank(null, Rank.QUEEN));
        top.nextBlind();
        DeckCard queen = new DeckCard(Rank.QUEEN, Suit.HEARTS);
        check("leader's Anathema touches nobody", !top.getRun(tb).getAfflictions().debuffs(queen));
        check("the fizzled relic is still spent", top.getRun(ta).getRelics().isEmpty());

        // Ties are not "above": at level pegs nobody can be targeted, so a random-rival relic fizzles too.
        Match tied = started(13L);
        PlayerId xa = tied.getSeats().get(0), xb = tied.getSeats().get(1);
        checkInt("fixture is tied", (int) tied.seatsAbove(xa).size(), 0);
        setMoney(tied.getRun(xb), 20);
        tied.getRun(xa).addRelic(Relics.HARPAX.make());
        tied.useRelic(xa, 0, RelicTarget.none());
        checkInt("Harpax fizzles against a tied seat", tied.getRun(xb).getMoney(), 20);

        // Random rival: Harpax cast from a mid-ranked seat lands on a seat strictly above it, never below.
        Match rnd = ranked3(14L);
        PlayerId ra = rnd.getSeats().get(0), rb = rnd.getSeats().get(1);   // ra outranks rb outranks the third
        setMoney(rnd.getRun(ra), 50);
        setMoney(rnd.getRun(rb), 0);
        rnd.getRun(rb).addRelic(Relics.HARPAX.make());
        rnd.useRelic(rb, 0, RelicTarget.none());
        checkInt("Harpax's random victim (a seat above) loses the cut", rnd.getRun(ra).getMoney(), 40);
        checkInt("the caster gains the cut", rnd.getRun(rb).getMoney(), 10);

        // Aegis is per seat: one shielded victim absorbs its copy while the other still takes the hit.
        Match shield = ranked3(15L);
        PlayerId sa = shield.getSeats().get(0), sb = shield.getSeats().get(1), sc = shield.getSeats().get(2);
        shield.getRun(sa).addRelic(Relics.AEGIS.make());
        shield.useRelic(sa, 0, RelicTarget.none());
        shield.getRun(sc).addRelic(Relics.ANATHEMA.make());
        shield.useRelic(sc, 0, RelicTarget.rank(null, Rank.KING));
        shield.nextBlind();
        DeckCard k2 = new DeckCard(Rank.KING, Suit.CLUBS);
        check("the shielded seat absorbs its copy", !shield.getRun(sa).getAfflictions().debuffs(k2));
        check("the unshielded seat is still hit", shield.getRun(sb).getAfflictions().debuffs(k2));
        checkInt("Anger counts a targeting per victim", shield.getRun(sa).getStats().getTimesTargeted(), 1);
        checkInt("Anger counts the unshielded victim too", shield.getRun(sb).getStats().getTimesTargeted(), 1);
    }

    // --- match helpers ---

    private static Match started(long seed) {
        Match m = Match.create(seed, List.of("A", "B"));
        m.start();
        return m;
    }

    /** Two seats where seat 0 outranks seat 1: seat 0 clears the opening blind, seat 1 scores nothing. */
    private static Match ranked(long seed) {
        return rankedTable(seed, List.of("A", "B"), 1);
    }

    /** Three seats ranked strictly a > b > c, by giving each a different number of scoring cards. */
    private static Match ranked3(long seed) {
        return rankedTable(seed, List.of("A", "B", "C"), 2);
    }

    /**
     * Plays one blind so the standings separate: every seat except the last plays a hand, and seats earlier in the
     * list play more cards than later ones, so scores (and therefore awarded points) come out strictly ordered.
     * Leaves the match in the shop with points recorded.
     */
    private static Match rankedTable(long seed, List<String> names, int scorers) {
        Match m = Match.create(seed, names);
        for (PlayerId id : m.getSeats()) stackToWin(m.getRun(id));
        m.start();
        List<PlayerId> seats = m.getSeats();
        for (int i = 0; i < seats.size(); i++) {
            Run run = m.getRun(seats.get(i));
            if (i < scorers) {                       // earlier seats play wider hands -> strictly higher scores
                int want = 5 - i;
                int n = Math.min(want, run.getRound().getHand().size());
                run.getRound().play(new java.util.ArrayList<>(run.getRound().getHand().subList(0, n)));
            }
            run.getRound().finish();
        }
        m.toShop();                                  // settles the blind and records the points
        return m;
    }

    /** Sets a run's money to exactly {@code amount}, whatever the blind reward left behind. */
    private static void setMoney(Run run, int amount) {
        run.addMoney(amount - run.getMoney());
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

    /** Stacks {@code run} so a single hand clears any blind under any boss: eight Aces in cycling suits (so any 3- or 5-card window is a same-rank hand, never an accidental flush) and every hand type leveled far up. */
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

    private static void checkThrows(String label, Runnable body) {
        boolean threw = false;
        try { body.run(); } catch (RuntimeException e) { threw = true; }
        check(label, threw);
    }

    private static void check(String label, boolean ok) {
        if (!ok) failures++;
        System.out.printf("%-48s %s%n", label, ok ? "PASS" : "FAIL");
    }
}
