package model.cards.relics;

import model.cards.consumables.ConsumableCard;
import model.cards.consumables.ConsumableSpec;
import model.game.player.Run;

import java.util.List;
import java.util.random.RandomGenerator;

/**
 * The ten Relics — Balatry's multiplayer-facing cards. A relic is bought/found, held in the relic area, and
 * spent through {@link model.game.Match#useRelic} with a {@link RelicTarget}. Effects fall into three groups:
 * round-scoped opponent debuffs that lodge on the target's {@link model.game.player.Afflictions}
 * (Anathema/Miasma/Katadesmos/Limos), immediate cross-player effects (Pyre/Katabasis/Harpax), and self/global
 * effects (Metabole/Mimesis/Aegis). Hostile effects are gated by the resolver: an armed Aegis on the target
 * negates them, and the targeting is recorded for Anger either way.
 *
 * <p>Costs are uniform for now ({@link #COST}); they are a tuning knob, not a balance claim.
 */
public enum Relics {

    /** Debuffs a rank for one selected opponent for their next round. */
    ANATHEMA("Anathema", RelicKind.OPPONENT, ctx ->
            ctx.target().getAfflictions().armRankDebuff(ctx.selection().rank())),

    /** Debuffs a suit for one selected opponent for their next round. */
    MIASMA("Miasma", RelicKind.OPPONENT, ctx ->
            ctx.target().getAfflictions().armSuitDebuff(ctx.selection().suit())),

    /** Debuffs a selected board position of an opponent for their next round (the joker is not shown to the caster). */
    KATADESMOS("Katadesmos", RelicKind.OPPONENT, ctx ->
            ctx.target().getAfflictions().armJokerDebuff(ctx.selection().jokerIndex())),

    /** Destroys a random consumable from a selected opponent. */
    PYRE("Pyre", RelicKind.OPPONENT, ctx -> {
        List<ConsumableCard> cs = ctx.target().getConsumables();
        if (!cs.isEmpty()) ctx.target().destroyConsumable(cs.get(ctx.random().nextInt(cs.size())));
    }),

    /** Debuffs the first slot of a selected player's next shop. */
    LIMOS("Limos", RelicKind.PLAYER, ctx ->
            ctx.target().getAfflictions().armFirstSlotDebuff()),

    /** Levels down a selected hand type of a selected player. */
    KATABASIS("Katabasis", RelicKind.PLAYER, ctx -> {
        if (ctx.selection().handType() != null)
            ctx.target().getHandLevels().levelDown(ctx.selection().handType());
    }),

    /** Rerolls the shared boss blind of the next ante (a table-level change for every seat). */
    METABOLE("Metabole", RelicKind.GLOBAL, ctx -> ctx.match().rerollNextBoss()),

    /** Creates a copy of the last consumable used by any player. */
    MIMESIS("Mimesis", RelicKind.SELF, ctx -> {
        ConsumableSpec last = ctx.match().getLastConsumableUsed();
        if (last != null) ctx.source().createConsumable(last);
    }),

    /** Steals 25% (rounded down) of a selected opponent's money. */
    HARPAX("Harpax", RelicKind.OPPONENT, ctx -> {
        Run target = ctx.target();
        int amount = Math.max(0, target.getMoney()) / 4;
        if (amount > 0) {
            target.addMoney(-amount);
            ctx.source().addMoney(amount);
        }
    }),

    /** Negates the next hostile effect aimed at the caster this ante. */
    AEGIS("Aegis", RelicKind.SELF, ctx -> ctx.source().getAfflictions().armAegis());

    private static final int COST = 5;

    private final RelicSpec spec;

    Relics(String displayName, RelicKind kind, RelicEffect effect) {
        this.spec = new RelicSpec(displayName, kind, COST, effect);
    }

    public RelicSpec spec() { return spec; }

    /** A fresh card for this relic at its spec's price. */
    public RelicCard make() { return new RelicCard(spec); }

    /** A uniformly random relic (relics carry no appearance weights). */
    public static Relics random(RandomGenerator stream) {
        Relics[] all = values();
        return all[stream.nextInt(all.length)];
    }

    /** The relic backing {@code spec}, or {@code null} if none — convenience for tests and tooling. */
    public static Relics of(RelicSpec spec) {
        for (Relics r : values()) if (r.spec == spec) return r;
        return null;
    }
}
