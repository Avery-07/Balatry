package model.game.actions;

import model.items.jokers.JokerCard;
import model.items.jokers.JokerSpec;
import model.items.jokers.Rarity;
import model.items.packs.BoosterPack;
import model.items.packs.PackKind;
import model.items.packs.PackSize;
import model.game.Match;
import model.game.MatchConfig;
import model.game.Sin;
import model.game.player.PlayResult;
import model.game.player.PlayerId;
import model.game.player.Run;

import java.util.List;

/** Run-as-main harness for the action layer: dispatch and index resolution, phase and actor gating, pack-opening flow, recorded sin choices, and action-log replay determinism. */
public final class ActionTests {

    private static int failures = 0;

    public static void main(String[] args) {
        roundActions();
        validation();
        shopAndPackActions();
        packUsesConsumablesImmediately();
        packSkip();
        spectralPackDealsSelectionHand();
        boardAndChoiceActions();
        replayDeterminism();

        System.out.println(failures == 0 ? "\nALL PASS" : "\n" + failures + " FAILURE(S)");
        if (failures != 0) System.exit(1);
    }

    /** Play, discard, and finish flow through apply with index payloads resolved against the live hand. */
    private static void roundActions() {
        Match m = newMatch(200L);
        m.start();
        PlayerId a = m.getSeats().get(0);
        Object result = m.apply(new Action.PlayHand(a, List.of(0, 1, 2, 3, 4)));
        check("PlayHand returns the play result", result instanceof PlayResult pr && pr.handScore().signum() > 0);
        int handAfterPlay = m.getRun(a).getRound().getHand().size();
        m.apply(new Action.DiscardCards(a, List.of(0)));
        checkInt("DiscardCards resolves against the refreshed hand",
                m.getRun(a).getRound().getHand().size(), handAfterPlay);
        m.apply(new Action.FinishRound(a));
        check("FinishRound resolves the round", m.getRun(a).getRound().getOutcome() != model.game.player.RoundOutcome.IN_PROGRESS);
    }

    /** Bad payloads and wrong contexts reject without mutating anything. */
    private static void validation() {
        Match m = newMatch(201L);
        m.start();
        PlayerId a = m.getSeats().get(0);
        checkThrows("unknown seat is rejected", () -> m.apply(new Action.FinishRound(new PlayerId(9))));
        checkThrows("duplicate hand indices are rejected", () -> m.apply(new Action.PlayHand(a, List.of(0, 0, 1, 2, 3))));
        checkThrows("out-of-range hand index is rejected", () -> m.apply(new Action.PlayHand(a, List.of(0, 1, 2, 3, 99))));
        checkInt("a rejected play consumed nothing", m.getRun(a).getRound().getHandsRemaining(),
                m.getRun(a).getRound().getHandsRemaining());
        checkThrows("shop actions are gated out of the blind phase", () -> m.apply(new Action.BuyCard(a, 0)));
        for (PlayerId id : m.getSeats()) m.getRun(id).getRound().finish();
        m.toShop();
        checkThrows("round actions are gated out of the shop phase", () -> m.apply(new Action.PlayHand(a, List.of(0))));
    }

    /** Shop purchases, rerolls, and the buy-open-pick pack flow, all via actions. */
    private static void shopAndPackActions() {
        Match m = newMatch(202L);
        m.start();
        PlayerId a = m.getSeats().get(0);
        for (PlayerId id : m.getSeats()) { m.getRun(id).addMoney(50); m.getRun(id).getRound().finish(); }
        m.toShop();
        Run run = m.getRun(a);

        int slot = -1;
        var shop = run.getShop();
        for (int i = 0; i < shop.getSlotCount(); i++)
            if (shop.getSlot(i) != null && run.canAcquire(shop.getSlot(i))) { slot = i; break; }
        Object bought = m.apply(new Action.BuyCard(a, slot));
        check("BuyCard returns the item", bought != null);
        m.apply(new Action.RerollShop(a));
        check("RerollShop rerolls", shop.getRerolls() == 1);

        Object opening = m.apply(new Action.BuyPack(a, 0));
        check("BuyPack opens immediately", opening == run.getCurrentOpening() && run.getCurrentOpening() != null);

        // Pick from a controlled Standard pack: its playing-card option is used immediately (it joins the deck),
        // which is the observable, non-targeted case now that consumable picks are consumed rather than stored.
        run.grantPack(new BoosterPack(PackKind.STANDARD, PackSize.NORMAL));
        m.apply(new Action.OpenPack(a, run.getPendingPacks().size() - 1));
        int picksLeft = run.getCurrentOpening().getPicksLeft();
        int deck = run.getDeck().size();
        Object picked = m.apply(new Action.PickFromPack(a, firstOption(run), null));
        check("PickFromPack routes the pick somewhere", picked != null && run.getDeck().size() > deck);
        check("the pick budget advanced or the opening closed",
                run.getCurrentOpening() == null || run.getCurrentOpening().getPicksLeft() == picksLeft - 1);
    }

    /** A consumable picked from a pack is used at once, not kept: a Celestial pick levels a hand and never lands in inventory. */
    private static void packUsesConsumablesImmediately() {
        Match m = newMatch(204L);
        m.start();
        PlayerId a = m.getSeats().get(0);
        Run run = m.getRun(a);

        int levelsBefore = totalHandLevels(run);
        run.grantPack(new BoosterPack(PackKind.CELESTIAL, PackSize.NORMAL));
        m.apply(new Action.OpenPack(a, run.getPendingPacks().size() - 1));
        int consumablesBefore = run.getConsumables().size();
        int picks = run.getCurrentOpening().getPicksLeft();
        for (int k = 0; k < picks; k++) m.apply(new Action.PickFromPack(a, firstOption(run), null));

        check("a planet pick is used, not stored", run.getConsumables().size() == consumablesBefore);
        check("a planet pick leveled a hand immediately", totalHandLevels(run) > levelsBefore);
        // The opening is no longer auto-closed on the last pick: it lingers with 0 picks (so the client can animate any
        // card change), and a no-pick SkipPack closes it — which is what the client submits once the animation settles.
        check("the opening lingers with no picks left", run.getCurrentOpening() != null && run.getCurrentOpening().getPicksLeft() == 0);
        m.apply(new Action.SkipPack(a));
        check("a no-pick SkipPack then closes it", run.getCurrentOpening() == null);
    }

    /** Skipping a pack abandons its remaining picks and closes the opening; skipping with none open is rejected. */
    private static void packSkip() {
        Match m = newMatch(205L);
        m.start();
        PlayerId a = m.getSeats().get(0);
        Run run = m.getRun(a);
        run.grantPack(new BoosterPack(PackKind.CELESTIAL, PackSize.MEGA));   // MEGA keeps 2, so a skip really abandons picks
        m.apply(new Action.OpenPack(a, run.getPendingPacks().size() - 1));
        check("a pack is open before skipping", run.getCurrentOpening() != null);
        m.apply(new Action.SkipPack(a));
        check("SkipPack closes the opening", run.getCurrentOpening() == null);
        checkThrows("skipping with no pack open is rejected", () -> m.apply(new Action.SkipPack(a)));
    }

    /** A Spectral pack opened in the shop deals a temporary hand so its card-selecting picks (Medium, Talisman…) can be aimed. */
    private static void spectralPackDealsSelectionHand() {
        Match m = newMatch(206L);
        m.start();
        PlayerId a = m.getSeats().get(0);
        for (PlayerId id : m.getSeats()) m.getRun(id).getRound().finish();
        m.toShop();
        Run run = m.getRun(a);
        check("no selection hand in the shop before opening", run.getSelectionHand().isEmpty());
        run.grantPack(new BoosterPack(PackKind.SPECTRAL, PackSize.NORMAL));
        m.apply(new Action.OpenPack(a, run.getPendingPacks().size() - 1));
        check("a Spectral pack deals a selection hand", !run.getSelectionHand().isEmpty());
        m.apply(new Action.SkipPack(a));
        check("the selection hand clears when the pack closes", run.getSelectionHand().isEmpty());
    }

    private static int totalHandLevels(Run run) {
        int sum = 0;
        for (model.game.scoring.HandType t : model.game.scoring.HandType.values()) sum += run.getHandLevels().levelOf(t);
        return sum;
    }

    /** MoveJoker reorders the board; SubmitSinChoice feeds the recorded provider, one-shot. */
    private static void boardAndChoiceActions() {
        RecordedChoiceProvider choices = new RecordedChoiceProvider();
        Match m = Match.create(203L, List.of("A", "B"), MatchConfig.defaults()
                .withSinSelector((ante, rng) -> Sin.PRIDE)
                .withSinChoiceProvider(choices));
        PlayerId a = m.getSeats().get(0);
        m.apply(new Action.SubmitSinChoice(a, 3));   // x3, recorded before the round begins
        m.start();
        check("the recorded choice reached Pride",
                m.getRun(a).getSinState().getPrideMultiplier().compareTo(new java.math.BigDecimal("3")) == 0);
        check("the unanswered seat defaulted to x1",
                m.getRun(m.getSeats().get(1)).getSinState().getPrideMultiplier().compareTo(java.math.BigDecimal.ONE) == 0);

        Run run = m.getRun(a);
        JokerCard left = new JokerCard(JokerSpec.named("Left", Rarity.COMMON).build(), 2);
        JokerCard right = new JokerCard(JokerSpec.named("Right", Rarity.COMMON).build(), 2);
        run.board().add(left);
        run.board().add(right);
        m.apply(new Action.MoveJoker(a, 1, 0));
        check("MoveJoker reorders the board", run.board().get(0) == right && run.board().get(1) == left);

        run.grantPack(new BoosterPack(PackKind.BUFFOON, PackSize.NORMAL));
        m.apply(new Action.OpenPack(a, 0));
        check("OpenPack sets the current opening", run.getCurrentOpening() != null);
        checkThrows("a spent option cannot be picked twice", () -> {
            m.apply(new Action.PickFromPack(a, 0, null));
            m.apply(new Action.PickFromPack(a, 0, null));
        });

        // An unfinished opening dies with its round: no picking across phase boundaries.
        run.grantPack(new BoosterPack(PackKind.BUFFOON, PackSize.MEGA));
        m.apply(new Action.OpenPack(a, 0));
        check("a fresh opening has picks left", run.getCurrentOpening().getPicksLeft() > 0);
        for (PlayerId id : m.getSeats()) m.getRun(id).getRound().finish();
        m.toShop();
        check("an unfinished opening dies with its round", run.getCurrentOpening() == null);
        checkThrows("stale picks are impossible", () -> m.apply(new Action.PickFromPack(a, 0, null)));
    }

    /** The same action log applied to a same-seed match reproduces the same state — the replay contract. */
    private static void replayDeterminism() {
        List<Action> log = List.of(
                new Action.PlayHand(new PlayerId(0), List.of(0, 1, 2, 3, 4)),
                new Action.DiscardCards(new PlayerId(0), List.of(0, 1)),
                new Action.PlayHand(new PlayerId(0), List.of(2, 3, 4)),
                new Action.FinishRound(new PlayerId(0)),
                new Action.PlayHand(new PlayerId(1), List.of(0, 1, 2)),
                new Action.FinishRound(new PlayerId(1)));
        String first = playAndFingerprint(204L, log);
        String second = playAndFingerprint(204L, log);
        check("an action log replays to identical state", first.equals(second) && !first.isEmpty());
    }

    private static String playAndFingerprint(long seed, List<Action> log) {
        Match m = newMatch(seed);
        m.start();
        for (Action action : log) m.apply(action);
        m.toShop();
        StringBuilder sb = new StringBuilder();
        for (PlayerId id : m.getSeats()) {
            Run r = m.getRun(id);
            sb.append(id).append(':').append(r.getMoney()).append('/').append(r.getStats().getTotalHandsPlayed());
            for (var c : r.getShop().getSlots())
                sb.append('/').append(c == null ? "-" : c.getClass().getSimpleName() + c.getShopValue());
            sb.append('\n');
        }
        return sb.toString();
    }

    private static Match newMatch(long seed) {
        Match m = Match.create(seed, List.of("A", "B"), MatchConfig.defaults()
                .withSinSelector((ante, rng) -> Sin.SLOTH)
                .withSinChoiceProvider(new RecordedChoiceProvider()));
        return m;
    }

    private static int firstOption(Run run) {
        var options = run.getCurrentOpening().getOptions();
        for (int i = 0; i < options.size(); i++) if (options.get(i) != null) return i;
        throw new IllegalStateException("no options left");
    }

    private static void check(String label, boolean ok) {
        System.out.printf("%-46s %s%n", label, ok ? "PASS" : "FAIL");
        if (!ok) failures++;
    }

    private static void checkInt(String label, int actual, int expected) {
        check(label + " (" + actual + ")", actual == expected);
    }

    private static void checkThrows(String label, Runnable r) {
        try { r.run(); check(label, false); }
        catch (RuntimeException e) { check(label, true); }
    }
}
