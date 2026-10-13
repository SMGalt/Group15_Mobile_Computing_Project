package com.group15.studydesk;

import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.junit.Assert.*;

import android.content.*;
import android.os.*;
import androidx.test.core.app.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.*;
import java.io.*;
import java.time.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

@RunWith(AndroidJUnit4.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class DeviceFlowsTest {
  Context context;
  Repository repo;
  UiDevice device;

  @Before
  public void before() throws Exception {
    context = ApplicationProvider.getApplicationContext();
    repo = new Repository(context);
    device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    Repository.IO
        .submit(
            () -> {
              repo.db.records().clear("guest");
            })
        .get();
    context
        .getSharedPreferences("settings", 0)
        .edit()
        .clear()
        .putBoolean("onboarded", true)
        .putBoolean("notifications", false)
        .commit();
    context.getSharedPreferences("timer", 0).edit().clear().commit();
  }

  void settle() throws Exception {
    Repository.IO.submit(() -> {}).get();
    InstrumentationRegistry.getInstrumentation().waitForIdleSync();
  }

  void shot(String name) {
    File dir = new File(context.getExternalFilesDir(null), "screenshots");
    dir.mkdirs();
    assertTrue(device.takeScreenshot(new File(dir, name + ".png")));
  }

  void text(String label, String value) {
    onView(withContentDescription(label))
        .perform(
            scrollTo(),
            replaceText(value),
            androidx.test.espresso.action.ViewActions.closeSoftKeyboard());
  }

  Row create(String kind, Map<String, String> fields) throws Exception {
    Intent intent =
        new Intent(context, EditorActivity.class)
            .putExtra("kind", kind)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    try (ActivityScenario<EditorActivity> s = ActivityScenario.launch(intent)) {
      for (Map.Entry<String, String> e : fields.entrySet()) text(e.getKey(), e.getValue());
      onView(withId(R.id.save)).perform(click());
      settle();
    }
    return Repository.IO
        .submit(() -> Rules.kind(repo.all(), kind).get(Rules.kind(repo.all(), kind).size() - 1))
        .get();
  }

  void chooseCourse(String id) {
    /* Spinner selection by visible text in the actual native dropdown. */
    onView(withContentDescription("Course")).perform(scrollTo(), click());
    onData(org.hamcrest.Matchers.anything()).atPosition(1).perform(click());
  }

  @Test
  public void test01CoreFlowThroughNativeFormsAndRestart() throws Exception {
    Row course = create("course", Map.of("Course name", "Mobile Computing", "Credits", "5"));
    Intent intent =
        new Intent(context, EditorActivity.class)
            .putExtra("kind", "assignment")
            .putExtra("course", course.id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    Row assignment;
    try (ActivityScenario<EditorActivity> s = ActivityScenario.launch(intent)) {
      text("Assignment title", "Lab report");
      text("Due date", LocalDate.now().plusDays(1).toString());
      onView(withId(R.id.save)).perform(click());
      settle();
    }
    assignment = Repository.IO.submit(() -> Rules.kind(repo.all(), "assignment").get(0)).get();
    assertEquals(course.id, assignment.course());
    assertFalse(assignment.b("done"));
    try (ActivityScenario<MainActivity> s =
        ActivityScenario.launch(
            new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) {
      assertTrue(device.wait(Until.hasObject(By.text("Lab report")), 5000));
      shot("01-next-up");
      onView(withText("Mark done")).perform(click());
      settle();
      assertTrue(device.wait(Until.hasObject(By.text("Add a deadline")), 5000));
    }
    try (ActivityScenario<MainActivity> s =
        ActivityScenario.launch(
            new Intent(context, MainActivity.class)
                .putExtra("screen", "Plan")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) {
      assertTrue(device.wait(Until.hasObject(By.text("Mobile Computing")), 5000));
      device.findObject(By.text("Mobile Computing")).click();
      assertTrue(device.wait(Until.hasObject(By.text("Lab report")), 5000));
      assertTrue(device.hasObject(By.text("Undo")));
      shot("02-completed-after-relaunch");
      device.findObject(By.text("Undo")).click();
      settle();
      assertFalse(
          Repository.IO.submit(() -> repo.find("assignment", assignment.id).b("done")).get());
    }
    try (ActivityScenario<EditorActivity> s =
        ActivityScenario.launch(
            new Intent(context, EditorActivity.class)
                .putExtra("kind", "assignment")
                .putExtra("id", assignment.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) {
      text("Assignment title", "Revised lab report");
      onView(withId(R.id.save)).perform(click());
      settle();
    }
    assertEquals(
        "Revised lab report",
        Repository.IO.submit(() -> repo.find("assignment", assignment.id).title()).get());
    Repository.IO
        .submit(
            () -> {
              Row a = repo.find("assignment", assignment.id);
              repo.delete(a);
              repo.changed();
            })
        .get();
    assertFalse(Repository.IO.submit(() -> repo.find("assignment", assignment.id).live()).get());
  }

  @Test
  public void test02InputValidationAndCourseCascade() throws Exception {
    try (ActivityScenario<EditorActivity> s =
        ActivityScenario.launch(
            new Intent(context, EditorActivity.class)
                .putExtra("kind", "course")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) {
      text("Course name", "");
      onView(withId(R.id.save)).perform(click());
      settle();
      assertTrue(Repository.IO.submit(() -> Rules.kind(repo.all(), "course").isEmpty()).get());
      text("Course name", "Graphics");
      onView(withId(R.id.save)).perform(click());
      settle();
    }
    Row c = Repository.IO.submit(() -> Rules.kind(repo.all(), "course").get(0)).get();
    Repository.IO
        .submit(
            () -> {
              repo.save(
                  new Row("assignment")
                      .put("courseId", c.id)
                      .put("title", "Exercise")
                      .put("dueDate", LocalDate.now().toString()));
              repo.save(
                  new Row("exam")
                      .put("courseId", c.id)
                      .put("title", "Exam")
                      .put("dueDate", LocalDate.now().toString()));
              repo.save(new Row("grade").put("subjectId", c.id).put("grade", 6).put("weight", 1));
              repo.delete(c);
            })
        .get();
    assertEquals(
        0,
        Repository.IO.submit(() -> Rules.kind(repo.all(), "assignment").size()).get().intValue());
    assertEquals(
        0, Repository.IO.submit(() -> Rules.kind(repo.all(), "exam").size()).get().intValue());
    assertEquals(
        0, Repository.IO.submit(() -> Rules.kind(repo.all(), "grade").size()).get().intValue());
  }

  @Test
  public void test03CalendarNavigationAndTimetable() throws Exception {
    Row term =
        create(
            "term",
            Map.of("Name", "Autumn 2026", "Start date", "2026-09-01", "End date", "2026-12-31"));
    Repository.IO
        .submit(
            () -> {
              repo.save(
                  new Row("lesson")
                      .put("termId", term.id)
                      .put("title", "Mobile Computing lecture")
                      .put("weekday", LocalDate.now().getDayOfWeek().getValue() % 7)
                      .put("startsAt", "09:00")
                      .put("endsAt", "10:00")
                      .put("room", "B101"));
            })
        .get();
    try (ActivityScenario<MainActivity> s =
        ActivityScenario.launch(
            new Intent(context, MainActivity.class)
                .putExtra("screen", "Calendar")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) {
      assertTrue(device.wait(Until.hasObject(By.text("Mobile Computing lecture")), 5000));
      if (context.getResources().getConfiguration().screenHeightDp >= 500) {
        String month =
            LocalDate.now()
                .format(
                    java.time.format.DateTimeFormatter.ofPattern(
                        "MMMM yyyy", java.util.Locale.ENGLISH));
        assertTrue(device.wait(Until.hasObject(By.text(month)), 5000));
        device.findObject(By.descStartsWith(LocalDate.now().plusDays(1).toString())).click();
        onView(withId(R.id.dateButton))
            .check(
                androidx.test.espresso.assertion.ViewAssertions.matches(
                    withText(LocalDate.now().plusDays(1).toString())));
        onView(withId(R.id.previous)).perform(click());
      }
      shot("03-calendar");
      onView(withId(R.id.next)).perform(click());
      onView(withId(R.id.dateButton))
          .check(
              androidx.test.espresso.assertion.ViewAssertions.matches(
                  withText(LocalDate.now().plusDays(1).toString())));
      onView(withId(R.id.previous)).perform(click());
      onView(withId(R.id.dateButton))
          .check(
              androidx.test.espresso.assertion.ViewAssertions.matches(
                  withText(LocalDate.now().toString())));
    }
    try (ActivityScenario<MainActivity> s =
        ActivityScenario.launch(
            new Intent(context, MainActivity.class)
                .putExtra("screen", "Timetable")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) {
      shot("04-timetable");
      onView(withId(R.id.next)).perform(click());
      onView(withId(R.id.dateButton))
          .check(
              androidx.test.espresso.assertion.ViewAssertions.matches(
                  withText(LocalDate.now().plusDays(7).toString())));
    }
  }

  @Test
  public void test04TimerBackgroundRestoreAndSave() throws Exception {
    try (ActivityScenario<MainActivity> s = ActivityScenario.launch(new Intent(context, MainActivity.class).putExtra("screen", "Timer"))) {
      onView(withId(R.id.start)).perform(scrollTo(), click());
      settle();
      long before = new TimerState(context).secs();
      device.pressHome();
      Thread.sleep(2200);
      context.startActivity(
          new Intent(context, MainActivity.class).putExtra("screen", "Timer")
              .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
      assertTrue(device.wait(Until.hasObject(By.text("Pause")), 5000));
      assertTrue(new TimerState(context).secs() <= before - 2);
      shot("05-timer-restored");
      shot("timer-before-pause");
      device.findObject(By.text("Pause")).click();
      settle();
      shot("timer-after-pause");
      assertFalse("Timer did not pause after button tap", new TimerState(context).running());
      long paused = new TimerState(context).secs();
      Thread.sleep(1100);
      assertEquals(paused, new TimerState(context).secs());
      context
          .getSharedPreferences("timer", 0)
          .edit()
          .putInt("secs", 1410)
          .putLong("sessionStart", System.currentTimeMillis() - 90000)
          .commit();
      onView(withId(R.id.saveSession)).perform(scrollTo(), click());
      assertTrue(device.wait(Until.hasObject(By.textContains("Save study session")), 5000));
      onView(withId(android.R.id.button1)).perform(click());
      settle();
      assertEquals(
          1, Repository.IO.submit(() -> Rules.kind(repo.all(), "session").size()).get().intValue());
    }
  }

  @Test
  public void test05NotebookAutosaveAndRestart() throws Exception {
    Row note =
        create(
            "note",
            Map.of(
                "Note title",
                "Lecture notes",
                "Note content",
                "# Revision\n- [ ] Review chapter\n**Key idea**"));
    try (ActivityScenario<EditorActivity> s =
        ActivityScenario.launch(
            new Intent(context, EditorActivity.class)
                .putExtra("kind", "note")
                .putExtra("id", note.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) {
      text("Note content", "# Revision\n- [x] Review chapter\n**Saved automatically**");
      Thread.sleep(1200);
      settle();
      shot("06-notebook-editor");
    }
    assertTrue(
        Repository.IO
            .submit(() -> repo.find("note", note.id).s("content").contains("Saved automatically"))
            .get());
  }

  @Test
  public void test06NotificationDenialRebuildAndDoneAction() throws Exception {
    assertFalse(
        "Run with notification permission denied before instrumentation",
        Reminders.allowed(context));
    context.getSharedPreferences("settings", 0).edit().putBoolean("notifications", true).commit();
    Row a =
        new Row("assignment")
            .put("title", "Reminder test")
            .put("dueDate", LocalDate.now().plusDays(2).toString());
    Repository.IO
        .submit(
            () -> {
              repo.save(a);
              repo.changed();
            })
        .get();
    assertTrue(context.getSharedPreferences("alarms", 0).getStringSet("ids", Set.of()).isEmpty());
    if (Build.VERSION.SDK_INT >= 33)
      InstrumentationRegistry.getInstrumentation()
          .getUiAutomation()
          .grantRuntimePermission(
              context.getPackageName(), "android.permission.POST_NOTIFICATIONS");
    Repository.IO.submit(() -> repo.changed()).get();
    assertFalse(context.getSharedPreferences("alarms", 0).getStringSet("ids", Set.of()).isEmpty());
    context.sendBroadcast(
        new Intent(context, ReminderReceiver.class)
            .setAction("mark_done")
            .putExtra("row", a.id)
            .putExtra("owner", "guest")
            .putExtra("notificationId", 1000));
    Thread.sleep(500);
    settle();
    assertTrue(Repository.IO.submit(() -> repo.find("assignment", a.id).b("done")).get());
    assertTrue(context.getSharedPreferences("alarms", 0).getStringSet("ids", Set.of()).isEmpty());
  }

  @Test
  public void test07BackupRoundTripAndNoSecrets() throws Exception {
    Row course = create("course", Map.of("Course name", "Backup course"));
    File backup = new File(context.getCacheDir(), "test-backup.zip");
    new SecureStore(context).put("cloudKey", "SENSITIVE_TEST_ONLY");
    Repository.IO
        .submit(
            () -> {
              try {
                Backup.exportTo(context, repo, android.net.Uri.fromFile(backup));
                repo.db.records().clear("guest");
                Backup.importFrom(context, repo, android.net.Uri.fromFile(backup));
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            })
        .get();
    assertEquals(
        course.title(), Repository.IO.submit(() -> repo.find("course", course.id).title()).get());
    try (java.util.zip.ZipInputStream zip =
        new java.util.zip.ZipInputStream(new FileInputStream(backup))) {
      zip.getNextEntry();
      String text = new String(IOUtil.read(zip), java.nio.charset.StandardCharsets.UTF_8);
      assertFalse(text.contains("SENSITIVE_TEST_ONLY"));
      assertFalse(text.contains("access_token"));
    }
    new SecureStore(context).put("cloudKey", null);
  }

  @Test
  public void test08DarkLandscapeAndStats() throws Exception {
    context.getSharedPreferences("settings", 0).edit().putString("mode", "dark").commit();
    Repository.IO
        .submit(
            () -> {
              repo.save(
                  new Row("session")
                      .put("startedAt", Instant.now().toString())
                      .put("durationMinutes", 60)
                      .put("notes", "Revision"));
            })
        .get();
    // Stats is a sub-tab of the Timer hub now, not a desk screen — the original reaches it as
    // Timer → Stats, so this launches the hub directly rather than a MainActivity "screen".
    try (ActivityScenario<MainActivity> s =
        ActivityScenario.launch(
            new Intent(context, MainActivity.class).putExtra("screen", "Timer")
                .putExtra("sub", "Stats")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) {
      assertTrue(device.wait(Until.hasObject(By.textContains("1.0 hours")), 5000));
      shot("07-dark-stats");
      s.onActivity(
          a ->
              a.setRequestedOrientation(
                  android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
      Thread.sleep(500);
      shot("08-landscape-stats");
      s.onActivity(
          a ->
              a.setRequestedOrientation(
                  android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
    }
  }
}
