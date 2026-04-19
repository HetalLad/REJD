package gwu.rejd.gui;

import com.google.gson.Gson;
import gwu.rejd.notes.NoteModel;
import gwu.rejd.notes.NotePreloadCache;
import gwu.rejd.notes.NoteRepository;
import gwu.rejd.notes.ReplyModel;
import gwu.rejd.util.UserContext;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import netscape.javascript.JSObject;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class ClassDiagramView extends BorderPane {

    // ---------------------------------------------------------------
    // Fields
    // ---------------------------------------------------------------

    private final WebView  webView      = new WebView();
    private double         zoomLevel    = 1.0;
    private final Button   exportButton = new Button("Export");

    /** In-memory mirror of the persisted notes list. */
    private final List<NoteModel> notes = new ArrayList<>();

    /** Root of the project used for .rejd/rejd-notes.json */
    private Path projectRoot = Paths.get("").toAbsolutePath();

    /**
     * The most-recently-loaded WebEngine, held statically so the Eclipse plugin
     * can push updated notes into the live view from a non-JavaFX thread via
     * {@link #loadNotesIntoLiveView(Path)}.
     */
    private static volatile WebEngine liveEngine;

    private final Gson gson = new Gson();

    /**
     * Bridge is held as a field to prevent garbage collection —
     * WebView holds only a weak reference to injected Java objects.
     */
    private final NotesBridge bridge = new NotesBridge();

    // ---------------------------------------------------------------
    // Constructor
    // ---------------------------------------------------------------

    public ClassDiagramView() {
        setCenter(webView);
        exportButton.setDisable(true);
        exportButton.setOnAction(e -> showExportDialog());

        webView.getEngine().setJavaScriptEnabled(true);
        webView.setContextMenuEnabled(false);

        // Right-click → show context menu for the clicked node
        webView.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (event.getButton() == MouseButton.SECONDARY) {
                try {
                    double x = event.getX();
                    double y = event.getY();

                    WebEngine engine = webView.getEngine();
                    Object nodeIdResult = engine.executeScript(
                        "(function() {" +
                        "  if (typeof showContextMenu === 'undefined') return null;" +
                        "  const el = document.elementFromPoint(" + x + ", " + y + ");" +
                        "  const node = el && el.closest('.node');" +
                        "  return node ? node.getAttribute('data-id') : null;" +
                        "})()"
                    );

                    String clickedNodeId = nodeIdResult == null ? null : nodeIdResult.toString();

                    if (clickedNodeId != null && !clickedNodeId.isBlank()) {
                        String escaped = escapeJs(clickedNodeId);
                        engine.executeScript(
                            "showContextMenu(" + x + ", " + y + ", '" + escaped + "');"
                        );
                    } else {
                        engine.executeScript("hideContextMenu();");
                        engine.executeScript("hideNoteDialog();");
                    }
                } catch (Exception e) {
                    // Diagram page not loaded yet — ignore
                }
                event.consume();
            }
        });

        // Ctrl + scroll → zoom
        webView.addEventFilter(ScrollEvent.SCROLL, event -> {
            if (event.isControlDown()) {
                double delta = event.getDeltaY() > 0 ? 0.1 : -0.1;
                zoomLevel = Math.max(0.4, Math.min(2.5, zoomLevel + delta));
                webView.getEngine().executeScript("setZoom(" + zoomLevel + ");");
                event.consume();
            }
        });
    }

    // ---------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------

    public Button getExportButton() {
        return exportButton;
    }

    /** Called by the Eclipse plugin to point this view at a specific project. */
    public void setProjectRoot(Path root) {
        this.projectRoot = root != null ? root : Paths.get("").toAbsolutePath();
    }

    /**
     * Loads notes for {@code projectRoot} from disk and injects them into the
     * live WebView via {@code loadNotes()}.  Safe to call from any thread —
     * the executeScript is dispatched onto the JavaFX application thread.
     * No-op if no WebView has rendered a diagram yet.
     */
    public static void loadNotesIntoLiveView(Path projectRoot) {
        WebEngine engine = liveEngine;
        if (engine == null) return;
        List<NoteModel> loaded = NoteRepository.load(projectRoot);
        if (loaded.isEmpty()) return;
        String json = new Gson().toJson(loaded);
        javafx.application.Platform.runLater(() ->
            engine.executeScript("loadNotes(" + json + ");")
        );
    }

    public void renderGraph(String graphJson) {
        WebEngine engine = webView.getEngine();
        var htmlUrl = getClass().getResource("/web/simple-diagram.html");

        if (htmlUrl == null) {
            engine.loadContent("<html><body>simple-diagram.html not found in resources/web</body></html>");
            return;
        }

        engine.load(htmlUrl.toExternalForm());

        engine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == javafx.concurrent.Worker.State.SUCCEEDED) {
                try {
                    // 0. Publish engine so the Eclipse plugin can call loadNotesIntoLiveView()
                    liveEngine = engine;

                    // 1. Inject Java bridge so JS can call back to persist notes
                    JSObject window = (JSObject) engine.executeScript("window");
                    window.setMember("javaBridge", bridge);

                    // 2. Identify current user
                    engine.executeScript(
                        "setCurrentAuthor('" + escapeJs(UserContext.getCurrentUser()) + "');"
                    );

                    // 3. Load persisted notes — prefer disk, fall back to startup preload cache
                    List<NoteModel> loaded = NoteRepository.load(projectRoot);
                    if (loaded.isEmpty()) {
                        loaded = NotePreloadCache.get(projectRoot);
                    }
                    notes.clear();
                    notes.addAll(loaded);
                    if (!notes.isEmpty()) {
                        String notesJson = gson.toJson(notes);
                        engine.executeScript("loadNotes(" + notesJson + ");");
                    }

                    // 4. Render the diagram (badges are drawn using populated notesByNode)
                    engine.executeScript("renderGraph(" + graphJson + ");");
                    engine.executeScript("setZoom(" + zoomLevel + ");");
                    exportButton.setDisable(false);
                } catch (Exception e) {
                    engine.loadContent("<html><body>Render failed: " + escapeHtml(e.toString()) + "</body></html>");
                }
            }
        });
    }

    public void clear() {
        webView.getEngine().loadContent(
            "<html><body style='font-family: Arial; padding: 16px;'>No diagram loaded.</body></html>"
        );
        zoomLevel = 1.0;
        exportButton.setDisable(true);
    }

    // ---------------------------------------------------------------
    // Export dialog
    // ---------------------------------------------------------------

    private void showExportDialog() {
        Stage dialog = new Stage();
        dialog.setTitle("Export Diagram");
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.initOwner(getScene().getWindow());

        ToggleGroup tg = new ToggleGroup();
        RadioButton withNotes    = new RadioButton("Export with notes");
        RadioButton withoutNotes = new RadioButton("Export without notes");
        withNotes.setToggleGroup(tg);
        withoutNotes.setToggleGroup(tg);
        withNotes.setSelected(true);

        final File[] selectedFile = {null};
        TextField filePathField = new TextField();
        filePathField.setPromptText("No location selected");
        filePathField.setEditable(false);
        filePathField.setPrefWidth(300);

        Button chooseButton = new Button("Choose location\u2026");
        chooseButton.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Save Diagram");
            fc.setInitialFileName("diagram.png");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG Image", "*.png"));
            File file = fc.showSaveDialog(dialog);
            if (file != null) {
                selectedFile[0] = file;
                filePathField.setText(file.getAbsolutePath());
            }
        });

        HBox fileRow = new HBox(8, chooseButton, filePathField);
        fileRow.setAlignment(Pos.CENTER_LEFT);

        Button cancelBtn    = new Button("Cancel");
        Button doExportBtn  = new Button("Export");
        cancelBtn.setOnAction(e -> dialog.close());
        doExportBtn.setOnAction(e -> {
            if (selectedFile[0] == null) {
                new Alert(Alert.AlertType.WARNING, "Please choose a save location.", ButtonType.OK).showAndWait();
                return;
            }
            doExport(selectedFile[0], withNotes.isSelected());
            dialog.close();
        });

        HBox actionRow = new HBox(8, cancelBtn, doExportBtn);
        actionRow.setAlignment(Pos.CENTER_RIGHT);

        VBox content = new VBox(12, withNotes, withoutNotes, fileRow, actionRow);
        content.setPadding(new Insets(16));

        dialog.setScene(new Scene(content));
        dialog.setResizable(false);
        dialog.showAndWait();
    }

    private void doExport(File file, boolean includeNotes) {
        WebEngine engine = webView.getEngine();
        try {
            if (!includeNotes) engine.executeScript("hideNotes();");
            WritableImage image = webView.snapshot(null, null);
            if (!includeNotes) engine.executeScript("showNotes();");
            ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", file);
        } catch (IOException ex) {
            new Alert(Alert.AlertType.ERROR, "Failed to save image: " + ex.getMessage(), ButtonType.OK).showAndWait();
        }
    }

    // ---------------------------------------------------------------
    // Inner class — JavaScript ↔ Java bridge for note persistence
    // ---------------------------------------------------------------

    /**
     * Exposed to JS as window.javaBridge.
     * All public methods are callable from JavaScript via JSObject.
     */
    public class NotesBridge {

        /** Called when a new note is created in JS. */
        public void saveNote(String noteJson) {
            NoteModel note = gson.fromJson(noteJson, NoteModel.class);
            if (note == null) return;
            notes.removeIf(n -> note.id != null && note.id.equals(n.id));
            notes.add(note);
            NoteRepository.save(projectRoot, notes);
        }

        /** Called when a reply is submitted in JS. */
        public void saveReply(String noteId, String replyJson) {
            ReplyModel reply = gson.fromJson(replyJson, ReplyModel.class);
            if (reply == null) return;
            for (NoteModel note : notes) {
                if (noteId.equals(note.id)) {
                    note.replies.removeIf(r -> reply.id != null && reply.id.equals(r.id));
                    note.replies.add(reply);
                    NoteRepository.save(projectRoot, notes);
                    return;
                }
            }
        }

        /** Soft-deletes a note (sets isDeleted = true). */
        public void deleteNote(String noteId) {
            for (NoteModel note : notes) {
                if (noteId.equals(note.id)) {
                    note.isDeleted = true;
                    NoteRepository.save(projectRoot, notes);
                    return;
                }
            }
        }

        /** Soft-deletes a reply (sets isDeleted = true). */
        public void deleteReply(String noteId, String replyId) {
            for (NoteModel note : notes) {
                if (noteId.equals(note.id)) {
                    for (ReplyModel reply : note.replies) {
                        if (replyId.equals(reply.id)) {
                            reply.isDeleted = true;
                            NoteRepository.save(projectRoot, notes);
                            return;
                        }
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private String escapeHtml(String text) {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;");
    }

    private String escapeJs(String text) {
        return text
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\"", "\\\"");
    }
}