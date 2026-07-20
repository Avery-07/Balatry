package model.game;

import model.cards.Card;
import model.cards.DeckCard;
import model.cards.DeckCard.Rank;
import model.cards.DeckCard.Suit;
import model.cards.DeckType;
import model.cards.consumables.ConsumableCard;
import model.cards.consumables.ConsumableType;
import model.game.player.PlayerId;
import model.game.player.Run;
import model.game.player.SeatConfig;
import model.game.player.Sleeve;
import model.game.scoring.HandType;
import model.game.shop.CatalogShopPool;

import java.util.ArrayList;
import java.util.List;

/**
 * Run-as-main harness for the decks and sleeves whose effects live in the engine rather than in the starting
 * deal — Plasma, Bazaar, Ghost and Anaglyph, plus the Frugal and Celestial sleeves. The composition variants are
 * covered by {@code LoadoutTests}; this is about what they do once play begins.
 */
public final class LoadoutEffectTests {

    private static int failures = 0;

    public static void main(String[] args) {
        plasma();
        bazaar();
        ghostAndCelestialPools();
        anaglyph();
        frugal();
        celestialLevels();

        System.out.println(failures == 0 ? "\nALL PASS" : "\n" + failures + " FAILURE(S)");
        if (failures != 0) System.exit(1);
    }

    /** Plasma doubles every blind and averages chips against mult before they multiply. */
    private static void plasma() {
        Match plain = seatedMatch(DeckType.STANDARD);
        Match plasma = seatedMatch(DeckType.PLASMA);
        plain.start();
        plasma.start();
        PlayerId seat = new PlayerId(0);
        checkLong("Plasma doubles the blind",
                plasma.getCurrentTarget(seat), plain.getCurrentTarget(seat) * 2);

        // A lopsided hand is worth far less under Plasma; the same total split evenly is worth far more.
        Run run = plasma.getRun(seat);
        var lopsided = new model.game.scoring.ScoringResult(
                java.math.BigDecimal.valueOf(1000), java.math.BigDecimal.valueOf(100),
                java.math.BigDecimal.valueOf(10), List.of());
        var balanced = run.balanceIfPlasma(lopsided);
        check("Plasma equalises chips and mult", balanced.chips().compareTo(balanced.mult()) == 0);
        checkLong("both meet at the average", balanced.chips().longValue(), 55);
        checkLong("and the score is their product", balanced.score().longValue(), 55L * 55);

        // Every other deck leaves the result untouched.
        Run standard = plain.getRun(seat);
        check("a standard deck does not balance",
                standard.balanceIfPlasma(lopsided).score().compareTo(lopsided.score()) == 0);
    }

    /** Bazaar charges more for packs and refreshes them on a reroll; other decks leave the pack row alone. */
    private static void bazaar() {
        check("Bazaar surcharges packs", DeckType.BAZAAR.packSurcharge() == 1);
        check("other decks do not", DeckType.STANDARD.packSurcharge() == 0);
        check("Bazaar rerolls its packs", DeckType.BAZAAR.rerollRefreshesPacks());
        check("other decks keep their packs", !DeckType.STANDARD.rerollRefreshesPacks());

        Match match = seatedMatch(DeckType.BAZAAR);
        match.start();
        Run run = match.getRun(new PlayerId(0));
        run.addMoney(200);
        run.openShop();
        var shop = run.getShop();
        List<String> before = packLabels(shop);
        shop.reroll();
        check("the pack row actually changed", !packLabels(shop).equals(before));

        Match plain = seatedMatch(DeckType.STANDARD);
        plain.start();
        Run other = plain.getRun(new PlayerId(0));
        other.addMoney(200);
        other.openShop();
        var plainShop = other.getShop();
        List<String> kept = packLabels(plainShop);
        plainShop.reroll();
        check("a standard reroll leaves the packs alone", packLabels(plainShop).equals(kept));
    }

    /** Ghost lets Spectrals into the card row; Celestial keeps Planets out of it. */
    private static void ghostAndCelestialPools() {
        check("Ghost allows spectrals", DeckType.GHOST.allowsSpectralInShop());
        check("other decks do not", !DeckType.STANDARD.allowsSpectralInShop());

        checkInt("a standard shop offers planets", countType(DeckType.STANDARD, Sleeve.STANDARD, ConsumableType.PLANET) > 0 ? 1 : 0, 1);
        checkInt("a standard shop offers no spectrals", countType(DeckType.STANDARD, Sleeve.STANDARD, ConsumableType.SPECTRAL), 0);
        checkInt("a Ghost shop offers spectrals", countType(DeckType.GHOST, Sleeve.STANDARD, ConsumableType.SPECTRAL) > 0 ? 1 : 0, 1);
        checkInt("Celestial sees no planets", countType(DeckType.STANDARD, Sleeve.CELESTIAL, ConsumableType.PLANET), 0);
    }

    /** Anaglyph pays a Double Tag and a Fool for clearing a boss — and nothing for a lesser blind. */
    private static void anaglyph() {
        check("Anaglyph rewards bosses", DeckType.ANAGLYPH.rewardsBossBonus());

        Run boss = runWithDeck(DeckType.ANAGLYPH, Sleeve.STANDARD, 3L);
        var round = boss.beginRound(1);   // a target of 1 is cleared by any hand
        playFive(round);
        round.finish();
        boss.endRound(Blind.BOSS);
        checkInt("a cleared boss grants a tag", boss.getPendingTags().size(), 1);
        check("and it is the Double Tag",
                boss.getPendingTags().get(0) == model.game.tags.SkipTag.DOUBLE_TAG);
        check("and a Fool lands in the consumables", holdsFool(boss));

        Run small = runWithDeck(DeckType.ANAGLYPH, Sleeve.STANDARD, 3L);
        var smallRound = small.beginRound(1);
        playFive(smallRound);
        smallRound.finish();
        small.endRound(Blind.SMALL);
        checkInt("a small blind grants nothing extra", small.getPendingTags().size(), 0);

        Run plain = runWithDeck(DeckType.STANDARD, Sleeve.STANDARD, 3L);
        var plainRound = plain.beginRound(1);
        playFive(plainRound);
        plainRound.finish();
        plain.endRound(Blind.BOSS);
        checkInt("another deck's boss grants nothing", plain.getPendingTags().size(), 0);
    }

    /** Frugal swaps interest for a flat payout on everything left unused. */
    private static void frugal() {
        // A target of 0 is cleared the moment the round is finished, leaving every hand and discard unspent —
        // which is exactly the state Frugal pays out on.
        Run frugal = runWithDeck(DeckType.STANDARD, Sleeve.FRUGAL, 4L);
        frugal.addMoney(50);                  // plenty of interest to forgo
        int before = frugal.getMoney();
        var round = frugal.beginRound(0);
        round.finish();
        frugal.endRound(Blind.SMALL);
        int gained = frugal.getMoney() - before;

        Run normal = runWithDeck(DeckType.STANDARD, Sleeve.STANDARD, 4L);
        normal.addMoney(50);
        int normalBefore = normal.getMoney();
        var normalRound = normal.beginRound(0);
        normalRound.finish();
        normal.endRound(Blind.SMALL);
        int normalGained = normal.getMoney() - normalBefore;

        // Frugal: $3 reward + 4 hands x $2 + 3 discards x $1 = $14. Standard: $3 + 4 hands x $1 + $10 interest.
        checkInt("Frugal pays per hand and discard", gained, 3 + 4 * 2 + 3);
        check("Frugal really forgoes interest", gained != normalGained);
        check("a $50 seat would have earned interest otherwise", normalGained > 3 + 4);
    }

    /** Celestial trades shop Planets for two free levels on every hand type, each ante. */
    private static void celestialLevels() {
        Run run = runWithDeck(DeckType.STANDARD, Sleeve.CELESTIAL, 5L);
        checkInt("levels start at 1", run.getHandLevels().levelOf(HandType.PAIR), 1);
        run.beginAnte();
        checkInt("an ante grants two levels", run.getHandLevels().levelOf(HandType.PAIR), 3);
        checkInt("every type gains them", run.getHandLevels().levelOf(HandType.FLUSH), 3);
        run.beginAnte();
        checkInt("and again next ante", run.getHandLevels().levelOf(HandType.PAIR), 5);

        Run plain = runWithDeck(DeckType.STANDARD, Sleeve.STANDARD, 5L);
        plain.beginAnte();
        checkInt("another sleeve gains nothing", plain.getHandLevels().levelOf(HandType.PAIR), 1);
    }

    // --- helpers ---

    /** How many cards of {@code type} turn up across a wide sample of shop rolls for this loadout. */
    private static int countType(DeckType deck, Sleeve sleeve, ConsumableType type) {
        Run run = runWithDeck(deck, sleeve, 99L);
        int found = 0;
        for (int i = 0; i < 400; i++) {
            Card c = CatalogShopPool.INSTANCE.roll(run,
                    run.getRng().streamFor(model.game.rng.RngSource.SHOP_CONTENTS, i));
            if (c instanceof ConsumableCard con && con.getSpec().getType() == type) found++;
        }
        return found;
    }

    private static boolean holdsFool(Run run) {
        for (ConsumableCard c : run.getConsumables())
            if (c.getSpec().getName().equals("The Fool")) return true;
        return false;
    }

    private static List<String> packLabels(model.game.shop.Shop shop) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < shop.getPackCount(); i++) out.add(String.valueOf(shop.getPack(i)));
        return out;
    }

    /** A two-seat match on {@code deck}, so runs see a real match and can read the table's deck. */
    private static Match seatedMatch(DeckType deck) {
        return Match.createSeated(17L, List.of(SeatConfig.of("A"), SeatConfig.of("B")),
                MatchConfig.defaults().withDeckType(deck));
    }

    /** Seat 0 of a match on {@code deck}, with {@code sleeve} applied and a deck full enough to play hands. */
    private static Run runWithDeck(DeckType deck, Sleeve sleeve, long seed) {
        Match match = Match.createSeated(seed, List.of(
                new SeatConfig("A", sleeve, Stake.WHITE), SeatConfig.of("B")),
                MatchConfig.defaults().withDeckType(deck));
        Run run = match.getRun(new PlayerId(0));
        for (int i = 0; i < 20; i++)
            run.addCardToDeck(new DeckCard(Rank.values()[i % Rank.values().length], Suit.values()[i % 4]));
        return run;
    }

    private static void playFive(model.game.player.Round round) {
        round.play(new ArrayList<>(round.getHand().subList(0, Math.min(5, round.getHand().size()))));
    }

    private static void check(String label, boolean ok) {
        if (!ok) failures++;
        System.out.printf("%-52s %s%n", label, ok ? "PASS" : "FAIL");
    }

    private static void checkInt(String label, int actual, int expected) {
        check(label + " (" + actual + ")", actual == expected);
    }

    private static void checkLong(String label, long actual, long expected) {
        check(label + " (" + actual + ")", actual == expected);
    }
}
