package model.cards;

import model.modifiers.Enhancement;
import model.modifiers.Seal;

public final class DeckCard extends Card {
    private Rank rank;
    private Suit suit;
    private Enhancement enhancement;
    private Seal seal;

    public DeckCard(Rank rank, Suit suit) {
        this.rank = rank;
        this.suit = suit;
    }

    public Rank getRank() { return rank; }
    public Suit getSuit() { return suit; }
    public Enhancement getEnhancement() { return enhancement; }
    public Seal getSeal() { return seal; }
    public void setRank(Rank rank) { this.rank = rank; }   // intrinsic attribute, not a modifier
    public void setSuit(Suit suit) { this.suit = suit; }   // intrinsic attribute, not a modifier

    /** Applies an enhancement, replacing any existing one (a card has at most one). */
    public void apply(Enhancement enhancement) { this.enhancement = enhancement; }
    /** Removes the enhancement, if it is the given one. */
    public void remove(Enhancement enhancement) { if (this.enhancement == enhancement) this.enhancement = null; }

    /** Applies a seal, replacing any existing one (a card has at most one). */
    public void apply(Seal seal) { this.seal = seal; }
    /** Removes the seal, if it is the given one. */
    public void remove(Seal seal) { if (this.seal == seal) this.seal = null; }

    public boolean isFace() { return rank == Rank.KING || rank == Rank.QUEEN || rank == Rank.JACK; }
    public boolean isSpade() { return suit == Suit.SPADES || enhancement == Enhancement.WILD; }
    public boolean isHeart() { return suit == Suit.HEARTS || enhancement == Enhancement.WILD; }
    public boolean isClub() { return suit == Suit.CLUBS || enhancement == Enhancement.WILD; }
    public boolean isDiamond() { return suit == Suit.DIAMONDS || enhancement == Enhancement.WILD; }

    public enum Rank {
        TWO(2),
        THREE(3),
        FOUR(4),
        FIVE(5),
        SIX(6),
        SEVEN(7),
        EIGHT(8),
        NINE(9),
        TEN(10),
        JACK(10),
        QUEEN(10),
        KING(10),
        ACE(11);
        final int chips;
        Rank(int chips) { this.chips = chips; }
        public int getChips() { return chips; }
    }

    public enum Suit {
        SPADES,
        HEARTS,
        CLUBS,
        DIAMONDS
    }
}