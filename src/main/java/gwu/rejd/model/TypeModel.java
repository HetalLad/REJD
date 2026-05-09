/*
File Name: TypeModel.java
Authors: Anirvinna Jain, Hetal Lad, Saptorshee Nag
Description: The file describes the type model class
*/

// Package info
package gwu.rejd.model;

// Import Statements
import gwu.rejd.model.enums.TypeKind;
import gwu.rejd.model.enums.Visibility;

import java.util.*;

/**
* TypeModel class describes the TypeModel object.
*/
public final class TypeModel {
  private final String fqn;
  private final String simpleName;
  private final String packageName;
  private final TypeKind kind;
  private final Visibility visibility;
  private final Set<String> modifiers;     // "static", "abstract", etc.
  private final List<String> annotations;  // "@Override", etc.
  private final String superclass;         // may be null
  private final List<String> interfaces;   // strings for now
  private final List<FieldModel> fields;
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
    this.packageName = packageName == null ? "" : packageName;
    this.kind = Objects.requireNonNull(kind);
    this.visibility = Objects.requireNonNull(visibility);
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
