package cn.hcy.gopan;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Session material is encrypted with a non-exportable Android Keystore key. */
final class Vault {
    static synchronized SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore"); ks.load(null);
        if (!ks.containsAlias("gopan.session")) {
            KeyGenerator gen = KeyGenerator.getInstance("AES", "AndroidKeyStore");
            gen.init(new KeyGenParameterSpec.Builder("gopan.session", KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            gen.generateKey();
        }
        return (SecretKey)ks.getKey("gopan.session", null);
    }
    static void put(Context c, String value) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key());
        String sealed = Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + ":" + Base64.encodeToString(cipher.doFinal(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)), Base64.NO_WRAP);
        if (!c.getSharedPreferences("vault", 0).edit().putString("session", sealed).commit()) throw new java.io.IOException("无法保存登录状态");
    }
    static String get(Context c) {
        try {
            String s = c.getSharedPreferences("vault", 0).getString("session", ""); if (s.isEmpty()) return "{}";
            String[] parts = s.split(":"); Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)));
            return new String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) { return "{}"; }
    }
    static void clear(Context c) { c.getSharedPreferences("vault", 0).edit().clear().commit(); }
}
