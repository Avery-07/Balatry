package client.game;

/** Small display-string helpers shared across screens. */
final class Fmt {
    private Fmt() { }

    static String blindName(String blind) {
        return switch (blind == null ? "" : blind) {
            case "SMALL" -> "Small Blind"; case "BIG" -> "Big Blind"; case "BOSS" -> "Boss Blind"; default -> "Blind";
        };
    }

    static String dollars(int n) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < Math.min(n, 6); i++) b.append('$');
        return b + "+";
    }

    static String ordinal(int n) {
        return switch (n) { case 1 -> "1st"; case 2 -> "2nd"; case 3 -> "3rd"; default -> n + "th"; };
    }

    /** A held card/joker's short label: the part before the first '-', else truncated. */
    static String shortName(String s) {
        int cut = s.indexOf('-');
        return cut > 0 ? s.substring(0, cut) : (s.length() > 7 ? s.substring(0, 7) : s);
    }
}
