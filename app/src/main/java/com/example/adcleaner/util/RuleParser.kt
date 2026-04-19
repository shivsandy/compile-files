package com.example.adcleaner.util

import com.example.adcleaner.domain.ParsedRules

object RuleParser {
    fun parse(raw: String): ParsedRules {
        val domains = mutableSetOf<String>()
        val filePatterns = mutableSetOf<Regex>()
        val trackingSignatures = mutableSetOf<String>()

        raw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("!") && !it.startsWith("#") }
            .forEach { line ->
                when {
                    line.startsWith("||") -> {
                        val domain = line.removePrefix("||").substringBefore("^").substringBefore("/")
                        if (domain.isNotBlank()) domains += domain
                    }

                    line.startsWith("/") && line.endsWith("/") -> {
                        runCatching { Regex(line.removePrefix("/").removeSuffix("/")) }
                            .onSuccess { filePatterns += it }
                    }

                    line.contains("tracker", ignoreCase = true) ||
                        line.contains("analytics", ignoreCase = true) -> {
                        trackingSignatures += line.lowercase()
                    }

                    line.contains("*") -> {
                        val regex = line
                            .replace(".", "\\.")
                            .replace("*", ".*")
                        runCatching { Regex(regex) }.onSuccess { filePatterns += it }
                    }
                }
            }

        return ParsedRules(
            domains = domains,
            filePatterns = filePatterns,
            trackingSignatures = trackingSignatures
        )
    }
}
