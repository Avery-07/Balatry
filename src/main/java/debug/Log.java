package debug;

import model.game.actions.Action;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;

/**
 * A tiny categorized debug logger. Because every accepted action round-trips to every client, watching the
 * {@code RECV} stream in the terminal shows exactly what each seat sees, in order — invaluable while wiring new
 * gestures. Output is plain {@code stdout}, one timestamped line per event:
 *
 * <pre>[12:03:41.207] [RECV] seat 1        BuyCard[actor=PlayerId[seat=1], slotIndex=2]</pre>
 *
 * <p>Categories can be toggled at runtime via {@link #setEnabled}; all are on by default. This lives entirely on
 * the client side (nothing in the model or transport calls it), so the harness build stays silent.
 */
public final class Log {

    public enum Category { RECV, SEND, PHASE, UI, ERROR }

    private static final EnumSet<Category> ENABLED = EnumSet.allOf(Category.class);
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private Log() { }

    public static void setEnabled(Category c, boolean on) {
        if (on) ENABLED.add(c); else ENABLED.remove(c);
    }

    public static boolean isEnabled(Category c) {
        return ENABLED.contains(c);
    }

    public static void log(Category c, String msg) {
        if (!ENABLED.contains(c)) return;
        System.out.println("[" + LocalTime.now().format(TS) + "] [" + c + "] " + msg);
    }

    /** An accepted action arriving from the server (includes this seat's own, echoed back). */
    public static void recv(Action a, int localSeat) {
        int actor = a.actor().seat();
        log(Category.RECV, "seat " + actor + (actor == localSeat ? " (self)" : "") + "\t" + a);
    }

    /** An action this client is submitting. */
    public static void send(Action a) {
        log(Category.SEND, "seat " + a.actor().seat() + "\t" + a);
    }

    public static void phase(Object from, Object to) {
        log(Category.PHASE, from + " -> " + to);
    }

    public static void ui(String msg) {
        log(Category.UI, msg);
    }

    public static void error(String msg) {
        log(Category.ERROR, msg);
    }
}
