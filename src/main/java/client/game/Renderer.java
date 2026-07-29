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

    // Per-joker face textures, loaded lazily from /sprites/joker/<Name>.png and cached (misses cached as null, so
    // the classpath is hit once per name). Key = the display name with every non-alphanumeric char stripped, so
    // "Half Joker" -> HalfJoker.png and "Oops! All 6s" -> OopsAll6s.png. Absent file => vector tile fallback.
    private final java.util.Map<String, Image> jokerTex = new java.util.HashMap<>();

    /** The face texture for a joker's display name, or {@code null} if no PNG is present (caller draws the vector tile). */
    public Image jokerTexture(String displayName) {
        if (displayName == null || displayName.isEmpty()) return null;
        String key = displayName.replaceAll("[^A-Za-z0-9]", "");
        if (key.isEmpty()) return null;
        if (jokerTex.containsKey(key)) return jokerTex.get(key);
        Image img = null;
        try {
            var in = getClass().getResourceAsStream("/sprites/joker/" + key + ".png");
            if (in != null) { Image i = new Image(in); if (!i.isError() && i.getWidth() > 0) img = i; }
        } catch (RuntimeException ignored) { }
        jokerTex.put(key, img);
        return img;
    }

    /**
     * Draws a texture scaled to fit inside (x,y,w,h) preserving its aspect ratio — the largest centered copy that
     * fits, letterboxed if the tile's shape differs. Never stretches: a joker face keeps its proportions whatever
     * the tile's aspect is.
     */
    public void imageFit(Image img, double x, double y, double w, double h) {
        if (img == null) return;
        double iw = img.getWidth(), ih = img.getHeight();
        if (iw <= 0 || ih <= 0) return;
        double s = Math.min(w / iw, h / ih);
        double dw = iw * s, dh = ih * s;
        g.drawImage(img, x + (w - dw) / 2, y + (h - dh) / 2, dw, dh);
    }

    public void fillRect(Color c, double x, double y, double w, double h) { g.setFill(c); g.fillRect(x, y, w, h); }

    public void panel(double x, double y, double w, double h, Color fill, Color border, double arc, double bw) {
        if (fill != null) { g.setFill(fill); g.fillRoundRect(x, y, w, h, arc, arc); }
        if (border != null) { g.setStroke(border); g.setLineWidth(bw); g.strokeRoundRect(x, y, w, h, arc, arc); }
    }

    /** Runs {@code draw} rotated {@code deg} about {@code (cx,cy)} — lets a rectangular tile carry the same idle sway a card does. */
    public void rotated(double cx, double cy, double deg, Runnable draw) {
        if (deg == 0) { draw.run(); return; }
        g.save();
        g.translate(cx, cy);
        g.rotate(deg);
        g.translate(-cx, -cy);
        draw.run();
        g.restore();
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
        card(rankOrd, suitOrd, cx, cy, w, h, deg, selected, 0);
    }

    /**
     * As {@link #card}, plus a flip: {@code flipT} 0 is face up, 1 face down (the deck's back), and anything in
     * between squashes the card horizontally through its edge-on midpoint — the turn animation itself.
     */
    public void card(int rankOrd, int suitOrd, double cx, double cy, double w, double h,
                     double deg, boolean selected, double flipT) {
        g.save();
        g.translate(cx, cy);
        g.rotate(deg);
        double squash = Math.abs(1 - 2 * flipT);
        if (squash < 0.02) squash = 0.02;   // never scale to zero; the stroke would vanish oddly
        g.scale(squash, 1);
        double x = -w / 2, y = -h / 2, arc = 10;
        if (flipT > 0.5 || rankOrd < 0) {   // the back: past edge-on, or a card we are not allowed to see
            back(x, y, w, h, arc);
        } else if (sheet != null) {
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

    /**
     * The card back — the deck's face to the world. Uses the back texture when one is loaded ({@link #backSheet});
     * until then, a vector back: felt-dark panel, double border, diamond lattice.
     */
    private void back(double x, double y, double w, double h, double arc) {
        if (backImage != null) { g.drawImage(backImage, x, y, w, h); return; }
        g.setFill(Color.web("#27436e")); g.fillRoundRect(x, y, w, h, arc, arc);
        g.setStroke(Color.web("#182a47")); g.setLineWidth(3); g.strokeRoundRect(x + 2, y + 2, w - 4, h - 4, arc, arc);
        g.setStroke(Color.web("#3c619c")); g.setLineWidth(1);
        double step = 9;
        for (double d = -h; d < w; d += step) {   // the lattice, clipped by hand to the inset rect
            double x1 = Math.max(x + 6, x + d), y1 = d < 0 ? y + 6 - d : y + 6;
            double x2 = Math.min(x + w - 6, x + d + h), y2 = y1 + (x2 - x1);
            if (x1 < x2 && y2 <= y + h - 6) g.strokeLine(x1, y1, x2, y2);
            double y3 = d < 0 ? y + h - 6 + d : y + h - 6;
            double y4 = y3 - (x2 - x1);
            if (x1 < x2 && y4 >= y + 6) g.strokeLine(x1, y3, x2, y4);
        }
    }

    /** Installs the deck-back texture (the table's deck art); null keeps the vector back. */
    public void backSheet(Image img) { if (img != null && !img.isError()) backImage = img; }

    private Image backImage;

    private static int spriteRow(int suit) { return switch (suit) { case 1 -> 0; case 2 -> 1; case 3 -> 2; default -> 3; }; }
}
