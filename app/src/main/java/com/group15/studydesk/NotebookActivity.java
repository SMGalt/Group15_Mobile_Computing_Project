package com.group15.studydesk;

import android.content.*;
import android.os.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import com.group15.studydesk.databinding.*;
import java.util.*;
import org.json.*;

public class NotebookActivity extends BaseActivity {
  ActivityEditorBinding ui;
  Repository repo;
  Row note;
  List<NoteLayout.Box> boxes = new ArrayList<>();
  FrameLayout canvas;
  Handler handler = new Handler(Looper.getMainLooper());
  boolean loading = true;
  EditText title;
  int pageWidth;

  @Override
  protected void onCreate(Bundle state) {
    super.onCreate(state);
    ui = ActivityEditorBinding.inflate(getLayoutInflater());
    setContentView(ui.getRoot());
    insets(ui.root);
    repo = new Repository(this);
    ui.toolbar.setTitle(tr("Notebook"));
    ui.toolbar.setNavigationIcon(android.R.drawable.ic_menu_close_clear_cancel);
    ui.toolbar.setNavigationOnClickListener(v -> finish());
    ui.toolbar.getMenu().add(0, 1, 0, tr("Edit details"));
    ui.toolbar.getMenu().add(0, 2, 0, tr("Add text box"));
    ui.toolbar.getMenu().add(0, 3, 0, tr("One column"));
    ui.toolbar.setOnMenuItemClickListener(
        item -> {
          if (item.getItemId() == 1)
            startActivity(
                new Intent(this, EditorActivity.class)
                    .putExtra("kind", "note")
                    .putExtra("id", note.id));
          else if (item.getItemId() == 3) {
            boxes = NoteLayout.read(NoteLayout.join(boxes), null);
            render();
            save();
          } else addBox(.1, boxes.size() * 84);
          return true;
        });
    ui.save.setText(tr("Save note"));
    ui.save.setOnClickListener(
        v -> {
          save();
          message("Saved");
        });
    repo.observe()
        .observe(
            this,
            rows -> {
              if (loading) {
                note = Rules.byId(rows, "note", getIntent().getStringExtra("id"));
                if (note == null) {
                  finish();
                  return;
                }
                note = note.copy();
                boxes = NoteLayout.read(note.s("content"), note.json().opt("layout"));
                title = NativeForms.text(this, ui.fields, "Note title", note.title(), false);
                title.addTextChangedListener(
                    new TextWatcher() {
                      public void beforeTextChanged(CharSequence s, int st, int count, int after) {}

                      public void onTextChanged(CharSequence s, int st, int before, int count) {
                        scheduleSave();
                      }

                      public void afterTextChanged(Editable e) {}
                    });
                canvas = new FrameLayout(this);
                canvas.setBackgroundColor(Appearance.color(this, R.attr.deskSurface));
                ui.fields.addView(
                    canvas, new LinearLayout.LayoutParams(-1, Math.max(800, boxes.size() * 120)));
                canvas.post(
                    () -> {
                      pageWidth = canvas.getWidth();
                      render();
                    });
                canvas.setOnTouchListener(
                    (v, e) -> {
                      if (e.getAction() == MotionEvent.ACTION_UP) {
                        addBox(
                            e.getX() / Math.max(1, pageWidth),
                            e.getY() / getResources().getDisplayMetrics().density);
                        return true;
                      }
                      return true;
                    });
                loading = false;
              }
            });
  }

  void addBox(double x, double y) {
    NoteLayout.Box box = NoteLayout.box(x, y, .56, "");
    boxes.add(box);
    render();
    editBox(box.id());
  }

  void render() {
    if (canvas == null || pageWidth == 0) return;
    canvas.removeAllViews();
    float scale = getResources().getDisplayMetrics().density;
    int bottom = 800;
    for (NoteLayout.Box box : boxes) {
      LinearLayout card = new LinearLayout(this);
      card.setOrientation(LinearLayout.VERTICAL);
      card.setPadding(6, 4, 6, 8);
      LinearLayout handles = new LinearLayout(this);
      TextView move = NativeNoteRenderer.text(this, "⋮⋮");
      move.setContentDescription("Move text box");
      move.setMinHeight((int) (40 * scale));
      handles.addView(move, new LinearLayout.LayoutParams(0, -2, 1));
      TextView resize = NativeNoteRenderer.text(this, "↔");
      resize.setContentDescription("Resize text box");
      resize.setMinHeight((int) (40 * scale));
      handles.addView(resize);
      TextView edit = NativeNoteRenderer.text(this, "✎");
      edit.setContentDescription("Edit text box");
      handles.addView(edit);
      edit.setOnClickListener(v -> editBox(box.id()));
      TextView delete = NativeNoteRenderer.text(this, "×");
      delete.setContentDescription("Delete text box");
      handles.addView(delete);
      delete.setOnClickListener(
          v ->
              new AlertDialog.Builder(this)
                  .setTitle("Delete this text box?")
                  .setNegativeButton("Cancel", null)
                  .setPositiveButton(
                      "Delete",
                      (d, w) -> {
                        boxes.removeIf(b -> b.id().equals(box.id()));
                        if (boxes.isEmpty()) boxes.add(NoteLayout.box(0, 0, 1, ""));
                        render();
                        save();
                      })
                  .show());
      card.addView(handles);
      card.addView(
          NativeNoteRenderer.render(
              this, box.text(), text -> replace(box.id(), box.x(), box.y(), box.w(), text)));
      card.setOnClickListener(v -> editBox(box.id()));
      FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams((int) (box.w() * pageWidth), -2);
      lp.leftMargin = (int) (box.x() * pageWidth);
      lp.topMargin = (int) (box.y() * scale);
      canvas.addView(card, lp);
      bottom = Math.max(bottom, lp.topMargin + 400);
      move.setOnTouchListener(
          new View.OnTouchListener() {
            float x, y;

            public boolean onTouch(View v, MotionEvent e) {
              if (e.getAction() == MotionEvent.ACTION_DOWN) {
                x = e.getRawX();
                y = e.getRawY();
                v.getParent().requestDisallowInterceptTouchEvent(true);
                return true;
              }
              if (e.getAction() == MotionEvent.ACTION_MOVE) {
                lp.leftMargin =
                    Math.max(
                        0, Math.min(pageWidth - lp.width, lp.leftMargin + (int) (e.getRawX() - x)));
                lp.topMargin = Math.max(0, lp.topMargin + (int) (e.getRawY() - y));
                x = e.getRawX();
                y = e.getRawY();
                card.setLayoutParams(lp);
                return true;
              }
              if (e.getAction() == MotionEvent.ACTION_UP) {
                replace(
                    box.id(),
                    lp.leftMargin / (double) pageWidth,
                    lp.topMargin / scale,
                    box.w(),
                    box.text());
                return true;
              }
              return true;
            }
          });
      resize.setOnTouchListener(
          new View.OnTouchListener() {
            float x;

            public boolean onTouch(View v, MotionEvent e) {
              if (e.getAction() == MotionEvent.ACTION_DOWN) {
                x = e.getRawX();
                v.getParent().requestDisallowInterceptTouchEvent(true);
                return true;
              }
              if (e.getAction() == MotionEvent.ACTION_MOVE) {
                lp.width =
                    Math.max(
                        (int) (.18 * pageWidth),
                        Math.min(pageWidth - lp.leftMargin, lp.width + (int) (e.getRawX() - x)));
                x = e.getRawX();
                card.setLayoutParams(lp);
                return true;
              }
              if (e.getAction() == MotionEvent.ACTION_UP)
                replace(box.id(), box.x(), box.y(), lp.width / (double) pageWidth, box.text());
              return true;
            }
          });
    }
    canvas.setLayoutParams(new LinearLayout.LayoutParams(-1, bottom));
  }

  void replace(String id, double x, double y, double w, String text) {
    for (int i = 0; i < boxes.size(); i++)
      if (boxes.get(i).id().equals(id)) {
        NoteLayout.Box normalized = NoteLayout.box(x, y, w, text);
        boxes.set(i, new NoteLayout.Box(id, normalized.x(), normalized.y(), normalized.w(), text));
        break;
      }
    render();
    scheduleSave();
  }

  void editBox(String id) {
    NoteLayout.Box selected =
        boxes.stream().filter(b -> b.id().equals(id)).findFirst().orElse(null);
    if (selected == null) return;
    LinearLayout form = new LinearLayout(this);
    form.setOrientation(LinearLayout.VERTICAL);
    form.setPadding(20, 0, 20, 0);
    EditText input = NativeForms.text(this, form, "Text box source", selected.text(), false);
    input.setInputType(
        android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
    input.setMinLines(6);
    input.setGravity(Gravity.TOP);
    input.addTextChangedListener(
        new TextWatcher() {
          public void beforeTextChanged(CharSequence s, int st, int count, int after) {}

          public void onTextChanged(CharSequence s, int st, int before, int count) {
            for (int i = 0; i < boxes.size(); i++)
              if (boxes.get(i).id().equals(id)) {
                NoteLayout.Box b = boxes.get(i);
                boxes.set(i, new NoteLayout.Box(b.id(), b.x(), b.y(), b.w(), s.toString()));
                break;
              }
            scheduleSave();
          }

          public void afterTextChanged(Editable e) {}
        });

    HorizontalScrollView scroll = new HorizontalScrollView(this);
    LinearLayout bar = new LinearLayout(this);
    scroll.addView(bar);
    form.addView(scroll);
    for (String mark :
        new String[] {
          "Bold",
          "Italic",
          "Underline",
          "Important",
          "Query",
          "Confirmed",
          "Heading",
          "List",
          "Checklist",
          "Math / chemistry"
        }) {
      Button button = new Button(this);
      button.setText(tr(mark));
      bar.addView(button);
      button.setOnClickListener(
          v -> {
            int start = Math.max(0, input.getSelectionStart()),
                end = Math.max(start, input.getSelectionEnd());
            String selectedText = input.getText().toString().substring(start, end);
            String delimiter =
                switch (mark) {
                  case "Bold" -> "**";
                  case "Italic" -> "*";
                  case "Underline" -> "__";
                  case "Important" -> "==";
                  case "Query" -> "=2=";
                  case "Confirmed" -> "=3=";
                  case "Math / chemistry" -> "$$";
                  default -> "";
                };
            String prefix =
                switch (mark) {
                  case "Heading" -> "# ";
                  case "List" -> "- ";
                  case "Checklist" -> "- [ ] ";
                  default -> delimiter;
                };
            input.getText().replace(start, end, prefix + selectedText + delimiter);
            input.setSelection(
                start + prefix.length(), start + prefix.length() + selectedText.length());
          });
    }
    new AlertDialog.Builder(this)
        .setTitle("Edit text box")
        .setView(form)
        .setPositiveButton(
            "Done",
            (d, w) ->
                replace(id, selected.x(), selected.y(), selected.w(), input.getText().toString()))
        .setNeutralButton(
            "Preview",
            (d, w) -> {
              replace(id, selected.x(), selected.y(), selected.w(), input.getText().toString());
            })
        .setNegativeButton(
            "Close",
            (d, w) -> {
              render();
              save();
            })
        .show();
  }

  void scheduleSave() {
    handler.removeCallbacksAndMessages(null);
    handler.postDelayed(this::save, 700);
  }

  void save() {
    if (note == null || title == null) return;
    try {
      note.put("title", title.getText().toString())
          .put("content", NoteLayout.join(boxes))
          .put("layout", NoteLayout.write(boxes));
      Row snapshot = note.copy();
      repo.write(
          () -> {
            repo.save(snapshot);
            runOnUiThread(() -> note = snapshot);
          });
    } catch (JSONException e) {
      message("Note could not be saved");
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (note != null)
      Repository.IO.execute(
          () -> {
            Row stored = repo.find("note", note.id);
            if (stored != null && !stored.updatedAt.equals(note.updatedAt))
              runOnUiThread(
                  () -> {
                    note = stored.copy();
                    boxes = NoteLayout.read(note.s("content"), note.json().opt("layout"));
                    title.setText(note.title());
                    render();
                  });
          });
  }

  @Override
  protected void onPause() {
    super.onPause();
    handler.removeCallbacksAndMessages(null);
    if (!loading) save();
  }
}
