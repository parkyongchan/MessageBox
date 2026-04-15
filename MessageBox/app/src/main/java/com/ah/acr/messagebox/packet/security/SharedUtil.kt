package com.ah.acr.messagebox.packet.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SharedUtil(private val context: Context) {
    companion object{
        //private const val TAG = "SharedUtil"
        private const val MASTER_KEY_ALIAS = "_androidx_security_master_key_"
        private const val KEY_SIZE = 256
        private const val PREFERENCE_FILE_KEY = "_preferences"
    }

    private val sharedPreferences: SharedPreferences by lazy {
        val spec = KeyGenParameterSpec
                .Builder(MASTER_KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE)
                .build()

        val masterKey = MasterKey
                .Builder(context)
                .setKeyGenParameterSpec(spec)
                .build()

        EncryptedSharedPreferences.create(
                context,
                context.packageName + PREFERENCE_FILE_KEY,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }


    fun putAny(key: String, value: Any){
        val editor = sharedPreferences.edit()

        with(editor) {
            when (value) {
                is Int -> {
                    //Log.d(TAG, "Input Key is $key, Int Value is $value ")
                    putInt(key, value)
                }
                is String -> {
                    //Log.d(TAG, "Input Key is $key, String Value is $value ")
                    putString(key, value)
                }
                is Boolean -> {
                    //Log.d(TAG, "Input Key is $key, Boolean Value is $value ")
                    putBoolean(key, value)
                }
                else -> throw IllegalArgumentException("error")
            }
            apply()
        }
    }

    fun getString(key: String) = sharedPreferences.getString(key, "")

    fun getNumber(key: String) = sharedPreferences.getInt(key, 0)

    fun getBoolean(key: String) = sharedPreferences.getBoolean(key, false)
}
