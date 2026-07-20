package model.items.relics;

import model.items.DeckCard.Rank;
import model.items.DeckCard.Suit;
import model.game.player.PlayerId;
import model.game.scoring.HandType;

/** The caster's choice for a relic use: which seat (if any) it is aimed at, plus the one selector the relic reads. */
public record RelicTarget(PlayerId opponent, Rank rank, Suit suit, int jokerIndex, HandType handType) {

    public static final int NO_INDEX = -1;

    /** No target and no selector (Metabole, Mimesis, Aegis). */
    public static RelicTarget none() { return new RelicTarget(null, null, null, NO_INDEX, null); }

    /** A bare seat target with no further selector (Pyre, Harpax, Limos). */
    public static RelicTarget on(PlayerId seat) { return new RelicTarget(seat, null, null, NO_INDEX, null); }

    /** Seat plus a rank (Anathema). */
    public static RelicTarget rank(PlayerId seat, Rank rank) { return new RelicTarget(seat, rank, null, NO_INDEX, null); }

    /** Seat plus a suit (Miasma). */
    public static RelicTarget suit(PlayerId seat, Suit suit) { return new RelicTarget(seat, null, suit, NO_INDEX, null); }

    /** Seat plus a board position (Katadesmos). */
    public static RelicTarget joker(PlayerId seat, int jokerIndex) { return new RelicTarget(seat, null, null, jokerIndex, null); }

    /** Seat plus a hand type (Katabasis). */
    public static RelicTarget hand(PlayerId seat, HandType handType) { return new RelicTarget(seat, null, null, NO_INDEX, handType); }
}
