package com.classroom.rfdetector.core

import android.app.NotificationManager
import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SilentModeTest {
    @Test fun silenceLeftOnByAnAppThatWasKilledIsUndoneOnTheNextLaunch() {
        val app = RuntimeEnvironment.getApplication()
        val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        shadowOf(manager).setNotificationPolicyAccessGranted(true)
        manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)

        SilentMode(app).on() // 감시 중에 앱이 강제로 닫혀 off()가 불리지 않았다.
        assertEquals(NotificationManager.INTERRUPTION_FILTER_PRIORITY, manager.currentInterruptionFilter)

        val next = SilentMode(app) // 다음에 앱을 열었다.
        assertTrue(next.recoverAfterAbnormalExit())
        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL, manager.currentInterruptionFilter)
        assertFalse("Only once", next.recoverAfterAbnormalExit())
    }

    @Test fun aNormalStopLeavesNothingToRecover() {
        val app = RuntimeEnvironment.getApplication()
        val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        shadowOf(manager).setNotificationPolicyAccessGranted(true)
        val silent = SilentMode(app)
        silent.on(); silent.off()
        assertFalse(SilentMode(app).recoverAfterAbnormalExit())
    }
}
