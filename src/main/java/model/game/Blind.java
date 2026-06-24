package model.game;

/** The three blind slots within an ante, each with its base cash-out reward. The boss for {@link #BOSS} is selected separately. */
public enum Blind {
    SMALL(3),
    BIG(4),
    BOSS(5);

    private final int reward;

    Blind(int reward) { this.reward = reward; }

    /** Base money awarded for clearing this blind. */
    public int getReward() { return reward; }
}