package com.rochias.peroxyde.core_db

import java.io.File

const val STORAGE_SCHEMA_VERSION: String = "storage/v1"

enum class ComplianceStatus {
    ALERT_LOW,
    COMPLIANT,
    ALERT_HIGH,
}

data class AnalysisRecord(
    val localId: String,
    val capturedAtEpochMs: Long,
    val ppm: Int,
    val complianceStatus: ComplianceStatus,
    val imagePath: String,
    val captureRulesVersion: String,
    val analysisRulesVersion: String,
)

data class SyncQueueItem(
    val localId: String,
    val idempotencyKey: String,
    val queuedAtEpochMs: Long,
    val attemptCount: Int,
    val lastError: String?,
)

interface AnalysisLocalStore {
    fun saveValidatedAnalysis(record: AnalysisRecord)
    fun getById(localId: String): AnalysisRecord?
    fun listAnalyses(): List<AnalysisRecord>

    fun enqueueForSync(localId: String, nowEpochMs: Long)
    fun listPendingQueue(): List<SyncQueueItem>
    fun markRetry(localId: String, error: String)
    fun acknowledge(localId: String)
}

class FileAnalysisLocalStore(private val rootDir: File) : AnalysisLocalStore {
    private val analysesFile = File(rootDir, "analyses.tsv")
    private val queueFile = File(rootDir, "sync_queue.tsv")

    init {
        rootDir.mkdirs()
        if (!analysesFile.exists()) analysesFile.createNewFile()
        if (!queueFile.exists()) queueFile.createNewFile()
    }

    override fun saveValidatedAnalysis(record: AnalysisRecord) {
        if (getById(record.localId) != null) return
        analysesFile.appendText(record.toTsv() + "\n")
    }

    override fun getById(localId: String): AnalysisRecord? = listAnalyses().firstOrNull { it.localId == localId }

    override fun listAnalyses(): List<AnalysisRecord> =
        analysesFile.readLines()
            .filter { it.isNotBlank() }
            .map(::analysisFromTsv)

    override fun enqueueForSync(localId: String, nowEpochMs: Long) {
        if (listPendingQueue().any { it.localId == localId }) return
        queueFile.appendText(
            listOf(localId, localId, nowEpochMs.toString(), "0", "").joinToString("\t") + "\n",
        )
    }

    override fun listPendingQueue(): List<SyncQueueItem> =
        queueFile.readLines()
            .filter { it.isNotBlank() }
            .map(::queueFromTsv)

    override fun markRetry(localId: String, error: String) {
        val updated = listPendingQueue().map { item ->
            if (item.localId == localId) item.copy(attemptCount = item.attemptCount + 1, lastError = error) else item
        }
        writeQueue(updated)
    }

    override fun acknowledge(localId: String) {
        val remaining = listPendingQueue().filterNot { it.localId == localId }
        writeQueue(remaining)
    }

    private fun writeQueue(items: List<SyncQueueItem>) {
        queueFile.writeText(items.joinToString(separator = "\n") { it.toTsv() } + if (items.isNotEmpty()) "\n" else "")
    }
}

object CoreDbModule {
    fun createFileStore(rootDir: File): AnalysisLocalStore = FileAnalysisLocalStore(rootDir)
}

private fun AnalysisRecord.toTsv(): String = listOf(
    localId,
    capturedAtEpochMs.toString(),
    ppm.toString(),
    complianceStatus.name,
    imagePath,
    captureRulesVersion,
    analysisRulesVersion,
).joinToString("\t")

private fun analysisFromTsv(line: String): AnalysisRecord {
    val cols = line.split("\t")
    require(cols.size >= 7) { "Invalid analysis line: $line" }
    return AnalysisRecord(
        localId = cols[0],
        capturedAtEpochMs = cols[1].toLong(),
        ppm = cols[2].toInt(),
        complianceStatus = ComplianceStatus.valueOf(cols[3]),
        imagePath = cols[4],
        captureRulesVersion = cols[5],
        analysisRulesVersion = cols[6],
    )
}

private fun SyncQueueItem.toTsv(): String = listOf(
    localId,
    idempotencyKey,
    queuedAtEpochMs.toString(),
    attemptCount.toString(),
    lastError ?: "",
).joinToString("\t")

private fun queueFromTsv(line: String): SyncQueueItem {
    val cols = line.split("\t")
    require(cols.size >= 5) { "Invalid queue line: $line" }
    return SyncQueueItem(
        localId = cols[0],
        idempotencyKey = cols[1],
        queuedAtEpochMs = cols[2].toLong(),
        attemptCount = cols[3].toInt(),
        lastError = cols[4].ifBlank { null },
    )
}
