package com.example.adcleaner.domain

import java.time.Instant

enum class ArtifactType { CACHE, TRACKER, MEDIA, TEMP }
enum class RiskLevel { LOW, MODERATE, AGGRESSIVE }

data class ScanItem(
    val id: String,
    val appName: String,
    val packageName: String,
    val filePath: String,
    val sizeBytes: Long,
    val type: ArtifactType,
    val riskLevel: RiskLevel,
    val matchedRule: String
)

data class AdSource(
    val name: String,
    val url: String,
    val enabled: Boolean = true,
    val lastUpdated: Instant? = null,
    val ruleCount: Int = 0
)

data class ParsedRules(
    val domains: Set<String> = emptySet(),
    val filePatterns: Set<Regex> = emptySet(),
    val trackingSignatures: Set<String> = emptySet()
)

data class CleanupReport(
    val deletedItems: List<ScanItem>,
    val bytesFreed: Long,
    val remainingItems: Int,
    val timestamp: Instant = Instant.now()
)
