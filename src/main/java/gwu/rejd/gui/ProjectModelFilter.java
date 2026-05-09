/*
Filename: ProjectModelFilter.java
Authors: Anirvinna Jain, Hetal Lad, Saptorshee Nag
Description: Filters a project model based on the diagram scope
*/

// Package info
package gwu.rejd.gui;

// Import Statements
import gwu.rejd.model.PackageNode;
import gwu.rejd.model.ProjectModel;
import gwu.rejd.model.TypeModel;

import java.util.LinkedHashMap;
import java.util.Map;

/**
Public class implements a filter for a ProjectModel based on a DiagramScope
*/
public class ProjectModelFilter {
    // Filter method
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

    // To build the package tree
    private PackageNode buildPackageTree(Map<String, TypeModel> typesByFqn) {
        PackageNode root = new PackageNode("");

        for (TypeModel type : typesByFqn.values()) {
            addToPackageTree(root, type.getPackageName(), type.getFqn());
        }

        return root;
    }

    // To add a package name to a package tree
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
