package model.game.bosses;

import model.game.BossBlind;
import model.game.Match;
import model.game.player.PlayerId;
import model.game.player.Run;
import model.game.scoring.HandType;

/** The Hivemind: the most-played hand type across all players (whole-run counts, summed over every seat) is debuffed this round — playing it scores with zero base chips and zero base mult, so the hand's level contribution vanishes while card chips and joker effects still apply. */
final class HivemindBehavior implements BossBehavior {

    @Override
    public void onBossBegin(Match match) {
        HandType debuffed = mostPlayedAcrossTable(match);
        if (debuffed == null) return;   // nobody has played a hand yet — no debuff
        for (PlayerId id : match.getSeats()) {
            Run run = match.getRun(id);
            if (run.effectiveBoss() == BossBlind.THE_HIVEMIND) run.getRound().setDebuffedHandType(debuffed);
        }
    }

    /** The hand type with the highest summed run-count over all seats; ties toward the higher-ranking type. */
    private static HandType mostPlayedAcrossTable(Match match) {
        HandType best = null;
        int bestCount = 0;
        for (HandType type : HandType.values()) {   // declaration order is highest-ranking first
            int total = 0;
            for (PlayerId id : match.getSeats()) total += match.getRun(id).getStats().getHandPlays(type);
            if (total > bestCount) { best = type; bestCount = total; }
        }
        return best;
    }
}
