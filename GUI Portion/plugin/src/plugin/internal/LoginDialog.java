/*
File Name: LoginDialog.java
Authors: Anirvinna Jain, Hetal Lad, Saptorshee Nag
Description: The file is used to make the login dialgue appear at the start of the program execution.
*/

// Package info
package plugin.internal;

// Import statements
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Dialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.jface.dialogs.TitleAreaDialog;

/**
* Class used to build the login dialog at the start of the program execution.
* Source: https://www.vogella.com/tutorials/EclipseDialogs/article.html
*/
public class LoginDialog extends TitleAreaDialog 
{
    private Text txtFirstName;

    private String firstName;

    public LoginDialog(Shell parentShell) 
    {
            super(parentShell);
    }

    // Create Dialog Popup
    public void create() 
    {
        super.create();
        setTitle("Login");
        setMessage("Enter your Username", IMessageProvider.INFORMATION);
    }

    // Create the dialog area
    protected Control createDialogArea(Composite parent) {
        Composite area = (Composite) super.createDialogArea(parent);
        Composite container = new Composite(area, SWT.NONE);
        container.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        GridLayout layout = new GridLayout(2, false);
        container.setLayout(layout);

        createFirstName(container);

        return area;
    }

    // Creates the first name labels and handles the data.
    private void createFirstName(Composite container) {
        Label lbtFirstName = new Label(container, SWT.NONE);
        lbtFirstName.setText("Name");

        GridData dataFirstName = new GridData();
        dataFirstName.grabExcessHorizontalSpace = true;
        dataFirstName.horizontalAlignment = GridData.FILL;

        txtFirstName = new Text(container, SWT.BORDER);
        txtFirstName.setLayoutData(dataFirstName);
    }

    // Checks if the dialog is resizable
    @Override
    protected boolean isResizable() {
        return true;
    }

    // Saves input
    private void saveInput() {
        firstName = txtFirstName.getText().trim();
    }

    // Checks if the user is done
    @Override
    protected void okPressed() {
        saveInput();
        super.okPressed();
    }

    // Getter for the name value
    public String getFirstName() {
        return firstName;
    }
}
