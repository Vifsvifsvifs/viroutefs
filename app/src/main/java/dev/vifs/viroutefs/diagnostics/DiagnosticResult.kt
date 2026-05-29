package dev.vifs.viroutefs.diagnostics

enum class DiagnosticStatus {
    SUCCESS,
    WARNING,
    ERROR,
}

data class DiagnosticResult(
    val status: DiagnosticStatus,
    val simpleExplanation: String,
    val technicalDetails: String,
    val recommendedAction: String,
    val elapsedMs: Long? = null,
) {
    fun technicalDetailsWithElapsed(): String = buildString {
        append(technicalDetails)
        elapsedMs?.let { append("\nВремя выполнения: ").append(it).append(" мс") }
    }
}
