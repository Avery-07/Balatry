package model.game.shop;

import model.items.Card;
import model.items.DeckCard;
import model.items.DeckCard.Rank;
import model.items.DeckCard.Suit;
import model.items.consumables.ConsumableCard;
import model.items.consumables.ConsumableType;
import model.items.jokers.JokerCard;
import model.items.jokers.JokerSpec;
import model.items.jokers.Rarity;
import model.items.packs.BoosterPack;
import model.items.packs.PackKind;
import model.items.packs.PackSize;
import model.items.vouchers.Voucher;
import model.items.vouchers.Vouchers;
import model.game.Match;
import model.game.player.PlayerId;
import model.game.player.RoundOutcome;
import model.game.player.Run;
import model.modifiers.Edition;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Run-as-main harness for the shop. */
public final class ShopTests {

    private static int failures = 0;

    private static final JokerSpec SPEC = JokerSpec.named("Stub", Rarity.COMMON).build();
    private static final ShopPool JOKER_POOL = stream -> new JokerCard(SPEC, 4);
    private static final ShopPool CARD_POOL = stream -> { DeckCard d = new DeckCard(Rank.ACE, Suit.SPADES); d.setShopValue(1); return d; };
    private static final ShopPool NEG_JOKER_POOL = stream -> { JokerCard j = new JokerCard(SPEC, 4); j.apply(Edition.NEGATIVE); return j; };

    public static void main(String[] args) {
        unitChecks();
        fullShopChecks();
        modifierPassChecks();

        System.out.println(failures == 0 ? "\nALL PASS" : "\n" + failures + " FAILURE(S)");
        if (failures != 0) System.exit(1);
    }

    /** {@link Shop} unit behaviour: mirroring, buy/sell, slot limits + NEGATIVE, reroll, Match open/close. */
    private static void unitChecks() {
        // --- same seed -> identical offerings ---
        Shop sa = new Run(7L).openShop();
        Shop sb = new Run(7L).openShop();
        boolean mirrored = sa.getSlotCount() == sb.getSlotCount();
        for (int i = 0; mirrored && i < sa.getSlotCount(); i++) mirrored = sameItem(sa.getSlot(i), sb.getSlot(i));
        check("same seed -> mirrored shop", mirrored);

        Shop sc = new Run(8L).openShop();
        boolean differs = false;
        for (int i = 0; i < sa.getSlotCount(); i++) if (!sameItem(sa.getSlot(i), sc.getSlot(i))) differs = true;
        check("different seed -> different shop", differs);

        // --- buy a joker: charged, routed to inventory, slot cleared ---
        Run r1 = new Run(1L); r1.addMoney(10);
        Shop s1 = new Shop(r1, 0, 2, JOKER_POOL);
        checkInt("two slots", s1.getSlotCount(), 2);
        Card bought = s1.buy(0);
        check("bought a joker", bought instanceof JokerCard);
        checkInt("joker in inventory", r1.getJokers().size(), 1);
        checkInt("money 10 -> 6", r1.getMoney(), 6);
        check("slot cleared", s1.getSlot(0) == null);

        // --- slot limit blocks a normal joker; NEGATIVE bypasses it ---
        Run r2 = new Run(2L); r2.addMoney(100);
        for (int i = 0; i < 5; i++) r2.acquire(new JokerCard(SPEC, 4));
        checkInt("5 joker slots used", r2.usedJokerSlots(), 5);
        check("cannot add a normal joker", !r2.canAddJoker(new JokerCard(SPEC, 4)));
        checkThrows("buy blocked when full", () -> new Shop(r2, 0, 1, JOKER_POOL).buy(0));
        Card neg = new Shop(r2, 0, 1, NEG_JOKER_POOL).buy(0);
        check("negative joker bought", neg instanceof JokerCard);
        checkInt("inventory grew to 6", r2.getJokers().size(), 6);
        checkInt("used slots still 5", r2.usedJokerSlots(), 5);

        // --- reroll cost escalates and is affordability-gated ---
        Run r3 = new Run(3L); r3.addMoney(20);
        Shop s3 = new Shop(r3, 0, 2, JOKER_POOL);
        checkInt("first reroll costs 5", s3.rerollCost(), 5);
        s3.reroll();
        checkInt("money 20 -> 15", r3.getMoney(), 15);
        checkInt("next reroll costs 6", s3.rerollCost(), 6);
        s3.reroll();
        checkInt("money 15 -> 9", r3.getMoney(), 9);
        checkInt("third reroll costs 7", s3.rerollCost(), 7);
        r3.addMoney(-9);
        checkThrows("reroll blocked when broke", s3::reroll);

        // --- sell returns half the shop value and frees the slot ---
        Run r4 = new Run(4L); r4.addMoney(10);
        new Shop(r4, 0, 1, JOKER_POOL).buy(0);   // -4 -> 6
        int got = r4.sellJoker(0);
        checkInt("sell value 2", got, 2);
        checkInt("money 6 -> 8", r4.getMoney(), 8);
        checkInt("inventory empty", r4.getJokers().size(), 0);

        // --- cannot buy without money ---
        checkThrows("buy blocked when broke", () -> new Shop(new Run(5L), 0, 1, JOKER_POOL).buy(0));

        // --- a bought playing card goes to the deck ---
        Run r6 = new Run(6L); r6.addMoney(5);
        Shop s6 = new Shop(r6, 0, 1, CARD_POOL);
        Card card = s6.buy(0);
        check("bought a deck card", card instanceof DeckCard);
        checkInt("deck grew by 1", r6.getDeck().size(), 1);
        checkInt("money 5 -> 4", r6.getMoney(), 4);

        // --- purchase lifecycle: pricing grants act before validation, ON_BOUGHT fires only on completion ---
        purchaseLifecycleChecks();

        // --- Match opens a shop per seat at the barrier and closes it on leaving ---
        Match match = Match.create(50L, List.of("A", "B"));
        match.start();
        for (PlayerId id : match.getSeats()) exhaust(match, id);
        match.toShop();
        boolean opened = true;
        for (PlayerId id : match.getSeats())
            opened &= match.getRun(id).getShop() != null && match.getRun(id).getShop().getSlotCount() == 3;
        check("shop opened per seat", opened);
        match.nextBlind();
        boolean closed = true;
        for (PlayerId id : match.getSeats()) closed &= match.getRun(id).getShop() == null;
        check("shop closed on next blind", closed);
    }

    /** Full three-row shop: dimensions, weighted card row, booster packs, and voucher redemption rules. */
    private static void fullShopChecks() {
        // --- shop dimensions: 3 card / 3 pack / 2 voucher rows ---
        Shop shop = new Run(7L).openShop();
        checkInt("card slots = 3", shop.getSlotCount(), 3);
        checkInt("pack slots = 3", shop.getPackCount(), 3);
        checkInt("voucher slots = 2", shop.getVoucherCount(), 2);

        // --- card row holds only jokers/tarots/planets (no playing cards), all priced ---
        boolean cardRowOk = true, priced = true;
        Random pool = new Random(99);
        for (int i = 0; i < 400; i++) {
            Card c = CatalogShopPool.INSTANCE.roll(pool);
            boolean kindOk = c instanceof JokerCard
                    || (c instanceof ConsumableCard cc
                    && (cc.getSpec().getType() == ConsumableType.TAROT || cc.getSpec().getType() == ConsumableType.PLANET));
            cardRowOk &= kindOk && !(c instanceof DeckCard);
            priced &= c.getShopValue() >= 0;
        }
        check("card row is jokers/tarots/planets only", cardRowOk);
        check("card row items are priced", priced);

        // --- mirroring: same seed -> identical card, pack, and voucher rows ---
        Shop a = new Run(11L).openShop();
        Shop b = new Run(11L).openShop();
        boolean mirrored = true;
        for (int i = 0; i < a.getSlotCount(); i++) mirrored &= sameItem(a.getSlot(i), b.getSlot(i));
        for (int i = 0; i < a.getPackCount(); i++)
            mirrored &= a.getPack(i).kind() == b.getPack(i).kind() && a.getPack(i).size() == b.getPack(i).size();
        for (int i = 0; i < a.getVoucherCount(); i++)
            mirrored &= a.getVoucher(i).getSpec().getName().equals(b.getVoucher(i).getSpec().getName());
        check("same seed -> mirrored card/pack/voucher rows", mirrored);

        // --- booster packs: option counts, content type, pick counts ---
        Run pr = new Run(1L);
        List<Card> buffoon = new BoosterPack(PackKind.BUFFOON, PackSize.NORMAL).open(pr, new Random(1));
        check("Buffoon Normal: 2 jokers", buffoon.size() == 2 && buffoon.stream().allMatch(c -> c instanceof JokerCard));

        List<Card> arcana = new BoosterPack(PackKind.ARCANA, PackSize.JUMBO).open(pr, new Random(2));
        check("Arcana Jumbo: 5 tarots", arcana.size() == 5
                && arcana.stream().allMatch(c -> c instanceof ConsumableCard cc && cc.getSpec().getType() == ConsumableType.TAROT));

        BoosterPack celMega = new BoosterPack(PackKind.CELESTIAL, PackSize.MEGA);
        List<Card> celestial = celMega.open(pr, new Random(3));
        check("Celestial Mega: 5 planets", celestial.size() == 5
                && celestial.stream().allMatch(c -> c instanceof ConsumableCard cc && cc.getSpec().getType() == ConsumableType.PLANET));
        checkInt("Mega keeps 2", celMega.pickCount(pr), 2);

        List<Card> spectral = new BoosterPack(PackKind.SPECTRAL, PackSize.NORMAL).open(pr, new Random(4));
        check("Spectral Normal: 2 spectrals", spectral.size() == 2
                && spectral.stream().allMatch(c -> c instanceof ConsumableCard cc && cc.getSpec().getType() == ConsumableType.SPECTRAL));
        List<Card> myth = new BoosterPack(PackKind.MYTH, PackSize.NORMAL).open(pr, new Random(4));
        check("Myth Normal: 3 relics", myth.size() == 3
                && myth.stream().allMatch(c -> c instanceof model.items.relics.RelicCard));

        // Sampler bonus widens the option set
        pr.setPackOptionBonus(1);
        checkInt("Sampler: Buffoon Normal now 3 options",
                new BoosterPack(PackKind.BUFFOON, PackSize.NORMAL).open(pr, new Random(5)).size(), 3);

        // --- buying a pack charges and clears the slot ---
        Run buyer = new Run(2L); buyer.addMoney(50);
        Shop bs = buyer.openShop();
        int before = buyer.getMoney();
        BoosterPack pack0 = bs.getPack(0);
        int cost = pack0.getShopValue();
        BoosterPack bought = bs.buyPack(0);
        check("bought the pack", bought == pack0);
        checkInt("charged the pack cost", buyer.getMoney(), before - cost);
        check("pack slot cleared", bs.getPack(0) == null);

        // --- voucher rules: prerequisite, one-per-ante, effect, eligibility ---
        Run v = new Run(3L);
        int slotsBefore = v.getShopSlots();
        Voucher overstock = Vouchers.OVERSTOCK.make();
        check("Overstock redeemable", v.canRedeem(overstock));
        check("Overstock Plus blocked before base", !v.canRedeem(Vouchers.OVERSTOCK_PLUS.make()));
        v.redeemVoucher(overstock);
        checkInt("Overstock effect: +1 shop slot", v.getShopSlots(), slotsBefore + 1);
        check("Overstock not re-redeemable", !v.canRedeem(Vouchers.OVERSTOCK.make()));
        check("second voucher blocked this ante", !v.canRedeem(Vouchers.GRABBER.make()));
        v.beginAnte();
        check("Overstock Plus now allowed (base met, new ante)", v.canRedeem(Vouchers.OVERSTOCK_PLUS.make()));
        v.redeemVoucher(Vouchers.OVERSTOCK_PLUS.make());
        checkInt("Overstock Plus effect: +1 shop slot again", v.getShopSlots(), slotsBefore + 2);

        // discount voucher feeds shop pricing
        Run d = new Run(4L); d.addMoney(50);
        d.redeemVoucher(Vouchers.CLEARANCE_SALE.make());
        checkInt("Clearance Sale sets 25% off", d.getShopDiscount(), 25);

        // shop-level redemption charges and applies through the voucher row
        Run sr = new Run(5L); sr.addMoney(50);
        VoucherPool fixed = (run, stream) -> Vouchers.GRABBER.make();
        Shop vshop = new Shop(sr, 0, 0, CatalogShopPool.INSTANCE, 0, null, 1, fixed);
        int handsBefore = sr.getBaseHands(), moneyBefore = sr.getMoney();
        vshop.redeemVoucher(0);
        checkInt("Grabber via shop: +1 hand", sr.getBaseHands(), handsBefore + 1);
        checkInt("Grabber charged $10", sr.getMoney(), moneyBefore - 10);
        check("voucher slot cleared", vshop.getVoucher(0) == null);
    }

    /** The shop-modifier pass: NEXT_SHOP tags consumed into the {@link ShopSetup} (Coupon, D6, Voucher, Uncommon/Rare injection, Negative transform), rolled-position salting keeping tagged and untagged seats mirrored, the per-reroll purchase cap, and the sin's {@code configureShop} hook wired through the Match. */
    private static void modifierPassChecks() {
        // --- Coupon Tag: initial card + pack rows free; rerolled contents full price; tag consumed ---
        Run coupon = new Run(60L); coupon.addMoney(10);
        coupon.grantTag(model.game.tags.SkipTag.COUPON_TAG);
        Shop cs = coupon.openShop();
        check("Coupon consumed on shop open", coupon.getPendingTags().isEmpty());
        boolean allFree = true;
        for (int i = 0; i < cs.getSlotCount(); i++) allFree &= cs.slotPrice(i) == 0;
        for (int i = 0; i < cs.getPackCount(); i++) allFree &= cs.packPrice(i) == 0;
        check("Coupon: initial cards and packs are free", allFree);
        int before = coupon.getMoney();
        cs.buyPack(0);
        checkInt("Coupon: free pack purchase charges nothing", coupon.getMoney(), before);
        cs.reroll();   // -5
        check("Coupon: rerolled contents are full price", cs.getSlot(0) == null || cs.slotPrice(0) > 0);
        coupon.closeShop();
        check("next shop is not free", coupon.openShop().slotPrice(0) > 0);

        // --- D6 Tag: reroll cost starts at $0 (still +$1 per reroll), this shop only ---
        Run d6 = new Run(61L);
        d6.grantTag(model.game.tags.SkipTag.D6_TAG);
        Shop ds = d6.openShop();
        checkInt("D6: first reroll costs 0", ds.rerollCost(), 0);
        ds.reroll();   // affordable at $0
        checkInt("D6: second reroll costs 1", ds.rerollCost(), 1);
        d6.closeShop();
        checkInt("D6: next shop rerolls from the base again", d6.openShop().rerollCost(), 5);

        // --- Voucher Tags stack: each adds one voucher slot to the next shop ---
        Run vt = new Run(62L);
        vt.grantTag(model.game.tags.SkipTag.VOUCHER_TAG);
        vt.grantTag(model.game.tags.SkipTag.VOUCHER_TAG);
        checkInt("two Voucher Tags -> 4 voucher slots", vt.openShop().getVoucherCount(), 4);

        // --- Uncommon/Rare Tags: free leading jokers; the rolled offering behind them stays mirrored ---
        Run tagged = new Run(63L);
        tagged.grantTag(model.game.tags.SkipTag.UNCOMMON_TAG);
        tagged.grantTag(model.game.tags.SkipTag.RARE_TAG);
        Run plain = new Run(63L);
        Shop ts = tagged.openShop();
        Shop pls = plain.openShop();
        checkInt("Uncommon+Rare -> 3 extra leading slots", ts.getSlotCount(), pls.getSlotCount() + 3);
        boolean injectedOk = true;
        for (int i = 0; i < 3; i++)
            injectedOk &= ts.getSlot(i) instanceof JokerCard && ts.slotPrice(i) == 0;
        check("injected jokers are free", injectedOk);
        check("Uncommon Tag jokers are Uncommon",
                ((JokerCard) ts.getSlot(0)).getSpec().getRarity() == Rarity.UNCOMMON
                        && ((JokerCard) ts.getSlot(1)).getSpec().getRarity() == Rarity.UNCOMMON);
        check("Rare Tag joker is Rare", ((JokerCard) ts.getSlot(2)).getSpec().getRarity() == Rarity.RARE);
        boolean rolledMirrored = true;
        for (int i = 0; i < pls.getSlotCount(); i++) rolledMirrored &= sameItem(ts.getSlot(3 + i), pls.getSlot(i));
        check("rolled offering mirrors an untagged seat", rolledMirrored);
        tagged.addMoney(20); plain.addMoney(20);
        ts.reroll(); pls.reroll();
        checkInt("injected slots vanish on reroll", ts.getSlotCount(), pls.getSlotCount());
        boolean rerollMirrored = true;
        for (int i = 0; i < pls.getSlotCount(); i++) rerollMirrored &= sameItem(ts.getSlot(i), pls.getSlot(i));
        check("rerolled offering still mirrors", rerollMirrored);

        // --- Negative Tag: the next base-edition rolled joker becomes free and Negative, across rerolls ---
        int[] rolls = {0};
        ShopPool lateJokers = stream -> {
            if (rolls[0]++ < 2) { DeckCard d = new DeckCard(Rank.ACE, Suit.SPADES); d.setShopValue(1); return d; }
            return new JokerCard(SPEC, 4);
        };
        Run neg = new Run(64L); neg.addMoney(20);
        ShopSetup negSetup = new ShopSetup(2, lateJokers, 0, null, 0, null);
        negSetup.addNegativeJokerGrant();
        Shop ns = new Shop(neg, 0, negSetup);
        check("no joker rolled -> grant unconsumed", ns.getSlot(0).getEdition() == null && ns.slotPrice(0) > 0);
        ns.reroll();   // both slots roll jokers now
        check("grant survives the reroll: first joker is Negative",
                ns.getSlot(0).getEdition() == Edition.NEGATIVE);
        checkInt("transformed joker is free", ns.slotPrice(0), 0);
        check("one grant transforms one joker", ns.getSlot(1).getEdition() == null && ns.slotPrice(1) > 0);

        // --- Negative Tag skips jokers that already carry an edition ---
        ShopPool foilPool = stream -> { JokerCard j = new JokerCard(SPEC, 4); j.apply(Edition.FOIL); return j; };
        ShopSetup foilSetup = new ShopSetup(2, foilPool, 0, null, 0, null);
        foilSetup.addNegativeJokerGrant();
        Shop fs = new Shop(new Run(65L), 0, foilSetup);
        check("editioned jokers are not transformed",
                fs.getSlot(0).getEdition() == Edition.FOIL && fs.slotPrice(0) > 0);

        // --- purchase cap: one item per roll state; a reroll grants a fresh allowance; a capped buy is side-effect free ---
        Run capped = new Run(66L); capped.addMoney(30);
        ShopSetup capSetup = new ShopSetup(3, JOKER_POOL, 0, null, 0, null);
        capSetup.setMaxPurchasesPerReroll(1);
        Shop caps = new Shop(capped, 0, capSetup);
        caps.buy(0);
        int held = capped.getMoney();
        int jokers = capped.getJokers().size();
        checkThrows("second buy this roll is blocked", () -> caps.buy(1));
        check("blocked buy is side-effect free",
                capped.getMoney() == held && capped.getJokers().size() == jokers && caps.getSlot(1) != null);
        caps.reroll();
        caps.buy(0);
        checkInt("reroll grants a fresh allowance", capped.getJokers().size(), jokers + 1);

        // --- the sin's configureShop hook: wired per seat through the Match, applied before tags ---
        model.game.sins.SinModifier shopSin = new model.game.sins.SinModifier() {
            @Override public void configureShop(Run run, ShopSetup setup) {
                setup.addSlots(1);
                setup.addVouchers(1);
            }
        };
        Match sinMatch = Match.create(70L, List.of("A", "B"),
                model.game.MatchConfig.defaults()
                        .withSinSelector((ante, rng) -> model.game.Sin.GREED)
                        .withSinResolver(sin -> shopSin));
        sinMatch.start();
        for (PlayerId id : sinMatch.getSeats()) exhaust(sinMatch, id);
        sinMatch.getRun(sinMatch.getSeats().get(0)).grantTag(model.game.tags.SkipTag.VOUCHER_TAG);
        sinMatch.toShop();
        Shop seatA = sinMatch.getRun(sinMatch.getSeats().get(0)).getShop();
        Shop seatB = sinMatch.getRun(sinMatch.getSeats().get(1)).getShop();
        checkInt("sin adds a card slot on every seat", seatB.getSlotCount(), 4);
        checkInt("sin voucher on seat B", seatB.getVoucherCount(), 3);
        checkInt("sin voucher + tag voucher stack on seat A", seatA.getVoucherCount(), 4);
    }

    /** The purchase lifecycle around {@code Shop.charge}: Loyalty Card's 4th purchase is free (granted at pricing time, counted at completion), a failed buy leaves no side effects (no counter advance, no burned grant), and ON_BOUGHT never fires for a failed buy. */
    private static void purchaseLifecycleChecks() {
        // Loyalty Card: purchases 1-3 paid, 4th free, counter advances only on completed buys.
        Run loyal = new Run(30L); loyal.addMoney(100);
        JokerCard loyalty = model.items.jokers.Jokers.LOYALTY_CARD.make();
        loyal.board().add(loyalty);
        Shop ls = new Shop(loyal, 0, 4, JOKER_POOL);
        int m0 = loyal.getMoney();
        ls.buy(0); ls.buy(1); ls.buy(2);
        checkInt("first 3 purchases paid", loyal.getMoney(), m0 - 12);
        checkInt("Loyalty counted 3 purchases", loyalty.getCounter(), 3);
        ls.buy(3);
        checkInt("4th purchase free", loyal.getMoney(), m0 - 12);
        checkInt("Loyalty counted the free purchase too", loyalty.getCounter(), 4);

        // A failed buy is side-effect free: the counter does not advance...
        Run broke = new Run(31L); broke.addMoney(2);   // joker costs 4
        JokerCard loyalty2 = model.items.jokers.Jokers.LOYALTY_CARD.make();
        loyalty2.setCounter(2);                        // 2 completed purchases so far
        broke.board().add(loyalty2);
        Shop bs2 = new Shop(broke, 0, 2, JOKER_POOL);
        checkThrows("unaffordable buy throws", () -> bs2.buy(0));
        checkInt("failed buy does not advance Loyalty", loyalty2.getCounter(), 2);
        // ...and the free grant is not burned: the 3rd completed purchase is still paid, the 4th still free.
        broke.addMoney(10);
        int m1 = broke.getMoney();
        bs2.buy(0);                                    // 3rd completed purchase: paid
        checkInt("3rd purchase still paid after a failed attempt", broke.getMoney(), m1 - 4);
        bs2.buy(1);                                    // 4th completed purchase: free
        checkInt("free 4th purchase survives a failed attempt", broke.getMoney(), m1 - 4);

        // ON_BOUGHT fires only when a purchase completes.
        Run probeRun = new Run(32L);
        JokerCard boughtProbe = new JokerCard(
                JokerSpec.named("BoughtProbe", Rarity.COMMON)
                        .on(model.game.scoring.Trigger.ON_BOUGHT,
                                (run, self) -> self.setCounter(self.getCounter() + 1))
                        .build(), 0);
        probeRun.board().add(boughtProbe);
        Shop ps = new Shop(probeRun, 0, 2, JOKER_POOL);
        checkThrows("broke probe buy throws", () -> ps.buy(0));
        checkInt("no ON_BOUGHT for a failed buy", boughtProbe.getCounter(), 0);
        probeRun.addMoney(10);
        ps.buy(0);
        checkInt("ON_BOUGHT fires once per completed buy", boughtProbe.getCounter(), 1);
    }

    private static void exhaust(Match match, PlayerId id) {
        while (match.getRun(id).getRound().getOutcome() == RoundOutcome.IN_PROGRESS) {
            List<DeckCard> one = new ArrayList<>(match.getRun(id).getRound().getHand().subList(0, 1));
            match.getRun(id).getRound().play(one);
        }
    }

    private static boolean sameItem(Card x, Card y) {
        if (x == null || y == null) return x == y;
        if (x.getClass() != y.getClass() || x.getShopValue() != y.getShopValue()) return false;
        if (x instanceof DeckCard dx && y instanceof DeckCard dy) return dx.getRank() == dy.getRank() && dx.getSuit() == dy.getSuit();
        return true;
    }

    private static void check(String label, boolean ok) {
        if (!ok) failures++;
        System.out.printf("%-52s %s%n", label, ok ? "PASS" : "FAIL");
    }

    private static void checkInt(String label, int actual, int expected) {
        check(label + " (" + actual + ")", actual == expected);
    }

    private static void checkThrows(String label, Runnable action) {
        boolean threw = false;
        try { action.run(); } catch (RuntimeException e) { threw = true; }
        check(label, threw);
    }
}
