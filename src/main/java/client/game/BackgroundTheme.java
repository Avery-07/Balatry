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

    // In-match backdrops are now rolled per state change by client.engine.BackgroundPalette (two wide-apart
    // jewel-tone mains + a darkened blend) over DEFAULT's structural knobs, agitation scaled by the ante — so
    // there are no fixed per-phase presets here any more. DEFAULT stays as the neutral menu/lobby field.
}