package model.game.shop;

import model.items.Card;
import model.items.DeckCard;
import model.items.PlayingCards;
import model.items.packs.BoosterPack;
import model.items.packs.PackKind;
import model.items.packs.PackSize;
import model.items.vouchers.Vouchers;
import model.game.player.Run;

import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Run-as-main harness for playing-card generation: Standard packs offer real playing cards that roll enhancement,
 * seal and edition modifiers (stackable); the Illusion voucher raises those odds; Magic Trick adds playing cards to
 * the shop card row; and the vouchers wire the run flags. All generation is deterministic on a seeded stream.
 */
public final class PlayingCardTests {

    private static int failures = 0;

    public static void main(String[] args) {
        standardPackYieldsPlayingCards();
        modifiersRollAndStack();
        illusionBoostsModifiers();
        magicTrickAddsShopCards();
        vouchersWireTheFlags();
        deterministic();

        System.out.println(failures == 0 ? "\nALL PASS" : "\n" + failures + " FAILURE(S)");
        if (failures != 0) System.exit(1);
    }

    private static void standardPackYieldsPlayingCards() {
        Run run = new Run(1L);
        BoosterPack pack = new BoosterPack(PackKind.STANDARD, PackSize.MEGA);
        List<Card> opts = pack.open(run, new java.util.Random(7L));
        boolean allCards = !opts.isEmpty();
        for (Card c : opts) if (!(c instanceof DeckCard)) allCards = false;
        check("a Standard pack offers only playing cards", allCards);
        checkInt("a Mega Standard pack offers 5", opts.size(), 5);
    }

    private static void modifiersRollAndStack() {
        RandomGenerator s = new java.util.Random(42L);
        int enh = 0, seal = 0, ed = 0, triple = 0, total = 4000;
        for (int i = 0; i < total; i++) {
            DeckCard d = PlayingCards.rolled(s, false);
            boolean e = d.getEnhancement() != null, se = d.getSeal() != null, edn = d.getEdition() != null;
            if (e) enh++;
            if (se) seal++;
            if (edn) ed++;
            if (e && se && edn) triple++;
        }
        check("base enhancements appear", enh > 0 && enh < total);
        check("base seals appear", seal > 0 && seal < total);
        check("base editions appear", ed > 0 && ed < total);
        check("a card can stack all three modifiers", triple > 0);
        check("enhancement is the most common modifier", enh > seal && enh > ed);
    }

    private static void illusionBoostsModifiers() {
        check("Illusion raises the share of modified cards", countModified(true) > countModified(false));
    }

    private static int countModified(boolean illusion) {
        RandomGenerator s = new java.util.Random(99L);
        int mod = 0, total = 4000;
        for (int i = 0; i < total; i++) {
            DeckCard d = PlayingCards.rolled(s, illusion);
            if (d.getEnhancement() != null || d.getSeal() != null || d.getEdition() != null) mod++;
        }
        return mod;
    }

    private static void magicTrickAddsShopCards() {
        check("no playing cards in the shop without Magic Trick", countShopPlayingCards(new Run(3L)) == 0);
        Run trick = new Run(3L);
        trick.setMagicTrickCards(true);
        check("Magic Trick puts playing cards in the shop", countShopPlayingCards(trick) > 0);
    }

    private static int countShopPlayingCards(Run run) {
        int cards = 0;
        for (int i = 0; i < 2000; i++)
            if (CatalogShopPool.INSTANCE.roll(run, new java.util.Random(1000L + i)) instanceof DeckCard) cards++;
        return cards;
    }

    private static void vouchersWireTheFlags() {
        Run run = new Run(5L);
        Vouchers.MAGIC_TRICK.spec().getEffect().apply(run);
        check("Magic Trick voucher enables shop playing cards", run.isMagicTrickActive() && !run.isIllusionActive());
        Vouchers.ILLUSION.spec().getEffect().apply(run);
        check("Illusion voucher turns on the boost (keeps Magic Trick)", run.isMagicTrickActive() && run.isIllusionActive());
    }

    private static void deterministic() {
        DeckCard a = PlayingCards.rolled(new java.util.Random(123L), true);
        DeckCard b = PlayingCards.rolled(new java.util.Random(123L), true);
        check("same seed yields the same card", a.getRank() == b.getRank() && a.getSuit() == b.getSuit()
                && a.getEnhancement() == b.getEnhancement() && a.getSeal() == b.getSeal() && a.getEdition() == b.getEdition());
    }

    private static void check(String label, boolean ok) {
        System.out.printf("%-52s %s%n", label, ok ? "PASS" : "FAIL");
        if (!ok) failures++;
    }

    private static void checkInt(String label, int actual, int expected) {
        boolean ok = actual == expected;
        System.out.printf("%-52s %s%n", label, ok ? "PASS" : "FAIL (" + actual + " != " + expected + ")");
        if (!ok) failures++;
    }
}
