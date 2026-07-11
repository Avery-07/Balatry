package model.game.sins;

/** Sloth: skipping a blind rewards a second tag ({@link #tagsPerSkip}). */
final class SlothModifier implements SinModifier {

    @Override
    public int tagsPerSkip() { return 2; }
}
