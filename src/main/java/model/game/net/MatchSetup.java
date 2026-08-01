package model.game.net;

import model.items.DeckType;
import model.game.MatchConfig;
import model.game.SinSelector;
import model.game.Stake;
import model.game.host.MatchHost;
import model.game.player.SeatConfig;
import model.game.player.Sleeve;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The agreed starting state of a match: seed, table deck, and the full seat roster (each seat's name, sleeve and
 * stake). Lockstep replay means the server and every client must build an <em>identical</em> starting match, so
 * this is the one thing that has to be shared verbatim before play begins.
 *
 * <p>It used to be read from system properties on every process independently, which meant a typo on one client
 * desynced the match silently. Now the server owns the roster — it collects each seat's choices as they join and
 * broadcasts the assembled setup — and {@link #encode}/{@link #decode} are that wire form. The property reader
 * survives only for the headless {@code ServerMain}, where it seeds the host's own defaults.
 *
 * <pre>
 *   -Dbalatry.seed=42
 *   -Dbalatry.deck=STANDARD                       (a {@link DeckType} name; the whole table shares it)
 *   -Dbalatry.players=P0,P1                       (names only: everyone on the default sleeve and stake)
 *   -Dbalatry.players=Ann:RED_BLUE:GOLD,Bo:black:purple   (name:sleeve:stake, per seat; case-insensitive)
 * </pre>
 */
public record MatchSetup(long seed, DeckType deck, boolean sinsEnabled, List<SeatConfig> seats) {

    /** Field separator inside one seat entry, and between seats — kept out of names by {@link #sanitize}. */
    private static final String SEAT_SEP = ",", FIELD_SEP = ":";

    public MatchSetup {
        if (deck == null) deck = DeckType.STANDARD;
        seats = List.copyOf(seats);
    }

    /** Backward-compatible construction with sins on (the default table rule). */
    public MatchSetup(long seed, DeckType deck, List<SeatConfig> seats) { this(seed, deck, true, seats); }

    /** Reads the setup from system properties, applying the documented defaults. */
    public static MatchSetup fromProperties() {
        return new MatchSetup(
                Long.parseLong(System.getProperty("balatry.seed", "42")),
                parse(DeckType.class, System.getProperty("balatry.deck", "STANDARD")),
                !"false".equalsIgnoreCase(System.getProperty("balatry.sins", "true")),
                parseSeats(System.getProperty("balatry.players", "P0,P1")));
    }

    /** The match config this setup implies: the networked policies, the table's deck, and whether sins are active. */
    public MatchConfig config() {
        MatchConfig c = MatchHost.networkedConfig().withDeckType(deck);
        return sinsEnabled ? c : c.withSinSelector(SinSelector.NONE);
    }

    /** Seat names in order, for logging and for the seat count the server waits on. */
    public List<String> names() {
        List<String> out = new ArrayList<>(seats.size());
        for (SeatConfig s : seats) out.add(s.name());
        return out;
    }

    /** This setup with a different table deck (the host's pick in the lobby). */
    public MatchSetup withDeck(DeckType d) { return new MatchSetup(seed, d, sinsEnabled, seats); }

    /** This setup with sins turned on or off (the host's pick in the lobby). */
    public MatchSetup withSins(boolean on) { return new MatchSetup(seed, deck, on, seats); }

    /** This setup with a different roster (a seat joining, leaving, or changing its loadout). */
    public MatchSetup withSeats(List<SeatConfig> s) { return new MatchSetup(seed, deck, sinsEnabled, s); }

    // --- wire form ---------------------------------------------------------

    /** {@code seed<TAB>DECK<TAB>SINS<TAB>name:SLEEVE:STAKE,…} — the exact bytes every side must agree on. */
    public String encode() {
        StringBuilder sb = new StringBuilder().append(seed).append('\t').append(deck.name())
                .append('\t').append(sinsEnabled).append('\t');
        for (int i = 0; i < seats.size(); i++) {
            SeatConfig s = seats.get(i);
            if (i > 0) sb.append(SEAT_SEP);
            sb.append(s.name()).append(FIELD_SEP).append(s.sleeve().name()).append(FIELD_SEP).append(s.stake().name());
        }
        return sb.toString();
    }

    /** Parses {@link #encode}'s form. An empty roster is legal: a lobby nobody has joined yet. */
    public static MatchSetup decode(String line) {
        String[] parts = line.split("\t", -1);
        if (parts.length < 4) throw new IllegalArgumentException("malformed setup: " + line);
        List<SeatConfig> seats = parts[3].isBlank() ? List.of() : parseSeats(parts[3]);
        return new MatchSetup(Long.parseLong(parts[0].trim()), parse(DeckType.class, parts[1]),
                Boolean.parseBoolean(parts[2].trim()), seats);
    }

    // --- parsing -----------------------------------------------------------

    /** Parses a {@link DeckType} name, case-insensitively. */
    public static DeckType parseDeck(String spec) { return parse(DeckType.class, spec); }

    /** Parses {@code name[:sleeve[:stake]]} entries, comma-separated. */
    public static List<SeatConfig> parseSeats(String spec) {
        List<SeatConfig> seats = new ArrayList<>();
        for (String entry : spec.split(SEAT_SEP)) {
            String[] parts = entry.trim().split(FIELD_SEP);
            String name = parts[0].trim();
            Sleeve sleeve = parts.length > 1 ? parse(Sleeve.class, parts[1]) : Sleeve.STANDARD;
            Stake stake   = parts.length > 2 ? parse(Stake.class, parts[2])  : Stake.WHITE;
            seats.add(new SeatConfig(name, sleeve, stake));
        }
        return seats;
    }

    /**
     * Strips the separators and whitespace the wire form reserves, so a player-typed name can never corrupt the
     * roster line. Falls back to {@code fallback} if nothing usable is left.
     */
    public static String sanitize(String name, String fallback) {
        if (name == null) return fallback;
        String clean = name.replaceAll("[\\t\\r\\n" + SEAT_SEP + FIELD_SEP + "]", "").trim();
        return clean.isEmpty() ? fallback : clean;
    }

    /** Case-insensitive enum lookup that names the legal values when it fails. */
    private static <E extends Enum<E>> E parse(Class<E> type, String raw) {
        String key = raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('&', '_');
        try {
            return Enum.valueOf(type, key);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown " + type.getSimpleName() + " '" + raw.trim()
                    + "'; expected one of " + List.of(type.getEnumConstants()));
        }
    }
}
