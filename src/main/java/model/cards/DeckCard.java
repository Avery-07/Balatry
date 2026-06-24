package model.cards;

public final class DeckCard extends Card {
    private Rank rank;
    private Suit suit;

    private Enhancement enhancement;
    private Edition edition;
    private Seal seal;
    private Sticker sticker;

    public DeckCard(Rank rank, Suit suit) {
        this.rank = rank;
        this.suit = suit;
    }

    public Rank effectiveRank() {
        return rank;
    }

    public Suit effectiveSuit() {
        return suit;
    }

    public void setRank(Rank rank) {
        this.rank = rank;
    }

    public void setSuit(Suit suit) {
        this.suit = suit;
    }

    public void setEnhancement(Enhancement enhancement) {
        this.enhancement = enhancement;
    }

    public void setEdition(Edition edition) {
        this.edition = edition;
    }

    public void setSeal(Seal seal) {
        this.seal = seal;
    }

    public void setSticker(Sticker sticker) {
        this.sticker = sticker;
    }
}
