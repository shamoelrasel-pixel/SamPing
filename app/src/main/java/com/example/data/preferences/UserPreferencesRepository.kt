package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.example.domain.model.MessageChannel
import com.example.domain.model.RetryPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "autosend_settings")

data class UserPreferences(
    val defaultChannel: MessageChannel = MessageChannel.SMS,
    val defaultSimId: Int = -1,
    val preSendReminderMinutes: Int = 0,
    val notifyOnSent: Boolean = true,
    val notifyOnFailure: Boolean = true,
    val missedPolicyCatchUp: Boolean = false,
    val defaultRetryPolicy: RetryPolicy = RetryPolicy.NO_RETRY,
    val onboardingCompleted: Boolean = false,
    val darkMode: String = "SYSTEM", // SYSTEM, LIGHT, DARK
    val incomingSmsTone: String = "SHAMRING", // SHAMRING, CHIME, BELL, SYSTEM, SILENT
    val scheduledSmsTone: String = "BELL", // BELL, SHAMRING, CHIME, SYSTEM, SILENT
    val swipeActionsEnabled: Boolean = true,
    val archivedConversationKeys: Set<String> = emptySet()
)

class UserPreferencesRepository(private val context: Context) {

    private object PreferencesKeys {
        val DEFAULT_CHANNEL = stringPreferencesKey("default_channel")
        val DEFAULT_SIM_ID = intPreferencesKey("default_sim_id")
        val PRE_SEND_REMINDER_MIN = intPreferencesKey("pre_send_reminder_min")
        val NOTIFY_ON_SENT = booleanPreferencesKey("notify_on_sent")
        val NOTIFY_ON_FAILURE = booleanPreferencesKey("notify_on_failure")
        val MISSED_POLICY_CATCHUP = booleanPreferencesKey("missed_policy_catchup")
        val DEFAULT_RETRY_POLICY = stringPreferencesKey("default_retry_policy")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val DARK_MODE = stringPreferencesKey("dark_mode")
        val INCOMING_SMS_TONE = stringPreferencesKey("incoming_sms_tone")
        val SCHEDULED_SMS_TONE = stringPreferencesKey("scheduled_sms_tone")
        val SWIPE_ACTIONS_ENABLED = booleanPreferencesKey("swipe_actions_enabled")
        val ARCHIVED_CONVERSATION_KEYS = stringSetPreferencesKey("archived_conversation_keys")
    }

    val userPreferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { preferences ->
        val channelStr = preferences[PreferencesKeys.DEFAULT_CHANNEL] ?: MessageChannel.SMS.name
        val channel = runCatching { MessageChannel.valueOf(channelStr) }.getOrDefault(MessageChannel.SMS)

        val retryStr = preferences[PreferencesKeys.DEFAULT_RETRY_POLICY] ?: RetryPolicy.NO_RETRY.name
        val retry = runCatching { RetryPolicy.valueOf(retryStr) }.getOrDefault(RetryPolicy.NO_RETRY)

        UserPreferences(
            defaultChannel = channel,
            defaultSimId = preferences[PreferencesKeys.DEFAULT_SIM_ID] ?: -1,
            preSendReminderMinutes = preferences[PreferencesKeys.PRE_SEND_REMINDER_MIN] ?: 0,
            notifyOnSent = preferences[PreferencesKeys.NOTIFY_ON_SENT] ?: true,
            notifyOnFailure = preferences[PreferencesKeys.NOTIFY_ON_FAILURE] ?: true,
            missedPolicyCatchUp = preferences[PreferencesKeys.MISSED_POLICY_CATCHUP] ?: false,
            defaultRetryPolicy = retry,
            onboardingCompleted = preferences[PreferencesKeys.ONBOARDING_COMPLETED] ?: false,
            darkMode = preferences[PreferencesKeys.DARK_MODE] ?: "SYSTEM",
            incomingSmsTone = preferences[PreferencesKeys.INCOMING_SMS_TONE] ?: "CHIME",
            scheduledSmsTone = preferences[PreferencesKeys.SCHEDULED_SMS_TONE] ?: "BELL",
            swipeActionsEnabled = preferences[PreferencesKeys.SWIPE_ACTIONS_ENABLED] ?: true,
            archivedConversationKeys = preferences[PreferencesKeys.ARCHIVED_CONVERSATION_KEYS] ?: emptySet()
        )
    }

    suspend fun setSwipeActionsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.SWIPE_ACTIONS_ENABLED] = enabled }
    }

    suspend fun setConversationArchived(key: String, archived: Boolean) {
        context.dataStore.edit { prefs ->
            val current = prefs[PreferencesKeys.ARCHIVED_CONVERSATION_KEYS]?.toMutableSet() ?: mutableSetOf()
            if (archived) {
                current.add(key)
            } else {
                current.remove(key)
            }
            prefs[PreferencesKeys.ARCHIVED_CONVERSATION_KEYS] = current
        }
    }

    suspend fun setDefaultChannel(channel: MessageChannel) {
        context.dataStore.edit { it[PreferencesKeys.DEFAULT_CHANNEL] = channel.name }
    }

    suspend fun setDefaultSimId(simId: Int) {
        context.dataStore.edit { it[PreferencesKeys.DEFAULT_SIM_ID] = simId }
    }

    suspend fun setPreSendReminderMinutes(minutes: Int) {
        context.dataStore.edit { it[PreferencesKeys.PRE_SEND_REMINDER_MIN] = minutes }
    }

    suspend fun setNotifyOnSent(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.NOTIFY_ON_SENT] = enabled }
    }

    suspend fun setNotifyOnFailure(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.NOTIFY_ON_FAILURE] = enabled }
    }

    suspend fun setMissedPolicyCatchUp(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.MISSED_POLICY_CATCHUP] = enabled }
    }

    suspend fun setDefaultRetryPolicy(policy: RetryPolicy) {
        context.dataStore.edit { it[PreferencesKeys.DEFAULT_RETRY_POLICY] = policy.name }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.ONBOARDING_COMPLETED] = completed }
    }

    suspend fun setDarkMode(mode: String) {
        context.dataStore.edit { it[PreferencesKeys.DARK_MODE] = mode }
    }

    suspend fun setIncomingSmsTone(tone: String) {
        context.dataStore.edit { it[PreferencesKeys.INCOMING_SMS_TONE] = tone }
    }

    suspend fun setScheduledSmsTone(tone: String) {
        context.dataStore.edit { it[PreferencesKeys.SCHEDULED_SMS_TONE] = tone }
    }
}
