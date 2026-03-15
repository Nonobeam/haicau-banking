package per.nonobeam.config;

import com.github.f4b6a3.ulid.UlidCreator;

public final class UlidGenerator {

  private UlidGenerator() {}

  public static String generate(String prefix) {
    String ulid = UlidCreator.getUlid().toLowerCase();
    return prefix + "_" + ulid;
  }
}
