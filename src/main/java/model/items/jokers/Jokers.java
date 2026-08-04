package model.items.jokers;

import model.items.DeckCard;
import model.items.DeckCard.Rank;
import model.items.consumables.ConsumableCard;
import model.items.consumables.ConsumableType;
import model.items.consumables.Planets;
import model.items.consumables.Spectrals;
import model.items.consumables.Tarots;
import model.game.Blind;
import model.game.Match;
import model.game.player.Player;
import model.game.player.PlayerId;
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

/** The joker catalog, in document order with a comment before every group of five. */
public enum Jokers {

    // region Jokers 001-005
    JOKER("Joker", "+4 Mult.", Rarity.COMMON, 2, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> run.getScoring().addMult(4))),
    BANNER("Banner", "+40 Chips for each remaining discard.", Rarity.COMMON, 5, b -> b
            .state((self, info) -> "Currently : +" + (30L * info.discardsRemaining()) + " Chips")
            .on(Trigger.ON_HAND_PLAYED, (run, self) ->
                    run.getScoring().addChips(30L * run.getRound().getDiscardsRemaining()))),
    BLUE_JOKER("Blue Joker", "+2 Chips for each card in your deck.", Rarity.COMMON, 5, b -> b
            .state((self, info) -> "Currently : +" + (2L * info.deckSize()) + " Chips")
            .on(Trigger.ON_HAND_PLAYED, (run, self) ->
                    run.getScoring().addChips(2L * run.getDeck().size()))),
    BULL("Bull", "+2 Chips for each $1 you have.", Rarity.UNCOMMON, 6, b -> b
            .state((self, info) -> "Currently : +" + (2L * Math.max(0, info.money())) + " Chips")
            .on(Trigger.ON_HAND_PLAYED, (run, self) ->
                    run.getScoring().addChips(2L * Math.max(0, run.getMoney())))),
    SQUARE_JOKER("Square Joker", "Gains +4 Chips if the played hand has exactly 4 cards.", Rarity.COMMON, 4, b -> b
            .state(self -> "Currently : +" + self.getCounter() + " Chips")
            .on(Trigger.ON_HAND_PLAYED, (run, self) -> {
                if (run.getScoring().getHand().playedCount() == 4) self.addCounter(4);
                run.getScoring().addChips(self.getCounter());
            })),
    // endregion
    // region Jokers 006-010 (missing : Hiker)
    HIKER("HIKER", "Every played card permanently gains +5 Chips when scored.", Rarity.UNCOMMON, 5, b -> b.on(Trigger.ON_SCORED_CARD, (run, self) -> {
                DeckCard c = scored(run);
                if (c == null) return;
                c.addBonusChips(5);              // permanent: the card scores +5 more every future hand
                run.getScoring().addChips(5);    // and the gain already counts on the hand it is earned
            })),
    HALF_JOKER("Half Joker", "+20 Mult if the played hand has 3 or fewer cards.", Rarity.COMMON, 5, b -> b
            .on(Trigger.ON_HAND_PLAYED, (run, self) -> { if (run.getScoring().getHand().playedCount() <= 3) run.getScoring().addMult(20); })),
    MYSTIC_SUMMIT("Mystic Summit", "+15 Mult when 0 discards remain.", Rarity.COMMON, 5, b -> b
            .on(Trigger.ON_HAND_PLAYED, (run, self) -> {
                if (run.getRound() != null && run.getRound().getDiscardsRemaining() == 0)
                    run.getScoring().addMult(15);
            })),
    RAISED_FIST("Raised Fist", "Adds double the rank of the lowest card held in hand to Mult.", Rarity.COMMON, 5, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> {
                DeckCard low = null;
                for (DeckCard c : run.getHeld()) if (low == null || c.getRank().getChips() < low.getRank().getChips()) low = c;
                if (low != null) run.getScoring().addMult(2L * low.getRank().getChips());
            })),
    MISPRINT("Misprint", "+0 to +15 Mult (random), then X0.75 to X1.5 Mult (random).", Rarity.UNCOMMON, 6, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> {
                RandomGenerator g = gen(run, RngSource.MISC);
                run.getScoring().addMult(g.nextInt(16));
                run.getScoring().multiplyMult(BigDecimal.valueOf(75 + 25L * g.nextInt(4), 2));
            })),
    // endregion
    // region Jokers 011-015
    SWASHBUCKLER("Swashbuckler", "Adds the sell value of all your other Jokers to Mult.", Rarity.COMMON, 4, b -> b
            .state((self, info) -> "Currently : +" + info.otherJokersSellValue(self) + " Mult")
            .on(Trigger.ON_HAND_PLAYED, (run, self) -> {
                int sum = 0;
                for (JokerCard j : run.getJokers()) if (j != self) sum += j.getSellValue();
                run.getScoring().addMult(sum);
            })),
    ABSTRACT_JOKER("Abstract Joker", "+3 Mult for each Joker and consumable you own.", Rarity.COMMON, 4, b -> b
            .state((self, info) -> "Currently : +" + (3L * info.jokerCount()) + " Mult")
            .on(Trigger.ON_HAND_PLAYED, (run, self) -> {
                run.getScoring().addMult(3L * run.getJokers().size());
            })),
    GREEN_JOKER("Green Joker", "+1 Mult per hand played, -1 Mult per discard.", Rarity.COMMON, 4, b -> b
            .state(self -> "Currently : +" + self.getCounter() + " Mult")
            .on(Trigger.ON_HAND_PLAYED, (run, self) -> {
                self.addCounter(1);
                run.getScoring().addMult(self.getCounter());
            })
            .on(Trigger.ON_HAND_DISCARDED, (run, self) -> self.setCounter(Math.max(0, self.getCounter() - 1)))),
    BOOTSTRAPS("Bootstraps", "+2 Mult for every $5 you have.", Rarity.UNCOMMON, 7, b -> b
            .on(Trigger.ON_HAND_PLAYED, (run, self) -> run.getScoring().addMult(2L * (Math.max(0, run.getMoney()) / 5)))),
    EROSION("Erosion", "+4 Mult for each card below the deck's starting size in your full deck.", Rarity.UNCOMMON, 6, b -> b
            .state((self, info) -> "Currently : +" + (4L * Math.max(0, 52 - info.deckSize())) + " Mult")
            .on(Trigger.ON_HAND_PLAYED, (run, self) -> run.getScoring().addMult(4L * Math.max(0, 52 - run.getDeck().size())))),
    // endregion
    // region Jokers 016-020 (missing : Red Joker)
    CEREMONIAL_DAGGER("Ceremonial Dagger", "When Blind selected, destroys the Joker to the right and adds triple its sell value to Mult.", Rarity.UNCOMMON, 6, b -> b
            .state(self -> "Currently : +" + self.getCounter() + " Mult")
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
    RED_JOKER("Red Joker", "Gains +3 Mult when any Booster Pack is skipped.", Rarity.COMMON, 5, b -> b
            .state(self -> "Currently : +" + self.getCounter() + " Mult")
            .on(Trigger.ON_PACK_SKIPPED, (run, self) -> self.addCounter(3))
            .on(Trigger.ON_HAND_PLAYED, (run, self) -> { if (self.getCounter() > 0) run.getScoring().addMult(self.getCounter()); })),
    FLASH_CARD("Flash Card", "Gains +2 Mult per reroll in the shop.", Rarity.UNCOMMON, 5, b -> b
            .state(self -> "Currently : +" + self.getCounter() + " mult")
            .on(Trigger.ON_HAND_PLAYED, (run, self) -> {
                self.setCounter((int) (2L * run.getStats().getRerolls()));
                run.getScoring().addMult(self.getCounter());
            })),
    ACROBAT("Acrobat", "X3 Mult on the final hand of the round.", Rarity.UNCOMMON, 6, b -> b
            .on(Trigger.ON_HAND_PLAYED, (run, self) -> {
                if (run.getRound() != null && run.getRound().getHandsRemaining() == 1)
                run.getScoring().multiplyMult(x("3"));
            })),
    CARD_SHARP("Card Sharp", "X3 Mult if this poker hand was already played this round.", Rarity.UNCOMMON, 6, b -> b
            .on(Trigger.ON_HAND_PLAYED, (run, self) -> {
                if (run.getStats().getHandPlaysThisRound(run.getScoring().getHand().type()) > 1)
                    run.getScoring().multiplyMult(x("3"));
            })),
    // endregion
    // region Jokers 021-025
    JOKER_STENCIL("Joker Stencil", "X1 Mult for each empty Joker slot (Joker Stencil included).", Rarity.UNCOMMON, 8, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> {
                int jokers = 0;
                for (JokerCard j : run.getJokers()) if (j.getSpec() != self.getSpec()) jokers++;
                int empty = run.getJokerSlots() - jokers;
                if (empty > 0) run.getScoring().multiplyMult(BigDecimal.valueOf(empty));
            })),
    THROWBACK("Throwback", "X0.5 Mult for each Blind skipped this run.", Rarity.UNCOMMON, 6, b -> b
            .state(self -> "Currently : X" + self.getCounter() + " mult")
            .on(Trigger.ON_HAND_PLAYED, (run, self) -> run.getScoring().multiplyMult(onePlus("0.3", run.getStats().getBlindsSkipped())))),
    CHALLENGER("Challenger", "X0.25 Mult for each sticker you own.", Rarity.UNCOMMON, 8, b -> b
            .on(Trigger.ON_HAND_PLAYED, (run, self) -> {
                int stickers = 0;
                for (JokerCard j : run.getJokers())      stickers += j.getStickers().size();
                for (ConsumableCard cc : run.getConsumables()) stickers += cc.getStickers().size();
                for (DeckCard d : run.getDeck())         stickers += d.getStickers().size();
                if (stickers > 0) run.getScoring().multiplyMult(onePlus("0.25", stickers));
            })),
    HOLOGRAM("Hologram", "Gains X0.25 Mult every time a playing card is added to your deck.", Rarity.UNCOMMON, 7, b -> b
            .on(Trigger.ON_HAND_PLAYED, (run, self) -> run.getScoring().multiplyMult(onePlus("0.25", run.getStats().getCardsAdded())))),
    MADNESS("Madness", "When Small/Big Blind selected, gain X0.5 Mult and destroy an adjacent Joker.", Rarity.UNCOMMON, 7, b -> b
            .state(self -> "Currently : X" + (1 + 0.5 * self.getCounter()) + " mult")
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
    // endregion
    // region Jokers 026-030 (missing : Scalper, Vulture, Transparent Joker)
    GRUDGE("Grudge", "Permanently gains X0.2 Mult each time an opponent effect targets you.", Rarity.UNCOMMON, 7, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> {
                int hits = run.getStats().getTimesTargeted();
                if (hits > 0) run.getScoring().multiplyMult(onePlus("0.2", hits));
            })),
    SCALPER("Scalper", "Gains X0.1 upon emptying a shop", Rarity.COMMON, 5, b -> b
            .on(Trigger.ON_SHOP_EMPTIED, (run, self) -> self.addCounter(1))
            .on(Trigger.ON_HAND_PLAYED, (run, self) -> {
                if (self.getCounter() > 0) run.getScoring().multiplyMult(onePlus("0.1", self.getCounter()));
            })
            .state(card -> card.getCounter() == 0 ? null : "X" + onePlus("0.1", card.getCounter()).toPlainString() + " Mult")),
    GENERATIONAL_HATER("Generational Hater", "When a Boss Blind is defeated, adds a random sticker to a random Joker of a player above you.", Rarity.UNCOMMON, 8, b -> b.trait(JokerTrait.SEAT_COUPLING).on(Trigger.ON_BOSS_DEFEATED,
            (run, self) -> {
                Match m = run.getMatch();
                if (m == null || run.getPlayerId() == null) return;
                List<PlayerId> above = m.seatsAbove(run.getPlayerId());
                if (above.isEmpty()) return;
                RandomGenerator g = gen(run, RngSource.MISC);
                List<JokerCard> theirs = m.getRun(above.get(g.nextInt(above.size()))).getJokers();
                if (theirs.isEmpty()) return;
                Sticker[] pool = { Sticker.ETERNAL, Sticker.PERISHABLE, Sticker.RENTAL, Sticker.STICKY, Sticker.DELAYED, Sticker.FLOATING, Sticker.FRAGILE };
                theirs.get(g.nextInt(theirs.size())).apply(pool[g.nextInt(pool.length)]);
            })),
    VULTURE("Vulture", "Gains X0.25 Mult whenever an opponent fails to beat a blind.", Rarity.UNCOMMON, 7, b -> b.trait(JokerTrait.SEAT_COUPLING)
            .on(Trigger.ON_BLIND_SETTLED, (run, self) -> {
                Match m = run.getMatch();
                if (m == null) return;
                int failed = 0;
                for (Player p : m.getPlayers()) {
                    if (p.run() == run) continue;
                    var r = m.getResult(p.id());
                    if (r != null && r.outcome() == model.game.player.RoundOutcome.LOST) failed++;
                }
                if (failed > 0) self.addCounter(failed);
            })
            .on(Trigger.ON_HAND_PLAYED, (run, self) -> {
                if (self.getCounter() > 0) run.getScoring().multiplyMult(onePlus("0.25", self.getCounter()));
            })
            .state(card -> card.getCounter() == 0 ? null : "X" + onePlus("0.25", card.getCounter()).toPlainString() + " Mult")),
    TRANSPARENT_JOKER("Transparent Joker", "After 2 rounds, sell to copy a random Joker from the leading player.", Rarity.UNCOMMON, 7, b -> b.trait(JokerTrait.SEAT_COUPLING)
            .on(Trigger.ON_BLIND_SETTLED, (run, self) -> self.addCounter(1))   // rounds owned (every blind, win or lose)
            .on(Trigger.ON_SOLD, (run, self) -> {
                if (self.getCounter() < 2) return;                            // only after 2 rounds owned
                Match m = run.getMatch();
                if (m == null) return;
                List<PlayerId> ranking = m.getStandings().ranking();          // the points leader
                if (ranking.isEmpty()) return;
                var leader = m.getRun(ranking.get(0));
                if (leader == run) return;                                    // we are already leading — no one to copy
                List<JokerCard> theirs = leader.getJokers();
                if (theirs.isEmpty()) return;
                JokerCard pick = theirs.get(gen(run, RngSource.MISC).nextInt(theirs.size()));
                run.queueJokerFromSale(new JokerCard(pick.getSpec()));        // a fresh copy takes the slot the sale frees
            })
            .state(card -> "Owned " + card.getCounter() + " round" + (card.getCounter() == 1 ? "" : "s"))),
    // endregion
    // region Jokers 031-035
    FIBONACCI("Fibonacci", "Each played Ace, 2, 3, 5, or 8 gives +8 Mult when scored.", Rarity.UNCOMMON, 8, b -> b.on(Trigger.ON_SCORED_CARD,
            (run, self) -> { DeckCard c = scored(run); if (c != null && anyRank(run, c, Jokers::isFibonacci)) run.getScoring().addMult(8); })),
    SIXTH_SENSE("Sixth Sense", "If the first hand of the round is a single 6, destroy it and create a Spectral card.", Rarity.UNCOMMON, 6, b -> b.on(Trigger.ON_SCORED_CARD,
            (run, self) -> {
                DeckCard c = scored(run);
                int hand = run.getStats().getTotalHandsPlayed();
                if (c != null && self.getCounter() != hand
                        && run.getStats().getHandsPlayedThisRound() == 1
                        && run.getScoring().getHand().playedCount() == 1
                        && countsAs(run, c, Rank.SIX)) {
                    self.setCounter(hand);
                    run.destroyDeckCards(List.of(c));
                    run.createConsumable(Spectrals.random(gen(run, RngSource.SPECTRAL_GENERATION)).spec());
                }
            })),
    CELESTIAL_7("Celestial 7", "1 in 4 chance to create an editioned Planet card when a 7 is played.", Rarity.COMMON, 5, b -> b.on(Trigger.ON_SCORED_CARD,
            (run, self) -> {
                DeckCard c = scored(run);
                if (c != null && countsAs(run, c, Rank.SEVEN) && run.roll(RngSource.MISC, 1, 4))
                    createEditioned(run, new ConsumableCard(Planets.random(gen(run, RngSource.PLANET_GENERATION)).spec()));
            })),
    EIGHT_BALL("8 Ball", "1 in 4 chance for each played 8 to create an editioned Tarot card when scored.", Rarity.COMMON, 5, b -> b.on(Trigger.ON_SCORED_CARD,
            (run, self) -> {
                DeckCard c = scored(run);
                if (c != null && countsAs(run, c, Rank.EIGHT) && run.roll(RngSource.TAROT_GENERATION, 1, 4))
                    createEditioned(run, new ConsumableCard(Tarots.random(gen(run, RngSource.TAROT_GENERATION)).spec()));
            })),
    CLOUD_9("Cloud 9", "Earn $1 for each 9 in your full deck at end of round.", Rarity.UNCOMMON, 7, b -> b.on(Trigger.ON_ROUND_END,
            (run, self) -> {
                int nines = 0;
                for (DeckCard c : run.getDeck()) if (countsAs(run, c, Rank.NINE)) nines++;   // an 8 counts as a 9 under Dyscalculie
                run.addMoney(nines);
            })),
    // endregion
    // region Jokers 036-040
    WALKIE_TALKIE("Walkie Talkie", "Each played 10 or 4 gives +10 Chips and +4 Mult when scored.", Rarity.COMMON, 4, b -> b.on(Trigger.ON_SCORED_CARD,
            (run, self) -> { DeckCard c = scored(run); if (c != null && (countsAs(run, c, Rank.TEN) || countsAs(run, c, Rank.FOUR))) { run.getScoring().addChips(10); run.getScoring().addMult(4); } })),
    HIT_THE_ROAD("Hit the Road", "Gains X0.2 Mult for every Jack discarded this round (resets when a Boss Blind is beaten).", Rarity.RARE, 8, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> run.getScoring().multiplyMult(onePlus("0.5", run.getStats().getDiscardedThisRound(Rank.JACK))))),
    SHOOT_THE_MOON("Shoot the Moon", "Each Queen held in hand gives +13 Mult.", Rarity.COMMON, 5, b -> b.on(Trigger.ON_HELD_CARD,
            (run, self) -> { DeckCard c = scored(run); if (c != null && c.getRank() == Rank.QUEEN) run.getScoring().addMult(13); })),
    BARON("Baron", "Each King held in hand gives X1.5 Mult.", Rarity.UNCOMMON, 7, b -> b.on(Trigger.ON_HELD_CARD,
            (run, self) -> { DeckCard c = scored(run); if (c != null && c.getRank() == Rank.KING) run.getScoring().multiplyMult(x("1.5")); })),
    SCHOLAR("Scholar", "Played Aces give +20 Chips and +4 Mult when scored.", Rarity.COMMON, 4, b -> b.on(Trigger.ON_SCORED_CARD,
            (run, self) -> { DeckCard c = scored(run); if (c != null && countsAs(run, c, Rank.ACE)) { run.getScoring().addChips(20); run.getScoring().addMult(4); } })),
    // endregion
    // region Jokers 041-045
    SEVEN_ATE_NINE("7 Ate 9", "If the last three played cards of a hand are a 7, an 8, and another card, destroy the last card.", Rarity.UNCOMMON, 6, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> {
                List<DeckCard> cards = run.getScoring().getScoringCards();
                int n = cards.size();
                if (n >= 3 && countsAs(run, cards.get(n - 3), Rank.SEVEN) && countsAs(run, cards.get(n - 2), Rank.EIGHT))
                    run.destroyDeckCards(List.of(cards.get(n - 1)));
            })),
    SCARY_FACE("Scary Face", "Played face cards give +30 Chips when scored.", Rarity.COMMON, 4, b -> b.on(Trigger.ON_SCORED_CARD,
            (run, self) -> { DeckCard c = scored(run); if (c != null && run.isFaceCard(c)) run.getScoring().addChips(30); })),
    SMILEY_FACE("Smiley Face", "Played face cards give +5 Mult when scored.", Rarity.COMMON, 4, b -> b.on(Trigger.ON_SCORED_CARD,
            (run, self) -> { DeckCard c = scored(run); if (c != null && run.isFaceCard(c)) run.getScoring().addMult(5); })),
    PHOTOGRAPH("Photograph", "First played face card gives X2 Mult when scored.", Rarity.COMMON, 5, b -> b.on(Trigger.ON_SCORED_CARD,
            (run, self) -> {
                DeckCard c = scored(run);
                int hand = run.getStats().getTotalHandsPlayed();
                if (c != null && run.isFaceCard(c) && self.getCounter() != hand) { run.getScoring().multiplyMult(x("2")); self.setCounter(hand); }
            })),
    RIDE_THE_BUS("Ride the Bus", "+1 Mult per consecutive hand played without a scoring face card.", Rarity.COMMON, 6, b -> b
            .state(self -> "Current streak: +" + (self.getCounter() >> 1) + " Mult")
            .on(Trigger.ON_SCORED_CARD, (run, self) -> { DeckCard c = scored(run); if (c != null && run.isFaceCard(c)) self.setCounter(self.getCounter() | 1); })   // low bit = a scoring face appeared this hand
            .on(Trigger.ON_HAND_PLAYED, (run, self) -> {
                int streak = self.getCounter() >> 1;
                streak = (self.getCounter() & 1) == 1 ? 0 : streak + 1;
                run.getScoring().addMult(streak);
                self.setCounter(streak << 1);
            })),
    // endregion
    // region Jokers 046-050
    WRATHFUL_JOKER("Wrathful Joker", "Played Spade cards give +3 Mult when scored.", Rarity.COMMON, 5, b -> b.on(Trigger.ON_SCORED_CARD,
            (run, self) -> { if (isSuit(run.getScoring(), Suit.SPADE)) run.getScoring().addMult(3); })),
    LUSTY_JOKER("Lusty Joker", "Played Heart cards give +3 Mult when scored.", Rarity.COMMON, 5, b -> b.on(Trigger.ON_SCORED_CARD,
            (run, self) -> { if (isSuit(run.getScoring(), Suit.HEART)) run.getScoring().addMult(3); })),
    GLUTTONOUS_JOKER("Gluttonous Joker", "Played Club cards give +3 Mult when scored.", Rarity.COMMON, 5, b -> b.on(Trigger.ON_SCORED_CARD,
            (run, self) -> { if (isSuit(run.getScoring(), Suit.CLUB)) run.getScoring().addMult(3); })),
    GREEDY_JOKER("Greedy Joker", "Played Diamond cards give +3 Mult when scored.", Rarity.COMMON, 5, b -> b.on(Trigger.ON_SCORED_CARD,
            (run, self) -> { if (isSuit(run.getScoring(), Suit.DIAMOND)) run.getScoring().addMult(3); })),
    CASTLE("Castle", "Gains +4 Chips for each played card if all played cards share one suit.", Rarity.UNCOMMON, 6, b -> b
            .state(self -> "Current bonus: +" + self.getCounter() + " Chips")
            .on(Trigger.ON_HAND_PLAYED, (run, self) -> {
                if (run.getScoring().getHand().hasFlush() && run.getScoring().getHand().playedCount() == 5)
                    self.addCounter(run.getScoring().getHand().playedCount());
                run.getScoring().addChips(self.getCounter());
            })),
    // endregion
    // region Jokers 051-055
    ARROWHEAD("Arrowhead", "1 in 2 chance for played Spade cards to give +75 Chips when scored.", Rarity.UNCOMMON, 7, b -> b.on(Trigger.ON_SCORED_CARD,
            (run, self) -> { if (isSuit(run.getScoring(), Suit.SPADE) && run.roll(RngSource.MISC, 1, 2)) run.getScoring().addChips(75); })),
    BLOODSTONE("Bloodstone", "1 in 2 chance for played Heart cards to give X1.5 Mult when scored.", Rarity.UNCOMMON, 7, b -> b.on(Trigger.ON_SCORED_CARD,
            (run, self) -> { if (isSuit(run.getScoring(), Suit.HEART) && run.roll(RngSource.MISC, 1, 2)) run.getScoring().multiplyMult(x("1.5")); })),
    ONYX_AGATE("Onyx Agate", "1 in 2 chance for played Club cards to give +16 Mult when scored.", Rarity.UNCOMMON, 7, b -> b.on(Trigger.ON_SCORED_CARD,
            (run, self) -> { if (isSuit(run.getScoring(), Suit.CLUB) && run.roll(RngSource.MISC, 1, 2)) run.getScoring().addMult(16); })),
    ROUGH_GEM("Rough Gem", "1 in 2 chance for played Diamond cards to give $2 when scored.", Rarity.UNCOMMON, 7, b -> b.on(Trigger.ON_SCORED_CARD,
            (run, self) -> { if (isSuit(run.getScoring(), Suit.DIAMOND) && run.roll(RngSource.MISC, 1, 2)) run.addMoney(2); })),
    FLOWER_POT("Flower Pot", "X1 Mult for every different suit in the played hand.", Rarity.UNCOMMON, 6, b -> b
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
    // endregion
    // region Jokers 056-060
    ODD_TODD("Odd Todd", "Played odd-rank cards give +31 Chips when scored.", Rarity.COMMON, 4, b -> b.on(Trigger.ON_SCORED_CARD,
            (run, self) -> { DeckCard c = scored(run); if (c != null && anyRank(run, c, Jokers::isOdd)) run.getScoring().addChips(31); })),
    EVEN_STEVEN("Even Steven", "Played even-rank cards give +4 Mult when scored.", Rarity.COMMON, 4, b -> b.on(Trigger.ON_SCORED_CARD,
            (run, self) -> { DeckCard c = scored(run); if (c != null && anyRank(run, c, Jokers::isEven)) run.getScoring().addMult(4); })),
    THE_IDOL("The Idol", "Each played card of a set rank and suit gives X2 Mult (changes every round).", Rarity.UNCOMMON, 6, b -> b
            .state(self -> "Idolized card this round: " + rankName(self.getCounter() / 4) + " of " + suitIndexName(self.getCounter() % 4))
            .on(Trigger.ON_ROUND_START, (run, self) -> {
                RandomGenerator g = gen(run, RngSource.MISC);
                self.setCounter(g.nextInt(Rank.values().length) * 4 + g.nextInt(4));
            })
            .on(Trigger.ON_SCORED_CARD, (run, self) -> {
                DeckCard c = scored(run);
                if (c != null && countsAs(run, c, Rank.values()[self.getCounter() / 4]) && matchesSuit(c, self.getCounter() % 4))
                    run.getScoring().multiplyMult(x("2"));
            })),
    ANCIENT_JOKER("Ancient Joker", "Each played card of a set suit gives X1.5 Mult (suit changes each round).", Rarity.RARE, 8, b -> b
            .state(self -> self.getCounter() > 0 ? "Suit this round: " + suitIndexName(self.getCounter() - 1) : "Suit chosen at round start")
            .on(Trigger.ON_ROUND_START, (run, self) -> self.setCounter(gen(run, RngSource.MISC).nextInt(4) + 1))
            .on(Trigger.ON_SCORED_CARD, (run, self) -> { DeckCard c = scored(run); if (c != null && self.getCounter() > 0 && matchesSuit(c, self.getCounter() - 1)) run.getScoring().multiplyMult(x("1.5")); })),
    SUPERNOVA("Supernova", "Adds the number of times this hand type has been played this run to Mult.", Rarity.COMMON, 5, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> run.getScoring().addMult(run.getStats().getHandPlays(run.getScoring().getHand().type())))),
    // endregion
    // region Jokers 061-065
    SIAMESE_CAT("Siamese Cat", "Gains +2 Mult if the played hand contains a Two Pair.", Rarity.UNCOMMON, 6, b -> b
            .state(self -> "Current bonus: +" + self.getCounter() + " Mult")
            .on(Trigger.ON_HAND_PLAYED,
                    (run, self) -> {
                        if (run.getScoring().getHand().hasTwoPair()) self.addCounter(2);
                        run.getScoring().addMult(self.getCounter());
                    })),
    CUCKOO_BIRD("Cuckoo Bird", "If the played hand contains a Three of a Kind, set a held card's rank to match the hand.", Rarity.COMMON, 4, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> {
                List<DeckCard> held = run.getHeld();
                if (run.getScoring().getHand().hasThreeOfAKind() && !held.isEmpty()) {
                    Rank r = dominantRank(run.getScoring().getScoringCards());
                    if (r != null) held.get(0).setRank(r);
                }
            })),
    RABBIT("Rabbit", "If the played hand contains a Four of a Kind, create a copy of a played card.", Rarity.COMMON, 6, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> {
                List<DeckCard> cards = run.getScoring().getScoringCards();
                if (run.getScoring().getHand().hasFourOfAKind() && !cards.isEmpty())
                    run.addCardToHand(copyOf(cards.get(0)));
            })),
    CHEETAH("Cheetah", "Gains +15 Chips if the played hand contains a Straight.", Rarity.COMMON, 5, b -> b
            .state(self -> "Current bonus: +" + self.getCounter() + " Chips")
            .on(Trigger.ON_HAND_PLAYED, (run, self) -> {
                if (run.getScoring().getHand().hasStraight()) self.addCounter(15);
                run.getScoring().addChips(self.getCounter());
            })),
    CHAMELEON("Chameleon", "If the played hand contains a Flush, set a held card's suit to the first played card's suit.", Rarity.COMMON, 6, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> {
                List<DeckCard> cards = run.getScoring().getScoringCards();
                List<DeckCard> held = run.getHeld();
                if (run.getScoring().getHand().hasFlush() && !cards.isEmpty() && !held.isEmpty())
                    held.get(0).setSuit(cards.get(0).getSuit());
            })),
    // endregion
    // region Jokers 066-070
    THE_DUO("The Duo", "X2 Mult if the played hand contains a Pair.", Rarity.RARE, 8, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> { if (run.getScoring().getHand().hasPair()) run.getScoring().multiplyMult(x("2")); })),
    THE_TRIO("The Trio", "X3 Mult if the played hand contains a Three of a Kind.", Rarity.RARE, 8, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> { if (run.getScoring().getHand().hasThreeOfAKind()) run.getScoring().multiplyMult(x("3")); })),
    THE_FAMILY("The Family", "X4 Mult if the played hand contains a Four of a Kind.", Rarity.RARE, 8, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> { if (run.getScoring().getHand().hasFourOfAKind()) run.getScoring().multiplyMult(x("4")); })),
    THE_ORDER("The Order", "X3 Mult if the played hand contains a Straight.", Rarity.RARE, 8, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> { if (run.getScoring().getHand().hasStraight()) run.getScoring().multiplyMult(x("3")); })),
    THE_TRIBE("The Tribe", "X3 Mult if the played hand contains a Flush.", Rarity.RARE, 8, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> { if (run.getScoring().getHand().hasFlush()) run.getScoring().multiplyMult(x("2")); })),
    // endregion
    // region Jokers 071-075
    BUTTERFLY("Butterfly", "If the played hand is a Full House, add a random enhancement to one of the played cards.", Rarity.COMMON, 6, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> {
                List<DeckCard> cards = run.getScoring().getScoringCards();
                if (run.getScoring().getHand().type() == HandType.FULL_HOUSE && !cards.isEmpty()) {
                    Enhancement[] all = Enhancement.values();
                    cards.get(0).apply(all[gen(run, RngSource.MISC).nextInt(all.length)]);
                }
            })),
    SEANCE("Séance", "If the played hand contains a Straight Flush, create a Negative Spectral card.", Rarity.UNCOMMON, 6, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> {
                var hand = run.getScoring().getHand();
                if (hand.hasStraight() && hand.hasFlush()) {   // a straight and a flush together is a straight flush
                    ConsumableCard card = new ConsumableCard(Spectrals.random(gen(run, RngSource.SPECTRAL_GENERATION)).spec());
                    card.apply(Edition.NEGATIVE);
                    run.addConsumable(card);
                }
            })),
    MATERIALIST("Materialist", "All played Gold, Stone, and Steel cards give +7 Mult.", Rarity.COMMON, 5, b -> b.on(Trigger.ON_SCORED_CARD,
            (run, self) -> {
                DeckCard c = scored(run);
                if (c == null) return;
                Enhancement e = c.getEnhancement();
                if (e == Enhancement.GOLD || e == Enhancement.STONE || e == Enhancement.STEEL) run.getScoring().addMult(7);
            })),
    GOLDEN_TICKET("Golden Ticket", "Played Gold cards earn $3 when scored; held Gold-sealed cards earn $2.", Rarity.COMMON, 5, b -> b
            .on(Trigger.ON_SCORED_CARD, (run, self) -> { DeckCard c = scored(run); if (c != null && c.getEnhancement() == Enhancement.GOLD) run.addMoney(3); })
            .on(Trigger.ON_HELD_CARD, (run, self) -> { DeckCard c = scored(run); if (c != null && c.getSeal() == Seal.GOLD_SEAL) run.addMoney(2); })),
    STONE_JOKER("Stone Joker", "+30 Chips for each Stone Card in your full deck.", Rarity.UNCOMMON, 6, b -> b
            .state((self, info) -> "Currently : +" + (35L * info.deckCountWithEnhancement(Enhancement.STONE)) + " Chips")
            .on(Trigger.ON_HAND_PLAYED,
            (run, self) -> run.getScoring().addChips(35L * deckCount(run, Enhancement.STONE)))),
    // endregion
    // region Jokers 076-080 (missing : Lucky Cat)
    STEEL_JOKER("Steel Joker", "X0.2 Mult for each Steel Card in your full deck.", Rarity.UNCOMMON, 7, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> run.getScoring().multiplyMult(onePlus("0.2", deckCount(run, Enhancement.STEEL))))),
    GLASS_JOKER("Glass Joker", "Gains X0.75 Mult for every Glass Card that is destroyed.", Rarity.UNCOMMON, 6, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> run.getScoring().multiplyMult(onePlus("0.75", run.getStats().getGlassDestroyed())))),
    LUCKY_CAT("Lucky Cat", "Gains X0.25 Mult every time a Lucky card successfully triggers.", Rarity.UNCOMMON, 6, b -> b
            .state(self -> "Currently : X" + onePlus("0.25", self.getCounter()) + " Mult")
            .on(Trigger.ON_LUCKY_TRIGGERED, (run, self) -> self.addCounter(1))   // X0.25 (a quarter-unit) per success
            .on(Trigger.ON_HAND_PLAYED, (run, self) -> { if (self.getCounter() > 0) run.getScoring().multiplyMult(onePlus("0.25", self.getCounter())); })),
    MARBLE_JOKER("Marble Joker", "Adds a Stone card to the deck when Blind is selected (½ chance it has a seal).", Rarity.UNCOMMON, 6, b -> b.on(Trigger.ON_ROUND_START,
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
    MIDAS_MASK("Midas Mask", "All played face cards become Gold cards when scored.", Rarity.UNCOMMON, 7, b -> b.on(Trigger.ON_SCORED_CARD,
            (run, self) -> { DeckCard c = scored(run); if (c != null && run.isFaceCard(c)) c.apply(Enhancement.GOLD); })),
    // endregion
    // region Jokers 081-085 (missing : Dyscalculie, Pareidolie)
    CERTIFICATE("Certificate", "When the round begins, add a random playing card with a random seal and enhancement to your hand.", Rarity.UNCOMMON, 6, b -> b.on(Trigger.ON_ROUND_START,
            (run, self) -> {
                RandomGenerator g = gen(run, RngSource.MISC);
                DeckCard c = new DeckCard(Rank.values()[g.nextInt(Rank.values().length)], DeckCard.Suit.values()[g.nextInt(4)]);
                c.apply(Seal.values()[g.nextInt(Seal.values().length)]);
                c.apply(Enhancement.values()[g.nextInt(Enhancement.values().length)]);
                run.addCardToHand(c);
            })),
    VAMPIRE("Vampire", "Gains X0.1 Mult per scoring Enhanced card played, removing the enhancement.", Rarity.UNCOMMON, 7, b -> b
            .state(self -> "Currently : X" + (new BigDecimal("1").add(new BigDecimal("0.1").multiply(BigDecimal.valueOf(self.getCounter())))) + " Mult")
            .on(Trigger.ON_SCORED_CARD, (run, self) -> {
                DeckCard c = scored(run);
                if (c != null && c.getEnhancement() != null) { self.addCounter(1); c.remove(c.getEnhancement()); }
            })
            .on(Trigger.ON_HAND_PLAYED, (run, self) -> run.getScoring().multiplyMult(onePlus("0.1", self.getCounter())))),
    DRIVERS_LICENSE("Driver's License", "X3 Mult if you have at least 16 Enhanced cards in your full deck.", Rarity.RARE, 7, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> {
                int enhanced = 0;
                for (DeckCard c : run.getDeck()) if (c.getEnhancement() != null) enhanced++;
                if (enhanced >= 16) run.getScoring().multiplyMult(x("3"));
            })),
    DYSCALCULIE("Dyscalculie", "Each numbered card also counts as the numbered rank above (Ace as 2); face cards are unaffected. Applies to hands and rank-based Jokers alike.", Rarity.UNCOMMON, 7, b -> b.trait(JokerTrait.DYSCALCULIA)),
    PAREIDOLIA("Pareidolia", "All cards are considered face cards.", Rarity.UNCOMMON, 5, b -> b.trait(JokerTrait.PAREIDOLIA)),
    // endregion
    // region Jokers 086-090 (missing : Smeared Joker, Four Fingers, Superposition, Shortcut, Oops! All 6s)
    SMEARED_JOKER("Smeared Joker", "Hearts and Diamonds count as one suit; Spades and Clubs count as one suit.", Rarity.UNCOMMON, 7, b -> b.trait(JokerTrait.SMEARED)),
    FOUR_FINGERS("Four Fingers", "All Flushes and Straights can be made with 4 cards.", Rarity.UNCOMMON, 7, b -> b.trait(JokerTrait.FOUR_FINGERS)),
    SUPERPOSITION("Superposition", "Creates a Tarot card if the played hand contains an Ace and a Straight (wrap-around allowed).", Rarity.COMMON, 4, b -> b.on(Trigger.ON_HAND_PLAYED, (run, self) -> {
                HandType type = run.getScoring().getHand().type();
                boolean straight = type == HandType.STRAIGHT || type == HandType.STRAIGHT_FLUSH;
                boolean ace = run.getScoring().getScoringCards().stream().anyMatch(c -> countsAs(run, c, Rank.ACE));
                if (straight && ace) run.createConsumable(Tarots.random(gen(run, RngSource.TAROT_GENERATION)).spec());
            })),
    SHORTCUT("Shortcut", "Allows Straights to be made with gaps of 1 rank.", Rarity.UNCOMMON, 7, b -> b.trait(JokerTrait.SHORTCUT)),
    OOPS_ALL_6S("Oops! All 6s", "Doubles all listed probabilities.", Rarity.UNCOMMON, 4, b -> b.trait(JokerTrait.PROBABILITY_DOUBLER)),
    // endregion
    // region Jokers 091-095 (missing : Splash)
    SPLASH("Splash", "Every played card counts in scoring; cards that wouldn't count are retriggered once.", Rarity.COMMON, 7, b -> b.trait(JokerTrait.SPLASH)),
    HANGING_CHAD("Hanging Chad", "Retriggers the first scoring card 2 additional times.", Rarity.COMMON, 4, b -> b.retriggerPlayed((run, self, card) -> {
        int hand = run.getStats().getTotalHandsPlayed();
        if (self.getCounter() != hand) { self.setCounter(hand); return 2; }
        return 0;
    })),
    DUSK("Dusk", "Retriggers all played cards in the final hand of the round.", Rarity.UNCOMMON, 5, b -> b.retriggerPlayed(
            (run, self, card) -> (run.getRound() != null && run.getRound().getHandsRemaining() == 1) ? 1 : 0)),
    HACK("Hack", "Retriggers each played 2, 3, 4, or 5.", Rarity.UNCOMMON, 6, b -> b.retriggerPlayed(
            (run, self, card) -> anyRank(run, card, r -> r == Rank.TWO || r == Rank.THREE || r == Rank.FOUR || r == Rank.FIVE) ? 1 : 0)),
    SOCK_AND_BUSKIN("Sock and Buskin", "Retriggers all played face cards.", Rarity.UNCOMMON, 6, b -> b.retriggerPlayed(
            (run, self, card) -> run.isFaceCard(card) ? 1 : 0)),
    // endregion
    // region Jokers 096-100
    MIME("Mime", "Retriggers all cards held in hand.", Rarity.UNCOMMON, 5, b -> b.retriggerHeld((run, self, card) -> 1)),
    BUSINESS_CARD("Business Card", "Played face cards have a 1 in 2 chance to give $2 when scored.", Rarity.COMMON, 4, b -> b.on(Trigger.ON_SCORED_CARD,
            (run, self) -> { DeckCard c = scored(run); if (c != null && run.isFaceCard(c) && run.roll(RngSource.MISC, 1, 2)) run.addMoney(2); })),
    RESERVED_PARKING("Reserved Parking", "Each face card held in hand has a 1 in 2 chance to give $1.", Rarity.COMMON, 6, b -> b.on(Trigger.ON_HELD_CARD,
            (run, self) -> { DeckCard c = scored(run); if (c != null && run.isFaceCard(c) && run.roll(RngSource.MISC, 1, 2)) run.addMoney(2); })),
    TO_DO_LIST("To Do List", "Earn $4 if the played poker hand is a set type (changes at end of round).", Rarity.COMMON, 4, b -> b
            .state(self -> "Listed hand this round: " + handTypeName(self.getCounter()))
            .on(Trigger.ON_ROUND_START, (run, self) -> self.setCounter(gen(run, RngSource.MISC).nextInt(HandType.values().length)))
            .on(Trigger.ON_HAND_PLAYED, (run, self) -> { if (run.getScoring().getHand().type().ordinal() == self.getCounter()) run.addMoney(4); })),
    STARGAZING("Stargazing", "Earn $3 if played hand type is the same as last played by any other player.", Rarity.COMMON, 4, b -> b.trait(JokerTrait.SEAT_COUPLING).on(Trigger.ON_HAND_PLAYED, (run, self) -> {
                Match m = run.getMatch();
                if (m == null) return;
                var mine = run.getScoring().getHand().type();
                for (Player p : m.getPlayers()) {
                    if (p.run() == run) continue;
                    if (p.run().getStats().getLastHandType() == mine) { run.addMoney(3); return; }
                }
            })),
    // endregion
    // region Jokers 101-105
    MAIL_IN_REBATE("Mail-In Rebate", "Earn $4 for each discarded card of a set rank (changes every round).", Rarity.COMMON, 4, b -> b
            .state(self -> "Rebate rank this round: " + rankName(self.getCounter()))
            .on(Trigger.ON_ROUND_START, (run, self) -> self.setCounter(gen(run, RngSource.MISC).nextInt(Rank.values().length)))
            .on(Trigger.ON_HAND_DISCARDED, (run, self) -> {
                int n = 0;
                for (DeckCard c : run.getLastDiscarded()) if (countsAs(run, c, Rank.values()[self.getCounter()])) n++;
                if (n > 0) run.addMoney(4 * n);
            })),
    FACELESS_JOKER("Faceless Joker", "Earn $5 if 3 or more face cards are discarded at once.", Rarity.COMMON, 4, b -> b.on(Trigger.ON_HAND_DISCARDED,
            (run, self) -> {
                int faces = 0;
                for (DeckCard c : run.getLastDiscarded()) if (run.isFaceCard(c)) faces++;
                if (faces >= 3) run.addMoney(6);
            })),
    DELAYED_GRATIFICATION("Delayed Gratification", "Earn $2 per unused discard at the end of the round.", Rarity.COMMON, 4, b -> b.on(Trigger.ON_ROUND_END,
            (run, self) -> { if (run.getRound() != null) run.addMoney(2 * run.getRound().getDiscardsRemaining()); })),
    MATADOR("Matador", "Earn $8 if the played hand triggers the Boss Blind ability.", Rarity.UNCOMMON, 7, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> { if (run.bossTriggeredThisPlay()) run.addMoney(8); })),
    TRADING_CARD("Trading Card", "If the first discard of the round is a single card, destroy it and earn $3.", Rarity.UNCOMMON, 6, b -> b.on(Trigger.ON_HAND_DISCARDED,
            (run, self) -> {
                if (run.getStats().getDiscardsUsedThisRound() == 1 && run.getLastDiscarded().size() == 1) {
                    run.destroyDeckCards(run.getLastDiscarded());
                    run.addMoney(3);
                }
            })),
    // endregion
    // region Jokers 106-110
    YELLOW_JOKER("Yellow Joker", "Earn $4 at end of round.", Rarity.COMMON, 6, b -> b.on(Trigger.ON_ROUND_END,
            (run, self) -> run.addMoney(4))),
    SATELLITE("Satellite", "Gives $1 for every three levels of your most-played hand.", Rarity.UNCOMMON, 6, b -> b.on(Trigger.ON_ROUND_END,
            (run, self) -> {
                HandType most = run.getStats().getMostPlayedHand();
                if (most != null) run.addMoney(run.getHandLevels().levelOf(most) / 3);
            })),
    ROCKET("Rocket", "Earn $1 at end of round; payout increases by $2 when a Boss Blind is defeated.", Rarity.UNCOMMON, 6, b -> b
            .state(self -> "Earns $" + (1 + 2 * self.getCounter()) + " at end of round")
            .on(Trigger.ON_ROUND_END, (run, self) -> run.addMoney(1 + 2 * self.getCounter()))
            .on(Trigger.ON_BOSS_DEFEATED, (run, self) -> self.addCounter(1))),
    TO_THE_MOON("To the Moon", "Earn an extra $1 of interest for every $5 you have at end of round.", Rarity.UNCOMMON, 5, b -> b),   // passive: extra interest applied in RoundSettlement (checked by ownership)
    INVESTMENT("Investment", "Earn $1 at end of round for every $5 of Joker sell value you own.", Rarity.COMMON, 5, b -> b.on(Trigger.ON_ROUND_END,
            (run, self) -> {
                int value = 0;
                for (JokerCard j : run.getJokers()) value += j.getSellValue();
                if (value >= 5) run.addMoney(value / 5);
            })),
    // endregion
    // region Jokers 111-115 (missing : Curator)
    EGG("Egg", "Gains $3 of sell value at end of round.", Rarity.COMMON, 4, b -> b.on(Trigger.ON_ROUND_END,
            (run, self) -> self.setSellValue(self.getSellValue() + 3))),
    GIFT_CARD("Gift Card", "Adds $1 of sell value to every Joker and consumable at end of round.", Rarity.UNCOMMON, 6, b -> b.on(Trigger.ON_ROUND_END,
            (run, self) -> {
                for (JokerCard j : run.getJokers()) j.setSellValue(j.getSellValue() + 1);
                for (ConsumableCard cc : run.getConsumables()) cc.setSellValue(cc.getSellValue() + 1);
            })),
    CREDIT_CARD("Credit Card", "Go up to -$20 in debt. Gains +1 Mult per $1 spent while in debt; resets when out of debt.", Rarity.COMMON, 2, b -> b
            .state(self -> "Currently: +" + self.getCounter() + " Mult")
            .debtAllowance(20)
            .on(Trigger.ON_SPEND, (run, self) -> self.setCounter(self.getCounter() + run.getLastInDebtSpend()))
            .on(Trigger.ON_EARN, (run, self) -> { if (run.getMoney() >= 0) self.setCounter(0); })
            .on(Trigger.ON_HAND_PLAYED, (run, self) -> run.getScoring().addMult(self.getCounter()))),
    LOYALTY_CARD("Loyalty Card", "Every 4th purchase in the shop is free.", Rarity.UNCOMMON, 7, b -> b
            .state(self -> ((self.getCounter() + 1) % 4 == 0)
                    ? "FREE !" : (4 - (self.getCounter() + 1) % 4) + " buys left")
            .on(Trigger.ON_PURCHASE_PRICING,
                    (run, self) -> { if ((self.getCounter() + 1) % 4 == 0) run.makePurchaseFree(); })
            .on(Trigger.ON_BOUGHT,
                    (run, self) -> self.setCounter(self.getCounter() + 1))),
    CURATOR("Curator", "Standard Packs are always free.", Rarity.UNCOMMON, 7, b -> b.on(Trigger.ON_PURCHASE_PRICING, (run, self) -> {
                if (run.getPurchaseItem() instanceof model.items.packs.BoosterPack p
                        && p.kind() == model.items.packs.PackKind.STANDARD) run.makePurchaseFree();
            })),
    // endregion
    // region Jokers 116-120 (missing : Copyright)
    COPYRIGHT("Copyright", "Earn $2 each round for each Joker you own that another player also owns.", Rarity.COMMON, 4, b -> b.trait(JokerTrait.SEAT_COUPLING).on(Trigger.ON_ROUND_END, (run, self) -> {
                Match m = run.getMatch();
                if (m == null) return;
                int shared = 0;
                for (JokerCard mine : run.getJokers()) {
                    boolean otherOwns = false;
                    for (Player p : m.getPlayers()) {
                        if (p.run() == run) continue;
                        for (JokerCard theirs : p.run().getJokers()) if (theirs.getSpec() == mine.getSpec()) { otherOwns = true; break; }
                        if (otherOwns) break;
                    }
                    if (otherOwns) shared++;
                }
                if (shared > 0) run.addMoney(2 * shared);
            })),
    ROBIN_HOOD("Robin Hood", "Earn $6 at the end of the round if you are the poorest player.", Rarity.COMMON, 4, b -> b.trait(JokerTrait.SEAT_COUPLING).on(Trigger.ON_ROUND_END,
            (run, self) -> {
                Match m = run.getMatch();
                if (m == null) return;
                for (Player p : m.getPlayers()) if (p.run() != run && p.run().getMoney() < run.getMoney()) return;
                run.addMoney(6);   // no one is strictly poorer: this seat is (tied) poorest
            })),
    ASTRONOMER("Astronomer", "All Planet cards and Celestial Packs in the shop are free.", Rarity.UNCOMMON, 8, b -> b.on(Trigger.ON_PURCHASE_PRICING, (run, self) -> {
                var item = run.getPurchaseItem();
                boolean planet = item instanceof ConsumableCard c && c.getSpec().getType() == ConsumableType.PLANET;
                boolean celestial = item instanceof model.items.packs.BoosterPack p && p.kind() == model.items.packs.PackKind.CELESTIAL;
                if (planet || celestial) run.makePurchaseFree();
            })),
    SPACE_JOKER("Space Joker", "1 in 4 chance to upgrade the level of the played poker hand.", Rarity.UNCOMMON, 5, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> { if (run.roll(RngSource.MISC, 1, 4)) run.levelUpHand(run.getScoring().getHand().type()); })),
    CONSTELLATION("Constellation", "Gains X0.1 Mult every time a Planet card is used.", Rarity.UNCOMMON, 6, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> run.getScoring().multiplyMult(onePlus("0.1", run.getStats().getConsumablesUsed(ConsumableType.PLANET))))),
    // endregion
    // region Jokers 121-125
    BURNT_JOKER("Burnt Joker", "Upgrades the level of the first discarded poker hand each round.", Rarity.RARE, 8, b -> b.on(Trigger.ON_HAND_DISCARDED,
            (run, self) -> {
                if (run.getStats().getDiscardsUsedThisRound() == 1 && !run.getLastDiscarded().isEmpty())
                    run.levelUpHand(new HandEvaluator().evaluate(run.getLastDiscarded()).type());
            })),
    HALLUCINATION("Hallucination", "1 in 2 chance to create a Tarot card when any Booster Pack is opened.", Rarity.COMMON, 4, b -> b.on(Trigger.ON_PACK_OPENED, (run, self) -> {
                if (run.roll(RngSource.MISC, 1, 2)) run.createConsumable(Tarots.random(gen(run, RngSource.TAROT_GENERATION)).spec());
            })),
    CARTOMANCER("Cartomancer", "Create a Tarot card when a Blind is selected.", Rarity.UNCOMMON, 6, b -> b.on(Trigger.ON_ROUND_START,
            (run, self) -> run.createConsumable(Tarots.random(gen(run, RngSource.TAROT_GENERATION)).spec()))),
    FORTUNE_TELLER("Fortune Teller", "+1 Mult per Tarot card used this run.", Rarity.COMMON, 6, b -> b
            .state((self, info) -> "Currently : +" + info.consumablesUsed(ConsumableType.TAROT) + " Mult")
            .on(Trigger.ON_HAND_PLAYED,
            (run, self) -> run.getScoring().addMult(run.getStats().getConsumablesUsed(ConsumableType.TAROT)))),
    VAGABOND("Vagabond", "Creates a Tarot card if a hand is played with $4 or less.", Rarity.RARE, 8, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> { if (run.getMoney() <= 4) run.createConsumable(Tarots.random(gen(run, RngSource.TAROT_GENERATION)).spec()); })),
    // endregion
    // region Jokers 126-130
    ICE_CREAM("Ice Cream", "+100 Chips, -5 Chips for every hand played.", Rarity.COMMON, 5, b -> b
            .state(self -> "Chips left: " + Math.max(0, 100 - 5 * self.getCounter()))
            .on(Trigger.ON_HAND_PLAYED,
                    (run, self) -> {
                        run.getScoring().addChips(Math.max(0, 100 - 5 * self.getCounter()));
                        self.addCounter(1);
                    })),
    POPCORN("Popcorn", "+20 Mult, -4 Mult per round played.", Rarity.COMMON, 5, b -> b
            .state(self -> "Mult left: " + Math.max(0, 20 - 4 * self.getCounter()))
            .on(Trigger.ON_HAND_PLAYED, (run, self) -> run.getScoring().addMult(Math.max(0, 20 - 4 * self.getCounter())))
            .on(Trigger.ON_ROUND_END, (run, self) -> self.addCounter(1))),
    MUSHROOM("Mushroom", "Gains +4 Mult every 3 cards discarded; self-destructs at +32 Mult.", Rarity.COMMON, 5, b -> b
            .state(self -> "Currently : +" + (self.getCounter() * 5 / 3) + " Chips, and +" + (self.getCounter() / 3) + " Mult")
            .on(Trigger.ON_HAND_DISCARDED, (run, self) -> self.addCounter(run.getLastDiscarded().size()))
            .on(Trigger.ON_HAND_PLAYED, (run, self) -> {
                run.getScoring().addMult(self.getCounter() * 5 / 3);
                run.getScoring().addMult(self.getCounter() / 3);
                if (self.getCounter() >= 32) run.destroyJoker(self);
            })),
    GROS_MICHEL("Gros Michel", "+15 Mult; 1 in 6 chance to be destroyed at end of round.", Rarity.COMMON, 5, b -> b
            .on(Trigger.ON_HAND_PLAYED, (run, self) -> run.getScoring().addMult(15))
            .on(Trigger.ON_ROUND_END, (run, self) -> { if (run.roll(RngSource.MISC, 1, 6)) run.destroyJoker(self); })),
    CAVENDISH("Cavendish", "X3 Mult; 1 in 1000 chance to be destroyed at end of round.", Rarity.COMMON, 4, b -> b
            .on(Trigger.ON_HAND_PLAYED, (run, self) -> run.getScoring().multiplyMult(x("3")))
            .on(Trigger.ON_ROUND_END, (run, self) -> { if (run.roll(RngSource.MISC, 1, 1000)) self.setCounter(1); })
            .on(Trigger.ON_ROUND_START, (run, self) -> { if (self.getCounter() == 1) run.destroyJoker(self); })),
    // endregion
    // region Jokers 131-135 (missing : Turtle Bean, Diet Cola)
    RAMEN("Ramen", "X2 Mult, loses X0.01 Mult per card discarded.", Rarity.UNCOMMON, 6, b -> b
            .state(self -> "Current mult: X" + new BigDecimal("2").subtract(new BigDecimal("0.01").multiply(BigDecimal.valueOf(self.getCounter()))).max(BigDecimal.ZERO))
            .on(Trigger.ON_HAND_DISCARDED, (run, self) -> self.addCounter(run.getLastDiscarded().size()))
            .on(Trigger.ON_HAND_PLAYED, (run, self) -> {
                BigDecimal mult = x("2").subtract(new BigDecimal("0.01").multiply(BigDecimal.valueOf(self.getCounter())));
                run.getScoring().multiplyMult(mult.max(BigDecimal.ZERO));
            })),
    STRAWBERRY("StrawBerry", "X3 Mult; consumed if more than one hand is played.", Rarity.UNCOMMON, 6, b -> b
            .on(Trigger.ON_HAND_PLAYED, (run, self) -> {
                run.getScoring().multiplyMult(3L);
                self.addCounter(1);
                if (self.getCounter() == 2) run.destroyJoker(self);
            })
            .on(Trigger.ON_ROUND_END, (run, self) -> self.setCounter(0))),
    TURTLE_BEAN("Turtle Bean", "+5 hand size, reduced by 1 each round.", Rarity.UNCOMMON, 6, b -> b
            .state(self -> (5-self.getCounter()) + " rounds remaining")
            .on(Trigger.ON_BOUGHT, (run, self) -> run.setHandSize(run.getHandSize()+5))
            .on(Trigger.ON_ROUND_END, (run, self) -> {
                self.addCounter(1);
                run.setHandSize(run.getHandSize() - 1);
                if (self.getCounter() == 5) run.destroyJoker(self);
            })),
    SELTZER("Seltzer", "Retriggers all played cards for the next 10 hands.", Rarity.UNCOMMON, 6, b -> b
            .state(self -> self.getCounter() + " uses remaining")
            .on(Trigger.ON_BOUGHT, (run, self) -> self.setCounter(10))
            .on(Trigger.ON_HAND_PLAYED, (run, self) -> {
                self.addCounter(-1);
                if(self.getCounter() == 0) run.destroyJoker(self);
            })
            .retriggerPlayed((run, self, card) -> 1)),
    DIET_COLA("Diet Cola", "After 1 round, self-destructs and creates a Double Tag.", Rarity.UNCOMMON, 6, b -> b.on(Trigger.ON_ROUND_END, (run, self) -> {
                run.grantTag(model.game.tags.SkipTag.DOUBLE_TAG);   // a Double Tag copies the next tag the seat takes
                run.destroyJoker(self);
            })),
    // endregion
    // region Jokers 136-140 (missing : Chef Joker)
    CHEF_JOKER("Chef Joker", "Gains X1 Mult every time a food Joker is consumed.", Rarity.RARE, 8, b -> b
            .state(self -> "Currently : X" + (1 + self.getCounter()) + " Mult")
            .on(Trigger.ON_JOKER_DESTROYED, (run, self) -> { JokerCard d = run.getDestroyedJoker(); if (d != null && isFood(d)) self.addCounter(1); })
            .on(Trigger.ON_HAND_PLAYED, (run, self) -> { if (self.getCounter() > 0) run.getScoring().multiplyMult(onePlus("1", self.getCounter())); })),
    MERCHANT("Merchant", "+2 consumable slots.", Rarity.COMMON, 4, b -> b
            .on(Trigger.ON_BOUGHT, (run, self) -> run.setConsumableSlots(run.getConsumableSlots() + 2))
            .on(Trigger.ON_SOLD, (run, self) -> run.setConsumableSlots(run.getConsumableSlots() - 2))),
    THE_VOID("The Void", "+1 Joker slot.", Rarity.UNCOMMON, 6, b -> b
            .on(Trigger.ON_BOUGHT, (run, self) -> run.setJokerSlots(run.getJokerSlots() + 1))
            .on(Trigger.ON_SOLD, (run, self) -> run.setJokerSlots(run.getJokerSlots() - 1))),
    RIFF_RAFF("Riff-Raff", "When Blind is selected, create 2 Common Jokers.", Rarity.COMMON, 6, b -> b
            .on(Trigger.ON_ROUND_START, (run, self) -> {
                for (int k = 0; k < 2; k++)
                    run.createJoker(Jokers.randomOfRarity(Rarity.COMMON, gen(run, RngSource.JOKER_GENERATION)).make());
            })),
    DRUNKARD("Drunkard", "+1 discard each round.", Rarity.COMMON, 4, b -> b
            .on(Trigger.ON_BOUGHT, (run, self) -> run.setBaseDiscards(run.getBaseDiscards() + 2))
            .on(Trigger.ON_SOLD, (run, self) -> run.setBaseDiscards(run.getBaseDiscards() - 2))),
    // endregion
    // region Jokers 141-145 (missing : Mr_Bones)
    MERRY_ANDY("Merry Andy", "+3 discards each round, -1 hand size.", Rarity.UNCOMMON, 7, b -> b
            .on(Trigger.ON_BOUGHT, (run, self) -> {
                run.setBaseDiscards(run.getBaseDiscards() + 2);
                run.setHandSize(run.getHandSize() - 1);
            })
            .on(Trigger.ON_SOLD, (run, self) -> {
                run.setBaseDiscards(run.getBaseDiscards() - 2);
                run.setHandSize(run.getHandSize() + 1);
            })),
    BURGLAR("Burglar", "When Blind selected, gain +3 Hands and lose all discards.", Rarity.UNCOMMON, 6, b -> b
            .on(Trigger.ON_BOUGHT, (run, self) -> {
                run.setBaseHands(run.getBaseHands() + 3);
                self.addCounter(run.getBaseDiscards());
                run.setBaseDiscards(0);
            })
            .on(Trigger.ON_SOLD, (run, self) -> {
                run.setBaseHands(run.getBaseHands() - 3);
                run.setBaseDiscards(run.getBaseDiscards() + self.getCounter());
            })),
    JUGGLER("Juggler", "+1 hand size.", Rarity.COMMON, 4, b -> b
            .on(Trigger.ON_BOUGHT, (run, self) -> run.setHandSize(run.getHandSize() + 1))
            .on(Trigger.ON_SOLD, (run, self) -> run.setHandSize(run.getHandSize() - 1))),
    TROUBADOUR("Troubadour", "+2 hand size, -1 hand per round.", Rarity.UNCOMMON, 6, b -> b
            .on(Trigger.ON_BOUGHT, (run, self) -> {
                run.setHandSize(run.getHandSize() + 2);
                run.setBaseHands(run.getBaseHands() - 1);
            })
            .on(Trigger.ON_SOLD, (run, self) -> {
                run.setHandSize(run.getHandSize() - 2);
                run.setBaseHands(run.getBaseHands() + 1);
            })),
    MR_BONES("Mr. Bones", "Prevents a blind loss if chips scored are at least 25% of the requirement, then self-destructs.", Rarity.UNCOMMON, 5, b -> b.trait(JokerTrait.PREVENTS_LOSS)),
    // endregion
    // region Jokers 146-150 (missing : Chaos The Clown)
    LUCHADOR("Luchador", "Sell this card to disable the current Boss Blind for yourself only.", Rarity.UNCOMMON, 5, b -> b.on(Trigger.ON_SOLD,
            (run, self) -> run.disableBossForRound())),
    CHAOS_THE_CLOWN("Chaos the Clown", "1 free reroll per shop.", Rarity.COMMON, 4, b -> b),   // passive: the shop's first reroll is free while owned (handled in Shop)
    STUNTMAN("Stuntman", "+250 Chips, -2 hand size.", Rarity.RARE, 7, b -> b
            .on(Trigger.ON_HAND_PLAYED, (run, self) -> run.getScoring().addChips(250))
            .on(Trigger.ON_BOUGHT, (run, self) -> run.setHandSize(run.getHandSize() - 2))
            .on(Trigger.ON_SOLD, (run, self) -> run.setHandSize(run.getHandSize() + 2))),
    WEE_JOKER("Wee Joker", "Gains +8 Chips when each played 2 is scored.", Rarity.RARE, 8, b -> b
            .state(self -> "Currently : +" + self.getCounter() + " Chips")
            .on(Trigger.ON_SCORED_CARD, (run, self) -> { DeckCard c = scored(run); if (c != null && countsAs(run, c, Rank.TWO)) self.addCounter(8); })
            .on(Trigger.ON_HAND_PLAYED, (run, self) -> run.getScoring().addChips(self.getCounter()))),
    BASEBALL_CARD("Baseball Card", "Uncommon Jokers each give X1.5 Mult.", Rarity.RARE, 8, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> {
                for (JokerCard j : run.getJokers()) if (j.getSpec().getRarity() == Rarity.UNCOMMON) run.getScoring().multiplyMult(x("1.5"));
            })),
    // endregion
    // region Jokers 151-155
    OBELISK("Obelisk", "Gains X0.2 Mult per consecutive hand that differs from your last three hand types.", Rarity.RARE, 8, b -> b
            .state(self -> "Currently : X" + onePlus("0.2", self.getCounter()) + " Mult")
            .on(Trigger.ON_HAND_PLAYED,
            (run, self) -> {
                List<HandType> recent = run.getStats().getRecentHands(3);
                HandType cur = recent.get(0);
                boolean repeat = (recent.size() > 1 && recent.get(1) == cur) || (recent.size() > 2 && recent.get(2) == cur);
                if (repeat) self.setCounter(0); else self.addCounter(1);
                run.getScoring().multiplyMult(onePlus("0.2", self.getCounter()));
            })),
    CAMPFIRE("Campfire", "Gains X0.25 Mult per card sold; resets when a Boss Blind is defeated.", Rarity.RARE, 9, b -> b
            .state((self, info) -> "Currently : X" + onePlus("0.25", info.cardsSold() - self.getCounter()) + " Mult")
            .on(Trigger.ON_HAND_PLAYED, (run, self) -> run.getScoring().multiplyMult(onePlus("0.25", run.getStats().getCardsSold() - self.getCounter())))
            .on(Trigger.ON_BOSS_DEFEATED, (run, self) -> self.setCounter(run.getStats().getCardsSold()))),
    ADVENTURER("Adventurer", "X0.5 Mult for each Joker you own that no other player owns.", Rarity.RARE, 8, b -> b.trait(JokerTrait.SEAT_COUPLING).on(Trigger.ON_HAND_PLAYED,
            (run, self) -> {
                Match m = run.getMatch();
                int unique = 0;
                for (JokerCard mine : run.getJokers()) if (!otherPlayerOwns(m, run, mine)) unique++;
                if (unique > 0) run.getScoring().multiplyMult(onePlus("0.5", unique));
            })),
    DNA("DNA", "If the first hand of the round is a single card, add a permanent copy to the deck and draw it.", Rarity.RARE, 8, b -> b.on(Trigger.ON_SCORED_CARD,
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
    ALCHEMIST("Alchemist", "On shop exit, gives all held consumables a random edition.", Rarity.RARE, 7, b -> b.on(Trigger.ON_SHOP_END,
            (run, self) -> {
                for (ConsumableCard cc : run.getConsumables())
                    if (cc.getEdition() == null)
                        cc.apply(switch (gen(run, RngSource.CARD_EDITION).nextInt(3)) {
                            case 0 -> Edition.FOIL; case 1 -> Edition.HOLOGRAPHIC; default -> Edition.POLYCHROME; });
            })),
    // endregion
    // region Jokers 156-160 (missing : The Mimic, Espionnage)
    INVISIBLE_JOKER("Invisible Joker", "After 2 rounds, sell this card to duplicate a random Joker.", Rarity.RARE, 8, b -> b
            .state(self -> self.getCounter() + "/2")
            .on(Trigger.ON_ROUND_END, (run, self) -> self.addCounter(1))
            .on(Trigger.ON_SOLD, (run, self) -> {
                if(self.getCounter() >= 2) {
                    List<JokerCard> js = run.getJokers();
                    js.remove(self);
                    run.createJoker(js.get(gen(run, RngSource.MISC).nextInt(js.size())));
                }
            })
    ),
    THE_MIMIC("The Mimic", "Copies the ability of the Joker in the same slot of the leading player.", Rarity.RARE, 10, b -> {
                // Register the same delegate on every trigger so the copy is the WHOLE joker (Cloud 9's end-of-round
                // payout, a scoring joker's mult, a shop reaction, ...) — not just its played-hand part.
                for (Trigger t : Trigger.values()) b.on(t, (run, self) -> mimicCopy(run, self, t));
                return b.trait(JokerTrait.SEAT_COUPLING);
            }),
    ESPIONNAGE("Espionnage", "The Joker in this slot is debuffed for every player above you in the standings.", Rarity.RARE, 8, b -> b.trait(JokerTrait.SEAT_COUPLING)
            .on(Trigger.ON_BLIND_SETTLED, (run, self) -> {
                Match m = run.getMatch();
                if (m == null || run.getPlayerId() == null) return;
                int slot = run.getJokers().indexOf(self);
                if (slot < 0) return;
                for (PlayerId above : m.seatsAbove(run.getPlayerId()))   // debuff the same slot on each seat above, next round
                    m.getRun(above).getAfflictions().armJokerDebuff(slot);
            })),
    BLUEPRINT("Blueprint", "Retriggers the Joker to the right.", Rarity.RARE, 10, b -> b
            .on(Trigger.ON_HAND_PLAYED, (run, self) -> {
                List<JokerCard> js = run.getJokers();
                int i = js.indexOf(self);
                if (i >= 0 && i + 1 < js.size()) run.getScoring().retriggerJoker(js.get(i + 1));
            })),
    BRAINSTORM("Brainstorm", "Retriggers the leftmost Joker.", Rarity.RARE, 10, b -> b.on(Trigger.ON_HAND_PLAYED,
            (run, self) -> {
                List<JokerCard> js = run.getJokers();
                if (!js.isEmpty() && js.get(0) != self) run.getScoring().retriggerJoker(js.get(0));
            })),
    // endregion
    // region Jokers 161-165 (missing : CANIO)
    CANIO("Canio", "Gains X1 Mult when a face card is destroyed; X0.25 Mult when a numbered card is destroyed.", Rarity.LEGENDARY, 20, b -> b
            .state(self -> "Currently : X" + onePlus("0.25", self.getCounter()) + " Mult")
            .on(Trigger.ON_CARD_DESTROYED, (run, self) -> {
                DeckCard c = run.getDestroyedCard();
                if (c != null) self.addCounter(run.isFaceCard(c) ? 4 : 1);   // +X1 per face card, +X0.25 per other (quarter-units)
            })
            .on(Trigger.ON_HAND_PLAYED, (run, self) -> {
                if (self.getCounter() > 0) run.getScoring().multiplyMult(onePlus("0.25", self.getCounter()));
            })),
    YORICK("Yorick", "Gains X1 Mult every 23 cards discarded.", Rarity.LEGENDARY, 20, b -> b
            .on(Trigger.ON_HAND_PLAYED, (run, self) -> run.getScoring().multiplyMult(onePlus("1", run.getStats().getCardsDiscarded() / 23)))),
    TRIBOULET("Triboulet", "Played Kings and Queens each give X2 Mult when scored.", Rarity.LEGENDARY, 20, b -> b
            .on(Trigger.ON_SCORED_CARD, (run, self) -> { DeckCard c = scored(run); if (c != null && (c.getRank() == Rank.KING || c.getRank() == Rank.QUEEN)) run.getScoring().multiplyMult(x("2")); })),
    CHICOT("Chicot", "Disables the effect of every Boss Blind.", Rarity.LEGENDARY, 20, b -> b
            .trait(JokerTrait.DISABLES_BOSS)),
    PERKEO("Perkeo", "Creates a copy of 1 random consumable in your possession at end of shop.", Rarity.LEGENDARY, 20, b -> b
            .on(Trigger.ON_SHOP_END, (run, self) -> {
                List<ConsumableCard> cs = run.getConsumables();
                if (!cs.isEmpty()) run.createConsumable(cs.get(gen(run, RngSource.MISC).nextInt(cs.size())).getSpec());
            }));
    // endregion

    private final Rarity rarity;
    private final JokerSpec spec;

    Jokers(String displayName, String description, Rarity rarity, int cost, UnaryOperator<JokerSpec.Builder> define) {
        this.rarity = rarity;
        this.spec = define.apply(JokerSpec.named(displayName, rarity).cost(cost).description(description)).build();
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

    // --- tooltip state helpers (feed JokerSpec.stateOf; keep these words readable, they face the player) ---

    /** "King" from a {@code Rank} ordinal. */
    private static String rankName(int ord) {
        Rank[] ranks = Rank.values();
        return ord >= 0 && ord < ranks.length ? ranks[ord].displayName() : "?";
    }

    /** "Spades" from the matchesSuit index — NOTE: 0=spade,1=heart,2=club,3=diamond, not Suit enum order. */
    private static String suitIndexName(int idx) {
        DeckCard.Suit suit = switch (idx) {
            case 0 -> DeckCard.Suit.SPADES; case 1 -> DeckCard.Suit.HEARTS;
            case 2 -> DeckCard.Suit.CLUBS;  default -> DeckCard.Suit.DIAMONDS;
        };
        return suit.displayName();
    }

    /** "Four of a Kind" from a {@code HandType} ordinal, via the model's single naming authority. */
    private static String handTypeName(int ord) {
        return ord >= 0 && ord < HandType.values().length ? HandType.values()[ord].displayName() : "?";
    }

    private static DeckCard scored(model.game.player.Run run) {
        return run.getScoring().getCurrentScoredCard();
    }

    /**
     * The Mimic's delegate: acts as the leading player's joker in the same board slot, applying that joker's effect
     * for {@code trigger} against this seat's run. Registered on every trigger, so it copies the whole ability.
     * Skips a debuffed target or itself; a copied Mimic/Blueprint finds no matching slot on this board and fizzles,
     * and no joker effect re-fires its own trigger, so there is no unbounded recursion.
     */
    private static void mimicCopy(model.game.player.Run run, JokerCard self, Trigger trigger) {
        Match m = run.getMatch();
        if (m == null) return;
        List<PlayerId> ranking = m.getStandings().ranking();
        if (ranking.isEmpty()) return;
        var leader = m.getRun(ranking.get(0));
        if (leader == run) return;                       // we lead — no one to mimic
        int slot = run.getJokers().indexOf(self);
        if (slot < 0) return;
        List<JokerCard> theirs = leader.getJokers();
        if (slot >= theirs.size()) return;
        JokerCard copied = theirs.get(slot);
        if (copied == self || copied.isDebuffed()) return;
        copied.trigger(trigger, run);                    // be that joker, against this seat's run
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

    /** Whether {@code j} is a "food" joker — one Chef Joker feeds on when it leaves the board. */
    private static boolean isFood(JokerCard j) { return Food.SPECS.contains(j.getSpec()); }

    /** The food-joker spec set, in a lazy holder so it is built after the enum constants exist. */
    private static final class Food {
        static final java.util.Set<JokerSpec> SPECS = java.util.Set.of(
                GROS_MICHEL.spec(), CAVENDISH.spec(), ICE_CREAM.spec(), POPCORN.spec(),
                RAMEN.spec(), TURTLE_BEAN.spec(), SELTZER.spec(), DIET_COLA.spec(), EGG.spec());
    }

    /** Whether {@code c} counts as {@code rank} — its own rank, or (under Dyscalculie) the numbered rank above. */
    private static boolean countsAs(model.game.player.Run run, DeckCard c, Rank rank) {
        return c.getRank() == rank
                || (run.hasActiveTrait(JokerTrait.DYSCALCULIA) && c.getRank().numberedAbove() == rank);
    }

    /** Whether any rank {@code c} counts as (its own, plus the numbered rank above under Dyscalculie) satisfies {@code p}. */
    private static boolean anyRank(model.game.player.Run run, DeckCard c, java.util.function.Predicate<Rank> p) {
        if (p.test(c.getRank())) return true;
        if (run.hasActiveTrait(JokerTrait.DYSCALCULIA)) {
            Rank above = c.getRank().numberedAbove();
            return above != null && p.test(above);
        }
        return false;
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

    /** The most frequent rank among {@code cards} (the set/trips rank of the played hand), or null if empty. */
    private static Rank dominantRank(List<DeckCard> cards) {
        Rank best = null;
        int bestCount = 0;
        for (DeckCard c : cards) {
            int count = 0;
            for (DeckCard d : cards) if (d.getRank() == c.getRank()) count++;
            if (count > bestCount) { bestCount = count; best = c.getRank(); }
        }
        return best;
    }

    /** Whether any seat other than {@code owner} holds a joker of {@code joker}'s spec (read-only cross-seat check). */
    private static boolean otherPlayerOwns(Match match, model.game.player.Run owner, JokerCard joker) {
        if (match == null) return false;
        for (Player p : match.getPlayers()) {
            if (p.run() == owner) continue;
            for (JokerCard theirs : p.run().getJokers())
                if (theirs.getSpec() == joker.getSpec()) return true;
        }
        return false;
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
