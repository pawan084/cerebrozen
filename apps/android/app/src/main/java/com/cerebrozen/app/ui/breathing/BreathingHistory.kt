package com.cerebrozen.app.ui.breathing

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class BreathingSessionRecord(
    val completedAtEpochMillis: Long,
    val pattern: BreathPattern,
    val durationSeconds: Int,
    val rounds: Int,
)

interface BreathingHistoryStore {
    val history: Flow<List<BreathingSessionRecord>>
    suspend fun save(record: BreathingSessionRecord)
    suspend fun clear()
}

private val Context.breathingDataStore by preferencesDataStore(name = "offline_breathing")

class DataStoreBreathingHistory(context: Context) : BreathingHistoryStore {
    private val store = context.applicationContext.breathingDataStore
    private val recordsKey = stringSetPreferencesKey("completed_sessions")

    override val history: Flow<List<BreathingSessionRecord>> = store.data.map { prefs ->
        prefs[recordsKey].orEmpty().mapNotNull(::decode)
            .sortedByDescending(BreathingSessionRecord::completedAtEpochMillis)
    }

    override suspend fun save(record: BreathingSessionRecord) {
        store.edit { prefs ->
            val updated = (prefs[recordsKey].orEmpty().mapNotNull(::decode) + record)
                .sortedByDescending(BreathingSessionRecord::completedAtEpochMillis)
                .take(MAX_HISTORY)
                .map(::encode)
                .toSet()
            prefs[recordsKey] = updated
        }
    }

    override suspend fun clear() {
        store.edit { it.remove(recordsKey) }
    }

    private fun encode(record: BreathingSessionRecord): String = listOf(
        record.completedAtEpochMillis,
        record.pattern.name,
        record.durationSeconds,
        record.rounds,
    ).joinToString("|")

    private fun decode(raw: String): BreathingSessionRecord? {
        val parts = raw.split('|')
        if (parts.size != 4) return null
        return runCatching {
            BreathingSessionRecord(
                completedAtEpochMillis = parts[0].toLong(),
                pattern = BreathPattern.valueOf(parts[1]),
                durationSeconds = parts[2].toInt(),
                rounds = parts[3].toInt(),
            )
        }.getOrNull()
    }

    private companion object { const val MAX_HISTORY = 50 }
}
