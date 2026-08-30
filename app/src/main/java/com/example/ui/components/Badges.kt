package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.DeliveryStatus
import com.example.domain.model.MessageChannel
import com.example.domain.model.RecurrenceType
import com.example.domain.model.ScheduleStatus
import com.example.ui.theme.BrandIndigo
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusErrorContainer
import com.example.ui.theme.StatusInfo
import com.example.ui.theme.StatusInfoContainer
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusSuccessContainer
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.StatusWarningContainer
import com.example.ui.theme.WhatsAppGreen
import com.example.ui.theme.WhatsAppGreenContainer
import com.example.ui.theme.WhatsAppGreenDark

@Composable
fun ChannelBadge(
    channel: MessageChannel,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor, icon) = Triple(
        Color(0xFFEEF2FF),
        BrandIndigo,
        Icons.Default.Sms
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag("channel_badge_${channel.name}")
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = textColor,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "SMS",
            color = textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun ScheduleStatusBadge(
    status: ScheduleStatus,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor, icon) = when (status) {
        ScheduleStatus.SCHEDULED -> Triple(
            StatusInfoContainer,
            StatusInfo,
            Icons.Default.Schedule
        )
        ScheduleStatus.PROCESSING -> Triple(
            StatusWarningContainer,
            StatusWarning,
            Icons.Default.HourglassTop
        )
        ScheduleStatus.PAUSED -> Triple(
            Color(0xFFF1F5F9),
            Color(0xFF64748B),
            Icons.Default.PauseCircle
        )
        ScheduleStatus.COMPLETED -> Triple(
            StatusSuccessContainer,
            StatusSuccess,
            Icons.Default.CheckCircle
        )
        ScheduleStatus.FAILED -> Triple(
            StatusErrorContainer,
            StatusError,
            Icons.Default.Error
        )
        ScheduleStatus.MISSED -> Triple(
            StatusWarningContainer,
            StatusWarning,
            Icons.Default.Error
        )
        ScheduleStatus.CANCELLED -> Triple(
            Color(0xFFF1F5F9),
            Color(0xFF64748B),
            Icons.Default.Error
        )
        ScheduleStatus.CONFIRMATION_REQUIRED -> Triple(
            WhatsAppGreenContainer,
            WhatsAppGreenDark,
            Icons.Default.Chat
        )
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag("status_badge_${status.name}")
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = textColor,
            modifier = Modifier.size(12.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = status.displayName,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun DeliveryStatusBadge(
    status: DeliveryStatus,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor) = when (status) {
        DeliveryStatus.SCHEDULED -> Pair(StatusInfoContainer, StatusInfo)
        DeliveryStatus.PROCESSING -> Pair(StatusWarningContainer, StatusWarning)
        DeliveryStatus.SENT -> Pair(Color(0xFFEFF6FF), Color(0xFF2563EB))
        DeliveryStatus.DELIVERED -> Pair(StatusSuccessContainer, StatusSuccess)
        DeliveryStatus.FAILED -> Pair(StatusErrorContainer, StatusError)
        DeliveryStatus.MISSED -> Pair(StatusWarningContainer, StatusWarning)
        DeliveryStatus.CANCELLED -> Pair(Color(0xFFF1F5F9), Color(0xFF64748B))
        DeliveryStatus.CONFIRMATION_REQUIRED -> Pair(WhatsAppGreenContainer, WhatsAppGreenDark)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .padding(horizontal = 6.dp, vertical = 3.dp)
            .testTag("delivery_status_badge_${status.name}")
    ) {
        Text(
            text = status.displayName,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun SimBadge(
    simName: String,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 6.dp, vertical = 3.dp)
    ) {
        Icon(
            imageVector = Icons.Default.SimCard,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(12.dp)
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = simName,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp
        )
    }
}

@Composable
fun RecurrenceBadge(
    recurrenceType: RecurrenceType,
    modifier: Modifier = Modifier
) {
    if (recurrenceType == RecurrenceType.ONCE) return

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
            .padding(horizontal = 6.dp, vertical = 3.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Repeat,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(12.dp)
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = recurrenceType.displayName,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
