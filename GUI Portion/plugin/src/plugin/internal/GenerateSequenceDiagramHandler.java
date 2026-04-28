package plugin.internal;

import gwu.rejd.extractor.MultiFileProjectLoader;
import gwu.rejd.model.MethodModel;
import gwu.rejd.model.ProjectModel;
import gwu.rejd.model.TypeModel;
import plugin.ui.RejdDiagramView;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.dialogs.ListDialog;
import org.eclipse.ui.handlers.HandlerUtil;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles "Generate Sequence Diagram" from the right-click context menu.
 * Parses the selected .java file, shows a native SWT method picker, then
 * delegates all rendering to RejdDiagramView → gwu.rejd.
 */
public class GenerateSequenceDiagramHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        IFile file = getSelectedJavaFile(event);
        if (file == null) return null;

        Path javaPath = file.getLocation().toFile().toPath();
        Shell shell = HandlerUtil.getActiveShell(event);
        IWorkbenchPage page = HandlerUtil.getActiveWorkbenchWindow(event).getActivePage();

        try {
            MultiFileProjectLoader loader = new MultiFileProjectLoader();
            ProjectModel model = loader.loadProject("project", List.of(javaPath));
            CompilationUnit cu = loader.parseFile(javaPath);

            List<MethodEntry> methods = collectMethods(model);
            if (methods.isEmpty()) { showError(shell, "No methods found in the selected file."); return null; }

            MethodEntry chosen = pickMethod(shell, methods);
            if (chosen == null) return null;

            String methodId = chosen.methodId;
            String label    = chosen.label;

            try {
                RejdDiagramView view = (RejdDiagramView) page.showView(RejdDiagramView.ID);
                view.generateAndShowSequenceDiagram(model, cu, methodId, label);
            } catch (PartInitException ex) {
                showError(shell, "Could not open diagram view: " + ex.getMessage());
            }

        } catch (IOException ex) {
            showError(shell, "Failed to parse file:\n" + ex.getMessage());
        }

        return null;
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private IFile getSelectedJavaFile(ExecutionEvent event) {
        ISelection sel = HandlerUtil.getCurrentSelection(event);
        if (sel instanceof IStructuredSelection ss) {
            Object first = ss.getFirstElement();
            if (first instanceof IFile f && "java".equalsIgnoreCase(f.getFileExtension()))
                return f;
        }
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
        dialog.setMessage("Choose a method to generate a sequence diagram for:");
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
        new org.eclipse.jface.dialogs.MessageDialog(shell, "REJD Error", null, msg,
                org.eclipse.jface.dialogs.MessageDialog.ERROR, new String[]{"OK"}, 0).open();
    }

    private static final class MethodEntry {
        final String label, methodId;
        MethodEntry(String label, String methodId) { this.label = label; this.methodId = methodId; }
    }
}
