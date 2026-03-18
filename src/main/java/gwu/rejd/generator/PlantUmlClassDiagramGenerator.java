package gwu.rejd.generator;

import gwu.rejd.model.FieldModel;
import gwu.rejd.model.MethodModel;
import gwu.rejd.model.ParamModel;
import gwu.rejd.model.ProjectModel;
import gwu.rejd.model.RelationshipModel;
import gwu.rejd.model.TypeModel;
import gwu.rejd.model.enums.RelationshipKind;
import gwu.rejd.model.enums.TypeKind;
import gwu.rejd.model.enums.Visibility;

import java.util.List;
import java.util.stream.Collectors;

public class PlantUmlClassDiagramGenerator {

    public String generate(ProjectModel projectModel, List<RelationshipModel> relationships) {
        StringBuilder sb = new StringBuilder();
        sb.append("@startuml\n");
        sb.append("!pragma layout smetana\n\n");
        sb.append("left to right direction\n\n");
        sb.append("skinparam classBackgroundColor lightyellow\n");
        sb.append("skinparam classBorderColor darkred\n");
        sb.append("skinparam classArrowColor darkred\n\n");

        for (TypeModel type : projectModel.getTypesByFqn().values()) {
            sb.append(typeBlock(type)).append("\n");
            if (hasNoVisibleMembers(type)) {
                sb.append("hide ").append(type.getSimpleName()).append(" members\n");
            }
        }

        sb.append("\n");
        for (RelationshipModel rel : relationships) {
            sb.append(relationshipLine(rel)).append("\n");
        }

        sb.append("\n@enduml\n");
        return sb.toString();
    }

    private String typeBlock(TypeModel type) {
        StringBuilder sb = new StringBuilder();

        sb.append(typeKeyword(type)).append(" ").append(type.getSimpleName()).append(" {\n");

        for (FieldModel field : type.getFields()) {
            sb.append("  ").append(visibilitySymbol(field.getVisibility()))
              .append(field.getType()).append(" ").append(field.getName()).append("\n");
        }

        for (MethodModel method : type.getMethods()) {
            if (method.isConstructor()) continue;
            sb.append("  ").append(visibilitySymbol(method.getVisibility()))
              .append(method.getReturnType()).append(" ").append(method.getName())
              .append("(").append(formatParams(method.getParams())).append(")\n");
        }

        sb.append("}");
        return sb.toString();
    }

    private boolean hasNoVisibleMembers(TypeModel type) {
        if (!type.getFields().isEmpty()) return false;
        for (MethodModel m : type.getMethods()) {
            if (!m.isConstructor()) return false;
        }
        return true;
    }

    private String typeKeyword(TypeModel type) {
        if (type.getKind() == TypeKind.INTERFACE) return "interface";
        if (type.getKind() == TypeKind.ENUM) return "enum";
        if (type.getModifiers().contains("abstract")) return "abstract class";
        return "class";
    }

    private String visibilitySymbol(Visibility visibility) {
        switch (visibility) {
            case PUBLIC:         return "+";
            case PRIVATE:        return "-";
            case PROTECTED:      return "#";
            case PACKAGE_PRIVATE: return "~";
            default:             return "~";
        }
    }

    private String formatParams(List<ParamModel> params) {
        return params.stream()
                .map(p -> p.getType() + " " + p.getName())
                .collect(Collectors.joining(", "));
    }

    private String relationshipLine(RelationshipModel rel) {
        String arrow;
        switch (rel.kind()) {
            case EXTENDS:    arrow = "--|>"; break;
            case IMPLEMENTS: arrow = "..|>"; break;
            case USES:       arrow = "-->";  break;
            case COMPOSES:   arrow = "*-->"; break;
            default:         arrow = "-->";  break;
        }
        return rel.sourceFqn() + " " + arrow + " " + rel.targetName();
    }
}
