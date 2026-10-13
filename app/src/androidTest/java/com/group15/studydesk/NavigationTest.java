package com.group15.studydesk;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class NavigationTest {
  private ActivityScenario<MainActivity> launch() {
    Context context = ApplicationProvider.getApplicationContext();
    context.getSharedPreferences("settings", 0).edit().putBoolean("onboarded", true).commit();
    return ActivityScenario.launch(
        new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  }

  private void clickTab(MainActivity activity, int index, String screen) {
    assertTrue(activity.ui.navigation.getChildAt(index).performClick());
    assertEquals(screen, activity.screen);
    assertEquals(index, activity.ui.navigation.selected());
  }

  @Test
  public void studyCanBeReopenedAfterOtherBottomTabs() {
    try (ActivityScenario<MainActivity> scenario = launch()) {
      scenario.onActivity(activity -> {
        for (int index : new int[] {1, 2, 1, 2}) {
          clickTab(activity, index, index == 1 ? "Plan" : "Grades");
          clickTab(activity, 0, "Next Up");
          assertEquals("Next Up", activity.ui.heading.getText().toString());
          assertEquals(View.GONE, activity.ui.subTabs.getVisibility());
        }
      });
    }
  }

  @Test
  public void studyCanBeReopenedAfterSubTabsAndIntentRoutes() {
    try (ActivityScenario<MainActivity> scenario = launch()) {
      scenario.onActivity(activity -> {
        for (int sub : new int[] {1, 2}) {
          clickTab(activity, 1, "Plan");
          activity.ui.subTabs.getChildAt(sub).performClick();
          assertEquals(sub == 1 ? "Calendar" : "Timetable", activity.screen);
          clickTab(activity, 0, "Next Up");
        }
        clickTab(activity, 2, "Grades");
        activity.ui.subTabs.getChildAt(1).performClick();
        assertEquals("Analytics", activity.screen);
        clickTab(activity, 0, "Next Up");
        activity.navigate("Calendar");
        assertEquals(1, activity.ui.navigation.selected());
        clickTab(activity, 0, "Next Up");
      });
    }
  }

  @Test
  public void studyCanBeReopenedFromMoreWhileStudyWasSelected() {
    try (ActivityScenario<MainActivity> scenario = launch()) {
      scenario.onActivity(activity -> {
        for (String screen : MainActivity.MORE_SCREENS) {
          activity.show(screen);
          assertEquals(-1, activity.ui.navigation.selected());
          clickTab(activity, 0, "Next Up");
          assertEquals("", activity.overflow);
        }
      });
    }
  }
}
