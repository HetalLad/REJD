/*
 * File Name: PlantUmlClassDiagramGenerator.java
 * Authors: Anirvinna Jain, Hetal Lad, Saptorshee Nag
 * Description: Generates PlantUML source text for class diagrams
 * using parsed project types and relationships.
 */

package gwu.rejd.generator;

import gwu.rejd.model.FieldModel;
import gwu.rejd.model.MethodModel;
import gwu.rejd.model.ParamModel;
import gwu.rejd.model.ProjectModel;
import gwu.rejd.model.RelationshipModel;
import gwu.rejd.model.TypeModel;
import gwu.rejd.model.enums.TypeKind;
import gwu.rejd.model.enums.Visibility;

import java.util.List;
import java.util.stream.Collectors;

public class PlantUmlClassDiagramGenerator {

    // Builds the full PlantUML class diagram text
    public String generate(ProjectModel projectModel, List<RelationshipModel> relationships) {
        StringBuilder sb = new StringBuilder();

        sb.append("@startuml\n");
        sb.append("!pragma layout smetana\n");
        sb.append("left to right direction\n");
        sb.append("skinparam linetype ortho\n\n");

        sb.append("skinparam shadowing false\n");
        sb.append("skinparam dpi 170\n");
        sb.append("skinparam backgroundColor #FAFBFC\n");
        sb.append("skinparam defaultFontName Arial\n");
        sb.append("skinparam defaultFontSize 13\n");
        sb.append("skinparam ArrowColor #5B6470\n");
        sb.append("skinparam ArrowThickness 1.3\n\n");

        // Diagram styling
        sb.append("skinparam class {\n");
        sb.append("  BackgroundColor #FFF9CC\n");
        sb.append("  BorderColor #C97B63\n");
        sb.append("  ArrowColor #5B6470\n");
        sb.append("  FontColor #2F3542\n");
        sb.append("  FontSize 13\n");
        sb.append("  AttributeFontColor #2F3542\n");
        sb.append("  AttributeFontSize 12\n");
        sb.append("  StereotypeFontColor #5C6370\n");
        sb.append("  StereotypeFontSize 11\n");
        sb.append("}\n\n");

        sb.append("skinparam package {\n");
        sb.append("  BackgroundColor #F4F5F7\n");
        sb.append("  BorderColor #7E8794\n");
        sb.append("  FontColor #2F3542\n");
        sb.append("}\n\n");

        sb.append("hide circle\n");
        sb.append("hide empty fields\n");
        sb.append("hide empty methods\n\n");

        // Add all parsed classes/interfaces/enums
        for (TypeModel type : projectModel.getTypesByFqn().values()) {
            sb.append(typeBlock(type)).append("\n\n");
        }

        // Add UML relationships between types
        for (RelationshipModel rel : relationships) {
            sb.append(relationshipLine(rel)).append("\n");
        }

        sb.append("\n@enduml\n");
        return sb.toString();
    }

    // Generates the PlantUML block for a single type
    private String typeBlock(TypeModel type) {
        StringBuilder sb = new StringBuilder();

        sb.append(typeKeyword(type))
          .append(" ")
          .append(safeName(type.getSimpleName()))
          .append(" {\n");

        for (FieldModel field : type.getFields()) {
            sb.append("  ")
              .append(visibilitySymbol(field.getVisibility()))
              .append(field.getName())
              .append(" : ")
              .append(cleanType(field.getType()))
              .append("\n");
        }

        for (MethodModel method : type.getMethods()) {
            if (method.isConstructor()) {
                continue;
            }

            sb.append("  ")
              .append(visibilitySymbol(method.getVisibility()))
              .append(method.getName())
              .append("(")
              .append(formatParams(method.getParams()))
              .append(")")
              .append(" : ")
              .append(cleanType(method.getReturnType()))
              .append("\n");
        }

        sb.append("}");
        return sb.toString();
    }

    private String typeKeyword(TypeModel type) {
        if (type.getKind() == TypeKind.INTERFACE) return "interface";
        if (type.getKind() == TypeKind.ENUM) return "enum";
        if (type.getModifiers().contains("abstract")) return "abstract class";
        return "class";
    }

    private String visibilitySymbol(Visibility visibility) {
        switch (visibility) {
            case PUBLIC:
                return "+";
            case PRIVATE:
                return "-";
            case PROTECTED:
                return "#";
            case PACKAGE_PRIVATE:
            default:
                return "~";
        }
    }

    private String formatParams(List<ParamModel> params) {
        return params.stream()
                .map(p -> p.getName() + " : " + cleanType(p.getType()))
                .collect(Collectors.joining(", "));
    }

    // Converts extracted relationships into PlantUML arrows
    private String relationshipLine(RelationshipModel rel) {
        String source = safeName(simpleName(rel.sourceFqn()));
        String target = safeName(simpleName(rel.targetName()));

        String arrow;
        switch (rel.kind()) {
            case EXTENDS:
                arrow = "--|>";
                break;
            case IMPLEMENTS:
                arrow = "..|>";
                break;
            case USES:
                arrow = "..>";
                break;
            case ASSOCIATES:
                arrow = "-->";
                break;
            case AGGREGATES:
                arrow = "o--";
                break;
            case COMPOSES:
                arrow = "*--";
                break;
            default:
                arrow = "-->";
                break;
        }

        return source + " " + arrow + " " + target;
    }

    private String simpleName(String value) {
        if (value == null || value.isBlank()) return "";
        int lastDot = value.lastIndexOf('.');
        return lastDot >= 0 ? value.substring(lastDot + 1) : value;
    }

    // Cleans names so they work safely in PlantUML
    private String safeName(String value) {
        return value.replace("$", "_").replace(".", "_");
    }

    private String cleanType(String value) {
        if (value == null || value.isBlank()) return "";
        return value.replace("$", "_");
    }
}
