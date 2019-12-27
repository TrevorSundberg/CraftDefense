package trevorsundberg;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

public class Utilities {
  public static final int TicksPerSecond = 20;
  public static final int TicksDespawn = 6000;

  public static boolean isHostile(EntityType type) {
    switch (type) {
    case BAT:
    case BLAZE:
    case CAVE_SPIDER:
    case CREEPER:
    case ENDER_DRAGON:
    case ENDERMAN:
    case GHAST:
    case GIANT:
    case MAGMA_CUBE:
    case PIG_ZOMBIE:
    case SILVERFISH:
    case SKELETON:
    case SLIME:
    case SPIDER:
    case WITCH:
    case WITHER:
    case WOLF:
    case ZOMBIE:
      return true;
    default:
      return false;
    }
  }

  public static ItemStack createWrittenBook(String title, String author, String... pages) {
    ItemStack stack = new ItemStack(Material.WRITTEN_BOOK);
    BookMeta meta = (BookMeta) stack.getItemMeta();

    meta.setTitle(title);
    meta.setAuthor(author);
    meta.setPages(pages);
    return stack;
  }
}
