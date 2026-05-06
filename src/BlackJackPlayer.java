import java.util.ArrayList;
import java.util.List;

/**
 * Represents a player in a Blackjack game.
 * Each player has a name, multiple hands (for splits), and an immutable BlackJackChip
 * reference that is reassigned on every chip operation to preserve immutability.
 */
public class BlackJackPlayer {

    private final String name;
    private final List<List<Card>> hands;
    private BlackJackChip chips;             // NOT final — reassigned on every chip operation
    private static final int MAX_HAND_SIZE = 11;
    private final GameLog gameLog;
    private int currentHandIndex;
    private int currentBet;

    /**
     * Primary constructor used by BlackJackGUI:
     *   new BlackJackPlayer("Captain", BlackJackChip.create(1000, gameLog, clock))
     */
    public BlackJackPlayer(String name, BlackJackChip chips) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Name cannot be blank.");
        if (chips == null) throw new IllegalArgumentException("Chips cannot be null.");
        this.name = name;
        this.chips = chips;
        this.gameLog = GameLog.noOp(); // safe no-op logger; GUI passes its own GameLog via BlackJackChip
        this.hands = new ArrayList<>();
        this.hands.add(new ArrayList<>());
        this.currentHandIndex = 0;
        this.currentBet = 0;
    }

    /**
     * Convenience constructor for quick tests / non-GUI usage.
     */
    public BlackJackPlayer(String name, int initialChips) {
        this(name, BlackJackChip.createSilent(initialChips));
    }

    // -------------------------------------------------------------------------
    // Name / toString
    // -------------------------------------------------------------------------

    public String getName() { return name; }

    @Override
    public String toString() {
        return name + "'s Hands: " + hands + " | Chips: " + chips;
    }

    // -------------------------------------------------------------------------
    // Hand access — NOTE: returns the LIVE list, not a defensive copy,
    // so GUI calls like player.getHands().add(...) and player.getHands().set(...)
    // actually mutate state.
    // -------------------------------------------------------------------------

    public List<List<Card>> getHands() {
        return hands;                          // live reference — intentional
    }

    public List<Card> getCurrentHand() {
        return hands.get(currentHandIndex);
    }

    /** Alias used in some GUI paths. */
    public List<Card> getHand() {
        return getCurrentHand();
    }

    public int getCurrentHandIndex() {
        return currentHandIndex;
    }

    // -------------------------------------------------------------------------
    // Hand management
    // -------------------------------------------------------------------------

    public void addCard(Card card) {
        if (!canAddCard()) {
            throw new IllegalStateException("Cannot add more cards — maximum hand size reached.");
        }
        getCurrentHand().add(card);
        gameLog.log(name + " added: " + card);
    }

    public void clearHands() {
        hands.clear();
        hands.add(new ArrayList<>());
        currentHandIndex = 0;
        gameLog.log(name + "'s hands cleared.");
    }

    /** Clears only the current hand (kept for compatibility). */
    public void clearHand() {
        getCurrentHand().clear();
        gameLog.log(name + "'s current hand cleared.");
    }

    public boolean canAddCard() {
        return getCurrentHand().size() < MAX_HAND_SIZE;
    }

    // -------------------------------------------------------------------------
    // Score calculation
    // -------------------------------------------------------------------------

    public int calculateHandValue(List<Card> hand) {
        int value = 0;
        int aces = 0;
        for (Card card : hand) {
            value += card.getValue();
            if ("A".equals(card.getRank())) aces++;
        }
        while (value > 21 && aces > 0) {
            value -= 10;
            aces--;
        }
        return value;
    }

    /** Convenience overload for the current hand. */
    public int calculateHandValue() {
        return calculateHandValue(getCurrentHand());
    }

    public boolean isBust(List<Card> hand) {
        boolean bust = calculateHandValue(hand) > 21;
        if (bust) gameLog.log(name + " busted!");
        return bust;
    }

    // -------------------------------------------------------------------------
    // Bet tracking (currentBet is separate from chip deduction)
    // -------------------------------------------------------------------------

    public int getCurrentBet() { return currentBet; }

    public void setCurrentBet(int bet) { this.currentBet = bet; }

    // -------------------------------------------------------------------------
    // Double Down
    // -------------------------------------------------------------------------

    public void doubleDown(Deck deck) {
        if (!canDoubleDown()) {
            throw new IllegalStateException("Cannot double down — conditions not met.");
        }
        chips = chips.removeChips(currentBet); // pay the extra bet (reassign!)
        currentBet *= 2;
        deck.dealCard(this, 0);
        gameLog.log(name + " doubled down. New bet: " + currentBet);
    }

    public boolean canDoubleDown() {
        return getCurrentHand().size() == 2 && chips.hasEnough(currentBet);
    }

    // -------------------------------------------------------------------------
    // Split
    // -------------------------------------------------------------------------

    public void split(Deck deck) {
        if (!canSplit()) {
            throw new IllegalStateException("Cannot split — conditions not met.");
        }
        int originalHandIndex = currentHandIndex;
        List<Card> current = getCurrentHand();
        List<Card> newHand = new ArrayList<>();
        newHand.add(current.remove(1));        // move second card to new hand
        hands.add(newHand);
        chips = chips.removeChips(currentBet); // pay split bet (reassign!)
        currentHandIndex = originalHandIndex;
        deck.dealCard(this, 0);
        currentHandIndex = hands.size() - 1;
        deck.dealCard(this, 0);
        currentHandIndex = originalHandIndex;
        gameLog.log(name + " split. Hand 1: " + current + " | Hand 2: " + newHand);
    }

    public boolean canSplit() {
        List<Card> hand = getCurrentHand();
        return hand.size() == 2
                && hand.get(0).getRank().equals(hand.get(1).getRank())
                && chips.hasEnough(currentBet);
    }

    // -------------------------------------------------------------------------
    // Chip management — all mutating ops reassign the chips reference
    // -------------------------------------------------------------------------

    public BlackJackChip getChips() { return chips; }

    /**
     * Replaces the chip reference entirely.
     * Called by GUI when it needs to sync chip state (e.g. after load).
     */
    public void setChips(BlackJackChip chips) {
        if (chips == null) throw new IllegalArgumentException("Chips cannot be null.");
        this.chips = chips;
    }

    public int getMaxBet() { return chips.maxBet(); }

    public void placeMaxBet() {
        int maxBet = getMaxBet();
        if (maxBet <= 0) throw new IllegalStateException("No chips available for max bet.");
        chips = chips.removeChips(maxBet); // reassign!
        this.currentBet = maxBet;
        gameLog.log(name + " placed max bet: " + maxBet);
    }
}
