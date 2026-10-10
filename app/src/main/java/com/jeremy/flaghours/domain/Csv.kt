package com.jeremy.flaghours.domain

/** Minimal RFC 4180 CSV reading and writing. */
object Csv {

    fun escape(field: String): String =
        if (field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + field.replace("\"", "\"\"") + "\"" else field

    fun line(fields: List<String>): String = fields.joinToString(",") { escape(it) }

    /** Parses CSV text into rows. Handles quoted fields with commas, quotes and newlines; CRLF or LF. */
    fun parse(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        val s = text.removePrefix("﻿")
        var rowHasContent = false
        while (i < s.length) {
            val c = s[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < s.length && s[i + 1] == '"') {
                        field.append('"'); i++
                    } else {
                        inQuotes = false
                    }
                } else {
                    field.append(c)
                }
            } else {
                when (c) {
                    '"' -> { inQuotes = true; rowHasContent = true }
                    ',' -> { row.add(field.toString()); field.clear(); rowHasContent = true }
                    '\r' -> Unit
                    '\n' -> {
                        if (rowHasContent || field.isNotEmpty()) {
                            row.add(field.toString()); rows.add(row)
                        }
                        row = mutableListOf(); field.clear(); rowHasContent = false
                    }
                    else -> { field.append(c); rowHasContent = true }
                }
            }
            i++
        }
        if (rowHasContent || field.isNotEmpty()) {
            row.add(field.toString()); rows.add(row)
        }
        return rows
    }
}
