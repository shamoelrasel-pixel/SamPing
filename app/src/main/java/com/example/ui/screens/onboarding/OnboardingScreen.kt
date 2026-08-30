package com.example.ui.screens.onboarding

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.ScheduleSend
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.service.DefaultSmsHelper
import com.example.ui.components.PermissionRationaleDialog
import com.example.ui.components.openAppSettings
import com.example.ui.theme.BrandIndigo
import com.example.ui.theme.StatusSuccess
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun OnboardingScreen(
    onFinishOnboarding: () -> Unit
) {
    val context = LocalContext.current

    val initialPermissionsList = remember {
        buildList {
            add(Manifest.permission.SEND_SMS)
            add(Manifest.permission.READ_PHONE_STATE)
            add(Manifest.permission.READ_CONTACTS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    var isDefaultSms by remember { mutableStateOf(DefaultSmsHelper.isDefaultSmsApp(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isDefaultSms = DefaultSmsHelper.isDefaultSmsApp(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val multiplePermissionsState = rememberMultiplePermissionsState(permissions = initialPermissionsList)
    val smsPermissionState = rememberPermissionState(Manifest.permission.SEND_SMS)
    val phoneStatePermissionState = rememberPermissionState(Manifest.permission.READ_PHONE_STATE)
    val contactsPermissionState = rememberPermissionState(Manifest.permission.READ_CONTACTS)

    var showSmsRationaleDialog by remember { mutableStateOf(false) }

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // App Icon Graphic
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(BrandIndigo)
            ) {
                Icon(
                    imageVector = Icons.Default.ScheduleSend,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(44.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Welcome to ShamPing",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Complete Android messaging app with live SMS conversations, dual-SIM support, and scheduled automation.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Permissions Checklist
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PermissionItem(
                    icon = Icons.Default.Sms,
                    title = "Send SMS",
                    description = "Required to dispatch scheduled text messages directly from your device.",
                    isGranted = smsPermissionState.status.isGranted,
                    onRequest = {
                        if (smsPermissionState.status.shouldShowRationale) {
                            showSmsRationaleDialog = true
                        } else {
                            smsPermissionState.launchPermissionRequest()
                        }
                    }
                )

                PermissionItem(
                    icon = Icons.Default.SimCard,
                    title = "Dual-SIM Detection",
                    description = "Enables selecting your preferred SIM card on dual-SIM devices.",
                    isGranted = phoneStatePermissionState.status.isGranted,
                    onRequest = {
                        phoneStatePermissionState.launchPermissionRequest()
                    }
                )

                PermissionItem(
                    icon = Icons.Default.Contacts,
                    title = "Contacts Integration",
                    description = "Quickly pick recipients and fill in names and numbers with 1 tap.",
                    isGranted = contactsPermissionState.status.isGranted,
                    onRequest = {
                        contactsPermissionState.launchPermissionRequest()
                    }
                )

                PermissionItem(
                    icon = Icons.Default.PhoneAndroid,
                    title = "Default SMS Override (Recommended)",
                    description = "Sets ShamPing as Default SMS app to receive text messages and send scheduled texts with zero OS confirmation dialogs.",
                    isGranted = isDefaultSms,
                    onRequest = {
                        context.findActivity()?.let { act ->
                            DefaultSmsHelper.requestDefaultSmsApp(act)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Primary Action Button
            Button(
                onClick = {
                    if (!multiplePermissionsState.allPermissionsGranted) {
                        multiplePermissionsState.launchMultiplePermissionRequest()
                    }
                    onFinishOnboarding()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("onboarding_continue_btn"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = if (smsPermissionState.status.isGranted) "Get Started" else "Grant Permissions & Continue",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showSmsRationaleDialog) {
        PermissionRationaleDialog(
            title = "SMS Permission Required",
            description = "ShamPing needs permission to send SMS text messages so it can handle your live chats and automatically dispatch your scheduled reminders and greetings at your designated times.",
            icon = Icons.Default.Sms,
            isPermanentlyDenied = !smsPermissionState.status.shouldShowRationale && !smsPermissionState.status.isGranted,
            onDismiss = { showSmsRationaleDialog = false },
            onConfirm = {
                showSmsRationaleDialog = false
                if (smsPermissionState.status.shouldShowRationale) {
                    smsPermissionState.launchPermissionRequest()
                } else {
                    context.openAppSettings()
                }
            }
        )
    }
}

@Composable
fun PermissionItem(
    icon: ImageVector,
    title: String,
    description: String,
    isGranted: Boolean,
    onRequest: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (isGranted) StatusSuccess.copy(alpha = 0.15f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isGranted) StatusSuccess else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (isGranted) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Granted",
                    tint = StatusSuccess,
                    modifier = Modifier.size(22.dp)
                )
            } else {
                OutlinedButton(
                    onClick = onRequest,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Grant", fontSize = 12.sp)
                }
            }
        }
    }
}
