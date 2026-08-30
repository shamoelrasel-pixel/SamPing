package com.example.ui.screens.dashboard

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.AutoSendApplication
import com.example.data.local.entity.HistoryEntity
import com.example.data.local.entity.RecycleBinEntity
import com.example.data.local.entity.ScheduleEntity
import com.example.domain.engine.TemplateParser
import com.example.domain.model.DeliveryStatus
import com.example.domain.model.MessageChannel
import com.example.domain.model.RecurrenceType
import com.example.domain.model.ScheduleStatus
import com.example.domain.model.SmsConversation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

data class DashboardUiState(
    val selectedTabIndex: Int = 0, // 0 = Chats, 1 = Archived, 2 = Deleted
    val searchQuery: String = "",
    val conversations: List<SmsConversation> = emptyList(),
    val activeConversations: List<SmsConversation> = emptyList(),
    val filteredConversations: List<SmsConversation> = emptyList(),
    val archivedConversations: List<SmsConversation> = emptyList(),
    val filteredArchivedConversations: List<SmsConversation> = emptyList(),
    val deletedItems: List<RecycleBinEntity> = emptyList(),
    val filteredDeletedItems: List<RecycleBinEntity> = emptyList(),
    val selectedConversationKeys: Set<String> = emptySet(),
    val isSelectionMode: Boolean = false,
    val upcomingSchedules: List<ScheduleEntity> = emptyList(),
    val todaySchedules: List<ScheduleEntity> = emptyList(),
    val totalActiveCount: Int = 0,
    val recurringCount: Int = 0,
    val sentTodayCount: Int = 0,
    val failedCount: Int = 0,
    val nextScheduledItem: ScheduleEntity? = null,
    val isDefaultSmsApp: Boolean = false,
    val showDefaultSmsBanner: Boolean = false,
    val snackbarMessage: String? = null,
    val isLoading: Boolean = false
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as AutoSendApplication
    private val scheduleRepo = app.scheduleRepository
    private val historyRepo = app.historyRepository
    private val smsRepo = app.smsRepository
    private val alarmScheduler = app.alarmScheduler
    private val notificationHelper = app.notificationHelper
    private val simManager = app.simManager
    private val smsSender = app.smsSender
    private val preferencesRepo = app.userPreferencesRepository

    val userPreferences = preferencesRepo.userPreferencesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = com.example.data.preferences.UserPreferences()
    )

    private val _searchQuery = MutableStateFlow("")
    private val _selectedTab = MutableStateFlow(0)
    private val _isDefaultSmsState = MutableStateFlow(smsRepo.isDefaultSmsApp())
    private val _isBannerDismissed = MutableStateFlow(false)
    private val _selectedConversationKeys = MutableStateFlow<Set<String>>(emptySet())
    private val _snackbarMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<DashboardUiState> = combine(
        combine(
            smsRepo.conversationsFlow,
            smsRepo.getRecycleBinItemsFlow(),
            scheduleRepo.allSchedules,
            historyRepo.getSentTodayCount(),
            historyRepo.failedCount
        ) { convs, recycledItems, schedules, sentToday, failedCount ->
            Quintuple(convs, recycledItems, schedules, sentToday, failedCount)
        },
        combine(
            _searchQuery,
            _selectedTab,
            _isDefaultSmsState,
            _isBannerDismissed
        ) { query, tabIndex, isDefault, isDismissed ->
            Quadruple(query, tabIndex, isDefault, isDismissed)
        },
        combine(
            _selectedConversationKeys,
            _snackbarMessage
        ) { selectedKeys, snackbarMsg ->
            Pair(selectedKeys, snackbarMsg)
        }
    ) { (convs, recycledItems, schedules, sentToday, failedCount), (query, tabIndex, isDefault, isDismissed), (selectedKeys, snackbarMsg) ->
        val now = System.currentTimeMillis()
        val startOfToday = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endOfToday = startOfToday + 86400000L

        val active = schedules.filter { it.status == ScheduleStatus.SCHEDULED }
        val today = active.filter { it.nextExecutionEpochMs in startOfToday until endOfToday }
        val upcoming = active.filter { it.nextExecutionEpochMs >= now }.sortedBy { it.nextExecutionEpochMs }
        val recurring = active.filter { it.recurrenceType != RecurrenceType.ONCE }

        // Split into Active (Chats) vs Archived
        val activeChats = convs.filter { !it.isArchived }
        val archivedChats = convs.filter { it.isArchived }

        val filteredChats = if (query.isBlank()) {
            activeChats
        } else {
            activeChats.filter {
                it.recipientName.contains(query, ignoreCase = true) ||
                it.recipientPhone.contains(query) ||
                it.snippet.contains(query, ignoreCase = true)
            }
        }

        val filteredArchived = if (query.isBlank()) {
            archivedChats
        } else {
            archivedChats.filter {
                it.recipientName.contains(query, ignoreCase = true) ||
                it.recipientPhone.contains(query) ||
                it.snippet.contains(query, ignoreCase = true)
            }
        }

        val filteredRecycled = if (query.isBlank()) {
            recycledItems
        } else {
            recycledItems.filter {
                it.recipientName.contains(query, ignoreCase = true) ||
                it.recipientPhone.contains(query) ||
                it.messageBody.contains(query, ignoreCase = true)
            }
        }

        val showBanner = !isDefault && !isDismissed

        DashboardUiState(
            selectedTabIndex = tabIndex,
            searchQuery = query,
            conversations = convs,
            activeConversations = activeChats,
            filteredConversations = filteredChats,
            archivedConversations = archivedChats,
            filteredArchivedConversations = filteredArchived,
            deletedItems = recycledItems,
            filteredDeletedItems = filteredRecycled,
            selectedConversationKeys = selectedKeys,
            isSelectionMode = selectedKeys.isNotEmpty(),
            upcomingSchedules = upcoming.take(15),
            todaySchedules = today,
            totalActiveCount = active.size,
            recurringCount = recurring.size,
            sentTodayCount = sentToday,
            failedCount = failedCount,
            nextScheduledItem = upcoming.firstOrNull(),
            isDefaultSmsApp = isDefault,
            showDefaultSmsBanner = showBanner,
            snackbarMessage = snackbarMsg,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState(isLoading = true)
    )

    init {
        checkDefaultSmsStatus()
        refreshConversations()
        cleanExpiredRecycledItems()
    }

    fun checkDefaultSmsStatus() {
        _isDefaultSmsState.value = smsRepo.isDefaultSmsApp()
    }

    fun dismissDefaultSmsBanner() {
        _isBannerDismissed.value = true
    }

    fun onTabSelected(index: Int) {
        _selectedTab.value = index
        clearSelection()
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun refreshConversations() {
        viewModelScope.launch {
            smsRepo.refreshConversations()
        }
    }

    fun cleanExpiredRecycledItems() {
        viewModelScope.launch {
            smsRepo.cleanExpiredRecycledItems()
        }
    }

    fun clearSnackbarMessage() {
        _snackbarMessage.value = null
    }

    fun showSnackbar(message: String) {
        _snackbarMessage.value = message
    }

    // --- Message Multi-Selection Operations ---

    private fun getConversationKey(conv: SmsConversation): String = "${conv.threadId}_${conv.recipientPhone}"

    fun toggleConversationSelection(conv: SmsConversation) {
        val key = getConversationKey(conv)
        val current = _selectedConversationKeys.value.toMutableSet()
        if (current.contains(key)) {
            current.remove(key)
        } else {
            current.add(key)
        }
        _selectedConversationKeys.value = current
    }

    fun selectAllConversations() {
        val currentConvs = if (_selectedTab.value == 1) {
            uiState.value.filteredArchivedConversations
        } else {
            uiState.value.filteredConversations
        }
        _selectedConversationKeys.value = currentConvs.map { getConversationKey(it) }.toSet()
    }

    fun clearSelection() {
        _selectedConversationKeys.value = emptySet()
    }

    fun togglePin(conv: SmsConversation) {
        viewModelScope.launch {
            val result = smsRepo.togglePin(conv.threadId, conv.recipientPhone)
            when (result) {
                is com.example.data.repository.PinResult.Success -> {
                    showSnackbar(if (result.isPinned) "Pinned conversation" else "Unpinned conversation")
                }
                is com.example.data.repository.PinResult.LimitReached -> {
                    showSnackbar(result.message)
                }
            }
            smsRepo.refreshConversations()
        }
    }

    fun pinSelectedConversations() {
        val selected = getSelectedConversationsList()
        if (selected.isEmpty()) return

        viewModelScope.launch {
            val items = selected.map { it.threadId to it.recipientPhone }
            val result = smsRepo.pinConversations(items)
            when (result) {
                is com.example.data.repository.PinResult.Success -> {
                    showSnackbar(result.message)
                }
                is com.example.data.repository.PinResult.LimitReached -> {
                    showSnackbar(result.message)
                }
            }
            clearSelection()
            smsRepo.refreshConversations()
        }
    }

    fun unpinSelectedConversations() {
        val selected = getSelectedConversationsList()
        if (selected.isEmpty()) return

        viewModelScope.launch {
            val items = selected.map { it.threadId to it.recipientPhone }
            smsRepo.unpinConversations(items)
            showSnackbar("Unpinned ${selected.size} conversation${if (selected.size > 1) "s" else ""}")
            clearSelection()
            smsRepo.refreshConversations()
        }
    }

    fun markSelectedAsRead() {
        val selected = getSelectedConversationsList()
        if (selected.isEmpty()) return

        viewModelScope.launch {
            val items = selected.map { it.threadId to it.recipientPhone }
            smsRepo.markThreadsAsRead(items)
            showSnackbar("Marked ${selected.size} conversation${if (selected.size > 1) "s" else ""} as read")
            clearSelection()
        }
    }

    fun markSelectedAsUnread() {
        val selected = getSelectedConversationsList()
        if (selected.isEmpty()) return

        viewModelScope.launch {
            val items = selected.map { it.threadId to it.recipientPhone }
            smsRepo.markThreadsAsUnread(items)
            showSnackbar("Marked ${selected.size} conversation${if (selected.size > 1) "s" else ""} as unread")
            clearSelection()
        }
    }

    fun deleteSelectedConversations() {
        val selected = getSelectedConversationsList()
        if (selected.isEmpty()) return

        viewModelScope.launch {
            val items = selected.map { it.threadId to it.recipientPhone }
            smsRepo.deleteThreads(items)
            showSnackbar("Moved ${selected.size} conversation${if (selected.size > 1) "s" else ""} to Deleted")
            clearSelection()
        }
    }

    fun archiveSelectedConversations() {
        val selected = getSelectedConversationsList()
        if (selected.isEmpty()) return

        viewModelScope.launch {
            val items = selected.map { it.threadId to it.recipientPhone }
            smsRepo.archiveConversations(items)
            showSnackbar("Archived ${selected.size} conversation${if (selected.size > 1) "s" else ""}")
            clearSelection()
        }
    }

    fun unarchiveSelectedConversations() {
        val selected = getSelectedConversationsList()
        if (selected.isEmpty()) return

        viewModelScope.launch {
            val items = selected.map { it.threadId to it.recipientPhone }
            smsRepo.unarchiveConversations(items)
            showSnackbar("Restored ${selected.size} conversation${if (selected.size > 1) "s" else ""} to Chats")
            clearSelection()
        }
    }

    fun deleteConversation(conv: SmsConversation) {
        viewModelScope.launch {
            smsRepo.deleteThreads(listOf(conv.threadId to conv.recipientPhone))
            showSnackbar("Conversation moved to Deleted")
        }
    }

    fun archiveConversation(conv: SmsConversation) {
        viewModelScope.launch {
            smsRepo.archiveConversation(conv.threadId, conv.recipientPhone)
            showSnackbar("Archived conversation with ${conv.recipientName.ifBlank { conv.recipientPhone }}")
        }
    }

    fun unarchiveConversation(conv: SmsConversation) {
        viewModelScope.launch {
            smsRepo.unarchiveConversation(conv.threadId, conv.recipientPhone)
            showSnackbar("Restored ${conv.recipientName.ifBlank { conv.recipientPhone }} to Chats")
        }
    }

    fun markConversationAsRead(conv: SmsConversation) {
        viewModelScope.launch {
            smsRepo.markThreadsAsRead(listOf(conv.threadId to conv.recipientPhone))
        }
    }

    fun markConversationAsUnread(conv: SmsConversation) {
        viewModelScope.launch {
            smsRepo.markThreadsAsUnread(listOf(conv.threadId to conv.recipientPhone))
            showSnackbar("Marked as unread")
        }
    }

    // --- Deleted (Recycle Bin) Operations ---

    fun restoreDeletedItem(item: RecycleBinEntity) {
        viewModelScope.launch {
            smsRepo.restoreRecycledItem(item)
            showSnackbar("Restored message from ${item.recipientName.ifBlank { item.recipientPhone }}")
        }
    }

    fun restoreDeletedConversation(threadId: Long, recipientPhone: String) {
        viewModelScope.launch {
            smsRepo.restoreRecycledConversation(threadId, recipientPhone)
            showSnackbar("Restored conversation with $recipientPhone")
        }
    }

    fun deleteDeletedItemPermanently(item: RecycleBinEntity) {
        viewModelScope.launch {
            smsRepo.deleteRecycledItemById(item.id)
            showSnackbar("Item permanently deleted")
        }
    }

    fun emptyDeletedBin() {
        viewModelScope.launch {
            smsRepo.emptyRecycleBin()
            showSnackbar("Deleted items emptied")
        }
    }

    private fun getSelectedConversationsList(): List<SmsConversation> {
        val keys = _selectedConversationKeys.value
        return uiState.value.conversations.filter { keys.contains(getConversationKey(it)) }
    }

    fun togglePause(schedule: ScheduleEntity) {
        viewModelScope.launch {
            if (schedule.status == ScheduleStatus.SCHEDULED) {
                alarmScheduler.cancelSchedule(schedule.id)
                scheduleRepo.updateStatus(schedule.id, ScheduleStatus.PAUSED)
            } else if (schedule.status == ScheduleStatus.PAUSED) {
                scheduleRepo.updateStatus(schedule.id, ScheduleStatus.SCHEDULED)
                alarmScheduler.scheduleMessageExecution(
                    scheduleId = schedule.id,
                    triggerEpochMs = schedule.nextExecutionEpochMs,
                    preSendMinutes = schedule.preSendReminderMinutes
                )
            }
            smsRepo.refreshConversations()
        }
    }

    fun deleteSchedule(schedule: ScheduleEntity) {
        viewModelScope.launch {
            alarmScheduler.cancelSchedule(schedule.id)
            scheduleRepo.deleteSchedule(schedule)
            smsRepo.refreshConversations()
        }
    }

    fun duplicateSchedule(schedule: ScheduleEntity) {
        viewModelScope.launch {
            val duplicated = schedule.copy(
                id = 0,
                nextExecutionEpochMs = System.currentTimeMillis() + 3600000L,
                executionCount = 0,
                retryAttempt = 0,
                status = ScheduleStatus.SCHEDULED,
                createdAtEpochMs = System.currentTimeMillis(),
                updatedAtEpochMs = System.currentTimeMillis()
            )
            val newId = scheduleRepo.insertSchedule(duplicated)
            alarmScheduler.scheduleMessageExecution(
                scheduleId = newId,
                triggerEpochMs = duplicated.nextExecutionEpochMs,
                preSendMinutes = duplicated.preSendReminderMinutes
            )
            smsRepo.refreshConversations()
        }
    }

    fun sendNow(schedule: ScheduleEntity) {
        viewModelScope.launch {
            val parsed = TemplateParser.parse(
                schedule.messageBody,
                schedule.recipientName,
                System.currentTimeMillis()
            )

            val hasSmsPermission = ContextCompat.checkSelfPermission(
                app,
                Manifest.permission.SEND_SMS
            ) == PackageManager.PERMISSION_GRANTED

            val simInfo = simManager.getSimInfoForSubscriptionId(schedule.simSubscriptionId)

            if (!hasSmsPermission) {
                val errorMsg = "SEND_SMS permission is not granted. Please grant permission in Settings."
                val historyId = historyRepo.insertHistory(
                    HistoryEntity(
                        scheduleId = schedule.id,
                        recipientName = schedule.recipientName.ifBlank { schedule.recipientPhone },
                        recipientPhone = schedule.recipientPhone,
                        messageBody = parsed,
                        channel = MessageChannel.SMS,
                        simDisplayName = simInfo?.displayName ?: "SIM 1",
                        scheduledEpochMs = System.currentTimeMillis(),
                        executedEpochMs = System.currentTimeMillis(),
                        status = DeliveryStatus.FAILED,
                        errorReason = errorMsg,
                        retryAttempt = 0
                    )
                )
                notificationHelper.showPermissionRequiredNotification(
                    scheduleId = schedule.id,
                    recipientName = schedule.recipientName.ifBlank { schedule.recipientPhone }
                )
                return@launch
            }

            val historyId = historyRepo.insertHistory(
                HistoryEntity(
                    scheduleId = schedule.id,
                    recipientName = schedule.recipientName.ifBlank { schedule.recipientPhone },
                    recipientPhone = schedule.recipientPhone,
                    messageBody = parsed,
                    channel = MessageChannel.SMS,
                    simDisplayName = simInfo?.displayName ?: "SIM 1",
                    scheduledEpochMs = System.currentTimeMillis(),
                    executedEpochMs = System.currentTimeMillis(),
                    status = DeliveryStatus.SENT,
                    errorReason = null,
                    retryAttempt = 0
                )
            )

            val result = smsSender.sendSms(
                scheduleId = schedule.id,
                historyId = historyId,
                recipientPhone = schedule.recipientPhone,
                messageText = parsed,
                subscriptionId = schedule.simSubscriptionId
            )

            if (result.isSuccess) {
                historyRepo.updateStatus(historyId, DeliveryStatus.SENT, null)
                notificationHelper.showSmsSentNotification(
                    recipientName = schedule.recipientName.ifBlank { schedule.recipientPhone },
                    messagePreview = parsed
                )
                smsRepo.refreshConversations()
            } else {
                historyRepo.updateStatus(historyId, DeliveryStatus.FAILED, result.errorMessage)
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
private data class Quintuple<A, B, C, D, E>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E)

