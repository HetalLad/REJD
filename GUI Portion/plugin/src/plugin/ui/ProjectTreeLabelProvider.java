/*
 * File Name: ProjectTreeLabelProvider.java
 * Authors: Anirvinna Jain, Hetal Lad, Saptorshee Nag
 * Description: This provides the labels for package and type nodes
 * displayed in the project explorer tree.
 */

package plugin.ui;

import gwu.rejd.model.PackageNode;
import gwu.rejd.model.TypeModel;

import org.eclipse.jface.viewers.LabelProvider;

/**
 * This handles display text for package/type tree nodes.
 */
public class ProjectTreeLabelProvider extends LabelProvider {

    @Override
    public String getText(Object element) {
        // This will show a readable label for the default package
        if (element instanceof PackageNode n)
            return n.getName().isEmpty() ? "(default)" : n.getName();
        // This will Display simple class/interface name in the tree
        if (element instanceof TypeModel t)
            return t.getSimpleName();
        return super.getText(element);
    }
}
