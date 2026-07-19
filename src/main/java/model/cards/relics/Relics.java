package model.cards.relics;

import model.cards.consumables.ConsumableCard;
import model.cards.consumables.ConsumableSpec;
import model.game.player.Run;

import java.util.List;
import java.util.random.RandomGenerator;

/** The ten Relics — Balatry's multiplayer-facing cards. */
public enum Relics {

    /** Debuffs a rank for every seat above the caster, for their next round. */
    ANATHEMA("Anathema", "Debuffs a chosen rank for every seat above you, for their next round.",
            RelicSelector.RANK, RelicKind.RIVALS, ctx ->
            ctx.target().getAfflictions().armRankDebuff(ctx.selection().rank())),

    /** Debuffs a suit for every seat above the caster, for their next round. */
    MIASMA("Miasma", "Debuffs a chosen suit for every seat above you, for their next round.",
            RelicSelector.SUIT, RelicKind.RIVALS, ctx ->
            ctx.target().getAfflictions().armSuitDebuff(ctx.selection().suit())),

    /** Debuffs a chosen board position for every seat above the caster, for one round (the jokers hit are not shown to the caster). */
    KATADESMOS("Katadesmos", "Debuffs a chosen board slot for every seat above you, for one round.",
            RelicSelector.JOKER_SLOT, RelicKind.RIVALS, ctx ->
            ctx.target().getAfflictions().armJokerDebuff(ctx.selection().jokerIndex())),

    /** Destroys a random consumable from every seat above the caster in the standings. */
    PYRE("Pyre", "Destroys a random consumable held by every player above you in the standings.",
            RelicSelector.NONE, RelicKind.RIVALS, ctx -> {
        List<ConsumableCard> cs = ctx.target().getConsumables();
        if (!cs.isEmpty()) ctx.target().destroyConsumable(cs.get(ctx.random().nextInt(cs.size())));
    }),

    /** Debuffs the first shop slot of one random seat above the caster, for that visit only. */
    LIMOS("Limos", "Debuffs the first shop slot of a random player above you, next shop only.",
            RelicSelector.NONE, RelicKind.RANDOM_RIVAL, ctx ->
            ctx.target().getAfflictions().armFirstSlotDebuff()),

    /** Levels down a chosen hand type for every seat above the caster. */
    KATABASIS("Katabasis", "Levels down a chosen hand type for every seat above you.",
            RelicSelector.HAND_TYPE, RelicKind.RIVALS, ctx -> {
        if (ctx.selection().handType() != null)
            ctx.target().getHandLevels().levelDown(ctx.selection().handType());
    }),

    /** Rerolls the shared boss blind of the next ante (a table-level change for every seat). */
    METABOLE("Metabole", "Rerolls the shared boss blind of the next ante.",
            RelicSelector.NONE, RelicKind.GLOBAL, ctx -> ctx.match().rerollNextBoss()),

    /** Creates a copy of the last consumable used by any player. */
    MIMESIS("Mimesis", "Creates a copy of the last consumable used by any player.",
            RelicSelector.NONE, RelicKind.SELF, ctx -> {
        ConsumableSpec last = ctx.match().getLastConsumableUsed();
        if (last != null) ctx.source().createConsumable(last);
    }),

    /** Steals 20% (rounded down) of the money of one random seat above the caster. */
    HARPAX("Harpax", "Steals 20% (rounded down) of the money of a random player above you.",
            RelicSelector.NONE, RelicKind.RANDOM_RIVAL, ctx -> {
        Run target = ctx.target();
        int amount = Math.max(0, target.getMoney()) / 5;
        if (amount > 0) {
            target.addMoney(-amount);
            ctx.source().addMoney(amount);
        }
    }),

    /** Negates the next hostile effect aimed at the caster this ante. */
    AEGIS("Aegis", "Negates the next hostile effect aimed at you this ante.",
            RelicSelector.NONE, RelicKind.SELF, ctx -> ctx.source().getAfflictions().armAegis());

    private static final int COST = 5;

    private final RelicSpec spec;

    Relics(String displayName, String description, RelicSelector selector, RelicKind kind, RelicEffect effect) {
        this.spec = new RelicSpec(displayName, kind, selector, COST, description, effect);
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
