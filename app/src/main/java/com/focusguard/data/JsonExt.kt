package com.focusguard.data

import org.json.JSONObject

internal fun JSONObject.stringSet(key: String): Set<String> =
    optJSONArray(key)?.let { a -> (0 until a.length()).mapTo(linkedSetOf()) { a.getString(it) } } ?: emptySet()
