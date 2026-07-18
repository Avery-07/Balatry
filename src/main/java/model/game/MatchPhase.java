package model.game;

/** The synchronized lifecycle of a match; all players move through these phases together. */
public enum MatchPhase {
    LOBBY,    // seated, not yet started
    SELECTION, // players are choosing between playing a round or skipping it.
    BLIND,    // players are scoring against the current blind
    RESULT,   // players are viewing the blind's result before the shop opens
    SHOP,     // players are spending between blinds
    FINISHED  // resolved
}