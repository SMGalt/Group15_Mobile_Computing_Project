package com.group15.studydesk;

import android.os.Bundle;
import android.widget.FrameLayout;

/** Debug-only host for exercising real OS AppWidget RemoteViews, binding and launch intents. */
public class WidgetPreviewActivity extends BaseActivity {
  FrameLayout root;

  @Override
  protected void onCreate(Bundle state) {
    super.onCreate(state);
    root = new FrameLayout(this);
    setContentView(root);
    insets(root);
  }
}
