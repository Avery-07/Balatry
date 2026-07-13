package model.game.sins;

import model.cards.Card;
import model.cards.vouchers.Voucher;
import model.game.player.Run;

/** Envy: the round-end joker/consumable swap lives at the Match level ({@code Match#swapJokers} and friends);
 * this modifier feeds the other half — every card and pack purchase this shop phase is logged openly on
 * {@link SinTableState}, and any other player can copy one at twice the price paid via
 * {@code Match#envyCopyPurchase}. Vouchers are redemptions, not items, and are not copyable; copies themselves
 * never enter the log, so there are no 4x chains. */
public final class EnvyModifier implements SinModifier {

    @Override
    public void onPurchase(Run buyer, Card item, int pricePaid) {
        if (item instanceof Voucher) return;
        if (buyer.getMatch() == null || buyer.getPlayerId() == null) return;
        buyer.getMatch().getSinTableState().recordEnvyPurchase(buyer.getPlayerId(), item, pricePaid);
    }

    @Override
    public void onRoundBegin(Run run) {
        if (run.getMatch() != null) run.getMatch().getSinTableState().clearEnvyLog();
    }
}
