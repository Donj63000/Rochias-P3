package com.rochias.peroxyde.core_sync

import com.rochias.peroxyde.core_db.AnalysisLocalStore
import com.rochias.peroxyde.core_db.AnalysisRecord

sealed class SyncResult {
    data class Ack(val remoteReceiptId: String) : SyncResult()
    data class RetryableFailure(val reason: String) : SyncResult()
}

data class SyncPayload(
    val localId: String,
    val idempotencyKey: String,
    val analysis: AnalysisRecord,
)

interface SyncApi {
    fun pushAnalysis(payload: SyncPayload): SyncResult
}

data class SyncRunStats(
    val sent: Int,
    val acked: Int,
    val failed: Int,
)

class SyncEngine(
    private val store: AnalysisLocalStore,
    private val api: SyncApi,
) {
    fun runOnce(): SyncRunStats {
        val pending = store.listPendingQueue()
        var sent = 0
        var acked = 0
        var failed = 0

        pending.forEach { item ->
            val record = store.getById(item.localId) ?: run {
                store.acknowledge(item.localId)
                return@forEach
            }
            sent += 1
            when (
                val result = api.pushAnalysis(
                    SyncPayload(
                        localId = item.localId,
                        idempotencyKey = item.idempotencyKey,
                        analysis = record,
                    ),
                )
            ) {
                is SyncResult.Ack -> {
                    store.acknowledge(item.localId)
                    acked += 1
                }

                is SyncResult.RetryableFailure -> {
                    store.markRetry(item.localId, result.reason)
                    failed += 1
                }
            }
        }

        return SyncRunStats(sent = sent, acked = acked, failed = failed)
    }
}

object CoreSyncModule
