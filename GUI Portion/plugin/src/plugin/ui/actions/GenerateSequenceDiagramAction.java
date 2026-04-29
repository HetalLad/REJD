package plugin.ui.actions;

import gwu.rejd.extractor.MultiFileProjectLoader;
import gwu.rejd.model.MethodModel;
import gwu.rejd.model.ProjectModel;
import gwu.rejd.model.TypeModel;
import plugin.ui.RejdDiagramView;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.ListDialog;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Context-menu action: "Generate Sequence Diagram".
 *
 * Single file selected  → parses that file, shows method picker.
 * Package/project selected → loads the entire source tree, shows method picker
 *                            across all types (same behaviour as the standalone app).
 */
public class GenerateSequenceDiagramAction implements IObjectActionDelegate {

    private ISelection selection;

    @Override
    public void setActivePart(IAction action, IWorkbenchPart targetPart) {}

    @Override
    public void selectionChanged(IAction action, ISelection selection) {
        this.selection = selection;
    }

    @Override
    public void run(IAction action) {
        Shell shell = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();

        if (!(selection instanceof IStructuredSelection ss)) return;
        Object element = ss.getFirstElement();

        // Try to resolve a single .java file first
        Path singleFile = resolveSingleJavaFile(element);

        // Fall back: resolve the project source directory for multi-file loading
        File sourceDir = (singleFile == null)
                ? GenerateClassDiagramAction.resolveSourceDir(element)
                : null;

        if (singleFile == null && sourceDir == null) {
            MessageDialog.openInformation(shell, "REJD",
                    "Please select a .java file, package, or project.");
            return;
        }

        IWorkbenchPage page = PlatformUI.getWorkbench()
                .getActiveWorkbenchWindow().getActivePage();

        new Thread(() -> {
            try {
                ProjectModel model;
                CompilationUnit singleCu;   // non-null only for single-file mode
                MultiFileProjectLoader loader = new MultiFileProjectLoader();

                if (singleFile != null) {
                    // Single-file mode
                    model    = loader.loadProject("project", List.of(singleFile));
                    singleCu = loader.parseFile(singleFile);
                } else {
                    // Whole-project mode — load every .java under sourceDir
                    List<Path> javaPaths = collectJavaPaths(sourceDir.toPath());
                    if (javaPaths.isEmpty()) {
                        showError(shell, "No .java files found in: " + sourceDir);
                        return;
                    }
                    model    = loader.loadProject(sourceDir.getName(), javaPaths);
                    singleCu = null;        // resolved per-method below
                }

                List<MethodEntry> methods = collectMethods(model);
                if (methods.isEmpty()) {
                    showError(shell, "No methods found in the selected source.");
                    return;
                }

                // Back to UI thread: show method picker then trigger rendering
                final ProjectModel finalModel = model;
                Display.getDefault().asyncExec(() -> {
                    MethodEntry chosen = pickMethod(shell, methods);
                    if (chosen == null) return;

                    // For whole-project mode we need the CompilationUnit for the
                    // chosen method's file — look it up via the source tree.
                    CompilationUnit cu = singleCu;
                    if (cu == null) {
                        cu = findCuForMethod(loader, sourceDir.toPath(), chosen, finalModel);
                        if (cu == null) {
                            showError(shell, "Could not locate source for " + chosen.label);
                            return;
                        }
                    }

                    final CompilationUnit finalCu = cu;
                    try {
                        RejdDiagramView view = (RejdDiagramView) page.showView(RejdDiagramView.ID);
                        view.generateAndShowSequenceDiagram(finalModel, finalCu,
                                chosen.methodId, chosen.label);
                    } catch (PartInitException ex) {
                        showError(shell, "Could not open diagram view: " + ex.getMessage());
                    }
                });

            } catch (IOException ex) {
                showError(shell, "Failed to parse source:\n" + ex.getMessage());
            }
        }, "rejd-seq-action").start();
    }

    // ── Resolution helpers ────────────────────────────────────────────────────

    /** Returns the filesystem Path for a single selected .java file, or null. */
    private Path resolveSingleJavaFile(Object element) {
        if (element instanceof ICompilationUnit cu) {
            org.eclipse.core.runtime.IPath loc = cu.getResource().getLocation();
            return loc != null ? loc.toFile().toPath() : null;
        }
        if (element instanceof IFile f && "java".equalsIgnoreCase(f.getFileExtension())) {
            org.eclipse.core.runtime.IPath loc = f.getLocation();
            return loc != null ? loc.toFile().toPath() : null;
        }
        return null;
    }

    /**
     * For whole-project mode: finds and parses the .java file that declares
     * the type owning the chosen method, then returns its CompilationUnit.
     */
    private CompilationUnit findCuForMethod(MultiFileProjectLoader loader,
                                             Path sourceRoot,
                                             MethodEntry chosen,
                                             ProjectModel model) {
        // Extract the FQN from the methodId: "com.example.Foo#bar():void" → "com.example.Foo"
        String fqn = chosen.methodId.contains("#")
                ? chosen.methodId.substring(0, chosen.methodId.indexOf('#'))
                : null;
        if (fqn == null) return null;

        TypeModel type = model.getTypesByFqn().get(fqn);
        if (type == null) return null;

        // Convert FQN to relative file path, e.g. com/example/Foo.java
        String relativePath = fqn.replace('.', File.separatorChar) + ".java";

        try {
            List<Path> candidates = collectJavaPaths(sourceRoot);
            Path found = candidates.stream()
                    .filter(p -> p.toString().replace('/', File.separatorChar)
                                              .endsWith(relativePath))
                    .findFirst().orElse(null);
            return found != null ? loader.parseFile(found) : null;
        } catch (IOException ex) {
            return null;
        }
    }

    private List<Path> collectJavaPaths(Path root) throws IOException {
        try (Stream<Path> s = Files.walk(root)) {
            return s.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".java"))
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    // ── Method collection & picker ────────────────────────────────────────────

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

    private void showError(Shell shell, String msg) {
        Display.getDefault().asyncExec(() ->
                MessageDialog.openError(shell, "REJD Error", msg));
    }

    private static final class MethodEntry {
        final String label, methodId;
        MethodEntry(String label, String methodId) {
            this.label = label; this.methodId = methodId;
        }
    }
}
