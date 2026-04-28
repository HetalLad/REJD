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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Context-menu action: "Generate Sequence Diagram".
 * Resolves a .java source file from any selection type, parses it,
 * shows a method picker, then delegates rendering to {@link RejdDiagramView}.
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

        Path javaPath = resolveJavaPath();
        if (javaPath == null) {
            MessageDialog.openInformation(shell, "REJD",
                    "Please select a .java file to generate a sequence diagram.");
            return;
        }

        System.out.println("REJD: GenerateSequenceDiagramAction → " + javaPath);

        IWorkbenchPage page = PlatformUI.getWorkbench()
                .getActiveWorkbenchWindow().getActivePage();

        new Thread(() -> {
            try {
                MultiFileProjectLoader loader = new MultiFileProjectLoader();
                ProjectModel model = loader.loadProject("project", List.of(javaPath));
                CompilationUnit cu = loader.parseFile(javaPath);

                List<MethodEntry> methods = collectMethods(model);
                if (methods.isEmpty()) {
                    showError(shell, "No methods found in the selected file.");
                    return;
                }

                Display.getDefault().asyncExec(() -> {
                    MethodEntry chosen = pickMethod(shell, methods);
                    if (chosen == null) return;
                    try {
                        RejdDiagramView view = (RejdDiagramView) page.showView(RejdDiagramView.ID);
                        view.generateAndShowSequenceDiagram(model, cu, chosen.methodId, chosen.label);
                    } catch (PartInitException ex) {
                        showError(shell, "Could not open diagram view: " + ex.getMessage());
                    }
                });

            } catch (IOException ex) {
                showError(shell, "Failed to parse file:\n" + ex.getMessage());
            }
        }, "rejd-seq-action").start();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /**
     * Resolves a Path to a .java file from any supported selection type.
     * Returns null if the selection does not point to a .java file.
     */
    private Path resolveJavaPath() {
        if (!(selection instanceof IStructuredSelection ss)) return null;
        Object element = ss.getFirstElement();

        // ICompilationUnit: .java file in Package Explorer
        if (element instanceof ICompilationUnit cu) {
            org.eclipse.core.runtime.IPath loc = cu.getResource().getLocation();
            return loc != null ? loc.toFile().toPath() : null;
        }

        // IFile: raw resource (Project Explorer)
        if (element instanceof IFile f && "java".equalsIgnoreCase(f.getFileExtension())) {
            org.eclipse.core.runtime.IPath loc = f.getLocation();
            return loc != null ? loc.toFile().toPath() : null;
        }

        // IPackageFragment / IJavaProject / IProject: no single file — return null
        return null;
    }

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
