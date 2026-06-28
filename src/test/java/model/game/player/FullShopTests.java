package model.game.player;

import model.cards.Card;
import model.cards.DeckCard;
import model.cards.consumables.ConsumableCard;
import model.cards.consumables.ConsumableType;
import model.cards.jokers.JokerCard;
import model.cards.packs.BoosterPack;
import model.cards.packs.PackKind;
import model.cards.packs.PackSize;
import model.cards.vouchers.Voucher;
import model.cards.vouchers.Vouchers;

import java.util.List;
import java.util.Random;

/** Run-as-main harness for the full shop: weighted card row, booster packs, and voucher redemption rules. */
public final class FullShopTests {

    private static int failures = 0;

    public static void main(String[] args) {
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
        check("Myth pack opens empty (unbuilt)", new BoosterPack(PackKind.MYTH, PackSize.NORMAL).open(pr, new Random(4)).isEmpty());

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

        System.out.println(failures == 0 ? "\nALL PASS" : "\n" + failures + " FAILURE(S)");
        if (failures != 0) System.exit(1);
    }

    private static boolean sameItem(Card x, Card y) {
        if (x == null || y == null) return x == y;
        return x.getClass() == y.getClass() && x.getShopValue() == y.getShopValue();
    }

    private static void check(String label, boolean ok) {
        if (!ok) failures++;
        System.out.printf("%-50s %s%n", label, ok ? "PASS" : "FAIL");
    }

    private static void checkInt(String label, int actual, int expected) {
        check(label + " (" + actual + ")", actual == expected);
    }
}
