package com.example.ui.screens.dashboard

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.ScheduleSend
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.RestorePage
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.entity.RecycleBinEntity
import com.example.data.local.entity.ScheduleEntity
import com.example.domain.model.MessageChannel
import com.example.domain.model.SmsConversation
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ScheduleCard
import com.example.ui.components.ScheduleDetailsDialog
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.BrandAccent
import com.example.ui.theme.BrandPrimary
import com.example.ui.theme.BrandPrimaryDark
import com.example.ui.theme.BrandPrimaryLight
import com.example.ui.theme.LightBg
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusScheduled
import com.example.ui.theme.StatusSuccess
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToCompose: (String, String, String) -> Unit,
    onNavigateToChatThread: (Long, String, String) -> Unit,
    onNavigateToCreateSchedule: (Long?, Long?, String?, String?) -> Unit,
    onNavigateToSchedules: () -> Unit,
    onNavigateToCalendar: () -> Unit,
    onNavigateToTemplates: () -> Unit,
    onNavigateToHistory: () -> Unit,
    viewModel: DashboardViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val userPreferences by viewModel.userPreferences.collectAsState()

    var selectedScheduleForDetails by remember { mutableStateOf<ScheduleEntity?>(null) }
    var scheduleToDelete by remember { mutableStateOf<ScheduleEntity?>(null) }
    var showDeleteSelectedDialog by remember { mutableStateOf(false) }
    var conversationToDelete by remember { mutableStateOf<SmsConversation?>(null) }
    var itemToDeletePermanently by remember { mutableStateOf<RecycleBinEntity?>(null) }
    var showEmptyBinDialog by remember { mutableStateOf(false) }
    var showStatsDialog by remember { mutableStateOf(false) }

    // Launcher for default SMS role request
    val defaultSmsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.checkDefaultSmsStatus()
        viewModel.refreshConversations()
        viewModel.dismissDefaultSmsBanner()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkDefaultSmsStatus()
                viewModel.refreshConversations()
                viewModel.cleanExpiredRecycledItems()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.checkDefaultSmsStatus()
        viewModel.refreshConversations()
        viewModel.cleanExpiredRecycledItems()
    }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbarMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (uiState.isSelectionMode) {
                val currentConversations = if (uiState.selectedTabIndex == 1) {
                    uiState.archivedConversations
                } else {
                    uiState.activeConversations
                }
                val selectedConversations = remember(uiState.selectedConversationKeys, currentConversations) {
                    currentConversations.filter { uiState.selectedConversationKeys.contains("${it.threadId}_${it.recipientPhone}") }
                }
                val allPinned = selectedConversations.isNotEmpty() && selectedConversations.all { it.isPinned }
                val allRead = selectedConversations.isNotEmpty() && selectedConversations.all { it.isRead }
                val targetList = if (uiState.selectedTabIndex == 1) uiState.filteredArchivedConversations else uiState.filteredConversations
                val allSelected = targetList.isNotEmpty() && uiState.selectedConversationKeys.size >= targetList.size

                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = "Close selection")
                        }
                    },
                    title = {
                        Text(
                            text = "${uiState.selectedConversationKeys.size} selected",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    actions = {
                        // Select All / Deselect All
                        IconButton(onClick = {
                            if (allSelected) viewModel.clearSelection() else viewModel.selectAllConversations()
                        }) {
                            Icon(
                                imageVector = if (allSelected) Icons.Default.Deselect else Icons.Default.SelectAll,
                                contentDescription = if (allSelected) "Deselect All" else "Select All"
                            )
                        }

                        if (uiState.selectedTabIndex == 0) {
                            // PIN / Unpin (Max 5)
                            IconButton(onClick = {
                                if (allPinned) viewModel.unpinSelectedConversations() else viewModel.pinSelectedConversations()
                            }) {
                                Icon(
                                    imageVector = if (allPinned) Icons.Outlined.PushPin else Icons.Default.PushPin,
                                    contentDescription = if (allPinned) "Unpin" else "Pin (Max 5)"
                                )
                            }

                            // Archive
                            IconButton(onClick = { viewModel.archiveSelectedConversations() }) {
                                Icon(
                                    imageVector = Icons.Default.Archive,
                                    contentDescription = "Archive Selected"
                                )
                            }
                        } else if (uiState.selectedTabIndex == 1) {
                            // Unarchive
                            IconButton(onClick = { viewModel.unarchiveSelectedConversations() }) {
                                Icon(
                                    imageVector = Icons.Default.Unarchive,
                                    contentDescription = "Restore to Chats"
                                )
                            }
                        }

                        // Mark Read / Unread
                        IconButton(onClick = {
                            if (allRead) viewModel.markSelectedAsUnread() else viewModel.markSelectedAsRead()
                        }) {
                            Icon(
                                imageVector = if (allRead) Icons.Default.MarkEmailUnread else Icons.Default.MarkEmailRead,
                                contentDescription = if (allRead) "Mark as Unread" else "Mark as Read"
                            )
                        }

                        // Delete Selected
                        IconButton(onClick = { showDeleteSelectedDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Delete Selected",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            } else {
                var showMoreMenu by remember { mutableStateOf(false) }

                TopAppBar(
                    title = {
                        Text(
                            text = "SamPing",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    actions = {
                        IconButton(onClick = onNavigateToTemplates, modifier = Modifier.testTag("top_nav_templates")) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.TextSnippet,
                                contentDescription = "Templates",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = onNavigateToHistory, modifier = Modifier.testTag("top_nav_logs")) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "Logs",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = onNavigateToCalendar) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = "Calendar",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More Options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("App Statistics") },
                                onClick = {
                                    showMoreMenu = false
                                    showStatsDialog = true
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.FlashOn, contentDescription = null, tint = BrandPrimary)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Refresh Inbox") },
                                onClick = {
                                    showMoreMenu = false
                                    viewModel.refreshConversations()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Restore, contentDescription = null)
                                }
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        },
        floatingActionButton = {
            if (!uiState.isSelectionMode && uiState.selectedTabIndex != 2) {
                ExtendedFloatingActionButton(
                    onClick = { onNavigateToCompose("", "", "") },
                    icon = { Icon(Icons.Default.Edit, contentDescription = "Compose Message") },
                    text = { Text("Start Chat", fontWeight = FontWeight.Bold) },
                    containerColor = BrandPrimary,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(16.dp),
                    elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
                    modifier = Modifier.testTag("fab_compose_sms")
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
        ) {
            // Search Bar
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::onSearchQueryChanged,
                placeholder = {
                    Text(
                        when (uiState.selectedTabIndex) {
                            0 -> "Search messages, contacts, numbers..."
                            1 -> "Search archived chats..."
                            else -> "Search deleted messages..."
                        },
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    if (uiState.searchQuery.isNotBlank()) {
                        IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search", modifier = Modifier.size(18.dp))
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedIndicatorColor = BrandPrimary,
                    unfocusedIndicatorColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                )
            )

            // Default SMS App Banner if not default and not dismissed
            if (uiState.showDefaultSmsBanner) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.NotificationsActive,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Set SamPing as Default SMS",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            IconButton(
                                onClick = { viewModel.dismissDefaultSmsBanner() },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Required for automatic background SMS dispatch and complete inbox synchronization.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { viewModel.dismissDefaultSmsBanner() },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("Not Now", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                        val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
                                        if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_SMS)) {
                                            val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
                                            defaultSmsLauncher.launch(intent)
                                        }
                                    } else {
                                        val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
                                            putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
                                        }
                                        defaultSmsLauncher.launch(intent)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text("Set Default", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Material Design 3 Standard TabRow: Chats | Archived | Deleted
            TabRow(
                selectedTabIndex = uiState.selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = BrandPrimary,
                divider = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    )
                }
            ) {
                Tab(
                    selected = uiState.selectedTabIndex == 0,
                    onClick = { viewModel.onTabSelected(0) },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Chats", fontWeight = if (uiState.selectedTabIndex == 0) FontWeight.Bold else FontWeight.Normal)
                            if (uiState.filteredConversations.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = CircleShape,
                                    color = if (uiState.selectedTabIndex == 0) BrandPrimary else MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = "${uiState.filteredConversations.size}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (uiState.selectedTabIndex == 0) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                )
                Tab(
                    selected = uiState.selectedTabIndex == 1,
                    onClick = { viewModel.onTabSelected(1) },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Archived", fontWeight = if (uiState.selectedTabIndex == 1) FontWeight.Bold else FontWeight.Normal)
                            if (uiState.filteredArchivedConversations.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = CircleShape,
                                    color = if (uiState.selectedTabIndex == 1) BrandPrimary else MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = "${uiState.filteredArchivedConversations.size}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (uiState.selectedTabIndex == 1) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                )
                Tab(
                    selected = uiState.selectedTabIndex == 2,
                    onClick = { viewModel.onTabSelected(2) },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Deleted", fontWeight = if (uiState.selectedTabIndex == 2) FontWeight.Bold else FontWeight.Normal)
                            if (uiState.filteredDeletedItems.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = CircleShape,
                                    color = if (uiState.selectedTabIndex == 2) BrandPrimary else MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = "${uiState.filteredDeletedItems.size}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (uiState.selectedTabIndex == 2) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                )
            }

            // Tab Content
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = BrandPrimary)
                }
            } else {
                        when (uiState.selectedTabIndex) {
                            0 -> {
                                ConversationsTabContent(
                                    conversations = uiState.filteredConversations,
                                    selectedKeys = uiState.selectedConversationKeys,
                                    isSelectionMode = uiState.isSelectionMode,
                                    swipeActionsEnabled = userPreferences.swipeActionsEnabled,
                                    isArchivedTab = false,
                                    onConversationClick = { conv ->
                                        if (uiState.isSelectionMode) {
                                            viewModel.toggleConversationSelection(conv)
                                        } else {
                                            onNavigateToChatThread(conv.threadId, conv.recipientPhone, conv.recipientName)
                                        }
                                    },
                                    onConversationLongClick = { conv ->
                                        viewModel.toggleConversationSelection(conv)
                                    },
                                    onTogglePin = { conv ->
                                        viewModel.togglePin(conv)
                                    },
                                    onMarkRead = { conv ->
                                        viewModel.markConversationAsRead(conv)
                                    },
                                    onMarkUnread = { conv ->
                                        viewModel.markConversationAsUnread(conv)
                                    },
                                    onArchive = { conv ->
                                        viewModel.archiveConversation(conv)
                                    },
                                    onUnarchive = { conv ->
                                        viewModel.unarchiveConversation(conv)
                                    },
                                    onDelete = { conv ->
                                        conversationToDelete = conv
                                    },
                                    onNewMessageClick = { onNavigateToCompose("", "", "") }
                                )
                            }
                            1 -> {
                                ConversationsTabContent(
                                    conversations = uiState.filteredArchivedConversations,
                                    selectedKeys = uiState.selectedConversationKeys,
                                    isSelectionMode = uiState.isSelectionMode,
                                    swipeActionsEnabled = userPreferences.swipeActionsEnabled,
                                    isArchivedTab = true,
                                    onConversationClick = { conv ->
                                        if (uiState.isSelectionMode) {
                                            viewModel.toggleConversationSelection(conv)
                                        } else {
                                            onNavigateToChatThread(conv.threadId, conv.recipientPhone, conv.recipientName)
                                        }
                                    },
                                    onConversationLongClick = { conv ->
                                        viewModel.toggleConversationSelection(conv)
                                    },
                                    onTogglePin = { conv ->
                                        viewModel.togglePin(conv)
                                    },
                                    onMarkRead = { conv ->
                                        viewModel.markConversationAsRead(conv)
                                    },
                                    onMarkUnread = { conv ->
                                        viewModel.markConversationAsUnread(conv)
                                    },
                                    onArchive = { conv ->
                                        viewModel.archiveConversation(conv)
                                    },
                                    onUnarchive = { conv ->
                                        viewModel.unarchiveConversation(conv)
                                    },
                                    onDelete = { conv ->
                                        conversationToDelete = conv
                                    },
                                    onNewMessageClick = { onNavigateToCompose("", "", "") }
                                )
                            }
                            2 -> {
                                DeletedTabContent(
                                    deletedItems = uiState.filteredDeletedItems,
                                    onRestoreItem = { item -> viewModel.restoreDeletedItem(item) },
                                    onRestoreConversation = { item -> viewModel.restoreDeletedConversation(item.threadId, item.recipientPhone) },
                                    onDeletePermanently = { item -> itemToDeletePermanently = item },
                                    onEmptyBin = { showEmptyBinDialog = true }
                                )
                            }
                        }
                    }
                }
            }

    // Quick Stats Overview Dialog (bKash "Tap for Balance" equivalent for SamPing)
    if (showStatsDialog) {
        AlertDialog(
            onDismissRequest = { showStatsDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.FlashOn,
                        contentDescription = null,
                        tint = BrandPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SamPing Live Overview",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Real-time summary of your SMS automation & conversation status:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatCard(
                            title = "Active Chats",
                            value = "${uiState.activeConversations.size}",
                            icon = Icons.AutoMirrored.Filled.Chat,
                            accentColor = BrandPrimary,
                            modifier = Modifier.weight(1f),
                            onClick = { showStatsDialog = false }
                        )
                        StatCard(
                            title = "Active Pings",
                            value = "${uiState.totalActiveCount}",
                            icon = Icons.Default.Schedule,
                            accentColor = Color(0xFF4F46E5),
                            modifier = Modifier.weight(1f),
                            onClick = {
                                showStatsDialog = false
                                onNavigateToSchedules()
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatCard(
                            title = "Sent Today",
                            value = "${uiState.sentTodayCount}",
                            icon = Icons.Default.CheckCircle,
                            accentColor = StatusSuccess,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                showStatsDialog = false
                                onNavigateToHistory()
                            }
                        )
                        StatCard(
                            title = "Archived",
                            value = "${uiState.archivedConversations.size}",
                            icon = Icons.Default.Inventory2,
                            accentColor = Color(0xFF475569),
                            modifier = Modifier.weight(1f),
                            onClick = {
                                showStatsDialog = false
                                viewModel.onTabSelected(1)
                            }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showStatsDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Close", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Delete Selected Conversations Dialog
    if (showDeleteSelectedDialog) {
        val count = uiState.selectedConversationKeys.size
        AlertDialog(
            onDismissRequest = { showDeleteSelectedDialog = false },
            title = { Text("Move $count Conversation${if (count > 1) "s" else ""} to Deleted?") },
            text = { Text("Selected conversation(s) will be moved to Deleted and retained for 30 days before permanent removal.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSelectedConversations()
                        showDeleteSelectedDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_selected_btn")
                ) {
                    Text("Move to Deleted ($count)")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSelectedDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Single Conversation Dialog
    if (conversationToDelete != null) {
        val conv = conversationToDelete!!
        AlertDialog(
            onDismissRequest = { conversationToDelete = null },
            title = { Text("Move to Deleted?") },
            text = { Text("Move conversation with ${conv.recipientName.ifBlank { conv.recipientPhone }} to Deleted? It can be restored within 30 days.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteConversation(conv)
                        conversationToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_conv_btn")
                ) {
                    Text("Move to Deleted")
                }
            },
            dismissButton = {
                TextButton(onClick = { conversationToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Empty Recycle Bin Dialog
    if (showEmptyBinDialog) {
        val count = uiState.deletedItems.size
        AlertDialog(
            onDismissRequest = { showEmptyBinDialog = false },
            icon = {
                Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(32.dp))
            },
            title = { Text("Empty Deleted Bin?", fontWeight = FontWeight.Bold) },
            text = { Text("This will permanently delete all $count item(s) from Deleted. This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.emptyDeletedBin()
                        showEmptyBinDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_empty_bin_btn")
                ) {
                    Text("Empty Bin")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyBinDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Permanently Delete Single Item Dialog
    if (itemToDeletePermanently != null) {
        val item = itemToDeletePermanently!!
        AlertDialog(
            onDismissRequest = { itemToDeletePermanently = null },
            title = { Text("Permanently Delete?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to permanently delete this message? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteDeletedItemPermanently(item)
                        itemToDeletePermanently = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_permanent_btn")
                ) {
                    Text("Delete Permanently")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDeletePermanently = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Schedule Details Dialog
    if (selectedScheduleForDetails != null) {
        val current = selectedScheduleForDetails!!
        ScheduleDetailsDialog(
            schedule = current,
            onDismiss = { selectedScheduleForDetails = null },
            onEdit = {
                val id = current.id
                selectedScheduleForDetails = null
                onNavigateToCreateSchedule(id, null, null, null)
            },
            onSendNow = {
                viewModel.sendNow(current)
                selectedScheduleForDetails = null
            },
            onDelete = {
                scheduleToDelete = current
                selectedScheduleForDetails = null
            }
        )
    }

    // Confirm Delete Dialog for Schedules
    if (scheduleToDelete != null) {
        val schedule = scheduleToDelete!!
        AlertDialog(
            onDismissRequest = { scheduleToDelete = null },
            title = { Text("Delete Schedule?") },
            text = { Text("Are you sure you want to cancel and delete the scheduled message to ${schedule.recipientName.ifBlank { schedule.recipientPhone }}?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSchedule(schedule)
                        scheduleToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_btn")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { scheduleToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConversationsTabContent(
    conversations: List<SmsConversation>,
    selectedKeys: Set<String>,
    isSelectionMode: Boolean,
    swipeActionsEnabled: Boolean,
    isArchivedTab: Boolean,
    onConversationClick: (SmsConversation) -> Unit,
    onConversationLongClick: (SmsConversation) -> Unit,
    onTogglePin: (SmsConversation) -> Unit,
    onMarkRead: (SmsConversation) -> Unit,
    onMarkUnread: (SmsConversation) -> Unit,
    onArchive: (SmsConversation) -> Unit,
    onUnarchive: (SmsConversation) -> Unit,
    onDelete: (SmsConversation) -> Unit,
    onNewMessageClick: () -> Unit
) {
    if (conversations.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isArchivedTab) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        modifier = Modifier.size(72.dp),
                        shape = CircleShape,
                        color = BrandPrimaryLight.copy(alpha = 0.6f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Archive,
                                contentDescription = null,
                                tint = BrandPrimary,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No Archived Chats",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Archived conversations disappear from Chats and appear here with full message history preserved.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            } else {
                EmptyStateCard(
                    icon = Icons.AutoMirrored.Filled.Message,
                    title = "No Messages Yet",
                    description = "Your SMS inbox is ready. Tap the button below to start a new chat or schedule a text.",
                    actionButtonText = "Compose New Message",
                    onActionClick = onNewMessageClick
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(conversations, key = { "${it.threadId}_${it.recipientPhone}" }) { conv ->
                val isSelected = selectedKeys.contains("${conv.threadId}_${conv.recipientPhone}")
                ConversationItemCard(
                    conversation = conv,
                    isSelected = isSelected,
                    isSelectionMode = isSelectionMode,
                    swipeActionsEnabled = swipeActionsEnabled,
                    isArchivedTab = isArchivedTab,
                    onClick = { onConversationClick(conv) },
                    onLongClick = { onConversationLongClick(conv) },
                    onTogglePin = { onTogglePin(conv) },
                    onMarkRead = { onMarkRead(conv) },
                    onMarkUnread = { onMarkUnread(conv) },
                    onArchive = { onArchive(conv) },
                    onUnarchive = { onUnarchive(conv) },
                    onDelete = { onDelete(conv) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ConversationItemCard(
    conversation: SmsConversation,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    swipeActionsEnabled: Boolean,
    isArchivedTab: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onTogglePin: () -> Unit,
    onMarkRead: () -> Unit,
    onMarkUnread: () -> Unit,
    onArchive: () -> Unit,
    onUnarchive: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    val outlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)

    val cardBorder = when {
        isSelected -> BorderStroke(1.5.dp, BrandPrimary)
        conversation.isPinned -> BorderStroke(1.dp, BrandPrimary.copy(alpha = 0.4f))
        else -> BorderStroke(1.dp, outlineColor)
    }

    val cardBackground = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        conversation.isPinned -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        else -> MaterialTheme.colorScheme.surface
    }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (swipeActionsEnabled && !isSelectionMode) {
                when (value) {
                    SwipeToDismissBoxValue.StartToEnd -> {
                        if (isArchivedTab) onUnarchive() else onArchive()
                        false
                    }
                    SwipeToDismissBoxValue.EndToStart -> {
                        onDelete()
                        false
                    }
                    SwipeToDismissBoxValue.Settled -> false
                }
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = swipeActionsEnabled && !isSelectionMode,
        enableDismissFromEndToStart = swipeActionsEnabled && !isSelectionMode,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val color = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Color(0xFF10B981) // Archive / Restore green
                SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.error // Delete red
                SwipeToDismissBoxValue.Settled -> Color.Transparent
            }
            val alignment = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                SwipeToDismissBoxValue.Settled -> Alignment.Center
            }
            val icon = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> if (isArchivedTab) Icons.Default.Unarchive else Icons.Default.Archive
                SwipeToDismissBoxValue.EndToStart -> Icons.Default.Delete
                SwipeToDismissBoxValue.Settled -> null
            }
            val text = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> if (isArchivedTab) "Unarchive" else "Archive"
                SwipeToDismissBoxValue.EndToStart -> "Delete"
                SwipeToDismissBoxValue.Settled -> ""
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(color)
                    .padding(horizontal = 20.dp),
                contentAlignment = alignment
            ) {
                if (icon != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (direction == SwipeToDismissBoxValue.StartToEnd) {
                            Icon(icon, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text, color = Color.White, fontWeight = FontWeight.Bold)
                        } else {
                            Text(text, color = Color.White, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(icon, contentDescription = null, tint = Color.White)
                        }
                    }
                }
            }
        }
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                ),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = cardBackground),
            border = cardBorder,
            elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 2.dp else 0.5.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Selection Checkbox when in selection mode
                if (isSelectionMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onClick() },
                        colors = CheckboxDefaults.colors(checkedColor = BrandPrimary),
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }

                // Contact Avatar / Initial Circle
                Box(contentAlignment = Alignment.BottomEnd) {
                    Surface(
                        modifier = Modifier.size(46.dp),
                        shape = CircleShape,
                        color = when {
                            isSelected -> BrandPrimary
                            conversation.unreadCount > 0 -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (isSelected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            } else {
                                val initial = (conversation.recipientName.ifBlank { conversation.recipientPhone })
                                    .take(1).uppercase(Locale.getDefault())
                                Text(
                                    text = initial,
                                    fontWeight = FontWeight.Bold,
                                    color = if (conversation.unreadCount > 0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 17.sp
                                )
                            }
                        }
                    }

                    if (conversation.isPinned && !isSelected) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(BrandPrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.PushPin,
                                contentDescription = "Pinned",
                                tint = Color.White,
                                modifier = Modifier.size(9.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = conversation.recipientName.ifBlank { conversation.recipientPhone },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = if (conversation.unreadCount > 0) FontWeight.Bold else FontWeight.SemiBold,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (conversation.isPinned) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    Icons.Default.PushPin,
                                    contentDescription = "Pinned",
                                    tint = BrandPrimary,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }

                        val dateStr = remember(conversation.dateEpochMs) {
                            val now = System.currentTimeMillis()
                            val diff = now - conversation.dateEpochMs
                            when {
                                diff < 86400000L -> {
                                    val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
                                    sdf.format(Date(conversation.dateEpochMs))
                                }
                                diff < 7 * 86400000L -> {
                                    val sdf = SimpleDateFormat("EEE", Locale.getDefault())
                                    sdf.format(Date(conversation.dateEpochMs))
                                }
                                else -> {
                                    val sdf = SimpleDateFormat("MMM d", Locale.getDefault())
                                    sdf.format(Date(conversation.dateEpochMs))
                                }
                            }
                        }

                        Text(
                            text = dateStr,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (conversation.unreadCount > 0) BrandPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            fontWeight = if (conversation.unreadCount > 0) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 11.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = conversation.snippet.ifBlank { "Tap to view conversation" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (conversation.unreadCount > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (conversation.unreadCount > 0) FontWeight.SemiBold else FontWeight.Normal,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        if (conversation.hasScheduledMessages) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(StatusScheduled.copy(alpha = 0.12f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ScheduleSend,
                                        contentDescription = null,
                                        tint = StatusScheduled,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "Scheduled",
                                        color = StatusScheduled,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        if (conversation.unreadCount > 0) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(BrandPrimary),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${conversation.unreadCount}",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Options Menu
                if (!isSelectionMode) {
                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "Options",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            if (!isArchivedTab) {
                                DropdownMenuItem(
                                    text = { Text(if (conversation.isPinned) "Unpin chat" else "Pin chat (Max 5)") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (conversation.isPinned) Icons.Outlined.PushPin else Icons.Default.PushPin,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        onTogglePin()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Archive chat") },
                                    leadingIcon = { Icon(Icons.Default.Archive, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        onArchive()
                                    }
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text("Restore to Chats") },
                                    leadingIcon = { Icon(Icons.Default.Unarchive, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        onUnarchive()
                                    }
                                )
                            }

                            if (conversation.unreadCount > 0) {
                                DropdownMenuItem(
                                    text = { Text("Mark as read") },
                                    leadingIcon = { Icon(Icons.Default.MarkEmailRead, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        onMarkRead()
                                    }
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text("Mark as unread") },
                                    leadingIcon = { Icon(Icons.Default.MarkEmailUnread, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        onMarkUnread()
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Select") },
                                leadingIcon = { Icon(Icons.Default.SelectAll, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    onLongClick()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete conversation", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showMenu = false
                                    onDelete()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DeletedTabContent(
    deletedItems: List<RecycleBinEntity>,
    onRestoreItem: (RecycleBinEntity) -> Unit,
    onRestoreConversation: (RecycleBinEntity) -> Unit,
    onDeletePermanently: (RecycleBinEntity) -> Unit,
    onEmptyBin: () -> Unit
) {
    if (deletedItems.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                modifier = Modifier.size(72.dp),
                shape = CircleShape,
                color = BrandPrimaryLight.copy(alpha = 0.5f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = null,
                        tint = BrandPrimary,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Deleted is Empty",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Deleted SMS messages and conversations are kept here for 30 days before permanent deletion.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header summary bar with Empty Bin button
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Auto-deletes after 30 days",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                        TextButton(
                            onClick = onEmptyBin,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Empty Bin", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            items(deletedItems, key = { it.id }) { item ->
                DeletedItemCard(
                    item = item,
                    onRestoreItem = { onRestoreItem(item) },
                    onRestoreConversation = { onRestoreConversation(item) },
                    onDeletePermanently = { onDeletePermanently(item) }
                )
            }
        }
    }
}

@Composable
fun DeletedItemCard(
    item: RecycleBinEntity,
    onRestoreItem: () -> Unit,
    onRestoreConversation: () -> Unit,
    onDeletePermanently: () -> Unit
) {
    val now = System.currentTimeMillis()
    val thirtyDaysMs = 30L * 24L * 60L * 60L * 1000L
    val expiryEpochMs = item.deletedAtEpochMs + thirtyDaysMs
    val msRemaining = (expiryEpochMs - now).coerceAtLeast(0L)
    val daysRemaining = TimeUnit.MILLISECONDS.toDays(msRemaining).toInt()

    val dateFormat = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault())
    val deletedDateStr = dateFormat.format(Date(item.deletedAtEpochMs))

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Name / Phone & Days Left Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        modifier = Modifier.size(34.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = (item.recipientName.ifBlank { item.recipientPhone }).take(1).uppercase(Locale.getDefault()),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = item.recipientName.ifBlank { item.recipientPhone },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (item.recipientName.isNotBlank() && item.recipientName != item.recipientPhone) {
                            Text(
                                text = item.recipientPhone,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (daysRemaining <= 3) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = if (daysRemaining == 0) "Deletes today" else "$daysRemaining days left",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (daysRemaining <= 3) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Body
            Text(
                text = item.messageBody,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Deleted date info
            Text(
                text = "Deleted: $deletedDateStr",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                fontSize = 11.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Delete permanently button
                IconButton(
                    onClick = onDeletePermanently,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete permanently",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                if (item.itemType == "CONVERSATION") {
                    OutlinedButton(
                        onClick = onRestoreConversation,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Default.RestorePage, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Restore Thread", fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }

                FilledTonalButton(
                    onClick = onRestoreItem,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp), tint = StatusSuccess)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Restore", fontSize = 12.sp, color = StatusSuccess, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = title,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}
