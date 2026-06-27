package model.cards.consumables;

import model.cards.Card;
import model.cards.DeckCard;
import model.cards.DeckCard.Rank;
import model.cards.DeckCard.Suit;
import model.cards.jokers.JokerCard;
import model.cards.jokers.Jokers;
import model.game.player.Run;
import model.game.rng.RngSource;
import model.modifiers.Edition;
import model.modifiers.Enhancement;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * The twenty-two Tarot cards. Each carries the Balatry effect (the "New Effect" where the master sheet
 * diverges from the base game, otherwise the base effect). Card-targeting tarots read their selection from
 * {@link Run#getConsumableTargets()}; "up to N" effects apply to the first {@code min(N, selection)} cards.
 * Effect-created cards and probabilistic rolls draw on the run's keyed RNG so a shared seed mirrors outcomes.
 */
public enum Tarots {

    THE_FOOL("The Fool", (run, self) -> {
        ConsumableSpec last = run.getLastTarotOrPlanet();
        if (last != null) run.createConsumable(last);
    }),
    THE_MAGICIAN("The Magician", (run, self) -> enhance(run, 2, Enhancement.LUCKY)),
    THE_HIGH_PRIESTESS("The High Priestess", (run, self) -> {
        createRandomPlanet(run);
        createRandomPlanet(run);
    }),
    THE_EMPRESS("The Empress", (run, self) -> enhance(run, 2, Enhancement.MULT)),
    THE_EMPEROR("The Emperor", (run, self) -> {
        createRandomTarot(run);
        createRandomTarot(run);
    }),
    THE_HIEROPHANT("The Hierophant", (run, self) -> enhance(run, 2, Enhancement.BONUS)),
    THE_LOVERS("The Lovers", (run, self) -> enhance(run, 2, Enhancement.WILD)),
    THE_CHARIOT("The Chariot", (run, self) -> enhance(run, 1, Enhancement.STEEL)),
    JUSTICE("Justice", (run, self) -> enhance(run, 1, Enhancement.GLASS)),
    THE_HERMIT("The Hermit", (run, self) -> run.addMoney(Math.max(0, Math.min(run.getMoney(), 20)))),
    THE_WHEEL_OF_FORTUNE("The Wheel of Fortune", (run, self) -> {
        if (run.roll(RngSource.WHEEL_OF_FORTUNE, 1, 2)) run.addMoney(15);
    }),
    STRENGTH("Strength", (run, self) -> {
        List<DeckCard> targets = run.getConsumableTargets();
        Rank[] ranks = Rank.values();
        for (int i = 0; i < Math.min(2, targets.size()); i++) {
            DeckCard card = targets.get(i);
            card.setRank(ranks[(card.getRank().ordinal() + 1) % ranks.length]);   // wraps Ace -> Two
        }
    }),
    THE_HANGED_MAN("The Hanged Man", (run, self) -> {
        List<DeckCard> targets = run.getConsumableTargets();
        run.destroyDeckCards(targets.subList(0, Math.min(2, targets.size())));
    }),
    DEATH("Death", (run, self) -> {
        List<DeckCard> targets = run.getConsumableTargets();
        if (targets.size() < 2) return;
        DeckCard left = targets.get(0), right = targets.get(1);
        left.setRank(right.getRank());
        left.setSuit(right.getSuit());
        left.apply(right.getEnhancement());
        left.apply(right.getSeal());
        left.apply(right.getEdition());
    }),
    TEMPERANCE("Temperance", (run, self) -> {
        int sum = 0;
        for (JokerCard joker : run.getJokers()) sum += joker.getSellValue();
        run.addMoney(Math.min(sum, 50));
    }),
    THE_DEVIL("The Devil", (run, self) -> enhance(run, 1, Enhancement.GOLD)),
    THE_TOWER("The Tower", (run, self) -> enhance(run, 2, Enhancement.STONE)),
    THE_STAR("The Star", (run, self) -> {
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
    THE_MOON("The Moon", (run, self) -> {
        if (!run.roll(RngSource.MOON_EDITION, 1, 4)) return;
        List<DeckCard> held = run.getHeld();
        if (held.isEmpty()) return;
        RandomGenerator stream = run.getRng().streamFor(RngSource.MOON_EDITION, run.nextSalt(RngSource.MOON_EDITION));
        held.get(stream.nextInt(held.size())).apply(randomShinyEdition(stream));
    }),
    THE_SUN("The Sun", (run, self) -> {
        if (!run.roll(RngSource.SUN_EDITION, 1, 4)) return;
        List<JokerCard> jokers = run.getJokers();
        if (jokers.isEmpty()) return;
        RandomGenerator stream = run.getRng().streamFor(RngSource.SUN_EDITION, run.nextSalt(RngSource.SUN_EDITION));
        jokers.get(stream.nextInt(jokers.size())).apply(randomShinyEdition(stream));
    }),
    JUDGEMENT("Judgement", (run, self) -> createRandomJoker(run)),
    THE_WORLD("The World", (run, self) -> {
        List<DeckCard> targets = run.getConsumableTargets();
        if (targets.size() < 3) return;
        Suit suit = targets.get(2).getSuit();
        targets.get(0).setSuit(suit);
        targets.get(1).setSuit(suit);
    });

    private static final int COST = 3;

    private final ConsumableSpec spec;

    Tarots(String displayName, ConsumableEffect effect) {
        this.spec = new ConsumableSpec(displayName, ConsumableType.TAROT, COST, effect);
    }

    public ConsumableSpec spec() { return spec; }

    /** A fresh card for this tarot at its spec's price. */
    public ConsumableCard make() { return new ConsumableCard(spec); }

    // --- effect helpers ---

    /** Applies {@code enhancement} to the first {@code min(max, selection)} selected cards. */
    private static void enhance(Run run, int max, Enhancement enhancement) {
        List<DeckCard> targets = run.getConsumableTargets();
        for (int i = 0; i < Math.min(max, targets.size()); i++) targets.get(i).apply(enhancement);
    }

    private static void createRandomPlanet(Run run) {
        Planets[] all = Planets.values();
        RandomGenerator stream = run.getRng().streamFor(RngSource.PLANET_GENERATION, run.nextSalt(RngSource.PLANET_GENERATION));
        run.createConsumable(all[stream.nextInt(all.length)].spec());
    }

    private static void createRandomTarot(Run run) {
        Tarots[] all = values();
        RandomGenerator stream = run.getRng().streamFor(RngSource.TAROT_GENERATION, run.nextSalt(RngSource.TAROT_GENERATION));
        run.createConsumable(all[stream.nextInt(all.length)].spec());
    }

    private static void createRandomJoker(Run run) {
        Jokers[] all = Jokers.values();
        RandomGenerator stream = run.getRng().streamFor(RngSource.JOKER_GENERATION, run.nextSalt(RngSource.JOKER_GENERATION));
        run.createJoker(all[stream.nextInt(all.length)].make());
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
