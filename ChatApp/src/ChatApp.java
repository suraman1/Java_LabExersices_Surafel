import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.*;
import javafx.stage.*;

import java.util.List;

public class ChatApp extends Application {

    private final Client client = new Client();
    private StackPane  root;
    private BorderPane mainLayout;

    private VBox       dmListBox;
    private VBox       groupListBox;
    private TabPane    sidebarTabs;

    private VBox       messageBox;
    private ScrollPane chatScroll;
    private TextField  inputField;
    private Label      chatTitleLabel;

    private String  activeScopeType;
    private int     activeScopeId    = -1;

    @Override
    public void start(Stage stage) throws Exception {
        root = new StackPane();
        Scene scene = new Scene(root, 980, 680);
        stage.setTitle("ChatApp");
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> { client.disconnect(); Platform.exit(); });
        stage.show();

        showAuthScreen(stage);
    }

    void showAuthScreen(Stage stage) {
        VBox screen = new VBox();
        screen.setAlignment(Pos.CENTER);
        screen.setStyle("-fx-background-color: #2B5278;");

        VBox card = new VBox(14);
        card.setMaxWidth(360);
        card.setPadding(new Insets(36, 40, 36, 40));
        card.setStyle(
                "-fx-background-color: #FFFFFF;" +
                        "-fx-background-radius: 12;" +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.35), 20, 0, 0, 4);");

        Label appName = new Label("ChatApp");
        appName.setFont(Font.font("Georgia", FontWeight.BOLD, 28));
        appName.setStyle("-fx-text-fill: #2CA5E0;");

        Label subtitle = new Label("Sign in or create an account");
        subtitle.setStyle("-fx-text-fill: #666666; -fx-font-size: 13px;");

        TextField userField = styledTextField("Username");
        PasswordField passField = styledPasswordField("Password");

        Label errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: #E53935; -fx-font-size: 12px;");
        errorLabel.setWrapText(true);

        Button loginBtn    = accentButton("Sign In",      "#2CA5E0");
        Button registerBtn = accentButton("Create Account", "#4CAF50");

        loginBtn.setMaxWidth(Double.MAX_VALUE);
        registerBtn.setMaxWidth(Double.MAX_VALUE);

        loginBtn.setOnAction(e -> {
            String u = userField.getText().trim();
            String p = passField.getText();
            if (u.isEmpty() || p.isEmpty()) { errorLabel.setText("Fill in both fields."); return; }
            runBg(() -> {
                try {
                    client.connect("localhost", 5000);
                    if (client.login(u, p)) {
                        Platform.runLater(() -> showMainApp(stage));
                    } else {
                        Platform.runLater(() -> errorLabel.setText("Wrong username or password."));
                        client.disconnect();
                    }
                } catch (Exception ex) {
                    Platform.runLater(() -> errorLabel.setText("Cannot connect: " + ex.getMessage()));
                }
            });
        });

        registerBtn.setOnAction(e -> {
            String u = userField.getText().trim();
            String p = passField.getText();
            if (u.isEmpty() || p.isEmpty()) { errorLabel.setText("Fill in both fields."); return; }
            if (p.length() < 4)             { errorLabel.setText("Password must be ≥4 chars."); return; }
            runBg(() -> {
                try {
                    client.connect("localhost", 5000);
                    if (client.register(u, p)) {
                        client.login(u, p);
                        Platform.runLater(() -> showMainApp(stage));
                    } else {
                        Platform.runLater(() -> errorLabel.setText("Username already taken."));
                        client.disconnect();
                    }
                } catch (Exception ex) {
                    Platform.runLater(() -> errorLabel.setText("Cannot connect: " + ex.getMessage()));
                }
            });
        });

        card.getChildren().addAll(appName, subtitle, userField, passField, loginBtn, registerBtn, errorLabel);
        screen.getChildren().add(card);
        VBox.setVgrow(screen, Priority.ALWAYS);
        root.getChildren().setAll(screen);
    }

    void showMainApp(Stage stage) {
        stage.setTitle("Chat  –  " + client.username);
        mainLayout = new BorderPane();
        mainLayout.setLeft(buildSidebar());
        mainLayout.setCenter(buildEmptyCenter());
        root.getChildren().setAll(mainLayout);

        client.setPushCallback(line -> Platform.runLater(() -> handlePush(line)));

        refreshDmList();
        refreshGroupList();
    }

    VBox buildSidebar() {
        Label title = new Label("Chat");
        title.setFont(Font.font("Georgia", FontWeight.BOLD, 18));
        title.setStyle("-fx-text-fill: #FFFFFF;");

        Button newGroupBtn = new Button("+ Group");
        newGroupBtn.setStyle(
                "-fx-background-color: rgba(255,255,255,0.15);" +
                        "-fx-text-fill: #FFFFFF;" +
                        "-fx-background-radius: 6;" +
                        "-fx-font-size: 11px;" +
                        "-fx-padding: 4 10 4 10;");
        newGroupBtn.setOnAction(e -> showCreateGroupDialog());
        HBox header = new HBox(title, new Region(), newGroupBtn);
        HBox.setHgrow(header.getChildren().get(1), Priority.ALWAYS);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(14, 12, 14, 16));
        header.setStyle("-fx-background-color: #1E3A5A;");

        TextField searchBar = styledTextField("Search users…");
        searchBar.setStyle(
                "-fx-background-color: rgba(255,255,255,0.15);" +
                        "-fx-text-fill: #FFFFFF;" +
                        "-fx-prompt-text-fill: rgba(255,255,255,0.55);" +
                        "-fx-background-radius: 18;" +
                        "-fx-padding: 7 14 7 14;" +
                        "-fx-font-size: 13px;");
        HBox searchRow = new HBox(searchBar);
        searchRow.setPadding(new Insets(8, 10, 6, 10));
        searchRow.setStyle("-fx-background-color: #2B5278;");
        HBox.setHgrow(searchBar, Priority.ALWAYS);

        VBox searchResults = new VBox(2);
        searchResults.setPadding(new Insets(0, 8, 6, 8));
        searchResults.setStyle("-fx-background-color: #2B5278;");
        searchResults.setVisible(false);
        searchResults.setManaged(false);

        searchBar.textProperty().addListener((obs, oldVal, q) -> {
            if (q.length() < 1) {
                searchResults.setVisible(false);
                searchResults.setManaged(false);
                return;
            }
            runBg(() -> {
                try {
                    List<String> res = client.search(q);
                    Platform.runLater(() -> {
                        searchResults.getChildren().clear();
                        if (res.isEmpty()) {
                            searchResults.setVisible(false);
                            searchResults.setManaged(false);
                            return;
                        }
                        for (String u : res) {
                            Button btn = new Button("👤  " + u);
                            btn.setMaxWidth(Double.MAX_VALUE);
                            btn.setStyle(
                                    "-fx-background-color: rgba(255,255,255,0.1);" +
                                            "-fx-text-fill: #FFFFFF;" +
                                            "-fx-alignment: center-left;" +
                                            "-fx-background-radius: 6;" +
                                            "-fx-padding: 8 12 8 12;" +
                                            "-fx-font-size: 13px;");
                            btn.setOnAction(ev -> {
                                searchBar.clear();
                                searchResults.setVisible(false);
                                searchResults.setManaged(false);
                                openDirectChat(u);
                            });
                            searchResults.getChildren().add(btn);
                        }
                        searchResults.setVisible(true);
                        searchResults.setManaged(true);
                    });
                } catch (Exception ignored) {}
            });
        });

        sidebarTabs = new TabPane();
        sidebarTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        sidebarTabs.setStyle("-fx-tab-min-width: 100px;");

        ScrollPane dmScroll = new ScrollPane();
        dmListBox = new VBox(1);
        dmScroll.setContent(dmListBox);
        dmScroll.setFitToWidth(true);
        dmScroll.setStyle("-fx-background: #FFFFFF; -fx-background-color: #FFFFFF;");

        ScrollPane grpScroll = new ScrollPane();
        groupListBox = new VBox(1);
        grpScroll.setContent(groupListBox);
        grpScroll.setFitToWidth(true);
        grpScroll.setStyle("-fx-background: #FFFFFF; -fx-background-color: #FFFFFF;");

        Tab chatsTab  = new Tab("Chats",  dmScroll);
        Tab groupsTab = new Tab("Groups", grpScroll);
        sidebarTabs.getTabs().addAll(chatsTab, groupsTab);
        VBox.setVgrow(sidebarTabs, Priority.ALWAYS);

        VBox sidebar = new VBox(header, searchRow, searchResults, sidebarTabs);
        sidebar.setPrefWidth(270);
        sidebar.setStyle("-fx-background-color: #FFFFFF;");
        return sidebar;
    }

    VBox buildEmptyCenter() {
        Label lbl = new Label("Select a chat or search for a user");
        lbl.setStyle("-fx-text-fill: #AAAAAA; -fx-font-size: 15px;");
        VBox box = new VBox(lbl);
        box.setAlignment(Pos.CENTER);
        box.setStyle("-fx-background-color: #F5F5F5;");
        return box;
    }

    void buildChatArea(String title) {
        chatTitleLabel = new Label(title);
        chatTitleLabel.setFont(Font.font("Georgia", FontWeight.BOLD, 15));
        chatTitleLabel.setStyle("-fx-text-fill: #FFFFFF;");

        HBox topBar = new HBox(chatTitleLabel);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(12, 16, 12, 16));
        topBar.setStyle("-fx-background-color: #2B5278;");

        messageBox = new VBox(6);
        messageBox.setPadding(new Insets(14, 16, 14, 16));
        messageBox.setStyle("-fx-background-color: #E5DDD5;");  // Telegram wallpaper beige

        chatScroll = new ScrollPane(messageBox);
        chatScroll.setFitToWidth(true);
        chatScroll.setStyle("-fx-background: #E5DDD5; -fx-background-color: #E5DDD5;");
        messageBox.heightProperty().addListener((obs, o, n) -> chatScroll.setVvalue(1.0));

        inputField = new TextField();
        inputField.setPromptText("Write a message…");
        inputField.setStyle(
                "-fx-background-color: #FFFFFF;" +
                        "-fx-text-fill: #111111;" +
                        "-fx-background-radius: 20;" +
                        "-fx-border-radius: 20;" +
                        "-fx-padding: 9 14 9 14;" +
                        "-fx-font-size: 13px;" +
                        "-fx-border-color: #E0E0E0;" +
                        "-fx-border-width: 1;");
        HBox.setHgrow(inputField, Priority.ALWAYS);

        Button sendBtn = new Button("➤");
        sendBtn.setStyle(
                "-fx-background-color: #2CA5E0;" +
                        "-fx-text-fill: white;" +
                        "-fx-background-radius: 50%;" +
                        "-fx-min-width: 38px; -fx-min-height: 38px;" +
                        "-fx-font-size: 14px;");

        Runnable doSend = () -> {
            String text = inputField.getText().trim();
            if (text.isEmpty() || activeScopeId == -1) return;
            client.sendMessage(activeScopeType, activeScopeId, text);
            inputField.clear();
        };
        sendBtn.setOnAction(e -> doSend.run());
        inputField.setOnAction(e -> doSend.run());

        HBox inputBar = new HBox(10, inputField, sendBtn);
        inputBar.setAlignment(Pos.CENTER);
        inputBar.setPadding(new Insets(10, 14, 10, 14));
        inputBar.setStyle("-fx-background-color: #F0F0F0;");

        BorderPane chatPane = new BorderPane();
        chatPane.setTop(topBar);
        chatPane.setCenter(chatScroll);
        chatPane.setBottom(inputBar);
        mainLayout.setCenter(chatPane);
    }

    void openDirectChat(String peer) {
        buildChatArea(peer);
        activeScopeType = "direct";
        activeScopeId   = -1;
        messageBox.getChildren().clear();
        runBg(() -> {
            try {
                int chatId = client.getOrCreateDirect(peer);
                activeScopeId = chatId;
                List<String[]> hist = client.getHistory("direct", chatId);
                Platform.runLater(() -> {
                    for (String[] m : hist)
                        addBubble(m[0], m[1].replace("\\|", "|"), m[0].equals(client.username));
                });
                refreshDmList();
            } catch (Exception ex) {
                Platform.runLater(() -> showError("Cannot open chat: " + ex.getMessage()));
            }
        });
    }

    void openGroupChat(int groupId, String groupName) {
        buildChatArea("👥  " + groupName);
        activeScopeType = "group";
        activeScopeId   = groupId;
        messageBox.getChildren().clear();
        runBg(() -> {
            try {
                List<String[]> hist = client.getHistory("group", groupId);
                Platform.runLater(() -> {
                    for (String[] m : hist)
                        addBubble(m[0], m[1].replace("\\|", "|"), m[0].equals(client.username));
                });
            } catch (Exception ex) {
                Platform.runLater(() -> showError("Cannot load history: " + ex.getMessage()));
            }
        });
    }

    void handlePush(String line) {
        if (line.startsWith("INCOMING_MSG|")) {
            String[] p = line.split("\\|", 5);
            String scope   = p[1];
            int    scopeId = Integer.parseInt(p[2]);
            String sender  = p[3];
            String text    = p[4].replace("\\|", "|");

            if (scope.equals(activeScopeType) && scopeId == activeScopeId) {
                addBubble(sender, text, sender.equals(client.username));
            } else {
                if ("direct".equals(scope)) refreshDmList();
                else                        refreshGroupList();
            }
        }
    }

    void refreshDmList() {
        runBg(() -> {
            try {
                List<String[]> dms = client.getDmList();
                Platform.runLater(() -> {
                    dmListBox.getChildren().clear();
                    if (dms.isEmpty()) {
                        Label empty = new Label("No chats yet — search for a user above");
                        empty.setStyle("-fx-text-fill: #AAAAAA; -fx-font-size: 12px; -fx-padding: 20;");
                        empty.setWrapText(true);
                        dmListBox.getChildren().add(empty);
                        return;
                    }
                    for (String[] dm : dms) {  // [chatId, peer, lastMsg]
                        dmListBox.getChildren().add(chatRow(dm[1], dm[2], () -> openDirectChat(dm[1])));
                    }
                });
            } catch (Exception ignored) {}
        });
    }

    void refreshGroupList() {
        runBg(() -> {
            try {
                List<String[]> groups = client.getGroupList();
                Platform.runLater(() -> {
                    groupListBox.getChildren().clear();
                    if (groups.isEmpty()) {
                        Label empty = new Label("No groups — click "+ "Group "+" above");
                        empty.setStyle("-fx-text-fill: #AAAAAA; -fx-font-size: 12px; -fx-padding: 20;");
                        empty.setWrapText(true);
                        groupListBox.getChildren().add(empty);
                        return;
                    }
                    for (String[] g : groups) {  // [groupId, name]
                        int gid = Integer.parseInt(g[0]);
                        groupListBox.getChildren().add(chatRow("👥  " + g[1], "", () -> openGroupChat(gid, g[1])));
                    }
                });
            } catch (Exception ignored) {}
        });
    }

    HBox chatRow(String name, String preview, Runnable onClick) {
        Label nameLbl    = new Label(name);
        nameLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #111111;");
        Label previewLbl = new Label(preview.length() > 40 ? preview.substring(0,40)+"…" : preview);
        previewLbl.setStyle("-fx-text-fill: #777777; -fx-font-size: 11px;");
        VBox text = new VBox(2, nameLbl, previewLbl);
        HBox row  = new HBox(12, text);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(10, 14, 10, 14));
        row.setStyle("-fx-background-color: #FFFFFF; -fx-cursor: hand;");
        row.setOnMouseEntered(e -> row.setStyle("-fx-background-color: #EAF4FB; -fx-cursor: hand;"));
        row.setOnMouseExited(e  -> row.setStyle("-fx-background-color: #FFFFFF; -fx-cursor: hand;"));
        row.setOnMouseClicked(e -> onClick.run());
        return row;
    }

    void addBubble(String sender, String text, boolean fromMe) {
        Label senderLbl = new Label(fromMe ? "You" : sender);
        senderLbl.setStyle("-fx-font-size: 11px; -fx-font-weight: bold;" +
                "-fx-text-fill: " + (fromMe ? "#2CA5E0" : "#E53935") + ";");

        Label textLbl = new Label(text);
        textLbl.setWrapText(true);
        textLbl.setMaxWidth(440);
        textLbl.setStyle("-fx-font-size: 13px; -fx-text-fill: #111111;");

        VBox bubble = new VBox(2, senderLbl, textLbl);
        bubble.setPadding(new Insets(8, 14, 8, 14));
        bubble.setStyle(
                "-fx-background-radius: " + (fromMe ? "14 4 14 14" : "4 14 14 14") + ";" +
                        "-fx-background-color: " + (fromMe ? "#EFFDDE" : "#FFFFFF") + ";" +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.08), 4, 0, 0, 1);");

        HBox row = new HBox(bubble);
        row.setAlignment(fromMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        messageBox.getChildren().add(row);
    }

    void showCreateGroupDialog() {
        Stage dlg = new Stage();
        dlg.initModality(Modality.APPLICATION_MODAL);
        dlg.setTitle("Create Group");

        VBox box = new VBox(12);
        box.setPadding(new Insets(24));
        box.setStyle("-fx-background-color: #FFFFFF;");

        Label title = new Label("New Group");
        title.setFont(Font.font("Georgia", FontWeight.BOLD, 16));
        title.setStyle("-fx-text-fill: #2B5278;");

        TextField nameField = styledTextField("Group name");
        Label errorLbl = new Label();
        errorLbl.setStyle("-fx-text-fill: #E53935; -fx-font-size: 11px;");

        Button createBtn = accentButton("Create", "#2CA5E0");
        createBtn.setMaxWidth(Double.MAX_VALUE);

        createBtn.setOnAction(e -> {
            String name = nameField.getText().trim();
            if (name.isEmpty()) { errorLbl.setText("Enter a name."); return; }
            runBg(() -> {
                try {
                    client.createGroup(name);
                    Platform.runLater(() -> {
                        dlg.close();
                        refreshGroupList();
                        // Switch to Groups tab
                        sidebarTabs.getSelectionModel().select(1);
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> errorLbl.setText(ex.getMessage()));
                }
            });
        });

        box.getChildren().addAll(title, nameField, createBtn, errorLbl);
        dlg.setScene(new Scene(box, 300, 180));
        dlg.showAndWait();
    }
    TextField styledTextField(String prompt) {
        TextField tf = new TextField();
        tf.setPromptText(prompt);
        tf.setStyle(
                "-fx-background-color: #F5F5F5;" +
                        "-fx-text-fill: #111111;" +
                        "-fx-border-color: #DDDDDD;" +
                        "-fx-border-radius: 6;" +
                        "-fx-background-radius: 6;" +
                        "-fx-padding: 9 12 9 12;" +
                        "-fx-font-size: 13px;");
        return tf;
    }

    PasswordField styledPasswordField(String prompt) {
        PasswordField pf = new PasswordField();
        pf.setPromptText(prompt);
        pf.setStyle(
                "-fx-background-color: #F5F5F5;" +
                        "-fx-text-fill: #111111;" +
                        "-fx-border-color: #DDDDDD;" +
                        "-fx-border-radius: 6;" +
                        "-fx-background-radius: 6;" +
                        "-fx-padding: 9 12 9 12;" +
                        "-fx-font-size: 13px;");
        return pf;
    }

    Button accentButton(String text, String color) {
        Button btn = new Button(text);
        btn.setStyle(
                "-fx-background-color: " + color + ";" +
                        "-fx-text-fill: #FFFFFF;" +
                        "-fx-background-radius: 8;" +
                        "-fx-font-size: 13px;" +
                        "-fx-padding: 10 16 10 16;" +
                        "-fx-cursor: hand;");
        btn.setOnMouseEntered(e -> btn.setOpacity(0.88));
        btn.setOnMouseExited(e  -> btn.setOpacity(1.0));
        return btn;
    }

    void showError(String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        a.showAndWait();
    }

    static void runBg(Runnable r) {
        Thread t = new Thread(r);
        t.setDaemon(true);
        t.start();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
