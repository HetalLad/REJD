package plugin.ui;

import com.google.gson.Gson;
import gwu.rejd.extractor.MultiFileProjectLoader;
import gwu.rejd.generator.DiagramRenderer;
import gwu.rejd.generator.PlantUmlSequenceDiagramGenerator;
import gwu.rejd.gui.DiagramScope;
import gwu.rejd.model.PackageNode;
import gwu.rejd.model.ProjectModel;
import gwu.rejd.model.MethodModel;
import gwu.rejd.model.TypeModel;
import gwu.rejd.notes.NoteModel;
import gwu.rejd.notes.NotePreloadCache;
import gwu.rejd.notes.NoteRepository;
import gwu.rejd.notes.ReplyModel;
import gwu.rejd.util.UserContext;

import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.BrowserFunction;
import org.eclipse.swt.browser.ProgressAdapter;
import org.eclipse.swt.browser.ProgressEvent;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.dialogs.ListDialog;
import org.eclipse.ui.part.ViewPart;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Eclipse ViewPart — package/type tree on the left, class diagram in a native
 * SWT Browser (WebKit on macOS, Edge on Windows, GTK WebKit on Linux) on the right.
 * No JavaFX required.
 *
 * Features parity with the standalone ClassDiagramView:
 *   - Ctrl+scroll zoom (0.4× – 2.5×, persisted across renders)
 *   - Eclipse theme background colour injection
 *   - Notes (create / reply / delete) via BrowserFunction bridge
 *   - NotePreloadCache fallback when repository file is not yet written
 *   - Sequence diagram display with theme-aware background
 *   - Export PNG with optional notes, via JS canvas → base64 → Java
 *   - clear() resets the view to a placeholder
 *   - Right-click on nodes shows Add-Note context menu (handled in HTML JS)
 *   - Click outside context menu closes it (handled in HTML JS)
 */
public class RejdDiagramView extends ViewPart {

    public static final String ID = "plugin.ui.RejdDiagramView";

    // ── SWT controls ──────────────────────────────────────────────────────────
    private Button     exportBtn;
    private TreeViewer treeViewer;
    private Browser    browser;

    // ── Project state ─────────────────────────────────────────────────────────
    private volatile ProjectModel currentModel;
    private volatile File         currentSourceDir;
    private volatile List<Path>   currentJavaPaths;
    private ProjectTreeContentProvider treeContentProvider;

    // ── Page-load tracking ────────────────────────────────────────────────────
    /** True when the class-diagram HTML is fully loaded and ready for renderGraph(). */
    private boolean pageLoaded        = false;
    /** Guards the ProgressAdapter so it only acts on class-HTML loads, not sequence HTML. */
    private boolean expectingClassHtml = false;
    /** Graph JSON waiting to render once the class HTML finishes loading. */
    private String  pendingJson       = null;
    /** Cached HTML string — read once from the classpath, reused on reload. */
    private String  cachedClassHtml   = null;

    // ── Zoom & theme ──────────────────────────────────────────────────────────
    /** Current zoom level applied via JS setZoom(). Range 0.4 – 2.5. */
    private double zoomLevel     = 1.0;
    /** CSS colour string (e.g. "rgb(240,240,240)") from the Eclipse IDE theme. */
    private String eclipseBgColor = null;

    // ── Export ────────────────────────────────────────────────────────────────
    private volatile String pendingExportPath = null;

    // ── Notes ─────────────────────────────────────────────────────────────────
    private final Gson gson = new Gson();
    private final List<NoteModel> notes = new ArrayList<>();
    private Path projectRoot = java.nio.file.Paths.get("").toAbsolutePath();

    // ── ViewPart lifecycle ────────────────────────────────────────────────────

    @Override
    public void createPartControl(Composite parent) {
        System.out.println("REJD: RejdDiagramView.createPartControl()");
        parent.setLayout(new GridLayout(1, false));
        ((GridLayout) parent.getLayout()).marginWidth  = 0;
        ((GridLayout) parent.getLayout()).marginHeight = 0;
        ((GridLayout) parent.getLayout()).verticalSpacing = 0;

        // ── Top toolbar: Export PNG button ────────────────────────────────────
        Composite topBar = new Composite(parent, SWT.NONE);
        GridLayout topLayout = new GridLayout(1, false);
        topLayout.marginHeight = 3;
        topLayout.marginWidth  = 6;
        topBar.setLayout(topLayout);
        topBar.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        exportBtn = new Button(topBar, SWT.PUSH);
        exportBtn.setText("Export PNG");
        exportBtn.setEnabled(false);
        exportBtn.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
        exportBtn.addListener(SWT.Selection, e -> onExport());

        // ── Full-width browser (fills all remaining space) ────────────────────
        // Tree viewer is still built for programmatic population; it is not
        // rendered in the UI — users trigger generation via right-click or REJD menu.
        treeContentProvider = new ProjectTreeContentProvider();
        Composite browserWrapper = new Composite(parent, SWT.NONE);
        browserWrapper.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        browserWrapper.setLayout(new GridLayout(1, false));
        ((GridLayout) browserWrapper.getLayout()).marginWidth  = 0;
        ((GridLayout) browserWrapper.getLayout()).marginHeight = 0;

        browser = createBrowser(browserWrapper);
        if (browser != null) {
            browser.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
            registerBrowserFunctions();
            setupBrowserListeners();
            loadClassHtml();
        }
        System.out.println("REJD: RejdDiagramView created, browser=" + browser);
    }

    // ── Browser creation ──────────────────────────────────────────────────────

    private Browser createBrowser(Composite parent) {
        String os = System.getProperty("os.name", "").toLowerCase();
        Browser b = null;
        if (os.contains("mac")) {
            try {
                b = new Browser(parent, SWT.WEBKIT);
                System.out.println("REJD: Using SWT.WEBKIT (macOS)");
            } catch (Exception e) {
                System.err.println("REJD: WEBKIT failed, trying SWT.NONE: " + e.getMessage());
                try { b = new Browser(parent, SWT.NONE); } catch (Exception e2) {
                    System.err.println("REJD: No browser available: " + e2.getMessage());
                }
            }
        } else if (os.contains("win")) {
            try {
                b = new Browser(parent, SWT.EDGE);
                System.out.println("REJD: Using SWT.EDGE (Windows)");
            } catch (Exception e) {
                System.err.println("REJD: EDGE failed, trying SWT.NONE: " + e.getMessage());
                try { b = new Browser(parent, SWT.NONE); } catch (Exception e2) {
                    System.err.println("REJD: No browser available: " + e2.getMessage());
                }
            }
        } else {
            try {
                b = new Browser(parent, SWT.NONE);
                System.out.println("REJD: Using SWT.NONE (Linux/other)");
            } catch (Exception e) {
                System.err.println("REJD: No browser available: " + e.getMessage());
            }
        }
        return b;
    }

    // ── Browser setup — called once ───────────────────────────────────────────

    /**
     * Registers all BrowserFunctions on the Browser widget.
     * BrowserFunctions are bound to the Browser instance and survive page navigations,
     * so they only need to be registered once.
     */
    private void registerBrowserFunctions() {

        // PNG export callback — called from JS exportToPng() with a base64 data URL
        new BrowserFunction(browser, "javaExportComplete") {
            @Override public Object function(Object[] args) {
                String dataUrl = (args != null && args.length > 0 && args[0] instanceof String)
                        ? (String) args[0] : null;
                String savePath = pendingExportPath;
                pendingExportPath = null;
                if (savePath == null) return null;
                if (dataUrl == null || !dataUrl.startsWith("data:")) {
                    showError("Export failed", "Could not capture diagram image.");
                    return null;
                }
                try {
                    String base64 = dataUrl.substring(dataUrl.indexOf(',') + 1);
                    byte[] bytes = Base64.getDecoder().decode(base64);
                    Files.write(java.nio.file.Paths.get(savePath), bytes);
                    MessageBox done = new MessageBox(getSite().getShell(), SWT.ICON_INFORMATION | SWT.OK);
                    done.setText("Export Complete");
                    done.setMessage("Diagram saved to:\n" + savePath);
                    done.open();
                } catch (Exception ex) {
                    showError("Export Error", ex.getMessage());
                }
                return null;
            }
        };

        // Notes bridge — thin wrappers; window.javaBridge delegates to these
        new BrowserFunction(browser, "_jbSaveNote") {
            @Override public Object function(Object[] args) {
                if (args == null || args.length == 0) return null;
                NoteModel note = gson.fromJson((String) args[0], NoteModel.class);
                if (note == null) return null;
                notes.removeIf(n -> note.id != null && note.id.equals(n.id));
                notes.add(note);
                NoteRepository.save(projectRoot, notes);
                return null;
            }
        };
        new BrowserFunction(browser, "_jbSaveReply") {
            @Override public Object function(Object[] args) {
                if (args == null || args.length < 3) return null;

                String noteId = (String) args[0];
                String parentReplyId = (String) args[1]; // may be empty/null for direct note reply
                ReplyModel reply = gson.fromJson((String) args[2], ReplyModel.class);
                if (reply == null) return null;

                for (NoteModel n : notes) {
                    if (noteId.equals(n.id)) {
                        if (parentReplyId == null || parentReplyId.isBlank()) {
                            n.replies.removeIf(r -> reply.id != null && reply.id.equals(r.id));
                            n.replies.add(reply);
                        } else {
                            ReplyModel parent = findReplyById(n.replies, parentReplyId);
                            if (parent != null) {
                                if (parent.replies == null) {
                                    parent.replies = new java.util.ArrayList<>();
                                }
                                parent.replies.removeIf(r -> reply.id != null && reply.id.equals(r.id));
                                parent.replies.add(reply);
                            }
                        }

                        NoteRepository.save(projectRoot, notes);
                        break;
                    }
                }

                return null;
            }
        };
        new BrowserFunction(browser, "_jbDeleteNote") {
            @Override public Object function(Object[] args) {
                if (args == null || args.length == 0) return null;
                String noteId = (String) args[0];
                for (NoteModel n : notes) {
                    if (noteId.equals(n.id)) { n.isDeleted = true; break; }
                }
                NoteRepository.save(projectRoot, notes);
                return null;
            }
        };
        new BrowserFunction(browser, "_jbDeleteReply") {
            @Override public Object function(Object[] args) {
                if (args == null || args.length < 2) return null;

                String noteId = (String) args[0];
                String replyId = (String) args[1];

                for (NoteModel n : notes) {
                    if (noteId.equals(n.id)) {
                        ReplyModel reply = findReplyById(n.replies, replyId);
                        if (reply != null) {
                            reply.isDeleted = true;
                            NoteRepository.save(projectRoot, notes);
                        }
                        break;
                    }
                }

                return null;
            }
        };
    }

    /**
     * Registers the single ProgressAdapter that handles class-HTML post-load setup.
     * The adapter is permanently registered but ignores loads where
     * {@code expectingClassHtml} is false (e.g. sequence diagram HTML).
     */
    private void setupBrowserListeners() {
        browser.addProgressListener(new ProgressAdapter() {
            @Override
            public void completed(ProgressEvent event) {
                if (!expectingClassHtml) return;   // sequence or other load — ignore
                expectingClassHtml = false;
                pageLoaded = true;
                System.out.println("REJD: class HTML loaded — injecting bridge, notes, zoom");

                // 1. Inject window.javaBridge object (BrowserFunctions are global
                //    but the page needs the named object to call them via callBridge())
                browser.execute(
                    "window.javaBridge = {" +
                    "  saveNote:    function(j)       { _jbSaveNote(j); }," +
                    "  saveReply:   function(id,pid,j) { _jbSaveReply(id,pid,j); }," +
                    "  deleteNote:  function(id)      { _jbDeleteNote(id); }," +
                    "  deleteReply: function(nid,rid) { _jbDeleteReply(nid,rid); }" +
                    "};");

                // 2. Set current author for note attribution
                String user = UserContext.getCurrentUser();
                if (user != null && !user.isBlank()) {
                    browser.execute("if(typeof setCurrentAuthor==='function') setCurrentAuthor('"
                            + escapeJs(user) + "');");
                }

                // 3. Apply Eclipse theme background (if one was set before first load)
                if (eclipseBgColor != null) {
                    browser.execute("if(typeof setBackground==='function') setBackground('"
                            + escapeJs(eclipseBgColor) + "');");
                }

                // 4. Load persisted notes (with NotePreloadCache fallback)
                injectNotes();

                // 5. Render any diagram that arrived before the page was ready
                if (pendingJson != null) {
                    executeRender(pendingJson);
                    pendingJson = null;
                }
            }
        });
    }

    /**
     * Loads (or reloads) the class-diagram HTML into the browser.
     * The HTML string is cached after the first classpath read.
     * Safe to call multiple times — e.g., to return from a sequence view.
     */
    private void loadClassHtml() {
        if (cachedClassHtml == null) {
            URL htmlUrl = getClass().getClassLoader().getResource("web/simple-diagram.html");
            System.out.println("REJD: HTML URL = " + htmlUrl);
            if (htmlUrl == null) {
                System.err.println("REJD ERROR: simple-diagram.html not found on classpath");
                browser.setText("<html><body>simple-diagram.html not found on classpath</body></html>", false);
                return;
            }
            try (InputStream is = htmlUrl.openStream()) {
                cachedClassHtml = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                System.out.println("REJD: HTML read OK, length=" + cachedClassHtml.length());
            } catch (IOException ex) {
                System.err.println("REJD ERROR: cannot read simple-diagram.html: " + ex);
                browser.setText("<html><body>Cannot read simple-diagram.html: "
                        + ex.getMessage() + "</body></html>", false);
                return;
            }
        }
        pageLoaded = false;
        expectingClassHtml = true;
        browser.setText(cachedClassHtml, true);
    }

    // ── Render helpers ────────────────────────────────────────────────────────

    /**
     * Calls renderGraph() in the browser and applies zoom and Eclipse theme.
     * Must be called on the SWT UI thread with pageLoaded == true.
     */
    private void executeRender(String json) {
        System.out.println("REJD: renderGraph(), json length=" + json.length());
        browser.execute("renderGraph(" + json + ");");
        browser.execute("setZoom(" + zoomLevel + ");");
        if (eclipseBgColor != null) {
            browser.execute("if(typeof setBackground==='function') setBackground('"
                    + escapeJs(eclipseBgColor) + "');");
        }
        exportBtn.setEnabled(true);
    }

    private void renderWithScope(ProjectModel model, DiagramScope scope) {
        String json = buildGraphJson(model, scope);
        Display.getDefault().asyncExec(() -> {
            if (pageLoaded) {
            	injectNotes();
                executeRender(json);
            } else {
                pendingJson = json;
                if (!expectingClassHtml) loadClassHtml();  // reload after sequence view
            }
        });
    }

    // ── Notes ─────────────────────────────────────────────────────────────────

    /**
     * Loads notes from disk for the current projectRoot, falls back to
     * NotePreloadCache if the repository file does not exist yet, and injects
     * them into the page via loadNotes().
     */
    private void injectNotes() {
        List<NoteModel> loaded = NoteRepository.load(projectRoot);
        if (loaded.isEmpty()) {
            loaded = NotePreloadCache.get(projectRoot);
        }
        notes.clear();
        notes.addAll(loaded);
        if (!notes.isEmpty()) {
            browser.execute("if(typeof loadNotes==='function') loadNotes("
                    + gson.toJson(notes) + ");");
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Stores the Eclipse IDE background colour (e.g. "rgb(240,240,240)") and
     * applies it to the currently-loaded diagram page immediately if possible.
     */
    public void setEclipseBackground(String cssColor) {
        this.eclipseBgColor = cssColor;
        if (pageLoaded && browser != null && !browser.isDisposed()) {
            browser.execute("if(typeof setBackground==='function') setBackground('"
                    + escapeJs(cssColor) + "');");
        }
    }

    /**
     * Resets the view to a "no diagram loaded" placeholder and disables Export.
     * Zoom level is also reset to 1.0.
     */
    public void clear() {
        if (browser != null && !browser.isDisposed()) {
            browser.setText(
                "<html><body style='font-family:Arial;padding:24px;background:#eef1f6;'>" +
                "<p style='color:#666;font-size:14px;'>No diagram loaded.<br>" +
                "Right-click a Java project or package in the Package Explorer " +
                "and choose <b>Generate Class Diagram</b>.</p></body></html>", false);
        }
        pageLoaded         = false;
        expectingClassHtml = false;
        zoomLevel          = 1.0;
        if (exportBtn != null && !exportBtn.isDisposed()) exportBtn.setEnabled(false);
    }

    /**
     * Walks {@code sourceDir} for .java files, builds a ProjectModel, populates
     * the tree, and renders the full class diagram.
     */
    public void generateClassDiagram(File sourceDir) {
        System.out.println("REJD: generateClassDiagram: " + sourceDir);
        new Thread(() -> {
            try {
                List<Path> javaPaths = collectJavaPaths(sourceDir.toPath());
                System.out.println("REJD: found " + javaPaths.size() + " java files");
                if (javaPaths.isEmpty()) {
                    showError("No Java files", "No .java files found in: " + sourceDir);
                    return;
                }

                ProjectModel model = new MultiFileProjectLoader()
                        .loadProject(sourceDir.getName(), javaPaths);
                System.out.println("REJD: model types: " + model.getTypesByFqn().size());
                currentModel     = model;
                currentSourceDir = sourceDir;
                currentJavaPaths = javaPaths;
                projectRoot      = resolveProjectRoot(sourceDir.toPath());

                String json = buildGraphJson(model, DiagramScope.entireProject());

                Display.getDefault().asyncExec(() -> {
                    if (treeViewer != null) {
                        treeViewer.setInput(model);
                        treeViewer.expandToLevel(2);
                    }
                    if (pageLoaded) {
                    	injectNotes();
                        executeRender(json);
                    } else {
                        pendingJson = json;
                        if (!expectingClassHtml) loadClassHtml();  // reload after sequence view
                    }
                });

            } catch (IOException ex) {
                showError("Generation failed", "Failed to generate class diagram:\n" + ex.getMessage());
            }
        }, "rejd-class-gen").start();
    }

    /**
     * Generates a sequence diagram for the given method and shows it in the Browser.
     * Embeds the PNG as a base64 data URL to avoid file:// security restrictions.
     * Uses the Eclipse theme background if one has been set.
     */
    public void generateAndShowSequenceDiagram(ProjectModel model, CompilationUnit cu,
                                                String methodId, String label) {
        new Thread(() -> {
            try {
                String plantUml = new PlantUmlSequenceDiagramGenerator().generate(model, cu, methodId);
                Path pngPath = Files.createTempFile("rejd-seq-", ".png");
                new DiagramRenderer().render(plantUml, pngPath);

                byte[] pngBytes = Files.readAllBytes(pngPath);
                String b64 = Base64.getEncoder().encodeToString(pngBytes);
                String dataUrl = "data:image/png;base64," + b64;

                // Use Eclipse theme colour if available, otherwise dark
                String bg = eclipseBgColor != null ? eclipseBgColor : "#1e1e2e";

                Display.getDefault().asyncExec(() -> {
                    if (browser == null || browser.isDisposed()) return;
                    String html = "<!DOCTYPE html><html><body style='margin:0;padding:8px;"
                            + "background:" + bg + ";display:flex;justify-content:center;align-items:flex-start'>"
                            + "<img src='" + dataUrl
                            + "' style='max-width:100%;height:auto;display:block;'>"
                            + "</body></html>";
                    pageLoaded         = false;   // class HTML no longer active
                    expectingClassHtml = false;   // sequence load must not trigger class setup
                    browser.setText(html, false);
                    exportBtn.setEnabled(false);  // exportToPng not available for sequence
                });
            } catch (IOException ex) {
                showError("Sequence diagram failed", ex.getMessage());
            }
        }, "rejd-seq-gen").start();
    }

    // ── Tree context menu ─────────────────────────────────────────────────────

    private void buildTreeContextMenu() {
        if (treeViewer == null) return;   // tree not rendered in full-browser mode
        Menu menu = new Menu(treeViewer.getTree());

        MenuItem genClass = new MenuItem(menu, SWT.PUSH);
        genClass.setText("Generate Class Diagram");
        genClass.addListener(SWT.Selection, e -> onTreeGenerateClass());

        MenuItem genSeq = new MenuItem(menu, SWT.PUSH);
        genSeq.setText("Generate Sequence Diagram");
        genSeq.addListener(SWT.Selection, e -> onTreeGenerateSequence());

        treeViewer.getTree().setMenu(menu);
    }

    private void onTreeGenerateClass() {
        if (currentModel == null) return;
        Object sel = getTreeSelection();
        DiagramScope scope = scopeForSelection(sel);
        renderWithScope(currentModel, scope);
    }

    private void onTreeGenerateSequence() {
        if (currentModel == null || currentSourceDir == null) return;
        Object sel = getTreeSelection();

        if (sel instanceof TypeModel type) {
            // Single class selected — use just that type's source file
            generateSequenceForType(type);
        } else {
            // Package node, no selection, or project root — pick from all types
            generateSequenceForProject();
        }
    }

    /** Opens a method picker across ALL types in the currently loaded project model. */
    private void generateSequenceForProject() {
        new Thread(() -> {
            List<MethodEntry> methods = collectMethods(currentModel);
            if (methods.isEmpty()) {
                showError("No methods", "No methods found in the loaded project.");
                return;
            }
            Display.getDefault().asyncExec(() -> {
                Shell shell = Display.getDefault().getActiveShell();
                MethodEntry chosen = pickMethod(shell, methods);
                if (chosen == null) return;

                // Locate the source file for the chosen method's type
                String fqn = chosen.methodId.contains("#")
                        ? chosen.methodId.substring(0, chosen.methodId.indexOf('#'))
                        : null;
                TypeModel type = (fqn != null) ? currentModel.getTypesByFqn().get(fqn) : null;
                if (type == null) {
                    showError("Not found", "Could not find type for method: " + chosen.label);
                    return;
                }
                Path sourcePath = findSourceFile(type);
                if (sourcePath == null) {
                    showError("Source not found",
                            "Could not locate source file for " + (fqn != null ? fqn : chosen.label));
                    return;
                }
                new Thread(() -> {
                    try {
                        MultiFileProjectLoader loader = new MultiFileProjectLoader();
                        ProjectModel fileModel = loader.loadProject("project", List.of(sourcePath));
                        CompilationUnit cu = loader.parseFile(sourcePath);
                        generateAndShowSequenceDiagram(fileModel, cu, chosen.methodId, chosen.label);
                    } catch (IOException ex) {
                        showError("Parse failed", "Failed to parse source:\\n" + ex.getMessage());
                    }
                }, "rejd-seq-project").start();
            });
        }, "rejd-seq-collect").start();
    }

    private Object getTreeSelection() {
        if (treeViewer == null) return null;
        IStructuredSelection ss = treeViewer.getStructuredSelection();
        return ss.isEmpty() ? null : ss.getFirstElement();
    }

    private DiagramScope scopeForSelection(Object sel) {
        if (sel instanceof PackageNode node) {
            String pkg = treeContentProvider.getFullPackageName(node);
            return pkg.isEmpty() ? DiagramScope.entireProject() : DiagramScope.forPackage(pkg);
        }
        if (sel instanceof TypeModel t) {
            String pkg = t.getPackageName();
            return (pkg == null || pkg.isBlank()) ? DiagramScope.entireProject()
                    : DiagramScope.forPackage(pkg);
        }
        return DiagramScope.entireProject();
    }

    // ── Sequence from tree ────────────────────────────────────────────────────

    private void generateSequenceForType(TypeModel type) {
        Path sourcePath = findSourceFile(type);
        if (sourcePath == null) {
            showError("Source not found", "Could not locate source file for " + type.getFqn());
            return;
        }
        new Thread(() -> {
            try {
                MultiFileProjectLoader loader = new MultiFileProjectLoader();
                ProjectModel fileModel = loader.loadProject("project", List.of(sourcePath));
                CompilationUnit cu = loader.parseFile(sourcePath);

                List<MethodEntry> methods = collectMethods(fileModel);
                if (methods.isEmpty()) {
                    showError("No methods", "No methods found in " + type.getSimpleName());
                    return;
                }
                Display.getDefault().asyncExec(() -> {
                    Shell shell = Display.getDefault().getActiveShell();
                    MethodEntry chosen = pickMethod(shell, methods);
                    if (chosen == null) return;
                    generateAndShowSequenceDiagram(fileModel, cu, chosen.methodId, chosen.label);
                });
            } catch (IOException ex) {
                showError("Parse failed", "Failed to parse " + type.getSimpleName() + ":\n" + ex.getMessage());
            }
        }, "rejd-seq-prepare").start();
    }

    private Path findSourceFile(TypeModel type) {
        if (currentJavaPaths == null) return null;
        String fqnPath = type.getFqn().replace('.', File.separatorChar) + ".java";
        return currentJavaPaths.stream()
                .filter(p -> p.toString().replace('/', File.separatorChar).endsWith(fqnPath))
                .findFirst().orElse(null);
    }

    // ── Graph building ────────────────────────────────────────────────────────

    /**
     * Resolves the true project root from a source directory path.
     * Walks up past common Maven/Gradle source layouts:
     *   src/main/java → project root (3 levels up)
     *   src/main      → project root (2 levels up)
     *   src           → project root (1 level up)
     * Falls back to the given path if none of those patterns match.
     */
    private static Path resolveProjectRoot(Path sourceDir) {
        Path p = sourceDir.toAbsolutePath().normalize();
        // Strip trailing src/main/java, src/main, src, or just src
        String[] strip = { "src/main/java", "src" + File.separator + "main" + File.separator + "java",
                           "src/main",      "src" + File.separator + "main",
                           "src" };
        String pathStr = p.toString();
        for (String suffix : strip) {
            if (pathStr.endsWith(File.separator + suffix) || pathStr.endsWith("/" + suffix)) {
                int idx = pathStr.lastIndexOf(File.separator + suffix);
                if (idx < 0) idx = pathStr.lastIndexOf("/" + suffix);
                if (idx >= 0) return java.nio.file.Paths.get(pathStr.substring(0, idx));
            }
        }
        // Already at root or unrecognised layout — use as-is
        return p;
    }

    private String buildGraphJson(ProjectModel model, DiagramScope scope) {
        gwu.rejd.extractor.RelationshipExtractor relExtractor =
                new gwu.rejd.extractor.RelationshipExtractor();
        gwu.rejd.generator.SimpleGraphGenerator graphGen =
                new gwu.rejd.generator.SimpleGraphGenerator();
        gwu.rejd.gui.ProjectModelFilter modelFilter =
                new gwu.rejd.gui.ProjectModelFilter();
        gwu.rejd.gui.RelationshipScopeFilter relFilter =
                new gwu.rejd.gui.RelationshipScopeFilter();

        ProjectModel scoped = modelFilter.filter(model, scope);
        List<gwu.rejd.model.RelationshipModel> allRels = relExtractor.extract(model);
        List<gwu.rejd.model.RelationshipModel> scopedRels = relFilter.filter(scope, allRels);
        return graphGen.generate(scoped, scopedRels);
    }

    // ── Export ────────────────────────────────────────────────────────────────

    private void onExport() {
        if (browser == null || browser.isDisposed()) return;

        MessageBox notesBox = new MessageBox(getSite().getShell(),
                SWT.ICON_QUESTION | SWT.YES | SWT.NO | SWT.CANCEL);
        notesBox.setText("Export Diagram");
        notesBox.setMessage("Include notes in the exported image?");
        int result = notesBox.open();
        if (result == SWT.CANCEL) return;
        boolean includeNotes = (result == SWT.YES);

        FileDialog fd = new FileDialog(getSite().getShell(), SWT.SAVE);
        fd.setText("Save Diagram As PNG");
        fd.setFileName("diagram.png");
        fd.setFilterExtensions(new String[]{"*.png"});
        fd.setFilterNames(new String[]{"PNG Image (*.png)"});
        String savePath = fd.open();
        if (savePath == null) return;

        pendingExportPath = savePath;
        browser.execute("exportToPng(" + includeNotes + ");");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<MethodEntry> collectMethods(ProjectModel model) {
        List<MethodEntry> entries = new ArrayList<>();
        for (TypeModel type : model.getTypesByFqn().values()) {
            for (MethodModel m : type.getMethods()) {
                entries.add(new MethodEntry(
                        type.getSimpleName() + "." + formatMethod(m),
                        m.getMethodId()));
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
        if (!m.isConstructor() && !m.getReturnType().isBlank())
            sb.append(" : ").append(m.getReturnType());
        return sb.toString();
    }

    private MethodEntry pickMethod(Shell shell, List<MethodEntry> methods) {
        ListDialog dialog = new ListDialog(shell);
        dialog.setTitle("Select Method");
        dialog.setMessage("Choose a method for the sequence diagram:");
        dialog.setContentProvider(ArrayContentProvider.getInstance());
        dialog.setLabelProvider(new LabelProvider() {
            @Override public String getText(Object e) {
                return e instanceof MethodEntry me ? me.label : super.getText(e);
            }
        });
        dialog.setInput(methods.toArray());
        if (dialog.open() != Window.OK) return null;
        Object[] result = dialog.getResult();
        return result != null && result.length > 0 ? (MethodEntry) result[0] : null;
    }

    private List<Path> collectJavaPaths(Path root) throws IOException {
        // Files.list() — non-recursive, only .java files directly in the given folder.
        // To include sub-packages, right-click each sub-package separately.
        try (Stream<Path> stream = Files.list(root)) {
            return stream
                .filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".java"))
                .sorted()
                .collect(Collectors.toList());
        }
    }

    private void showError(String title, String msg) {
        Display.getDefault().asyncExec(() -> {
            Shell shell = Display.getDefault().getActiveShell();
            org.eclipse.jface.dialogs.MessageDialog.openError(shell, title, msg);
        });
    }
    
    private ReplyModel findReplyById(List<ReplyModel> replies, String replyId) {
        if (replies == null || replyId == null) return null;

        for (ReplyModel reply : replies) {
            if (replyId.equals(reply.id)) {
                return reply;
            }

            ReplyModel nested = findReplyById(reply.replies, replyId);
            if (nested != null) {
                return nested;
            }
        }

        return null;
    }

    private static String escapeJs(String s) {
        return s.replace("\\", "\\\\").replace("'", "\\'").replace("\"", "\\\"");
    }

    @Override public void setFocus() {
        if (browser != null && !browser.isDisposed()) browser.setFocus();
    }

    @Override public void dispose() { super.dispose(); }

    // ── Inner classes ─────────────────────────────────────────────────────────

    private static final class MethodEntry {
        final String label, methodId;
        MethodEntry(String label, String methodId) { this.label = label; this.methodId = methodId; }
    }
    
    public void setLoggedInUser(String username) {

        if (browser == null || browser.isDisposed()) {
            return;
        }

        Display.getDefault().asyncExec(() -> {

            if (browser == null || browser.isDisposed()) {
                return;
            }

            browser.execute(
                "if(typeof setCurrentAuthor==='function') " +
                "setCurrentAuthor('" + escapeJs(username) + "');"
            );
        });
    }
}
