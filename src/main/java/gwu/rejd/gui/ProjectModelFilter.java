package gwu.rejd.gui;

import gwu.rejd.model.PackageNode;
import gwu.rejd.model.ProjectModel;
import gwu.rejd.model.TypeModel;

import java.util.LinkedHashMap;
import java.util.Map;

public class ProjectModelFilter {

    public ProjectModel filter(ProjectModel original, DiagramScope scope) {
        if (original == null || scope == null || scope.isEntireProject()) {
            return original;
        }

        Map<String, TypeModel> filteredTypes = new LinkedHashMap<>();

        for (Map.Entry<String, TypeModel> entry : original.getTypesByFqn().entrySet()) {
            TypeModel type = entry.getValue();
            if (scope.getPackageName().equals(type.getPackageName())) {
                filteredTypes.put(entry.getKey(), type);
            }
        }

        PackageNode filteredRoot = buildPackageTree(filteredTypes);

        return new ProjectModel(
                original.getProjectId(),
                scope.getPackageName(),
                original.getImports(),
                filteredRoot,
                filteredTypes
        );
    }

    private PackageNode buildPackageTree(Map<String, TypeModel> typesByFqn) {
        PackageNode root = new PackageNode("");

        for (TypeModel type : typesByFqn.values()) {
            addToPackageTree(root, type.getPackageName(), type.getFqn());
        }

        return root;
    }

    private void addToPackageTree(PackageNode root, String packageName, String typeFqn) {
        PackageNode node = root;

        if (packageName != null && !packageName.isBlank()) {
            for (String segment : packageName.split("\\.")) {
                node = node.getOrCreateChild(segment);
            }
        }

        node.addType(typeFqn);
    }
}