package model.game.sins;

import model.items.jokers.JokerCard;
import model.items.jokers.Jokers;
import model.items.jokers.Rarity;
import model.game.Match;
import model.game.Sin;
import model.game.player.BlindResult;
import model.game.player.PlayerId;
import model.game.player.Run;
import model.game.rng.Rng;
import model.game.rng.RngSource;
import model.game.shop.ShopSetup;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Pride: at round start each player picks a score multiplier (x1 / x1.5 / x2 / x3) applied to their points if
 * their round score reaches {@code target x multiplier}; and each shop phase auctions a table-rolled legendary
 * joker as a blind, all-pay auction — each seat pays in ${@value #BID_STEP} increments (spent immediately, never
 * refunded), nobody sees anyone else's total, and the single highest total at close takes the joker (a tie leaves
 * it unclaimed; a full-boarded winner simply loses the joker, having already paid). */
public final class PrideModifier implements SinModifier {

    /** The fixed dollar increment of one blind-auction bid. */
    public static final int BID_STEP = 5;

    /** Option index -> multiplier; mirrors {@link #CHOICE} option order. x1 is the no-gamble default. */
    private static final List<BigDecimal> MULTIPLIERS =
            List.of(BigDecimal.ONE, new BigDecimal("1.5"), new BigDecimal("2"), new BigDecimal("3"));

    static final SinChoice CHOICE = new SinChoice(Sin.PRIDE,
            "Pride: choose a score multiplier (higher = harder target, bigger payoff)",
            List.of("x1", "x1.5", "x2", "x3"));

    @Override
    public SinChoice roundChoice() { return CHOICE; }

    @Override
    public void onRoundBegin(Run run) {
        if (run.getMatch() == null) return;   // sins act only within a match
        int i = run.getMatch().getSinChoiceProvider().choose(run, CHOICE);
        if (i < 0 || i >= MULTIPLIERS.size()) i = 0;   // defensive clamp: an invalid answer falls back to x1
        run.getSinState().setPrideMultiplier(MULTIPLIERS.get(i));
    }

    @Override
    public void configureShop(Run run, ShopSetup setup) {
        Match match = run.getMatch();
        if (match == null) return;
        SinTableState table = match.getSinTableState();
        if (table.getPrideLegendary() == null) {   // first seat to open rolls it; the table salt makes it shared
            var stream = match.getRng().streamFor(RngSource.PRIDE_LEGENDARY,
                    Rng.combine(match.getAnte(), match.getBlind().ordinal()));
            table.setPrideLegendary(Jokers.randomOfRarity(Rarity.LEGENDARY, stream).make());
        }
    }

    @Override
    public void onShopPhaseEnd(Match match) {
        SinTableState table = match.getSinTableState();
        JokerCard legendary = table.getPrideLegendary();
        if (legendary == null) return;
        Map<PlayerId, Integer> totals = table.getPrideBids();   // total already paid per seat (all-pay)
        int top = totals.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        if (top > 0) {
            List<PlayerId> leaders = new ArrayList<>();
            for (Map.Entry<PlayerId, Integer> e : totals.entrySet()) if (e.getValue() == top) leaders.add(e.getKey());
            if (leaders.size() == 1) {           // a single highest payer wins; a tie leaves the joker unclaimed
                Run winner = match.getRun(leaders.get(0));
                if (winner.canAcquire(legendary)) winner.acquire(legendary);
                // else: no board room — the joker is lost, and the all-pay bid is not refunded
            }
        }
        table.clearPrideAuction();               // the money was spent as each bid landed; nothing to charge here
    }

    @Override
    public void onRoundSettled(Run run, BlindResult result) {
        BigDecimal threshold = BigDecimal.valueOf(result.target()).multiply(run.getSinState().getPrideMultiplier());
        run.getSinState().setPrideThresholdMet(result.score().compareTo(threshold) >= 0);
    }
}
