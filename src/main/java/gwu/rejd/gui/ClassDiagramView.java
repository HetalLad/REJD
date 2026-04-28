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

    /** CSS colour passed by the Eclipse host to match the shell theme (e.g. "rgb(240,240,240)"). */
    private String eclipseBgColor = null;

    /** Set to true once the diagram HTML page has fully loaded (Worker.State.SUCCEEDED). */
    private boolean pageLoaded = false;

    /** Holds a renderGraph JSON call that arrived before the page was ready. */
    private String pendingGraphJson = null;

    /** Guard so the one-time page-load listener is only registered once. */
    private boolean pageListenerRegistered = false;

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

        if (pageLoaded) {
            // Page already ready — execute JS immediately
            executeRender(engine, graphJson);
            return;
        }

        // Store the latest pending JSON (replaces any earlier pending call)
        pendingGraphJson = graphJson;

        // Load the HTML page once and register a one-shot load listener
        if (!pageListenerRegistered) {
            pageListenerRegistered = true;

            // Read the HTML as a string so we can use loadContent().
            // WebView's internal WebKit cannot load bundleresource:// (OSGi) or jar:// URLs
            // directly — it only supports http/https/file/data.  loadContent() bypasses
            // that restriction by injecting the content directly into the engine.
            java.net.URL htmlUrl = getClass().getClassLoader()
                    .getResource("web/simple-diagram.html");
            System.out.println("REJD: HTML resource URL = " + htmlUrl);
            if (htmlUrl == null) {
                System.err.println("REJD ERROR: simple-diagram.html not found on classpath.");
                engine.loadContent("<html><body>simple-diagram.html not found</body></html>");
                return;
            }

            String htmlContent;
            try (java.io.InputStream is = htmlUrl.openStream()) {
                htmlContent = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                System.out.println("REJD: HTML read OK, length=" + htmlContent.length());
            } catch (java.io.IOException ex) {
                System.err.println("REJD ERROR: Cannot read simple-diagram.html: " + ex);
                engine.loadContent("<html><body>Cannot read simple-diagram.html: " + ex.getMessage() + "</body></html>");
                return;
            }

            // loadContent() fires SUCCEEDED once when the injected HTML is ready
            engine.loadContent(htmlContent, "text/html");

            engine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
                if (newState != javafx.concurrent.Worker.State.SUCCEEDED) return;
                if (pageLoaded) return;   // guard: process first SUCCEEDED only
                pageLoaded = true;
                System.out.println("REJD: page SUCCEEDED — injecting bridge and rendering");
                try {
                    // 1. Publish engine for live note updates from the Eclipse plugin
                    liveEngine = engine;

                    // 2. Inject Java↔JS bridge
                    JSObject window = (JSObject) engine.executeScript("window");
                    window.setMember("javaBridge", bridge);

                    // 3. Identify current user
                    String user = UserContext.getCurrentUser();
                    if (user != null) {
                        engine.executeScript("setCurrentAuthor('" + escapeJs(user) + "');");
                    }

                    // 4. Load persisted notes
                    List<NoteModel> loaded = NoteRepository.load(projectRoot);
                    if (loaded.isEmpty()) loaded = NotePreloadCache.get(projectRoot);
                    notes.clear();
                    notes.addAll(loaded);
                    if (!notes.isEmpty()) {
                        engine.executeScript("loadNotes(" + gson.toJson(notes) + ");");
                    }

                    // 5. Replay any pending render call
                    if (pendingGraphJson != null) {
                        executeRender(engine, pendingGraphJson);
                        pendingGraphJson = null;
                    }
                } catch (Exception e) {
                    // Log but do NOT call engine.loadContent() here — that would
                    // retrigger SUCCEEDED and create an infinite loop.
                    System.err.println("REJD ERROR in page load listener: " + e);
                    e.printStackTrace();
                }
            });
        }
    }

    /** Executes the actual renderGraph JS call and post-render steps. Must run on FX thread. */
    private void executeRender(WebEngine engine, String graphJson) {
        try {
            System.out.println("REJD: calling JS renderGraph(), json length=" + graphJson.length());
            engine.executeScript("renderGraph(" + graphJson + ");");
            engine.executeScript("setZoom(" + zoomLevel + ");");
            if (eclipseBgColor != null) {
                engine.executeScript(
                        "document.body && (document.body.style.background='" + eclipseBgColor + "');");
            }
            exportButton.setDisable(false);
        } catch (Exception e) {
            System.err.println("REJD ERROR in executeRender: " + e);
            engine.loadContent("<html><body>Render failed: " + escapeHtml(e.toString()) + "</body></html>");
        }
    }

    public void clear() {
        webView.getEngine().loadContent(
            "<html><body style='font-family: Arial; padding: 16px;'>No diagram loaded.</body></html>"
        );
        zoomLevel = 1.0;
        exportButton.setDisable(true);
    }

    /**
     * Stores a CSS background colour applied to every page loaded by this view.
     * Used by the Eclipse plugin to match the IDE shell theme.
     */
    public void setEclipseBackground(String cssColor) {
        this.eclipseBgColor = cssColor;
    }

    /**
     * Renders a sequence diagram PNG inside the WebView, using a plain HTML wrapper.
     * Used by the Eclipse plugin in place of a separate SWT ImageView.
     *
     * @param pngPath     path to the generated PNG file
     * @param bgCssColor  CSS colour for the page background, or {@code null} for white
     */
    public void renderSequenceDiagram(Path pngPath, String bgCssColor) {
        String bg = bgCssColor != null ? bgCssColor : (eclipseBgColor != null ? eclipseBgColor : "#ffffff");
        webView.getEngine().loadContent(
            "<!DOCTYPE html><html><body style='margin:0;padding:8px;background:" + bg + "'>"
            + "<img src='" + pngPath.toUri().toString()
            + "' style='max-width:100%;display:block;margin:auto'>"
            + "</body></html>"
        );
        exportButton.setDisable(false);
    }

    /**
     * Captures a snapshot of the current WebView content and delivers it to
     * {@code callback} on the JavaFX thread.
     * Hides/restores note badges around the snapshot when {@code includeNotes} is false.
     * Safe to call from any thread.
     */
    public void snapshotForExport(boolean includeNotes,
                                   java.util.function.Consumer<javafx.scene.image.WritableImage> callback) {
        javafx.application.Platform.runLater(() -> {
            WebEngine engine = webView.getEngine();
            if (!includeNotes) {
                try { engine.executeScript("hideNotes();"); } catch (Exception ignored) {}
            }
            javafx.scene.image.WritableImage image = webView.snapshot(null, null);
            if (!includeNotes) {
                try { engine.executeScript("showNotes();"); } catch (Exception ignored) {}
            }
            callback.accept(image);
        });
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