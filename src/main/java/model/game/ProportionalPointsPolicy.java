package model.game;

import model.game.player.BlindResult;
import model.game.player.PlayerId;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

/** The default {@link PointsPolicy}: every seat that cleared the blind receives a share of the round's reward proportional to its chip score — a seat that scored a fifth of the cleared seats' combined chips earns a fifth of the reward. */
public final class ProportionalPointsPolicy implements PointsPolicy {

    private static final MathContext SHARE_PRECISION = new MathContext(20);
    private static final long PER_ROUND = 50;   // g(x) = PER_ROUND * x + BASE
    private static final long BASE = 50;

    /** The reward for the given blind: {@code 50x + 50} on the 1-based global round index. */
    public static long reward(int ante, Blind blind) {
        int roundIndex = (ante - 1) * 3 + blindIndex(blind);
        return PER_ROUND * roundIndex + BASE;
    }

    @Override
    public Map<PlayerId, Long> award(int ante, Blind blind, Map<PlayerId, BlindResult> results) {
        BigDecimal total = BigDecimal.ZERO;
        for (BlindResult r : results.values())
            if (r.cleared()) total = total.add(r.score());
        Map<PlayerId, Long> award = new LinkedHashMap<>();
        if (total.signum() <= 0) return award;   // nobody cleared: no points this round

        BigDecimal reward = BigDecimal.valueOf(reward(ante, blind));
        for (Map.Entry<PlayerId, BlindResult> e : results.entrySet()) {
            BlindResult r = e.getValue();
            if (!r.cleared()) continue;
            BigDecimal share = reward.multiply(r.score()).divide(total, SHARE_PRECISION);
            award.put(e.getKey(), share.setScale(0, RoundingMode.HALF_UP).longValueExact());
        }
        return award;
    }

    /** 1-based position of the blind within its ante, independent of enum ordinal. */
    private static int blindIndex(Blind blind) {
        return switch (blind) {
            case SMALL -> 1;
            case BIG   -> 2;
            case BOSS  -> 3;
        };
    }
}
