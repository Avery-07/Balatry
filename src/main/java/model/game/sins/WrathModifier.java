package model.game.sins;

import model.items.packs.BoosterPack;
import model.items.packs.PackKind;
import model.items.packs.PackSize;
import model.game.player.Run;

/** Wrath: every round begins with a free Mega Myth Pack per player (the same seeded contents for every seat — open it via {@link Run#openPendingPack}, picks are relics cast immediately via {@code Match#useRelicCard}); an unopened pack dies with its round. */
final class WrathModifier implements SinModifier {

    @Override
    public void onRoundBegin(Run run) {
        run.grantPack(new BoosterPack(PackKind.MYTH, PackSize.MEGA));
    }
}
