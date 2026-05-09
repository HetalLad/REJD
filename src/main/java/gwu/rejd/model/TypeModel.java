/*
File Name: TypeModel.java
Authors: Anirvinna Jain, Hetal Lad, Saptorshee Nag
Description: The file Represents a parsed Java type such as a class,
interface, enum, or annotation.
*/

// Package info
package gwu.rejd.model;

// Import Statements
import gwu.rejd.model.enums.TypeKind;
import gwu.rejd.model.enums.Visibility;

import java.util.*;

/**
 * This is a Immutable model used to store type-level
 * information extracted from the AST.
 */
public final class TypeModel {
  private final String fqn; // Fully qualified name (example: com.app.UserService)
  private final String simpleName;  // Simple class/interface name
  private final String packageName; // Package the type belongs to
  private final TypeKind kind; // Class, interface, enum, annotation, etc.
  private final Visibility visibility;
  private final Set<String> modifiers;     // Stores modifiers like static, abstract, final, etc.
  private final List<String> annotations;  // Stores annotations found on the type
  private final String superclass;         // Parent class if inheritance exists
  private final List<String> interfaces;   // Implemented interfaces
  private final List<FieldModel> fields;    // Parsed fields and methods belonging to this type
  private final List<MethodModel> methods;

  public TypeModel(
      String fqn,
      String simpleName,
      String packageName,
      TypeKind kind,
      Visibility visibility,
      Set<String> modifiers,
      List<String> annotations,
      String superclass,
      List<String> interfaces,
      List<FieldModel> fields,
      List<MethodModel> methods
  ) {
    this.fqn = Objects.requireNonNull(fqn);
    this.simpleName = Objects.requireNonNull(simpleName);

    // This is Default to empty package for unnamed/default package cases
    this.packageName = packageName == null ? "" : packageName;
    this.kind = Objects.requireNonNull(kind);
    this.visibility = Objects.requireNonNull(visibility);

    // These are defensive copies prevent accidental external modification
    this.modifiers = Collections.unmodifiableSet(new LinkedHashSet<>(modifiers == null ? Set.of() : modifiers));
    this.annotations = Collections.unmodifiableList(new ArrayList<>(annotations == null ? List.of() : annotations));
    this.superclass = superclass;
    this.interfaces = Collections.unmodifiableList(new ArrayList<>(interfaces == null ? List.of() : interfaces));
    this.fields = Collections.unmodifiableList(new ArrayList<>(fields == null ? List.of() : fields));
    this.methods = Collections.unmodifiableList(new ArrayList<>(methods == null ? List.of() : methods));
  }

  public String getFqn() { return fqn; }
  public String getSimpleName() { return simpleName; }
  public String getPackageName() { return packageName; }
  public TypeKind getKind() { return kind; }
  public Visibility getVisibility() { return visibility; }
  public Set<String> getModifiers() { return modifiers; }
  public List<String> getAnnotations() { return annotations; }
  public String getSuperclass() { return superclass; }
  public List<String> getInterfaces() { return interfaces; }
  public List<FieldModel> getFields() { return fields; }
  public List<MethodModel> getMethods() { return methods; }
}
