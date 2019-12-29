package trevorsundberg;

import java.util.HashSet;
import java.util.Iterator;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

public class InfiniteBlockManager implements Listener {
  Plugin Plugin;
  HashSet<Material> BlockTypes;

  private final String Prefix = "Infinite ";

  void initialize(Plugin plugin, HashSet<Material> blockTypes) {
    this.Plugin = plugin;
    this.BlockTypes = blockTypes;

    PluginManager manager = plugin.getServer().getPluginManager();
    manager.registerEvents(this, plugin);

    final InfiniteBlockManager self = this;
    plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
      @Override
      public void run() {
        if (plugin.isEnabled() == false)
          return;
        for (Player player : plugin.getServer().getOnlinePlayers()) {
          self.forceOneOfEachBlock(player);
        }
      }
    }, 1, 1);
  }

  private void updateName(ItemStack stack) {
    ItemMeta meta = stack.getItemMeta();
    if (meta.getDisplayName() == null || meta.getDisplayName().length() == 0) {
      meta.setDisplayName(Prefix + Utilities.toHumanFriendlyMaterialName(stack.getType()));
    }
    stack.setItemMeta(meta);
  }

  private void forceOneOfEachBlock(Player p) {
    HashSet<Material> foundMaterials = new HashSet<Material>();

    Material cursorType = p.getItemOnCursor().getType();
    if (this.BlockTypes.contains(cursorType)) {
      foundMaterials.add(cursorType);
    }

    Inventory inv = p.getInventory();
    for (int i = 0; i <= inv.getSize(); ++i) {
      ItemStack item = inv.getItem(i);
      if (item == null)
        continue;

      Material itemType = item.getType();
      if (this.BlockTypes.contains(itemType)) {
        if (foundMaterials.contains(itemType) == false) {
          if (item.getAmount() != 1) {
            item.setAmount(1);
            this.updateName(item);
            inv.setItem(i, item);
          }
          foundMaterials.add(itemType);
        } else {
          // Remove any extra infinite items in their inventory
          inv.remove(item);
        }
      }
    }

    // Search through torch no visualall the block types we support, and if we
    // didn't find one then
    // give it to the player
    Iterator<Material> blockTypes = this.BlockTypes.iterator();
    while (blockTypes.hasNext()) {
      Material mat = blockTypes.next();
      if (foundMaterials.contains(mat) == false) {
        ItemStack s = new ItemStack(mat, 1);
        this.updateName(s);
        inv.addItem(s);
      }
    }
  }

  @EventHandler
  public void onBlockBreak(BlockBreakEvent event) {
    Block b = event.getBlock();

    if (this.BlockTypes.contains(b.getType())) {
      event.setCancelled(true);
      b.setType(Material.AIR);
    }
  }

  // This should probably never happen, but just in case it does, prevent items
  // from spawning when we have infinite
  @EventHandler
  public void onItemSpawn(ItemSpawnEvent event) {
    if (this.BlockTypes.contains(event.getEntity().getItemStack().getType()))
      event.setCancelled(true);
  }
}
