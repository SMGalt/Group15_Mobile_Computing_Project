package com.group15.studydesk;

import android.content.Intent;
import android.os.Bundle;

/** Compatibility route for previously issued timer notifications. */
public class TimerActivity extends BaseActivity {
  @Override
  protected void onCreate(Bundle state) {
    super.onCreate(state);
    Intent target = new Intent(this, MainActivity.class).putExtra("screen", "Timer");
    if (getIntent().getExtras() != null) target.putExtras(getIntent().getExtras());
    startActivity(target.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
    finish();
  }
}
