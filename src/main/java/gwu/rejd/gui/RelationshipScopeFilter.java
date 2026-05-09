/*
 * File Name: RelationshipScopeFilter.java
 * Authors: Anirvinna Jain, Hetal Lad, Saptorshee Nag
 * Description: Filters relationships based on the currently
 * selected diagram scope/package.
 */

package gwu.rejd.gui;

import gwu.rejd.model.RelationshipModel;
import gwu.rejd.util.TypeNameUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Used to limit visible relationships when a package-level
 * diagram scope is selected.
 */

public class RelationshipScopeFilter {

    public List<RelationshipModel> filter(DiagramScope scope, List<RelationshipModel> relationships) {

        // Show everything when viewing the entire project
        if (scope == null || scope.isEntireProject()) {
            return relationships;
        }

        return relationships.stream()
                .filter(rel -> scope.getPackageName().equals(TypeNameUtils.packageNameOf(rel.sourceFqn())))
                .collect(Collectors.toList());
    }
}
