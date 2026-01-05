package com.expensetracker

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.expensetracker.presentation.dashboard.DashboardScreen
import com.expensetracker.presentation.analysis.CategoryAnalysisScreen
import com.expensetracker.presentation.settings.SettingsScreen
import com.expensetracker.presentation.theme.ExpenseTrackerTheme
import com.expensetracker.service.TransactionService
import dagger.hilt.android.AndroidEntryPoint

sealed class Screen(val route: String, val title: String) {
    object Dashboard : Screen("dashboard", "Dashboard")
    object Rules : Screen("rules", "Rules")
    object Classify : Screen("classify", "Classify")
    object Settings : Screen("settings", "Settings")
    object Analysis : Screen("analysis", "Category Analysis")
    object PdfImport : Screen("pdf_import", "Import from PDF")
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            startMonitoringService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissions()
        
        // Check if launched from notification
        val navigateTo = intent.getStringExtra("navigate_to")
        
        setContent {
            ExpenseTrackerTheme {
                MainScreen(initialRoute = navigateTo)
            }
        }
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Handle navigation when app is already running
        val navigateTo = intent.getStringExtra("navigate_to")
        if (navigateTo != null) {
            // Recreate to trigger navigation
            recreate()
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    private fun startMonitoringService() {
        val intent = Intent(this, TransactionService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(initialRoute: String? = null) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    
    // Handle deep link navigation from notification
    LaunchedEffect(initialRoute) {
        if (initialRoute == "classify") {
            navController.navigate(Screen.Classify.route) {
                popUpTo(Screen.Dashboard.route) {
                    saveState = true
                }
                launchSingleTop = true
            }
        }
    }

    val bottomNavItems = listOf(
        Screen.Dashboard,
        Screen.Rules,
        Screen.Classify,
        Screen.Settings
    )

    Scaffold(
        bottomBar = {
            if (currentDestination?.route in bottomNavItems.map { it.route }) {
                NavigationBar {
                    bottomNavItems.forEach { screen ->
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    when (screen) {
                                        Screen.Dashboard -> Icons.Default.Dashboard
                                        Screen.Rules -> Icons.Default.Rule
                                        Screen.Classify -> Icons.Default.Category
                                        Screen.Settings -> Icons.Default.Settings
                                        else -> Icons.Default.Dashboard
                                    },
                                    contentDescription = screen.title
                                )
                            },
                            label = { Text(screen.title) },
                            selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    onCategoryClick = { categoryId ->
                        // TODO: Navigate to category details
                    },
                    onAnalysisClick = {
                        navController.navigate(Screen.Analysis.route)
                    }
                )
            }
            composable(Screen.Rules.route) {
                com.expensetracker.presentation.settings.RulesScreen()
            }
            composable(Screen.Classify.route) {
                com.expensetracker.presentation.classify.ClassifyScreen()
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onPdfImportClick = {
                        navController.navigate(Screen.PdfImport.route)
                    }
                )
            }
            
            composable(Screen.Analysis.route) {
                CategoryAnalysisScreen(
                    onBackClick = { navController.popBackStack() }
                )
            }
            
            composable(Screen.PdfImport.route) {
                com.expensetracker.presentation.pdfimport.PdfImportScreen(
                    onBackClick = { navController.popBackStack() },
                    onImportSuccess = { 
                        // Pop back to Dashboard and ensure it's at the top
                        navController.popBackStack(Screen.Dashboard.route, inclusive = false)
                    }
                )
            }
        }
    }
}