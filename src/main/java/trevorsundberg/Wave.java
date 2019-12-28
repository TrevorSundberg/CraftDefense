package trevorsundberg;

import java.util.ArrayList;
import java.util.logging.Level;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Damageable;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.PigZombie;
import org.bukkit.entity.Wolf;
import org.bukkit.entity.EnderDragon.Phase;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

@SuppressWarnings("deprecation")
public class Wave {
  public static class Attack {
    public Material Weapon = null;
    public DamageCause Cause = null;
    public Enchantment Enchantment = null;
  }

  public static class EnemyWave {
    // on fire
    // status effects (potions)
    // drops (optional, otherwise uses default/scaled drop control)
    // custom names
    public EnemyType Type = EnemyType.ZOMBIE;
    public ArmorType Armor = ArmorType.NONE;
    public WeaponType Weapon = WeaponType.NONE;
    public EnemyType Vehicle = null;
    public double InitialAmount = 3;
    public double PerPlayer = 3;
    public double PerDifficulty = 3;
    public double DelaySeconds = 1.0;
    public double EnemiesPerSecond = 1.0;

    public double SpeedScale = 1.0;
    public double DamageScale = 1.0;
    public int HitPoints = 0;
    public boolean OnFire = false;

    public ArrayList<Attack> Immunities = new ArrayList<Attack>();
    public ArrayList<Attack> OnlyDamagedBy = new ArrayList<Attack>();

    public Entity spawnAttacker(Location l) {
      Entity mainEnemy = Wave.spawnEnemy(this.Type, l);
      Wave.setupWeapon(mainEnemy, this.Weapon);
      Wave.setupArmor(mainEnemy, this.Armor);

      if (this.OnFire) {
        mainEnemy.setFireTicks(100000);
      }

      if (mainEnemy instanceof LivingEntity) {
        LivingEntity c = (LivingEntity) mainEnemy;
        c.setRemoveWhenFarAway(false);
      }

      if (this.HitPoints > 0 && mainEnemy instanceof Damageable) {
        Damageable damagable = (Damageable) mainEnemy;
        damagable.setMaxHealth(this.HitPoints);
        damagable.setHealth(this.HitPoints);
      }

      if (this.Vehicle != null) {
        Entity vehicle = Wave.spawnEnemy(this.Vehicle, l);

        // A vehicle was specified, so attempt to set the passenger to the main enemy
        // spawned
        if (vehicle.setPassenger(mainEnemy) == false) {
          vehicle.getServer().getLogger().log(Level.WARNING,
              "Failed to use " + this.Vehicle + " as a vehicle for entity " + this.Type);
          vehicle.remove();
        }
      }

      return mainEnemy;
    }
  }

  public enum EnemyType {
    BAT, BLAZE, CAVE_SPIDER, CREEPER, ENDER_DRAGON, ENDERMAN, GHAST, GIANT, MAGMA_CUBE, PIG_ZOMBIE, SILVERFISH,
    SKELETON, SLIME, SPIDER, SUPER_CREEPER, WITCH, WITHER, WITHER_SKELETON, VILLAGER_ZOMBIE, WOLF, ZOMBIE,
  }

  public enum SpecialWaveType {
    ARROW, FIRE_ARROW, LIGHTNING, ANVIL,
  }

  public enum WeaponType {
    NONE, WOODEN_SWORD, STONE_SWORD, IRON_SWORD, GOLDEN_SWORD, DIAMOND_SWORD, BOW
  }

  public enum ArmorType {
    NONE, LEATHER, IRON, GOLD, DIAMOND
  }

  public static class SpecialWave {
    public SpecialWaveType Type;
    public double DelaySeconds = 1.0;
    public double DurationSeconds = 60.0;
    public double Intensity = 25.0;
    public double TargetPlayerProbability = 0.1;
  }

  public String Name = "Rest Wave";
  public String Message;
  public ArrayList<EnemyWave> EnemyWaves = new ArrayList<EnemyWave>();
  public ArrayList<SpecialWave> SpecialWaves = new ArrayList<SpecialWave>();
  public int AwardedEmeralds;
  public int AwardedXp;

  public Wave() {
  }

  public Wave(SpecialWaveType type, String name) {
    SpecialWave wave = new SpecialWave();
    wave.Type = type;
    this.Name = name;
    this.SpecialWaves.add(wave);
  }

  public Wave(String message) {
    this.Message = message;
  }

  public Wave(String name, EnemyType enemyType, double intensity, int emeralds, int xp) {
    this(name, enemyType, intensity, emeralds, xp, null);
  }

  public Wave(String name, EnemyType enemyType, double intensity, int emeralds, int xp, String message) {
    this.AwardedEmeralds = emeralds;
    this.AwardedXp = xp;
    this.Name = name;

    EnemyWave enemies = new EnemyWave();

    enemies.InitialAmount = intensity;
    enemies.PerPlayer = intensity + 2;
    enemies.PerDifficulty = intensity;
    enemies.SpeedScale = 1.0;

    enemies.Type = enemyType;

    this.EnemyWaves.add(enemies);
  }

  public boolean isRest() {
    return this.EnemyWaves.isEmpty() && this.SpecialWaves.isEmpty();
  }

  private static void setupWeapon(Entity entity, WeaponType weapon) {
    if (weapon == null || weapon == WeaponType.NONE) {
      return;
    }

    if (entity instanceof LivingEntity) {
      LivingEntity l = (LivingEntity) entity;
      EntityEquipment e = l.getEquipment();

      if (weapon == WeaponType.BOW) {
        e.setItemInMainHand(new ItemStack(Material.BOW));
      } else if (weapon == WeaponType.WOODEN_SWORD) {
        e.setItemInMainHand(new ItemStack(Material.WOODEN_SWORD));
      } else if (weapon == WeaponType.STONE_SWORD) {
        e.setItemInMainHand(new ItemStack(Material.STONE_SWORD));
      } else if (weapon == WeaponType.IRON_SWORD) {
        e.setItemInMainHand(new ItemStack(Material.IRON_SWORD));
      } else if (weapon == WeaponType.GOLDEN_SWORD) {
        e.setItemInMainHand(new ItemStack(Material.GOLDEN_SWORD));
      } else if (weapon == WeaponType.DIAMOND_SWORD) {
        e.setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));
      }
    }
  }

  private static void setupArmor(Entity entity, ArmorType armor) {
    if (armor == null || armor == ArmorType.NONE) {
      return;
    }

    if (entity instanceof LivingEntity) {
      LivingEntity l = (LivingEntity) entity;
      EntityEquipment e = l.getEquipment();

      Material head = null, legs = null, chest = null, boots = null;

      if (armor == ArmorType.LEATHER) {
        head = Material.LEATHER_HELMET;
        legs = Material.LEATHER_LEGGINGS;
        chest = Material.LEATHER_CHESTPLATE;
        boots = Material.LEATHER_BOOTS;
      } else if (armor == ArmorType.IRON) {
        head = Material.IRON_HELMET;
        legs = Material.IRON_LEGGINGS;
        chest = Material.IRON_CHESTPLATE;
        boots = Material.IRON_BOOTS;
      } else if (armor == ArmorType.GOLD) {
        head = Material.LEGACY_GOLD_HELMET;
        legs = Material.LEGACY_GOLD_LEGGINGS;
        chest = Material.LEGACY_GOLD_CHESTPLATE;
        boots = Material.LEGACY_GOLD_BOOTS;
      } else if (armor == ArmorType.DIAMOND) {
        head = Material.DIAMOND_HELMET;
        legs = Material.DIAMOND_LEGGINGS;
        chest = Material.DIAMOND_CHESTPLATE;
        boots = Material.DIAMOND_BOOTS;
      }

      e.setHelmet(new ItemStack(head));
      e.setLeggings(new ItemStack(legs));
      e.setChestplate(new ItemStack(chest));
      e.setBoots(new ItemStack(boots));
    }
  }

  private static Entity spawnEnemy(EnemyType enemy, Location l) {
    World w = l.getWorld();

    switch (enemy) {
    case BAT:
      return w.spawnEntity(l, EntityType.BAT);
    case BLAZE:
      return w.spawnEntity(l, EntityType.BLAZE);
    case CAVE_SPIDER:
      return w.spawnEntity(l, EntityType.CAVE_SPIDER);
    case CREEPER:
      return w.spawnEntity(l, EntityType.CREEPER);
    case ENDER_DRAGON: {
      EnderDragon dragon = (EnderDragon) w.spawnEntity(l, EntityType.ENDER_DRAGON);
      dragon.setAI(true);
      dragon.setPhase(Phase.CIRCLING);
      dragon.resetMaxHealth();
      return dragon;
    }
    case ENDERMAN:
      return w.spawnEntity(l, EntityType.ENDERMAN);
    case GHAST:
      return w.spawnEntity(l, EntityType.GHAST);
    case GIANT:
      return w.spawnEntity(l, EntityType.GIANT);
    case MAGMA_CUBE:
      return w.spawnEntity(l, EntityType.MAGMA_CUBE);
    case PIG_ZOMBIE: {
      PigZombie pigZombie = (PigZombie) w.spawnEntity(l, EntityType.PIG_ZOMBIE);
      pigZombie.setAngry(true);
      return pigZombie;
    }
    case SILVERFISH:
      return w.spawnEntity(l, EntityType.SILVERFISH);
    case SKELETON:
      return w.spawnEntity(l, EntityType.SKELETON);
    case SLIME:
      return w.spawnEntity(l, EntityType.SLIME);
    case SPIDER:
      return w.spawnEntity(l, EntityType.SPIDER);
    case SUPER_CREEPER: {
      Creeper creeper = (Creeper) w.spawnEntity(l, EntityType.CREEPER);
      creeper.setPowered(true);
      return creeper;
    }
    case WITCH:
      return w.spawnEntity(l, EntityType.WITCH);
    case WITHER:
      return w.spawnEntity(l, EntityType.WITHER);
    case WITHER_SKELETON:
      return w.spawnEntity(l, EntityType.WITHER_SKELETON);
    case VILLAGER_ZOMBIE:
      return w.spawnEntity(l, EntityType.ZOMBIE_VILLAGER);
    case WOLF: {
      Wolf wolf = (Wolf) w.spawnEntity(l, EntityType.WOLF);
      wolf.setAngry(true);
      return wolf;
    }
    case ZOMBIE:
      return w.spawnEntity(l, EntityType.ZOMBIE);
    }

    return null;
  }
}
