package com.group15.studydesk;

import android.content.Context;
import android.text.*;
import android.text.style.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.regex.*;

/**
 * Native block preview. The active box edits ordinary source text to keep Android IME composition
 * intact.
 */
public final class NativeNoteRenderer {
  public static LinearLayout render(
      Context c, String source, java.util.function.Consumer<String> change) {
    LinearLayout body = new LinearLayout(c);
    body.setOrientation(LinearLayout.VERTICAL);
    String[] lines = source.split("\n", -1);
    for (int i = 0; i < lines.length; i++) {
      String line = lines[i];
      final int index = i;
      String trim = line.trim();
      if (trim.startsWith("tree:")) {
        StringBuilder tree = new StringBuilder(line);
        while (i + 1 < lines.length
            && (lines[i + 1].startsWith("\t") || lines[i + 1].startsWith("  ")))
          tree.append("\n").append(lines[++i]);
        body.addView(
            new NoteDiagramView(c, tree.toString()),
            new LinearLayout.LayoutParams(
                -1, Math.max(120, tree.toString().split("\n").length * 40)));
        continue;
      }
      if (trim.startsWith("graph:") || trim.contains(" -> ") && !trim.startsWith("$$")) {
        body.addView(new NoteDiagramView(c, trim), new LinearLayout.LayoutParams(-1, 160));
        continue;
      }
      Matcher photo = Pattern.compile("^!\\[([A-Za-z0-9._/-]+)\\](?:\\s+(.*))?$").matcher(trim);
      if (photo.find()) {
        String asset = photo.group(1);
        if (asset.matches("[A-Za-z0-9-]+")) {
          File file = new File(new File(c.getFilesDir(), "attachments"), asset);
          if (file.isFile()) {
            ImageView image = new ImageView(c);
            image.setAdjustViewBounds(true);
            image.setImageBitmap(android.graphics.BitmapFactory.decodeFile(file.getAbsolutePath()));
            body.addView(image, new LinearLayout.LayoutParams(-1, -2));
          }
        }
        TextView caption = text(c, photo.group(2) == null ? "Photo attachment" : photo.group(2));
        body.addView(caption);
        continue;
      }
      if (trim.startsWith("- [ ] ") || trim.startsWith("- [x] ")) {
        CheckBox checkbox = new CheckBox(c);
        checkbox.setText(inline(c, trim.substring(6)));
        checkbox.setChecked(trim.startsWith("- [x] "));
        checkbox.setOnCheckedChangeListener(
            (v, on) -> {
              String[] copy = source.split("\n", -1);
              copy[index] = copy[index].replaceFirst("\\[([ x])\\]", on ? "[x]" : "[ ]");
              change.accept(String.join("\n", copy));
            });
        body.addView(checkbox);
        continue;
      }
      int header = trim.startsWith("## ") ? 2 : trim.startsWith("# ") ? 1 : 0;
      String displayed =
          header > 0
              ? trim.substring(header + 1)
              : trim.startsWith("- ") ? "• " + trim.substring(2) : line;
      TextView t = text(c, "");
      t.setText(inline(c, displayed));
      if (header > 0) {
        t.setTextSize(header == 1 ? 24 : 20);
        t.setTypeface(android.graphics.Typeface.create("serif", android.graphics.Typeface.BOLD));
      }
      body.addView(t, new LinearLayout.LayoutParams(-1, -2));
    }
    return body;
  }

  static TextView text(Context c, String s) {
    TextView t = new TextView(c);
    t.setText(s);
    t.setTextSize(16);
    t.setLineSpacing(4, 1);
    t.setPadding(2, 3, 2, 3);
    t.setTextColor(Appearance.color(c, R.attr.deskInk));
    return t;
  }

  static CharSequence inline(Context c, String source) {
    SpannableStringBuilder out = new SpannableStringBuilder();
    Matcher science = Pattern.compile("\\$\\$(.+?)\\$\\$").matcher(source);
    int at = 0;
    while (science.find()) {
      out.append(marks(c, source.substring(at, science.start()), 0));
      String raw = science.group(1);
      NoteSyntax.Chem chem = NoteSyntax.chemistry(raw);
      List<NoteSyntax.Node> math = chem == null ? NoteSyntax.maths(raw) : null;
      if (chem != null) {
        out.append(chem.display());
        if (chem.equation()) {
          out.append(chem.balanced() ? "  ✓ balanced" : "  ⚠ ");
          if (!chem.balanced()) {
            Set<String> elements = new LinkedHashSet<>(chem.left().keySet());
            elements.addAll(chem.right().keySet());
            for (String e : elements)
              out.append(
                  e
                      + " "
                      + chem.left().getOrDefault(e, 0)
                      + "·"
                      + chem.right().getOrDefault(e, 0)
                      + " ");
          }
        }
      } else if (math != null) {
        int start = out.length();
        out.append(NoteSyntax.flatten(math));
        out.setSpan(new MathSpan(math), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      } else out.append(science.group());
      at = science.end();
    }
    out.append(marks(c, source.substring(at), 0));
    return out;
  }

  static CharSequence marks(Context c, String source, int depth) {
    if (depth > 8) return source;
    String[] rules = {
      "\\*\\*(.+?)\\*\\*",
      "__(.+?)__",
      "=([23])=(.+?)=\\1=",
      "==(.+?)==",
      "(?<!\\*)\\*([^*]+)\\*(?!\\*)",
      "(?<!_)_([^_]+)_(?!_)"
    };
    Matcher best = null;
    int selected = -1;
    for (int i = 0; i < rules.length; i++) {
      Matcher m = Pattern.compile(rules[i]).matcher(source);
      if (m.find() && (best == null || m.start() < best.start())) {
        best = m;
        selected = i;
      }
    }
    if (best == null) return source;
    SpannableStringBuilder out = new SpannableStringBuilder();
    out.append(marks(c, source.substring(0, best.start()), depth + 1));
    int start = out.length();
    out.append(marks(c, best.group(selected == 2 ? 2 : 1), depth + 1));
    int end = out.length();
    Object span =
        selected == 0
            ? new StyleSpan(android.graphics.Typeface.BOLD)
            : selected == 1
                ? new UnderlineSpan()
                : selected <= 3
                    ? new BackgroundColorSpan(
                        Appearance.color(
                            c,
                            selected == 2
                                ? (best.group(1).equals("2")
                                    ? R.attr.deskQuery
                                    : R.attr.deskConfirmed)
                                : R.attr.deskHighlight))
                    : new StyleSpan(android.graphics.Typeface.ITALIC);
    if (end > start) out.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    out.append(marks(c, source.substring(best.end()), depth + 1));
    return out;
  }
}
