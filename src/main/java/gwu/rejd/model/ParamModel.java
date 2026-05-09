/*
File Name: ParamModel.java
Authors: Anirvinna Jain, Hetal Lad, Saptorshee Nag
Description: The file describes the parameter models.
*/

// Package info
package gwu.rejd.model;

// Import statements
import java.util.Objects;

/**
 * This is a simple immutable model for storing parameter data.
 */
public final class ParamModel {
  private final String name;
  private final String type;

  public ParamModel(String name, String type) {
    // Some parameters may not have names available
    // depending on parsing/source information.
    this.name = name == null ? "" : name;
    this.type = Objects.requireNonNull(type);
  }

  public String getName() { return name; }
  public String getType() { return type; }
}
