package rejd_csci;

import gwu.rejd.gui.ClassDiagramView;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;

import java.nio.file.Path;

public class FileAndProjectListner implements ISelectionListener, IResourceChangeListener {

    // ── ISelectionListener ────────────────────────────────────────────────────

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

    // ── IResourceChangeListener ───────────────────────────────────────────────

    @Override
    public void resourceChanged(IResourceChangeEvent event) {
        IResourceDelta delta = event.getDelta();
        if (delta == null) return;

        for (IResourceDelta child : delta.getAffectedChildren()) {
            IResource resource = child.getResource();
            if (!(resource instanceof IProject)) continue;

            // Only act on the OPEN flag for a project that is now open
            if ((child.getFlags() & IResourceDelta.OPEN) == 0) continue;
            IProject project = (IProject) resource;
            if (!project.isOpen()) continue;

            IPath eclipsePath = project.getLocation();
            if (eclipsePath == null) continue;
            Path projectPath = eclipsePath.toFile().toPath();

            // Load notes from disk and push into the live WebView on the JavaFX thread.
            // loadNotesIntoLiveView() is a no-op if no diagram has been rendered yet;
            // the notes will be loaded from disk automatically at the next renderGraph() call.
            ClassDiagramView.loadNotesIntoLiveView(projectPath);
        }
    }
}
