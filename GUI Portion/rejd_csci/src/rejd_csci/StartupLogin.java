package rejd_csci;

import gwu.rejd.notes.NoteModel;
import gwu.rejd.notes.NotePreloadCache;
import gwu.rejd.notes.NoteRepository;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IStartup;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

import java.nio.file.Path;
import java.util.List;

public class StartupLogin implements IStartup {

    private static FileAndProjectListner listener;

    @Override
    public void earlyStartup() {
        // Pre-load notes for all projects already open in the workspace.
        // earlyStartup() runs on a background thread, so file I/O is safe here.
        preloadNotesForOpenProjects();

        Display.getDefault().asyncExec(() -> {
            Shell shell = Display.getDefault().getActiveShell();

            LoginDialog dialog = new LoginDialog(shell);
            dialog.create();

            if (dialog.open() == Window.OK) {
                IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();

                listener = new FileAndProjectListner();

                // Opens the DiagramView when the user clicks a file or project
                window.getSelectionService().addPostSelectionListener(listener);

                // Re-injects notes whenever a project is opened during the session
                ResourcesPlugin.getWorkspace().addResourceChangeListener(
                        listener, IResourceChangeEvent.POST_CHANGE);
            }
        });
    }

    private static void preloadNotesForOpenProjects() {
        IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
        for (IProject project : projects) {
            if (!project.isOpen()) continue;
            IPath eclipsePath = project.getLocation();
            if (eclipsePath == null) continue;
            Path projectPath = eclipsePath.toFile().toPath();
            List<NoteModel> notes = NoteRepository.load(projectPath);
            NotePreloadCache.put(projectPath, notes);
        }
    }
}
