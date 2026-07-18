package model.game;

import model.cards.DeckCard;
import model.cards.DeckCard.Rank;
import model.cards.DeckCard.Suit;
import model.cards.jokers.JokerCard;
import model.cards.jokers.JokerSpec;
import model.cards.jokers.Rarity;
import model.game.player.BlindResult;
import model.game.player.PlayerId;
import model.game.player.Run;
import model.game.player.RoundOutcome;
import model.game.sins.SinModifier;
import model.modifiers.Edition;

import java.util.ArrayList;
import java.util.List;

/** Run-as-main harness for the {@link Match} loop: target table, deal/barrier/settle, and blind/ante/sin progression. */
public final class MatchTests {

    private static int failures = 0;

    public static void main(String[] args) {
        // --- target table spot checks ---
        checkLong("ante1 small", BlindTargets.target(1, Blind.SMALL), 300);
        checkLong("ante1 big", BlindTargets.target(1, Blind.BIG), 450);
        checkLong("ante1 boss", BlindTargets.target(1, Blind.BOSS), 600);
        checkLong("ante2 small", BlindTargets.target(2, Blind.SMALL), 800);
        checkLong("ante8 boss", BlindTargets.target(8, Blind.BOSS), 100_000);

        Match match = Match.create(123L, List.of("Winner", "Loser"));
        PlayerId winner = match.getSeats().get(0);
        PlayerId loser = match.getSeats().get(1);

        // Stack the winner's deck so a single Flush Five clears ante-1 small (300).
        Run winRun = match.getRun(winner);
        winRun.resetDeck(java.util.List.of());
        for (int i = 0; i < 8; i++) winRun.addCardToDeck(new DeckCard(Rank.ACE, Suit.SPADES));

        match.start();
        check("phase BLIND after start", match.getPhase() == MatchPhase.BLIND);
        checkInt("ante 1", match.getAnte(), 1);
        check("blind SMALL", match.getBlind() == Blind.SMALL);
        checkLong("current target 300", match.getCurrentTarget(), 300);
        check("sin selected", match.getActiveSin() != null);
        checkInt("round number 1 at ante1 small", match.getRoundNumber(), 1);
        check("ante boss locked at ante start", match.getAnteBoss() != null);
        check("every seat opens with $4", match.getRun(loser).getMoney() == Match.STARTING_MONEY);
        checkInt("winner dealt 8", match.getRun(winner).getRound().getHand().size(), 8);
        checkInt("loser dealt 8", match.getRun(loser).getRound().getHand().size(), 8);

        // Barrier rejects an early toShop while rounds are unfinished.
        checkThrows("toShop blocked mid-blind", match::toShop);

        // Winner clears with a Flush Five and finishes early; loser exhausts hands and fails.
        match.getRun(winner).getRound().play(handOf(match.getRun(winner), 5));
        match.getRun(winner).getRound().finish();
        check("winner WON", match.getRun(winner).getRound().getOutcome() == RoundOutcome.WON);
        exhaust(match.getRun(loser));

        match.toShop();
        check("phase SHOP", match.getPhase() == MatchPhase.SHOP);
        BlindResult wr = match.getResult(winner);
        BlindResult lr = match.getResult(loser);
        check("winner cleared", wr.cleared());
        check("winner earned money", wr.moneyEarned() > 0);
        check("loser not cleared", !lr.cleared());
        checkInt("loser earned nothing", lr.moneyEarned(), 0);

        // Progress SMALL -> BIG -> BOSS -> ante 2 SMALL, checking targets and rollover.
        match.nextBlind();
        check("now BIG", match.getBlind() == Blind.BIG);
        checkLong("big target 450", match.getCurrentTarget(), 450);
        checkInt("round number 2 at ante1 big", match.getRoundNumber(), 2);

        advance(match);   // exhaust BIG
        check("now BOSS", match.getBlind() == Blind.BOSS);
        checkLong("boss target 600", match.getCurrentTarget(), 600);

        Sin sinAnte1 = match.getActiveSin();
        advance(match);   // exhaust BOSS -> ante 2
        checkInt("ante rolled to 2", match.getAnte(), 2);
        check("back to SMALL", match.getBlind() == Blind.SMALL);
        checkInt("round number 4 at ante2 small", match.getRoundNumber(), 4);
        checkLong("ante2 small target 800", match.getCurrentTarget(), 800);
        check("sin reselected at ante rollover", match.getActiveSin() != null);
        check("sin field repopulated", sinAnte1 != null);

        match.finish();
        check("finished", match.getPhase() == MatchPhase.FINISHED);

        sinDispatchOrdering();
        swapGuards();
        endOfMatch();
        skipAndTags();

        System.out.println(failures == 0 ? "\nALL PASS" : "\n" + failures + " FAILURE(S)");
        if (failures != 0) System.exit(1);
    }

    /** The active sin's lifecycle hooks fire once per ante (onAnteBegin) and once per seat per deal/settle. */
    private static void sinDispatchOrdering() {
        Spy spy = new Spy();
        // Inject a resolver that returns the spy for whatever sin is selected, so dispatch is observed deterministically.
        Match match = Match.create(123L, List.of("A", "B"),
                MatchConfig.defaults().withSinResolver(sin -> spy));

        check("default resolver yields NONE before start",
                Match.create(1L, List.of("A", "B")).getSinModifier() == SinModifier.NONE);

        match.start();                                  // ante 1 SMALL: 1 anteBegin, 2 roundBegins
        checkInt("onAnteBegin after start", spy.anteBegins, 1);
        checkInt("onRoundBegin after start", spy.roundBegins, 2);
        check("active modifier is the injected spy", match.getSinModifier() == spy);

        advance(match);                                 // settle SMALL (+2), deal BIG  (+2)
        advance(match);                                 // settle BIG   (+2), deal BOSS (+2)
        advance(match);                                 // settle BOSS  (+2), roll to ante 2 SMALL: +1 anteBegin, deal +2

        checkInt("onAnteBegin total over 2 antes", spy.anteBegins, 2);
        checkInt("onRoundBegin total (4 deals x2 seats)", spy.roundBegins, 8);
        checkInt("onRoundSettled total (3 settles x2 seats)", spy.settles, 6);
    }

    /** swapJokers guards: phase + sin gating, self-swap, index validation, slot accounting, and the happy path. */
    private static void swapGuards() {
        Match envy = Match.create(77L, List.of("A", "B"),
                MatchConfig.defaults().withSinSelector((ante, rng) -> Sin.ENVY));
        PlayerId a = envy.getSeats().get(0);
        PlayerId b = envy.getSeats().get(1);
        JokerCard ja = stubJoker();
        JokerCard jb = stubJoker();
        envy.getRun(a).board().add(ja);
        envy.getRun(b).board().add(jb);

        envy.start();
        checkThrows("swap blocked outside SHOP", () -> envy.swapJokers(a, 0, b, 0));

        for (PlayerId id : envy.getSeats()) exhaust(envy.getRun(id));
        envy.toShop();
        checkThrows("swap rejects a self-swap", () -> envy.swapJokers(a, 0, a, 0));
        checkThrows("swap rejects a bad index", () -> envy.swapJokers(a, 3, b, 0));

        envy.swapJokers(a, 0, b, 0);
        check("swap exchanged the jokers", envy.getRun(a).getJokers().get(0) == jb
                && envy.getRun(b).getJokers().get(0) == ja);

        // Eternal guards against sale and destruction; a swap is neither (decision: no consent, no protection).
        envy.getRun(a).getJokers().get(0).apply(model.modifiers.Sticker.ETERNAL);
        envy.swapJokers(a, 0, b, 0);
        check("an Eternal joker is still swappable", envy.getRun(b).getJokers().get(0).hasSticker(model.modifiers.Sticker.ETERNAL));

        // Slot accounting: B is full (5 slot-consumers) plus one NEGATIVE joker; trading the NEGATIVE
        // away for A's slot-consuming joker would put B at 6/5 used slots, so the swap must be rejected.
        for (int i = 0; i < 4; i++) envy.getRun(b).board().add(stubJoker());   // B: 5 consumers
        JokerCard negative = stubJoker();
        negative.apply(Edition.NEGATIVE);
        envy.getRun(b).board().add(negative);                                  // B: index 5, slot-free
        checkThrows("swap blocked by slot accounting", () -> envy.swapJokers(a, 0, b, 5));

        // Sin gating: the same operation under a non-Envy sin is rejected even in SHOP.
        Match pride = Match.create(78L, List.of("A", "B"),
                MatchConfig.defaults().withSinSelector((ante, rng) -> Sin.PRIDE));
        PlayerId pa = pride.getSeats().get(0);
        PlayerId pb = pride.getSeats().get(1);
        pride.getRun(pa).board().add(stubJoker());
        pride.getRun(pb).board().add(stubJoker());
        pride.start();
        for (PlayerId id : pride.getSeats()) exhaust(pride.getRun(id));
        pride.toShop();
        checkThrows("swap blocked under a non-Envy sin", () -> pride.swapJokers(pa, 0, pb, 0));
    }

    /** End of match: the final ante's boss settles straight to FINISHED — no post-match shop, no further advance. */
    private static void endOfMatch() {
        check("default match length is 7 antes", Match.create(1L, List.of("A", "B")).getAnteCount() == 7);

        Match match = Match.create(99L, List.of("A", "B"), MatchConfig.defaults().withAnteCount(1));
        checkInt("configured length is 1 ante", match.getAnteCount(), 1);
        match.start();
        advance(match);   // settle SMALL, deal BIG
        advance(match);   // settle BIG, deal BOSS
        for (PlayerId id : match.getSeats()) exhaust(match.getRun(id));
        match.toShop();   // final boss settles: the match ends here
        check("FINISHED after the final boss", match.getPhase() == MatchPhase.FINISHED);
        check("final results recorded", match.getResults().size() == 2);
        boolean noShops = true;
        for (PlayerId id : match.getSeats()) noShops &= match.getRun(id).getShop() == null;
        check("no post-match shop opened", noShops);
        checkThrows("nextBlind blocked after the match", match::nextBlind);
    }

    /** The skip verb: SKIPPED outcome, forfeited economy and points, tag granted; guards on acting and phase. */
    private static void skipAndTags() {
        Match match = Match.create(140L, List.of("A", "B"),
                MatchConfig.defaults().withSinSelector((ante, rng) -> Sin.PRIDE)
                        .withBossSelector((ante, rng, exclude) -> BossBlind.THE_HOOK));   // hands untouched
        PlayerId a = match.getSeats().get(0);
        PlayerId b = match.getSeats().get(1);
        for (PlayerId id : match.getSeats()) {
            java.util.List<DeckCard> aces = new java.util.ArrayList<>();
            for (int i = 0; i < 16; i++) aces.add(new DeckCard(Rank.ACE, Suit.SPADES));
            match.getRun(id).resetDeck(aces);
        }
        match.start();
        check("every blind carries a seeded tag", match.getCurrentTag() != null);

        match.skipBlind(a);                                   // A skips; B clears
        check("skip ends the round SKIPPED",
                match.getRun(a).getRound().getOutcome() == RoundOutcome.SKIPPED);
        checkThrows("a seat cannot skip twice", () -> match.skipBlind(a));
        check("the skip granted exactly one tag", match.getRun(a).getStats().getTagsGained() == 1);
        int moneyAtSkip = match.getRun(a).getMoney();

        match.getRun(b).getRound().play(handOf(match.getRun(b), 5));   // a Flush Five clears; exhaust() would not
        match.getRun(b).getRound().finish();
        match.toShop();
        BlindResult skipped = match.getResult(a);
        check("a skipped result forfeits everything", skipped.outcome() == RoundOutcome.SKIPPED
                && skipped.score().signum() == 0 && skipped.moneyEarned() == 0);
        check("the skipper is absent from the award",
                !match.getStandings().getLastAward().containsKey(a));
        check("the clearer takes the full round pot",
                match.getStandings().getLastAward().getOrDefault(b, 0L) == 100L);
        checkThrows("no skipping outside the blind phase", () -> match.skipBlind(b));
        match.nextBlind();

        // Acting forfeits the right to skip.
        match.getRun(b).getRound().play(handOf(match.getRun(b), 5));
        checkThrows("no skip after acting", () -> match.skipBlind(b));

        // Investment: pending until the next boss is defeated, then $25 per copy.
        match.getRun(a).grantTag(model.game.tags.SkipTag.INVESTMENT_TAG);
        match.getRun(a).grantTag(model.game.tags.SkipTag.INVESTMENT_TAG);
        exhaust(match.getRun(a));
        match.getRun(b).getRound().finish();
        match.toShop();
        match.nextBlind();                                    // the ante-1 boss
        for (PlayerId id : match.getSeats()) {                // both clear it (Flush Five >> target)
            match.getRun(id).getRound().play(handOf(match.getRun(id), 5));
            if (match.getRun(id).getRound().getOutcome() == RoundOutcome.IN_PROGRESS)
                match.getRun(id).getRound().finish();
        }
        // Equalize interest (both seats at the cap) so the only cash-out difference is the Investment payout.
        // A holds the 2 granted copies plus however many the skipped blind's seeded tag happened to add.
        match.getRun(a).addMoney(100);
        match.getRun(b).addMoney(100);
        long copies = match.getRun(a).getPendingTags().stream()
                .filter(t -> t == model.game.tags.SkipTag.INVESTMENT_TAG).count();
        check("at least the two granted Investments are pending", copies >= 2);
        long aBefore = match.getRun(a).getMoney();
        long bBefore = match.getRun(b).getMoney();
        match.toShop();
        long aDelta = match.getRun(a).getMoney() - aBefore;
        long bDelta = match.getRun(b).getMoney() - bBefore;
        check("Investment pays $25 per copy at a defeated boss (" + aDelta + " vs " + bDelta + ")",
                aDelta - bDelta == 25 * copies);
        check("consumed Investments leave the pending area", match.getRun(a).getPendingTags().isEmpty());

        // Sloth: a skip grants the tag twice.
        Match sloth = Match.create(141L, List.of("A", "B"),
                MatchConfig.defaults().withSinSelector((ante, rng) -> Sin.SLOTH));
        PlayerId sa = sloth.getSeats().get(0);
        sloth.start();
        sloth.skipBlind(sa);
        check("Sloth grants a second tag on skip", sloth.getRun(sa).getStats().getTagsGained() == 2);
    }

    private static JokerCard stubJoker() {
        return new JokerCard(JokerSpec.named("Stub", Rarity.COMMON).build(), 4);
    }

    /** Counts each lifecycle dispatch so the test can assert the seam fires at the right points. */
    private static final class Spy implements SinModifier {
        int anteBegins, roundBegins, settles;
        @Override public void onAnteBegin(Match m)                 { anteBegins++; }
        @Override public void onRoundBegin(Run r)                  { roundBegins++; }
        @Override public void onRoundSettled(Run r, BlindResult b) { settles++; }
    }

    /** Exhausts a run's hands with one-card plays so its round becomes terminal. */
    private static void exhaust(Run run) {
        while (run.getRound().getOutcome() == RoundOutcome.IN_PROGRESS)
            run.getRound().play(handOf(run, 1));
    }

    /** From SHOP/BLIND: deal (if needed), exhaust both seats, and advance to the next shop. */
    private static void advance(Match match) {
        for (PlayerId id : match.getSeats()) exhaust(match.getRun(id));
        match.toShop();
        match.nextBlind();
    }

    private static List<DeckCard> handOf(Run run, int n) {
        return new ArrayList<>(run.getRound().getHand().subList(0, n));
    }

    private static void check(String label, boolean ok) {
        if (!ok) failures++;
        System.out.printf("%-34s %s%n", label, ok ? "PASS" : "FAIL");
    }

    private static void checkInt(String label, int actual, int expected) {
        check(label + " (" + actual + ")", actual == expected);
    }

    private static void checkLong(String label, long actual, long expected) {
        check(label + " (" + actual + ")", actual == expected);
    }

    private static void checkThrows(String label, Runnable action) {
        boolean threw = false;
        try { action.run(); } catch (RuntimeException e) { threw = true; }
        check(label, threw);
    }
}