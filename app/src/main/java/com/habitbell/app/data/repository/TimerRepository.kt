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

/**
 * Top-level DataStore extension property for local persistent preferences.
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "habit_bell_prefs")

/**
 * Repository orchestrating access, modification, and persistence of wellness timer profiles,
 * favorite lists, recent session history, and scheduled habit reminders.
 *
 * Exposes immutable reactive [StateFlow] pipelines to provide single-source-of-truth state
 * updates across ViewModels, car automotive interfaces, and cast services.
 *
 * @param context Android context for DataStore initialization and disk operations.
 */
class TimerRepository(private val context: Context) {

    /** SharedPreferences handle for persisting user-customized profile durations and interval timings. */
    private val prefs = context.getSharedPreferences("habit_bell_settings", Context.MODE_PRIVATE)

    /** Coroutine scope on [Dispatchers.IO] dedicated to background data persistence. */
    private val scope = CoroutineScope(Dispatchers.IO)

    /** Mutable backing stream holding the complete catalog of preset and user-created profiles with persisted customizations. */
    private val _profiles = MutableStateFlow<List<TimerProfile>>(loadInitialProfiles())

    /**
     * Loads the default preset catalog and overlays any persistent user customizations
     * (total duration, interval timing) previously saved by the user.
     *
     * @return List of [TimerProfile] entities with user preferences restored.
     */
    private fun loadInitialProfiles(): List<TimerProfile> {
        return DefaultProfiles.ALL_PRESETS.map { defaultProfile ->
            val savedDuration = prefs.getInt("profile_duration_${defaultProfile.id}", -1)
            val savedInterval = prefs.getInt("profile_interval_${defaultProfile.id}", -1)

            val dur = if (savedDuration > 0) {
                savedDuration
            } else if (defaultProfile.id == "eating-mindful-20") {
                val aliasDur = prefs.getInt("profile_duration_eating", -1)
                if (aliasDur > 0) aliasDur else defaultProfile.totalDurationSeconds
            } else {
                defaultProfile.totalDurationSeconds
            }

            val inter = if (savedInterval >= 0) {
                savedInterval
            } else if (defaultProfile.id == "eating-mindful-20") {
                val aliasInter = prefs.getInt("profile_interval_eating", -1)
                if (aliasInter >= 0) aliasInter else defaultProfile.intervalDurationSeconds
            } else {
                defaultProfile.intervalDurationSeconds
            }

            defaultProfile.copy(
                totalDurationSeconds = dur,
                intervalDurationSeconds = inter
            )
        }
    }

    /** Public read-only stream emitting the live list of available timer profiles. */
    val profiles: StateFlow<List<TimerProfile>> = _profiles.asStateFlow()

    /** Mutable backing stream tracking recently executed profile IDs (ordered most recent first). */
    private val _recentProfileIds = MutableStateFlow<List<String>>(listOf("eating-mindful-20", "pranayama-box-breath", "reiki-session-45"))

    /** Public read-only stream emitting list of recently used profile IDs. */
    val recentProfileIds: StateFlow<List<String>> = _recentProfileIds.asStateFlow()

    /** Mutable backing stream for active daily habit reminders. */
    private val _reminders = MutableStateFlow<List<RoutineReminder>>(DefaultReminders.ALL_REMINDERS)

    /** Public read-only stream emitting list of scheduled routine reminders. */
    val reminders: StateFlow<List<RoutineReminder>> = _reminders.asStateFlow()

    /**
     * Derived stream filtering profiles marked as favorite by the user.
     * Starts eagerly to ensure instantaneous UI population.
     */
    val favorites: StateFlow<List<TimerProfile>> = _profiles.map { list ->
        list.filter { it.isFavorite }
    }.stateIn(scope, SharingStarted.Eagerly, DefaultProfiles.ALL_PRESETS.filter { it.isFavorite })

    /**
     * Queries a profile by its unique ID.
     *
     * @param id The unique profile string identifier.
     * @return Matching [TimerProfile] if found, or null otherwise.
     */
    fun getProfileById(id: String): TimerProfile? {
        return _profiles.value.find { it.id == id }
    }

    /**
     * Toggles the favorite status for a given profile ID.
     *
     * @param profileId Unique ID of the profile whose favorite status will be inverted.
     */
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

    /**
     * Records the completion or execution of a session, moving its ID to the front of
     * the recent history list (capped at 5 recent profiles).
     *
     * @param profileId Unique ID of the completed profile.
     */
    fun recordSessionCompleted(profileId: String) {
        _recentProfileIds.update { list ->
            val updated = list.filter { it != profileId }.toMutableList()
            updated.add(0, profileId)
            if (updated.size > 5) updated.take(5) else updated
        }
    }

    /**
     * Saves a newly created or edited custom timer profile into the repository.
     * If a profile with the same ID already exists, it is replaced; otherwise appended.
     *
     * @param profile The [TimerProfile] instance to insert or update.
     */
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

    /**
     * Updates specific runtime settings of an existing profile without mutating its core identity.
     *
     * @param profileId Target profile identifier.
     * @param totalDuration Optional new total duration in seconds.
     * @param intervalDuration Optional new interval duration in seconds.
     * @param displayMode Optional toggle for keep-screen-on display mode.
     * @param pocketMode Optional toggle for proximity-sensor pocket mode.
     */
    fun updateProfileSettings(
        profileId: String,
        totalDuration: Int? = null,
        intervalDuration: Int? = null,
        displayMode: Boolean? = null,
        pocketMode: Boolean? = null
    ) {
        val editor = prefs.edit()
        if (totalDuration != null) {
            editor.putInt("profile_duration_$profileId", totalDuration)
            if (profileId == "eating-mindful-20" || profileId == "eating") {
                editor.putInt("profile_duration_eating", totalDuration)
                editor.putInt("profile_duration_eating-mindful-20", totalDuration)
            }
        }
        if (intervalDuration != null) {
            editor.putInt("profile_interval_$profileId", intervalDuration)
            if (profileId == "eating-mindful-20" || profileId == "eating") {
                editor.putInt("profile_interval_eating", intervalDuration)
                editor.putInt("profile_interval_eating-mindful-20", intervalDuration)
            }
        }
        editor.apply()

        _profiles.update { list ->
            list.map { profile ->
                if (profile.id == profileId || (profile.id == "eating-mindful-20" && profileId == "eating")) {
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
