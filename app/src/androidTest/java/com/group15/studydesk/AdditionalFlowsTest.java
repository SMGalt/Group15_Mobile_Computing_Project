package com.group15.studydesk;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.junit.Assert.*;

import android.content.*;
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
public class AdditionalFlowsTest {
  Context c;
  Repository repo;
  UiDevice device;

  @Before
  public void setup() throws Exception {
    c = ApplicationProvider.getApplicationContext();
    repo = new Repository(c);
    device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    Repository.IO.submit(() -> repo.db.records().clear("guest")).get();
    c.getSharedPreferences("settings", 0)
        .edit()
        .clear()
        .putBoolean("onboarded", true)
        .putBoolean("notifications", false)
        .commit();
  }

  void settle() throws Exception {
    Repository.IO.submit(() -> {}).get();
    InstrumentationRegistry.getInstrumentation().waitForIdleSync();
  }

  void shot(String name) {
    File dir = new File(c.getExternalFilesDir(null), "screenshots");
    dir.mkdirs();
    assertTrue(device.takeScreenshot(new File(dir, name + ".png")));
  }

  @Test
  public void topicsAreEditableAndSurviveExamRename() throws Exception {
    Row course = new Row("course").put("name", "Mobile Computing").put("credits", 5);
    Row exam =
        new Row("exam")
            .put("courseId", course.id)
            .put("title", "Midterm quiz")
            .put("dueDate", LocalDate.now().plusDays(7).toString())
            .put("difficulty", "medium");
    Repository.IO
        .submit(
            () -> {
              repo.save(course);
              repo.save(exam);
            })
        .get();
    try (ActivityScenario<MainActivity> s =
        ActivityScenario.launch(
            new Intent(c, MainActivity.class)
                .putExtra("screen", "Next Up")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) {
      assertTrue(device.wait(Until.hasObject(By.text("Midterm quiz")), 5000));
      onView(withId(R.id.more)).perform(click());
      onView(withText("Topics"))
          .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog())
          .perform(click());
      onView(withId(android.R.id.button1))
          .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog())
          .perform(click());
      onView(
              org.hamcrest.Matchers.allOf(
                  withClassName(org.hamcrest.Matchers.is(EditText.class.getName())), isDisplayed()))
          .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog())
          .perform(replaceText("TCP protocol"), closeSoftKeyboard());
      onView(withId(android.R.id.button1))
          .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog())
          .perform(click());
      settle();
      assertEquals(
          1,
          Repository.IO
              .submit(() -> repo.find("exam", exam.id).json().optJSONArray("topics").length())
              .get()
              .intValue());
      onView(withId(R.id.more)).perform(click());
      onView(withText("Topics"))
          .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog())
          .perform(click());
      onView(withText("TCP protocol"))
          .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog())
          .perform(click());
      settle();
      assertTrue(
          Repository.IO
              .submit(
                  () ->
                      repo.find("exam", exam.id)
                          .json()
                          .optJSONArray("topics")
                          .optJSONObject(0)
                          .optBoolean("done"))
              .get());
      shot("09-exam-topics");
      device.pressBack();
    }
    try (ActivityScenario<EditorActivity> s =
        ActivityScenario.launch(
            new Intent(c, EditorActivity.class)
                .putExtra("kind", "exam")
                .putExtra("id", exam.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) {
      onView(withContentDescription("Exam title"))
          .perform(scrollTo(), replaceText("Revised quiz"), closeSoftKeyboard());
      onView(withId(R.id.save)).perform(click());
      settle();
    }
    assertTrue(
        Repository.IO
            .submit(
                () ->
                    repo.find("exam", exam.id)
                        .json()
                        .optJSONArray("topics")
                        .optJSONObject(0)
                        .optBoolean("done"))
            .get());
  }

  @Test
  public void repeatingPlansRemainSeparateFromActualStudy() throws Exception {
    try (ActivityScenario<EditorActivity> s =
        ActivityScenario.launch(
            new Intent(c, EditorActivity.class)
                .putExtra("kind", "planned")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) {
      onView(withContentDescription("Study plan"))
          .perform(scrollTo(), replaceText("Weekly revision"), closeSoftKeyboard());
      onView(withContentDescription("Number of occurrences (0 = term horizon)"))
          .perform(scrollTo(), replaceText("3"), closeSoftKeyboard());
      onView(withId(R.id.save)).perform(click());
      settle();
    }
    List<Row> plans = Repository.IO.submit(() -> Rules.kind(repo.all(), "planned")).get();
    assertEquals(3, plans.size());
    assertFalse(plans.get(0).s("seriesId").isEmpty());
    assertEquals(plans.get(0).s("seriesId"), plans.get(1).s("seriesId"));
    assertEquals(
        0, Repository.IO.submit(() -> Rules.kind(repo.all(), "session").size()).get().intValue());
    try (ActivityScenario<MainActivity> s =
        ActivityScenario.launch(
            new Intent(c, MainActivity.class)
                .putExtra("screen", "Study plans")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) {
      assertTrue(device.wait(Until.hasObject(By.text("Weekly revision")), 5000));
      shot("10-repeating-plans");
    }
  }

  @Test
  public void notebookCanvasRendersMathChemAndDraggableBoxes() throws Exception {
    Row note =
        new Row("note")
            .put("title", "Science revision")
            .put(
                "content",
                "# Revision\n"
                    + "**Important** and ==highlight==\n"
                    + "$$\\frac{a+b}{2}$$\n"
                    + "$$2H2 + O2 -> 2H2O$$\n"
                    + "- [ ] Review chapter\n"
                    + "A -> B -> C");
    Repository.IO.submit(() -> repo.save(note)).get();
    try (ActivityScenario<NotebookActivity> s =
        ActivityScenario.launch(
            new Intent(c, NotebookActivity.class)
                .putExtra("id", note.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) {
      assertTrue(device.wait(Until.hasObject(By.desc("Edit text box")), 5000));
      shot("11-notebook-native");
      device.findObject(By.desc("Edit text box")).click();
      onView(withContentDescription("Text box source"))
          .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog())
          .perform(
              replaceText("# Updated\n$$x^2 + y_1$$\n- [x] Review chapter"), closeSoftKeyboard());
      Thread.sleep(900);
      settle();
      assertTrue(
          Repository.IO
              .submit(() -> repo.find("note", note.id).s("content").contains("# Updated"))
              .get());
      onView(withId(android.R.id.button1))
          .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog())
          .perform(click());
      settle();
      shot("12-notebook-saved");
    }
    Row stored = Repository.IO.submit(() -> repo.find("note", note.id)).get();
    assertNotNull(stored.json().optJSONObject("layout"));
    assertEquals(
        stored.s("content"),
        NoteLayout.join(NoteLayout.read(stored.s("content"), stored.json().opt("layout"))));
  }

  @Test
  public void lockInAndStopwatchUseRealControls() throws Exception {
    c.getSharedPreferences("timer", 0).edit().clear().commit();
    try (ActivityScenario<MainActivity> s = ActivityScenario.launch(new Intent(c, MainActivity.class).putExtra("screen", "Timer"))) {
      onView(withId(R.id.mode)).perform(scrollTo(), click());
      onView(withId(R.id.start)).perform(scrollTo(), click());
      Thread.sleep(1100);
      assertTrue(new TimerState(c).stopwatch());
      assertTrue(new TimerState(c).secs() >= 1);
      onView(withId(R.id.start)).perform(scrollTo(), click());
      onView(withId(R.id.mode)).perform(scrollTo(), click());
      onView(withId(R.id.lock)).perform(scrollTo(), click());
      assertTrue(new TimerState(c).p.getBoolean("lock", false));
      device.waitForIdle();
      // Android can show its first-use immersive-mode explanation above the app.
      UiObject2 gotIt = device.wait(
          Until.findObject(By.text(java.util.regex.Pattern.compile("(?i)got it"))), 1500);
      if (gotIt != null) gotIt.click();
      shot("13-lock-in");
      onView(withId(R.id.lock)).perform(scrollTo(), click());
      assertFalse(new TimerState(c).p.getBoolean("lock", false));
    }
  }

  @Test
  public void cloudOutboxAndAccountIsolationRemainDurable() throws Exception {
    Row r =
        new Row("assignment")
            .put("title", "Offline work")
            .put("dueDate", LocalDate.now().plusDays(1).toString());
    Repository.IO
        .submit(
            () -> {
              repo.save(r);
              r.put("title", "Offline revised");
              repo.save(r);
            })
        .get();
    assertEquals(
        1, Repository.IO.submit(() -> repo.db.records().pending("guest").size()).get().intValue());
    assertEquals(
        "Offline revised",
        Repository.IO.submit(() -> repo.db.records().pending("guest").get(0).title()).get());
    Repository.IO.submit(() -> repo.delete(r)).get();
    assertNotNull(
        Repository.IO.submit(() -> repo.db.records().pending("guest").get(0).deletedAt).get());
    c.getSharedPreferences("session", 0).edit().putString("owner", "fixture-other-user").commit();
    assertEquals(0, Repository.IO.submit(() -> repo.all().size()).get().intValue());
    c.getSharedPreferences("session", 0).edit().clear().commit();
  }
}
