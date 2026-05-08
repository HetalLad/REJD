package plugin.internal;

import gwu.rejd.notes.NotePreloadCache;
import gwu.rejd.notes.NoteRepository;

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
import java.util.List;
import gwu.rejd.notes.NoteModel;

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
                page.showView(plugin.ui.RejdDiagramView.ID);
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

            // Pre-load notes from disk into the in-memory cache.
            // RejdDiagramView reads from NotePreloadCache the next time a diagram
            // is rendered, so no JavaFX call is needed here.
            List<NoteModel> notes = NoteRepository.load(projectPath);
            NotePreloadCache.put(projectPath, notes);
        }
    }
}
