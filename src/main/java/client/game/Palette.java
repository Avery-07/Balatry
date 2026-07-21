package client.game;

import javafx.scene.paint.Color;

/** The client's committed dark "arcade felt" palette, shared by every screen. */
final class Palette {
    private Palette() { }

    static final Color FELT_A = Color.web("#2c6a58"), FELT_B = Color.web("#12291f");
    static final Color PANEL = Color.web("#1b1c1e"), PANEL2 = Color.web("#26272b"), EDGE = Color.web("#4a4d54");
    static final Color INK = Color.web("#f4f1e8"), DIM = Color.web("#b9c4bd"), FAINT = Color.web("#7f8d86");
    static final Color ORANGE = Color.web("#f0a92b"), RED = Color.web("#d1442f"), GREEN = Color.web("#37a862");
    static final Color BLUE = Color.web("#2f6fb0"), GOLD = Color.web("#b8912c"), PURPLE = Color.web("#8a4f9e");
    static final Color DARK = Color.web("#241a05");   // text on orange buttons
    static final Color PODIUM_GOLD = Color.web("#ffd45e");   // first place, in standings and the finish screen
}
