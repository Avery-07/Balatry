package model.items.consumables;

import model.items.Card;
import model.items.DeckCard;
import model.items.DeckCard.Rank;
import model.items.DeckCard.Suit;
import model.items.jokers.JokerCard;
import model.items.jokers.JokerSpec;
import model.items.jokers.Jokers;
import model.items.jokers.Rarity;
import model.game.player.Run;
import model.game.rng.RngSource;
import model.game.scoring.HandType;
import model.modifiers.Edition;
import model.modifiers.Enhancement;
import model.modifiers.Seal;
import model.modifiers.Sticker;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/** The nineteen Spectral cards (the Balatry effect where the master sheet diverges, otherwise the base effect). */
public enum Spectrals {

    FAMILIAR("Familiar", "Destroys up to 3 random cards in hand, then adds 3 random enhanced face cards.", (run, self) -> {
        RandomGenerator s = gen(run);
        destroyRandomHeld(run, 3, s);
        for (int i = 0; i < 3; i++) run.addCardToHand(enhancedFace(s));
    }),
    GRIM("Grim", "Destroys up to 2 random cards in hand, then adds 2 random enhanced Aces.", (run, self) -> {
        RandomGenerator s = gen(run);
        destroyRandomHeld(run, 2, s);
        for (int i = 0; i < 2; i++) run.addCardToHand(enhanced(Rank.ACE, s));
    }),
    INCANTATION("Incantation", "Destroys up to 4 random cards in hand, then adds 4 random enhanced numbered cards.", (run, self) -> {
        RandomGenerator s = gen(run);
        destroyRandomHeld(run, 4, s);
        for (int i = 0; i < 4; i++) run.addCardToHand(enhancedNumbered(s));
    }),
    TALISMAN("Talisman", "Adds a Gold Seal to 1 selected card.", (run, self) -> sealFirst(run, Seal.GOLD_SEAL)),
    AURA("Aura", "Gives 1 selected card a random shiny edition (Foil, Holo, Poly, or Negative).", (run, self) -> {
        List<Card> t = run.getConsumableTargets();   // any selected card: deck card, joker, or consumable
        if (t.isEmpty()) return;
        t.get(0).apply(randomAuraEdition(gen(run)));
    }),
    WRAITH("Wraith", "Creates a random Rare joker, but divides your money by 3.", (run, self) -> {
        run.createJoker(Jokers.randomOfRarity(Rarity.RARE, gen(run)).make());
        run.addMoney(run.getMoney() / 3 - run.getMoney());   // divide money by 3
    }),
    SIGIL("Sigil", "Converts up to 5 selected cards to the suit of the first.", (run, self) -> {
        List<DeckCard> t = run.getDeckCardTargets();
        if (t.isEmpty()) return;
        Suit suit = t.get(0).getSuit();
        for (int i = 1; i < Math.min(5, t.size()); i++) t.get(i).setSuit(suit);
    }),
    OUIJA("Ouija", "Converts up to 4 selected cards to the rank of the first.", (run, self) -> {
        List<DeckCard> t = run.getDeckCardTargets();
        if (t.isEmpty()) return;
        Rank rank = t.get(0).getRank();
        for (int i = 1; i < Math.min(4, t.size()); i++) t.get(i).setRank(rank);
    }),
    ECTOPLASM("Ectoplasm", "Gives a random joker a Negative edition, and −1 hand size.", (run, self) -> {
        List<JokerCard> jokers = run.getJokers();
        if (!jokers.isEmpty()) jokers.get(gen(run).nextInt(jokers.size())).apply(Edition.NEGATIVE);
        run.setHandSize(run.getHandSize() - 1);
    }),
    IMMOLATE("Immolate", "Destroys up to 5 random cards in hand, then gives $20.", (run, self) -> {
        destroyRandomHeld(run, 5, gen(run));
        run.addMoney(20);
    }),
    ANKH("Ankh", "Creates a copy of a random joker, then destroys a random joker.", (run, self) -> {
        List<JokerCard> jokers = run.getJokers();
        if (jokers.isEmpty()) return;
        RandomGenerator s = gen(run);
        JokerCard source = jokers.get(s.nextInt(jokers.size()));
        JokerSpec spec = source.getSpec();
        int value = source.getShopValue();
        run.destroyJoker(jokers.get(s.nextInt(jokers.size())));   // destroys one
        run.createJoker(new JokerCard(spec, value));              // non-negative copy
    }),
    DEJA_VU("Déjà Vu", "Adds a Red Seal to 1 selected card.", (run, self) -> sealFirst(run, Seal.RED_SEAL)),
    HEX("Hex", "Gives a random joker Polychrome, then destroys a random joker.", (run, self) -> {
        List<JokerCard> jokers = run.getJokers();
        if (jokers.isEmpty()) return;
        RandomGenerator s = gen(run);
        int i = s.nextInt(jokers.size());
        jokers.get(i).apply(Edition.POLYCHROME);
        if (jokers.size() > 1) {
            int v = s.nextInt(jokers.size() - 1);
            if (v >= i) v++;                       // destroy one other joker
            run.destroyJoker(jokers.get(v));
        }
    }),
    TRANCE("Trance", "Adds a Blue Seal to 1 selected card.", (run, self) -> sealFirst(run, Seal.BLUE_SEAL)),
    MEDIUM("Medium", "Adds a Green Seal to 1 selected card.", (run, self) -> sealFirst(run, Seal.GREEN_SEAL)),
    EXORCISM("Exorcism", "Removes a random sticker from 1 selected card, joker, or consumable.", (run, self) -> {
        List<Card> t = run.getConsumableTargets();   // any selected card: deck card, joker, or consumable
        if (t.isEmpty()) return;
        Card card = t.get(0);
        List<Sticker> stickers = new ArrayList<>(card.getStickers().keySet());
        if (stickers.isEmpty()) return;
        card.remove(stickers.get(gen(run).nextInt(stickers.size())));
    }),
    CRYPTID("Cryptid", "Creates 2 copies of 1 selected card in your hand.", (run, self) -> {
        List<DeckCard> t = run.getDeckCardTargets();
        if (t.isEmpty()) return;
        for (int i = 0; i < 2; i++) run.addCardToHand(copyOf(t.get(0)));
    }),
    THE_SOUL("The Soul", 0, "Creates a random Legendary joker (if you have room).", (run, self) -> run.createJoker(Jokers.randomOfRarity(Rarity.LEGENDARY, gen(run)).make())),
    BLACK_HOLE("Black Hole", 0, "Levels up every hand type by 1 (your most-played gains an extra level).", (run, self) -> {
        for (HandType h : HandType.values()) run.levelUpHand(h);
        HandType mostPlayed = run.getStats().getMostPlayedHand();
        if (mostPlayed != null) run.levelUpHand(mostPlayed);   // most-played hand gains a second level (+2 total)
    });

    private static final int COST = 4;
    private static final int STANDARD_WEIGHT = 1;   // The Soul and Black Hole override this to 0

    private static final Rank[] FACE = { Rank.JACK, Rank.QUEEN, Rank.KING };
    private static final Rank[] NUMBERED = {
            Rank.TWO, Rank.THREE, Rank.FOUR, Rank.FIVE, Rank.SIX, Rank.SEVEN, Rank.EIGHT, Rank.NINE, Rank.TEN
    };

    private final ConsumableSpec spec;
    private final int weight;   // appearance weight in normal spectral generation (0 = excluded, e.g. The Soul / Black Hole)

    Spectrals(String displayName, String description, ConsumableEffect effect) {
        this(displayName, STANDARD_WEIGHT, description, effect);
    }

    Spectrals(String displayName, int weight, String description, ConsumableEffect effect) {
        this.spec = new ConsumableSpec(displayName, ConsumableType.SPECTRAL, COST, description, effect);
        this.weight = weight;
    }

    public ConsumableSpec spec() { return spec; }
    public int weight()          { return weight; }

    /** A fresh card for this spectral at its spec's price. */
    public ConsumableCard make() { return new ConsumableCard(spec); }

    /** A random spectral by appearance weight; weight-0 entries (The Soul, Black Hole) never appear here. */
    public static Spectrals random(RandomGenerator stream) {
        int total = 0;
        for (Spectrals s : values()) total += s.weight;
        int roll = stream.nextInt(total);
        for (Spectrals s : values()) { roll -= s.weight; if (roll < 0) return s; }
        return values()[0];   // unreachable
    }

    // --- effect helpers ---

    private static RandomGenerator gen(Run run) {
        return run.getRng().streamFor(RngSource.SPECTRAL_GENERATION, run.nextSalt(RngSource.SPECTRAL_GENERATION));
    }

    private static void sealFirst(Run run, Seal seal) {
        List<DeckCard> t = run.getDeckCardTargets();
        if (!t.isEmpty()) t.get(0).apply(seal);
    }

    /** Destroys up to {@code n} distinct random cards from the hand. */
    private static void destroyRandomHeld(Run run, int n, RandomGenerator s) {
        List<DeckCard> held = new ArrayList<>(run.getHeld());
        List<DeckCard> victims = new ArrayList<>();
        for (int k = 0; k < n && !held.isEmpty(); k++) victims.add(held.remove(s.nextInt(held.size())));
        run.destroyDeckCards(victims);
    }

    /** A new card of {@code rank} with a random suit and a random enhancement. */
    private static DeckCard enhanced(Rank rank, RandomGenerator s) {
        DeckCard c = new DeckCard(rank, Suit.values()[s.nextInt(Suit.values().length)]);
        c.apply(Enhancement.values()[s.nextInt(Enhancement.values().length)]);
        return c;
    }

    private static DeckCard enhancedFace(RandomGenerator s)     { return enhanced(FACE[s.nextInt(FACE.length)], s); }
    private static DeckCard enhancedNumbered(RandomGenerator s) { return enhanced(NUMBERED[s.nextInt(NUMBERED.length)], s); }

    private static DeckCard copyOf(DeckCard src) {
        DeckCard c = new DeckCard(src.getRank(), src.getSuit());
        c.apply(src.getEnhancement());
        c.apply(src.getSeal());
        c.apply(src.getEdition());
        return c;
    }

    private static Edition randomAuraEdition(RandomGenerator s) {
        return switch (s.nextInt(4)) {
            case 0 -> Edition.FOIL;
            case 1 -> Edition.HOLOGRAPHIC;
            case 2 -> Edition.POLYCHROME;
            default -> Edition.NEGATIVE;
        };
    }
}
