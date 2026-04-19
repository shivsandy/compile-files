package com.example.adcleaner.data.repository

import android.app.Application
import com.example.adcleaner.data.local.StorageScanner
import com.example.adcleaner.data.network.NetworkModule
import com.example.adcleaner.domain.AdSource
import com.example.adcleaner.domain.CleanupReport
import com.example.adcleaner.domain.ParsedRules
import com.example.adcleaner.domain.ScanItem
import com.example.adcleaner.util.RuleParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class AdCleanupRepository(private val application: Application) {
    private val scanner = StorageScanner(application)

    private val defaultSources = listOf(
        AdSource("EasyList", "https://easylist.to/easylist/easylist.txt"),
        AdSource("EasyPrivacy", "https://easylist.to/easylist/easyprivacy.txt"),
        AdSource("AdGuard Tracking", "https://adguardteam.github.io/AdGuardSDNSFilter/Filters/filter.txt"),
        AdSource("StevenBlack Hosts", "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts")
    )

    suspend fun getSources(): List<AdSource> = defaultSources

    suspend fun fetchRules(sources: List<AdSource>): ParsedRules = withContext(Dispatchers.IO) {
        sources.filter { it.enabled }
            .mapNotNull { source ->
                runCatching {
                    NetworkModule.filterService.fetchList(source.url)
                }.getOrNull()
            }
            .map { RuleParser.parse(it) }
            .fold(ParsedRules()) { acc, parsed ->
                ParsedRules(
                    domains = acc.domains + parsed.domains,
                    filePatterns = acc.filePatterns + parsed.filePatterns,
                    trackingSignatures = acc.trackingSignatures + parsed.trackingSignatures
                )
            }
    }

    suspend fun scan(rules: ParsedRules): List<ScanItem> = scanner.scan(rules)

    suspend fun deleteSelected(selected: List<ScanItem>, allItems: List<ScanItem>): CleanupReport =
        withContext(Dispatchers.IO) {
            var bytesFreed = 0L
            val deleted = mutableListOf<ScanItem>()
            selected.forEach { item ->
                val file = File(item.filePath)
                if (file.exists() && file.canWrite() && file.delete()) {
                    bytesFreed += item.sizeBytes
                    deleted += item
                }
            }
            CleanupReport(
                deletedItems = deleted,
                bytesFreed = bytesFreed,
                remainingItems = (allItems.map { it.id } - deleted.map { it.id }.toSet()).size
            )
        }
}
