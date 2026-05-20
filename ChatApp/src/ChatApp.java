BoB:
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.*;
import java.net.Socket;
import java.nio.file.*;
import java.util.Base64;

public class ChatApp extends Application {

    BufferedReader in;
    PrintWriter out;

    VBox messages = new VBox(6);
    TextField input = new TextField();

    String username = "Bob";
    String group = "Software";

    Stage mainStage;

    @Override
    public void start(Stage stage) {
        this.mainStage = stage;
        connect();

        Label title = new Label("Group: " + group);
        title.setStyle("-fx-text-fill:white; -fx-font-size:14px; -fx-font-weight:bold;");
        HBox top = new HBox(title);
        top.setPadding(new Insets(12));
        top.setStyle("-fx-background-color:#2B5278;");

        ScrollPane scroll = new ScrollPane(messages);
        messages.setPadding(new Insets(10));
        messages.setStyle("-fx-background-color:#E5DDD5;");
        scroll.setFitToWidth(true);
        messages.heightProperty().addListener((obs, o, n) -> scroll.setVvalue(1.0));

        input.setPromptText("Write a message...");
        input.setStyle(
                "-fx-background-color:#FFFFFF;" +
                        "-fx-background-radius:20;" +
                        "-fx-border-radius:20;" +
                        "-fx-border-color:#E0E0E0;" +
                        "-fx-padding:10 14 10 14;" +
                        "-fx-font-size:13px;"
        );
        HBox.setHgrow(input, Priority.ALWAYS);

        Button send = new Button("➤");
        send.setStyle(
                "-fx-background-color:#2CA5E0;" +
                        "-fx-text-fill:white;" +
                        "-fx-background-radius:50%;" +
                        "-fx-min-width:38px;" +
                        "-fx-min-height:38px;" +
                        "-fx-font-size:14px;"
        );

        Button fileBtn = new Button("📎");
        fileBtn.setStyle(
                "-fx-background-color:#888888;" +
                        "-fx-text-fill:white;" +
                        "-fx-background-radius:50%;" +
                        "-fx-min-width:38px;" +
                        "-fx-min-height:38px;" +
                        "-fx-font-size:14px;"
        );

        Runnable sendAction = () -> {
            String text = input.getText().trim();
            if (text.isEmpty()) return;
            out.println(group + "|" + username + "|TEXT|" + text);
            input.clear();
        };

        send.setOnAction(e -> sendAction.run());
        input.setOnAction(e -> sendAction.run());
        fileBtn.setOnAction(e -> sendFile());

        HBox bottom = new HBox(10, fileBtn, input, send);
        bottom.setPadding(new Insets(10));
        bottom.setAlignment(Pos.CENTER);
        bottom.setStyle("-fx-background-color:#F0F0F0;");

        BorderPane root = new BorderPane();
        root.setTop(top);
        root.setCenter(scroll);
        root.setBottom(bottom);

        stage.setScene(new Scene(root, 420, 650));
        stage.setTitle("Chat - " + username);
        stage.show();
    }

    void connect() {
        try {
            Socket socket = new Socket("localhost", 5000);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            Thread t = new Thread(this::listen);
            t.setDaemon(true);
            t.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

void listen() {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                String[] p = line.split("\\|", 4);
                if (p.length < 3) continue;
                String user = p[0];
                String type = p[1];
                String payload = p.length > 2 ? p[2] : "";
                String extra = p.length > 3 ? p[3] : "";
                boolean me = user.equals(username);
                Platform.runLater(() -> {
                    if (type.equals("TEXT")) addTextBubble(user, payload, me);
                    else if (type.equals("FILE")) addFileBubble(user, payload, extra, me);
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void sendFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select a file");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Text & Code Files", "*.txt", "*.py", "*.java", "*.json", "*.csv", "*.xml", "*.md")
        );
        File file = chooser.showOpenDialog(mainStage);
        if (file == null) return;
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            if (bytes.length > 1024 * 1024) {
                new Alert(Alert.AlertType.ERROR, "File too large! Max 1MB.").show();
                return;
            }
            String encoded = Base64.getEncoder().encodeToString(bytes);
            out.println(group + "|" + username + "|FILE|" + file.getName() + "|" + encoded);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void addTextBubble(String user, String msg, boolean me) {
        Label sender = new Label(me ? "You" : user);
        sender.setStyle("-fx-font-size:11px; -fx-text-fill:" + (me ? "#2CA5E0" : "#E53935") + ";");

        Label text = new Label(msg);
        text.setWrapText(true);
        text.setMaxWidth(260);
        text.setStyle("-fx-font-size:13px; -fx-text-fill:#111;");

        VBox bubble = new VBox(3, sender, text);
        bubble.setPadding(new Insets(8, 12, 8, 12));
        bubble.setStyle(
                "-fx-background-radius:18;" +
                        "-fx-background-color:" + (me ? "#DCF8C6" : "#FFFFFF") + ";" +
                        "-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.08),4,0,0,1);"
        );

        HBox row = new HBox(bubble);
        row.setAlignment(me ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        row.setPadding(new Insets(4, 8, 4, 8));
        messages.getChildren().add(row);
    }

    void addFileBubble(String user, String fileName, String base64Data, boolean me) {
        Label sender = new Label(me ? "You" : user);
        sender.setStyle("-fx-font-size:11px; -fx-text-fill:" + (me ? "#2CA5E0" : "#E53935") + ";");

        Label fileLabel = new Label("📄 " + fileName);
        fileLabel.setStyle("-fx-font-size:13px; -fx-text-fill:#111;");

        Button download = new Button("⬇️ Save");
        download.setStyle(
                "-fx-background-color:#2CA5E0;" +
                        "-fx-text-fill:white;" +
                        "-fx-background-radius:10;" +
                        "-fx-font-size:11px;" +
                        "-fx-padding:4 10 4 10;"
        );
        download.setOnAction(e -> saveFile(fileName, base64Data));

        VBox bubble = new VBox(5, sender, fileLabel, download);
        bubble.setPadding(new Insets(8, 12, 8, 12));
        bubble.setStyle(
                "-fx-background-radius:18;" +
                        "-fx-background-color:" + (me ? "#DCF8C6" : "#FFFFFF") + ";" +
                        "-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.08),4,0,0,1);"
        );

        HBox row = new HBox(bubble);
        row.setAlignment(me ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        row.setPadding(new Insets(4, 8, 4, 8));
        messages.getChildren().add(row);
    }

void saveFile(String fileName, String base64Data) {
        FileChooser chooser = new FileChooser();
        chooser.setInitialFileName(fileName);
        File dest = chooser.showSaveDialog(mainStage);
        if (dest == null) return;
        try {
            Files.write(dest.toPath(), Base64.getDecoder().decode(base64Data));
            new Alert(Alert.AlertType.INFORMATION, "File saved!").show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}