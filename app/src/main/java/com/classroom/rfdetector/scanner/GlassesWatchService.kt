package com.classroom.rfdetector.scanner

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.le.ScanFilter
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.ParcelUuid
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.classroom.rfdetector.MainActivity
import com.classroom.rfdetector.core.GLASSES_COMPANY_IDS
import com.classroom.rfdetector.core.META_GLASSES_SERVICE
import com.classroom.rfdetector.core.RfObservation

/**
 * 감시하는 동안 떠 있는 알림과 함께 실행된다. 앱 화면이 보일 때는 아무것도 받지 않고(화면의 탐색이 받는다),
 * 화면이 꺼지거나 다른 앱으로 가면 AI 안경 신호 조건으로만 블루투스를 받는다.
 * Android는 화면이 꺼진 동안 조건 없는 블루투스 탐색 결과를 주지 않기 때문이다. 와이파이는 이때 받지 않는다.
 */
class GlassesWatchService : Service() {
    companion object {
        private const val CHANNEL_WATCH = "watch"
        private const val CHANNEL_ALERT = "glasses-alert"
        private const val ID_WATCH = 1
        private const val ID_ALERT = 2

        @Volatile var instance: GlassesWatchService? = null
            private set

        /** 감시를 시작할 때(앱 화면이 보일 때) 부른다. 화면 밖에서 시작하는 것은 Android가 막는다. */
        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, GlassesWatchService::class.java)) }
        }

        fun stop(context: Context) {
            instance?.stopBackground()
            runCatching { context.stopService(Intent(context, GlassesWatchService::class.java)) }
        }

        /** 화면이 꺼진 동안 받을 블루투스 조건: 안경 회사 코드와 Meta 안경 서비스 코드. */
        fun glassesFilters(): List<ScanFilter> {
            val service = ParcelUuid.fromString("0000%04x-0000-1000-8000-00805f9b34fb".format(META_GLASSES_SERVICE))
            return GLASSES_COMPANY_IDS.keys.sorted().map { id -> ScanFilter.Builder().setManufacturerData(id, ByteArray(0)).build() } +
                ScanFilter.Builder().setServiceUuid(service).build() +
                ScanFilter.Builder().setServiceData(service, ByteArray(0)).build()
        }
    }

    private var scanner: BleScanner? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_WATCH, "감시 중 표시", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) })
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ALERT, "AI 안경 신호", NotificationManager.IMPORTANCE_HIGH).apply {
            enableVibration(false) // 진동은 앱이 직접 울린다. 방해 금지 중에도 울리도록.
            setSound(null, null)
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val types = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        val started = runCatching { ServiceCompat.startForeground(this, ID_WATCH, watchNotification("시험 감시 중 · 화면을 꺼도 AI 안경 신호는 계속 받습니다"), types) }
        if (started.isFailure) stopSelf()
        return START_NOT_STICKY
    }

    /** 화면이 꺼지거나 다른 앱으로 갔을 때. 안경 신호만 받는다. */
    fun watchInBackground(onObs: (RfObservation) -> Unit): Boolean {
        val ble = scanner ?: BleScanner(this).also { scanner = it }
        return ble.startFiltered(glassesFilters(), onObs)
    }

    /** 앱 화면으로 돌아왔을 때. 화면의 탐색이 다시 받는다. */
    fun stopBackground() {
        scanner?.stop()
    }

    /** 화면이 꺼진 동안 새 안경 신호를 받았을 때 알림을 띄운다. 진동은 앱이 따로 울린다. */
    fun notifyGlasses(count: Int) {
        val manager = getSystemService(NotificationManager::class.java)
        runCatching {
            manager.notify(ID_ALERT, NotificationCompat.Builder(this, CHANNEL_ALERT)
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentTitle("AI 안경 신호 ${count}개")
                .setContentText("앱을 열어 가까운 쪽을 찾고 안경을 직접 확인하세요.")
                .setContentIntent(openApp())
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build())
        }
    }

    private fun watchNotification(text: String): Notification = NotificationCompat.Builder(this, CHANNEL_WATCH)
        .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
        .setContentTitle("AI 안경 탐지")
        .setContentText(text)
        .setContentIntent(openApp())
        .setOngoing(true)
        .build()

    private fun openApp() = PendingIntent.getActivity(this, 0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    override fun onDestroy() {
        scanner?.stop()
        scanner = null
        runCatching { getSystemService(NotificationManager::class.java).cancel(ID_ALERT) }
        if (instance === this) instance = null
        super.onDestroy()
    }
}
