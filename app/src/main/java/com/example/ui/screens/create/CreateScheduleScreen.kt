package com.example.ui.screens.create

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.data.local.entity.TemplateEntity
import com.example.domain.model.EndConditionType
import com.example.domain.model.LeapYearHandling
import com.example.domain.model.MessageChannel
import com.example.domain.model.RecurrenceType
import com.example.domain.model.RetryPolicy
import com.example.domain.model.ShortMonthHandling
import com.example.domain.util.TextFormatter
import com.example.domain.util.TextFormattingToolbar
import com.example.ui.components.ChannelBadge
import com.example.ui.components.PermissionRationaleDialog
import com.example.ui.components.RecurrencePreviewDialog
import com.example.ui.components.SmsPermissionBanner
import com.example.ui.components.openAppSettings
import com.example.ui.theme.BrandIndigo
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.WhatsAppGreen
import com.example.ui.theme.WhatsAppGreenContainer
import com.example.ui.theme.WhatsAppGreenDark
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalPermissionsApi::class)
@Composable
fun CreateScheduleScreen(
    scheduleId: Long? = null,
    templateId: Long? = null,
    initialPhone: String? = null,
    initialName: String? = null,
    onNavigateBack: () -> Unit,
    viewModel: CreateScheduleViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val templates by viewModel.templates.collectAsState()

    var showTemplateSheet by remember { mutableStateOf(false) }
    var showRecurrencePreview by remember { mutableStateOf(false) }
    var bodyTextFieldValue by remember { mutableStateOf(TextFieldValue(uiState.messageBody)) }

    LaunchedEffect(uiState.messageBody) {
        if (uiState.messageBody != bodyTextFieldValue.text) {
            bodyTextFieldValue = TextFieldValue(
                text = uiState.messageBody,
                selection = TextRange(uiState.messageBody.length)
            )
        }
    }

    val smsPermissionState = rememberPermissionState(Manifest.permission.SEND_SMS)
    val contactsPermissionState = rememberPermissionState(Manifest.permission.READ_CONTACTS)
    val phoneStatePermissionState = rememberPermissionState(Manifest.permission.READ_PHONE_STATE)

    LaunchedEffect(scheduleId) {
        if (scheduleId != null && scheduleId > 0) {
            viewModel.loadScheduleForEdit(scheduleId)
        }
    }

    LaunchedEffect(initialPhone, initialName) {
        viewModel.initParams(initialPhone, initialName)
    }

    LaunchedEffect(templateId, templates) {
        if (templateId != null && templateId > 0 && templates.isNotEmpty()) {
            templates.firstOrNull { it.id == templateId }?.let {
                viewModel.applyTemplate(it)
            }
        }
    }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            onNavigateBack()
        }
    }

    // Contact Picker Launcher
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
                        viewModel.updateRecipient(displayName, phoneNumber)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Date Picker Dialog
    val datePickerDialog = remember(uiState.scheduledDate) {
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                viewModel.updateDate(LocalDate.of(year, month + 1, dayOfMonth))
            },
            uiState.scheduledDate.year,
            uiState.scheduledDate.monthValue - 1,
            uiState.scheduledDate.dayOfMonth
        ).apply {
            datePicker.minDate = System.currentTimeMillis() - 1000
        }
    }

    // Time Picker Dialog
    val timePickerDialog = remember(uiState.scheduledTime) {
        TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                viewModel.updateTime(LocalTime.of(hourOfDay, minute))
            },
            uiState.scheduledTime.hour,
            uiState.scheduledTime.minute,
            false // 12-hour AM/PM
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (uiState.isEditMode) "Edit Schedule" else "Schedule Message",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Channel Info
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Sms,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Channel: SMS (Direct Device Carrier)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Scheduled texts will be dispatched automatically via system SMS.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // SIM Selector (Dual-SIM devices for SMS)
            if (uiState.availableSims.size > 1) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.SimCard, contentDescription = null, tint = BrandIndigo, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Preferred SIM Card", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            uiState.availableSims.forEach { sim ->
                                FilterChip(
                                    selected = uiState.selectedSimId == sim.subscriptionId,
                                    onClick = { viewModel.updateSelectedSim(sim.subscriptionId) },
                                    label = { Text(sim.displayName, fontSize = 12.sp) },
                                    leadingIcon = {
                                        if (uiState.selectedSimId == sim.subscriptionId) {
                                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Recipient Details
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Recipient Information",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Phone Number with Contact Picker Button
                    OutlinedTextField(
                        value = uiState.recipientPhone,
                        onValueChange = { viewModel.updateRecipient(uiState.recipientName, it) },
                        label = { Text("Phone Number *") },
                        placeholder = { Text("+1 (555) 000-0000") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    if (contactsPermissionState.status.isGranted) {
                                        contactPickerLauncher.launch(null)
                                    } else {
                                        contactsPermissionState.launchPermissionRequest()
                                    }
                                },
                                modifier = Modifier.testTag("pick_contact_btn")
                            ) {
                                Icon(
                                    Icons.Default.ContactPhone,
                                    contentDescription = "Pick Contact",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("recipient_phone_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Optional Recipient Name
                    OutlinedTextField(
                        value = uiState.recipientName,
                        onValueChange = { viewModel.updateRecipient(it, uiState.recipientPhone) },
                        label = { Text("Contact Name (Optional)") },
                        placeholder = { Text("e.g. John Doe") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("recipient_name_input")
                    )
                }
            }

            // Message Composer Section
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Message Content",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextFormattingToolbar(
                                onBoldClick = {
                                    val updated = TextFormatter.applyBold(bodyTextFieldValue)
                                    bodyTextFieldValue = updated
                                    viewModel.updateMessageBody(updated.text)
                                },
                                onItalicClick = {
                                    val updated = TextFormatter.applyItalic(bodyTextFieldValue)
                                    bodyTextFieldValue = updated
                                    viewModel.updateMessageBody(updated.text)
                                },
                                onUnderlineClick = {
                                    val updated = TextFormatter.applyUnderline(bodyTextFieldValue)
                                    bodyTextFieldValue = updated
                                    viewModel.updateMessageBody(updated.text)
                                },
                                onClearClick = {
                                    val updated = TextFormatter.applyClear(bodyTextFieldValue)
                                    bodyTextFieldValue = updated
                                    viewModel.updateMessageBody(updated.text)
                                }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            TextButton(
                                onClick = { showTemplateSheet = true },
                                modifier = Modifier.testTag("open_template_sheet_btn")
                            ) {
                                Icon(Icons.Default.TextSnippet, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Template", fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Text Field
                    OutlinedTextField(
                        value = bodyTextFieldValue,
                        onValueChange = { newVal ->
                            bodyTextFieldValue = newVal
                            viewModel.updateMessageBody(newVal.text)
                        },
                        label = { Text("Message Body *") },
                        placeholder = { Text("Hi {first_name}, happy birthday! Hope you have a wonderful day!") },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("message_body_input")
                    )

                    // SMS Segment / Character Counter
                    Spacer(modifier = Modifier.height(6.dp))
                    val charCount = uiState.messageBody.length
                    val smsSegments = if (charCount == 0) 1 else (charCount - 1) / 160 + 1
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = "$charCount chars • $smsSegments SMS part${if (smsSegments > 1) "s" else ""}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Template Variable Injection Chips
                    Text("Insert Dynamic Variable:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        VariableChip(label = "{first_name}", onClick = { viewModel.insertVariable("{first_name}") })
                        VariableChip(label = "{name}", onClick = { viewModel.insertVariable("{name}") })
                        VariableChip(label = "{date}", onClick = { viewModel.insertVariable("{date}") })
                        VariableChip(label = "{time}", onClick = { viewModel.insertVariable("{time}") })
                        VariableChip(label = "{day_of_week}", onClick = { viewModel.insertVariable("{day_of_week}") })
                        VariableChip(label = "{month}", onClick = { viewModel.insertVariable("{month}") })
                    }
                }
            }

            // Real-time Live Preview Card
            if (uiState.messageBody.isNotBlank()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFEEF2FF)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Visibility, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Live Output Preview",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = uiState.parsedPreview,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Timing & Schedule Section
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Schedule Time & Recurrence",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Date & Time pickers Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { datePickerDialog.show() },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("pick_date_btn"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = uiState.scheduledDate.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())),
                                fontSize = 13.sp
                            )
                        }

                        OutlinedButton(
                            onClick = { timePickerDialog.show() },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("pick_time_btn"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = uiState.scheduledTime.format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())),
                                fontSize = 13.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Recurrence Type Dropdown / Chips
                    Text("Repeat Pattern", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        RecurrenceType.values().forEach { type ->
                            FilterChip(
                                selected = uiState.recurrenceType == type,
                                onClick = { viewModel.updateRecurrenceType(type) },
                                label = { Text(type.displayName, fontSize = 12.sp) }
                            )
                        }
                    }

                    // Weekdays selection if WEEKDAYS
                    if (uiState.recurrenceType == RecurrenceType.WEEKDAYS) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Select Days of the Week:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val days = listOf("M", "T", "W", "T", "F", "S", "S")
                            days.forEachIndexed { index, label ->
                                val dayNum = index + 1
                                val isSelected = uiState.selectedDaysOfWeek.contains(dayNum)
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable { viewModel.toggleDayOfWeek(dayNum) }
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }

                    // Interval selector for custom / multi-week / multi-month / multi-year
                    if (uiState.recurrenceType in listOf(RecurrenceType.EVERY_X_WEEKS, RecurrenceType.EVERY_X_MONTHS, RecurrenceType.EVERY_X_YEARS, RecurrenceType.CUSTOM_INTERVAL)) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Repeat Interval: Every ${uiState.recurrenceInterval} ${
                            when (uiState.recurrenceType) {
                                RecurrenceType.EVERY_X_WEEKS -> "weeks"
                                RecurrenceType.EVERY_X_MONTHS -> "months"
                                RecurrenceType.EVERY_X_YEARS -> "years"
                                else -> "days"
                            }
                        }", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Slider(
                            value = uiState.recurrenceInterval.toFloat(),
                            onValueChange = { viewModel.updateRecurrenceInterval(it.toInt()) },
                            valueRange = 1f..12f,
                            steps = 10
                        )
                    }

                    // Short Month & Leap Year edge case handlers (for monthly and yearly)
                    if (uiState.recurrenceType in listOf(RecurrenceType.MONTHLY_DATE, RecurrenceType.EVERY_X_MONTHS, RecurrenceType.YEARLY, RecurrenceType.EVERY_X_YEARS)) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("Short Month Strategy (e.g. Feb 30/31):", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    FilterChip(
                                        selected = uiState.shortMonthHandling == ShortMonthHandling.LAST_VALID_DAY,
                                        onClick = { viewModel.updateShortMonthHandling(ShortMonthHandling.LAST_VALID_DAY) },
                                        label = { Text("Send on Last Valid Day (Feb 28)", fontSize = 10.sp) }
                                    )
                                    FilterChip(
                                        selected = uiState.shortMonthHandling == ShortMonthHandling.SKIP_MONTH,
                                        onClick = { viewModel.updateShortMonthHandling(ShortMonthHandling.SKIP_MONTH) },
                                        label = { Text("Skip Month", fontSize = 10.sp) }
                                    )
                                }
                            }
                        }
                    }

                    // End Condition Section (if repeating)
                    if (uiState.recurrenceType != RecurrenceType.ONCE) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text("End Recurrence", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            EndConditionType.values().forEach { endType ->
                                FilterChip(
                                    selected = uiState.endType == endType,
                                    onClick = { viewModel.updateEndCondition(endType) },
                                    label = { Text(endType.displayName, fontSize = 11.sp) }
                                )
                            }
                        }

                        if (uiState.endType == EndConditionType.AFTER_COUNT) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Stop after ${uiState.maxOccurrences} occurrences", fontSize = 12.sp)
                            Slider(
                                value = uiState.maxOccurrences.toFloat(),
                                onValueChange = { viewModel.updateEndCondition(EndConditionType.AFTER_COUNT, maxCount = it.toInt()) },
                                valueRange = 2f..50f,
                                steps = 47
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { showRecurrencePreview = true },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Repeat, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Preview Next Calculated Dates", fontSize = 12.sp)
                        }
                    }
                }
            }

            // Advanced Options: Pre-Send Reminders & Retry Policy
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Reliability & Notifications",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Pre-Send Notification Alert:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0 to "None", 5 to "5 min", 15 to "15 min", 30 to "30 min", 60 to "1 hour").forEach { (mins, label) ->
                            FilterChip(
                                selected = uiState.preSendReminderMinutes == mins,
                                onClick = { viewModel.updatePreSendReminderMinutes(mins) },
                                label = { Text(label, fontSize = 11.sp) }
                            )
                        }
                    }

                    if (uiState.channel == MessageChannel.SMS) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Failure Retry Policy:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            RetryPolicy.values().forEach { policy ->
                                FilterChip(
                                    selected = uiState.retryPolicy == policy,
                                    onClick = { viewModel.updateRetryPolicy(policy) },
                                    label = { Text(policy.displayName, fontSize = 11.sp) }
                                )
                            }
                        }
                    }
                }
            }

            // Error Banner
            if (uiState.errorMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = uiState.errorMessage!!,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // Action Button: Schedule Message Directly
            Button(
                onClick = {
                    viewModel.saveScheduleDirectly()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("submit_schedule_btn"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Schedule, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (uiState.isEditMode) "Save Changes" else "Schedule Message",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    // Template Picker Modal Bottom Sheet
    if (showTemplateSheet) {
        ModalBottomSheet(
            onDismissRequest = { showTemplateSheet = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("Select a Template", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))
                LazyColumn(
                    modifier = Modifier.height(350.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(templates, key = { it.id }) { template ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.applyTemplate(template)
                                    showTemplateSheet = false
                                },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(template.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(template.content, fontSize = 12.sp, maxLines = 2)
                            }
                        }
                    }
                }
            }
        }
    }

    // Recurrence Preview Dialog
    if (showRecurrencePreview) {
        RecurrencePreviewDialog(
            startEpochMs = uiState.calculateTriggerEpochMs(),
            config = uiState.toRecurrenceConfig(),
            onDismiss = { showRecurrencePreview = false }
        )
    }

    // Review & Confirmation Dialog
    if (uiState.showReviewDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissReview() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Confirm Schedule")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Please confirm the scheduled dispatch details:")
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text("Channel: ${uiState.channel.displayName}", fontWeight = FontWeight.Bold)
                    Text("Recipient: ${uiState.recipientName.ifBlank { "Unknown" }} (${uiState.recipientPhone})")
                    Text("Date & Time: ${uiState.scheduledDate} at ${uiState.scheduledTime.format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))}")
                    Text("Repeat: ${uiState.recurrenceType.displayName}")
                    if (uiState.preSendReminderMinutes > 0) {
                        Text("Pre-alert: ${uiState.preSendReminderMinutes} min prior")
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Resolved Message Content:", fontWeight = FontWeight.SemiBold)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = uiState.parsedPreview,
                            modifier = Modifier.padding(10.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (uiState.channel == MessageChannel.SMS && !smsPermissionState.status.isGranted) {
                            smsPermissionState.launchPermissionRequest()
                        }
                        viewModel.saveSchedule()
                    },
                    modifier = Modifier.testTag("confirm_save_schedule_btn")
                ) {
                    Text("Confirm & Schedule")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissReview() }) {
                    Text("Edit Details")
                }
            }
        )
    }
}

@Composable
fun ChannelSelectButton(
    channel: MessageChannel,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isSelected) BrandIndigo else MaterialTheme.colorScheme.outlineVariant
    val containerColor = if (isSelected) Color(0xFFEEF2FF) else MaterialTheme.colorScheme.surface

    Card(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag("channel_select_${channel.name}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = BrandIndigo,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = BrandIndigo
            )
        }
    }
}

@Composable
fun VariableChip(
    label: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
    }
}
