package model.game.net;

import model.cards.DeckCard.Rank;
import model.cards.DeckCard.Suit;
import model.cards.relics.RelicTarget;
import model.game.actions.Action;
import model.game.player.PlayerId;
import model.game.scoring.HandType;

import java.util.ArrayList;
import java.util.List;

/**
 * A zero-dependency, one-line-per-action wire codec: a tab-separated record of a type tag and scalar fields,
 * safe to send over a raw socket's text stream. Deliberately hand-rolled rather than reflective so the sealed
 * {@link Action} hierarchy is exhaustively compile-checked here — a new action record fails to build until it
 * is given a wire form — and so the whole thing is testable in a sandbox with no dependency to fetch. When the
 * project moves to a build with Jackson on the classpath, this class is the single swap point.
 *
 * <p>Grammar: {@code TAG \t field \t field ...}, fields never contain tabs or newlines (all are ints, enum
 * names, or {@code -} for absent). Index lists are comma-joined; an empty list is {@code -}.
 */
public final class ActionCodec {

    private static final String SEP = "\t";
    private static final String NONE = "-";

    private ActionCodec() { }

    /** Serialize one action to a single newline-free line. */
    public static String encode(Action a) {
        return switch (a) {
            case Action.PlayHand x       -> join("PLAY", x.actor(), ints(x.handIndices()));
            case Action.DiscardCards x   -> join("DISCARD", x.actor(), ints(x.handIndices()));
            case Action.FinishRound x    -> join("FINISH", x.actor());
            case Action.SkipBlind x      -> join("SKIP", x.actor());
            case Action.UseConsumable x  -> join("USECONS", x.actor(), x.consumableIndex(), ints(x.targetHandIndices()));
            case Action.UseRelic x       -> join("USERELIC", x.actor(), x.relicIndex(), target(x.target()));
            case Action.SellJoker x      -> join("SELLJOKER", x.actor(), x.index());
            case Action.SellConsumable x -> join("SELLCONS", x.actor(), x.index());
            case Action.SellRelic x      -> join("SELLRELIC", x.actor(), x.index());
            case Action.MoveJoker x      -> join("MOVE", x.actor(), x.from(), x.to());
            case Action.OpenPack x       -> join("OPENPACK", x.actor(), x.pendingIndex());
            case Action.PickFromPack x   -> join("PICK", x.actor(), x.optionIndex(), target(x.relicTarget()));
            case Action.BuyCard x        -> join("BUY", x.actor(), x.slotIndex());
            case Action.BuyPack x        -> join("BUYPACK", x.actor(), x.packIndex());
            case Action.RedeemVoucher x  -> join("VOUCHER", x.actor(), x.voucherIndex());
            case Action.RerollShop x     -> join("REROLL", x.actor());
            case Action.PrideBid x       -> join("BID", x.actor(), x.amount());
            case Action.EnvyCopy x       -> join("COPY", x.actor(), x.logIndex());
            case Action.EnvySwap x       -> join("SWAP", x.actor(), x.myIndex(), seat(x.other()), x.theirIndex());
            case Action.WrathDestroy x   -> join("DESTROY", x.actor(), x.jokerIndex());
            case Action.GluttonyEat x    -> join("EAT", x.actor(), x.jokerIndex());
            case Action.SubmitSinChoice x-> join("CHOICE", x.actor(), x.optionIndex());
            case Action.ReadyForNext x   -> join("READY", x.actor());
            case Action.NotReady x       -> join("UNREADY", x.actor());
        };
    }

    /** Parse one line back into an action; throws on an unknown tag or malformed field. */
    public static Action decode(String line) {
        String[] f = line.split(SEP, -1);
        String tag = f[0];
        PlayerId actor = seat(f[1]);
        return switch (tag) {
            case "PLAY"      -> new Action.PlayHand(actor, indexList(f[2]));
            case "DISCARD"   -> new Action.DiscardCards(actor, indexList(f[2]));
            case "FINISH"    -> new Action.FinishRound(actor);
            case "SKIP"      -> new Action.SkipBlind(actor);
            case "USECONS"   -> new Action.UseConsumable(actor, i(f[2]), indexList(f[3]));
            case "USERELIC"  -> new Action.UseRelic(actor, i(f[2]), target(f, 3));
            case "SELLJOKER" -> new Action.SellJoker(actor, i(f[2]));
            case "SELLCONS"  -> new Action.SellConsumable(actor, i(f[2]));
            case "SELLRELIC" -> new Action.SellRelic(actor, i(f[2]));
            case "MOVE"      -> new Action.MoveJoker(actor, i(f[2]), i(f[3]));
            case "OPENPACK"  -> new Action.OpenPack(actor, i(f[2]));
            case "PICK"      -> new Action.PickFromPack(actor, i(f[2]), target(f, 3));
            case "BUY"       -> new Action.BuyCard(actor, i(f[2]));
            case "BUYPACK"   -> new Action.BuyPack(actor, i(f[2]));
            case "VOUCHER"   -> new Action.RedeemVoucher(actor, i(f[2]));
            case "REROLL"    -> new Action.RerollShop(actor);
            case "BID"       -> new Action.PrideBid(actor, i(f[2]));
            case "COPY"      -> new Action.EnvyCopy(actor, i(f[2]));
            case "SWAP"      -> new Action.EnvySwap(actor, i(f[2]), seat(f[3]), i(f[4]));
            case "DESTROY"   -> new Action.WrathDestroy(actor, i(f[2]));
            case "EAT"       -> new Action.GluttonyEat(actor, i(f[2]));
            case "CHOICE"    -> new Action.SubmitSinChoice(actor, i(f[2]));
            case "READY"     -> new Action.ReadyForNext(actor);
            case "UNREADY"   -> new Action.NotReady(actor);
            default -> throw new IllegalArgumentException("unknown action tag: " + tag);
        };
    }

    // --- field codecs ---

    private static String join(String tag, PlayerId actor, Object... rest) {
        StringBuilder sb = new StringBuilder(tag).append(SEP).append(actor.seat());
        for (Object r : rest) sb.append(SEP).append(r);
        return sb.toString();
    }

    private static String ints(List<Integer> xs) {
        if (xs == null || xs.isEmpty()) return NONE;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < xs.size(); i++) { if (i > 0) sb.append(','); sb.append(xs.get(i)); }
        return sb.toString();
    }

    private static List<Integer> indexList(String s) {
        List<Integer> out = new ArrayList<>();
        if (s.equals(NONE) || s.isEmpty()) return out;
        for (String part : s.split(",")) out.add(Integer.parseInt(part));
        return out;
    }

    private static int i(String s) { return Integer.parseInt(s); }

    private static PlayerId seat(String s) { return s.equals(NONE) ? null : new PlayerId(Integer.parseInt(s)); }
    private static String seat(PlayerId id) { return id == null ? NONE : String.valueOf(id.seat()); }

    /** A RelicTarget serializes as five fields: opponent, rank, suit, jokerIndex, handType. */
    private static String target(RelicTarget t) {
        if (t == null) return NONE + SEP + NONE + SEP + NONE + SEP + RelicTarget.NO_INDEX + SEP + NONE;
        return seat(t.opponent()) + SEP + name(t.rank()) + SEP + name(t.suit())
                + SEP + t.jokerIndex() + SEP + name(t.handType());
    }

    private static RelicTarget target(String[] f, int at) {
        PlayerId opp = seat(f[at]);
        Rank rank = f[at + 1].equals(NONE) ? null : Rank.valueOf(f[at + 1]);
        Suit suit = f[at + 2].equals(NONE) ? null : Suit.valueOf(f[at + 2]);
        int jokerIndex = i(f[at + 3]);
        HandType handType = f[at + 4].equals(NONE) ? null : HandType.valueOf(f[at + 4]);
        if (opp == null && rank == null && suit == null && jokerIndex == RelicTarget.NO_INDEX && handType == null)
            return RelicTarget.none();
        return new RelicTarget(opp, rank, suit, jokerIndex, handType);
    }

    private static String name(Enum<?> e) { return e == null ? NONE : e.name(); }
}
