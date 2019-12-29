package trevorsundberg;

import org.bukkit.Material;

public class Utilities {
  public static final int TicksPerSecond = 20;
  public static final int TicksDespawn = 6000;

  public static String toHumanFriendlyMaterialName(Material material) {
    return material.toString().replace("_", " ").toLowerCase();
  }
}
