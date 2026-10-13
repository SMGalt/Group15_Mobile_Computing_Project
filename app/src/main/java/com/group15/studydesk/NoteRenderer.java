package com.group15.studydesk;

import android.text.*;
import android.text.style.*;
import java.util.regex.*;

/** Native Spannable Markdown preview. Raw Markdown remains editable and portable. */
public final class NoteRenderer {
  public static CharSequence render(String source) {
    SpannableString out = new SpannableString(source);
    Matcher headings = Pattern.compile("(?m)^#{1,6} .+$").matcher(source);
    while (headings.find()) {
      out.setSpan(
          new StyleSpan(android.graphics.Typeface.BOLD), headings.start(), headings.end(), 0);
      out.setSpan(new RelativeSizeSpan(1.25f), headings.start(), headings.end(), 0);
    }
    Matcher bold = Pattern.compile("\\*\\*(.+?)\\*\\*").matcher(source);
    while (bold.find())
      out.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), bold.start(), bold.end(), 0);
    Matcher code = Pattern.compile("(?s)```.*?```|`[^`]+`").matcher(source);
    while (code.find()) out.setSpan(new TypefaceSpan("monospace"), code.start(), code.end(), 0);
    return out;
  }
}
