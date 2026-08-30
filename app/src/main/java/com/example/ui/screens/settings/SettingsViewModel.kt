package com.example.ui.screens.settings

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.AutoSendApplication
import com.example.data.preferences.UserPreferences
import com.example.domain.model.MessageChannel
import com.example.domain.model.RetryPolicy
import com.example.domain.model.SimInfo
import com.example.service.DefaultSmsHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val preferences: UserPreferences = UserPreferences(),
    val availableSims: List<SimInfo> = emptyList(),
    val canScheduleExactAlarms: Boolean = true,
    val isDefaultSmsApp: Boolean = false,
    val hasSmsPermission: Boolean = false,
    val hasContactsPermission: Boolean = false,
    val hasNotificationsPermission: Boolean = false,
    val hasPhoneStatePermission: Boolean = false,
    val isExportSuccess: Boolean = false,
    val isImportSuccess: Boolean = false
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as AutoSendApplication
    private val preferencesRepo = app.userPreferencesRepository
    private val simManager = app.simManager
    private val smsSender = app.smsSender
    private val alarmScheduler = app.alarmScheduler
    private val notificationHelper = app.notificationHelper

    private val smsRepo = app.smsRepository

    val recycleBinItemCount: StateFlow<Int> = smsRepo.getRecycleBinItemsFlow().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    ).let { flow ->
        MutableStateFlow(0).also { state ->
            viewModelScope.launch {
                flow.collect { state.value = it.size }
            }
        }
    }

    val blockedNumberCount: StateFlow<Int> = smsRepo.getBlockedNumbersFlow().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    ).let { flow ->
        MutableStateFlow(0).also { state ->
            viewModelScope.launch {
                flow.collect { state.value = it.size }
            }
        }
    }

    val userPreferences: StateFlow<UserPreferences> = preferencesRepo.userPreferencesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UserPreferences()
    )

    private val _sims = MutableStateFlow<List<SimInfo>>(emptyList())
    val availableSims: StateFlow<List<SimInfo>> = _sims.asStateFlow()

    private val _isDefaultSms = MutableStateFlow(false)
    val isDefaultSmsApp: StateFlow<Boolean> = _isDefaultSms.asStateFlow()

    init {
        refreshStatus()
    }

    fun refreshStatus() {
        _sims.value = simManager.getAvailableSims()
        _isDefaultSms.value = DefaultSmsHelper.isDefaultSmsApp(app)
    }

    fun canScheduleExactAlarms(): Boolean = alarmScheduler.canScheduleExactAlarms()
    fun hasSmsPermission(): Boolean = smsSender.hasSmsPermission()
    fun hasNotificationPermission(): Boolean = notificationHelper.hasNotificationPermission()

    fun requestDefaultSmsApp(activity: Activity) {
        DefaultSmsHelper.requestDefaultSmsApp(activity)
    }

    fun updateDefaultChannel(channel: MessageChannel) {
        viewModelScope.launch {
            preferencesRepo.setDefaultChannel(channel)
        }
    }

    fun updateDefaultSim(simId: Int) {
        viewModelScope.launch {
            preferencesRepo.setDefaultSimId(simId)
        }
    }

    fun updatePreSendReminder(minutes: Int) {
        viewModelScope.launch {
            preferencesRepo.setPreSendReminderMinutes(minutes)
        }
    }

    fun updateNotifyOnSent(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepo.setNotifyOnSent(enabled)
        }
    }

    fun updateNotifyOnFailure(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepo.setNotifyOnFailure(enabled)
        }
    }

    fun updateMissedPolicyCatchUp(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepo.setMissedPolicyCatchUp(enabled)
        }
    }

    fun updateDefaultRetryPolicy(policy: RetryPolicy) {
        viewModelScope.launch {
            preferencesRepo.setDefaultRetryPolicy(policy)
        }
    }

    fun updateDarkMode(mode: String) {
        viewModelScope.launch {
            preferencesRepo.setDarkMode(mode)
        }
    }

    fun updateIncomingSmsTone(tone: String) {
        viewModelScope.launch {
            preferencesRepo.setIncomingSmsTone(tone)
        }
    }

    fun updateScheduledSmsTone(tone: String) {
        viewModelScope.launch {
            preferencesRepo.setScheduledSmsTone(tone)
        }
    }

    fun updateSwipeActionsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepo.setSwipeActionsEnabled(enabled)
        }
    }

    fun playTonePreview(tone: String) {
        notificationHelper.playTone(tone)
    }
}
