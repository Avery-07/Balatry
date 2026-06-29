package model.game.scoring;

import model.cards.DeckCard;
import model.cards.DeckCard.Rank;
import model.cards.DeckCard.Suit;
import model.cards.consumables.Planets;
import model.cards.jokers.JokerCard;
import model.cards.jokers.JokerEffect;
import model.cards.jokers.JokerSpec;
import model.cards.jokers.Rarity;
import model.game.player.Round;
import model.game.player.Run;

import java.util.List;

/**
 * Run-as-main harness pinning the five previously-undispatched triggers: ON_SOLD, ON_SHOP_START,
 * ON_SHOP_REROLL, ON_SHOP_END, ON_HAND_DISCARDED. Each uses a probe joker whose counter advances on its
 * trigger; ON_HAND_DISCARDED also proves the discarded-card context channel is readable from the effect.
 */
public final class TriggerTests {

    private static int failures = 0;

    public static void main(String[] args) {
        // ON_SOLD fires on the joker being sold (so it can react to its own sale, e.g. Luchador), not on bystanders
        Run soldJoker = new Run(0L);
        JokerCard bystander = probe(Trigger.ON_SOLD, +1);   // index 0: a different joker, must NOT react
        soldJoker.getJokers().add(bystander);
        JokerCard probeA = probe(Trigger.ON_SOLD, +1);      // index 1: the one we sell
        soldJoker.getJokers().add(probeA);
        soldJoker.sellJoker(1);
        checkInt("ON_SOLD fires on the sold joker", probeA.getCounter(), 1);
        checkInt("ON_SOLD does not fire on bystanders", bystander.getCounter(), 0);

        // selling a consumable does not fire ON_SOLD on jokers under the own-sale semantics
        Run soldCons = new Run(0L);
        JokerCard probeB = probe(Trigger.ON_SOLD, +1);
        soldCons.getJokers().add(probeB);
        soldCons.getConsumables().add(Planets.MERCURY.make());
        soldCons.sellConsumable(0);
        checkInt("consumable sale does not fire ON_SOLD on jokers", probeB.getCounter(), 0);

        // ON_SHOP_START fires on open, not before
        Run shopStart = new Run(0L);
        JokerCard probeC = probe(Trigger.ON_SHOP_START, +1);
        shopStart.getJokers().add(probeC);
        checkInt("no ON_SHOP_START before open", probeC.getCounter(), 0);
        shopStart.openShop();
        checkInt("ON_SHOP_START fires on open", probeC.getCounter(), 1);

        // ON_SHOP_END fires on close only
        Run shopEnd = new Run(0L);
        JokerCard probeD = probe(Trigger.ON_SHOP_END, +1);
        shopEnd.getJokers().add(probeD);
        shopEnd.openShop();
        checkInt("ON_SHOP_END not fired by open", probeD.getCounter(), 0);
        shopEnd.closeShop();
        checkInt("ON_SHOP_END fires on close", probeD.getCounter(), 1);

        // ON_SHOP_REROLL fires per reroll
        Run reroll = new Run(0L);
        JokerCard probeE = probe(Trigger.ON_SHOP_REROLL, +1);
        reroll.getJokers().add(probeE);
        reroll.addMoney(100);
        reroll.openShop();
        checkInt("no ON_SHOP_REROLL before reroll", probeE.getCounter(), 0);
        reroll.getShop().reroll();
        reroll.getShop().reroll();
        checkInt("ON_SHOP_REROLL fires once per reroll", probeE.getCounter(), 2);

        // ON_HAND_DISCARDED fires on discard AND the discarded cards are readable from the effect
        Run discard = new Run(0L);
        // probe banks the size of the discard it sees, proving the context channel
        JokerCard probeF = new JokerCard(
                JokerSpec.named("DiscardProbe", Rarity.COMMON)
                        .on(Trigger.ON_HAND_DISCARDED,
                                (run, self) -> self.setCounter(self.getCounter() + run.getLastDiscarded().size()))
                        .build(),
                0);
        discard.getJokers().add(probeF);
        for (int i = 0; i < 8; i++) discard.getDeck().add(new DeckCard(Rank.values()[i], Suit.SPADES));
        Round round = discard.beginRound(300);
        List<DeckCard> toss = round.getHand().subList(0, 2);
        round.discard(toss);
        checkInt("ON_HAND_DISCARDED fires with discarded-card context", probeF.getCounter(), 2);
        checkInt("discard context cleared after the broadcast", discard.getLastDiscarded().size(), 0);

        System.out.println(failures == 0 ? "\nALL PASS" : "\n" + failures + " FAILURE(S)");
        if (failures != 0) System.exit(1);
    }

    private static JokerCard probe(Trigger trigger, int delta) {
        JokerEffect bump = (run, self) -> self.setCounter(self.getCounter() + delta);
        return new JokerCard(JokerSpec.named("Probe", Rarity.COMMON).on(trigger, bump).build(), 0);
    }

    private static JokerCard plainJoker() {
        return new JokerCard(JokerSpec.named("Plain", Rarity.COMMON).build(), 0);
    }

    private static void checkInt(String label, int actual, int expected) {
        boolean ok = actual == expected;
        System.out.printf("%-52s %s%n", label, ok ? "PASS" : "FAIL (got " + actual + ", want " + expected + ")");
        if (!ok) failures++;
    }
}
