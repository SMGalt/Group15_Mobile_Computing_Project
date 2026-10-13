package com.group15.studydesk;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import java.util.*;

/** Ports the three baseline diagram primitives: chain, qualitative axes, two-level tree. */
public class NoteDiagramView extends View {
  final String source;
  final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

  public NoteDiagramView(Context c, String s) {
    super(c);
    source = s;
    setContentDescription(s);
  }

  @Override
  protected void onDraw(Canvas c) {
    super.onDraw(c);
    p.setColor(Appearance.color(getContext(), R.attr.deskInk));
    p.setTextSize(14 * getResources().getDisplayMetrics().scaledDensity);
    p.setStrokeWidth(2);
    float width = getWidth(), height = getHeight();
    if (source.startsWith("graph:")) {
      float x = 32, y = height - 28;
      c.drawLine(x, 12, x, y, p);
      c.drawLine(x, y, width - 16, y, p);
      String shape = "";
      for (String s :
          new String[] {"linear", "up-curve", "down-curve", "s-curve", "peak", "decay", "step"})
        if (source.contains(s)) shape = s;
      double[][] points =
          switch (shape) {
            case "linear" -> new double[][] {{0, 0}, {1, 1}};
            case "up-curve" -> new double[][] {{0, 0}, {.35, .12}, {.65, .38}, {.85, .66}, {1, 1}};
            case "down-curve" ->
                new double[][] {{0, 1}, {.15, .66}, {.35, .38}, {.65, .12}, {1, 0}};
            case "s-curve" ->
                new double[][] {
                  {0, .02}, {.2, .06}, {.4, .25}, {.5, .5}, {.6, .75}, {.8, .94}, {1, .98}
                };
            case "peak" -> new double[][] {{0, .05}, {.25, .45}, {.5, .95}, {.75, .45}, {1, .05}};
            case "decay" ->
                new double[][] {{0, 1}, {.15, .62}, {.3, .4}, {.5, .22}, {.75, .09}, {1, .04}};
            case "step" -> new double[][] {{0, .05}, {.45, .05}, {.5, .9}, {1, .9}};
            default -> new double[0][];
          };
      Path path = new Path();
      double plateau = points.length > 0 ? points[(int) (points.length * .66)][1] : 0;
      for (int i = 0; i < points.length; i++) {
        double py =
            source.contains("plateau") && i >= (int) (points.length * .66) ? plateau : points[i][1];
        float px = x + (float) points[i][0] * (width - 60), yp = y - (float) py * (height - 55);
        if (i == 0) path.moveTo(px, yp);
        else path.lineTo(px, yp);
      }
      p.setStyle(Paint.Style.STROKE);
      c.drawPath(path, p);
      p.setStyle(Paint.Style.FILL);
      c.drawText(source.substring(6), 40, height - 5, p);
      return;
    }
    if (source.startsWith("tree:")) {
      String[] lines = source.split("\n");
      float y = 26;
      for (int i = 0; i < lines.length; i++) {
        String line = lines[i];
        if (i == 0) line = line.substring(5).trim();
        if (line.isEmpty()) continue;
        boolean child = line.startsWith("\t") || line.startsWith("  ");
        float x = child ? 52 : 12;
        if (child) {
          c.drawLine(24, y - 25, 24, y, p);
          c.drawLine(24, y - 5, x - 5, y - 5, p);
        }
        c.drawText(line.trim(), x, y, p);
        y += 34;
      }
      return;
    }
    String[] nodes = source.replaceAll("->\\s*$", "").split("->");
    int columns = Math.max(1, (int) (width / 130));
    float cell = width / columns;
    for (int i = 0; i < nodes.length; i++) {
      float x = (i % columns) * cell + 5, y = (i / columns) * 52 + 8;
      p.setStyle(Paint.Style.STROKE);
      c.drawRoundRect(x, y, x + cell - 26, y + 38, 6, 6, p);
      p.setStyle(Paint.Style.FILL);
      String label = nodes[i].trim();
      while (p.measureText(label) > cell - 38 && label.length() > 2)
        label = label.substring(0, label.length() - 1);
      c.drawText(label, x + 6, y + 25, p);
      if (i < nodes.length - 1) c.drawText("→", x + cell - 24, y + 25, p);
    }
    if (source.trim().endsWith("->")) c.drawText("↩", width - 30, height - 8, p);
  }
}
