package com.group15.studydesk;

import static org.junit.Assert.*;

import android.appwidget.*;
import android.content.*;
import android.os.*;
import android.widget.*;
import androidx.test.core.app.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.*;
import java.io.*;
import java.time.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class VisualAndWidgetTest {
  Context c;
  Repository repo;
  UiDevice device;

  @Before
  public void start() throws Exception {
    c = ApplicationProvider.getApplicationContext();
    repo = new Repository(c);
    device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    c.getSharedPreferences("settings", 0)
        .edit()
        .clear()
        .putBoolean("onboarded", true)
        .putBoolean("notifications", false)
        .commit();
    Repository.IO
        .submit(
            () -> {
              repo.db.records().clear("guest");
              DemoData.load(repo);
            })
        .get();
  }

  void shot(String name) {
    File dir = new File(c.getExternalFilesDir(null), "screenshots");
    dir.mkdirs();
    assertTrue(device.takeScreenshot(new File(dir, name + ".png")));
  }

  /**
   * Each desk screen still renders in all three palettes.
   *
   * <p>The expected heading is the TAB's name, not the legacy screen name. The original keeps "Plan"
   * on screen across List, Calendar and Timetable and the port now does the same, so Calendar and
   * Timetable are expected to read "Plan" — asserting the old flat label here is what made this test
   * encode the wrong navigation.
   */
  @Test
  public void sampleScreensRenderInLightDarkAndBlack() throws Exception {
    String[][] screens = {
      {"Next Up", "Next Up"},
      {"Plan", "Plan"},
      {"Calendar", "Plan"},
      {"Timetable", "Plan"},
      {"Grades", "Grades"},
    };
    for (String mode : new String[] {"light", "dark", "black"}) {
      c.getSharedPreferences("settings", 0).edit().putString("mode", mode).commit();
      for (String[] screen : screens) {
        try (ActivityScenario<MainActivity> scenario =
            ActivityScenario.launch(
                new Intent(c, MainActivity.class)
                    .putExtra("screen", screen[0])
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) {
          assertTrue(
              device.wait(
                  Until.hasObject(By.res(c.getPackageName(), "heading").text(screen[1])), 5000));
          device.waitForIdle();
          shot("gallery-" + mode + "-" + screen[0].replace(' ', '-'));
        }
      }
    }
  }

  /**
   * The desk opens on the original's four tabs, in the original's order, with the original's labels.
   *
   * <p>This is the assertion the old suite was missing. It had no coverage of the bottom bar at all,
   * which is how the port ended up shipping five tabs — including Calendar and Notebook, which the
   * original deliberately keeps OUT of the mobile bar (`railOnly`, and a Plan and a Timer sub-tab
   * respectively).
   */
  @Test
  public void bottomBarIsTheOriginalsFourTabsInOrder() throws Exception {
    try (ActivityScenario<MainActivity> scenario =
        ActivityScenario.launch(
            new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) {
      device.waitForIdle();
      // Read the strip's children rather than matching the container: the container carries no text
      // of its own, the labels are on the buttons inside it.
      UiObject2 bar =
          device.wait(Until.findObject(By.res(c.getPackageName(), "navigation")), 5000);
      assertNotNull("no bottom bar", bar);
      List<String> labels = new ArrayList<>();
      for (UiObject2 child : bar.getChildren()) labels.add(child.getText());
      assertEquals(
          "bottom bar must be the original's four tabs, in its order",
          Arrays.asList("Study", "Plan", "Grades", "Timer"),
          labels);
      shot("gallery-bottom-bar");
    }
  }

  /**
   * Plan and Grades each carry the original's sub-tab strip, and switching one changes the body
   * without changing the heading — the original keeps "Plan" on screen across all three.
   */
  @Test
  public void subTabsSwitchTheBodyAndNotTheHeading() throws Exception {
    try (ActivityScenario<MainActivity> scenario =
        ActivityScenario.launch(
            new Intent(c, MainActivity.class)
                .putExtra("screen", "Plan")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) {
      device.waitForIdle();
      UiObject2 strip = device.wait(Until.findObject(By.res(c.getPackageName(), "subTabs")), 5000);
      assertNotNull("Plan has no sub-tab strip", strip);
      List<String> tabs = new ArrayList<>();
      for (UiObject2 child : strip.getChildren()) tabs.add(child.getText());
      assertEquals(Arrays.asList("LIST", "CALENDAR", "TIMETABLE"), tabs);

      strip.getChildren().get(1).click();
      device.waitForIdle();
      assertTrue(
          "heading must still read Plan on the Calendar sub-tab",
          device.wait(
              Until.hasObject(By.res(c.getPackageName(), "heading").text("Plan")), 5000));
      assertTrue(
          "the Calendar sub-tab shows the month grid",
          device.wait(Until.hasObject(By.res(c.getPackageName(), "calendar")), 5000));
      shot("gallery-plan-calendar-subtab");
    }
  }

  @Test
  public void widgetRemoteViewsRenderAndTapTheirOwnRoute() throws Exception {
    android.app.UiAutomation automation =
        InstrumentationRegistry.getInstrumentation().getUiAutomation();
    try (ParcelFileDescriptor descriptor =
        automation.executeShellCommand(
            "cmd appwidget grantbind --package " + c.getPackageName() + " --user 0")) {
      IOUtil.read(new FileInputStream(descriptor.getFileDescriptor()));
    }
    AppWidgetHost host = new AppWidgetHost(c, 1517);
    host.startListening();
    AppWidgetManager manager = AppWidgetManager.getInstance(c);
    try (ActivityScenario<WidgetPreviewActivity> scenario =
        ActivityScenario.launch(WidgetPreviewActivity.class)) {
      for (Class<?> provider : new Class<?>[] {DeskWidget.class, UpcomingWidget.class}) {
        c.startActivity(
            new Intent(c, WidgetPreviewActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
        device.waitForIdle();
        int id = host.allocateAppWidgetId();
        ComponentName component = new ComponentName(c, provider);
        assertTrue(manager.bindAppWidgetIdIfAllowed(id, component));
        AppWidgetProviderInfo info = manager.getAppWidgetInfo(id);
        assertNotNull(info);
        scenario.onActivity(
            a -> {
              AppWidgetHostView view = host.createView(a, id, info);
              a.root.removeAllViews();
              a.root.addView(view, new android.widget.FrameLayout.LayoutParams(-1, 600));
            });
        Repository.IO.submit(() -> DeskWidget.refresh(c)).get();
        String title = "StudyDesk · " + (provider == DeskWidget.class ? "Next Up" : "Upcoming");
        assertTrue(device.wait(Until.hasObject(By.text(title)), 5000));
        shot("widget-" + provider.getSimpleName());
        device.findObject(By.text(title)).click();
        assertTrue(
            device.wait(
                Until.hasObject(
                    By.res(c.getPackageName(), "heading")
                        .text(provider == DeskWidget.class ? "Next Up" : "Plan")),
                5000));
        host.deleteAppWidgetId(id);
      }
    } finally {
      host.stopListening();
      host.deleteHost();
      try (ParcelFileDescriptor descriptor =
          automation.executeShellCommand(
              "cmd appwidget revokebind --package " + c.getPackageName() + " --user 0")) {
        IOUtil.read(new FileInputStream(descriptor.getFileDescriptor()));
      }
    }
  }

  @Test
  /**
   * Capture the upstream Capacitor build's auth screen, when that build is installed.
   *
   * <p>This is a reference grab, not a test of this app — it only proves the port has not taken over
   * the original's package name. Skipped rather than failed when the baseline is absent, which is
   * the normal case on a machine that only builds the port; and the port no longer has an auth
   * screen of its own to compare against.
   */
  public void originalAndroidBaselineCanLaunchWithoutOurIdentity() throws Exception {
    Intent probe =
        new Intent()
            .setComponent(new ComponentName("com.StudyDesk.app", "com.StudyDesk.app.MainActivity"));
    org.junit.Assume.assumeTrue(
        "upstream com.StudyDesk.app is not installed on this device",
        c.getPackageManager().resolveActivity(probe, 0) != null);
    c.startActivity(probe.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    assertTrue(device.wait(Until.hasObject(By.pkg("com.StudyDesk.app")), 10000));
    device.wait(Until.hasObject(By.textContains("访客")), 5000);
    shot("upstream-android-auth");
    File dir = new File(c.getExternalFilesDir(null), "screenshots");
    device.dumpWindowHierarchy(new File(dir, "upstream-android-ui.xml"));
    device.pressHome();
  }
}
