package model.game.sins;

import model.cards.Card;
import model.cards.DeckCard;
import model.cards.consumables.ConsumableCard;
import model.cards.jokers.JokerCard;
import model.cards.packs.BoosterPack;
import model.cards.relics.RelicCard;
import model.cards.vouchers.Voucher;
import model.game.Match;
import model.game.player.PlayerId;
import model.game.player.Run;
import model.game.shop.GreedShopPool;
import model.game.shop.Shop;
import model.game.shop.ShopSetup;
import model.modifiers.Sticker;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Greed. Two mechanics:
 *
 * <p><b>The chips-to-money ladder</b> — $1 for every chip threshold crossed, each next dollar needing x1.5 more
 * chips (500, then +750, +1125, +1687, ..., floored to whole chips). Per player and <em>per round</em>: the
 * ladder lives in {@link SinState} and resets with it every round, so each blind starts a fresh count from 0.
 * Dollars pay immediately as a hand's score crosses rungs, via {@link #onHandScored}.
 *
 * <p><b>The shared shop</b> — seats keep their seed-mirrored shops (that is what makes the shop "shared"), and
 * a completed purchase by any player debuffs that item in every <em>other</em> seat's open shop. Matching is by
 * item identity ({@link #identityOf}: joker/consumable/relic/voucher spec, pack kind+size, playing-card
 * rank+suit), not slot position, so it survives reroll divergence; claims are remembered on the match's
 * {@link SinTableState} for the shop phase, and {@link #onShopRerolled} re-debuffs claimed items that reappear
 * in a non-buyer's reroll. The DEBUFFED sticker is permanent — buy a claimed item and it stays dead until
 * something like Exorcism removes the sticker. Rarity: the card row swaps to {@link GreedShopPool}.
 */
public final class GreedModifier implements SinModifier {

    /** Chips required for the first dollar of each round's ladder. */
    public static final BigDecimal BASE_REQUIREMENT = BigDecimal.valueOf(500);
    /** Growth of the requirement after each dollar earned. */
    static final BigDecimal ESCALATION = new BigDecimal("1.5");

    @Override
    public void onRoundBegin(Run run) {
        // Claims live for one shop phase; the next round's begin clears them (idempotent across seats).
        if (run.getMatch() != null) run.getMatch().getSinTableState().clearGreedClaims();
    }

    @Override
    public void configureShop(Run run, ShopSetup setup) {
        setup.setCardPool(GreedShopPool.INSTANCE);
    }

    @Override
    public void onHandScored(Run run, BigDecimal handScore) {
        SinState s = run.getSinState();
        s.addGreedChips(handScore);
        while (s.getGreedChips().compareTo(s.getGreedThreshold()) >= 0) {
            run.addMoney(1);
            s.escalateGreed();
        }
    }

    @Override
    public void onPurchase(Run buyer, Card item) {
        Match match = buyer.getMatch();
        if (match == null || buyer.getPlayerId() == null) return;
        String key = identityOf(item);
        if (key == null) return;
        match.getSinTableState().recordGreedClaim(key, buyer.getPlayerId());
        for (PlayerId id : match.getSeats()) {
            if (id.equals(buyer.getPlayerId())) continue;
            Shop shop = match.getRun(id).getShop();
            if (shop != null) debuffMatching(shop, key);
        }
    }

    @Override
    public void onShopRerolled(Run run, Shop shop) {
        Match match = run.getMatch();
        if (match == null || run.getPlayerId() == null) return;
        for (Map.Entry<String, PlayerId> claim : match.getSinTableState().getGreedClaims().entrySet())
            if (!claim.getValue().equals(run.getPlayerId()))   // the buyer's own reappearances stay clean
                debuffMatching(shop, claim.getKey());
    }

    /** Applies the permanent DEBUFFED sticker to every item in {@code shop} matching {@code key}. */
    private static void debuffMatching(Shop shop, String key) {
        for (Card c : shop.getSlots())            mark(c, key);
        for (BoosterPack p : shop.getPacks())     mark(p, key);
        for (Voucher v : shop.getVouchers())      mark(v, key);
    }

    private static void mark(Card card, String key) {
        if (card != null && key.equals(identityOf(card)) && !card.isDebuffed()) card.apply(Sticker.DEBUFFED);
    }

    /**
     * A cross-seat identity key: mirrored copies of "the same item" share a key regardless of which seat's shop
     * they sit in. Unknown card kinds get {@code null} and are simply not propagated.
     */
    static String identityOf(Card card) {
        if (card instanceof JokerCard j)      return "joker:" + j.getSpec().getName();
        if (card instanceof ConsumableCard c) return "consumable:" + c.getSpec().getName();
        if (card instanceof RelicCard r)      return "relic:" + r.getSpec().getName();
        if (card instanceof BoosterPack p)    return "pack:" + p.kind() + ":" + p.size();
        if (card instanceof Voucher v)        return "voucher:" + v.getSpec().getName();
        if (card instanceof DeckCard d)       return "deck:" + d.getRank() + ":" + d.getSuit();
        return null;
    }
}
