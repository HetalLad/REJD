package gwu.rejd.generator;

import gwu.rejd.model.FieldModel;
import gwu.rejd.model.MethodModel;
import gwu.rejd.model.ParamModel;
import gwu.rejd.model.ProjectModel;
import gwu.rejd.model.RelationshipModel;
import gwu.rejd.model.TypeModel;
import gwu.rejd.model.enums.Visibility;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class SimpleGraphGenerator {

	public String generate(ProjectModel projectModel, List<RelationshipModel> relationships) {
	    StringBuilder sb = new StringBuilder();
	    sb.append("{\"nodes\":[");

	    boolean first = true;
	    Set<String> internal = new HashSet<>();

	    for (TypeModel type : projectModel.getTypesByFqn().values()) {
	        String id = sanitize(type.getSimpleName());
	        internal.add(id);

	        if (!first) sb.append(",");
	        first = false;

	        sb.append("{")
	          .append("\"id\":\"").append(escape(id)).append("\",")
	          .append("\"label\":\"").append(escape(type.getSimpleName())).append("\",")
	          .append("\"members\":\"").append(escape(buildMembers(type))).append("\",")
	          .append("\"kind\":\"").append(type.getKind().name()).append("\",")
	          .append("\"isAbstract\":").append(type.getModifiers().contains("abstract"))
	          .append("}");
	    }

	    Set<String> externalAdded = new HashSet<>();
	    for (RelationshipModel rel : relationships) {
	        String target = sanitize(simpleName(rel.targetName()));
	        if (!internal.contains(target) && externalAdded.add(target)) {
	            if (!first) sb.append(",");
	            first = false;

	            sb.append("{")
	              .append("\"id\":\"").append(escape(target)).append("\",")
	              .append("\"label\":\"").append(escape(simpleName(rel.targetName()))).append("\",")
	              .append("\"members\":\"\"")
	              .append("}");
	        }
	    }

	    sb.append("],\"edges\":[");

	    boolean firstEdge = true;
	    for (RelationshipModel rel : relationships) {
	        String source = sanitize(simpleName(rel.sourceFqn()));
	        String target = sanitize(simpleName(rel.targetName()));

	        String edgeId = "edge::"
	                + sanitize(rel.sourceFqn())
	                + "::"
	                + sanitize(rel.targetName())
	                + "::"
	                + rel.kind().name();

	        if (!firstEdge) sb.append(",");
	        firstEdge = false;

	        sb.append("{")
	          .append("\"id\":\"").append(escape(edgeId)).append("\",")
	          .append("\"source\":\"").append(escape(source)).append("\",")
	          .append("\"target\":\"").append(escape(target)).append("\",")
	          .append("\"kind\":\"").append(rel.kind().name()).append("\"")
	          .append("}");
	    }

	    sb.append("]}");
	    return sb.toString();
	}

    private String buildMembers(TypeModel type) {
        StringBuilder sb = new StringBuilder();

        for (FieldModel field : type.getFields()) {
            sb.append(visibilitySymbol(field.getVisibility()))
              .append(field.getName())
              .append(" : ")
              .append(field.getType())
              .append("\n");
        }

        for (MethodModel method : type.getMethods()) {
            if (method.isConstructor()) continue;

            sb.append(visibilitySymbol(method.getVisibility()))
              .append(method.getName())
              .append("(")
              .append(formatParams(method.getParams()))
              .append(")")
              .append(" : ")
              .append(method.getReturnType())
              .append("\n");
        }

        return sb.toString().trim();
    }

    private String formatParams(List<ParamModel> params) {
        return params.stream()
                .map(p -> p.getName() + " : " + p.getType())
                .collect(Collectors.joining(", "));
    }

    private String visibilitySymbol(Visibility visibility) {
        switch (visibility) {
            case PUBLIC: return "+";
            case PRIVATE: return "-";
            case PROTECTED: return "#";
            case PACKAGE_PRIVATE:
            default: return "~";
        }
    }

    private String simpleName(String value) {
        int lastDot = value.lastIndexOf('.');
        return lastDot >= 0 ? value.substring(lastDot + 1) : value;
    }

    private String sanitize(String value) {
        return value.replace("$", "_").replace(".", "_").replace(" ", "_");
    }

    private String escape(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "")
                .replace("\n", "\\n");
    }
}