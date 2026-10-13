package com.group15.studydesk;

import android.content.*;
import android.os.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import com.group15.studydesk.databinding.*;
import java.time.*;
import java.time.format.*;
import java.time.temporal.*;
import java.util.*;
import org.json.*;

public class MainActivity extends BaseActivity implements CardAdapter.Actions {
  Row pendingAttachment;
  String observedOwner = "";
  androidx.lifecycle.LiveData<List<Row>> observed;
  ActivityMainBinding ui;
  Repository repo;
  CardAdapter adapter;
  List<Row> rows = new ArrayList<>();
  /**
   * The four bottom tabs, in the original's order and wording. The original's mobile bar is four
   * items and has been since v1.9 — a fifth takes each slot from ~90px to ~72px at 360px wide and
   * the longest shipped label stops fitting. The port had five, which also promoted Calendar and
   * Notebook to first-class destinations they are not.
   *
   * <p>Timer shares this activity and bottom bar. Lock In temporarily hides navigation.
   */
  static final String[] TABS = {"Study", "Plan", "Grades", "Timer"};

  /** TimerPanel renders the timer's sub-tabs; Study has none. */
  static final String[][] SUBS = {
    {}, {"List", "Calendar", "Timetable"}, {"Grades", "Trends"}, {},
  };

  /**
   * The render() branch each (tab, sub) pair shows.
   *
   * <p>These are the same flat labels render() already switches on. Navigation was the thing that
   * was wrong — it offered sixteen flat destinations instead of four tabs with sub-tabs — so the
   * branches themselves are reused rather than rewritten, and this table is the whole mapping.
   */
  static final String[][] BRANCH = {
    {"Next Up"}, {"Plan", "Calendar", "Timetable"}, {"Grades", "Analytics"}, {"Timer"},
  };

  /**
   * Record-kind screens the original has no destination for: it manages terms inside Timetable,
   * creates plans and commitments from the calendar, keeps calendar feeds in Settings and reaches
   * archived courses through a Grades toggle. Folding each into its original home is a larger job
   * than re-homing the navigation, so they stay reachable from one overflow menu rather than being
   * deleted — the data kinds and their editors are unchanged either way.
   */
  static final String[] MORE_SCREENS = {
    "Academic terms", "Study plans", "Commitments", "Attendance", "Calendar feeds", "Archived courses"
  };

  int tab, sub;
  String screen = "Next Up", course = "", filter = "All";
  /** Set while one of {@link #MORE_SCREENS} is showing; those have no tab to light up. */
  String overflow = "";

  /** Which tab's sub-tabs {@link #ui} currently holds, so they are rebuilt only on a tab change. */
  int subTabsFor = -1;
  TimerPanel timerPanel;
  Bundle timerSavedState;
  String timerReturnScreen = "Next Up";
  boolean resumed;

  LocalDate day = LocalDate.now();

  @Override
  protected void onCreate(Bundle state) {
    super.onCreate(state);
    ui = ActivityMainBinding.inflate(getLayoutInflater());
    setContentView(ui.getRoot());
    insets(ui.root);
    repo = new Repository(this);
    adapter = new CardAdapter(this);
    ui.list.setLayoutManager(
        new androidx.recyclerview.widget.GridLayoutManager(
            this, getResources().getConfiguration().screenWidthDp >= 600 ? 2 : 1));
    ui.list.setAdapter(adapter);
    if (state != null) {
      if (state.containsKey("attachmentKind")) {
        pendingAttachment = new Row(state.getString("attachmentKind"));
        pendingAttachment.id = state.getString("attachmentId");
      }
      screen = state.getString("screen", "Next Up");
      course = state.getString("course", "");
      filter = state.getString("filter", "All");
      day = LocalDate.parse(state.getString("day", day.toString()));
      timerSavedState = state.getBundle("timerPanel");
      timerReturnScreen = state.getString("timerReturnScreen", "Next Up");
      boolean found = false;
      for (int t = 0; t < BRANCH.length; t++)
        for (int s = 0; s < BRANCH[t].length; s++)
          if (BRANCH[t][s].equals(screen)) {
            tab = t;
            sub = s;
            found = true;
          }
      if (!found) overflow = screen;
    }
    // No Account item, and no sign-in: the desk is local and this build opens straight into Study.
    ui.toolbar.getMenu().add(0, 1, 0, tr("More")).setShowAsAction(2);
    ui.toolbar.getMenu().add(0, 2, 0, tr("Settings")).setShowAsAction(0);
    ui.toolbar.getMenu().add(0, 3, 0, tr("Filter"));
    ui.toolbar.setOnMenuItemClickListener(
        item -> {
          switch (item.getItemId()) {
            case 2 -> startActivity(new Intent(this, SettingsActivity.class));
            case 3 -> filters();
            default -> {
              String[] translatedMore = new String[MORE_SCREENS.length];
              for (int i = 0; i < MORE_SCREENS.length; i++) translatedMore[i] = tr(MORE_SCREENS[i]);
              new AlertDialog.Builder(this)
                  .setTitle(tr("Your desk"))
                  .setItems(translatedMore, (d, i) -> show(MORE_SCREENS[i]))
                  .show();
            }
          }
          return true;
        });
    ui.navigation.setBar(true);
    ui.navigation.setTabs(Arrays.asList(TABS), tab, this::openTab);
    ui.add.setOnClickListener(v -> addMenu());
    ui.previous.setOnClickListener(
        v -> {
          day = day.minusDays(screen.equals("Timetable") ? 7 : 1);
          render();
        });
    ui.next.setOnClickListener(
        v -> {
          day = day.plusDays(screen.equals("Timetable") ? 7 : 1);
          render();
        });
    ui.dateButton.setOnClickListener(
        v ->
            new android.app.DatePickerDialog(
                    this,
                    (p, y, m, d) -> {
                      day = LocalDate.of(y, m + 1, d);
                      ui.calendar.setDate(
                          day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli());
                      render();
                    },
                    day.getYear(),
                    day.getMonthValue() - 1,
                    day.getDayOfMonth())
                .show());
    ui.calendar.setOnDateChangeListener(
        (v, y, m, d) -> {
          day = LocalDate.of(y, m + 1, d);
          render();
        });
    ui.search.addTextChangedListener(
        new TextWatcher() {
          public void beforeTextChanged(CharSequence s, int st, int c, int a) {}

          public void onTextChanged(CharSequence s, int st, int before, int count) {
            render();
          }

          public void afterTextChanged(Editable e) {}
        });
    observeOwner();
    if (state == null) handleIntent(getIntent());
    else render();
    Privacy.check(this);
    getOnBackPressedDispatcher().addCallback(this,
        new androidx.activity.OnBackPressedCallback(true) {
          @Override public void handleOnBackPressed() {
            if (tab == 3 && overflow.isEmpty()) {
              if (timerPanel.ui.lock.isChecked()) timerPanel.leave();
              else if (timerPanel.sub != 0) timerPanel.openSub(0);
              else timerPanel.leave();
            } else if (!overflow.isEmpty() || tab != 0 || !course.isEmpty()) {
              place(0, 0);
            } else {
              setEnabled(false);
              getOnBackPressedDispatcher().onBackPressed();
              setEnabled(true);
            }
          }
        });
  }

  void observeOwner() {
    String owner = repo.owner();
    if (!owner.equals(observedOwner)) {
      if (observed != null) observed.removeObservers(this);
      observedOwner = owner;
      observed = repo.observe();
      observed.observe(
          this,
          data -> {
            rows = data;
            render();
          });
    }
  }

  /** All four bottom destinations share the same activity. */
  void openTab(int index) {
    place(index, 0);
  }

  /**
   * Point the desk at a tab and one of its sub-tabs.
   *
   * <p>The single writer of {@link #screen} during navigation, so the flat label every branch of
   * render() switches on can never drift from the tab pair the user is actually on.
   */
  void place(int nextTab, int nextSub) {
    if (nextTab == 3 && (tab != 3 || !overflow.isEmpty())) timerReturnScreen = screen;
    if (tab == 3 && timerPanel != null && nextTab != 3) timerPanel.rememberTask();
    tab = Math.max(0, Math.min(BRANCH.length - 1, nextTab));
    String[] branches = BRANCH[tab];
    sub = branches.length == 0 ? 0 : Math.max(0, Math.min(branches.length - 1, nextSub));
    screen = branches.length == 0 ? "Next Up" : branches[sub];
    overflow = "";
    course = "";
    filter = "All";
    ui.search.setText("");
    render();
  }

  /** Show one of the record screens that has no tab of its own; see {@link #MORE_SCREENS}. */
  void show(String name) {
    overflow = name;
    screen = name;
    course = "";
    filter = "All";
    ui.search.setText("");
    render();
  }

  /**
   * Legacy flat screen names — still the wire format for widget taps, reminder intents, notification
   * payloads and saved instance state. Mapped back onto the four tabs so an old caller lands on the
   * right screen instead of falling through to render()'s default branch.
   */
  void navigate(String legacy) {
    for (int t = 0; t < BRANCH.length; t++)
      for (int s = 0; s < BRANCH[t].length; s++)
        if (BRANCH[t][s].equals(legacy)) {
          place(t, s);
          return;
        }
    // Legacy routes into the timer hub now stay inside the desk.
    if (legacy.equals("Notebook") || legacy.equals("Study log") || legacy.equals("Stats")) {
      place(3, 0);
      timerPanel.openSub(TimerPanel.subIndex(legacy));
      return;
    }
    show(legacy);
  }

  @Override
  protected void onResume() {
    super.onResume();
    resumed = true;
    if (timerPanel != null) timerPanel.resume();
    if (repo != null) {
      observeOwner();
      repo.write(() -> repo.purgeActions());
      CloudSync.schedule(this);
    }
  }

  @Override
  protected void onPause() {
    resumed = false;
    if (timerPanel != null) timerPanel.pause();
    super.onPause();
  }

  @Override
  protected void onDestroy() {
    if (timerPanel != null) timerPanel.pause();
    super.onDestroy();
  }

  void ensureTimer() {
    if (timerPanel != null) return;
    timerPanel = new TimerPanel(this, ui.timerHost, timerSavedState,
        new Intent(getIntent()), () -> navigate(timerReturnScreen), this::setTimerLocked);
    if (resumed) timerPanel.resume();
  }

  void setTimerLocked(boolean locked) {
    ui.toolbar.setVisibility(locked ? View.GONE : View.VISIBLE);
    ui.heading.setVisibility(locked ? View.GONE : View.VISIBLE);
    ui.navigation.setVisibility(locked ? View.GONE : View.VISIBLE);
    if (timerChip != null && locked) timerChip.setVisibility(View.GONE);
  }

  @Override
  protected void onNewIntent(Intent i) {
    super.onNewIntent(i);
    setIntent(i);
    handleIntent(i);
    render();
  }

  void handleIntent(Intent i) {
    String view = i.getStringExtra("screen");
    // Through navigate(), not a raw field write: a widget or reminder still sends flat names, and
    // assigning them to `screen` directly left the tab pair disagreeing with the screen on show.
    if (view != null) navigate(view);
    if (tab == 3 && overflow.isEmpty()) {
      ensureTimer();
      String timerSub = i.getStringExtra("sub");
      if (timerSub != null) timerPanel.openSub(TimerPanel.subIndex(timerSub));
      timerPanel.applyPlan(i.getStringExtra("plan"));
    }
    if (Intent.ACTION_SEND.equals(i.getAction())) {
      String text = i.getStringExtra(Intent.EXTRA_TEXT);
      startActivity(
          new Intent(this, EditorActivity.class).putExtra("kind", "note").putExtra("shared", text));
    }
  }

  private void filters() {
    List<String> rawChoices =
        new ArrayList<>(Arrays.asList("All", "Incomplete", "Completed", "No due date", "Due soon"));
    List<String> displayChoices = new ArrayList<>();
    for (String choice : rawChoices) displayChoices.add(tr(choice));
    for (Row c : Rules.kind(rows, "course")) {
      rawChoices.add(c.title());
      displayChoices.add(c.title());
    }
    new AlertDialog.Builder(this)
        .setTitle(tr("Filter"))
        .setItems(
            displayChoices.toArray(new String[0]),
            (d, w) -> {
              filter = rawChoices.get(w);
              course = "";
              if (w >= 5) course = Rules.kind(rows, "course").get(w - 5).id;
              render();
            })
        .show();
  }

  String kindForScreen() {
    return switch (screen) {
      case "Plan", "Archived courses" -> "course";
      case "Notebook" -> "note";
      case "Study log" -> "session";
      case "Grades" -> "grade";
      case "Timetable" -> "lesson";
      case "Academic terms" -> "term";
      case "Study plans" -> "planned";
      case "Commitments" -> "commitment";
      case "Calendar feeds" -> "feed";
      default -> "action";
    };
  }

  void addMenu() {
    if (screen.equals("Plan") && !course.isEmpty()
        || screen.equals("Calendar")
        || screen.equals("Next Up")) {
      String[] kinds =
          screen.equals("Next Up")
              ? new String[] {"action", "course", "assignment", "exam"}
              : new String[] {
                "assignment", "exam", "planned", "commitment", "lesson", "note", "grade", "course"
              };
      new AlertDialog.Builder(this)
          .setTitle(tr("Add to your desk"))
          .setItems(kinds, (d, w) -> edit(new Row(kinds[w])))
          .show();
    } else edit(new Row(kindForScreen()));
  }

  @Override
  public void edit(Row row) {
    Intent i =
        new Intent(this, EditorActivity.class)
            .putExtra("kind", row.kind)
            .putExtra("course", course.isEmpty() ? row.course() : course);
    if (!row.key.isEmpty()) i.putExtra("id", row.id);
    else if (screen.equals("Calendar")) i.putExtra("selectedDate", day.toString());
    startActivity(i);
  }

  boolean matches(Row r) {
    String q = ui.search.getText().toString().toLowerCase(Locale.ROOT);
    if (!q.isEmpty() && !(r.title() + " " + r.payload).toLowerCase(Locale.ROOT).contains(q))
      return false;
    if (!course.isEmpty() && !r.course().equals(course) && !r.id.equals(course)) return false;
    if (filter.equals("Incomplete") && r.b("done")
        || filter.equals("Completed") && !r.b("done")
        || filter.equals("No due date") && !r.s("dueDate").isEmpty()) return false;
    if (filter.equals("Due soon")
        && (r.s("dueDate").isEmpty()
            || Rules.days(r.s("dueDate"), LocalDate.now())
                > Rules.horizon(
                    r.s("type"),
                    getSharedPreferences("settings", 0).getString("dueWindow", "smart"))))
      return false;
    return true;
  }

  void render() {
    if (ui == null) return;
    // Clicks on the selected tab are ignored, so keep the strip in sync with every route.
    // Overflow screens belong to no bottom tab; all bottom destinations remain clickable there.
    ui.navigation.select(overflow.isEmpty() ? tab : -1);
    // The heading names the TAB, not the sub-tab — the original keeps "Plan" on screen across List,
    // Calendar and Timetable, and "Grades" across Grades and Trends. Study is the one tab whose
    // screen is titled differently from its own label ("Next Up" under a tab reading "Study").
    ui.heading.setText(tr(overflow.isEmpty() ? (tab == 0 ? "Next Up" : TABS[tab]) : overflow));
    boolean timer = tab == 3 && overflow.isEmpty();
    ui.timerHost.setVisibility(timer ? View.VISIBLE : View.GONE);
    ui.list.setVisibility(timer ? View.GONE : View.VISIBLE);
    ui.eyebrow.setVisibility(timer ? View.GONE : View.VISIBLE);
    ui.summary.setVisibility(timer ? View.GONE : View.VISIBLE);
    if (timer) {
      ui.subTabs.setVisibility(View.GONE);
      ui.calendar.setVisibility(View.GONE);
      ui.dateNav.setVisibility(View.GONE);
      ui.search.setVisibility(View.GONE);
      ui.add.setVisibility(View.GONE);
      ensureTimer();
      return;
    }
    boolean hasSubs = overflow.isEmpty() && SUBS[tab].length > 0;
    ui.subTabs.setVisibility(hasSubs ? View.VISIBLE : View.GONE);
    if (hasSubs) {
      if (subTabsFor != tab) {
        ui.subTabs.setTabs(Arrays.asList(SUBS[tab]), sub, i -> place(tab, i));
        subTabsFor = tab;
      } else ui.subTabs.select(sub);
    }
    ui.calendar.setRecords(rows);
    ui.calendar.setFirstDayOfWeek(
        getSharedPreferences("settings", 0).getString("weekStart", "1").equals("0")
            ? java.util.Calendar.SUNDAY
            : java.util.Calendar.MONDAY);
    if (screen.equals("Calendar")
        && !java.time.Instant.ofEpochMilli(ui.calendar.getDate())
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate()
            .equals(day)) {
      long chosen = day.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
      if (chosen >= ui.calendar.getMinDate() && chosen <= ui.calendar.getMaxDate())
        ui.calendar.setDate(chosen, false, true);
    }
    ui.eyebrow.setText(
        day.format(
                DateTimeFormatter.ofPattern(
                    "EEEE · d MMMM yyyy", getResources().getConfiguration().getLocales().get(0)))
            .toUpperCase());
    ui.dateNav.setVisibility(
        screen.equals("Calendar") || screen.equals("Timetable") ? View.VISIBLE : View.GONE);
    ui.calendar.setVisibility(
        screen.equals("Calendar") && getResources().getConfiguration().screenHeightDp >= 500
            ? View.VISIBLE
            : View.GONE);
    ui.dateButton.setText(day.toString());
    ui.search.setVisibility(
        screen.equals("Notebook")
                || screen.equals("Plan")
                || screen.equals("Study log")
                || screen.equals("Grades")
            ? View.VISIBLE
            : View.GONE);
    ui.add.setVisibility(
        screen.equals("Stats") || screen.equals("Analytics") || screen.equals("Attendance")
            ? View.GONE
            : View.VISIBLE);
    ui.add.setText(tr(screen.equals("Calendar") ? "Add event" : "Add " + kindForScreen()));
    List<CardAdapter.Card> cards = new ArrayList<>();
    int pending = 0;
    for (Row r : rows)
      if (r.live() && (r.kind.equals("assignment") || r.kind.equals("exam")) && !r.b("done"))
        pending++;
    ui.summary.setText(
        tr("Saved on this device") + " · " + pending + " " + tr("unfinished deadlines"));
    switch (screen) {
      case "Next Up" -> {
        List<Row> actions = Rules.nextUp(rows, LocalDate.now());
        if (actions.isEmpty()) {
          boolean active =
              Rules.kind(rows, "course").stream().anyMatch(c -> c.s("archivedAt").isEmpty());
          cards.add(
              new CardAdapter.Card(
                  null,
                  tr("YOUR NEXT STEP"),
                  tr(active ? "Add a deadline" : "Start a new period"),
                  tr(
                      active
                          ? "Add an assignment or exam to see your next steps."
                          : "Add your courses to build your study plan."),
                  tr("Go to Plan"),
                  () -> navigate("Plan")));
        }
        for (String bucket : new String[] {"today", "this_week", "later"})
          for (Row r : actions)
            if (r.s("bucket").equals(bucket))
              cards.add(
                  card(
                      r,
                      bucket.equals("this_week")
                          ? "This week"
                          : bucket.equals("today") ? "Today" : "Later"));
      }
      case "Plan" -> {
        if (course.isEmpty()) {
          for (Row r : Rules.kind(rows, "course"))
            if (r.s("archivedAt").isEmpty() && matches(r)) cards.add(card(r, "Course"));
        } else {
          Row c = Rules.byId(rows, "course", course);
          if (c != null) {
            ui.heading.setText(c.title());
            cards.add(
                new CardAdapter.Card(
                    null,
                    "COURSE",
                    c.title(),
                    "Credits: " + c.n("credits", 1) + " · " + c.s("semester"),
                    "All courses",
                    () -> {
                      course = "";
                      render();
                    }));
          }
          for (String k : new String[] {"assignment", "exam", "note", "grade"})
            for (Row r : Rules.kind(rows, k)) if (matches(r)) cards.add(card(r, k));
        }
      }
      case "Archived courses" -> {
        for (Row r : Rules.kind(rows, "course"))
          if (!r.s("archivedAt").isEmpty() && matches(r)) cards.add(card(r, "Archived"));
      }
      case "Calendar" -> agenda(cards, day);
      case "Timetable" -> {
        LocalDate monday =
            day.with(
                TemporalAdjusters.previousOrSame(
                    getSharedPreferences("settings", 0).getString("weekStart", "1").equals("0")
                        ? DayOfWeek.SUNDAY
                        : DayOfWeek.MONDAY));
        for (int i = 0; i < 7; i++) {
          LocalDate date = monday.plusDays(i);
          cards.add(
              new CardAdapter.Card(
                  null, date.getDayOfWeek().toString(), date.toString(), "", "", null));
          for (Row r : Rules.kind(rows, "lesson"))
            if (Rules.lessonOn(r, rows, date) && matches(r)) cards.add(card(r, date.toString()));
        }
      }
      case "Grades" -> {
        String mode = getSharedPreferences("settings", 0).getString("gradeMode", "ib");
        cards.add(
            new CardAdapter.Card(
                null,
                "GRADE POINT AVERAGE",
                String.format(Locale.US, "%.2f", Rules.gpa(rows, mode, false)),
                "Scale: " + mode + " · current courses",
                "",
                null));
        for (Row c : Rules.kind(rows, "course")) {
          List<Row> grades = new ArrayList<>();
          for (Row g : Rules.kind(rows, "grade"))
            if (g.course().equals(c.id) && matches(g)) grades.add(g);
          if (!grades.isEmpty()) {
            double sum = grades.stream().mapToDouble(g -> Math.max(0, g.n("weight", 1))).sum();
            cards.add(
                new CardAdapter.Card(
                    null,
                    c.s("archivedAt").isEmpty() ? "CURRENT COURSE" : "PAST COURSE",
                    c.title(),
                    "Weighted mean: "
                        + String.format(Locale.US, "%.2f", Rules.effectiveGrade(grades))
                        + " · Credits: "
                        + c.n("credits", 1),
                    "",
                    null));
            for (Row g : grades)
              cards.add(
                  card(
                      g,
                      "Weight share: "
                          + (sum == 0
                              ? "equal"
                              : String.format(Locale.US, "%.1f%%", 100 * g.n("weight", 1) / sum))));
          }
        }
      }
      case "Stats", "Analytics" ->
          cards.addAll(
              Statistics.cards(
                  rows, getSharedPreferences("settings", 0), screen.equals("Analytics")));
      case "Attendance" -> attendance(cards);
      default -> {
        List<Row> list = Rules.kind(rows, kindForScreen());
        if (screen.equals("Study log"))
          list.sort(Comparator.comparing((Row r) -> r.s("startedAt")).reversed());
        if (screen.equals("Notebook"))
          list.sort(Comparator.comparing((Row r) -> r.updatedAt).reversed());
        for (Row r : list) if (matches(r)) cards.add(card(r, r.kind));
      }
    }
    if (cards.isEmpty())
      cards.add(
          new CardAdapter.Card(
              null,
              "YOUR DESK",
              tr("Nothing here yet"),
              tr("Use Add to create your first record."),
              "",
              null));
    adapter.submit(cards);
  }

  /** The card body lives in DeskCard so the timer hub's Log and Notebook tabs describe rows
   *  identically; see that class. */
  CardAdapter.Card card(Row r, String tag) {
    return DeskCard.of(r, tag, rows);
  }

  void agenda(List<CardAdapter.Card> cards, LocalDate date) {
    List<Row> agenda = new ArrayList<>();
    List<int[]> blocked = new ArrayList<>();
    for (Row r : rows) {
      if (!r.live() || !matches(r)) continue;
      boolean occurs =
          switch (r.kind) {
            case "assignment", "exam" -> r.s("dueDate").equals(date.toString());
            case "lesson" -> Rules.lessonOn(r, rows, date);
            case "commitment" -> Rules.commitmentOn(r, date);
            case "planned" ->
                !r.s("startsAt").isEmpty()
                    && Times.local(r.s("startsAt")).toLocalDate().equals(date);
            case "session" ->
                !r.s("startedAt").isEmpty()
                    && Times.local(r.s("startedAt")).toLocalDate().equals(date);
            default -> false;
          };
      if (occurs) {
        agenda.add(r);
        try {
          if (r.kind.equals("lesson"))
            blocked.add(new int[] {Rules.minutes(r.s("startsAt")), Rules.minutes(r.s("endsAt"))});
          if (r.kind.equals("commitment"))
            blocked.add(new int[] {Rules.minutes(r.s("startTime")), Rules.minutes(r.s("endTime"))});
          if (r.kind.equals("planned") && r.s("dismissedAt").isEmpty())
            blocked.add(
                new int[] {
                  Times.local(r.s("startsAt")).getHour() * 60
                      + Times.local(r.s("startsAt")).getMinute(),
                  Times.local(r.s("startsAt")).getHour() * 60
                      + Times.local(r.s("startsAt")).getMinute()
                      + (int) r.n("durationMinutes", 25)
                });
        } catch (Exception ignored) {
        }
      }
    }
    agenda.sort(
        Comparator.comparing(
            r ->
                switch (r.kind) {
                  case "lesson" -> r.s("startsAt");
                  case "commitment" -> r.s("startTime");
                  case "planned" -> Times.local(r.s("startsAt")).toLocalTime().toString();
                  default -> r.s("dueTime");
                }));
    for (Row r : agenda) cards.add(card(r, r.kind));
    for (Row r : Rules.kind(rows, "exam"))
      if (!r.b("done") && !r.s("dueDate").isEmpty() && Rules.studyStart(r).equals(date))
        cards.add(
            new CardAdapter.Card(
                null,
                "STUDY START",
                r.title(),
                "Exam on " + r.s("dueDate"),
                "Open exam",
                () -> showDetail(r)));
    for (int[] free :
        Rules.freeWindows(
            blocked,
            8 * 60,
            Integer.parseInt(getSharedPreferences("settings", 0).getString("studyUntil", "1260"))))
      cards.add(
          new CardAdapter.Card(
              null,
              "FREE WINDOW",
              String.format(
                  Locale.US,
                  "%02d:%02d — %02d:%02d",
                  free[0] / 60,
                  free[0] % 60,
                  free[1] / 60,
                  free[1] % 60),
              "Available for a study plan",
              "Plan study",
              () ->
                  startActivity(
                      new Intent(this, EditorActivity.class)
                          .putExtra("kind", "planned")
                          .putExtra(
                              "plannedStarts", date.atTime(free[0] / 60, free[0] % 60).toString())
                          .putExtra("plannedDuration", Math.min(25, free[1] - free[0])))));
  }

  void attendance(List<CardAdapter.Card> cards) {
    int present = 0, total = 0;
    for (Row r : Rules.kind(rows, "attendance")) {
      if (r.s("status").equals("present")) present++;
      if (r.s("status").equals("present") || r.s("status").equals("absent")) total++;
      Row lesson = Rules.byId(rows, "lesson", r.s("timetableEntryId"));
      cards.add(
          new CardAdapter.Card(
              r,
              r.s("date"),
              lesson == null ? "Deleted lesson" : lesson.title(),
              r.s("status") + " · " + r.s("note"),
              "Change mark",
              () -> markAttendance(lesson, r.s("date"))));
    }
    cards.add(
        0,
        new CardAdapter.Card(
            null,
            "ATTENDANCE",
            total == 0
                ? "No marks yet"
                : String.format(Locale.US, "%.0f%%", 100.0 * present / total),
            present + " attended / " + total + " counted lessons",
            "",
            null));
    for (Row lesson : Rules.kind(rows, "lesson"))
      if (Rules.lessonOn(lesson, rows, day))
        cards.add(
            new CardAdapter.Card(
                lesson,
                day.toString(),
                lesson.title(),
                lesson.s("startsAt") + " · " + lesson.s("room"),
                "Mark attendance",
                () -> markAttendance(lesson, day.toString())));
  }

  void markAttendance(Row lesson, String date) {
    if (lesson == null) return;
    new AlertDialog.Builder(this)
        .setTitle(lesson.title() + " · " + date)
        .setItems(
            new String[] {"present", "absent", "cancelled", "rescheduled", "Unmark"},
            (d, w) ->
                repo.write(
                    () -> {
                      Row mark = null;
                      for (Row r : repo.all())
                        if (r.kind.equals("attendance")
                            && r.s("timetableEntryId").equals(lesson.id)
                            && r.s("date").equals(date)) {
                          mark = r;
                          break;
                        }
                      if (mark == null)
                        mark =
                            new Row("attendance")
                                .put("timetableEntryId", lesson.id)
                                .put("date", date);
                      if (w == 4) repo.delete(mark);
                      else {
                        mark.deletedAt = null;
                        mark.put(
                            "status",
                            new String[] {"present", "absent", "cancelled", "rescheduled"}[w]);
                        repo.save(mark);
                      }
                    }))
        .show();
  }

  @Override
  public void primary(Row r) {
    switch (r.kind) {
      case "course" -> {
        screen = "Plan";
        course = r.id;
        filter = "All";
        render();
      }
      case "assignment", "exam", "action" ->
          repo.write(
              () -> {
                Row current = repo.find(r.kind, r.id);
                if (current != null && current.live()) repo.toggle(current);
              });
      case "grade" -> edit(r);
      case "note" -> startActivity(new Intent(this, NotebookActivity.class).putExtra("id", r.id));
      case "planned" -> {
        place(3, 0);
        timerPanel.openSub(0);
        timerPanel.applyPlan(r.id);
      }
      case "feed" ->
          repo.write(
              () -> {
                try {
                  CalendarFeed.refresh(this, repo, r);
                  runOnUiThread(() -> message("Feed refreshed"));
                } catch (Exception e) {
                  runOnUiThread(() -> message(e.getMessage()));
                }
              });
      default -> showDetail(r);
    }
  }

  @Override
  public void more(Row r) {
    List<String> options = new ArrayList<>(Arrays.asList("Details", "Delete"));
    if (r.kind.equals("course")) {
      options.add(r.s("archivedAt").isEmpty() ? "Archive" : "Restore");
      if (!r.s("semester").isEmpty())
        options.add(r.s("archivedAt").isEmpty() ? "Archive period" : "Restore period");
    }
    if (r.kind.equals("exam")) options.add("Topics");
    if (r.kind.equals("assignment") || r.kind.equals("note")) options.add("Attachments");
    if (r.kind.equals("lesson")) options.add("Mark attendance");
    if (r.kind.equals("planned")) {
      options.add("Log completed study");
      options.add("Dismiss / Restore plan");
    }
    if (!r.s("seriesId").isEmpty()) options.add("Delete remaining series");
    if (r.kind.equals("session")) options.add("AI debrief");
    new AlertDialog.Builder(this)
        .setTitle(r.title())
        .setItems(
            options.toArray(new String[0]),
            (d, w) -> {
              String choice = options.get(w);
              switch (choice) {
                case "Details" -> showDetail(r);
                case "Delete" -> confirmDelete(r, false);
                case "Delete remaining series" -> confirmDelete(r, true);
                case "Archive period", "Restore period" ->
                    repo.write(
                        () -> {
                          for (Row c : Rules.kind(repo.all(), "course"))
                            if (c.s("semester").equals(r.s("semester")))
                              repo.save(
                                  c.put(
                                      "archivedAt",
                                      choice.equals("Archive period")
                                          ? java.time.Instant.now().toString()
                                          : null));
                        });
                case "Archive", "Restore" ->
                    repo.write(
                        () ->
                            repo.save(
                                r.put(
                                    "archivedAt",
                                    choice.equals("Archive")
                                        ? java.time.Instant.now().toString()
                                        : null)));
                case "Topics" -> topics(r);
                case "Attachments" -> Attachments.show(this, repo, r);
                case "Mark attendance" -> markAttendance(r, day.toString());
                case "Log completed study" ->
                    repo.write(
                        () -> {
                          Row s =
                              new Row("session")
                                  .put("subjectId", r.course())
                                  .put("startedAt", r.s("startsAt"))
                                  .put("durationMinutes", r.n("durationMinutes", 25))
                                  .put("notes", r.s("notes"));
                          repo.save(s);
                          repo.save(r.put("fulfilledBy", s.id).put("dismissedAt", null));
                        });
                case "Dismiss / Restore plan" ->
                    repo.write(
                        () ->
                            repo.save(
                                r.put(
                                        "dismissedAt",
                                        r.s("dismissedAt").isEmpty()
                                            ? java.time.Instant.now().toString()
                                            : null)
                                    .put("fulfilledBy", null)));
                case "AI debrief" -> AiDebrief.show(this, repo, r);
              }
            })
        .show();
  }

  void confirmDelete(Row r, boolean series) {
    new AlertDialog.Builder(this)
        .setTitle(tr("Delete") + " " + r.title() + "?")
        .setMessage(tr("This removes the record and cancels its reminders."))
        .setNegativeButton(tr("Cancel"), null)
        .setPositiveButton(
            tr("Delete"),
            (d, w) ->
                repo.write(
                    () -> {
                      if (series) {
                        for (Row x : repo.all())
                          if (x.live()
                              && x.s("seriesId").equals(r.s("seriesId"))
                              && x.kind.equals(r.kind)
                              && (x.kind.equals("lesson")
                                  || (!x.s("startsAt").isEmpty()
                                      && !Times.instant(x.s("startsAt"))
                                          .isBefore(Times.instant(r.s("startsAt")))
                                      && x.s("fulfilledBy").isEmpty()
                                      && x.s("dismissedAt").isEmpty()))) repo.delete(x);
                      } else repo.delete(r);
                    }))
        .show();
  }

  void showDetail(Row r) {
    new AlertDialog.Builder(this)
        .setTitle(r.title().isEmpty() ? r.kind : r.title())
        .setMessage(
            r.kind.equals("note") ? NoteRenderer.render(r.s("content")) : card(r, r.kind).detail())
        .setPositiveButton(tr("Edit"), (d, w) -> edit(r))
        .setNegativeButton(tr("Close"), null)
        .setNeutralButton(
            r.kind.equals("exam") ? tr("Topics") : tr("Share"),
            (d, w) -> {
              if (r.kind.equals("exam")) topics(r);
              else
                startActivity(
                    Intent.createChooser(
                        new Intent(Intent.ACTION_SEND)
                            .setType("text/plain")
                            .putExtra(
                                Intent.EXTRA_TEXT, r.title() + "\n" + card(r, r.kind).detail()),
                        tr("Share")));
            })
        .show();
  }

  void topics(Row exam) {
    JSONArray a = exam.json().optJSONArray("topics");
    if (a == null) a = new JSONArray();
    JSONArray list = a;
    String[] labels = new String[list.length()];
    boolean[] checked = new boolean[list.length()];
    for (int i = 0; i < list.length(); i++) {
      JSONObject t = list.optJSONObject(i);
      labels[i] = t.optString("title");
      checked[i] = t.optBoolean("done");
    }
    new AlertDialog.Builder(this)
        .setTitle(tr("Exam topics"))
        .setMultiChoiceItems(
            labels,
            checked,
            (d, w, on) -> {
              try {
                list.getJSONObject(w).put("done", on);
                repo.write(
                    () -> {
                      Row current = repo.find("exam", exam.id);
                      if (current != null) {
                        current.put("topics", list);
                        repo.save(current);
                      }
                    });
              } catch (JSONException e) {
                message(e.getMessage());
              }
            })
        .setPositiveButton(
            tr("Add topic"),
            (d, w) -> {
              EditText input = new EditText(this);
              new AlertDialog.Builder(this)
                  .setTitle(tr("Topic title"))
                  .setView(input)
                  .setPositiveButton(
                      tr("Add"),
                      (x, y) -> {
                        if (input.getText().toString().trim().isEmpty()) return;
                        try {
                          list.put(
                              new JSONObject()
                                  .put("id", UUID.randomUUID().toString())
                                  .put("title", input.getText().toString().trim())
                                  .put("done", false));
                          repo.write(() -> repo.save(exam.put("topics", list)));
                        } catch (JSONException e) {
                          message(e.getMessage());
                        }
                      })
                  .setNegativeButton(tr("Cancel"), null)
                  .show();
            })
        .setNeutralButton(
            tr("Delete topic"),
            (d, w) -> {
              if (labels.length > 0)
                new AlertDialog.Builder(this)
                    .setItems(
                        labels,
                        (x, y) -> {
                          list.remove(y);
                          repo.write(() -> repo.save(exam.put("topics", list)));
                        })
                    .show();
            })
        .setNegativeButton(tr("Close"), null)
        .show();
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (request == 7301
        && result == RESULT_OK
        && data != null
        && data.getData() != null
        && pendingAttachment != null) {
      Attachments.importFile(this, data.getData(), pendingAttachment);
      pendingAttachment = null;
    }
  }

  @Override
  protected void onSaveInstanceState(Bundle b) {
    if (pendingAttachment != null) {
      b.putString("attachmentKind", pendingAttachment.kind);
      b.putString("attachmentId", pendingAttachment.id);
    }
    super.onSaveInstanceState(b);
    b.putString("screen", screen);
    b.putString("course", course);
    b.putString("filter", filter);
    b.putString("day", day.toString());
    b.putString("timerReturnScreen", timerReturnScreen);
    if (timerPanel != null) {
      Bundle timerState = new Bundle();
      timerPanel.saveState(timerState);
      b.putBundle("timerPanel", timerState);
    } else if (timerSavedState != null) b.putBundle("timerPanel", timerSavedState);
  }
}
