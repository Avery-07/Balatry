package model.cards;

public sealed abstract class Card permits DeckCard, JokerCard, ConsumableCard {
}
