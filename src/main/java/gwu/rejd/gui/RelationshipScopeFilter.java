package gwu.rejd.gui;

import gwu.rejd.model.RelationshipModel;
import gwu.rejd.util.TypeNameUtils;

import java.util.List;
import java.util.stream.Collectors;

public class RelationshipScopeFilter {

    public List<RelationshipModel> filter(DiagramScope scope, List<RelationshipModel> relationships) {
        if (scope == null || scope.isEntireProject()) {
            return relationships;
        }

        return relationships.stream()
                .filter(rel -> scope.getPackageName().equals(TypeNameUtils.packageNameOf(rel.sourceFqn())))
                .collect(Collectors.toList());
    }
}