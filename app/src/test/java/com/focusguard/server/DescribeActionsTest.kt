package com.focusguard.server

import com.focusguard.data.ApprovalExecutor
import com.focusguard.decision.Approvals
import com.focusguard.model.StrictAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** El texto de cada acción es lo que lee el compañero en el correo: ninguna clave del ejecutor puede caer en el texto genérico. */
class DescribeActionsTest {
    private val generic = Approvals.describe("clave_que_no_existe")

    @Test fun cadaAccionTieneSuTexto() {
        val keys = listOf(
            ApprovalExecutor.KEY_ADULT_OFF, ApprovalExecutor.KEY_APPROVAL_MODE, ApprovalExecutor.KEY_DAY_START, ApprovalExecutor.KEY_NEW_APPS,
            ApprovalExecutor.KEY_EMBEDDED, ApprovalExecutor.KEY_IMAGE_SENSITIVITY, ApprovalExecutor.KEY_IMAGE_PROBE_OFF, ApprovalExecutor.KEY_IMAGE_VIGILANCE, ApprovalExecutor.KEY_IMAGE_ADAPTIVE, ApprovalExecutor.KEY_ADULT_TEXT_EXEMPT, ApprovalExecutor.KEY_FOLD_FLOATING, ApprovalExecutor.KEY_SIGN_OUT, ApprovalExecutor.KEY_PARTNER_NOTICES_OFF, ApprovalExecutor.KEY_ADULT_WORD_REMOVE, ApprovalExecutor.KEY_ADULT_ALLOW, ApprovalExecutor.KEY_SERVER_CHANGE, ApprovalExecutor.KEY_DNS_OFF, ApprovalExecutor.KEY_DNS_EXCLUDE,
            ApprovalExecutor.KEY_LAUNCHER_ICON, ApprovalExecutor.KEY_PARENTAL_OFF, ApprovalExecutor.KEY_URGE_SHARING_OFF, ApprovalExecutor.streakRestore(java.time.LocalDate.of(2026, 10, 27)), ApprovalExecutor.profileOff(1), ApprovalExecutor.profilePause(1), ApprovalExecutor.profileDelete(1),
            ApprovalExecutor.profileEdit(1), ApprovalExecutor.sessionEnd(1), ApprovalExecutor.KEY_PARTNER_PANEL,
            ApprovalExecutor.strictExit(StrictAction.Disable), ApprovalExecutor.strictExit(StrictAction.Pause(15)),
        )
        keys.forEach { assertNotEquals(generic, Approvals.describe(it), it) }
    }

    @Test fun laSalidaDelModoEstrictoDiceSiPausaODesactiva() {
        assertTrue("15" in Approvals.describe(ApprovalExecutor.strictExit(StrictAction.Pause(15))))
        assertTrue("Desactivar" in Approvals.describe(ApprovalExecutor.strictExit(StrictAction.Disable)))
    }

    @Test fun laClaveDeLaSalidaDelModoEstrictoVuelveASerLaAccion() {
        listOf(StrictAction.Disable, StrictAction.Pause(5), StrictAction.Pause(30)).forEach {
            val key = ApprovalExecutor.strictExit(it)
            assertTrue(ApprovalExecutor.isStrictExit(key))
            assertEquals(it, ApprovalExecutor.strictActionOf(key))
        }
        assertNull(ApprovalExecutor.strictActionOf("strict_exit:pause:abc"))
        assertNull(ApprovalExecutor.strictActionOf("strict_exit:otra"))
    }
}
