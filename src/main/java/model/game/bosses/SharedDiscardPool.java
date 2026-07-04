package model.game.bosses;

/** The Commons' single table-wide discard pool: every participating seat's discards draw it down together. */
public final class SharedDiscardPool {

    private int remaining;

    SharedDiscardPool(int remaining) { this.remaining = remaining; }

    /** Discards left in the shared pool. */
    public int getRemaining() { return remaining; }

    /** Consumes one shared discard; the caller validates {@link #getRemaining()} first. */
    public void consume() {
        if (remaining <= 0) throw new IllegalStateException("shared discard pool is empty");
        remaining--;
    }
}
