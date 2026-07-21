package model.game.scoring;

/** The poker hands, highest-ranking first; each carries its level-1 base and per-level gains (vanilla Balatro values). */
public enum HandType {
    FLUSH_FIVE     (160, 16, 50, 3),
    FLUSH_HOUSE    (140, 14, 40, 4),
    FIVE_OF_A_KIND (120, 12, 35, 3),
    STRAIGHT_FLUSH (100,  8, 40, 4),
    FOUR_OF_A_KIND ( 60,  7, 30, 3),
    FULL_HOUSE     ( 40,  4, 25, 2),
    FLUSH          ( 35,  4, 15, 2),
    STRAIGHT       ( 30,  4, 30, 3),
    THREE_OF_A_KIND( 30,  3, 20, 2),
    TWO_PAIR       ( 20,  2, 20, 1),
    PAIR           ( 10,  2, 15, 1),
    HIGH_CARD      (  5,  1, 10, 1);

    private final int baseChips;
    private final int baseMult;
    private final int chipsPerLevel;
    private final int multPerLevel;

    HandType(int baseChips, int baseMult, int chipsPerLevel, int multPerLevel) {
        this.baseChips = baseChips;
        this.baseMult = baseMult;
        this.chipsPerLevel = chipsPerLevel;
        this.multPerLevel = multPerLevel;
    }

    public int getBaseChips()     { return baseChips; }
    public int getBaseMult()      { return baseMult; }
    public int getChipsPerLevel() { return chipsPerLevel; }
    public int getMultPerLevel()  { return multPerLevel; }

    /**
     * "Four of a Kind" — the single authority for a hand type's player-facing name. Both the client and the
     * joker tooltips render through this, so the prettifying rules live in exactly one place.
     */
    public String displayName() {
        String[] words = name().toLowerCase().split("_");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            String w = words[i];
            if (i > 0) out.append(' ');
            if (i > 0 && (w.equals("of") || w.equals("a"))) out.append(w);
            else out.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return out.toString();
    }
}