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

    /** {@code FOUR_OF_A_KIND} → {@code Four of a Kind}; keeps the little words lowercase. */
    static String handName(String enumName) {
        String[] words = enumName.toLowerCase().split("_");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            String w = words[i];
            if (i > 0) out.append(' ');
            if (i > 0 && (w.equals("of") || w.equals("a"))) out.append(w);
            else out.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return out.toString();
    }

    /** "King of Hearts" from rank/suit ordinals (the sprite-sheet order the whole client uses). */
    static String cardTitle(int rankOrd, int suitOrd) {
        String[] ranks = { "2", "3", "4", "5", "6", "7", "8", "9", "10", "Jack", "Queen", "King", "Ace" };
        String[] suits = { "Hearts", "Clubs", "Diamonds", "Spades" };
        String rank = rankOrd >= 0 && rankOrd < ranks.length ? ranks[rankOrd] : "?";
        String suit = suitOrd >= 0 && suitOrd < suits.length ? suits[suitOrd] : "?";
        return rank + " of " + suit;
    }

    /**
     * A hand card's hover text from its snapshot label ({@code KING-HEARTS[GOLD]{RED}}): the readable title,
     * then one line per extra — the enhancement in brackets and the seal in braces.
     */
    static String cardTip(String label, int rankOrd, int suitOrd) {
        StringBuilder tip = new StringBuilder(cardTitle(rankOrd, suitOrd));
        int b = label.indexOf('['), be = label.indexOf(']');
        if (b >= 0 && be > b) tip.append('\n').append(title(label.substring(b + 1, be))).append(" card");
        int c = label.indexOf('{'), ce = label.indexOf('}');
        if (c >= 0 && ce > c) tip.append('\n').append(title(label.substring(c + 1, ce))).append(" seal");
        return tip.toString();
    }

    /** {@code GOLD} → {@code Gold}. */
    static String title(String s) {
        if (s.isEmpty()) return s;
        String lower = s.toLowerCase().replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
