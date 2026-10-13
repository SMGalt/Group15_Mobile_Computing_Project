package com.group15.studydesk;

import android.app.*;
import android.os.Bundle;

public class DeskApp extends Application {
  int visible;

  @Override
  public void onCreate() {
    super.onCreate();
    Reminders.channels(this);
    Diagnostics.install(this);
    CloudSync.schedule(this);
    getSystemService(android.net.ConnectivityManager.class)
        .registerDefaultNetworkCallback(
            new android.net.ConnectivityManager.NetworkCallback() {
              @Override
              public void onAvailable(android.net.Network network) {
                CloudSync.schedule(DeskApp.this);
              }
            });
    registerActivityLifecycleCallbacks(
        new ActivityLifecycleCallbacks() {
          public void onActivityCreated(Activity a, Bundle b) {}

          public void onActivityStarted(Activity a) {
            if (visible++ == 0) {
              RealtimeClient.start(DeskApp.this);
              CloudSync.NETWORK.execute(
                  () -> {
                    Repository repo = new Repository(DeskApp.this);
                    CalendarFeed.refreshDue(DeskApp.this, repo);
                  });
            }
          }

          public void onActivityResumed(Activity a) {}

          public void onActivityPaused(Activity a) {}

          public void onActivityStopped(Activity a) {
            if (--visible == 0) RealtimeClient.stop();
          }

          public void onActivitySaveInstanceState(Activity a, Bundle b) {}

          public void onActivityDestroyed(Activity a) {}
        });
  }
}
