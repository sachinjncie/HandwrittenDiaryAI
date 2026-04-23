package com.diaryai.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

data class OnboardingPage(val icon: ImageVector, val title: String, val description: String)

private val pages = listOf(
    OnboardingPage(Icons.Default.MenuBook, "Scan Your Diary", "Capture handwritten diary pages with your camera. ML Kit auto-crops and enhances each page."),
    OnboardingPage(Icons.Default.DocumentScanner, "On-Device OCR", "Text is recognized entirely on-device. No internet needed — your words never leave your phone."),
    OnboardingPage(Icons.Default.Psychology, "AI-Powered Insights", "Gemma 4 runs locally to correct OCR, extract tasks, and build a personal knowledge base from your notes."),
    OnboardingPage(Icons.Default.CloudSync, "Notion Free Sync", "Sync structured tasks and knowledge entries to your free Notion workspace. Large images stay local."),
    OnboardingPage(Icons.Default.Security, "Encrypted Backup", "Full backup to Google Drive appDataFolder — encrypted, versioned, and restorable on any device.")
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { pageIndex ->
            val page = pages[pageIndex]
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(page.icon, null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(24.dp))
                Text(page.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Text(page.description, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            }
        }

        // Page indicators
        Row(modifier = Modifier.padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(pages.size) { i ->
                val selected = pagerState.currentPage == i
                Surface(
                    modifier = Modifier.size(if (selected) 24.dp else 8.dp, 8.dp),
                    shape = MaterialTheme.shapes.small,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                ) {}
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onFinish) { Text("Skip") }
            Button(onClick = {
                if (pagerState.currentPage < pages.size - 1) {
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                } else {
                    onFinish()
                }
            }) {
                Text(if (pagerState.currentPage < pages.size - 1) "Next" else "Get Started")
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Default.ArrowForward, null, modifier = Modifier.size(16.dp))
            }
        }
    }
}
