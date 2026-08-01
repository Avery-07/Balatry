package client.game;

import debug.Log;
import javafx.geometry.VPos;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.BlendMode;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
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
    private Image enhSheet;              // enhancement backgrounds (3x3 grid of 71x95); the transparent face draws on top
    private double enhCellW, enhCellH;
    private Image sealSheet;             // seals (2x2 grid of 71x95); a transparent stamp drawn on top of the face
    private double sealCellW, sealCellH;
    private String family = "Monospaced";

    // 4-color deck (Hearts red, Clubs blue, Diamonds orange, Spades dark) — suit ordinals: SPADES0 HEARTS1 CLUBS2 DIAMONDS3.
    private static final Color[] SUIT_COLOR = { Color.web("#33373f"), Color.web("#e0392c"), Color.web("#2f6fb0"), Color.web("#e0861e") };
    private static final String[] SUIT_SYM = { "♠", "♥", "♣", "♦" };
    private static final String[] RANK = { "2","3","4","5","6","7","8","9","10","J","Q","K","A" };
    // Enhancement.ordinal() -> cell index in Enhancements.png (3x3). Enum order is BONUS,MULT,WILD,GLASS,STEEL,
    // STONE,GOLD,LUCKY; the sheet's order isn't. Cell 0 is the plain base drawn behind an un-enhanced face.
    private static final int[] ENH_CELL = { 3, 4, 5, 7, 8, 1, 2, 6 };
    private static final int ENH_STONE = 5;   // STONE ordinal: a Stone card is only the stone, no rank/suit
    // Seal.ordinal() -> cell in Seals.png (2x2). Enum order is RED,BLUE,GOLD,PURPLE; the sheet is Gold,Purple,Red,Blue.
    private static final int[] SEAL_CELL = { 2, 3, 0, 1 };

    public Renderer(GraphicsContext g) { this.g = g; g.setImageSmoothing(false); }

    public GraphicsContext gc() { return g; }

    private double clock;   // frame time (seconds), set once per frame; drives the animated edition shimmers
    /** Sets the frame clock the edition effects animate on; call once per frame before drawing cards. */
    public void clock(double t) { clock = t; }

    public void cardSheet(Image img) { if (img != null) { sheet = img; cellW = img.getWidth() / 13.0; cellH = img.getHeight() / 4.0; } }
    public void enhancementSheet(Image img) { if (img != null && !img.isError() && img.getWidth() > 0) { enhSheet = img; enhCellW = img.getWidth() / 3.0; enhCellH = img.getHeight() / 3.0; } }
    public void sealSheet(Image img) { if (img != null && !img.isError() && img.getWidth() > 0) { sealSheet = img; sealCellW = img.getWidth() / 2.0; sealCellH = img.getHeight() / 2.0; } }
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
            var in = getClass().getResourceAsStream("/sprites/jokers/" + key + ".png");
            if (in != null) { Image i = new Image(in); if (!i.isError() && i.getWidth() > 0) img = i; }
            else { Log.error("Error loading joker texture: " + key);}
        } catch (RuntimeException ignored) {}
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

    // --- consumable atlases (Planet / Tarot / Spectral): one sheet each, a grid of 71x95 cells. A stripped display
    // name maps to a cell, so the draw sites look a face up by the same label the joker path uses. ---
    private static final class Atlas {
        Image img; int cols; double cw, ch;
        final java.util.Map<String, Integer> cell = new java.util.HashMap<>();
    }
    private final Atlas planets = new Atlas(), tarots = new Atlas(), spectrals = new Atlas();

    /** A display name reduced to its texture key — letters and digits only, the same rule the joker path uses. */
    private static String key(String displayName) { return displayName == null ? "" : displayName.replaceAll("[^A-Za-z0-9]", ""); }

    private static void loadAtlas(Atlas a, Image img, int cols, int rows) {
        a.img = img; a.cols = cols; a.cw = img.getWidth() / (double) cols; a.ch = img.getHeight() / (double) rows;
    }

    /** The Planet sheet (4x3). Enum order maps to cells through the layout array (the sheet is not in enum order). */
    public void planetSheet(Image img) {
        if (img == null || img.isError() || img.getWidth() <= 0) return;
        loadAtlas(planets, img, 4, 3);
        int[] cell = { 11, 3, 9, 4, 8, 7, 5, 6, 10, 2, 1, 0 };
        var v = model.items.consumables.Planets.values();
        for (int i = 0; i < v.length && i < cell.length; i++) planets.cell.put(key(v[i].spec().getName()), cell[i]);
    }

    /** The Tarot sheet (5x5). Enum order maps 1:1 except JUSTICE/STRENGTH, which the sheet numbers traditionally. */
    public void tarotSheet(Image img) {
        if (img == null || img.isError() || img.getWidth() <= 0) return;
        loadAtlas(tarots, img, 5, 5);
        int[] cell = { 0, 1, 2, 3, 4, 5, 6, 7, 11, 9, 10, 8, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21 };
        var v = model.items.consumables.Tarots.values();
        for (int i = 0; i < v.length && i < cell.length; i++) tarots.cell.put(key(v[i].spec().getName()), cell[i]);
    }

    /** The Spectral sheet (5x4). The Soul sits at cell 0; Exorcism and Black Hole have no art (-1 => vector fallback). */
    public void spectralSheet(Image img) {
        if (img == null || img.isError() || img.getWidth() <= 0) return;
        loadAtlas(spectrals, img, 5, 4);
        int[] cell = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, -1, 16, 0, -1 };
        var v = model.items.consumables.Spectrals.values();
        for (int i = 0; i < v.length && i < cell.length; i++) if (cell[i] >= 0) spectrals.cell.put(key(v[i].spec().getName()), cell[i]);
    }

    /**
     * Draws a consumable's face (planet, tarot or spectral) for its display name into (x,y,w,h), preserving aspect
     * (letterboxed, never stretched). Returns false when no sheet carries that name — so the caller falls back to its
     * vector tile, exactly like the joker faces. Names are unique across the three sheets, so the order is arbitrary.
     */
    public boolean consumableFace(String label, double x, double y, double w, double h) {
        String k = key(label);
        return drawCell(tarots, k, x, y, w, h) || drawCell(planets, k, x, y, w, h) || drawCell(spectrals, k, x, y, w, h);
    }

    private boolean drawCell(Atlas a, String k, double x, double y, double w, double h) {
        if (a.img == null) return false;
        Integer cell = a.cell.get(k);
        if (cell == null) return false;
        double s = Math.min(w / a.cw, h / a.ch), dw = a.cw * s, dh = a.ch * s;
        g.drawImage(a.img, (cell % a.cols) * a.cw, (cell / a.cols) * a.ch, a.cw, a.ch,
                x + (w - dw) / 2, y + (h - dh) / 2, dw, dh);
        return true;
    }

    // --- pack atlases: one 2x2 sheet per kind, size -> cell (NORMAL 0, JUMBO 1, MEGA 2; cell 3 spare). Myth (relic
    // packs) has no art yet, so it falls back to the vector tile. ---
    private final Atlas arcanaPacks = new Atlas(), buffoonPacks = new Atlas(), celestialPacks = new Atlas(),
            spectralPacks = new Atlas(), standardPacks = new Atlas();

    private Atlas packAtlas(model.items.packs.PackKind kind) {
        return switch (kind) {
            case ARCANA -> arcanaPacks;
            case BUFFOON -> buffoonPacks;
            case CELESTIAL -> celestialPacks;
            case SPECTRAL -> spectralPacks;
            case STANDARD -> standardPacks;
            case MYTH -> null;   // relic packs have no art yet
        };
    }

    /** A pack sheet for one kind (2x2). Each size's display label ("Mega Arcana Pack", …) maps to its cell. */
    public void packSheet(model.items.packs.PackKind kind, Image img) {
        Atlas a = packAtlas(kind);
        if (a == null || img == null || img.isError() || img.getWidth() <= 0) return;
        loadAtlas(a, img, 2, 2);
        int[] sizeCell = { 0, 1, 2 };   // NORMAL, JUMBO, MEGA
        var sizes = model.items.packs.PackSize.values();
        for (int i = 0; i < sizes.length && i < sizeCell.length; i++)
            a.cell.put(key(new model.items.packs.BoosterPack(kind, sizes[i]).toString()), sizeCell[i]);
    }

    /** Draws a booster pack's art for its display label into (x,y,w,h) preserving aspect, or false if no sheet has it. */
    public boolean packFace(String label, double x, double y, double w, double h) {
        String k = key(label);
        return drawCell(arcanaPacks, k, x, y, w, h) || drawCell(buffoonPacks, k, x, y, w, h)
                || drawCell(celestialPacks, k, x, y, w, h) || drawCell(spectralPacks, k, x, y, w, h)
                || drawCell(standardPacks, k, x, y, w, h);
    }

    // --- voucher atlas (Vouchers.png, 8x4 = 32 cells) ---
    private final Atlas voucherAtlas = new Atlas();

    // TODO(mapping): Vouchers.ordinal() -> cell in Vouchers.png. Right now EVERY voucher points at cell 0 (the first
    // texture) as a placeholder. Edit this array to give each voucher its real cell — the order matches the Vouchers
    // enum (see model/items/vouchers/Vouchers.java), the comment on each row names the eight entries, and -1 means
    // "no art, keep the vector tile" (the sheet has 32 cells but the enum has 34, so at least two must be -1).
    private static final int[] VOUCHER_CELL = {
            0, 8, 3, 11, 4, 12, 16, 22, // Overstock, Overstock Plus, Clearance Sale, Liquidation, Hone, Glow Up, Reroll Surplus, Reroll Glut
            18, 24, 19, 25, 5, 13, 6, 14,   // Crystal Ball, Omen Globe, Telescope, Observatory, Grabber, Nacho Tong, Wasteful, Recyclomancy
            1, 9, 2, 10, 17, 23, 7, 15,   // Tarot Merchant, Tarot Tycoon, Planet Merchant, Planet Tycoon, Seed Money, Money Tree, Blank, Antimatter
            20, 26, 21, 27, 0, 0, 0, 0,   // Magic Trick, Illusion, Paint Brush, Palette, Sampler, Connoisseur, Relic Merchant, Relic Tycoon
            0, 0                      // Showman, Encore
    };

    /** The voucher sheet (8x4). Each voucher's display name maps to a cell via {@link #VOUCHER_CELL}. */
    public void voucherSheet(Image img) {
        if (img == null || img.isError() || img.getWidth() <= 0) return;
        loadAtlas(voucherAtlas, img, 8, 4);
        var v = model.items.vouchers.Vouchers.values();
        for (int i = 0; i < v.length; i++) {
            int cell = i < VOUCHER_CELL.length ? VOUCHER_CELL[i] : -1;
            if (cell >= 0) voucherAtlas.cell.put(key(v[i].spec().getName()), cell);
        }
    }

    /** Draws a voucher's art for its display name into (x,y,w,h) preserving aspect, or false if none. */
    public boolean voucherFace(String label, double x, double y, double w, double h) {
        return drawCell(voucherAtlas, key(label), x, y, w, h);
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
        card(rankOrd, suitOrd, -1, -1, -1, cx, cy, w, h, deg, selected, 0);
    }

    /** As {@link #card}, carrying enhancement/seal/edition ordinals ({@code -1} for none of each). */
    public void card(int rankOrd, int suitOrd, int enhancement, int seal, int edition, double cx, double cy, double w, double h, double deg, boolean selected) {
        card(rankOrd, suitOrd, enhancement, seal, edition, cx, cy, w, h, deg, selected, 0);
    }

    /** As {@link #card}, plus a flip, with no modifiers. */
    public void card(int rankOrd, int suitOrd, double cx, double cy, double w, double h, double deg, boolean selected, double flipT) {
        card(rankOrd, suitOrd, -1, -1, -1, cx, cy, w, h, deg, selected, flipT);
    }

    /**
     * Draws a card with {@code enhancement}/{@code seal}/{@code edition} ordinals ({@code -1} = none) and a flip:
     * {@code flipT} 0 is face up, 1 face down (the deck's back), between squashes it through its edge-on midpoint —
     * the turn animation. The face art ({@code cards.png}) is transparent-backed, so a card base is drawn first: the
     * enhancement's background when it has one, else the plain base cell. A Stone card shows only the stone. The
     * seal is a stamp over the finished face; the edition is its animated shimmer on top of everything.
     */
    public void card(int rankOrd, int suitOrd, int enhancement, int seal, int edition, double cx, double cy, double w, double h,
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
            if (enhSheet != null) {   // the card base: enhancement background, or cell 0 (plain) for an un-enhanced card
                int cell = (enhancement >= 0 && enhancement < ENH_CELL.length) ? ENH_CELL[enhancement] : 0;
                g.drawImage(enhSheet, (cell % 3) * enhCellW, (cell / 3) * enhCellH, enhCellW, enhCellH, x, y, w, h);
            } else {
                g.setFill(Color.web("#f6f4ee")); g.fillRoundRect(x, y, w, h, arc, arc);
            }
            if (!(enhancement == ENH_STONE && enhSheet != null))   // Stone hides rank/suit; every other card draws its face
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
        boolean faceUp = !(flipT > 0.5 || rankOrd < 0);
        if (faceUp && sealSheet != null && seal >= 0 && seal < SEAL_CELL.length) {   // the seal stamp, over the finished face
            int scell = SEAL_CELL[seal];
            g.drawImage(sealSheet, (scell % 2) * sealCellW, (scell / 2) * sealCellH, sealCellW, sealCellH, x, y, w, h);
        }
        if (faceUp) editionEffect(edition, x, y, w, h, arc);   // Foil/Holo/Poly shimmer or Negative inversion, over the face
        if (selected) { g.setStroke(Color.web("#f0a92b")); g.setLineWidth(4); g.strokeRoundRect(x, y, w, h, arc, arc); }
        g.restore();
    }

    /**
     * Paints an edition's effect inside the card/tile silhouette at (x,y,w,h), clipped to the rounded shape so it
     * never bleeds into the transparent corners: Foil a cool metallic sheen, Holographic a pink shimmer, Polychrome
     * a drifting rainbow, Negative a colour inversion. Animated on the frame {@link #clock}. A no-op for {@code -1}.
     * Public so tile draws (jokers, consumables, shop/pack items) can lay it over their texture the same way.
     */
    public void editionEffect(int edition, double x, double y, double w, double h, double arc) {
        if (edition < 0) return;
        g.save();
        clipRoundRect(x, y, w, h, arc);
        switch (edition) {
            case 0 -> sheen(x, y, w, h, Color.web("#bff2ff"), Color.web("#5fd0ff"), 0.85, BlendMode.SCREEN);   // FOIL
            case 1 -> sheen(x, y, w, h, Color.web("#ff9ad6"), Color.web("#b06bff"), 0.65, BlendMode.SCREEN);   // HOLOGRAPHIC
            case 2 -> polychrome(x, y, w, h);                                                                  // POLYCHROME
            case 3 -> {                                                                                        // NEGATIVE
                g.setGlobalBlendMode(BlendMode.DIFFERENCE);
                g.setFill(Color.WHITE);
                g.fillRect(x, y, w, h);   // white × DIFFERENCE inverts the card's colours
            }
            default -> { }
        }
        g.restore();
    }

    /** A moving diagonal light band (Foil, Holographic) — a two-tone highlight that drifts across on the clock. */
    private void sheen(double x, double y, double w, double h, Color bright, Color tint, double alpha, BlendMode blend) {
        double p = 0.3 + 0.4 * (Math.sin(clock * 0.9) * 0.5 + 0.5);
        LinearGradient lg = new LinearGradient(x, y, x + w, y + h, false, CycleMethod.NO_CYCLE,
                new Stop(0, transparent(tint)),
                new Stop(Math.max(0.001, p - 0.28), transparent(tint)),
                new Stop(p, bright),
                new Stop(Math.min(0.999, p + 0.28), transparent(tint)),
                new Stop(1, transparent(tint)));
        g.setGlobalBlendMode(blend);
        g.setGlobalAlpha(alpha);
        g.setFill(lg);
        g.fillRect(x, y, w, h);
    }

    /** The Polychrome rainbow: a full-spectrum gradient whose hues drift along the clock. */
    private void polychrome(double x, double y, double w, double h) {
        double shift = (clock * 0.12) % 1.0;
        Stop[] stops = new Stop[7];
        for (int i = 0; i <= 6; i++) {
            double pos = i / 6.0;
            stops[i] = new Stop(pos, Color.hsb(((pos + shift) % 1.0) * 360, 0.85, 1.0, 0.55));
        }
        LinearGradient lg = new LinearGradient(x, y, x + w, y, false, CycleMethod.NO_CYCLE, stops);
        g.setGlobalBlendMode(BlendMode.OVERLAY);
        g.setGlobalAlpha(0.75);
        g.setFill(lg);
        g.fillRect(x, y, w, h);
    }

    private static Color transparent(Color c) { return Color.color(c.getRed(), c.getGreen(), c.getBlue(), 0); }

    /** Clips the graphics context to a rounded rectangle — the card's silhouette, so overlays stay inside it. */
    private void clipRoundRect(double x, double y, double w, double h, double r) {
        g.beginPath();
        g.moveTo(x + r, y);
        g.arcTo(x + w, y, x + w, y + h, r);
        g.arcTo(x + w, y + h, x, y + h, r);
        g.arcTo(x, y + h, x, y, r);
        g.arcTo(x, y, x + w, y, r);
        g.closePath();
        g.clip();
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
