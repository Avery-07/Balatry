package model.cards.consumables;

import model.cards.DeckCard;
import model.cards.DeckCard.Rank;
import model.cards.DeckCard.Suit;
import model.cards.jokers.JokerCard;
import model.cards.jokers.JokerSpec;
import model.cards.jokers.Rarity;
import model.game.player.Run;
import model.game.rng.Rng;
import model.game.rng.RngSource;
import model.modifiers.Edition;
import model.modifiers.Enhancement;

import java.util.List;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;

/** Run-as-main harness for the 22 Tarot cards: enhancements, conversions, money, creation, and the probabilistic editions. */
public final class TarotTests {

    private static int failures = 0;

    public static void main(String[] args) {
        check("all 22 tarots present", Tarots.values().length == 22);

        enhancements();
        conversions();
        money();
        creation();
        probabilistic();

        System.out.println(failures == 0 ? "\nALL PASS" : "\n" + failures + " FAILURE(S)");
        if (failures != 0) System.exit(1);
    }

    // --- enhancement tarots ---
    private static void enhancements() {
        DeckCard a = card(Rank.TWO, Suit.SPADES), b = card(Rank.THREE, Suit.SPADES);
        use(new Run(1L), Tarots.THE_MAGICIAN, List.of(a, b));
        check("Magician -> 2 Lucky", a.getEnhancement() == Enhancement.LUCKY && b.getEnhancement() == Enhancement.LUCKY);

        DeckCard c = card(Rank.TWO, Suit.SPADES), d = card(Rank.THREE, Suit.SPADES);
        use(new Run(1L), Tarots.THE_EMPRESS, List.of(c, d));
        check("Empress -> 2 Mult", c.getEnhancement() == Enhancement.MULT && d.getEnhancement() == Enhancement.MULT);

        DeckCard e = card(Rank.TWO, Suit.SPADES), f = card(Rank.THREE, Suit.SPADES);
        use(new Run(1L), Tarots.THE_HIEROPHANT, List.of(e, f));
        check("Hierophant -> 2 Bonus", e.getEnhancement() == Enhancement.BONUS && f.getEnhancement() == Enhancement.BONUS);

        // The Lovers: new effect enhances 2 cards into Wild (base game enhanced only 1)
        DeckCard g = card(Rank.TWO, Suit.SPADES), h = card(Rank.THREE, Suit.SPADES);
        use(new Run(1L), Tarots.THE_LOVERS, List.of(g, h));
        check("Lovers -> 2 Wild (new effect)", g.getEnhancement() == Enhancement.WILD && h.getEnhancement() == Enhancement.WILD);

        // The Tower: new effect enhances 2 cards into Stone
        DeckCard i = card(Rank.TWO, Suit.SPADES), j = card(Rank.THREE, Suit.SPADES);
        use(new Run(1L), Tarots.THE_TOWER, List.of(i, j));
        check("Tower -> 2 Stone (new effect)", i.getEnhancement() == Enhancement.STONE && j.getEnhancement() == Enhancement.STONE);

        // single-card enhancers apply to the first selection only
        DeckCard k = card(Rank.TWO, Suit.SPADES), spare = card(Rank.THREE, Suit.SPADES);
        use(new Run(1L), Tarots.THE_CHARIOT, List.of(k, spare));
        check("Chariot -> 1 Steel (only first)", k.getEnhancement() == Enhancement.STEEL && spare.getEnhancement() == null);

        DeckCard l = card(Rank.TWO, Suit.SPADES);
        use(new Run(1L), Tarots.JUSTICE, List.of(l));
        check("Justice -> Glass", l.getEnhancement() == Enhancement.GLASS);

        DeckCard m = card(Rank.TWO, Suit.SPADES);
        use(new Run(1L), Tarots.THE_DEVIL, List.of(m));
        check("Devil -> Gold", m.getEnhancement() == Enhancement.GOLD);
    }

    // --- rank/suit conversions ---
    private static void conversions() {
        DeckCard five = card(Rank.FIVE, Suit.SPADES), ace = card(Rank.ACE, Suit.SPADES);
        use(new Run(1L), Tarots.STRENGTH, List.of(five, ace));
        check("Strength -> rank +1 (Five->Six)", five.getRank() == Rank.SIX);
        check("Strength -> Ace wraps to Two", ace.getRank() == Rank.TWO);

        Run hm = new Run(1L);
        DeckCard d1 = card(Rank.TWO, Suit.SPADES), d2 = card(Rank.THREE, Suit.SPADES), keep = card(Rank.FOUR, Suit.SPADES);
        hm.addCardToDeck(d1); hm.addCardToDeck(d2); hm.addCardToDeck(keep);
        use(hm, Tarots.THE_HANGED_MAN, List.of(d1, d2));
        check("Hanged Man -> 2 cards destroyed from deck", hm.getDeck().size() == 1 && hm.getDeck().contains(keep));

        DeckCard left = card(Rank.TWO, Suit.SPADES);
        DeckCard right = card(Rank.KING, Suit.HEARTS);
        right.apply(Enhancement.GOLD);
        use(new Run(1L), Tarots.DEATH, List.of(left, right));
        check("Death -> left copies right (rank/suit/enh)",
                left.getRank() == Rank.KING && left.getSuit() == Suit.HEARTS && left.getEnhancement() == Enhancement.GOLD);

        // The World: new effect sets left+middle suit to the right card's suit
        DeckCard wa = card(Rank.TWO, Suit.SPADES), wb = card(Rank.THREE, Suit.CLUBS), wc = card(Rank.FOUR, Suit.DIAMONDS);
        use(new Run(1L), Tarots.THE_WORLD, List.of(wa, wb, wc));
        check("World -> left+middle take right's suit", wa.getSuit() == Suit.DIAMONDS && wb.getSuit() == Suit.DIAMONDS);
    }

    // --- money tarots ---
    private static void money() {
        Run hermitLow = new Run(1L); hermitLow.addMoney(15);
        use(hermitLow, Tarots.THE_HERMIT, List.of());
        check("Hermit -> doubles (15 -> 30)", hermitLow.getMoney() == 30);

        Run hermitCap = new Run(1L); hermitCap.addMoney(50);
        use(hermitCap, Tarots.THE_HERMIT, List.of());
        check("Hermit -> gain capped at $20 (50 -> 70)", hermitCap.getMoney() == 70);

        Run temp = new Run(1L);
        temp.board().add(new JokerCard(stub(), 10));   // sell value 5
        temp.board().add(new JokerCard(stub(), 10));   // sell value 5
        use(temp, Tarots.TEMPERANCE, List.of());
        check("Temperance -> sum of joker sell values ($10)", temp.getMoney() == 10);
    }

    // --- creation tarots (exercise the slot-freed-on-use fix) ---
    private static void creation() {
        Run hp = new Run(1L);
        use(hp, Tarots.THE_HIGH_PRIESTESS, List.of());
        check("High Priestess -> 2 planets created", hp.getConsumables().size() == 2
                && hp.getConsumables().stream().allMatch(c -> c.getSpec().getType() == ConsumableType.PLANET));

        Run emp = new Run(1L);
        use(emp, Tarots.THE_EMPEROR, List.of());
        check("Emperor -> 2 tarots created", emp.getConsumables().size() == 2
                && emp.getConsumables().stream().allMatch(c -> c.getSpec().getType() == ConsumableType.TAROT));

        Run judge = new Run(1L);
        use(judge, Tarots.JUDGEMENT, List.of());
        check("Judgement -> 1 joker created", judge.getJokers().size() == 1);

        // The Fool recreates the last Tarot/Planet used this run (here: Mercury)
        Run fool = new Run(1L);
        fool.addConsumable(Planets.MERCURY.make());
        fool.useConsumable(0);   // levels Pair, records Mercury as last Tarot/Planet
        use(fool, Tarots.THE_FOOL, List.of());
        check("Fool -> recreates last planet (Mercury)", fool.getConsumables().size() == 1
                && fool.getConsumables().get(0).getSpec() == Planets.MERCURY.spec());
    }

    // --- probabilistic tarots (forced via a scripted Rng) ---
    private static void probabilistic() {
        Run wheel = new Run(new ScriptedRng(true));
        use(wheel, Tarots.THE_WHEEL_OF_FORTUNE, List.of());
        check("Wheel of Fortune (hit) -> +$15", wheel.getMoney() == 15);

        // The Star: with one candidate (a joker) and the roll forced, that joker goes Negative
        Run star = new Run(new ScriptedRng(true));
        JokerCard starTarget = new JokerCard(stub(), 4);
        star.board().add(starTarget);
        use(star, Tarots.THE_STAR, List.of());
        check("Star (hit) -> a card becomes Negative", starTarget.getEdition() == Edition.NEGATIVE);

        // The Moon: held card gains a shiny edition (single held card via hand size 1)
        Run moon = new Run(new ScriptedRng(true));
        moon.setHandSize(1);
        moon.addCardToDeck(card(Rank.SEVEN, Suit.SPADES));
        moon.beginRound(300);
        use(moon, Tarots.THE_MOON, List.of());
        check("Moon (hit) -> held card gains a shiny edition", isShiny(moon.getHeld().get(0).getEdition()));

        // The Sun: a joker gains a shiny edition
        Run sun = new Run(new ScriptedRng(true));
        JokerCard sunTarget = new JokerCard(stub(), 4);
        sun.board().add(sunTarget);
        use(sun, Tarots.THE_SUN, List.of());
        check("Sun (hit) -> joker gains a shiny edition", isShiny(sunTarget.getEdition()));
    }

    // --- helpers ---

    private static void use(Run run, Tarots tarot, List<DeckCard> targets) {
        run.addConsumable(tarot.make());
        run.useConsumable(run.getConsumables().size() - 1, targets);
    }

    private static DeckCard card(Rank rank, Suit suit) { return new DeckCard(rank, suit); }

    private static JokerSpec stub() { return JokerSpec.named("Stub", Rarity.COMMON).build(); }

    private static boolean isShiny(Edition e) {
        return e == Edition.FOIL || e == Edition.HOLOGRAPHIC || e == Edition.POLYCHROME;
    }

    private static void check(String label, boolean ok) {
        if (!ok) failures++;
        System.out.printf("%-50s %s%n", label, ok ? "PASS" : "FAIL");
    }

    /** Forces every chance roll to a fixed outcome; delegates value draws to a fixed stream for determinism. */
    private static final class ScriptedRng implements Rng {
        private final boolean chanceResult;
        ScriptedRng(boolean chanceResult) { this.chanceResult = chanceResult; }

        @Override public double nextDouble(RngSource source, long salt) { return new SplittableRandom(salt).nextDouble(); }
        @Override public int nextInt(RngSource source, long salt, int bound) { return new SplittableRandom(salt).nextInt(bound); }
        @Override public boolean chance(RngSource source, long salt, int numerator, int denominator) { return chanceResult; }
        @Override public RandomGenerator streamFor(RngSource source, long salt) { return new SplittableRandom(salt); }
    }
}
