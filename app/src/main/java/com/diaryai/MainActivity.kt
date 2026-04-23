package com.diaryai

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.diaryai.ui.screens.*
import com.diaryai.ui.theme.DiaryAITheme
import com.diaryai.ui.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("diary_ai_app", Context.MODE_PRIVATE)
        val firstRun = prefs.getBoolean("first_run", true)

        setContent {
            DiaryAITheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DiaryNavHost(
                        startDestination = if (firstRun) "onboarding" else "home",
                        onOnboardingComplete = {
                            prefs.edit().putBoolean("first_run", false).apply()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun DiaryNavHost(startDestination: String, onOnboardingComplete: () -> Unit) {
    val navController = rememberNavController()
    val viewModel: MainViewModel = hiltViewModel()

    NavHost(navController = navController, startDestination = startDestination) {

        composable("onboarding") {
            OnboardingScreen(onFinish = {
                onOnboardingComplete()
                navController.navigate("home") {
                    popUpTo("onboarding") { inclusive = true }
                }
            })
        }

        composable("home") {
            HomeScreen(
                viewModel = viewModel,
                onNewScan = {
                    viewModel.createNewSession()
                    navController.navigate("scan_session")
                },
                onSessionClick = { sessionId ->
                    viewModel.loadSession(sessionId)
                    navController.navigate("ocr_review")
                },
                onNavigate = { route -> navController.navigate(route) }
            )
        }

        composable("scan_session") {
            ScanSessionScreen(
                viewModel = viewModel,
                onProceedToOcr = { navController.navigate("ocr_review") },
                onBack = { navController.popBackStack() }
            )
        }

        composable("ocr_review") {
            OcrReviewScreen(
                viewModel = viewModel,
                onProceedToAI = { navController.navigate("ai_results") },
                onBack = { navController.popBackStack() }
            )
        }

        composable("ai_results") {
            AiResultsScreen(
                viewModel = viewModel,
                onDone = {
                    navController.navigate("home") {
                        popUpTo("home") { inclusive = false }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable("search") {
            ArchiveSearchScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }

        composable("tasks") {
            TasksScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }

        composable("knowledge") {
            KnowledgeScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }

        composable("sync") {
            SyncCenterScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }

        composable("backup") {
            BackupScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }

        // Settings now has a sub-route for Notion setup wizard
        composable("settings") {
            SettingsScreen(
                viewModel = viewModel,
                onNotionSetup = { navController.navigate("notion_setup") },
                onBack = { navController.popBackStack() }
            )
        }

        // ── NEW: Notion setup wizard ──────────────────────────────────────
        composable("notion_setup") {
            NotionSetupScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
