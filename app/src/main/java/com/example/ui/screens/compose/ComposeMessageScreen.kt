package com.example.ui.screens.compose

import android.Manifest
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.ScheduleSend
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.domain.util.TextFormatter
import com.example.domain.util.TextFormattingToolbar
import com.example.ui.screens.chat.QuickScheduleDialog
import com.example.ui.theme.BrandIndigo
import com.example.ui.theme.StatusScheduled
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalPermissionsApi::class)
@Composable
fun ComposeMessageScreen(
    initialAddress: String = "",
    initialName: String = "",
    initialBody: String = "",
    onNavigateBack: () -> Unit,
    onMessageSentOrScheduled: (String, String) -> Unit,
    onNavigateToAdvancedScheduler: (String, String, String) -> Unit,
    viewModel: ComposeViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var isScheduleDialogOpen by remember { mutableStateOf(false) }
    var scheduleEpochMs by remember { mutableStateOf(System.currentTimeMillis() + 3600000L) }
    var showContactListDialog by remember { mutableStateOf(false) }
    var contactSearchTerm by remember { mutableStateOf("") }

    var bodyTextFieldValue by remember { mutableStateOf(TextFieldValue(uiState.messageBody)) }

    val contactsPermissionState = rememberPermissionState(Manifest.permission.READ_CONTACTS)

    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val cursor: Cursor? = context.contentResolver.query(
                    uri,
                    arrayOf(
                        ContactsContract.Contacts._ID,
                        ContactsContract.Contacts.DISPLAY_NAME,
                        ContactsContract.Contacts.HAS_PHONE_NUMBER
                    ),
                    null, null, null
                )
                cursor?.use {
                    if (it.moveToFirst()) {
                        val contactId = it.getString(it.getColumnIndexOrThrow(ContactsContract.Contacts._ID))
                        val displayName = it.getString(it.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME)) ?: ""
                        val hasPhone = it.getInt(it.getColumnIndexOrThrow(ContactsContract.Contacts.HAS_PHONE_NUMBER))

                        var phoneNumber = ""
                        if (hasPhone > 0) {
                            val phoneCursor: Cursor? = context.contentResolver.query(
                                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                                "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                                arrayOf(contactId),
                                null
                            )
                            phoneCursor?.use { pCursor ->
                                if (pCursor.moveToFirst()) {
                                    phoneNumber = pCursor.getString(pCursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)) ?: ""
                                }
                            }
                        }
                        if (phoneNumber.isNotBlank()) {
                            viewModel.setRecipient(phoneNumber, displayName)
                        } else {
                            viewModel.onNameChanged(displayName)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    LaunchedEffect(initialAddress, initialName, initialBody) {
        viewModel.initParams(initialAddress, initialName, initialBody)
    }

    LaunchedEffect(uiState.messageBody) {
        if (uiState.messageBody != bodyTextFieldValue.text) {
            bodyTextFieldValue = TextFieldValue(
                text = uiState.messageBody,
                selection = TextRange(uiState.messageBody.length)
            )
        }
    }

    LaunchedEffect(contactsPermissionState.status.isGranted) {
        if (contactsPermissionState.status.isGranted) {
            viewModel.refreshContacts()
        }
    }

    LaunchedEffect(uiState.draftSavedMessage) {
        uiState.draftSavedMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    fun openContactPicker() {
        if (contactsPermissionState.status.isGranted) {
            if (uiState.deviceContacts.isNotEmpty()) {
                contactSearchTerm = ""
                showContactListDialog = true
            } else {
                contactPickerLauncher.launch(null)
            }
        } else {
            contactsPermissionState.launchPermissionRequest()
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
                title = { Text("New Message", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { handleBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Save as Draft button in toolbar
                    IconButton(
                        onClick = {
                            viewModel.saveDraftAndExit {
                                onNavigateBack()
                            }
                        },
                        enabled = uiState.recipientPhone.isNotBlank() && bodyTextFieldValue.text.isNotBlank()
                    ) {
                        Icon(
                            imageVector = Icons.Default.BookmarkBorder,
                            contentDescription = "Save as Draft",
                            tint = if (uiState.recipientPhone.isNotBlank() && bodyTextFieldValue.text.isNotBlank())
                                Color(0xFFF59E0B)
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    }

                    IconButton(
                        onClick = { openContactPicker() },
                        modifier = Modifier.testTag("compose_top_pick_contact")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContactPhone,
                            contentDescription = "Select from Contacts",
                            tint = MaterialTheme.colorScheme.primary
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
            Surface(
                tonalElevation = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Schedule Button
                    val recipientCount = if (uiState.recipients.isNotEmpty()) uiState.recipients.size else if (uiState.recipientPhone.isNotBlank()) 1 else 0
                    OutlinedButton(
                        onClick = { isScheduleDialogOpen = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ScheduleSend,
                            contentDescription = null,
                            tint = StatusScheduled,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (recipientCount > 1) "Schedule ($recipientCount)" else "Schedule",
                            color = StatusScheduled,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Instant Send Button
                    Button(
                        onClick = {
                            viewModel.sendInstantSms { phone, name ->
                                onMessageSentOrScheduled(phone, name)
                            }
                        },
                        enabled = !uiState.isSending && (uiState.recipients.isNotEmpty() || uiState.recipientPhone.isNotBlank()) && bodyTextFieldValue.text.isNotBlank(),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("compose_send_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandIndigo)
                    ) {
                        if (uiState.isSending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (recipientCount > 1) "Send ($recipientCount)" else "Send Now",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))

                // Recipient Chips for Group SMS / Multi-recipient
                if (uiState.recipients.isNotEmpty()) {
                    Text(
                        text = "Recipients (${uiState.recipients.size}):",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        uiState.recipients.forEach { rec ->
                            InputChip(
                                selected = true,
                                onClick = { /* no-op */ },
                                label = {
                                    Text(
                                        text = rec.name.ifBlank { rec.phoneNumber },
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                },
                                trailingIcon = {
                                    IconButton(
                                        onClick = { viewModel.removeRecipient(rec) },
                                        modifier = Modifier.size(16.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove recipient",
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                },
                                colors = InputChipDefaults.inputChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Recipient phone number field with inline Contact Picker action
                OutlinedTextField(
                    value = uiState.recipientPhone,
                    onValueChange = viewModel::onPhoneChanged,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("compose_phone_input"),
                    label = { Text(if (uiState.recipients.isNotEmpty()) "Add Another Recipient" else "Recipient Phone Number *") },
                    placeholder = { Text("+1234567890 or type name to search") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    leadingIcon = {
                        Icon(Icons.Default.Phone, contentDescription = null)
                    },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (uiState.recipientPhone.isNotBlank()) {
                                IconButton(
                                    onClick = {
                                        viewModel.addRecipient(uiState.recipientPhone, uiState.recipientName)
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Add Recipient",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                IconButton(onClick = { viewModel.onPhoneChanged("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                                }
                            }
                            IconButton(
                                onClick = { openContactPicker() },
                                modifier = Modifier.testTag("compose_pick_contact_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContactPhone,
                                    contentDescription = "Select from Contacts",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                // Quick "Select from Contacts" helper chip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AssistChip(
                        onClick = { openContactPicker() },
                        label = { Text("Select from Contacts (Group SMS)", fontSize = 12.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.PersonSearch,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )

                    if (uiState.recipientName.isNotBlank()) {
                        Text(
                            text = "Selected: ${uiState.recipientName}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Contact Autocomplete Suggestions
                if (uiState.isContactDropdownVisible && uiState.filteredContacts.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column {
                            uiState.filteredContacts.forEach { contact ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.selectContact(contact) }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        modifier = Modifier.size(32.dp),
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = contact.name.take(1),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = contact.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = contact.phoneNumber,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Contact Name field (Optional)
            item {
                OutlinedTextField(
                    value = uiState.recipientName,
                    onValueChange = viewModel::onNameChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Contact Name (Optional)") },
                    placeholder = { Text("e.g. Alex, Mom, Client") },
                    leadingIcon = {
                        Icon(Icons.Default.Person, contentDescription = null)
                    },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
            }

            // SIM selector if multi-SIM
            if (uiState.availableSims.size > 1) {
                item {
                    Text(
                        text = "Send via SIM Card:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        uiState.availableSims.forEach { sim ->
                            FilterChip(
                                selected = uiState.selectedSimSubscriptionId == sim.subscriptionId,
                                onClick = { viewModel.selectSim(sim.subscriptionId) },
                                label = { Text(sim.displayName) },
                                leadingIcon = {
                                    Icon(Icons.Default.SimCard, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            )
                        }
                    }
                }
            }

            // Formatting Controls Toolbar (Bold, Italic, Underline, Clear)
            item {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Message Body (SMS)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        TextFormattingToolbar(
                            onBoldClick = {
                                val updated = TextFormatter.applyBold(bodyTextFieldValue)
                                bodyTextFieldValue = updated
                                viewModel.onBodyChanged(updated.text)
                            },
                            onItalicClick = {
                                val updated = TextFormatter.applyItalic(bodyTextFieldValue)
                                bodyTextFieldValue = updated
                                viewModel.onBodyChanged(updated.text)
                            },
                            onUnderlineClick = {
                                val updated = TextFormatter.applyUnderline(bodyTextFieldValue)
                                bodyTextFieldValue = updated
                                viewModel.onBodyChanged(updated.text)
                            },
                            onClearClick = {
                                val updated = TextFormatter.applyClear(bodyTextFieldValue)
                                bodyTextFieldValue = updated
                                viewModel.onBodyChanged(updated.text)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    OutlinedTextField(
                        value = bodyTextFieldValue,
                        onValueChange = { newVal ->
                            bodyTextFieldValue = newVal
                            viewModel.onBodyChanged(newVal.text)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .testTag("compose_message_input"),
                        placeholder = { Text("Type your message here...") },
                        shape = RoundedCornerShape(12.dp)
                    )

                    val charCount = bodyTextFieldValue.text.length
                    val partCount = (charCount / 160) + 1
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, start = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "$charCount characters • $partCount SMS part${if (partCount > 1) "s" else ""}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Drafts are saved locally",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFD97706)
                        )
                    }
                }
            }

            // Template Quick Insertion Chips
            if (uiState.availableTemplates.isNotEmpty()) {
                item {
                    Text(
                        text = "Insert Template:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        uiState.availableTemplates.take(6).forEach { tmpl ->
                            SuggestionChip(
                                onClick = {
                                    viewModel.applyTemplate(tmpl)
                                    bodyTextFieldValue = TextFieldValue(tmpl.content)
                                },
                                label = { Text(tmpl.title, fontSize = 12.sp) }
                            )
                        }
                    }
                }
            }

            // Error message
            if (uiState.errorMessage != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = uiState.errorMessage ?: "",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            // Advanced Recurring & Calendar link
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onNavigateToAdvancedScheduler(
                                    uiState.recipientPhone,
                                    uiState.recipientName,
                                    bodyTextFieldValue.text
                                )
                            }
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Need Recurring or Dynamic Schedule?",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Daily, weekly, monthly recurrence with template variables",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }

    // Contact List Selector Dialog (Supports single and multi-select for Group SMS)
    if (showContactListDialog) {
        val filteredList = if (contactSearchTerm.isBlank()) {
            uiState.deviceContacts
        } else {
            uiState.deviceContacts.filter {
                it.name.contains(contactSearchTerm, ignoreCase = true) ||
                it.phoneNumber.contains(contactSearchTerm)
            }
        }

        AlertDialog(
            onDismissRequest = { showContactListDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Select Contacts", fontWeight = FontWeight.Bold)
                        if (uiState.recipients.isNotEmpty()) {
                            Text(
                                "${uiState.recipients.size} selected",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    TextButton(onClick = {
                        showContactListDialog = false
                        contactPickerLauncher.launch(null)
                    }) {
                        Text("System Picker", fontSize = 12.sp)
                    }
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = contactSearchTerm,
                        onValueChange = { contactSearchTerm = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search name or number...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    if (filteredList.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No matching contacts found",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 320.dp)
                        ) {
                            items(filteredList) { contact ->
                                val isSelected = uiState.recipients.any { it.phoneNumber == contact.phoneNumber }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.toggleContactRecipient(contact)
                                        }
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { viewModel.toggleContactRecipient(contact) }
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Surface(
                                        modifier = Modifier.size(36.dp),
                                        shape = CircleShape,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = contact.name.take(1).ifBlank { "#" },
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = contact.name.ifBlank { "Unknown Contact" },
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                                        )
                                        Text(
                                            text = contact.phoneNumber,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showContactListDialog = false }) {
                    Text("Done (${uiState.recipients.size})")
                }
            },
            dismissButton = {
                TextButton(onClick = { showContactListDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (isScheduleDialogOpen) {
        QuickScheduleDialog(
            initialEpochMs = scheduleEpochMs,
            messagePreview = bodyTextFieldValue.text,
            recipient = uiState.recipientName.ifBlank { uiState.recipientPhone },
            onConfirm = {
                viewModel.scheduleSms(scheduleEpochMs) {
                    isScheduleDialogOpen = false
                    onMessageSentOrScheduled(uiState.recipientPhone, uiState.recipientName)
                }
            },
            onDismiss = { isScheduleDialogOpen = false },
            onTimeChanged = { scheduleEpochMs = it }
        )
    }
}
