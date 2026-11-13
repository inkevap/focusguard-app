package com.focusguard.ui.protection

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.focusguard.decision.NewAppsPolicy
import com.focusguard.ui.components.ChipChoices
import com.focusguard.ui.components.FgCell
import com.focusguard.ui.components.FgGroup
import com.focusguard.ui.components.FgSwitchRow
import com.focusguard.ui.formatAmountOfTime
import com.focusguard.ui.profiles.GroupStack

/** Apps recién instaladas (A11): bloqueadas unas horas para que instalar algo no sea una salida fácil. */
@Composable
internal fun NewAppsCard(policy: NewAppsPolicy, onChange: (NewAppsPolicy) -> Unit, modifier: Modifier = Modifier) {
    GroupStack(modifier) {
        FgGroup(footer = "Una app nueva queda bloqueada durante un tiempo, así no sirve para saltarte tus reglas.") {
            FgSwitchRow("Apps recién instaladas", policy.enabled, { onChange(policy.copy(enabled = it)) })
        }
        if (policy.enabled) {
            FgGroup(header = "Tiempo bloqueada") {
                FgCell {
                    ChipChoices(
                        options = NewAppsPolicy.HOURS_OPTIONS.map { it to formatAmountOfTime(it * 60L) },
                        selected = policy.hours,
                        onSelect = { onChange(policy.copy(hours = it)) },
                    )
                }
            }
        }
    }
}
