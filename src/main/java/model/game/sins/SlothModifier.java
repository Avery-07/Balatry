package model.game.sins;

/**
 * Sloth: skipping a blind rewards a second tag ({@link #tagsPerSkip}). Sloth's other half — a spectral card for
 * leaving a shop without buying — couples to shop lifecycle hooks and is deferred to the shop-modifier pass,
 * alongside Greed's and Lust's shop behaviour and the NEXT_SHOP tag family.
 */
final class SlothModifier implements SinModifier {

    @Override
    public int tagsPerSkip() { return 2; }
}
