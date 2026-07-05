package model.cards.packs;

import model.cards.Card;
import model.cards.DeckCard;
import model.cards.DeckCard.Rank;
import model.cards.DeckCard.Suit;
import model.cards.consumables.Planets;
import model.cards.consumables.Spectrals;
import model.cards.consumables.Tarots;
import model.cards.jokers.Jokers;
import model.cards.relics.Relics;
import model.game.player.Run;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * A booster pack: a buyable (not sellable) card that, when opened, offers a set of cards of one
 * {@link PackKind} to choose from. Counts follow the master sheet; option/pick counts factor in voucher
 * bonuses (Sampler, Connoisseur). Every kind now has a content catalog (Myth packs offer Relics).
 */
public final class BoosterPack extends Card {

    private final PackKind kind;
    private final PackSize size;

    public BoosterPack(PackKind kind, PackSize size) {
        this.kind = kind;
        this.size = size;
        setShopValue(cost(size));
    }

    public PackKind kind() { return kind; }
    public PackSize size() { return size; }

    private static int cost(PackSize size) {
        return switch (size) { case NORMAL -> 4; case JUMBO -> 6; case MEGA -> 8; };
    }

    /** Cards offered to choose from, before any voucher bonus. Buffoon/Spectral packs offer fewer. */
    public int baseOptionCount() {
        boolean small = kind == PackKind.BUFFOON || kind == PackKind.SPECTRAL;
        return switch (size) {
            case NORMAL      -> small ? 2 : 3;
            case JUMBO, MEGA -> small ? 4 : 5;
        };
    }

    /** How many offered cards the player keeps, before any voucher bonus (Mega keeps 2). */
    public int basePickCount() { return size == PackSize.MEGA ? 2 : 1; }

    /** Options to choose from, factoring the run's pack bonus (Sampler). */
    public List<Card> open(Run run, RandomGenerator stream) {
        int count = baseOptionCount() + run.getPackOptionBonus();
        List<Card> options = new ArrayList<>();
        for (int i = 0; i < count; i++) options.add(generate(stream));
        return options;
    }

    /** Opens this pack into a {@link PackOpening}: the generated options plus the pick budget. */
    public PackOpening openFor(Run run, RandomGenerator stream) {
        return new PackOpening(this, open(run, stream), pickCount(run));
    }

    /** How many cards the player keeps, factoring the run's bonus (Connoisseur, Mega only). */
    public int pickCount(Run run) {
        return basePickCount() + (size == PackSize.MEGA ? run.getPackMegaPickBonus() : 0);
    }

    private Card generate(RandomGenerator stream) {
        return switch (kind) {
            case ARCANA    -> Tarots.random(stream).make();
            case CELESTIAL -> Planets.random(stream).make();
            case BUFFOON   -> Jokers.weightedRandom(stream).make();
            case SPECTRAL  -> Spectrals.random(stream).make();
            case MYTH      -> Relics.random(stream).make();
            case STANDARD  -> {
                DeckCard d = new DeckCard(
                        Rank.values()[stream.nextInt(Rank.values().length)],
                        Suit.values()[stream.nextInt(Suit.values().length)]);
                d.setShopValue(1);
                yield d;
            }
        };
    }
}
