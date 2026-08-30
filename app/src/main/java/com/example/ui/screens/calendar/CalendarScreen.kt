package com.example.ui.screens.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.entity.ScheduleEntity
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ScheduleCard
import com.example.ui.components.ScheduleDetailsDialog
import com.example.ui.theme.BrandIndigo
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun CalendarScreen(
    onNavigateToCreate: (Long?, Long?) -> Unit,
    viewModel: CalendarViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedScheduleForDetails by remember { mutableStateOf<ScheduleEntity?>(null) }

    val monthYearTitle = remember(uiState.currentMonth) {
        uiState.currentMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))
    }

    val selectedDateTitle = remember(uiState.selectedDate) {
        uiState.selectedDate.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.getDefault()))
    }

    Scaffold { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Screen Header
            item {
                Text(
                    text = "Calendar View",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // Calendar Month Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Month Navigation Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = monthYearTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row {
                                IconButton(onClick = { viewModel.previousMonth() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Month")
                                }
                                IconButton(onClick = { viewModel.nextMonth() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Month")
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Weekday Labels
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat").forEach { day ->
                                Text(
                                    text = day,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.width(36.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Calendar Grid Days
                        val daysInMonth = uiState.currentMonth.lengthOfMonth()
                        val firstDayOfWeek = uiState.currentMonth.atDay(1).dayOfWeek.value % 7 // 0 = Sunday, 1 = Monday, etc.

                        val totalCells = ((firstDayOfWeek + daysInMonth + 6) / 7) * 7

                        for (week in 0 until totalCells / 7) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                for (dayIndex in 0..6) {
                                    val cellIndex = week * 7 + dayIndex
                                    val dayOfMonth = cellIndex - firstDayOfWeek + 1

                                    if (dayOfMonth in 1..daysInMonth) {
                                        val cellDate = uiState.currentMonth.atDay(dayOfMonth)
                                        val isSelected = cellDate == uiState.selectedDate
                                        val isToday = cellDate == LocalDate.now()
                                        val hasSchedules = uiState.datesWithSchedulesInMonth.contains(cellDate)

                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    when {
                                                        isSelected -> MaterialTheme.colorScheme.primary
                                                        isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                        else -> Color.Transparent
                                                    }
                                                )
                                                .clickable { viewModel.selectDate(cellDate) },
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                text = "$dayOfMonth",
                                                fontSize = 13.sp,
                                                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                                color = when {
                                                    isSelected -> MaterialTheme.colorScheme.onPrimary
                                                    isToday -> MaterialTheme.colorScheme.primary
                                                    else -> MaterialTheme.colorScheme.onSurface
                                                }
                                            )
                                            if (hasSchedules) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(4.dp)
                                                        .clip(CircleShape)
                                                        .background(if (isSelected) MaterialTheme.colorScheme.onPrimary else BrandIndigo)
                                                )
                                            }
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.size(38.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Selected Date Scheduled Items Section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Scheduled on Date",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = selectedDateTitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Button(
                        onClick = { onNavigateToCreate(null, null) },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Schedule", fontSize = 12.sp)
                    }
                }
            }

            if (uiState.schedulesForSelectedDate.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Default.CalendarMonth,
                        title = "No Messages Scheduled",
                        description = "No messages are scheduled for $selectedDateTitle.",
                        actionButtonText = "Schedule for this Date",
                        onActionClick = { onNavigateToCreate(null, null) }
                    )
                }
            } else {
                items(uiState.schedulesForSelectedDate, key = { it.id }) { schedule ->
                    ScheduleCard(
                        schedule = schedule,
                        onCardClick = { selectedScheduleForDetails = schedule },
                        onEdit = { onNavigateToCreate(schedule.id, null) },
                        onDuplicate = {},
                        onTogglePause = {},
                        onSendNow = {},
                        onDelete = {}
                    )
                }
            }
        }
    }

    if (selectedScheduleForDetails != null) {
        val current = selectedScheduleForDetails!!
        ScheduleDetailsDialog(
            schedule = current,
            onDismiss = { selectedScheduleForDetails = null },
            onEdit = {
                val id = current.id
                selectedScheduleForDetails = null
                onNavigateToCreate(id, null)
            },
            onSendNow = { selectedScheduleForDetails = null },
            onDelete = { selectedScheduleForDetails = null }
        )
    }
}
