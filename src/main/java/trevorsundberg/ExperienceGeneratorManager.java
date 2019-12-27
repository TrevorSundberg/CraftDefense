package trevorsundberg;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;

public class ExperienceGeneratorManager implements Listener {
  public static String DisplayName = "Experience Generator";

  Plugin Plugin;
  Server Server;
  ItemStack ExperienceGeneratorStack;
  int TicksPerItemDestroyed = 25;

  float ValueScale = 0.04f;
  HashMap<Material, Float> ValuesPerMaterial = new HashMap<Material, Float>();

  final HashMap<Location, ExperienceGenerator> ExperienceGeneratorByLocation = new HashMap<Location, ExperienceGenerator>();

  public class ExperienceGenerator {
    public Plugin Plugin;
    public Location Location;
    public float AccumulatedAmount;

    public Inventory getInventory() {
      Block block = this.Location.getBlock();
      if (block.getState() instanceof InventoryHolder) {
        InventoryHolder inventoryHolder = (InventoryHolder) block.getState();
        Inventory inventory = inventoryHolder.getInventory();
        if (ExperienceGeneratorManager.isBlockOurType(block)) {
          return inventory;
        }
      }
      return null;
    }
  }

  void initialize(Plugin plugin) {
    Server server = plugin.getServer();
    server.getPluginManager().registerEvents(this, plugin);

    this.Plugin = plugin;
    this.Server = server;

    this.initializeValues();

    this.ExperienceGeneratorStack = new ItemStack(Material.DROPPER, 1);
    ItemMeta meta = this.ExperienceGeneratorStack.getItemMeta();
    meta.setDisplayName(DisplayName);
    this.ExperienceGeneratorStack.setItemMeta(meta);

    @SuppressWarnings("deprecation")
    ShapedRecipe recipe = new ShapedRecipe(this.ExperienceGeneratorStack);
    recipe.shape("CAC", "CBC", "CCC");
    recipe.setIngredient('A', Material.FURNACE);
    recipe.setIngredient('B', Material.FERMENTED_SPIDER_EYE);
    recipe.setIngredient('C', Material.REDSTONE_BLOCK);
    server.addRecipe(recipe);

    final ExperienceGeneratorManager self = this;
    server.getScheduler().runTaskTimer(plugin, new Runnable() {
      @Override
      public void run() {
        self.updateAllTables();
      }
    }, TicksPerItemDestroyed, TicksPerItemDestroyed);
  }

  private void updateAllTables() {
    ArrayList<Location> locationsToRemove = new ArrayList<Location>();

    // Loop through all the tables, consuming whatever items are in their place
    for (ExperienceGenerator table : this.ExperienceGeneratorByLocation.values()) {
      Inventory tableInventory = table.getInventory();

      // We know a table is invalid if it has no inventory
      if (tableInventory == null) {
        locationsToRemove.add(table.Location);
        continue;
      }

      // Loop through all slots (first to last)
      for (int i = 0; i < tableInventory.getSize(); ++i) {
        ItemStack stack = tableInventory.getItem(i);

        if (stack == null || stack.getAmount() == 0)
          continue;

        if (stack.getType() == Material.LEGACY_EXP_BOTTLE)
          continue;

        float xpGain = getValueForSingleItem(stack);
        table.AccumulatedAmount += xpGain;

        int amountLeftover = stack.getAmount() - 1;

        if (amountLeftover == 0) {
          tableInventory.clear(i);
        } else {
          stack.setAmount(amountLeftover);
          tableInventory.setItem(i, stack);
        }
        break;
      }

      // If we've accumulated enough xp for bottles, then attempt to add the bottles
      // to the table
      // Note: We actually backlog xp, so its safe!
      while (table.AccumulatedAmount >= 1.0f) {
        // Attempt to add an experience bottle to the experience generator
        HashMap<Integer, ItemStack> unstoredValues = tableInventory
            .addItem(new ItemStack(Material.LEGACY_EXP_BOTTLE, 1));

        // An empty or null hash map means the xp bottle was added
        if (unstoredValues == null || unstoredValues.isEmpty()) {
          table.AccumulatedAmount -= 1.0f;

          // Play an effect at this location (have to do it above, the client moves
          // effects out of blocks)
          Location center = table.Location.clone();
          center.add(0.5, 1.2, 0.5);
          center.getWorld().playEffect(center, Effect.POTION_BREAK, 0);
        } else {
          // Can't do anything else (nowhere to put it, just skip for now and process it
          // later)
          break;
        }
      }
    }

    // Clear any invalid tables out
    for (Location tableLocation : locationsToRemove) {
      this.ExperienceGeneratorByLocation.remove(tableLocation);
    }
  }

  public float getValueForSingleItem(ItemStack stack) {
    // By default, we assume all items return this much unscaled xp
    float value = 1.0f;

    // Check if we have a specific value for this material
    Float materialValue = this.ValuesPerMaterial.get(stack.getType());
    if (materialValue != null)
      value = materialValue.floatValue();

    // Enchanted items are worth more!
    value *= (stack.getEnchantments().size() + 1);
    return value * this.ValueScale;
  }

  public static boolean isBlockOurType(Block block) {
    if (block.getState() instanceof InventoryHolder) {
      List<MetadataValue> list = block.getMetadata("craftdefense_type");
      if (list.size() == 1) {
        return list.get(0).asString() == DisplayName;
      }
    }
    return false;
  }

  @EventHandler
  public void onBlockPlace(BlockPlaceEvent event) {
    Block block = event.getBlock();

    if (event.getItemInHand().getItemMeta().getDisplayName() == DisplayName
        || ExperienceGeneratorManager.isBlockOurType(block)) {
      ExperienceGenerator table = new ExperienceGenerator();
      table.Plugin = this.Plugin;
      table.Location = block.getLocation();
      table.AccumulatedAmount = 0.0f;
      block.setMetadata("craftdefense_type", new FixedMetadataValue(this.Plugin, DisplayName));
      this.ExperienceGeneratorByLocation.put(block.getLocation(), table);
    }
  }

  @EventHandler
  public void onBlockBreak(BlockBreakEvent event) {
    Block block = event.getBlock();

    if (ExperienceGeneratorManager.isBlockOurType(block)) {
      // We don't bother to remove it here, we just let the update handle removing the
      // table

      // Unfortunately for some reason the original block type (not our named type) is
      // dropped
      // moreover, setting the drops on the block DOES NOT actually affect the drops
      // (makes sense, should be on the event...)
      // So we have to cancel the event, turn the block into AIR, and then drop the
      // item ourselves.
      event.setCancelled(true);
      block.setType(Material.AIR);
      block.getWorld().dropItemNaturally(block.getLocation(), this.ExperienceGeneratorStack);
    }
  }

  private void initializeValues() {
    this.ValuesPerMaterial.put(Material.AIR, 0.0f);
    this.ValuesPerMaterial.put(Material.BOOKSHELF, 4.0f);
    this.ValuesPerMaterial.put(Material.BUCKET, 2.0f);
    this.ValuesPerMaterial.put(Material.CAULDRON, 2.0f);
    this.ValuesPerMaterial.put(Material.COAL, 2.0f);
    this.ValuesPerMaterial.put(Material.COAL_ORE, 1.5f);
    this.ValuesPerMaterial.put(Material.DIAMOND, 6.0f);
    this.ValuesPerMaterial.put(Material.DIAMOND_BLOCK, 60.0f);
    this.ValuesPerMaterial.put(Material.DIRT, 0.1f);
    this.ValuesPerMaterial.put(Material.EMERALD_BLOCK, 10.0f);
    this.ValuesPerMaterial.put(Material.EMERALD_ORE, 5.0f);
    this.ValuesPerMaterial.put(Material.ENCHANTED_BOOK, 2.0f);
    this.ValuesPerMaterial.put(Material.ENCHANTING_TABLE, 30.0f);
    this.ValuesPerMaterial.put(Material.ENDER_CHEST, 20.0f);
    this.ValuesPerMaterial.put(Material.LEGACY_EXP_BOTTLE, 0.0f);
    this.ValuesPerMaterial.put(Material.LEGACY_EYE_OF_ENDER, 4.0f);
    this.ValuesPerMaterial.put(Material.FERMENTED_SPIDER_EYE, 2.0f);
    this.ValuesPerMaterial.put(Material.GLOWSTONE, 8.0f);
    this.ValuesPerMaterial.put(Material.GLOWSTONE_DUST, 2.0f);
    this.ValuesPerMaterial.put(Material.GOLD_BLOCK, 30.0f);
    this.ValuesPerMaterial.put(Material.GOLD_INGOT, 3.0f);
    this.ValuesPerMaterial.put(Material.GRASS, 0.3f);
    this.ValuesPerMaterial.put(Material.GRAVEL, 0.2f);
    this.ValuesPerMaterial.put(Material.IRON_BLOCK, 20.0f);
    this.ValuesPerMaterial.put(Material.IRON_INGOT, 2.0f);
    this.ValuesPerMaterial.put(Material.LAPIS_BLOCK, 10.0f);
    this.ValuesPerMaterial.put(Material.MELON, 0.1f);
    this.ValuesPerMaterial.put(Material.NETHERRACK, 0.2f);
    this.ValuesPerMaterial.put(Material.OBSIDIAN, 5.0f);
    this.ValuesPerMaterial.put(Material.QUARTZ, 2.0f);
    this.ValuesPerMaterial.put(Material.QUARTZ_BLOCK, 20.0f);
    this.ValuesPerMaterial.put(Material.REDSTONE, 1.5f);
    this.ValuesPerMaterial.put(Material.REDSTONE_BLOCK, 15.0f);
    this.ValuesPerMaterial.put(Material.SAND, 0.1f);
    this.ValuesPerMaterial.put(Material.SNOW, 0.04f);
    this.ValuesPerMaterial.put(Material.SNOWBALL, 0.01f);
    this.ValuesPerMaterial.put(Material.SNOW_BLOCK, 0.06f);
    this.ValuesPerMaterial.put(Material.WRITTEN_BOOK, 3.0f);
  }
}
