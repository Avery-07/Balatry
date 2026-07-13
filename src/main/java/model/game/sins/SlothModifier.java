package model.game.sins;

import model.cards.consumables.Spectrals;
import model.game.player.Run;
import model.game.rng.RngSource;
import model.game.shop.Shop;

/** Sloth: skipping a blind rewards a second tag ({@link #tagsPerSkip}), and a shop visit with zero purchases
 * (rerolls don't count) offers a random spectral at close — silently lost if the consumable area is full. */
final class SlothModifier implements SinModifier {

    @Override
    public int tagsPerSkip() { return 2; }

    @Override
    public void onShopClosed(Run run, Shop shop) {
        if (shop.totalPurchases() > 0) return;
        var stream = run.getRng().streamFor(RngSource.SPECTRAL_GENERATION, run.nextSalt(RngSource.SPECTRAL_GENERATION));
        run.createConsumable(Spectrals.random(stream).spec());
    }
}
