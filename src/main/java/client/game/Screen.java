package client.game;

/** One phase's center panel. Given the frame context and the center rect, it draws and registers its buttons. */
interface Screen {
    void render(Ui ui, double x, double y, double w, double h);
}
