package rejd_csci;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;

public class FileAndProjectListner implements ISelectionListener {

    @Override
    public void selectionChanged(IWorkbenchPart sourcePart, ISelection selection) {
        if (!(selection instanceof IStructuredSelection)) {
            return;
        }

        Object element = ((IStructuredSelection) selection).getFirstElement();

        if (element instanceof IFile || element instanceof IProject) {
            IWorkbenchPage page = PlatformUI.getWorkbench()
                    .getActiveWorkbenchWindow()
                    .getActivePage();

            try {
                page.showView("rejd_csci.views.DiagramView");
            } catch (PartInitException e) {
                e.printStackTrace();
            }
        }
    }
}