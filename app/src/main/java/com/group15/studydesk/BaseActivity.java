package com.group15.studydesk;

import android.content.*;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.*;
import androidx.core.view.*;
import java.util.Locale;

public class BaseActivity extends AppCompatActivity {
  android.os.Handler timerHandler = new android.os.Handler(android.os.Looper.getMainLooper());
  com.google.android.material.button.MaterialButton timerChip;
  final Runnable timerTick =
      new Runnable() {
        public void run() {
          if (timerChip == null) return;
          TimerState t = new TimerState(BaseActivity.this);
          boolean active = t.running() && (t.stopwatch() || t.secs() > 0);
          boolean timerVisible = BaseActivity.this instanceof MainActivity
              && ((MainActivity) BaseActivity.this).tab == 3
              && ((MainActivity) BaseActivity.this).overflow.isEmpty();
          timerChip.setVisibility(active && !timerVisible ? android.view.View.VISIBLE : android.view.View.GONE);
          if (active) {
            timerChip.setText(tr(t.phase()) + " · " + Rules.clock(t.secs()));
          }
          timerHandler.postDelayed(this, 1000);
        }
      };

  protected String activeLanguage;

  @Override
  protected void onResume() {
    super.onResume();
    String currentLang = getSharedPreferences("settings", 0).getString("language", "en");
    if (activeLanguage != null && !activeLanguage.equals(currentLang)) {
      activeLanguage = currentLang;
      recreate();
      return;
    }
    timerHandler.removeCallbacks(timerTick);
    timerHandler.post(timerTick);
  }

  @Override
  protected void onPause() {
    super.onPause();
    timerHandler.removeCallbacks(timerTick);
  }

  @Override
  protected void attachBaseContext(Context c) {
    String lang = c.getSharedPreferences("settings", 0).getString("language", "en");
    Configuration conf = new Configuration(c.getResources().getConfiguration());
    conf.setLocale(Locale.forLanguageTag(lang));
    super.attachBaseContext(c.createConfigurationContext(conf));
  }

  @Override
  protected void onCreate(Bundle state) {
    activeLanguage = getSharedPreferences("settings", 0).getString("language", "en");
    String mode = getSharedPreferences("settings", 0).getString("mode", "light");
    AppCompatDelegate.setDefaultNightMode(
        Appearance.dark(this) ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
    String character = Appearance.character(this);
    if (character.equals("stacks")) setTheme(R.style.Theme_StudyDesk_Stacks);
    else if (character.equals("slate")) setTheme(R.style.Theme_StudyDesk_Slate);
    else if (mode.equals("black")) setTheme(R.style.Theme_StudyDesk_Black);
    super.onCreate(state);
  }

  protected void insets(View root) {
    if (!(this instanceof TimerActivity) && root instanceof android.widget.LinearLayout) {
      timerChip =
          new com.google.android.material.button.MaterialButton(
              this, null, com.google.android.material.R.attr.borderlessButtonStyle);
      timerChip.setTextSize(13);
      timerChip.setAllCaps(false);
      timerChip.setVisibility(android.view.View.GONE);
      timerChip.setOnClickListener(
          v ->
              startActivity(
                  new Intent(this, MainActivity.class).putExtra("screen", "Timer")
                      .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)));
      ((android.widget.LinearLayout) root)
          .addView(
              timerChip,
              1,
              new android.widget.LinearLayout.LayoutParams(
                  -1, (int) (48 * getResources().getDisplayMetrics().density)));
    }

    WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), root);
    controller.setAppearanceLightStatusBars(!Appearance.dark(this));
    controller.setAppearanceLightNavigationBars(!Appearance.dark(this));
    ViewCompat.setOnApplyWindowInsetsListener(
        root,
        (v, i) -> {
          androidx.core.graphics.Insets b =
              i.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
          v.setPadding(b.left, b.top, b.right, b.bottom);
          return i;
        });
  }

  protected String tr(String s) {
    return Language.translate(this, s);
  }

  protected void message(String s) {
    android.widget.Toast.makeText(this, tr(s), android.widget.Toast.LENGTH_LONG).show();
  }
}
