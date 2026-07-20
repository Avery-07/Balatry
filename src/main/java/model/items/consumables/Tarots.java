package model.items.consumables;

import model.items.Card;
import model.items.DeckCard;
import model.items.DeckCard.Rank;
import model.items.DeckCard.Suit;
import model.items.jokers.JokerCard;
import model.items.jokers.Jokers;
import model.game.player.Run;
import model.game.rng.RngSource;
import model.modifiers.Edition;
import model.modifiers.Enhancement;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/** The twenty-two Tarot cards. */
public enum Tarots {

    THE_FOOL("The Fool", "Creates a copy of the last Tarot or Planet you used this run.", (run, self) -> {
        ConsumableSpec last = run.getLastTarotOrPlanet();
        if (last != null) run.createConsumable(last);
    }),
    THE_MAGICIAN("The Magician", "Enhances up to 2 selected cards into Lucky cards.", 1, (run, self) -> enhance(run, 2, Enhancement.LUCKY)),
    THE_HIGH_PRIESTESS("The High Priestess", "Creates 2 random Planet cards (if you have room).", (run, self) -> {
        createRandomPlanet(run);
        createRandomPlanet(run);
    }),
    THE_EMPRESS("The Empress", "Enhances up to 2 selected cards into Mult cards.", 1, (run, self) -> enhance(run, 2, Enhancement.MULT)),
    THE_EMPEROR("The Emperor", "Creates 2 random Tarot cards (if you have room).", (run, self) -> {
        createRandomTarot(run);
        createRandomTarot(run);
    }),
    THE_HIEROPHANT("The Hierophant", "Enhances up to 2 selected cards into Bonus cards.", 1, (run, self) -> enhance(run, 2, Enhancement.BONUS)),
    THE_LOVERS("The Lovers", "Enhances up to 2 selected cards into Wild cards.", 1, (run, self) -> enhance(run, 2, Enhancement.WILD)),
    THE_CHARIOT("The Chariot", "Enhances 1 selected card into a Steel card.", 1, (run, self) -> enhance(run, 1, Enhancement.STEEL)),
    JUSTICE("Justice", "Enhances 1 selected card into a Glass card.", 1, (run, self) -> enhance(run, 1, Enhancement.GLASS)),
    THE_HERMIT("The Hermit", "Doubles your money (adds up to $20).", (run, self) -> run.addMoney(Math.max(0, Math.min(run.getMoney(), 20)))),
    THE_WHEEL_OF_FORTUNE("The Wheel of Fortune", "1 in 2 chance to gain $15.", (run, self) -> {
        if (run.roll(RngSource.WHEEL_OF_FORTUNE, 1, 2)) run.addMoney(15);
    }),
    STRENGTH("Strength", "Increases the rank of up to 2 selected cards by 1.", 1, (run, self) -> {
        List<DeckCard> targets = run.getDeckCardTargets();
        Rank[] ranks = Rank.values();
        for (int i = 0; i < Math.min(2, targets.size()); i++) {
            DeckCard card = targets.get(i);
            card.setRank(ranks[(card.getRank().ordinal() + 1) % ranks.length]);   // wraps Ace -> Two
        }
    }),
    THE_HANGED_MAN("The Hanged Man", "Destroys up to 2 selected cards.", 1, (run, self) -> {
        List<DeckCard> targets = run.getDeckCardTargets();
        run.destroyDeckCards(targets.subList(0, Math.min(2, targets.size())));
    }),
    DEATH("Death", "Select 2 cards: the left becomes an exact copy of the right.", 2, (run, self) -> {
        List<DeckCard> targets = run.getDeckCardTargets();
        if (targets.size() < 2) return;
        DeckCard left = targets.get(0), right = targets.get(1);
        left.setRank(right.getRank());
        left.setSuit(right.getSuit());
        left.apply(right.getEnhancement());
        left.apply(right.getSeal());
        left.apply(right.getEdition());
    }),
    TEMPERANCE("Temperance", "Gives the total sell value of your jokers, up to $50.", (run, self) -> {
        int sum = 0;
        for (JokerCard joker : run.getJokers()) sum += joker.getSellValue();
        run.addMoney(Math.min(sum, 50));
    }),
    THE_DEVIL("The Devil", "Enhances 1 selected card into a Gold card.", 1, (run, self) -> enhance(run, 1, Enhancement.GOLD)),
    THE_TOWER("The Tower", "Enhances up to 2 selected cards into Stone cards.", 1, (run, self) -> enhance(run, 2, Enhancement.STONE)),
    THE_STAR("The Star", "1 in 8 chance to turn a random card, joker or consumable Negative.", (run, self) -> {
        if (!run.roll(RngSource.STAR_NEGATIVE, 1, 8)) return;
        List<Card> pool = new ArrayList<>();
        pool.addAll(run.getHeld());
        pool.addAll(run.getJokers());
        pool.addAll(run.getConsumables());
        pool.remove(self);   // the Star card is removed after consume; don't let it target itself
        if (pool.isEmpty()) return;
        RandomGenerator stream = run.getRng().streamFor(RngSource.STAR_NEGATIVE, run.nextSalt(RngSource.STAR_NEGATIVE));
        pool.get(stream.nextInt(pool.size())).apply(Edition.NEGATIVE);
    }),
    THE_MOON("The Moon", "1 in 4 chance to give a random card in your hand a shiny edition.", (run, self) -> {
        if (!run.roll(RngSource.MOON_EDITION, 1, 4)) return;
        List<DeckCard> held = run.getHeld();
        if (held.isEmpty()) return;
        RandomGenerator stream = run.getRng().streamFor(RngSource.MOON_EDITION, run.nextSalt(RngSource.MOON_EDITION));
        held.get(stream.nextInt(held.size())).apply(randomShinyEdition(stream));
    }),
    THE_SUN("The Sun", "1 in 4 chance to give a random joker a shiny edition.", (run, self) -> {
        if (!run.roll(RngSource.SUN_EDITION, 1, 4)) return;
        List<JokerCard> jokers = run.getJokers();
        if (jokers.isEmpty()) return;
        RandomGenerator stream = run.getRng().streamFor(RngSource.SUN_EDITION, run.nextSalt(RngSource.SUN_EDITION));
        jokers.get(stream.nextInt(jokers.size())).apply(randomShinyEdition(stream));
    }),
    JUDGEMENT("Judgement", "Creates a random joker (if you have room).", (run, self) -> createRandomJoker(run)),
    THE_WORLD("The World", "Select 3 cards: the first two take the suit of the third.", 3, (run, self) -> {
        List<DeckCard> targets = run.getDeckCardTargets();
        if (targets.size() < 3) return;
        Suit suit = targets.get(2).getSuit();
        targets.get(0).setSuit(suit);
        targets.get(1).setSuit(suit);
    });

    private static final int COST = 3;

    private final ConsumableSpec spec;

    Tarots(String displayName, String description, ConsumableEffect effect) {
        this(displayName, description, 0, effect);
    }

    /** A targeted tarot: using it demands at least {@code minTargets} selected cards, so it cannot be wasted. */
    Tarots(String displayName, String description, int minTargets, ConsumableEffect effect) {
        this.spec = new ConsumableSpec(displayName, ConsumableType.TAROT, COST, description, minTargets, effect);
    }

    public ConsumableSpec spec() { return spec; }

    /** A fresh card for this tarot at its spec's price. */
    public ConsumableCard make() { return new ConsumableCard(spec); }

    /** A uniformly random tarot (tarots carry no appearance weights). */
    public static Tarots random(RandomGenerator stream) {
        Tarots[] all = values();
        return all[stream.nextInt(all.length)];
    }

    // --- effect helpers ---

    /** Applies {@code enhancement} to the first {@code min(max, selection)} selected cards. */
    private static void enhance(Run run, int max, Enhancement enhancement) {
        List<DeckCard> targets = run.getDeckCardTargets();
        for (int i = 0; i < Math.min(max, targets.size()); i++) targets.get(i).apply(enhancement);
    }

    private static void createRandomPlanet(Run run) {
        RandomGenerator stream = run.getRng().streamFor(RngSource.PLANET_GENERATION, run.nextSalt(RngSource.PLANET_GENERATION));
        run.createConsumable(Planets.random(stream).spec());
    }

    private static void createRandomTarot(Run run) {
        RandomGenerator stream = run.getRng().streamFor(RngSource.TAROT_GENERATION, run.nextSalt(RngSource.TAROT_GENERATION));
        run.createConsumable(Tarots.random(stream).spec());
    }

    private static void createRandomJoker(Run run) {
        RandomGenerator stream = run.getRng().streamFor(RngSource.JOKER_GENERATION, run.nextSalt(RngSource.JOKER_GENERATION));
        run.createJoker(Jokers.weightedRandom(stream).make());
    }

    /** One of Foil / Holographic / Polychrome (Negative excluded), drawn from {@code stream}. */
    private static Edition randomShinyEdition(RandomGenerator stream) {
        return switch (stream.nextInt(3)) {
            case 0 -> Edition.FOIL;
            case 1 -> Edition.HOLOGRAPHIC;
            default -> Edition.POLYCHROME;
        };
    }
}
