package trevorsundberg;

import java.util.ArrayList;

import org.bukkit.World;
import org.bukkit.plugin.Plugin;

public class DayTimeManager {
  public interface DayChangedCallback {
    void onDayChanged(int oldDay, int newDay);
  }

  public abstract class DayEventCallback {
    double TimeOfDayNormalized;

    abstract void onDayEvent(int day);
  }

  public class Task {
    public int RunOnTick;
    public Runnable ToRun;
  }

  private World MainWorld;
  private final int TrueLengthOfDayTicks = 24000; // 20 minutes
  private int LengthOfDayTicks;
  private double TimeAccumulator = 0;
  private final ArrayList<Task> Tasks = new ArrayList<Task>();

  public DayChangedCallback OnDayChanged;
  public ArrayList<DayEventCallback> DayEventCallbacks = new ArrayList<DayEventCallback>();
  public int CurrentTick = 0;
  public double TimeScale = 1;

  public DayTimeManager(final Plugin plugin, World world, int lengthOfDayTicks) {
    this.LengthOfDayTicks = lengthOfDayTicks;

    final DayTimeManager self = this;

    plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
      @Override
      public void run() {
        if (plugin.isEnabled() == false)
          return;

        self.TimeAccumulator += self.TimeScale;

        while (self.TimeAccumulator > 1.0) {
          self.incrementTick();
          self.TimeAccumulator -= 1.0;
        }

        // Always update the time of day visually
        self.MainWorld.setTime((int) (self.getTimeOfDayNormalized() * TrueLengthOfDayTicks));
      }
    }, 1, 1);

    this.MainWorld = world;
  }

  public void scheduleTask(double delayNormalized, Runnable task) {
    int delayTicks = (int) (this.LengthOfDayTicks * delayNormalized);
    int tickToRunOn = this.CurrentTick + delayTicks;
    Task ourTask = new Task();
    ourTask.RunOnTick = tickToRunOn;
    ourTask.ToRun = task;
    this.Tasks.add(ourTask);
  }

  public void advanceTo(double dayWithNormalizedTime) {
    int day = (int) dayWithNormalizedTime;
    double timeOfDayNormalized = dayWithNormalizedTime - day;

    while (this.getDay() < day) {
      this.incrementTick();
    }

    while (this.getTimeOfDayNormalized() < timeOfDayNormalized) {
      this.incrementTick();
    }
  }

  public int getDay() {
    return this.CurrentTick / this.LengthOfDayTicks;
  }

  // Gets the time of day from [0, 1] where 0 is the morning, 0.5 is midnight,
  // and 1 is the morning again
  public double getTimeOfDayNormalized() {
    return (this.CurrentTick % this.LengthOfDayTicks) / (double) this.LengthOfDayTicks;
  }

  public double convertSecondsToDayNormalizedUnits(double seconds) {
    double lengthOfDaySeconds = this.LengthOfDayTicks / (double) Utilities.TicksPerSecond;
    return seconds / lengthOfDaySeconds;
  }

  private void incrementTick() {
    int oldDay = this.getDay();

    ++this.CurrentTick;

    int newDay = this.getDay();

    if (newDay != oldDay) {
      if (this.OnDayChanged != null) {
        this.OnDayChanged.onDayChanged(oldDay, newDay);
      }
    }

    for (int i = 0; i < this.DayEventCallbacks.size(); ++i) {
      DayEventCallback dayEvent = this.DayEventCallbacks.get(i);

      int dayEventTick = (int) (dayEvent.TimeOfDayNormalized * this.LengthOfDayTicks);

      if (dayEventTick == (this.CurrentTick % this.LengthOfDayTicks)) {
        dayEvent.onDayEvent(newDay);
      }
    }

    for (int i = 0; i < this.Tasks.size();) {
      Task task = this.Tasks.get(i);

      if (this.CurrentTick == task.RunOnTick) {
        task.ToRun.run();
        this.Tasks.remove(i);
      } else {
        ++i;
      }
    }
  }
}
