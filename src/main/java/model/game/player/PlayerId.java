package model.game.player;

/** Stable identity for a seat in a match; serializes as the protocol-level player reference. */
public record PlayerId(int seat) {
    public PlayerId {
        if (seat < 0) throw new IllegalArgumentException("seat must be >= 0: " + seat);
    }
}