/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.lib.util

import android.content.Context
import android.net.Uri
import dev.patrickgold.florisboard.app.florisPreferenceModel
import dev.patrickgold.jetpref.datastore.JetPref
import dev.patrickgold.jetpref.datastore.jetprefDatastoreDir
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.florisboard.lib.kotlin.io.subFile
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Serializable
data class FlorisSettingsBackup(
    val version: String = "1.0",
    val timestamp: Long = System.currentTimeMillis(),
    val appVersion: String,
    val preferences: String
)

object SettingsBackupUtils {
    private val json = Json { 
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private fun getAppVersion(context: Context): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }

    suspend fun exportSettings(context: Context, outputStream: OutputStream): Result<Unit> {
        return try {
            val prefs by florisPreferenceModel()
            
            // Read the datastore file directly
            val datastoreFile = context.jetprefDatastoreDir
                .subFile("${prefs.name}.${JetPref.JETPREF_FILE_EXT}")
            
            val preferencesContent = if (datastoreFile.exists()) {
                datastoreFile.readText()
            } else {
                "{}"
            }
            
            val backup = FlorisSettingsBackup(
                appVersion = getAppVersion(context),
                preferences = preferencesContent
            )
            
            val jsonString = json.encodeToString(backup)
            outputStream.write(jsonString.toByteArray())
            outputStream.close()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importSettings(context: Context, inputStream: InputStream): Result<Int> {
        return try {
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val backup = json.decodeFromString<FlorisSettingsBackup>(jsonString)
            
            val prefs by florisPreferenceModel()
            
            // Write the preferences back to the datastore file
            val datastoreFile = context.jetprefDatastoreDir
                .subFile("${prefs.name}.${JetPref.JETPREF_FILE_EXT}")
            
            // Write the backup content to the datastore file
            datastoreFile.writeText(backup.preferences)
            
            // Reload the preferences using the existing system
            prefs.datastorePersistenceHandler?.loadPrefs(datastoreFile, false)
            prefs.datastorePersistenceHandler?.persistPrefs()
            
            // Count the approximate number of preferences (rough estimate)
            val prefCount = backup.preferences.count { it == '"' } / 4 // Rough estimate
            
            Result.success(prefCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun generateBackupFileName(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
        val timestamp = dateFormat.format(Date())
        return "florisboard_settings_$timestamp.json"
    }

    suspend fun exportSettingsToUri(context: Context, uri: Uri): Result<Unit> {
        return try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                exportSettings(context, outputStream).getOrThrow()
            } ?: throw Exception("Failed to open output stream")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importSettingsFromUri(context: Context, uri: Uri): Result<Int> {
        return try {
            val result = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                importSettings(context, inputStream).getOrThrow()
            } ?: throw Exception("Failed to open input stream")
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}