package model.game.shop;

import model.items.Card;
import model.items.DeckCard;
import model.items.DeckCard.Rank;
import model.items.DeckCard.Suit;
import model.items.consumables.ConsumableCard;
import model.items.consumables.ConsumableType;
import model.items.consumables.Planets;
import model.items.consumables.Spectrals;
import model.items.packs.BoosterPack;
import model.items.packs.PackKind;
import model.items.packs.PackSize;
import model.items.vouchers.Vouchers;
import model.game.player.Run;
import model.game.scoring.HandEvaluation;
import model.game.scoring.HandEvaluator;
import model.game.scoring.HandType;
import model.game.scoring.ScoringEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Run-as-main harness for the newly-wired vouchers: each effect sets the right run knob, the shop card row shifts
 * toward Tarots/Planets, Omen Globe puts Spectrals in Arcana packs, Telescope plants your most-played Planet in a
 * Celestial pack, and Observatory adds a held Planet's Mult when scoring its hand.
 */
public final class VoucherEffectTests {

    private static int failures = 0;
    private static final HandEvaluator EVAL = new HandEvaluator();
    private static final ScoringEngine ENGINE = new ScoringEngine();

    public static void main(String[] args) {
        flags();
        shopWeights();
        omenGlobe();
        telescope();
        observatory();

        System.out.println(failures == 0 ? "\nALL PASS" : "\n" + failures + " FAILURE(S)");
        if (failures != 0) System.exit(1);
    }

    private static void flags() {
        Run r = new Run(1L);
        apply(Vouchers.TAROT_MERCHANT, r);
        checkInt("Tarot Merchant weight", r.getTarotWeightBonus(), 20);
        apply(Vouchers.TAROT_TYCOON, r);
        checkInt("Tarot Tycoon stacks on Merchant", r.getTarotWeightBonus(), 60);
        apply(Vouchers.RELIC_MERCHANT, r);
        checkInt("Relic Merchant weight", r.getRelicWeightBonus(), 20);
        apply(Vouchers.RELIC_TYCOON, r);
        checkInt("Relic Tycoon stacks on Merchant", r.getRelicWeightBonus(), 60);
        apply(Vouchers.HONE, r);   checkInt("Hone edition rate", r.getEditionRate(), 1);
        apply(Vouchers.GLOW_UP, r); checkInt("Glow Up edition rate", r.getEditionRate(), 2);
        apply(Vouchers.HONE, r);   checkInt("Hone never downgrades Glow Up", r.getEditionRate(), 2);
        apply(Vouchers.OMEN_GLOBE, r); check("Omen Globe flag", r.hasOmenGlobe());
        apply(Vouchers.OBSERVATORY, r); check("Observatory keeps Telescope", r.hasTelescope() && r.hasObservatory());
        apply(Vouchers.SHOWMAN, r); check("Showman flag", r.isShowman());
        apply(Vouchers.ENCORE, r);  check("Encore flag", r.isEncore());
    }

    private static void shopWeights() {
        int plain = countTarots(new Run(2L));
        Run merch = new Run(2L);
        apply(Vouchers.TAROT_MERCHANT, merch);
        apply(Vouchers.TAROT_TYCOON, merch);
        check("Tarot vouchers raise the shop's Tarot share", countTarots(merch) > plain);

        check("Relics appear in the base shop", countRelics(new Run(2L)) > 0);
        Run relicMerch = new Run(2L);
        apply(Vouchers.RELIC_MERCHANT, relicMerch);
        apply(Vouchers.RELIC_TYCOON, relicMerch);
        check("Relic vouchers raise the shop's Relic share", countRelics(relicMerch) > countRelics(new Run(2L)));
    }

    private static int countTarots(Run run) {
        int tarots = 0;
        for (int i = 0; i < 3000; i++) {
            Card c = CatalogShopPool.INSTANCE.roll(run, new Random(500L + i));
            if (c instanceof ConsumableCard cc && cc.getSpec().getType() == ConsumableType.TAROT) tarots++;
        }
        return tarots;
    }

    private static int countRelics(Run run) {
        int relics = 0;
        for (int i = 0; i < 3000; i++)
            if (CatalogShopPool.INSTANCE.roll(run, new Random(500L + i)) instanceof model.items.relics.RelicCard) relics++;
        return relics;
    }

    private static void omenGlobe() {
        check("no Spectrals in an Arcana pack without Omen Globe", spectralsInArcana(new Run(3L)) == 0);
        Run omen = new Run(3L);
        apply(Vouchers.OMEN_GLOBE, omen);
        check("Omen Globe puts Spectrals in Arcana packs", spectralsInArcana(omen) > 0);
    }

    private static int spectralsInArcana(Run run) {
        int spectrals = 0;
        BoosterPack arcana = new BoosterPack(PackKind.ARCANA, PackSize.MEGA);
        for (int i = 0; i < 400; i++)
            for (Card c : arcana.open(run, new Random(700L + i)))
                if (c instanceof ConsumableCard cc && cc.getSpec().getType() == ConsumableType.SPECTRAL) spectrals++;
        return spectrals;
    }

    private static void telescope() {
        Run run = new Run(4L);
        for (int i = 0; i < 5; i++) run.getStats().recordHandPlayed(HandType.FLUSH);   // Flush is most-played
        apply(Vouchers.TELESCOPE, run);
        BoosterPack celestial = new BoosterPack(PackKind.CELESTIAL, PackSize.MEGA);
        boolean hasJupiter = false;
        for (Card c : celestial.open(run, new Random(9L)))
            if (c instanceof ConsumableCard cc && cc.getSpec() == Planets.JUPITER.spec()) hasJupiter = true;   // Jupiter = Flush
        check("Telescope plants the most-played hand's Planet in a Celestial pack", hasJupiter);
    }

    private static void observatory() {
        Run run = new Run(5L);
        run.createConsumable(Planets.JUPITER.spec());   // hold Jupiter (the Flush planet)
        List<DeckCard> flush = new ArrayList<>();
        for (Rank r : new Rank[]{Rank.TWO, Rank.FOUR, Rank.SIX, Rank.EIGHT, Rank.TEN}) flush.add(new DeckCard(r, Suit.HEARTS));

        long without = score(run, flush);
        run.setObservatory(true);
        long with = score(run, flush);
        check("Observatory adds the held Planet's hand Mult", with > without);
    }

    private static long score(Run run, List<DeckCard> played) {
        HandEvaluation e = EVAL.evaluate(played);
        long bc = run.getHandLevels().chipsFor(e.type());
        long bm = run.getHandLevels().multFor(e.type());
        return ENGINE.score(run, e.context(), bc, bm, e.scoringCards(), List.of()).score().longValueExact();
    }

    private static void apply(Vouchers v, Run r) { v.spec().getEffect().apply(r); }

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
