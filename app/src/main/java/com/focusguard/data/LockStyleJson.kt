package com.focusguard.data

import com.focusguard.model.LockPalettes
import com.focusguard.model.LockStyle
import org.json.JSONObject

/** La pantalla de bloqueo personalizada ⇄ JSON (preferencias y copia de seguridad). */
internal object LockStyleJson {
    fun toJson(style: LockStyle) = JSONObject()
        .put("message", style.message)
        .put("palette", style.paletteId)
        .put("emoji", style.emoji)
        .put("rotating", style.rotating)
        .put("guardMessage", style.guardMessage)
        .put("appMessages", JSONObject(style.appMessages))

    fun fromJson(json: JSONObject) = LockStyle(
        message = json.optString("message"),
        paletteId = LockPalettes.byId(json.optString("palette")).id,
        emoji = json.optString("emoji"),
        rotating = json.optBoolean("rotating"),
        guardMessage = json.optString("guardMessage"),
        appMessages = json.optJSONObject("appMessages")?.let { o -> o.keys().asSequence().associateWith { o.optString(it) } } ?: emptyMap(),
    )
}
