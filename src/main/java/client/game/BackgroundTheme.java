package client.game;

public record BackgroundTheme(
        double zoom,
        int warpSteps,
        double spinSpeed,
        double paintSpeed,
        double spinAmount,
        double contrast,
        double sharpenStrength,
        int colour1,
        int colour2,
        int colour3
) {
    // The values from your original hardcoded setup
    public static final BackgroundTheme DEFAULT = new BackgroundTheme(
            26.0, 5, 0.025, 1.0, 0.22, 2.1, 1.0,
            0x750000, 0x003375, 0x220826
    );

    public static BackgroundTheme RANDOM() {
        return new BackgroundTheme(
            15.0 + Math.random() * 25.0,        // zoom: 15.0 to 40.0
            2 + (int)(Math.random() * 5),       // warpSteps: 2 to 6 (keeps performance stable)
            (Math.random() - 0.5) * 0.1,        // spinSpeed: -0.05 to 0.05 (can spin both ways)
            0.5 + Math.random() * 2.0,          // paintSpeed: 0.5 to 2.5
            0.1 + Math.random() * 0.4,          // spinAmount: 0.1 to 0.5
            1.5 + Math.random() * 1.5,          // contrast: 1.5 to 3.0
            Math.random() * 1.5,                // sharpenStrength: 0.0 to 1.5
            (int)(Math.random() * 0xFFFFFF),    // colour1: Random 24-bit RGB
            (int)(Math.random() * 0xFFFFFF),    // colour2: Random 24-bit RGB
            (int)(Math.random() * 0xFFFFFF)     // colour3: Random 24-bit RGB;
            );
    }

    // Example of a secondary preset you can transition to
    public static final BackgroundTheme BOSS_PHASE = new BackgroundTheme(
            26.0, 6, 0.080, 2.5, 0.40, 2.8, 1.5,
            0x990000, 0x550000, 0x110000
    );

    // --- Per-game-state presets (GameClient transitions between these as the phase changes). Tuned around DEFAULT's
    //     structure; the calm phases spin slower and cooler, the tense ones faster and warmer. ---

    /** Small blind: calm, cool blue. */
    public static final BackgroundTheme SMALL_BLIND = new BackgroundTheme(
            26.0, 5, 0.022, 0.9, 0.20, 2.1, 1.0,
            0x0f3a70, 0x1c6fa0, 0x081226
    );

    /** Big blind: deeper blue with a touch more churn. */
    public static final BackgroundTheme BIG_BLIND = new BackgroundTheme(
            26.0, 5, 0.032, 1.2, 0.24, 2.2, 1.1,
            0x123a75, 0x2060b0, 0x0a1020
    );

    /** Shop: settled green &amp; gold. */
    public static final BackgroundTheme SHOP = new BackgroundTheme(
            24.0, 5, 0.018, 0.8, 0.18, 2.0, 1.0,
            0x1d5c2a, 0x7a5f10, 0x0a1a10
    );

    /** Blind cleared: celebratory green &amp; gold, a little livelier. */
    public static final BackgroundTheme RESULT = new BackgroundTheme(
            26.0, 5, 0.035, 1.3, 0.24, 2.2, 1.1,
            0x2a6b1f, 0x8a6a12, 0x0e1a08
    );

    /** Match over: dim, regal purple &amp; gold. */
    public static final BackgroundTheme FINISHED = new BackgroundTheme(
            22.0, 5, 0.015, 0.7, 0.18, 2.0, 1.0,
            0x3a1d5c, 0x6b5a12, 0x0a0818
    );
}