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

/** Run-as-main harness for the sin seam and the Pride modifier: registry wiring, the round-begin choice resolved through the injected {@link SinChoiceProvider}, the round-settled threshold check (met / not met / no-gamble), and {@link SinState} reset. */
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
        greedLadder();
        greedShopPool();
        greedSharedShop();
        greedRerollClaims();
        greedDebuffEnforcement();
        greedLadderWiring();
        lustMultiplier();
        lustWiring();
        lustShop();
        slothSpectral();
        envyCopy();
        prideAuction();

        System.out.println(failures == 0 ? "\nALL PASS" : "\n" + failures + " FAILURE(S)");
        if (failures != 0) System.exit(1);
    }

    private static void registration() {
        check("Pride registered as PrideModifier", Sins.modifierFor(Sin.PRIDE) instanceof PrideModifier);
        check("Wrath registered as WrathModifier", Sins.modifierFor(Sin.WRATH) instanceof WrathModifier);
        check("Sloth registered with a double skip grant", Sins.modifierFor(Sin.SLOTH).tagsPerSkip() == 2);
        check("Gluttony registered as GluttonyModifier", Sins.modifierFor(Sin.GLUTTONY) instanceof GluttonyModifier);
        check("Greed registered as GreedModifier", Sins.modifierFor(Sin.GREED) instanceof GreedModifier);
        check("Lust registered as LustModifier", Sins.modifierFor(Sin.LUST) instanceof LustModifier);
        check("Envy registered as EnvyModifier", Sins.modifierFor(Sin.ENVY) instanceof EnvyModifier);
        boolean allRegistered = true;
        for (Sin sin : Sin.values()) allRegistered &= Sins.modifierFor(sin) != SinModifier.NONE;
        check("all seven sins resolve to a real modifier", allRegistered);
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


    /** The chips-to-money ladder: 500 then x1.5 more per dollar, paid as crossed, reset every round. */
    private static void greedLadder() {
        Match m = Match.create(90L, List.of("A", "B"),
                MatchConfig.defaults().withSinSelector((ante, rng) -> Sin.GREED));
        m.start();
        Run run = m.getRun(m.getSeats().get(0));
        GreedModifier greed = new GreedModifier();

        int base = run.getMoney();
        greed.onHandScored(run, java.math.BigDecimal.valueOf(499));
        checkInt("499 chips pay nothing", run.getMoney() - base, 0);
        greed.onHandScored(run, java.math.BigDecimal.ONE);
        checkInt("crossing 500 pays $1", run.getMoney() - base, 1);
        greed.onHandScored(run, java.math.BigDecimal.valueOf(800));       // total 1300, rung at 1250
        checkInt("crossing 1250 pays the second dollar", run.getMoney() - base, 2);
        greed.onHandScored(run, java.math.BigDecimal.valueOf(9087));      // total 10387: rungs 2375, 4062, 6592, 10387
        checkInt("one huge hand crosses several rungs at once", run.getMoney() - base, 6);
        check("the next rung escalates x1.5 floored",
                run.getSinState().getGreedThreshold().compareTo(java.math.BigDecimal.valueOf(16079)) == 0);

        run.getSinState().beginRound();
        check("a new round counts from 0", run.getSinState().getGreedChips().signum() == 0
                && run.getSinState().getGreedThreshold().compareTo(GreedModifier.BASE_REQUIREMENT) == 0);
        greed.onHandScored(run, java.math.BigDecimal.valueOf(500));
        checkInt("the ladder pays again from 500", run.getMoney() - base, 7);
    }

    /** Greed's card row: same type mix, boosted joker rarity (30/45/25). */
    private static void greedShopPool() {
        Run run = new Run(91L);
        var setup = new model.game.shop.ShopSetup(3, model.game.shop.CatalogShopPool.INSTANCE, 0, null, 0, null);
        new GreedModifier().configureShop(run, setup);
        check("configureShop swaps in the Greed pool", setup.getCardPool() == model.game.shop.GreedShopPool.INSTANCE);

        int common = 0, uncommon = 0, rare = 0; boolean tarot = false, planet = false;
        var stream = new java.util.Random(7);
        for (int i = 0; i < 800; i++) {
            var c = model.game.shop.GreedShopPool.INSTANCE.roll(stream);
            if (c instanceof model.cards.jokers.JokerCard j) {
                switch (j.getSpec().getRarity()) {
                    case COMMON -> common++;
                    case UNCOMMON -> uncommon++;
                    case RARE -> rare++;
                    default -> { }
                }
            } else if (c instanceof model.cards.consumables.ConsumableCard cc) {
                if (cc.getSpec().getType() == model.cards.consumables.ConsumableType.TAROT) tarot = true;
                if (cc.getSpec().getType() == model.cards.consumables.ConsumableType.PLANET) planet = true;
            }
        }
        check("uncommons become the norm (" + common + "/" + uncommon + "/" + rare + ")", uncommon > common);
        check("rares are common enough to matter", rare > 80);
        check("the type mix keeps tarots and planets", tarot && planet);
    }

    /** The shared shop: a completed purchase debuffs the item in every other seat's shop, by identity. */
    private static void greedSharedShop() {
        Match m = Match.create(92L, List.of("A", "B", "C"),
                MatchConfig.defaults().withSinSelector((ante, rng) -> Sin.GREED));
        m.start();
        PlayerId a = m.getSeats().get(0), b = m.getSeats().get(1), c = m.getSeats().get(2);
        for (PlayerId id : m.getSeats()) { m.getRun(id).addMoney(50); m.getRun(id).getRound().finish(); }
        m.toShop();

        // Card row: A buys the first acquirable slot; B's and C's mirrored copies are debuffed, the rest clean.
        var shopA = m.getRun(a).getShop();
        int slot = -1;
        for (int i = 0; i < shopA.getSlotCount(); i++)
            if (shopA.getSlot(i) != null && m.getRun(a).canAcquire(shopA.getSlot(i))) { slot = i; break; }
        check("a buyable slot exists", slot >= 0);
        var bought = shopA.buy(slot);
        check("the buyer's copy stays clean", !bought.isDebuffed());
        check("other seats' copies are debuffed",
                m.getRun(b).getShop().getSlot(slot).isDebuffed() && m.getRun(c).getShop().getSlot(slot).isDebuffed());
        int other = (slot + 1) % shopA.getSlotCount();
        check("unclaimed items stay clean", !m.getRun(b).getShop().getSlot(other).isDebuffed());
        check("the claim is recorded to the buyer", m.getSinTableState().getGreedClaims().containsValue(a));

        // Pack row: A buys pack 0; B's mirrored pack is debuffed and its purchase is refused outright.
        shopA.buyPack(0);
        check("the mirrored pack is debuffed", m.getRun(b).getShop().getPack(0).isDebuffed());
        checkThrows("a debuffed pack cannot be purchased", () -> m.getRun(b).getShop().buyPack(0));

        // Voucher row: A redeems a voucher; B's mirrored copy is debuffed and redemption is refused pre-charge.
        var shopB = m.getRun(b).getShop();
        int v = -1;
        for (int i = 0; i < shopA.getVoucherCount(); i++)
            if (shopA.getVoucher(i) != null && m.getRun(a).canRedeem(shopA.getVoucher(i))) { v = i; break; }
        check("a redeemable voucher exists", v >= 0);
        shopA.redeemVoucher(v);
        check("the mirrored voucher is debuffed", shopB.getVoucher(v).isDebuffed());
        int money = m.getRun(b).getMoney();
        final int vi = v;
        checkThrows("a debuffed voucher cannot be redeemed", () -> shopB.redeemVoucher(vi));
        checkInt("the refused redemption charged nothing", m.getRun(b).getMoney(), money);
    }

    /** Claims outlive rerolls: a claimed identity reappearing in a non-buyer's reroll is re-debuffed. */
    private static void greedRerollClaims() {
        Match m = Match.create(93L, List.of("A", "B"),
                MatchConfig.defaults().withSinSelector((ante, rng) -> Sin.GREED));
        m.start();
        Run runA = m.getRun(m.getSeats().get(0)), runB = m.getRun(m.getSeats().get(1));
        var spec = model.cards.jokers.JokerSpec.named("Coveted", model.cards.jokers.Rarity.COMMON).build();
        model.game.shop.ShopPool covetedPool = stream -> new model.cards.jokers.JokerCard(spec, 4);

        new GreedModifier().onPurchase(runA, new model.cards.jokers.JokerCard(spec, 4), 4);   // A claims "Coveted"

        runB.addMoney(10);
        var shopB = new model.game.shop.Shop(runB, 9, new model.game.shop.ShopSetup(2, covetedPool, 0, null, 0, null));
        check("claims do not reach into unrolled shops by magic", !shopB.getSlot(0).isDebuffed());
        shopB.reroll();
        check("the claimed identity is re-debuffed on a non-buyer's reroll",
                shopB.getSlot(0).isDebuffed() && shopB.getSlot(1).isDebuffed());

        runA.addMoney(10);
        var shopA = new model.game.shop.Shop(runA, 9, new model.game.shop.ShopSetup(2, covetedPool, 0, null, 0, null));
        shopA.reroll();
        check("the buyer's own reappearances stay clean", !shopA.getSlot(0).isDebuffed());

        new GreedModifier().onRoundBegin(runA);
        check("claims die at round begin", m.getSinTableState().getGreedClaims().isEmpty());
    }

    /** A permanently debuffed item is dead at every active-use site until the sticker is removed. */
    private static void greedDebuffEnforcement() {
        Match m = Match.create(94L, List.of("A", "B"),
                MatchConfig.defaults().withSinSelector((ante, rng) -> Sin.GREED));
        m.start();
        PlayerId a = m.getSeats().get(0);
        Run run = m.getRun(a);

        run.createConsumable(model.cards.consumables.Tarots.THE_HERMIT.spec());
        run.getConsumables().get(0).apply(model.modifiers.Sticker.DEBUFFED);
        checkThrows("a debuffed consumable cannot be used", () -> run.useConsumable(0));

        var relic = new model.cards.relics.RelicCard(model.cards.relics.Relics.AEGIS.spec());
        relic.apply(model.modifiers.Sticker.DEBUFFED);
        checkThrows("a debuffed relic cannot be cast",
                () -> m.useRelicCard(a, relic, model.cards.relics.RelicTarget.none()));
    }

    /** Round wiring: the per-hand hook fires after scoring with the hand's own score. */
    private static void greedLadderWiring() {
        var seen = new java.util.ArrayList<java.math.BigDecimal>();
        SinModifier spy = new SinModifier() {
            @Override public void onHandScored(Run run, java.math.BigDecimal handScore) { seen.add(handScore); }
        };
        Match m = Match.create(95L, List.of("A", "B"),
                MatchConfig.defaults()
                        .withSinSelector((ante, rng) -> Sin.GREED)
                        .withSinResolver(sin -> spy));
        m.start();
        Run run = m.getRun(m.getSeats().get(0));
        run.getRound().play(new java.util.ArrayList<>(run.getRound().getHand().subList(0, 5)));
        checkInt("one played hand fires the hook once", seen.size(), 1);
        check("the hook carries the hand score", seen.get(0).signum() > 0);
    }


    /** The diversity multiplier: +0.5x per type unlocked before the hand; the unlocking hand gets nothing. */
    private static void lustMultiplier() {
        Match m = Match.create(96L, List.of("A", "B"),
                MatchConfig.defaults().withSinSelector((ante, rng) -> Sin.LUST));
        m.start();
        Run run = m.getRun(m.getSeats().get(0));
        LustModifier lust = new LustModifier();
        var hundred = java.math.BigDecimal.valueOf(100);

        check("the round's first hand is x1",
                lust.adjustHandScore(run, model.game.scoring.HandType.PAIR, hundred).compareTo(hundred) == 0);
        check("a repeat keeps the unlocked x1.5 but adds nothing",
                lust.adjustHandScore(run, model.game.scoring.HandType.PAIR, hundred)
                        .compareTo(java.math.BigDecimal.valueOf(150)) == 0);
        check("a new type still scores at the old multiplier",
                lust.adjustHandScore(run, model.game.scoring.HandType.FLUSH, hundred)
                        .compareTo(java.math.BigDecimal.valueOf(150)) == 0);
        check("two unlocked types pay x2",
                lust.adjustHandScore(run, model.game.scoring.HandType.HIGH_CARD, hundred)
                        .compareTo(java.math.BigDecimal.valueOf(200)) == 0);
        checkInt("three types unlocked", run.getSinState().lustUniqueTypes(), 3);
        check("the advertised next multiplier is x2.5",
                run.getSinState().lustMultiplier().compareTo(new java.math.BigDecimal("2.5")) == 0);

        run.getSinState().beginRound();
        check("a new round starts back at x1",
                lust.adjustHandScore(run, model.game.scoring.HandType.PAIR, hundred).compareTo(hundred) == 0);
    }

    /** Round wiring: the adjusted score is what the play result, round total, and best-hand tracker see. */
    private static void lustWiring() {
        var fixed = java.math.BigDecimal.valueOf(777);
        SinModifier spy = new SinModifier() {
            @Override public java.math.BigDecimal adjustHandScore(Run run, model.game.scoring.HandType type,
                                                                  java.math.BigDecimal handScore) { return fixed; }
        };
        Match m = Match.create(97L, List.of("A", "B"),
                MatchConfig.defaults()
                        .withSinSelector((ante, rng) -> Sin.LUST)
                        .withSinResolver(sin -> spy));
        m.start();
        Run run = m.getRun(m.getSeats().get(0));
        var result = run.getRound().play(new java.util.ArrayList<>(run.getRound().getHand().subList(0, 5)));
        check("the play result carries the adjusted score", result.handScore().compareTo(fixed) == 0);
        check("the round total is built from the adjusted score", run.getRound().getScore().compareTo(fixed) == 0);

        // End to end with the real modifier: one played hand unlocks its type for the hands after it.
        Match real = Match.create(98L, List.of("A", "B"),
                MatchConfig.defaults().withSinSelector((ante, rng) -> Sin.LUST));
        real.start();
        Run realRun = real.getRun(real.getSeats().get(0));
        realRun.getRound().play(new java.util.ArrayList<>(realRun.getRound().getHand().subList(0, 5)));
        checkInt("a real played hand unlocks its type", realRun.getSinState().lustUniqueTypes(), 1);
        check("the next hand would score x1.5",
                realRun.getSinState().lustMultiplier().compareTo(new java.math.BigDecimal("1.5")) == 0);
    }

    /** The crowded shop: +2 items in the card/pack rows only, mirrored across seats, one purchase per roll. */
    private static void lustShop() {
        Match m = Match.create(99L, List.of("A", "B"),
                MatchConfig.defaults().withSinSelector((ante, rng) -> Sin.LUST));
        m.start();
        PlayerId a = m.getSeats().get(0), b = m.getSeats().get(1);
        for (PlayerId id : m.getSeats()) { m.getRun(id).addMoney(50); m.getRun(id).getRound().finish(); }
        m.toShop();
        var shopA = m.getRun(a).getShop();
        var shopB = m.getRun(b).getShop();

        checkInt("two extra items land in the card/pack rows",
                shopA.getSlotCount() + shopA.getPackCount(), 3 + 3 + LustModifier.EXTRA_ITEMS);
        checkInt("the voucher row never grows", shopA.getVoucherCount(), 2);
        check("seats grow the same shape",
                shopA.getSlotCount() == shopB.getSlotCount() && shopA.getPackCount() == shopB.getPackCount());

        // Same seed, same split: the extra-item rolls are table-level and deterministic.
        Match m2 = Match.create(99L, List.of("A", "B"),
                MatchConfig.defaults().withSinSelector((ante, rng) -> Sin.LUST));
        m2.start();
        for (PlayerId id : m2.getSeats()) m2.getRun(id).getRound().finish();
        m2.toShop();
        var shop2 = m2.getRun(m2.getSeats().get(0)).getShop();
        check("the split replays with the seed",
                shop2.getSlotCount() == shopA.getSlotCount() && shop2.getPackCount() == shopA.getPackCount());

        // One purchase per roll state; a reroll grants a fresh allowance.
        checkInt("a fresh shop allows one purchase", shopA.purchasesRemaining(), 1);
        int slot = -1;
        for (int i = 0; i < shopA.getSlotCount(); i++)
            if (shopA.getSlot(i) != null && m.getRun(a).canAcquire(shopA.getSlot(i))) { slot = i; break; }
        check("a buyable slot exists", slot >= 0);
        shopA.buy(slot);
        checkInt("the allowance is spent", shopA.purchasesRemaining(), 0);
        int next = -1;
        for (int i = 0; i < shopA.getSlotCount(); i++)
            if (shopA.getSlot(i) != null && m.getRun(a).canAcquire(shopA.getSlot(i))) { next = i; break; }
        final int ni = next;
        if (ni >= 0) checkThrows("a second purchase this roll is refused", () -> shopA.buy(ni));
        shopA.reroll();
        checkInt("a reroll grants a fresh allowance", shopA.purchasesRemaining(), 1);
    }


    /** Sloth: a shop visit with zero purchases offers a random spectral at close; lost if slots are full. */
    private static void slothSpectral() {
        Match m = Match.create(100L, List.of("A", "B"),
                MatchConfig.defaults().withSinSelector((ante, rng) -> Sin.SLOTH));
        m.start();
        PlayerId a = m.getSeats().get(0), b = m.getSeats().get(1);
        for (PlayerId id : m.getSeats()) { m.getRun(id).addMoney(50); m.getRun(id).getRound().finish(); }
        m.toShop();
        m.getRun(a).getShop().buyPack(0);   // A buys (inert unopened pack); B buys nothing
        m.nextBlind();
        checkInt("a buyer gets no spectral", m.getRun(a).getConsumables().size(), 0);
        checkInt("an empty visit offers a spectral", m.getRun(b).getConsumables().size(), 1);
        check("the offer is a spectral", m.getRun(b).getConsumables().get(0).getSpec().getType()
                == model.cards.consumables.ConsumableType.SPECTRAL);

        // Full consumable area: the offer is silently lost.
        m.getRun(b).createConsumable(model.cards.consumables.Tarots.THE_HERMIT.spec());   // B now at 2/2
        for (PlayerId id : m.getSeats()) m.getRun(id).getRound().finish();
        m.toShop();
        m.nextBlind();   // neither bought anything
        checkInt("a full area loses the offer", m.getRun(b).getConsumables().size(), 2);
        checkInt("A's empty visit still pays", m.getRun(a).getConsumables().size(), 1);
    }

    /** Envy: purchases are logged openly and copyable by others at twice the price paid. */
    private static void envyCopy() {
        Match m = Match.create(101L, List.of("A", "B"),
                MatchConfig.defaults().withSinSelector((ante, rng) -> Sin.ENVY));
        m.start();
        PlayerId a = m.getSeats().get(0), b = m.getSeats().get(1);
        for (PlayerId id : m.getSeats()) { m.getRun(id).addMoney(50); m.getRun(id).getRound().finish(); }
        m.toShop();

        var shopA = m.getRun(a).getShop();
        int slot = -1;
        for (int i = 0; i < shopA.getSlotCount(); i++)
            if (shopA.getSlot(i) != null && m.getRun(a).canAcquire(shopA.getSlot(i))) { slot = i; break; }
        int price = shopA.slotPrice(slot);
        shopA.buy(slot);
        var log = m.getSinTableState().getEnvyLog();
        checkInt("the purchase is logged", log.size(), 1);
        check("the log carries buyer and price", log.get(0).buyer().equals(a) && log.get(0).pricePaid() == price);

        int money = m.getRun(b).getMoney();
        var copy = m.envyCopyPurchase(b, 0);
        checkInt("the copy costs twice the price paid", money - m.getRun(b).getMoney(), price * 2);
        check("the copy is a fresh item of the same identity",
                copy != log.get(0).item() && GreedModifier.identityOf(copy).equals(GreedModifier.identityOf(log.get(0).item())));
        checkInt("copies never enter the log", m.getSinTableState().getEnvyLog().size(), 1);
        checkThrows("own purchases cannot be copied", () -> m.envyCopyPurchase(a, 0));

        // Pack copies arrive unopened as a pending pack.
        shopA.buyPack(0);
        m.envyCopyPurchase(b, 1);
        checkInt("a copied pack is pending", m.getRun(b).getPendingPacks().size(), 1);

        m.nextBlind();
        check("the log dies at round begin", m.getSinTableState().getEnvyLog().isEmpty());
        checkThrows("copying is shop-phase only", () -> m.envyCopyPurchase(b, 0));
    }

    /** Pride: the table-rolled legendary goes to the highest valid standing bid; ties mean nobody wins. */
    private static void prideAuction() {
        Match m = Match.create(102L, List.of("A", "B"),
                MatchConfig.defaults().withSinSelector((ante, rng) -> Sin.PRIDE));
        m.start();
        PlayerId a = m.getSeats().get(0), b = m.getSeats().get(1);
        checkThrows("bids are shop-phase only", () -> m.prideBid(a, 5));
        for (PlayerId id : m.getSeats()) { m.getRun(id).addMoney(50); m.getRun(id).getRound().finish(); }
        m.toShop();

        var legendary = m.getSinTableState().getPrideLegendary();
        check("a legendary is on the block", legendary != null
                && legendary.getSpec().getRarity() == model.cards.jokers.Rarity.LEGENDARY);
        Match replay = Match.create(102L, List.of("A", "B"),
                MatchConfig.defaults().withSinSelector((ante, rng) -> Sin.PRIDE));
        replay.start();
        for (PlayerId id : replay.getSeats()) replay.getRun(id).getRound().finish();
        replay.toShop();
        check("the roll replays with the seed", replay.getSinTableState().getPrideLegendary()
                .getSpec().getName().equals(legendary.getSpec().getName()));

        checkThrows("cannot bid more than you have", () -> m.prideBid(a, 1000));
        m.prideBid(a, 12);
        m.prideBid(b, 8);
        int moneyA = m.getRun(a).getMoney(), moneyB = m.getRun(b).getMoney(), boardA = m.getRun(a).board().size();
        m.nextBlind();
        checkInt("the winner pays the bid", moneyA - m.getRun(a).getMoney(), 12);
        checkInt("the winner boards the legendary", m.getRun(a).board().size(), boardA + 1);
        checkInt("losers pay nothing", m.getRun(b).getMoney(), moneyB);
        check("the auction is consumed", m.getSinTableState().getPrideLegendary() == null);

        // A tie among valid leaders: the joker is too proud to be shared.
        for (PlayerId id : m.getSeats()) m.getRun(id).getRound().finish();
        m.toShop();
        m.prideBid(a, 7);
        m.prideBid(b, 7);
        int sizeA = m.getRun(a).board().size(), sizeB = m.getRun(b).board().size();
        int tMoneyA = m.getRun(a).getMoney(), tMoneyB = m.getRun(b).getMoney();
        m.nextBlind();
        check("a tie awards nobody and charges nobody",
                m.getRun(a).board().size() == sizeA && m.getRun(b).board().size() == sizeB
                        && m.getRun(a).getMoney() == tMoneyA && m.getRun(b).getMoney() == tMoneyB);

        // An insolvent leader is skipped and the next-highest wins.
        for (PlayerId id : m.getSeats()) m.getRun(id).getRound().finish();
        m.toShop();
        int allIn = m.getRun(a).getMoney();
        m.prideBid(a, allIn);
        m.prideBid(b, 3);
        m.getRun(a).getShop().reroll();   // A dips below the bid
        int wMoneyB = m.getRun(b).getMoney(), wBoardB = m.getRun(b).board().size();
        m.nextBlind();
        checkInt("the insolvent leader is skipped; next-highest pays", wMoneyB - m.getRun(b).getMoney(), 3);
        checkInt("and boards the legendary", m.getRun(b).board().size(), wBoardB + 1);
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
