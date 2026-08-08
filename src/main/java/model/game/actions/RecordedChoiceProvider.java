package model.game.actions;

import model.game.player.PlayerId;
import model.game.player.Run;
import model.game.sins.SinChoice;
import model.game.sins.SinChoiceProvider;

import java.util.HashMap;
import java.util.Map;

/**
 * The action-driven {@link SinChoiceProvider}: {@code Action.SubmitSinChoice} records an answer per seat, and
 * the model's synchronous {@link #choose} consumes it when the round begins. Answers are one-shot — consumed on
 * read so a stale choice never leaks into a later round — and a missing answer falls back to option 0, which is
 * every choice's no-gamble default (the server can treat that as its timeout rule for free).
 */
public final class RecordedChoiceProvider implements SinChoiceProvider {

    private final Map<PlayerId, Integer> answers = new HashMap<>();

    /** Records {@code option} as {@code id}'s answer to the next choice asked of them. */
    public void record(PlayerId id, int option) { answers.put(id, option); }

    /** {@code id}'s recorded-but-not-yet-consumed answer, or -1 if none — a read-only peek for the snapshot. */
    public int peek(PlayerId id) {
        Integer a = answers.get(id);
        return a != null ? a : -1;
    }

    @Override
    public int choose(Run run, SinChoice request) {
        Integer answer = answers.remove(run.getPlayerId());
        return answer != null ? answer : 0;
    }
}
