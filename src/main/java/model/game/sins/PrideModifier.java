package model.game.sins;

import model.cards.jokers.JokerCard;
import model.cards.jokers.Jokers;
import model.cards.jokers.Rarity;
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
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Pride: at round start each player picks a score multiplier (x1 / x1.5 / x2 / x3) applied to their points if
 * their round score reaches {@code target x multiplier}; and each shop phase auctions a table-rolled legendary
 * joker — highest standing bid at close wins and pays, ties (among valid leaders) mean nobody wins, an
 * insolvent or full-boarded leader is skipped for the next-highest. Losers pay nothing. */
public final class PrideModifier implements SinModifier {

    /** Option index -> multiplier; mirrors {@link #CHOICE} option order. x1 is the no-gamble default. */
    private static final List<BigDecimal> MULTIPLIERS =
            List.of(BigDecimal.ONE, new BigDecimal("1.5"), new BigDecimal("2"), new BigDecimal("3"));

    static final SinChoice CHOICE = new SinChoice(Sin.PRIDE,
            "Pride: choose a score multiplier (higher = harder target, bigger payoff)",
            List.of("x1", "x1.5", "x2", "x3"));

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
        Map<PlayerId, Integer> bids = table.getPrideBids();
        List<Integer> amounts = bids.values().stream().distinct().sorted(Comparator.reverseOrder()).toList();
        outer:
        for (int amount : amounts) {
            List<PlayerId> valid = new ArrayList<>();
            for (Map.Entry<PlayerId, Integer> e : bids.entrySet()) {
                if (e.getValue() != amount) continue;
                Run run = match.getRun(e.getKey());
                if (run.getMoney() - amount >= run.minBalance() && run.canAcquire(legendary)) valid.add(e.getKey());
            }
            if (valid.size() > 1) break;         // a tie among valid leaders: the joker is too proud to be shared
            if (valid.size() == 1) {
                Run winner = match.getRun(valid.get(0));
                winner.spend(amount);
                winner.acquire(legendary);
                break outer;
            }                                    // nobody valid at this amount: consider the next-highest
        }
        table.clearPrideAuction();
    }

    @Override
    public void onRoundSettled(Run run, BlindResult result) {
        BigDecimal threshold = BigDecimal.valueOf(result.target()).multiply(run.getSinState().getPrideMultiplier());
        run.getSinState().setPrideThresholdMet(result.score().compareTo(threshold) >= 0);
    }
}
