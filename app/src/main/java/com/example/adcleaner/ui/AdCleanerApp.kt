package com.example.adcleaner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.adcleaner.domain.RiskLevel
import com.example.adcleaner.ui.viewmodel.AdCleanerViewModel

@Composable
fun AdCleanerApp(viewModel: AdCleanerViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val tabs = listOf("Scan", "Ad Sources", "Results", "Settings")

    Scaffold { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, label ->
                    Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(label) })
                }
            }

            if (state.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            when (selectedTab) {
                0 -> ScanTab(
                    onRunScan = viewModel::runScan,
                    onRefreshSources = viewModel::refreshSources,
                    scanCount = state.items.size,
                    lastScanLabel = state.lastScanAt?.toString() ?: "never"
                )

                1 -> SourcesTab(onRefresh = viewModel::refreshSources, sources = state.sources)
                2 -> ResultsTab(
                    state = state,
                    onSelect = viewModel::toggleSelection,
                    onSelectAll = viewModel::selectAllVisible,
                    onClearSelection = viewModel::clearSelection,
                    onRiskFilter = viewModel::setRiskFilter,
                    onDelete = { showDeleteDialog = true },
                    visibleItems = viewModel.filteredItems(state)
                )

                3 -> SettingsTab(sessionLog = state.sessionLog)
            }

            state.error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        if (showDeleteDialog) {
            val selectedItems = state.items.filter { it.id in state.selectedIds }
            val totalSize = selectedItems.sumOf { it.sizeBytes }
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("Confirm deletion") },
                text = {
                    Text("Delete ${selectedItems.size} files and free $totalSize bytes? This action is manual and explicit.")
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.confirmDelete()
                        showDeleteDialog = false
                    }) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun ScanTab(onRunScan: () -> Unit, onRefreshSources: () -> Unit, scanCount: Int, lastScanLabel: String) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Run local scan across user and system apps (read-only where restricted).")
        Button(onClick = onRefreshSources) { Text("Update ad sources") }
        Button(onClick = onRunScan) { Text("Start smart scan") }
        Text("Current detections: $scanCount")
        Text("Last scan: $lastScanLabel")
    }
}

@Composable
private fun SourcesTab(onRefresh: () -> Unit, sources: List<com.example.adcleaner.domain.AdSource>) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onRefresh) { Text("Refresh all lists") }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(sources) { source ->
                Column {
                    Text(source.name)
                    Text(source.url, style = MaterialTheme.typography.bodySmall)
                    Text("Rules: ${source.ruleCount} | Updated: ${source.lastUpdated ?: "-"}")
                }
            }
        }
    }
}

@Composable
private fun ResultsTab(
    state: com.example.adcleaner.ui.viewmodel.AdCleanerUiState,
    onSelect: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onRiskFilter: (RiskLevel?) -> Unit,
    onDelete: () -> Unit,
    visibleItems: List<com.example.adcleaner.domain.ScanItem>
) {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = state.riskFilter == null, onClick = { onRiskFilter(null) }, label = { Text("All") })
            RiskLevel.entries.forEach { risk ->
                FilterChip(
                    selected = state.riskFilter == risk,
                    onClick = { onRiskFilter(risk) },
                    label = { Text(risk.name) }
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onSelectAll) { Text("Select All") }
            Button(onClick = onClearSelection) { Text("Clear") }
            Button(onClick = onDelete, enabled = state.selectedIds.isNotEmpty()) { Text("Delete Selected") }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(visibleItems) { item ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    Checkbox(checked = item.id in state.selectedIds, onCheckedChange = { onSelect(item.id) })
                    Column {
                        Text("${item.appName} • ${item.type} • ${item.riskLevel}")
                        Text(item.filePath, style = MaterialTheme.typography.bodySmall)
                        Text("${item.sizeBytes} bytes | rule: ${item.matchedRule}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        state.lastReport?.let { report ->
            Text("Last cleanup: removed ${report.deletedItems.size} files, freed ${report.bytesFreed} bytes, remaining ${report.remainingItems}.")
        }
    }
}

@Composable
private fun SettingsTab(sessionLog: List<String>) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Safety & Transparency")
        Text("• No automatic deletions\n• Manual user confirmation\n• Session-only logs")
        LazyColumn {
            items(sessionLog) { entry ->
                Text("- $entry", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
