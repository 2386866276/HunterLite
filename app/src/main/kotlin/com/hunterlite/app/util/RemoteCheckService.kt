package com.hunterlite.app.util

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException

/**
 * 运行在独立进程（见 AndroidManifest.xml 中 android:process=":check"）的检测服务。
 *
 * 存在的意义：Magisk DenyList / Zygisk 隐藏名单通常按主包名（com.hunterlite.app）配置，
 * 容易漏配子进程（com.hunterlite.app:check）。让子进程独立跑一遍核心检测，
 * 如果结果和主进程不一致，就说明 root 环境针对主进程做了隐藏 —— 这是很强的信号。
 *
 * 通信方式用 Messenger 而非 AIDL，因为只需要传一个 boolean，没必要引入 aidl 文件。
 *
 * 需要在 AndroidManifest.xml 的 <application> 标签内添加：
 *
 * <service
 *     android:name=".util.RemoteCheckService"
 *     android:process=":check"
 *     android:exported="false" />
 */
class RemoteCheckService : Service() {

    companion object {
        const val MSG_CHECK = 1
        const val MSG_CHECK_RESULT = 2
        const val KEY_SIGNAL = "signal"
    }

    private val handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (msg.what == MSG_CHECK) {
                // 在子进程里跑和主进程一样的核心检测逻辑
                val signal = RootChecker.quickLocalSignal(applicationContext)
                val reply = Message.obtain(null, MSG_CHECK_RESULT)
                reply.data = Bundle().apply { putBoolean(KEY_SIGNAL, signal) }
                try {
                    msg.replyTo?.send(reply)
                } catch (e: RemoteException) {
                    // 主进程可能已经超时放弃等待，忽略即可
                }
            } else {
                super.handleMessage(msg)
            }
        }
    }

    private val messenger = Messenger(handler)

    override fun onBind(intent: Intent?): IBinder = messenger.binder
}
