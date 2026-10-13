package com.group15.studydesk;

import android.content.*;
import android.net.Uri;
import android.os.*;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import com.group15.studydesk.databinding.*;
import org.json.*;

public class SettingsActivity extends BaseActivity {
  ActivityEditorBinding ui;
  SharedPreferences p;
  Repository repo;

  @Override
  protected void onCreate(Bundle b) {
    super.onCreate(b);
    ui = ActivityEditorBinding.inflate(getLayoutInflater());
    setContentView(ui.getRoot());
    insets(ui.root);
    repo = new Repository(this);
    p = getSharedPreferences("settings", 0);
    ui.toolbar.setTitle(tr("Settings"));
    ui.toolbar.setNavigationIcon(android.R.drawable.ic_menu_close_clear_cancel);
    ui.toolbar.setNavigationOnClickListener(v -> finish());
    ui.save.setVisibility(android.view.View.GONE);
    build();
  }

  void choose(String key, String label, String[] options, String def) {
    choose(key, label, options, options, def);
  }

  void choose(String key, String label, String[] options, String[] labels, String def) {
    NativeForms.choice(
        this,
        ui.fields,
        label,
        options,
        labels,
        p.getString(key, def),
        s -> {
          if (!s.equals(p.getString(key, def))) {
            p.edit().putString(key, s).commit();
            if (key.equals("mode") || key.equals("language")) {
              recreate();
              return;
            }
            repo.write(() -> {});
          }
        });
  }

  void flag(String key, String label, boolean def) {
    NativeForms.toggle(
        this,
        ui.fields,
        label,
        p.getBoolean(key, def),
        s -> {
          p.edit().putBoolean(key, s).commit();
          if (key.equals("notifications")
              && s
              && Build.VERSION.SDK_INT >= 33
              && !Reminders.allowed(this))
            requestPermissions(new String[] {"android.permission.POST_NOTIFICATIONS"}, 42);
          repo.write(() -> {});
        });
  }

  void build() {
    NativeForms.heading(
        this,
        ui.fields,
        "Appearance",
        "Paper colours, a clear type hierarchy, and a calmer study desk.");
    choose("mode", "Lighting", new String[] {"light", "dark", "black"}, "light");
    NativeForms.choice(
        this,
        ui.fields,
        "Character",
        new String[] {"free", "stacks", "slate"},
        new String[] {"Studio", "Stacks (supporter)", "Slate (supporter)"},
        p.getString("character", "free"),
        value -> {
          if (!value.equals("free") && !Appearance.entitled(this)) {
            message("This character theme requires a supporter entitlement in your own project");
            return;
          }
          if (!value.equals(p.getString("character", "free"))) {
            p.edit().putString("character", value).commit();
            recreate();
          }
        });
    NativeForms.button(
        this,
        ui.fields,
        "Refresh supporter entitlement",
        () ->
            repo.write(
                () -> {
                  try {
                    Appearance.refresh(this);
                    runOnUiThread(this::recreate);
                  } catch (Exception e) {
                    runOnUiThread(() -> message(e.getMessage()));
                  }
                }));
    choose(
        "widgetPalette",
        "Home-screen widget palette",
        new String[] {"auto", "light", "dark", "black"},
        "auto");
    // English and Chinese only. The original ships ten, but its other eight are
    // reference material rather than a requirement here, and carrying unused
    // catalogues into the APK buys nothing.
    choose("language", "Language", new String[] {"en", "zh"}, new String[] {"English", "中文"}, "en");
    NativeForms.heading(this, ui.fields, "Grades and calendar", "");
    choose("gradeMode", "Grade scale", new String[] {"ib", "us", "custom"}, "ib");
    EditText
        min =
            NativeForms.text(
                this, ui.fields, "Custom minimum", p.getString("scaleMin", "0"), false),
        max =
            NativeForms.text(
                this, ui.fields, "Custom maximum", p.getString("scaleMax", "10"), false),
        pass =
            NativeForms.text(
                this, ui.fields, "Custom pass mark", p.getString("scalePass", "5"), false);
    choose("scaleDirection", "Best grade", new String[] {"up", "down"}, "up");
    NativeForms.button(
        this,
        ui.fields,
        "Save custom scale",
        () -> {
          try {
            double a = Double.parseDouble(min.getText().toString()),
                z = Double.parseDouble(max.getText().toString()),
                t = Double.parseDouble(pass.getText().toString());
            if (!Double.isFinite(a)
                || !Double.isFinite(z)
                || !Double.isFinite(t)
                || a >= z
                || a < -99999
                || z > 99999
                || t < a
                || t > z) throw new IllegalArgumentException();
            p.edit()
                .putString("scaleMin", String.valueOf(a))
                .putString("scaleMax", String.valueOf(z))
                .putString("scalePass", String.valueOf(t))
                .commit();
            message("Scale saved");
          } catch (Exception e) {
            message("Enter valid scale bounds and pass mark");
          }
        });
    choose("weekStart", "Week starts on (0 = Sunday)", new String[] {"1", "0"}, "1");
    choose(
        "dayStart", "Study day starts at", new String[] {"0", "1", "2", "3", "4", "5", "6"}, "0");
    choose(
        "dueWindow",
        "Due soon window (days)",
        new String[] {"smart", "3", "7", "14", "30", "all"},
        "smart");
    EditText until =
        NativeForms.text(
            this,
            ui.fields,
            "Study until (minutes after midnight)",
            p.getString("studyUntil", "1260"),
            false);
    NativeForms.button(
        this,
        ui.fields,
        "Save planning window",
        () -> {
          try {
            int n = Integer.parseInt(until.getText().toString());
            if (n < 0 || n > 1440) throw new IllegalArgumentException();
            p.edit().putString("studyUntil", String.valueOf(n)).commit();
            message("Saved");
          } catch (Exception e) {
            message("Enter 0–1440 minutes");
          }
        });
    NativeForms.heading(
        this,
        ui.fields,
        "Reminders and focus",
        Reminders.allowed(this)
            ? "Notifications are allowed."
            : "Notifications are currently blocked. Your saved records and timer still work.");
    flag("notifications", "Study reminders", true);
    choose(
        "planLead",
        "Planned-study reminder lead (minutes)",
        new String[] {"0", "5", "15", "30", "60", "120"},
        "30");
    flag("planStart", "Remind at study start", true);
    flag("focusChip", "Ongoing focus notification", true);
    flag("focusPin", "Offer screen pinning in Lock In", false);
    NativeForms.button(
        this,
        ui.fields,
        "Notification permissions",
        () -> {
          if (Build.VERSION.SDK_INT >= 33 && !Reminders.allowed(this))
            requestPermissions(new String[] {"android.permission.POST_NOTIFICATIONS"}, 42);
          else
            startActivity(
                new Intent("android.settings.APP_NOTIFICATION_SETTINGS")
                    .putExtra("android.provider.extra.APP_PACKAGE", getPackageName()));
        });
    NativeForms.button(
        this,
        ui.fields,
        "Exact reminder access",
        () -> {
          if (Build.VERSION.SDK_INT >= 31)
            startActivity(
                new Intent(
                    "android.settings.REQUEST_SCHEDULE_EXACT_ALARM",
                    Uri.parse("package:" + getPackageName())));
          else message("Exact reminders are available");
        });
    NativeForms.button(
        this,
        ui.fields,
        "Send test reminder",
        () -> {
          if (!Reminders.allowed(this)) {
            message("Enable notification permission first");
            return;
          }
          sendBroadcast(
              new Intent(this, ReminderReceiver.class)
                  .setAction("reminder")
                  .putExtra("notificationId", 987)
                  .putExtra("title", "StudyDesk reminder test")
                  .putExtra("body", "Notifications are working on this device.")
                  .putExtra("screen", "Next Up"));
        });
    // No account, no cloud: this build is local-only and usable straight after install. The
    // Supabase URL / anon-key fields and the "Account" and "Sync status" blocks went with it —
    // leaving the credentials form behind would have been the sign-in feature with its door
    // removed. CloudClient/CloudSync stay in the tree but are unreachable, since the record kinds
    // and the outbox are shared with the import/export paths.
    flag("aiEnabled", "AI study debrief (opt in)", false);
    NativeForms.heading(
        this,
        ui.fields,
        "AI debrief",
        "When requested, this sends up to 400 characters of your session note to your own"
            + " ai-generate function. Manual study records always work offline.");
    NativeForms.button(this, ui.fields, "Add Next Up widget", () -> pinWidget(DeskWidget.class));
    NativeForms.button(
        this, ui.fields, "Add Upcoming widget", () -> pinWidget(UpcomingWidget.class));
    NativeForms.heading(this, ui.fields, "Your data", "");
    NativeForms.button(
        this,
        ui.fields,
        "Load sample desk",
        () ->
            new AlertDialog.Builder(this)
                .setTitle("Add sample records?")
                .setMessage(
                    "This adds labelled Sample courses and deadlines without replacing your own"
                        + " records.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton(
                    "Add samples",
                    (d, w) ->
                        repo.write(
                            () -> {
                              DemoData.load(repo);
                              runOnUiThread(() -> message("Sample desk added"));
                            }))
                .show());
    NativeForms.button(
        this,
        ui.fields,
        "Export backup",
        () ->
            startActivityForResult(
                new Intent(Intent.ACTION_CREATE_DOCUMENT)
                    .setType("application/zip")
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .putExtra(
                        Intent.EXTRA_TITLE,
                        "StudyDesk-backup-" + java.time.LocalDate.now() + ".zip"),
                801));
    NativeForms.button(
        this,
        ui.fields,
        "Import backup / StudyDesk JSON",
        () ->
            startActivityForResult(
                new Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .setType("*/*")
                    .addCategory(Intent.CATEGORY_OPENABLE),
                802));
    NativeForms.heading(this, ui.fields, "Feedback and diagnostics", "");
    NativeForms.button(this, ui.fields, "Send feedback to your project", this::feedback);
    NativeForms.button(
        this,
        ui.fields,
        "Your feedback status",
        () ->
            CloudSync.NETWORK.execute(
                () -> {
                  try {
                    CloudClient c = new CloudClient(this);
                    org.json.JSONArray rows = c.pull("feedback", repo.owner());
                    StringBuilder text = new StringBuilder();
                    org.json.JSONArray queued = FeedbackQueue.pending(this, repo.owner());
                    for (int n = 0; n < queued.length(); n++)
                      text.append(queued.getJSONObject(n).optString("message"))
                          .append("\nWaiting to send\n\n");
                    for (int i = 0; i < rows.length(); i++) {
                      JSONObject f = rows.getJSONObject(i);
                      text.append(f.optString("message"))
                          .append("\n")
                          .append(
                              (!f.isNull("resolved_at")
                                  ? "resolved"
                                  : !f.isNull("progressed_at")
                                      ? "in progress"
                                      : !f.isNull("triaged_at") ? "reviewed" : "received"))
                          .append("\n\n");
                    }
                    runOnUiThread(
                        () ->
                            new AlertDialog.Builder(this)
                                .setTitle("Your feedback")
                                .setMessage(
                                    text.length() == 0 ? "No feedback yet" : text.toString())
                                .setPositiveButton("Close", null)
                                .show());
                  } catch (Exception e) {
                    runOnUiThread(() -> message(e.getMessage()));
                  }
                }));
    flag("errorReports", "Send error reports to your project", false);
    NativeForms.heading(
        this,
        ui.fields,
        "About StudyDesk Java",
        "Based on Limekana/StudyDesk 1.17.0 (MIT), commit addd6fd. Native Java/XML migration"
            + " assisted by AI for Group 15. Baseline features are attributed to Limecore Studio.");
    NativeForms.button(
        this,
        ui.fields,
        "Original source and licence",
        () ->
            new AlertDialog.Builder(this)
                .setTitle("MIT License · Limecore Studio")
                .setMessage(Licence.text(this))
                .setPositiveButton("Close", null)
                .setNeutralButton(
                    "Source",
                    (d, w) ->
                        startActivity(
                            new Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse(
                                    "https://github.com/Limekana/StudyDesk/tree/addd6fdcf5a69c7d6f23e8daf13e197197a7a998"))))
                .show());
    NativeForms.button(this, ui.fields, "Data and privacy", () -> Privacy.show(this));
    NativeForms.button(
        this,
        ui.fields,
        "Check upstream version",
        () ->
            CloudSync.NETWORK.execute(
                () -> {
                  try {
                    okhttp3.OkHttpClient c = new okhttp3.OkHttpClient();
                    try (okhttp3.Response r =
                        c.newCall(
                                new okhttp3.Request.Builder()
                                    .url(
                                        "https://api.github.com/repos/Limekana/StudyDesk/releases/latest")
                                    .build())
                            .execute()) {
                      if (!r.isSuccessful() || r.body() == null)
                        throw new java.io.IOException("Update check unavailable");
                      JSONObject release = new JSONObject(r.body().string());
                      String tag = release.optString("tag_name");
                      runOnUiThread(
                          () ->
                              new AlertDialog.Builder(this)
                                  .setTitle("Upstream release: " + tag)
                                  .setMessage(
                                      "This native migration is a separate application. Upstream"
                                          + " React APKs cannot update it. Migration baseline:"
                                          + " 1.17.0.")
                                  .setPositiveButton("Close", null)
                                  .show());
                    }
                  } catch (Exception e) {
                    runOnUiThread(() -> message(e.getMessage()));
                  }
                }));
  }

  void pinWidget(Class<? extends android.appwidget.AppWidgetProvider> provider) {
    android.appwidget.AppWidgetManager manager =
        android.appwidget.AppWidgetManager.getInstance(this);
    if (manager.isRequestPinAppWidgetSupported())
      manager.requestPinAppWidget(new ComponentName(this, provider), null, null);
    else message("Add StudyDesk widgets from your launcher's widget picker");
  }

  void feedback() {
    CloudClient c = new CloudClient(this);
    if (c.session() == null) {
      message("Sign in to your own project to send feedback");
      return;
    }
    LinearLayout form = new LinearLayout(this);
    form.setOrientation(LinearLayout.VERTICAL);
    EditText message = NativeForms.text(this, form, "Feedback", "", false);
    final String[] category = {"bug"};
    final String[] rating = {"0"};
    NativeForms.choice(
        this,
        form,
        "Rating",
        new String[] {"0", "1", "2", "3", "4", "5"},
        new String[] {"No rating", "1", "2", "3", "4", "5"},
        "0",
        v -> rating[0] = v);
    NativeForms.choice(
        this,
        form,
        "Category",
        new String[] {"bug", "idea", "praise", "other"},
        new String[] {"bug", "idea", "praise", "other"},
        "bug",
        v -> category[0] = v);
    new AlertDialog.Builder(this)
        .setTitle(tr("Feedback"))
        .setView(form)
        .setNegativeButton(tr("Cancel"), null)
        .setPositiveButton(
            tr("Send"),
            (d, w) -> {
              String text = message.getText().toString().trim();
              if (text.isEmpty() || text.length() > 4000) {
                message("Feedback must contain 1–4000 characters");
                return;
              }
              repo.write(
                  () -> {
                    try {
                      JSONObject record =
                          new JSONObject()
                              .put("id", java.util.UUID.randomUUID().toString())
                              .put("user_id", repo.owner())
                              .put("message", text)
                              .put("category", category[0])
                              .put(
                                  "rating",
                                  rating[0].equals("0")
                                      ? org.json.JSONObject.NULL
                                      : Integer.parseInt(rating[0]))
                              .put("app", "StudyDesk")
                              .put("app_version", BuildConfig.VERSION_NAME);
                      FeedbackQueue.enqueue(this, repo.owner(), record);
                      CloudSync.schedule(this);
                      runOnUiThread(
                          () ->
                              message(
                                  "Feedback saved; it will send when your project is reachable"));
                    } catch (Exception e) {
                      runOnUiThread(() -> message(e.getMessage()));
                    }
                  });
            })
        .show();
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (result != RESULT_OK || data == null || data.getData() == null) return;
    Uri uri = data.getData();
    repo.write(
        () -> {
          try {
            if (request == 801) Backup.exportTo(this, repo, uri);
            else if (request == 802) Backup.importFrom(this, repo, uri);
            runOnUiThread(() -> message(request == 801 ? "Backup exported" : "Backup imported"));
          } catch (Exception e) {
            runOnUiThread(() -> message(e.getMessage()));
          }
        });
  }

  @Override
  public void onRequestPermissionsResult(int request, String[] names, int[] grants) {
    super.onRequestPermissionsResult(request, names, grants);
    repo.write(() -> {});
    message(
        Reminders.allowed(this)
            ? "Notifications enabled"
            : "Notifications remain blocked; local features are available");
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (repo != null) repo.write(() -> {});
  }
}
