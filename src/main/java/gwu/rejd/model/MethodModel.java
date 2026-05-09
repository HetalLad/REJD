/*
File Name: MethodModel.java
Authors: Anirvinna Jain, Hetal Lad, Saptorshee Nag
Description: The file describes the method models.
*/

// Package info
package gwu.rejd.model;

// Import statements
import gwu.rejd.model.enums.Visibility;

import java.util.*;

/**
 * Immutable model representing a method extracted
 * from the Java AST.
 */

// This is a unique identifier used internally for tracking methods
public final class MethodModel {
  // Unique identifier used internally for tracking methods
  private final String methodId;
  // Core method information
  private final String name;
  private final String returnType; 
  // Parameters associated with this method
  private final List<ParamModel> params;
  private final Visibility visibility;
  // Modifiers such as static, final, abstract, etc.
  private final Set<String> modifiers;
  // Stores annotations like @Override or @Autowired
  private final List<String> annotations;
  // Helps distinguish constructors from regular methods
  private final boolean isConstructor;

  public MethodModel(
      String methodId,
      String name,
      String returnType,
      List<ParamModel> params,
      Visibility visibility,
      Set<String> modifiers,
      List<String> annotations,
      boolean isConstructor
  ) {
    this.methodId = Objects.requireNonNull(methodId);
    this.name = Objects.requireNonNull(name);

    // Because Constructors may not have a return type
    this.returnType = returnType == null ? "" : returnType;

    // Defensive copies to keep the model immutable
    this.params = Collections.unmodifiableList(new ArrayList<>(params == null ? List.of() : params));
    this.visibility = Objects.requireNonNull(visibility);
    this.modifiers = Collections.unmodifiableSet(new LinkedHashSet<>(modifiers == null ? Set.of() : modifiers));
    this.annotations = Collections.unmodifiableList(new ArrayList<>(annotations == null ? List.of() : annotations));
    this.isConstructor = isConstructor;
  }

  public String getMethodId() { return methodId; }
  public String getName() { return name; }
  public String getReturnType() { return returnType; }
  public List<ParamModel> getParams() { return params; }
  public Visibility getVisibility() { return visibility; }
  public Set<String> getModifiers() { return modifiers; }
  public List<String> getAnnotations() { return annotations; }
  public boolean isConstructor() { return isConstructor; }
}
