import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.*;

public class NotepadApp extends Application {

    private final TextArea textArea = new TextArea();
    private final ListView<String> lineNumberList = new ListView<>();
    private final Label statusBar = new Label("Lines: 1 | Col: 1");
    private File currentFile = null;

    public void start(Stage stage) {

        BorderPane root = new BorderPane();

        textArea.setWrapText(false);

        textArea.textProperty().addListener((obs, oldText, newText) -> updateLineNumbers());

        textArea.caretPositionProperty().addListener((obs, oldPos, newPos) -> updateStatusBar());

        lineNumberList.setFocusTraversable(false);
        lineNumberList.setMouseTransparent(true);
        lineNumberList.setPrefWidth(50);
        lineNumberList.setStyle(
                "-fx-background-color: #f0f0f0;" +
                        "-fx-border-color: #cccccc;" +
                        "-fx-border-width: 0 1 0 0;"
        );
        lineNumberList.setCellFactory(lv -> new ListCell<>() {
            {
                setStyle("-fx-alignment: center-right; -fx-padding: 0 6 0 0;" +
                        "-fx-font-family: 'Courier New'; -fx-font-size: 13;" +
                        "-fx-text-fill: #888888; -fx-background-color: transparent;");
            }
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item);
            }
        });

        textArea.scrollTopProperty().addListener((obs, oldVal, newVal) ->
                lineNumberList.scrollTo((int)(newVal.doubleValue() / 16)));

        HBox editorBox = new HBox(lineNumberList, textArea);
        HBox.setHgrow(textArea, Priority.ALWAYS);

        MenuBar menuBar = new MenuBar();

        Menu fileMenu = new Menu("File");
        MenuItem newFile  = new MenuItem("New");
        MenuItem openFile = new MenuItem("Open");
        MenuItem saveFile = new MenuItem("Save");

        saveFile.setAccelerator(KeyCombination.keyCombination("CTRL+S"));
        openFile.setAccelerator(KeyCombination.keyCombination("CTRL+O"));
        newFile.setAccelerator(KeyCombination.keyCombination("CTRL+N"));

        fileMenu.getItems().addAll(newFile, openFile, saveFile);

        Menu themeMenu = new Menu("Theme");
        MenuItem light = new MenuItem("Light");
        MenuItem dark  = new MenuItem("Dark");
        themeMenu.getItems().addAll(light, dark);

        menuBar.getMenus().addAll(fileMenu, themeMenu);

        newFile.setOnAction(e -> {
            textArea.clear();
            currentFile = null;
            stage.setTitle("NotepadFX");
        });

        openFile.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            File file = chooser.showOpenDialog(stage);
            if (file != null) {
                loadFile(file);
                currentFile = file;
                stage.setTitle("NotepadFX — " + file.getName());
            }
        });

        saveFile.setOnAction(e -> save(stage));

        light.setOnAction(e -> applyTheme(false));
        dark.setOnAction(e  -> applyTheme(true));

        root.setTop(menuBar);
        root.setCenter(editorBox);
        root.setBottom(statusBar);

        statusBar.setStyle("-fx-padding: 2 6 2 6; -fx-font-size: 12;");

        Scene scene = new Scene(root, 900, 650);
        stage.setTitle("NotepadFX");
        stage.setScene(scene);
        stage.show();

        updateLineNumbers();
    }
    private void applyTheme(boolean isDark) {
        if (isDark) {
            textArea.setStyle(
                    "-fx-control-inner-background: #1e1e1e;" +
                            "-fx-text-fill: #d4d4d4;" +
                            "-fx-font-family: 'Courier New'; -fx-font-size: 13;"
            );
            lineNumberList.setStyle(
                    "-fx-background-color: #252526;" +
                            "-fx-border-color: #3c3c3c;" +
                            "-fx-border-width: 0 1 0 0;"
            );
        } else {
            textArea.setStyle(
                    "-fx-control-inner-background: white;" +
                            "-fx-text-fill: black;" +
                            "-fx-font-family: 'Courier New'; -fx-font-size: 13;"
            );
            lineNumberList.setStyle(
                    "-fx-background-color: #f0f0f0;" +
                            "-fx-border-color: #cccccc;" +
                            "-fx-border-width: 0 1 0 0;"
            );
        }
    }

    private void save(Stage stage) {
        try {
            if (currentFile == null) {
                FileChooser chooser = new FileChooser();
                currentFile = chooser.showSaveDialog(stage);
            }
            if (currentFile != null) {
                try (FileWriter writer = new FileWriter(currentFile)) {
                    writer.write(textArea.getText());
                }
                stage.setTitle("NotepadFX — " + currentFile.getName());
            }
        } catch (IOException e) {
            showError("Save failed", e.getMessage());
        }
    }

    private void loadFile(File file) {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            textArea.setText(sb.toString());
            textArea.positionCaret(0);
        } catch (IOException e) {
            showError("Open failed", e.getMessage());
        }
    }

    private void updateLineNumbers() {
        String text = textArea.getText();

        int lineCount = text.isEmpty() ? 1 : text.split("\n", -1).length;

        if (lineNumberList.getItems().size() != lineCount) {
            lineNumberList.getItems().clear();
            for (int i = 1; i <= lineCount; i++) {
                lineNumberList.getItems().add(String.valueOf(i));
            }
        }

        updateStatusBar();
    }

    private void updateStatusBar() {
        String text = textArea.getText();
        int caret = textArea.getCaretPosition();

        String before = caret > text.length() ? text : text.substring(0, caret);
        int currentLine = before.split("\n", -1).length;

        int lastNl = before.lastIndexOf('\n');
        int col = (lastNl == -1) ? caret + 1 : caret - lastNl;

        int totalLines = text.isEmpty() ? 1 : text.split("\n", -1).length;
        statusBar.setText("Lines: " + totalLines + " | Ln: " + currentLine + " | Col: " + col);
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setHeaderText(title);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
