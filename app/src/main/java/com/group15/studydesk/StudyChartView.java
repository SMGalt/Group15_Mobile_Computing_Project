package com.group15.studydesk;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;

/** Native, responsive Canvas charts. Values are recomputed from Room records. */
public class StudyChartView extends View {
  double[] values = new double[0];
  boolean heat = false;
  Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

  public StudyChartView(Context c, AttributeSet a) {
    super(c, a);
  }

  public void show(double[] v, boolean heat) {
    values = v == null ? new double[0] : v;
    this.heat = heat;
    setVisibility(values.length == 0 ? GONE : VISIBLE);
    invalidate();
  }

  protected void onDraw(Canvas c) {
    super.onDraw(c);
    double max = 1;
    for (double v : values) if (Double.isFinite(v)) max = Math.max(max, v);
    int accent = androidx.core.content.ContextCompat.getColor(getContext(), R.color.accent);
    if (heat) {
      float size = Math.min(getHeight() / 7f, getWidth() / (float) Math.ceil(values.length / 7f));
      for (int i = 0; i < values.length; i++) {
        paint.setColor(accent);
        paint.setAlpha(values[i] == 0 ? 25 : (int) (60 + 195 * values[i] / max));
        float x = (i / 7) * size, y = (i % 7) * size;
        c.drawRoundRect(x + 2, y + 2, x + size - 2, y + size - 2, 3, 3, paint);
      }
    } else {
      float width = getWidth() / (float) Math.max(1, values.length);
      paint.setColor(accent);
      for (int i = 0; i < values.length; i++) {
        if (!Double.isFinite(values[i])) continue;
        float h = (float) (values[i] / max * (getHeight() - 12));
        c.drawRoundRect(
            i * width + 4, getHeight() - h - 6, (i + 1) * width - 4, getHeight() - 6, 4, 4, paint);
      }
    }
  }
}
