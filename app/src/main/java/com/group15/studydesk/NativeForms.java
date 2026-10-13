package com.group15.studydesk;

import android.view.*;
import android.widget.*;
import com.google.android.material.button.*;
import com.google.android.material.switchmaterial.*;
import com.group15.studydesk.databinding.*;

public final class NativeForms {
  public static EditText text(
      BaseActivity a, LinearLayout parent, String label, String value, boolean secret) {
    FieldTextBinding b = FieldTextBinding.inflate(a.getLayoutInflater(), parent, false);
    b.getRoot().setHint(a.tr(label));
    b.input.setText(value);
    b.input.setContentDescription(label);
    if (secret)
      b.input.setInputType(
          android.text.InputType.TYPE_CLASS_TEXT
              | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
    parent.addView(b.getRoot());
    return b.input;
  }

  public static MaterialButton button(
      BaseActivity a, LinearLayout parent, String label, Runnable action) {
    MaterialButton b = new MaterialButton(a);
    b.setText(a.tr(label));
    b.setAllCaps(false);
    parent.addView(b, new LinearLayout.LayoutParams(-1, -2));
    b.setOnClickListener(v -> action.run());
    return b;
  }

  public static void heading(BaseActivity a, LinearLayout parent, String title, String body) {
    TextView h = new TextView(a);
    h.setText(a.tr(title));
    h.setTextSize(24);
    h.setTypeface(android.graphics.Typeface.create("serif", android.graphics.Typeface.BOLD));
    h.setPadding(0, 24, 0, 12);
    parent.addView(h);
    if (body != null && !body.isEmpty()) {
      TextView t = new TextView(a);
      t.setText(a.tr(body));
      t.setTextSize(14);
      t.setPadding(0, 0, 0, 16);
      parent.addView(t);
    }
  }

  public static void choice(
      BaseActivity a,
      LinearLayout parent,
      String label,
      String[] values,
      String[] labels,
      String selected,
      java.util.function.Consumer<String> on) {
    FieldChoiceBinding b = FieldChoiceBinding.inflate(a.getLayoutInflater(), parent, false);
    b.label.setText(a.tr(label));
    String[] displayLabels = new String[labels.length];
    for (int k = 0; k < labels.length; k++) displayLabels[k] = a.tr(labels[k]);
    b.choice.setAdapter(
        new ArrayAdapter<>(a, android.R.layout.simple_spinner_dropdown_item, displayLabels));
    int i = java.util.Arrays.asList(values).indexOf(selected);
    b.choice.setSelection(Math.max(0, i));
    b.choice.setOnItemSelectedListener(
        new AdapterView.OnItemSelectedListener() {
          public void onNothingSelected(AdapterView<?> p) {}

          public void onItemSelected(AdapterView<?> p, View v, int position, long id) {
            on.accept(values[position]);
          }
        });
    parent.addView(b.getRoot());
  }

  public static void toggle(
      BaseActivity a,
      LinearLayout parent,
      String label,
      boolean selected,
      java.util.function.Consumer<Boolean> on) {
    SwitchMaterial s = new SwitchMaterial(a);
    s.setText(a.tr(label));
    s.setMinHeight((int) (56 * a.getResources().getDisplayMetrics().density));
    s.setChecked(selected);
    s.setOnCheckedChangeListener((v, b) -> on.accept(b));
    parent.addView(s);
  }
}
