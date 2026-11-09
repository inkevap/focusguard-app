package com.focusguard.data

import android.content.Context
import com.focusguard.decision.ProfileTemplate
import com.focusguard.model.BlockMode
import com.focusguard.model.LimitKind
import com.focusguard.model.LimitPeriod
import com.focusguard.model.ScheduleInterval
import com.focusguard.model.UsageLimit
import com.focusguard.model.WebMatchType
import com.focusguard.model.WebRule
import java.io.FileNotFoundException
import java.time.DayOfWeek
import org.json.JSONArray
import org.json.JSONObject

/** Las plantillas de perfil: las genéricas, que van en el repositorio, y las personales, que solo existen en el APK de quien las compiló. */
class Templates(val generic: List<ProfileTemplate>, val personal: List<ProfileTemplate>)

/**
 * Lee `assets/plantillas_genericas.json` (de `data/seed/`) y, si existe, `assets/plantillas.json` (de `app/personal/`, que git ignora:
 * son del usuario y no se publican). Un archivo personal roto no estorba: se ignora y la app arranca igual.
 */
object TemplateCatalog {

    fun fromAssets(context: Context): Templates {
        val generic = read(context, "plantillas_genericas.json")?.let(::parse).orEmpty()
        val personal = runCatching { read(context, "plantillas.json")?.let(::parse) }.getOrNull().orEmpty()
        return Templates(generic, personal)
    }

    private fun read(context: Context, name: String): String? = try {
        context.assets.open(name).bufferedReader().use { it.readText() }
    } catch (_: FileNotFoundException) {
        null
    }

    /** Falla con una excepción si el archivo no es válido: un fallo en el archivo genérico se ve en las pruebas, no en el teléfono. */
    internal fun parse(text: String): List<ProfileTemplate> {
        val array = JSONObject(text).getJSONArray("templates")
        val templates = (0 until array.length()).map { template(array.getJSONObject(it)) }
        require(templates.map { it.id }.toSet().size == templates.size) { "plantillas con identificador repetido" }
        return templates
    }

    private fun template(item: JSONObject): ProfileTemplate {
        val schedule = item.optJSONObject("schedule")
        return ProfileTemplate(
            id = item.getString("id"),
            emoji = item.optString("emoji"),
            title = item.getString("title"),
            description = item.optString("description"),
            mode = when (val mode = item.optString("mode", "block")) {
                "block" -> BlockMode.BLOCK
                "allow_only" -> BlockMode.ALLOW_ONLY
                else -> error("modo desconocido: $mode")
            },
            categories = item.stringSet("categories"),
            apps = item.stringSet("apps"),
            webs = item.stringSet("webs").map { WebRule(it, WebMatchType.EXACT) },
            intervals = schedule?.let(::intervals).orEmpty(),
            limit = item.optInt("limitMinutes").takeIf { it > 0 }?.let { UsageLimit(LimitKind.TIME, it, LimitPeriod.DAILY) },
        )
    }

    private fun intervals(schedule: JSONObject): List<ScheduleInterval> {
        val from = minutes(schedule.getString("from"))
        val to = minutes(schedule.getString("to"))
        return days(schedule.get("days")).map { ScheduleInterval(it, from, to) }
    }

    private fun days(value: Any): List<DayOfWeek> = when (value) {
        "all" -> DayOfWeek.entries
        "weekdays" -> DayOfWeek.entries.take(5)
        "weekend" -> DayOfWeek.entries.drop(5)
        is JSONArray -> (0 until value.length()).map { DayOfWeek.of(value.getInt(it)) }
        else -> error("días no válidos: $value")
    }

    /** «22:00» o «24:00» como minutos desde las 00:00. */
    private fun minutes(text: String): Int {
        val (hours, minutes) = text.split(':').map(String::toInt)
        return hours * 60 + minutes
    }
}
