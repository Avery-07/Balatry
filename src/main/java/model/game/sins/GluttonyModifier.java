package model.game.sins;

import model.game.Match;
import model.game.player.PlayerId;
import model.game.player.Run;
import model.game.shop.GluttonyShopPool;
import model.game.shop.ShopSetup;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gluttony: shops contain all types of consumables (the card row swaps to {@link GluttonyShopPool}), jokers
 * become edible ({@code Match#gluttonyEatJoker}: destroy for sell value + ${@value #EAT_BONUS}, counting as a
 * consumable use), and every consumable used mints ${@value #GAUGE_PER_USE} into a communal gauge tracked on
 * the match's {@link SinTableState}.
 *
 * <p>When the ante's boss settles, the gauge pays out: the top consumer takes {@value #LEADER_SHARE_PERCENT}%
 * of the pool and everyone else splits the rest equally. Tied leaders split the leader share; if <em>all</em>
 * seats are tied the whole pool splits equally. Shares are floored to whole dollars and the remainder goes to
 * the first leader in seat order, so the payout is deterministic and always totals the pool.
 */
public final class GluttonyModifier implements SinModifier {

    /** Dollars minted into the communal gauge per consumable used. */
    public static final int GAUGE_PER_USE = 4;
    /** Dollars gained on top of sell value when a joker is eaten. */
    public static final int EAT_BONUS = 5;
    /** The top consumer's share of the gauge at payout. */
    public static final int LEADER_SHARE_PERCENT = 60;

    @Override
    public void configureShop(Run run, ShopSetup setup) {
        setup.setCardPool(GluttonyShopPool.INSTANCE);
    }

    @Override
    public void onConsumableUsed(Run run) {
        if (run.getMatch() == null || run.getPlayerId() == null) return;   // headless runs have no table
        run.getMatch().getSinTableState().recordGluttonyUse(run.getPlayerId(), GAUGE_PER_USE);
    }

    @Override
    public void onAnteSettled(Match match) {
        SinTableState table = match.getSinTableState();
        int pool = table.getGluttonyGauge();
        if (pool <= 0) return;

        List<PlayerId> seats = match.getSeats();
        int max = 0;
        for (PlayerId id : seats) max = Math.max(max, table.gluttonyUses(id));
        List<PlayerId> leaders = new ArrayList<>(), others = new ArrayList<>();
        for (PlayerId id : seats) (table.gluttonyUses(id) == max ? leaders : others).add(id);

        Map<PlayerId, Integer> payout = new LinkedHashMap<>();
        int distributed;
        if (others.isEmpty()) {                    // everyone tied (including all-zero): equal split
            int share = pool / seats.size();
            for (PlayerId id : seats) payout.put(id, share);
            distributed = share * seats.size();
        } else {
            int perLeader = pool * LEADER_SHARE_PERCENT / 100 / leaders.size();
            int perOther  = pool * (100 - LEADER_SHARE_PERCENT) / 100 / others.size();
            for (PlayerId id : leaders) payout.put(id, perLeader);
            for (PlayerId id : others)  payout.put(id, perOther);
            distributed = perLeader * leaders.size() + perOther * others.size();
        }
        payout.merge(leaders.get(0), pool - distributed, Integer::sum);   // floor remainder: first leader in seat order

        for (Map.Entry<PlayerId, Integer> e : payout.entrySet())
            match.getRun(e.getKey()).addMoney(e.getValue());
        table.clearGluttony();   // consumed: a repeated settle cannot double-pay
    }
}
