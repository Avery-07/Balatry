package model.cards.jokers;

import model.cards.DeckCard;
import model.cards.DeckCard.Rank;
import model.cards.consumables.ConsumableCard;
import model.cards.consumables.ConsumableType;
import model.cards.consumables.Spectrals;
import model.cards.consumables.Tarots;
import model.game.Blind;
import model.game.rng.RngSource;
import model.game.scoring.HandEvaluator;
import model.game.scoring.HandType;
import model.game.scoring.ScoringSession;
import model.game.scoring.Trigger;
import model.modifiers.Edition;
import model.modifiers.Enhancement;
import model.modifiers.Seal;
import model.modifiers.Sticker;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.random.RandomGenerator;

/**
 * The joker catalog, in document order with a comment before every group of five. Each entry diverges to its
 * "New Effect" where the design doc defines one. Effects read only the existing run/scoring/stats surface;
 * jokers needing systems not yet modelled (boss blinds, static hand-size/discard modifiers, evaluator-threshold
 * changes, pack-skip/open events, the tag system, per-card bonus-chip storage) are left as inline {@code skipped:}
 * comments at their position. A few use small counter encodings (a hand-id marker, or bit-packed state) to stay
 * self-contained without new engine hooks.
 */
public enum Jokers {

    // Jokers 001-005
    JOKER("Joker", Rarity.COMMON, 2, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> run.getScoring().addMult(4))),
    GREEDY_JOKER("Greedy Joker", Rarity.COMMON, 5, b -> b.on(Trigger.ON_SCORED_CARD,
            (run, self) -> { if (isSuit(run.getScoring(), Suit.DIAMOND)) run.getScoring().addMult(3); })),
    LUSTY_JOKER("Lusty Joker", Rarity.COMMON, 5, b -> b.on(Trigger.ON_SCORED_CARD,
            (run, self) -> { if (isSuit(run.getScoring(), Suit.HEART)) run.getScoring().addMult(3); })),
    WRATHFUL_JOKER("Wrathful Joker", Rarity.COMMON, 5, b -> b.on(Trigger.ON_SCORED_CARD,
            (run, self) -> { if (isSuit(run.getScoring(), Suit.SPADE)) run.getScoring().addMult(3); })),
    GLUTTONOUS_JOKER("Gluttonous Joker", Rarity.COMMON, 5, b -> b.on(Trigger.ON_SCORED_CARD,
            (run, self) -> { if (isSuit(run.getScoring(), Suit.CLUB)) run.getScoring().addMult(3); })),

    // Jokers 006-010
    JOLLY_JOKER("Jolly Joker", Rarity.COMMON, 3, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> { if (run.getScoring().getHand().hasPair()) run.getScoring().addMult(8); })),
    ZANY_JOKER("Zany Joker", Rarity.COMMON, 4, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> { if (run.getScoring().getHand().hasThreeOfAKind()) run.getScoring().addMult(12); })),
    MAD_JOKER("Mad Joker", Rarity.COMMON, 4, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> { if (run.getScoring().getHand().hasTwoPair()) run.getScoring().addMult(10); })),
    CRAZY_JOKER("Crazy Joker", Rarity.COMMON, 4, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> { if (run.getScoring().getHand().hasStraight()) run.getScoring().addMult(12); })),
    DROLL_JOKER("Droll Joker", Rarity.COMMON, 4, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> { if (run.getScoring().getHand().hasFlush()) run.getScoring().addMult(10); })),

    // Jokers 011-015
    SLY_JOKER("Sly Joker", Rarity.COMMON, 3, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> { if (run.getScoring().getHand().hasPair()) run.getScoring().addChips(50); })),
    WILY_JOKER("Wily Joker", Rarity.COMMON, 4, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> { if (run.getScoring().getHand().hasThreeOfAKind()) run.getScoring().addChips(100); })),
    CLEVER_JOKER("Clever Joker", Rarity.COMMON, 4, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> { if (run.getScoring().getHand().hasTwoPair()) run.getScoring().addChips(80); })),
    DEVIOUS_JOKER("Devious Joker", Rarity.COMMON, 4, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> { if (run.getScoring().getHand().hasStraight()) run.getScoring().addChips(100); })),
    CRAFTY_JOKER("Crafty Joker", Rarity.COMMON, 4, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> { if (run.getScoring().getHand().hasFlush()) run.getScoring().addChips(80); })),

    // Jokers 016-020  (skipped : 018 Four Fingers)
    HALF_JOKER("Half Joker", Rarity.COMMON, 5, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> { if (run.getScoring().getHand().playedCount() <= 3) run.getScoring().addMult(20); })),
    JOKER_STENCIL("Joker Stencil", Rarity.UNCOMMON, 8, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> {
                int jokers = 0;
                for (JokerCard j : run.getJokers()) if (j.getSpec() != self.getSpec()) jokers++;
                int empty = run.getJokerSlots() - jokers;
                if (empty > 0) run.getScoring().multiplyMult(BigDecimal.valueOf(empty));
            })),
    MIME("Mime", Rarity.UNCOMMON, 5, b -> b.retriggerHeld((run, self, card) -> 1)),
    CREDIT_CARD("Credit Card", Rarity.COMMON, 2, b -> b
            .debtAllowance(20)
            .on(Trigger.ON_SPEND, (run, self) -> self.setCounter(self.getCounter() + run.getLastInDebtSpend()))
            .on(Trigger.ON_EARN, (run, self) -> { if (run.getMoney() >= 0) self.setCounter(0); })
            .on(Trigger.ON_HAND_PLAYED, (run, self) -> run.getScoring().addMult(self.getCounter()))),

    // Jokers 021-025
    CEREMONIAL_DAGGER("Ceremonial Dagger", Rarity.UNCOMMON, 6, b -> b
            .on(Trigger.ON_ROUND_START, (run, self) -> {
                List<JokerCard> jokers = run.getJokers();
                int i = jokers.indexOf(self);
                if (i >= 0 && i + 1 < jokers.size()) {
                    JokerCard right = jokers.get(i + 1);
                    if (run.destroyJoker(right))   // Eternal enforcement lives in Board.destroy
                        self.setCounter(self.getCounter() + 3 * right.getSellValue());
                }
            })
            .on(Trigger.ON_HAND_PLAYED, (run, self) -> run.getScoring().addMult(self.getCounter()))),
    BANNER("Banner", Rarity.COMMON, 5, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> { if (run.getRound() != null) run.getScoring().addChips(30L * run.getRound().getDiscardsRemaining()); })),
    MYSTIC_SUMMIT("Mystic Summit", Rarity.COMMON, 5, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> {
                if (run.getRound() != null && run.getRound().getDiscardsRemaining() == 0)
                    run.getScoring().addMult(15);
            })),
    MARBLE_JOKER("Marble Joker", Rarity.UNCOMMON, 6, b -> b.on(Trigger.ON_ROUND_START,
            (run, self) -> {
                DeckCard stone = new DeckCard(Rank.ACE, DeckCard.Suit.SPADES);
                stone.apply(Enhancement.STONE);
                long salt = run.getDeck().size();
                if (run.getRng().chance(RngSource.MISC, salt, 1, 2)) {
                    Seal[] seals = Seal.values();
                    stone.apply(seals[run.getRng().nextInt(RngSource.CARD_SEAL, salt, seals.length)]);
                }
                run.addCardToDeck(stone);
            })),
    LOYALTY_CARD("Loyalty Card", Rarity.UNCOMMON, 5, b -> b
            // counter = completed purchases since acquisition; the grant is a pure read at pricing time,
            // so a failed buy neither advances the count nor burns the free purchase.
            .on(Trigger.ON_PURCHASE_PRICING,
                    (run, self) -> { if ((self.getCounter() + 1) % 4 == 0) run.makePurchaseFree(); })
            .on(Trigger.ON_BOUGHT,
                    (run, self) -> self.setCounter(self.getCounter() + 1))),

    // Jokers 026-030  (skipped : 030 Chaos the Clown)
    EIGHT_BALL("8 Ball", Rarity.COMMON, 5, b -> b.on(Trigger.ON_SCORED_CARD,
            (run, self) -> {
                DeckCard c = scored(run);
                if (c != null && c.getRank() == Rank.EIGHT && run.roll(RngSource.TAROT_GENERATION, 1, 4))
                    createEditioned(run, new ConsumableCard(Tarots.random(gen(run, RngSource.TAROT_GENERATION)).spec()));
            })),
    MISPRINT("Misprint", Rarity.COMMON, 5, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> {
                RandomGenerator g = gen(run, RngSource.MISC);
                run.getScoring().addMult(g.nextInt(16));                                            // new: +0 to +15 Mult
                run.getScoring().multiplyMult(BigDecimal.valueOf(0.75 + g.nextDouble() * 1.25));    // and X0.75 to X2
            })),
    DUSK("Dusk", Rarity.UNCOMMON, 5, b -> b.retriggerPlayed(
            (run, self, card) -> (run.getRound() != null && run.getRound().getHandsRemaining() == 1) ? 1 : 0)),
    RAISED_FIST("Raised Fist", Rarity.COMMON, 5, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> {
                DeckCard low = null;
                for (DeckCard c : run.getHeld()) if (low == null || c.getRank().getChips() < low.getRank().getChips()) low = c;
                if (low != null) run.getScoring().addMult(2L * low.getRank().getChips());
            })),

    // Jokers 031-035
    FIBONACCI("Fibonacci", Rarity.UNCOMMON, 8, b -> b.on(Trigger.ON_SCORED_CARD,
            (run, self) -> { DeckCard c = scored(run); if (c != null && isFibonacci(c.getRank())) run.getScoring().addMult(8); })),
    STEEL_JOKER("Steel Joker", Rarity.UNCOMMON, 7, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> run.getScoring().multiplyMult(onePlus("0.2", deckCount(run, Enhancement.STEEL))))),
    SCARY_FACE("Scary Face", Rarity.COMMON, 4, b -> b.on(Trigger.ON_SCORED_CARD,
            (run, self) -> { DeckCard c = scored(run); if (c != null && c.isFace()) run.getScoring().addChips(30); })),
    ABSTRACT_JOKER("Abstract Joker", Rarity.COMMON, 4, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> run.getScoring().addMult(3L * run.getJokers().size()))),
    DELAYED_GRATIFICATION("Delayed Gratification", Rarity.COMMON, 4, b -> b.on(Trigger.ON_ROUND_END,
            (run, self) -> { if (run.getRound() != null) run.addMoney(2 * run.getRound().getDiscardsRemaining()); })),   // new: $2 per unused discard

    // Jokers 036-040  (skipped : 037 Pareidolia)
    HACK("Hack", Rarity.UNCOMMON, 6, b -> b.retriggerPlayed(
            (run, self, card) -> switch (card.getRank()) { case TWO, THREE, FOUR, FIVE -> 1; default -> 0; })),
    GROS_MICHEL("Gros Michel", Rarity.COMMON, 5, b -> b
            .on(Trigger.ON_HAND_PLAYED, (run, self) -> run.getScoring().addMult(15))
            .on(Trigger.ON_ROUND_END, (run, self) -> { if (run.roll(RngSource.MISC, 1, 6)) self.setCounter(1); })   // doomed (1 in 6)
            .on(Trigger.ON_ROUND_START, (run, self) -> { if (self.getCounter() == 1) run.destroyJoker(self); })),   // destroyed at next blind select (safe; ON_ROUND_END iterates the live board)
    EVEN_STEVEN("Even Steven", Rarity.COMMON, 4, b -> b.on(Trigger.ON_SCORED_CARD,
            (run, self) -> { DeckCard c = scored(run); if (c != null && isEven(c.getRank())) run.getScoring().addMult(4); })),
    ODD_TODD("Odd Todd", Rarity.COMMON, 4, b -> b.on(Trigger.ON_SCORED_CARD,
            (run, self) -> { DeckCard c = scored(run); if (c != null && isOdd(c.getRank())) run.getScoring().addChips(31); })),

    // Jokers 041-045
    SCHOLAR("Scholar", Rarity.COMMON, 4, b -> b.on(Trigger.ON_SCORED_CARD,
            (run, self) -> { DeckCard c = scored(run); if (c != null && c.getRank() == Rank.ACE) { run.getScoring().addChips(20); run.getScoring().addMult(4); } })),
    BUSINESS_CARD("Business Card", Rarity.COMMON, 4, b -> b.on(Trigger.ON_SCORED_CARD,
            (run, self) -> { DeckCard c = scored(run); if (c != null && c.isFace() && run.roll(RngSource.MISC, 1, 2)) run.addMoney(2); })),
    SUPERNOVA("Supernova", Rarity.COMMON, 5, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> run.getScoring().addMult(run.getStats().getHandPlays(run.getScoring().getHand().type())))),
    RIDE_THE_BUS("Ride the Bus", Rarity.COMMON, 6, b -> b
            .on(Trigger.ON_SCORED_CARD, (run, self) -> { DeckCard c = scored(run); if (c != null && c.isFace()) self.setCounter(self.getCounter() | 1); })   // low bit = a scoring face appeared this hand
            .on(Trigger.ON_HAND_PLAYED, (run, self) -> {
                int streak = self.getCounter() >> 1;
                streak = (self.getCounter() & 1) == 1 ? 0 : streak + 1;
                run.getScoring().addMult(streak);
                self.setCounter(streak << 1);
            })),
    SPACE_JOKER("Space Joker", Rarity.UNCOMMON, 5, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> { if (run.roll(RngSource.MISC, 1, 4)) run.levelUpHand(run.getScoring().getHand().type()); })),

    // Jokers 046-050  (skipped : 047 Burglar)
    EGG("Egg", Rarity.COMMON, 4, b -> b.on(Trigger.ON_ROUND_END,
            (run, self) -> self.setSellValue(self.getSellValue() + 3))),
    BLACKBOARD("Blackboard", Rarity.UNCOMMON, 6, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> {
                boolean all = true;
                for (DeckCard c : run.getHeld()) if (!(c.isSpade() || c.isClub())) { all = false; break; }
                if (all) run.getScoring().multiplyMult(x("3"));
            })),
    RUNNER("Runner", Rarity.COMMON, 5, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> {
                if (run.getScoring().getHand().hasStraight()) self.addCounter(15);
                run.getScoring().addChips(self.getCounter());
            })),
    ICE_CREAM("Ice Cream", Rarity.COMMON, 5, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> {
                run.getScoring().addChips(Math.max(0, 100 - 5 * self.getCounter()));
                self.addCounter(1);
            })),

    // Jokers 051-055  (skipped : 052 Splash)
    DNA("DNA", Rarity.RARE, 8, b -> b.on(Trigger.ON_SCORED_CARD,
            (run, self) -> {
                DeckCard c = scored(run);
                int hand = run.getStats().getTotalHandsPlayed();
                if (c != null && self.getCounter() != hand
                        && run.getStats().getHandsPlayedThisRound() == 1
                        && run.getScoring().getHand().playedCount() == 1) {
                    self.setCounter(hand);
                    run.addCardToHand(copyOf(c));
                }
            })),
    BLUE_JOKER("Blue Joker", Rarity.COMMON, 5, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> run.getScoring().addChips(2L * run.getDeck().size()))),
    SIXTH_SENSE("Sixth Sense", Rarity.UNCOMMON, 6, b -> b.on(Trigger.ON_SCORED_CARD,
            (run, self) -> {
                DeckCard c = scored(run);
                int hand = run.getStats().getTotalHandsPlayed();
                if (c != null && self.getCounter() != hand
                        && run.getStats().getHandsPlayedThisRound() == 1
                        && run.getScoring().getHand().playedCount() == 1
                        && c.getRank() == Rank.SIX) {
                    self.setCounter(hand);
                    run.destroyDeckCards(List.of(c));
                    run.createConsumable(Spectrals.random(gen(run, RngSource.SPECTRAL_GENERATION)).spec());
                }
            })),
    CONSTELLATION("Constellation", Rarity.UNCOMMON, 6, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> run.getScoring().multiplyMult(onePlus("0.1", run.getStats().getConsumablesUsed(ConsumableType.PLANET))))),

    // Jokers 056-060  (skipped : 056 Hiker)
    FACELESS_JOKER("Faceless Joker", Rarity.COMMON, 4, b -> b.on(Trigger.ON_HAND_DISCARDED,
            (run, self) -> {
                int faces = 0;
                for (DeckCard c : run.getLastDiscarded()) if (c.isFace()) faces++;
                if (faces >= 3) run.addMoney(6);
            })),
    GREEN_JOKER("Green Joker", Rarity.COMMON, 4, b -> b
            .on(Trigger.ON_HAND_PLAYED, (run, self) -> { self.addCounter(1); run.getScoring().addMult(self.getCounter()); })
            .on(Trigger.ON_HAND_DISCARDED, (run, self) -> self.setCounter(Math.max(0, self.getCounter() - 1)))),
    SUPERPOSITION("Superposition", Rarity.COMMON, 6, b -> b.on(Trigger.ON_SCORED_CARD,
            (run, self) -> {
                DeckCard c = scored(run);
                int hand = run.getStats().getTotalHandsPlayed();
                if (c != null && c.getRank() == Rank.ACE && run.getScoring().getHand().hasStraight() && self.getCounter() != hand) {
                    self.setCounter(hand);
                    run.createConsumable(Tarots.random(gen(run, RngSource.TAROT_GENERATION)).spec());
                }
            })),
    TO_DO_LIST("To Do List", Rarity.COMMON, 4, b -> b
            .on(Trigger.ON_ROUND_START, (run, self) -> self.setCounter(gen(run, RngSource.MISC).nextInt(HandType.values().length)))
            .on(Trigger.ON_HAND_PLAYED, (run, self) -> { if (run.getScoring().getHand().type().ordinal() == self.getCounter()) run.addMoney(4); })),

    // Jokers 061-065  (skipped : 063 Red Joker)
    CAVENDISH("Cavendish", Rarity.COMMON, 4, b -> b
            .on(Trigger.ON_HAND_PLAYED, (run, self) -> run.getScoring().multiplyMult(x("3")))
            .on(Trigger.ON_ROUND_END, (run, self) -> { if (run.roll(RngSource.MISC, 1, 1000)) self.setCounter(1); })
            .on(Trigger.ON_ROUND_START, (run, self) -> { if (self.getCounter() == 1) run.destroyJoker(self); })),
    CARD_SHARP("Card Sharp", Rarity.UNCOMMON, 6, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> { if (run.getStats().getHandPlaysThisRound(run.getScoring().getHand().type()) > 1) run.getScoring().multiplyMult(x("3")); })),
    MADNESS("Madness", Rarity.UNCOMMON, 7, b -> b
            .on(Trigger.ON_ROUND_START, (run, self) -> {
                if (run.getMatch() == null || run.getMatch().getBlind() == Blind.BOSS) return;
                self.addCounter(1);
                List<JokerCard> js = run.getJokers();
                int i = js.indexOf(self);
                List<Integer> adj = new ArrayList<>();
                if (i - 1 >= 0 && !js.get(i - 1).hasSticker(Sticker.ETERNAL)) adj.add(i - 1);
                if (i + 1 < js.size() && !js.get(i + 1).hasSticker(Sticker.ETERNAL)) adj.add(i + 1);
                if (!adj.isEmpty()) run.destroyJoker(js.get(adj.get(gen(run, RngSource.MISC).nextInt(adj.size()))));
            })
            .on(Trigger.ON_HAND_PLAYED, (run, self) -> run.getScoring().multiplyMult(onePlus("0.5", self.getCounter())))),
    SQUARE_JOKER("Square Joker", Rarity.COMMON, 4, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> {
                if (run.getScoring().getHand().playedCount() == 4) self.addCounter(4);
                run.getScoring().addChips(self.getCounter());
            })),

    // Jokers 066-070  (skipped : 069 Shortcut)
    SEANCE("Seance", Rarity.UNCOMMON, 6, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> {
                if (run.getScoring().getHand().type() == HandType.STRAIGHT_FLUSH)
                    createEditioned(run, new ConsumableCard(Spectrals.random(gen(run, RngSource.SPECTRAL_GENERATION)).spec()));
            })),
    RIFF_RAFF("Riff-Raff", Rarity.COMMON, 6, b -> b.on(Trigger.ON_ROUND_START,
            (run, self) -> {
                for (int k = 0; k < 2; k++)
                    run.createJoker(Jokers.randomOfRarity(Rarity.COMMON, gen(run, RngSource.JOKER_GENERATION)).make());
            })),
    VAMPIRE("Vampire", Rarity.UNCOMMON, 7, b -> b
            .on(Trigger.ON_SCORED_CARD, (run, self) -> {
                DeckCard c = scored(run);
                if (c != null && c.getEnhancement() != null) { self.addCounter(1); c.remove(c.getEnhancement()); }
            })
            .on(Trigger.ON_HAND_PLAYED, (run, self) -> run.getScoring().multiplyMult(onePlus("0.1", self.getCounter())))),
    HOLOGRAM("Hologram", Rarity.UNCOMMON, 7, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> run.getScoring().multiplyMult(onePlus("0.25", run.getStats().getCardsAdded())))),

    // Jokers 071-075
    VAGABOND("Vagabond", Rarity.RARE, 8, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> { if (run.getMoney() <= 4) run.createConsumable(Tarots.random(gen(run, RngSource.TAROT_GENERATION)).spec()); })),
    BARON("Baron", Rarity.RARE, 8, b -> b.on(Trigger.ON_HELD_CARD,
            (run, self) -> { DeckCard c = scored(run); if (c != null && c.getRank() == Rank.KING) run.getScoring().multiplyMult(x("1.5")); })),
    CLOUD_9("Cloud 9", Rarity.UNCOMMON, 7, b -> b.on(Trigger.ON_ROUND_END,
            (run, self) -> {
                int nines = 0;
                for (DeckCard c : run.getDeck()) if (c.getRank() == Rank.NINE) nines++;
                run.addMoney(nines);
            })),
    ROCKET("Rocket", Rarity.UNCOMMON, 6, b -> b
            .on(Trigger.ON_ROUND_END, (run, self) -> run.addMoney(1 + 2 * self.getCounter()))
            .on(Trigger.ON_BOSS_DEFEATED, (run, self) -> self.addCounter(1))),
    OBELISK("Obelisk", Rarity.RARE, 8, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> {
                List<HandType> recent = run.getStats().getRecentHands(3);
                HandType cur = recent.get(0);
                boolean repeat = (recent.size() > 1 && recent.get(1) == cur) || (recent.size() > 2 && recent.get(2) == cur);
                if (repeat) self.setCounter(0); else self.addCounter(1);
                run.getScoring().multiplyMult(onePlus("0.2", self.getCounter()));
            })),

    // Jokers 076-080  (skipped : 080 Turtle Bean)
    MIDAS_MASK("Midas Mask", Rarity.UNCOMMON, 7, b -> b.on(Trigger.ON_SCORED_CARD,
            (run, self) -> { DeckCard c = scored(run); if (c != null && c.isFace()) c.apply(Enhancement.GOLD); })),
    LUCHADOR("Luchador", Rarity.UNCOMMON, 5, b -> b.on(Trigger.ON_SOLD,
            (run, self) -> run.disableBossForRound())),
    PHOTOGRAPH("Photograph", Rarity.COMMON, 5, b -> b.on(Trigger.ON_SCORED_CARD,
            (run, self) -> {
                DeckCard c = scored(run);
                int hand = run.getStats().getTotalHandsPlayed();
                if (c != null && c.isFace() && self.getCounter() != hand) { run.getScoring().multiplyMult(x("2")); self.setCounter(hand); }
            })),
    GIFT_CARD("Gift Card", Rarity.UNCOMMON, 6, b -> b.on(Trigger.ON_ROUND_END,
            (run, self) -> {
                for (JokerCard j : run.getJokers()) j.setSellValue(j.getSellValue() + 1);
                for (ConsumableCard cc : run.getConsumables()) cc.setSellValue(cc.getSellValue() + 1);
            })),

    // Jokers 081-085  (skipped : 084 To the Moon, 085 Hallucination)
    EROSION("Erosion", Rarity.UNCOMMON, 6, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> run.getScoring().addMult(4L * Math.max(0, 52 - run.getDeck().size())))),
    RESERVED_PARKING("Reserved Parking", Rarity.COMMON, 6, b -> b.on(Trigger.ON_HELD_CARD,
            (run, self) -> { DeckCard c = scored(run); if (c != null && c.isFace() && run.roll(RngSource.MISC, 1, 2)) run.addMoney(2); })),   // new: $2
    MAIL_IN_REBATE("Mail-In Rebate", Rarity.COMMON, 4, b -> b
            .on(Trigger.ON_ROUND_START, (run, self) -> self.setCounter(gen(run, RngSource.MISC).nextInt(Rank.values().length)))
            .on(Trigger.ON_HAND_DISCARDED, (run, self) -> {
                int n = 0;
                for (DeckCard c : run.getLastDiscarded()) if (c.getRank().ordinal() == self.getCounter()) n++;
                if (n > 0) run.addMoney(4 * n);
            })),

    // Jokers 086-090  (skipped : 087 Juggler, 088 Drunkard)
    FORTUNE_TELLER("Fortune Teller", Rarity.COMMON, 6, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> run.getScoring().addMult(run.getStats().getConsumablesUsed(ConsumableType.TAROT)))),
    STONE_JOKER("Stone Joker", Rarity.UNCOMMON, 6, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> run.getScoring().addChips(35L * deckCount(run, Enhancement.STONE)))),
    YELLOW_JOKER("Yellow Joker", Rarity.COMMON, 6, b -> b.on(Trigger.ON_ROUND_END,
            (run, self) -> run.addMoney(4))),

    // Jokers 091-095  (skipped : 091 Lucky Cat, 094 Diet Cola)
    BASEBALL_CARD("Baseball Card", Rarity.RARE, 8, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> {
                for (JokerCard j : run.getJokers()) if (j.getSpec().getRarity() == Rarity.UNCOMMON) run.getScoring().multiplyMult(x("1.5"));
            })),
    BULL("Bull", Rarity.UNCOMMON, 6, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> run.getScoring().addChips(2L * Math.max(0, run.getMoney())))),
    TRADING_CARD("Trading Card", Rarity.UNCOMMON, 6, b -> b.on(Trigger.ON_HAND_DISCARDED,
            (run, self) -> {
                if (run.getStats().getDiscardsUsedThisRound() == 1 && run.getLastDiscarded().size() == 1) {
                    run.destroyDeckCards(run.getLastDiscarded());
                    run.addMoney(3);
                }
            })),

    // Jokers 096-100
    FLASH_CARD("Flash Card", Rarity.UNCOMMON, 5, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> run.getScoring().addMult(2L * run.getStats().getRerolls()))),
    POPCORN("Popcorn", Rarity.COMMON, 5, b -> b
            .on(Trigger.ON_HAND_PLAYED, (run, self) -> run.getScoring().addMult(Math.max(0, 20 - 4 * self.getCounter())))
            .on(Trigger.ON_ROUND_END, (run, self) -> self.addCounter(1))),
    SPARE_TROUSERS("Spare Trousers", Rarity.UNCOMMON, 6, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> {
                if (run.getScoring().getHand().hasTwoPair()) self.addCounter(2);
                run.getScoring().addMult(self.getCounter());
            })),
    ANCIENT_JOKER("Ancient Joker", Rarity.RARE, 8, b -> b
            .on(Trigger.ON_ROUND_START, (run, self) -> self.setCounter(gen(run, RngSource.MISC).nextInt(4) + 1))
            .on(Trigger.ON_SCORED_CARD, (run, self) -> { DeckCard c = scored(run); if (c != null && self.getCounter() > 0 && matchesSuit(c, self.getCounter() - 1)) run.getScoring().multiplyMult(x("1.5")); })),
    RAMEN("Ramen", Rarity.UNCOMMON, 6, b -> b
            .on(Trigger.ON_HAND_DISCARDED, (run, self) -> self.addCounter(run.getLastDiscarded().size()))
            .on(Trigger.ON_HAND_PLAYED, (run, self) -> {
                BigDecimal mult = x("2").subtract(new BigDecimal("0.01").multiply(BigDecimal.valueOf(self.getCounter())));
                run.getScoring().multiplyMult(mult.max(BigDecimal.ZERO));
            })),

    // Jokers 101-105  (skipped : 102 Seltzer)
    WALKIE_TALKIE("Walkie Talkie", Rarity.COMMON, 4, b -> b.on(Trigger.ON_SCORED_CARD,
            (run, self) -> { DeckCard c = scored(run); if (c != null && (c.getRank() == Rank.TEN || c.getRank() == Rank.FOUR)) { run.getScoring().addChips(10); run.getScoring().addMult(4); } })),
    CASTLE("Castle", Rarity.UNCOMMON, 6, b -> b.on(Trigger.ON_HAND_PLAYED, (run, self) -> {
                if (run.getScoring().getHand().hasFlush() && run.getScoring().getHand().playedCount() == 5)
                    self.addCounter(run.getScoring().getHand().playedCount());
                run.getScoring().addChips(self.getCounter());
            })),
    SMILEY_FACE("Smiley Face", Rarity.COMMON, 4, b -> b.on(Trigger.ON_SCORED_CARD,
            (run, self) -> { DeckCard c = scored(run); if (c != null && c.isFace()) run.getScoring().addMult(5); })),
    CAMPFIRE("Campfire", Rarity.RARE, 9, b -> b
            .on(Trigger.ON_HAND_PLAYED, (run, self) -> run.getScoring().multiplyMult(onePlus("0.25", run.getStats().getCardsSold() - self.getCounter())))
            .on(Trigger.ON_BOSS_DEFEATED, (run, self) -> self.setCounter(run.getStats().getCardsSold()))),

    // Jokers 106-110
    GOLDEN_TICKET("Golden Ticket", Rarity.COMMON, 5, b -> b
            .on(Trigger.ON_SCORED_CARD, (run, self) -> { DeckCard c = scored(run); if (c != null && c.getEnhancement() == Enhancement.GOLD) run.addMoney(3); })
            .on(Trigger.ON_HELD_CARD, (run, self) -> { DeckCard c = scored(run); if (c != null && c.getSeal() == Seal.GOLD_SEAL) run.addMoney(2); })),
    MR_BONES("Mr. Bones", Rarity.UNCOMMON, 5, b -> b.trait(JokerTrait.PREVENTS_LOSS)),
    ACROBAT("Acrobat", Rarity.UNCOMMON, 6, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> { if (run.getRound() != null && run.getRound().getHandsRemaining() == 1) run.getScoring().multiplyMult(x("3")); })),
    SOCK_AND_BUSKIN("Sock and Buskin", Rarity.UNCOMMON, 6, b -> b.retriggerPlayed(
            (run, self, card) -> card.isFace() ? 1 : 0)),
    SWASHBUCKLER("Swashbuckler", Rarity.COMMON, 4, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> {
                int sum = 0;
                for (JokerCard j : run.getJokers()) if (j != self) sum += j.getSellValue();
                run.getScoring().addMult(sum);
            })),

    // Jokers 111-115  (skipped : 111 Troubadour, 113 Smeared Joker)
    CERTIFICATE("Certificate", Rarity.UNCOMMON, 6, b -> b.on(Trigger.ON_ROUND_START,
            (run, self) -> {
                RandomGenerator g = gen(run, RngSource.MISC);
                DeckCard c = new DeckCard(Rank.values()[g.nextInt(Rank.values().length)], DeckCard.Suit.values()[g.nextInt(4)]);
                c.apply(Seal.values()[g.nextInt(Seal.values().length)]);
                c.apply(Enhancement.values()[g.nextInt(Enhancement.values().length)]);
                run.addCardToHand(c);
            })),
    THROWBACK("Throwback", Rarity.UNCOMMON, 6, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> run.getScoring().multiplyMult(onePlus("0.3", run.getStats().getBlindsSkipped())))),
    HANGING_CHAD("Hanging Chad", Rarity.COMMON, 4, b -> b.retriggerPlayed((run, self, card) -> {
                int hand = run.getStats().getTotalHandsPlayed();
                if (self.getCounter() != hand) { self.setCounter(hand); return 2; }
                return 0;
            })),

    // Jokers 116-120
    ROUGH_GEM("Rough Gem", Rarity.UNCOMMON, 7, b -> b.on(Trigger.ON_SCORED_CARD,
            (run, self) -> { if (isSuit(run.getScoring(), Suit.DIAMOND) && run.roll(RngSource.MISC, 1, 2)) run.addMoney(2); })),
    BLOODSTONE("Bloodstone", Rarity.UNCOMMON, 7, b -> b.on(Trigger.ON_SCORED_CARD,
            (run, self) -> { if (isSuit(run.getScoring(), Suit.HEART) && run.roll(RngSource.MISC, 1, 2)) run.getScoring().multiplyMult(x("1.5")); })),
    ARROWHEAD("Arrowhead", Rarity.UNCOMMON, 7, b -> b.on(Trigger.ON_SCORED_CARD,
            (run, self) -> { if (isSuit(run.getScoring(), Suit.SPADE) && run.roll(RngSource.MISC, 1, 2)) run.getScoring().addChips(75); })),
    ONYX_AGATE("Onyx Agate", Rarity.UNCOMMON, 7, b -> b.on(Trigger.ON_SCORED_CARD,
            (run, self) -> { if (isSuit(run.getScoring(), Suit.CLUB) && run.roll(RngSource.MISC, 1, 2)) run.getScoring().addMult(16); })),
    GLASS_JOKER("Glass Joker", Rarity.UNCOMMON, 6, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> run.getScoring().multiplyMult(onePlus("0.75", run.getStats().getGlassDestroyed())))),

    // Jokers 121-125  (skipped : 121 Showman, 125 Merry Andy)
    FLOWER_POT("Flower Pot", Rarity.UNCOMMON, 6, b -> b
            .on(Trigger.ON_SCORED_CARD, (run, self) -> {
                int hand = run.getStats().getTotalHandsPlayed();
                int mask = (self.getCounter() >> 4) == hand ? (self.getCounter() & 0xF) : 0;
                DeckCard c = scored(run);
                if (c != null) mask |= suitMask(c);
                self.setCounter((hand << 4) | mask);
            })
            .on(Trigger.ON_HAND_PLAYED, (run, self) -> {
                int hand = run.getStats().getTotalHandsPlayed();
                int mask = (self.getCounter() >> 4) == hand ? (self.getCounter() & 0xF) : 0;
                int suits = Integer.bitCount(mask);
                if (suits > 1) run.getScoring().multiplyMult(BigDecimal.valueOf(suits));
            })),
    BLUEPRINT("Blueprint", Rarity.RARE, 10, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> {
                List<JokerCard> js = run.getJokers();
                int i = js.indexOf(self);
                if (i >= 0 && i + 1 < js.size()) run.getScoring().retriggerJoker(js.get(i + 1));
            })),
    WEE_JOKER("Wee Joker", Rarity.RARE, 8, b -> b
            .on(Trigger.ON_SCORED_CARD, (run, self) -> { DeckCard c = scored(run); if (c != null && c.getRank() == Rank.TWO) self.addCounter(8); })
            .on(Trigger.ON_HAND_PLAYED, (run, self) -> run.getScoring().addChips(self.getCounter()))),

    // Jokers 126-130  (skipped : 126 Oops! All 6s)
    THE_IDOL("The Idol", Rarity.UNCOMMON, 6, b -> b
            .on(Trigger.ON_ROUND_START, (run, self) -> {
                RandomGenerator g = gen(run, RngSource.MISC);
                self.setCounter(g.nextInt(Rank.values().length) * 4 + g.nextInt(4));
            })
            .on(Trigger.ON_SCORED_CARD, (run, self) -> {
                DeckCard c = scored(run);
                if (c != null && c.getRank().ordinal() == self.getCounter() / 4 && matchesSuit(c, self.getCounter() % 4))
                    run.getScoring().multiplyMult(x("2"));
            })),
    SEEING_DOUBLE("Seeing Double", Rarity.UNCOMMON, 6, b -> b
            .on(Trigger.ON_SCORED_CARD, (run, self) -> {
                int hand = run.getStats().getTotalHandsPlayed();
                int mask = (self.getCounter() >> 2) == hand ? (self.getCounter() & 3) : 0;
                DeckCard c = scored(run);
                if (c != null) { if (c.isClub()) mask |= 1; if (c.isSpade() || c.isHeart() || c.isDiamond()) mask |= 2; }
                self.setCounter((hand << 2) | mask);
            })
            .on(Trigger.ON_HAND_PLAYED, (run, self) -> {
                int hand = run.getStats().getTotalHandsPlayed();
                int mask = (self.getCounter() >> 2) == hand ? (self.getCounter() & 3) : 0;
                if (mask == 3) run.getScoring().multiplyMult(x("2"));
            })),
    MATADOR("Matador", Rarity.UNCOMMON, 7, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> { if (run.bossTriggeredThisPlay()) run.addMoney(8); })),
    HIT_THE_ROAD("Hit the Road", Rarity.RARE, 8, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> run.getScoring().multiplyMult(onePlus("0.5", run.getStats().getDiscardedThisRound(Rank.JACK))))),

    // Jokers 131-135
    THE_DUO("The Duo", Rarity.RARE, 8, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> { if (run.getScoring().getHand().hasPair()) run.getScoring().multiplyMult(x("2")); })),
    THE_TRIO("The Trio", Rarity.RARE, 8, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> { if (run.getScoring().getHand().hasThreeOfAKind()) run.getScoring().multiplyMult(x("3")); })),
    THE_FAMILY("The Family", Rarity.RARE, 8, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> { if (run.getScoring().getHand().hasFourOfAKind()) run.getScoring().multiplyMult(x("4")); })),
    THE_ORDER("The Order", Rarity.RARE, 8, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> { if (run.getScoring().getHand().hasStraight()) run.getScoring().multiplyMult(x("3")); })),
    THE_TRIBE("The Tribe", Rarity.RARE, 8, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> { if (run.getScoring().getHand().hasFlush()) run.getScoring().multiplyMult(x("2")); })),

    // Jokers 136-140  (skipped : 136 Stuntman, 137 Invisible Joker)
    BRAINSTORM("Brainstorm", Rarity.RARE, 10, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> {
                List<JokerCard> js = run.getJokers();
                if (!js.isEmpty() && js.get(0) != self) run.getScoring().retriggerJoker(js.get(0));
            })),
    SATELLITE("Satellite", Rarity.UNCOMMON, 6, b -> b.on(Trigger.ON_ROUND_END,
            (run, self) -> {
                HandType most = run.getStats().getMostPlayedHand();
                if (most != null) run.addMoney(run.getHandLevels().levelOf(most) / 3);
            })),
    SHOOT_THE_MOON("Shoot the Moon", Rarity.COMMON, 5, b -> b.on(Trigger.ON_HELD_CARD,
            (run, self) -> { DeckCard c = scored(run); if (c != null && c.getRank() == Rank.QUEEN) run.getScoring().addMult(13); })),

    // Jokers 141-145  (skipped : 143 Astronomer)
    DRIVERS_LICENSE("Driver's License", Rarity.RARE, 7, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> {
                int enhanced = 0;
                for (DeckCard c : run.getDeck()) if (c.getEnhancement() != null) enhanced++;
                if (enhanced >= 16) run.getScoring().multiplyMult(x("3"));
            })),
    CARTOMANCER("Cartomancer", Rarity.UNCOMMON, 6, b -> b.on(Trigger.ON_ROUND_START,
            (run, self) -> run.createConsumable(Tarots.random(gen(run, RngSource.TAROT_GENERATION)).spec()))),
    BURNT_JOKER("Burnt Joker", Rarity.RARE, 8, b -> b.on(Trigger.ON_HAND_DISCARDED,
            (run, self) -> {
                if (run.getStats().getDiscardsUsedThisRound() == 1 && !run.getLastDiscarded().isEmpty())
                    run.levelUpHand(new HandEvaluator().evaluate(run.getLastDiscarded()).type());
            })),
    BOOTSTRAPS("Bootstraps", Rarity.UNCOMMON, 7, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> run.getScoring().addMult(2L * (Math.max(0, run.getMoney()) / 5)))),

    // Jokers 146-150  (skipped : 146 Canio)
    TRIBOULET("Triboulet", Rarity.LEGENDARY, 20, b -> b.on(Trigger.ON_SCORED_CARD,
            (run, self) -> { DeckCard c = scored(run); if (c != null && (c.getRank() == Rank.KING || c.getRank() == Rank.QUEEN)) run.getScoring().multiplyMult(x("2")); })),
    YORICK("Yorick", Rarity.LEGENDARY, 20, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> run.getScoring().multiplyMult(onePlus("1", run.getStats().getCardsDiscarded() / 23)))),
    CHICOT("Chicot", Rarity.LEGENDARY, 20, b -> b.trait(JokerTrait.DISABLES_BOSS)),
    PERKEO("Perkeo", Rarity.LEGENDARY, 20, b -> b.on(Trigger.ON_SHOP_END,
            (run, self) -> {
                List<ConsumableCard> cs = run.getConsumables();
                if (!cs.isEmpty()) run.createConsumable(cs.get(gen(run, RngSource.MISC).nextInt(cs.size())).getSpec());
            }));

    private final Rarity rarity;
    private final JokerSpec spec;

    Jokers(String displayName, Rarity rarity, int cost, UnaryOperator<JokerSpec.Builder> define) {
        this.rarity = rarity;
        this.spec = define.apply(JokerSpec.named(displayName, rarity).cost(cost)).build();
    }

    public Rarity rarity()  { return rarity; }
    public JokerSpec spec() { return spec; }

    // rarity split for shop/pack draws (cumulative, out of 100): Common 55 / Uncommon 25 / Rare 10
    private static final int COMMON_WEIGHT = 55, UNCOMMON_WEIGHT = 90;

    /** A random joker of {@code rarity}; falls back to a rarity-weighted pick while that bucket is unimplemented. */
    public static Jokers randomOfRarity(Rarity rarity, RandomGenerator stream) {
        Jokers[] all = values();
        int matching = 0;
        for (Jokers j : all) if (j.rarity() == rarity) matching++;
        if (matching == 0) return weightedRandom(stream);
        int nth = stream.nextInt(matching);
        for (Jokers j : all) if (j.rarity() == rarity && nth-- == 0) return j;
        return all[0];   // unreachable
    }

    /** A random joker weighted by rarity; falls back to any joker while a rarity bucket is unimplemented. */
    public static Jokers weightedRandom(RandomGenerator stream) {
        return weightedRandom(stream, COMMON_WEIGHT, UNCOMMON_WEIGHT);
    }

    /** Rarity-weighted draw with caller-supplied cumulative weights out of 100 (Greed boosts rarity: 30/75). */
    public static Jokers weightedRandom(RandomGenerator stream, int commonCumulative, int uncommonCumulative) {
        int r = stream.nextInt(100);
        Rarity target = r < commonCumulative ? Rarity.COMMON : r < uncommonCumulative ? Rarity.UNCOMMON : Rarity.RARE;
        Jokers[] all = values();
        int matching = 0;
        for (Jokers j : all) if (j.rarity() == target) matching++;
        if (matching == 0) return all[stream.nextInt(all.length)];
        int nth = stream.nextInt(matching);
        for (Jokers j : all) if (j.rarity() == target && nth-- == 0) return j;
        return all[0];   // unreachable
    }

    /** A fresh card for this joker at its shop price. */
    public JokerCard make() { return new JokerCard(spec); }

    // --- effect helpers ---

    private enum Suit { SPADE, HEART, CLUB, DIAMOND }

    private static DeckCard scored(model.game.player.Run run) {
        return run.getScoring().getCurrentScoredCard();
    }

    private static boolean isSuit(ScoringSession s, Suit suit) {
        DeckCard c = s.getCurrentScoredCard();
        if (c == null) return false;
        return switch (suit) {
            case SPADE   -> c.isSpade();
            case HEART   -> c.isHeart();
            case CLUB    -> c.isClub();
            case DIAMOND -> c.isDiamond();
        };
    }

    /** Matches a card against a suit index (0=spade,1=heart,2=club,3=diamond); WILD matches any. */
    private static boolean matchesSuit(DeckCard c, int idx) {
        return switch (idx) {
            case 0 -> c.isSpade();
            case 1 -> c.isHeart();
            case 2 -> c.isClub();
            default -> c.isDiamond();
        };
    }

    /** Bitmask of the suits a card counts as (bit0 spade, 1 heart, 2 club, 3 diamond); WILD is all four. */
    private static int suitMask(DeckCard c) {
        return (c.isSpade() ? 1 : 0) | (c.isHeart() ? 2 : 0) | (c.isClub() ? 4 : 0) | (c.isDiamond() ? 8 : 0);
    }

    private static boolean isEven(Rank r) {
        return switch (r) { case TWO, FOUR, SIX, EIGHT, TEN -> true; default -> false; };
    }

    private static boolean isOdd(Rank r) {
        return switch (r) { case ACE, THREE, FIVE, SEVEN, NINE -> true; default -> false; };
    }

    private static boolean isFibonacci(Rank r) {
        return switch (r) { case ACE, TWO, THREE, FIVE, EIGHT -> true; default -> false; };
    }

    /** Count of deck cards carrying {@code e}. */
    private static int deckCount(model.game.player.Run run, Enhancement e) {
        int n = 0;
        for (DeckCard c : run.getDeck()) if (c.getEnhancement() == e) n++;
        return n;
    }

    /** An exact BigDecimal multiplier from a decimal string, e.g. {@code x("1.5")}. */
    private static BigDecimal x(String v) { return new BigDecimal(v); }

    /** {@code 1 + per * n} as a BigDecimal multiplier (per given as an exact decimal string). */
    private static BigDecimal onePlus(String per, long n) {
        return BigDecimal.ONE.add(new BigDecimal(per).multiply(BigDecimal.valueOf(n)));
    }

    /** A keyed sub-stream for an emergent joker draw. */
    private static RandomGenerator gen(model.game.player.Run run, RngSource source) {
        return run.getRng().streamFor(source, run.nextSalt(source));
    }

    /** A faithful permanent duplicate of a playing card (rank, suit, enhancement, seal, edition). */
    private static DeckCard copyOf(DeckCard c) {
        DeckCard.Suit suit = DeckCard.Suit.SPADES;
        if (c.getEnhancement() != Enhancement.WILD) {
            if (c.isHeart())        suit = DeckCard.Suit.HEARTS;
            else if (c.isClub())    suit = DeckCard.Suit.CLUBS;
            else if (c.isDiamond()) suit = DeckCard.Suit.DIAMONDS;
        }
        DeckCard copy = new DeckCard(c.getRank(), suit);
        if (c.getEnhancement() != null) copy.apply(c.getEnhancement());
        if (c.getSeal() != null)        copy.apply(c.getSeal());
        if (c.getEdition() != null)     copy.apply(c.getEdition());
        return copy;
    }

    /** Gives {@code card} a random shiny edition and adds it to the consumable area if there is room. */
    private static void createEditioned(model.game.player.Run run, ConsumableCard card) {
        RandomGenerator g = gen(run, RngSource.CARD_EDITION);
        card.apply(switch (g.nextInt(3)) { case 0 -> Edition.FOIL; case 1 -> Edition.HOLOGRAPHIC; default -> Edition.POLYCHROME; });
        run.addConsumable(card);
    }
}
