package es.jesus.ampodcasts;

import android.content.*;
import android.security.keystore.*;
import android.util.Base64;
import java.security.KeyStore;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

/** The user's credential is encrypted with a non-exportable device key. */
final class GeminiKeyStore {
    private static final String ALIAS="onda_gemini_api_v1";
    private static javax.crypto.SecretKey deviceKey() throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if(!store.containsAlias(ALIAS)) {
            KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            generator.generateKey();
        }
        return (javax.crypto.SecretKey)store.getKey(ALIAS,null);
    }
    static String seal(String text,javax.crypto.SecretKey key) throws Exception {
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE,key);
        return Base64.encodeToString(c.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(c.doFinal(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)),Base64.NO_WRAP);
    }
    static String open(String text,javax.crypto.SecretKey key) throws Exception {
        String[] parts=text.split(":",-1); if(parts.length!=2)throw new java.io.IOException("Clave guardada no válida");
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)));
        return new String(c.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)),java.nio.charset.StandardCharsets.UTF_8);
    }
    static SharedPreferences prefs(Context c){return c.getSharedPreferences("gemini_private",Context.MODE_PRIVATE);}
    static boolean has(Context c){return prefs(c).contains("encrypted_key");}
    static String get(Context c) throws Exception { String encrypted=prefs(c).getString("encrypted_key","");return encrypted.isEmpty()?"":open(encrypted,deviceKey()); }
    static String normalize(String key) {
        key=key==null?"":key.trim();
        // Credentials are opaque: accept both standard and authorization keys.
        if(!key.matches("[A-Za-z0-9._~-]{20,2048}"))throw new IllegalArgumentException("Pega solo la clave API completa, sin comillas, espacios ni saltos de línea.");
        return key;
    }
    static String saveError(Exception error) {
        if(error instanceof IllegalArgumentException)return "Pega solo la clave API completa, sin comillas, espacios ni saltos de línea.";
        if(error instanceof java.io.IOException)return "No se pudo guardar la clave en este móvil. Vuelve a intentarlo.";
        return "No se pudo cifrar la clave en este móvil ("+error.getClass().getSimpleName()+"). Vuelve a intentarlo.";
    }
    static void save(Context c,String key) throws Exception {
        key=normalize(key);
        if(!prefs(c).edit().putString("encrypted_key",seal(key,deviceKey())).commit())throw new java.io.IOException("No se pudo guardar la clave");
    }
    static void remove(Context c){prefs(c).edit().remove("encrypted_key").putBoolean(GeminiPreparation.ENABLED,false).apply();}
}
