package plugin.internal;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Dialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

/**
 * Lightweight login dialog — shows the OS username pre-filled (read-only)
 * and asks the user to confirm with a single "Continue" button.
 * No password field; no external authentication.
 *
 * Returns the confirmed username string, or null if the shell was closed.
 */
public class LoginDialog extends Dialog {

    /** Ensures the dialog is shown at most once per JVM session. */
    private static boolean shown = false;

    private String result;

    public LoginDialog(Shell parent) {
        super(parent, SWT.APPLICATION_MODAL | SWT.DIALOG_TRIM);
        setText("REJD Diagrams");
    }

    /** Opens the dialog and returns the confirmed username, or {@code null} if dismissed.
     *  After the first call the dialog is skipped and the OS username is returned immediately. */
    public String open() {
        String osUsername = System.getProperty("user.name");
        if (shown) return osUsername;
        shown = true;

        Shell shell = new Shell(getParent(), getStyle());
        shell.setText(getText());

        GridLayout layout = new GridLayout(2, false);
        layout.marginWidth  = 16;
        layout.marginHeight = 12;
        layout.verticalSpacing = 10;
        shell.setLayout(layout);

        // Title label
        Label title = new Label(shell, SWT.NONE);
        title.setText("Welcome back");
        GridData titleGd = new GridData(SWT.LEFT, SWT.CENTER, true, false, 2, 1);
        title.setLayoutData(titleGd);

        // Separator
        Label sep = new Label(shell, SWT.SEPARATOR | SWT.HORIZONTAL);
        sep.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));

        // Username label + read-only text
        new Label(shell, SWT.NONE).setText("Username:");

        Text userText = new Text(shell, SWT.BORDER | SWT.READ_ONLY);
        userText.setText(osUsername);
        userText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        // Spacer
        new Label(shell, SWT.NONE).setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));

        // Continue button — right-aligned
        new Label(shell, SWT.NONE); // left filler
        Button continueBtn = new Button(shell, SWT.PUSH);
        continueBtn.setText("Continue");
        continueBtn.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
        shell.setDefaultButton(continueBtn);

        continueBtn.addListener(SWT.Selection, e -> {
            result = osUsername;
            shell.close();
        });

        shell.addListener(SWT.Close, e -> {
            if (result == null) result = null; // dismissed without clicking Continue
        });

        shell.pack();
        shell.setMinimumSize(280, shell.getSize().y);

        // Centre over parent
        Shell parent = getParent();
        int x = parent.getLocation().x + (parent.getSize().x - shell.getSize().x) / 2;
        int y = parent.getLocation().y + (parent.getSize().y - shell.getSize().y) / 2;
        shell.setLocation(x, y);

        shell.open();

        Display display = getParent().getDisplay();
        while (!shell.isDisposed()) {
            if (!display.readAndDispatch()) display.sleep();
        }

        return result;
    }
}
