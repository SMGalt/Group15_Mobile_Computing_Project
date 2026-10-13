package com.group15.studydesk;

import android.content.*;
import android.os.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.textfield.*;
import com.group15.studydesk.databinding.*;
import java.time.*;
import java.util.*;
import org.json.*;

public class EditorActivity extends BaseActivity {
  ActivityEditorBinding ui;
  Repository repo;
  Row row;
  List<Row> all = new ArrayList<>();
  final Map<String, View> inputs = new LinkedHashMap<>();
  final Map<String, List<String>> values = new HashMap<>();
  boolean initialized = false, existing = false;
  Handler handler = new Handler(Looper.getMainLooper());
  Runnable autoSave;

  @Override
  protected void onCreate(Bundle state) {
    super.onCreate(state);
    ui = ActivityEditorBinding.inflate(getLayoutInflater());
    setContentView(ui.getRoot());
    insets(ui.root);
    repo = new Repository(this);
    String kind = getIntent().getStringExtra("kind");
    if (kind == null) kind = "assignment";
    row = new Row(kind);
    String id = getIntent().getStringExtra("id");
    existing = id != null;
    if (id != null) row.id = id;
    if (state != null && state.containsKey("payload")) {
      row.payload = state.getString("payload");
      row.id = state.getString("id");
    }
    String rawTitle = (existing ? "Edit " : "Add ") + row.kind;
    String editorTitle = tr(rawTitle);
    if (editorTitle.equals(rawTitle)) {
      boolean isZh = "zh".equals(getSharedPreferences("settings", 0).getString("language", "en"));
      editorTitle = isZh ? (existing ? "编辑" : "添加") + tr(row.kind) : (existing ? "Edit " : "Add ") + tr(row.kind);
    }
    ui.toolbar.setTitle(editorTitle);
    ui.toolbar.setNavigationIcon(android.R.drawable.ic_menu_close_clear_cancel);
    ui.toolbar.setNavigationOnClickListener(v -> finish());
    ui.save.setText(tr("Save"));
    ui.save.setOnClickListener(v -> save(false));
    repo.observe()
        .observe(
            this,
            rows -> {
              all = rows;
              if (!initialized) {
                if (existing && state == null) {
                  Row stored = Rules.byId(rows, row.kind, row.id);
                  if (stored == null) {
                    message("Record no longer exists");
                    finish();
                    return;
                  }
                  row = stored.copy();
                }
                build();
                initialized = true;
              }
            });
  }

  private void build() {
    if (!existing && row.kind.equals("planned") && getIntent().hasExtra("plannedStarts"))
      row.put("startsAt", getIntent().getStringExtra("plannedStarts"))
          .put("durationMinutes", getIntent().getIntExtra("plannedDuration", 25));
    if (!existing && getIntent().hasExtra("selectedDate")) {
      String selected = getIntent().getStringExtra("selectedDate");
      switch (row.kind) {
        case "assignment", "exam" -> row.put("dueDate", selected);
        case "note" -> row.put("lessonDate", selected);
        case "planned" -> row.put("startsAt", selected + "T17:00");
        case "session" -> row.put("startedAt", selected + "T09:00");
        case "commitment" -> row.put("startsOn", selected);
      }
    }
    String shared = getIntent().getStringExtra("shared");
    if (shared != null && !existing) row.put("content", shared);
    // A custom assignment type is stored AS the type, so a row whose type is none of the fixed
    // options means "Other" plus that label — and `type` may be the only place it survives, because
    // the web app never stored the label separately. Recover both halves here; without them the
    // spinner falls back to its first entry and the next save files the assignment under Essay.
    String customType = "";
    for (FormSpec.Field field : FormSpec.fields(row.kind))
      if (field.key().equals("type")
          && !row.s("type").isEmpty()
          && !Arrays.asList(field.options().split("\\|", -1)).contains(row.s("type")))
        customType = row.s("type");
    for (FormSpec.Field field : FormSpec.fields(row.kind)) {
      String val = row.json().has(field.key()) ? row.s(field.key()) : field.initial();
      if ((field.type().equals("course") || field.type().equals("term")) && val.isEmpty())
        val = getIntent().getStringExtra(field.type().equals("course") ? "course" : "term");
      if (val == null) val = "";
      if (field.type().equals("datetime") && !val.isEmpty()) {
        try {
          val = Times.local(val).toString();
        } catch (Exception ignored) {
        }
      }
      if (field.type().equals("choice")
          || field.type().equals("course")
          || field.type().equals("term")) {
        FieldChoiceBinding b = FieldChoiceBinding.inflate(getLayoutInflater(), ui.fields, false);
        b.label.setText(tr(field.label()));
        List<String> ids = new ArrayList<>(), labels = new ArrayList<>();
        if (field.type().equals("choice")) {
          for (String option : field.options().split("\\|", -1)) {
            ids.add(option);
            labels.add(option.isEmpty() ? tr("None") : tr(option));
          }
        } else {
          ids.add("");
          labels.add(tr("None"));
          for (Row r : Rules.kind(all, field.type())) {
            if (r.id.equals(row.id)) continue;
            ids.add(r.id);
            labels.add(r.title() + (r.s("archivedAt").isEmpty() ? "" : " (archived)"));
          }
        }
        b.choice.setAdapter(
            new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
        int at = ids.indexOf(val);
        // A stored value outside the fixed list is not a reason to quietly select the first option.
        // "Other" is where a free-text value belongs, and it is the field that reveals the box
        // holding it.
        if (at < 0) at = Math.max(0, ids.indexOf("Other"));
        b.choice.setSelection(at);
        inputs.put(field.key(), b.choice);
        values.put(field.key(), ids);
        b.choice.setContentDescription(field.label());
        ui.fields.addView(b.getRoot());
      } else {
        FieldTextBinding b = FieldTextBinding.inflate(getLayoutInflater(), ui.fields, false);
        b.getRoot().setHint(tr(field.label()));
        if (field.key().equals("customType") && val.isEmpty()) val = customType;
        b.input.setText(val);
        b.input.setTag(field.key());
        b.input.setContentDescription(field.label());
        if (field.type().equals("multiline")) {
          b.input.setSingleLine(false);
          b.input.setMinLines(field.key().equals("content") ? 10 : 3);
          b.input.setInputType(
              android.text.InputType.TYPE_CLASS_TEXT
                  | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                  | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
          b.input.setGravity(Gravity.TOP);
        } else if (field.type().equals("number"))
          b.input.setInputType(
              android.text.InputType.TYPE_CLASS_NUMBER
                  | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                  | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
        else if (field.type().equals("date")
            || field.type().equals("time")
            || field.type().equals("datetime")) {
          b.getRoot().setEndIconMode(TextInputLayout.END_ICON_CUSTOM);
          b.getRoot().setEndIconDrawable(android.R.drawable.ic_menu_today);
          b.getRoot().setEndIconContentDescription("Choose " + field.label());
          b.getRoot().setEndIconOnClickListener(v -> pick(b.input, field.type()));
          b.input.setSingleLine(true);
        }
        inputs.put(field.key(), b.input);
        ui.fields.addView(b.getRoot());
      }
    }
    if (row.kind.equals("note")) {
      LinearLayout bar = new LinearLayout(this);
      bar.setOrientation(LinearLayout.HORIZONTAL);
      HorizontalScrollView scroll = new HorizontalScrollView(this);
      scroll.addView(bar);
      ui.fields.addView(scroll);
      for (String text : new String[] {"Bold", "Heading", "Checklist", "Code", "Math", "Preview"}) {
        Button b = new Button(this);
        b.setText(tr(text));
        bar.addView(b);
        b.setOnClickListener(
            v -> {
              EditText content = (EditText) inputs.get("content");
              if (text.equals("Preview")) {
                new AlertDialog.Builder(this)
                    .setTitle(row.title())
                    .setMessage(NoteRenderer.render(content.getText().toString()))
                    .setPositiveButton("Close", null)
                    .show();
                return;
              }
              String inserted =
                  switch (text) {
                    case "Bold" -> "**text**";
                    case "Heading" -> "\n## Heading\n";
                    case "Checklist" -> "\n- [ ] task";
                    case "Code" -> "\n```\ncode\n```\n";
                    default -> "\n$$ x^2 + y^2 $$\n";
                  };
              int at = Math.max(0, content.getSelectionStart());
              content.getText().insert(at, inserted);
            });
      }
      autoSave =
          () -> {
            if (existing && !isFinishing()) save(true);
          };
      ((EditText) inputs.get("content"))
          .addTextChangedListener(
              new TextWatcher() {
                public void beforeTextChanged(CharSequence s, int st, int c, int a) {}

                public void onTextChanged(CharSequence s, int st, int before, int count) {
                  handler.removeCallbacks(autoSave);
                  handler.postDelayed(autoSave, 900);
                }

                public void afterTextChanged(Editable e) {}
              });
    }
  }

  private void pick(EditText input, String type) {
    if (type.equals("time")) {
      LocalTime t;
      try {
        t = LocalTime.parse(input.getText());
      } catch (Exception e) {
        t = LocalTime.of(9, 0);
      }
      new android.app.TimePickerDialog(
              this,
              (v, h, m) -> input.setText(String.format(Locale.US, "%02d:%02d", h, m)),
              t.getHour(),
              t.getMinute(),
              true)
          .show();
      return;
    }
    LocalDate d;
    try {
      d = LocalDate.parse(input.getText().toString().split("T")[0]);
    } catch (Exception e) {
      d = LocalDate.now();
    }
    new android.app.DatePickerDialog(
            this,
            (v, y, m, day) -> {
              String chosen = LocalDate.of(y, m + 1, day).toString();
              if (type.equals("datetime")) {
                new android.app.TimePickerDialog(
                        this,
                        (x, h, min) ->
                            input.setText(chosen + String.format(Locale.US, "T%02d:%02d", h, min)),
                        9,
                        0,
                        true)
                    .show();
              } else input.setText(chosen);
            },
            d.getYear(),
            d.getMonthValue() - 1,
            d.getDayOfMonth())
        .show();
  }

  private Row collect() {
    Row copy = row.copy();
    for (FormSpec.Field f : FormSpec.fields(row.kind)) {
      View v = inputs.get(f.key());
      String value =
          v instanceof Spinner
              ? values.get(f.key()).get(((Spinner) v).getSelectedItemPosition())
              : (f.key().equals("content")
                  ? ((EditText) v).getText().toString()
                  : ((EditText) v).getText().toString().trim());
      Object data = value.isEmpty() ? null : value;
      if (f.type().equals("number") && !value.isEmpty()) {
        try {
          data = Double.parseDouble(value);
        } catch (Exception e) {
          throw new IllegalArgumentException("Check " + f.label());
        }
      }
      copy.put(f.key(), data);
    }
    return copy;
  }

  private void save(boolean autosave) {
    try {
      Row next = collect();
      android.content.SharedPreferences p = getSharedPreferences("settings", 0);
      FormSpec.validate(
          next,
          all,
          p.getString("gradeMode", "ib"),
          Double.parseDouble(p.getString("scaleMin", "0")),
          Double.parseDouble(p.getString("scaleMax", "10")));
      for (FormSpec.Field field : FormSpec.fields(next.kind))
        if (field.type().equals("datetime") && !next.s(field.key()).isEmpty())
          next.put(field.key(), Times.editedInstant(row.s(field.key()), next.s(field.key())));
      if (!autosave) ui.save.setEnabled(false);
      repo.write(
          () -> {
            repo.save(next);
            if (!existing && next.kind.equals("planned")) {
              int count = (int) next.n("repeatCount", 1), weeks = (int) next.n("repeatWeeks", 1);
              if (count == 0)
                count =
                    Rules.repeatDates(
                            Times.local(next.s("startsAt")).toLocalDate(),
                            weeks,
                            Rules.repeatHorizon(Times.local(next.s("startsAt")).toLocalDate(), all))
                        .size();
              if (count > 1) {
                String series = UUID.randomUUID().toString();
                next.put("seriesId", series);
                repo.save(next);
                for (int i = 1; i < count; i++) {
                  Row repeated = next.copy();
                  repeated.id = UUID.randomUUID().toString();
                  repeated.put(
                      "startsAt",
                      Times.local(next.s("startsAt")).plusWeeks((long) i * weeks).toString());
                  repo.save(repeated);
                }
              }
            }
            if (!existing
                && next.kind.equals("lesson")
                && !next.s("additionalWeekdays").isEmpty()) {
              String series = UUID.randomUUID().toString();
              next.put("seriesId", series);
              repo.save(next);
              Set<Integer> days = new HashSet<>();
              days.add((int) next.n("weekday", 1));
              for (String s : next.s("additionalWeekdays").split(",")) {
                int day = Integer.parseInt(s.trim());
                if (day < 0 || day > 6 || !days.add(day)) continue;
                Row repeated = next.copy();
                repeated.id = UUID.randomUUID().toString();
                repeated.put("weekday", day);
                repo.save(repeated);
              }
            }
            runOnUiThread(
                () -> {
                  row = next;
                  existing = true;
                  if (!autosave) {
                    message("Saved");
                    finish();
                  }
                });
          });
    } catch (Exception e) {
      if (!autosave) message(e.getMessage());
    }
  }

  @Override
  protected void onSaveInstanceState(Bundle state) {
    super.onSaveInstanceState(state);
    if (initialized) {
      state.putString("payload", collect().payload);
      state.putString("id", row.id);
    }
  }

  @Override
  protected void onPause() {
    super.onPause();
    handler.removeCallbacksAndMessages(null);
    if (initialized && existing && row.kind.equals("note")) save(true);
  }
}
