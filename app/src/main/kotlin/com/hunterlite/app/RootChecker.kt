package com.hunterlite.app.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import android.provider.Settings
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.InputStreamReader
import java.lang.reflect.Method
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class ComprehensiveScanResult(
    val isRooted: Boolean,
    val isEmulator: Boolean,
    val riskScore: Int,
    val findings: List<String>
)

object RootChecker {

    const val SUSPICIOUS_THRESHOLD = 30

    // 权重配置
    private const val WEIGHT_SU_PATH = 15
    private const val WEIGHT_SU_EXEC = 20
    private const val WEIGHT_BUILD_TAGS = 10
    private const val WEIGHT_ROOT_APP = 20
    private const val WEIGHT_MAPS_KEYWORD = 25
    private const val WEIGHT_DEBUGGER = 10
    private const val WEIGHT_SIGNATURE = 30
    private const val WEIGHT_SELINUX = 15
    private const val WEIGHT_MOUNT_RW = 20
    private const val WEIGHT_SYSPROP = 15
    private const val WEIGHT_EMULATOR = 10
    private const val WEIGHT_BOOTLOADER_UNLOCK = 20
    // 【修复】20 -> 5：品牌特征缺失可能误伤"刷了第三方 ROM 的普通用户"（如小米刷
    // PixelExperience 后指纹不含品牌名），降为低权重仅作参考信号，不再单独触发 Root 判定。
    private const val WEIGHT_BRAND_SPOOFING = 5
    private const val WEIGHT_FRIDA_PORT = 25
    private const val WEIGHT_ADB = 5         // 新增：USB 调试权重
    private const val WEIGHT_WIRELESS_ADB = 10 // 新增：无线调试权重 (风险更高)
    private const val WEIGHT_SU_FUNCTIONAL = 25 // 新增：功能性 su 执行验证 (比 which su 更强)
    private const val WEIGHT_MAGISK_SOCKET = 25 // 新增：Magisk/Zygisk 守护进程 socket 检测
    private const val WEIGHT_XPOSED_CLASS = 25  // 新增：Xposed/LSPosed 类加载检测
    private const val WEIGHT_DEV_OPTIONS = 5    // 新增：开发者选项总开关
    private const val WEIGHT_BUSYBOX = 10           // 新增：busybox 检测
    private const val WEIGHT_LD_PRELOAD = 15        // 新增：LD_PRELOAD 环境变量注入检测
    private const val WEIGHT_STACKTRACE_HOOK = 15   // 新增：调用栈中的 hook 框架痕迹
    private const val WEIGHT_PROP_MISMATCH = 20     // 新增：多来源系统属性交叉验证不一致
    private const val WEIGHT_INSTALLER_SOURCE = 10  // 新增：安装来源非预期渠道
    private const val WEIGHT_MULTIPROCESS_MISMATCH = 30 // 新增：主进程与子进程检测结果不一致(DenyList 漏配信号)

    private fun t(zh: Boolean, zhText: String, enText: String) = if (zh) zhText else enText

    suspend fun comprehensiveScan(
        context: Context,
        zh: Boolean,
        expectedSignatureSha256: String? = null,
        expectedInstallers: List<String> = emptyList() // 留空则跳过安装来源检测，避免误伤侧载/自分发用户
    ): ComprehensiveScanResult {
        val reasons = mutableListOf<String>()
        var threatScore = 0
        var isEmulatorDetected = false

        // 1. 基础与文件系统
        if (checkSuPaths()) { reasons.add(t(zh, "检测到 su 二进制文件", "Detected su binary files")); threatScore += WEIGHT_SU_PATH }
        if (checkSuExecutable()) { reasons.add(t(zh, "成功执行 su 命令", "Successfully executed su command")); threatScore += WEIGHT_SU_EXEC }
        if (checkSuFunctional()) { reasons.add(t(zh, "su -c id 功能性验证成功 (获得 uid=0)", "Functional su -c id verification succeeded (uid=0 obtained)")); threatScore += WEIGHT_SU_FUNCTIONAL }
        if (checkBuildTags()) { reasons.add(t(zh, "Build.TAGS 包含 test-keys", "Build.TAGS contains test-keys")); threatScore += WEIGHT_BUILD_TAGS }
        if (checkRootManagerApps(context)) { reasons.add(t(zh, "检测到已安装 Root 管理类 APP", "Detected Root manager apps")); threatScore += WEIGHT_ROOT_APP }
        if (checkBusybox()) { reasons.add(t(zh, "检测到 busybox 二进制文件", "Detected busybox binary")); threatScore += WEIGHT_BUSYBOX }
        
        // 2. 内存、进程与注入
        if (checkMapsKeywords()) { reasons.add(t(zh, "/proc/self/maps 中发现可疑注入痕迹", "Suspicious injection traces in /proc/self/maps")); threatScore += WEIGHT_MAPS_KEYWORD }
        if (checkFridaPort()) { reasons.add(t(zh, "检测到 Frida 默认端口 (27042) 监听", "Frida default port (27042) listening detected")); threatScore += WEIGHT_FRIDA_PORT }
        if (checkMagiskDaemonSocket()) { reasons.add(t(zh, "检测到 Magisk/Zygisk 守护进程 socket (抽象命名空间)", "Detected Magisk/Zygisk daemon socket (abstract namespace)")); threatScore += WEIGHT_MAGISK_SOCKET }
        if (checkXposedClassLoaded()) { reasons.add(t(zh, "检测到 Xposed/LSPosed 框架类已加载到当前进程", "Xposed/LSPosed framework class loaded in current process")); threatScore += WEIGHT_XPOSED_CLASS }
        if (checkLdPreload()) { reasons.add(t(zh, "检测到 LD_PRELOAD 环境变量注入", "Detected LD_PRELOAD environment injection")); threatScore += WEIGHT_LD_PRELOAD }
        if (checkStackTraceHook()) { reasons.add(t(zh, "调用栈中发现 hook 框架痕迹", "Hook framework traces found in call stack")); threatScore += WEIGHT_STACKTRACE_HOOK }
        if (checkPropertyCrossValidation()) { reasons.add(t(zh, "系统属性多来源交叉验证结果不一致", "System property cross-validation mismatch across sources")); threatScore += WEIGHT_PROP_MISMATCH }
        if (checkMultiProcessConsistency(context)) { reasons.add(t(zh, "主进程与子进程检测结果不一致 (疑似 DenyList 漏配)", "Main/child process detection mismatch (possible DenyList gap)")); threatScore += WEIGHT_MULTIPROCESS_MISMATCH }
        
        // 3. 调试与签名
        if (checkDebugger()) { reasons.add(t(zh, "检测到调试器连接或 TracerPID 异常", "Debugger connected or TracerPID abnormal")); threatScore += WEIGHT_DEBUGGER }
        if (expectedSignatureSha256 != null && !checkSignature(context, expectedSignatureSha256)) { 
            reasons.add(t(zh, "APP 签名与预期不符，可能被二次打包", "APP signature mismatch, possibly repackaged"))
            threatScore += WEIGHT_SIGNATURE 
        }
        if (expectedInstallers.isNotEmpty() && checkNonStandardInstaller(context, expectedInstallers)) {
            reasons.add(t(zh, "APP 安装来源非预期渠道", "APP installed from unexpected source"))
            threatScore += WEIGHT_INSTALLER_SOURCE
        }
        
        // 4. 系统环境与挂载
        if (checkSELinux() == "Permissive") { reasons.add(t(zh, "SELinux 处于宽容模式 (Permissive)", "SELinux is in Permissive mode")); threatScore += WEIGHT_SELINUX }
        if (checkMountRW()) { reasons.add(t(zh, "系统分区被挂载为可写 (rw)", "System partitions mounted as read-write")); threatScore += WEIGHT_MOUNT_RW }
        if (checkSystemProperties()) { reasons.add(t(zh, "系统属性异常 (如 ro.debuggable=1)", "Abnormal system properties")); threatScore += WEIGHT_SYSPROP }
        
        // 5. 调试端口检测 (新增)
        // 【修复】Settings.Global 对普通第三方应用不可读（会抛 SecurityException），
        // 旧实现三条检测全部恒为 false。现改为：USB 调试优先探测 adbd 默认端口 5555
        // 监听（/proc/net/tcp，普通应用可读），Settings.Global 仅作辅助且失败时不计分。
        if (checkAdbDaemonPort() || checkAdbEnabled(context)) {
            reasons.add(t(zh, "检测到 ADB 调试活动 (5555 端口监听或 USB 调试开启)", "ADB debugging activity detected (port 5555 or USB debugging)"))
            threatScore += WEIGHT_ADB
        }
        if (checkWirelessAdb(context)) {
            reasons.add(t(zh, "无线调试已开启 (局域网高风险)", "Wireless Debugging is enabled (High Risk)"))
            threatScore += WEIGHT_WIRELESS_ADB
        }
        if (checkDeveloperOptionsEnabled(context)) {
            reasons.add(t(zh, "开发者选项已开启", "Developer Options enabled"))
            threatScore += WEIGHT_DEV_OPTIONS
        }

        // 6. 模拟器与伪装
        isEmulatorDetected = checkEmulator()
        if (isEmulatorDetected) { reasons.add(t(zh, "检测到模拟器/虚拟机环境", "Emulator/Virtual environment detected")); threatScore += WEIGHT_EMULATOR }

        val blReason = checkBootloaderUnlock(zh)
        if (blReason != null) { reasons.add(blReason); threatScore += WEIGHT_BOOTLOADER_UNLOCK }

        val brandReasons = checkBrandSpecific(zh)
        if (brandReasons.isNotEmpty()) { reasons.addAll(brandReasons); threatScore += WEIGHT_BRAND_SPOOFING }

        val finalSafetyScore = if (100 - threatScore < 0) 0 else 100 - threatScore

        return ComprehensiveScanResult(
            isRooted = threatScore >= SUSPICIOUS_THRESHOLD,
            isEmulator = isEmulatorDetected,
            riskScore = finalSafetyScore,
            findings = reasons
        )
    }

    // ================= 底层检测实现 =================

    private fun checkSuPaths(): Boolean {
        // 【修复】移除 /data/adb/magisk、/data/adb/ksu、/data/adb/ap、/sbin/.magisk：
        // /data 目录对无 root 应用不可读（权限 drwxrwx--x），File.exists() 恒为 false，
        // 这些路径在普通应用下是死代码。保留普通应用可 stat 的可见路径。
        val paths = arrayOf(
            "/system/bin/su", "/system/xbin/su", "/sbin/su", "/system/sbin/su",
            "/system/bin/.ext/su", "/data/local/xbin/su", "/data/local/bin/su",
            "/data/local/su", "/system/app/Superuser.apk", "/cache/su", "/vendor/bin/su"
        )
        return paths.any { File(it).exists() }
    }

    private fun checkSuExecutable(): Boolean {
        var process: Process? = null
        return try {
            process = Runtime.getRuntime().exec(arrayOf("which", "su"))
            BufferedReader(InputStreamReader(process.inputStream)).use { it.readLine() != null }
        } catch (e: Exception) { false } finally { process?.destroy() }
    }

    // 新增：功能性 su 验证 —— 实际执行 su -c id 并解析输出是否为 uid=0
    // 比 checkSuExecutable() 更强：即便 su 文件被隐藏改名，只要能拿到 root 权限就会命中；
    // 同时能过滤掉"存在 su 命令但权限被拒绝"的假阳性场景。
    //
    // 【修复】旧实现先 readLine() 再 waitFor()：Magisk 授权弹窗未决时 su 进程存活且无输出，
    // readLine() 会无限阻塞，waitFor(2s) 的超时保护根本执行不到。现改为先 waitFor 超时，
    // 超时则 destroy 并视为未 root，进程正常退出后再读取输出。
    private fun checkSuFunctional(): Boolean {
        var process: Process? = null
        return try {
            process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val exited = process.waitFor(2, TimeUnit.SECONDS)
            if (!exited) {
                // 授权弹窗未决或进程卡死：宁可漏检，不可阻塞
                process.destroy()
                return false
            }
            val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
            output.contains("uid=0")
        } catch (e: Exception) {
            false
        } finally {
            process?.destroy()
        }
    }

    // 新增：Magisk/Zygisk 守护进程会监听抽象命名空间 unix socket（不落地到文件系统，
    // 常规的文件路径隐藏对它无效）。读 /proc/net/unix 找相关关键词。
    private fun checkMagiskDaemonSocket(): Boolean {
        val keywords = listOf("magiskd", "zygiskd", "magisk_pipe", "resetprop")
        return try {
            File("/proc/net/unix").bufferedReader().useLines { lines ->
                lines.any { line ->
                    val lower = line.lowercase()
                    keywords.any { lower.contains(it) }
                }
            }
        } catch (e: Exception) {
            false
        }
    }

    // 新增：尝试加载 Xposed/LSPosed 核心类。若这些类已被注入到当前进程的
    // classloader 中，Class.forName 会成功返回而不抛异常。
    private fun checkXposedClassLoaded(): Boolean {
        val classNames = listOf(
            "de.robv.android.xposed.XposedBridge",
            "de.robv.android.xposed.XC_MethodHook",
            "org.lsposed.lspd.core.Main",
            "com.swift.sandhook.SandHook"
        )
        return classNames.any { name ->
            try {
                Class.forName(name)
                true
            } catch (e: Throwable) {
                false
            }
        }
    }

    // 新增：开发者选项总开关。单独权重较低，主要作为辅助信号和 ADB 检测联动判断。
    private fun checkDeveloperOptionsEnabled(context: Context): Boolean {
        return try {
            Settings.Global.getInt(
                context.contentResolver,
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
                0
            ) == 1
        } catch (e: Exception) {
            false
        }
    }

    // 新增：busybox 检测，和 su 路径检测同一挂。
    // 【修复】旧实现只查固定路径，Magisk 24+ 的 busybox 位于 /data/adb/magisk/busybox
    // （应用不可见）、KernelSU 的在 /data/adb/ksu/bin/busybox，全部漏报。
    // 现补充 PATH 执行探测（which busybox）与更多常见挂载路径。
    private fun checkBusybox(): Boolean {
        val paths = arrayOf(
            "/system/xbin/busybox", "/system/bin/busybox", "/sbin/busybox",
            "/vendor/bin/busybox", "/system/sd/xbin/busybox"
        )
        if (paths.any { File(it).exists() }) return true
        var process: Process? = null
        return try {
            process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "command -v busybox"))
            BufferedReader(InputStreamReader(process.inputStream)).use { it.readLine() != null }
        } catch (e: Exception) {
            false
        } finally {
            process?.destroy()
        }
    }

    // 新增：读 /proc/self/environ 检测 LD_PRELOAD 是否被设置。
    // 部分 native hook / 注入手法会通过这个环境变量在进程启动时预加载恶意 so。
    private fun checkLdPreload(): Boolean {
        return try {
            val raw = File("/proc/self/environ").readBytes()
            val entries = String(raw).split('\u0000')
            entries.any { it.startsWith("LD_PRELOAD=") && it.substringAfter("=").isNotBlank() }
        } catch (e: Exception) {
            false
        }
    }

    // 新增：扫描当前线程调用栈，查找 hook 框架相关类名。
    // 注意：这是弱信号——只有当 hook 框架的方法恰好在调用路径上时才会命中，
    // 建议仅作为 checkXposedClassLoaded() 的补充，不单独作为主要判据。
    private fun checkStackTraceHook(): Boolean {
        val keywords = listOf("xposed", "epic", "sandhook", "frida", "substrate", "lsposed")
        return try {
            Thread.currentThread().stackTrace.any { element ->
                val cls = element.className.lowercase()
                keywords.any { cls.contains(it) }
            }
        } catch (e: Exception) {
            false
        }
    }

    // 新增：同一系统属性分别用反射 API 和 getprop 命令两种方式读取，
    // 结果不一致说明某一层被 hook 篡改了返回值。
    //
    // 【为什么不会因为系统权限限制而误报】Android 10+/12+ 起，系统对第三方
    // APP 通过 SystemProperties.get() 反射读取部分属性做了收紧（很多 ro.*
    // 属性对非特权 APP 直接返回空字符串），而 exec("getprop", key) 调用系统
    // 二进制的方式有时能读到、反射拿不到（反之也可能）。下面的判断逻辑要求
    // "两边都成功读到值且内容不同"才算异常——只要有一边因权限限制返回空/null，
    // 就会被当作"无法判定"直接跳过，所以正常的系统级读取限制不会触发误报。
    private fun checkPropertyCrossValidation(): Boolean {
        val keysToCheck = listOf("ro.debuggable", "ro.secure", "ro.build.type")
        for (key in keysToCheck) {
            val viaReflection = getPropViaReflection(key)
            val viaExec = getPropViaExec(key)
            if (viaReflection != null && viaExec != null && viaReflection != viaExec) {
                return true
            }
        }
        return false
    }

    private fun getPropViaReflection(key: String): String? {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val getMethod: Method = clazz.getMethod("get", String::class.java, String::class.java)
            val value = getMethod.invoke(null, key, "") as String
            if (value.isEmpty()) null else value
        } catch (e: Exception) {
            null
        }
    }

    private fun getPropViaExec(key: String): String? {
        var process: Process? = null
        return try {
            process = Runtime.getRuntime().exec(arrayOf("getprop", key))
            val line = BufferedReader(InputStreamReader(process.inputStream)).use { it.readLine() }
            process.waitFor(1, TimeUnit.SECONDS)
            if (line.isNullOrBlank()) null else line.trim()
        } catch (e: Exception) {
            null
        } finally {
            process?.destroy()
        }
    }

    // 新增：安装来源检测。默认不启用（expectedInstallers 传空则跳过），
    // 因为侧载/自分发（GitHub、爱发电等渠道）本身就是正常场景，不应误伤。
    // 只有你明确知道自己的分发渠道包名（如 Play 商店 "com.android.vending"）时才建议开启。
    private fun checkNonStandardInstaller(context: Context, expectedInstallers: List<String>): Boolean {
        val installer = try {
            if (Build.VERSION.SDK_INT >= 30) {
                context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getInstallerPackageName(context.packageName)
            }
        } catch (e: Exception) {
            null
        }
        return installer != null && installer !in expectedInstallers
    }

    // 新增：多进程交叉验证。开一个独立进程的 RemoteCheckService（见同目录 RemoteCheckService.kt），
    // 主进程和子进程各跑一遍核心检测（su 路径/执行/root APP），结果不一致是很强的信号——
    // 因为 Magisk DenyList 等隐藏名单通常按主包名配置，容易漏掉子进程（如 ":check"）。
    // 若子进程未在 AndroidManifest.xml 中声明或绑定超时，视为"无法判定"，直接跳过不计分，
    // 避免因为忘记配置 manifest 而误伤所有用户。
    internal fun quickLocalSignal(context: Context): Boolean {
        return checkSuPaths() || checkSuExecutable() || checkSuFunctional() || checkRootManagerApps(context)
    }

    suspend fun checkMultiProcessConsistency(context: Context): Boolean {
        val appContext = context.applicationContext
        val mainSignal = quickLocalSignal(appContext)
        val remoteSignal = withTimeoutOrNull(3000L) { queryRemoteSignal(appContext) } ?: return false
        return remoteSignal != mainSignal
    }

    private suspend fun queryRemoteSignal(context: Context): Boolean? = suspendCancellableCoroutine { cont ->
        var connection: ServiceConnection? = null

        fun finish(result: Boolean?) {
            connection?.let { c -> runCatching { context.unbindService(c) } }
            if (cont.isActive) cont.resume(result) {}
        }

        val incomingMessenger = Messenger(object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                if (msg.what == RemoteCheckService.MSG_CHECK_RESULT) {
                    finish(msg.data?.getBoolean(RemoteCheckService.KEY_SIGNAL, false) ?: false)
                } else {
                    super.handleMessage(msg)
                }
            }
        })

        connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                try {
                    val serviceMessenger = Messenger(binder)
                    val msg = Message.obtain(null, RemoteCheckService.MSG_CHECK)
                    msg.replyTo = incomingMessenger
                    serviceMessenger.send(msg)
                } catch (e: RemoteException) {
                    finish(null)
                }
            }
            override fun onServiceDisconnected(name: ComponentName?) {}
        }

        try {
            val intent = Intent(context, RemoteCheckService::class.java)
            val bound = context.bindService(intent, connection!!, Context.BIND_AUTO_CREATE)
            if (!bound) finish(null)
        } catch (e: Exception) {
            finish(null)
        }

        cont.invokeOnCancellation {
            connection?.let { c -> runCatching { context.unbindService(c) } }
        }
    }

    private fun checkBuildTags(): Boolean {
        val tags = Build.TAGS
        return tags != null && tags.contains("test-keys")
    }

    private fun checkRootManagerApps(context: Context): Boolean {
        // 【修复】包列表与 AndroidManifest.xml 的 <queries> 声明保持双向一致：
        // Android 11+ 包可见性限制下，未在 <queries> 声明的包 getPackageInfo 必然抛
        // NameNotFoundException，属于永久漏报。此处补齐了旧代码缺失的包名。
        val packages = arrayOf(
            // Root 管理器
            "com.topjohnwu.magisk", "com.topjohnwu.magisk.canary", "io.github.vvb2060.magisk",
            "me.weishu.kernelsu", "me.bmax.apatch", "eu.chainfire.supersu",
            "com.noshufou.android.su", "com.koushikdutta.superuser", "com.thirdparty.superuser",
            "com.zachspong.temprootremovejb", "com.amphoras.hidemyroot", "com.formyhm.hideroot",
            "com.yellowes.su", "com.kingroot.kinguser", "com.kingo.root",
            "com.smedialink.oneclickroot", "com.zhiqupk.root.global", "com.alephzain.framaroot",
            // Xposed / LSPosed
            "de.robv.android.xposed.installer", "org.meowcat.edxposed.manager",
            "org.lsposed.manager", "com.saurik.substrate",
            // 虚拟空间 / 双开
            "com.vmos.pro", "com.vmos.gp", "io.virtualapp", "com.vphonegaga.titan",
            "com.lbe.parallel.intl", "com.excelliance.dualaid", "com.ludashi.dualspace",
            "com.polestar.super.clone", "info.cloneapp.app",
            // 游戏修改器
            "org.sbtools.gamehack", "com.zune.gamekiller", "com.cih.game_cih"
        )
        val pm = context.packageManager
        return packages.any { pkg -> try { pm.getPackageInfo(pkg, 0); true } catch (e: Exception) { false } }
    }

    // /proc/self/maps 关键词扫描 —— 找当前进程已加载的可疑 so/模块名痕迹。
    //
    // 【局限性，务必知晓】这是纯子串匹配，理论上存在误判风险：如果 APP 自身
    // 依赖的某个第三方库的文件名/路径恰好包含这几个词也会被命中。已经把
    // 过短、容易撞车的关键字（比如 "lsp"）换成了更精确的完整模块名片段
    // （"lspd" 对应 LSPosed 实际释放的 liblspd.so），降低误伤概率。
    // 如果之后遇到批量误报，第一步就该检查这里的关键字列表是不是太宽泛。
    private fun checkMapsKeywords(): Boolean {
        val keywords = listOf("magisk", "zygisk", "frida", "xposed", "substrate", "riru", "lspd", "epic")
        return try {
            File("/proc/self/maps").bufferedReader().useLines { lines ->
                lines.any { line -> keywords.any { line.lowercase().contains(it) } }
            }
        } catch (e: Exception) { false }
    }

    private fun checkDebugger(): Boolean {
        if (Debug.isDebuggerConnected() || Debug.waitingForDebugger()) return true
        val file = File("/proc/self/status")
        if (file.exists()) {
            try {
                BufferedReader(FileReader(file)).useLines { lines ->
                    for (line in lines) {
                        if (line.startsWith("TracerPid:")) {
                            val pid = line.substringAfter(":").trim().toIntOrNull() ?: 0
                            if (pid != 0) return true
                        }
                    }
                }
            } catch (e: Exception) {}
        }
        return false
    }

    private fun checkSignature(context: Context, expectedSha256: String): Boolean {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
            }
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                packageInfo.signatures
            } ?: return false
            val md = MessageDigest.getInstance("SHA-256")
            signatures.any { sig ->
                val digest = md.digest(sig.toByteArray())
                val hex = digest.joinToString("") { "%02x".format(it) }
                hex.equals(expectedSha256, ignoreCase = true)
            }
        } catch (e: Exception) { false }
    }

    private fun checkSELinux(): String {
        val file = File("/sys/fs/selinux/enforce")
        return if (file.exists() && runCatching { file.readText().trim() == "0" }.getOrDefault(false)) "Permissive" else "Enforcing"
    }

    private fun checkMountRW(): Boolean {
        val file = File("/proc/mounts")
        if (file.exists()) {
            try {
                BufferedReader(FileReader(file)).useLines { lines ->
                    for (line in lines) {
                        val parts = line.split(" ")
                        if (parts.size >= 4) {
                            val point = parts[1]; val flags = parts[3]
                            if ((point == "/system" || point == "/vendor" || point == "/product") && flags.contains("rw")) return true
                        }
                    }
                }
            } catch (e: Exception) {}
        }
        return false
    }

    // 危险系统属性检测（ro.debuggable=1 / ro.secure=0）。
    //
    // 【适用场景说明】这两个属性在正式零售版固件上分别应该是 0 和 1。
    // 极少数情况下厂商内部工程机/测试机固件会带 ro.debuggable=1，这种设备
    // 命中这条不是检测逻辑的锅——这些设备本身确实处于非零售安全基线，
    // 判定为风险是合理行为。如果你的用户群体里有大量拿到工程机的开发者/
    // 测试人员，可以考虑单独降低这条的权重，而不是把它当 bug 修掉。
    private fun checkSystemProperties(): Boolean {
        val dangerousProps = mapOf("ro.debuggable" to "1", "ro.secure" to "0")
        // 【修复】Android 10+ 对非特权应用反射读 ro.* 受限（返回空），旧实现基本失效。
        // 现补充 getprop 命令通道（普通应用可执行），两条通道任一命中即判定。
        try {
            val clazz = Class.forName("android.os.SystemProperties")
            val getMethod: Method = clazz.getMethod("get", String::class.java, String::class.java)
            for ((prop, expected) in dangerousProps) {
                val value = getMethod.invoke(null, prop, "unknown") as String
                if (value.isNotEmpty() && value != "unknown" && value.contains(expected)) return true
            }
        } catch (e: Exception) {}
        val roDebuggable = getPropViaExec("ro.debuggable")
        val roSecure = getPropViaExec("ro.secure")
        if (roDebuggable == "1" || roSecure == "0") return true
        return false
    }

    // Frida 默认端口 (27042 / 十六进制 0x69B2) 监听检测。
    //
    // 【局限性，务必知晓】这条只能抓到"用默认端口跑的 frida-server"，攻击者
    // 只要在启动 frida-server 时加 -p 参数改成非默认端口，这条检测就直接失效。
    // 不会误伤真机（真机不会有进程监听这个端口），但也不要指望它拦住
    // 稍微懂点行的逆向人员，建议只把它当作过滤"脚本小子"级别攻击的低成本手段，
    // 真正要防高阶动态调试还是得靠前面的 Xposed 类加载检测 + maps 关键词扫描组合。
    private fun checkFridaPort(): Boolean {
        val tcpFiles = arrayOf("/proc/net/tcp", "/proc/net/tcp6")
        for (path in tcpFiles) {
            try {
                BufferedReader(FileReader(File(path))).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        if (line!!.contains(":69B2")) return true
                    }
                }
            } catch (e: Exception) {}
        }
        return false
    }

    // 新增：adbd 守护进程默认端口 (5555 / 十六进制 0x15B3) 监听检测。
    // 【修复】Settings.Global.ADB_ENABLED 对普通第三方应用不可读，此为有效替代信号：
    // USB 调试开启时 adbd 会在本机 5555 端口监听（/proc/net/tcp 对应用可读）。
    private fun checkAdbDaemonPort(): Boolean {
        val tcpFiles = arrayOf("/proc/net/tcp", "/proc/net/tcp6")
        for (path in tcpFiles) {
            try {
                BufferedReader(FileReader(File(path))).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        // 本地地址列格式为 "0100007F:15B3"，监听态 STATE=0A (LISTEN)
                        if (line!!.contains(":15B3") && line!!.contains("0A")) return true
                    }
                }
            } catch (e: Exception) {}
        }
        return false
    }

    // 新增：USB 调试检测。
    // 【已知限制】Settings.Global 对普通第三方应用不可读（抛 SecurityException），
    // 此函数在无系统权限时恒为 false 且不会误加分；USB 调试主判据已切换为
    // checkAdbDaemonPort() 端口探测，本函数仅作为低版本/特殊 ROM 的辅助信号。
    private fun checkAdbEnabled(context: Context): Boolean {
        return try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
        } catch (e: Exception) { false }
    }

    // 新增：无线调试检测 (Android 11+ / API 30+)。
    // 【已知限制】同 checkAdbEnabled：Settings.Global 无权限时恒为 false 不计分。
    private fun checkWirelessAdb(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= 30) {
                // 无线调试的系统设置键名为 "adb_wifi_enabled"
                Settings.Global.getInt(context.contentResolver, "adb_wifi_enabled", 0) == 1
            } else false
        } catch (e: Exception) { false }
    }

    // 模拟器/虚拟机环境检测。
    //
    // 【为什么这么查】常见模拟器（Android Studio AVD、夜神、蓝叠等）在 Build 字段里
    // 会残留厂商没清理干净的默认值（比如 "generic"、"goldfish"、"sdk"），或者
    // 干脆用自己的品牌名（"nox"、"bluestacks"）。文件层面查 QEMU 特有的 pipe
    // 设备和调试库，这两类是模拟器环境比较难完全抹掉的痕迹。
    //
    // 【已知局限 & 加固说明】纯字符串子串匹配存在理论上的误判风险——如果某台
    // 真机的 Build.PRODUCT/DEVICE 代号恰好包含这几个关键字子串会被误伤（实际
    // 观察中未见真实案例，主流厂商命名规范基本不会撞上这些词）。为了同时降低
    // 误判风险、提高对"抹了 Build 字段但没改内核属性"的模拟器的检出率，这里
    // 额外加了 ro.kernel.qemu / ro.boot.hardware 两个更底层的属性交叉验证——
    // 真机这两个属性要么不存在，要么值不是 1/ranchu/goldfish。
    private fun checkEmulator(): Boolean {
        // 【修复】补充国产模拟器品牌关键词（MuMu/雷电/逍遥/MEmu 等），
        // 并新增 goldfish_pipe 设备文件探测（QEMU 模拟器特有，比 Build 字段更难抹掉）。
        val keywords = arrayOf(
            "generic", "sdk", "emulator", "goldfish", "vbox", "nox", "bluestacks",
            "mumu", "ldplayer", "memu", "droid4x", "remix", "ttvm", "rainbow"
        )
        val props = arrayOf(Build.FINGERPRINT, Build.MODEL, Build.MANUFACTURER, Build.BRAND, Build.DEVICE, Build.PRODUCT)
        val isMatch = props.any { prop -> keywords.any { prop.lowercase().contains(it) } }

        val qemuPropMatch = try {
            val clazz = Class.forName("android.os.SystemProperties")
            val getMethod: Method = clazz.getMethod("get", String::class.java, String::class.java)
            val qemu = getMethod.invoke(null, "ro.kernel.qemu", "0") as String
            val hw = (getMethod.invoke(null, "ro.boot.hardware", "unknown") as String).lowercase()
            qemu == "1" || hw.contains("ranchu") || hw.contains("goldfish")
        } catch (e: Exception) { false }

        return isMatch || qemuPropMatch || File("/dev/qemu_pipe").exists() ||
            File("/dev/goldfish_pipe").exists() || File("/system/lib/libc_malloc_debug_qemu.so").exists()
    }

    // Bootloader 解锁状态检测。
    //
    // 【已知局限 & 设计取舍】ro.boot.verifiedbootstate / ro.boot.flash.locked /
    // ro.warranty_bit 这几个属性不是所有厂商都严格按 AOSP 规范填写，部分老旧
    // 或小众品牌设备可能干脆不存在这些属性。这种情况下代码会安全跳过（不会
    // 误报"已解锁"），代价是极少数解锁设备可能漏检——这是刻意的保守设计：
    // 宁可漏检也不要在拿不准的时候误伤正版锁 Bootloader 的用户。
    private fun checkBootloaderUnlock(zh: Boolean): String? {
        val propsToCheck = mapOf(
            "ro.boot.verifiedbootstate" to listOf("orange", "yellow"),
            "ro.boot.flash.locked" to listOf("0"),
            "ro.warranty_bit" to listOf("1")
        )
        try {
            val clazz = Class.forName("android.os.SystemProperties")
            val getMethod: Method = clazz.getMethod("get", String::class.java, String::class.java)
            for ((prop, badValues) in propsToCheck) {
                val value = getMethod.invoke(null, prop, "unknown") as String
                if (value.isNotEmpty() && value != "unknown" && badValues.contains(value)) {
                    return t(zh, "Bootloader 处于解锁状态 ($prop=$value)", "Bootloader is unlocked ($prop=$value)")
                }
            }
        } catch (e: Exception) {}
        // 【修复】ro.boot.* 属 boot 属性，Android 对非特权应用隐藏（反射返回空），
        // 旧实现因此基本失效。补充 /proc/cmdline 通道：该文件对普通应用可读，
        // 且包含内核实际收到的 androidboot.verifiedbootstate 等引导参数。
        val cmdline = try { File("/proc/cmdline").readText().lowercase() } catch (e: Exception) { "" }
        if (cmdline.contains("androidboot.verifiedbootstate=orange") ||
            cmdline.contains("androidboot.verifiedbootstate=yellow") ||
            cmdline.contains("androidboot.flash.locked=0") ||
            cmdline.contains("androidboot.warranty_bit=1")) {
            return t(zh, "Bootloader 处于解锁状态 (通过 /proc/cmdline)", "Bootloader is unlocked (via /proc/cmdline)")
        }
        return null
    }

    // 品牌定制系统特征检测。
    //
    // 【历史 bug 说明】旧版本只查单一厂商属性（比如一加的 ro.build.oemfingerprint），
    // 缺失就直接判定"疑似改机"。但厂商大版本升级时经常会悄悄砍掉/改名旧属性——
    // 一加并入 OPLUS 体系后的新系统就是真实案例：正版真机上 ro.build.oemfingerprint
    // 已经不存在了，导致所有用最新系统的正版一加手机都会被误判成"改机"。
    // 影响：这是个高频误报，任何厂商只要做一次系统属性调整就会让该品牌全量用户中招，
    // 会被用户当成软件 bug 来反馈（参考实测反馈）。
    //
    // 【修复方式】改成双重确认：厂商定制属性缺失 且 Build.FINGERPRINT 里也
    // 找不到品牌关键字，两个条件同时成立才判定为疑似改机。
    // Build.FINGERPRINT 是系统级构建标识，改机工具通常只改 MANUFACTURER/BRAND/MODEL
    // 这几个显眼字段，很少会连 FINGERPRINT 里的品牌信息都伪造干净，
    // 所以拿它做兜底信号比单查一个易失效的厂商私有属性更稳。
    private fun checkBrandSpecific(zh: Boolean): List<String> {
        val findings = mutableListOf<String>()
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        val fingerprint = try { Build.FINGERPRINT.lowercase() } catch (e: Exception) { "" }
        try {
            val clazz = Class.forName("android.os.SystemProperties")
            val getMethod: Method = clazz.getMethod("get", String::class.java, String::class.java)

            fun propMissing(key: String): Boolean {
                val value = getMethod.invoke(null, key, "unknown") as String
                return value == "unknown" || value.isEmpty()
            }

            if (manufacturer.contains("xiaomi") || brand.contains("xiaomi") || brand.contains("redmi")) {
                val legacyMissing = propMissing("ro.miui.ui.version.name")
                val fingerprintMatches = fingerprint.contains("xiaomi") || fingerprint.contains("redmi")
                if (legacyMissing && !fingerprintMatches) findings.add(t(zh, "小米/红米设备缺失 MIUI/HyperOS 特征 (疑似改机)", "Xiaomi/Redmi missing MIUI/HyperOS properties"))
            }
            if (manufacturer.contains("oppo") || brand.contains("oppo") || brand.contains("realme")) {
                val legacyMissing = propMissing("ro.build.version.opporom") && propMissing("ro.build.version.oplusrom")
                val fingerprintMatches = fingerprint.contains("oppo") || fingerprint.contains("realme")
                if (legacyMissing && !fingerprintMatches) findings.add(t(zh, "OPPO/真我设备缺失 ColorOS/realmeUI 特征 (疑似改机)", "OPPO/realme missing ColorOS/realmeUI properties"))
            }
            if (manufacturer.contains("vivo") || brand.contains("vivo") || brand.contains("iqoo")) {
                val legacyMissing = propMissing("ro.vivo.os.version")
                val fingerprintMatches = fingerprint.contains("vivo") || fingerprint.contains("iqoo")
                if (legacyMissing && !fingerprintMatches) findings.add(t(zh, "vivo/iQOO 设备缺失 OriginOS/FuntouchOS 特征 (疑似改机)", "vivo/iQOO missing OriginOS/FuntouchOS properties"))
            }
            if (manufacturer.contains("oneplus") || brand.contains("oneplus")) {
                val legacyMissing = propMissing("ro.build.oemfingerprint")
                val fingerprintMatches = fingerprint.contains("oneplus")
                if (legacyMissing && !fingerprintMatches) findings.add(t(zh, "一加设备缺失 OEM 指纹特征 (疑似改机)", "OnePlus missing OEM fingerprint"))
            }
            if (brand.contains("redmagic") || brand.contains("nubia") || manufacturer.contains("nubia")) {
                val legacyMissing = propMissing("ro.build.nubia.rom.name")
                val fingerprintMatches = fingerprint.contains("nubia") || fingerprint.contains("redmagic")
                if (legacyMissing && !fingerprintMatches) findings.add(t(zh, "红魔/努比亚设备缺失 ROM 特征 (疑似改机)", "RedMagic/nubia missing ROM properties"))
            }
            if (brand.contains("lenovo") || brand.contains("legion") || manufacturer.contains("lenovo")) {
                val legacyMissing = propMissing("ro.product.device")
                val fingerprintMatches = fingerprint.contains("lenovo")
                if (legacyMissing && !fingerprintMatches) findings.add(t(zh, "联想/拯救者设备缺失设备特征 (疑似改机)", "Lenovo/Legion missing device properties"))
            }
        } catch (e: Exception) {}
        return findings
    }
}