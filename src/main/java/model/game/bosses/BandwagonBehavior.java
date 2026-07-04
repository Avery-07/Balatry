package model.game.bosses;

import model.cards.jokers.JokerCard;
import model.game.BossBlind;
import model.game.Match;
import model.game.player.PlayerId;
import model.game.player.Run;
import model.modifiers.Sticker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Bandwagon: the joker owned by the most players (identity by spec name, counted once per seat across
 * every seat) is debuffed for the round — every copy on participating boards gets a {@link Sticker#DEBUFFED}
 * sticker, removed at the barrier. A bandwagon needs riders: with no joker owned by at least two seats, the
 * blind has no effect. Ties break toward the lexicographically smallest name (deterministic and mirror-safe).
 *
 * <p>Only stickers this behaviour added are stripped, mirroring the Katadesmos convention, so a genuinely
 * debuffed joker is never un-debuffed. Known edge, accepted: a mid-round Luchador does not lift the stickers
 * (they are physical state, unlike The Hivemind's play-time gate).
 */
final class BandwagonBehavior implements BossBehavior {

    private final List<JokerCard> stickered = new ArrayList<>();   // exactly the cards this behaviour debuffed

    @Override
    public void onBossBegin(Match match) {
        String target = mostOwnedJoker(match);
        if (target == null) return;
        for (PlayerId id : match.getSeats()) {
            Run run = match.getRun(id);
            if (run.effectiveBoss() != BossBlind.THE_BANDWAGON) continue;
            for (JokerCard joker : run.getJokers()) {
                if (joker.getSpec().getName().equals(target) && !joker.isDebuffed()) {
                    joker.apply(Sticker.DEBUFFED);
                    stickered.add(joker);
                }
            }
        }
    }

    @Override
    public void onBossEnd(Match match) {
        for (JokerCard joker : stickered) joker.remove(Sticker.DEBUFFED);
        stickered.clear();
    }

    /** The spec name owned by the most seats (each seat counts once), requiring at least two owners; ties by name. */
    private static String mostOwnedJoker(Match match) {
        Map<String, Integer> owners = new HashMap<>();
        for (PlayerId id : match.getSeats()) {
            Set<String> owned = new HashSet<>();
            for (JokerCard joker : match.getRun(id).getJokers()) owned.add(joker.getSpec().getName());
            for (String name : owned) owners.merge(name, 1, Integer::sum);
        }
        String best = null;
        int bestCount = 1;   // strictly more than one owner required
        for (Map.Entry<String, Integer> e : owners.entrySet()) {
            if (e.getValue() > bestCount
                    || (e.getValue() == bestCount && best != null && e.getKey().compareTo(best) < 0)) {
                best = e.getKey();
                bestCount = e.getValue();
            }
        }
        return best;
    }
}
