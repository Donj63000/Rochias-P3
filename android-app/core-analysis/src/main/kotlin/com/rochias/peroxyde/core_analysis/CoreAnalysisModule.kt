package com.rochias.peroxyde.core_analysis

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

const val ANALYSIS_RULES_VERSION: String = "analysis-rules/v2"

private const val DEFAULT_SATURATION_CHANNEL_THRESHOLD = 250
private const val DEFAULT_UNDEREXPOSED_CHANNEL_THRESHOLD = 12
private const val DEFAULT_MIN_VALID_PIXEL_RATIO = 0.45
private const val DEFAULT_MAX_SATURATED_RATIO = 0.30
private const val DEFAULT_MAX_UNDEREXPOSED_RATIO = 0.30
private const val DEFAULT_MIN_LUMINANCE = 25.0
private const val DEFAULT_MAX_LUMINANCE = 240.0
private const val DEFAULT_MIN_SHARPNESS = 10.0
private const val DEFAULT_CONFIDENCE_WARNING_THRESHOLD = 0.55
private const val DEFAULT_CONFIDENCE_PASS_THRESHOLD = 0.75

data class VersionedAnalysisDecision(
    val contractVersion: String,
    val analysisResult: String,
    val complianceStatus: String,
    val recommendedAction: String,
)

data class RgbPixel(
    val r: Int,
    val g: Int,
    val b: Int,
)

data class ImageFrame(
    val width: Int,
    val height: Int,
    val pixels: List<RgbPixel>,
) {
    init {
        require(width > 0 && height > 0) { "Image dimensions must be positive." }
        require(pixels.size == width * height) { "Pixel count must be width * height." }
    }

    fun pixelAt(x: Int, y: Int): RgbPixel = pixels[y * width + x]
}

data class Roi(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
) {
    fun area(): Int = width * height
}

data class AnalysisPatch(
    val ppm: Double,
    val lab: LabColor,
)

enum class QualityStatus {
    ACCEPTED,
    WARNING,
    REJECTED,
}

data class CaptureQuality(
    val status: QualityStatus,
    val sharpnessScore: Double,
    val saturationRatio: Double,
    val underExposedRatio: Double,
    val meanLuminance: Double,
    val scaleDetected: Boolean,
    val reasons: List<String>,
)

data class ColorSampleLab(
    val lab: LabColor,
    val keptPixels: Int,
    val rejectedPixels: Int,
)

data class LabColor(
    val l: Double,
    val a: Double,
    val b: Double,
)

data class PpmEstimate(
    val ppmEstimate: Double,
    val ppmMin: Double,
    val ppmMax: Double,
    val deltaE76ToLower: Double,
    val deltaE76ToUpper: Double,
    val deltaE00ToLower: Double,
    val deltaE00ToUpper: Double,
)

data class AnalysisConfidence(
    val score: Double,
    val level: QualityStatus,
    val notes: List<String>,
)

data class AnalysisOutcome(
    val quality: CaptureQuality,
    val colorSample: ColorSampleLab?,
    val ppmEstimate: PpmEstimate?,
    val confidence: AnalysisConfidence,
    val decision: VersionedAnalysisDecision?,
)

object CoreAnalysisModule {

    fun evaluatePpm(ppm: Int): VersionedAnalysisDecision {
        return when {
            ppm < 100 -> VersionedAnalysisDecision(
                contractVersion = ANALYSIS_RULES_VERSION,
                analysisResult = "ATTENTION TAUX BAS",
                complianceStatus = "MAINTENANCE_QUALITE",
                recommendedAction = "Appliquer les consignes maintenance/qualité.",
            )

            ppm <= 500 -> VersionedAnalysisDecision(
                contractVersion = ANALYSIS_RULES_VERSION,
                analysisResult = "CONFORME POUR LA PRODUCTION",
                complianceStatus = "CONFORME",
                recommendedAction = "Poursuivre la production normale.",
            )

            else -> VersionedAnalysisDecision(
                contractVersion = ANALYSIS_RULES_VERSION,
                analysisResult = "ALERTE SEUIL DÉPASSÉ — PRODUCTION NON CONFORME",
                complianceStatus = "SEUIL_DEPASSE",
                recommendedAction = "Arrêt/notification/recontrôle immédiats.",
            )
        }
    }

    fun analyzeStrip(
        frame: ImageFrame,
        roi: Roi,
        referenceScale: List<AnalysisPatch>,
    ): AnalysisOutcome {
        val quality = classifyCaptureQuality(frame, roi)
        if (quality.status == QualityStatus.REJECTED) {
            return AnalysisOutcome(
                quality = quality,
                colorSample = null,
                ppmEstimate = null,
                confidence = AnalysisConfidence(0.0, QualityStatus.REJECTED, quality.reasons),
                decision = null,
            )
        }

        val sample = sampleLab(frame, roi)
        val validRatio = sample.keptPixels.toDouble() / roi.area().toDouble()
        if (validRatio < DEFAULT_MIN_VALID_PIXEL_RATIO) {
            val notes = quality.reasons + "Trop peu de pixels exploitables dans la ROI."
            return AnalysisOutcome(
                quality = quality.copy(status = QualityStatus.REJECTED, reasons = notes),
                colorSample = sample,
                ppmEstimate = null,
                confidence = AnalysisConfidence(0.0, QualityStatus.REJECTED, notes),
                decision = null,
            )
        }

        val estimate = estimatePpm(sample.lab, referenceScale)
        val confidence = buildConfidence(quality, estimate)
        val decision = evaluatePpm(estimate.ppmEstimate.roundToInt())

        return AnalysisOutcome(
            quality = quality,
            colorSample = sample,
            ppmEstimate = estimate,
            confidence = confidence,
            decision = decision,
        )
    }

    fun classifyCaptureQuality(frame: ImageFrame, roi: Roi): CaptureQuality {
        val reasons = mutableListOf<String>()
        val scaleDetected = detectScale(frame, roi)
        if (!scaleDetected) reasons += "Échelle colorimétrique non détectée."

        val roiPixels = extractRoi(frame, roi)
        val saturationRatio = roiPixels.count { isSaturated(it) }.toDouble() / roiPixels.size.toDouble()
        val underRatio = roiPixels.count { isUnderExposed(it) }.toDouble() / roiPixels.size.toDouble()
        val meanLuminance = roiPixels.map { relativeLuminance(it) }.average()
        val sharpness = estimateSharpness(frame, roi)

        if (saturationRatio > DEFAULT_MAX_SATURATED_RATIO) reasons += "Saturation excessive de la capture."
        if (underRatio > DEFAULT_MAX_UNDEREXPOSED_RATIO) reasons += "Sous-exposition excessive de la capture."
        if (meanLuminance < DEFAULT_MIN_LUMINANCE || meanLuminance > DEFAULT_MAX_LUMINANCE) {
            reasons += "Luminance hors plage opérationnelle."
        }
        if (sharpness < DEFAULT_MIN_SHARPNESS) reasons += "Flou excessif détecté."

        val status = when {
            reasons.any {
                it.contains("non détectée") || it.contains("excessive") || it.contains("hors plage") || it.contains("Flou")
            } -> QualityStatus.REJECTED
            reasons.isNotEmpty() -> QualityStatus.WARNING
            else -> QualityStatus.ACCEPTED
        }

        return CaptureQuality(
            status = status,
            sharpnessScore = sharpness,
            saturationRatio = saturationRatio,
            underExposedRatio = underRatio,
            meanLuminance = meanLuminance,
            scaleDetected = scaleDetected,
            reasons = reasons,
        )
    }

    fun sampleLab(frame: ImageFrame, roi: Roi): ColorSampleLab {
        val roiPixels = extractRoi(frame, roi)
        val kept = roiPixels.filterNot { isSaturated(it) || isUnderExposed(it) }
        val rejected = roiPixels.size - kept.size

        val avg = if (kept.isEmpty()) RgbPixel(0, 0, 0) else RgbPixel(
            r = kept.map { it.r }.average().roundToInt(),
            g = kept.map { it.g }.average().roundToInt(),
            b = kept.map { it.b }.average().roundToInt(),
        )

        return ColorSampleLab(
            lab = srgbToLab(avg),
            keptPixels = kept.size,
            rejectedPixels = rejected,
        )
    }

    fun estimatePpm(sampleLab: LabColor, referenceScale: List<AnalysisPatch>): PpmEstimate {
        require(referenceScale.size >= 2) { "At least two reference patches are required." }

        val sorted = referenceScale
            .map { patch ->
                val d76 = deltaE76(sampleLab, patch.lab)
                val d00 = deltaE00(sampleLab, patch.lab)
                Triple(patch, d76, d00)
            }
            .sortedBy { it.third }

        val lower = sorted[0]
        val upper = sorted[1]

        val total = lower.third + upper.third
        val t = if (total == 0.0) 0.5 else (lower.third / total).coerceIn(0.0, 1.0)

        val ppm = lower.first.ppm + t * (upper.first.ppm - lower.first.ppm)
        val ppmMin = minOf(lower.first.ppm, upper.first.ppm)
        val ppmMax = maxOf(lower.first.ppm, upper.first.ppm)

        return PpmEstimate(
            ppmEstimate = ppm,
            ppmMin = ppmMin,
            ppmMax = ppmMax,
            deltaE76ToLower = lower.second,
            deltaE76ToUpper = upper.second,
            deltaE00ToLower = lower.third,
            deltaE00ToUpper = upper.third,
        )
    }

    fun srgbToLab(pixel: RgbPixel): LabColor {
        val r = srgbToLinear(pixel.r / 255.0)
        val g = srgbToLinear(pixel.g / 255.0)
        val b = srgbToLinear(pixel.b / 255.0)

        val x = (0.4124564 * r + 0.3575761 * g + 0.1804375 * b) / 0.95047
        val y = (0.2126729 * r + 0.7151522 * g + 0.0721750 * b) / 1.00000
        val z = (0.0193339 * r + 0.1191920 * g + 0.9503041 * b) / 1.08883

        val fx = labF(x)
        val fy = labF(y)
        val fz = labF(z)

        return LabColor(
            l = 116.0 * fy - 16.0,
            a = 500.0 * (fx - fy),
            b = 200.0 * (fy - fz),
        )
    }

    fun deltaE76(first: LabColor, second: LabColor): Double {
        return sqrt(
            (first.l - second.l).pow(2) +
                (first.a - second.a).pow(2) +
                (first.b - second.b).pow(2),
        )
    }

    fun deltaE00(first: LabColor, second: LabColor): Double {
        val lBar = (first.l + second.l) / 2.0
        val c1 = sqrt(first.a.pow(2) + first.b.pow(2))
        val c2 = sqrt(second.a.pow(2) + second.b.pow(2))
        val cBar = (c1 + c2) / 2.0

        val g = 0.5 * (1 - sqrt((cBar.pow(7)) / (cBar.pow(7) + 25.0.pow(7))))
        val a1Prime = (1 + g) * first.a
        val a2Prime = (1 + g) * second.a
        val c1Prime = sqrt(a1Prime.pow(2) + first.b.pow(2))
        val c2Prime = sqrt(a2Prime.pow(2) + second.b.pow(2))

        val h1Prime = hueRadians(a1Prime, first.b)
        val h2Prime = hueRadians(a2Prime, second.b)

        val deltaLPrime = second.l - first.l
        val deltaCPrime = c2Prime - c1Prime
        val deltaHPrime = deltaHuePrime(h1Prime, h2Prime, c1Prime, c2Prime)

        val sL = 1 + (0.015 * (lBar - 50).pow(2)) / sqrt(20 + (lBar - 50).pow(2))
        val cBarPrime = (c1Prime + c2Prime) / 2.0
        val hBarPrime = averageHuePrime(h1Prime, h2Prime, c1Prime, c2Prime)
        val t = 1 - 0.17 * cos(hBarPrime - PI / 6) + 0.24 * cos(2 * hBarPrime) +
            0.32 * cos(3 * hBarPrime + PI / 30) - 0.20 * cos(4 * hBarPrime - 63.0 * PI / 180)
        val sC = 1 + 0.045 * cBarPrime
        val sH = 1 + 0.015 * cBarPrime * t

        val deltaTheta = 30.0 * PI / 180 * exp(-((hBarPrime * 180 / PI - 275) / 25.0).pow(2))
        val rC = 2 * sqrt(cBarPrime.pow(7) / (cBarPrime.pow(7) + 25.0.pow(7)))
        val rT = -rC * sin(2 * deltaTheta)

        val lTerm = deltaLPrime / sL
        val cTerm = deltaCPrime / sC
        val hTerm = deltaHPrime / sH

        return sqrt(lTerm.pow(2) + cTerm.pow(2) + hTerm.pow(2) + rT * cTerm * hTerm)
    }

    private fun buildConfidence(quality: CaptureQuality, estimate: PpmEstimate): AnalysisConfidence {
        val uncertainty = (estimate.deltaE00ToLower + estimate.deltaE00ToUpper)
        val qualityPenalty = quality.reasons.size * 0.15
        val uncertaintyPenalty = (uncertainty / 50.0).coerceIn(0.0, 0.8)
        val score = (1.0 - qualityPenalty - uncertaintyPenalty).coerceIn(0.0, 1.0)

        val level = when {
            score >= DEFAULT_CONFIDENCE_PASS_THRESHOLD -> QualityStatus.ACCEPTED
            score >= DEFAULT_CONFIDENCE_WARNING_THRESHOLD -> QualityStatus.WARNING
            else -> QualityStatus.REJECTED
        }

        val notes = buildList {
            addAll(quality.reasons)
            if (uncertainty > 20.0) add("Couleur ambiguë entre patchs adjacents.")
        }

        return AnalysisConfidence(score = score, level = level, notes = notes)
    }

    private fun detectScale(frame: ImageFrame, roi: Roi): Boolean {
        if (!isRoiValid(frame, roi)) return false
        val margin = minOf(frame.width, frame.height) * 0.02
        return roi.x > margin && roi.y > margin &&
            (roi.x + roi.width) < (frame.width - margin) &&
            (roi.y + roi.height) < (frame.height - margin)
    }

    private fun extractRoi(frame: ImageFrame, roi: Roi): List<RgbPixel> {
        require(isRoiValid(frame, roi)) { "ROI is outside image bounds." }

        val pixels = ArrayList<RgbPixel>(roi.area())
        for (y in roi.y until (roi.y + roi.height)) {
            for (x in roi.x until (roi.x + roi.width)) {
                pixels += frame.pixelAt(x, y)
            }
        }
        return pixels
    }

    private fun isRoiValid(frame: ImageFrame, roi: Roi): Boolean {
        return roi.x >= 0 && roi.y >= 0 && roi.width > 0 && roi.height > 0 &&
            roi.x + roi.width <= frame.width &&
            roi.y + roi.height <= frame.height
    }

    private fun estimateSharpness(frame: ImageFrame, roi: Roi): Double {
        val xStart = roi.x
        val xEnd = roi.x + roi.width - 1
        val yStart = roi.y
        val yEnd = roi.y + roi.height - 1

        var gradientSum = 0.0
        var count = 0

        for (y in yStart until yEnd) {
            for (x in xStart until xEnd) {
                val current = relativeLuminance(frame.pixelAt(x, y))
                val right = relativeLuminance(frame.pixelAt(x + 1, y))
                val down = relativeLuminance(frame.pixelAt(x, y + 1))
                gradientSum += kotlin.math.abs(right - current) + kotlin.math.abs(down - current)
                count++
            }
        }

        return if (count == 0) 0.0 else gradientSum / count.toDouble()
    }

    private fun isSaturated(pixel: RgbPixel): Boolean {
        return pixel.r >= DEFAULT_SATURATION_CHANNEL_THRESHOLD ||
            pixel.g >= DEFAULT_SATURATION_CHANNEL_THRESHOLD ||
            pixel.b >= DEFAULT_SATURATION_CHANNEL_THRESHOLD
    }

    private fun isUnderExposed(pixel: RgbPixel): Boolean {
        return pixel.r <= DEFAULT_UNDEREXPOSED_CHANNEL_THRESHOLD &&
            pixel.g <= DEFAULT_UNDEREXPOSED_CHANNEL_THRESHOLD &&
            pixel.b <= DEFAULT_UNDEREXPOSED_CHANNEL_THRESHOLD
    }

    private fun relativeLuminance(pixel: RgbPixel): Double {
        return 0.2126 * pixel.r + 0.7152 * pixel.g + 0.0722 * pixel.b
    }

    private fun srgbToLinear(value: Double): Double {
        return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
    }

    private fun labF(value: Double): Double {
        return if (value > 216.0 / 24389.0) value.pow(1.0 / 3.0) else (24389.0 / 27.0 * value + 16.0) / 116.0
    }

    private fun hueRadians(a: Double, b: Double): Double {
        if (a == 0.0 && b == 0.0) return 0.0
        val hue = atan2(b, a)
        return if (hue >= 0) hue else hue + 2 * PI
    }

    private fun deltaHuePrime(h1: Double, h2: Double, c1Prime: Double, c2Prime: Double): Double {
        if (c1Prime == 0.0 || c2Prime == 0.0) return 0.0
        var delta = h2 - h1
        if (delta > PI) delta -= 2 * PI
        if (delta < -PI) delta += 2 * PI
        return 2 * sqrt(c1Prime * c2Prime) * sin(delta / 2)
    }

    private fun averageHuePrime(h1: Double, h2: Double, c1Prime: Double, c2Prime: Double): Double {
        if (c1Prime == 0.0 || c2Prime == 0.0) return h1 + h2
        val absDiff = kotlin.math.abs(h1 - h2)
        return when {
            absDiff <= PI -> (h1 + h2) / 2
            h1 + h2 < 2 * PI -> (h1 + h2 + 2 * PI) / 2
            else -> (h1 + h2 - 2 * PI) / 2
        }
    }
}
