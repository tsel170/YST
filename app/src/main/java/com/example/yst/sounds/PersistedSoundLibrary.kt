package com.example.yst.sounds

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.example.yst.detection.SoundClip

class PersistedSoundLibrary(
    context: Context,
) : SoundLibrary {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)
    private val memory = MemorySoundLibrary(loadStored())

    override fun load(): List<SoundClip> = memory.load()

    override fun add(clips: List<SoundClip>): List<SoundClip> {
        val updated = memory.add(clips)
        save(updated)
        return updated
    }

    override fun remove(id: String): List<SoundClip> {
        releasePermission(id)
        val updated = memory.remove(id)
        save(updated)
        return updated
    }

    fun importUris(uris: List<Uri>): List<SoundClip> {
        val imported = mutableListOf<SoundClip>()
        for (uri in uris) {
            if (!keepPermission(uri)) {
                continue
            }
            imported += SoundClip(
                id = uri.toString(),
                displayName = displayName(uri),
            )
        }
        return add(imported)
    }

    private fun loadStored(): List<SoundClip> {
        return SoundCatalogCodec.decode(preferences.getString(KEY_CATALOG, "").orEmpty())
    }

    private fun save(clips: List<SoundClip>) {
        preferences.edit().putString(KEY_CATALOG, SoundCatalogCodec.encode(clips)).apply()
    }

    private fun keepPermission(uri: Uri): Boolean {
        return try {
            appContext.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
            true
        } catch (_: SecurityException) {
            false
        }
    }

    private fun releasePermission(id: String) {
        val uri = runCatching { Uri.parse(id) }.getOrNull() ?: return
        try {
            appContext.contentResolver.releasePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
            // The permission may already have been revoked.
        }
    }

    private fun displayName(uri: Uri): String {
        try {
            appContext.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        val name = cursor.getString(index)
                        if (!name.isNullOrBlank()) {
                            return name
                        }
                    }
                }
            }
        } catch (_: SecurityException) {
            // Fall back to the last path segment.
        }
        return uri.lastPathSegment ?: uri.toString()
    }

    private companion object {
        const val PREFERENCE_NAME = "voice_trigger_sounds"
        const val KEY_CATALOG = "catalog"
    }
}
