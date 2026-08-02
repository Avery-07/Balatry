package model.game;

/** The seven ante modifiers; one is active per ante and applies symmetrically to every player. */
public enum Sin {
    PRIDE("Pride", "Bet a points multiplier (×1–×3) each round — kept only if you beat target × your bet. The shop auctions a Legendary joker."),
    GREED("Greed", "Earn $1 per chip milestone crossed. The shop is shared: buying an item debuffs it in every rival's shop."),
    LUST("Lust", "Each new hand type you play this round boosts later hands (+0.5× score). Shops carry an extra card and pack."),
    WRATH("Wrath", "Every round begins with a free Mega Myth Pack; its relics are cast the moment you open it."),
    GLUTTONY("Gluttony", "Shops sell every consumable type and let you eat jokers for cash. Each consumable used feeds a communal payout gauge."),
    ENVY("Envy", "Every rival's shop purchase is public — copy one for double its price, or swap a joker with a rival at round end."),
    SLOTH("Sloth", "Skipping a blind grants two tags. Leaving a shop without buying gives a free Spectral.");

    private final String displayName, description;

    Sin(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String displayName()  { return displayName; }
    public String description()  { return description; }
}