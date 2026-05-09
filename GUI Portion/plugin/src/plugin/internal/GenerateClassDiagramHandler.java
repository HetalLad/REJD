/*
File Name: GenerateClassDiagramHandler.java
Authors: Anirvinna Jain, Hetal Lad, Saptorshee Nag
Description: The file implements the context menu option to generate class diagrams.
*/

// Package info
package plugin.internal;

// Import statements
import plugin.ui.RejdDiagramView;
import plugin.ui.actions.GenerateClassDiagramAction;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.handlers.HandlerUtil;

import java.io.File;

/**
 * Handles "Generate Class Diagram" from the right-click context menu.
 * Gets the source directory from the selected resource's project and delegates
 * all parsing, model building, and rendering to RejdDiagramView → gwu.rejd.
 */
public class GenerateClassDiagramHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        ISelection sel = HandlerUtil.getCurrentSelection(event);
        if (!(sel instanceof IStructuredSelection ss)) return null;

        Object first = ss.getFirstElement();
        if (first == null) return null;

        File sourceDir = GenerateClassDiagramAction.resolveSourceDir(first);

        Shell shell = HandlerUtil.getActiveShell(event);
        IWorkbenchPage page = HandlerUtil.getActiveWorkbenchWindow(event).getActivePage();

        try {
            RejdDiagramView view = (RejdDiagramView) page.showView(RejdDiagramView.ID);
            view.generateClassDiagram(sourceDir);
        } catch (PartInitException ex) {
            new org.eclipse.jface.dialogs.MessageDialog(shell, "REJD Error", null,
                "Could not open diagram view: " + ex.getMessage(),
                org.eclipse.jface.dialogs.MessageDialog.ERROR,
                new String[]{"OK"}, 0).open();
        }

        return null;
    }
}
