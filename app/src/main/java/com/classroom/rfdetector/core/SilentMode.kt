package com.classroom.rfdetector.core

import android.app.NotificationManager
import android.content.Context
import android.os.Build

/**
 * 측정·감시 중에는 전화, 알림, 알람이 울리지 않게 방해 금지를 켜고 끝나면 되돌린다.
 * 미디어 범주만 남겨 두어 이 앱의 진동 알림은 계속 울린다. 사용자가 방해 금지 권한을 허용해야 쓸 수 있다.
 *
 * 되돌릴 설정은 휴대폰 안에도 적어 둔다. 감시 중 앱이 강제로 닫혀도 다음에 앱을 열 때 원래대로 돌린다.
 */
class SilentMode(context: Context) {
    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val store = context.getSharedPreferences("silent-mode", Context.MODE_PRIVATE)
    private var previous: Pair<Int, NotificationManager.Policy>? = null

    fun allowed(): Boolean = manager.isNotificationPolicyAccessGranted

    fun on() {
        if (previous != null || !allowed()) return
        // 일부 기기·테스트 환경은 현재 정책을 주지 않는다. 그때는 아무것도 허용하지 않는 기본값으로 되돌린다.
        val saved = manager.currentInterruptionFilter to (manager.notificationPolicy ?: NotificationManager.Policy(0, 0, 0))
        previous = saved
        store.edit()
            .putBoolean("active", true)
            .putInt("filter", saved.first)
            .putInt("categories", saved.second.priorityCategories)
            .putInt("calls", saved.second.priorityCallSenders)
            .putInt("messages", saved.second.priorityMessageSenders)
            .putInt("effects", saved.second.suppressedVisualEffects)
            .commit()
        manager.notificationPolicy = NotificationManager.Policy(NotificationManager.Policy.PRIORITY_CATEGORY_MEDIA, 0, 0)
        manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
    }

    fun off() {
        val (filter, policy) = previous ?: return
        previous = null
        restore(filter, policy)
    }

    /** 앱을 열 때 부른다. 지난번 감시가 끝나지 않은 채 앱이 닫혔다면 무음을 원래대로 돌린다. 되돌렸으면 true. */
    fun recoverAfterAbnormalExit(): Boolean {
        if (previous != null || !store.getBoolean("active", false)) return false
        val policy = NotificationManager.Policy(store.getInt("categories", 0), store.getInt("calls", 0),
            store.getInt("messages", 0), store.getInt("effects", 0))
        restore(store.getInt("filter", NotificationManager.INTERRUPTION_FILTER_ALL), policy)
        return true
    }

    private fun restore(filter: Int, policy: NotificationManager.Policy) {
        store.edit().clear().commit()
        if (!allowed()) return
        // Android 15부터는 앱이 켠 방해 금지가 이 앱 전용 모드로 따로 관리되므로, 그 모드만 끄면 된다.
        if (Build.VERSION.SDK_INT >= 35) manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        else {
            manager.notificationPolicy = policy
            manager.setInterruptionFilter(filter.takeIf { it != NotificationManager.INTERRUPTION_FILTER_UNKNOWN } ?: NotificationManager.INTERRUPTION_FILTER_ALL)
        }
    }
}
