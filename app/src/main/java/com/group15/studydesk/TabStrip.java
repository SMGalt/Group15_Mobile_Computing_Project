package com.group15.studydesk;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.widget.LinearLayout;
import com.google.android.material.button.MaterialButton;
import java.util.*;
import java.util.function.IntConsumer;

/**
 * A segmented pill of equally weighted tabs — the app's one sub-navigation control.
 *
 * <p>Ports the original's {@code .timer-subtabs} strip: a hairline-outlined pill whose selected
 * segment is filled with ink and whose unselected segments are bare. The original runs one of these
 * under Plan (List / Calendar / Timetable), under Grades (Grades / Trends) and under Timer (Timer /
 * Notebook / Log / Stats), and the port had no equivalent at all — every one of those was a separate
 * top-level destination instead.
 *
 * <p>Built in code rather than inflated because the segment count differs per strip and the app has
 * no other tab widget to reuse.
 */
public class TabStrip extends LinearLayout {
  private final List<MaterialButton> buttons = new ArrayList<>();
  private final List<String> labels = new ArrayList<>();
  private IntConsumer onSelect;
  private int selected = -1;
  private boolean bar;

  public TabStrip(Context c) {
    this(c, null);
  }

  public TabStrip(Context c, AttributeSet a) {
    super(c, a);
    setOrientation(HORIZONTAL);
    setBackground(outline());
  }

  /**
   * Draw as the bottom bar rather than a segmented pill: no outline, no filled segment, and the
   * selected label in ink instead of paper-on-ink. The two are one widget because they want the
   * same thing — N equally weighted labels, one of them current — and differ only in paint.
   */
  public void setBar(boolean bar) {
    this.bar = bar;
    setBackground(bar ? null : outline());
    // Re-label, not just re-paint: the case of the label is part of the difference between the two
    // treatments, so a strip that is switched after setTabs has to be re-lettered too.
    for (int i = 0; i < buttons.size(); i++)
      buttons.get(i).setText(label(i));
    if (!buttons.isEmpty()) select(selected);
  }

  private String label(int i) {
    String raw = labels.get(i);
    String translated = Language.translate(getContext(), raw);
    return bar ? translated : translated.toUpperCase(Locale.ROOT);
  }

  /**
   * Replace the strip's segments. {@code selected} is clamped, and setting it this way never fires
   * {@code onSelect} — the caller is stating where it already is, not asking to move.
   */
  public void setTabs(List<String> next, int selected, IntConsumer onSelect) {
    this.onSelect = onSelect;
    labels.clear();
    labels.addAll(next);
    removeAllViews();
    buttons.clear();
    for (int i = 0; i < labels.size(); i++) {
      MaterialButton b =
          new MaterialButton(getContext(), null, com.google.android.material.R.attr.borderlessButtonStyle);
      // The original's two treatments differ in more than fill: its bottom bar reads "Study" while
      // its sub-tab pills read "LIST". Casing is part of which one this is, so it follows `bar`.
      b.setText(label(i));
      b.setTextSize(11);
      b.setAllCaps(false);
      b.setLetterSpacing(0.06f);
      b.setMinWidth(0);
      b.setMinimumWidth(0);
      b.setPadding(0, 0, 0, 0);
      b.setInsetTop(0);
      b.setInsetBottom(0);
      b.setBackground(null);
      b.setBackgroundTintList(null);
      final int index = i;
      // The strip does not move itself on a click: the caller does, via select(). A tab whose
      // destination is a separate activity (Timer) has to be able to refuse the move, and a widget
      // that has already painted the new selection cannot un-paint it.
      b.setOnClickListener(
          v -> {
            if (index == this.selected) return;
            if (this.onSelect != null) this.onSelect.accept(index);
          });
      addView(b, new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1));
      buttons.add(b);
    }
    select(selected);
  }

  /** Move the highlight without notifying. -1 clears it. Safe before {@link #setTabs}. */
  public void select(int index) {
    if (buttons.isEmpty()) return;
    selected = index == -1 ? -1 : Math.max(0, Math.min(buttons.size() - 1, index));
    for (int i = 0; i < buttons.size(); i++) {
      MaterialButton b = buttons.get(i);
      boolean on = i == selected;
      if (bar) {
        b.setTextColor(Appearance.color(getContext(), on ? R.attr.deskInk : R.attr.deskMuted));
        b.setBackground(null);
      } else {
        b.setTextColor(Appearance.color(getContext(), on ? R.attr.deskPaper : R.attr.deskMuted));
        b.setBackground(on ? solid() : null);
      }
    }
  }

  public int selected() {
    return selected;
  }

  public List<String> labels() {
    return Collections.unmodifiableList(labels);
  }

  private GradientDrawable outline() {
    GradientDrawable d = new GradientDrawable();
    d.setCornerRadius(dp(4));
    d.setColor(0x00000000);
    d.setStroke(Math.max(1, dp(1)), Appearance.color(getContext(), R.attr.deskLine));
    return d;
  }

  private GradientDrawable solid() {
    GradientDrawable d = new GradientDrawable();
    d.setCornerRadius(dp(4));
    d.setColor(Appearance.color(getContext(), R.attr.deskInk));
    return d;
  }

  private int dp(int v) {
    return (int)
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
  }
}
