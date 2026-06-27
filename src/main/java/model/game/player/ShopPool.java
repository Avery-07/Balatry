package model.game.player;

import model.cards.Card;
import model.cards.DeckCard;
import model.cards.DeckCard.Rank;
import model.cards.DeckCard.Suit;
import model.cards.consumables.ConsumableCard;
import model.cards.consumables.ConsumableEffect;
import model.cards.consumables.ConsumableSpec;
import model.cards.consumables.ConsumableType;
import model.cards.jokers.JokerCard;
import model.cards.jokers.JokerSpec;
import model.cards.jokers.Rarity;

import java.util.random.RandomGenerator;

/** Supplies shop items. Pluggable so the real catalog replaces the placeholder once content exists. */
@FunctionalInterface
public interface ShopPool {

    /** Rolls one item using only {@code stream} (for determinism), with its shop value set. */
    Card roll(RandomGenerator stream);

    /** Placeholder mix until the catalog lands: ~50% playing card, ~30% joker, ~20% consumable. */
    ShopPool PLACEHOLDER = stream -> {
        int r = stream.nextInt(100);
        if (r < 50) {
            DeckCard card = new DeckCard(
                    Rank.values()[stream.nextInt(Rank.values().length)],
                    Suit.values()[stream.nextInt(Suit.values().length)]);
            card.setShopValue(1);
            return card;
        }
        if (r < 80) {
            Rarity rarity = Rarity.values()[stream.nextInt(3)];   // Common/Uncommon/Rare for the stub
            int cost = switch (rarity) {
                case COMMON -> 4; case UNCOMMON -> 6; case RARE -> 8; case LEGENDARY -> 10;
            };
            return new JokerCard(JokerSpec.named("Stub Joker", rarity).build(), cost);
        }
        ConsumableType type = ConsumableType.values()[stream.nextInt(ConsumableType.values().length)];
        return new ConsumableCard(new ConsumableSpec("Stub " + type, type, 3, ConsumableEffect.NO_OP));
    };
}