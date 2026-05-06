import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.UUID;

/**
 * Class: GameLog
 * Author: Richmond Dhaenens
 * Since: 11/8/2024
 * Version: 30
 * Description: A casino-grade logger for Blackjack with player tracking, chip ledger, security, session stats, and rewards.
 */
public class GameLog {
    private final Map<String, File> logFiles;
    private final Map<String, Deque<String>> memoryLogs;
    private final Map<String, Deque<Snapshot>> snapshots;
    private final Clock clock;
    private final String logDir;
    private final boolean inMemoryOnly;
    private final int maxMemoryEntries;
    private final Map<String, Integer> playerActions; // Anomaly detection
    private final Map<String, PlayerSession> playerSessions; // Player tracking
    private final Map<String, LocalDateTime> sessionStartMap; // Session duration
    private final Map<String, Integer> loyaltyPoints; // Rewards
    private final Map<String, Boolean> flaggedPlayers; // Security flags

    public GameLog(String logDir, Clock clock, boolean inMemoryOnly, int maxMemoryEntries) {
        this.logDir = logDir.endsWith("/") ? logDir : logDir + "/";
        this.clock = clock;
        this.inMemoryOnly = inMemoryOnly;
        this.maxMemoryEntries = maxMemoryEntries;
        this.logFiles = new HashMap<>();
        this.memoryLogs = new HashMap<>();
        this.snapshots = new HashMap<>();
        this.playerActions = new HashMap<>();
        this.playerSessions = new HashMap<>();
        this.sessionStartMap = new HashMap<>();
        this.loyaltyPoints = new HashMap<>();
        this.flaggedPlayers = new HashMap<>();
        initializeLogs(
            "game_log.txt", "bets_log.txt", "errors_log.txt", "actions_log.txt",
            "state_log.txt", "deck_log.txt", "round_results.txt", "security_log.txt"
        );
    }

    public GameLog(String filename) {
        this("resources/logs/", Clock.systemUTC(), false, 1000);
        logFiles.put("game_log.txt", new File(logDir + filename));
    }

    public GameLog() {
        this("resources/logs/", Clock.systemUTC(), false, 1000);
    }

    public static GameLog noOp() {
        return new GameLog() {
            @Override public void log(String message) {}
            @Override public void logError(String errorMessage, Exception e) {}
            @Override public void logBet(String playerId, int betAmount, int remainingChips) {}
            @Override public void logDoubleDown(String playerId, int doubledBet, int remainingChips) {}
            @Override public void logSplit(String playerId, String originalHand, String newHand) {}
            @Override public void logRound(String playerId, String action, String card) {}
            @Override public void logPlayerHand(String playerId, String hand) {}
            @Override public void logFinalScore(String playerId, int score) {}
            @Override public void logSessionSummary(int totalRounds, String summary) {}
            @Override public void logNewGame(PlayerSession session) {}
            @Override public void logBust(String playerId) {}
            @Override public void logDeckOperation(String operation, int remainingCards) {}
            @Override public void logShuffle() {}
            @Override public void logGameStart(PlayerSession session) {}
            @Override public void logRoundStart(int roundNumber) {}
            @Override public void logGameOutcome(String playerId, String outcome, int chipsChange) {}
            @Override public void exportLog(String filename, Path path) throws IOException {}
            @Override public String toJson(String filename) { return "{}"; }
            @Override public Snapshot rollback(String filename) { return null; }
            @Override public void logChipTransaction(String playerId, int amount, String source, String destination) {}
            @Override public void flagPlayer(String playerId, String reason) {}
            @Override public void logSessionDuration(String playerId, Duration playTime) {}
            @Override public void logReward(String playerId, int points, String reason) {}
        };
    }

    public static GameLog inMemory() {
        return new GameLog("", Clock.systemUTC(), true, 1000);
    }

    private void initializeLogs(String... filenames) {
        for (String filename : filenames) {
            if (inMemoryOnly) {
                memoryLogs.put(filename, new LinkedList<>());
                snapshots.put(filename, new LinkedList<>());
            } else {
                File file = new File(logDir + filename);
                logFiles.put(filename, file);
                try {
                    if (!file.exists()) {
                        if (file.getParentFile() != null && !file.getParentFile().exists()) {
                            file.getParentFile().mkdirs();
                        }
                        file.createNewFile();
                    }
                } catch (IOException e) {
                    logError("Error creating log file: " + file.getName(), e);
                }
            }
        }
    }

    private synchronized void logToFile(String filename, String message, Snapshot snapshot) {
        String timestamp = LocalDateTime.now(clock).toString();
        String entry = "[" + timestamp + "] " + message;
        if (inMemoryOnly) {
            Deque<String> log = memoryLogs.get(filename);
            Deque<Snapshot> snap = snapshots.get(filename);
            if (log != null) {
                if (log.size() >= maxMemoryEntries) {
                    log.removeFirst();
                    snap.removeFirst();
                }
                log.add(entry);
                if (snapshot != null) snap.add(snapshot);
            }
        } else {
            File file = logFiles.get(filename);
            if (file == null) return;
            try (PrintStream ps = new PrintStream(new FileOutputStream(file, true))) {
                ps.println(entry);
            } catch (IOException e) {
                logError("Error writing to log file: " + file.getAbsolutePath(), e);
            }
        }
    }

    public String getLogContents(String filename) {
        if (inMemoryOnly) {
            Deque<String> log = memoryLogs.get(filename);
            return log != null ? String.join("\n", log) : "";
        } else {
            File file = logFiles.get(filename);
            if (file == null || !file.exists()) return "";
            try {
                return Files.readString(file.toPath());
            } catch (IOException e) {
                logError("Error reading log file: " + file.getAbsolutePath(), e);
                return "";
            }
        }
    }

    public void exportLog(String filename, Path path) throws IOException {
        Files.writeString(path, getLogContents(filename));
    }

    public String toJson(String filename) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"filename\": \"").append(filename).append("\",");
        json.append("\"auditHash\": \"").append(getAuditHash(filename)).append("\",");
        json.append("\"entries\": [");
        Deque<String> log = memoryLogs.get(filename);
        if (log != null) {
            int i = 0;
            for (String entry : log) {
                json.append("\"").append(entry.replace("\"", "\\\"")).append("\"");
                if (i++ < log.size() - 1) json.append(",");
            }
        }
        json.append("]");
        json.append("}");
        return json.toString();
    }

    public Snapshot rollback(String filename) {
        if (!inMemoryOnly || snapshots.get(filename) == null || snapshots.get(filename).isEmpty()) return null;
        Deque<Snapshot> snap = snapshots.get(filename);
        Deque<String> log = memoryLogs.get(filename);
        log.removeLast();
        return snap.removeLast();
    }

    public String getAuditHash(String filename) {
        String contents = getLogContents(filename);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(contents.getBytes("UTF-8"));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            logError("Failed to compute audit hash for " + filename, e);
            return Integer.toHexString(contents.hashCode());
        }
    }

    private void checkAnomaly(String playerId, String action) {
        String key = playerId + ":" + action;
        int count = playerActions.getOrDefault(key, 0) + 1;
        playerActions.put(key, count);
        if (count > 10) {
            flagPlayer(playerId, "Repeated " + action + " " + count + " times");
        }
    }

    public void log(String message) { logToFile("game_log.txt", message, null); }

    public void logBet(String playerId, int betAmount, int remainingChips) {
        checkAnomaly(playerId, "bet");
        Snapshot snap = new Snapshot(playerId, remainingChips + betAmount, betAmount);
        logToFile("bets_log.txt", String.format("%s placed a bet of %d chips. Remaining chips: %d", playerId, betAmount, remainingChips), snap);
        logReward(playerId, betAmount / 10, "Bet placed"); // 1 point per 10 chips
    }

    public void logError(String errorMessage, Exception e) {
        String timestamp = LocalDateTime.now(clock).toString();
        String entry = "[" + timestamp + "] ERROR: " + errorMessage;
        if (inMemoryOnly) {
            Deque<String> log = memoryLogs.get("errors_log.txt");
            if (log != null) {
                if (log.size() >= maxMemoryEntries) log.removeFirst();
                log.add(entry);
                if (e != null) log.add(e.toString());
            }
        } else {
            File file = logFiles.get("errors_log.txt");
            if (file == null) return;
            try (PrintStream ps = new PrintStream(new FileOutputStream(file, true))) {
                ps.println(entry);
                if (e != null) e.printStackTrace(ps);
            } catch (IOException ex) {
                logToFile("errors_log.txt", "Critical: Error writing to error log: " + ex.getMessage(), null);
            }
        }
    }

    public void logDoubleDown(String playerId, int doubledBet, int remainingChips) {
        checkAnomaly(playerId, "doubleDown");
        Snapshot snap = new Snapshot(playerId, remainingChips + doubledBet, doubledBet);
        logToFile("actions_log.txt", String.format("%s doubled down with a bet of %d chips. Remaining chips: %d", playerId, doubledBet, remainingChips), snap);
    }

    public void logSplit(String playerId, String originalHand, String newHand) {
        checkAnomaly(playerId, "split");
        logToFile("actions_log.txt", String.format("%s split their hand. Original hand: %s, New hand: %s", playerId, originalHand, newHand), null);
    }

    public void logRound(String playerId, String action, String card) {
        checkAnomaly(playerId, "round:" + action);
        logToFile("actions_log.txt", String.format("%s %s%s", playerId, action, card != null ? " and drew " + card : "."), null);
    }

    public void logPlayerHand(String playerId, String hand) { logToFile("state_log.txt", playerId + "'s hand: " + hand, null); }
    public void logFinalScore(String playerId, int score) { logToFile("round_results.txt", playerId + "'s final score: " + score, null); }
    public void logSessionSummary(int totalRounds, String summary) {
        logToFile("round_results.txt", "Session Summary:", null);
        logToFile("round_results.txt", "Total Rounds Played: " + totalRounds, null);
        logToFile("round_results.txt", summary, null);
        if (totalRounds >= 5) logReward("all", 50, "Session milestone: " + totalRounds + " rounds");
    }

    public void logNewGame(PlayerSession session) {
        String playerId = session.playerId();
        playerSessions.put(playerId, session);
        sessionStartMap.put(playerId, LocalDateTime.now(clock));
        logToFile("game_log.txt", String.format("New game started for %s. IP: %s, Device: %s", playerId, session.ipAddress(), session.deviceInfo()), null);
    }

    public void logBust(String playerId) { log(playerId + " has busted!"); }

    public void logDeckOperation(String operation, int remainingCards) {
        logToFile("deck_log.txt", String.format("Deck operation: %s. Remaining cards: %d", operation, remainingCards), null);
    }

    public void logShuffle() { logToFile("deck_log.txt", "The deck has been shuffled.", null); }

    public void logGameStart(PlayerSession session) {
        String playerId = session.playerId();
        playerSessions.put(playerId, session);
        sessionStartMap.put(playerId, LocalDateTime.now(clock));
        logToFile("game_log.txt", String.format("Game started on: %s for %s. IP: %s", LocalDateTime.now(clock), playerId, session.ipAddress()), null);
    }

    public void logRoundStart(int roundNumber) { logToFile("game_log.txt", "Round " + roundNumber + " started.", null); }

    public void logGameOutcome(String playerId, String outcome, int chipsChange) {
        logToFile("round_results.txt", String.format("%s has %s the game. Chips Change: %d", playerId, outcome, chipsChange), null);
        if ("won".equals(outcome)) logReward(playerId, 20, "Game won");
        if (sessionStartMap.containsKey(playerId)) {
            Duration playTime = Duration.between(sessionStartMap.remove(playerId), LocalDateTime.now(clock));
            logSessionDuration(playerId, playTime);
        }
    }

    public void logChipTransaction(String playerId, int amount, String source, String destination) {
        logToFile("bets_log.txt", String.format("%s transferred %d chips from %s to %s", playerId, amount, source, destination), null);
    }

    public void flagPlayer(String playerId, String reason) {
        flaggedPlayers.put(playerId, true);
        logToFile("security_log.txt", String.format("Player %s flagged: %s", playerId, reason), null);
    }

    public void logSessionDuration(String playerId, Duration playTime) {
        long minutes = playTime.toMinutes();
        logToFile("game_log.txt", String.format("%s played for %d minutes", playerId, minutes), null);
        if (minutes >= 60) logReward(playerId, 100, "Long session: " + minutes + " minutes");
    }

    public void logReward(String playerId, int points, String reason) {
        if ("all".equals(playerId)) {
            playerSessions.keySet().forEach(id -> logReward(id, points, reason));
            return;
        }
        int currentPoints = loyaltyPoints.getOrDefault(playerId, 0) + points;
        loyaltyPoints.put(playerId, currentPoints);
        logToFile("round_results.txt", String.format("%s earned %d loyalty points for %s. Total: %d", playerId, points, reason, currentPoints), null);
    }

    // Player session record
    public record PlayerSession(String playerId, String ipAddress, LocalDateTime sessionStart, String deviceInfo) {
        public PlayerSession {
            if (playerId == null || playerId.isEmpty()) playerId = UUID.randomUUID().toString();
        }
    }

    // Snapshot for rollback
    public static class Snapshot {
        private final String playerId;
        private final int previousChips;
        private final int betAmount;

        public Snapshot(String playerId, int previousChips, int betAmount) {
            this.playerId = playerId;
            this.previousChips = previousChips;
            this.betAmount = betAmount;
        }

        public String getPlayerId() { return playerId; }
        public int getPreviousChips() { return previousChips; }
        public int getBetAmount() { return betAmount; }
    }
}
