package com.example.adcleaner.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.adcleaner.data.repository.AdCleanupRepository
import com.example.adcleaner.domain.AdSource
import com.example.adcleaner.domain.CleanupReport
import com.example.adcleaner.domain.ParsedRules
import com.example.adcleaner.domain.RiskLevel
import com.example.adcleaner.domain.ScanItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

data class AdCleanerUiState(
    val sources: List<AdSource> = emptyList(),
    val isLoading: Boolean = false,
    val items: List<ScanItem> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
    val riskFilter: RiskLevel? = null,
    val appFilter: String? = null,
    val minSizeBytes: Long = 0,
    val currentRules: ParsedRules = ParsedRules(),
    val lastReport: CleanupReport? = null,
    val sessionLog: List<String> = emptyList(),
    val error: String? = null,
    val lastScanAt: Instant? = null
)

class AdCleanerViewModel(private val repository: AdCleanupRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(AdCleanerUiState())
    val uiState: StateFlow<AdCleanerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.update { it.copy(sources = repository.getSources()) }
        }
    }

    fun refreshSources() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                repository.fetchRules(_uiState.value.sources)
            }.onSuccess { rules ->
                val updatedSources = _uiState.value.sources.map { it.copy(lastUpdated = Instant.now()) }
                _uiState.update {
                    it.copy(
                        currentRules = rules,
                        sources = updatedSources,
                        isLoading = false,
                        sessionLog = it.sessionLog + "Sources updated at ${Instant.now()}"
                    )
                }
            }.onFailure { ex ->
                _uiState.update { it.copy(error = ex.message, isLoading = false) }
            }
        }
    }

    fun runScan() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                repository.scan(_uiState.value.currentRules)
            }.onSuccess { items ->
                _uiState.update {
                    it.copy(
                        items = items,
                        selectedIds = emptySet(),
                        isLoading = false,
                        lastScanAt = Instant.now(),
                        sessionLog = it.sessionLog + "Scan completed: ${items.size} items"
                    )
                }
            }.onFailure { ex ->
                _uiState.update { it.copy(error = ex.message, isLoading = false) }
            }
        }
    }

    fun toggleSelection(id: String) {
        _uiState.update {
            val updated = it.selectedIds.toMutableSet()
            if (!updated.add(id)) updated.remove(id)
            it.copy(selectedIds = updated)
        }
    }

    fun selectAllVisible() {
        _uiState.update { state ->
            val visibleIds = filteredItems(state).map { it.id }.toSet()
            state.copy(selectedIds = visibleIds)
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedIds = emptySet()) }
    }

    fun setRiskFilter(risk: RiskLevel?) {
        _uiState.update { it.copy(riskFilter = risk) }
    }

    fun setAppFilter(appName: String?) {
        _uiState.update { it.copy(appFilter = appName) }
    }

    fun setMinSize(minSizeBytes: Long) {
        _uiState.update { it.copy(minSizeBytes = minSizeBytes) }
    }

    fun confirmDelete() {
        viewModelScope.launch {
            val state = _uiState.value
            val selected = state.items.filter { it.id in state.selectedIds }
            val report = repository.deleteSelected(selected, state.items)
            _uiState.update {
                it.copy(
                    items = it.items.filterNot { s -> s.id in state.selectedIds },
                    selectedIds = emptySet(),
                    lastReport = report,
                    sessionLog = it.sessionLog + "Deleted ${report.deletedItems.size} files (${report.bytesFreed} B)"
                )
            }
        }
    }

    fun filteredItems(state: AdCleanerUiState = _uiState.value): List<ScanItem> {
        return state.items.filter { item ->
            (state.riskFilter == null || item.riskLevel == state.riskFilter) &&
                (state.appFilter.isNullOrBlank() || item.appName == state.appFilter) &&
                (item.sizeBytes >= state.minSizeBytes)
        }
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AdCleanerViewModel(AdCleanupRepository(application)) as T
                }
            }
    }
}
