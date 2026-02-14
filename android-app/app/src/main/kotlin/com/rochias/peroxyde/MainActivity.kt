package com.rochias.peroxyde

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.rochias.peroxyde.core_camera.CaptureInput
import com.rochias.peroxyde.core_db.AnalysisLocalStore
import com.rochias.peroxyde.core_db.ComplianceStatus
import com.rochias.peroxyde.core_db.CoreDbModule
import com.rochias.peroxyde.core_sync.SyncApi
import com.rochias.peroxyde.core_sync.SyncEngine
import com.rochias.peroxyde.core_sync.SyncPayload
import com.rochias.peroxyde.core_sync.SyncResult
import com.rochias.peroxyde.feature_test.OperatorCaptureRequest
import com.rochias.peroxyde.feature_test.TestFlowResult
import com.rochias.peroxyde.feature_test.TestWorkflowService
import java.io.File

private enum class AppRoute {
    ACCUEIL,
    TEST,
    HISTORIQUE,
    AIDE,
}

class MainActivity : ComponentActivity() {

    private val localStore: AnalysisLocalStore by lazy {
        CoreDbModule.createFileStore(File(filesDir, "analyses"))
    }

    private val workflowService: TestWorkflowService by lazy {
        TestWorkflowService(
            store = localStore,
            syncEngine = SyncEngine(localStore, OfflineFirstSyncApi()),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PeroxydeApp(
                        workflowService = workflowService,
                        localStore = localStore,
                    )
                }
            }
        }
    }
}

@Composable
private fun PeroxydeApp(
    workflowService: TestWorkflowService,
    localStore: AnalysisLocalStore,
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = AppRoute.ACCUEIL.name,
    ) {
        composable(route = AppRoute.ACCUEIL.name) {
            AccueilScreen(navController)
        }
        composable(route = AppRoute.TEST.name) {
            TestScreen(navController = navController, workflowService = workflowService)
        }
        composable(route = AppRoute.HISTORIQUE.name) {
            HistoriqueScreen(navController = navController, localStore = localStore)
        }
        composable(route = AppRoute.AIDE.name) {
            AideScreen(navController)
        }
    }
}

@Composable
private fun AccueilScreen(navController: NavHostController) {
    NavigationScaffold(
        title = "Rochias Peroxyde Tester",
        subtitle = "Accueil opérateur",
        navController = navController,
    )
}

@Composable
private fun TestScreen(
    navController: NavHostController,
    workflowService: TestWorkflowService,
) {
    var resultMessage by remember { mutableStateOf("Lancer une analyse pour vérifier la conformité.") }

    NavigationScaffold(
        title = "Effectuer un test",
        subtitle = resultMessage,
        navController = navController,
        extraContent = {
            Button(
                onClick = {
                    val request = OperatorCaptureRequest(
                        imagePath = "captures/operateur-${System.currentTimeMillis()}.jpg",
                        captureInput = CaptureInput(
                            distanceCm = 18.0,
                            angleDegrees = 4.0,
                            luminance = 130.0,
                            blurScore = 0.2,
                            saturationRatio = 0.1,
                        ),
                        capturedAtEpochMs = System.currentTimeMillis(),
                    )

                    resultMessage = when (val flowResult = workflowService.runOperatorFlow(request)) {
                        is TestFlowResult.Rejected -> {
                            "Capture refusée : ${flowResult.capture.operatorMessages.joinToString()}"
                        }

                        is TestFlowResult.AnalysisRejected -> {
                            "${flowResult.operatorMessage} (${flowResult.reasons.joinToString()})"
                        }

                        is TestFlowResult.Completed -> {
                            val decision = when (flowResult.record.complianceStatus) {
                                ComplianceStatus.ALERT_LOW -> "ATTENTION TAUX BAS"
                                ComplianceStatus.COMPLIANT -> "CONFORME POUR LA PRODUCTION"
                                ComplianceStatus.ALERT_HIGH -> "ALERTE SEUIL DÉPASSÉ"
                            }
                            "Taux estimé = ${flowResult.record.ppm} PPM — $decision"
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Analyser")
            }
        },
    )
}

@Composable
private fun HistoriqueScreen(
    navController: NavHostController,
    localStore: AnalysisLocalStore,
) {
    val records = remember { localStore.listAnalyses() }
    val lastRecord = records.maxByOrNull { it.capturedAtEpochMs }

    val subtitle = if (lastRecord == null) {
        "Aucun scan historisé pour le moment."
    } else {
        "Dernier scan : ${lastRecord.ppm} PPM (${lastRecord.complianceStatus.name})"
    }

    NavigationScaffold(
        title = "Historique",
        subtitle = subtitle,
        navController = navController,
    )
}

@Composable
private fun AideScreen(navController: NavHostController) {
    NavigationScaffold(
        title = "Aide",
        subtitle = "Vérifier cadrage, luminosité et netteté avant l'analyse.",
        navController = navController,
    )
}

@Composable
private fun NavigationScaffold(
    title: String,
    subtitle: String,
    navController: NavHostController,
    extraContent: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.headlineSmall)
        Text(text = subtitle, style = MaterialTheme.typography.bodyLarge)

        Button(
            onClick = { navController.navigate(AppRoute.ACCUEIL.name) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Accueil")
        }
        Button(
            onClick = { navController.navigate(AppRoute.TEST.name) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Test")
        }
        Button(
            onClick = { navController.navigate(AppRoute.HISTORIQUE.name) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Historique")
        }
        Button(
            onClick = { navController.navigate(AppRoute.AIDE.name) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Aide")
        }

        extraContent?.invoke()
    }
}

private class OfflineFirstSyncApi : SyncApi {
    override fun pushAnalysis(payload: SyncPayload): SyncResult {
        return SyncResult.RetryableFailure("Serveur indisponible, synchronisation différée.")
    }
}
