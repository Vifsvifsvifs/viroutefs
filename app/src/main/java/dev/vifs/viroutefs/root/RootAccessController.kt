// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.root

import android.content.Context
import android.content.Intent

enum class RootManager(
    val displayName: String,
    val packageNames: List<String>,
) {
    KernelSuNext("KernelSU Next", listOf("com.rifsxd.ksunext")),
    KernelSu("KernelSU", listOf("me.weishu.kernelsu")),
    Magisk("Magisk", listOf("com.topjohnwu.magisk")),
    APatch("APatch", listOf("me.bmax.apatch")),
    Unknown("Неизвестный root-менеджер", emptyList()),
}

class RootAccessController(context: Context) {
    private val appContext = context.applicationContext
    private val executor = RootCommandExecutor()

    fun detectedManager(): RootManager? = RootManager.entries
        .filterNot { it == RootManager.Unknown }
        .firstOrNull { manager ->
            manager.packageNames.any { packageName ->
                runCatching { appContext.packageManager.getPackageInfo(packageName, 0) }.isSuccess
            }
        }

    fun managerLaunchIntent(): Intent? = detectedManager()
        ?.packageNames
        ?.firstNotNullOfOrNull(appContext.packageManager::getLaunchIntentForPackage)
        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * The only side effect is invoking `su`, which may show the root manager's
     * user-controlled permission prompt. The probe itself is read-only.
     */
    fun requestAndProbe(timeoutMillis: Long = ROOT_REQUEST_TIMEOUT_MILLIS): RootProbeOutcome {
        val result = executor.execute(ROOT_CAPABILITY_PROBE_SCRIPT, timeoutMillis)
        if (!result.suCommandVisible) {
            return RootProbeOutcome(
                message = "Команда su пока недоступна этому приложению. Разрешите ViRouteFS в root-менеджере и повторите проверку.",
                suCommandVisible = false,
            )
        }
        if (!result.completed) {
            return RootProbeOutcome(
                message = "Root-менеджер не завершил запрос. Проверьте его окно и повторите попытку.",
                suCommandVisible = true,
            )
        }
        val snapshot = runCatching { parseRootCapabilityProbe(result.output) }.getOrNull()
        if (result.exitCode != 0 || snapshot?.rootGranted != true) {
            return RootProbeOutcome(
                snapshot = snapshot,
                message = "Root-доступ не выдан. Базовые VPN, маршруты и DNS продолжают работать без него.",
                suCommandVisible = true,
            )
        }
        return RootProbeOutcome(
            snapshot = snapshot,
            message = "Root-доступ подтверждён. Пока выполнена только безопасная проверка возможностей; сетевые правила не менялись.",
            suCommandVisible = true,
        )
    }
}

private const val ROOT_REQUEST_TIMEOUT_MILLIS = 45_000L

private val ROOT_CAPABILITY_PROBE_SCRIPT = """
    has_cmd() { command -v "${'$'}1" >/dev/null 2>&1 && printf 1 || printf 0; }
    printf 'uid='; id -u 2>/dev/null || printf unknown; printf '\n'
    printf 'identity='; id 2>/dev/null | tr '\n' ' '; printf '\n'
    printf 'selinux='; (getenforce 2>/dev/null || printf unknown) | tr '\n' ' '; printf '\n'
    printf 'kernel='; uname -r 2>/dev/null | tr '\n' ' '; printf '\n'
    printf 'cap_eff='; awk '/^CapEff:/{print ${'$'}2}' /proc/self/status 2>/dev/null | tr '\n' ' '; printf '\n'
    printf 'ip='; has_cmd ip; printf '\n'
    printf 'iptables='; has_cmd iptables; printf '\n'
    printf 'ip6tables='; has_cmd ip6tables; printf '\n'
    printf 'nft='; has_cmd nft; printf '\n'
    printf 'tcpdump='; has_cmd tcpdump; printf '\n'
    printf 'tc='; has_cmd tc; printf '\n'
    printf 'conntrack='; has_cmd conntrack; printf '\n'
    printf 'wireguard_kernel='; if [ -d /sys/module/wireguard ]; then printf 1; else printf 0; fi; printf '\n'
    printf 'nfqueue='; if command -v iptables >/dev/null 2>&1 && iptables -j NFQUEUE -h 2>&1 | grep -q -- '--queue-bypass'; then printf 1; else printf 0; fi; printf '\n'
""".trimIndent()
