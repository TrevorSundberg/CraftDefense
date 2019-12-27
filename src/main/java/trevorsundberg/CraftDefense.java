package trevorsundberg;

import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.Effect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Villager.Profession;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import trevorsundberg.Wave.ArmorType;
import trevorsundberg.Wave.SpecialWave;
import trevorsundberg.Wave.SpecialWaveType;
import trevorsundberg.Wave.WeaponType;

public class CraftDefense extends JavaPlugin implements Listener, DayTimeManager.DayChangedCallback {
  private World MainWorld;
  private boolean MainWorldInitializedOnFirstJoin = false;
  private int InitializeCount = 0; // Fully initialized when we hit 2 (both in onEnable, and on the player's first
                                   // join)
  private int SpawnY;
  private int LowestY;
  private Random Rand;
  private final ArrayList<Location> FireOustLocations = new ArrayList<Location>();
  private int FireOustIndex = 0;

  private final ArrayList<Wave> Waves = new ArrayList<Wave>();
  private final ArrayList<Villager> Villagers = new ArrayList<Villager>();
  private final HashSet<UUID> Veterans = new HashSet<UUID>();

  private DayTimeManager DayTimeManager;
  private final TeleporterManager TeleporterManager = new TeleporterManager();
  private final SavedInventoryManager SavedInventoryManager = new SavedInventoryManager();
  private final ExperienceGeneratorManager ExperienceGeneratorManager = new ExperienceGeneratorManager();
  private final AttackerManager AttackerManager = new AttackerManager();
  private final InfiniteBlockManager InfiniteTorchManager = new InfiniteBlockManager();

  // Fixed Constants
  private final int MinimumYForPlatform = 63; // Ocean level
  private final int SiegeTunnelHalfWidth = 4;
  private final int SiegeTunnelLength = 30;
  private final int SiegeTunnelInCut = 4;
  private final int VillagerPenRadius = 6;
  private final int VillagerHouseRadius = 4;
  private final int VillagerInnerWoodStart = 3;
  private final int VillagerBooks = 2;

  // Tweakable Constants
  private final int WarningTimerSeconds = 5;
  private int SpawnRadius = 25;
  private int RecommendedBuildRadius = 12;
  private final int SpawnExtraGoodMobTicks = 500;
  private final int GoodMobRadius = 96;
  private final double AttackTimeNormalized = 0.6;
  private int TicksPerDay = 460 * 20;

  public CraftDefense() {
    this.Waves.add(new Wave("Gather resources"));
    this.Waves.add(new Wave("Build walls, teleporters, and weaponry"));
    this.Waves.add(new Wave("Be vigilant. This is your last day"));

    // Add in wither skeletons, and other waves with armor/swords and
    // different intensities
    this.Waves.add(new Wave("Zombies", Wave.EnemyType.ZOMBIE, 3, 1, 18, "The Wither King mobilizes his ground forces"));
    this.Waves.add(new Wave("Silverfish", Wave.EnemyType.SILVERFISH, 4, 1, 18));
    this.Waves.add(new Wave("Skeletons", Wave.EnemyType.SKELETON, 4, 2, 20));
    this.Waves.add(new Wave("Spiders", Wave.EnemyType.SPIDER, 3, 3, 21));
    this.Waves.add(new Wave("Wolves", Wave.EnemyType.WOLF, 5, 3, 22));

    // Bat wave
    {
      Wave wave = new Wave();
      wave.AwardedEmeralds = 3;
      wave.AwardedXp = 24;
      wave.Name = "Bats";
      wave.Message = "A tiny fluttering sound...";

      // Bats
      {
        Wave.EnemyWave enemyWave = new Wave.EnemyWave();
        enemyWave.InitialAmount = 4;
        enemyWave.PerDifficulty = 3;
        enemyWave.PerPlayer = 4;

        enemyWave.Type = Wave.EnemyType.BAT;
        enemyWave.DelaySeconds = 1;
        enemyWave.EnemiesPerSecond = 0.9;
        wave.EnemyWaves.add(enemyWave);
      }
      // Lightning
      {
        SpecialWave specialWave = new SpecialWave();
        specialWave.Type = Wave.SpecialWaveType.LIGHTNING;
        specialWave.DelaySeconds = 26;
        specialWave.DurationSeconds = 30;
        wave.SpecialWaves.add(specialWave);
      }

      this.Waves.add(wave);
    }

    this.Waves.add(new Wave("Villager Zombies", Wave.EnemyType.VILLAGER_ZOMBIE, 6, 1, 18));
    {
      Wave wave = new Wave();
      wave.AwardedEmeralds = 5;
      wave.AwardedXp = 80;
      wave.Name = "The King's Right Hand";
      wave.Message = "The ground trembles...";

      // Zombies
      {
        Wave.EnemyWave enemyWave = new Wave.EnemyWave();
        enemyWave.InitialAmount = 2;
        enemyWave.PerDifficulty = 3;
        enemyWave.PerPlayer = 3;
        enemyWave.Type = Wave.EnemyType.ZOMBIE;
        enemyWave.DelaySeconds = 0;
        enemyWave.EnemiesPerSecond = 2;
        wave.EnemyWaves.add(enemyWave);
      }
      // Skeleton jockeys
      {
        Wave.EnemyWave enemyWave = new Wave.EnemyWave();
        enemyWave.InitialAmount = 2;
        enemyWave.PerDifficulty = 1;
        enemyWave.PerPlayer = 1;
        enemyWave.Type = Wave.EnemyType.SKELETON;
        enemyWave.Vehicle = Wave.EnemyType.SPIDER;
        enemyWave.DelaySeconds = 1;
        enemyWave.EnemiesPerSecond = 1;
        wave.EnemyWaves.add(enemyWave);
      }
      // Skeletons (bows)
      {
        Wave.EnemyWave enemyWave = new Wave.EnemyWave();
        enemyWave.InitialAmount = 2;
        enemyWave.PerDifficulty = 1;
        enemyWave.PerPlayer = 2;
        enemyWave.Type = Wave.EnemyType.SKELETON;
        enemyWave.DelaySeconds = 15;
        enemyWave.EnemiesPerSecond = 2;
        enemyWave.Weapon = WeaponType.BOW;
        enemyWave.Armor = ArmorType.LEATHER;
        wave.EnemyWaves.add(enemyWave);
      }
      // Giant
      {
        Wave.EnemyWave enemyWave = new Wave.EnemyWave();
        enemyWave.InitialAmount = 1;
        enemyWave.PerDifficulty = 0.2;
        enemyWave.PerPlayer = 0.1;
        enemyWave.Type = Wave.EnemyType.GIANT;
        enemyWave.DelaySeconds = 5;
        wave.EnemyWaves.add(enemyWave);
      }

      this.Waves.add(wave);
    }
    this.Waves.add(new Wave("The attackers need a day to regroup their forces!"));

    this.Waves.add(new Wave("Witches", Wave.EnemyType.WITCH, 3, 3, 32, "The Wither King sends mystical beings"));
    this.Waves.add(new Wave("Creepers", Wave.EnemyType.CREEPER, 3, 3, 32, "In the name of the King..."));
    {
      Wave wave = new Wave();
      wave.AwardedEmeralds = 3;
      wave.AwardedXp = 46;
      wave.Name = "Ghasts And Pigmen";

      // Ghasts
      {
        Wave.EnemyWave enemyWave = new Wave.EnemyWave();
        enemyWave.InitialAmount = 2;
        enemyWave.PerDifficulty = 3;
        enemyWave.PerPlayer = 3;
        enemyWave.Type = Wave.EnemyType.GHAST;
        enemyWave.DelaySeconds = 0;
        enemyWave.EnemiesPerSecond = 2;
        wave.EnemyWaves.add(enemyWave);
      }
      // Pigmen
      {
        Wave.EnemyWave enemyWave = new Wave.EnemyWave();
        enemyWave.InitialAmount = 5;
        enemyWave.PerDifficulty = 5;
        enemyWave.PerPlayer = 5;
        enemyWave.Type = Wave.EnemyType.PIG_ZOMBIE;
        enemyWave.DelaySeconds = 5;
        enemyWave.EnemiesPerSecond = 0.7;
        enemyWave.Weapon = WeaponType.STONE_SWORD;
        wave.EnemyWaves.add(enemyWave);
      }
      this.Waves.add(wave);
    }

    this.Waves.add(new Wave("Endermen", Wave.EnemyType.ENDERMAN, 3, 4, 40));
    this.Waves.add(new Wave("Slimes", Wave.EnemyType.SLIME, 6, 4, 48));
    this.Waves.add(new Wave("Cave Spiders", Wave.EnemyType.CAVE_SPIDER, 5, 4, 50));
    {
      Wave wave = new Wave();
      wave.AwardedEmeralds = 8;
      wave.AwardedXp = 120;
      wave.Name = "The King's Pets";
      wave.Message = "A massive fluttering sound...";

      // Bats
      {
        Wave.EnemyWave enemyWave = new Wave.EnemyWave();
        enemyWave.InitialAmount = 1.2;
        enemyWave.PerDifficulty = 0.1;
        enemyWave.PerPlayer = 0.2;
        enemyWave.Type = Wave.EnemyType.BAT;
        enemyWave.DelaySeconds = 0;
        enemyWave.EnemiesPerSecond = 2;
        wave.EnemyWaves.add(enemyWave);
      }
      // Endermen
      {
        Wave.EnemyWave enemyWave = new Wave.EnemyWave();
        enemyWave.InitialAmount = 2;
        enemyWave.PerDifficulty = 1;
        enemyWave.PerPlayer = 1;
        enemyWave.Type = Wave.EnemyType.ENDERMAN;
        enemyWave.DelaySeconds = 3;
        enemyWave.EnemiesPerSecond = 1;
        wave.EnemyWaves.add(enemyWave);
      }
      // Ender Dragon
      {
        Wave.EnemyWave enemyWave = new Wave.EnemyWave();
        enemyWave.InitialAmount = 1;
        enemyWave.PerDifficulty = 0.2;
        enemyWave.PerPlayer = 0.1;
        enemyWave.Type = Wave.EnemyType.ENDER_DRAGON;
        enemyWave.DelaySeconds = 5;
        wave.EnemyWaves.add(enemyWave);
      }

      this.Waves.add(wave);
    }
    this.Waves.add(new Wave("The Wither King rethinks his strategy..."));
    this.Waves.add(new Wave("The attackers need another day to regroup their forces!"));

    this.Waves.add(new Wave("Magma Cubes", Wave.EnemyType.MAGMA_CUBE, 5, 4, 52));
    this.Waves.add(new Wave("Super Creepers", Wave.EnemyType.SUPER_CREEPER, 3, 7, 60, "IN THE NAME OF THE KING!"));
    this.Waves.add(new Wave("Blazes", Wave.EnemyType.BLAZE, 6, 8, 26));
    {
      Wave wave = new Wave();
      wave.AwardedEmeralds = 8;
      wave.AwardedXp = 120;
      wave.Name = "Wither Skeletons";
      wave.Message = "A fight in the shade...";

      // Small arrow barrage
      {
        Wave.SpecialWave specialWave = new Wave.SpecialWave();
        specialWave.Type = SpecialWaveType.ARROW;
        specialWave.DurationSeconds = 6;
        specialWave.Intensity = 10;
        wave.SpecialWaves.add(specialWave);
      }
      // Arrow barrage
      {
        Wave.SpecialWave specialWave = new Wave.SpecialWave();
        specialWave.Type = SpecialWaveType.ARROW;
        specialWave.DurationSeconds = 14;
        specialWave.DelaySeconds = 8;
        specialWave.Intensity = 2000;
        wave.SpecialWaves.add(specialWave);
      }
      // Wither Skeletons (swords)
      {
        Wave.EnemyWave enemyWave = new Wave.EnemyWave();
        enemyWave.InitialAmount = 9;
        enemyWave.PerDifficulty = 2;
        enemyWave.PerPlayer = 5;
        enemyWave.Type = Wave.EnemyType.WITHER_SKELETON;
        enemyWave.DelaySeconds = 23;
        enemyWave.EnemiesPerSecond = 1;
        enemyWave.Weapon = WeaponType.DIAMOND_SWORD;
        wave.EnemyWaves.add(enemyWave);
      }
      // Wither Skeletons (bows)
      {
        Wave.EnemyWave enemyWave = new Wave.EnemyWave();
        enemyWave.InitialAmount = 5;
        enemyWave.PerDifficulty = 1;
        enemyWave.PerPlayer = 2;
        enemyWave.Type = Wave.EnemyType.WITHER_SKELETON;
        enemyWave.DelaySeconds = 32;
        enemyWave.EnemiesPerSecond = 1;
        enemyWave.Weapon = WeaponType.BOW;
        enemyWave.Armor = ArmorType.IRON;
        wave.EnemyWaves.add(enemyWave);
      }

      this.Waves.add(wave);
    }

    {
      Wave wave = new Wave();
      wave.AwardedEmeralds = 6;
      wave.AwardedXp = 60;
      wave.Name = "ACME";

      // Cave-spider
      {
        Wave.EnemyWave enemyWave = new Wave.EnemyWave();
        enemyWave.InitialAmount = 5;
        enemyWave.PerDifficulty = 3;
        enemyWave.PerPlayer = 5;
        enemyWave.Type = Wave.EnemyType.CAVE_SPIDER;
        enemyWave.DelaySeconds = 0;
        enemyWave.EnemiesPerSecond = 0.6;
        wave.EnemyWaves.add(enemyWave);
      }
      // Anviles
      {
        Wave.SpecialWave specialWave = new Wave.SpecialWave();
        specialWave.Type = SpecialWaveType.ANVIL;
        specialWave.DurationSeconds = 40;
        specialWave.DelaySeconds = 5;
        specialWave.Intensity = 300;
        wave.SpecialWaves.add(specialWave);
      }
      // Cave-spider (rush)
      {
        Wave.EnemyWave enemyWave = new Wave.EnemyWave();
        enemyWave.InitialAmount = 5;
        enemyWave.PerDifficulty = 3;
        enemyWave.PerPlayer = 5;
        enemyWave.Type = Wave.EnemyType.CAVE_SPIDER;
        enemyWave.DelaySeconds = 16;
        enemyWave.EnemiesPerSecond = 5;
        wave.EnemyWaves.add(enemyWave);
      }
      this.Waves.add(wave);
    }

    {
      Wave wave = new Wave();
      wave.AwardedEmeralds = 100;
      wave.AwardedXp = 1000;
      wave.Name = "The Wither King";
      wave.Message = "All hail";

      // Zombies
      {
        Wave.EnemyWave enemyWave = new Wave.EnemyWave();
        enemyWave.InitialAmount = 2;
        enemyWave.PerDifficulty = 3;
        enemyWave.PerPlayer = 7;
        enemyWave.Type = Wave.EnemyType.ZOMBIE;
        enemyWave.DelaySeconds = 0;
        enemyWave.EnemiesPerSecond = 2;
        wave.EnemyWaves.add(enemyWave);
      }
      // Skeletons
      {
        Wave.EnemyWave enemyWave = new Wave.EnemyWave();
        enemyWave.InitialAmount = 2;
        enemyWave.PerDifficulty = 1;
        enemyWave.PerPlayer = 2;
        enemyWave.Type = Wave.EnemyType.SKELETON;
        enemyWave.DelaySeconds = 6;
        enemyWave.EnemiesPerSecond = 1;
        wave.EnemyWaves.add(enemyWave);
      }
      // Wither Skeletons
      {
        Wave.EnemyWave enemyWave = new Wave.EnemyWave();
        enemyWave.InitialAmount = 3;
        enemyWave.PerDifficulty = 1.5;
        enemyWave.PerPlayer = 2;
        enemyWave.Type = Wave.EnemyType.WITHER_SKELETON;
        enemyWave.DelaySeconds = 14;
        enemyWave.EnemiesPerSecond = 1.5;
        enemyWave.Armor = ArmorType.GOLD;
        wave.EnemyWaves.add(enemyWave);
      }
      // Wither
      {
        Wave.EnemyWave enemyWave = new Wave.EnemyWave();
        enemyWave.InitialAmount = 1;
        enemyWave.PerDifficulty = 0;
        enemyWave.PerPlayer = 0;
        enemyWave.Type = Wave.EnemyType.WITHER;
        enemyWave.DelaySeconds = 20;
        wave.EnemyWaves.add(enemyWave);
      }
      // Wither Skeletons (phase 2)
      {
        Wave.EnemyWave enemyWave = new Wave.EnemyWave();
        enemyWave.InitialAmount = 4;
        enemyWave.PerDifficulty = 1.5;
        enemyWave.PerPlayer = 5;
        enemyWave.Type = Wave.EnemyType.WITHER_SKELETON;
        enemyWave.DelaySeconds = 30;
        enemyWave.EnemiesPerSecond = 1.5;
        enemyWave.Weapon = WeaponType.DIAMOND_SWORD;
        enemyWave.Armor = ArmorType.DIAMOND;
        wave.EnemyWaves.add(enemyWave);
      }
      // Zombie Pigmen
      {
        Wave.EnemyWave enemyWave = new Wave.EnemyWave();
        enemyWave.InitialAmount = 4;
        enemyWave.PerDifficulty = 2;
        enemyWave.PerPlayer = 2;
        enemyWave.Type = Wave.EnemyType.PIG_ZOMBIE;
        enemyWave.DelaySeconds = 55;
        enemyWave.EnemiesPerSecond = 0.5;
        enemyWave.Weapon = WeaponType.DIAMOND_SWORD;
        enemyWave.Armor = ArmorType.DIAMOND;
        wave.EnemyWaves.add(enemyWave);
      }

      this.Waves.add(wave);
    }
  }

  public Player[] getPlayers() {
    Player[] players = new Player[this.getServer().getOnlinePlayers().size()];
    return this.getServer().getOnlinePlayers().toArray(players);
  }

  public static void copyFolder(File src, File dest) {
    CraftDefense.copyFolder(Path.of(src.getAbsolutePath()), Path.of(dest.getAbsolutePath()));
  }

  public static void copyFolder(Path src, Path dest) {
    try {
      Files.walk(src).forEach(s -> {
        try {
          Path d = dest.resolve(src.relativize(s));
          if (Files.isDirectory(s)) {
            if (!Files.exists(d))
              Files.createDirectory(d);
            return;
          }
          Files.copy(s, d);// use flag to override existing
        } catch (Exception e) {
          e.printStackTrace();
        }
      });
    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }

  public static int getInt(JSONObject obj, String member, int defaultValue) {
    Object value = obj.get(member);
    if (value instanceof Double)
      return (int) value;
    if (value instanceof Integer)
      return (int) value;
    return defaultValue;
  }

  public static boolean getBoolean(JSONObject obj, String member, boolean defaultValue) {
    Object value = obj.get(member);
    if (value instanceof Boolean)
      return (boolean) value;
    return defaultValue;
  }

  public static double getDouble(JSONObject obj, String member, double defaultValue) {
    Object value = obj.get(member);
    if (value instanceof Double)
      return (double) value;
    if (value instanceof Integer)
      return (double) value;
    return defaultValue;
  }

  public static String getString(JSONObject obj, String member, String defaultValue) {
    Object value = obj.get(member);
    if (value instanceof String)
      return (String) value;
    return defaultValue;
  }

  public static JSONArray getArray(JSONObject obj, String member) {
    Object value = obj.get(member);
    if (value instanceof JSONArray)
      return (JSONArray) value;
    return new JSONArray();
  }

  @SuppressWarnings("unchecked")
  public static <T extends Enum<T>> T getEnum(JSONObject obj, String member, T defaultValue) {
    Object value = obj.get(member);
    if (value instanceof String)
      return (T) T.valueOf(defaultValue.getClass(), (String) value);
    return defaultValue;
  }

  public Wave.Attack readAttack(JSONObject jAttack) {
    Wave.Attack attack = new Wave.Attack();
    attack.Weapon = CraftDefense.<Material>getEnum(jAttack, "Weapon", null);
    attack.Cause = CraftDefense.<DamageCause>getEnum(jAttack, "Cause", null);

    String enchantment = getString(jAttack, "Enchantment", null);
    if (enchantment != null)
      attack.Enchantment = Enchantment.getByName(enchantment);
    return attack;
  }

  public void loadConfig() {
    try {
      // Grab the data folder directory, and make sure it exists
      File dataFolder = this.getDataFolder();
      dataFolder.mkdirs();

      File configPath = new File(dataFolder, "config.json");

      FileReader reader = new FileReader(configPath.getAbsolutePath());
      JSONParser parser = new JSONParser();
      JSONObject jConfig = (JSONObject) parser.parse(reader);

      this.TicksPerDay = Utilities.TicksPerSecond * (int) jConfig.get("SecondsPerDay");

      this.SpawnRadius = (int) jConfig.get("SpawnRadius");
      this.RecommendedBuildRadius = (int) jConfig.get("RecommendedBuildRadius");

      JSONArray waves = (JSONArray) jConfig.get("Waves");

      if (waves != null) {
        this.Waves.clear();

        for (int i = 0; i < waves.size(); ++i) {
          Wave wave = new Wave();
          this.Waves.add(wave);

          Object jWaveUnknown = waves.get(i);

          if (jWaveUnknown instanceof String) {
            wave.Message = (String) jWaveUnknown;
          } else if (jWaveUnknown instanceof JSONArray) {
            // "Zombies", Wave.EnemyType.ZOMBIE, 3, 1, 18, "The Wither King mobilizes his
            // ground forces"
            JSONArray jSimpleWave = (JSONArray) jWaveUnknown;

            Wave.EnemyWave enemies = new Wave.EnemyWave();
            wave.EnemyWaves.add(enemies);

            wave.Name = (String) jSimpleWave.get(0);
            enemies.Type = (Wave.EnemyType) jSimpleWave.get(1);

            int intensity = (int) jSimpleWave.get(2);
            enemies.InitialAmount = intensity;
            enemies.PerPlayer = intensity + 2;
            enemies.PerDifficulty = intensity;

            wave.AwardedEmeralds = (int) jSimpleWave.get(3);
            wave.AwardedXp = (int) jSimpleWave.get(4);

            if (jSimpleWave.size() > 5)
              wave.Message = (String) jSimpleWave.get(5);
          } else {
            JSONObject jWave = (JSONObject) waves.get(i);

            wave.Name = getString(jWave, "Name", "Rest Wave");
            wave.Message = getString(jWave, "Message", "");
            wave.AwardedEmeralds = getInt(jWave, "AwardedEmeralds", 0);
            wave.AwardedXp = getInt(jWave, "AwardedXp", 0);

            JSONArray jEnemyWaves = getArray(jWave, "EnemyWaves");
            for (int j = 0; j < jEnemyWaves.size(); ++j) {
              JSONObject jEnemyWave = (JSONObject) jEnemyWaves.get(j);

              Wave.EnemyWave enemyWave = new Wave.EnemyWave();
              wave.EnemyWaves.add(enemyWave);

              enemyWave.Type = getEnum(jEnemyWave, "Type", Wave.EnemyType.ZOMBIE);
              enemyWave.Armor = getEnum(jEnemyWave, "Armor", Wave.ArmorType.NONE);
              enemyWave.Weapon = getEnum(jEnemyWave, "Weapon", Wave.WeaponType.NONE);
              enemyWave.Vehicle = CraftDefense.<Wave.EnemyType>getEnum(jEnemyWave, "Vehicle", null);

              enemyWave.InitialAmount = getDouble(jEnemyWave, "InitialAmount", 3);
              enemyWave.PerPlayer = getDouble(jEnemyWave, "PerPlayer", 3);
              enemyWave.PerDifficulty = getDouble(jEnemyWave, "PerDifficulty", 3);

              enemyWave.DelaySeconds = getDouble(jEnemyWave, "DelaySeconds", 1.0);
              enemyWave.EnemiesPerSecond = getDouble(jEnemyWave, "EnemiesPerSecond", 1.0);

              enemyWave.SpeedScale = getDouble(jEnemyWave, "SpeedScale", 1.0);
              enemyWave.DamageScale = getDouble(jEnemyWave, "DamageScale", 1.0);
              enemyWave.HitPoints = getInt(jEnemyWave, "HitPoints", 0);
              enemyWave.OnFire = getBoolean(jEnemyWave, "OnFire", false);

              JSONArray jImmunities = getArray(jEnemyWave, "Immunities");
              for (int k = 0; k < jImmunities.size(); ++k) {
                JSONObject jAttack = (JSONObject) jImmunities.get(k);
                enemyWave.Immunities.add(readAttack(jAttack));
              }

              JSONArray jOnlyDamagedBy = getArray(jEnemyWave, "OnlyDamagedBy");
              for (int k = 0; k < jOnlyDamagedBy.size(); ++k) {
                JSONObject jAttack = (JSONObject) jOnlyDamagedBy.get(k);
                enemyWave.OnlyDamagedBy.add(readAttack(jAttack));
              }
            }

            JSONArray jSpecialWaves = getArray(jWave, "SpecialWaves");
            for (int j = 0; j < jSpecialWaves.size(); ++j) {
              JSONObject jSpecialWave = (JSONObject) jEnemyWaves.get(j);

              Wave.SpecialWave specialWave = new Wave.SpecialWave();

              specialWave.Type = getEnum(jSpecialWave, "Type", Wave.SpecialWaveType.LIGHTNING);

              specialWave.DelaySeconds = getDouble(jSpecialWave, "DelaySeconds", 1.0);
              specialWave.DurationSeconds = getDouble(jSpecialWave, "DurationSeconds", 60.0);
              specialWave.Intensity = getDouble(jSpecialWave, "Intensity", 25.0);
              specialWave.TargetPlayerProbability = getDouble(jSpecialWave, "TargetPlayerProbability", 0.1);

              wave.SpecialWaves.add(specialWave);
            }
          }
        }
      }

    } catch (Exception e) {
      this.getLogger().log(Level.WARNING, "Config was unable to load: " + e.getMessage());
    }
  }

  @Override
  public void onEnable() {
    final CraftDefense self = this;

    this.loadConfig();

    // Grab the main world where the entire siege will occur
    this.MainWorld = getServer().getWorlds().get(0);
    this.MainWorld.setSpawnLocation(0, 0, 0);

    this.DayTimeManager = new DayTimeManager(this, this.MainWorld, this.TicksPerDay);
    this.DayTimeManager.OnDayChanged = this;

    // Setup the callback for when it's night time (attacking time!)
    this.DayTimeManager.DayEventCallbacks.add(DayTimeManager.new DayEventCallback() {
      {
        TimeOfDayNormalized = AttackTimeNormalized;
      }

      @Override
      void onDayEvent(int day) {
        self.spawnWave(day);
      }
    });

    // Various warning timers before the wave starts
    this.addWarningTimer(120, "*** Next wave in 2 minutes ***", 1.0f);
    this.addWarningTimer(60, "*** Next wave in 1 minute ***", 1.1f);
    this.addWarningTimer(30, "*** Next wave in 30 seconds ***", 1.2f);
    this.addWarningTimer(10, "*** Next wave in 10 seconds ***", 1.3f);

    // Setup the warning timer
    for (int i = 1; i < WarningTimerSeconds; ++i) {
      this.addWarningTimer(i + 1, "*** Next wave in " + (i + 1) + " ***", 1.4f);
    }

    this.addWarningTimer(1, "*** Next wave in 1 seconds ***", 2.0f);

    // Create and seed our random number generator based on the world
    // (consistency at initialization!)
    this.Rand = new Random(this.MainWorld.getSeed());

    PluginManager manager = this.getServer().getPluginManager();
    manager.registerEvents(this, this);
    manager.registerEvents(this.SavedInventoryManager, this);
    manager.registerEvents(this.TeleporterManager, this);
    this.AttackerManager.initialize(this, this.Rand);
    this.ExperienceGeneratorManager.initialize(this);
    HashSet<Material> infiniteMaterials = new HashSet<Material>();
    infiniteMaterials.add(Material.TORCH);
    this.InfiniteTorchManager.initialize(this, infiniteMaterials);

    this.getServer().getScheduler().runTaskTimer(this, new Runnable() {
      @Override
      public void run() {
        if (self.isEnabled() == false)
          return;
        self.spawnAmbientMob();
      }
    }, SpawnExtraGoodMobTicks, SpawnExtraGoodMobTicks);

    // Make the villager go no where, every frame.
    this.getServer().getScheduler().runTaskTimer(this, new Runnable() {
      @Override
      public void run() {
        if (self.isEnabled() == false)
          return;
        self.villagerImmobilization();
        self.disableWeather();
        self.putOutFires();
        self.burnNonAttackersOnSiegePath();
      }
    }, 1, 1);

    this.initializeMainWorld();
  }

  @SuppressWarnings("deprecation")
  public void initializeMainWorld() {
    this.getLogger().log(Level.INFO, "Initializing main CraftDefense world... (pass " + this.InitializeCount + ")");
    this.LowestY = 256;

    for (int x = -SpawnRadius; x <= SpawnRadius; ++x) {
      for (int z = -SpawnRadius; z <= SpawnRadius; ++z) {
        int y = this.MainWorld.getHighestBlockYAt(x, z);

        if (y < this.LowestY && y >= MinimumYForPlatform) {
          this.LowestY = y;
        }
      }
    }

    if (this.FireOustLocations.isEmpty()) {
      int highestY = Math.min(this.LowestY + 64, 256);
      for (int x = -SpawnRadius; x <= SpawnRadius; ++x) {
        for (int z = -SpawnRadius; z <= SpawnRadius; ++z) {
          for (int y = this.LowestY - 1; y < highestY; ++y) {
            this.FireOustLocations.add(new Location(this.MainWorld, x, y, z));
          }
        }
      }

      Collections.shuffle(this.FireOustLocations, this.Rand);
    }

    this.SpawnY = this.LowestY + 3;
    this.MainWorld.setSpawnLocation(0, this.SpawnY, 0);

    // Clear out all water and lava above a certain point for quite a distance out
    // This helps to prevent exploits with water and lava
    int noWaterLavaRadius = SpawnRadius + 128;
    for (int x = -noWaterLavaRadius; x <= noWaterLavaRadius; ++x) {
      for (int z = -noWaterLavaRadius; z <= noWaterLavaRadius; ++z) {
        // Note: We don't clear out the layer where the grass and clay is, because we
        // already have a wall of adminium around
        // the entire spawn area - This also means that we won't screw up ocean biomes
        // (ideally)
        for (int y = this.LowestY; y < 255; ++y) {
          Block block = this.MainWorld.getBlockAt(x, y, z);
          Material mat = block.getType();
          if (mat == Material.WATER || mat == Material.LEGACY_STATIONARY_WATER || mat == Material.LAVA
              || mat == Material.LEGACY_STATIONARY_LAVA) {
            this.MainWorld.getBlockAt(x, y, z).setType(Material.AIR);
          }
        }
      }
    }

    for (int x = -SpawnRadius; x <= SpawnRadius; ++x) {
      for (int z = -SpawnRadius; z <= SpawnRadius; ++z) {
        for (int y = this.LowestY; y < 255; ++y) {
          this.MainWorld.getBlockAt(x, y, z).setType(Material.AIR);
        }

        if (Math.abs(x) <= RecommendedBuildRadius && Math.abs(z) <= RecommendedBuildRadius) {
          this.MainWorld.getBlockAt(x, this.LowestY - 1, z).setType(Material.CLAY);
        } else {
          this.MainWorld.getBlockAt(x, this.LowestY - 1, z).setType(Material.GRASS);
        }

        // If we're at the outer perimeter, then spawn bedrock
        // This is to prevent exploits such as letting water flow in
        if (Math.abs(x) == SpawnRadius || Math.abs(z) == SpawnRadius) {
          this.MainWorld.getBlockAt(x, this.LowestY - 1, z).setType(Material.BEDROCK);
        }

        for (int i = 2; i <= 4; ++i) {
          this.MainWorld.getBlockAt(x, this.LowestY - i, z).setType(Material.BEDROCK);
        }
      }
    }

    // Clear the siege tunnel path
    for (int x = SpawnRadius - SiegeTunnelInCut; x <= SpawnRadius + SiegeTunnelLength; ++x) {
      for (int z = -SiegeTunnelHalfWidth; z <= SiegeTunnelHalfWidth; ++z) {
        for (int y = this.LowestY; y < 255; ++y) {
          this.MainWorld.getBlockAt(x, y, z).setType(Material.AIR);
        }
      }
    }

    // Generate the siege tunnel path
    this.generateSiegeTunnel();

    // Go through all entities and kill anything in the spawn area
    List<Entity> entities = this.MainWorld.getEntities();
    for (int i = 0; i < entities.size(); ++i) {
      Entity e = entities.get(i);

      if (e.getType() != EntityType.PLAYER) {
        e.remove();
      }
    }

    // First we build the fence
    for (int x = -VillagerPenRadius; x <= VillagerPenRadius; ++x) {
      for (int z = -VillagerPenRadius; z <= VillagerPenRadius; ++z) {
        if (x == VillagerPenRadius || x == -VillagerPenRadius || z == VillagerPenRadius || z == -VillagerPenRadius) {
          if (x == 0 || z == 0) {
            Block fence = this.MainWorld.getBlockAt(x, this.LowestY + 0, z);
            fence.setType(Material.LEGACY_FENCE_GATE);

            if (z == 0) {
              // fence.setData((byte) 1);
            }
          } else {
            this.MainWorld.getBlockAt(x, this.LowestY + 0, z).setType(Material.LEGACY_FENCE);
            this.MainWorld.getBlockAt(x, this.LowestY + 1, z).setType(Material.LEGACY_FENCE);
          }
        }
      }
    }

    for (int x = -VillagerHouseRadius; x <= VillagerHouseRadius; ++x) {
      for (int z = -VillagerHouseRadius; z <= VillagerHouseRadius; ++z) {
        if (Math.abs(x) == VillagerHouseRadius || Math.abs(z) == VillagerHouseRadius) {
          if (x != 0 && z != 0) {
            this.MainWorld.getBlockAt(x, this.LowestY + 0, z).setType(Material.LEGACY_WOOD);
          }

          if (Math.abs(x) == 1 || Math.abs(z) == 1) {
            this.MainWorld.getBlockAt(x, this.LowestY + 1, z).setType(Material.GLASS);
          }

          this.MainWorld.getBlockAt(x, this.LowestY + 2, z).setType(Material.LEGACY_WOOD);
        }

        this.MainWorld.getBlockAt(x, this.LowestY + 3, z).setType(Material.LEGACY_WOOD);
      }
    }

    // Make the first layer of closing bookshelves
    for (int x = -VillagerInnerWoodStart; x <= VillagerInnerWoodStart; ++x) {
      for (int z = -VillagerInnerWoodStart; z <= VillagerInnerWoodStart; ++z) {
        if (Math.abs(x) == VillagerInnerWoodStart || Math.abs(z) == VillagerInnerWoodStart) {
          if (Math.abs(x) == 1 || Math.abs(z) == 1) {
            this.MainWorld.getBlockAt(x, this.LowestY + 0, z).setType(Material.LEGACY_WOOD);
            this.MainWorld.getBlockAt(x, this.LowestY + 1, z).setType(Material.LEGACY_WOOD);
            this.MainWorld.getBlockAt(x, this.LowestY + 2, z).setType(Material.LEGACY_WOOD);
          }
        }
      }
    }

    for (int x = -VillagerBooks; x <= VillagerBooks; ++x) {
      for (int z = -VillagerBooks; z <= VillagerBooks; ++z) {
        if (Math.abs(x) == VillagerBooks || Math.abs(z) == VillagerBooks) {
          if (x != 0 && z != 0) {
            this.MainWorld.getBlockAt(x, this.LowestY + 0, z).setType(Material.BOOKSHELF);
            this.MainWorld.getBlockAt(x, this.LowestY + 1, z).setType(Material.BOOKSHELF);
            this.MainWorld.getBlockAt(x, this.LowestY + 2, z).setType(Material.BOOKSHELF);
          }
        }
      }
    }

    this.MainWorld.getBlockAt(0, this.LowestY + 0, 0).setType(Material.ANVIL);
    this.MainWorld.getBlockAt(0, this.LowestY + 1, 0).setType(Material.ENCHANTING_TABLE);

    this.Villagers.clear();
    this.AttackerManager.FixedTargets.clear();
    int currentProf = 0;
    Profession[] professions = new Profession[] { Profession.ARMORER, Profession.CLERIC, Profession.FARMER,
        Profession.TOOLSMITH, Profession.BUTCHER, };
    for (int x = -VillagerInnerWoodStart; x <= VillagerInnerWoodStart; ++x) {
      for (int z = -VillagerInnerWoodStart; z <= VillagerInnerWoodStart; ++z) {
        if (Math.abs(x) == VillagerInnerWoodStart || Math.abs(z) == VillagerInnerWoodStart) {
          if (Math.abs(x) == 1 || Math.abs(z) == 1) {
            this.MainWorld.getBlockAt(x, this.LowestY + 0, z).setType(Material.LEGACY_WOOD);
            this.MainWorld.getBlockAt(x, this.LowestY + 1, z).setType(Material.LEGACY_WOOD);
            this.MainWorld.getBlockAt(x, this.LowestY + 2, z).setType(Material.LEGACY_WOOD);
          } else if (x != 0 && z != 0) {
            Location l = new Location(this.MainWorld, x + 0.5, this.LowestY, z + 0.5);

            // Minecart minecart = (Minecart)
            // this.MainWorld.spawnEntity(l, EntityType.MINECART);

            Villager villager = (Villager) this.MainWorld.spawnEntity(l, EntityType.VILLAGER);
            villager.setBreed(false);

            Profession randProf = professions[currentProf];
            ++currentProf;
            currentProf %= professions.length;

            villager.setProfession(randProf);

            // minecart.setPassenger(villager);
            this.Villagers.add(villager);
            this.AttackerManager.FixedTargets.add(villager);

            // make villagers stand over bedrock so they can't fall
            l.subtract(0.0, 1.0, 0.0);
            this.MainWorld.getBlockAt(l).setType(Material.BEDROCK);
          }
        }
      }
    }

    this.getLogger().log(Level.INFO, "Finished initializing (pass " + this.InitializeCount + ")");
    ++this.InitializeCount;

    if (this.isFullyInitialized()) {
      this.getLogger().log(Level.INFO, "CraftDefense fully initialized!");
    }
  }

  private boolean isFullyInitialized() {
    return this.InitializeCount == 2;
  }

  private void generateSiegeTunnel() {
    for (int x = SpawnRadius - SiegeTunnelInCut; x <= SpawnRadius + SiegeTunnelLength; ++x) {
      for (int z = -SiegeTunnelHalfWidth; z <= SiegeTunnelHalfWidth; ++z) {
        this.MainWorld.getBlockAt(x, this.LowestY - 1, z).setType(Material.NETHER_BRICKS);

        for (int y = 0; y <= (this.LowestY - 2); ++y) {
          this.MainWorld.getBlockAt(x, y, z).setType(Material.BEDROCK);
        }

        // If we're at the edges of the siege tunnel, put up walls!
        if (Math.abs(z) == SiegeTunnelHalfWidth && x > SpawnRadius) {
          for (int y = this.LowestY - 2; y < this.LowestY + 3; ++y) {
            this.MainWorld.getBlockAt(x, y, z + (int) Math.signum(z)).setType(Material.BEDROCK);
          }
        }
      }
    }
  }

  private boolean addWarningTimer(double secondsBeforeStart, final String message, final float pitch) {
    final CraftDefense self = this;

    final double timeBeforeNormalized = AttackTimeNormalized
        - this.DayTimeManager.convertSecondsToDayNormalizedUnits(secondsBeforeStart);

    if (timeBeforeNormalized < 0)
      return false;

    this.DayTimeManager.DayEventCallbacks.add(DayTimeManager.new DayEventCallback() {
      {
        TimeOfDayNormalized = timeBeforeNormalized;
      }

      @Override
      void onDayEvent(int day) {
        if (day >= self.Waves.size())
          return;

        Wave wave = self.Waves.get(day);
        if (wave.isRest() == false) {
          self.getServer().broadcastMessage(Text.Yellow + message);

          for (Player player : self.getServer().getOnlinePlayers()) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.4f, pitch);
          }
        }
      }
    });

    return true;
  }

  private void villagerImmobilization() {
    int villageSize = this.Villagers.size();
    for (int i = 0; i < villageSize; ++i) {
      this.Villagers.get(i).setVelocity(new Vector(0.0, 0.0, 0.0));
    }
  }

  private void disableWeather() {
    // Disable weather in the main world
    this.MainWorld.setStorm(false);
    this.MainWorld.setThundering(false);
  }

  private void putOutFires() {
    for (int i = 0; i < 500; ++i) {
      Location l = this.FireOustLocations.get(this.FireOustIndex);
      Block b = l.getBlock();
      if (b.getType() == Material.FIRE) {
        b.setType(Material.AIR);
      }

      ++this.FireOustIndex;
      if (this.FireOustIndex >= this.FireOustLocations.size()) {
        this.FireOustIndex = 0;
      }
    }
  }

  private void spawnAmbientMob() {
    int x = 0;
    int z = 0;

    int maxIterations = 10;
    for (;;) {
      x = this.Rand.nextInt(GoodMobRadius * 2) - GoodMobRadius;
      z = this.Rand.nextInt(GoodMobRadius * 2) - GoodMobRadius;

      if (Math.abs(x) > SpawnRadius && Math.abs(z) > SpawnRadius) {
        break;
      }

      --maxIterations;
      if (maxIterations == 0)
        return;
    }

    int y = this.MainWorld.getHighestBlockYAt(x, z) + 1;

    Location l = new Location(this.MainWorld, x, y, z);

    int diceRoll = this.Rand.nextInt(5);

    switch (diceRoll) {
    case 0:
      this.MainWorld.spawnEntity(l, EntityType.COW);
      break;
    case 1:
      this.MainWorld.spawnEntity(l, EntityType.PIG);
      break;
    case 2:
      this.MainWorld.spawnEntity(l, EntityType.CHICKEN);
      break;
    case 3:
    case 4:
      this.MainWorld.spawnEntity(l, EntityType.SHEEP);
      break;
    }
  }

  public boolean isSiegeTunnel(Location l) {
    if (l.getWorld() != this.MainWorld) {
      return false;
    }

    int x = l.getBlockX();
    int z = l.getBlockZ();

    return x >= SpawnRadius - SiegeTunnelInCut && x <= SpawnRadius + SiegeTunnelLength
        && Math.abs(z) <= SiegeTunnelHalfWidth;
  }

  private void burnNonAttackersOnSiegePath() {
    List<Entity> allEntities = this.MainWorld.getEntities();

    for (int i = 0; i < allEntities.size(); ++i) {
      Entity e = allEntities.get(i);

      // If the entity is dead, invalid, or not a living entity then skip it
      // The LivingEntity part is to prevent things like items and XP from burning
      if (e.isValid() == false || e.isDead() || (e instanceof LivingEntity) == false)
        continue;

      // As long as its not a spawned enemy
      if (this.AttackerManager.isEnemy(e) == false) {
        Location l = e.getLocation();
        World w = l.getWorld();
        int x = l.getBlockX();
        int y = l.getBlockY();
        int z = l.getBlockZ();

        l = new Location(w, x, y - 1, z);

        if (this.isSiegeTunnel(l)) {
          Block b = this.MainWorld.getBlockAt(l);
          System.out.println(b.getType());

          if (b.getType() == Material.NETHER_BRICKS && e.getFireTicks() <= 5) {
            e.setFireTicks(70);
          }
        }
      }
    }
  }

  // Called by the DayTimeManager
  @SuppressWarnings("deprecation")
  @Override
  public void onDayChanged(int oldDay, int newDay) {
    // The day always changes in the morning
    // In CraftDefense, this is when we award players and get rid of enemies

    Wave waveCompleted = this.Waves.get(oldDay);

    Collection<? extends Player> players = this.getServer().getOnlinePlayers();

    // For now since block protection is a difficult problem to solve, just
    // regenerate the siege path
    // This does mean they can get extra nether brick... but oh well!
    this.generateSiegeTunnel();

    this.getServer().broadcastMessage(Text.Green + "*** Day " + (oldDay + 1) + " complete. ***");

    if (waveCompleted.AwardedEmeralds != 0) {
      this.getServer().broadcastMessage(Text.Aqua + "*** Awarded " + waveCompleted.AwardedEmeralds + " emerald! ***");

      ItemStack emeralds = new ItemStack(Material.EMERALD, waveCompleted.AwardedEmeralds);

      for (Player player : players) {
        player.getInventory().addItem(emeralds);
        player.giveExp(waveCompleted.AwardedXp);
      }
    }
    if (waveCompleted.AwardedXp != 0)
      this.getServer().broadcastMessage(Text.Aqua + "*** Awarded " + waveCompleted.AwardedXp + " xp! ***");

    this.AttackerManager.killAllAttackers();

    // If this was the last wave
    if (newDay == this.Waves.size()) {
      this.getServer().broadcastMessage(Text.DarkGreen + "*** Congratulations, you've beat the game! ***");

      for (Player player : players) {
        player.setHealth(player.getMaxHealth());
        player.setFireTicks(0);
        player.setLevel(999);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setGameMode(GameMode.CREATIVE);
      }

      // Disable this plugin entirely
      this.shutDown();
    } else {
      Wave nextWave = this.Waves.get(newDay);
      this.getServer().broadcastMessage(Text.Gold + "*** Next wave: " + nextWave.Name + " ***");
    }
  }

  private void spawnWave(int day) {
    if (day >= this.Waves.size())
      return;

    final CraftDefense self = this;
    final Wave wave = this.Waves.get(day);

    @SuppressWarnings("deprecation")
    double difficulty = this.MainWorld.getDifficulty().getValue();

    for (int i = 0; i < wave.EnemyWaves.size(); ++i) {
      final Wave.EnemyWave enemyWave = wave.EnemyWaves.get(i);
      int numPlayers = this.getServer().getOnlinePlayers().size();

      int totalSpawns = Math.max(1,
          (int) (numPlayers * (enemyWave.PerPlayer + difficulty * enemyWave.PerDifficulty) + enemyWave.InitialAmount));

      double normalizedDelay = this.DayTimeManager.convertSecondsToDayNormalizedUnits(enemyWave.DelaySeconds);
      double normalizedTimePerSpawn = this.DayTimeManager
          .convertSecondsToDayNormalizedUnits(1.0 / enemyWave.EnemiesPerSecond);

      for (int j = 0; j < totalSpawns; ++j) {
        double totalDelay = normalizedDelay + normalizedTimePerSpawn * j;
        this.DayTimeManager.scheduleTask(totalDelay, new Runnable() {
          @Override
          public void run() {
            int x = 0;
            int y = 0;
            int z = 0;

            // We spawn enemies in a slightly randomized
            // enclosed space in the siege path (half the
            // path width in radius)
            x = SpawnRadius + SiegeTunnelLength - self.Rand.nextInt(SiegeTunnelHalfWidth);
            z = self.Rand.nextInt(SiegeTunnelHalfWidth) - SiegeTunnelHalfWidth / 2;
            y = self.SpawnY;

            Location l = new Location(self.MainWorld, x, y, z);
            Entity attacker = enemyWave.spawnAttacker(l);
            self.AttackerManager.addAttacker(attacker, enemyWave);
            if (attacker.getVehicle() != null)
              self.AttackerManager.addAttacker(attacker.getVehicle(), enemyWave);
          }
        });
      }
    }

    for (int i = 0; i < wave.SpecialWaves.size(); ++i) {
      final Wave.SpecialWave specialWave = wave.SpecialWaves.get(i);

      int count = (int) (specialWave.Intensity);
      double normalizedDelay = this.DayTimeManager.convertSecondsToDayNormalizedUnits(specialWave.DelaySeconds);
      double normalizedDuration = this.DayTimeManager.convertSecondsToDayNormalizedUnits(specialWave.DurationSeconds);
      double timePerIteration = normalizedDuration / count;

      for (int j = 0; j < count; ++j) {
        double totalDelay = normalizedDelay + timePerIteration * j;

        this.DayTimeManager.scheduleTask(totalDelay, new Runnable() {
          @Override
          public void run() {
            Player targetedPlayer = self.getRandomPlayer();
            if (self.Rand.nextDouble() > specialWave.TargetPlayerProbability)
              targetedPlayer = null;

            switch (specialWave.Type) {
            case ARROW:
            case FIRE_ARROW: {
              Location spawnLocation = new Location(self.MainWorld,
                  SpawnRadius + SiegeTunnelLength - self.Rand.nextDouble() * 5, self.SpawnY,
                  (self.Rand.nextDouble() - 0.5) * SpawnRadius * 1.7);

              if (targetedPlayer != null)
                spawnLocation.setZ(targetedPlayer.getLocation().getZ());

              spawnLocation.setY(self.MainWorld.getHighestBlockYAt(spawnLocation) + 2.0);

              Vector direction = new Vector(-1, 1, 0).normalize();

              final Arrow arrow = self.MainWorld.spawnArrow(spawnLocation, direction,
                  1.5f + (float) (self.Rand.nextDouble() * 1.0), 1.0f);

              if (specialWave.Type == SpecialWaveType.FIRE_ARROW) {
                arrow.setFireTicks(arrow.getMaxFireTicks());
              }

              self.getServer().getScheduler().runTaskLater(self, new Runnable() {
                @Override
                public void run() {
                  if (self.isEnabled() == false)
                    return;
                  arrow.remove();
                }
              }, Utilities.TicksPerSecond * 8);
              break;
            }

            case LIGHTNING: {
              Location spawnLocation = null;

              if (targetedPlayer != null) {
                spawnLocation = self.MainWorld.getHighestBlockAt(targetedPlayer.getLocation()).getLocation();
              } else {
                spawnLocation = new Location(self.MainWorld, SpawnRadius * (self.Rand.nextDouble() - 0.5) * 2,
                    self.SpawnY, SpawnRadius * (self.Rand.nextDouble() - 0.5) * 2);
                spawnLocation.setY(self.MainWorld.getHighestBlockYAt(spawnLocation));
              }

              self.MainWorld.strikeLightning(spawnLocation);

              int outerArea = SpawnRadius * 4;
              Location outerLocation = new Location(self.MainWorld, outerArea * (self.Rand.nextDouble() - 0.5) * 2,
                  self.SpawnY, outerArea * (self.Rand.nextDouble() - 0.5) * 2);
              outerLocation.setY(self.MainWorld.getHighestBlockYAt(outerLocation));
              self.MainWorld.strikeLightning(outerLocation);
              break;
            }

            case ANVIL: {
              Location spawnLocation = null;

              if (targetedPlayer != null) {
                spawnLocation = targetedPlayer.getLocation();
                spawnLocation.setY(128);
              } else {
                spawnLocation = new Location(self.MainWorld, SpawnRadius * (self.Rand.nextDouble() - 0.5) * 2, 128,
                    SpawnRadius * (self.Rand.nextDouble() - 0.5) * 2);
              }

              Block b = spawnLocation.getBlock();
              b.setType(Material.ANVIL);

              // Randomize the orientation and brokenness of the anvil
              /*
               * if (self.Rand.nextBoolean() == true) b.setData((byte) (4 +
               * self.Rand.nextInt(8))); else b.setData((byte) (8 + self.Rand.nextInt(4)));
               */
              break;
            }

            default:
              break;
            }

          }
        });
      }

    }

    this.broadcastWaveMessage();
  }

  public void broadcastWaveMessage() {
    String message = this.getWaveMessageOrNull(this.DayTimeManager.getDay(), true);
    if (message != null)
      this.getServer().broadcastMessage(message);
  }

  public String getWaveMessageOrNull(int day, Boolean includeSpecialMessage) {
    if (day >= this.Waves.size())
      return null;

    Wave wave = this.Waves.get(day);
    String msg = Text.Gold + "*** Day " + (day + 1) + " of " + this.Waves.size() + " : " + wave.Name + " ***";

    if (includeSpecialMessage) {
      if (wave.Message != null)
        msg += Text.Gold + "\n*** " + wave.Message + " ***";
    }

    return msg;
  }

  public String getWaveListFromDay(int day) {
    String ret = "";
    for (int i = day; i < this.Waves.size(); ++i) {
      Wave wave = this.Waves.get(i);
      ret += (i + 1) + ": " + wave.Name + ", ";
    }
    if (ret != "") {
      ret = ret.substring(0, ret.length() - 2);
    }
    return ret;
  }

  @SuppressWarnings("incomplete-switch")
  @EventHandler
  public void onCreatureSpawn(CreatureSpawnEvent event) {
    if (event.getSpawnReason() != SpawnReason.CUSTOM) {
      // Don't spawn these types of entities (they're generally annoying)
      switch (event.getEntityType()) {
      case BAT:
      case BLAZE:
      case CAVE_SPIDER:
      case CREEPER:
      case DROWNED:
      case ELDER_GUARDIAN:
      case ENDER_DRAGON:
      case ENDERMAN:
      case ENDERMITE:
      case EVOKER:
      case EVOKER_FANGS:
      case GHAST:
      case GIANT:
      case GUARDIAN:
      case HUSK:
      case ILLUSIONER:
      case IRON_GOLEM:
      case MAGMA_CUBE:
      case MINECART_MOB_SPAWNER:
      case PHANTOM:
      case PIG_ZOMBIE:
      case PILLAGER:
      case RAVAGER:
      case SHULKER:
      case SILVERFISH:
      case SKELETON:
      case SKELETON_HORSE:
      case SLIME:
      case SPIDER:
      case STRAY:
      case VEX:
      case VINDICATOR:
      case WITCH:
      case WITHER:
      case WITHER_SKELETON:
      case WOLF:
      case ZOMBIE:
      case ZOMBIE_HORSE:
      case ZOMBIE_VILLAGER:

        event.setCancelled(true);
        break;
      }
    }
  }

  @EventHandler
  public void onPlayerJoin(PlayerJoinEvent event) {
    this.giveFirstItems(event);

    if (this.MainWorldInitializedOnFirstJoin == false) {
      this.MainWorldInitializedOnFirstJoin = true;

      final CraftDefense self = this;
      ;
      // This is so super ghetto, we've already initialized the world,
      // just do it again to avoid caves wrecking our crap (after the
      // first player joins, 1 second later)
      this.getServer().getScheduler().runTaskLater(this, new Runnable() {
        @Override
        public void run() {
          if (self.isEnabled() == false)
            return;
          self.initializeMainWorld();
        }
      }, Utilities.TicksPerSecond);
    }
  }

  @EventHandler
  public void onPlayerSpawn(PlayerRespawnEvent event) {
    this.giveFirstItems(event);
  }

  public void giveFirstItems(PlayerEvent event) {
    // Get the current player
    Player p = event.getPlayer();

    ItemStack s;

    // If this player is already a veteran (not the first spawn...)
    if (this.Veterans.contains(p.getUniqueId()) == false) {
      // Clear the screen
      for (int i = 0; i < 50; ++i) {
        p.sendMessage(" ");
      }

      p.sendMessage(Text.LightPurple + "*** Defend the villagers at all costs (they will SCREAM!) ***");

      s = new ItemStack(Material.STONE_SWORD, 1);
      p.getInventory().addItem(s);

      s = new ItemStack(Material.BOW, 1);
      p.getInventory().addItem(s);

      s = new ItemStack(Material.ARROW, 64);
      p.getInventory().addItem(s);

      s = new ItemStack(Material.COOKED_BEEF, 1);
      p.getInventory().addItem(s);

      this.Veterans.add(p.getUniqueId());
    }

    String message = this.getWaveMessageOrNull(this.DayTimeManager.getDay(), true);
    if (message != null)
      p.sendMessage(message);
  }

  private Player getRandomPlayer() {
    Player[] players = getPlayers();

    if (players.length == 0) {
      return null;
    }

    int index = this.Rand.nextInt(players.length);
    return players[index];
  }

  public File getSaveDirectory(String saveName) {
    File dataFolder = this.getDataFolder();
    return new File(dataFolder, saveName);
  }

  @Override
  public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
    // global
    if (cmd.getName().equalsIgnoreCase("cd-timescale")) {
      double newTimeScale = Double.parseDouble(args[0]);
      this.DayTimeManager.TimeScale = newTimeScale;
      this.getServer().broadcastMessage("TimeScale set to " + newTimeScale);
      return true;
    }

    if (cmd.getName().equalsIgnoreCase("cd-goto")) {
      double waveDay = Double.parseDouble(args[0]);
      this.DayTimeManager.advanceTo(waveDay);
      this.getServer().broadcastMessage("Advanced to " + waveDay);
      return true;
    }

    if (sender instanceof Player == false)
      return false;

    // Player specific
    Player p = (Player) sender;

    if (cmd.getName().equalsIgnoreCase("cd-current")) {
      p.sendMessage(getWaveMessageOrNull(this.DayTimeManager.getDay(), true));
    }

    if (cmd.getName().equalsIgnoreCase("cd-next")) {
      String msg = getWaveMessageOrNull(this.DayTimeManager.getDay() + 1, false);
      if (msg == null) {
        msg = "This is the last wave!!";
      }
      p.sendMessage(msg);
    }

    if (cmd.getName().equalsIgnoreCase("cd-list")) {
      String msg = getWaveListFromDay(this.DayTimeManager.getDay());
      p.sendMessage(msg);
    }

    if (cmd.getName().equalsIgnoreCase("cd-save")) {
      if (args.length != 1) {
        p.sendMessage(Text.Red + "Save command requires a name of the save");
        return true;
      }

      String saveName = args[0];
      File saveDirectory = getSaveDirectory(saveName);

      this.getServer().savePlayers();

      List<World> worlds = this.getServer().getWorlds();
      for (World world : worlds) {
        world.save();

        File worldCurrentDirectory = world.getWorldFolder();
        String worldFileName = worldCurrentDirectory.getName();
        File worldSavedDirectory = new File(saveDirectory, worldFileName);

        // Copy this world to a new directory (made by the save in our data directory)
        worldSavedDirectory.mkdirs();

        CraftDefense.copyFolder(worldCurrentDirectory, worldSavedDirectory);
      }
    }

    if (cmd.getName().equalsIgnoreCase("cd-load")) {
      if (args.length != 1) {
        p.sendMessage(Text.Red + "Load command requires a name of the save");
        return true;
      }

      String saveName = args[0];
      File saveDirectory = getSaveDirectory(saveName);

      if (saveDirectory.exists() == false) {

        for (Player player : this.getServer().getOnlinePlayers()) {
          player.kickPlayer("World is reloading, please rejoin in a few seconds");
        }

        List<World> worlds = this.getServer().getWorlds();
        for (World world : worlds) {
          if (this.getServer().unloadWorld(world, false) == false)
            this.getLogger().log(Level.SEVERE, "Unable to unload the world '" + world.getName()
                + "' when attempting to load from save '" + saveName + "'");

          File worldCurrentDirectory = world.getWorldFolder();
          String worldFileName = worldCurrentDirectory.getName();
          File worldSavedDirectory = new File(saveDirectory, worldFileName);
          File backupName = new File(saveDirectory, worldFileName + new Date().toString() + ".bak");
          if (!worldCurrentDirectory.renameTo(backupName)) {
            this.getLogger().log(Level.SEVERE, "Unable to delete/backup world '" + world.getName()
                + "' when attempting to load from save '" + saveName);
          }

          CraftDefense.copyFolder(worldSavedDirectory, worldCurrentDirectory);
        }
      } else {
        this.getServer().broadcastMessage(Text.Red + "Unable to find save '" + saveName + "'");
      }
    }

    if (cmd.getName().equalsIgnoreCase("cd-debug")) {
      p.sendMessage("Giving extra items for debug");

      ItemStack s;

      s = this.ExperienceGeneratorManager.ExperienceGeneratorStack;
      p.getInventory().addItem(s);

      s = new ItemStack(Material.IRON_BLOCK, 10);
      p.getInventory().addItem(s);

      s = new ItemStack(Material.GOLD_BLOCK, 10);
      p.getInventory().addItem(s);

      s = new ItemStack(Material.EMERALD_BLOCK, 10);
      p.getInventory().addItem(s);

      s = new ItemStack(Material.DIAMOND_BLOCK, 10);
      p.getInventory().addItem(s);

      s = new ItemStack(Material.OAK_WOOD, 64);
      p.getInventory().addItem(s);

      s = new ItemStack(Material.REDSTONE_BLOCK, 64);
      p.getInventory().addItem(s);

      s = new ItemStack(Material.FERMENTED_SPIDER_EYE, 10);
      p.getInventory().addItem(s);

      s = new ItemStack(Material.COBBLESTONE, 64);
      p.getInventory().addItem(s);

      s = new ItemStack(Material.ARROW, 30);
      p.getInventory().addItem(s);

      s = new ItemStack(Material.LAVA_BUCKET, 1);
      p.getInventory().addItem(s);

      s = new ItemStack(Material.SNOWBALL, 1);
      p.getInventory().addItem(s);

      s = new ItemStack(Material.WATER_BUCKET, 1);
      p.getInventory().addItem(s);

      s = new ItemStack(Material.TROPICAL_FISH, 1);
      p.getInventory().addItem(s);

      s = new ItemStack(Material.WHITE_WOOL, 20);
      p.getInventory().addItem(s);

      s = new ItemStack(Material.ICE, 10);
      p.getInventory().addItem(s);

      s = new ItemStack(Material.TORCH, 10);
      p.getInventory().addItem(s);

      s = new ItemStack(Material.DIAMOND_PICKAXE, 1);
      p.getInventory().addItem(s);

      s = new ItemStack(Material.LAPIS_BLOCK, 10);
      p.getInventory().addItem(s);

      s = new ItemStack(Material.STONE_PRESSURE_PLATE, 10);
      p.getInventory().addItem(s);

      p.updateInventory();
      return true;
    }

    return false;
  }

  @EventHandler
  public void onPlayerEmptyBucket(PlayerBucketEmptyEvent event) {
    Player player = event.getPlayer();

    // If the place where the water or lava would be placed is above a certain
    // y-value...
    int yValue = this.LowestY - 1;
    if (event.getBlockClicked().getRelative(event.getBlockFace()).getLocation().getY() >= yValue) {
      player.sendMessage(Text.DarkRed + "*** Only use water/lava below the spawn (y of " + yValue + ") ***");
      event.setCancelled(true);
    }
  }

  @EventHandler
  public void onEntityDeath(EntityDeathEvent event) {
    final Entity e = event.getEntity();

    if (e.getType() == EntityType.ENDER_DRAGON) {
      event.getDrops().clear();

      // Kill the ender dragon just before it actually drops the dragon egg
      final CraftDefense self = this;
      this.getServer().getScheduler().runTaskLater(self, new Runnable() {
        @Override
        public void run() {
          if (self.isEnabled() == false)
            return;
          World w = e.getLocation().getWorld();
          w.createExplosion(e.getLocation(), 0.0f);
          e.remove();
        }
      }, Utilities.TicksPerSecond * 9); // This time is carefully tweaked to allow the explosion and XP drop, but not
                                        // the Dragon Egg/portal
    }

    if (e.getType() == EntityType.WITHER) {
      World w = e.getLocation().getWorld();
      for (int i = 0; i < 5; ++i) {
        w.createExplosion(e.getLocation(), 0.0f);
      }
    }

    // If this entity is one of the original villagers...
    if (this.Villagers.contains(e)) {
      // Remove the villager from the list
      this.Villagers.remove(e);

      // If a player killed the villager...
      Villager v = (Villager) e;
      Entity killer = v.getKiller();
      if (killer instanceof Player) {
        this.getServer()
            .broadcastMessage(Text.Red + "*** " + v.getKiller().getName() + " killed a villager, watch it! ***");
      }

      // Tell everyone that the villager died
      this.getServer().broadcastMessage(Text.DarkRed + "*** " + this.Villagers.size() + " villagers left! ***");

      // If all the villagers are dead...
      if (this.Villagers.size() == 0) {
        this.getServer().broadcastMessage(Text.DarkRed + "***  GAME OVER ***");

        for (Player player : this.getServer().getOnlinePlayers()) {
          player.getWorld().createExplosion(player.getLocation(), 20.0f);
          player.damage(10000);
          player.kickPlayer("GAME OVER");
        }

        // Turn off the plugin entirely
        this.shutDown();
      }
    }
  }

  private void shutDown() {
    this.getServer().getPluginManager().disablePlugin(this);
    this.getServer().getScheduler().cancelTasks(this);
    this.setEnabled(false);
  }

  @EventHandler
  public void onBlockPlaced(BlockPlaceEvent event) {
    Location l = event.getBlockPlaced().getLocation();

    // Protection for the siege tunnel
    if (this.isSiegeTunnel(l)) {
      event.getPlayer().sendMessage(Text.DarkRed + "*** No blocks can be placed on the siege path ***");
      event.setCancelled(true);
      return;
    }
  }

  @EventHandler
  public void onBlockBreak(BlockBreakEvent event) {
    Block b = event.getBlock();
    Location l = b.getLocation();

    // Protection for the siege tunnel
    if (this.isSiegeTunnel(l)) {
      event.getPlayer().sendMessage(Text.DarkRed + "*** No blocks can be removed from the siege path ***");
      event.setCancelled(true);
      return;
    }

    // This is the layer snow, which can be exploited with the infnite snowman trick
    if (b.getType() == Material.SNOW) {
      // We prevent this exploit by cancelling the event some percent of the time
      // (probability)
      if (this.Rand.nextDouble() < 0.8)
        event.setCancelled(true);
    }

    // If this is ice, just turn the ice into air
    if (b.getType() == Material.ICE) {
      b.setType(Material.AIR);
    }
  }

  @EventHandler
  public void onBlockChange(BlockFromToEvent event) {
    if (this.isFullyInitialized() == false)
      return;

    Block from = event.getBlock();
    Block to = event.getToBlock();

    if (from.getLocation().getBlockY() >= this.LowestY - 1) {
      // We don't want ice to change into water (unless its below the lowest y value,
      // then its ok!)
      if (from.getType() == Material.ICE) {
        event.setCancelled(true);
        from.setType(Material.AIR);
        to.setType(Material.AIR);
      }
    }
  }

  @EventHandler
  public void onBlockFade(BlockFadeEvent event) {
    if (this.isFullyInitialized() == false)
      return;

    Material m = event.getBlock().getType();

    if (m == Material.SNOW || m == Material.SNOW_BLOCK || m == Material.ICE) {
      event.setCancelled(true);
    }
  }

  @EventHandler
  public void onEntityDamage(EntityDamageEvent event) {
    // Mobs don't take fall damage (so we can't exploit mobs)
    if (event.getEntityType() != EntityType.PLAYER && event.getCause() == DamageCause.FALL) {
      event.setCancelled(true);

    }

    if (event.isCancelled() == false && event.getEntityType() == EntityType.VILLAGER) {
      Location l = event.getEntity().getLocation();
      l.getWorld().playEffect(l, Effect.EXTINGUISH, 0);
      l.getWorld().playEffect(l, Effect.GHAST_SHRIEK, 0);
    }
  }

  @EventHandler
  public void onBlockDispense(BlockDispenseEvent event) {
    Material itemType = event.getItem().getType();
    if (itemType == Material.WATER_BUCKET || itemType == Material.LAVA_BUCKET) {
      this.getServer().broadcastMessage(Text.DarkRed + "*** Cannot dispense water or lava (makes it too easy) ***");
      event.setCancelled(true);
    }
  }
}
