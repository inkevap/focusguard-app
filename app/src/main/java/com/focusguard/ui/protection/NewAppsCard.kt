package com.focusguard.ui.protection

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.focusguard.decision.NewAppsPolicy
import com.focusguard.ui.components.AppCard
import com.focusguard.ui.components.ChipChoices
import com.focusguard.ui.components.Hint
import com.focusguard.ui.components.SwitchRow
import com.focusguard.ui.formatAmountOfTime

/** Apps recién instaladas (A11): bloqueadas unas horas para que instalar algo no sea una salida fácil. */
@Composable
internal fun NewAppsCard(policy: NewAppsPolicy, onChange: (NewAppsPolicy) -> Unit, modifier: Modifier = Modifier) {
    AppCard(modifier) {
        Column {
            SwitchRow(
                title = "Apps recién instaladas",
                subtitle = "Una app nueva queda bloqueada durante un tiempo, así no sirve para saltarte tus reglas.",
                checked = policy.enabled,
                onCheckedChange = { onChange(policy.copy(enabled = it)) },
            )
            if (policy.enabled) {
                Hint("Tiempo bloqueada", Modifier.padding(top = 12.dp))
                ChipChoices(
                    options = NewAppsPolicy.HOURS_OPTIONS.map { it to formatAmountOfTime(it * 60L) },
                    selected = policy.hours,
                    onSelect = { onChange(policy.copy(hours = it)) },
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
