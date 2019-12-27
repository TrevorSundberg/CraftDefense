package trevorsundberg;

import java.util.HashMap;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public class SavedInventoryManager implements Listener {
  class FullInventory {
    public ItemStack[] Armor;
    public ItemStack[] Inventory;
  }

  private final HashMap<UUID, FullInventory> SavedInventoryPerPlayer = new HashMap<UUID, FullInventory>();

  @EventHandler
  public void onEntityDeath(PlayerDeathEvent event) {
    event.getDrops().clear();

    Player player = event.getEntity();
    PlayerInventory playerInventory = player.getInventory();

    FullInventory fullInventory = new FullInventory();
    fullInventory.Armor = playerInventory.getArmorContents();
    fullInventory.Inventory = playerInventory.getContents();

    this.SavedInventoryPerPlayer.put(player.getUniqueId(), fullInventory);
  }

  @EventHandler
  public void onPlayerRespawn(PlayerRespawnEvent event) {
    Player p = event.getPlayer();
    PlayerInventory playerInventory = p.getInventory();

    FullInventory fullInventory = this.SavedInventoryPerPlayer.get(p.getUniqueId());

    if (fullInventory != null) {
      playerInventory.setArmorContents(fullInventory.Armor);
      playerInventory.setContents(fullInventory.Inventory);
    }

    this.SavedInventoryPerPlayer.remove(p.getUniqueId());
  }
}
