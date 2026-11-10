package com.radley.homefit.coach

import android.content.Context
import android.util.Base64
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The user's Anthropic API key, encrypted with AES-GCM under a keyset that is itself wrapped by
 * a hardware-backed Android Keystore key. The ciphertext can't be decrypted off this device,
 * which is why [PREFS] is excluded from cloud backup and device transfer.
 */
class CoachKeyStore(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val aead: Aead by lazy {
        AeadConfig.register()
        AndroidKeysetManager.Builder()
            .withSharedPref(context, KEYSET, PREFS)
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
            .keysetHandle
            .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
    }

    private val _hasKey = MutableStateFlow(prefs.contains(CIPHERTEXT))
    val hasKey: StateFlow<Boolean> = _hasKey.asStateFlow()

    fun save(apiKey: String) {
        val ciphertext = aead.encrypt(apiKey.trim().toByteArray(), ASSOCIATED_DATA)
        prefs.edit().putString(CIPHERTEXT, Base64.encodeToString(ciphertext, Base64.NO_WRAP)).apply()
        _hasKey.value = true
    }

    /** Null when unset, or when the Keystore key is gone (e.g. after a restore to a new phone). */
    fun load(): String? = prefs.getString(CIPHERTEXT, null)?.let { stored ->
        runCatching { String(aead.decrypt(Base64.decode(stored, Base64.NO_WRAP), ASSOCIATED_DATA)) }.getOrNull()
    }

    fun clear() {
        prefs.edit().remove(CIPHERTEXT).apply()
        _hasKey.value = false
    }

    companion object {
        /** Shared preferences aren't in res/xml/data_extraction_rules.xml, so this never leaves the device. */
        const val PREFS = "coach_key_prefs"
        private const val KEYSET = "coach_keyset"
        private const val CIPHERTEXT = "api_key"
        private const val MASTER_KEY_URI = "android-keystore://homefit_coach_master_key"
        private val ASSOCIATED_DATA = "homefit-coach-api-key".toByteArray()
    }
}
