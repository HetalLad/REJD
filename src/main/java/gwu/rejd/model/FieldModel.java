/*
File Name: FieldModel.java
Authors: Anirvinna Jain, Hetal Lad, Saptorshee Nag
Description: The file describes the field model.
*/

// Package info
package gwu.rejd.model;

// Import statements
import gwu.rejd.model.enums.Visibility;

import java.util.*;

/**
* Class that describes the FieldModel object
*/
public final class FieldModel {
  private final String name;
  private final String type;
  private final Visibility visibility;
  private final Set<String> modifiers;
  private final List<String> annotations;

  public FieldModel(String name, String type, Visibility visibility, Set<String> modifiers, List<String> annotations) {
    this.name = Objects.requireNonNull(name);
    this.type = Objects.requireNonNull(type);
    this.visibility = Objects.requireNonNull(visibility);
    this.modifiers = Collections.unmodifiableSet(new LinkedHashSet<>(modifiers == null ? Set.of() : modifiers));
    this.annotations = Collections.unmodifiableList(new ArrayList<>(annotations == null ? List.of() : annotations));
  }

  public String getName() { return name; }
  public String getType() { return type; }
  public Visibility getVisibility() { return visibility; }
  public Set<String> getModifiers() { return modifiers; }
  public List<String> getAnnotations() { return annotations; }
}
