package com.example.util

import com.example.data.entity.ComparisonItem
import java.text.DecimalFormat

object UnitCalculator {

    private val priceFormatter = DecimalFormat("#,##0.00")
    private val multiplierFormatter = DecimalFormat("0.0")

    fun getMultiplier(unit: String): Double {
        return when (unit.lowercase().trim()) {
            "kg", "l" -> 1000.0
            "lb" -> 453.59237
            "oz" -> 28.349523
            "fl.oz.", "fl. oz.", "fl oz" -> 29.5735
            "gal" -> 3785.41178
            else -> 1.0 // g, ml, 개
        }
    }

    data class ComparisonResult(
        val item: ComparisonItem,
        val isValid: Boolean,
        val totalQuantity: Double, // total quantity in normalized base (g, ml, 개)
        val pricePerBaseUnit: Double, // KRW per 1g/1ml/1개
        val referencePrice: Double, // KRW per reference value (e.g., 100g)
        var rank: Int = -1,
        var isBest: Boolean = false,
        var priceDifferenceText: String = "",
        var percentageDifferenceText: String = "",
        var multiplierText: String = ""
    )

    fun calculateResults(
        items: List<ComparisonItem>,
        referenceValue: Double,
        referenceUnit: String
    ): List<ComparisonResult> {
        val refMultiplier = getMultiplier(referenceUnit)
        val normalizedReferenceBase = referenceValue * refMultiplier

        // Calculate basic conversion and base unit prices
        val results = items.map { item ->
            val itemMultiplier = getMultiplier(item.unit)
            val totalQuantity = item.capacity * item.bundleCount * item.multiplierForCustomUnitCheck(itemMultiplier)
            val isValid = item.price > 0 && totalQuantity > 0

            val pricePerBaseUnit = if (isValid) item.price / totalQuantity else Double.MAX_VALUE
            val referencePrice = if (isValid) pricePerBaseUnit * normalizedReferenceBase else 0.0

            ComparisonResult(
                item = item,
                isValid = isValid,
                totalQuantity = totalQuantity,
                pricePerBaseUnit = pricePerBaseUnit,
                referencePrice = referencePrice
            )
        }

        // Filter valid results for ranking
        val validResults = results.filter { it.isValid }.sortedBy { it.pricePerBaseUnit }

        // Assign ranks and calculate differences relative to the 1st place (Best)
        if (validResults.isNotEmpty()) {
            val best = validResults.first()
            validResults.forEachIndexed { index, result ->
                result.rank = index + 1
                result.isBest = index == 0

                if (index > 0) {
                    val diffVal = result.referencePrice - best.referencePrice
                    val diffPercent = if (best.referencePrice > 0) (diffVal / best.referencePrice) else 0.0
                    val multiplier = if (best.referencePrice > 0) (result.referencePrice / best.referencePrice) else 1.0

                    val language = java.util.Locale.getDefault().language
                    val formatter = if (language == "ko" || language == "ja") {
                        DecimalFormat("#,##0")
                    } else {
                        DecimalFormat("#,##0.00")
                    }
                    val prefix = when (language) {
                        "ko" -> "+"
                        "ja" -> "+"
                        else -> "+$"
                    }
                    val suffixPrice = when (language) {
                        "ko" -> "원"
                        "ja" -> "円"
                        else -> ""
                    }
                    val suffixMultiplier = when (language) {
                        "ko" -> "배"
                        "ja" -> "倍"
                        else -> "x"
                    }

                    result.priceDifferenceText = "$prefix${formatter.format(diffVal)}$suffixPrice"
                    result.percentageDifferenceText = "+${(diffPercent * 100).toInt()}%"
                    result.multiplierText = "${multiplierFormatter.format(multiplier)}$suffixMultiplier"
                }
            }
        }

        // Map ranked results back to the original items order
        return results.map { rawResult ->
            if (rawResult.isValid) {
                validResults.find { it.item.id == rawResult.item.id } ?: rawResult
            } else {
                rawResult
            }
        }
    }

    // Extension helper for safe capacity logic
    private fun ComparisonItem.multiplierForCustomUnitCheck(baseMultiplier: Double): Double {
        return baseMultiplier
    }

    fun formatPrice(amount: Double): String {
        val language = java.util.Locale.getDefault().language
        val formatter = if (language == "ko" || language == "ja") {
            DecimalFormat("#,##0")
        } else {
            DecimalFormat("#,##0.00")
        }
        return formatter.format(amount)
    }
}
