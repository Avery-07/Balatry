package client.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleUnaryOperator;

/**
 * Diffs an incoming snapshot into the retained {@link CardEntity} list instead of rebuilding it. Entities are
 * matched by stable id: a card already on screen keeps its object — and therefore its in-flight motion and its
 * selection — while a newly-dealt card is spawned at the deck position so the renderer can glide it into place.
 * This is the core of the shippable renderer: no frame ever wipes client-only UI state, and every change becomes
 * an animation target rather than a teardown. Pure and unit-tested.
 */
public final class Reconciler {

    private Reconciler() { }

    /** One card the snapshot wants on screen, in hand order. {@code enhancement}/{@code seal}/{@code edition} are ordinals, -1 = none. */
    public record Desired(int id, int rank, int suit, int enhancement, int seal, int edition, String label) {
        public Desired(int id, int rank, int suit, String label) { this(id, rank, suit, -1, -1, -1, label); }
    }

    /**
     * Returns a fresh list in {@code desired} order, reusing existing entities by id (preserving their motion and
     * selection) and creating new ones at {@code (spawnX, spawnY)}. Entities no longer desired are simply left out
     * (the caller may keep them briefly for an exit animation; this list is the live set).
     */
    public static List<CardEntity> reconcile(List<CardEntity> existing, List<Desired> desired,
                                             double spawnX, double spawnY,
                                             double durationSeconds, DoubleUnaryOperator ease) {
        Map<Integer, CardEntity> byId = new HashMap<>();
        for (CardEntity e : existing) byId.put(e.id(), e);

        List<CardEntity> out = new ArrayList<>(desired.size());
        for (Desired d : desired) {
            CardEntity e = byId.get(d.id());
            if (e == null) {
                e = new CardEntity(d.id(), d.rank(), d.suit(), d.label(), d.enhancement(), d.seal(), d.edition(), spawnX, spawnY, durationSeconds, ease);
            } else {
                e.update(d.rank(), d.suit(), d.enhancement(), d.seal(), d.edition(), d.label());   // keeps its Motion and selection
            }
            out.add(e);
        }
        return out;
    }
}
