package model.cards.consumables;

import model.game.player.Run;
import model.cards.DeckCard;
import model.cards.DeckCard.Rank;
import model.cards.DeckCard.Suit;
import model.cards.jokers.JokerCard;
import model.cards.jokers.JokerSpec;
import model.cards.jokers.Rarity;
import model.game.scoring.HandType;
import model.modifiers.Edition;
import model.modifiers.Seal;
import model.modifiers.Sticker;

import java.util.List;

/** Run-as-main harness for the 19 Spectral cards: seals, suit/rank rewrites, edition/sticker effects, joker copy/destroy, and generation. */
public final class SpectralTests {

    private static int failures = 0;

    public static void main(String[] args) {
        check("all 19 spectrals present", Spectrals.values().length == 19);

        // weighted generation never surfaces the hidden spectrals (weight 0)
        java.util.Random rng = new java.util.Random(42);
        boolean hiddenExcluded = true;
        for (int i = 0; i < 500; i++) {
            Spectrals s = Spectrals.random(rng);
            if (s == Spectrals.THE_SOUL || s == Spectrals.BLACK_HOLE) hiddenExcluded = false;
        }
        check("Spectrals.random excludes The Soul / Black Hole", hiddenExcluded);

        // --- seal spectrals apply to the first selected card ---
        DeckCard t = card(Rank.FIVE, Suit.SPADES);
        use(Spectrals.TALISMAN, List.of(t));
        check("Talisman -> Gold Seal", t.getSeal() == Seal.GOLD_SEAL);

        DeckCard dv = card(Rank.SIX, Suit.HEARTS);
        use(Spectrals.DEJA_VU, List.of(dv));
        check("Deja Vu -> Red Seal", dv.getSeal() == Seal.RED_SEAL);

        // --- Sigil: selected cards take the leftmost's suit (new) ---
        DeckCard s0 = card(Rank.TWO, Suit.CLUBS), s1 = card(Rank.THREE, Suit.HEARTS), s2 = card(Rank.FOUR, Suit.DIAMONDS);
        use(Spectrals.SIGIL, List.of(s0, s1, s2));
        check("Sigil -> all match leftmost suit", s1.getSuit() == Suit.CLUBS && s2.getSuit() == Suit.CLUBS && s0.getSuit() == Suit.CLUBS);

        // --- Ouija: selected cards take the leftmost's rank (new) ---
        DeckCard o0 = card(Rank.KING, Suit.SPADES), o1 = card(Rank.THREE, Suit.HEARTS), o2 = card(Rank.NINE, Suit.CLUBS);
        use(Spectrals.OUIJA, List.of(o0, o1, o2));
        check("Ouija -> all match leftmost rank", o1.getRank() == Rank.KING && o2.getRank() == Rank.KING);

        // --- Cryptid: 2 copies of the selected card enter the deck ---
        Run cr = new Run(1L);
        DeckCard src = card(Rank.ACE, Suit.SPADES);
        src.apply(Seal.BLUE_SEAL);
        cr.getConsumables().add(Spectrals.CRYPTID.make());
        cr.useConsumable(0, List.of(src));
        long copies = cr.getDeck().stream().filter(c -> c.getRank() == Rank.ACE && c.getSeal() == Seal.BLUE_SEAL).count();
        check("Cryptid -> 2 copies added to deck", copies == 2);

        // --- Aura: first selected card gets one of the four editions ---
        Run aura = new Run(2L);
        DeckCard at = card(Rank.SEVEN, Suit.SPADES);
        aura.getConsumables().add(Spectrals.AURA.make());
        aura.useConsumable(0, List.of(at));
        check("Aura -> card gains an edition", at.getEdition() != null);

        // widened selection channel: Aura can now target a non-deck card (a joker)
        Run auraJoker = new Run(21L);
        JokerCard auraTarget = joker();
        auraJoker.getJokers().add(auraTarget);
        auraJoker.getConsumables().add(Spectrals.AURA.make());
        auraJoker.useConsumable(0, List.of(auraTarget));
        check("Aura -> can edition a joker target", auraTarget.getEdition() != null);

        // --- Exorcism: removes a sticker from the selected card ---
        Run ex = new Run(3L);
        DeckCard sticky = card(Rank.TEN, Suit.HEARTS);
        sticky.apply(Sticker.ETERNAL);
        ex.getConsumables().add(Spectrals.EXORCISM.make());
        ex.useConsumable(0, List.of(sticky));
        check("Exorcism -> sticker removed", !sticky.hasSticker(Sticker.ETERNAL));

        // Exorcism can also strip a sticker off a joker (widened selection channel)
        Run exJ = new Run(31L);
        JokerCard stickyJoker = joker();
        stickyJoker.apply(Sticker.PERISHABLE);
        exJ.getJokers().add(stickyJoker);
        exJ.getConsumables().add(Spectrals.EXORCISM.make());
        exJ.useConsumable(0, List.of(stickyJoker));
        check("Exorcism -> sticker removed from joker", !stickyJoker.hasSticker(Sticker.PERISHABLE));

        // --- Wraith: creates a joker and divides money by 3 (new) ---
        Run wr = new Run(4L); wr.addMoney(30);
        wr.getConsumables().add(Spectrals.WRAITH.make());
        wr.useConsumable(0);
        checkInt("Wraith -> money divided by 3", wr.getMoney(), 10);
        checkInt("Wraith -> made a joker", wr.getJokers().size(), 1);

        // --- Ectoplasm: negative to a joker, -1 hand size ---
        Run ec = new Run(5L);
        ec.getJokers().add(joker());
        int handBefore = ec.getHandSize();
        ec.getConsumables().add(Spectrals.ECTOPLASM.make());
        ec.useConsumable(0);
        check("Ectoplasm -> joker negative", ec.getJokers().get(0).getEdition() == Edition.NEGATIVE);
        checkInt("Ectoplasm -> -1 hand size", ec.getHandSize(), handBefore - 1);

        // --- Immolate: +$20 ---
        Run im = new Run(6L);
        im.getConsumables().add(Spectrals.IMMOLATE.make());
        im.useConsumable(0);
        checkInt("Immolate -> +$20", im.getMoney(), 20);

        // --- The Soul: creates a (legendary, falling back) joker ---
        Run soul = new Run(7L);
        soul.getConsumables().add(Spectrals.THE_SOUL.make());
        soul.useConsumable(0);
        checkInt("The Soul -> made a joker", soul.getJokers().size(), 1);

        // --- Black Hole: every hand levels up by 1 ---
        Run bh = new Run(8L);
        bh.getConsumables().add(Spectrals.BLACK_HOLE.make());
        bh.useConsumable(0);
        check("Black Hole -> Pair L2", bh.getHandLevels().levelOf(HandType.PAIR) == 2);
        check("Black Hole -> Flush L2", bh.getHandLevels().levelOf(HandType.FLUSH) == 2);

        // --- Black Hole: the most-played hand gains a second level (+2 total) ---
        Run bh2 = new Run(81L);
        bh2.getStats().recordHandPlayed(HandType.FLUSH);   // Flush is now the most-played hand
        bh2.getConsumables().add(Spectrals.BLACK_HOLE.make());
        bh2.useConsumable(0);
        check("Black Hole -> most-played Flush L3", bh2.getHandLevels().levelOf(HandType.FLUSH) == 3);
        check("Black Hole -> other hand Pair L2", bh2.getHandLevels().levelOf(HandType.PAIR) == 2);

        // --- Ankh: copies a joker and destroys one; with one joker, count holds and the copy is not negative ---
        Run ankh = new Run(9L);
        ankh.getJokers().add(joker());
        ankh.getConsumables().add(Spectrals.ANKH.make());
        ankh.useConsumable(0);
        checkInt("Ankh -> still one joker", ankh.getJokers().size(), 1);
        check("Ankh -> copy is not negative", ankh.getJokers().get(0).getEdition() == null);

        // --- Hex: polychrome to a joker, destroys the other ---
        Run hex = new Run(10L);
        hex.getJokers().add(joker());
        hex.getJokers().add(joker());
        hex.getConsumables().add(Spectrals.HEX.make());
        hex.useConsumable(0);
        checkInt("Hex -> one joker remains", hex.getJokers().size(), 1);
        check("Hex -> survivor is polychrome", hex.getJokers().get(0).getEdition() == Edition.POLYCHROME);

        // --- Familiar: destroys 3 held, adds 3 enhanced face cards (in a round) ---
        Run fam = new Run(11L);
        for (int i = 0; i < 8; i++) fam.getDeck().add(card(Rank.TWO, Suit.SPADES));   // plain, unenhanced
        fam.beginRound(300);
        fam.getConsumables().add(Spectrals.FAMILIAR.make());
        fam.useConsumable(0);
        List<DeckCard> enhanced = fam.getDeck().stream().filter(c -> c.getEnhancement() != null).toList();
        check("Familiar -> 3 enhanced cards added", enhanced.size() == 3);
        check("Familiar -> added cards are face", enhanced.stream().allMatch(DeckCard::isFace));

        System.out.println(failures == 0 ? "\nALL PASS" : "\n" + failures + " FAILURE(S)");
        if (failures != 0) System.exit(1);
    }

    private static void use(Spectrals spectral, List<DeckCard> targets) {
        Run run = new Run(0L);
        run.getConsumables().add(spectral.make());
        run.useConsumable(0, targets);
    }

    private static DeckCard card(Rank rank, Suit suit) { return new DeckCard(rank, suit); }

    private static JokerCard joker() { return new JokerCard(JokerSpec.named("Stub", Rarity.COMMON).build(), 4); }

    private static void check(String label, boolean ok) {
        if (!ok) failures++;
        System.out.printf("%-46s %s%n", label, ok ? "PASS" : "FAIL");
    }

    private static void checkInt(String label, int actual, int expected) {
        check(label + " (" + actual + ")", actual == expected);
    }
}
