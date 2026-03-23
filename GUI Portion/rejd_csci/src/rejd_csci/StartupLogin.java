package rejd_csci;

import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IStartup;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

public class StartupLogin implements IStartup {

    private static FileAndProjectListner listener;

    @Override
    public void earlyStartup() {
        Display.getDefault().asyncExec(() -> {
            Shell shell = Display.getDefault().getActiveShell();

            LoginDialog dialog = new LoginDialog(shell);
            dialog.create();

            if (dialog.open() == Window.OK) {
                IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();

                listener = new FileAndProjectListner();
                window.getSelectionService().addPostSelectionListener(listener);
            }
        });
    }
}