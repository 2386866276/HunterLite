package com.hunterlite.app.ui.language

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class LanguagePref { SYSTEM, ZH, EN }

object LanguageManager {
    private const val PREF_NAME = "hunterlite_language"
    private const val KEY_LANG = "language"

    private val _settings = MutableStateFlow(LanguagePref.SYSTEM)
    val settings: StateFlow<LanguagePref> = _settings.asStateFlow()
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        _settings.value = runCatching {
            LanguagePref.valueOf(prefs.getString(KEY_LANG, LanguagePref.SYSTEM.name)!!)
        }.getOrDefault(LanguagePref.SYSTEM)
    }

    fun setLanguage(pref: LanguagePref) {
        _settings.value = pref
        prefs.edit().putString(KEY_LANG, pref.name).apply()
    }
}

data class AppStrings(
    val aboutHelp: String, val statusScanning: String, val statusRooted: String,
    val statusWaiting: String, val statusSafe: String, val riskScore: String,
    val terminalOutput: String, val anomalies: String, val deviceProfile: String,
    val copyInfo: String, val deviceModel: String, val systemVersion: String,
    val cpuArch: String, val kernelVersion: String, val buildTags: String,
    val seLinuxStatus: String, val startScan: String, val scanning: String,
    val shareReport: String, val copiedToast: String, val aboutTitle: String,
    val version: String, val author: String, val appearance: String,
    val language: String, val langSystem: String, val modeLight: String,
    val modeDark: String, val modeSystem: String, val telegram: String,
    val feedback: String, val privacy: String, val terms: String,
    val understand: String, val logInit: String, val logProps: String,
    val logMaps: String, val logMounts: String, val logFrida: String,
    val logPackages: String, val logDone: String, val repTitle: String,
    val repDevice: String, val repSystem: String, val repKernel: String,
    val repSelinux: String, val repRisk: String, val repStatus: String,
    val repRooted: String, val repClean: String, val repEmulator: String,
    val repFindings: String, val repFooter: String,
    val qqProfile: String, val afdian: String, val bilibili: String,
    val thankTesters: String, val copiedQQ: String,
    val privacyText: String, val termsText: String,
    val cpuModel: String, val cpuCores: String, val totalRam: String, val securityPatch: String,
    val buildFingerprint: String, val deviceCodename: String, val romVersion: String,
    val storageInfo: String, val uptimeLabel: String, val screenInfo: String,
    val sysLanguage: String, val timezoneLabel: String,
    val noParticularOrder: String,
    val keyStatusTitle: String, val keyChecking: String, val repKeys: String
) {
    companion object {
        fun get(zh: Boolean) = if (zh) ZH else EN

        val ZH = AppStrings(
            aboutHelp = "关于与帮助", statusScanning = "正在深度嗅探...",
            statusRooted = "警告：发现 Root 痕迹", statusWaiting = "等待扫描...",
            statusSafe = "安全：环境纯净", riskScore = "风险评分: ",
            terminalOutput = "终端输出", anomalies = "检测到的异常特征：",
            deviceProfile = "设备环境档案", copyInfo = "复制信息",
            deviceModel = "设备型号", systemVersion = "系统版本",
            cpuArch = "CPU 架构", kernelVersion = "内核版本",
            buildTags = "构建标签", seLinuxStatus = "SELinux 状态",
            startScan = "开始深度扫描", scanning = "扫描中...",
            shareReport = "分享检测报告", copiedToast = "设备信息已复制到剪贴板",
            aboutTitle = "关于 HunterLite", version = "版本 1.2.0",
            author = "作者：林映雪", appearance = "外观主题",
            language = "语言", langSystem = "跟随系统",
            modeLight = "浅色", modeDark = "深色", modeSystem = "跟随系统",
            telegram = "Telegram 频道与更新", feedback = "反馈与求助",
            privacy = "隐私政策", terms = "服务条款", understand = "我已知晓",
            logInit = "正在初始化 HunterLite 引擎...", logProps = "正在扫描系统属性...",
            logMaps = "正在分析内存映射 (/proc/self/maps)...", logMounts = "正在检查挂载点...",
            logFrida = "正在检测 Frida/Xposed...", logPackages = "正在检查可疑应用包名...",
            logDone = "扫描完成，正在生成报告...",
            repTitle = "=== HunterLite 设备体检报告 ===", repDevice = "设备: ",
            repSystem = "系统: ", repKernel = "内核: ", repSelinux = "SELinux: ",
            repRisk = "风险评分: ", repStatus = "状态: ",
            repRooted = "发现 Root 痕迹", repClean = "环境纯净安全",
            repEmulator = "警告：检测到模拟器环境", repFindings = "--- 异常特征列表 ---",
            repFooter = "由 HunterLite 生成 | TG: @HunterLiteapp",
            qqProfile = "作者 QQ 主页", afdian = "爱发电主页 (赞助支持)",
            bilibili = "哔哩哔哩空间", thankTesters = "鸣谢测试人员",
            copiedQQ = "QQ号已复制到剪贴板",
            privacyText = "HunterLite 尊重并保护您的隐私。\n本应用是一款本地设备环境检测工具，所有的 Root 环境嗅探、系统属性读取及挂载点检测均在您的设备本地完成。\n\n我们承诺：\n1. 不收集、不上传、不共享任何设备信息、检测结果或个人数据。\n2. 不包含任何第三方数据统计 SDK 或广告追踪代码。\n3. 不需要任何网络权限（除非您主动点击链接跳转至 Telegram 或发送邮件）。\n您可以放心使用本应用进行设备安全测试。",
            termsText = "欢迎使用 HunterLite。使用本应用即表示您同意以下条款：\n\n1. 用途限制：本应用仅供学习、安全研究及设备环境测试使用。请勿将其用于任何非法或恶意用途。\n2. 免责声明：Root 检测涉及读取系统底层文件及属性。虽然本应用已尽力确保代码的安全性，但因 Android 设备碎片化及各类 Root 方案（如 Magisk, KernelSU, APatch）的差异，作者（林映雪）不对因使用本应用导致的任何设备故障、数据丢失或系统不稳定承担法律责任。\n3. 知识产权：本应用的所有代码及设计归作者所有，未经授权不得用于商业倒卖。",
            cpuModel = "处理器型号", cpuCores = "CPU 核心数", totalRam = "运行内存", securityPatch = "安全补丁",
            buildFingerprint = "构建指纹", deviceCodename = "设备代号", romVersion = "ROM 版本",
            storageInfo = "存储空间", uptimeLabel = "开机时长", screenInfo = "屏幕信息",
            sysLanguage = "系统语言", timezoneLabel = "时区",
            noParticularOrder = "排名不分先后",
            keyStatusTitle = "安全密钥状态", keyChecking = "正在校验安全密钥...",
            repKeys = "安全密钥"
        )

        val EN = AppStrings(
            aboutHelp = "About & Help", statusScanning = "Deep sniffing in progress...",
            statusRooted = "Warning: Root traces detected", statusWaiting = "Waiting for scan...",
            statusSafe = "Safe: Environment is clean", riskScore = "Risk Score: ",
            terminalOutput = "Terminal Output", anomalies = "Detected anomalies:",
            deviceProfile = "Device Profile", copyInfo = "Copy Info",
            deviceModel = "Device Model", systemVersion = "System Version",
            cpuArch = "CPU Architecture", kernelVersion = "Kernel Version",
            buildTags = "Build Tags", seLinuxStatus = "SELinux Status",
            startScan = "Start Deep Scan", scanning = "Scanning...",
            shareReport = "Share Report", copiedToast = "Device info copied to clipboard",
            aboutTitle = "About HunterLite", version = "Version 1.2.0",
            author = "Author: Lin Yingxue", appearance = "Appearance",
            language = "Language", langSystem = "System",
            modeLight = "Light", modeDark = "Dark", modeSystem = "System",
            telegram = "Telegram Channel & Updates", feedback = "Feedback & Support",
            privacy = "Privacy Policy", terms = "Terms of Service", understand = "I Understand",
            logInit = "Initializing HunterLite Engine...", logProps = "Scanning system properties...",
            logMaps = "Analyzing /proc/self/maps...", logMounts = "Checking mount points...",
            logFrida = "Detecting Frida/Xposed...", logPackages = "Checking suspicious packages...",
            logDone = "Scan complete. Generating report...",
            repTitle = "=== HunterLite Device Report ===", repDevice = "Device: ",
            repSystem = "System: ", repKernel = "Kernel: ", repSelinux = "SELinux: ",
            repRisk = "Risk Score: ", repStatus = "Status: ",
            repRooted = "Root traces detected", repClean = "Environment clean",
            repEmulator = "Warning: Emulator environment detected", repFindings = "--- Findings ---",
            repFooter = "Generated by HunterLite | TG: @HunterLiteapp",
            qqProfile = "Author QQ Profile", afdian = "Afdian (Sponsor)",
            bilibili = "Bilibili Space", thankTesters = "Special Thanks to Testers",
            copiedQQ = "QQ copied to clipboard",
            privacyText = "HunterLite respects and protects your privacy.\nThis application is a local device environment detection tool. All Root environment sniffing, system property reading, and mount point detection are completed locally on your device.\n\nWe promise:\n1. We do not collect, upload, or share any device information, detection results, or personal data.\n2. We do not include any third-party data statistics SDKs or advertising tracking code.\n3. We do not require any network permissions (unless you actively click a link to jump to Telegram or send an email).\nYou can use this application for device security testing with confidence.",
            termsText = "Welcome to HunterLite. By using this application, you agree to the following terms:\n\n1. Usage Restrictions: This application is for learning, security research, and device environment testing purposes only. Please do not use it for any illegal or malicious purposes.\n2. Disclaimer: Root detection involves reading system-level files and properties. Although this application strives to ensure code safety, due to Android device fragmentation and various Root solutions (such as Magisk, KernelSU, APatch), the author (Lin Yingxue) shall not bear legal responsibility for any device failure, data loss, or system instability caused by using this application.\n3. Intellectual Property: All code and design of this application belong to the author. Unauthorized commercial resale is prohibited.",
            cpuModel = "CPU Model", cpuCores = "CPU Cores", totalRam = "Total RAM", securityPatch = "Security Patch",
            buildFingerprint = "Build Fingerprint", deviceCodename = "Device Codename", romVersion = "ROM Version",
            storageInfo = "Storage", uptimeLabel = "Uptime", screenInfo = "Screen Info",
            sysLanguage = "System Language", timezoneLabel = "Timezone",
            noParticularOrder = "In no particular order",
            keyStatusTitle = "Secure Key Status", keyChecking = "Checking secure keys...",
            repKeys = "Secure Keys"
        )
    }
}