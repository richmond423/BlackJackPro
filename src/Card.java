/**
 * Represents a single playing card with a rank, suit, and value.
 *
 * toString()     → human-readable:  "A of Hearts (Value: 11)"
 * toImageName()  → filename-safe:   "A_Hearts"
 *                  GUI should use:  "file:resources/images/" + card.toImageName() + ".png"
 */
public class Card implements Comparable<Card> {

    private final String rank;  // e.g. "2", "10", "J", "Q", "K", "A"
    private final String suit;  // e.g. "Hearts", "Diamonds", "Clubs", "Spades"
    private final int value;    // numeric value (2–11)
    private boolean marked;

    // Static log uses noOp() so a bad path never crashes card construction.
    private static final GameLog gameLog = GameLog.noOp();

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    public Card(String rank, String suit, int value) {
        if (rank == null || rank.isBlank()) throw new IllegalArgumentException("Rank cannot be blank.");
        if (suit == null || suit.isBlank()) throw new IllegalArgumentException("Suit cannot be blank.");
        this.rank  = rank;
        this.suit  = suit;
        this.value = value;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public String getRank()  { return rank; }
    public String getSuit()  { return suit; }
    public int    getValue() { return value; }

    public boolean hasSameRank(Card other) {
        return this.rank.equals(other.rank);
    }

    public void markAsSuspicious() { marked = true; }
    public boolean isMarked() { return marked; }

    // -------------------------------------------------------------------------
    // String representations
    // -------------------------------------------------------------------------

    /**
     * Human-readable form used for logging and console output.
     * Example: "A of Hearts (Value: 11)"
     */
    @Override
    public String toString() {
        return rank + " of " + suit + " (Value: " + value + ")";
    }

    /**
     * Filesystem-safe name used for image lookup.
     * Example: "A_Hearts"  →  resources/images/A_Hearts.png
     *
     * Convention (adopt this when naming your image files):
     *   rank : A 2 3 4 5 6 7 8 9 10 J Q K
     *   suit : Hearts Diamonds Clubs Spades
     *   file : {rank}_{suit}.png   e.g.  10_Diamonds.png
     */
    public String toImageName() {
        return rank + "_" + suit;
    }

    public String getImageName() {
        return toImageName();
    }

    // -------------------------------------------------------------------------
    // Parsing
    // -------------------------------------------------------------------------

    /**
     * Parses a card from its toString() format:
     * "A of Hearts (Value: 11)"
     */
    public static Card fromString(String cardString) {
        try {
            if (cardString == null
                    || !cardString.contains(" of ")
                    || !cardString.contains("(Value:")) {
                throw new IllegalArgumentException("Invalid format: " + cardString);
            }

            String[] rankAndRest = cardString.split(" of ", 2);
            String rank = rankAndRest[0].trim();

            String[] suitAndValue = rankAndRest[1].split(" \\(Value: ", 2);
            String suit  = suitAndValue[0].trim();
            int    value = Integer.parseInt(suitAndValue[1].replace(")", "").trim());

            return new Card(rank, suit, value);
        } catch (Exception e) {
            gameLog.logError("Failed to parse card: " + cardString, e);
            throw new IllegalArgumentException("Invalid card string: " + cardString, e);
        }
    }

    /**
     * Returns the standard Blackjack value for a given rank string.
     * Aces are initialised at 11; BlackJackPlayer.calculateHandValue() reduces
     * them to 1 as needed.
     */
    public static int calculateCardValue(String rank) {
        if (rank == null) throw new IllegalArgumentException("Rank cannot be null.");
        switch (rank) {
            case "A":             return 11;
            case "K": case "Q":
            case "J": case "10": return 10;
            default:
                try {
                    return Integer.parseInt(rank);
                } catch (NumberFormatException e) {
                    gameLog.logError("Unknown rank: " + rank, e);
                    throw new IllegalArgumentException("Unknown rank: " + rank, e);
                }
        }
    }

    // -------------------------------------------------------------------------
    // Comparable / equals / hashCode
    // -------------------------------------------------------------------------

    @Override
    public int compareTo(Card other) {
        return Integer.compare(this.value, other.value);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Card)) return false;
        Card other = (Card) obj;
        return rank.equals(other.rank) && suit.equals(other.suit);
    }

    @Override
    public int hashCode() {
        return rank.hashCode() * 31 + suit.hashCode();
    }
}
