package dev.vifs.viroutefs.route

import dev.vifs.viroutefs.diagnostics.DiagnosticResult

data class RouteDiagnosticStep(
    val title: String,
    val result: DiagnosticResult?,
    val skippedReason: String? = null,
) {
    val wasRun: Boolean = result != null
}
