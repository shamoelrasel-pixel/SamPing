package com.example.ui.screens.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Drafts
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ScheduleSend
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.domain.model.SmsChatMessage
import com.example.domain.util.PhoneNumberDetector
import com.example.domain.util.TextFormatter
import com.example.domain.util.TextFormattingToolbar
import com.example.ui.theme.BrandIndigo
import com.example.ui.theme.StatusScheduled
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatThreadScreen(
    threadId: Long,
    address: String,
    name: String,
    onNavigateBack: () -> Unit,
    onNavigateToScheduleDetails: (Long) -> Unit,
    viewModel: ChatThreadViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    var textFieldValue by remember { mutableStateOf(TextFieldValue(uiState.messageInput)) }

    // Tap-to-Call Confirmation State (ALWAYS confirm before calling)
    var pendingCallNumber by remember { mutableStateOf<String?>(null) }
    var pendingCallLabel by remember { mutableStateOf<String?>(null) }

    // Message options & delete states
    var selectedMessageForOptions by remember { mutableStateOf<SmsChatMessage?>(null) }
    var showDeleteMessageDialog by remember { mutableStateOf<SmsChatMessage?>(null) }
    var showDeleteConversationDialog by remember { mutableStateOf(false) }
    var showBlockConfirmationDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    LaunchedEffect(threadId, address, name) {
        viewModel.initConversation(threadId, address, name)
    }

    LaunchedEffect(uiState.messageInput) {
        if (uiState.messageInput != textFieldValue.text) {
            textFieldValue = TextFieldValue(
                text = uiState.messageInput,
                selection = androidx.compose.ui.text.TextRange(uiState.messageInput.length)
            )
        }
    }

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    LaunchedEffect(uiState.successSnackbar) {
        uiState.successSnackbar?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
            viewModel.dismissSnackbar()
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Long)
            viewModel.dismissSnackbar()
        }
    }

    val handleBack = {
        viewModel.saveDraftAndExit {
            onNavigateBack()
        }
    }

    BackHandler {
        handleBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(38.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                val initials = (uiState.contactName.ifBlank { uiState.address })
                                    .take(1).uppercase(Locale.getDefault())
                                Text(
                                    text = initials,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontSize = 16.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = uiState.contactName.ifBlank { uiState.address },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (uiState.contactName.isNotBlank() && uiState.contactName != uiState.address) {
                                Text(
                                    text = uiState.address,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { handleBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Save as Draft
                    IconButton(
                        onClick = { viewModel.saveCurrentAsDraft() },
                        enabled = textFieldValue.text.isNotBlank()
                    ) {
                        Icon(
                            imageVector = Icons.Default.BookmarkBorder,
                            contentDescription = "Save Draft",
                            tint = if (textFieldValue.text.isNotBlank()) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    }
                    // Call button (always opens confirmation)
                    IconButton(
                        onClick = {
                            pendingCallNumber = uiState.address
                            pendingCallLabel = uiState.contactName.ifBlank { uiState.address }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Call Contact",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    // Schedule shortcut
                    IconButton(
                        onClick = {
                            if (textFieldValue.text.isBlank()) {
                                viewModel.onMessageInputChange("Hi, ")
                            }
                            viewModel.openScheduleDialog()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ScheduleSend,
                            contentDescription = "Schedule SMS",
                            tint = StatusScheduled
                        )
                    }
                    // 3-dots overflow menu for Block / Delete Conversation
                    IconButton(onClick = { showMenu = !showMenu }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More Options")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        if (uiState.isArchived) {
                            DropdownMenuItem(
                                text = { Text("Restore to Chats") },
                                onClick = {
                                    showMenu = false
                                    viewModel.toggleArchive()
                                },
                                leadingIcon = { Icon(Icons.Default.Unarchive, contentDescription = null) }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("Archive Conversation") },
                                onClick = {
                                    showMenu = false
                                    viewModel.toggleArchive()
                                },
                                leadingIcon = { Icon(Icons.Default.Archive, contentDescription = null) }
                            )
                        }
                        if (uiState.isBlocked) {
                            DropdownMenuItem(
                                text = { Text("Unblock SMS Sender") },
                                onClick = {
                                    showMenu = false
                                    viewModel.unblockSender()
                                },
                                leadingIcon = { Icon(Icons.Default.Block, contentDescription = null) }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("Block SMS Sender") },
                                onClick = {
                                    showMenu = false
                                    showBlockConfirmationDialog = true
                                },
                                leadingIcon = { Icon(Icons.Default.Block, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Delete Conversation", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMenu = false
                                showDeleteConversationDialog = true
                            },
                            leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (!uiState.isBlocked) {
                ChatBottomInputBar(
                    textFieldValue = textFieldValue,
                    onValueChange = { newVal ->
                        textFieldValue = newVal
                        viewModel.onMessageInputChange(newVal.text)
                    },
                    onBold = {
                        val updated = TextFormatter.applyBold(textFieldValue)
                        textFieldValue = updated
                        viewModel.onMessageInputChange(updated.text)
                    },
                    onItalic = {
                        val updated = TextFormatter.applyItalic(textFieldValue)
                        textFieldValue = updated
                        viewModel.onMessageInputChange(updated.text)
                    },
                    onUnderline = {
                        val updated = TextFormatter.applyUnderline(textFieldValue)
                        textFieldValue = updated
                        viewModel.onMessageInputChange(updated.text)
                    },
                    onClearFormatting = {
                        val updated = TextFormatter.applyClear(textFieldValue)
                        textFieldValue = updated
                        viewModel.onMessageInputChange(updated.text)
                    },
                    onSend = viewModel::sendInstantMessage,
                    onScheduleClick = viewModel::openScheduleDialog,
                    isSending = uiState.isSending,
                    availableSims = uiState.availableSims,
                    selectedSimId = uiState.selectedSimSubscriptionId,
                    onSelectSim = viewModel::selectSim
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Blocked sender warning banner
                if (uiState.isBlocked) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(0.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    Icons.Default.Block,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Sender is blocked from SMS in SamPing.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            TextButton(onClick = { viewModel.unblockSender() }) {
                                Text("Unblock", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                }

                if (uiState.messages.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Drafts,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (uiState.isBlocked) "No messages. Sender is blocked." else "Unified Conversation",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (uiState.isBlocked) "Unblock this sender to resume SMS." else "All messages, drafts, and schedules for this contact are unified here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.messages, key = { it.id }) { msg ->
                            ChatMessageBubble(
                                message = msg,
                                onScheduledClick = { msg.scheduleId?.let(onNavigateToScheduleDetails) },
                                onDeleteScheduled = { msg.scheduleId?.let(viewModel::deleteScheduledMessage) },
                                onRetryFailed = { viewModel.retryFailedMessage(msg) },
                                onEditDraft = {
                                    viewModel.onMessageInputChange(msg.body)
                                },
                                onLongClick = {
                                    selectedMessageForOptions = msg
                                },
                                onPhoneDetectedClick = { detectedPhone ->
                                    pendingCallNumber = detectedPhone
                                    pendingCallLabel = detectedPhone
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // MANDATORY Tap-To-Call Confirmation Dialog
    if (pendingCallNumber != null) {
        val targetNumber = pendingCallNumber ?: ""
        val targetLabel = pendingCallLabel ?: targetNumber
        AlertDialog(
            onDismissRequest = {
                pendingCallNumber = null
                pendingCallLabel = null
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = { Text("Call Confirmation", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "Call $targetLabel ($targetNumber)?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val num = pendingCallNumber
                        pendingCallNumber = null
                        pendingCallLabel = null
                        if (!num.isNullOrBlank()) {
                            try {
                                val intent = Intent(Intent.ACTION_DIAL).apply {
                                    data = Uri.parse("tel:${Uri.encode(num)}")
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                ) {
                    Text("Call")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingCallNumber = null
                    pendingCallLabel = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Message Long-Press Action Sheet / Dialog
    if (selectedMessageForOptions != null) {
        val msg = selectedMessageForOptions!!
        AlertDialog(
            onDismissRequest = { selectedMessageForOptions = null },
            title = { Text("Message Options", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = msg.body,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Copy option
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("SMS text", msg.body)
                                clipboard.setPrimaryClip(clip)
                                selectedMessageForOptions = null
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Copy Message Text", style = MaterialTheme.typography.bodyMedium)
                    }

                    // Delete option (moves to Recycle Bin)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val target = selectedMessageForOptions
                                selectedMessageForOptions = null
                                showDeleteMessageDialog = target
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Delete Message (Recycle Bin)", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedMessageForOptions = null }) {
                    Text("Close")
                }
            }
        )
    }

    // Delete Individual Message Confirmation
    if (showDeleteMessageDialog != null) {
        val msgToDelete = showDeleteMessageDialog!!
        AlertDialog(
            onDismissRequest = { showDeleteMessageDialog = null },
            title = { Text("Delete Message?", fontWeight = FontWeight.Bold) },
            text = {
                Text("This message will be moved to the Recycle Bin. It will be retained for 30 days before permanent deletion.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteMessage(msgToDelete)
                        showDeleteMessageDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteMessageDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Entire Conversation Confirmation
    if (showDeleteConversationDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConversationDialog = false },
            title = { Text("Delete Entire Conversation?", fontWeight = FontWeight.Bold) },
            text = {
                Text("All messages in this conversation will be moved to the Recycle Bin. You can restore them within 30 days.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConversationDialog = false
                        viewModel.deleteEntireConversation {
                            onNavigateBack()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConversationDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Block Sender Confirmation Dialog
    if (showBlockConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showBlockConfirmationDialog = false },
            title = { Text("Block ${uiState.contactName.ifBlank { uiState.address }}?", fontWeight = FontWeight.Bold) },
            text = {
                Text("You will no longer receive or display SMS from this sender in SamPing. Note: This applies strictly to SMS in SamPing and does not block phone calls.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showBlockConfirmationDialog = false
                        viewModel.blockSender {
                            onNavigateBack()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Block Sender")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlockConfirmationDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (uiState.isScheduleDialogVisible) {
        QuickScheduleDialog(
            initialEpochMs = uiState.scheduleTimeEpochMs,
            messagePreview = textFieldValue.text,
            recipient = uiState.contactName.ifBlank { uiState.address },
            onConfirm = viewModel::confirmScheduleMessage,
            onDismiss = viewModel::closeScheduleDialog,
            onTimeChanged = viewModel::updateScheduleTime
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun ChatMessageBubble(
    message: SmsChatMessage,
    onScheduledClick: () -> Unit,
    onDeleteScheduled: () -> Unit,
    onRetryFailed: () -> Unit,
    onEditDraft: () -> Unit,
    onLongClick: () -> Unit,
    onPhoneDetectedClick: (String) -> Unit
) {
    val isIncoming = message.isIncoming
    val isScheduled = message.isScheduled
    val isDraft = message.isDraft
    val isFailed = message.isFailed

    val detectedPhoneNumbers: List<String> = remember(message.body) {
        PhoneNumberDetector.extractPhoneNumbers(message.body)
    }

    val detectedOtpCode: String? = remember(message.body) {
        com.example.domain.util.SenderIdentityHelper.extractOtpCode(message.body)
    }

    val context = LocalContext.current

    val alignment = if (isIncoming) Alignment.Start else Alignment.End
    val bubbleColor = when {
        isDraft -> Color(0xFFFEF3C7) // Warm Amber Draft
        isFailed -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f)
        isScheduled -> StatusScheduled.copy(alpha = 0.15f)
        isIncoming -> MaterialTheme.colorScheme.surface
        else -> MaterialTheme.colorScheme.primary
    }
    val textColor = when {
        isDraft -> Color(0xFF92400E)
        isFailed -> MaterialTheme.colorScheme.onErrorContainer
        isScheduled -> MaterialTheme.colorScheme.onSurface
        isIncoming -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onPrimary
    }
    val shape = when {
        isIncoming -> RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp)
        isScheduled || isDraft -> RoundedCornerShape(16.dp)
        else -> RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomEnd = 16.dp, bottomStart = 16.dp)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Card(
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = bubbleColor),
            elevation = CardDefaults.cardElevation(defaultElevation = if (isIncoming || isDraft) 1.dp else 0.dp),
            modifier = Modifier
                .widthIn(max = 310.dp)
                .combinedClickable(
                    onClick = { /* normal click */ },
                    onLongClick = onLongClick
                )
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                // Draft Header Badge
                if (isDraft) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Drafts,
                                contentDescription = null,
                                tint = Color(0xFFD97706),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Draft (Not Sent)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFD97706)
                            )
                        }
                        TextButton(
                            onClick = onEditDraft,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                            modifier = Modifier.height(24.dp)
                        ) {
                            Text("Load to input", fontSize = 11.sp, color = Color(0xFFD97706))
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Scheduled Header Badge
                if (isScheduled) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ScheduleSend,
                                contentDescription = null,
                                tint = StatusScheduled,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Scheduled Message",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = StatusScheduled
                            )
                        }
                        IconButton(
                            onClick = onDeleteScheduled,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancel Scheduled",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Failed Header Badge
                if (isFailed) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Failed to Send",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        IconButton(
                            onClick = onRetryFailed,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Retry",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    if (!message.errorReason.isNullOrBlank()) {
                        Text(
                            text = "Reason: ${message.errorReason}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                            fontSize = 10.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Message Text Content
                Text(
                    text = message.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor,
                    lineHeight = 20.sp
                )

                // Detected Phone Numbers (Tap-to-Call with Confirmation)
                if (detectedPhoneNumbers.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        detectedPhoneNumbers.forEach { phone ->
                            AssistChip(
                                onClick = { onPhoneDetectedClick(phone) },
                                label = { Text("Call $phone", fontSize = 11.sp) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Call,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = if (isIncoming) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary
                                    )
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = if (isIncoming) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f),
                                    labelColor = if (isIncoming) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }
                }

                // Detected OTP / Verification PIN Code (1-tap Copy to Clipboard)
                if (!detectedOtpCode.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    AssistChip(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("OTP Code", detectedOtpCode)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Code copied: $detectedOtpCode", Toast.LENGTH_SHORT).show()
                        },
                        label = {
                            Text(
                                text = "Copy Code: $detectedOtpCode",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = if (isIncoming) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (isIncoming) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f),
                            labelColor = if (isIncoming) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Footer Row with timestamp & delivery status checkmarks
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
                    val timeStr = if (isScheduled && message.scheduledTriggerEpochMs != null) {
                        val fullFmt = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
                        "Triggers: " + fullFmt.format(Date(message.scheduledTriggerEpochMs))
                    } else if (isDraft) {
                        "Saved draft"
                    } else {
                        timeFormat.format(Date(message.dateEpochMs))
                    }

                    Text(
                        text = timeStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isIncoming || isScheduled || isDraft || isFailed)
                            MaterialTheme.colorScheme.onSurfaceVariant
                        else
                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                        fontSize = 10.sp
                    )

                    if (!isIncoming && !isScheduled && !isDraft && !isFailed) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (message.isDelivered || message.status == 1) "✓✓" else "✓",
                            color = if (message.isDelivered) Color(0xFF4ADE80) else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChatBottomInputBar(
    textFieldValue: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onBold: () -> Unit,
    onItalic: () -> Unit,
    onUnderline: () -> Unit,
    onClearFormatting: () -> Unit,
    onSend: () -> Unit,
    onScheduleClick: () -> Unit,
    isSending: Boolean,
    availableSims: List<com.example.domain.model.SimInfo>,
    selectedSimId: Int?,
    onSelectSim: (Int?) -> Unit
) {
    Surface(
        tonalElevation = 3.dp,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            // Multi-SIM switcher pill if dual-SIM
            if (availableSims.size > 1) {
                Row(
                    modifier = Modifier.padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    availableSims.forEach { sim ->
                        FilterChip(
                            selected = selectedSimId == sim.subscriptionId,
                            onClick = { onSelectSim(sim.subscriptionId) },
                            label = { Text(sim.displayName, fontSize = 11.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.SimCard, contentDescription = null, modifier = Modifier.size(12.dp))
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }
            }

            // Formatting Controls Toolbar (Bold, Italic, Underline, Clear)
            TextFormattingToolbar(
                modifier = Modifier.fillMaxWidth(),
                onBoldClick = onBold,
                onItalicClick = onItalic,
                onUnderlineClick = onUnderline,
                onClearClick = onClearFormatting
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Schedule Button (Clock icon)
                IconButton(
                    onClick = onScheduleClick,
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = "Schedule this SMS",
                        tint = StatusScheduled
                    )
                }

                // Message Text Field
                OutlinedTextField(
                    value = textFieldValue,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                    placeholder = { Text("Text message (SMS)", fontSize = 14.sp) },
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 4,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    )
                )

                // Send Button
                IconButton(
                    onClick = onSend,
                    enabled = textFieldValue.text.isNotBlank() && !isSending,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            if (textFieldValue.text.isNotBlank()) BrandIndigo else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .testTag("send_sms_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send SMS",
                        tint = if (textFieldValue.text.isNotBlank()) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }

            // SMS Part Counter & Auto-save status
            if (textFieldValue.text.isNotBlank()) {
                val charCount = textFieldValue.text.length
                val partCount = (charCount / 160) + 1
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.BookmarkBorder,
                            contentDescription = null,
                            tint = Color(0xFFD97706),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Draft auto-saved",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFD97706),
                            fontSize = 11.sp
                        )
                    }
                    Text(
                        text = "$charCount chars • $partCount SMS part${if (partCount > 1) "s" else ""}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickScheduleDialog(
    initialEpochMs: Long,
    messagePreview: String,
    recipient: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onTimeChanged: (Long) -> Unit
) {
    var selectedCalendar by remember {
        mutableStateOf(Calendar.getInstance().apply { timeInMillis = initialEpochMs })
    }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = selectedCalendar.timeInMillis
    )
    val timePickerState = rememberTimePickerState(
        initialHour = selectedCalendar.get(Calendar.HOUR_OF_DAY),
        initialMinute = selectedCalendar.get(Calendar.MINUTE),
        is24Hour = false
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ScheduleSend, contentDescription = null, tint = StatusScheduled)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Schedule SMS", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "To: $recipient",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )

                if (messagePreview.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "\"$messagePreview\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Quick Presets:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val cal = Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 1) }
                            selectedCalendar = cal
                            onTimeChanged(cal.timeInMillis)
                        },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("+1 Hour", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            val cal = Calendar.getInstance().apply {
                                add(Calendar.DAY_OF_YEAR, 1)
                                set(Calendar.HOUR_OF_DAY, 9)
                                set(Calendar.MINUTE, 0)
                            }
                            selectedCalendar = cal
                            onTimeChanged(cal.timeInMillis)
                        },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("Tomorrow 9 AM", fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Selected Execution Time:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        val fmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                        Text(fmt.format(selectedCalendar.time), fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = { showTimePicker = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        val fmt = SimpleDateFormat("h:mm a", Locale.getDefault())
                        Text(fmt.format(selectedCalendar.time), fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = StatusScheduled)
            ) {
                Text("Schedule Now")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { dateMs ->
                        val cal = Calendar.getInstance().apply {
                            timeInMillis = dateMs
                            set(Calendar.HOUR_OF_DAY, selectedCalendar.get(Calendar.HOUR_OF_DAY))
                            set(Calendar.MINUTE, selectedCalendar.get(Calendar.MINUTE))
                        }
                        selectedCalendar = cal
                        onTimeChanged(cal.timeInMillis)
                    }
                    showDatePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Select Time") },
            text = {
                TimePicker(state = timePickerState)
            },
            confirmButton = {
                TextButton(onClick = {
                    val cal = Calendar.getInstance().apply {
                        timeInMillis = selectedCalendar.timeInMillis
                        set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                        set(Calendar.MINUTE, timePickerState.minute)
                    }
                    selectedCalendar = cal
                    onTimeChanged(cal.timeInMillis)
                    showTimePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
