package gwu.rejd.model;

import java.util.Objects;

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