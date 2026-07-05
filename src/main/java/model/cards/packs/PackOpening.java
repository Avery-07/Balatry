package model.cards.packs;

import model.cards.Card;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An opened booster pack: the offered options and the remaining pick budget. Picking removes the card from the
 * offer (indices stay stable; picked slots become null) and hands it to the caller, who routes it — jokers to
 * the board, consumables to the area, and Myth-pack relics to an immediate cast via
 * {@code Match#useRelicCard} ("use immediately"). Unpicked options are simply abandoned.
 */
public final class PackOpening {

    private final BoosterPack pack;
    private final List<Card> options;
    private int picksLeft;

    PackOpening(BoosterPack pack, List<Card> options, int picks) {
        this.pack = pack;
        this.options = new ArrayList<>(options);
        this.picksLeft = picks;
    }

    public BoosterPack getPack()   { return pack; }
    public int getPicksLeft()      { return picksLeft; }

    /** The current offer; picked slots are null. Unmodifiable. */
    public List<Card> getOptions() { return Collections.unmodifiableList(options); }

    /** Takes the option at {@code index}, spending one pick. */
    public Card pick(int index) {
        if (picksLeft <= 0) throw new IllegalStateException("no picks remaining");
        Card card = options.get(index);
        if (card == null) throw new IllegalStateException("option " + index + " was already picked");
        options.set(index, null);
        picksLeft--;
        return card;
    }
}
