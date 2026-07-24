package com.yura.tts.core

internal object TextNormalizer {
    fun normalize(text: String): String {
        if (text.isEmpty()) return text
        var normalized = DATE_REGEX.replace(text, ::normalizeDate)
        normalized = TIME_REGEX.replace(normalized, ::normalizeTime)
        normalized = CURRENCY_REGEX.replace(normalized, ::normalizeCurrency)
        normalized = PERCENT_REGEX.replace(normalized, ::normalizePercent)
        return THOUSANDS_REGEX.replace(normalized) { match -> match.value.replace(",", "") }
    }

    private fun normalizeDate(match: MatchResult): String {
        val year = match.groupValues[1]
        val month = match.groupValues[2].toIntOrNull() ?: return match.value
        val day = match.groupValues[3].toIntOrNull() ?: return match.value
        if (!isValidDate(year.toIntOrNull() ?: return match.value, month, day)) return match.value
        return digitsToChinese(year) + "年" + integerToChinese(month.toString()) + "月" +
            integerToChinese(day.toString()) + "日"
    }

    private fun normalizeTime(match: MatchResult): String {
        val hour = match.groupValues[1].toIntOrNull() ?: return match.value
        val minute = match.groupValues[2].toIntOrNull() ?: return match.value
        val secondText = match.groupValues[3]
        val second = secondText.takeIf(String::isNotEmpty)?.toIntOrNull()
        if (hour !in 0..23 || minute !in 0..59 || second != null && second !in 0..59) return match.value

        val result = StringBuilder()
            .append(integerToChinese(hour.toString()))
            .append("点")
        if (minute == 0) {
            result.append("整")
        } else {
            result.append(integerToChinese(minute.toString())).append("分")
        }
        if (second != null && second > 0) {
            result.append(integerToChinese(second.toString())).append("秒")
        }
        return result.toString()
    }

    private fun normalizeCurrency(match: MatchResult): String {
        val unit = when (match.groupValues[1]) {
            "$" -> "美元"
            "¥", "￥" -> "元"
            "€" -> "欧元"
            "£" -> "英镑"
            else -> return match.value
        }
        return decimalToChinese(match.groupValues[2].replace(",", "")) + unit
    }

    private fun normalizePercent(match: MatchResult): String =
        "百分之" + decimalToChinese(match.groupValues[1].replace(",", ""))

    private fun decimalToChinese(value: String): String {
        val normalized = value.removePrefix("+")
        val negative = normalized.startsWith("-")
        val unsigned = normalized.removePrefix("-")
        val parts = unsigned.split('.', limit = 2)
        val integerPart = integerToChinese(parts.firstOrNull().orEmpty())
        val decimalPart = parts.getOrNull(1)?.takeIf(String::isNotEmpty)?.let(::digitsToChinese)
        val spoken = if (decimalPart == null) integerPart else integerPart + "点" + decimalPart
        return if (negative) "负" + spoken else spoken
    }

    private fun integerToChinese(value: String): String {
        val digits = value.trimStart('0').ifEmpty { "0" }
        if (digits == "0") return "零"
        if (digits.length > 16 || digits.any { !it.isDigit() }) return digitsToChinese(digits)

        val sections = mutableListOf<Int>()
        var end = digits.length
        while (end > 0) {
            val start = (end - 4).coerceAtLeast(0)
            sections += digits.substring(start, end).toInt()
            end = start
        }

        val result = StringBuilder()
        var pendingZero = false
        for (sectionIndex in sections.lastIndex downTo 0) {
            val section = sections[sectionIndex]
            if (section == 0) {
                if (result.isNotEmpty()) pendingZero = true
                continue
            }
            if (result.isNotEmpty() && (pendingZero || section < 1000) && result.last() != '零') {
                result.append("零")
            }
            result.append(sectionToChinese(section))
            result.append(SECTION_UNITS[sectionIndex])
            pendingZero = false
        }
        return result.toString()
    }

    private fun sectionToChinese(section: Int): String {
        val result = StringBuilder()
        var remaining = section
        var pendingZero = false
        for (unitIndex in 3 downTo 0) {
            val divisor = POWERS_OF_TEN[unitIndex]
            val digit = remaining / divisor
            remaining %= divisor
            if (digit == 0) {
                if (result.isNotEmpty() && remaining > 0) pendingZero = true
                continue
            }
            if (pendingZero && result.lastOrNull() != '零') result.append("零")
            if (!(unitIndex == 1 && digit == 1 && result.isEmpty())) {
                result.append(DIGITS[digit])
            }
            result.append(SMALL_UNITS[unitIndex])
            pendingZero = false
        }
        return result.toString()
    }

    private fun digitsToChinese(value: String): String = buildString {
        value.forEach { character ->
            append(if (character.isDigit()) DIGITS[character.digitToInt()] else character)
        }
    }

    private fun isValidDate(year: Int, month: Int, day: Int): Boolean {
        if (month !in 1..12 || day < 1) return false
        val maxDay = when (month) {
            2 -> if (isLeapYear(year)) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }
        return day <= maxDay
    }

    private fun isLeapYear(year: Int): Boolean = year % 400 == 0 || year % 4 == 0 && year % 100 != 0

    private val DIGITS = listOf("零", "一", "二", "三", "四", "五", "六", "七", "八", "九")
    private val SMALL_UNITS = listOf("", "十", "百", "千")
    private val SECTION_UNITS = listOf("", "万", "亿", "万亿")
    private val POWERS_OF_TEN = intArrayOf(1, 10, 100, 1000)

    private val DATE_REGEX = Regex("""(?<!\d)(\d{4})[-/.年](\d{1,2})[-/.月](\d{1,2})日?(?!\d)""")
    private val TIME_REGEX = Regex("""(?<![\d:.：])(\d{1,2})[:：](\d{2})(?:[:：](\d{2}))?(?![\d:：])""")
    private val CURRENCY_REGEX = Regex("""([$¥￥€£])\s*([+-]?(?:\d{1,3}(?:,\d{3})+|\d+)(?:\.\d+)?)""")
    private val PERCENT_REGEX = Regex("""([+-]?(?:\d{1,3}(?:,\d{3})+|\d+)(?:\.\d+)?)\s*%""")
    private val THOUSANDS_REGEX = Regex("""(?<![\d.])\d{1,3}(?:,\d{3})+(?:\.\d+)?(?![\d.])""")
}
