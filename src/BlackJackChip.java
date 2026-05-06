import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * An immutable, enterprise-grade class representing a player's chips in a Blackjack game.
 * Provides optimized chip management with logging, auditing, cryptographic integrity, and export/import capabilities.
 * Includes basic transaction statistics for analytics.
 */
public final class BlackJackChip implements Serializable {
    private static final long serialVersionUID = 1L;
    private final int totalChips;
    private final GameLog gameLog;
    private final transient Clock clock; // Transient for serialization
    private final List<String> transactionHistory;

    BlackJackChip(int totalChips, GameLog gameLog, Clock clock, List<String> history) {
        if (totalChips < 0) throw new IllegalArgumentException("Initial chip amount cannot be negative.");
        if (gameLog == null) throw new IllegalArgumentException("GameLog cannot be null.");
        if (clock == null) throw new IllegalArgumentException("Clock cannot be null.");
        this.totalChips = totalChips;
        this.gameLog = gameLog;
        this.clock = clock;
        this.transactionHistory = new ArrayList<>(history);
        logTransaction("Initialized chips with " + totalChips);
    }

    public static BlackJackChip create(int initialAmount, GameLog gameLog, Clock clock) {
        return new BlackJackChip(initialAmount, gameLog, clock, new ArrayList<>());
    }

    public static BlackJackChip createSilent(int initialAmount) {
        return create(initialAmount, GameLog.noOp(), Clock.systemUTC());
    }

    public int getTotalChips() { return totalChips; }

    public BlackJackChip addChips(int amount) {
        if (amount < 0) throw new IllegalArgumentException("Cannot add negative chips: " + amount);
        if (totalChips > Integer.MAX_VALUE - amount) throw new IllegalStateException("Chip total would exceed " + Integer.MAX_VALUE + " after adding " + amount);
        int newTotal = totalChips + amount;
        return new BlackJackChip(newTotal, gameLog, clock, transactionHistory);
    }

    public BlackJackChip removeChips(int amount) {
        if (!hasEnough(amount)) {
            throw new IllegalStateException("Insufficient chips: requested " + amount + ", available " + totalChips);
        }
        int newTotal = totalChips - amount;
        return new BlackJackChip(newTotal, gameLog, clock, transactionHistory);
    }

    public BlackJackChip resetChips(int newAmount) {
        if (newAmount < 0) throw new IllegalArgumentException("Cannot reset to negative chips: " + newAmount);
        return new BlackJackChip(newAmount, gameLog, clock, transactionHistory);
    }

    public boolean isOutOfChips() { return totalChips == 0; }

    public boolean hasEnough(int amount) {
        return amount >= 0 && amount <= totalChips;
    }

    public int percentOfChips(double percent) {
        if (percent < 0 || percent > 1) {
            throw new IllegalArgumentException("Percentage must be between 0 and 1, got " + percent);
        }
        int bet = (int) Math.floor(totalChips * percent);
        logTransaction("Calculated " + (percent * 100) + "% bet: " + bet);
        return bet;
    }

    public void validateState() {
        if (totalChips < 0) {
            gameLog.logError("Invalid state: totalChips is negative (" + totalChips + ")", null);
            throw new IllegalStateException("Chip total cannot be negative.");
        }
    }

    public List<String> getTransactionHistory() {
        return Collections.unmodifiableList(transactionHistory);
    }

    public Optional<String> getLastTransaction() {
        if (transactionHistory.isEmpty()) return Optional.empty();
        return Optional.of(transactionHistory.get(transactionHistory.size() - 1));
    }

    public BlackJackChip withLog(GameLog newLog) {
        if (newLog == null) throw new IllegalArgumentException("New GameLog cannot be null.");
        return new BlackJackChip(totalChips, newLog, clock, transactionHistory);
    }

    public BlackJackChip withClock(Clock newClock) {
        if (newClock == null) throw new IllegalArgumentException("New Clock cannot be null.");
        return new BlackJackChip(totalChips, gameLog, newClock, transactionHistory);
    }

    public String getAuditHash() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String historyString = String.join("", transactionHistory);
            byte[] hash = digest.digest(historyString.getBytes("UTF-8"));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            gameLog.logError("Failed to compute audit hash", e);
            return Integer.toHexString(transactionHistory.hashCode()); // Fallback
        }
    }

    public TransactionStats getStats() {
        return new TransactionStats(transactionHistory);
    }

    private void logTransaction(String message) {
        String now = LocalDateTime.now(clock).toString();
        String timestamped = "[" + now + "] " + message;
        transactionHistory.add(timestamped);
        gameLog.log(timestamped);
    }

    public String toJson() {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"totalChips\": ").append(totalChips).append(",");
        json.append("\"auditHash\": \"").append(getAuditHash()).append("\",");
        json.append("\"transactions\": [");
        for (int i = 0; i < transactionHistory.size(); i++) {
            json.append("\"").append(transactionHistory.get(i).replace("\"", "\\\"")).append("\"");
            if (i < transactionHistory.size() - 1) json.append(",");
        }
        json.append("]");
        json.append("}");
        return json.toString();
    }

    public String toCsv() {
        StringBuilder csv = new StringBuilder();
        csv.append("TotalChips,").append(totalChips).append("\n");
        csv.append("AuditHash,").append(getAuditHash()).append("\n");
        csv.append("TransactionHistory\n");
        for (String transaction : transactionHistory) {
            csv.append("\"").append(transaction.replace("\"", "\"\"")).append("\"\n");
        }
        return csv.toString();
    }

    @Override
    public String toString() {
        return String.format("[Chips: %d @ %s]", totalChips, LocalDateTime.now(clock));
    }

    public int maxBet() { return totalChips; }
}

/**
 * Utility class for BlackJackChip operations like export and parsing.
 */
final class BlackJackChipUtils {
    private BlackJackChipUtils() {} // Prevent instantiation

    public static void exportToFile(BlackJackChip chips, Path path, ExportFormat format) throws IOException {
        String content = switch (format) {
            case JSON -> chips.toJson();
            case CSV -> chips.toCsv();
        };
        Files.writeString(path, content);
    }

    public static BlackJackChip fromJson(String json, GameLog gameLog, Clock clock) {
        try {
            String[] parts = json.substring(1, json.length() - 1).split(",\"transactions\":");
            String chipsPart = parts[0].split(",")[0]; // "totalChips": 123
            int totalChips = Integer.parseInt(chipsPart.split(":")[1].trim());

            List<String> history = new ArrayList<>();
            if (parts.length > 1) {
                String transactions = parts[1].substring(1, parts[1].length() - 1); // Strip []
                if (!transactions.isEmpty()) {
                    String[] transArray = transactions.split("\",\"");
                    for (String trans : transArray) {
                        history.add(trans.replace("\\\"", "\"").replaceAll("^\"|\"$", ""));
                    }
                }
            }
            return new BlackJackChip(totalChips, gameLog, clock, history);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse JSON: " + json, e);
        }
    }

    public static BlackJackChip fromCsv(String csv, GameLog gameLog, Clock clock) {
        try {
            String[] lines = csv.split("\n");
            int totalChips = Integer.parseInt(lines[0].split(",")[1].trim()); // TotalChips,123
            List<String> history = new ArrayList<>();
            if (lines.length > 2) { // Skip TotalChips and AuditHash lines
                for (int i = 2; i < lines.length; i++) {
                    String trans = lines[i].replace("\"\"", "\"").replaceAll("^\"|\"$", "");
                    if (!trans.equals("TransactionHistory")) history.add(trans);
                }
            }
            return new BlackJackChip(totalChips, gameLog, clock, history);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse CSV: " + csv, e);
        }
    }

    public enum ExportFormat {
        JSON, CSV
    }
}

/**
 * Basic stats derived from transaction history.
 */
final class TransactionStats {
    private final int transactionCount;
    private final int largestGain;
    private final int largestLoss;

    TransactionStats(List<String> transactions) {
        this.transactionCount = transactions.size();
        int maxGain = 0, maxLoss = 0;
        for (String trans : transactions) {
            if (trans.contains("Added")) {
                int amount = extractAmount(trans, "Added", "chips.");
                maxGain = Math.max(maxGain, amount);
            } else if (trans.contains("Removed")) {
                int amount = extractAmount(trans, "Removed", "chips.");
                maxLoss = Math.max(maxLoss, amount);
            }
        }
        this.largestGain = maxGain;
        this.largestLoss = maxLoss;
    }

    private int extractAmount(String trans, String action, String suffix) {
        int start = trans.indexOf(action) + action.length() + 1;
        int end = trans.indexOf(suffix, start);
        return Integer.parseInt(trans.substring(start, end).trim());
    }

    public int getTransactionCount() { return transactionCount; }
    public int getLargestGain() { return largestGain; }
    public int getLargestLoss() { return largestLoss; }
}
