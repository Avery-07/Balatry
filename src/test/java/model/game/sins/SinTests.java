package model.game.sins;

import model.game.Match;
import model.game.MatchConfig;
import model.game.Sin;
import model.game.SinSelector;
import model.game.player.BlindResult;
import model.game.player.PlayerId;
import model.game.player.Run;
import model.game.player.RoundOutcome;

import java.math.BigDecimal;
import java.util.List;

/**
 * Run-as-main harness for the sin seam and the Pride modifier: registry wiring, the round-begin choice resolved
 * through the injected {@link SinChoiceProvider}, the round-settled threshold check (met / not met / no-gamble),
 * and {@link SinState} reset.
 */
public final class SinTests {

    private static int failures = 0;
    private static final BigDecimal TWO = new BigDecimal("2");

    public static void main(String[] args) {
        registration();
        roundBeginChoice();
        roundSettledThreshold();
        stateReset();
        wrathPack();
        wrathDestroyForFree();
        gluttonyShop();
        gluttonyGaugeFeed();
        gluttonyEating();
        gluttonyPayout();
        gluttonyAnteLifecycle();

        System.out.println(failures == 0 ? "\nALL PASS" : "\n" + failures + " FAILURE(S)");
        if (failures != 0) System.exit(1);
    }

    private static void registration() {
        check("Pride registered as PrideModifier", Sins.modifierFor(Sin.PRIDE) instanceof PrideModifier);
        check("Wrath registered as WrathModifier", Sins.modifierFor(Sin.WRATH) instanceof WrathModifier);
        check("Sloth registered with a double skip grant", Sins.modifierFor(Sin.SLOTH).tagsPerSkip() == 2);
        check("Gluttony registered as GluttonyModifier", Sins.modifierFor(Sin.GLUTTONY) instanceof GluttonyModifier);
        check("unbuilt sin resolves to NONE", Sins.modifierFor(Sin.GREED) == SinModifier.NONE);
    }

    /** onRoundBegin consults the injected provider and stores the chosen multiplier on each seat's SinState. */
    private static void roundBeginChoice() {
        SinSelector alwaysPride = (ante, rng) -> Sin.PRIDE;

        // Provider picks option index 2 -> x2, for every seat.
        Match m2 = Match.create(7L, List.of("A", "B"),
                MatchConfig.defaults().withSinSelector(alwaysPride).withSinChoiceProvider((run, req) -> 2));
        m2.start();
        for (PlayerId id : m2.getSeats())
            check("Pride x2 stored for " + id, m2.getRun(id).getSinState().getPrideMultiplier().compareTo(TWO) == 0);

        // Default provider (FIRST -> index 0 -> x1, the no-gamble default).
        Match m1 = Match.create(7L, List.of("A", "B"), MatchConfig.defaults().withSinSelector(alwaysPride));
        m1.start();
        check("default provider -> x1",
                m1.getRun(m1.getSeats().get(0)).getSinState().getPrideMultiplier().compareTo(BigDecimal.ONE) == 0);
    }

    /** onRoundSettled marks the threshold met iff score >= target x multiplier; the point multiplier follows. */
    private static void roundSettledThreshold() {
        PrideModifier pride = new PrideModifier();
        long target = 600;

        // x2 chosen, score exactly target x2 -> met, point multiplier x2.
        Run met = headlessWithMultiplier(TWO);
        pride.onRoundSettled(met, result(target, target * 2));
        check("met threshold (score == target x2)", met.getSinState().isPrideThresholdMet());
        check("met -> point multiplier x2", met.getSinState().pridePointMultiplier().compareTo(TWO) == 0);

        // x2 chosen, cleared the blind but below target x2 -> not met, point multiplier x1.
        Run missed = headlessWithMultiplier(TWO);
        pride.onRoundSettled(missed, result(target, target * 2 - 1));
        check("not met (cleared but below target x2)", !missed.getSinState().isPrideThresholdMet());
        check("not met -> point multiplier x1", missed.getSinState().pridePointMultiplier().compareTo(BigDecimal.ONE) == 0);

        // x1 chosen -> threshold == target, so any clear meets it (no-gamble baseline, multiplier has no effect).
        Run safe = headlessWithMultiplier(BigDecimal.ONE);
        pride.onRoundSettled(safe, result(target, target));
        check("x1 met on clear", safe.getSinState().isPrideThresholdMet());
        check("x1 -> point multiplier x1", safe.getSinState().pridePointMultiplier().compareTo(BigDecimal.ONE) == 0);
    }

    private static void stateReset() {
        Run run = headlessWithMultiplier(TWO);
        run.getSinState().setPrideThresholdMet(true);
        run.getSinState().beginRound();
        check("beginRound resets multiplier to x1", run.getSinState().getPrideMultiplier().compareTo(BigDecimal.ONE) == 0);
        check("beginRound clears threshold-met", !run.getSinState().isPrideThresholdMet());
    }

    /** Wrath's free Mega Myth Pack: same seeded offer per seat, pick budget enforced, immediate relic cast. */
    private static void wrathPack() {
        Match m = Match.create(64L, List.of("A", "B"),
                MatchConfig.defaults().withSinSelector((ante, rng) -> Sin.WRATH));
        PlayerId a = m.getSeats().get(0);
        PlayerId b = m.getSeats().get(1);
        m.start();

        check("a pending pack is granted at round begin", m.getRun(a).getPendingPacks().size() == 1);
        var pack = m.getRun(a).getPendingPacks().get(0);
        check("the grant is a Mega Myth Pack",
                pack.kind() == model.cards.packs.PackKind.MYTH && pack.size() == model.cards.packs.PackSize.MEGA);

        var openA = m.getRun(a).openPendingPack(0);
        var openB = m.getRun(b).openPendingPack(0);
        check("the pack leaves the pending area on open", m.getRun(a).getPendingPacks().isEmpty());
        checkInt("Mega offer is 5 options", openA.getOptions().size(), 5);
        checkInt("Mega pick budget is 2", openA.getPicksLeft(), 2);
        boolean mirrored = true;
        for (int i = 0; i < 5; i++) {
            var ca = (model.cards.relics.RelicCard) openA.getOptions().get(i);
            var cb = (model.cards.relics.RelicCard) openB.getOptions().get(i);
            mirrored &= ca.getSpec().getName().equals(cb.getSpec().getName());
        }
        check("both seats see the same seeded offer", mirrored);

        // A picked relic is cast immediately (Harpax as the probe if offered; otherwise just burn the picks).
        var first = openA.pick(0);
        var second = openA.pick(1);
        check("picks come off the offer", openA.getOptions().get(0) == null && openA.getOptions().get(1) == null);
        checkThrows("the pick budget is enforced", () -> openA.pick(2));
        check("picked relics never touch the relic area", m.getRun(a).getRelics().isEmpty());
        m.useRelicCard(a, (model.cards.relics.RelicCard) first, model.cards.relics.RelicTarget.on(b));
        m.useRelicCard(a, (model.cards.relics.RelicCard) second, model.cards.relics.RelicTarget.on(b));
        check("pack relics cast without holding slots", m.getRun(a).getRelics().isEmpty());

        // An unopened pack dies with its round.
        for (PlayerId id : m.getSeats()) m.getRun(id).getRound().finish();
        m.toShop();
        check("an unopened pack does not survive the round", m.getRun(b).getPendingPacks().isEmpty());
    }

    /** Wrath's destroy-for-free: sin-gated, Eternal rejected, grants stack across rounds and die with the ante. */
    private static void wrathDestroyForFree() {
        Match m = Match.create(65L, List.of("A", "B"),
                MatchConfig.defaults().withSinSelector((ante, rng) -> Sin.WRATH));
        PlayerId a = m.getSeats().get(0);
        Run run = m.getRun(a);
        run.board().add(model.cards.jokers.Jokers.JOKER.make());
        run.board().add(model.cards.jokers.Jokers.GREEDY_JOKER.make());
        var eternal = model.cards.jokers.Jokers.LUSTY_JOKER.make();
        eternal.apply(model.modifiers.Sticker.ETERNAL);
        run.board().add(eternal);
        m.start();

        m.wrathDestroyJoker(a, 0);
        m.wrathDestroyJoker(a, 0);                       // grants stack
        checkInt("two destroys bank two grants", run.getSinState().getWrathFreeJokers(), 2);
        checkInt("destroyed jokers leave the board", run.board().size(), 1);
        checkThrows("an Eternal joker cannot be destroyed", () -> m.wrathDestroyJoker(a, 0));
        check("destroying earns no money", run.getMoney() == 0);

        // The grants survive into the shop and make joker purchases free; a pack purchase is untouched.
        for (PlayerId id : m.getSeats()) m.getRun(id).getRound().finish();
        m.toShop();
        var shop = run.getShop();
        run.addMoney(50);
        int before = run.getMoney();
        boolean jokerBought = false;
        for (int i = 0; i < shop.getSlotCount(); i++) {
            if (shop.getSlot(i) instanceof model.cards.jokers.JokerCard && run.canAcquire(shop.getSlot(i))) {
                shop.buy(i);
                jokerBought = true;
                break;
            }
        }
        if (jokerBought) {
            checkInt("a grant makes the joker purchase free", run.getMoney(), before);
            checkInt("one grant consumed on completion", run.getSinState().getWrathFreeJokers(), 1);
        }
        int cash = run.getMoney();
        shop.buyPack(0);
        check("a pack purchase does not touch the grant", run.getMoney() < cash
                && run.getSinState().getWrathFreeJokers() == (jokerBought ? 1 : 2));

        // Grants expire with the ante.
        run.getSinState().grantWrathFreeJoker();
        run.beginAnte();
        checkInt("unspent grants die with the ante", run.getSinState().getWrathFreeJokers(), 0);

        // Sin gating: the same action under another sin is rejected.
        Match pride = Match.create(66L, List.of("A", "B"),
                MatchConfig.defaults().withSinSelector((ante, rng) -> Sin.PRIDE));
        PlayerId pa = pride.getSeats().get(0);
        pride.getRun(pa).board().add(model.cards.jokers.Jokers.JOKER.make());
        pride.start();
        checkThrows("destroy-for-free is Wrath-gated", () -> pride.wrathDestroyJoker(pa, 0));
    }


    /** Gluttony's shop half: the card row swaps to the all-consumables pool; the pool rolls all five kinds. */
    private static void gluttonyShop() {
        Run run = new Run(80L);
        var setup = new model.game.shop.ShopSetup(3, model.game.shop.CatalogShopPool.INSTANCE, 0, null, 0, null);
        new GluttonyModifier().configureShop(run, setup);
        check("configureShop swaps in the Gluttony pool", setup.getCardPool() == model.game.shop.GluttonyShopPool.INSTANCE);

        boolean joker = false, tarot = false, planet = false, spectral = false, relic = false, onlyValid = true;
        var stream = new java.util.Random(99);
        for (int i = 0; i < 600; i++) {
            var c = model.game.shop.GluttonyShopPool.INSTANCE.roll(stream);
            if (c instanceof model.cards.jokers.JokerCard) joker = true;
            else if (c instanceof model.cards.relics.RelicCard) relic = true;
            else if (c instanceof model.cards.consumables.ConsumableCard cc) {
                switch (cc.getSpec().getType()) {
                    case TAROT -> tarot = true;
                    case PLANET -> planet = true;
                    case SPECTRAL -> spectral = true;
                    default -> onlyValid = false;
                }
            } else onlyValid = false;
        }
        check("Gluttony pool rolls all types of consumables", joker && tarot && planet && spectral && relic);
        check("Gluttony pool rolls nothing else", onlyValid);
    }

    /** Every consumable use — from the consumable area or a relic cast — mints $4 and bumps the user's tally. */
    private static void gluttonyGaugeFeed() {
        Match m = Match.create(81L, List.of("A", "B"),
                MatchConfig.defaults().withSinSelector((ante, rng) -> Sin.GLUTTONY));
        PlayerId a = m.getSeats().get(0);
        PlayerId b = m.getSeats().get(1);
        m.start();

        m.getRun(a).createConsumable(model.cards.consumables.Tarots.THE_HERMIT.spec());
        m.getRun(a).useConsumable(0);
        checkInt("a consumable use mints $4", m.getSinTableState().getGluttonyGauge(), 4);
        checkInt("the user's tally bumps", m.getSinTableState().gluttonyUses(a), 1);
        checkInt("other seats' tallies do not", m.getSinTableState().gluttonyUses(b), 0);

        m.useRelicCard(a, new model.cards.relics.RelicCard(model.cards.relics.Relics.AEGIS.spec()),
                model.cards.relics.RelicTarget.none());
        checkInt("a relic cast is a consumable use too", m.getSinTableState().getGluttonyGauge(), 8);
        checkInt("relic cast bumps the tally", m.getSinTableState().gluttonyUses(a), 2);

        check("tallies are visible", m.getSinTableState().getGluttonyUses().get(a) == 2);
    }

    /** Eating a joker: destroy for sell value + $5, counted as a use; Eternal rejected; Gluttony-gated. */
    private static void gluttonyEating() {
        Match m = Match.create(82L, List.of("A", "B"),
                MatchConfig.defaults().withSinSelector((ante, rng) -> Sin.GLUTTONY));
        PlayerId a = m.getSeats().get(0);
        Run run = m.getRun(a);
        run.board().add(new model.cards.jokers.JokerCard(
                model.cards.jokers.JokerSpec.named("Snack", model.cards.jokers.Rarity.COMMON).build(), 4));
        var eternal = model.cards.jokers.Jokers.JOKER.make();
        eternal.apply(model.modifiers.Sticker.ETERNAL);
        run.board().add(eternal);
        m.start();

        int before = run.getMoney();
        int gained = m.gluttonyEatJoker(a, 0);
        checkInt("eating pays sell value + $5", gained, 2 + GluttonyModifier.EAT_BONUS);
        checkInt("the money arrives", run.getMoney(), before + 7);
        checkInt("the joker is gone", run.board().size(), 1);
        checkInt("eating counts as a consumable use", m.getSinTableState().getGluttonyGauge(), 4);
        checkInt("eating bumps the eater's tally", m.getSinTableState().gluttonyUses(a), 1);
        checkThrows("an Eternal joker cannot be eaten", () -> m.gluttonyEatJoker(a, 0));

        Match pride = Match.create(83L, List.of("A", "B"),
                MatchConfig.defaults().withSinSelector((ante, rng) -> Sin.PRIDE));
        PlayerId pa = pride.getSeats().get(0);
        pride.getRun(pa).board().add(model.cards.jokers.Jokers.JOKER.make());
        pride.start();
        checkThrows("eating is Gluttony-gated", () -> pride.gluttonyEatJoker(pa, 0));
    }

    /** The payout: 60% to the top consumer, 40% split among the rest; ties and rounding are deterministic. */
    private static void gluttonyPayout() {
        GluttonyModifier gluttony = new GluttonyModifier();

        // 3 seats, uses 3/1/1, pool $20 -> leader $12, others $4 each, no remainder.
        Match m = Match.create(84L, List.of("A", "B", "C"),
                MatchConfig.defaults().withSinSelector((ante, rng) -> Sin.GLUTTONY));
        m.start();
        PlayerId a = m.getSeats().get(0), b = m.getSeats().get(1), c = m.getSeats().get(2);
        var table = m.getSinTableState();
        for (int i = 0; i < 3; i++) table.recordGluttonyUse(a, GluttonyModifier.GAUGE_PER_USE);
        table.recordGluttonyUse(b, GluttonyModifier.GAUGE_PER_USE);
        table.recordGluttonyUse(c, GluttonyModifier.GAUGE_PER_USE);
        int ma = m.getRun(a).getMoney(), mb = m.getRun(b).getMoney(), mc = m.getRun(c).getMoney();
        gluttony.onAnteSettled(m);
        checkInt("top consumer takes 60%", m.getRun(a).getMoney() - ma, 12);
        checkInt("others split 40% equally (B)", m.getRun(b).getMoney() - mb, 4);
        checkInt("others split 40% equally (C)", m.getRun(c).getMoney() - mc, 4);
        checkInt("payout empties the gauge", table.getGluttonyGauge(), 0);
        int settled = m.getRun(a).getMoney();
        gluttony.onAnteSettled(m);
        checkInt("a repeated settle cannot double-pay", m.getRun(a).getMoney(), settled);

        // Floor remainder goes to the leader: 2 seats, uses 2/1, pool $12 -> 7+1 / 4.
        Match r = Match.create(85L, List.of("A", "B"),
                MatchConfig.defaults().withSinSelector((ante, rng) -> Sin.GLUTTONY));
        r.start();
        PlayerId ra = r.getSeats().get(0), rb = r.getSeats().get(1);
        for (int i = 0; i < 2; i++) r.getSinTableState().recordGluttonyUse(ra, GluttonyModifier.GAUGE_PER_USE);
        r.getSinTableState().recordGluttonyUse(rb, GluttonyModifier.GAUGE_PER_USE);
        int rma = r.getRun(ra).getMoney(), rmb = r.getRun(rb).getMoney();
        gluttony.onAnteSettled(r);
        checkInt("leader gets floor(60%) + remainder", r.getRun(ra).getMoney() - rma, 8);
        checkInt("other gets floor(40%)", r.getRun(rb).getMoney() - rmb, 4);

        // All tied -> the whole pool splits equally.
        Match t = Match.create(86L, List.of("A", "B"),
                MatchConfig.defaults().withSinSelector((ante, rng) -> Sin.GLUTTONY));
        t.start();
        PlayerId ta = t.getSeats().get(0), tb = t.getSeats().get(1);
        t.getSinTableState().recordGluttonyUse(ta, GluttonyModifier.GAUGE_PER_USE);
        t.getSinTableState().recordGluttonyUse(tb, GluttonyModifier.GAUGE_PER_USE);
        int tma = t.getRun(ta).getMoney(), tmb = t.getRun(tb).getMoney();
        gluttony.onAnteSettled(t);
        check("all tied -> equal split",
                t.getRun(ta).getMoney() - tma == 4 && t.getRun(tb).getMoney() - tmb == 4);
    }

    /** Match wiring: the gauge survives small/big settles, pays when the boss settles, resets with the ante. */
    private static void gluttonyAnteLifecycle() {
        Match m = Match.create(87L, List.of("A", "B"),
                MatchConfig.defaults().withSinSelector((ante, rng) -> Sin.GLUTTONY));
        m.start();
        PlayerId a = m.getSeats().get(0), b = m.getSeats().get(1);

        m.getSinTableState().recordGluttonyUse(a, GluttonyModifier.GAUGE_PER_USE);   // uses A=1, B=0, pool $4
        for (PlayerId id : m.getSeats()) m.getRun(id).getRound().finish();
        m.toShop();
        check("small settle leaves the gauge alone", m.getSinTableState().getGluttonyGauge() == 4);
        m.nextBlind();
        for (PlayerId id : m.getSeats()) m.getRun(id).getRound().finish();
        m.toShop();
        check("big settle leaves the gauge alone", m.getSinTableState().getGluttonyGauge() == 4);
        m.nextBlind();
        int ma = m.getRun(a).getMoney(), mb = m.getRun(b).getMoney();
        for (PlayerId id : m.getSeats()) m.getRun(id).getRound().finish();
        m.toShop();   // boss settles: identical seats settle identically, so deltas differ only by the payout
        int da = m.getRun(a).getMoney() - ma, db = m.getRun(b).getMoney() - mb;
        checkInt("boss settle pays the gauge (A - B = $3 - $1)", da - db, 2);
        checkInt("the payout consumed the gauge", m.getSinTableState().getGluttonyGauge(), 0);
        m.nextBlind();   // ante rolls over: the table state resets for the next sin
        m.getSinTableState().recordGluttonyUse(a, GluttonyModifier.GAUGE_PER_USE);
        m.getRun(a).beginAnte();   // (per-run resets are separate; the table reset happened in refreshSinForAnte)
        check("a fresh ante starts with an empty tally map or the new use only",
                m.getSinTableState().gluttonyUses(a) == 1 && m.getSinTableState().getGluttonyGauge() == 4);
    }

    private static void checkInt(String label, int actual, int expected) {
        check(label + " (" + actual + ")", actual == expected);
    }

    private static void checkThrows(String label, Runnable action) {
        boolean threw = false;
        try { action.run(); } catch (RuntimeException e) { threw = true; }
        check(label, threw);
    }

    private static Run headlessWithMultiplier(BigDecimal m) {
        Run run = new Run(0L);
        run.getSinState().setPrideMultiplier(m);
        return run;
    }

    private static BlindResult result(long target, long score) {
        return new BlindResult(RoundOutcome.WON, BigDecimal.valueOf(score), BigDecimal.ZERO, target, 0, 0);
    }

    private static void check(String label, boolean ok) {
        if (!ok) failures++;
        System.out.printf("%-46s %s%n", label, ok ? "PASS" : "FAIL");
    }
}
