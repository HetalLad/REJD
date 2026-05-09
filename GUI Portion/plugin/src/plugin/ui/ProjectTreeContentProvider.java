package plugin.ui;

import gwu.rejd.model.PackageNode;
import gwu.rejd.model.ProjectModel;
import gwu.rejd.model.TypeModel;

import org.eclipse.jface.viewers.ITreeContentProvider;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * This will build the package/type tree shown in the project explorer.
 */
public class ProjectTreeContentProvider implements ITreeContentProvider {

    private ProjectModel model;
    // This will keep track of full package names while the tree is being built
    private final Map<PackageNode, String> fullNames = new IdentityHashMap<>();

    @Override
    public Object[] getElements(Object inputElement) {
        fullNames.clear();
        if (!(inputElement instanceof ProjectModel)) return new Object[0];
        model = (ProjectModel) inputElement;
        PackageNode root = model.getPackageRoot();
        fullNames.put(root, "");
        return childrenOf(root);
    }

    @Override
    public Object[] getChildren(Object parent) {
        if (parent instanceof PackageNode node) return childrenOf(node);
        return new Object[0];
    }

    @Override
    public Object getParent(Object element) { return null; }

    @Override
    public boolean hasChildren(Object element) {
        if (!(element instanceof PackageNode node)) return false;
        return !node.getChildren().isEmpty() || !node.getTypeFqns().isEmpty();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Object[] childrenOf(PackageNode node) {
        if (model == null) return new Object[0];
        String parentFqn = fullNames.getOrDefault(node, "");
        List<Object> kids = new ArrayList<>();

        for (Map.Entry<String, PackageNode> e : node.getChildren().entrySet()) {
            String segment = e.getKey();
            PackageNode child = e.getValue();
            String childFqn = parentFqn.isEmpty() ? segment : parentFqn + "." + segment;
            fullNames.put(child, childFqn);
            kids.add(child);
        }

        for (String fqn : node.getTypeFqns()) {
            TypeModel t = model.getTypesByFqn().get(fqn);
            if (t != null) kids.add(t);
        }

        return kids.toArray();
    }

    /**
     * This is used by the label provider to display package names correctly.
     */
    public String getFullPackageName(PackageNode node) {
        return fullNames.getOrDefault(node, node.getName());
    }
}
