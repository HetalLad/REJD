
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
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.jface.dialogs.TitleAreaDialog;

// Source: https://www.vogella.com/tutorials/EclipseDialogs/article.html

public class LoginDialog extends TitleAreaDialog 
{
    private Text txtFirstName;

    private String firstName;

    public LoginDialog(Shell parentShell) 
    {
            super(parentShell);
    }


    public void create() 
    {
        super.create();
        setTitle("Login");
        setMessage("Enter your Username", IMessageProvider.INFORMATION);
    }

    protected Control createDialogArea(Composite parent) {
        Composite area = (Composite) super.createDialogArea(parent);
        Composite container = new Composite(area, SWT.NONE);
        container.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        GridLayout layout = new GridLayout(2, false);
        container.setLayout(layout);

        createFirstName(container);

        return area;
    }

    private void createFirstName(Composite container) {
        Label lbtFirstName = new Label(container, SWT.NONE);
        lbtFirstName.setText("Name");

        GridData dataFirstName = new GridData();
        dataFirstName.grabExcessHorizontalSpace = true;
        dataFirstName.horizontalAlignment = GridData.FILL;

        txtFirstName = new Text(container, SWT.BORDER);
        txtFirstName.setLayoutData(dataFirstName);
    }

    @Override
    protected boolean isResizable() {
        return true;
    }

    private void saveInput() {
        firstName = txtFirstName.getText().trim();
    }
    
    @Override
    protected void okPressed() {
        saveInput();
        super.okPressed();
    }

    public String getFirstName() {
        return firstName;
    }
}
