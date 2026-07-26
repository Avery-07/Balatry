package client.game;

import model.items.DeckCard;
import model.game.scoring.HandType;

/** Small display-string helpers shared across screens (public: {@code client.MatchSnapshot} shares them too). */
public final class Fmt {
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

    /** {@code FOUR_OF_A_KIND} → {@code Four of a Kind}, via the model's single naming authority. */
    static String handName(String enumName) {
        try {
            return HandType.valueOf(enumName).displayName();
        } catch (IllegalArgumentException e) {
            return title(enumName);   // an unknown type still renders readably
        }
    }

    /** "King of Hearts" from rank/suit ordinals ({@code Rank}/{@code Suit} enum order, as the snapshot ships). */
    static String cardTitle(int rankOrd, int suitOrd) {
        DeckCard.Rank[] ranks = DeckCard.Rank.values();
        DeckCard.Suit[] suits = DeckCard.Suit.values();
        String rank = rankOrd >= 0 && rankOrd < ranks.length ? ranks[rankOrd].displayName() : "?";
        String suit = suitOrd >= 0 && suitOrd < suits.length ? suits[suitOrd].displayName() : "?";
        return rank + " of " + suit;
    }

    /**
     * A hand card's hover text from its snapshot label ({@code KING-HEARTS[GOLD]{RED}<FOIL>(Eternal · Sticky $5)}):
     * the readable title, then one line per extra — enhancement in brackets, seal in braces, edition in angle
     * brackets, and any stickers (already display-formatted) in parentheses.
     */
    static String cardTip(String label, int rankOrd, int suitOrd) {
        StringBuilder tip = new StringBuilder(cardTitle(rankOrd, suitOrd));
        int b = label.indexOf('['), be = label.indexOf(']');
        if (b >= 0 && be > b) tip.append('\n').append(title(label.substring(b + 1, be))).append(" card");
        int c = label.indexOf('{'), ce = label.indexOf('}');
        if (c >= 0 && ce > c) tip.append('\n').append(title(label.substring(c + 1, ce))).append(" seal");
        int d = label.indexOf('<'), de = label.indexOf('>');
        if (d >= 0 && de > d) tip.append('\n').append(title(label.substring(d + 1, de))).append(" edition");
        int e = label.indexOf('('), ee = label.indexOf(')');
        if (e >= 0 && ee > e) tip.append('\n').append(label.substring(e + 1, ee));   // stickers, already formatted
        return tip.toString();
    }

    /** {@code GOLD} → {@code Gold}; public — the one title-caser for enum-name display everywhere. */
    public static String title(String s) {
        if (s.isEmpty()) return s;
        String lower = s.toLowerCase().replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
