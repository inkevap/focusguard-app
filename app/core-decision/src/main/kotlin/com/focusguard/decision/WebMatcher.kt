package com.focusguard.decision

import com.focusguard.model.WebMatchType
import com.focusguard.model.WebRule

object WebMatcher {

    fun matches(rule: WebRule, url: String): Boolean {
        val value = UrlNormalizer.normalize(rule.value)
        if (value.isEmpty()) return false
        return when (rule.matchType) {
            WebMatchType.CONTAINS -> UrlNormalizer.normalize(url).contains(value)
            WebMatchType.EXACT -> {
                val host = UrlNormalizer.host(url)
                val ruleHost = UrlNormalizer.host(value)
                host == ruleHost || host.endsWith(".$ruleHost")
            }
        }
    }

    fun firstMatch(rules: List<WebRule>, url: String): WebRule? = rules.firstOrNull { matches(it, url) }
}
