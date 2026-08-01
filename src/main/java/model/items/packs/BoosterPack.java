package model.items.packs;

import model.items.Card;
import model.items.DeckCard;
import model.items.consumables.ConsumableCard;
import model.items.consumables.Planets;
import model.items.consumables.Spectrals;
import model.items.consumables.Tarots;
import model.items.jokers.Jokers;
import model.items.relics.Relics;
import model.game.player.Run;
import model.game.scoring.HandType;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/** A booster pack: a buyable (not sellable) card that, when opened, offers a set of cards of one {@link PackKind} to choose from. */
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

    /** The pack's shelf name — "Arcana Pack", "Jumbo Celestial Pack", "Mega Buffoon Pack". */
    public String displayName() {
        String sizePrefix = switch (size) { case NORMAL -> ""; case JUMBO -> "Jumbo "; case MEGA -> "Mega "; };
        String kindName = switch (kind) {
            case ARCANA -> "Arcana"; case CELESTIAL -> "Celestial"; case STANDARD -> "Standard";
            case BUFFOON -> "Buffoon"; case SPECTRAL -> "Spectral"; case MYTH -> "Myth";
        };
        return sizePrefix + kindName + " Pack";
    }

    @Override
    public String toString() { return displayName(); }   // the client shows packs via String.valueOf

    private static final int OMEN_GLOBE_SPECTRAL_SHARE = 20;   // Omen Globe: % of Arcana-pack options that become Spectral

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
        for (int i = 0; i < count; i++) options.add(generate(run, stream));
        // Telescope: a Celestial pack always offers your most-played hand's Planet (planted in the first slot).
        if (kind == PackKind.CELESTIAL && run.hasTelescope() && !options.isEmpty()) {
            HandType most = run.getStats().getMostPlayedHand();
            Planets p = most == null ? null : Planets.forHand(most);
            if (p != null && options.stream().noneMatch(c -> c instanceof ConsumableCard cc && cc.getSpec() == p.spec()))
                options.set(0, p.make());
        }
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

    private Card generate(Run run, RandomGenerator stream) {
        return switch (kind) {
            case ARCANA    -> (run.hasOmenGlobe() && stream.nextInt(100) < OMEN_GLOBE_SPECTRAL_SHARE)
                    ? Spectrals.random(stream).make() : Tarots.random(stream).make();   // Omen Globe: Spectrals in Arcana packs
            case CELESTIAL -> Planets.random(stream).make();
            case BUFFOON   -> Jokers.weightedRandom(stream).make();
            case SPECTRAL  -> Spectrals.random(stream).make();
            case MYTH      -> Relics.random(stream).make();
            case STANDARD  -> {   // a real playing card, with rolled enhancement/seal/edition (Illusion boosts the odds)
                DeckCard d = model.items.PlayingCards.rolled(stream, run.isIllusionActive());
                d.setShopValue(1);
                yield d;
            }
        };
    }
}
