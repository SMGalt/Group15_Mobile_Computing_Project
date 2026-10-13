package com.group15.studydesk;

import android.graphics.*;
import android.text.style.ReplacementSpan;
import java.util.*;

/** Fractions, roots and accents use native Canvas; parser enforces the baseline grammar. */
public class MathSpan extends ReplacementSpan {
  final List<NoteSyntax.Node> nodes;

  public MathSpan(List<NoteSyntax.Node> n) {
    nodes = n;
  }

  float width(List<NoteSyntax.Node> list, Paint p) {
    float w = 0;
    for (NoteSyntax.Node n : list) {
      if (n.type().equals("text")) w += p.measureText(n.text());
      else if (n.type().equals("frac") || n.type().equals("binom"))
        w += Math.max(width(n.first(), p) * .8f, width(n.second(), p) * .8f) + 12;
      else w += width(n.first(), p) + (n.type().equals("root") ? p.measureText("√") : 4);
    }
    return w;
  }

  @Override
  public int getSize(Paint p, CharSequence text, int start, int end, Paint.FontMetricsInt f) {
    if (f != null) {
      Paint.FontMetricsInt metrics = p.getFontMetricsInt();
      f.ascent = (int) (metrics.ascent * 1.5);
      f.descent = (int) (metrics.descent * 1.5 + 6);
      f.top = f.ascent;
      f.bottom = f.descent;
    }
    return (int) Math.ceil(width(nodes, p));
  }

  @Override
  public void draw(
      Canvas c, CharSequence t, int start, int end, float x, int top, int y, int bottom, Paint p) {
    drawNodes(c, nodes, x, y, p);
  }

  float drawNodes(Canvas c, List<NoteSyntax.Node> list, float x, float y, Paint p) {
    for (NoteSyntax.Node n : list) {
      if (n.type().equals("text")) {
        c.drawText(n.text(), x, y, p);
        x += p.measureText(n.text());
      } else if (n.type().equals("frac") || n.type().equals("binom")) {
        float w = Math.max(width(n.first(), p) * .8f, width(n.second(), p) * .8f) + 12;
        Paint small = new Paint(p);
        small.setTextSize(p.getTextSize() * .8f);
        drawNodes(
            c, n.first(), x + (w - width(n.first(), small)) / 2, y - p.getTextSize() * .65f, small);
        drawNodes(
            c,
            n.second(),
            x + (w - width(n.second(), small)) / 2,
            y + p.getTextSize() * .55f,
            small);
        c.drawLine(x + 3, y - p.getTextSize() * .25f, x + w - 3, y - p.getTextSize() * .25f, p);
        x += w;
      } else {
        if (n.type().equals("root")) {
          String root = n.text().equals("cbrt") ? "∛" : "√";
          c.drawText(root, x, y, p);
          x += p.measureText(root);
        }
        float beginning = x;
        x = drawNodes(c, n.first(), x, y, p);
        if (n.type().equals("accent")) {
          c.drawLine(beginning, y - p.getTextSize(), x, y - p.getTextSize(), p);
          if (n.text().equals("vec"))
            c.drawLine(x - 4, y - p.getTextSize() - 3, x, y - p.getTextSize(), p);
        }
        x += 4;
      }
    }
    return x;
  }
}
