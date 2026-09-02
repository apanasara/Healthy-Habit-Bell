package com.habitbell.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.habitbell.app.data.default.DefaultProfiles
import com.habitbell.app.data.default.DefaultReminders
import com.habitbell.app.data.model.RoutineReminder
import com.habitbell.app.data.model.TimerProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "habit_bell_prefs")

class TimerRepository(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO)

    private val _profiles = MutableStateFlow<List<TimerProfile>>(DefaultProfiles.ALL_PRESETS)
    val profiles: StateFlow<List<TimerProfile>> = _profiles.asStateFlow()

    private val _recentProfileIds = MutableStateFlow<List<String>>(listOf("eating-mindful-20", "pranayama-box-breath", "reiki-session-45"))
    val recentProfileIds: StateFlow<List<String>> = _recentProfileIds.asStateFlow()

    private val _reminders = MutableStateFlow<List<RoutineReminder>>(DefaultReminders.ALL_REMINDERS)
    val reminders: StateFlow<List<RoutineReminder>> = _reminders.asStateFlow()

    val favorites: StateFlow<List<TimerProfile>> = _profiles.map { list ->
        list.filter { it.isFavorite }
    }.stateIn(scope, SharingStarted.Eagerly, DefaultProfiles.ALL_PRESETS.filter { it.isFavorite })

    fun getProfileById(id: String): TimerProfile? {
        return _profiles.value.find { it.id == id }
    }

    fun toggleFavorite(profileId: String) {
        _profiles.update { currentList ->
            currentList.map { profile ->
                if (profile.id == profileId) {
                    profile.copy(isFavorite = !profile.isFavorite)
                } else {
                    profile
                }
            }
        }
    }

    fun recordSessionCompleted(profileId: String) {
        _recentProfileIds.update { list ->
            val updated = list.filter { it != profileId }.toMutableList()
            updated.add(0, profileId)
            if (updated.size > 5) updated.take(5) else updated
        }
    }

    fun saveCustomProfile(profile: TimerProfile) {
        _profiles.update { current ->
            val existingIndex = current.indexOfFirst { it.id == profile.id }
            if (existingIndex >= 0) {
                current.toMutableList().apply { set(existingIndex, profile) }
            } else {
                current + profile
            }
        }
    }

    fun updateProfileSettings(
        profileId: String,
        totalDuration: Int? = null,
        intervalDuration: Int? = null,
        displayMode: Boolean? = null,
        pocketMode: Boolean? = null
    ) {
        _profiles.update { list ->
            list.map { profile ->
                if (profile.id == profileId) {
                    profile.copy(
                        totalDurationSeconds = totalDuration ?: profile.totalDurationSeconds,
                        intervalDurationSeconds = intervalDuration ?: profile.intervalDurationSeconds,
                        displayMode = displayMode ?: profile.displayMode,
                        pocketMode = pocketMode ?: profile.pocketMode
                    )
                } else {
                    profile
                }
            }
        }
    }
}
