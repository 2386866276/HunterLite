package com.hunterlite.app

import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hunterlite.app.BuildConfig // 【修复】补充 BuildConfig 导入
import com.hunterlite.app.ui.theme.DarkModePref
import com.hunterlite.app.ui.theme.HunterLiteTheme
import com.hunterlite.app.ui.theme.ThemeManager
import com.hunterlite.app.ui.theme.ThemeStyle
import com.hunterlite.app.ui.language.LanguageManager
import com.hunterlite.app.ui.language.LanguagePref
import com.hunterlite.app.ui.language.AppStrings
import com.hunterlite.app.util.KeyLevel
import com.hunterlite.app.util.KeyStatus
import com.hunterlite.app.util.KeyStatusChecker
import com.hunterlite.app.util.RootChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.TimeZone

data class DeviceInfo(
    val manufacturer: String, val model: String, val androidVersion: String,
    val apiLevel: Int, val kernelVersion: String, val abiArchitecture: String,
    val bootloaderVersion: String, val blStatus: String, val buildTags: String,
    val seLinuxStatus: String, val brandInfo: String,
    val cpuHardware: String, val cpuCores: Int, val totalRam: String, val securityPatch: String,
    val buildFingerprint: String, val deviceCodename: String, val romVersion: String,
    val storageInfo: String, val uptime: String, val screenInfo: String,
    val systemLanguage: String, val timezone: String
)

fun fetchDeviceInfo(context: Context): DeviceInfo {
    val kernel = try { System.getProperty("os.version") ?: "Unknown" } catch (e: Exception) { "Unknown" }
    val selinux = try {
        val status = File("/sys/fs/selinux/enforce").readText().trim()
        if (status == "1") "Enforcing" else "Permissive"
    } catch (e: Exception) { "Unknown" }
    val blStatus = try {
        val clazz = Class.forName("android.os.SystemProperties")
        val getMethod = clazz.getMethod("get", String::class.java, String::class.java)
        val state = getMethod.invoke(null, "ro.boot.verifiedbootstate", "unknown") as String
        val locked = getMethod.invoke(null, "ro.boot.flash.locked", "unknown") as String
        when {
            state.equals("orange", true) || state.equals("yellow", true) || locked == "0" -> "Unlocked (已解锁)"
            state.equals("green", true) || locked == "1" -> "Locked (已锁定)"
            else -> "Unknown (未知)"
        }
    } catch (e: Exception) { "Unknown (未知)" }
    val brandInfo = try {
        val m = Build.MANUFACTURER.lowercase(); val b = Build.BRAND.lowercase()
        when {
            m.contains("xiaomi") || b.contains("xiaomi") || b.contains("redmi") -> "Xiaomi/Redmi"
            m.contains("oppo") || b.contains("oppo") -> "OPPO"
            b.contains("realme") -> "realme"
            m.contains("vivo") || b.contains("vivo") -> "vivo"
            b.contains("iqoo") -> "iQOO"
            m.contains("oneplus") || b.contains("oneplus") -> "OnePlus"
            b.contains("redmagic") || b.contains("nubia") -> "RedMagic/nubia"
            b.contains("lenovo") || b.contains("legion") -> "Lenovo/Legion"
            else -> Build.MANUFACTURER
        }
    } catch (e: Exception) { Build.MANUFACTURER }
    // 处理器型号：优先读 ro.chipname / ro.board.platform / ro.hardware，取第一个非空值
    val cpuHardware = try {
        val clazz = Class.forName("android.os.SystemProperties")
        val getMethod = clazz.getMethod("get", String::class.java, String::class.java)
        val candidates = listOf("ro.chipname", "ro.board.platform", "ro.hardware.chipname", "ro.product.board")
        candidates.firstNotNullOfOrNull { key ->
            val value = getMethod.invoke(null, key, "") as String
            value.takeIf { it.isNotBlank() && it != "unknown" }
        } ?: Build.HARDWARE
    } catch (e: Exception) { Build.HARDWARE }
    val cpuCores = try { Runtime.getRuntime().availableProcessors() } catch (e: Exception) { 0 }
    val totalRam = try {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        String.format(Locale.US, "%.1f GB", mi.totalMem / (1024.0 * 1024.0 * 1024.0))
    } catch (e: Exception) { "Unknown" }
    val securityPatch = try { Build.VERSION.SECURITY_PATCH.takeIf { it.isNotBlank() } ?: "Unknown" } catch (e: Exception) { "Unknown" }

    // Build 指纹与设备代号
    val buildFingerprint = try { Build.FINGERPRINT ?: "Unknown" } catch (e: Exception) { "Unknown" }
    val deviceCodename = try { "${Build.DEVICE} / ${Build.PRODUCT}" } catch (e: Exception) { "Unknown" }

    // 具体 ROM 版本号：按品牌读取对应属性，取不到则退回 Build.DISPLAY
    val romVersion = try {
        val clazz = Class.forName("android.os.SystemProperties")
        val getMethod = clazz.getMethod("get", String::class.java, String::class.java)
        val m = Build.MANUFACTURER.lowercase(); val b = Build.BRAND.lowercase()
        val candidates = when {
            m.contains("xiaomi") || b.contains("xiaomi") || b.contains("redmi") -> listOf("ro.mi.os.version.name", "ro.miui.ui.version.name")
            m.contains("oppo") || b.contains("oppo") || b.contains("realme") -> listOf("ro.build.version.oplusrom", "ro.build.version.opporom")
            m.contains("vivo") || b.contains("vivo") || b.contains("iqoo") -> listOf("ro.vivo.os.version")
            m.contains("oneplus") || b.contains("oneplus") -> listOf("ro.build.version.oplusrom", "ro.build.version.ota")
            b.contains("redmagic") || b.contains("nubia") || m.contains("nubia") -> listOf("ro.build.nubia.rom.name")
            else -> listOf("ro.build.display.id")
        }
        candidates.firstNotNullOfOrNull { key ->
            val value = getMethod.invoke(null, key, "") as String
            value.takeIf { it.isNotBlank() && it != "unknown" }
        } ?: Build.DISPLAY
    } catch (e: Exception) { Build.DISPLAY ?: "Unknown" }

    // 存储空间：可用 / 总量
    val storageInfo = try {
        val stat = StatFs(Environment.getDataDirectory().path)
        fun fmt(bytes: Long) = String.format(Locale.US, "%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        "${fmt(stat.availableBlocksLong * stat.blockSizeLong)} / ${fmt(stat.blockCountLong * stat.blockSizeLong)}"
    } catch (e: Exception) { "Unknown" }

    // 开机时长
    val uptime = try {
        val totalMinutes = SystemClock.elapsedRealtime() / 60000
        val days = totalMinutes / (24 * 60)
        val hours = (totalMinutes % (24 * 60)) / 60
        val minutes = totalMinutes % 60
        buildString { if (days > 0) append("${days}d "); append("${hours}h ${minutes}m") }
    } catch (e: Exception) { "Unknown" }

    // 屏幕信息：分辨率 @ 密度
    val screenInfo = try {
        val dm = context.resources.displayMetrics
        "${dm.widthPixels}x${dm.heightPixels} @ ${dm.densityDpi}dpi"
    } catch (e: Exception) { "Unknown" }

    val systemLanguage = try { Locale.getDefault().displayName } catch (e: Exception) { "Unknown" }
    val timezone = try { TimeZone.getDefault().id } catch (e: Exception) { "Unknown" }

    return DeviceInfo(Build.MANUFACTURER, Build.MODEL, Build.VERSION.RELEASE, Build.VERSION.SDK_INT,
        kernel, Build.SUPPORTED_ABIS?.joinToString(", ") ?: "Unknown",
        Build.BOOTLOADER ?: "Unknown", blStatus, Build.TAGS ?: "Unknown", selinux, brandInfo,
        cpuHardware, cpuCores, totalRam, securityPatch,
        buildFingerprint, deviceCodename, romVersion, storageInfo, uptime, screenInfo, systemLanguage, timezone)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.init(this)
        LanguageManager.init(this)
        enableEdgeToEdge()
        setContent { HunterLiteTheme { RootHunterScreen() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RootHunterScreen() {
    var isChecking by remember { mutableStateOf(false) }
    var scanResult by remember { mutableStateOf<com.hunterlite.app.util.ComprehensiveScanResult?>(null) }
    var keyStatuses by remember { mutableStateOf<List<KeyStatus>>(emptyList()) }
    var showAboutSheet by remember { mutableStateOf(false) }
    var terminalLogs by remember { mutableStateOf<List<String>>(emptyList()) }
    val context = LocalContext.current
    val deviceInfo = remember { fetchDeviceInfo(context) }
    val scope = rememberCoroutineScope()
    val langPref by LanguageManager.settings.collectAsState()
    val systemZh = remember { Locale.getDefault().language.startsWith("zh") }
    val zh = when (langPref) { LanguagePref.SYSTEM -> systemZh; LanguagePref.ZH -> true; LanguagePref.EN -> false }
    val s = AppStrings.get(zh)

    Scaffold(topBar = {
        CenterAlignedTopAppBar(
            title = { Text("HunterLite", fontWeight = FontWeight.Bold) },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            actions = { IconButton(onClick = { showAboutSheet = true }) { Icon(Icons.Filled.Info, contentDescription = s.aboutHelp) } }
        )
    }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(
                    containerColor = when {
                        isChecking -> MaterialTheme.colorScheme.surfaceVariant
                        scanResult?.isRooted == true -> MaterialTheme.colorScheme.errorContainer
                        else -> MaterialTheme.colorScheme.primaryContainer
                    }
                )) {
                    Column(modifier = Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(imageVector = when { isChecking -> Icons.Filled.Search; scanResult?.isRooted == true -> Icons.Filled.GppBad; else -> Icons.Filled.GppGood },
                            contentDescription = null, modifier = Modifier.size(64.dp),
                            tint = when { isChecking -> MaterialTheme.colorScheme.onSurfaceVariant; scanResult?.isRooted == true -> MaterialTheme.colorScheme.error; else -> MaterialTheme.colorScheme.primary })
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(text = when { isChecking -> s.statusScanning; scanResult?.isRooted == true -> s.statusRooted; scanResult == null -> s.statusWaiting; else -> s.statusSafe },
                            fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        if (scanResult != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            val scoreColor = when { scanResult!!.riskScore >= 80 -> MaterialTheme.colorScheme.primary; scanResult!!.riskScore >= 50 -> MaterialTheme.colorScheme.tertiary; else -> MaterialTheme.colorScheme.error }
                            Text(text = "${s.riskScore}${scanResult!!.riskScore}/100", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = scoreColor)
                        }
                    }
                }
            }
            if (isChecking || terminalLogs.isNotEmpty()) {
                item {
                    ElevatedCard(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(s.terminalOutput, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            terminalLogs.forEach { log -> Text(text = "> $log", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(vertical = 2.dp)) }
                        }
                    }
                }
            }
            if (scanResult != null && scanResult!!.findings.isNotEmpty()) {
                item { Text(text = s.anomalies, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) }
                items(scanResult!!.findings) { finding ->
                    ListItem(headlineContent = { Text(finding, color = MaterialTheme.colorScheme.onSurface) },
                        leadingContent = { Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)))
                }
            }
            item { DeviceInfoCard(deviceInfo, s) }
            if (keyStatuses.isNotEmpty()) item { KeyStatusCard(keyStatuses, zh) }
            item {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = {
                        isChecking = true; scanResult = null; keyStatuses = emptyList(); terminalLogs = listOf(s.logInit)
                        scope.launch {
                            delay(300); terminalLogs += s.logProps
                            delay(300); terminalLogs += s.logMaps
                            delay(300); terminalLogs += s.logMounts
                            delay(300); terminalLogs += s.logFrida
                            delay(300); terminalLogs += s.logPackages
                            terminalLogs += s.keyChecking
                            val (result, keys) = withContext(Dispatchers.IO) {
                                val r = RootChecker.comprehensiveScan(context, zh)
                                val k = KeyStatusChecker.checkAll(context, zh)
                                r to k
                            }
                            terminalLogs += s.logDone
                            delay(500); scanResult = result; keyStatuses = keys; isChecking = false
                        }
                    }, modifier = Modifier.fillMaxWidth().height(56.dp), enabled = !isChecking) {
                        Text(if (isChecking) s.scanning else s.startScan)
                    }
                    if (scanResult != null) {
                        OutlinedButton(onClick = { shareReport(context, deviceInfo, scanResult!!, keyStatuses, s) }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp)); Text(s.shareReport)
                        }
                    }
                }
            }
        }
    }
    if (showAboutSheet) AboutBottomSheet(onDismiss = { showAboutSheet = false }, context = context, s = s, langPref = langPref)
}

fun shareReport(context: Context, deviceInfo: DeviceInfo, result: com.hunterlite.app.util.ComprehensiveScanResult, keyStatuses: List<KeyStatus>, s: AppStrings) {
    val report = buildString {
        append("${s.repTitle}\n").append("${s.repDevice}${deviceInfo.manufacturer} ${deviceInfo.model}\n")
        append("${s.repSystem}Android ${deviceInfo.androidVersion} (API ${deviceInfo.apiLevel})\n")
        append("${s.cpuModel}: ${deviceInfo.cpuHardware} (${deviceInfo.cpuCores} cores) | ${s.totalRam}: ${deviceInfo.totalRam}\n")
        append("${s.romVersion}: ${deviceInfo.romVersion} | ${s.storageInfo}: ${deviceInfo.storageInfo}\n")
        append("${s.repKernel}${deviceInfo.kernelVersion}\n").append("${s.repSelinux}${deviceInfo.seLinuxStatus}\n")
        append("Bootloader: ${deviceInfo.blStatus}\n").append("Brand: ${deviceInfo.brandInfo}\n")
        append("${s.repRisk}${result.riskScore}/100\n").append("${s.repStatus}${if (result.isRooted) s.repRooted else s.repClean}\n")
        if (result.isEmulator) append("${s.repEmulator}\n")
        if (result.findings.isNotEmpty()) { append("\n${s.repFindings}\n"); result.findings.forEach { append("- $it\n") } }
        if (keyStatuses.isNotEmpty()) {
            append("\n--- ${s.repKeys} ---\n")
            keyStatuses.forEach { append("- ${it.name}: ${it.detail}\n") }
        }
        append("\nBuild: ${BuildConfig.BUILD_UUID} | ${BuildConfig.BUILD_TIME}\n")
        append("\n${s.repFooter}")
    }
    val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, report) }
    context.startActivity(Intent.createChooser(intent, s.shareReport))
}

fun copyDeviceInfo(context: Context, info: DeviceInfo, s: AppStrings) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Device Info", buildString {
        append("${s.deviceModel}: ${info.manufacturer} ${info.model}\n")
        append("${s.systemVersion}: Android ${info.androidVersion} (API ${info.apiLevel})\n")
        append("${s.cpuArch}: ${info.abiArchitecture}\n").append("${s.cpuModel}: ${info.cpuHardware}\n")
        append("${s.cpuCores}: ${info.cpuCores}\n").append("${s.totalRam}: ${info.totalRam}\n")
        append("${s.kernelVersion}: ${info.kernelVersion}\n").append("${s.securityPatch}: ${info.securityPatch}\n")
        append("${s.romVersion}: ${info.romVersion}\n").append("${s.deviceCodename}: ${info.deviceCodename}\n")
        append("${s.buildFingerprint}: ${info.buildFingerprint}\n")
        append("${s.storageInfo}: ${info.storageInfo}\n").append("${s.uptimeLabel}: ${info.uptime}\n")
        append("${s.screenInfo}: ${info.screenInfo}\n")
        append("${s.sysLanguage}: ${info.systemLanguage}\n").append("${s.timezoneLabel}: ${info.timezone}\n")
        append("Bootloader: ${info.blStatus}\n").append("Brand: ${info.brandInfo}\n")
        append("${s.seLinuxStatus}: ${info.seLinuxStatus}")
    }))
    Toast.makeText(context, s.copiedToast, Toast.LENGTH_SHORT).show()
}

@Composable
fun DeviceInfoCard(info: DeviceInfo, s: AppStrings) {
    val context = LocalContext.current
    ElevatedCard(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(modifier = Modifier.padding(vertical = 16.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(s.deviceProfile, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                IconButton(onClick = { copyDeviceInfo(context, info, s) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Filled.ContentCopy, contentDescription = s.copyInfo, modifier = Modifier.size(18.dp)) }
            }
            InfoRow(Icons.Filled.Smartphone, s.deviceModel, "${info.manufacturer} ${info.model}")
            InfoRow(Icons.Filled.Android, s.systemVersion, "Android ${info.androidVersion} (API ${info.apiLevel})")
            InfoRow(Icons.Filled.Memory, s.cpuArch, info.abiArchitecture)
            InfoRow(Icons.Filled.Widgets, s.cpuModel, info.cpuHardware)
            InfoRow(Icons.Filled.Speed, s.cpuCores, "${info.cpuCores}")
            InfoRow(Icons.Filled.SdStorage, s.totalRam, info.totalRam)
            InfoRow(Icons.Filled.SettingsSuggest, s.kernelVersion, info.kernelVersion)
            InfoRow(Icons.Filled.Update, s.securityPatch, info.securityPatch)
            InfoRow(Icons.Filled.SystemUpdate, s.romVersion, info.romVersion)
            InfoRow(Icons.Filled.Tag, s.deviceCodename, info.deviceCodename)
            InfoRow(Icons.Filled.Fingerprint, s.buildFingerprint, info.buildFingerprint)
            InfoRow(Icons.Filled.Storage, s.storageInfo, info.storageInfo)
            InfoRow(Icons.Filled.Timer, s.uptimeLabel, info.uptime)
            InfoRow(Icons.Filled.AspectRatio, s.screenInfo, info.screenInfo)
            InfoRow(Icons.Filled.Language, s.sysLanguage, info.systemLanguage)
            InfoRow(Icons.Filled.Public, s.timezoneLabel, info.timezone)
            InfoRow(Icons.Filled.DeveloperBoard, "Bootloader Ver", info.bootloaderVersion)
            val isBlLocked = info.blStatus.contains("Locked") && !info.blStatus.contains("Unlocked")
            InfoRow(icon = if (isBlLocked) Icons.Filled.Lock else Icons.Filled.LockOpen, label = "Bootloader", value = info.blStatus, valueColor = if (isBlLocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            InfoRow(Icons.Filled.VerifiedUser, "Brand", info.brandInfo)
            InfoRow(Icons.Filled.VerifiedUser, s.buildTags, info.buildTags)
            val seLinuxColor = when {
                info.seLinuxStatus.contains("Enforcing") -> MaterialTheme.colorScheme.primary
                info.seLinuxStatus.contains("Permissive") -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant // Unknown：无法读取，多数设备（含未root设备）的正常现象，不代表风险
            }
            InfoRow(icon = Icons.Filled.Security, label = s.seLinuxStatus, value = info.seLinuxStatus, valueColor = seLinuxColor)
        }
    }
}

@Composable
fun KeyStatusCard(statuses: List<KeyStatus>, zh: Boolean) {
    val s = AppStrings.get(zh)
    ElevatedCard(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(modifier = Modifier.padding(vertical = 16.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.VerifiedUser, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(12.dp))
                Text(s.keyStatusTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            statuses.forEach { st ->
                val color = when (st.level) {
                    KeyLevel.OK -> MaterialTheme.colorScheme.primary
                    KeyLevel.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
                    KeyLevel.WARN -> MaterialTheme.colorScheme.error
                    KeyLevel.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = when (st.level) {
                            KeyLevel.OK -> Icons.Filled.CheckCircle
                            KeyLevel.WARN -> Icons.Filled.Warning
                            else -> Icons.Filled.HelpOutline
                        },
                        contentDescription = null, modifier = Modifier.size(16.dp), tint = color
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(st.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Text(st.detail, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = color, maxLines = 2)
                }
            }
        }
    }
}

@Composable
fun InfoRow(icon: ImageVector, label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = valueColor, maxLines = 1)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutBottomSheet(onDismiss: () -> Unit, context: Context, s: AppStrings, langPref: LanguagePref) {
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showTermsDialog by remember { mutableStateOf(false) }
    val themeSettings by ThemeManager.settings.collectAsState()

    // 【修复】将局部 fun 改为 Lambda 变量，防止 Compose 编译器解析错位
    val copyQQ: (String) -> Unit = { qq ->
        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cb.setPrimaryClip(ClipData.newPlainText("QQ", qq))
        Toast.makeText(context, s.copiedQQ, Toast.LENGTH_SHORT).show()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Text(s.aboutTitle, modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            ListItem(headlineContent = { Text(s.version) }, supportingContent = { Text(s.author) }, leadingContent = { Icon(Icons.Filled.Info, null, tint = MaterialTheme.colorScheme.primary) })
            ListItem(headlineContent = { Text("Build UUID: ${BuildConfig.BUILD_UUID}") }, supportingContent = { Text("Build Time: ${BuildConfig.BUILD_TIME}") }, leadingContent = { Icon(Icons.Filled.Construction, null, tint = MaterialTheme.colorScheme.tertiary) })

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(s.appearance, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeStyle.entries.forEach { style -> ThemeStyleCard(style = style, selected = themeSettings.style == style, onClick = { ThemeManager.setStyle(style) }, modifier = Modifier.weight(1f)) }
            }
            Spacer(modifier = Modifier.height(16.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                DarkModePref.entries.forEachIndexed { index, mode ->
                    SegmentedButton(selected = themeSettings.darkMode == mode, onClick = { ThemeManager.setDarkMode(mode) }, shape = SegmentedButtonDefaults.itemShape(index, DarkModePref.entries.size)) {
                        Text(when (mode) { DarkModePref.LIGHT -> s.modeLight; DarkModePref.DARK -> s.modeDark; DarkModePref.SYSTEM -> s.modeSystem }, fontSize = 13.sp)
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(s.language, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                LanguagePref.entries.forEachIndexed { index, pref ->
                    SegmentedButton(selected = langPref == pref, onClick = { LanguageManager.setLanguage(pref) }, shape = SegmentedButtonDefaults.itemShape(index, LanguagePref.entries.size)) {
                        Text(when (pref) { LanguagePref.SYSTEM -> s.langSystem; LanguagePref.ZH -> "中文"; LanguagePref.EN -> "English" }, fontSize = 13.sp)
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            ListItem(headlineContent = { Text(s.telegram) }, supportingContent = { Text("@HunterLiteapp") }, leadingContent = { Icon(Icons.Filled.Chat, null, tint = MaterialTheme.colorScheme.primary) }, modifier = Modifier.clickable { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/HunterLiteapp"))) } })
            
            // 【修复】去掉 .apply{}，直接使用 Intent(action, uri)，解决 Expecting ')' 语法错误
            ListItem(headlineContent = { Text(s.feedback) }, supportingContent = { Text("pangfuga@gmail.com") }, leadingContent = { Icon(Icons.Filled.Email, null, tint = MaterialTheme.colorScheme.primary) }, modifier = Modifier.clickable { runCatching { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:pangfuga@gmail.com"))) } })
            
            ListItem(headlineContent = { Text(s.qqProfile) }, supportingContent = { Text("2386866276") }, leadingContent = { Icon(Icons.Filled.AccountCircle, null, tint = MaterialTheme.colorScheme.primary) }, modifier = Modifier.clickable { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://office.qq.com/mobile/jump.html?from=tim&k=k6NcxKZ_7LOP7W1z2Yat7lem5q5kUrHC&ret=0&type=1&param=01010100010100048e44b064&sid=1&rawuin=2386866276&qsig=undefined&authKey="))) } })
            ListItem(headlineContent = { Text(s.afdian) }, supportingContent = { Text("ifdian.net/a/zephyr7") }, leadingContent = { Icon(Icons.Filled.Favorite, null, tint = MaterialTheme.colorScheme.error) }, modifier = Modifier.clickable { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.ifdian.net/a/zephyr7"))) } })
            ListItem(headlineContent = { Text(s.bilibili) }, supportingContent = { Text("b23.tv/DAbA6zR") }, leadingContent = { Icon(Icons.Filled.PlayCircle, null, tint = MaterialTheme.colorScheme.primary) }, modifier = Modifier.clickable { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://b23.tv/DAbA6zR"))) } })

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(s.thankTesters, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(s.noParticularOrder, modifier = Modifier.padding(horizontal = 24.dp).offset(y = (-8).dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            
            ListItem(headlineContent = { Text("固执") }, supportingContent = { Text("QQ: 1034687640") }, leadingContent = { Icon(Icons.Filled.Person, null) }, modifier = Modifier.clickable { copyQQ("1034687640") })
            ListItem(headlineContent = { Text("枫丹白露") }, supportingContent = { Text("QQ: 246991296") }, leadingContent = { Icon(Icons.Filled.Person, null) }, modifier = Modifier.clickable { copyQQ("246991296") })
            ListItem(headlineContent = { Text("情.") }, supportingContent = { Text("QQ: 3115873493") }, leadingContent = { Icon(Icons.Filled.Person, null) }, modifier = Modifier.clickable { copyQQ("3115873493") })
            ListItem(headlineContent = { Text("..") }, supportingContent = { Text("QQ: 3480076737") }, leadingContent = { Icon(Icons.Filled.Person, null) }, modifier = Modifier.clickable { copyQQ("3480076737") })
            ListItem(headlineContent = { Text("学渣老钖") }, supportingContent = { Text("QQ: 2064137416") }, leadingContent = { Icon(Icons.Filled.Person, null) }, modifier = Modifier.clickable { copyQQ("2064137416") })
            ListItem(headlineContent = { Text("小逸⌒꩜ᯅ꩜⌒") }, supportingContent = { Text("QQ: 2513428288") }, leadingContent = { Icon(Icons.Filled.Person, null) }, modifier = Modifier.clickable { copyQQ("2513428288") })
            ListItem(headlineContent = { Text("一只野生的.") }, supportingContent = { Text("QQ: 3657359302") }, leadingContent = { Icon(Icons.Filled.Person, null) }, modifier = Modifier.clickable { copyQQ("3657359302") })

            Text("酷安", modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
            val openCoolapk: (String) -> Unit = { url -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }
            ListItem(headlineContent = { Text("XIAOWEN_123") }, leadingContent = { Icon(Icons.Filled.Person, null) }, modifier = Modifier.clickable { openCoolapk("http://www.coolapk.com/u/779435") })
            ListItem(headlineContent = { Text("夜神希。") }, leadingContent = { Icon(Icons.Filled.Person, null) }, modifier = Modifier.clickable { openCoolapk("http://www.coolapk.com/u/729038") })
            ListItem(headlineContent = { Text("黑橘火龙果") }, leadingContent = { Icon(Icons.Filled.Person, null) }, modifier = Modifier.clickable { openCoolapk("http://www.coolapk.com/u/22093669") })
            ListItem(headlineContent = { Text("小白出差") }, leadingContent = { Icon(Icons.Filled.Person, null) }, modifier = Modifier.clickable { openCoolapk("http://www.coolapk.com/u/38571318") })
            ListItem(headlineContent = { Text("DesireOr2") }, leadingContent = { Icon(Icons.Filled.Person, null) }, modifier = Modifier.clickable { openCoolapk("http://www.coolapk.com/u/21093214") })
            ListItem(headlineContent = { Text("VanYun") }, leadingContent = { Icon(Icons.Filled.Person, null) }, modifier = Modifier.clickable { openCoolapk("http://www.coolapk.com/u/1172470") })
            ListItem(headlineContent = { Text("真想不出名来") }, leadingContent = { Icon(Icons.Filled.Person, null) }, modifier = Modifier.clickable { openCoolapk("http://www.coolapk.com/u/38679230") })

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            ListItem(headlineContent = { Text(s.privacy) }, leadingContent = { Icon(Icons.Filled.PrivacyTip, null) }, modifier = Modifier.clickable { showPrivacyDialog = true })
            ListItem(headlineContent = { Text(s.terms) }, leadingContent = { Icon(Icons.Filled.Description, null) }, modifier = Modifier.clickable { showTermsDialog = true })
        }
    }
    if (showPrivacyDialog) LegalDialog(title = s.privacy, text = s.privacyText, confirmText = s.understand, onDismiss = { showPrivacyDialog = false })
    if (showTermsDialog) LegalDialog(title = s.terms, text = s.termsText, confirmText = s.understand, onDismiss = { showTermsDialog = false })
}

@Composable
fun ThemeStyleCard(style: ThemeStyle, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val preview = style.tokens(isDark = false)
    Surface(modifier = modifier, shape = RoundedCornerShape(12.dp), color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant, border = BorderStroke(2.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent), onClick = onClick) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(14.dp).clip(CircleShape).background(preview.accent))
                Box(Modifier.size(14.dp).clip(CircleShape).background(preview.bg300))
                Box(Modifier.size(14.dp).clip(CircleShape).background(preview.info))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(style.displayName, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
        }
    }
}

@Composable
fun LegalDialog(title: String, text: String, confirmText: String, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title, fontWeight = FontWeight.Bold) }, text = { Text(text, style = MaterialTheme.typography.bodyMedium) }, confirmButton = { TextButton(onClick = onDismiss) { Text(confirmText) } })
}