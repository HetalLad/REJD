package gwu.rejd.gui;

import gwu.rejd.extractor.MultiFileProjectLoader;
import gwu.rejd.extractor.RelationshipExtractor;
import gwu.rejd.generator.DiagramRenderer;
import gwu.rejd.generator.PlantUmlClassDiagramGenerator;
import gwu.rejd.generator.PlantUmlSequenceDiagramGenerator;
import gwu.rejd.model.MethodModel;
import gwu.rejd.model.ProjectModel;
import gwu.rejd.model.RelationshipModel;
import gwu.rejd.model.TypeModel;
import org.eclipse.jdt.core.dom.CompilationUnit;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class RejdApp extends Application {

    private static final String ENTIRE_PROJECT_OPTION = "Entire Project";

    private Stage primaryStage;
    private ClassDiagramController classDiagramController;
    private ComboBox<String> scopeComboBox;
    private ListView<File> fileListView;
    private TabPane tabPane;
    private Tab sequenceTab;
    private ImageView seqImageView;
    private Label seqStatusLabel;

    private final MultiFileProjectLoader loader = new MultiFileProjectLoader();

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;

        ClassDiagramView classDiagramView = new ClassDiagramView();
        classDiagramController = new ClassDiagramController(classDiagramView);

        // ── Class diagram tab ──────────────────────────────────────────
        scopeComboBox = new ComboBox<>();
        scopeComboBox.setPrefWidth(300);
        scopeComboBox.setOnAction(event -> {
            String selected = scopeComboBox.getValue();
            if (ENTIRE_PROJECT_OPTION.equals(selected)) {
                classDiagramController.setScope(DiagramScope.entireProject());
            } else {
                classDiagramController.setScope(DiagramScope.forPackage(selected));
            }
        });

        HBox toolbar = new HBox(10, new Label("Scope:"), scopeComboBox, classDiagramView.getExportButton());
        toolbar.setPadding(new Insets(10));

        BorderPane diagramPane = new BorderPane();
        diagramPane.setTop(toolbar);
        diagramPane.setCenter(classDiagramView);

        Tab classDiagramTab = new Tab("Class Diagram");
        classDiagramTab.setClosable(false);
        classDiagramTab.setContent(diagramPane);

        // ── Sequence diagram tab ───────────────────────────────────────
        seqImageView = new ImageView();
        seqImageView.setPreserveRatio(true);
        seqImageView.setSmooth(true);

        ScrollPane seqScroll = new ScrollPane(seqImageView);
        seqScroll.setFitToWidth(true);
        seqScroll.setFitToHeight(true);

        // Bind image width to scroll pane so it scales with window
        seqImageView.fitWidthProperty().bind(seqScroll.widthProperty().subtract(20));

        seqStatusLabel = new Label("Right-click a file and choose \"Generate Sequence Diagram\".");
        seqStatusLabel.setPadding(new Insets(6, 10, 6, 10));

        BorderPane seqPane = new BorderPane();
        seqPane.setTop(seqStatusLabel);
        seqPane.setCenter(seqScroll);

        sequenceTab = new Tab("Sequence Diagram");
        sequenceTab.setClosable(false);
        sequenceTab.setContent(seqPane);

        tabPane = new TabPane(classDiagramTab, sequenceTab);

        // ── Left panel ─────────────────────────────────────────────────
        Button openFolderBtn = new Button("Open Folder");
        Button addFilesBtn   = new Button("Add Files");
        Button clearBtn      = new Button("Clear");

        openFolderBtn.setMaxWidth(Double.MAX_VALUE);
        addFilesBtn.setMaxWidth(Double.MAX_VALUE);
        clearBtn.setMaxWidth(Double.MAX_VALUE);

        openFolderBtn.setOnAction(e -> openFolder());
        addFilesBtn.setOnAction(e -> addFiles());
        clearBtn.setOnAction(e -> {
            fileListView.getItems().clear();
            classDiagramController.clear();
            scopeComboBox.getItems().clear();
            seqImageView.setImage(null);
            seqStatusLabel.setText("Right-click a file and choose \"Generate Sequence Diagram\".");
        });

        fileListView = new ListView<>();
        fileListView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        fileListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(File item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName());
                setTooltip(empty || item == null ? null : new Tooltip(item.getAbsolutePath()));
            }
        });

        // Right-click context menu on file list
        MenuItem generateClassItem = new MenuItem("Generate Class Diagram");
        MenuItem generateSeqItem   = new MenuItem("Generate Sequence Diagram");
        ContextMenu contextMenu    = new ContextMenu(generateClassItem, generateSeqItem);
        fileListView.setContextMenu(contextMenu);

        generateClassItem.setOnAction(e -> {
            List<File> selected = fileListView.getSelectionModel().getSelectedItems();
            List<Path> paths = selected.isEmpty()
                    ? fileListView.getItems().stream().map(File::toPath).toList()
                    : selected.stream().map(File::toPath).toList();
            if (!paths.isEmpty()) renderClassDiagramAsync(paths);
        });

        generateSeqItem.setOnAction(e -> {
            File selected = fileListView.getSelectionModel().getSelectedItem();
            if (selected != null) generateSequenceDiagramAsync(selected.toPath());
            else showError("Select a single .java file first.");
        });

        VBox.setVgrow(fileListView, Priority.ALWAYS);

        Label filesLabel = new Label("Files");
        filesLabel.setStyle("-fx-font-weight: bold;");

        HBox btnRow = new HBox(6, openFolderBtn, addFilesBtn, clearBtn);
        HBox.setHgrow(openFolderBtn, Priority.ALWAYS);
        HBox.setHgrow(addFilesBtn, Priority.ALWAYS);

        VBox leftPanel = new VBox(8, filesLabel, btnRow, fileListView);
        leftPanel.setPadding(new Insets(10));
        leftPanel.setPrefWidth(220);
        leftPanel.setMinWidth(160);

        // ── Root layout ────────────────────────────────────────────────
        SplitPane splitPane = new SplitPane(leftPanel, tabPane);
        splitPane.setDividerPositions(0.2);

        Scene scene = new Scene(splitPane, 1300, 800);
        stage.setTitle("REJD - UML Tool");
        stage.setScene(scene);
        stage.show();
    }

    // ── File loading ───────────────────────────────────────────────────────

    private void openFolder() {
        DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle("Open Java Project Folder");
        File dir = dc.showDialog(primaryStage);
        if (dir == null) return;

        List<File> javaFiles;
        try (Stream<Path> stream = Files.walk(dir.toPath())) {
            javaFiles = stream
                    .filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".java"))
                    .sorted()
                    .map(Path::toFile)
                    .toList();
        } catch (IOException ex) {
            showError("Failed to read folder: " + ex.getMessage());
            return;
        }

        if (javaFiles.isEmpty()) {
            showError("No .java files found in the selected folder.");
            return;
        }

        fileListView.getItems().setAll(javaFiles);
        renderClassDiagramAsync(javaFiles.stream().map(File::toPath).toList());
    }

    private void addFiles() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Add Java Files");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Java Files", "*.java"));
        List<File> selected = fc.showOpenMultipleDialog(primaryStage);
        if (selected == null || selected.isEmpty()) return;

        for (File f : selected) {
            if (!fileListView.getItems().contains(f)) {
                fileListView.getItems().add(f);
            }
        }

        List<File> all = new ArrayList<>(fileListView.getItems());
        renderClassDiagramAsync(all.stream().map(File::toPath).toList());
    }

    // ── Class diagram ──────────────────────────────────────────────────────

    private void renderClassDiagramAsync(List<Path> paths) {
        new Thread(() -> {
            try {
                ProjectModel model = loader.loadProject("project", paths);

                List<String> packages = model.getTypesByFqn().values().stream()
                        .map(TypeModel::getPackageName)
                        .filter(p -> p != null && !p.isBlank())
                        .distinct().sorted().toList();

                Platform.runLater(() -> {
                    scopeComboBox.setItems(FXCollections.observableArrayList());
                    scopeComboBox.getItems().add(ENTIRE_PROJECT_OPTION);
                    scopeComboBox.getItems().addAll(packages);
                    scopeComboBox.setValue(ENTIRE_PROJECT_OPTION);
                    classDiagramController.setScope(DiagramScope.entireProject());
                    classDiagramController.showProject(model);
                    tabPane.getSelectionModel().select(0);
                });
            } catch (IOException ex) {
                Platform.runLater(() -> showError("Failed to parse files: " + ex.getMessage()));
            }
        }, "rejd-class-gen").start();
    }

    // ── Sequence diagram ───────────────────────────────────────────────────

    private void generateSequenceDiagramAsync(Path javaPath) {
        try {
            ProjectModel model = loader.loadProject("project", List.of(javaPath));
            CompilationUnit cu = loader.parseFile(javaPath);

            List<MethodEntry> methods = collectMethods(model);
            if (methods.isEmpty()) {
                showError("No methods found in the selected file.");
                return;
            }

            Optional<MethodEntry> chosen = pickMethod(methods);
            if (!chosen.isPresent()) return;

            String methodId = chosen.get().methodId;
            String label    = chosen.get().label;

            new Thread(() -> {
                try {
                    String plantUml = new PlantUmlSequenceDiagramGenerator()
                            .generate(model, cu, methodId);
                    Path tmpPng = Files.createTempFile("rejd-seq-", ".png");
                    new DiagramRenderer().render(plantUml, tmpPng);

                    Image img = new Image(tmpPng.toUri().toString());
                    Platform.runLater(() -> {
                        seqImageView.setImage(img);
                        seqStatusLabel.setText("Sequence diagram: " + label);
                        tabPane.getSelectionModel().select(sequenceTab);
                    });
                } catch (IOException | IllegalArgumentException ex) {
                    Platform.runLater(() -> showError("Failed to generate sequence diagram:\n" + ex.getMessage()));
                }
            }, "rejd-seq-gen").start();

        } catch (IOException ex) {
            showError("Failed to parse file: " + ex.getMessage());
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private List<MethodEntry> collectMethods(ProjectModel model) {
        List<MethodEntry> entries = new ArrayList<>();
        for (TypeModel type : model.getTypesByFqn().values()) {
            for (MethodModel m : type.getMethods()) {
                String label = type.getSimpleName() + "." + formatMethod(m);
                entries.add(new MethodEntry(label, m.getMethodId()));
            }
        }
        return entries;
    }

    private String formatMethod(MethodModel m) {
        StringBuilder sb = new StringBuilder(m.getName()).append("(");
        for (int i = 0; i < m.getParams().size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(m.getParams().get(i).getType());
        }
        sb.append(")");
        if (!m.isConstructor() && !m.getReturnType().isBlank()) {
            sb.append(" : ").append(m.getReturnType());
        }
        return sb.toString();
    }

    private Optional<MethodEntry> pickMethod(List<MethodEntry> methods) {
        ChoiceDialog<MethodEntry> dialog = new ChoiceDialog<>(methods.get(0), methods);
        dialog.setTitle("Select Method");
        dialog.setHeaderText("Choose a method to generate a sequence diagram for:");
        dialog.setContentText("Method:");
        return dialog.showAndWait();
    }

    private void showError(String message) {
        new Alert(Alert.AlertType.ERROR, message, ButtonType.OK).showAndWait();
    }

    private static final class MethodEntry {
        final String label;
        final String methodId;
        MethodEntry(String label, String methodId) { this.label = label; this.methodId = methodId; }
        @Override public String toString() { return label; }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
