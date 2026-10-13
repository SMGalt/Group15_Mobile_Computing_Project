package com.group15.studydesk;

import android.content.*;
import android.security.keystore.*;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

/** Tokens and PKCE verifier encrypted with a non-exportable Android Keystore key. */
public class SecureStore {
  private final SharedPreferences p;

  public SecureStore(Context c) {
    p = c.getSharedPreferences("vault", 0);
  }

  private SecretKey key() throws Exception {
    KeyStore store = KeyStore.getInstance("AndroidKeyStore");
    store.load(null);
    String alias = "studydesk.session";
    if (!store.containsAlias(alias)) {
      KeyGenerator g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
      g.init(
          new KeyGenParameterSpec.Builder(
                  alias, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
              .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
              .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
              .build());
      g.generateKey();
    }
    return (SecretKey) store.getKey(alias, null);
  }

  public synchronized void put(String name, String value) {
    try {
      if (value == null) {
        p.edit().remove(name).commit();
        return;
      }
      Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
      c.init(Cipher.ENCRYPT_MODE, key());
      String encoded =
          Base64.encodeToString(c.getIV(), Base64.NO_WRAP)
              + ":"
              + Base64.encodeToString(
                  c.doFinal(value.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
      p.edit().putString(name, encoded).commit();
    } catch (Exception e) {
      throw new IllegalStateException("Could not secure session", e);
    }
  }

  public synchronized String get(String name) {
    String stored = p.getString(name, null);
    if (stored == null) return null;
    try {
      String[] parts = stored.split(":");
      Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
      c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(parts[0], 0)));
      return new String(c.doFinal(Base64.decode(parts[1], 0)), StandardCharsets.UTF_8);
    } catch (Exception e) {
      p.edit().remove(name).commit();
      return null;
    }
  }

  public void clear() {
    p.edit().clear().commit();
  }
}
