package com.group15.studydesk;

import static org.junit.Assert.*;

import android.content.*;
import android.view.View;
import androidx.test.core.app.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.*;
import org.junit.*;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class TimerNavigationTest {
  Context context;

  @Before public void setup() {
    context = ApplicationProvider.getApplicationContext();
    context.getSharedPreferences("timer", 0).edit().clear().commit();
    context.getSharedPreferences("settings", 0).edit()
        .putBoolean("notifications", false).putBoolean("focusPin", false).commit();
  }

  @After public void cleanup() {
    context.getSharedPreferences("timer", 0).edit().clear().commit();
    Reminders.timer(context);
  }

  ActivityScenario<MainActivity> launch() {
    return ActivityScenario.launch(new Intent(context, MainActivity.class)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  }

  @Test public void timerStaysInsideMainAndSwitchingTabsKeepsItRunning() throws Exception {
    try (ActivityScenario<MainActivity> scenario = launch()) {
      scenario.onActivity(a -> {
        a.ui.navigation.getChildAt(3).performClick();
        assertEquals("Timer", a.screen);
        assertEquals(3, a.ui.navigation.selected());
        assertEquals(View.VISIBLE, a.ui.navigation.getVisibility());
        assertEquals(View.VISIBLE, a.ui.timerHost.getVisibility());
      });
      InstrumentationRegistry.getInstrumentation().waitForIdleSync();
      scenario.onActivity(a -> {
        a.timerPanel.ui.task.setText("Navigation test");
        a.timerPanel.ui.start.performClick();
        assertTrue(a.timerPanel.timer.running());
        a.ui.navigation.getChildAt(1).performClick();
        assertEquals("Plan", a.screen);
        assertEquals(View.GONE, a.ui.timerHost.getVisibility());
        assertTrue(a.timerPanel.timer.running());
      });
      Thread.sleep(1200);
      scenario.onActivity(a -> {
        a.ui.navigation.getChildAt(3).performClick();
        assertTrue(a.timerPanel.timer.running());
        assertTrue(a.timerPanel.timer.secs() < 1500);
        assertEquals("Navigation test", a.timerPanel.ui.task.getText().toString());
        assertEquals("Pause", a.timerPanel.ui.start.getText().toString());
      });
      scenario.recreate();
      scenario.onActivity(a -> {
        assertEquals("Timer", a.screen);
        assertEquals(3, a.tab);
        assertTrue(a.timerPanel.timer.running());
        assertEquals(View.VISIBLE, a.ui.navigation.getVisibility());
      });
    }
  }

  @Test public void backReturnsFromSubTabThenToThePreviousPlanPage() {
    try (ActivityScenario<MainActivity> scenario = launch()) {
      scenario.onActivity(a -> {
        a.navigate("Calendar");
        a.ui.navigation.getChildAt(3).performClick();
        a.timerPanel.ui.timerSubTabs.getChildAt(1).performClick();
        assertEquals(1, a.timerPanel.sub);
        a.getOnBackPressedDispatcher().onBackPressed();
        assertEquals("Timer", a.screen);
        assertEquals(0, a.timerPanel.sub);
        a.getOnBackPressedDispatcher().onBackPressed();
        assertEquals("Calendar", a.screen);
        assertEquals(1, a.ui.navigation.selected());
        a.navigate("Stats");
        assertEquals(3, a.timerPanel.sub);
      });
      scenario.recreate();
      scenario.onActivity(a -> {
        assertEquals("Timer", a.screen);
        assertEquals(3, a.timerPanel.sub);
        a.getOnBackPressedDispatcher().onBackPressed();
        a.getOnBackPressedDispatcher().onBackPressed();
        assertEquals("Calendar", a.screen);
      });
    }
  }

  @Test public void leavingLockInRequiresConfirmationAndPreservesRemainingTime() throws Exception {
    try (ActivityScenario<MainActivity> scenario = launch()) {
      scenario.onActivity(a -> a.navigate("Timer"));
      InstrumentationRegistry.getInstrumentation().waitForIdleSync();
      scenario.onActivity(a -> {
        a.timerPanel.timer.p.edit().putInt("secs", 1410).commit();
        a.timerPanel.ui.lock.setChecked(true);
        a.timerPanel.timer.start("Lock test", "", 1500);
        assertEquals(View.GONE, a.ui.navigation.getVisibility());
        a.getOnBackPressedDispatcher().onBackPressed();
      });
      UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
      assertTrue(device.wait(Until.hasObject(By.res("android", "button2")), 5000));
      device.findObject(By.res("android", "button2")).click();
      scenario.onActivity(a -> {
        assertTrue(a.timerPanel.timer.running());
        a.getOnBackPressedDispatcher().onBackPressed();
      });
      assertTrue(device.wait(Until.hasObject(By.res("android", "button1")), 5000));
      device.findObject(By.res("android", "button1")).click();
      InstrumentationRegistry.getInstrumentation().waitForIdleSync();
      scenario.onActivity(a -> {
        assertEquals("Next Up", a.screen);
        assertFalse(a.timerPanel.timer.running());
        assertFalse(a.timerPanel.ui.lock.isChecked());
        assertTrue(a.timerPanel.timer.secs() <= 1410);
        assertTrue(a.timerPanel.timer.secs() > 1390);
        assertEquals(View.VISIBLE, a.ui.navigation.getVisibility());
      });
    }
  }

  @Test public void timerNotificationRouteUsesTheExistingMainActivity() {
    try (ActivityScenario<MainActivity> scenario = launch()) {
      scenario.onActivity(a -> {
        a.navigate("Grades");
        a.onNewIntent(new Intent(a, MainActivity.class).putExtra("screen", "Timer"));
        assertEquals("Timer", a.screen);
        assertEquals(3, a.ui.navigation.selected());
        a.getOnBackPressedDispatcher().onBackPressed();
        assertEquals("Grades", a.screen);
      });
    }
  }

  @Test public void recreationKeepsTheCurrentTabAfterAnInitialTimerIntent() {
    try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(
        new Intent(context, MainActivity.class).putExtra("screen", "Timer"))) {
      scenario.onActivity(a -> a.navigate("Calendar"));
      scenario.recreate();
      scenario.onActivity(a -> {
        assertEquals("Calendar", a.screen);
        assertEquals(1, a.tab);
        assertEquals(View.GONE, a.ui.timerHost.getVisibility());
      });
    }
  }
}
