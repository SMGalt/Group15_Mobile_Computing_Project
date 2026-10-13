package com.group15.studydesk;

import static org.junit.Assert.*;

import android.content.*;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.time.*;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Run seed, reboot the emulator, inspect system alarms before relaunch, then run verify. */
@RunWith(AndroidJUnit4.class)
public class RebootValidationTest {
  @Test
  public void persistedReminderAndTimerAnchors() throws Exception {
    Context c = ApplicationProvider.getApplicationContext();
    Repository repo = new Repository(c);
    boolean seed = "seed".equals(InstrumentationRegistry.getArguments().getString("phase"));
    if (seed) {
      c.getSharedPreferences("settings", 0).edit().putBoolean("notifications", true).commit();
      c.getSharedPreferences("timer", 0)
          .edit()
          .clear()
          .putInt("duration", 3600)
          .putInt("secs", 3600)
          .commit();
      Repository.IO
          .submit(
              () -> {
                repo.db.records().clear("guest");
                Row row =
                    new Row("assignment")
                        .put("title", "Reboot fixture")
                        .put("dueDate", LocalDate.now().plusDays(2).toString())
                        .put("dueTime", "18:00");
                row.id = "reboot-fixture";
                repo.save(row);
                repo.changed();
                new TimerState(c).start("Reboot fixture", "", 3600);
                Reminders.timer(c);
              })
          .get();
    } else {
      assertNotNull(Repository.IO.submit(() -> repo.find("assignment", "reboot-fixture")).get());
      TimerState timer = new TimerState(c);
      assertTrue(timer.running());
      assertTrue(timer.secs() < 3600);
      assertTrue(timer.secs() > 0);
      timer.pause();
      Reminders.timer(c);
    }
    assertTrue(Reminders.allowed(c));
    assertTrue(
        c.getSharedPreferences("alarms", 0)
                .getStringSet("ids", java.util.Collections.emptySet())
                .size()
            >= 30);
  }
}
