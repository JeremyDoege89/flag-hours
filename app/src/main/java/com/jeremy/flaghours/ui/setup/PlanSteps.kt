package com.jeremy.flaghours.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jeremy.flaghours.domain.ComebackRule
import com.jeremy.flaghours.domain.EfficiencyTier
import com.jeremy.flaghours.domain.FlagsPayWhen
import com.jeremy.flaghours.domain.Guarantee
import com.jeremy.flaghours.domain.OvertimeMode
import com.jeremy.flaghours.domain.OvertimeRule
import com.jeremy.flaghours.domain.PayPlan
import com.jeremy.flaghours.domain.PeriodType
import com.jeremy.flaghours.ui.common.ChoiceChips
import com.jeremy.flaghours.ui.common.DateField
import com.jeremy.flaghours.ui.common.HoursField
import com.jeremy.flaghours.ui.common.IntField
import com.jeremy.flaghours.ui.common.MoneyField
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** The seven pay-plan steps, shared by the first-launch wizard and Settings > Pay plan. */
enum class PlanStep(val title: String, val help: String) {
    RATES("Your rates", "What you're paid per flag hour, and per hour for clocked time like training or shop work."),
    PERIOD("Pay period", "How often you get paid and when each period starts."),
    PAY_WHEN("When flags pay", "Which paycheck a flagged job lands on."),
    GUARANTEE("Guarantee", "A minimum number of flag hours you're paid each period, if your shop has one."),
    TYPE_RATES("Warranty, internal & diag rates", "Leave blank if they pay the same as your flag rate."),
    COMEBACKS("Comebacks", "How your shop pays when a job comes back."),
    EXTRAS("Extras", "Overtime, efficiency bonuses and how many decimals to show."),
    ;

    /** Reset this step's fields to defaults (used by Skip). */
    fun reset(plan: PayPlan): PayPlan {
        val d = PayPlan()
        return when (this) {
            RATES -> plan.copy(flagRateCents = d.flagRateCents, hourlyRateCents = d.hourlyRateCents)
            PERIOD -> plan.copy(period = d.period, periodStartDay = d.periodStartDay, biweeklyAnchorEpochDay = d.biweeklyAnchorEpochDay)
            PAY_WHEN -> plan.copy(flagsPayWhen = d.flagsPayWhen)
            GUARANTEE -> plan.copy(guarantee = d.guarantee)
            TYPE_RATES -> plan.copy(warrantyRateCents = null, internalRateCents = null, diagRateCents = null)
            COMEBACKS -> plan.copy(comebackRule = d.comebackRule, comebackSplitPct = d.comebackSplitPct)
            EXTRAS -> plan.copy(overtime = null, efficiencyTiers = emptyList(), decimals = d.decimals)
        }
    }
}

@Composable
fun PlanStepContent(step: PlanStep, plan: PayPlan, onChange: (PayPlan) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(step.help, style = MaterialTheme.typography.bodyLarge)
        when (step) {
            PlanStep.RATES -> RatesStep(plan, onChange)
            PlanStep.PERIOD -> PeriodStep(plan, onChange)
            PlanStep.PAY_WHEN -> PayWhenStep(plan, onChange)
            PlanStep.GUARANTEE -> GuaranteeStep(plan, onChange)
            PlanStep.TYPE_RATES -> TypeRatesStep(plan, onChange)
            PlanStep.COMEBACKS -> ComebacksStep(plan, onChange)
            PlanStep.EXTRAS -> ExtrasStep(plan, onChange)
        }
    }
}

@Composable
private fun RatesStep(plan: PayPlan, onChange: (PayPlan) -> Unit) {
    MoneyField(plan.flagRateCents, { onChange(plan.copy(flagRateCents = it ?: 0)) }, "Flag rate per hour")
    MoneyField(plan.hourlyRateCents, { onChange(plan.copy(hourlyRateCents = it ?: 0)) }, "Hourly rate (training, shop time)")
}

@Composable
private fun PeriodStep(plan: PayPlan, onChange: (PayPlan) -> Unit) {
    ChoiceChips(PeriodType.entries, plan.period, { it.label }, { onChange(plan.copy(period = it)) })
    if (plan.period != PeriodType.SEMI_MONTHLY) {
        Text("Period starts on", style = MaterialTheme.typography.labelLarge)
        ChoiceChips(
            DayOfWeek.entries,
            DayOfWeek.of(plan.periodStartDay.coerceIn(1, 7)),
            { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) },
            { onChange(plan.copy(periodStartDay = it.value)) },
        )
    }
    if (plan.period == PeriodType.BIWEEKLY) {
        val anchor = plan.biweeklyAnchorEpochDay?.let { LocalDate.ofEpochDay(it) } ?: LocalDate.now()
        DateField(anchor, { onChange(plan.copy(biweeklyAnchorEpochDay = it.toEpochDay(), periodStartDay = it.dayOfWeek.value)) }, "First day of a current or past period")
    }
}

@Composable
private fun PayWhenStep(plan: PayPlan, onChange: (PayPlan) -> Unit) {
    ChoiceChips(FlagsPayWhen.entries, plan.flagsPayWhen, { it.label }, { onChange(plan.copy(flagsPayWhen = it)) })
    Text(
        when (plan.flagsPayWhen) {
            FlagsPayWhen.RO_CLOSED -> "A job counts toward the period in which you set it to Closed (or Paid)."
            FlagsPayWhen.MARKED_PAID -> "A job counts toward the period in which you mark it Paid. Safest if ROs often wait on parts or warranty."
            FlagsPayWhen.WORK_DATE -> "A job counts toward the period you did the work, no matter when the RO closes."
        },
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun GuaranteeStep(plan: PayPlan, onChange: (PayPlan) -> Unit) {
    val kinds = listOf("None", "Flat", "If clocked")
    val selected = when (plan.guarantee) {
        Guarantee.None -> "None"
        is Guarantee.Flat -> "Flat"
        is Guarantee.IfClocked -> "If clocked"
    }
    val hours = when (val g = plan.guarantee) {
        Guarantee.None -> 4000
        is Guarantee.Flat -> g.hoursHundredths
        is Guarantee.IfClocked -> g.hoursHundredths
    }
    ChoiceChips(kinds, selected, { it }, onSelect = {
        onChange(
            plan.copy(
                guarantee = when (it) {
                    "Flat" -> Guarantee.Flat(hours)
                    "If clocked" -> Guarantee.IfClocked(hours, hours)
                    else -> Guarantee.None
                },
            ),
        )
    })
    when (val g = plan.guarantee) {
        Guarantee.None -> Text("No guarantee: you're paid exactly the hours you flag.", style = MaterialTheme.typography.bodyMedium)
        is Guarantee.Flat -> HoursField(g.hoursHundredths, { onChange(plan.copy(guarantee = g.copy(hoursHundredths = it ?: 0))) }, "Guaranteed flag hours per period")
        is Guarantee.IfClocked -> {
            HoursField(g.hoursHundredths, { onChange(plan.copy(guarantee = g.copy(hoursHundredths = it ?: 0))) }, "Guaranteed flag hours per period")
            HoursField(g.minClockedHundredths, { onChange(plan.copy(guarantee = g.copy(minClockedHundredths = it ?: 0))) }, "Only if I clock at least")
        }
    }
    if (plan.guarantee != Guarantee.None) {
        Text("If you flag less, the difference is topped up at your flag rate.", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun TypeRatesStep(plan: PayPlan, onChange: (PayPlan) -> Unit) {
    val hint = "Same as flag rate"
    MoneyField(plan.warrantyRateCents, { onChange(plan.copy(warrantyRateCents = it)) }, "Warranty rate", placeholder = hint)
    MoneyField(plan.internalRateCents, { onChange(plan.copy(internalRateCents = it)) }, "Internal rate", placeholder = hint)
    MoneyField(plan.diagRateCents, { onChange(plan.copy(diagRateCents = it)) }, "Diag rate", placeholder = hint)
}

@Composable
private fun ComebacksStep(plan: PayPlan, onChange: (PayPlan) -> Unit) {
    ChoiceChips(ComebackRule.entries, plan.comebackRule, { it.label }, { onChange(plan.copy(comebackRule = it)) })
    Text(
        when (plan.comebackRule) {
            ComebackRule.PAID -> "A line marked as a comeback pays its flagged time like any other."
            ComebackRule.ZERO_PAY -> "A line marked as a comeback pays nothing; the original stays paid."
            ComebackRule.CLAWBACK -> "A line marked as a comeback pays nothing and the original job's hours are subtracted."
            ComebackRule.SPLIT -> "A line marked as a comeback pays a percentage of its flagged time."
        },
        style = MaterialTheme.typography.bodyMedium,
    )
    if (plan.comebackRule == ComebackRule.SPLIT) {
        IntField(plan.comebackSplitPct, { onChange(plan.copy(comebackSplitPct = (it ?: 0).coerceIn(0, 100))) }, "Comebacks pay", suffix = "%")
    }
}

@Composable
private fun ExtrasStep(plan: PayPlan, onChange: (PayPlan) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Overtime", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Switch(plan.overtime != null, { onChange(plan.copy(overtime = if (it) OvertimeRule() else null)) })
    }
    plan.overtime?.let { ot ->
        ChoiceChips(OvertimeMode.entries, ot.mode, { it.label }, { onChange(plan.copy(overtime = ot.copy(mode = it))) })
        HoursField(ot.thresholdHundredths, { onChange(plan.copy(overtime = ot.copy(thresholdHundredths = it ?: 0))) }, "Overtime after (hours per period)")
        IntField(ot.multiplierPct, { onChange(plan.copy(overtime = ot.copy(multiplierPct = it ?: 100))) }, "Overtime multiplier", suffix = "%")
        Text("150% = time and a half. The math used is shown on the dashboard.", style = MaterialTheme.typography.bodyMedium)
    }

    Text("Efficiency bonus tiers", style = MaterialTheme.typography.titleMedium)
    Text("At or above an efficiency, your flag rate is multiplied (110% = +10%).", style = MaterialTheme.typography.bodyMedium)
    plan.efficiencyTiers.forEachIndexed { i, tier ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IntField(tier.minEfficiencyPct, { v -> onChange(plan.copy(efficiencyTiers = plan.efficiencyTiers.replace(i, tier.copy(minEfficiencyPct = v ?: 0)))) }, "At efficiency", Modifier.weight(1f), "%")
            IntField(tier.multiplierPct, { v -> onChange(plan.copy(efficiencyTiers = plan.efficiencyTiers.replace(i, tier.copy(multiplierPct = v ?: 100)))) }, "Rate ×", Modifier.weight(1f), "%")
            IconButton(onClick = { onChange(plan.copy(efficiencyTiers = plan.efficiencyTiers.filterIndexed { j, _ -> j != i })) }) {
                Icon(Icons.Default.Delete, "Remove tier")
            }
        }
    }
    OutlinedButton(onClick = { onChange(plan.copy(efficiencyTiers = plan.efficiencyTiers + EfficiencyTier(120, 105))) }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.Add, null)
        Text("Add tier")
    }

    Text("Decimals shown for hours", style = MaterialTheme.typography.titleMedium)
    ChoiceChips(listOf(1, 2), plan.decimals, { if (it == 1) "1.6" else "1.55" }, { onChange(plan.copy(decimals = it)) })
}

private fun <T> List<T>.replace(i: Int, v: T): List<T> = mapIndexed { j, x -> if (j == i) v else x }
