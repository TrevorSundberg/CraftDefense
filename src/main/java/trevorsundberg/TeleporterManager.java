package trevorsundberg;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;

import org.bukkit.DyeColor;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.material.Wool;

public class TeleporterManager implements Listener {
  private final HashMap<DyeColor, ArrayList<Teleporter>> TeleportersByColor = new HashMap<DyeColor, ArrayList<Teleporter>>();

  private final HashMap<Location, Teleporter> TeleporterByLocation = new HashMap<Location, Teleporter>();

  private final HashSet<UUID> StandingOnTeleporter = new HashSet<UUID>();

  public class Teleporter {
    public DyeColor Color;
    public Location[] Locations = new Location[2];
  }

  @EventHandler
  public void onPlayerMovement(PlayerMoveEvent event) {
    Player p = event.getPlayer();
    Location l = p.getLocation();

    Teleporter tele = this.getTeleporterAt(l);

    if (tele != null) {
      if (this.StandingOnTeleporter.contains(p.getUniqueId()) == false) {
        tele = this.getNextTeleporter(tele);

        if (tele != null) {
          Location to = event.getTo();
          Location teleTo = tele.Locations[0];
          Location up = new Location(teleTo.getWorld(), teleTo.getBlockX() + 0.5, teleTo.getBlockY(),
              teleTo.getBlockZ() + 0.5);
          up.setPitch(to.getPitch());
          up.setYaw(to.getYaw());

          p.teleport(up);

          p.getWorld().playEffect(event.getFrom(), Effect.ENDER_SIGNAL, 0);
          p.getWorld().playEffect(event.getFrom(), Effect.BLAZE_SHOOT, 0);
          p.getWorld().playEffect(up, Effect.ENDER_SIGNAL, 0);
          p.getWorld().playEffect(up, Effect.BLAZE_SHOOT, 0);
        }

        this.StandingOnTeleporter.add(p.getUniqueId());
      }
    } else {
      this.StandingOnTeleporter.remove(p.getUniqueId());
    }
  }

  @EventHandler
  public void onBlockBreak(BlockBreakEvent event) {
    Teleporter tele = this.getTeleporterAt(event.getBlock().getLocation());

    if (tele != null) {
      this.destroyTeleporter(tele);
      event.getPlayer().sendMessage(Text.DarkPurple + "*** Teleporter removed ***");
    }
  }

  @EventHandler
  public Teleporter onBlockPlaced(BlockPlaceEvent event) {
    Location l = event.getBlockPlaced().getLocation();
    World w = l.getWorld();
    Location top = null;

    for (int dy = 0; dy <= 1; ++dy) {
      Location current = new Location(w, l.getBlockX(), l.getBlockY() + dy, l.getBlockZ());
      Block b = w.getBlockAt(current);

      if (b.getType() == Material.LEGACY_STONE_PLATE) {
        top = l;
        break;
      }
    }

    if (top != null) {
      Location below = new Location(w, top.getBlockX(), top.getBlockY() - 1, top.getBlockZ());

      Block b = w.getBlockAt(below);

      if (b.getType() == Material.LEGACY_WOOL) {
        // Do any prior cleanup, just so we don't ever get two teleporters at the same
        // place
        this.destroyInvalidTeleporters();

        // This is now a teleporter!
        @SuppressWarnings("deprecation")
        Wool wool = new Wool(b.getType(), b.getData());

        Teleporter t = new Teleporter();
        t.Locations[0] = top;
        t.Locations[1] = below;
        t.Color = wool.getColor();

        this.TeleporterByLocation.put(t.Locations[0], t);
        this.TeleporterByLocation.put(t.Locations[1], t);

        ArrayList<Teleporter> teleporters = this.TeleportersByColor.get(wool.getColor());

        if (teleporters == null) {
          teleporters = new ArrayList<Teleporter>();
          this.TeleportersByColor.put(wool.getColor(), teleporters);
        }

        event.getPlayer()
            .sendMessage(Text.Blue + "*** " + Text.DarkAqua + t.Color + Text.Blue + " teleporter created ***");
        teleporters.add(t);
        return t;
      }
    }

    return null;
  }

  private void destroyTeleporter(Teleporter t) {
    // Because this function also gets called by destroyInvalidTeleporters, we need
    // to make sure
    // that all we do is remove a single teleporter from the teleporters list
    this.TeleporterByLocation.remove(t.Locations[0]);
    this.TeleporterByLocation.remove(t.Locations[1]);

    ArrayList<Teleporter> teleporters = this.TeleportersByColor.get(t.Color);
    teleporters.remove(t);
  }

  public void destroyInvalidTeleporters() {
    for (ArrayList<Teleporter> teleporters : this.TeleportersByColor.values()) {
      for (int i = 0; i < teleporters.size();) {
        Teleporter teleporter = teleporters.get(i);

        Block plate = teleporter.Locations[0].getBlock();
        Block wool = teleporter.Locations[1].getBlock();

        // Make sure its got a stone plate on top, wool on bottom, and the wool matches
        // the color
        @SuppressWarnings("deprecation")
        boolean isInvalid = plate.getType() != Material.LEGACY_STONE_PLATE || wool.getType() != Material.LEGACY_WOOL
            || new Wool(wool.getType(), wool.getData()).getColor() != teleporter.Color;

        if (isInvalid) {
          // It would be nice to swap erase here, but we want to preserve order (linked
          // list in the future I guess)
          // Note: This should basically just remove the teleporter at the current index
          // If the array is modified in any other way within this function, this will
          // totally break!
          destroyTeleporter(teleporter);
        } else {
          ++i;
        }
      }
    }
  }

  public Teleporter getNextTeleporter(Teleporter t) {
    this.destroyInvalidTeleporters();

    Teleporter result = null;

    ArrayList<Teleporter> teleporters = this.TeleportersByColor.get(t.Color);
    int index = teleporters.indexOf(t);

    // If this is the last teleporter
    if (index >= teleporters.size() - 1) {
      result = teleporters.get(0);
    } else {
      result = teleporters.get(index + 1);
    }

    // If we are the only teleporter...
    if (result == t) {
      return null;
    }

    return result;
  }

  public Teleporter getTeleporterAt(Location l) {
    l = new Location(l.getWorld(), l.getBlockX(), l.getBlockY(), l.getBlockZ());

    // Because the destruction code can be expensive, we only want to run it if the
    // user
    // actually queries for a location that is a real teleporter
    if (this.TeleporterByLocation.containsKey(l))
      this.destroyInvalidTeleporters();

    return this.TeleporterByLocation.get(l);
  }
}
