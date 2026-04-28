package plugin.ui;

import gwu.rejd.model.PackageNode;
import gwu.rejd.model.TypeModel;

import org.eclipse.jface.viewers.LabelProvider;

/** Provides display labels for the project package/type tree. */
public class ProjectTreeLabelProvider extends LabelProvider {

    @Override
    public String getText(Object element) {
        if (element instanceof PackageNode n)
            return n.getName().isEmpty() ? "(default)" : n.getName();
        if (element instanceof TypeModel t)
            return t.getSimpleName();
        return super.getText(element);
    }
}
