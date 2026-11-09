package com.focusguard.permissions

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PermissionTest {

    /** Con NEW_TASK la pantalla de administradores de Android se cierra sola y el permiso no se puede dar (comprobado en un Motorola con Android 15). */
    @Test fun elAdministradorNoSeAbreEnUnaTareaNueva() {
        assertFalse(Permission.ADMIN.opensInNewTask)
    }

    @Test fun losDemasPermisosSiSeAbrenEnUnaTareaNueva() {
        Permission.entries.filter { it != Permission.ADMIN }.forEach { assertTrue(it.opensInNewTask, it.name) }
    }
}
