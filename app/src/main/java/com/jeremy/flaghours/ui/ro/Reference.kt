package com.jeremy.flaghours.ui.ro

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jeremy.flaghours.data.db.Job
import com.jeremy.flaghours.domain.LaborRange
import com.jeremy.flaghours.domain.RangeFit
import com.jeremy.flaghours.ui.common.hrs
import com.jeremy.flaghours.ui.theme.StatusColors

/** Reference-range badge: green in range, yellow below the low end, blue above the high end. */
@Composable
fun ReferenceBadge(job: Job, decimals: Int, canLookUp: Boolean, busy: Boolean, onLookUp: () -> Unit) {
    val fit = LaborRange.classify(job.flaggedHundredths, job.refLowHundredths, job.refHighHundredths)
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (fit != null) {
            val (color, label) = when (fit) {
                RangeFit.IN_RANGE -> StatusColors.inRange to "In range"
                RangeFit.BELOW -> StatusColors.belowRange to "Below range"
                RangeFit.ABOVE -> StatusColors.aboveRange to "Above range"
            }
            Text(
                label,
                color = if (fit == RangeFit.BELOW) Color.Black else Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.background(color, MaterialTheme.shapes.small).padding(horizontal = 8.dp, vertical = 4.dp),
            )
            val lo = job.refLowHundredths
            val hi = job.refHighHundredths
            val range = if (lo != null && hi != null && lo != hi) "${hrs(lo, decimals)}–${hrs(hi, decimals)} h" else "${hrs(lo ?: hi ?: 0, decimals)} h"
            Text("  Typical $range" + (job.refConfidence?.let { " · $it confidence" } ?: ""), style = MaterialTheme.typography.bodyMedium)
        } else if (canLookUp) {
            TextButton(onClick = onLookUp, enabled = !busy) { Text(if (busy) "Looking up…" else "Look up typical time") }
        }
    }
}
