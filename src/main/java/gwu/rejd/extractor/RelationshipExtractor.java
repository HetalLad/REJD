/*
Filename: RelationshipExtractor.java
Authors: Anirvinna Jain, Hetal Lad, Saptorshee Nag
Description: The file extracts relationships within a project.
*/

// Package info
package gwu.rejd.extractor;

// Import statements
import gwu.rejd.model.FieldModel;
import gwu.rejd.model.MethodModel;
import gwu.rejd.model.ParamModel;
import gwu.rejd.model.ProjectModel;
import gwu.rejd.model.RelationshipModel;
import gwu.rejd.model.TypeModel;
import gwu.rejd.model.enums.RelationshipKind;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
* Public class implements the ProjectModel relationship extractor.
*/
public class RelationshipExtractor {
    // Extracting relationships into a list
    public List<RelationshipModel> extract(ProjectModel projectModel) {
        Set<RelationshipModel> relationships = new LinkedHashSet<>();

        Set<String> knownSimpleNames = new LinkedHashSet<>();
        for (TypeModel type : projectModel.getTypesByFqn().values()) {
            knownSimpleNames.add(type.getSimpleName());
        }

        for (TypeModel type : projectModel.getTypesByFqn().values()) {
            String sourceFqn = type.getFqn();
            String sourceSimple = type.getSimpleName();

            // EXTENDS
            if (type.getSuperclass() != null && !type.getSuperclass().isBlank()) {
                String superSimple = simpleName(type.getSuperclass());
                if (!superSimple.equals(sourceSimple)) {
                    relationships.add(new RelationshipModel(sourceFqn, superSimple, RelationshipKind.EXTENDS));
                }
            }

            // IMPLEMENTS
            for (String iface : type.getInterfaces()) {
                String ifaceSimple = simpleName(iface);
                if (!ifaceSimple.equals(sourceSimple)) {
                    relationships.add(new RelationshipModel(sourceFqn, ifaceSimple, RelationshipKind.IMPLEMENTS));
                }
            }

            Set<String> structuralTargets = new LinkedHashSet<>();

            // FIELD RELATIONSHIPS
            for (FieldModel field : type.getFields()) {
                Set<String> referenced = extractReferencedTypes(field.getType());

                boolean collectionLike = isCollectionLike(field.getType());
                boolean arrayLike = isArrayLike(field.getType());

                for (String referencedType : referenced) {
                    if (!knownSimpleNames.contains(referencedType)) continue;
                    if (referencedType.equals(sourceSimple)) continue;

                    RelationshipKind kind;

                    if (collectionLike || arrayLike) {
                        kind = RelationshipKind.AGGREGATES;
                    } else if (field.getModifiers().contains("final")) {
                        kind = RelationshipKind.COMPOSES;
                    } else {
                        kind = RelationshipKind.DIRECTED_ASSOCIATION;
                    }

                    relationships.add(new RelationshipModel(sourceFqn, referencedType, kind));
                    structuralTargets.add(referencedType);
                }
            }

            // METHOD RELATIONSHIPS
            Set<String> usedTypes = new LinkedHashSet<>();

            for (MethodModel method : type.getMethods()) {
                for (String referencedType : extractReferencedTypes(method.getReturnType())) {
                    addUses(sourceFqn, sourceSimple, referencedType, knownSimpleNames, structuralTargets, usedTypes, relationships);
                }

                for (ParamModel param : method.getParams()) {
                    for (String referencedType : extractReferencedTypes(param.getType())) {
                        addUses(sourceFqn, sourceSimple, referencedType, knownSimpleNames, structuralTargets, usedTypes, relationships);
                    }
                }
            }
        }

        return new ArrayList<>(relationships);
    }

    // To add the uses relation
    private void addUses(String sourceFqn,
                         String sourceSimple,
                         String typeName,
                         Set<String> knownSimpleNames,
                         Set<String> structuralTargets,
                         Set<String> usedTypes,
                         Set<RelationshipModel> relationships) {

        if (typeName == null || typeName.isBlank()) return;
        if (!knownSimpleNames.contains(typeName)) return;
        if (typeName.equals(sourceSimple)) return;
        if (structuralTargets.contains(typeName)) return;
        if (!usedTypes.add(typeName)) return;

        relationships.add(new RelationshipModel(sourceFqn, typeName, RelationshipKind.USES));
    }

    // To extract the referenced types
    private Set<String> extractReferencedTypes(String type) {
        Set<String> result = new LinkedHashSet<>();
        if (type == null || type.isBlank()) return result;

        String cleaned = type
                .replace("...", " ")
                .replace("[", " ")
                .replace("]", " ")
                .replace("<", " ")
                .replace(">", " ")
                .replace(",", " ")
                .replace("?", " ")
                .replace("&", " ")
                .replace("extends", " ")
                .replace("super", " ");

        String[] parts = cleaned.trim().split("\\s+");
        for (String part : parts) {
            if (part.isBlank()) continue;

            String simple = simpleName(part);
            if (isPrimitiveOrVoid(simple)) continue;
            if (isContainerType(simple)) continue;

            result.add(simple);
        }

        return result;
    }

    // Checks what the collection type is
    private boolean isCollectionLike(String type) {
        if (type == null) return false;
        String t = type.trim();
        return t.contains("<") && (
                t.startsWith("List<") ||
                t.startsWith("Set<") ||
                t.startsWith("Collection<") ||
                t.startsWith("Iterable<") ||
                t.startsWith("Map<") ||
                t.startsWith("Queue<")
        );
    }

    // Checks if the type is array
    private boolean isArrayLike(String type) {
        return type != null && type.contains("[");
    }

    // Checks if it's a container
    private boolean isContainerType(String typeName) {
        switch (typeName) {
            case "List":
            case "Set":
            case "Collection":
            case "Iterable":
            case "Map":
            case "Queue":
            case "ArrayList":
            case "LinkedList":
            case "HashSet":
            case "LinkedHashSet":
            case "HashMap":
            case "LinkedHashMap":
            case "Optional":
                return true;
            default:
                return false;
        }
    }

    // Name modification
    private String simpleName(String typeName) {
        if (typeName == null || typeName.isBlank()) return "";
        int lastDot = typeName.lastIndexOf('.');
        return lastDot >= 0 ? typeName.substring(lastDot + 1).trim() : typeName.trim();
    }

    // Checks if the datatype is primitive.
    private boolean isPrimitiveOrVoid(String typeName) {
        switch (typeName) {
            case "byte":
            case "short":
            case "int":
            case "long":
            case "float":
            case "double":
            case "boolean":
            case "char":
            case "void":
            case "String":
                return true;
            default:
                return false;
        }
    }
}
