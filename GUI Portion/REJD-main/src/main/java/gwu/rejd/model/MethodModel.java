package gwu.rejd.model;

import gwu.rejd.model.enums.Visibility;

import java.util.*;

public final class MethodModel {
  private final String methodId;
  private final String name;
  private final String returnType; 
  private final List<ParamModel> params;
  private final Visibility visibility;
  private final Set<String> modifiers;
  private final List<String> annotations;
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
    this.returnType = returnType == null ? "" : returnType;
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