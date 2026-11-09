package com.focusguard.lock

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.focusguard.data.LockStyleRepository
import com.focusguard.data.TrustedClock
import com.focusguard.model.BlockDecision
import com.focusguard.model.BlockReason
import java.time.Instant
import org.koin.android.ext.android.inject

/** Plan B de la pantalla de bloqueo cuando la superposición no se puede mostrar (A4). */
class LockActivity : ComponentActivity() {

    private val lockStyle: LockStyleRepository by inject()
    private val clock: TrustedClock by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = LockScreenContent.of(
            context = this,
            reason = intent.getStringExtra(EXTRA_REASON)?.let(BlockReason::valueOf) ?: BlockReason.PROFILE_APP,
            target = intent.getStringExtra(EXTRA_TARGET).orEmpty(),
            availableAt = intent.getLongExtra(EXTRA_AVAILABLE_AT, 0L).takeIf { it > 0 }?.let(Instant::ofEpochMilli),
            displayName = intent.getStringExtra(EXTRA_NAME).orEmpty(),
            style = lockStyle.style.value,
            now = clock.now(),
        )
        setContentView(LockScreenView(this, content, intent.getBooleanExtra(EXTRA_EMERGENCY, false), onDismiss = ::finish))
    }

    companion object {
        private const val EXTRA_REASON = "reason"
        private const val EXTRA_TARGET = "target"
        private const val EXTRA_NAME = "name"
        private const val EXTRA_AVAILABLE_AT = "availableAt"
        private const val EXTRA_EMERGENCY = "emergency"

        fun intent(context: Context, block: BlockDecision.Block, displayName: String, emergency: Boolean = false): Intent =
            Intent(context, LockActivity::class.java)
                .putExtra(EXTRA_REASON, block.reason.name)
                .putExtra(EXTRA_TARGET, block.target)
                .putExtra(EXTRA_NAME, displayName)
                .putExtra(EXTRA_AVAILABLE_AT, block.availableAt?.toEpochMilli() ?: 0L)
                .putExtra(EXTRA_EMERGENCY, emergency)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY)
    }
}
