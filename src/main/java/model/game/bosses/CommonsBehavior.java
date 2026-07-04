package model.game.bosses;

import model.game.BossBlind;
import model.game.Match;
import model.game.player.PlayerId;
import model.game.player.Run;

/**
 * The Commons: all participating seats share one discard pool, sized as the sum of their per-round discards
 * at the deal. Any seat's discard draws the pool down for everyone — the tragedy of the commons, played out
 * in discards. A seat that disables the boss (Chicot at the deal, Luchador mid-round) reverts to its own
 * untouched personal counter; discards it contributed to the pool while participating stay spent.
 */
final class CommonsBehavior implements BossBehavior {

    private SharedDiscardPool pool;   // null until the boss round begins

    @Override
    public void onBossBegin(Match match) {
        int sum = 0;
        for (PlayerId id : match.getSeats()) {
            Run run = match.getRun(id);
            if (run.effectiveBoss() == BossBlind.THE_COMMONS) sum += run.getRound().getDiscardsRemaining();
        }
        pool = new SharedDiscardPool(sum);
    }

    @Override
    public SharedDiscardPool sharedDiscards(Run run) {
        if (pool == null) return null;                                    // pool not built yet (mid-onBossBegin sum)
        return run.effectiveBoss() == BossBlind.THE_COMMONS ? pool : null;   // non-participants keep their own counter
    }
}
