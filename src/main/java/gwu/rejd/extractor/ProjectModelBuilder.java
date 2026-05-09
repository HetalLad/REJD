/*
Filename: ProjectModelBuilder.java
Authors: Anirvinna Jain, Hetal Lad, Saptorshee Nag
Description: The file handles building project models.
*/

// Package info
package gwu.rejd.extractor;

// Import statements
import gwu.rejd.model.*;
import gwu.rejd.model.enums.*;

import org.eclipse.jdt.core.dom.*;

import java.util.*;

/**
* Public class builds the projet model out of a project.
*/
public final class ProjectModelBuilder {
  // ProjectModel build method
  public ProjectModel build(String projectId, CompilationUnit cu) {
    String packageName =
        (cu.getPackage() != null) ? cu.getPackage().getName().getFullyQualifiedName() : "";

    // To extract all import statements from this file, including static and wildcard ones.
    List<String> imports = new ArrayList<>();
    for (Object impObj : cu.imports()) {
      ImportDeclaration imp = (ImportDeclaration) impObj;
      String name = imp.getName().getFullyQualifiedName();
      if (imp.isOnDemand()) name += ".*";
      if (imp.isStatic()) name = "static " + name;
      imports.add(name);
    }

    // To go through the top-level types and build a map of FQN to TypeModel.
    Map<String, TypeModel> typesByFqn = new LinkedHashMap<>();
    PackageNode root = new PackageNode("");

    // To visit all top level ones in the current compilation note.
    for (Object t : cu.types()) {
      if (t instanceof TypeDeclaration) {
        TypeModel tm = buildTypeFromTypeDecl((TypeDeclaration) t, packageName, null, typesByFqn, root);
        typesByFqn.put(tm.getFqn(), tm);
        addToPackageTree(root, tm.getPackageName(), tm.getFqn());
      } else if (t instanceof EnumDeclaration) {
        TypeModel tm = buildTypeFromEnumDecl((EnumDeclaration) t, packageName, null, typesByFqn, root);
        typesByFqn.put(tm.getFqn(), tm);
        addToPackageTree(root, tm.getPackageName(), tm.getFqn());
      }
    }

    //This because ProjectModel now expects (projectId, packageName, imports, root, typesByFqn).
    return new ProjectModel(projectId, packageName, imports, root, typesByFqn);
  }

  // The main Type Builders.
  private TypeModel buildTypeFromTypeDecl(TypeDeclaration td, String packageName,
      String parentTypeFqn, Map<String, TypeModel> typesByFqn, PackageNode root) {
    String simple = td.getName().getIdentifier();
    String fqn = buildTypeFqn(packageName, parentTypeFqn, simple);
    TypeKind kind = td.isInterface() ? TypeKind.INTERFACE : TypeKind.CLASS;

    Visibility vis = visibilityFromModifiers(td.getModifiers());
    Set<String> mods = modifierKeywords(td.getModifiers());
    List<String> ann = annotationStrings(td.modifiers());

    String superclass = td.getSuperclassType() != null ? td.getSuperclassType().toString() : null;

    List<String> interfaces = new ArrayList<>();
    for (Object it : td.superInterfaceTypes()) interfaces.add(it.toString());

    List<FieldModel> fields = new ArrayList<>();
    for (FieldDeclaration fd : td.getFields()) fields.addAll(buildFields(fd));

    List<MethodModel> methods = new ArrayList<>();
    for (MethodDeclaration md : td.getMethods()) methods.add(buildMethod(fqn, md));

    // Here we recursively register nested types.
    for (Object member : td.bodyDeclarations()) {
      if (member instanceof TypeDeclaration) {
        TypeModel nested = buildTypeFromTypeDecl((TypeDeclaration) member, packageName, fqn, typesByFqn, root);
        typesByFqn.put(nested.getFqn(), nested);
        addToPackageTree(root, nested.getPackageName(), nested.getFqn());
      } else if (member instanceof EnumDeclaration) {
        TypeModel nested = buildTypeFromEnumDecl((EnumDeclaration) member, packageName, fqn, typesByFqn, root);
        typesByFqn.put(nested.getFqn(), nested);
        addToPackageTree(root, nested.getPackageName(), nested.getFqn());
      }
    }

    return new TypeModel(fqn, simple, packageName, kind, vis, mods, ann, superclass, interfaces, fields, methods);
  }

  private TypeModel buildTypeFromEnumDecl(EnumDeclaration ed, String packageName,
      String parentTypeFqn, Map<String, TypeModel> typesByFqn, PackageNode root) {
    String simple = ed.getName().getIdentifier();
    String fqn = buildTypeFqn(packageName, parentTypeFqn, simple);

    Visibility vis = visibilityFromModifiers(ed.getModifiers());
    Set<String> mods = modifierKeywords(ed.getModifiers());
    List<String> ann = annotationStrings(ed.modifiers());

    List<String> interfaces = new ArrayList<>();
    for (Object it : ed.superInterfaceTypes()) interfaces.add(it.toString());

    // The Enums can have fields, methods, and even nested types in their body.
    List<FieldModel> fields = new ArrayList<>();
    List<MethodModel> methods = new ArrayList<>();

    for (Object bd : ed.bodyDeclarations()) {
      if (bd instanceof FieldDeclaration) fields.addAll(buildFields((FieldDeclaration) bd));
      if (bd instanceof MethodDeclaration) methods.add(buildMethod(fqn, (MethodDeclaration) bd));
      if (bd instanceof TypeDeclaration) {
        TypeModel nested = buildTypeFromTypeDecl((TypeDeclaration) bd, packageName, fqn, typesByFqn, root);
        typesByFqn.put(nested.getFqn(), nested);
        addToPackageTree(root, nested.getPackageName(), nested.getFqn());
      } else if (bd instanceof EnumDeclaration) {
        TypeModel nested = buildTypeFromEnumDecl((EnumDeclaration) bd, packageName, fqn, typesByFqn, root);
        typesByFqn.put(nested.getFqn(), nested);
        addToPackageTree(root, nested.getPackageName(), nested.getFqn());
      }
    }

    return new TypeModel(fqn, simple, packageName, TypeKind.ENUM, vis, mods, ann, null, interfaces, fields, methods);
  }

  //  The current members of the fields.
  private List<FieldModel> buildFields(FieldDeclaration fd) {
    Visibility vis = visibilityFromModifiers(fd.getModifiers());
    Set<String> mods = modifierKeywords(fd.getModifiers());
    List<String> ann = annotationStrings(fd.modifiers());
    String type = fd.getType().toString();
    
    // A single FieldDeclaration can declare multiple variables like "int x, y;".
    List<FieldModel> out = new ArrayList<>();
    for (Object frag : fd.fragments()) {
      VariableDeclarationFragment vdf = (VariableDeclarationFragment) frag;
      out.add(new FieldModel(vdf.getName().getIdentifier(), type, vis, mods, ann));
    }
    return out;
  }

  // To build the methods
  private MethodModel buildMethod(String ownerTypeFqn, MethodDeclaration md) {
    Visibility vis = visibilityFromModifiers(md.getModifiers());
    Set<String> mods = modifierKeywords(md.getModifiers());
    List<String> ann = annotationStrings(md.modifiers());

    boolean isCtor = md.isConstructor();
    String name = md.getName().getIdentifier();
    // This is because constructors don't have a return type, and getReturnType2() can be null in edge cases.
    String returnType = isCtor ? "" : (md.getReturnType2() == null ? "void" : md.getReturnType2().toString());

    List<ParamModel> params = new ArrayList<>();
    for (Object p : md.parameters()) {
      SingleVariableDeclaration svd = (SingleVariableDeclaration) p;
      // Here we Append "..." for varargs so the signature stays accurate.
      String pType = svd.getType().toString() + (svd.isVarargs() ? "..." : "");
      params.add(new ParamModel(svd.getName().getIdentifier(), pType));
    }

    String methodId = buildMethodId(ownerTypeFqn, name, params, returnType, isCtor);
    return new MethodModel(methodId, name, returnType, params, vis, mods, ann, isCtor);
  }

  //  Updated Helpers.
  private String buildTypeFqn(String packageName, String parentTypeFqn, String simpleName) {
    String base = (packageName == null || packageName.isBlank()) ? "" : packageName + ".";
    if (parentTypeFqn != null && !parentTypeFqn.isBlank()) {
      return parentTypeFqn + "." + simpleName;
    }
    return base + simpleName;
  }

  // Adds the package name to the package tree
  private void addToPackageTree(PackageNode root, String packageName, String typeFqn) {
    PackageNode node = root;
    if (packageName != null && !packageName.isBlank()) {
      for (String seg : packageName.split("\\.")) {
        node = node.getOrCreateChild(seg);
      }
    }
    node.addType(typeFqn);
  }

  private Visibility visibilityFromModifiers(int flags) {
    if (org.eclipse.jdt.core.dom.Modifier.isPublic(flags)) return Visibility.PUBLIC;
    if (org.eclipse.jdt.core.dom.Modifier.isProtected(flags)) return Visibility.PROTECTED;
    if (org.eclipse.jdt.core.dom.Modifier.isPrivate(flags)) return Visibility.PRIVATE;
    return Visibility.PACKAGE_PRIVATE;
  }

  private Set<String> modifierKeywords(int flags) {
    Set<String> s = new LinkedHashSet<>();
    if (org.eclipse.jdt.core.dom.Modifier.isStatic(flags)) s.add("static");
    if (org.eclipse.jdt.core.dom.Modifier.isFinal(flags)) s.add("final");
    if (org.eclipse.jdt.core.dom.Modifier.isAbstract(flags)) s.add("abstract");
    if (org.eclipse.jdt.core.dom.Modifier.isSynchronized(flags)) s.add("synchronized");
    if (org.eclipse.jdt.core.dom.Modifier.isNative(flags)) s.add("native");
    if (org.eclipse.jdt.core.dom.Modifier.isTransient(flags)) s.add("transient");
    if (org.eclipse.jdt.core.dom.Modifier.isVolatile(flags)) s.add("volatile");
    if (org.eclipse.jdt.core.dom.Modifier.isDefault(flags)) s.add("default");
    return s;
  }

  // For the member annotations
  private List<String> annotationStrings(List<?> modifiersAndAnnotations) {
    List<String> out = new ArrayList<>();
    for (Object o : modifiersAndAnnotations) {
      if (o instanceof Annotation) out.add(o.toString()); 
    }
    return out;
  }

  private String buildMethodId(
      String ownerTypeFqn,
      String name,
      List<ParamModel> params,
      String returnType,
      boolean isCtor
  ) {
    StringBuilder sb = new StringBuilder();
    sb.append(ownerTypeFqn).append("#").append(isCtor ? "<init>" : name).append("(");
    for (int i = 0; i < params.size(); i++) {
      if (i > 0) sb.append(",");
      sb.append(params.get(i).getType());
    }
    sb.append(")");
    if (!isCtor) sb.append(":").append(returnType);
    return sb.toString();
  }
}
