package plugin.internal;

import gwu.rejd.gui.ClassDiagramController;
import gwu.rejd.gui.ClassDiagramView;
import gwu.rejd.gui.DiagramScope;
import gwu.rejd.model.ProjectModel;
import gwu.rejd.model.TypeModel;
import org.eclipse.jdt.core.dom.CompilationUnit;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.embed.swt.FXCanvas;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.part.ViewPart;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Eclipse ViewPart that hosts the full REJD UI from gwu.rejd inside Eclipse.
 *
 * Layout mirrors RejdApp exactly:
 *   Tab 1 "Class Diagram"    — ClassDiagramView (WebView) + Scope dropdown + Export button
 *   Tab 2 "Sequence Diagram" — scrollable PNG rendered by ClassDiagramController
 *
 * All pipeline logic (parsing, model building, rendering) lives in gwu.rejd.
 * This class is only an SWT↔JavaFX bridge + thin orchestration layer.
 */
public class DiagramView extends ViewPart {

    public static final String ID = "plugin.internal.DiagramView";

    private static final String ENTIRE_PROJECT = "Entire Project";

    private static volatile DiagramView instance;

    // SWT → JavaFX bridge
    private FXCanvas fxCanvas;

    // gwu.rejd pipeline objects — created on the JavaFX thread inside buildFxScene()
    private ClassDiagramView       classDiagramView;
    private ClassDiagramController controller;

    // JavaFX UI widgets
    private TabPane          tabPane;
    private Tab              classTab;
    private Tab              seqTab;
    private ComboBox<String> scopeComboBox;
    private ImageView        seqImageView;
    private Label            seqStatusLabel;

    public static DiagramView getInstance() { return instance; }

    // ── ViewPart lifecycle ─────────────────────────────────────────────────

    @Override
    public void createPartControl(Composite parent) {
        instance = this;
        parent.setLayout(new FillLayout());

        // FXCanvas starts the JavaFX toolkit automatically on first instantiation
        fxCanvas = new FXCanvas(parent, SWT.NONE);

        // Build the full JavaFX scene asynchronously on the FX thread
        Platform.runLater(this::buildFxScene);
    }

    /** Builds the scene that mirrors RejdApp's layout. Runs on the JavaFX thread. */
    private void buildFxScene() {
        // ── gwu.rejd pipeline ──────────────────────────────────────────
        classDiagramView = new ClassDiagramView();
        controller       = new ClassDiagramController(classDiagramView);

        // ── Tab 1: Class Diagram ───────────────────────────────────────
        scopeComboBox = new ComboBox<>();
        scopeComboBox.setPrefWidth(260);
        scopeComboBox.setOnAction(e -> {
            String sel = scopeComboBox.getValue();
            if (ENTIRE_PROJECT.equals(sel))
                controller.setScope(DiagramScope.entireProject());
            else if (sel != null)
                controller.setScope(DiagramScope.forPackage(sel));
        });

        HBox toolbar = new HBox(8,
                new Label("Scope:"), scopeComboBox,
                classDiagramView.getExportButton());
        toolbar.setPadding(new Insets(6));

        BorderPane classPane = new BorderPane();
        classPane.setTop(toolbar);
        classPane.setCenter(classDiagramView);

        classTab = new Tab("Class Diagram", classPane);
        classTab.setClosable(false);

        // ── Tab 2: Sequence Diagram ────────────────────────────────────
        seqImageView = new ImageView();
        seqImageView.setPreserveRatio(true);
        seqImageView.setSmooth(true);

        ScrollPane seqScroll = new ScrollPane(seqImageView);
        seqScroll.setFitToWidth(true);
        seqImageView.fitWidthProperty().bind(seqScroll.widthProperty().subtract(20));

        seqStatusLabel = new Label("Right-click a .java file → REJD Diagrams → Generate Sequence Diagram.");
        seqStatusLabel.setPadding(new Insets(6));

        BorderPane seqPane = new BorderPane();
        seqPane.setTop(seqStatusLabel);
        seqPane.setCenter(seqScroll);

        seqTab = new Tab("Sequence Diagram", seqPane);
        seqTab.setClosable(false);

        tabPane = new TabPane(classTab, seqTab);
        fxCanvas.setScene(new Scene(tabPane));
    }

    // ── Public API called by handlers ──────────────────────────────────────

    /**
     * Renders a class diagram for {@code model}.
     * Populates the Scope dropdown and switches to the Class Diagram tab.
     * Safe to call from any thread.
     */
    public void showClassDiagram(ProjectModel model) {
        // Use Platform.runLater so we run after buildFxScene() if it hasn't fired yet
        Platform.runLater(() -> {
            if (controller == null) return;

            List<String> packages = model.getTypesByFqn().values().stream()
                    .map(TypeModel::getPackageName)
                    .filter(p -> p != null && !p.isBlank())
                    .distinct().sorted()
                    .collect(Collectors.toList());

            scopeComboBox.setItems(FXCollections.observableArrayList());
            scopeComboBox.getItems().add(ENTIRE_PROJECT);
            scopeComboBox.getItems().addAll(packages);
            scopeComboBox.setValue(ENTIRE_PROJECT);

            controller.setScope(DiagramScope.entireProject());
            controller.showProject(model);
            tabPane.getSelectionModel().select(classTab);
        });
    }

    /**
     * Generates a sequence diagram for the given method using gwu.rejd's pipeline,
     * then displays the result in the Sequence Diagram tab.
     *
     * The handler passes raw inputs (model, cu, methodId); all rendering is delegated
     * to {@link ClassDiagramController#generateSequenceDiagram} in gwu.rejd.
     * Safe to call from any thread.
     */
    public void generateAndShowSequenceDiagram(ProjectModel model, CompilationUnit cu,
                                               String methodId, String label) {
        // Schedule after buildFxScene() so controller is guaranteed non-null
        Platform.runLater(() -> {
            if (controller == null) return;

            new Thread(() -> {
                try {
                    Path pngPath = controller.generateSequenceDiagram(model, cu, methodId);
                    showSequenceDiagram(pngPath, label);
                } catch (IOException | IllegalArgumentException ex) {
                    Display.getDefault().asyncExec(() -> {
                        Shell shell = Display.getDefault().getActiveShell();
                        new org.eclipse.jface.dialogs.MessageDialog(shell, "REJD Error", null,
                                "Sequence diagram failed:\n" + ex.getMessage(),
                                org.eclipse.jface.dialogs.MessageDialog.ERROR,
                                new String[]{"OK"}, 0).open();
                    });
                }
            }, "rejd-seq-gen").start();
        });
    }

    /** Displays a pre-rendered PNG in the Sequence Diagram tab. Safe to call from any thread. */
    private void showSequenceDiagram(Path pngPath, String label) {
        Platform.runLater(() -> {
            if (seqImageView == null) return;
            seqImageView.setImage(new Image(pngPath.toUri().toString()));
            seqStatusLabel.setText("Sequence diagram: " + label);
            tabPane.getSelectionModel().select(seqTab);
        });
    }

    @Override
    public void setFocus() {
        if (fxCanvas != null && !fxCanvas.isDisposed()) fxCanvas.setFocus();
    }

    @Override
    public void dispose() {
        if (instance == this) instance = null;
        super.dispose();
    }
}
