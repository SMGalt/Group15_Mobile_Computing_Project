package com.group15.studydesk;

import androidx.lifecycle.LiveData;
import androidx.room.*;
import java.util.List;

@Dao
public interface RecordDao {
  @Query("SELECT * FROM records WHERE account=:owner ORDER BY rowid")
  LiveData<List<Row>> observe(String owner);

  @Query("SELECT * FROM records WHERE account=:owner ORDER BY rowid")
  List<Row> all(String owner);

  @Query("SELECT * FROM records WHERE account=:owner AND kind=:kind AND id=:id LIMIT 1")
  Row find(String owner, String kind, String id);

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  void upsert(Row row);

  @Query("DELETE FROM records WHERE `key`=:key")
  void removeKey(String key);

  @Query("DELETE FROM records WHERE account=:owner")
  void clear(String owner);

  @Query("SELECT * FROM records WHERE account=:owner AND dirty=1 ORDER BY rowid")
  List<Row> pending(String owner);
}
