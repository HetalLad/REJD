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
* Public Class describes the parameter models.
*/
public final class ParamModel {
  private final String name;
  private final String type;

  public ParamModel(String name, String type) {
    this.name = name == null ? "" : name;
    this.type = Objects.requireNonNull(type);
  }

  public String getName() { return name; }
  public String getType() { return type; }
}
