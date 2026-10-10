package com.jeremy.flaghours.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class PeriodType(val label: String) {
    WEEKLY("Weekly"),
    BIWEEKLY("Every two weeks"),
    SEMI_MONTHLY("Twice a month (1st–15th, 16th–end)"),
}

@Serializable
enum class FlagsPayWhen(val label: String) {
    /** Paid in the period the RO (job) is closed. */
    RO_CLOSED("When the RO closes"),
    /** Paid in the period I mark the job paid. */
    MARKED_PAID("When I mark it paid"),
    /** Paid in the period the work was done. */
    WORK_DATE("In the week the work was done"),
}

@Serializable
enum class ComebackRule(val label: String) {
    /** The comeback line pays like any other line. */
    PAID("Comebacks pay normally"),
    /** The comeback line pays nothing. */
    ZERO_PAY("Comebacks pay nothing"),
    /** The comeback pays nothing and the original line's pay is taken back. */
    CLAWBACK("Original job is charged back"),
    /** The comeback line pays a percentage (comebackSplitPct). */
    SPLIT("Comebacks pay a percentage"),
}

@Serializable
sealed interface Guarantee {
    @Serializable
    @SerialName("none")
    data object None : Guarantee

    /** Always paid at least [hoursHundredths] flag hours per period. */
    @Serializable
    @SerialName("flat")
    data class Flat(val hoursHundredths: Int) : Guarantee

    /** Paid at least [hoursHundredths] flag hours if clocked at least [minClockedHundredths]. */
    @Serializable
    @SerialName("if_clocked")
    data class IfClocked(val hoursHundredths: Int, val minClockedHundredths: Int) : Guarantee
}

@Serializable
enum class OvertimeMode(val label: String) {
    /** Premium = (multiplier − 100%) × regular rate × clocked hours over threshold; regular rate = straight pay ÷ clocked hours. */
    REGULAR_RATE("Premium on regular rate (pay ÷ clocked)"),
    /** Flag hours paid beyond the threshold earn (multiplier − 100%) extra at flag rate. */
    FLAG_HOURS("Flag hours over threshold"),
    /** Clocked hours beyond the threshold earn (multiplier − 100%) extra at the hourly rate. */
    CLOCKED_HOURLY("Clocked hours over threshold at hourly rate"),
}

@Serializable
data class OvertimeRule(
    val mode: OvertimeMode = OvertimeMode.REGULAR_RATE,
    /** Threshold per pay period in hours (hundredths). 40 h weekly = 4000. */
    val thresholdHundredths: Int = 4000,
    /** 150 = time and a half. */
    val multiplierPct: Int = 150,
)

/** At or above [minEfficiencyPct] the flag rate is multiplied by [multiplierPct] (110 = +10%). */
@Serializable
data class EfficiencyTier(val minEfficiencyPct: Int, val multiplierPct: Int)

@Serializable
data class PayPlan(
    val flagRateCents: Long = 0,
    val warrantyRateCents: Long? = null,
    val internalRateCents: Long? = null,
    val diagRateCents: Long? = null,
    val hourlyRateCents: Long = 0,
    val period: PeriodType = PeriodType.WEEKLY,
    /** WEEKLY/BIWEEKLY: ISO day of week the period starts (1 = Monday … 7 = Sunday). Ignored for SEMI_MONTHLY. */
    val periodStartDay: Int = 1,
    /** BIWEEKLY: epoch day of any period start date. */
    val biweeklyAnchorEpochDay: Long? = null,
    val flagsPayWhen: FlagsPayWhen = FlagsPayWhen.MARKED_PAID,
    val guarantee: Guarantee = Guarantee.None,
    val comebackRule: ComebackRule = ComebackRule.PAID,
    /** Used when comebackRule == SPLIT. */
    val comebackSplitPct: Int = 50,
    val overtime: OvertimeRule? = null,
    val efficiencyTiers: List<EfficiencyTier> = emptyList(),
    val decimals: Int = 1,
    val setupComplete: Boolean = false,
) {
    fun rateFor(type: LaborType): Long = when (type) {
        LaborType.CUSTOMER -> flagRateCents
        LaborType.WARRANTY -> warrantyRateCents ?: flagRateCents
        LaborType.INTERNAL -> internalRateCents ?: flagRateCents
        LaborType.DIAG -> diagRateCents ?: flagRateCents
    }
}
