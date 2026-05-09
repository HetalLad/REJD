/*
File Name: GenerateClassDiagramAction.java
Authors: Anirvinna Jain, Hetal Lad, Saptorshee Nag
Description: Implements the action after clicking 'Generate Class Diagram' from the context menu
*/

// Package info
package plugin.ui.actions;

// Import statements
import plugin.ui.RejdDiagramView;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;

import java.io.File;

/**
 * Context-menu action: "Generate Class Diagram".
 * Handles ICompilationUnit, IPackageFragment, IJavaProject (Java model)
 * and IFile, IProject (resource model) selections.
 * Source directory preference: src/main/java → src → project root.
 */
public class GenerateClassDiagramAction implements IObjectActionDelegate {

    private ISelection selection;

    @Override
    public void setActivePart(IAction action, IWorkbenchPart targetPart) {}

    @Override
    public void selectionChanged(IAction action, ISelection selection) {
        this.selection = selection;
    }

    @Override
    public void run(IAction action) {
        System.out.println("REJD: GenerateClassDiagramAction.run() triggered, selection=" + selection);
        if (!(selection instanceof IStructuredSelection ss)) return;
        Object element = ss.getFirstElement();
        System.out.println("REJD: element = " + (element == null ? "null" : element.getClass().getName()));
        if (element == null) return;

        File sourceDir = resolveSourceDir(element);
        if (sourceDir == null) {
            Shell shell = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
            MessageDialog.openError(shell, "REJD", "Could not determine source directory.");
            return;
        }

        System.out.println("REJD: GenerateClassDiagramAction → " + sourceDir);

        IWorkbenchPage page = PlatformUI.getWorkbench()
                .getActiveWorkbenchWindow().getActivePage();
        try {
            RejdDiagramView view = (RejdDiagramView) page.showView(RejdDiagramView.ID);
            view.generateClassDiagram(sourceDir);
        } catch (PartInitException ex) {
            Shell shell = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
            MessageDialog.openError(shell, "REJD Error",
                    "Could not open diagram view: " + ex.getMessage());
        }
    }

    /**
     * Resolves the Java source root from any of the supported selection types.
     */
    public static File resolveSourceDir(Object element) {
        IProject project = null;

        // Java model types (Package Explorer)
        if (element instanceof ICompilationUnit cu) {
            // Single file → use its parent folder (the package directory)
            org.eclipse.core.runtime.IPath loc = cu.getResource().getLocation();
            return loc != null ? loc.toFile().getParentFile() : null;
        } else if (element instanceof IPackageFragment pkg) {
            // Package → resolve the actual folder on disk, not the source root.
            // This ensures model/enums shows only enum classes, not the whole tree.
            org.eclipse.core.runtime.IPath loc =
                    pkg.getResource() != null ? pkg.getResource().getLocation() : null;
            if (loc != null) return loc.toFile();
            // Fallback: climb to source root if resource location unavailable
            project = pkg.getJavaProject().getProject();
        } else if (element instanceof IJavaProject jp) {
            project = jp.getProject();
        }
        // Resource types 
        else if (element instanceof IFile f) {
            // Single file resource → use its parent folder
            org.eclipse.core.runtime.IPath loc = f.getLocation();
            return loc != null ? loc.toFile().getParentFile() : null;
        } else if (element instanceof IProject p) {
            project = p;
        }

        if (project == null || !project.isOpen()) return null;

        IFolder srcMain = project.getFolder("src/main/java");
        if (srcMain.exists()) return srcMain.getLocation().toFile();

        IFolder src = project.getFolder("src");
        if (src.exists()) return src.getLocation().toFile();

        return project.getLocation().toFile();
    }
}
