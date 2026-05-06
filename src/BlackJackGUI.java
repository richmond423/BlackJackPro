import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.animation.TranslateTransition;
import javafx.util.Duration;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.util.*;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * FIXED BlackJackGUI - Production Ready
 * Uses card.getImageName() for correct image filenames
 * All fields, imports, and methods included.
 */
public class BlackJackGUI extends Application {
    private static final double CARD_WIDTH = 120;
    private static final double CARD_HEIGHT = 168;

    private boolean isDealerTurn = false;
    private boolean isPlayerTurn = false;
    private boolean isMultiplayer = false;
    private boolean isHost = false;

    private List<String> playlist = new ArrayList<>();
    private int currentSongIndex = 0;
    private MediaPlayer mediaPlayer;
    private MediaPlayer voicePlayer;

    private HBox dealerCards, playerCards, aiCards, remoteCards;
    private Button splitButton, doubleDownButton, hitButton, standButton, restartButton, dealButton,
            aiPlayButton, startNewGameButton, pauseButton, settingsButton, multiplayerButton;
    private Button bet1Button, bet5Button, bet10Button, bet25Button, bet100Button, maxBetButton;

    private Deck deck;
    private Label dealerScore, playerScore, messageLabel, aiScore, aiBetLabel, remoteScore,
            playerChipsLabel, aiChipsLabel, remoteChipsLabel;
    private BlackJackPlayer player, dealer, aiPlayer, remotePlayer;
    private GameLog gameLog;
    private AIStrategy aiStrategy = new CautiousStrategy();
    private static final ObjectMapper mapper = new ObjectMapper();
    private StatsTracker statsTracker = new StatsTracker();
    private ResourceBundle messages;

    private ServerSocket serverSocket;
    private Socket clientSocket;
    private PrintWriter out;
    private BufferedReader in;

    private int currentBet = 0;
    private int aiBet = 0;
    private int remoteBet = 0;

    interface AIStrategy {
        boolean shouldHit(BlackJackPlayer ai, Deck deck);
        int placeBet(int availableChips, Deck deck);
    }

    static class RandomStrategy implements AIStrategy {
        private final Random random = new Random();
        @Override public boolean shouldHit(BlackJackPlayer ai, Deck deck) { return ai.calculateHandValue(ai.getCurrentHand()) < 17; }
        @Override public int placeBet(int availableChips, Deck deck) { return random.nextInt(Math.min(availableChips, 100)) + 1; }
    }

    static class CautiousStrategy implements AIStrategy {
        @Override public boolean shouldHit(BlackJackPlayer ai, Deck deck) { return ai.calculateHandValue(ai.getCurrentHand()) < 15; }
        @Override public int placeBet(int availableChips, Deck deck) { return Math.min(availableChips, new Random().nextInt(20) + 1); }
    }

    static class CardCountingStrategy implements AIStrategy {
        @Override public boolean shouldHit(BlackJackPlayer ai, Deck deck) {
            double trueCount = deck.getTrueCount();
            int threshold = trueCount > 2 ? 18 : trueCount < -2 ? 14 : 16;
            return ai.calculateHandValue(ai.getCurrentHand()) < threshold;
        }
        @Override public int placeBet(int availableChips, Deck deck) {
            double trueCount = deck.getTrueCount();
            return trueCount > 2 ? Math.min(availableChips, 100) : Math.min(availableChips, 10);
        }
    }

    static class StatsTracker {
        int wins = 0, losses = 0, games = 0;
        double totalBets = 0;
        Map<String, Integer> actions = new HashMap<>();

        void recordGame(boolean win, int bet, String action) {
            games++;
            if (win) wins++; else losses++;
            totalBets += bet;
            actions.merge(action, 1, Integer::sum);
        }

        double winPercentage() { return games > 0 ? (double) wins / games * 100 : 0; }
        double avgBet() { return games > 0 ? totalBets / games : 0; }
    }

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        gameLog = new GameLog("resources/logs/game_log.txt");
        messages = ResourceBundle.getBundle("lang.en", Locale.getDefault());

        loadPlaylist();
        playCurrentSong();

        Path deckPath = Paths.get("resources", "deck", "deck.txt");
        deck = new Deck(deckPath.toString(), 8, gameLog, Clock.systemUTC(), new Random(), 416, 0.75, Deck.DeckType.BLACKJACK);

        player = new BlackJackPlayer("Captain", BlackJackChip.create(1000, gameLog, Clock.systemUTC()));
        dealer = new BlackJackPlayer("Dealer Davy Bones", BlackJackChip.create(0, gameLog, Clock.systemUTC()));
        aiPlayer = new BlackJackPlayer("Scurvy Dog", BlackJackChip.create(1000, gameLog, Clock.systemUTC()));
        remotePlayer = new BlackJackPlayer("Remote Pirate", BlackJackChip.create(1000, gameLog, Clock.systemUTC()));

        Label dealerName = new Label(messages.getString("dealer.name"));
        dealerScore = new Label(messages.getString("score.prefix") + "0");
        dealerName.getStyleClass().add("label-pirate");
        dealerScore.getStyleClass().add("label-pirate");

        Label playerName = new Label("Captain");
        playerScore = new Label(messages.getString("score.prefix") + "0");
        playerChipsLabel = new Label(messages.getString("chips.prefix") + player.getChips().getTotalChips());
        playerName.getStyleClass().add("label-pirate");
        playerScore.getStyleClass().add("label-pirate");
        playerChipsLabel.getStyleClass().add("label-pirate");

        Label aiName = new Label(messages.getString("ai.name"));
        aiScore = new Label(messages.getString("score.prefix") + "0");
        aiChipsLabel = new Label(messages.getString("chips.prefix") + aiPlayer.getChips().getTotalChips());
        aiBetLabel = new Label(messages.getString("bet.prefix") + "0");
        aiName.getStyleClass().add("label-pirate");
        aiScore.getStyleClass().add("label-pirate");
        aiChipsLabel.getStyleClass().add("label-pirate");
        aiBetLabel.getStyleClass().add("label-pirate");

        Label remoteName = new Label(messages.getString("remote.name"));
        remoteScore = new Label(messages.getString("score.prefix") + "0");
        remoteChipsLabel = new Label(messages.getString("chips.prefix") + remotePlayer.getChips().getTotalChips());
        remoteName.getStyleClass().add("label-pirate");
        remoteScore.getStyleClass().add("label-pirate");
        remoteChipsLabel.getStyleClass().add("label-pirate");

        messageLabel = new Label(messages.getString("welcome.message"));
        messageLabel.getStyleClass().add("message-label");

        dealerCards = new HBox(10);
        playerCards = new HBox(10);
        aiCards = new HBox(10);
        remoteCards = new HBox(10);

        String buttonStyleClass = "button-pirate";

        splitButton = createStyledButton(messages.getString("split.button"), buttonStyleClass);
        doubleDownButton = createStyledButton(messages.getString("double.button"), buttonStyleClass);
        hitButton = createStyledButton(messages.getString("hit.button"), buttonStyleClass);
        standButton = createStyledButton(messages.getString("stand.button"), buttonStyleClass);
        restartButton = createStyledButton(messages.getString("restart.button"), buttonStyleClass);
        dealButton = createStyledButton(messages.getString("deal.button"), buttonStyleClass);
        aiPlayButton = createStyledButton(messages.getString("aiplay.button"), buttonStyleClass);
        startNewGameButton = createStyledButton(messages.getString("newgame.button"), buttonStyleClass);
        settingsButton = createStyledButton(messages.getString("settings.button"), buttonStyleClass);
        multiplayerButton = createStyledButton(messages.getString("multiplayer.button"), buttonStyleClass);
        pauseButton = createStyledButton(messages.getString("pause.button"), buttonStyleClass);

        bet1Button = createStyledButton(messages.getString("bet1.button"), buttonStyleClass);
        bet5Button = createStyledButton(messages.getString("bet5.button"), buttonStyleClass);
        bet10Button = createStyledButton(messages.getString("bet10.button"), buttonStyleClass);
        bet25Button = createStyledButton(messages.getString("bet25.button"), buttonStyleClass);
        bet100Button = createStyledButton(messages.getString("bet100.button"), buttonStyleClass);
        maxBetButton = createStyledButton(messages.getString("maxbet.button"), buttonStyleClass);

        bet1Button.setOnAction(e -> placeBet(1));
        bet5Button.setOnAction(e -> placeBet(5));
        bet10Button.setOnAction(e -> placeBet(10));
        bet25Button.setOnAction(e -> placeBet(25));
        bet100Button.setOnAction(e -> placeBet(100));
        maxBetButton.setOnAction(e -> placeMaxBet());

        Button saveButton = createStyledButton(messages.getString("save.button"), buttonStyleClass);
        Button loadButton = createStyledButton(messages.getString("load.button"), buttonStyleClass);

        saveButton.setOnAction(e -> saveGameState());
        loadButton.setOnAction(e -> loadGameState());
        pauseButton.setOnAction(e -> toggleMusic());

        settingsButton.setOnAction(e -> showSettingsDialog());
        multiplayerButton.setOnAction(e -> toggleMultiplayer(stage));

        hitButton.setOnAction(e -> playerHit());
        standButton.setOnAction(e -> playerStand());
        dealButton.setOnAction(e -> dealCards());
        restartButton.setOnAction(e -> restartGame());
        aiPlayButton.setOnAction(e -> aiPlay());
        startNewGameButton.setOnAction(e -> resetGame());
        splitButton.setOnAction(e -> playerSplit());
        doubleDownButton.setOnAction(e -> playerDoubleDown());

        HBox bettingButtons = new HBox(10, bet1Button, bet5Button, bet10Button, bet25Button, bet100Button, maxBetButton);
        bettingButtons.setAlignment(Pos.CENTER);

        HBox gameplayButtons = new HBox(10, hitButton, standButton, doubleDownButton, splitButton, aiPlayButton);
        gameplayButtons.setAlignment(Pos.CENTER);

        HBox gameControlButtons = new HBox(10, restartButton, dealButton, startNewGameButton, settingsButton, multiplayerButton);
        gameControlButtons.setAlignment(Pos.CENTER);

        HBox saveLoadButtons = new HBox(10, saveButton, loadButton, pauseButton);
        saveLoadButtons.setAlignment(Pos.CENTER);

        VBox dealerLayout = new VBox(10, dealerName, dealerScore, dealerCards);
        VBox playerLayout = new VBox(10, playerName, playerScore, playerChipsLabel, playerCards);
        VBox aiLayout = new VBox(10, aiName, aiScore, aiChipsLabel, aiBetLabel, aiCards);
        VBox remoteLayout = new VBox(10, remoteName, remoteScore, remoteChipsLabel, remoteCards);

        dealerLayout.setAlignment(Pos.CENTER);
        playerLayout.setAlignment(Pos.CENTER);
        aiLayout.setAlignment(Pos.CENTER);
        remoteLayout.setAlignment(Pos.CENTER);

        VBox mainLayout = new VBox(20, messageLabel, dealerLayout, playerLayout, aiLayout, remoteLayout,
                bettingButtons, gameplayButtons, gameControlButtons, saveLoadButtons);
        mainLayout.setPadding(new Insets(20));
        mainLayout.setAlignment(Pos.TOP_CENTER);

        Image backgroundImage = new Image("file:resources/images/pirate_background.jpg");
        BackgroundImage background = new BackgroundImage(backgroundImage, BackgroundRepeat.NO_REPEAT, BackgroundRepeat.NO_REPEAT,
                BackgroundPosition.CENTER, BackgroundSize.DEFAULT);
        mainLayout.setBackground(new Background(background));

        stage.getIcons().add(new Image("https://raw.githubusercontent.com/github/explore/main/topics/java/java.png"));

        Scene scene = new Scene(mainLayout, 1200, 800);
        scene.getStylesheets().add("file:resources/css/style.css");
        stage.setScene(scene);
        stage.setTitle(messages.getString("game.title"));
        stage.show();

        hitButton.setDisable(true);
        standButton.setDisable(true);
        dealButton.setDisable(true);
        aiPlayButton.setDisable(true);
        splitButton.setDisable(true);
        doubleDownButton.setDisable(true);
    }

    private Button createStyledButton(String text, String style) {
        Button btn = new Button(text);
        btn.getStyleClass().add(style);
        return btn;
    }

    private void toggleMusic() {
        if (mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING) {
            mediaPlayer.pause();
            pauseButton.setText(messages.getString("play.button"));
        } else {
            mediaPlayer.play();
            pauseButton.setText(messages.getString("pause.button"));
        }
    }

    private void loadPlaylist() {
        String[] musicFiles = {
            "Arabesque No. 1. Andantino con moto.mp3",
            "Nocturne in B flat minor, Op. 9 no. 1.mp3",
            "Paul Pitman - Moonlight Sonata Op. 27 No. 2 - III. Presto.mp3",
            "Piano Sonata no. 14 in C#m 'Moonlight', Op. 27 no. 2 - Complete Performance.mp3"
        };

        for (String fileName : musicFiles) {
            File musicFile = new File("resources/music/" + fileName).getAbsoluteFile();
            if (musicFile.exists()) {
                playlist.add(musicFile.toURI().toString());
            } else {
                System.err.println("Music file not found: " + musicFile.getAbsolutePath());
            }
        }
    }

    private void playCurrentSong() {
        if (playlist.isEmpty()) return;
        try {
            String mediaPath = playlist.get(currentSongIndex);
            Media media = new Media(mediaPath);
            mediaPlayer = new MediaPlayer(media);
            mediaPlayer.setOnEndOfMedia(() -> {
                currentSongIndex = (currentSongIndex + 1) % playlist.size();
                playCurrentSong();
            });
            mediaPlayer.setCycleCount(1);
            mediaPlayer.setVolume(0.5);
            mediaPlayer.play();
        } catch (Exception e) {
            System.err.println("Error playing song: " + e.getMessage());
        }
    }

    private void playVoice(String fileName) {
        try {
            File voiceFile = new File("resources/voice/" + fileName).getAbsoluteFile();
            if (voiceFile.exists()) {
                Media media = new Media(voiceFile.toURI().toString());
                voicePlayer = new MediaPlayer(media);
                voicePlayer.play();
            }
        } catch (Exception e) {
            System.err.println("Error playing voice: " + e.getMessage());
        }
    }

    private void dealCardWithAnimation(BlackJackPlayer p, int betAmount, HBox container) {
        Card card = deck.dealCard(p, betAmount);
        Node cardNode = createCardNode(card);
        TranslateTransition tt = new TranslateTransition(Duration.millis(500), cardNode);
        tt.setFromX(-200);
        tt.setToX(0);
        tt.play();
        container.getChildren().add(cardNode);
    }

    private Node createCardNode(Card card) {
        return createImageOrFallback(card.getImageName() + ".png", card.getRank() + "\n" + card.getSuit());
    }

    private Node createCardBackNode() {
        return createImageOrFallback("card_back.png", "Card\nBack");
    }

    private Node createImageOrFallback(String imageName, String fallbackText) {
        File imageFile = new File("resources/images/" + imageName);
        if (imageFile.exists()) {
            Image image = new Image(imageFile.toURI().toString());
            if (!image.isError()) {
                ImageView imageView = new ImageView(image);
                imageView.setFitWidth(CARD_WIDTH);
                imageView.setPreserveRatio(true);
                return imageView;
            }
        }
        return createFallbackCard(fallbackText);
    }

    private Node createFallbackCard(String text) {
        Label label = new Label(text);
        label.setAlignment(Pos.CENTER);
        label.setTextAlignment(TextAlignment.CENTER);
        label.setWrapText(true);
        label.setStyle("-fx-font-weight: bold; -fx-text-fill: #2f2517;");

        StackPane card = new StackPane(label);
        card.setMinSize(CARD_WIDTH, CARD_HEIGHT);
        card.setPrefSize(CARD_WIDTH, CARD_HEIGHT);
        card.setMaxSize(CARD_WIDTH, CARD_HEIGHT);
        card.setStyle("-fx-background-color: #f8f0d8; -fx-border-color: #2f2517; -fx-border-width: 2; -fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 8;");
        return card;
    }

    private void dealCards() {
        if (deck.getRemainingCards() < 25) {
            deck.shuffle();
            messageLabel.setText(messages.getString("deck.reshuffled"));
        }

        if (!isMultiplayer || isHost) {
            aiPlaceBet(null);
            player.clearHands();
            dealer.clearHands();
            aiPlayer.clearHands();
            remotePlayer.clearHands();

            dealCardWithAnimation(player, currentBet, playerCards);
            dealCardWithAnimation(dealer, 0, dealerCards);
            dealCardWithAnimation(aiPlayer, aiBet, aiCards);
            dealCardWithAnimation(remotePlayer, remoteBet, remoteCards);

            dealCardWithAnimation(player, 0, playerCards);
            dealCardWithAnimation(dealer, 0, dealerCards);
            dealCardWithAnimation(aiPlayer, 0, aiCards);
            dealCardWithAnimation(remotePlayer, 0, remoteCards);

            updateScores();
            displayCards();
            messageLabel.setText(messages.getString("player.turn"));
        } else {
            receiveGameState();
        }

        dealButton.setDisable(true);
        disableBettingButtons();
        hitButton.setDisable(false);
        standButton.setDisable(false);
        splitButton.setDisable(!player.canSplit());
        doubleDownButton.setDisable(!player.canDoubleDown());
        isPlayerTurn = true;
    }

    private void playerHit() {
        playVoice("hit_me.mp3");
        dealCardWithAnimation(player, 0, playerCards);
        statsTracker.recordGame(false, 0, "Hit");
        updateScores();
        displayCards();
        if (player.isBust(player.getCurrentHand())) {
            messageLabel.setText(messages.getString("player.bust"));
            endPlayerTurn();
        }
        if (isMultiplayer && isHost) sendGameState();
    }

    private void playerStand() {
        playVoice("stand_ye.mp3");
        statsTracker.recordGame(false, 0, "Stand");
        endPlayerTurn();
        if (isMultiplayer && isHost) sendGameState();
    }

    private void playerSplit() {
        playVoice("split_ye.mp3");
        player.split(deck);
        messageLabel.setText(messages.getString("split.bet") + currentBet + "!");
        gameLog.logBet("Captain", currentBet, player.getChips().getTotalChips());
        updateChipLabels();
        statsTracker.recordGame(false, 0, "Split");
        updateScores();
        displayCards();
        if (isMultiplayer && isHost) sendGameState();
    }

    private void playerDoubleDown() {
        playVoice("double_down.mp3");
        player.doubleDown(deck);
        currentBet = player.getCurrentBet();
        updateChipLabels();
        statsTracker.recordGame(false, 0, "Double Down");
        updateScores();
        displayCards();
        endPlayerTurn();
        if (isMultiplayer && isHost) sendGameState();
    }

    private void saveGameState() {
        try {
            String timestamp = String.valueOf(System.currentTimeMillis());
            Path savePath = Paths.get("resources/saves/save_" + timestamp + ".json");
            Files.createDirectories(savePath.getParent());

            Map<String, Object> saveData = new HashMap<>();
            saveData.put("playerChips", player.getChips().getTotalChips());
            saveData.put("aiChips", aiPlayer.getChips().getTotalChips());
            saveData.put("remoteChips", remotePlayer.getChips().getTotalChips());
            saveData.put("currentBet", currentBet);
            saveData.put("aiBet", aiBet);
            saveData.put("remoteBet", remoteBet);
            saveData.put("playerHands", player.getHands().stream()
                    .map(hand -> hand.stream().map(CardParser::cardToString).collect(Collectors.toList()))
                    .collect(Collectors.toList()));
            saveData.put("dealerHand", dealer.getCurrentHand().stream().map(CardParser::cardToString).collect(Collectors.toList()));
            saveData.put("aiHand", aiPlayer.getCurrentHand().stream().map(CardParser::cardToString).collect(Collectors.toList()));
            saveData.put("remoteHand", remotePlayer.getCurrentHand().stream().map(CardParser::cardToString).collect(Collectors.toList()));
            saveData.put("profile", Map.of("username", player.getName(), "musicVolume", mediaPlayer.getVolume(), "aiDifficulty", aiStrategy.getClass().getSimpleName()));
            saveData.put("stats", Map.of("wins", statsTracker.wins, "losses", statsTracker.losses, "games", statsTracker.games, "totalBets", statsTracker.totalBets, "actions", statsTracker.actions));

            Files.writeString(savePath, mapper.writeValueAsString(saveData));
            System.out.println("Game saved: " + savePath);
        } catch (IOException e) {
            System.err.println("Save error: " + e.getMessage());
        }
    }

    private void loadGameState() {
        try {
            File saveDir = new File("resources/saves");
            if (!saveDir.exists()) return;
            File[] saveFiles = saveDir.listFiles((dir, name) -> name.endsWith(".json"));
            if (saveFiles == null || saveFiles.length == 0) return;

            File latestSave = Arrays.stream(saveFiles).max(Comparator.comparingLong(File::lastModified)).get();
            String json = Files.readString(latestSave.toPath());
            Map<String, Object> saveData = mapper.readValue(json, Map.class);

            player.setChips(player.getChips().resetChips((int) saveData.get("playerChips")));
            aiPlayer.setChips(aiPlayer.getChips().resetChips((int) saveData.get("aiChips")));
            remotePlayer.setChips(remotePlayer.getChips().resetChips((int) saveData.get("remoteChips")));
            currentBet = (int) saveData.get("currentBet");
            aiBet = (int) saveData.get("aiBet");
            remoteBet = (int) saveData.get("remoteBet");
            syncPlayerBets();

            player.clearHands();
            List<List<String>> playerHands = (List<List<String>>) saveData.get("playerHands");
            player.getHands().clear();
            for (List<String> hand : playerHands) {
                player.getHands().add(CardParser.parseDeck(String.join(", ", hand)));
            }
            if (player.getHands().isEmpty()) {
                player.getHands().add(new ArrayList<>());
            }

            dealer.clearHands();
            List<String> dealerHand = (List<String>) saveData.get("dealerHand");
            dealer.getHands().set(0, CardParser.parseDeck(String.join(", ", dealerHand)));

            aiPlayer.clearHands();
            List<String> aiHand = (List<String>) saveData.get("aiHand");
            aiPlayer.getHands().set(0, CardParser.parseDeck(String.join(", ", aiHand)));

            remotePlayer.clearHands();
            List<String> remoteHand = (List<String>) saveData.get("remoteHand");
            remotePlayer.getHands().set(0, CardParser.parseDeck(String.join(", ", remoteHand)));

            Map<String, Object> profile = (Map<String, Object>) saveData.get("profile");
            mediaPlayer.setVolume((Double) profile.get("musicVolume"));
            String aiDifficulty = (String) profile.get("aiDifficulty");
            switch (aiDifficulty) {
                case "RandomStrategy": aiStrategy = new RandomStrategy(); break;
                case "CautiousStrategy": aiStrategy = new CautiousStrategy(); break;
                case "CardCountingStrategy": aiStrategy = new CardCountingStrategy(); break;
            }

            Map<String, Object> stats = (Map<String, Object>) saveData.get("stats");
            statsTracker.wins = (int) stats.get("wins");
            statsTracker.losses = (int) stats.get("losses");
            statsTracker.games = (int) stats.get("games");
            statsTracker.totalBets = (double) stats.get("totalBets");
            statsTracker.actions = (Map<String, Integer>) stats.get("actions");

            updateChipLabels();
            updateScores();
            displayCards();
            System.out.println("Game loaded from " + latestSave.getPath());
        } catch (IOException e) {
            System.err.println("Load error: " + e.getMessage());
        }
    }

    private void showSettingsDialog() {
        Stage settingsStage = new Stage();
        settingsStage.setTitle(messages.getString("settings.title"));

        VBox settingsLayout = new VBox(10);
        settingsLayout.setPadding(new Insets(10));
        settingsLayout.getStyleClass().add("settings-pane");

        Label nameLabel = new Label(messages.getString("name.label"));
        TextField nameField = new TextField(player.getName());
        nameField.getStyleClass().add("text-field-pirate");

        Label musicLabel = new Label(messages.getString("music.label"));
        Slider volumeSlider = new Slider(0, 1, mediaPlayer.getVolume());
        volumeSlider.valueProperty().addListener((obs, oldVal, newVal) -> mediaPlayer.setVolume(newVal.doubleValue()));

        Label aiLabel = new Label(messages.getString("ai.label"));
        ComboBox<String> aiCombo = new ComboBox<>();
        aiCombo.getItems().addAll(messages.getString("ai.random"), messages.getString("ai.cautious"), messages.getString("ai.cardcounter"));
        String current = aiStrategy.getClass().getSimpleName().replace("Strategy", "");
        if (current.equals("CardCounting")) current = "Card Counter";
        aiCombo.setValue(current);
        aiCombo.setOnAction(e -> {
            switch (aiCombo.getValue()) {
                case "Random": aiStrategy = new RandomStrategy(); break;
                case "Cautious": aiStrategy = new CautiousStrategy(); break;
                case "Card Counter": aiStrategy = new CardCountingStrategy(); break;
            }
        });

        Button statsButton = new Button(messages.getString("stats.button"));
        statsButton.getStyleClass().add("button-pirate");
        statsButton.setOnAction(e -> showStatsDashboard());

        Button saveSettings = new Button(messages.getString("save.settings.button"));
        saveSettings.getStyleClass().add("button-pirate");
        saveSettings.setOnAction(e -> {
            player = new BlackJackPlayer(nameField.getText(), player.getChips());
            saveGameState();
            settingsStage.close();
        });

        settingsLayout.getChildren().addAll(nameLabel, nameField, musicLabel, volumeSlider, aiLabel, aiCombo, statsButton, saveSettings);
        Scene settingsScene = new Scene(settingsLayout, 300, 400);
        settingsScene.getStylesheets().add("file:resources/css/style.css");
        settingsStage.setScene(settingsScene);
        settingsStage.show();
    }

    private void showStatsDashboard() {
        Stage statsStage = new Stage();
        statsStage.setTitle(messages.getString("stats.title"));

        VBox statsLayout = new VBox(10);
        statsLayout.setPadding(new Insets(10));
        statsLayout.getStyleClass().add("settings-pane");

        Label winsLabel = new Label(messages.getString("stats.wins") + statsTracker.wins);
        Label lossesLabel = new Label(messages.getString("stats.losses") + statsTracker.losses);
        Label winPercentLabel = new Label(messages.getString("stats.winpercent") + String.format("%.2f", statsTracker.winPercentage()));
        Label avgBetLabel = new Label(messages.getString("stats.avgbet") + String.format("%.2f", statsTracker.avgBet()));
        Label gamesLabel = new Label(messages.getString("stats.games") + statsTracker.games);

        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        BarChart<String, Number> actionChart = new BarChart<>(xAxis, yAxis);
        actionChart.setTitle(messages.getString("stats.actionchart"));
        xAxis.setLabel(messages.getString("stats.action"));
        yAxis.setLabel(messages.getString("stats.count"));

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName(messages.getString("stats.actions"));
        statsTracker.actions.forEach((action, count) -> series.getData().add(new XYChart.Data<>(action, count)));
        actionChart.getData().add(series);

        statsLayout.getChildren().addAll(winsLabel, lossesLabel, winPercentLabel, avgBetLabel, gamesLabel, actionChart);
        Scene statsScene = new Scene(statsLayout, 400, 500);
        statsScene.getStylesheets().add("file:resources/css/style.css");
        statsStage.setScene(statsScene);
        statsStage.show();
    }

    private void toggleMultiplayer(Stage stage) {
        if (!isMultiplayer) {
            TextInputDialog dialog = new TextInputDialog("localhost:12345");
            dialog.setTitle(messages.getString("multiplayer.title"));
            dialog.setHeaderText(messages.getString("multiplayer.header"));
            dialog.setContentText(messages.getString("multiplayer.prompt"));
            Optional<String> result = dialog.showAndWait();

            result.ifPresent(address -> {
                String[] parts = address.split(":");
                String host = parts[0];
                int port = Integer.parseInt(parts[1]);
                try {
                    if (host.equals("host")) {
                        isHost = true;
                        serverSocket = new ServerSocket(port);
                        new Thread(() -> {
                            try {
                                clientSocket = serverSocket.accept();
                                out = new PrintWriter(clientSocket.getOutputStream(), true);
                                in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                                messageLabel.setText(messages.getString("multiplayer.hosting"));
                            } catch (IOException e) {
                                messageLabel.setText(messages.getString("multiplayer.error") + e.getMessage());
                            }
                        }).start();
                    } else {
                        clientSocket = new Socket(host, port);
                        out = new PrintWriter(clientSocket.getOutputStream(), true);
                        in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                        messageLabel.setText(messages.getString("multiplayer.connected"));
                    }
                    isMultiplayer = true;
                } catch (IOException e) {
                    messageLabel.setText(messages.getString("multiplayer.error") + e.getMessage());
                }
            });
        } else {
            shutdownMultiplayer();
        }
    }

    private void shutdownMultiplayer() {
        try {
            if (serverSocket != null) serverSocket.close();
            if (clientSocket != null) clientSocket.close();
        } catch (IOException ignored) {}
        isMultiplayer = false;
        isHost = false;
        messageLabel.setText(messages.getString("multiplayer.disconnected"));
    }

    private void sendGameState() {
        if (!isMultiplayer || !isHost || out == null) return;
        try {
            Map<String, Object> state = new HashMap<>();
            state.put("playerHands", player.getHands().stream()
                    .map(hand -> hand.stream().map(CardParser::cardToString).collect(Collectors.toList()))
                    .collect(Collectors.toList()));
            state.put("dealerHand", dealer.getCurrentHand().stream().map(CardParser::cardToString).collect(Collectors.toList()));
            state.put("aiHand", aiPlayer.getCurrentHand().stream().map(CardParser::cardToString).collect(Collectors.toList()));
            state.put("remoteBet", remoteBet);
            state.put("remoteChips", remotePlayer.getChips().getTotalChips());
            out.println(mapper.writeValueAsString(state));
        } catch (IOException e) {
            messageLabel.setText(messages.getString("multiplayer.error") + e.getMessage());
        }
    }

    private void receiveGameState() {
        if (!isMultiplayer || isHost || in == null) return;
        try {
            String json = in.readLine();
            if (json != null) {
                Map<String, Object> state = mapper.readValue(json, Map.class);
                player.clearHands();
                List<List<String>> playerHands = (List<List<String>>) state.get("playerHands");
                for (List<String> hand : playerHands) {
                    player.getHands().add(CardParser.parseDeck(String.join(", ", hand)));
                }
                dealer.clearHands();
                List<String> dealerHand = (List<String>) state.get("dealerHand");
                dealer.getHands().set(0, CardParser.parseDeck(String.join(", ", dealerHand)));
                aiPlayer.clearHands();
                List<String> aiHand = (List<String>) state.get("aiHand");
                aiPlayer.getHands().set(0, CardParser.parseDeck(String.join(", ", aiHand)));
                remoteBet = (int) state.get("remoteBet");
                remotePlayer.getChips().resetChips((int) state.get("remoteChips"));
                updateChipLabels();
                updateScores();
                displayCards();
            }
        } catch (IOException e) {
            messageLabel.setText(messages.getString("multiplayer.error") + e.getMessage());
        }
    }

    private void endPlayerTurn() {
        hitButton.setDisable(true);
        standButton.setDisable(true);
        aiPlayButton.setDisable(true);
        splitButton.setDisable(true);
        doubleDownButton.setDisable(true);
        aiPlay();
        isDealerTurn = true;
        dealerPlay();
        displayCards();
        if (isMultiplayer && isHost) sendGameState();
    }

    private void dealerPlay() {
        while (dealer.calculateHandValue(dealer.getCurrentHand()) < 17) {
            dealCardWithAnimation(dealer, 0, dealerCards);
        }
        if (dealer.isBust(dealer.getCurrentHand())) {
            messageLabel.setText(messages.getString("dealer.bust"));
            statsTracker.recordGame(true, currentBet, "Dealer Bust");
        } else {
            messageLabel.setText(messages.getString("dealer.stands"));
        }
        updateScores();
        displayCards();
        determineWinner();
    }

    private void aiPlay() {
        messageLabel.setText(messages.getString("ai.turn"));
        while (aiStrategy.shouldHit(aiPlayer, deck)) {
            dealCardWithAnimation(aiPlayer, 0, aiCards);
        }
        if (aiPlayer.isBust(aiPlayer.getCurrentHand())) {
            messageLabel.setText(messages.getString("ai.bust"));
        } else {
            messageLabel.setText(messages.getString("ai.stands"));
        }
        updateScores();
        displayCards();
    }

    private void updateScores() {
        dealerScore.setText(messages.getString("score.prefix") + dealer.calculateHandValue(dealer.getCurrentHand()));
        playerScore.setText(messages.getString("score.prefix") + player.calculateHandValue(player.getCurrentHand()));
        aiScore.setText(messages.getString("score.prefix") + aiPlayer.calculateHandValue(aiPlayer.getCurrentHand()));
        remoteScore.setText(messages.getString("score.prefix") + remotePlayer.calculateHandValue(remotePlayer.getCurrentHand()));
    }

    private void updateChipLabels() {
        if (playerChipsLabel != null) {
            playerChipsLabel.setText(messages.getString("chips.prefix") + player.getChips().getTotalChips());
        }
        if (aiChipsLabel != null) {
            aiChipsLabel.setText(messages.getString("chips.prefix") + aiPlayer.getChips().getTotalChips());
        }
        if (remoteChipsLabel != null) {
            remoteChipsLabel.setText(messages.getString("chips.prefix") + remotePlayer.getChips().getTotalChips());
        }
        if (aiBetLabel != null) {
            aiBetLabel.setText(messages.getString("bet.prefix") + aiBet);
        }
    }

    private void syncPlayerBets() {
        player.setCurrentBet(currentBet);
        aiPlayer.setCurrentBet(aiBet);
        remotePlayer.setCurrentBet(remoteBet);
    }

    private void aiPlaceBet(Integer specificBet) {
        if (aiPlayer.getChips().getTotalChips() > 0) {
            int betAmount = specificBet != null ? Math.min(specificBet, aiPlayer.getChips().getTotalChips()) : aiStrategy.placeBet(aiPlayer.getChips().getTotalChips(), deck);
            aiBet = betAmount;
            aiPlayer.setChips(aiPlayer.getChips().removeChips(betAmount));
            aiPlayer.setCurrentBet(aiBet);
            updateChipLabels();
            messageLabel.setText(messages.getString("ai.bets") + aiBet + " chips!");
            gameLog.logBet("Scurvy Dog", aiBet, aiPlayer.getChips().getTotalChips());
        } else {
            aiBet = 0;
            aiPlayer.setCurrentBet(aiBet);
            updateChipLabels();
            messageLabel.setText(messages.getString("ai.nochips"));
        }
    }

    private void displayCards() {
        dealerCards.getChildren().clear();
        playerCards.getChildren().clear();
        aiCards.getChildren().clear();
        remoteCards.getChildren().clear();

        for (int i = 0; i < dealer.getCurrentHand().size(); i++) {
            if (i == 0 && !isDealerTurn) {
                dealerCards.getChildren().add(createCardBackNode());
            } else {
                Card card = dealer.getCurrentHand().get(i);
                dealerCards.getChildren().add(createCardNode(card));
            }
        }
        if (!isDealerTurn) dealerScore.setText(messages.getString("score.prefix") + "?");

        for (int handIndex = 0; handIndex < player.getHands().size(); handIndex++) {
            List<Card> hand = player.getHands().get(handIndex);
            HBox handDisplay = new HBox(10);
            for (Card card : hand) {
                handDisplay.getChildren().add(createCardNode(card));
            }
            playerCards.getChildren().add(handDisplay);
        }

        for (Card card : aiPlayer.getCurrentHand()) {
            aiCards.getChildren().add(createCardNode(card));
        }

        for (Card card : remotePlayer.getCurrentHand()) {
            remoteCards.getChildren().add(createCardNode(card));
        }

        updateScores();
    }

    private void restartGame() {
        currentBet = 0;
        aiBet = 0;
        remoteBet = 0;
        syncPlayerBets();
        player.clearHands();
        dealer.clearHands();
        aiPlayer.clearHands();
        remotePlayer.clearHands();
        updateScores();
        dealerCards.getChildren().clear();
        playerCards.getChildren().clear();
        aiCards.getChildren().clear();
        remoteCards.getChildren().clear();
        isDealerTurn = false;
        isPlayerTurn = false;
        dealButton.setDisable(true);
        hitButton.setDisable(true);
        standButton.setDisable(true);
        aiPlayButton.setDisable(true);
        splitButton.setDisable(true);
        doubleDownButton.setDisable(true);
        messageLabel.setText(messages.getString("newgame.message"));
        enableBettingButtons();
        updateChipLabels();
        gameLog.log("Game restarted. All hands and game state have been reset.");
        if (isMultiplayer && isHost) sendGameState();
    }

    private void determineWinner() {
        int playerValue = player.calculateHandValue(player.getCurrentHand());
        int dealerValue = dealer.calculateHandValue(dealer.getCurrentHand());
        int aiValue = aiPlayer.calculateHandValue(aiPlayer.getCurrentHand());
        int remoteValue = remotePlayer.calculateHandValue(remotePlayer.getCurrentHand());

        StringBuilder resultMessage = new StringBuilder();

        if (playerValue > 21) {
            resultMessage.append(messages.getString("player.bust"));
            statsTracker.recordGame(false, currentBet, "Bust");
            gameLog.logGameOutcome("Captain", "lost", -currentBet);
        } else if (dealerValue > 21 || playerValue > dealerValue) {
            player.setChips(player.getChips().addChips(currentBet * 2));
            resultMessage.append(messages.getString("player.wins"));
            statsTracker.recordGame(true, currentBet, "Win");
            gameLog.logGameOutcome("Captain", "won", currentBet * 2);
            playVoice("ye_win.mp3");
        } else if (playerValue == dealerValue) {
            player.setChips(player.getChips().addChips(currentBet));
            resultMessage.append(messages.getString("player.ties"));
            statsTracker.recordGame(false, currentBet, "Tie");
            gameLog.logGameOutcome("Captain", "tied", currentBet);
        } else {
            resultMessage.append(messages.getString("player.loses"));
            statsTracker.recordGame(false, currentBet, "Loss");
            gameLog.logGameOutcome("Captain", "lost", -currentBet);
        }

        if (aiValue > 21) {
            resultMessage.append(messages.getString("ai.bust"));
            statsTracker.recordGame(false, aiBet, "Bust");
            gameLog.logGameOutcome("Scurvy Dog", "lost", -aiBet);
        } else if (dealerValue > 21 || aiValue > dealerValue) {
            aiPlayer.setChips(aiPlayer.getChips().addChips(aiBet * 2));
            resultMessage.append(messages.getString("ai.wins"));
            statsTracker.recordGame(true, aiBet, "Win");
            gameLog.logGameOutcome("Scurvy Dog", "won", aiBet * 2);
        } else if (aiValue == dealerValue) {
            aiPlayer.setChips(aiPlayer.getChips().addChips(aiBet));
            resultMessage.append(messages.getString("ai.ties"));
            statsTracker.recordGame(false, aiBet, "Tie");
            gameLog.logGameOutcome("Scurvy Dog", "tied", aiBet);
        } else {
            resultMessage.append(messages.getString("ai.loses"));
            statsTracker.recordGame(false, aiBet, "Loss");
            gameLog.logGameOutcome("Scurvy Dog", "lost", -aiBet);
        }

        if (isMultiplayer) {
            if (remoteValue > 21) {
                resultMessage.append(messages.getString("remote.bust"));
                statsTracker.recordGame(false, remoteBet, "Bust");
                gameLog.logGameOutcome("Remote Pirate", "lost", -remoteBet);
            } else if (dealerValue > 21 || remoteValue > dealerValue) {
                remotePlayer.setChips(remotePlayer.getChips().addChips(remoteBet * 2));
                resultMessage.append(messages.getString("remote.wins"));
                statsTracker.recordGame(true, remoteBet, "Win");
                gameLog.logGameOutcome("Remote Pirate", "won", remoteBet * 2);
            } else if (remoteValue == dealerValue) {
                remotePlayer.setChips(remotePlayer.getChips().addChips(remoteBet));
                resultMessage.append(messages.getString("remote.ties"));
                statsTracker.recordGame(false, remoteBet, "Tie");
                gameLog.logGameOutcome("Remote Pirate", "tied", remoteBet);
            } else {
                resultMessage.append(messages.getString("remote.loses"));
                statsTracker.recordGame(false, remoteBet, "Loss");
                gameLog.logGameOutcome("Remote Pirate", "lost", -remoteBet);
            }
        }

        currentBet = 0;
        aiBet = 0;
        remoteBet = 0;
        syncPlayerBets();

        updateChipLabels();
        enableBettingButtons();
        dealButton.setDisable(true);
        messageLabel.setText(resultMessage.toString());
        if (isMultiplayer && isHost) sendGameState();
    }

    private void disableGameplayButtons() {
        hitButton.setDisable(true);
        standButton.setDisable(true);
        dealButton.setDisable(true);
        aiPlayButton.setDisable(true);
        splitButton.setDisable(true);
        doubleDownButton.setDisable(true);
    }

    private void placeBet(int amount) {
        if (player.getChips().hasEnough(amount)) {
            currentBet += amount;
            player.setChips(player.getChips().removeChips(amount));
            player.setCurrentBet(currentBet);
            updateChipLabels();
            messageLabel.setText(messages.getString("player.bets") + amount + "!");
            dealButton.setDisable(false);
            splitButton.setDisable(!player.canSplit());
            doubleDownButton.setDisable(!player.canDoubleDown());
            statsTracker.recordGame(false, amount, "Bet");
            gameLog.logBet("Captain", amount, player.getChips().getTotalChips());
            if (isMultiplayer && isHost) sendGameState();
        } else {
            messageLabel.setText(messages.getString("player.nochips.bet"));
        }
    }

    private void placeSplitBet() {
        if (player.getChips().hasEnough(currentBet)) {
            player.getChips().removeChips(currentBet);
            messageLabel.setText(messages.getString("split.bet") + currentBet + "!");
            gameLog.logBet("Captain", currentBet, player.getChips().getTotalChips());
            if (isMultiplayer && isHost) sendGameState();
        } else {
            messageLabel.setText(messages.getString("split.nochips"));
        }
    }

    private void placeMaxBet() {
        try {
            int maxBet = player.getChips().maxBet();
            if (maxBet > 0) {
                currentBet = maxBet;
                player.setChips(player.getChips().removeChips(maxBet));
                player.setCurrentBet(currentBet);
                updateChipLabels();
                messageLabel.setText(player.getName() + " " + messages.getString("maxbet.message") + currentBet + " chips!");
                disableBettingButtons();
                dealButton.setDisable(false);
                statsTracker.recordGame(false, maxBet, "Max Bet");
                gameLog.logBet(player.getName(), currentBet, player.getChips().getTotalChips());
                if (isMultiplayer && isHost) sendGameState();
            } else {
                messageLabel.setText(messages.getString("player.nochips.bet"));
            }
        } catch (Exception e) {
            messageLabel.setText(messages.getString("maxbet.error") + e.getMessage());
        }
    }

    private void disableBettingButtons() {
        bet1Button.setDisable(true);
        bet5Button.setDisable(true);
        bet10Button.setDisable(true);
        bet25Button.setDisable(true);
        bet100Button.setDisable(true);
        maxBetButton.setDisable(true);
    }

    private void enableBettingButtons() {
        bet1Button.setDisable(false);
        bet5Button.setDisable(false);
        bet10Button.setDisable(false);
        bet25Button.setDisable(false);
        bet100Button.setDisable(false);
        maxBetButton.setDisable(false);
    }

    private void resetGame() {
        player.setChips(player.getChips().resetChips(1000));
        aiPlayer.setChips(aiPlayer.getChips().resetChips(1000));
        remotePlayer.setChips(remotePlayer.getChips().resetChips(1000));
        currentBet = 0;
        aiBet = 0;
        remoteBet = 0;
        syncPlayerBets();
        updateChipLabels();
        isDealerTurn = false;
        isPlayerTurn = false;
        restartGame();
        startNewGameButton.setDisable(true);
        dealButton.setDisable(true);
        enableBettingButtons();
        messageLabel.setText(messages.getString("welcome.message"));
        displayCards();
        if (isMultiplayer && isHost) sendGameState();
    }

}
