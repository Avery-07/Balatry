package client.game;

import client.engine.Layout;
import javafx.scene.paint.Color;
import model.cards.DeckType;
import model.game.Stake;
import model.game.net.MatchSetup;
import model.game.player.SeatConfig;
import model.game.player.Sleeve;

import java.util.List;

import static client.game.Palette.*;

/**
 * Everything before the match: the main menu (name, loadout, host-or-join) and the lobby (who is here, the
 * table's deck, and the host's Start). It owns its own state because none of it exists in a {@link
 * client.MatchSnapshot} — there is no model until the server says the match has begun.
 *
 * <p>Input is the same idiom as the rest of the client: {@link #render} registers clickable regions on the
 * {@link Ui} and {@code GameClient} dispatches into them. Typing is the one addition — a canvas has no text
 * fields, so {@link #focus} names which of the two editable strings keystrokes land in, and {@link #onChar} /
 * {@link #onBackspace} edit it.
 */
final class Menu {

    /** Which screen the pre-match UI is showing. */
    enum Mode { MAIN, LOBBY }

    /** Which text field, if any, is taking keystrokes. */
    enum Focus { NONE, NAME, ADDRESS }

    private static final int NAME_LIMIT = 16, ADDRESS_LIMIT = 40;

    /** Matches the model's own limits ({@code Match.createSeated} seats 2-4). */
    private static final int MIN_PLAYERS = 2, MAX_PLAYERS = 4;

    Mode mode = Mode.MAIN;
    Focus focus = Focus.NONE;

    String name = defaultName();
    String address = "localhost";
    Sleeve sleeve = Sleeve.STANDARD;
    Stake stake = Stake.WHITE;
    DeckType deck = DeckType.STANDARD;

    boolean host;                  // this client opened the lobby (seat 0)
    String hostAddress = "";       // shown to the host so they can share it
    MatchSetup lobby;              // the roster as last broadcast, or null before the first frame
    int seat = -1;                 // this client's seat, once the server has assigned one
    String status = "";

    /** Set by GameClient: what the buttons actually do. */
    Runnable onHost, onJoin, onBegin, onLeave, onLoadoutChange;
    java.util.function.Consumer<DeckType> onDeckChange;

    /** This player's choices, sanitized for the wire (the name is typed, so it cannot be trusted raw). */
    SeatConfig seatConfig() {
        return new SeatConfig(MatchSetup.sanitize(name, defaultName()), sleeve, stake);
    }

    // --- input ---------------------------------------------------------------

    /** Appends one typed character to the focused field, if it is printable and there is room. */
    void onChar(String ch) {
        if (focus == Focus.NONE || ch.isEmpty()) return;
        char c = ch.charAt(0);
        if (c < ' ' || c == 127) return;   // control characters (Enter, Backspace) arrive here too
        if (focus == Focus.NAME && name.length() < NAME_LIMIT) name += c;
        else if (focus == Focus.ADDRESS && address.length() < ADDRESS_LIMIT) address += c;
    }

    void onBackspace() {
        if (focus == Focus.NAME && !name.isEmpty()) name = name.substring(0, name.length() - 1);
        else if (focus == Focus.ADDRESS && !address.isEmpty()) address = address.substring(0, address.length() - 1);
    }

    // --- rendering -----------------------------------------------------------

    void render(Ui ui) {
        if (mode == Mode.MAIN) renderMain(ui); else renderLobby(ui);
        if (!status.isEmpty()) ui.r.textCenter(status, Ui.W / 2.0, Ui.H - 28, 13, status.startsWith("ERR") ? RED : DIM);
    }

    /** The main menu is only identity and connection; the loadout is picked in the lobby, where others can see it. */
    private void renderMain(Ui ui) {
        Renderer r = ui.r;
        r.textCenterBold("BALATRY", Ui.W / 2.0, 150, 76, ORANGE);
        r.textCenter("a multiplayer Balatro", Ui.W / 2.0, 202, 16, DIM);

        double pw = 520, px = (Ui.W - pw) / 2, py = 268, ph = 320;
        r.panel(px, py, pw, ph, Color.web("#141517"), EDGE, 14, 3);

        double y = py + 28;
        r.textLeftBold("YOUR NAME", px + 30, y, 13, DIM);
        y += 22;
        textField(ui, px + 30, y, pw - 60, 42, Focus.NAME, name, "your name");
        y += 66;

        r.textLeftBold("PLAY WITH", px + 30, y, 13, DIM);
        y += 22;
        double bw = (pw - 72) / 2;
        ui.button(px + 30, y, bw, 48, "Host a game", GREEN, INK, run(onHost), true);
        ui.button(px + 42 + bw, y, bw, 48, "Join a game", BLUE, INK, run(onJoin), true);
        r.textCenter("others join your address", px + 30 + bw / 2, y + 62, 11, FAINT);
        r.textCenter("at the address below", px + 42 + bw + bw / 2, y + 62, 11, FAINT);
        y += 80;

        textField(ui, px + 30, y, pw - 60, 40, Focus.ADDRESS, address, "host address");
        r.textCenter("Sleeve, stake and deck are chosen in the lobby.", px + pw / 2, py + ph - 18, 11, FAINT);
    }

    private void renderLobby(Ui ui) {
        Renderer r = ui.r;
        r.textCenterBold("LOBBY", Ui.W / 2.0, 74, 42, ORANGE);

        double pw = 980, px = (Ui.W - pw) / 2, py = 116, ph = 570;
        r.panel(px, py, pw, ph, Color.web("#141517"), EDGE, 14, 3);
        ui.button(px + pw - 100, py - 44, 100, 36, host ? "Close lobby" : "Leave", RED, INK, run(onLeave), true);

        if (host) {
            r.textCenter("Others join at  " + hostAddress, px + pw / 2, py + 26, 15, GREEN);
            r.textCenter("(same machine: localhost)", px + pw / 2, py + 46, 11, FAINT);
        } else {
            r.textCenter("Connected to " + address, px + pw / 2, py + 26, 14, DIM);
            r.textCenter("waiting for the host to start", px + pw / 2, py + 46, 11, FAINT);
        }

        double colGap = 30, colW = (pw - 80 - colGap) / 2;
        double lx = px + 40, rx = lx + colW + colGap, top = py + 74;
        renderRoster(ui, lx, top, colW);
        renderLoadout(ui, rx, top, colW);

        List<SeatConfig> seats = seats();
        boolean canStart = seats.size() >= MIN_PLAYERS;
        if (host)
            ui.button(lx, py + ph - 68, pw - 80, 50,
                    canStart ? "Start the match" : "Need " + MIN_PLAYERS + " players",
                    canStart ? GREEN : Color.web("#303237"), canStart ? INK : DIM, run(onBegin), canStart);
        else
            r.textCenter("The host starts the match.", px + pw / 2, py + ph - 44, 13, DIM);
    }

    /** The left column: every seated player with the loadout they picked, plus the seats still open. */
    private void renderRoster(Ui ui, double x, double y, double w) {
        Renderer r = ui.r;
        List<SeatConfig> seats = seats();
        r.textLeftBold("PLAYERS  " + seats.size() + " / " + MAX_PLAYERS, x, y, 13, DIM);
        y += 24;
        for (int i = 0; i < MAX_PLAYERS; i++) {
            if (i < seats.size()) {
                SeatConfig s = seats.get(i);
                boolean me = i == seat;
                r.panel(x, y, w, 56, me ? Color.web("#221d10") : Color.web("#1a1b1f"), me ? ORANGE : EDGE, 10, 2);
                r.textLeftBold(s.name() + (i == 0 ? "  (host)" : "") + (me ? "  ◄ you" : ""), x + 18, y + 12, 15, INK);
                r.textLeft(s.sleeve().displayName() + "  ·  " + s.stake().displayName(), x + 18, y + 33, 11, DIM);
            } else {
                r.panel(x, y, w, 56, Color.web("#141517"), Color.web("#2a2c31"), 10, 2);
                r.textCenter(i < MIN_PLAYERS ? "waiting for a player…" : "open seat", x + w / 2, y + 28, 12, FAINT);
            }
            y += 64;
        }
    }

    /**
     * The right column: this seat's own sleeve and stake, and the table's deck. Changing a cycler sends the pick
     * to the server rather than editing local state — the roster everyone sees is the server's, so a choice is
     * only real once it has been echoed back in a LOBBY frame.
     */
    private void renderLoadout(Ui ui, double x, double y, double w) {
        Renderer r = ui.r;
        r.textLeftBold("YOUR LOADOUT", x, y, 13, DIM);
        y += 24;
        cycler(ui, x, y, w, "Sleeve", sleeve.displayName(),
                () -> changeSleeve(cycle(Sleeve.values(), sleeve, -1)),
                () -> changeSleeve(cycle(Sleeve.values(), sleeve, 1)));
        r.textCenter(sleeve.description(), x + w / 2, y + 62, 11, FAINT);
        y += 86;
        cycler(ui, x, y, w, "Stake", stake.displayName(),
                () -> changeStake(cycle(Stake.values(), stake, -1)),
                () -> changeStake(cycle(Stake.values(), stake, 1)));
        r.textCenter(stake.description(), x + w / 2, y + 62, 11, FAINT);
        y += 100;

        // The deck is the table's: the host picks it, everyone plays it, and everyone sees the current pick.
        r.textLeftBold("TABLE DECK", x, y, 13, DIM);
        y += 24;
        if (host) {
            cycler(ui, x, y, w, "Deck", deck.displayName(),
                    () -> changeDeck(cycle(DeckType.values(), deck, -1)),
                    () -> changeDeck(cycle(DeckType.values(), deck, 1)));
        } else {
            r.textLeftBold("Deck", x, y, 12, FAINT);
            r.panel(x, y + 16, w, 40, Color.web("#1a1b1f"), EDGE, 8, 2);
            r.textCenterBold(deck.displayName(), x + w / 2, y + 36, 15, DIM);
            r.textCenter("the host picks the table's deck", x + w / 2, y + 74, 10, FAINT);
        }
        r.textCenter(deck.description(), x + w / 2, y + 62, 11, FAINT);
    }

    private List<SeatConfig> seats() { return lobby == null ? List.of() : lobby.seats(); }

    // --- widgets -------------------------------------------------------------

    /** A click-to-focus text field with a caret while focused. */
    private void textField(Ui ui, double x, double y, double w, double h, Focus id, String value, String placeholder) {
        boolean active = focus == id;
        ui.r.panel(x, y, w, h, Color.web("#0f1012"), active ? ORANGE : EDGE, 8, active ? 3 : 2);
        boolean empty = value.isEmpty();
        ui.r.textLeft(empty ? placeholder : value + (active ? "|" : ""), x + 14, y + h / 2 - 8, 15,
                empty ? FAINT : INK);
        ui.buttons.add(new Ui.Btn(new Layout.Rect(x, y, w, h), () -> focus = id));
    }

    /** A labelled ◀ value ▶ selector. */
    private void cycler(Ui ui, double x, double y, double w, String label, String value, Runnable prev, Runnable next) {
        ui.r.textLeftBold(label, x, y, 12, FAINT);
        double by = y + 16, bh = 40, aw = 40;
        ui.button(x, by, aw, bh, "◀", Color.web("#2b2c30"), INK, prev, true);
        ui.r.panel(x + aw + 6, by, w - 2 * aw - 12, bh, Color.web("#1a1b1f"), EDGE, 8, 2);
        ui.r.textCenterBold(value, x + w / 2, by + bh / 2, 15, INK);
        ui.button(x + w - aw, by, aw, bh, "▶", Color.web("#2b2c30"), INK, next, true);
    }

    private void changeDeck(DeckType d) {
        deck = d;
        if (onDeckChange != null) onDeckChange.accept(d);
    }

    private void changeSleeve(Sleeve s) { sleeve = s; pushLoadout(); }

    private void changeStake(Stake s) { stake = s; pushLoadout(); }

    private void pushLoadout() { if (onLoadoutChange != null) onLoadoutChange.run(); }

    /**
     * Adopts the server's view of the lobby. The deck especially must come from here rather than from local
     * state: a guest never picks it, so without this it would sit on its default while the host changed it.
     */
    void applyLobby(MatchSetup setup) {
        lobby = setup;
        deck = setup.deck();
        if (seat >= 0 && seat < setup.seats().size()) {
            SeatConfig mine = setup.seats().get(seat);
            sleeve = mine.sleeve();
            stake = mine.stake();
        }
    }

    /** The next value in {@code values} from {@code current}, wrapping in either direction. */
    private static <E extends Enum<E>> E cycle(E[] values, E current, int step) {
        int i = (current.ordinal() + step + values.length) % values.length;
        return values[i];
    }

    private static Runnable run(Runnable r) { return r == null ? () -> { } : r; }

    private static String defaultName() {
        String user = System.getProperty("user.name", "Player");
        return MatchSetup.sanitize(user, "Player");
    }
}
