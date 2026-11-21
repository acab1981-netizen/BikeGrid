package com.example.bike_.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bike_.R
import com.example.bike_.domain.analytics.FitnessAnalytics
import com.example.bike_.ui.theme.LocalBikeUiPalette
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class PmcPeriod(val days: Int?) {
    M1(30), M3(90), M6(180), ALL(null)
}

@Composable
fun FitnessStatsScreen(
    modifier: Modifier = Modifier,
    viewModel: FitnessStatsViewModel = viewModel()
) {
    val palette = LocalBikeUiPalette.current
    val series by viewModel.series.collectAsStateWithLifecycle()
    val finishedCount by viewModel.finishedCount.collectAsStateWithLifecycle()
    var period by rememberSaveable { mutableStateOf(PmcPeriod.M3) }
    var helpExpanded by rememberSaveable { mutableStateOf(false) }
    var selectedIndex by rememberSaveable { mutableStateOf<Int?>(null) }

    val filtered = remember(series, period) {
        val p = period.days
        if (p == null || series.size <= p) series else series.takeLast(p)
    }
    val current = series.lastOrNull()
    val selected = selectedIndex?.let { filtered.getOrNull(it) } ?: current

    Column(
        modifier = modifier.fillMaxSize().background(palette.screenBg).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(stringResource(R.string.stats_title), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)

        if (current == null) {
            Card(colors = CardDefaults.cardColors(containerColor = palette.cardBg), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.fillMaxWidth().border(1.dp, palette.border, RoundedCornerShape(12.dp)).padding(16.dp)) {
                    Text(stringResource(R.string.stats_no_data), color = palette.textPrimary, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.stats_hint_low_data), color = palette.textSecondary, fontSize = 13.sp)
                }
            }
            return@Column
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard(Modifier.weight(1f), stringResource(R.string.stats_ctl), current.ctl.roundToInt().toString(), Color(0xFF2196F3))
            MetricCard(Modifier.weight(1f), stringResource(R.string.stats_atl), current.atl.roundToInt().toString(), Color(0xFFE91E63))
        }
        val tsbColor = tsbColor(current.tsb)
        MetricCard(Modifier.fillMaxWidth(), stringResource(R.string.stats_tsb), current.tsb.roundToInt().toString(), tsbColor, subtitle = stringResource(tsbStatusRes(current.tsb)))

        if (finishedCount < 3) {
            Text(stringResource(R.string.stats_hint_low_data), color = palette.textSecondary, fontSize = 12.sp)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PeriodChip(period == PmcPeriod.M1, stringResource(R.string.stats_period_1m)) { period = PmcPeriod.M1; selectedIndex = null }
            PeriodChip(period == PmcPeriod.M3, stringResource(R.string.stats_period_3m)) { period = PmcPeriod.M3; selectedIndex = null }
            PeriodChip(period == PmcPeriod.M6, stringResource(R.string.stats_period_6m)) { period = PmcPeriod.M6; selectedIndex = null }
            PeriodChip(period == PmcPeriod.ALL, stringResource(R.string.stats_period_all)) { period = PmcPeriod.ALL; selectedIndex = null }
        }

        PmcChart(
            points = filtered,
            selectedIndex = selectedIndex,
            onSelect = { selectedIndex = it }
        )

        if (selected != null) {
            val df = remember { DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.getDefault()) }
            Card(colors = CardDefaults.cardColors(containerColor = palette.cardBg), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.fillMaxWidth().border(1.dp, palette.border, RoundedCornerShape(12.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.stats_selected_day) + ": " + selected.date.format(df), fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                    Text("${stringResource(R.string.stats_daily_tss)}: ${selected.dailyTss.roundToInt()}", color = palette.textSecondary, fontSize = 13.sp)
                    Text("CTL: ${selected.ctl.roundToInt()}  ATL: ${selected.atl.roundToInt()}  TSB: ${selected.tsb.roundToInt()}", color = palette.textSecondary, fontSize = 13.sp)
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = palette.cardBg), shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.fillMaxWidth().border(1.dp, palette.border, RoundedCornerShape(12.dp)).padding(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Info, contentDescription = null, tint = palette.accent)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.stats_help_title), fontWeight = FontWeight.SemiBold, color = palette.textPrimary, modifier = Modifier.weight(1f))
                    TextButton(onClick = { helpExpanded = !helpExpanded }) { Text(if (helpExpanded) "−" else "+") }
                }
                if (helpExpanded) {
                    Text(stringResource(R.string.stats_help_body), color = palette.textSecondary, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun MetricCard(modifier: Modifier = Modifier, title: String, value: String, color: Color, subtitle: String? = null) {
    val palette = LocalBikeUiPalette.current
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = palette.cardBg), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.fillMaxWidth().border(1.dp, palette.border, RoundedCornerShape(12.dp)).padding(12.dp)) {
            Text(title, color = palette.textSecondary, fontSize = 12.sp)
            Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 24.sp)
            if (subtitle != null) Text(subtitle, color = palette.textSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun PeriodChip(selected: Boolean, label: String, onClick: () -> Unit) {
    val palette = LocalBikeUiPalette.current
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 12.sp) },
        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = palette.accent.copy(alpha = 0.18f), selectedLabelColor = palette.accent)
    )
}

@Composable
private fun PmcChart(points: List<FitnessAnalytics.DailyPoint>, selectedIndex: Int?, onSelect: (Int?) -> Unit) {
    val palette = LocalBikeUiPalette.current
    if (points.isEmpty()) return
    val ctlColor = Color(0xFF2196F3)
    val atlColor = Color(0xFFE91E63)
    val tsbPos = Color(0xFF4CAF50)
    val tsbNeg = Color(0xFFFFC107)

    val maxY = points.maxOf { maxOf(it.ctl, it.atl, abs(it.tsb), it.dailyTss) }.coerceAtLeast(10f)
    val minY = points.minOf { minOf(0f, it.tsb) }.coerceAtMost(-10f)
    val range = (maxY - minY).coerceAtLeast(1f)

    Card(colors = CardDefaults.cardColors(containerColor = palette.cardBg), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.fillMaxWidth().border(1.dp, palette.border, RoundedCornerShape(12.dp)).padding(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LegendDot(ctlColor, "CTL"); LegendDot(atlColor, "ATL"); LegendDot(tsbPos, "TSB")
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(220.dp)) {
                Canvas(
                    Modifier.fillMaxSize().pointerInput(points) {
                        detectTapGestures { offset ->
                            if (points.size <= 1) { onSelect(0); return@detectTapGestures }
                            val w = size.width.toFloat()
                            val idx = ((offset.x / w) * (points.size - 1)).roundToInt().coerceIn(0, points.lastIndex)
                            onSelect(idx)
                        }
                    }
                ) {
                    val w = size.width
                    val h = size.height
                    fun x(i: Int): Float = if (points.size == 1) w / 2f else (i.toFloat() / (points.size - 1).toFloat()) * w
                    fun y(v: Float): Float = h - ((v - minY) / range) * h

                    // zero line
                    val y0 = y(0f)
                    drawLine(palette.border, Offset(0f, y0), Offset(w, y0), strokeWidth = 1.dp.toPx())

                    // TSB line (colored segments by sign approximation)
                    val tsbPath = Path()
                    points.forEachIndexed { i, p -> val px = x(i); val py = y(p.tsb); if (i == 0) tsbPath.moveTo(px, py) else tsbPath.lineTo(px, py) }
                    drawPath(tsbPath, color = tsbPos, style = Stroke(width = 2.dp.toPx()))

                    // ATL dashed
                    val atlPath = Path()
                    points.forEachIndexed { i, p -> val px = x(i); val py = y(p.atl); if (i == 0) atlPath.moveTo(px, py) else atlPath.lineTo(px, py) }
                    drawPath(atlPath, color = atlColor, style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))))

                    // CTL solid
                    val ctlPath = Path()
                    points.forEachIndexed { i, p -> val px = x(i); val py = y(p.ctl); if (i == 0) ctlPath.moveTo(px, py) else ctlPath.lineTo(px, py) }
                    drawPath(ctlPath, color = ctlColor, style = Stroke(width = 3.dp.toPx()))

                    val sel = selectedIndex
                    if (sel != null && sel in points.indices) {
                        val sx = x(sel)
                        drawLine(palette.textSecondary.copy(alpha = 0.5f), Offset(sx, 0f), Offset(sx, h), strokeWidth = 1.dp.toPx())
                        val p = points[sel]
                        drawCircle(ctlColor, 4.dp.toPx(), Offset(sx, y(p.ctl)))
                        drawCircle(atlColor, 4.dp.toPx(), Offset(sx, y(p.atl)))
                        drawCircle(if (p.tsb >= 0) tsbPos else tsbNeg, 4.dp.toPx(), Offset(sx, y(p.tsb)))
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    val palette = LocalBikeUiPalette.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 12.sp, color = palette.textSecondary)
    }
}

private fun tsbStatusRes(tsb: Float): Int = when (FitnessAnalytics.tsbStatus(tsb)) {
    FitnessAnalytics.TsbStatus.FRESHNESS_DROP -> R.string.stats_status_freshness_drop
    FitnessAnalytics.TsbStatus.PEAK -> R.string.stats_status_peak
    FitnessAnalytics.TsbStatus.MAINTENANCE -> R.string.stats_status_maintenance
    FitnessAnalytics.TsbStatus.OPTIMAL -> R.string.stats_status_optimal
    FitnessAnalytics.TsbStatus.HIGH_RISK -> R.string.stats_status_high_risk
}

private fun tsbColor(tsb: Float): Color = when (FitnessAnalytics.tsbStatus(tsb)) {
    FitnessAnalytics.TsbStatus.FRESHNESS_DROP -> Color(0xFFFFC107)
    FitnessAnalytics.TsbStatus.PEAK -> Color(0xFF4CAF50)
    FitnessAnalytics.TsbStatus.MAINTENANCE -> Color(0xFF8BC34A)
    FitnessAnalytics.TsbStatus.OPTIMAL -> Color(0xFFFF9800)
    FitnessAnalytics.TsbStatus.HIGH_RISK -> Color(0xFFF44336)
}
