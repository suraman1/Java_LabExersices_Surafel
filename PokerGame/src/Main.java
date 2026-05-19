import javafx.application.Application;
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.scene.text.*;
import javafx.stage.Stage;

import java.util.*;

public class Main extends Application {

    private final GridPane gameGrid = new GridPane();
    private final List<Card> allCards = new ArrayList<>();
    private final List<Card> player1Stack = new ArrayList<>();
    private final List<Card> player2Stack = new ArrayList<>();
    private final List<Card> faceUpCards = new ArrayList<>();

    private final VBox player1StackBox = new VBox(10);
    private final VBox player2StackBox = new VBox(10);
    private final Label player1Score = new Label("0");
    private final Label player2Score = new Label("0");
    private final Label player1Name = new Label("Player 1");
    private final Label player2Name = new Label("Player 2");
    private final Label turnLabel = new Label("");

    private boolean playerOneTurn = true;
    private boolean isVsComputer = false;
    private int player1Wins = 0;
    private int player2Wins = 0;
    private boolean computerPlaying = false;

    @Override
    public void start(Stage stage) {
        Label title = new Label("Poker Game");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 36));
        StackPane topPane = new StackPane(title);
        topPane.setPadding(new Insets(10));
        VBox topSection = new VBox(topPane);

        VBox playerFirst = createScoreBox(player1Name, player1Score, player1StackBox);
        VBox playerSecond = createScoreBox(player2Name, player2Score, player2StackBox);
        turnLabel.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        HBox turnBox = new HBox(turnLabel);
        turnBox.setAlignment(Pos.CENTER);
        turnBox.setPadding(new Insets(5));
        VBox scoreColumn = new VBox(20, playerFirst, playerSecond, turnBox);
        scoreColumn.setAlignment(Pos.TOP_CENTER);
        scoreColumn.setPadding(new Insets(20));
        scoreColumn.setStyle("-fx-background-color: white; -fx-background-radius: 10;");
        scoreColumn.setPrefWidth(250);

        VBox leftWrapper = new VBox(scoreColumn);
        leftWrapper.setAlignment(Pos.TOP_CENTER);
        leftWrapper.setPadding(new Insets(20));

        VBox btnBox = createButtons();

        setupGameGrid();

        BorderPane root = new BorderPane();
        root.setTop(topSection);
        root.setCenter(gameGrid);
        root.setLeft(leftWrapper);
        root.setRight(btnBox);
        root.setStyle("-fx-background-color: lightblue;");

        Scene scene = new Scene(root, 1300, 800);
        stage.setScene(scene);
        stage.setTitle("Poker Game");
        stage.show();

        startGame();
    }

    private VBox createScoreBox(Label name, Label score, VBox stackBox) {
        name.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        score.setFont(Font.font("Arial", FontWeight.BOLD, 18));

        Label stackLabel = new Label("Won Cards:");
        stackLabel.setFont(Font.font("Arial", FontWeight.BOLD, 14));

        VBox box = new VBox(10, name, score, stackLabel, stackBox);
        box.setAlignment(Pos.TOP_CENTER);
        box.setStyle("-fx-background-color: #f0f0f0; -fx-padding: 15; -fx-background-radius: 10;");

        return box;
    }

    private void setupGameGrid() {
        gameGrid.setAlignment(Pos.CENTER);
        gameGrid.setHgap(8);
        gameGrid.setVgap(8);
        gameGrid.setPadding(new Insets(20));
    }

    private VBox createButtons() {
        VBox box = new VBox(15);
        box.setPadding(new Insets(20));
        box.setAlignment(Pos.TOP_CENTER);

        Button vsComputer = new Button("VS Computer");
        Button vsPlayer = new Button("VS Player");
        Button restart = new Button("Restart");
        Button stats = new Button("Stats");
        for (Button b : new Button[]{vsComputer, vsPlayer, restart, stats}) {
            b.setStyle("-fx-background-color:#2c3e50; -fx-text-fill:white; -fx-font-size:14; -fx-padding:8 15;");
        }

        vsComputer.setOnAction(e -> {
            isVsComputer = true;
            player1Name.setText("Player");
            player2Name.setText("Computer");
            restartGame();
        });
        vsPlayer.setOnAction(e -> {
            isVsComputer = false;
            player1Name.setText("Player 1");
            player2Name.setText("Player 2");
            restartGame();
        });
        restart.setOnAction(e -> restartGame());
        stats.setOnAction(e -> showWinner());

        box.getChildren().addAll(vsComputer, vsPlayer, restart, stats);
        return box;
    }

    private void startGame() {
        loadDeck();
        createCardGrid();
        updatePlayerStacksDisplay();
        updateTurnDisplay();
    }

    private void loadDeck() {
        allCards.clear();
        String[] suits = {"clubs", "diamonds", "hearts", "spades"};
        String[] ranks = {"2", "3", "4", "5", "6", "7", "8", "9", "10", "jack", "queen", "king", "ace"};

        for (String s : suits) {
            for (String r : ranks) {
                try {
                    Image img = new Image(getClass().getResourceAsStream("/cards/" + r + "_of_" + s + ".png"));
                    allCards.add(new Card(r, s, img));
                } catch (Exception e) {
                    System.err.println("Could not load card: " + r + "_of_" + s + ".png");
                }
            }
        }
        Collections.shuffle(allCards);
    }

    private void createCardGrid() {
        gameGrid.getChildren().clear();
        faceUpCards.clear();

        Image back = new Image(getClass().getResourceAsStream("/cards/back.png"));
        int totalCards = Math.min(48, allCards.size());

        for (int i = 0; i < totalCards; i++) {
            Card card = allCards.get(i);
            card.view.setImage(back);
            card.faceUp = false;
            card.view.setFitWidth(90);
            card.view.setFitHeight(135);
            card.view.setPreserveRatio(true);

            int row = i / 8;
            int col = i % 8;

            card.view.setOnMouseClicked(e -> {
                if (computerPlaying) return;
                if (isVsComputer && !playerOneTurn) return;
                handleCardClick(card);
            });
            gameGrid.add(card.view, col, row);
        }

        Random rand = new Random();
        int cardsToFaceUp = Math.min(4, totalCards);
        Set<Integer> chosenIndices = new HashSet<>();

        while (chosenIndices.size() < cardsToFaceUp) {
            int index = rand.nextInt(totalCards);
            chosenIndices.add(index);
        }

        for (int index : chosenIndices) {
            Card card = allCards.get(index);
            card.view.setImage(card.front);
            card.faceUp = true;
            faceUpCards.add(card);
        }
    }

    private void handleCardClick(Card clickedCard) {
        if (clickedCard.faceUp) return;
        if (computerPlaying) return;

        clickedCard.view.setImage(clickedCard.front);
        clickedCard.faceUp = true;

        Card matchingCard = null;
        for (Card faceUp : faceUpCards) {
            if (faceUp.rank.equals(clickedCard.rank)) {
                matchingCard = faceUp;
                break;
            }
        }

        if (matchingCard != null) {
            removeCardsFromGrid(clickedCard, matchingCard);

            List<Card> currentStack = playerOneTurn ? player1Stack : player2Stack;
            currentStack.add(clickedCard);
            currentStack.add(matchingCard);
            faceUpCards.remove(matchingCard);

            updateScores();
            updatePlayerStacksDisplay();

            stealOpponentCard();
            checkForMoreMatches();

            updateTurnDisplay();

            if (isVsComputer && !playerOneTurn && !allCards.isEmpty()) {
                computerTurn();
            }
        } else {
            List<Card> opponentStack = playerOneTurn ? player2Stack : player1Stack;

            if (!opponentStack.isEmpty()) {
                Card topOpponent = opponentStack.get(opponentStack.size() - 1);

                if (topOpponent.rank.equals(clickedCard.rank)) {
                    opponentStack.remove(topOpponent);
                    List<Card> currentStack = playerOneTurn ? player1Stack : player2Stack;
                    currentStack.add(clickedCard);
                    currentStack.add(topOpponent);

                    gameGrid.getChildren().remove(clickedCard.view);
                    allCards.remove(clickedCard);
                    reorganizeGrid();

                    updateScores();
                    updatePlayerStacksDisplay();
                    updateTurnDisplay();

                    if (isVsComputer && !playerOneTurn && !allCards.isEmpty()) {
                        computerTurn();
                    }
                    return;
                }
            }

            faceUpCards.add(clickedCard);
            playerOneTurn = !playerOneTurn;
            updateTurnDisplay();

            if (isVsComputer && !playerOneTurn && !allCards.isEmpty()) {
                computerTurn();
            }
        }
    }

    private void removeCardsFromGrid(Card card1, Card card2) {
        gameGrid.getChildren().remove(card1.view);
        gameGrid.getChildren().remove(card2.view);
        allCards.remove(card1);
        allCards.remove(card2);
        reorganizeGrid();
    }

    private void reorganizeGrid() {
        List<Card> remainingCards = new ArrayList<>(allCards);
        gameGrid.getChildren().clear();

        for (int i = 0; i < remainingCards.size(); i++) {
            Card card = remainingCards.get(i);
            int row = i / 8;
            int col = i % 8;
            gameGrid.add(card.view, col, row);
        }
    }

    private void stealOpponentCard() {
        List<Card> currentStack = playerOneTurn ? player1Stack : player2Stack;
        List<Card> opponentStack = playerOneTurn ? player2Stack : player1Stack;

        while (!opponentStack.isEmpty() && !currentStack.isEmpty()) {
            Card topOpponent = opponentStack.get(opponentStack.size() - 1);
            Card lastWon = currentStack.get(currentStack.size() - 1);

            if (topOpponent.rank.equals(lastWon.rank)) {
                opponentStack.remove(topOpponent);
                currentStack.add(topOpponent);
                updateScores();
                updatePlayerStacksDisplay();
            } else {
                break;
            }
        }
    }

    private void checkForMoreMatches() {
        List<Card> faceUpList = new ArrayList<>(faceUpCards);
        boolean matched = false;

        for (int i = 0; i < faceUpList.size(); i++) {
            for (int j = i + 1; j < faceUpList.size(); j++) {
                if (faceUpList.get(i).rank.equals(faceUpList.get(j).rank)) {
                    Card card1 = faceUpList.get(i);
                    Card card2 = faceUpList.get(j);

                    removeCardsFromGrid(card1, card2);

                    List<Card> currentStack = playerOneTurn ? player1Stack : player2Stack;
                    currentStack.add(card1);
                    currentStack.add(card2);
                    faceUpCards.remove(card1);
                    faceUpCards.remove(card2);

                    updateScores();
                    updatePlayerStacksDisplay();
                    stealOpponentCard();
                    matched = true;
                    break;
                }
            }
            if (matched) {
                checkForMoreMatches();
                break;
            }
        }
    }

    private void computerTurn() {
        if (computerPlaying) return;
        if (allCards.isEmpty()) return;
        if (playerOneTurn) return;

        computerPlaying = true;

        new Thread(() -> {
            try {
                Thread.sleep(800);
            } catch (InterruptedException e) {}

            javafx.application.Platform.runLater(() -> {
                if (!playerOneTurn && !allCards.isEmpty()) {
                    List<Card> faceDownCards = new ArrayList<>();
                    for (Card card : allCards) {
                        if (!card.faceUp) {
                            faceDownCards.add(card);
                        }
                    }

                    if (!faceDownCards.isEmpty()) {
                        Random rand = new Random();
                        Card selectedCard = faceDownCards.get(rand.nextInt(faceDownCards.size()));

                        selectedCard.view.setEffect(new DropShadow(20, Color.YELLOW));

                        new Thread(() -> {
                            try {
                                Thread.sleep(2000);
                            } catch (InterruptedException e) {}
                            javafx.application.Platform.runLater(() -> {
                                selectedCard.view.setEffect(new DropShadow());
                                computerPlaying = false;
                                handleCardClick(selectedCard);
                            });
                        }).start();
                    } else {
                        computerPlaying = false;
                    }
                } else {
                    computerPlaying = false;
                }
            });
        }).start();
    }

    private void updateScores() {
        player1Score.setText(String.valueOf(player1Stack.size()));
        player2Score.setText(String.valueOf(player2Stack.size()));
    }

    private void updatePlayerStacksDisplay() {
        player1StackBox.getChildren().clear();
        if (!player1Stack.isEmpty()) {
            Card topCard = player1Stack.get(player1Stack.size() - 1);
            ImageView topView = new ImageView(topCard.front);
            topView.setFitWidth(80);
            topView.setFitHeight(120);
            topView.setPreserveRatio(true);
            topView.setEffect(new DropShadow());
            player1StackBox.getChildren().add(topView);

            Label count = new Label("Count: " + player1Stack.size());
            count.setFont(Font.font("Arial", 12));
            player1StackBox.getChildren().add(count);
        } else {
            Label empty = new Label("No cards");
            player1StackBox.getChildren().add(empty);
        }

        player2StackBox.getChildren().clear();
        if (!player2Stack.isEmpty()) {
            Card topCard = player2Stack.get(player2Stack.size() - 1);
            ImageView topView = new ImageView(topCard.front);
            topView.setFitWidth(80);
            topView.setFitHeight(120);
            topView.setPreserveRatio(true);
            topView.setEffect(new DropShadow());
            player2StackBox.getChildren().add(topView);

            Label count = new Label("Count: " + player2Stack.size());
            count.setFont(Font.font("Arial", 12));
            player2StackBox.getChildren().add(count);
        } else {
            Label empty = new Label("No cards");
            player2StackBox.getChildren().add(empty);
        }
    }

    private void updateTurnDisplay() {
        String currentPlayer = playerOneTurn ? player1Name.getText() : player2Name.getText();
        turnLabel.setText("Current Turn: " + currentPlayer);
    }
    private void showWinner() {
        Stage s = new Stage();
        VBox box = new VBox(10);
        box.setMinHeight(100);
        box.setMaxWidth(100);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(20));
        box.setStyle("-fx-background-color: lightblue;");
        String winner = player1Wins > player2Wins ? "Player 1" :  isVsComputer ? "Computer" : "Player 2";
        Label winnerLabel = new Label(winner);

        box.getChildren().add(winnerLabel);
        Scene scene = new Scene(box, 250, 150);
        s.setScene(scene);
        s.setTitle("Game Statistics");
        s.show();

    }
    private void restartGame() {
        gameGrid.getChildren().clear();
        allCards.clear();
        player1Stack.clear();
        player2Stack.clear();
        faceUpCards.clear();

        player1Score.setText("0");
        player2Score.setText("0");
        playerOneTurn = true;
        computerPlaying = false;

        startGame();
    }

    private void showStats() {
        Stage s = new Stage();
        VBox box = new VBox(10);
        box.setMinHeight(100);
        box.setMaxWidth(100);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(20));
        box.setStyle("-fx-background-color: lightblue;");
        Label l1 = new Label(player1Name.getText() + " Wins: " + player1Wins);
        Label l2 = new Label(player2Name.getText() + " Wins: " + player2Wins);

        box.getChildren().addAll(l1, l2);

        Scene scene = new Scene(box, 250, 150);
        s.setScene(scene);
        s.setTitle("Game Statistics");
        s.show();
    }

    private static class Card {
        String rank, suit;
        Image front;
        ImageView view = new ImageView();
        boolean faceUp = false;

        Card(String r, String s, Image img) {
            rank = r;
            suit = s;
            front = img;
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}