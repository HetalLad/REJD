package gwu.rejd.extractor;

import gwu.rejd.model.*;
import gwu.rejd.model.enums.*;

import org.eclipse.jdt.core.dom.*;

import java.util.*;

public final class ProjectModelBuilder {

  public ProjectModel build(String projectId, CompilationUnit cu) {
    String packageName =
        (cu.getPackage() != null) ? cu.getPackage().getName().getFullyQualifiedName() : "";

    // Grab all import statements from this file, including static and wildcard ones
    List<String> imports = new ArrayList<>();
    for (Object impObj : cu.imports()) {
      ImportDeclaration imp = (ImportDeclaration) impObj;
      String name = imp.getName().getFullyQualifiedName();
      if (imp.isOnDemand()) name += ".*";
      if (imp.isStatic()) name = "static " + name;
      imports.add(name);
    }

    // Walk through the top-level types and build a map of FQN -> TypeModel
    Map<String, TypeModel> typesByFqn = new LinkedHashMap<>();
    PackageNode root = new PackageNode("");

    // Visit top-level types in this compilation unit
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

    //ProjectModel now expects (projectId, packageName, imports, root, typesByFqn)
    return new ProjectModel(projectId, packageName, imports, root, typesByFqn);
  }

  // ------------------ Type Builders ------------------

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

    // Recursively register nested types
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

    // Enums can have fields, methods, and even nested types in their body
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

  //  Members 

  private List<FieldModel> buildFields(FieldDeclaration fd) {
    Visibility vis = visibilityFromModifiers(fd.getModifiers());
    Set<String> mods = modifierKeywords(fd.getModifiers());
    List<String> ann = annotationStrings(fd.modifiers());
    String type = fd.getType().toString();
    
    // A single FieldDeclaration can declare multiple variables (e.g. "int x, y;")
    List<FieldModel> out = new ArrayList<>();
    for (Object frag : fd.fragments()) {
      VariableDeclarationFragment vdf = (VariableDeclarationFragment) frag;
      out.add(new FieldModel(vdf.getName().getIdentifier(), type, vis, mods, ann));
    }
    return out;
  }

  private MethodModel buildMethod(String ownerTypeFqn, MethodDeclaration md) {
    Visibility vis = visibilityFromModifiers(md.getModifiers());
    Set<String> mods = modifierKeywords(md.getModifiers());
    List<String> ann = annotationStrings(md.modifiers());

    boolean isCtor = md.isConstructor();
    String name = md.getName().getIdentifier();
    // Constructors don't have a return type, and getReturnType2() can be null in edge cases
    String returnType = isCtor ? "" : (md.getReturnType2() == null ? "void" : md.getReturnType2().toString());

    List<ParamModel> params = new ArrayList<>();
    for (Object p : md.parameters()) {
      SingleVariableDeclaration svd = (SingleVariableDeclaration) p;
      // Append "..." for varargs so the signature stays accurate
      String pType = svd.getType().toString() + (svd.isVarargs() ? "..." : "");
      params.add(new ParamModel(svd.getName().getIdentifier(), pType));
    }

    String methodId = buildMethodId(ownerTypeFqn, name, params, returnType, isCtor);
    return new MethodModel(methodId, name, returnType, params, vis, mods, ann, isCtor);
  }

  //  Helpers 

  private String buildTypeFqn(String packageName, String parentTypeFqn, String simpleName) {
    String base = (packageName == null || packageName.isBlank()) ? "" : packageName + ".";
    if (parentTypeFqn != null && !parentTypeFqn.isBlank()) {
      return parentTypeFqn + "." + simpleName;
    }
    return base + simpleName;
  }

  private void addToPackageTree(PackageNode root, String packageName, String typeFqn) {
    PackageNode node = root;
    if (packageName != null && !packageName.isBlank()) {
      // Split on dots and walk (or create) each segment of the package path
      for (String seg : packageName.split("\\.")) {
        node = node.getOrCreateChild(seg);
      }
    }
    node.addType(typeFqn);
  }

  // Using the fully qualified name here to avoid a clash with our own enums package import
  private Visibility visibilityFromModifiers(int flags) {
    if (org.eclipse.jdt.core.dom.Modifier.isPublic(flags)) return Visibility.PUBLIC;
    if (org.eclipse.jdt.core.dom.Modifier.isProtected(flags)) return Visibility.PROTECTED;
    if (org.eclipse.jdt.core.dom.Modifier.isPrivate(flags)) return Visibility.PRIVATE;
    return Visibility.PACKAGE_PRIVATE;
  }

  //Same here — fully qualifying Modifier to avoid the ambiguity
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

  private List<String> annotationStrings(List<?> modifiersAndAnnotations) {
    List<String> out = new ArrayList<>();
    for (Object o : modifiersAndAnnotations) {
      if (o instanceof Annotation) out.add(o.toString()); // e.g. @Override, @Deprecated
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
	// Build a signature like: com.example.Foo#doThing(int,String):void
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