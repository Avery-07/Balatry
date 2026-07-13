package model.game.actions;

import model.cards.relics.RelicTarget;
import model.game.player.PlayerId;

import java.util.List;

/**
 * One player-submitted command, the only path an untrusted client takes into the model
 * ({@code Match#apply}). Payloads reference state by index, never by object, so every action is
 * serialization-ready and an ordered log of them is simultaneously the network protocol, the replay
 * format, and the determinism contract (server arrival order is canonical order). Table transitions
 * (start, toShop, nextBlind) are deliberately not actions: the server drives them at barriers.
 */
public sealed interface Action {

    /** The seat submitting this action; validated by the dispatcher. */
    PlayerId actor();

    // --- round ---
    record PlayHand(PlayerId actor, List<Integer> handIndices) implements Action { }
    record DiscardCards(PlayerId actor, List<Integer> handIndices) implements Action { }
    record FinishRound(PlayerId actor) implements Action { }
    record SkipBlind(PlayerId actor) implements Action { }

    // --- inventory ---
    record UseConsumable(PlayerId actor, int consumableIndex, List<Integer> targetHandIndices) implements Action { }
    record UseRelic(PlayerId actor, int relicIndex, RelicTarget target) implements Action { }
    record SellJoker(PlayerId actor, int index) implements Action { }
    record SellConsumable(PlayerId actor, int index) implements Action { }
    record SellRelic(PlayerId actor, int index) implements Action { }
    record MoveJoker(PlayerId actor, int from, int to) implements Action { }
    record OpenPack(PlayerId actor, int pendingIndex) implements Action { }
    /** {@code relicTarget} is read only when the picked option is a relic (cast immediately); null means untargeted. */
    record PickFromPack(PlayerId actor, int optionIndex, RelicTarget relicTarget) implements Action { }

    // --- shop ---
    record BuyCard(PlayerId actor, int slotIndex) implements Action { }
    record BuyPack(PlayerId actor, int packIndex) implements Action { }
    record RedeemVoucher(PlayerId actor, int voucherIndex) implements Action { }
    record RerollShop(PlayerId actor) implements Action { }

    // --- sins ---
    record PrideBid(PlayerId actor, int amount) implements Action { }
    record EnvyCopy(PlayerId actor, int logIndex) implements Action { }
    record EnvySwap(PlayerId actor, int myIndex, PlayerId other, int theirIndex) implements Action { }
    record WrathDestroy(PlayerId actor, int jokerIndex) implements Action { }
    record GluttonyEat(PlayerId actor, int jokerIndex) implements Action { }
    /** Answers the pending sin choice (Pride's multiplier, Wrath's relic pick) for the actor's next round. */
    record SubmitSinChoice(PlayerId actor, int optionIndex) implements Action { }

    // --- table-level signals: part of the protocol and the replay log, but handled by the MatchHost, not the Match ---
    /** Declares the actor done with the current shop phase; the host advances when every seat is ready. */
    record ReadyForNext(PlayerId actor) implements Action { }
    /** Withdraws a previously declared readiness. */
    record NotReady(PlayerId actor) implements Action { }
}
