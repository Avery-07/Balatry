package model.game.player;

import model.cards.packs.BoosterPack;
import model.cards.packs.PackKind;
import model.cards.packs.PackSize;

import java.util.random.RandomGenerator;

/**
 * Rolls booster packs whose content catalogs exist (Arcana, Celestial, Standard, Buffoon); Spectral and Myth
 * are withheld until their catalogs land. Size is weighted Normal &gt; Jumbo &gt; Mega.
 */
public final class CatalogPackPool implements PackPool {

    public static final CatalogPackPool INSTANCE = new CatalogPackPool();

    private static final PackKind[] KINDS = { PackKind.BUFFOON, PackKind.ARCANA, PackKind.CELESTIAL, PackKind.STANDARD, PackKind.SPECTRAL };

    private CatalogPackPool() { }

    @Override
    public BoosterPack roll(RandomGenerator stream) {
        PackKind kind = KINDS[stream.nextInt(KINDS.length)];
        int s = stream.nextInt(100);
        PackSize size = s < 60 ? PackSize.NORMAL : s < 90 ? PackSize.JUMBO : PackSize.MEGA;
        return new BoosterPack(kind, size);
    }
}
