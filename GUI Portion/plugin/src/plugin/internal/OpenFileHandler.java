/*
File Name: OpenFileHandler.java
Authors: Anirvinna Jain, Hetal Lad, Saptorshee Nag
Description: The file implements the file opening methods.
*/

// Package info
package plugin.internal;

// Import statements
import gwu.rejd.extractor.MultiFileProjectLoader;
import gwu.rejd.model.MethodModel;
import gwu.rejd.model.ProjectModel;
import gwu.rejd.model.TypeModel;
import plugin.ui.RejdDiagramView;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.dialogs.ListDialog;
import org.eclipse.ui.handlers.HandlerUtil;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles "REJD > Open Java File..." from the main menu.
 * Opens a native SWT file browser, asks the user which diagram type to generate,
 * then delegates all generation to RejdDiagramView → gwu.rejd.
 */
public class OpenFileHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        Shell shell = HandlerUtil.getActiveShell(event);
        IWorkbenchPage page = HandlerUtil.getActiveWorkbenchWindow(event).getActivePage();
        
        FileDialog fd = new FileDialog(shell, SWT.OPEN | SWT.MULTI);
        fd.setText("Open Java File(s)");
        fd.setFilterExtensions(new String[]{"*.java", "*.*"});
        fd.setFilterNames(new String[]{"Java Files (*.java)", "All Files (*.*)"});
        if (fd.open() == null) return null;

        String filterPath = fd.getFilterPath();
        String[] names    = fd.getFileNames();
        if (names == null || names.length == 0) return null;

        List<Path> javaPaths = new ArrayList<>();
        for (String name : names) javaPaths.add(Paths.get(filterPath, name));

        int choice = new MessageDialog(shell, "REJD \u2014 Diagram Type", null,
                "What type of diagram do you want to generate?",
                MessageDialog.QUESTION,
                new String[]{"Class Diagram", "Sequence Diagram"}, 0).open();

        RejdDiagramView view;
        try {
            view = (RejdDiagramView) page.showView(RejdDiagramView.ID);
        } catch (PartInitException ex) {
            showError(shell, "Could not open diagram view: " + ex.getMessage());
            return null;
        }

        if (choice == 0) {
            // Class diagram — use the directory that contains the selected files
            view.generateClassDiagram(Paths.get(filterPath).toFile());
        } else if (choice == 1) {
            generateSequenceDiagram(shell, view, javaPaths.get(0));
        }

        return null;
    }

    // Sequence diagram helper methods
    private void generateSequenceDiagram(Shell shell, RejdDiagramView view, Path javaPath) {
        try {
            MultiFileProjectLoader loader = new MultiFileProjectLoader();
            ProjectModel model = loader.loadProject("project", List.of(javaPath));
            CompilationUnit cu = loader.parseFile(javaPath);

            List<MethodEntry> methods = collectMethods(model);
            if (methods.isEmpty()) { showError(shell, "No methods found in the selected file."); return; }

            MethodEntry chosen = pickMethod(shell, methods);
            if (chosen == null) return;

            view.generateAndShowSequenceDiagram(model, cu, chosen.methodId, chosen.label);

        } catch (IOException ex) {
            showError(shell, "Failed to parse file: " + ex.getMessage());
        }
    }

    // Helper methods
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
        MessageDialog.openError(shell, "REJD Error", msg);
    }

    private static final class MethodEntry {
        final String label, methodId;
        MethodEntry(String label, String methodId) { this.label = label; this.methodId = methodId; }
    }
}
