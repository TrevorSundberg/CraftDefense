package trevorsundberg;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

//import net.minecraft.server.v1_5_R3.EntityCreature;
//import net.minecraft.server.v1_5_R3.PathEntity;
//import net.minecraft.server.v1_5_R3.PathPoint;

import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
//import org.bukkit.craftbukkit.v1_5_R3.entity.CraftCreature;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Egg;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
import org.bukkit.util.Vector;

import trevorsundberg.Wave.Attack;

public class AttackerManager implements Listener {
  // How often we update the AIs (generally do not change this)
  // This also controls how often AIs attack and how often they update movement,
  // so again do not change this
  private final int AiUpdateTicks = 4;
  private final float AiDefaultSpeed = 0.18f;

  private final int ItemDropScale = 3;

  private final double WitherMaxDistanceToOrigin = 60.0;

  private final int StuckDestroyCount = 12;
  private final int StuckDestroyRepeatCount = 4;
  private final int StuckDestroyRadius = 2;
  private final double StuckDestroyDropChance = 0.5;

  // How close an enemy must be to damage its target
  private final double DamageTargetRadius = 1.2;
  private final double DamageTargetChance = 0.22;
  private Random Rand;

  private CraftDefense Plugin;

  private class Attacker {
    public Entity Entity;
    public LivingEntity Target;
    public Location StuckPos;
    public Wave.EnemyWave Wave;
    public int StuckCount;
  }

  private final HashMap<Entity, Attacker> TrackedAttackers = new HashMap<Entity, Attacker>();

  public ArrayList<LivingEntity> FixedTargets = new ArrayList<LivingEntity>();

  public void initialize(final CraftDefense plugin, Random rand) {
    this.Rand = rand;
    this.Plugin = plugin;

    final AttackerManager self = this;

    PluginManager manager = plugin.getServer().getPluginManager();
    manager.registerEvents(this, plugin);

    plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
      @Override
      public void run() {
        if (plugin.isEnabled() == false)
          return;
        self.update();
      }
    }, AiUpdateTicks, AiUpdateTicks);
  }

  public boolean isAttackMatch(Attack attack, ItemStack weaponStack, EntityDamageByEntityEvent event) {
    boolean weaponMatch = (attack.Weapon != null && weaponStack != null && attack.Weapon == weaponStack.getType());
    boolean causeMatch = (attack.Cause != null && attack.Cause == event.getCause());
    boolean enchantedMatch = (attack.Enchantment != null && weaponStack.containsEnchantment(attack.Enchantment));
    return weaponMatch || causeMatch || enchantedMatch;
  }

  // We don't allow friendly fire between enemies
  @EventHandler
  public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
    Entity damager = event.getDamager();
    Entity target = event.getEntity();
    if (this.isEnemy(damager)) {
      // If we're attacking another enemy entity
      if (this.isEnemy(target)) {
        // Don't allow friendly fire
        event.setCancelled(true);
        event.setDamage(-1);
      } else {
        // Otherwise, do boosted amounts of damage
        Attacker attacker = this.TrackedAttackers.get(damager);
        event.setDamage((int) Math.round(event.getDamage() * attacker.Wave.DamageScale));
      }
    } else {
      // Something that is NOT that attacker is damaging something else
      // Is the thing being damaged an enemy?
      Attacker targetedAttacker = this.TrackedAttackers.get(target);
      if (targetedAttacker != null) {
        ItemStack weaponStack = null;

        if (damager instanceof LivingEntity) {
          LivingEntity l = (LivingEntity) damager;
          weaponStack = l.getEquipment().getItemInMainHand();
        }

        if (damager instanceof ThrownPotion) {
          ThrownPotion p = (ThrownPotion) damager;
          weaponStack = p.getItem();
        }

        if (damager instanceof Snowball) {
          weaponStack = new ItemStack(Material.SNOWBALL);
          event.setDamage(Math.max(event.getDamage(), 1));
        }

        if (damager instanceof Egg) {
          weaponStack = new ItemStack(Material.EGG);
          event.setDamage(Math.max(event.getDamage(), 1));
        }

        boolean canDamage = true;

        ArrayList<Attack> immunities = targetedAttacker.Wave.Immunities;
        for (int i = 0; i < immunities.size(); ++i) {
          Attack attack = immunities.get(i);
          if (isAttackMatch(attack, weaponStack, event)) {
            canDamage = false;
            break;
          }
        }

        // If we still can be damaged after ignoring immunities...
        if (canDamage) {
          ArrayList<Attack> onlyDamagedBy = targetedAttacker.Wave.OnlyDamagedBy;
          if (onlyDamagedBy.size() != 0) {
            canDamage = false;

            for (int i = 0; i < onlyDamagedBy.size(); ++i) {
              Attack attack = onlyDamagedBy.get(i);
              if (isAttackMatch(attack, weaponStack, event)) {
                canDamage = true;
                break;
              }
            }
          }
        }

        // If we can't damage this entity based on immunities or restrictions, then
        // cancel the damage
        if (canDamage == false) {
          event.setCancelled(true);
          event.setDamage(-1);
          return;
        }
      }
    }
  }

  // Don't allow hostile entities to target each other
  @EventHandler
  public void onEntityTargetLivingEntity(EntityTargetLivingEntityEvent event) {
    if (this.isEnemy(event.getEntity())) {
      if (this.isEnemy(event.getTarget()))
        event.setCancelled(true);
    }
  }

  // Change the amount of loot dropped for attackers, and also make up new loot
  // for mobs that dont' drop
  @EventHandler
  public void onEntityDeath(EntityDeathEvent event) {
    Entity e = event.getEntity();

    if (!this.isEnemy(e))
      return;

    if (this.Rand.nextInt(3) == 0) {
      if (e.getType() == EntityType.BAT) {
        ItemStack s = null;
        switch (this.Rand.nextInt(2)) {
        case 0:
          s = new ItemStack(Material.REDSTONE, 1);
          break;
        case 1:
          s = new ItemStack(Material.STRING, 2);
          break;
        case 2:
          s = new ItemStack(Material.COAL, 2);
          break;
        }

        event.getDrops().add(s);
        event.setDroppedExp(4);
      }
    }

    // Gun powder is over-powered!
    boolean containsGunPowder = false;
    List<ItemStack> drops = event.getDrops();
    for (int i = 0; i < drops.size(); ++i) {
      ItemStack stack = drops.get(i);
      if (stack.getType() == Material.GUNPOWDER) {
        containsGunPowder = true;
        break;
      }
    }

    if (containsGunPowder == false) {
      // Drop more items
      List<ItemStack> dropsCopy = new ArrayList<ItemStack>(event.getDrops());
      for (int i = 1; i < ItemDropScale; ++i) {
        event.getDrops().addAll(dropsCopy);
      }
    }
  }

  public void killAllAttackers() {
    for (Attacker attacker : this.TrackedAttackers.values()) {
      final Entity e = attacker.Entity;

      if (!e.isDead()) {
        Location l = e.getLocation();
        World w = l.getWorld();
        w.playEffect(e.getLocation(), Effect.MOBSPAWNER_FLAMES, 0);
        w.playEffect(e.getLocation(), Effect.STEP_SOUND, 0);

        // Remove the entity by doing damage
        if (e instanceof LivingEntity) {
          LivingEntity livingEntity = (LivingEntity) e;
          livingEntity.damage(100000);

          // Make sure the entity is removed by 15 seconds
          // This is an important amount of time (it prevents the ender dragon from
          // dropping a portal!)
          // Do NOT change this time
          // It also makes sure all entities of all types are removed
          final long TimeUntilDeath = 20 * 15;

          final AttackerManager self = this;
          this.Plugin.getServer().getScheduler().runTaskLater(this.Plugin, new Runnable() {
            @Override
            public void run() {
              if (self.Plugin.isEnabled() == false)
                return;
              if (e.isValid())
                e.remove();
            }
          }, TimeUntilDeath);
        } else {
          e.remove();
        }
      }
    }

    this.TrackedAttackers.clear();
  }

  public boolean canEntityAttack(EntityType type) {
    switch (type) {
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

  private void update() {
    // Clear out dead fixed targets
    for (int i = 0; i < this.FixedTargets.size();) {
      Entity target = this.FixedTargets.get(i);
      if (target.isDead() || target.isValid() == false)
        this.FixedTargets.remove(i);
      else
        ++i;
    }

    for (Attacker attacker : this.TrackedAttackers.values()) {
      Entity e = attacker.Entity;
      if (e.isDead())
        continue;

      // Walk through all fixed targets, if we're next to one, then do damage!
      Location l = e.getLocation();
      for (int j = 0; j < this.FixedTargets.size(); ++j) {
        LivingEntity nextTo = this.FixedTargets.get(j);
        if (nextTo.getLocation().distance(l) <= DamageTargetRadius) {
          // Rough line of sight check (midpoint)
          Location midpoint = nextTo.getLocation().add(e.getLocation()).multiply(0.5);
          if (midpoint.getBlock().isEmpty()) {
            if (this.Rand.nextFloat() < DamageTargetChance) {
              // Do the minimum damage
              nextTo.damage(1, e);
            }
          }
        }
      }

      // If the target is dead or invalid...
      if (attacker.Target != null && (attacker.Target.isValid() == false || attacker.Target.isDead())) {
        // We need to retarget!
        attacker.Target = null;
      }

      // If there is no target (may have been invalidated above)
      if (attacker.Target == null) {
        // Randomly we target players (or if its the ender dragon, only let it target
        // players)
        if (this.Rand.nextInt(2) == 0 || e.getType() == EntityType.ENDER_DRAGON || this.FixedTargets.isEmpty()) {
          Player[] players = this.Plugin.getPlayers();
          if (players.length != 0) {
            int index = this.Rand.nextInt(players.length);
            attacker.Target = players[index];
          }
        } else {
          int index = this.Rand.nextInt(this.FixedTargets.size());
          attacker.Target = this.FixedTargets.get(index);
        }
      }

      if (attacker.Target != null) {
        Location targetPos = attacker.Target.getLocation();

        if (e instanceof Creature) {
          Creature creature = (Creature) e;

          if (creature.getType() == EntityType.WITHER) {
            // Quickly kill any animal targets
            if (creature.getTarget() instanceof Animals) {
              creature.getTarget().damage(1000, creature);
            }
          }

          creature.setTarget(attacker.Target);
        }

        // Really poor path finding, for now...
        if (true) {
          double moveSpeed = AiDefaultSpeed;
          int yOffset = 0;

          if (e.getType() == EntityType.BAT) {
            moveSpeed = 0.3;
            yOffset = 1;
          } else if (e.getType() == EntityType.GHAST) {
            moveSpeed = 0.24;
            yOffset = 12;
          } else if (e.getType() == EntityType.WITHER) {
            Location witherLocation = e.getLocation();
            double absX = Math.abs(witherLocation.getX());
            double absZ = Math.abs(witherLocation.getZ());
            if (absX > WitherMaxDistanceToOrigin || absZ > WitherMaxDistanceToOrigin) {
              e.teleport(new Location(e.getWorld(), 0, witherLocation.getY() + 5, 0));
            }

            moveSpeed = 0.5;
            yOffset = 15;
          } else if (e.getType() == EntityType.SLIME || e.getType() == EntityType.MAGMA_CUBE) {
            moveSpeed = 0.19;
          }

          moveSpeed *= attacker.Wave.SpeedScale;

          Vector vel = e.getVelocity();
          vel = vel.multiply(0.993);
          Location current = e.getLocation();
          Vector towardTarget = new Vector(targetPos.getX() - current.getX(),
              (targetPos.getY() - current.getY() + yOffset) * 0.2, targetPos.getZ() - current.getZ());
          towardTarget = towardTarget.normalize();
          towardTarget = towardTarget.multiply(moveSpeed);

          e.setVelocity(vel.add(towardTarget));
        }

        // If the entity can't normally attack on its own, then we need to deal fake
        // damage to the target
        if (canEntityAttack(e.getType()) == false && attacker.Wave.DamageScale >= 0) {
          if (l.distance(targetPos) <= DamageTargetRadius)
            attacker.Target.damage(Math.max(1, (int) attacker.Wave.DamageScale), e);
        }

        Location newPos = e.getLocation();

        if (attacker.StuckPos.distance(newPos) < StuckDestroyRadius) {
          ++attacker.StuckCount;

          if (attacker.StuckCount >= StuckDestroyCount) {
            attacker.StuckCount -= StuckDestroyRepeatCount;
            this.destroyRandomBlock(e.getLocation(), StuckDestroyRadius);
          }
        } else {
          attacker.StuckCount = 0;
          attacker.StuckPos = newPos;
        }
      }
    }
  }

  void addAttacker(Entity entity, Wave.EnemyWave wave) {
    Attacker attacker = new Attacker();
    attacker.Entity = entity;
    attacker.StuckPos = entity.getLocation();
    attacker.Wave = wave;
    this.TrackedAttackers.put(entity, attacker);
  }

  void destroyRandomBlock(Location l, int radius) {
    int x = l.getBlockX() + this.Rand.nextInt(radius * 2) - radius;
    int y = l.getBlockY() + this.Rand.nextInt(radius); // We only want it to
    // destroy mostly
    // upward (don't dig
    // holes!)
    int z = l.getBlockZ() + this.Rand.nextInt(radius * 2) - radius;

    Block b = l.getWorld().getBlockAt(x, y, z);

    Material m = b.getType();

    if (m != Material.BEDROCK && m != Material.AIR && m != Material.WATER && m != Material.ENCHANTING_TABLE) {
      double chance = 1.0;

      if (m == Material.NETHERRACK)
        chance = 0.98;
      if (m == Material.SAND)
        chance = 0.95;
      if (m == Material.GRAVEL)
        chance = 0.94;
      if (m == Material.DIRT)
        chance = 0.92;
      if (m == Material.GRASS)
        chance = 0.91;
      if (m == Material.COARSE_DIRT)
        chance = 0.90;
      if (m == Material.SOUL_SAND)
        chance = 0.88;
      if (m == Material.COBBLESTONE)
        chance = 0.71;
      if (m == Material.MOSSY_COBBLESTONE)
        chance = 0.70;
      if (m == Material.COBBLESTONE_WALL)
        chance = 0.68;
      if (m == Material.BRICK)
        chance = 0.66;
      if (m == Material.SANDSTONE)
        chance = 0.65;
      if (m == Material.NETHER_BRICKS)
        chance = 0.55;
      if (m == Material.STONE)
        chance = 0.52;
      if (m == Material.ANVIL)
        chance = 0.50;
      if (m == Material.COBWEB)
        chance = 0.45;
      if (m == Material.SMOOTH_STONE)
        chance = 0.40;
      if (m == Material.QUARTZ_BLOCK)
        chance = 0.33;
      if (m == Material.IRON_BARS)
        chance = 0.30;
      if (m == Material.BOOKSHELF)
        chance = 0.28;
      if (m == Material.OBSIDIAN)
        chance = 0.12;

      if (this.Rand.nextDouble() < chance) {
        Location blockPos = b.getLocation();

        if (this.Rand.nextDouble() < StuckDestroyDropChance)
          b.breakNaturally();
        else
          b.setType(Material.AIR);

        l.getWorld().playEffect(blockPos, Effect.SMOKE, 0);
        l.getWorld().playEffect(blockPos, Effect.BLAZE_SHOOT, 0);
      }
    }
  }

  public boolean isEnemy(Entity e) {
    return this.TrackedAttackers.containsKey(e);
  }
}
