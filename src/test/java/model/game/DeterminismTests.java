package model.game;

import model.cards.Card;
import model.cards.DeckCard;
import model.cards.consumables.ConsumableCard;
import model.cards.jokers.JokerCard;
import model.cards.packs.BoosterPack;
import model.cards.vouchers.Voucher;
import model.game.player.BlindResult;
import model.game.player.PlayerId;
import model.game.player.Round;
import model.game.player.RoundOutcome;
import model.game.player.Run;
import model.game.scoring.HandEvaluator;
import model.game.scoring.HandType;
import model.game.shop.Shop;
import model.modifiers.Sticker;

import java.util.ArrayList;
import java.util.List;

/**
 * Full-match determinism harness — the guard on the project's core invariant: same seed, same decisions,
 * bit-identical outcomes, mirrored across seats. Three checks:
 *
 * <ul>
 *   <li><b>Replay determinism:</b> a scripted 7-ante match (plays, discards, voluntary finishes, purchases,
 *       rerolls, voucher redemptions) run twice from the same seed produces an identical state trace at every
 *       settlement barrier and shop.</li>
 *   <li><b>Seat mirroring:</b> within one run, two seats executing the identical script have identical state
 *       fingerprints at every barrier and identical shop offerings at every shop.</li>
 *   <li><b>Cross-player machinery:</b> the same two checks with Envy pinned as the sin, The Commons pinned as
 *       the boss, and an Envy joker swap each shop — the shared pool, the swap, and sin dispatch all under the
 *       determinism lens.</li>
 * </ul>
 *
 * <p>The driver is state-blind-legal: every action is guarded by reads (never exception control flow), and it
 * adapts to whatever boss the seed produces (Psychic's 5-card rule, Eye/Mouth type restrictions, Cerulean
 * Bell's forced card, shared discard pools), so it survives any seed. A different seed must produce a
 * different trace — asserted, so the fingerprint can't silently degenerate.
 */
public final class DeterminismTests {

    private static int failures = 0;
    private static final HandEvaluator EVAL = new HandEvaluator();

    // Coverage counters, reset per match: the harness asserts its own non-vacuity, so a future driver
    // regression that stops clearing blinds or buying cards fails loudly instead of testing nothing.
    private static int clearedBlinds, purchases, swaps;

    public static void main(String[] args) {
        // --- replay determinism + seat mirroring under default (seeded) policies ---
        List<String> first  = playMatch(4242L, MatchConfig.defaults(), false);
        check("coverage: blinds cleared under default policies", clearedBlinds >= 4);
        check("coverage: the shop path is exercised", purchases >= 2);
        List<String> second = playMatch(4242L, MatchConfig.defaults(), false);
        compareTraces("replay (default policies)", first, second);
        List<String> other = playMatch(4243L, MatchConfig.defaults(), false);
        check("a different seed produces a different trace", !first.equals(other));

        // --- the cross-player machinery under the same lens: Envy + The Commons + a swap every shop ---
        MatchConfig pinned = MatchConfig.defaults()
                .withSinSelector((ante, rng) -> Sin.ENVY)
                .withBossSelector((ante, rng, exclude) -> BossBlind.THE_COMMONS);
        List<String> envy1 = playMatch(9001L, pinned, true, true);
        check("coverage: stacked decks clear heavily", clearedBlinds >= 10);
        check("coverage: Envy swaps executed", swaps >= 3);
        List<String> envy2 = playMatch(9001L, pinned, true, true);
        compareTraces("replay (Envy + Commons + swaps)", envy1, envy2);

        System.out.println(failures == 0 ? "\nALL PASS" : "\n" + failures + " FAILURE(S)");
        if (failures != 0) System.exit(1);
    }

    // --- the scripted match ---

    /**
     * Plays a full match with the state-blind driver, asserting seat mirroring along the way, and returns the
     * state trace (a fingerprint at every barrier and every shop) for replay comparison.
     */
    private static List<String> playMatch(long seed, MatchConfig config, boolean envySwaps) {
        return playMatch(seed, config, envySwaps, false);
    }

    private static List<String> playMatch(long seed, MatchConfig config, boolean envySwaps, boolean stackedDecks) {
        Match match = Match.create(seed, List.of("A", "B"), config);
        PlayerId a = match.getSeats().get(0);
        PlayerId b = match.getSeats().get(1);
        for (PlayerId id : match.getSeats()) {
            match.getRun(id).addMoney(45);   // seed money: the shop path must be exercised even in losing games
            if (stackedDecks) {              // a clearing deck: cash-out, points, and swap paths under load
                match.getRun(id).resetDeck(java.util.List.of());
                for (int i = 0; i < 16; i++)
                    match.getRun(id).addCardToDeck(new DeckCard(DeckCard.Rank.ACE, DeckCard.Suit.SPADES));
            }
        }
        match.start();
        clearedBlinds = 0; purchases = 0; swaps = 0;

        List<String> trace = new ArrayList<>();
        while (match.getPhase() == MatchPhase.BLIND) {
            for (PlayerId id : match.getSeats()) driveRound(match.getRun(id));
            match.toShop();

            for (PlayerId id : match.getSeats()) if (match.getResult(id).cleared()) clearedBlinds++;
            trace.add(barrierFingerprint(match));
            // The Commons couples seats by design: the shared pool is first-come-first-served, so round
            // scores are order-dependent (digging opportunities, and pool-readers like Banner). At a Commons
            // barrier only the persistent state must mirror; everywhere else, results included.
            boolean coupled = match.getBlind() == Blind.BOSS && match.getCurrentBoss() == BossBlind.THE_COMMONS;
            String fa = seatFingerprint(match.getRun(a), coupled ? null : match.getResult(a));
            String fb = seatFingerprint(match.getRun(b), coupled ? null : match.getResult(b));
            if (!fa.equals(fb)) System.out.println("== seat A ==\n" + fa + "\n== seat B ==\n" + fb);
            check("seats mirrored at the barrier (ante " + match.getAnte() + " " + match.getBlind() + ")", fa.equals(fb));

            if (match.getPhase() == MatchPhase.FINISHED) break;
            for (PlayerId id : match.getSeats()) driveShop(match.getRun(id));
            trace.add(shopFingerprint(match.getRun(a)) + "\n" + shopFingerprint(match.getRun(b)));
            check("shops mirrored (ante " + match.getAnte() + " " + match.getBlind() + ")",
                    shopFingerprint(match.getRun(a)).equals(shopFingerprint(match.getRun(b))));

            if (envySwaps && match.getActiveSin() == Sin.ENVY
                    && !match.getRun(a).getJokers().isEmpty() && !match.getRun(b).getJokers().isEmpty()) {
                match.swapJokers(a, 0, b, 0);
                swaps++;
            }

            match.nextBlind();
        }
        check("the scripted match reaches FINISHED", match.getPhase() == MatchPhase.FINISHED);
        trace.add(barrierFingerprint(match));
        return trace;
    }

    /**
     * Plays one seat's round with state-derived decisions only: one discard when the hand is deep, 5-card plays
     * (adapting to Psychic/Eye/Mouth/Cerulean Bell), and a voluntary early finish once the target is met on the
     * third or later hand — exercising the money-vs-share tradeoff.
     */
    private static void driveRound(Run run) {
        Round round = run.getRound();
        int discardActions = 0;
        HandType firstType = null;   // The Mouth: the round's pinned type, tracked driver-side

        while (round.getOutcome() == RoundOutcome.IN_PROGRESS) {
            BossBlind boss = run.effectiveBoss();
            List<DeckCard> hand = round.getHand();
            if (hand.isEmpty()) { round.finish(); break; }

            if (discardActions < 3 && round.getDiscardsRemaining() > 0 && hand.size() > 6
                    && !hasFlush(hand)) {                          // dig for the flush until one appears
                List<DeckCard> toss = offSuitCards(hand, 3);
                DeckCard forced = round.getForcedCard();
                if (forced != null && !toss.contains(forced)) toss.set(0, forced);   // Cerulean Bell binds discards too
                round.discard(toss);
                discardActions++;
                continue;
            }

            if (boss != null && boss.requiresFiveCards() && hand.size() < 5) { round.finish(); break; }   // The Psychic
            List<DeckCard> cards = bestFive(hand);
            DeckCard forced = round.getForcedCard();
            if (forced != null && !cards.contains(forced)) cards.set(0, forced);                // Cerulean Bell

            HandType type = EVAL.evaluate(cards).type();
            if (boss != null && boss.forbidsRepeatType()
                    && run.getStats().getHandPlaysThisRound(type) > 0) { round.finish(); break; }   // The Eye
            if (boss != null && boss.oneTypeOnly()
                    && firstType != null && type != firstType) { round.finish(); break; }           // The Mouth

            round.play(cards);
            if (firstType == null) firstType = type;

            if (round.getOutcome() == RoundOutcome.IN_PROGRESS && round.isTargetMet()
                    && run.getStats().getHandsPlayedThisRound() >= 3)
                round.finish();   // bank the remaining hands
        }
    }

    /** Shops with guarded reads only: one reroll with a cash cushion, the first acquirable+affordable card, a voucher. */
    private static void driveShop(Run run) {
        Shop shop = run.getShop();
        if (shop == null) return;

        if (run.getMoney() - shop.rerollCost() >= run.minBalance() + 8) shop.reroll();

        for (int i = 0; i < shop.getSlotCount(); i++) {
            Card item = shop.getSlot(i);
            if (item == null || !run.canAcquire(item)) continue;
            if (run.getMoney() - item.getShopValue() < run.minBalance() + 4) continue;   // conservative: discounts only cheapen
            shop.buy(i);
            purchases++;
            break;   // at most one card per shop keeps the economy varied but bounded
        }

        for (int i = 0; i < shop.getVoucherCount(); i++) {
            Voucher voucher = shop.getVoucher(i);
            if (voucher == null || !run.canRedeem(voucher)) continue;
            if (run.getMoney() - voucher.getShopValue() < run.minBalance()) continue;
            shop.redeemVoucher(i);
            break;
        }
    }

    /** Whether some suit already has five cards in {@code hand}. */
    private static boolean hasFlush(List<DeckCard> hand) {
        for (DeckCard.Suit suit : DeckCard.Suit.values()) {
            int count = 0;
            for (DeckCard c : hand) if (c.getSuit() == suit) count++;
            if (count >= 5) return true;
        }
        return false;
    }

    /** The strongest deterministic 5-card pick: a flush if a suit has five, else stacked ranks, else the first cards. */
    private static List<DeckCard> bestFive(List<DeckCard> hand) {
        for (DeckCard.Suit suit : DeckCard.Suit.values()) {
            List<DeckCard> ofSuit = new ArrayList<>();
            for (DeckCard c : hand) if (c.getSuit() == suit) ofSuit.add(c);
            if (ofSuit.size() >= 5) return new ArrayList<>(ofSuit.subList(0, 5));
        }
        List<DeckCard> picked = new ArrayList<>();   // pairs and better: collect the largest rank groups
        for (DeckCard.Rank rank : DeckCard.Rank.values()) {
            List<DeckCard> ofRank = new ArrayList<>();
            for (DeckCard c : hand) if (c.getRank() == rank) ofRank.add(c);
            if (ofRank.size() >= 2) for (DeckCard c : ofRank) if (picked.size() < 5) picked.add(c);
        }
        for (DeckCard c : hand) if (picked.size() < Math.min(5, hand.size()) && !picked.contains(c)) picked.add(c);
        return picked;
    }

    /** Up to {@code n} cards outside the hand's largest suit group (the driver's discard fodder). */
    private static List<DeckCard> offSuitCards(List<DeckCard> hand, int n) {
        DeckCard.Suit biggest = null;
        int best = -1;
        for (DeckCard.Suit suit : DeckCard.Suit.values()) {
            int count = 0;
            for (DeckCard c : hand) if (c.getSuit() == suit) count++;
            if (count > best) { best = count; biggest = suit; }
        }
        List<DeckCard> out = new ArrayList<>();
        for (DeckCard c : hand) if (c.getSuit() != biggest && out.size() < n) out.add(c);
        if (out.isEmpty()) out.add(hand.get(0));   // a mono-suit hand still discards something
        return out;
    }

    // --- fingerprints ---

    /** The full match state at a settlement barrier: phase coordinates, standings, and every seat's state. */
    private static String barrierFingerprint(Match match) {
        StringBuilder sb = new StringBuilder();
        sb.append("ante=").append(match.getAnte())
          .append(" blind=").append(match.getBlind())
          .append(" sin=").append(match.getActiveSin())
          .append(" boss=").append(match.getCurrentBoss())
          .append(" phase=").append(match.getPhase()).append('\n');
        for (PlayerId id : match.getSeats()) {
            sb.append("points=").append(match.getStandings().getPoints(id))
              .append(" award=").append(match.getStandings().getLastAward().getOrDefault(id, 0L)).append('\n');
            sb.append(seatFingerprint(match.getRun(id), match.getResult(id))).append('\n');
        }
        return sb.toString();
    }

    /** One seat's complete observable state (identity-free, so it doubles as the mirroring comparator). */
    private static String seatFingerprint(Run run, BlindResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("money=").append(run.getMoney());
        sb.append(" result=");
        if (result == null) sb.append("none");
        else sb.append(result.outcome()).append('/').append(result.score()).append('/')
               .append(result.bestHand()).append('/').append(result.target()).append('/')
               .append(result.handsRemaining()).append('/').append(result.moneyEarned());
        sb.append("\njokers=");
        for (JokerCard j : run.getJokers()) sb.append(cardDescriptor(j)).append(';');
        sb.append("\nconsumables=");
        for (ConsumableCard c : run.getConsumables()) sb.append(cardDescriptor(c)).append(';');
        sb.append("\nlevels=");
        for (HandType t : HandType.values()) sb.append(run.getHandLevels().levelOf(t)).append(',');
        sb.append("\ndeck=");
        for (DeckCard c : run.getDeck()) sb.append(cardDescriptor(c)).append(';');
        return sb.toString();
    }

    /** One seat's shop offerings after its scripted shopping: slots, packs, vouchers, reroll state. */
    private static String shopFingerprint(Run run) {
        Shop shop = run.getShop();
        if (shop == null) return "no-shop";
        StringBuilder sb = new StringBuilder("rerolls=").append(shop.getRerolls()).append('\n');
        for (Card c : shop.getSlots()) sb.append(c == null ? "-" : cardDescriptor(c)).append(';');
        sb.append('\n');
        for (BoosterPack p : shop.getPacks())
            sb.append(p == null ? "-" : p.kind() + "/" + p.size() + "/" + p.getShopValue()).append(';');
        sb.append('\n');
        for (Voucher v : shop.getVouchers())
            sb.append(v == null ? "-" : v.getSpec().getName() + "/" + v.getShopValue()).append(';');
        return sb.toString();
    }

    /** A value descriptor for any card kind: identity, modifiers, stickers, counters — no object identity. */
    private static String cardDescriptor(Card card) {
        StringBuilder sb = new StringBuilder();
        if (card instanceof DeckCard d)             sb.append(d.getRank()).append(d.getSuit())
                                                      .append('/').append(d.getEnhancement()).append('/').append(d.getSeal());
        else if (card instanceof JokerCard j)       sb.append(j.getSpec().getName()).append('/').append(j.getCounter());
        else if (card instanceof ConsumableCard c)  sb.append(c.getSpec().getName());
        else                                        sb.append(card.getClass().getSimpleName());
        sb.append('/').append(card.getEdition()).append('/').append(card.getShopValue());
        for (Sticker s : Sticker.values()) if (card.hasSticker(s)) sb.append('+').append(s);
        return sb.toString();
    }

    // --- assertions ---

    private static void compareTraces(String label, List<String> first, List<String> second) {
        if (first.equals(second)) { check(label, true); return; }
        int at = -1;
        for (int i = 0; i < Math.min(first.size(), second.size()); i++)
            if (!first.get(i).equals(second.get(i))) { at = i; break; }
        if (at < 0) at = Math.min(first.size(), second.size());   // one trace is a prefix of the other
        System.out.println("--- trace divergence at snapshot " + at + " ---");
        System.out.println(at < first.size() ? first.get(at) : "<absent>");
        System.out.println("--- vs ---");
        System.out.println(at < second.size() ? second.get(at) : "<absent>");
        check(label, false);
    }

    private static void check(String label, boolean ok) {
        if (!ok) failures++;
        if (!ok || !label.startsWith("seats mirrored") && !label.startsWith("shops mirrored"))
            System.out.printf("%-52s %s%n", label, ok ? "PASS" : "FAIL");   // the per-barrier checks only print on failure
    }
}
