package model.game.player;

/** A seat in the match: stable identity, display name, and the player's own {@link Run}. */
public record Player(PlayerId id, String name, Run run) {
    public Player {
        if (id == null) throw new IllegalArgumentException("id required");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name required");
        if (run == null) throw new IllegalArgumentException("run required");
    }
}