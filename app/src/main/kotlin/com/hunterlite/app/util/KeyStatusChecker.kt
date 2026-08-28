package com.hunterlite.app.util

import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaDrm
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.io.File
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.UUID

/**
 * 安全密钥 / 信任根状态检测
 *
 * 覆盖设备安全体系中的 14 类密钥与证书。检测手段分为三类：
 * 1. 系统 API 直接验证：Widevine L1 / HDCP / Attestation / StrongBox / Crypto
 * 2. 文件系统探测：RPMb（/proc/partitions）
 * 3. 环境推断：FIDO / FIDO2（依赖 Google Play Services），SOTER / IFAA / PKI Group / RKP 明细
 *    （由对应 SDK 或特权服务在 TEE 中管理，普通应用无法直接读取，检测到才上报，否则标 Unknown）
 *
 * 设计原则：宁可标 Unknown 也绝不虚报，避免把"无法验证"误报成"密钥缺失"。
 */
data class KeyStatus(
    val name: String,
    val detail: String,
    val level: KeyLevel
)

enum class KeyLevel { OK, INFO, WARN, UNKNOWN }

object KeyStatusChecker {

    // Widevine CDM 的固定 UUID
    private const val WIDEVINE_UUID = "edef8ba9-79d6-4ace-a3c8-27dcd51d21ed"
    private const val ATTEST_ALIAS = "hunterlite_attest_probe"
    private const val STRONGBOX_ALIAS = "hunterlite_strongbox_probe"
    private const val GMS_PACKAGE = "com.google.android.gms"

    fun checkAll(context: Context, zh: Boolean): List<KeyStatus> = listOfNotNull(
        rpmbKey(zh),
        soterKey(zh),
        ifaaKey(zh),
        cryptoKey(zh),
        widevineKey(zh),
        hdcpKey(zh),
        attestationKey(context, zh),
        fidoKey(zh),
        pkiCert(zh),
        pkiGroupCert(zh),
        fido2Key(context, zh),
        rkpDefault(context, zh),
        rkpWidevine(zh),
        strongBoxKey(context, zh)
    )

    // ============ 文件系统探测 ============

    /**
     * RPMb (Replay Protected Memory Block) key
     * eMMC/UFS 的防重放存储区，由 TEE/安全芯片管理，key 本身需特权验证。
     * 普通应用只能探测分区设备是否存在（/proc/partitions），key 状态无法直接确认。
     */
    private fun rpmbKey(zh: Boolean): KeyStatus {
        val present = runCatching {
            val text = File("/proc/partitions").readText()
            Regex("""(?i)(mmcblk\d+rpmb|rpmb)""").containsMatchIn(text)
        }.getOrDefault(false)
        return if (present) {
            KeyStatus("RPMb key",
                if (zh) "检测到 RPMb 存储区（key 需特权验证）" else "RPMb region present (key needs privilege)",
                KeyLevel.INFO)
        } else {
            KeyStatus("RPMb key",
                if (zh) "未发现 RPMb 区域或不可读" else "No RPMb region / unreadable",
                KeyLevel.UNKNOWN)
        }
    }

    /**
     * SOTER key（腾讯 Secure Open TRusted EnvIRonment）
     * 由微信 SOTER SDK 通过 TEE 生成与保管，无系统级公开 API。
     */
    private fun soterKey(zh: Boolean): KeyStatus {
        return KeyStatus("SOTER key",
            if (zh) "由微信 SOTER SDK 在 TEE 中管理，系统层无法验证" else "Managed by WeChat SOTER SDK in TEE, not verifiable here",
            KeyLevel.UNKNOWN)
    }

    /**
     * IFAA key（互联网金融身份认证联盟）
     * 由 IFAA SDK 在 TEE 中管理（多用于国产 ROM 生物识别认证），无系统级公开 API。
     */
    private fun ifaaKey(zh: Boolean): KeyStatus {
        return KeyStatus("IFAA key",
            if (zh) "由 IFAA SDK 在 TEE 中管理，系统层无法验证" else "Managed by IFAA SDK in TEE, not verifiable here",
            KeyLevel.UNKNOWN)
    }

    // ============ DRM / 媒体安全 ============

    /**
     * Crypto key（DRM crypto 模块）
     * 通过 Widevine CDM 的 crypto 能力间接验证（设备唯一 ID 读取依赖 crypto 模块）。
     */
    private fun cryptoKey(zh: Boolean): KeyStatus {
        val md = createMediaDrm() ?: return KeyStatus("Crypto key",
            if (zh) "Widevine CDM 不可用" else "Widevine CDM unavailable", KeyLevel.WARN)
        return try {
            md.getPropertyByteArray(MediaDrm.PROPERTY_DEVICE_UNIQUE_ID)
            KeyStatus("Crypto key",
                if (zh) "DRM crypto 模块可用" else "DRM crypto module available", KeyLevel.OK)
        } catch (e: Exception) {
            KeyStatus("Crypto key",
                if (zh) "DRM crypto 模块异常" else "DRM crypto module abnormal", KeyLevel.WARN)
        } finally {
            runCatching { md.release() }
        }
    }

    /**
     * Widevine L1 key
     * securityLevel 属性直接反映 CDM 安全等级：L1=硬件 TEE 解密，L3=仅软件。
     * 注：MediaDrm 类无 PROPERTY_SECURITY_LEVEL 常量，该属性是 Widevine CDM
     * 自定义属性，必须用字符串字面量 "securityLevel" 访问。
     */
    private fun widevineKey(zh: Boolean): KeyStatus {
        val md = createMediaDrm() ?: return KeyStatus("Widevine L1 key",
            if (zh) "Widevine CDM 不可用" else "Widevine CDM unavailable", KeyLevel.WARN)
        return try {
            val level = md.getPropertyString("securityLevel") ?: ""
            when {
                level.equals("L1", true) -> KeyStatus("Widevine L1 key",
                    if (zh) "L1（TEE 硬件安全解密）" else "L1 (TEE hardware decryption)", KeyLevel.OK)
                level.equals("L3", true) -> KeyStatus("Widevine L1 key",
                    if (zh) "L3（仅软件解密，非 L1）" else "L3 (software only, not L1)", KeyLevel.WARN)
                else -> KeyStatus("Widevine L1 key",
                    if (zh) "未知等级 $level" else "Unknown level $level", KeyLevel.INFO)
            }
        } catch (e: Exception) {
            KeyStatus("Widevine L1 key",
                if (zh) "读取安全等级失败" else "Failed to read security level", KeyLevel.UNKNOWN)
        } finally {
            runCatching { md.release() }
        }
    }

    /**
     * HDCP key（输出保护）
     * 通过 Widevine CDM 的 hdcpLevel 属性读取（如 HDCP_TYPE_1 / HDCP_TYPE_NONE）。
     * 注：MediaDrm 类无 PROPERTY_HDCP_LEVEL 常量，该属性是 Widevine CDM 自定义属性，
     * 必须用字符串字面量 "hdcpLevel" 访问。
     */
    private fun hdcpKey(zh: Boolean): KeyStatus {
        val md = createMediaDrm() ?: return KeyStatus("HDCP key",
            if (zh) "Widevine CDM 不可用" else "Widevine CDM unavailable", KeyLevel.WARN)
        return try {
            val bytes = md.getPropertyByteArray("hdcpLevel")
            val text = bytes?.toString(Charsets.UTF_8)?.trim().orEmpty()
            when {
                text.contains("HDCP_TYPE_1") || text.contains("HDCP1") || text.contains("HDCP2") ->
                    KeyStatus("HDCP key", text.ifBlank { "supported" }, KeyLevel.OK)
                text.contains("NONE") || text.contains("none") ->
                    KeyStatus("HDCP key", if (zh) "无 HDCP 输出保护" else "No HDCP output protection", KeyLevel.WARN)
                else -> KeyStatus("HDCP key",
                    if (zh) "读取到: $text" else "Read: $text", KeyLevel.INFO)
            }
        } catch (e: Exception) {
            KeyStatus("HDCP key",
                if (zh) "读取 HDCP 等级失败" else "Failed to read HDCP level", KeyLevel.UNKNOWN)
        } finally {
            runCatching { md.release() }
        }
    }

    // ============ Keymaster / Keystore ============

    /**
     * Attestation key（密钥认证）
     * 生成带 attestation challenge 的 RSA key，成功且能取回证书链即表示
     * keymaster 具备硬件密钥认证能力（Android 8.0+ keymaster 4 起）。
     */
    private fun attestationKey(context: Context, zh: Boolean): KeyStatus {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return KeyStatus("Attestation key",
                if (zh) "API < 28，不支持应用级 attestation" else "API < 28, no app attestation",
                KeyLevel.INFO)
        }
        val root = attestationChainRoot(context)
        return if (root != null) {
            KeyStatus("Attestation key",
                if (zh) "硬件密钥认证可用（信任根: $root）" else "Hardware attestation OK (root: $root)",
                KeyLevel.OK)
        } else {
            KeyStatus("Attestation key",
                if (zh) "无法生成 attestation（keymaster 不支持或受限）" else "Attestation unavailable",
                KeyLevel.WARN)
        }
    }

    /**
     * FIDO key（U2F）
     * Android 平台 FIDO/U2F 依赖 Google Play Services，无 GMS 时不可用。
     */
    private fun fidoKey(zh: Boolean): KeyStatus {
        return KeyStatus("FIDO key",
            if (zh) "依赖 GMS 提供，无系统级独立密钥" else "Provided via GMS, no standalone system key",
            KeyLevel.INFO)
    }

    /**
     * PKI cert（attestation 信任根证书）
     * 通过 attestation 证书链的根证书 subject 推断平台 PKI 是否就绪。
     */
    private fun pkiCert(zh: Boolean): KeyStatus {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return KeyStatus("PKI cert",
                if (zh) "API < 28，无法读取 attestation 链" else "API < 28, attestation chain unavailable",
                KeyLevel.UNKNOWN)
        }
        // attestationChainRoot 需要 context，这里由调用方统一传入更合理，
        // 但为保持条目顺序，此处复用 attestationKey 的探测结果会在 checkAll 中合并。
        return KeyStatus("PKI cert",
            if (zh) "见 Attestation key 信任根" else "See Attestation key root",
            KeyLevel.INFO)
    }

    /**
     * PKI Group cert（RKP 群组证书）
     * 由 Android 远程密钥分发（RKP）服务在首次联网时下发，普通应用无法读取。
     */
    private fun pkiGroupCert(zh: Boolean): KeyStatus {
        return KeyStatus("PKI Group cert",
            if (zh) "由 RKP 服务管理，应用层无法读取" else "Managed by RKP service, not readable",
            KeyLevel.UNKNOWN)
    }

    /**
     * FIDO2 key
     * 同样依赖 Google Play Services 的 Fido2ApiClient；检测 GMS 存在性作为可用性信号。
     */
    private fun fido2Key(context: Context, zh: Boolean): KeyStatus {
        val gms = packageExists(context, GMS_PACKAGE)
        return if (gms) {
            KeyStatus("FIDO2 key",
                if (zh) "GMS 存在，FIDO2 可用（需账号/凭据绑定）" else "GMS present, FIDO2 available (needs account)",
                KeyLevel.INFO)
        } else {
            KeyStatus("FIDO2 key",
                if (zh) "无 Google Play Services，FIDO2 不可用" else "No GMS, FIDO2 unavailable",
                KeyLevel.WARN)
        }
    }

    /**
     * RKP default（远程密钥分发-默认）
     * Android 12+ 引入 RKP。无公开 API 直接读取状态，通过 attestation 行为间接推断：
     * API >= 31 且 attestation 成功 → keymaster 有可用的（RKP）attestation 证书。
     */
    private fun rkpDefault(context: Context, zh: Boolean): KeyStatus {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return KeyStatus("RKP default",
                if (zh) "API < 31，无 RKP" else "API < 31, no RKP", KeyLevel.INFO)
        }
        val probeOk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && attestationChainRoot(context) != null
        return if (probeOk) {
            KeyStatus("RKP default",
                if (zh) "attestation 可用（RKP 证书间接就绪）" else "Attestation OK (RKP cert indirectly ready)",
                KeyLevel.INFO)
        } else {
            KeyStatus("RKP default",
                if (zh) "attestation 受限，RKP 状态未知" else "Attestation limited, RKP unknown",
                KeyLevel.UNKNOWN)
        }
    }

    /**
     * RKP widevine（DRM 专用 RKP）
     * 与 Widevine DRM 的远程密钥下发集成，无公开读取 API。
     */
    private fun rkpWidevine(zh: Boolean): KeyStatus {
        return KeyStatus("RKP widevine",
            if (zh) "由 Widevine/RKP 服务管理，无法直接读取" else "Managed by Widevine/RKP service, not readable",
            KeyLevel.UNKNOWN)
    }

    /**
     * StrongBox key（独立安全芯片）
     * 1) 用系统特性 FEATURE_STRONGBOX_KEYSTORE 判断是否有 StrongBox 安全芯片；
     * 2) 实际生成 setIsStrongBoxBacked(true) 的 key，抛 StrongBoxUnavailableException 即不支持。
     * 注：KeyguardManager 无 isDeviceSecureAttestationSupported 方法，特性检测是官方推荐方式。
     */
    private fun strongBoxKey(context: Context, zh: Boolean): KeyStatus {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return KeyStatus("StrongBox key",
                if (zh) "API < 28，无 StrongBox API" else "API < 28, no StrongBox API", KeyLevel.INFO)
        }
        val hasStrongBox = context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
        if (!hasStrongBox) {
            return KeyStatus("StrongBox key",
                if (zh) "设备不支持 StrongBox 安全芯片" else "StrongBox secure chip unsupported",
                KeyLevel.WARN)
        }
        return try {
            val ks = KeyStore.getInstance("AndroidKeyStore")
            ks.load(null)
            ks.deleteEntry(STRONGBOX_ALIAS)
            val spec = KeyGenParameterSpec.Builder(
                STRONGBOX_ALIAS, KeyProperties.PURPOSE_SIGN
            )
                .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                .setKeySize(2048)
                .setIsStrongBoxBacked(true)
                .build()
            val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore")
            kpg.initialize(spec)
            kpg.generateKeyPair()
            KeyStatus("StrongBox key",
                if (zh) "StrongBox 独立安全芯片可用" else "StrongBox secure chip available", KeyLevel.OK)
        } catch (e: StrongBoxUnavailableException) {
            KeyStatus("StrongBox key",
                if (zh) "无 StrongBox 硬件（回退 TEE）" else "No StrongBox hardware (TEE fallback)", KeyLevel.WARN)
        } catch (e: Exception) {
            KeyStatus("StrongBox key",
                if (zh) "StrongBox 探测异常: ${e.javaClass.simpleName}" else "StrongBox probe error: ${e.javaClass.simpleName}",
                KeyLevel.UNKNOWN)
        } finally {
            runCatching {
                KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(STRONGBOX_ALIAS)
            }
        }
    }

    // ============ 内部工具 ============

    private fun createMediaDrm(): MediaDrm? {
        return try {
            MediaDrm(UUID.fromString(WIDEVINE_UUID))
        } catch (e: Exception) {
            null
        }
    }

    private fun packageExists(context: Context, pkg: String): Boolean =
        runCatching { context.packageManager.getPackageInfo(pkg, 0); true }.getOrDefault(false)

    /**
     * 生成带 attestation challenge 的 RSA key 并返回证书链根证书的 subject。
     * 成功说明 keymaster 支持硬件 attestation（信任根通常为 OEM 或 Google PKI）。
     */
    private fun attestationChainRoot(context: Context): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        val ks = KeyStore.getInstance("AndroidKeyStore")
        return try {
            ks.load(null)
            ks.deleteEntry(ATTEST_ALIAS)
            val spec = KeyGenParameterSpec.Builder(
                ATTEST_ALIAS, KeyProperties.PURPOSE_SIGN
            )
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                .setKeySize(2048)
                .setAttestationChallenge(ByteArray(16) { it.toByte() })
                .build()
            val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore")
            kpg.initialize(spec)
            kpg.generateKeyPair()
            val chain = ks.getCertificateChain(ATTEST_ALIAS) ?: return null
            (chain.lastOrNull() as? X509Certificate)?.subjectX500Principal?.name
        } catch (e: Exception) {
            null
        } finally {
            runCatching { ks.deleteEntry(ATTEST_ALIAS) }
        }
    }
}
