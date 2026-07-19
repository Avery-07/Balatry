package client.game;

import javafx.geometry.VPos;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

/**
 * Thin draw layer over a {@link GraphicsContext}: rounded panels, aligned text, and cards (from the sprite sheet
 * if loaded, else a vector 4-color face). This is the only part of the client the tests can't reach, so it stays
 * deliberately small — layout, animation and hit-testing all live in the tested {@code client.engine} package.
 */
public final class Renderer {

    private final GraphicsContext g;
    private Image sheet;
    private double cellW, cellH;
    private String family = "Monospaced";

    // 4-color deck (Hearts red, Clubs blue, Diamonds orange, Spades dark) — suit ordinals: SPADES0 HEARTS1 CLUBS2 DIAMONDS3.
    private static final Color[] SUIT_COLOR = { Color.web("#33373f"), Color.web("#e0392c"), Color.web("#2f6fb0"), Color.web("#e0861e") };
    private static final String[] SUIT_SYM = { "♠", "♥", "♣", "♦" };
    private static final String[] RANK = { "2","3","4","5","6","7","8","9","10","J","Q","K","A" };

    public Renderer(GraphicsContext g) { this.g = g; g.setImageSmoothing(false); }

    public GraphicsContext gc() { return g; }

    public void cardSheet(Image img) { if (img != null) { sheet = img; cellW = img.getWidth() / 13.0; cellH = img.getHeight() / 4.0; } }
    public void font(String fam) { if (fam != null) family = fam; }

    public void fillRect(Color c, double x, double y, double w, double h) { g.setFill(c); g.fillRect(x, y, w, h); }

    public void panel(double x, double y, double w, double h, Color fill, Color border, double arc, double bw) {
        if (fill != null) { g.setFill(fill); g.fillRoundRect(x, y, w, h, arc, arc); }
        if (border != null) { g.setStroke(border); g.setLineWidth(bw); g.strokeRoundRect(x, y, w, h, arc, arc); }
    }

    /** Centered text (both axes) at (x,y). */
    public void textCenter(String s, double x, double y, double size, Color c) { text(s, x, y, size, c, TextAlignment.CENTER, VPos.CENTER, false); }
    /** Top-left anchored text at (x,y). */
    public void textLeft(String s, double x, double y, double size, Color c) { text(s, x, y, size, c, TextAlignment.LEFT, VPos.TOP, false); }
    public void textLeftBold(String s, double x, double y, double size, Color c) { text(s, x, y, size, c, TextAlignment.LEFT, VPos.TOP, true); }
    public void textCenterBold(String s, double x, double y, double size, Color c) { text(s, x, y, size, c, TextAlignment.CENTER, VPos.CENTER, true); }

    private void text(String s, double x, double y, double size, Color c, TextAlignment a, VPos v, boolean bold) {
        g.setFill(c);
        g.setFont(Font.font(family, bold ? javafx.scene.text.FontWeight.BOLD : javafx.scene.text.FontWeight.NORMAL, size));
        g.setTextAlign(a); g.setTextBaseline(v);
        g.fillText(s, x, y);
    }

    /** Draws a card centered at (cx,cy), tilted {@code deg}, with an optional selection ring. */
    public void card(int rankOrd, int suitOrd, double cx, double cy, double w, double h, double deg, boolean selected) {
        g.save();
        g.translate(cx, cy);
        g.rotate(deg);
        double x = -w / 2, y = -h / 2, arc = 10;
        if (sheet != null) {
            g.drawImage(sheet, rankOrd * cellW, spriteRow(suitOrd) * cellH, cellW, cellH, x, y, w, h);
        } else {
            g.setFill(Color.web("#f6f4ee")); g.fillRoundRect(x, y, w, h, arc, arc);
            Color sc = SUIT_COLOR[suitOrd];
            g.setFill(sc);
            g.setTextAlign(TextAlignment.LEFT); g.setTextBaseline(VPos.TOP);
            g.setFont(Font.font(family, javafx.scene.text.FontWeight.BOLD, h * 0.17));
            g.fillText(RANK[rankOrd] + SUIT_SYM[suitOrd], x + 7, y + 6);
            g.setTextAlign(TextAlignment.CENTER); g.setTextBaseline(VPos.CENTER);
            g.setFont(Font.font(family, javafx.scene.text.FontWeight.BOLD, h * 0.42));
            g.fillText(SUIT_SYM[suitOrd], 0, 4);
        }
        if (selected) { g.setStroke(Color.web("#f0a92b")); g.setLineWidth(4); g.strokeRoundRect(x, y, w, h, arc, arc); }
        g.restore();
    }

    private static int spriteRow(int suit) { return switch (suit) { case 1 -> 0; case 2 -> 1; case 3 -> 2; default -> 3; }; }
}
