package model.game;

import model.cards.DeckCard;
import model.cards.DeckCard.Rank;
import model.cards.DeckCard.Suit;
import model.cards.Decks;
import model.cards.jokers.Jokers;
import model.game.player.Round;
import model.game.player.RoundOutcome;
import model.game.player.Run;

import java.util.ArrayList;
import java.util.List;

/** Run-as-main harness for the boss-blind system: round-setup transforms, card debuffs, play-time effects, and disabling. */
public final class BossBlindTests {

    private static int failures = 0;

    public static void main(String[] args) {
        // --- round-setup transforms ---
        checkInt("The Manacle: -1 hand size", deal(BossBlind.THE_MANACLE).getHand().size(), 7);
        checkInt("The Water: -1 discard",     deal(BossBlind.THE_WATER).getDiscardsRemaining(), deal(null).getDiscardsRemaining() - 1);
        checkInt("The Needle: 1 hand",        deal(BossBlind.THE_NEEDLE).getHandsRemaining(), 1);

        // --- target multipliers ---
        checkInt("The Wall x2 target",      BossBlind.THE_WALL.targetMultiplier(), 2);
        checkInt("Violet Vessel x3 target", BossBlind.VIOLET_VESSEL.targetMultiplier(), 3);
        checkInt("regular boss x1 target",  BossBlind.THE_HOOK.targetMultiplier(), 1);

        // --- The Quartz: ~1 in 7 of the round's cards are debuffed, deterministically, restored at round end ---
        int d1 = debuffedDeckCount(quartzRun());
        check("The Quartz debuffs some but not all", d1 > 0 && d1 < 52);
        checkInt("The Quartz is seed-deterministic", debuffedDeckCount(quartzRun()), d1);
        Run quartz = quartzRun();
        quartz.endRound(Blind.BOSS);
        checkInt("The Quartz restores debuffs at round end", debuffedDeckCount(quartz), 0);

        // --- Luchador / Chicot disable the boss (effectiveBoss() goes null) ---
        Run lucha = bossRun(BossBlind.THE_MANACLE);
        lucha.getJokers().add(Jokers.LUCHADOR.make());
        check("boss active before Luchador sold", lucha.effectiveBoss() != null);
        lucha.sellJoker(0);                                  // Luchador's own-sale disables the boss this round
        check("boss disabled after Luchador sold", lucha.effectiveBoss() == null);

        Run chic = bossRun(BossBlind.THE_MANACLE);
        chic.getJokers().add(Jokers.CHICOT.make());
        check("Chicot disables the boss", chic.effectiveBoss() == null);

        // --- The Tooth: lose $1 per card played ---
        Run tooth = smallDeckRun(5);
        tooth.addMoney(10);
        Round tr = tooth.beginRound(1_000_000, BossBlind.THE_TOOTH);   // unreachable target, stays in progress
        tr.play(pick(tr, 2));
        checkInt("The Tooth: -$1 per card played", tooth.getMoney(), 8);

        // --- The Psychic: must play exactly 5 cards ---
        Run psy = smallDeckRun(8);
        Round pr = psy.beginRound(1_000_000, BossBlind.THE_PSYCHIC);
        check("The Psychic rejects a 2-card play", throwsOnPlay(pr, pick(pr, 2)));
        check("The Psychic allows a 5-card play",   !throwsOnPlay(pr, pick(pr, 5)));

        // --- Mr. Bones converts a loss into a clear (one charge) ---
        Run bones = smallDeckRun(5);
        bones.setBaseHands(1);
        bones.getJokers().add(Jokers.MR_BONES.make());
        Round br = bones.beginRound(1_000_000, null);   // far above any achievable score
        br.play(pick(br, 1));                            // out of hands and below target
        check("Mr. Bones prevents the loss", br.getOutcome() == RoundOutcome.WON);
        checkInt("Mr. Bones used one charge", bones.getJokers().get(0).getCounter(), 1);

        // --- ON_BOSS_DEFEATED escalates Rocket ---
        Run rocket = smallDeckRun(5);
        rocket.getJokers().add(Jokers.ROCKET.make());
        Round rr = rocket.beginRound(1, BossBlind.THE_HOOK);   // target 1 -> any play meets it
        rr.play(pick(rr, 1));
        rr.finish();                                           // rounds no longer auto-end at the target
        check("boss round won", rr.getOutcome() == RoundOutcome.WON);
        rocket.endRound(Blind.BOSS);
        checkInt("Rocket counts the boss defeated", rocket.getJokers().get(0).getCounter(), 1);

        System.out.println(failures == 0 ? "ALL PASS" : failures + " FAILURE(S)");
        if (failures > 0) System.exit(1);
    }

    // --- helpers ---

    private static Run bossRun(BossBlind boss) {
        Run r = new Run(0L);
        r.getDeck().addAll(Decks.standard());
        r.beginRound(1_000_000, boss);   // sets the active boss; the dealt hand is irrelevant to these checks
        return r;
    }

    private static Round deal(BossBlind boss) {
        Run r = new Run(0L);
        r.getDeck().addAll(Decks.standard());
        return r.beginRound(1_000_000, boss);
    }

    private static Run quartzRun() {
        Run r = new Run(0L);
        r.getDeck().addAll(Decks.standard());
        r.beginRound(1_000_000, BossBlind.THE_QUARTZ);
        return r;
    }

    private static int debuffedDeckCount(Run run) {
        int n = 0;
        for (DeckCard card : run.getDeck()) if (card.isDebuffed()) n++;
        return n;
    }

    private static Run smallDeckRun(int n) {
        Run r = new Run(0L);
        for (int i = 0; i < n; i++) r.getDeck().add(new DeckCard(Rank.TWO, Suit.SPADES));   // low, non-face, non-club
        return r;
    }

    private static List<DeckCard> pick(Round round, int n) {
        List<DeckCard> hand = round.getHand();
        return new ArrayList<>(hand.subList(0, n));
    }

    private static boolean throwsOnPlay(Round round, List<DeckCard> cards) {
        try { round.play(cards); return false; } catch (IllegalStateException e) { return true; }
    }

    private static void check(String name, boolean ok) {
        if (!ok) { failures++; System.out.println("FAIL: " + name); }
    }

    private static void checkInt(String name, int actual, int expected) {
        if (actual != expected) { failures++; System.out.println("FAIL: " + name + " (got " + actual + ", want " + expected + ")"); }
    }

    private static void checkLong(String name, long actual, long expected) {
        if (actual != expected) { failures++; System.out.println("FAIL: " + name + " (got " + actual + ", want " + expected + ")"); }
    }
}