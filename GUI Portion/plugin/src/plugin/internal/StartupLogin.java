/*
File Name: StartupLogin.java
Authors: Anirvinna Jain, Hetal Lad, Saptorshee Nag
Description: File to handle the project startup logic
*/

// Package info
package plugin.internal;

// Import statements
import gwu.rejd.notes.NoteModel;

import gwu.rejd.notes.NotePreloadCache;
import gwu.rejd.notes.NoteRepository;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import gwu.rejd.util.UserContext;

import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IStartup;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.jface.window.Window;

import java.nio.file.Path;
import java.util.List;
import plugin.ui.RejdDiagramView;

/**
* The class to handle the startup logic of the program.
*/
public class StartupLogin implements IStartup {

    private static FileAndProjectListner listener;

    @Override
    public void earlyStartup() {
        // Pre-load notes for all open workspace projects (background thread — safe for I/O)
        preloadNotesForOpenProjects();

        // Show login dialog, then register listeners and open the REJD view
        Display.getDefault().asyncExec(() -> {
            IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
            if (window == null) return;
            
            Shell parentShell = window.getShell();

            LoginDialog dialog = new LoginDialog(parentShell);
            
            if (dialog.open() == Window.OK)
            {
            	String username = dialog.getFirstName();
            	if (username != null) {
                    UserContext.setCurrentUser(username);
                }
            	
            	IWorkbenchPage page = window.getActivePage();

                if (page != null) {
                    RejdDiagramView view =
                        (RejdDiagramView) page.findView(RejdDiagramView.ID);

                    if (view != null) {
                        view.setLoggedInUser(username);
                    }
                }
            }
            
            
            listener = new FileAndProjectListner();

            
            // Open REJD Diagrams view immediately so it is visible on first launch
            IWorkbenchPage page = window.getActivePage();
            if (page != null) {
                try {
                    page.showView(plugin.ui.RejdDiagramView.ID);
                } catch (PartInitException ignored) {}
            }

            // Keep the view open whenever the user selects a file or project
            window.getSelectionService().addPostSelectionListener(listener);

            // Re-inject notes whenever a project is opened during the session
            ResourcesPlugin.getWorkspace().addResourceChangeListener(
                    listener, IResourceChangeEvent.POST_CHANGE);
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
