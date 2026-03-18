package gwu.rejd.extractor;

import gwu.rejd.model.FieldModel;
import gwu.rejd.model.MethodModel;
import gwu.rejd.model.ParamModel;
import gwu.rejd.model.ProjectModel;
import gwu.rejd.model.RelationshipModel;
import gwu.rejd.model.TypeModel;
import gwu.rejd.model.enums.RelationshipKind;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RelationshipExtractor {

    public List<RelationshipModel> extract(ProjectModel projectModel) {
        List<RelationshipModel> relationships = new ArrayList<>();

        // Build set of known simple names for quick lookup
        Set<String> knownSimpleNames = new HashSet<>();
        for (TypeModel type : projectModel.getTypesByFqn().values()) {
            knownSimpleNames.add(type.getSimpleName());
        }

        for (TypeModel type : projectModel.getTypesByFqn().values()) {
            String sourceFqn = type.getFqn();

            // EXTENDS
            if (type.getSuperclass() != null) {
                relationships.add(new RelationshipModel(sourceFqn, type.getSuperclass(), RelationshipKind.EXTENDS));
            }

            // IMPLEMENTS
            for (String iface : type.getInterfaces()) {
                relationships.add(new RelationshipModel(sourceFqn, iface, RelationshipKind.IMPLEMENTS));
            }

            // Track COMPOSES targets to avoid duplicating as USES
            Set<String> composedTypes = new HashSet<>();

            // COMPOSES — field type matches a known type in the model
            for (FieldModel field : type.getFields()) {
                String baseType = extractBaseType(field.getType());
                if (knownSimpleNames.contains(baseType)) {
                    relationships.add(new RelationshipModel(sourceFqn, baseType, RelationshipKind.COMPOSES));
                    composedTypes.add(baseType);
                }
            }

            // USES — return type or parameter types match a known type, skip if already COMPOSES
            Set<String> usedTypes = new HashSet<>();
            for (MethodModel method : type.getMethods()) {
                String returnBase = extractBaseType(method.getReturnType());
                addUses(sourceFqn, returnBase, knownSimpleNames, composedTypes, usedTypes, relationships);

                for (ParamModel param : method.getParams()) {
                    String paramBase = extractBaseType(param.getType());
                    addUses(sourceFqn, paramBase, knownSimpleNames, composedTypes, usedTypes, relationships);
                }
            }
        }

        return relationships;
    }

    private void addUses(String sourceFqn, String typeName, Set<String> knownSimpleNames,
                         Set<String> composedTypes, Set<String> usedTypes,
                         List<RelationshipModel> relationships) {
        if (typeName.isEmpty()) return;
        if (!knownSimpleNames.contains(typeName)) return;
        if (composedTypes.contains(typeName)) return;
        if (!usedTypes.add(typeName)) return; // already added
        relationships.add(new RelationshipModel(sourceFqn, typeName, RelationshipKind.USES));
    }

    /**
     * Strips generics and array brackets to get the bare type name.
     * e.g. "List<Foo>" → "List", "Foo[]" → "Foo"
     */
    private String extractBaseType(String type) {
        if (type == null || type.isEmpty()) return "";
        int generic = type.indexOf('<');
        if (generic != -1) type = type.substring(0, generic);
        int array = type.indexOf('[');
        if (array != -1) type = type.substring(0, array);
        return type.trim();
    }
}
