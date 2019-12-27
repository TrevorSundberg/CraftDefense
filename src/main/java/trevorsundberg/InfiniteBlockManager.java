package trevorsundberg;

import java.util.HashSet;
import java.util.Iterator;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public class InfiniteBlockManager implements Listener {
  Plugin Plugin;
  HashSet<Material> BlockTypes;

  void initialize(Plugin plugin, HashSet<Material> blockTypes) {
    this.Plugin = plugin;
    this.BlockTypes = blockTypes;
  }

  public void forceOneOfEachBlock(Player p) {
    this.forceOneOfEachBlock(p.getInventory());
    p.updateInventory();
  }

  @SuppressWarnings("deprecation")
  public void forceOneOfEachBlock(Inventory inv) {
    HashSet<Material> foundMaterials = new HashSet<Material>();

    for (int i = 0; i <= inv.getSize(); ++i) {
      ItemStack item = inv.getItem(i);
      if (item == null)
        continue;

      Material itemType = item.getType();
      if (this.BlockTypes.contains(itemType)) {
        if (foundMaterials.contains(itemType) == false) {
          item.setAmount(1);
          inv.setItem(i, item);
          foundMaterials.add(itemType);
        } else {
          // Remove any extra infinite torches in their inventory
          inv.remove(item);
        }
      }
    }

    // Search through all the block types we support, and if we didn't find one then
    // give it to the player
    Iterator<Material> blockTypes = this.BlockTypes.iterator();
    while (blockTypes.hasNext()) {
      Material mat = blockTypes.next();
      if (foundMaterials.contains(mat) == false) {
        ItemStack s = new ItemStack(mat, 1);
        inv.addItem(s);
      }
    }
  }

  @EventHandler
  public void onPlayerSpawn(PlayerRespawnEvent event) {
    this.forceOneOfEachBlock(event.getPlayer());
  }

  @EventHandler
  public void onBlockPlaced(BlockPlaceEvent event) {
    ItemStack item = event.getItemInHand();
    if (this.BlockTypes.contains(item.getType())) {
      this.forceOneOfEachBlock(event.getPlayer());
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

  // Prevent the player from removing infinite torches (if enabled)
  @EventHandler
  public void onInventoryMoveItem(InventoryMoveItemEvent event) {
    // if (this.InfiniteTorches && event.getItem().getType() == Material.TORCH)
    // event.setCancelled(true);
  }

  // Prevent the player from picking up more torches when we already have
  // 'infinite'
  @EventHandler
  public void onInventoryPickupItem(InventoryPickupItemEvent event) {
    ItemStack item = event.getItem().getItemStack();
    if (this.BlockTypes.contains(item.getType())) {
      event.getItem().remove();
      event.setCancelled(true);
      this.forceOneOfEachBlock(event.getInventory());
    }
  }

  // This should probably never happen, but just in case it does, prevent torches
  // from spawning when we have infinite
  @EventHandler
  public void onItemSpawn(ItemSpawnEvent event) {
    // if (this.InfiniteTorches && event.getEntity().getItemStack().getType() ==
    // Material.TORCH)
    // event.setCancelled(true);
  }
}
