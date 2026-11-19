package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.domain.PersianNumberUtils

/**
 * BiDi-safe, zero-overlap Persian Toman amount renderer.
 *
 * Root cause prevented:
 * Android/Compose `StaticLayout` in RTL layouts can overlap mixed LTR-digit runs and the RTL word
 * "تومان" when combined inside a single constrained `Text(..., maxLines = 1, softWrap = false)`.
 *
 * Solution:
 * Always render the LTR-isolated numeric run and the Persian unit "تومان" as separate sibling
 * `Text` composables inside a `Row` (or stacked `Column` for narrow KPI cards), using zero-allocation
 * [PersianNumberUtils.formatGroupedPersianLong].
 */
@Composable
fun OrosiAmountText(
    amountToman: Long,
    color: Color,
    numberStyle: TextStyle,
    modifier: Modifier = Modifier,
    unitStyle: TextStyle = MaterialTheme.typography.labelSmall,
    unitColor: Color = color,
    isIncome: Boolean = false,
    hideAmount: Boolean = false,
    showUnit: Boolean = true,
    stackedUnit: Boolean = false,
    prefixText: String? = null,
    numberFontWeight: FontWeight? = null,
    unitFontWeight: FontWeight = FontWeight.Medium,
    unitSpacing: Dp = 4.dp,
    stackedAlignment: Alignment.Horizontal = Alignment.Start
) {
    val numberRun = when {
        hideAmount -> "****"
        isIncome && amountToman > 0L -> "\u2066\u200E+${PersianNumberUtils.formatGroupedPersianLong(amountToman)}\u2069"
        else -> "\u2066${PersianNumberUtils.formatGroupedPersianLong(kotlin.math.abs(amountToman))}\u2069"
    }

    if (stackedUnit && showUnit) {
        Column(
            modifier = modifier,
            horizontalAlignment = stackedAlignment,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (!prefixText.isNullOrBlank()) {
                Text(
                    text = prefixText,
                    style = unitStyle,
                    fontWeight = unitFontWeight,
                    color = unitColor,
                    maxLines = 1
                )
            }
            Text(
                text = numberRun,
                style = numberStyle,
                fontWeight = numberFontWeight ?: numberStyle.fontWeight,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "تومان",
                style = unitStyle,
                fontWeight = unitFontWeight,
                color = unitColor,
                maxLines = 1
            )
        }
    } else {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(unitSpacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!prefixText.isNullOrBlank()) {
                Text(
                    text = prefixText,
                    style = unitStyle,
                    fontWeight = unitFontWeight,
                    color = unitColor,
                    maxLines = 1
                )
            }
            Text(
                text = numberRun,
                style = numberStyle,
                fontWeight = numberFontWeight ?: numberStyle.fontWeight,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (showUnit) {
                Text(
                    text = "تومان",
                    style = unitStyle,
                    fontWeight = unitFontWeight,
                    color = unitColor,
                    maxLines = 1
                )
            }
        }
    }
}
