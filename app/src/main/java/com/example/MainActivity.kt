package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.domain.util.SenderIdentityHelper
import com.example.ui.navigation.Screen
import com.example.ui.screens.calendar.CalendarScreen
import com.example.ui.screens.chat.ChatThreadScreen
import com.example.ui.screens.compose.ComposeMessageScreen
import com.example.ui.screens.create.CreateScheduleScreen
import com.example.ui.screens.dashboard.DashboardScreen
import com.example.ui.screens.history.HistoryScreen
import com.example.ui.screens.onboarding.OnboardingScreen
import com.example.ui.screens.recyclebin.RecycleBinScreen
import com.example.ui.screens.blocked.BlockedNumbersScreen
import com.example.ui.screens.schedules.SchedulesScreen
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.screens.templates.TemplatesScreen
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

data class BottomNavItem(
    val screen: Screen,
    val icon: ImageVector,
    val label: String
)

class MainActivity : ComponentActivity() {

    private val pendingExternalIntentData = mutableStateOf<Pair<String, String>?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        pendingExternalIntentData.value = extractSmsDataFromIntent(intent)

        setContent {
            val app = application as AutoSendApplication
            val userPrefs by app.userPreferencesRepository.userPreferencesFlow.collectAsState(
                initial = com.example.data.preferences.UserPreferences()
            )

            val isDarkTheme = when (userPrefs.darkMode) {
                "DARK" -> true
                "LIGHT" -> false
                else -> isSystemInDarkTheme()
            }

            MyApplicationTheme(darkTheme = isDarkTheme) {
                val navController = rememberNavController()
                val coroutineScope = rememberCoroutineScope()
                val externalIntent by pendingExternalIntentData

                LaunchedEffect(externalIntent) {
                    val intentData = externalIntent ?: return@LaunchedEffect
                    pendingExternalIntentData.value = null
                    val (address, body) = intentData
                    if (address.isNotBlank() || body.isNotBlank()) {
                        val conversations = app.smsRepository.conversations.value
                        val targetKey = SenderIdentityHelper.normalizeSenderKey(address)
                        val existingConv = if (targetKey != "UNKNOWN") {
                            conversations.firstOrNull {
                                SenderIdentityHelper.normalizeSenderKey(it.address) == targetKey
                            }
                        } else {
                            conversations.firstOrNull { it.address.equals(address, ignoreCase = true) }
                        }

                        if (existingConv != null && body.isBlank()) {
                            navController.navigate(
                                Screen.ChatThread.createRoute(
                                    threadId = existingConv.threadId,
                                    address = existingConv.address,
                                    name = existingConv.recipientName
                                )
                            )
                        } else {
                            val contactName = app.smsRepository.lookupContactName(address)
                                ?: SenderIdentityHelper.resolveOrganizationName(address, body)
                                ?: address
                            navController.navigate(
                                Screen.Compose.createRoute(
                                    address = address,
                                    name = contactName,
                                    body = body
                                )
                            )
                        }
                    }
                }

                val bottomNavItems = listOf(
                    BottomNavItem(Screen.Dashboard, Icons.Default.Dashboard, "Messages"),
                    BottomNavItem(Screen.Schedules, Icons.Default.Schedule, "Schedules"),
                    BottomNavItem(Screen.Calendar, Icons.Default.CalendarMonth, "Calendar"),
                    BottomNavItem(Screen.Settings, Icons.Default.Settings, "Settings")
                )

                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                val isBottomBarVisible = bottomNavItems.any { it.screen.route == currentRoute }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        if (isBottomBarVisible) {
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface,
                                tonalElevation = 3.dp,
                                modifier = Modifier
                            ) {
                                bottomNavItems.forEach { item ->
                                    val isSelected = currentRoute == item.screen.route
                                    NavigationBarItem(
                                        selected = isSelected,
                                        onClick = {
                                            if (currentRoute != item.screen.route) {
                                                navController.navigate(item.screen.route) {
                                                    popUpTo(navController.graph.findStartDestination().id) {
                                                        saveState = true
                                                    }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            }
                                        },
                                        icon = {
                                            Icon(
                                                imageVector = item.icon,
                                                contentDescription = item.label,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        },
                                        label = {
                                            Text(
                                                text = item.label,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                            )
                                        },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = MaterialTheme.colorScheme.primary,
                                            selectedTextColor = MaterialTheme.colorScheme.primary,
                                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                        ),
                                        modifier = Modifier.testTag("nav_tab_${item.label.lowercase()}")
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    val startDestination = if (userPrefs.onboardingCompleted) {
                        Screen.Dashboard.route
                    } else {
                        Screen.Onboarding.route
                    }

                    NavHost(
                        navController = navController,
                        startDestination = startDestination,
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable(Screen.Onboarding.route) {
                            OnboardingScreen(
                                onFinishOnboarding = {
                                    coroutineScope.launch {
                                        app.userPreferencesRepository.setOnboardingCompleted(true)
                                        navController.navigate(Screen.Dashboard.route) {
                                            popUpTo(Screen.Onboarding.route) { inclusive = true }
                                        }
                                    }
                                }
                            )
                        }

                        composable(Screen.Dashboard.route) {
                            DashboardScreen(
                                onNavigateToCompose = { address, name, body ->
                                    navController.navigate(Screen.Compose.createRoute(address, name, body))
                                },
                                onNavigateToChatThread = { threadId, address, name ->
                                    navController.navigate(Screen.ChatThread.createRoute(threadId, address, name))
                                },
                                onNavigateToCreateSchedule = { scheduleId, templateId, phone, name ->
                                    navController.navigate(Screen.CreateSchedule.createRoute(scheduleId, templateId, phone, name))
                                },
                                onNavigateToSchedules = {
                                    navController.navigate(Screen.Schedules.route)
                                },
                                onNavigateToCalendar = {
                                    navController.navigate(Screen.Calendar.route)
                                },
                                onNavigateToTemplates = {
                                    navController.navigate(Screen.Templates.route)
                                },
                                onNavigateToHistory = {
                                    navController.navigate(Screen.History.route)
                                }
                            )
                        }

                        composable(
                            route = Screen.Compose.route,
                            arguments = listOf(
                                navArgument("address") {
                                    type = NavType.StringType
                                    defaultValue = ""
                                },
                                navArgument("name") {
                                    type = NavType.StringType
                                    defaultValue = ""
                                },
                                navArgument("body") {
                                    type = NavType.StringType
                                    defaultValue = ""
                                }
                            )
                        ) { backStackEntry ->
                            val address = backStackEntry.arguments?.getString("address") ?: ""
                            val name = backStackEntry.arguments?.getString("name") ?: ""
                            val body = backStackEntry.arguments?.getString("body") ?: ""

                            ComposeMessageScreen(
                                initialAddress = address,
                                initialName = name,
                                initialBody = body,
                                onNavigateBack = { navController.popBackStack() },
                                onMessageSentOrScheduled = { sentAddr, sentName ->
                                    navController.navigate(Screen.ChatThread.createRoute(address = sentAddr, name = sentName)) {
                                        popUpTo(Screen.Compose.route) { inclusive = true }
                                    }
                                },
                                onNavigateToAdvancedScheduler = { advPhone, advName, _ ->
                                    navController.navigate(Screen.CreateSchedule.createRoute(phone = advPhone, name = advName))
                                }
                            )
                        }

                        composable(
                            route = Screen.ChatThread.route,
                            arguments = listOf(
                                navArgument("threadId") {
                                    type = NavType.LongType
                                    defaultValue = -1L
                                },
                                navArgument("address") {
                                    type = NavType.StringType
                                    defaultValue = ""
                                },
                                navArgument("name") {
                                    type = NavType.StringType
                                    defaultValue = ""
                                }
                            )
                        ) { backStackEntry ->
                            val threadId = backStackEntry.arguments?.getLong("threadId") ?: -1L
                            val address = backStackEntry.arguments?.getString("address") ?: ""
                            val name = backStackEntry.arguments?.getString("name") ?: ""

                            ChatThreadScreen(
                                threadId = threadId,
                                address = address,
                                name = name,
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToScheduleDetails = { scheduleId ->
                                    navController.navigate(Screen.CreateSchedule.createRoute(scheduleId = scheduleId))
                                }
                            )
                        }

                        composable(Screen.Schedules.route) {
                            SchedulesScreen(
                                onNavigateToCreate = { scheduleId, templateId ->
                                    navController.navigate(Screen.CreateSchedule.createRoute(scheduleId, templateId))
                                }
                            )
                        }

                        composable(Screen.Calendar.route) {
                            CalendarScreen(
                                onNavigateToCreate = { scheduleId, templateId ->
                                    navController.navigate(Screen.CreateSchedule.createRoute(scheduleId, templateId))
                                }
                            )
                        }

                        composable(Screen.Templates.route) {
                            TemplatesScreen(
                                onNavigateToCreateWithTemplate = { templateId ->
                                    navController.navigate(Screen.CreateSchedule.createRoute(templateId = templateId))
                                }
                            )
                        }

                        composable(Screen.History.route) {
                            HistoryScreen(
                                onNavigateToCreate = { recipientName, recipientPhone, messageText ->
                                    navController.navigate(Screen.CreateSchedule.createRoute(phone = recipientPhone, name = recipientName))
                                }
                            )
                        }

                        composable(Screen.Settings.route) {
                            SettingsScreen(
                                onNavigateToRecycleBin = { navController.navigate(Screen.RecycleBin.route) },
                                onNavigateToBlockedNumbers = { navController.navigate(Screen.BlockedNumbers.route) }
                            )
                        }

                        composable(Screen.RecycleBin.route) {
                            RecycleBinScreen(
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable(Screen.BlockedNumbers.route) {
                            BlockedNumbersScreen(
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable(
                            route = Screen.CreateSchedule.route,
                            arguments = listOf(
                                navArgument("scheduleId") {
                                    type = NavType.LongType
                                    defaultValue = -1L
                                },
                                navArgument("templateId") {
                                    type = NavType.LongType
                                    defaultValue = -1L
                                },
                                navArgument("phone") {
                                    type = NavType.StringType
                                    defaultValue = ""
                                },
                                navArgument("name") {
                                    type = NavType.StringType
                                    defaultValue = ""
                                }
                            )
                        ) { backStackEntry ->
                            val scheduleId = backStackEntry.arguments?.getLong("scheduleId")?.takeIf { it != -1L }
                            val templateId = backStackEntry.arguments?.getLong("templateId")?.takeIf { it != -1L }
                            val phone = backStackEntry.arguments?.getString("phone")?.takeIf { it.isNotBlank() }
                            val name = backStackEntry.arguments?.getString("name")?.takeIf { it.isNotBlank() }

                            CreateScheduleScreen(
                                scheduleId = scheduleId,
                                templateId = templateId,
                                initialPhone = phone,
                                initialName = name,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val extracted = extractSmsDataFromIntent(intent)
        if (extracted != null) {
            pendingExternalIntentData.value = extracted
        }
    }

    private fun extractSmsDataFromIntent(intent: Intent?): Pair<String, String>? {
        if (intent == null) return null
        val action = intent.action ?: return null
        if (action != Intent.ACTION_SENDTO &&
            action != Intent.ACTION_VIEW &&
            action != Intent.ACTION_SEND) {
            return null
        }

        var address = ""
        var body = ""

        val uri = intent.data
        if (uri != null) {
            val scheme = uri.scheme?.lowercase()
            if (scheme == "sms" || scheme == "smsto" || scheme == "mms" || scheme == "mmsto") {
                val ssp = uri.schemeSpecificPart ?: ""
                val queryIdx = ssp.indexOf('?')
                address = if (queryIdx >= 0) {
                    ssp.substring(0, queryIdx)
                } else {
                    ssp
                }
                try {
                    uri.getQueryParameter("body")?.let { body = it }
                } catch (e: Exception) {
                    // ignore
                }
            }
        }

        if (address.isBlank()) {
            address = intent.getStringExtra("address")
                ?: intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER)
                ?: intent.getStringExtra("phone")
                ?: ""
        }

        if (body.isBlank()) {
            body = intent.getStringExtra("sms_body")
                ?: intent.getStringExtra(Intent.EXTRA_TEXT)
                ?: ""
        }

        address = address.removePrefix("//").trim()
        if (address.isBlank() && body.isBlank()) return null
        return Pair(address, body)
    }
}
