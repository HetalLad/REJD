/*
File Name: GenerateSequenceDiagramHandler.java
Authors: Anirvinna Jain, Hetal Lad, Saptorshee Nag
Description: The file implements the context menu option to generate sequence diagram.
*/

// Package info
package plugin.internal;

// Import statements
import gwu.rejd.extractor.MultiFileProjectLoader;
import gwu.rejd.model.MethodModel;
import gwu.rejd.model.ProjectModel;
import gwu.rejd.model.TypeModel;
import plugin.ui.RejdDiagramView;
import plugin.ui.actions.GenerateClassDiagramAction;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.dialogs.ListDialog;
import org.eclipse.ui.handlers.HandlerUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Handles "Generate Sequence Diagram" from the REJD main menu bar.
 *
 * Single .java file selected → parses that file, shows method picker.
 * Package/project selected  → loads the entire source tree, shows method
 *                             picker across all types.
 */
public class GenerateSequenceDiagramHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        Shell shell = HandlerUtil.getActiveShell(event);
        IWorkbenchPage page = HandlerUtil.getActiveWorkbenchWindow(event).getActivePage();

        ISelection sel = HandlerUtil.getCurrentSelection(event);
        if (!(sel instanceof IStructuredSelection ss)) return null;
        Object element = ss.getFirstElement();

        // Try single .java file first
        Path singleFile = resolveSingleJavaFile(element);

        // Fall back to source directory for project/package selections
        File sourceDir = (singleFile == null)
                ? GenerateClassDiagramAction.resolveSourceDir(element)
                : null;

        if (singleFile == null && sourceDir == null) {
            showError(shell, "Please select a .java file, package, or project.");
            return null;
        }

        new Thread(() -> {
            try {
                MultiFileProjectLoader loader = new MultiFileProjectLoader();
                ProjectModel model;
                CompilationUnit singleCu;

                if (singleFile != null) {
                    model    = loader.loadProject("project", List.of(singleFile));
                    singleCu = loader.parseFile(singleFile);
                } else {
                    List<Path> javaPaths = collectJavaPaths(sourceDir.toPath());
                    if (javaPaths.isEmpty()) {
                        showError(shell, "No .java files found in: " + sourceDir);
                        return;
                    }
                    model    = loader.loadProject(sourceDir.getName(), javaPaths);
                    singleCu = null;
                }

                List<MethodEntry> methods = collectMethods(model);
                if (methods.isEmpty()) {
                    showError(shell, "No methods found in the selected source.");
                    return;
                }

                final ProjectModel finalModel = model;
                Display.getDefault().asyncExec(() -> {
                    MethodEntry chosen = pickMethod(shell, methods);
                    if (chosen == null) return;

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
        }, "rejd-seq-handler").start();

        return null;
    }

    // Helper Methods
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

    private CompilationUnit findCuForMethod(MultiFileProjectLoader loader,
                                             Path sourceRoot,
                                             MethodEntry chosen,
                                             ProjectModel model) {
        String fqn = chosen.methodId.contains("#")
                ? chosen.methodId.substring(0, chosen.methodId.indexOf('#'))
                : null;
        if (fqn == null) return null;
        String relativePath = fqn.replace('.', File.separatorChar) + ".java";
        try {
            return collectJavaPaths(sourceRoot).stream()
                    .filter(p -> p.toString().replace('/', File.separatorChar).endsWith(relativePath))
                    .findFirst()
                    .map(p -> { try { return loader.parseFile(p); } catch (IOException e) { return null; } })
                    .orElse(null);
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

    private void showError(Shell shell, String msg) {
        Display.getDefault().asyncExec(() ->
                new org.eclipse.jface.dialogs.MessageDialog(shell, "REJD Error", null, msg,
                        org.eclipse.jface.dialogs.MessageDialog.ERROR,
                        new String[]{"OK"}, 0).open());
    }

    private static final class MethodEntry {
        final String label, methodId;
        MethodEntry(String label, String methodId) { this.label = label; this.methodId = methodId; }
    }
}
