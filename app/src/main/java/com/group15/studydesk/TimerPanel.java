package com.group15.studydesk;

import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.*;
import com.group15.studydesk.databinding.*;
import java.time.*;
import java.util.*;

final class TimerPanel implements CardAdapter.Actions {
  final BaseActivity host;
  final Runnable returnToDesk;
  final java.util.function.Consumer<Boolean> onLock;
  Intent intent;
  /** The hub's four sub-tabs, in the original's order. */
  static final String[] SUBS = {"Timer", "Notebook", "Log", "Stats"};

  ActivityTimerBinding ui;
  TimerState timer;
  Repository repo;
  CardAdapter adapter;
  Handler tick = new Handler(Looper.getMainLooper());
  List<Row> courses = new ArrayList<>();
  List<Row> rows = new ArrayList<>();
  int sub = 0;
  String plan = "";
  /** Runs once the open save sheet is answered; see {@link #harvestThen}. */
  Runnable afterLog;

  boolean ready = false, saving = false;

  TimerPanel(BaseActivity host, ViewGroup container, Bundle state, Intent intent,
      Runnable returnToDesk, java.util.function.Consumer<Boolean> onLock) {
    this.host = host;
    this.intent = intent;
    if (state != null && state.containsKey("plan"))
      this.intent.putExtra("plan", state.getString("plan", ""));
    this.returnToDesk = returnToDesk;
    this.onLock = onLock;
    ui = ActivityTimerBinding.inflate(host.getLayoutInflater(), container, true);
    timer = new TimerState(host);
    repo = new Repository(host);
    adapter = new CardAdapter(this);
    ui.timerList.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(host));
    ui.timerList.setAdapter(adapter);
    sub = subIndex(intent.getStringExtra("sub"));
    if (state != null && state.containsKey("sub")) sub = state.getInt("sub", 0);
    ui.timerSubTabs.setTabs(Arrays.asList(SUBS), sub, this::openSub);
    ui.task.setText(timer.p.getString("task", ""));
    ui.minutes.setText(String.valueOf(timer.duration() / 60));
    ui.lock.setChecked(timer.p.getBoolean("lock", false));
    repo.observe()
        .observe(
            host,
            rows -> {
              this.rows = rows;
              if (sub != 0) fill();
              if (!ready) {
                courses = Rules.kind(rows, "course");
                List<String> labels = new ArrayList<>();
                labels.add(tr("General study"));
                for (Row c : courses) labels.add(c.title());
                ui.course.setAdapter(
                    new ArrayAdapter<>(
                        host, android.R.layout.simple_spinner_dropdown_item, labels));
                for (int i = 0; i < courses.size(); i++)
                  if (courses.get(i).id.equals(timer.p.getString("course", "")))
                    ui.course.setSelection(i + 1);
                plan = intent.getStringExtra("plan");
                if (plan == null) plan = "";
                // A plan is answered once, by the first block logged from this screen. Remember
                // which one so a rotation or a second block cannot hand an already-settled plan
                // back and re-point `fulfilledBy` at a later session.
                if (plan.equals(timer.p.getString("planFulfilled", ""))) plan = "";
                Row planned = Rules.byId(rows, "planned", plan);
                if (planned != null && !timer.running()) {
                  ui.task.setText(planned.title());
                  ui.minutes.setText(String.valueOf((int) planned.n("durationMinutes", 25)));
                  for (int i = 0; i < courses.size(); i++)
                    if (courses.get(i).id.equals(planned.course())) ui.course.setSelection(i + 1);
                }
                ready = true;
                openSub(sub);
              }
            });
    ui.start.setOnClickListener(
        v -> {
          if (timer.running()) {
            timer.pause();
            Reminders.timer(host);
            render();
            return;
          }
          int min;
          try {
            min = Integer.parseInt(ui.minutes.getText().toString().trim());
            if (min < 5 || min > 240) throw new IllegalArgumentException();
          } catch (Exception e) {
            message("Focus duration must be 5–240 minutes");
            return;
          }
          // A new target means a new block, so the time already on the clock is settled first.
          // Resuming an unchanged one is left exactly as it was paused, remainder and all.
          harvestThen(
              () -> {
                if (!timer.stopwatch() && min * 60 != timer.duration()) timer.retarget(min * 60);
                timer.start(ui.task.getText().toString(), course(), min * 60);
              });
        });
    ui.minutes.setOnFocusChangeListener(
        (v, focused) -> {
          if (!focused) commitMinutes();
        });
    ui.reset.setOnClickListener(v -> harvestThen(timer::reset));
    ui.mode.setOnClickListener(
        v -> {
          boolean next = !timer.stopwatch();
          harvestThen(
              () -> {
                timer
                    .p
                    .edit()
                    .putBoolean("stopwatch", next)
                    .putString("phase", "focus")
                    .putBoolean("lock", false)
                    .commit();
                ui.lock.setChecked(false);
                timer.reset();
              });
        });
    // Skip only shows outside a focus phase, so there is never anything to harvest here. It goes
    // through the same path anyway, so the rule is "every control that clears the clock settles it
    // first" rather than a list of exceptions to remember.
    ui.skip.setOnClickListener(v -> harvestThen(() -> timer.phase("focus")));
    ui.saveSession.setOnClickListener(v -> log(false));
    ui.lock.setOnCheckedChangeListener(
        (v, on) -> {
          timer.pause();
          if (on && (timer.stopwatch() || !timer.phase().equals("focus"))) timer.phase("focus");
          timer.p.edit().putBoolean("lock", on).putBoolean("stopwatch", false).commit();
          lock(on);
          render();
        });
    lock(ui.lock.isChecked());
  }

  /** The sub-tab a legacy flat screen name names, or 0. See {@code MainActivity.navigate}. */
  static int subIndex(String name) {
    if ("Notebook".equals(name)) return 1;
    if ("Study log".equals(name)) return 2;
    if ("Stats".equals(name)) return 3;
    return 0;
  }

  /** Switch hub sub-tabs. Only Timer owns the clock, so the rest swap the body out for a list. */
  void openSub(int index) {
    sub = Math.max(0, Math.min(SUBS.length - 1, index));
    ui.timerSubTabs.select(sub);
    boolean clock = sub == 0;
    ui.timerPage.setVisibility(clock ? View.VISIBLE : View.GONE);
    ui.timerList.setVisibility(clock ? View.GONE : View.VISIBLE);
    if (!clock) fill();
  }

  /** Build the cards for whichever list sub-tab is showing. */
  void fill() {
    List<CardAdapter.Card> cards = new ArrayList<>();
    if (sub == 1) {
      Row blank = new Row("note");
      cards.add(
          new CardAdapter.Card(
              null,
              tr("NOTEBOOK"),
              tr("New note"),
              "",
              tr("Add"),
              () -> host.startActivity(new Intent(host, EditorActivity.class).putExtra("kind", "note"))));
      List<Row> notes = Rules.kind(rows, "note");
      notes.sort(Comparator.comparing((Row r) -> r.updatedAt).reversed());
      for (Row r : notes) cards.add(DeskCard.of(r, "Note", rows));
    } else if (sub == 2) {
      cards.add(
          new CardAdapter.Card(
              null,
              tr("LOG"),
              tr("Log a past session"),
              "",
              tr("Add"),
              () ->
                  host.startActivity(
                      new Intent(host, EditorActivity.class).putExtra("kind", "session"))));
      List<Row> sessions = Rules.kind(rows, "session");
      sessions.sort(Comparator.comparing((Row r) -> r.s("startedAt")).reversed());
      for (Row r : sessions) cards.add(DeskCard.of(r, "Session", rows));
    } else {
      cards.addAll(Statistics.cards(rows, host.getSharedPreferences("settings", 0), false));
    }
    adapter.submit(cards);
  }

  @Override
  public void primary(Row row) {
    if (row.kind.equals("note"))
      host.startActivity(new Intent(host, NotebookActivity.class).putExtra("id", row.id));
    else edit(row);
  }

  @Override
  public void edit(Row row) {
    host.startActivity(
        new Intent(host, EditorActivity.class)
            .putExtra("kind", row.kind)
            .putExtra("id", row.id));
  }

  @Override
  public void more(Row row) {
    String[] options = {tr("Edit"), tr("Delete")};
    new androidx.appcompat.app.AlertDialog.Builder(host)
        .setTitle(row.title().isEmpty() ? tr(row.kind) : row.title())
        .setItems(
            options,
            (d, w) -> {
              if (w == 0) edit(row);
              else
                  repo.write(
                      () -> {
                        repo.delete(row);
                        host.runOnUiThread(() -> message("Deleted"));
                      });
            })
        .show();
  }

  void saveState(Bundle state) {
    state.putInt("sub", sub);
    state.putString("plan", plan);
  }

  void lock(boolean on) {
    host.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    WindowInsetsControllerCompat c = WindowCompat.getInsetsController(host.getWindow(), ui.timerRoot);
    if (on) {
      c.hide(WindowInsetsCompat.Type.systemBars());
      c.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
      host.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
      if (host.getSharedPreferences("settings", 0).getBoolean("focusPin", false)) host.startLockTask();
    } else {
      c.show(WindowInsetsCompat.Type.systemBars());
      try {
        host.stopLockTask();
      } catch (Exception ignored) {
      }
    }
    onLock.accept(on);
    ui.mode.setVisibility(on ? View.GONE : View.VISIBLE);
    // Lock In takes the screen: the original hides the whole sub-tab strip with it, and a tab
    // that swaps the clock out for a list mid-session is not something Lock In should offer.
    ui.timerSubTabs.setVisibility(on ? View.GONE : View.VISIBLE);
  }

  void leave() {
    if (ui.lock.isChecked())
      new AlertDialog.Builder(host)
          .setTitle(tr("Leave Lock In?"))
          .setMessage(tr("Your timer will pause."))
          .setNegativeButton(tr("Keep studying"), null)
          .setPositiveButton(
              tr("Leave"),
              (d, w) -> {
                timer.pause();
                timer.p.edit().putBoolean("lock", false).commit();
                ui.lock.setChecked(false);
                lock(false);
                Reminders.timer(host);
                returnToDesk.run();
              })
          .show();
    else returnToDesk.run();
  }

  void render() {
    long secs = timer.secs();
    ui.clock.setText(Rules.clock(secs));
    String p = timer.stopwatch() ? "Stopwatch" : (timer.phase().equals("short") ? "Short break" : (timer.phase().equals("long") ? "Long break" : "Focus"));
    ui.phase.setText(tr(p).toUpperCase(Locale.ROOT));
    ui.start.setText(tr(timer.running() ? "Pause" : "Start"));
    ui.mode.setText(tr(timer.stopwatch() ? "Pomodoro" : "Stopwatch"));
    ui.skip.setVisibility(timer.phase().equals("focus") ? View.GONE : View.VISIBLE);
    ui.minutes.setEnabled(!timer.running());
    ui.course.setEnabled(!timer.running());
    ui.progress.setProgress(
        timer.stopwatch()
            ? 0
            : (int)
                Math.min(
                    1000,
                    1000.0
                        * secs
                        / (timer.phase().equals("focus")
                            ? timer.duration()
                            : timer.phase().equals("short") ? 300 : 900)));
    ui.sessionCount.setText(timer.p.getInt("blocks", 0) + " " + tr("focus blocks completed"));
    if (timer.running() && !timer.stopwatch() && secs == 0) {
      timer.pause();
      if (timer.phase().equals("focus")) log(true);
      else timer.phase("focus");
    }
  }

  final Runnable clock =
      new Runnable() {
        public void run() {
          render();
          tick.postDelayed(this, 1000);
        }
      };

  void resume() {
    tick.removeCallbacks(clock);
    if (timer != null) {
      render();
      tick.post(clock);
    }
  }

  void pause() {
    tick.removeCallbacksAndMessages(null);
    timer.p.edit().putString("task", ui.task.getText().toString()).commit();
    Reminders.timer(host);
  }

  String tr(String text) { return host.tr(text); }

  void message(String text) { host.message(text); }

  void rememberTask() {
    timer.p.edit().putString("task", ui.task.getText().toString()).commit();
    Reminders.timer(host);
  }

  void applyPlan(String id) {
    if (id == null || id.isEmpty() || id.equals(timer.p.getString("planFulfilled", ""))) return;
    intent.putExtra("plan", id);
    plan = id;
    Row planned = Rules.byId(rows, "planned", id);
    if (planned != null && !timer.running()) {
      ui.task.setText(planned.title());
      ui.minutes.setText(String.valueOf((int) planned.n("durationMinutes", 25)));
      for (int i = 0; i < courses.size(); i++)
        if (courses.get(i).id.equals(planned.course())) ui.course.setSelection(i + 1);
    }
  }

  String course() {
    int at = ui.course.getSelectedItemPosition();
    return at > 0 ? courses.get(at - 1).id : "";
  }

  /**
   * Settles the focus time the timer is holding before {@code action} is allowed to clear it.
   *
   * <p>Anything past a minute raises the save sheet a completed block raises, so a reset, a mode
   * switch or a retarget logs the work instead of dropping it, and {@code action} then runs once the
   * user has answered. Under a minute there is nothing worth keeping — an abandoned block that short
   * is a misclick, not a session.
   */
  void harvestThen(Runnable action) {
    if (saving) return;
    if (timer.stopwatch() || !timer.phase().equals("focus") || timer.studied() < 60) {
      action.run();
      Reminders.timer(host);
      render();
      return;
    }
    afterLog = action;
    // Reuses log()'s sheet, and its write path, deliberately: time kept this way has to be
    // indistinguishable downstream from a block that ran to zero.
    if (!log(false)) {
      afterLog = null;
      action.run();
    }
    Reminders.timer(host);
    render();
  }

  /** Apply the minutes box to the countdown it names, if it names a different one. */
  void commitMinutes() {
    int min;
    try {
      min = Integer.parseInt(ui.minutes.getText().toString().trim());
    } catch (Exception e) {
      return;
    }
    if (min < 5 || min > 240 || timer.stopwatch() || min * 60 == timer.duration()) return;
    harvestThen(() -> timer.retarget(min * 60));
  }

  /** Returns whether the save sheet went up; false when there was nothing to log. */
  boolean log(boolean completed) {
    if (saving) return false;
    if (!timer.phase().equals("focus")) {
      timer.phase("focus");
      render();
      return false;
    }
    int studied = timer.studied();
    if (studied < 60) {
      message("Study for at least one minute before saving");
      return false;
    }
    timer.pause();
    saving = true;
    EditText note = new EditText(host);
    note.setHint(tr("What did you study?"));
    note.setMinLines(3);
    new AlertDialog.Builder(host)
        .setTitle(
            tr("Save study session") + " · " + Math.max(1, Math.round(studied / 60f)) + " min")
        .setView(note)
        .setItems(
            new String[] {"Focus 1", "Focus 2", "Focus 3", "Focus 4", "Focus 5"},
            (d, w) -> persistSession(studied, note.getText().toString(), w + 1, completed))
        .setPositiveButton(
            tr("Save"),
            (d, w) -> persistSession(studied, note.getText().toString(), null, completed))
        .setNegativeButton(
            tr("Discard"),
            (d, w) -> {
              saving = false;
              advance(completed);
            })
        .setOnCancelListener(
            d -> {
              saving = false;
              // The user backed out without deciding, so whatever was waiting on this sheet is
              // dropped too rather than firing against a later one.
              afterLog = null;
              render();
            })
        .show();
    return true;
  }

  void persistSession(int secs, String notes, Integer rating, boolean completed) {
    long start = timer.p.getLong("sessionStart", System.currentTimeMillis() - secs * 1000L);
    String c = course();
    // The plan is answered by the first block logged from this screen, and only by that one. A
    // second block in the same sitting is its own session, so re-pointing `fulfilledBy` at it would
    // erase which block actually met the intention.
    String settles = plan;
    if (!settles.isEmpty()) {
      plan = "";
      timer.p.edit().putString("planFulfilled", settles).commit();
    }
    repo.write(
        () -> {
          Row session =
              new Row("session")
                  .put("subjectId", c.isEmpty() ? null : c)
                  .put("startedAt", Instant.ofEpochMilli(start).toString())
                  .put("durationMinutes", Math.max(1, Math.min(1440, Math.round(secs / 60f))))
                  .put("notes", notes)
                  .put("focusRating", rating);
          Row fulfilled = null;
          if (!settles.isEmpty()) {
            Row p = repo.find("planned", settles);
            if (p != null && p.live() && p.s("fulfilledBy").isEmpty())
              fulfilled = p.put("fulfilledBy", session.id).put("dismissedAt", null);
          }
          repo.saveWithPlan(session, fulfilled);
          host.runOnUiThread(
              () -> {
                saving = false;
                message("Session saved");
                advance(completed);
              });
        });
  }

  void advance(boolean completed) {
    Runnable next = afterLog;
    afterLog = null;
    if (next != null) next.run();
    else if (completed) {
      int blocks = timer.p.getInt("blocks", 0) + 1;
      timer.p.edit().putInt("blocks", blocks).commit();
      timer.phase(ui.lock.isChecked() ? "focus" : blocks % 4 == 0 ? "long" : "short");
    } else timer.reset();
    Reminders.timer(host);
    render();
  }
}
