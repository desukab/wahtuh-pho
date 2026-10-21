package com.wahtuh.pho.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * API keys are encrypted with an AES-256-GCM key held by the Android Keystore.
 * The Keystore key never leaves the device and is not exportable. If the key is
 * invalidated the read fails closed rather than returning plaintext.
 */
public final class SecureStore {
    private static final String PREF = "wahtuh_secure";
    private static final String ALIAS = "wahtuh_pho_credentials_v1";
    private static final String TRANSFORM = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SharedPreferences prefs;

    public SecureStore(Context context) {
        this.prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public synchronized void put(String name, String value) {
        if (value == null || value.isEmpty()) {
            remove(name);
            return;
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORM);
            cipher.init(Cipher.ENCRYPT_MODE, key());
            byte[] iv = cipher.getIV();
            if (iv == null || iv.length != IV_BYTES) {
                iv = new byte[IV_BYTES];
                new SecureRandom().nextBytes(iv);
                cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
            }
            byte[] body = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] packed = new byte[iv.length + body.length];
            System.arraycopy(iv, 0, packed, 0, iv.length);
            System.arraycopy(body, 0, packed, iv.length, body.length);
            prefs.edit().putString(name, Base64.encodeToString(packed, Base64.NO_WRAP)).apply();
        } catch (Exception e) {
            throw new IllegalStateException("Could not encrypt credential", e);
        }
    }

    public synchronized String get(String name) {
        String encoded = prefs.getString(name, null);
        if (encoded == null) return "";
        try {
            byte[] packed = Base64.decode(encoded, Base64.NO_WRAP);
            byte[] iv = new byte[IV_BYTES];
            byte[] body = new byte[packed.length - IV_BYTES];
            System.arraycopy(packed, 0, iv, 0, IV_BYTES);
            System.arraycopy(packed, IV_BYTES, body, 0, body.length);
            Cipher cipher = Cipher.getInstance(TRANSFORM);
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(body), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Could not decrypt credential", e);
        }
    }

    public synchronized void remove(String name) {
        prefs.edit().remove(name).apply();
    }

    public synchronized boolean has(String name) {
        return prefs.contains(name);
    }

    /** Only used by tests to assert plaintext never reaches disk. */
    public String raw(String name) {
        return prefs.getString(name, "");
    }

    private SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (ks.containsAlias(ALIAS)) {
            return (SecretKey) ks.getKey(ALIAS, null);
        }
        KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        kg.init(new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build());
        return kg.generateKey();
    }
}
