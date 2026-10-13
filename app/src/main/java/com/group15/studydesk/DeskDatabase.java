package com.group15.studydesk;

import android.content.Context;
import androidx.room.*;

@Database(
    entities = {Row.class},
    version = 1,
    exportSchema = false)
public abstract class DeskDatabase extends RoomDatabase {
  public abstract RecordDao records();

  private static volatile DeskDatabase instance;

  public static DeskDatabase get(Context c) {
    if (instance == null)
      synchronized (DeskDatabase.class) {
        if (instance == null)
          instance =
              Room.databaseBuilder(c.getApplicationContext(), DeskDatabase.class, "studydesk.db")
                  .build();
      }
    return instance;
  }
}
