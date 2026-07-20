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
    Runnable onHost, onJoin, onBegin, onLeave;
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

    private void renderMain(Ui ui) {
        Renderer r = ui.r;
        r.textCenterBold("BALATRY", Ui.W / 2.0, 96, 64, ORANGE);
        r.textCenter("a multiplayer Balatro", Ui.W / 2.0, 140, 15, DIM);

        double pw = 560, px = (Ui.W - pw) / 2, py = 186, ph = 448;
        r.panel(px, py, pw, ph, Color.web("#141517"), EDGE, 14, 3);

        double y = py + 26;
        r.textLeftBold("YOU", px + 30, y, 13, DIM);
        y += 22;
        textField(ui, px + 30, y, pw - 60, 40, Focus.NAME, name, "your name");
        y += 56;

        // The loadout. The deck is the table's, so only the host picks it — a guest inherits whatever the host set.
        r.textLeftBold("YOUR LOADOUT", px + 30, y, 13, DIM);
        y += 22;
        cycler(ui, px + 30, y, pw - 60, "Sleeve", sleeve.displayName(),
                () -> sleeve = cycle(Sleeve.values(), sleeve, -1), () -> sleeve = cycle(Sleeve.values(), sleeve, 1));
        r.textCenter(sleeve.description(), px + pw / 2, y + 48, 11, FAINT);
        y += 68;
        cycler(ui, px + 30, y, pw - 60, "Stake", stake.displayName(),
                () -> stake = cycle(Stake.values(), stake, -1), () -> stake = cycle(Stake.values(), stake, 1));
        r.textCenter(stake.description(), px + pw / 2, y + 48, 11, FAINT);
        y += 78;

        r.textLeftBold("PLAY WITH", px + 30, y, 13, DIM);
        y += 22;
        ui.button(px + 30, y, (pw - 72) / 2, 46, "Host a game", GREEN, INK, run(onHost), true);
        r.textCenter("others join your address", px + 30 + (pw - 72) / 4, y + 62, 11, FAINT);

        double jx = px + 30 + (pw - 72) / 2 + 12;
        ui.button(jx, y, (pw - 72) / 2, 46, "Join a game", BLUE, INK, run(onJoin), true);
        r.textCenter("at the address below", jx + (pw - 72) / 4, y + 62, 11, FAINT);
        y += 80;

        textField(ui, px + 30, y, pw - 60, 38, Focus.ADDRESS, address, "host address");
    }

    private void renderLobby(Ui ui) {
        Renderer r = ui.r;
        r.textCenterBold("LOBBY", Ui.W / 2.0, 88, 44, ORANGE);

        double pw = 620, px = (Ui.W - pw) / 2, py = 140, ph = 520;
        r.panel(px, py, pw, ph, Color.web("#141517"), EDGE, 14, 3);
        ui.button(px + pw - 96, py - 52, 96, 38, host ? "Close" : "Leave", RED, INK, run(onLeave), true);

        if (host) {
            r.textCenter("Others join at  " + hostAddress, px + pw / 2, py + 28, 15, GREEN);
            r.textCenter("(same machine: localhost)", px + pw / 2, py + 50, 11, FAINT);
        } else {
            r.textCenter("Connected to " + address, px + pw / 2, py + 28, 14, DIM);
            r.textCenter("waiting for the host to start", px + pw / 2, py + 50, 11, FAINT);
        }

        // The table's deck: the host picks it, everyone plays it.
        double y = py + 76;
        if (host) {
            cycler(ui, px + 40, y, pw - 80, "Table deck", deck.displayName(),
                    () -> changeDeck(cycle(DeckType.values(), deck, -1)),
                    () -> changeDeck(cycle(DeckType.values(), deck, 1)));
        } else {
            r.panel(px + 40, y + 16, pw - 80, 40, Color.web("#1a1b1f"), EDGE, 8, 2);
            r.textCenterBold("Table deck:  " + deck.displayName(), px + pw / 2, y + 36, 14, DIM);
        }
        r.textCenter(deck.description(), px + pw / 2, y + 70, 11, FAINT);
        y += 96;

        r.textLeftBold("PLAYERS", px + 40, y, 13, DIM);
        y += 24;
        List<SeatConfig> seats = lobby == null ? List.of() : lobby.seats();
        for (int i = 0; i < seats.size(); i++) {
            SeatConfig s = seats.get(i);
            boolean me = i == seat;
            r.panel(px + 40, y, pw - 80, 52, me ? Color.web("#221d10") : Color.web("#1a1b1f"), me ? ORANGE : EDGE, 10, 2);
            r.textLeftBold(s.name() + (i == 0 ? "  (host)" : "") + (me ? "  ◄ you" : ""), px + 60, y + 12, 15, INK);
            r.textLeft(s.sleeve().displayName() + "  ·  " + s.stake().displayName(), px + 60, y + 32, 11, DIM);
            y += 60;
        }
        for (int i = seats.size(); i < 2; i++) {   // a match needs two, so show the empty seat as a prompt
            r.panel(px + 40, y, pw - 80, 52, Color.web("#141517"), Color.web("#2a2c31"), 10, 2);
            r.textCenter("waiting for a player…", px + pw / 2, y + 26, 12, FAINT);
            y += 60;
        }

        boolean canStart = seats.size() >= 2;
        if (host)
            ui.button(px + 40, py + ph - 70, pw - 80, 50,
                    canStart ? "Start the match" : "Need 2 players", canStart ? GREEN : Color.web("#303237"),
                    canStart ? INK : DIM, run(onBegin), canStart);
        else
            r.textCenter("The host starts the match.", px + pw / 2, py + ph - 45, 13, DIM);
    }

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
