package com.example.adcleaner.data.local

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.example.adcleaner.domain.ArtifactType
import com.example.adcleaner.domain.ParsedRules
import com.example.adcleaner.domain.RiskLevel
import com.example.adcleaner.domain.ScanItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class StorageScanner(private val application: Application) {

    private val sdkHints = listOf("admob", "facebookads", "adsdk", "adservice", "doubleclick")
    private val dirHints = listOf("cache", "tmp", "ads", "webview", "media")

    suspend fun scan(rules: ParsedRules): List<ScanItem> = withContext(Dispatchers.IO) {
        val packageManager = application.packageManager
        val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)

        installedApps.flatMap { appInfo ->
            val appName = packageManager.getApplicationLabel(appInfo).toString()
            val roots = candidateRoots(appInfo)
            roots.flatMap { root ->
                root.walkTopDown()
                    .maxDepth(4)
                    .filter { it.isFile }
                    .filter { it.length() > 0 }
                    .filter { looksAdRelated(it, rules) }
                    .map { file ->
                        toScanItem(appInfo, appName, file, rules)
                    }
                    .toList()
            }
        }.distinctBy { it.filePath }
    }

    private fun candidateRoots(appInfo: ApplicationInfo): List<File> {
        val dataDir = File(appInfo.dataDir ?: return emptyList())
        val cacheDir = File(dataDir, "cache")
        val codeCache = File(dataDir, "code_cache")
        val filesDir = File(dataDir, "files")
        return listOf(cacheDir, codeCache, filesDir, dataDir)
            .filter { it.exists() && it.canRead() }
    }

    private fun looksAdRelated(file: File, rules: ParsedRules): Boolean {
        val path = file.absolutePath.lowercase()
        val name = file.name.lowercase()
        val hasHint = sdkHints.any { path.contains(it) } || dirHints.any { path.contains(it) }
        val regexMatch = rules.filePatterns.any { regex -> regex.containsMatchIn(path) }
        val domainMatch = rules.domains.any { path.contains(it) || name.contains(it) }
        val trackerMatch = rules.trackingSignatures.any { path.contains(it) || name.contains(it) }
        return hasHint || regexMatch || domainMatch || trackerMatch
    }

    private fun toScanItem(
        appInfo: ApplicationInfo,
        appName: String,
        file: File,
        rules: ParsedRules
    ): ScanItem {
        val loweredPath = file.absolutePath.lowercase()
        val matchedRule = rules.domains.firstOrNull { loweredPath.contains(it) }
            ?: rules.trackingSignatures.firstOrNull { loweredPath.contains(it) }
            ?: "local-pattern"

        val type = when {
            loweredPath.contains("webview") -> ArtifactType.TRACKER
            loweredPath.contains("tmp") -> ArtifactType.TEMP
            loweredPath.contains("jpg") || loweredPath.contains("mp4") -> ArtifactType.MEDIA
            else -> ArtifactType.CACHE
        }

        val risk = when {
            loweredPath.contains("tracker") || loweredPath.contains("analytics") -> RiskLevel.AGGRESSIVE
            loweredPath.contains("ad") -> RiskLevel.MODERATE
            else -> RiskLevel.LOW
        }

        return ScanItem(
            id = UUID.randomUUID().toString(),
            appName = appName,
            packageName = appInfo.packageName,
            filePath = file.absolutePath,
            sizeBytes = file.length(),
            type = type,
            riskLevel = risk,
            matchedRule = matchedRule
        )
    }
}
