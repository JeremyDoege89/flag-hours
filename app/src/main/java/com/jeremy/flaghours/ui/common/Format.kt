package com.jeremy.flaghours.ui.common

import com.jeremy.flaghours.domain.PayPeriod
import com.jeremy.flaghours.domain.Units
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val dayFmt = DateTimeFormatter.ofPattern("EEE MMM d")
private val shortFmt = DateTimeFormatter.ofPattern("MMM d")
private val timeFmt = DateTimeFormatter.ofPattern("h:mm a")

fun fmtDate(d: LocalDate): String = d.format(dayFmt)
fun fmtShort(d: LocalDate): String = d.format(shortFmt)
fun fmtTime(t: LocalDateTime): String = t.format(timeFmt)
fun fmtDateTime(t: LocalDateTime): String = "${fmtDate(t.toLocalDate())} ${fmtTime(t)}"
fun fmtPeriod(p: PayPeriod): String = "${fmtShort(p.start)} – ${fmtShort(p.end)}"

fun hrs(hundredths: Int, decimals: Int): String = Units.formatHours(hundredths, decimals)
fun money(cents: Long): String = Units.formatMoney(cents)

fun minutesLabel(minutes: Long): String = "%d:%02d".format(minutes / 60, minutes % 60)

/** Text for editing a cents value, e.g. 3250 -> "32.50"; null -> "". */
fun moneyText(cents: Long?): String = cents?.let { Units.formatMoney(it).replace("$", "").replace(",", "") } ?: ""

/** Text for editing hundredths, trimmed: 160 -> "1.6", 155 -> "1.55". */
fun hoursText(hundredths: Int?): String = hundredths?.let {
    val s = Units.formatHours(it, 2)
    if (s.contains('.')) s.trimEnd('0').trimEnd('.') else s
} ?: ""
