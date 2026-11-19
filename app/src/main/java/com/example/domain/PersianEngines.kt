@file:Suppress("MemberVisibilityCanBePrivate", "SpellCheckingInspection")

package com.example.domain

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.regex.Pattern

object PersianNumberUtils {
    private val persianDigits = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')

    fun normalizePersianText(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when (ch) {
                'ي', 'ى', 'ئ' -> sb.append('ی')
                'ك' -> sb.append('ک')
                'ة' -> sb.append('ه')
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun toPersianDigits(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when (ch) {
                in '0'..'9' -> sb.append(persianDigits[ch - '0'])
                in '٠'..'٩' -> sb.append(persianDigits[ch - '٠'])
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun toEnglishDigits(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when (ch) {
                in '۰'..'۹' -> sb.append(('0'.code + (ch - '۰')).toChar())
                in '٠'..'٩' -> sb.append(('0'.code + (ch - '٠')).toChar())
                '،', ',', '٬', '_' -> {}
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun roundDownToCleanThousands(amountToman: Long): Long {
        if (amountToman < 1_000L) return amountToman.coerceAtLeast(0L)
        return (amountToman / 1_000L) * 1_000L
    }

    /**
     * Zero-allocation fast thousands-grouping into Persian digits (avoids `DecimalFormat` & `DecimalFormatSymbols`
     * allocations inside LazyColumn rows during 120Hz scrolling).
     */
    fun formatGroupedPersianLong(absVal: Long): String {
        if (absVal == 0L) return "۰"
        val digits = absVal.toString()
        val len = digits.length
        val commaCount = (len - 1) / 3
        val out = CharArray(len + commaCount)
        var outIdx = out.size - 1
        var groupCounter = 0
        for (i in len - 1 downTo 0) {
            if (groupCounter == 3) {
                out[outIdx--] = ','
                groupCounter = 0
            }
            val ch = digits[i]
            out[outIdx--] = if (ch in '0'..'9') ('۰'.code + (ch - '0')).toChar() else ch
            groupCounter++
        }
        return String(out)
    }

    fun formatToman(
        amount: Long,
        showUnit: Boolean = false,
        showSign: Boolean = false,
        hideAmount: Boolean = false
    ): String {
        if (hideAmount) {
            return if (showUnit) "**** تومان" else "****"
        }
        val absVal = kotlin.math.abs(amount)
        val formatted = formatGroupedPersianLong(absVal)
        val signPrefix = when {
            !showSign -> ""
            amount > 0 -> "+"
            amount < 0 -> "−"
            else -> ""
        }
        return if (showUnit) {
            "$signPrefix$formatted تومان"
        } else {
            "$signPrefix$formatted"
        }
    }

    /**
     * Formats an income (deposit) amount in Tomans so that in RTL layout the `+` sign
     * sits immediately to the LEFT of the digits and separated from `تومان` by a space:
     * visually rendered as: `تومان +۱۳,۰۰۰,۰۰۰`
     */
    fun formatIncomeToman(
        amount: Long,
        showUnit: Boolean = true,
        hideAmount: Boolean = false
    ): String {
        if (hideAmount) {
            return if (showUnit) "**** تومان" else "****"
        }
        val absVal = kotlin.math.abs(amount)
        val formatted = formatGroupedPersianLong(absVal)
        val ltrSignedNumber = "\u2066\u200E+$formatted\u2069"
        return if (showUnit) {
            "$ltrSignedNumber تومان"
        } else {
            ltrSignedNumber
        }
    }

    /**
     * Pre-formats an LTR-isolated Persian amount run (without "تومان") for zero-allocation
     * rendering inside 120Hz LazyColumn rows via [OrosiAmountText].
     */
    fun formatIsolatedAmountRun(amountToman: Long, isIncome: Boolean = false): String {
        val grouped = formatGroupedPersianLong(kotlin.math.abs(amountToman))
        return if (isIncome && amountToman > 0L) {
            "\u2066\u200E+$grouped\u2069"
        } else {
            "\u2066$grouped\u2069"
        }
    }

    /**
     * Formats the voice scale toggle button text with explicit LTR isolation around the math
     * operator so `×` and `÷` always sit to the LEFT of `۱۰۰۰` in RTL Persian layouts.
     */
    fun formatScaleToggleLabel(isThousandScale: Boolean): String {
        return if (isThousandScale) {
            "تغییر واحد به میلیون (\u2066\u200E×۱۰۰۰\u2069)"
        } else {
            "تغییر واحد به هزار (\u2066\u200E÷۱۰۰۰\u2069)"
        }
    }

    fun formatDecimal(value: Double, decimals: Int = 2): String {
        val pattern = if (decimals <= 0) "#" else "#." + "#".repeat(decimals)
        val symbols = DecimalFormatSymbols(Locale.US).apply {
            decimalSeparator = '.'
        }
        val formatted = DecimalFormat(pattern, symbols).format(value)
        return toPersianDigits(formatted.replace('.', '٫'))
    }

    /**
     * Strips commas (`,`, `٬`, `،`, `_`), spaces, and converts Persian/Arabic digits to English
     * before parsing as a Long so formatted inputs like "۱۰,۰۰۰" or "100,000" always save properly.
     */
    fun parseAmountInputToLong(input: String): Long? {
        val digitsOnly = toEnglishDigits(input)
            .filter { it in '0'..'9' }
        if (digitsOnly.isEmpty()) return null
        return digitsOnly.toLongOrNull()
    }

    /**
     * Live 3-digit comma formatter for amount text fields (e.g. "100000" -> "۱۰۰,۰۰۰").
     */
    fun formatLiveAmountInput(input: String): String {
        val digitsOnly = toEnglishDigits(input).filter { it in '0'..'9' }
        if (digitsOnly.isEmpty()) return ""
        val trimmed = digitsOnly.trimStart('0').ifEmpty { "0" }
        val capped = trimmed.take(15)
        val parsed = capped.toLongOrNull() ?: return toPersianDigits(capped)
        val symbols = DecimalFormatSymbols(Locale.US).apply {
            groupingSeparator = ','
        }
        val formatted = DecimalFormat("#,###", symbols).format(parsed)
        return toPersianDigits(formatted)
    }

    /**
     * Wraps a phone number or SMS sender address in Unicode Left-to-Right Isolate + LTR Mark
     * (`\u2066\u200E...\u2069`) so leading `+` signs (e.g. `+982187641`, `+989999987641`)
     * ALWAYS render on the LEFT side even inside RTL Persian text or comma-separated lists.
     */
    fun formatLtrSender(raw: String): String {
        val cleaned = raw.trim()
            .removePrefix("\u2066")
            .removePrefix("\u200E")
            .removePrefix("\u202A")
            .removeSuffix("\u2069")
            .removeSuffix("\u202C")
            .removeSuffix("\u200F")
            .trim()
        if (cleaned.isEmpty()) return ""
        return "\u2066\u200E$cleaned\u2069"
    }

    fun formatLtrSenderList(senders: List<String>, separator: String = " ، "): String =
        senders.mapNotNull { s -> formatLtrSender(s).takeIf { it.isNotEmpty() } }
            .joinToString(separator)
}

data class JalaliDateTime(
    val year: Int,
    val month: Int,
    val day: Int,
    val monthName: String,
    val weekdayName: String,
    val hour: Int,
    val minute: Int
) {
    private val persianDayStr: String = PersianNumberUtils.toPersianDigits(day.toString())
    private val persianYearStr: String = PersianNumberUtils.toPersianDigits(year.toString())

    val weekdayDate: String = "$weekdayName $persianDayStr $monthName"

    val dateWithYear: String = "$persianDayStr $monthName $persianYearStr"

    val fullWeekdayDateWithYear: String = "$weekdayName $persianDayStr $monthName $persianYearStr"

    val monthYearLabel: String = "$monthName $persianYearStr"

    val yearMonthKey: Int = year * 100 + month

    val timeStr: String = PersianNumberUtils.toPersianDigits(String.format(Locale.US, "%02d:%02d", hour, minute))
}

object JalaliCalendarUtils {
    private val tehranTimeZone: TimeZone = TimeZone.getTimeZone("Asia/Tehran")
    private val minuteCache = java.util.concurrent.ConcurrentHashMap<Long, JalaliDateTime>(512)

    private val persianMonths = arrayOf(
        "", "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
    )

    fun monthNameOf(month: Int): String = persianMonths.getOrElse(month.coerceIn(1, 12)) { "آبان" }

    fun isJalaliLeapYear(jy: Int): Boolean {
        val mod = ((jy + 12) % 33 + 33) % 33
        return mod in setOf(1, 5, 9, 13, 17, 22, 26, 30)
    }

    fun daysInJalaliMonth(jy: Int, jm: Int): Int = when {
        jm in 1..6 -> 31
        jm in 7..11 -> 30
        jm == 12 -> if (isJalaliLeapYear(jy)) 30 else 29
        else -> 30
    }

    fun timestampToJalali(timestampMillis: Long): JalaliDateTime = fromTimestamp(timestampMillis)

    fun fromTimestamp(timestampMillis: Long): JalaliDateTime {
        val effectiveMillis = if (timestampMillis > 946684800000L) {
            timestampMillis
        } else {
            System.currentTimeMillis()
        }
        val minuteKey = effectiveMillis / 60_000L
        minuteCache[minuteKey]?.let { return it }

        val cal = Calendar.getInstance(tehranTimeZone, Locale.US).apply {
            timeInMillis = effectiveMillis
        }
        val gy = cal.get(Calendar.YEAR)
        val gm = cal.get(Calendar.MONTH) + 1
        val gd = cal.get(Calendar.DAY_OF_MONTH)
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)

        val weekdayName = when (dayOfWeek) {
            Calendar.SATURDAY -> "شنبه"
            Calendar.SUNDAY -> "یکشنبه"
            Calendar.MONDAY -> "دوشنبه"
            Calendar.TUESDAY -> "سه‌شنبه"
            Calendar.WEDNESDAY -> "چهارشنبه"
            Calendar.THURSDAY -> "پنجشنبه"
            Calendar.FRIDAY -> "جمعه"
            else -> "شنبه"
        }

        val (jy, jm, jd) = gregorianToJalali(gy, gm, gd)
        val monthName = persianMonths.getOrElse(jm) { "آبان" }

        val computed = JalaliDateTime(
            year = jy,
            month = jm,
            day = jd,
            monthName = monthName,
            weekdayName = weekdayName,
            hour = hour,
            minute = minute
        )
        if (minuteCache.size > 4096) {
            minuteCache.clear()
        }
        minuteCache[minuteKey] = computed
        return computed
    }

    /**
     * Exact standard mathematical Gregorian-to-Jalali calendar conversion algorithm:
     * - Accounts for exact Gregorian leap years (4, 100, 400) and Jalali 33-year / 4-year cycles.
     * - First 6 months (Farvardin..Shahrivar) = 31 days (186 days total).
     * - Next 5 months (Mehr..Bahman) = 30 days.
     * - Esfand = 29 or 30 days in leap years.
     */
    fun gregorianToJalali(gy: Int, gm: Int, gd: Int): Triple<Int, Int, Int> {
        val gDaysInMonth = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        val gy2 = if (gm > 2) gy + 1 else gy
        var days = 355666 +
            (365 * gy) +
            ((gy2 + 3) / 4) -
            ((gy2 + 99) / 100) +
            ((gy2 + 399) / 400) +
            gd +
            gDaysInMonth[(gm - 1).coerceIn(0, 11)]

        var jy = -1595 + (33 * (days / 12053))
        days %= 12053
        jy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            jy += (days - 1) / 365
            days = (days - 1) % 365
        }
        val jm: Int
        val jd: Int
        if (days < 186) {
            jm = 1 + (days / 31)
            jd = 1 + (days % 31)
        } else {
            jm = 7 + ((days - 186) / 30)
            jd = 1 + ((days - 186) % 30)
        }
        return Triple(jy, jm.coerceIn(1, 12), jd.coerceIn(1, 31))
    }

    /**
     * Exact mathematical inverse of [gregorianToJalali]: converts a Jalali (jy, jm, jd)
     * date into its Gregorian (gy, gm, gd) triple.
     */
    fun jalaliToGregorian(jy: Int, jm: Int, jd: Int): Triple<Int, Int, Int> {
        val jy1 = jy + 1595
        var days = -355668 +
            (365 * jy1) +
            ((jy1 / 33) * 8) +
            (((jy1 % 33) + 3) / 4) +
            jd +
            if (jm < 7) (jm - 1) * 31 else ((jm - 7) * 30) + 186
        var gy = 400 * (days / 146097)
        days %= 146097
        if (days > 36524) {
            gy += 100 * (--days / 36524)
            days %= 36524
            if (days >= 365) days++
        }
        gy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            gy += (days - 1) / 365
            days = (days - 1) % 365
        }
        var gd = days + 1
        val isLeap = (gy % 4 == 0 && gy % 100 != 0) || (gy % 400 == 0)
        val gregorianMonthDays = intArrayOf(0, 31, if (isLeap) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        var gm = 1
        while (gm <= 12 && gd > gregorianMonthDays[gm]) {
            gd -= gregorianMonthDays[gm]
            gm++
        }
        return Triple(gy, gm.coerceIn(1, 12), gd.coerceIn(1, 31))
    }

    /**
     * Returns `[startInclusiveMillis, endExclusiveMillis)` in `Asia/Tehran` timezone
     * for the given Jalali year and month (1..12).
     */
    fun jalaliMonthEpochRangeMillis(jy: Int, jm: Int): Pair<Long, Long> {
        val safeMonth = jm.coerceIn(1, 12)
        val (startGy, startGm, startGd) = jalaliToGregorian(jy, safeMonth, 1)
        val nextYear = if (safeMonth == 12) jy + 1 else jy
        val nextMonth = if (safeMonth == 12) 1 else safeMonth + 1
        val (endGy, endGm, endGd) = jalaliToGregorian(nextYear, nextMonth, 1)

        val startCal = Calendar.getInstance(tehranTimeZone, Locale.US).apply {
            set(Calendar.YEAR, startGy)
            set(Calendar.MONTH, startGm - 1)
            set(Calendar.DAY_OF_MONTH, startGd)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val endCal = Calendar.getInstance(tehranTimeZone, Locale.US).apply {
            set(Calendar.YEAR, endGy)
            set(Calendar.MONTH, endGm - 1)
            set(Calendar.DAY_OF_MONTH, endGd)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return startCal.timeInMillis to endCal.timeInMillis
    }

    fun shiftJalaliYearMonth(yearMonthKey: Int, deltaMonths: Int): Int {
        var year = yearMonthKey / 100
        var month = (yearMonthKey % 100) + deltaMonths
        while (month < 1) {
            month += 12
            year -= 1
        }
        while (month > 12) {
            month -= 12
            year += 1
        }
        return year * 100 + month
    }

    fun calculateRemainingDaysToPayday(paydayStartDay: Int, nowMillis: Long = System.currentTimeMillis()): Int {
        val jalali = fromTimestamp(nowMillis)
        val monthLength = daysInJalaliMonth(jalali.year, jalali.month)
        val currentDay = jalali.day
        val targetDay = paydayStartDay.coerceIn(1, monthLength)
        return when {
            currentDay < targetDay -> (targetDay - currentDay).coerceIn(1, 31)
            currentDay > targetDay -> (monthLength - currentDay + targetDay).coerceIn(1, 31)
            else -> monthLength
        }
    }

    data class PaydayCycleWindow(
        val startMillis: Long,
        val endExclusiveMillis: Long,
        val label: String
    )

    private fun jalaliDayStartMillis(jy: Int, jm: Int, jd: Int): Long {
        val safeMonth = jm.coerceIn(1, 12)
        val safeDay = jd.coerceIn(1, daysInJalaliMonth(jy, safeMonth))
        val (gy, gm, gd) = jalaliToGregorian(jy, safeMonth, safeDay)
        return Calendar.getInstance(tehranTimeZone, Locale.US).apply {
            set(Calendar.YEAR, gy)
            set(Calendar.MONTH, gm - 1)
            set(Calendar.DAY_OF_MONTH, gd)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    /**
     * Computes the exact `[startMillis, endExclusiveMillis)` Tehran-time window and human-readable
     * Jalali range label for the user's current payday cycle (e.g. 1st..30th when `paydayStartDay == 1`,
     * or 25 Mehr..24 Aban when `paydayStartDay == 25`).
     */
    fun currentPaydayCycleRange(
        paydayStartDay: Int,
        nowMillis: Long = System.currentTimeMillis()
    ): PaydayCycleWindow {
        val nowJ = fromTimestamp(nowMillis)
        val safeStartDay = paydayStartDay.coerceIn(1, 30)

        val startY: Int
        val startM: Int
        val startD: Int
        val endY: Int
        val endM: Int
        val endD: Int

        if (safeStartDay == 1) {
            startY = nowJ.year
            startM = nowJ.month
            startD = 1
            val nextYm = shiftJalaliYearMonth(nowJ.yearMonthKey, 1)
            endY = nextYm / 100
            endM = nextYm % 100
            endD = 1
        } else if (nowJ.day >= safeStartDay) {
            startY = nowJ.year
            startM = nowJ.month
            startD = safeStartDay.coerceAtMost(daysInJalaliMonth(startY, startM))
            val nextYm = shiftJalaliYearMonth(nowJ.yearMonthKey, 1)
            endY = nextYm / 100
            endM = nextYm % 100
            endD = safeStartDay.coerceAtMost(daysInJalaliMonth(endY, endM))
        } else {
            val prevYm = shiftJalaliYearMonth(nowJ.yearMonthKey, -1)
            startY = prevYm / 100
            startM = prevYm % 100
            startD = safeStartDay.coerceAtMost(daysInJalaliMonth(startY, startM))
            endY = nowJ.year
            endM = nowJ.month
            endD = safeStartDay.coerceAtMost(daysInJalaliMonth(endY, endM))
        }

        val startMillis = jalaliDayStartMillis(startY, startM, startD)
        val endExclusiveMillis = jalaliDayStartMillis(endY, endM, endD)
        val startDisplay = fromTimestamp(startMillis)
        val endInclusiveDisplay = fromTimestamp((endExclusiveMillis - 1L).coerceAtLeast(startMillis))

        val startDayFa = PersianNumberUtils.toPersianDigits(startDisplay.day.toString())
        val label = if (startDisplay.year == endInclusiveDisplay.year) {
            "$startDayFa ${startDisplay.monthName} تا ${endInclusiveDisplay.dateWithYear}"
        } else {
            "${startDisplay.dateWithYear} تا ${endInclusiveDisplay.dateWithYear}"
        }
        return PaydayCycleWindow(
            startMillis = startMillis,
            endExclusiveMillis = endExclusiveMillis,
            label = label
        )
    }

    /**
     * Resolves colloquial Persian relative due-date hints ("فردا", "پس‌فردا", "هفته بعد", "ماه بعد")
     * into an actual Jalali date string.
     */
    fun resolveRelativeJalaliDate(
        hint: String,
        nowMillis: Long = System.currentTimeMillis()
    ): String {
        val norm = PersianNumberUtils.normalizePersianText(hint.trim())
        val offsetDays = when {
            norm.contains("پس‌فردا") || norm.contains("پس فردا") -> 2L
            norm.contains("فردا") -> 1L
            norm.contains("هفته بعد") || norm.contains("هفته آینده") || norm.contains("هفته دیگر") -> 7L
            norm.contains("ماه بعد") || norm.contains("ماه آینده") || norm.contains("ماه دیگر") -> 30L
            else -> 0L
        }
        return fromTimestamp(nowMillis + offsetDays * 86_400_000L).dateWithYear
    }
}

data class CashflowTxSnapshot(
    val id: Long,
    val type: String,
    val totalAmountToman: Long,
    val userShareToman: Long,
    val bankName: String,
    val timestamp: Long
)

enum class CashflowHealthStatus {
    POSITIVE,
    BALANCED,
    DEFICIT
}

data class SelfTransferPairDetail(
    val expenseTxId: Long,
    val incomeTxId: Long,
    val fromBankName: String,
    val toBankName: String,
    val amountToman: Long,
    val timestamp: Long,
    val dateTimeLabel: String
)

data class PaydayCashflowSummary(
    val cycleLabel: String,
    val totalIncomeToman: Long,
    val totalExpenseToman: Long,
    val reservedCommitmentsToman: Long,
    val excludedSelfTransferPairs: Int,
    val excludedSelfTransferTotalToman: Long,
    val netBalanceToman: Long,
    val healthStatus: CashflowHealthStatus,
    val excludedSelfTransferDetails: List<SelfTransferPairDetail> = emptyList()
)

/**
 * Pure P0 accounting analyzer for the user's current payday cycle:
 * - Strictly filters transactions to `[cycleStartMillis, cycleEndExclusiveMillis)`.
 * - Detects and excludes internal self-transfers between two different bank accounts of the user
 *   (identical `totalAmountToman` > 0 within ±10 minutes, 1-to-1 greedy closest-time matching)
 *   as well as any rows already typed `SELF_TRANSFER`.
 * - Computes honest positive / balanced / deficit monthly status.
 */
object OrosiCashflowAnalyzer {
    private const val SELF_TRANSFER_MAX_WINDOW_MS = 10L * 60L * 1000L

    private fun isRealBankName(bankName: String): Boolean {
        val clean = bankName.trim()
        if (clean.isBlank()) return false
        return clean != "نقدی / دستی" && clean != "دستی" && clean != "نقدی" && clean != "تراکنش صوتی"
    }

    fun analyzeCurrentPaydayCycle(
        transactions: List<CashflowTxSnapshot>,
        paydayStartDay: Int,
        reservedCommitmentsToman: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): PaydayCashflowSummary {
        val window = JalaliCalendarUtils.currentPaydayCycleRange(paydayStartDay, nowMillis)
        val inCycle = transactions.filter { tx ->
            tx.totalAmountToman > 0L &&
                tx.timestamp >= window.startMillis &&
                tx.timestamp < window.endExclusiveMillis
        }

        val expenses = inCycle.filter { it.type == "EXPENSE" }
        val incomes = inCycle.filter { it.type == "INCOME" }

        // Smart 1-to-1 greedy matching for internal self-transfers across distinct bank accounts within ±10 minutes
        val excludedIds = HashSet<Long>()
        val matchedIncomeIds = HashSet<Long>()
        val selfTransferDetails = mutableListOf<SelfTransferPairDetail>()
        var selfTransferPairs = 0
        var selfTransferTotalToman = 0L

        val candidateExpenses = expenses
            .filter { isRealBankName(it.bankName) }
            .sortedBy { it.timestamp }
        val candidateIncomesByAmount = incomes
            .filter { isRealBankName(it.bankName) }
            .groupBy { it.totalAmountToman }

        for (exp in candidateExpenses) {
            val matchingIncomes = candidateIncomesByAmount[exp.totalAmountToman] ?: continue
            var bestIncome: CashflowTxSnapshot? = null
            var bestDelta = Long.MAX_VALUE
            for (inc in matchingIncomes) {
                if (inc.id in matchedIncomeIds) continue
                if (inc.bankName.trim() == exp.bankName.trim()) continue
                val delta = kotlin.math.abs(inc.timestamp - exp.timestamp)
                if (delta <= SELF_TRANSFER_MAX_WINDOW_MS && delta < bestDelta) {
                    bestDelta = delta
                    bestIncome = inc
                }
            }
            if (bestIncome != null) {
                excludedIds.add(exp.id)
                excludedIds.add(bestIncome.id)
                matchedIncomeIds.add(bestIncome.id)
                selfTransferPairs++
                selfTransferTotalToman += exp.totalAmountToman
                val jalali = JalaliCalendarUtils.timestampToJalali(exp.timestamp)
                selfTransferDetails.add(
                    SelfTransferPairDetail(
                        expenseTxId = exp.id,
                        incomeTxId = bestIncome.id,
                        fromBankName = exp.bankName.trim(),
                        toBankName = bestIncome.bankName.trim(),
                        amountToman = exp.totalAmountToman,
                        timestamp = exp.timestamp,
                        dateTimeLabel = "${jalali.dateWithYear} • ${jalali.timeStr}"
                    )
                )
            }
        }

        val totalIncome = incomes
            .filter { it.id !in excludedIds }
            .sumOf { it.userShareToman.takeIf { v -> v > 0L } ?: it.totalAmountToman }
        val totalExpense = expenses
            .filter { it.id !in excludedIds }
            .sumOf { it.userShareToman.takeIf { v -> v > 0L } ?: it.totalAmountToman }

        val safeReserved = reservedCommitmentsToman.coerceAtLeast(0L)
        val netBalance = totalIncome - totalExpense - safeReserved
        val healthStatus = when {
            netBalance > 0L -> CashflowHealthStatus.POSITIVE
            netBalance == 0L -> CashflowHealthStatus.BALANCED
            else -> CashflowHealthStatus.DEFICIT
        }

        return PaydayCashflowSummary(
            cycleLabel = window.label,
            totalIncomeToman = totalIncome,
            totalExpenseToman = totalExpense,
            reservedCommitmentsToman = safeReserved,
            excludedSelfTransferPairs = selfTransferPairs,
            excludedSelfTransferTotalToman = selfTransferTotalToman,
            netBalanceToman = netBalance,
            healthStatus = healthStatus,
            excludedSelfTransferDetails = selfTransferDetails.sortedByDescending { it.timestamp }
        )
    }
}

/**
 * Standalone reconstruction of Jibeman's SMS bank-detection and parsing rules.
 * Reconstructed from classes.dex (DEX 039) in the supplied base.zip.
 */
interface BankSmsParser {
    fun parse(rawMessage: String): JibemanSmsEngine.ParsedSms?
}

object JibemanSmsEngine : BankSmsParser {

    data class BankProfile(
        val nameFa: String,
        val nameEn: String,
        val icon: String,
        val senderIds: List<String>
    )

    data class ParsedSms(
        /** "in" for deposit, "out" for withdrawal; null is retained if bytecode permits it. */
        val direction: String?,
        val amount: Long,
        val balance: Long?,
        val date: String?,
        val time: String?
    )

    data class SmsSenderRow(val address: String, val dateMillis: Long)

    data class ParserSms(val address: String, val dateMillis: Long, val body: String)

    data class DetectedBank(val bank: BankProfile, val senderNumbers: List<String>)

    data class BankDetectionResult(
        val inboxRowsRead: Int,
        val banks: List<DetectedBank>
    ) {
        companion object {
            fun unreadable(): BankDetectionResult = BankDetectionResult(0, emptyList())
        }
    }

    private data class SenderHit(val address: String, val lastMillis: Long)

    /** Exact ordered profile list built in DefaultBankProfiles.<clinit>. */
    val BANK_PROFILES: List<BankProfile> = listOf(
        BankProfile("پاسارگاد", "Pasargad", "ic_bank_pasargad", listOf("b.pasargad", "B.Pasargad", "098500019000", "98500019000", "+98500019000")),
        BankProfile("اقتصاد نوین", "Eghtesad Novin", "ic_bank_eghtesad_novin", listOf("ENBank", "Enbank", "+9890004800", "90004800")),
        BankProfile("شهر", "Shahr", "ic_bank_shahr", listOf("+98200035", "20005", "20003502", "+98200085", "700820428285", "9200035", "98200035", "200035")),
        BankProfile("انصار", "Ansar", "ic_bank_ansar", listOf("+98200036", "100036", "98100038")),
        BankProfile("تجارت", "Tejarat", "ic_bank_tejarat", listOf("5000973189", "985000973189", "tejaratbank", "TejaratBank")),
        BankProfile("رفاه کارگران", "Refah", "ic_bank_refah", listOf("Refah", "REFAH", "REFAH BANK", "Refah Bank", "RefahBank")),
        BankProfile("سامان", "Saman", "ic_bank_saman", listOf("+9820000", "Saman Bank", "Saman", "500095", "SamanBank", "9999920000", "2000084080", "99999984080", "099999984080", "9899999984080", "+989999984080", "+9899999984080", "989999920000", "+989999920000")),
        BankProfile("سرمایه", "Sarmayeh", "ic_bank_sarmayeh", listOf("+98300058", "98300058", "7007058", "987007058", "+987007058")),
        BankProfile("سینا", "Sina", "ic_bank_sina", listOf("Sina Bank", "+9850003700798704", "9850003700798704", "50003700798704", "09850004756", "+9850004756", "9850004756", "50004756", "50004751", "+98300028", "500048", "500019", "98500048", "sina bank", "SinaBank", "sinabank")),
        BankProfile("صادرات ایران", "Saderat", "ic_bank_saderat", listOf("+987007851040", "+9830009419", "9830009419", "30009419", "983-000-9419", "+98200060", "+98200040", "+9820004008", "+98700719", "BankSaderat", "700710", "700718", "98700719", "700719", "7007190")),
        BankProfile("سپه", "Sepah", "ic_bank_sepah", listOf("100072419", "98100072419", "+98100072419", "SEPAH", "Sepah", "SEPAHBANK", "SEPAH BANK", "SepahBank", "Sepah Bank", "BankSepah", "Bank Sepah", "banksepah", "BANKSEPAH", "986715001", "+986715001", "6715001", "986715000", "6715000", "+986715000", "+986715000015", "986715000015", "+989122200207", "200015", "98200015", "+98200015", "2000150", "982000150", "+982000150", "6715000015", "+986830068400107", "98715000015", "6715000016", "6715000017", "6715000018", "30002015", "9830002015", "+9830002015", "500015", "98500015", "+98500015")),
        BankProfile("صنعت و معدن", "Industry & Mine", "ic_bank_sanat_madan", listOf("+9820004003", "+98100099", "100099")),
        BankProfile("قرض \u200Cالحسنه رسالت", "Resalat QH", "ic_bank_resalat", listOf("2000474701", "+982000474701", "982000474701", "Resalat", "resalat", "RESALAT", "ResalatBank", "Resalat Bank", "resalatbank", "50001474701", "9850001474701", "+9850001474701", "9850004747", "+9850004747", "989999904747", "9999904747", "50004747", "500014747", "+9820004747", "20004746", "20004747", "+98500014747", "9820004747")),
        BankProfile("قرض \u200Cالحسنه مهر ایران", "Mehr Iran", "ic_bank_mehr_iran", listOf("B.QMEHRIRAN")),
        BankProfile("قوامین", "Ghavamin", "ic_bank_ghavamin", listOf("+981000222", "+9820000222", "+981105151", "2000222", "2000228")),
        BankProfile("مسکن", "Maskan", "ic_bank_maskan", listOf("bank maskan", "+9810002503", "+9850004920", "+98500094", "100025", "98100025", "Bank Maskan", "9850004930", "BANK MASKAN", "BANKMASKAN", "BankMaskan")),
        BankProfile("ملت", "Mellat", "ic_bank_mellat", listOf("+9815560001", "+981000920000", "981000920000", "1000920000", "9815560001", "+9830007505", "+9820003304", "+9820003305", "+9830003304", "Bank Mellat", "30003305", "BankMellat", "500092000")),
        BankProfile("ملی ایران", "Melli Iran", "ic_bank_melli", listOf("+987007170", "98500043087", "300084731", "+989032229936", "+98700717", "+98200044", "+9820004000", "98700717", "700717", "9830009417", "+9830009417", "30009417", "983000941001", "200080", "3000941001", "9830009417", "Bank Melli", "+98700759", "98300094170", "+983000941001")),
        BankProfile("مهر اقتصاد", "Mehr Eghtesad", "ic_bank_mehr_eghtesad", listOf("+98200089", "+98100089", "+982000089", "+981000089")),
        BankProfile("پارسیان", "Parsian", "ic_bank_parsian", listOf("99902318", "99992318", "+98200082", "+98300054", "+98500024", "+9850002318", "+9850001099", "50001099", "300071", "9830007171", "9810005403", "PARSIANBANK", "ParsianBank")),
        BankProfile("پست ایران", "Post", "ic_bank_post", listOf("POSTBANK", "9840400108", "+9840400108", "40400108", "50004940", "+9820004940", "9820004940", "20004940", "+98200029", "+98100029", "50004949", "98700717", "9850004940", "98500009440", "+9850004940")),
        BankProfile("کارآفرین", "Karafarin", "ic_bank_karafarin", listOf("200057780", "B.Karafarin", "98200004321", "+9830004321", "30004321", "+98200004321", "50004858", "50004857", "98200002341", "981000004", "200004321")),
        BankProfile("کشاورزی", "Keshavarzi", "ic_bank_keshavarzi", listOf("+98300081301", "5000181301", "KESHAVARZI", "+989999944444", "9999944444", "989999944444")),
        BankProfile("ایران زمین", "Iran Zamin", "ic_bank_iran_zamin", listOf("IZBANK")),
        BankProfile("گردشگری", "Tourism", "ic_bank_gardeshgari", listOf("TourismBank", "+982000300", "982000309", "982000300")),
        BankProfile("کوثر", "Kosar", "ic_bank_kosar", listOf("+9850002477", "10002477", "9810002477", "6715014005", "98715014005")),
        BankProfile("توسعه تعاون", "Tosee Taavon", "ic_bank_tosee_taavon", listOf("ttbank", "TTBANK", "+9820006438", "+985000257", "5000157", "+985000157", "500158", "30005816", "+989810007000", "9810007000")),
        BankProfile("خاورمیانه", "Middle East", "ic_bank_khavar_mianeh", listOf("9820004861", "+9820004861", "20004861", "20004840", "+9820004860", "9820004860", "20004860")),
        BankProfile("دی", "Dey", "ic_bank_dey", listOf("2000766", "+9820004002", "+9820043", "+9830002726", "Day Bank", "Day", "+98300097500027", "3000766", "500018", "982000766", "DayBank", "98200766", "+982000766\u202C\u200F")),
        BankProfile("حکمت ایرانیان", "Hekmat Iranian", "ic_bank_hekmat", listOf("+9820008955")),
        BankProfile("مؤسسه اعتباری توسعه", "Tosee Credit Inst.", "ic_bank_tosee", listOf("+9830005816")),
        BankProfile("توسعه صادرات", "EDBI", "ic_bank_tosee_saderat", listOf("7000730", "+9830009430", "9830009430", "30009430")),
        BankProfile("مؤسسه اعتباری ملل", "Melal Credit Inst.", "ic_bank_melall", listOf("+98200022222")),
        BankProfile("مؤسسه اعتباری نور", "Noor Credit Inst.", "ic_bank_noor", listOf("9830009480", "30009480", "+9820004009", "7007780", "20004293", "0200080947001", "0200002734006")),
        BankProfile("بلو", "Blu", "ic_bank_blu", listOf("+982187641", "98300087641", "300087641", "989999987641", "9999987641", "+989999987641")),
        BankProfile("ویپاد", "wepod", "ic_bank_wepod", listOf("+981000214", "98500011", "5000114", "+985000114", "985000114", "981000214", "1000214", "9830009017", "30009017")),
        BankProfile("پارسیان", "Parsian", "ic_bank_parsian", listOf("9830007171", "9899902318")),
        BankProfile("بانکینو", "bankino", "ic_bank_bankino", listOf("20004860")),
        BankProfile("ایران ونزوئلا", "iran venezuela", "ic_bank_iran_venezuela", listOf("20004860")),
    )

    private val numberToProfile: Map<String, BankProfile> by lazy {
        LinkedHashMap<String, BankProfile>().apply {
            for (profile in BANK_PROFILES) {
                for (number in profile.senderIds) put(number, profile)
            }
        }
    }

    private val lowercaseNumberToProfile: Map<String, BankProfile> by lazy {
        LinkedHashMap<String, BankProfile>().apply {
            for ((number, profile) in numberToProfile) {
                putIfAbsent(number.lowercase(Locale.ROOT), profile)
            }
        }
    }

    fun hasUssdMark(sender: String): Boolean = sender.indexOf('*') >= 0 || sender.indexOf('#') >= 0

    fun toLatinDigits(sender: String): String {
        val out = StringBuilder(sender.length)
        for (ch in sender) {
            val cp = ch.code
            when {
                cp in 0x06F0..0x06F9 -> out.append((cp - 0x06F0 + '0'.code).toChar())
                cp in 0x0660..0x0669 -> out.append((cp - 0x0660 + '0'.code).toChar())
                else -> out.append(ch)
            }
        }
        return out.toString()
    }

    fun normalizeSender(sender: String): String {
        var value = toLatinDigits(sender).lowercase(Locale.ROOT).trim()
            .filter { it.isLetterOrDigit() }
        if (value.startsWith("0098")) {
            value = value.substring(4)
        } else if (value.startsWith("98") && value.length > 8) {
            value = value.substring(2)
        }
        return value
    }

    fun senderMatches(first: String, second: String): Boolean {
        if (hasUssdMark(first) || hasUssdMark(second)) return false
        val a = normalizeSender(first)
        val b = normalizeSender(second)
        if (a.isEmpty() || b.isEmpty()) return false
        if (a == b) return true
        val bothNumeric = a.all { it.isDigit() } && b.all { it.isDigit() }
        return bothNumeric && a.length >= 5 && b.length >= 5 &&
            (a.endsWith(b) || b.endsWith(a))
    }

    private val customSenderToProfile = java.util.concurrent.ConcurrentHashMap<String, BankProfile>()
    private val resolveBankCache = java.util.concurrent.ConcurrentHashMap<String, Any>()
    private val NO_BANK_SENTINEL = Any()

    private val normalizedExactBankMap: Map<String, BankProfile> by lazy {
        val map = HashMap<String, BankProfile>(512)
        for (profile in BANK_PROFILES) {
            for (candidateRaw in profile.senderIds) {
                if (hasUssdMark(candidateRaw)) continue
                val norm = normalizeSender(candidateRaw)
                if (norm.isNotEmpty()) {
                    map.putIfAbsent(norm, profile)
                }
            }
        }
        map
    }

    private val normalizedNumericCandidates: List<Pair<String, BankProfile>> by lazy {
        val list = ArrayList<Pair<String, BankProfile>>(256)
        for (profile in BANK_PROFILES) {
            for (candidateRaw in profile.senderIds) {
                if (hasUssdMark(candidateRaw)) continue
                val norm = normalizeSender(candidateRaw)
                if (norm.length >= 5 && norm.all { it.isDigit() }) {
                    list.add(norm to profile)
                }
            }
        }
        list
    }

    fun unregisterCustomSender(senderAddress: String) {
        val cleanSender = senderAddress.trim()
        if (cleanSender.isEmpty()) return
        customSenderToProfile.remove(cleanSender)
        customSenderToProfile.remove(cleanSender.lowercase(Locale.ROOT))
        val norm = normalizeSender(cleanSender)
        if (norm.isNotEmpty()) {
            customSenderToProfile.remove(norm)
        }
        resolveBankCache.clear()
    }

    fun isKnownBankName(bankNameFa: String): Boolean {
        val clean = bankNameFa.trim().replace("\u200C", " ")
        if (clean.isEmpty()) return false
        return BANK_PROFILES.any {
            it.nameFa == bankNameFa.trim() ||
                it.nameFa.replace("\u200C", " ") == clean
        }
    }

    fun registerCustomSenderForBank(senderAddress: String, bankNameFa: String) {
        val cleanSender = senderAddress.trim()
        if (cleanSender.isEmpty()) return
        val profile = BANK_PROFILES.firstOrNull {
            it.nameFa == bankNameFa ||
                it.nameFa.replace("\u200C", " ") == bankNameFa.replace("\u200C", " ") ||
                it.nameFa.contains(bankNameFa) ||
                bankNameFa.contains(it.nameFa)
        } ?: BankProfile(
            nameFa = bankNameFa,
            nameEn = bankNameFa,
            icon = "ic_bank_blu",
            senderIds = listOf(cleanSender)
        )
        customSenderToProfile[cleanSender] = profile
        customSenderToProfile[cleanSender.lowercase(Locale.ROOT)] = profile
        val norm = normalizeSender(cleanSender)
        if (norm.isNotEmpty()) {
            customSenderToProfile[norm] = profile
        }
        resolveBankCache.clear()
    }

    fun findByNumber(sender: String): BankProfile? =
        customSenderToProfile[sender]
            ?: customSenderToProfile[sender.trim().lowercase(Locale.ROOT)]
            ?: numberToProfile[sender]
            ?: lowercaseNumberToProfile[sender.trim().lowercase(Locale.ROOT)]

    fun resolveBank(sender: String): BankProfile? {
        val cached = resolveBankCache[sender]
        if (cached != null) {
            return if (cached === NO_BANK_SENTINEL) null else cached as BankProfile
        }

        val resolved = resolveBankUncached(sender)
        if (resolveBankCache.size > 4096) {
            resolveBankCache.clear()
        }
        resolveBankCache[sender] = resolved ?: NO_BANK_SENTINEL
        return resolved
    }

    private fun resolveBankUncached(sender: String): BankProfile? {
        findByNumber(sender)?.let { return it }
        if (hasUssdMark(sender)) return null
        val normalized = normalizeSender(sender)
        if (normalized.isEmpty()) return null
        customSenderToProfile[normalized]?.let { return it }
        normalizedExactBankMap[normalized]?.let { return it }

        if (normalized.length >= 5 && normalized.all { it.isDigit() }) {
            for ((candidate, profile) in normalizedNumericCandidates) {
                if (normalized.endsWith(candidate) || candidate.endsWith(normalized)) {
                    return profile
                }
            }
        }
        return null
    }

    fun detectBanks(rows: List<SmsSenderRow>): BankDetectionResult {
        if (rows.isEmpty()) return BankDetectionResult.unreadable()

        val rowsByNormalizedSender = LinkedHashMap<String, MutableList<SmsSenderRow>>()
        for (row in rows) {
            val normalized = normalizeSender(row.address)
            if (normalized.isEmpty()) continue
            rowsByNormalizedSender.getOrPut(normalized) { mutableListOf() }.add(row)
        }

        val hitsByEnglishName = LinkedHashMap<String, Pair<BankProfile, MutableList<SenderHit>>>()
        for (sameSenderRows in rowsByNormalizedSender.values) {
            var newest = sameSenderRows.first()
            for (candidate in sameSenderRows.drop(1)) {
                if (candidate.dateMillis > newest.dateMillis) newest = candidate
            }
            val bank = resolveBank(newest.address) ?: continue
            val bucket = hitsByEnglishName.getOrPut(bank.nameEn) { bank to mutableListOf() }
            bucket.second += SenderHit(newest.address, newest.dateMillis)
        }

        val detected = hitsByEnglishName.values
            .sortedByDescending { entry -> entry.second.maxOfOrNull { it.lastMillis } ?: Long.MIN_VALUE }
            .map { (bank, hits) ->
                DetectedBank(
                    bank,
                    hits.sortedByDescending { it.lastMillis }.map { it.address }
                )
            }
        return BankDetectionResult(rows.size, detected)
    }

    // ------------------------- GenericSmsParser -------------------------

    private val depositKeywords = listOf(
        "واریز", "دریافت", "بستانکار", "افزایش", "عودت", "برگشت", "نشست", "به حساب شما"
    )
    private val withdrawalKeywords = listOf(
        "برداشت", "خرید", "خریداری", "پرداخت", "بدهکار", "کاهش", "انتقال", "حواله", "کارمزد", "قبض"
    )
    private val depositPositional = listOf("به حساب", "بستانکار")
    private val withdrawPositional = listOf("از حساب", "از سپرده")
    private val contextWithdraw = listOf("کارت اعتباری")
    private val notificationMarkers = listOf("شناسه تایید", "متشکریم", "سپاسگزاریم")
    private val amountLabels = listOf("مبلغ")
    private val balanceLabels = listOf(
        "موجودی قابل برداشت", "مانده قابل برداشت",
        "موجودی حساب", "مانده حساب",
        "موجودی فعلی", "مانده فعلی",
        "موجودی جدید", "مانده جدید",
        "موجودی", "مانده"
    )

    private const val AFTER_LABEL = "[^\\d]{0,18}?([\\d][\\d,]*)"
    private const val NON_MAIN_BALANCE_LOOKAHEAD = "(?!\\s*(?:باکس|قلک|وام|تسهیلات|قسط|بدهی|امتیاز|هدف|هدیه|بسته|اینترنت|شارژ))"
    private val amountLabelRegexes = amountLabels.map {
        Regex(Pattern.quote(it) + AFTER_LABEL)
    }
    private val balanceLabelRegexes = balanceLabels.map {
        Regex(Pattern.quote(it) + NON_MAIN_BALANCE_LOOKAHEAD + AFTER_LABEL)
    }
    private val strictBalanceLabelRegexes = balanceLabels.map {
        Regex(Pattern.quote(it) + NON_MAIN_BALANCE_LOOKAHEAD + "\\s*[:：\\-=،]?\\s*(?:مبلغ\\s*)?([\\d][\\d,]*)")
    }
    private val nonMainBalanceContextKeywords = listOf(
        "باکس", "قلک", "پس انداز خودکار", "پس‌انداز خودکار",
        "وام", "تسهیلات", "قسط", "بدهی", "امتیاز",
        "قرعه‌کشی", "قرعه کشی", "جایزه", "پیشنهاد",
        "حداقل موجودی", "کافی نیست", "ناموفق", "عدم موجودی",
        "کمبود موجودی", "سقف", "مسدود", "بلوکه", "کارت هدیه"
    )

    private val signedGroupedRegex = Regex("([+\\-−])?\\s*(\\d{1,3}(?:,\\d{3})+)")
    private val trailingSignRegex = Regex("\\d{1,3}(?:,\\d{3})+\\s*([+\\-−])")
    private val leadingSignRegex = Regex("([+\\-−])\\s*\\d{1,3}(?:,\\d{3})+")
    private val dateRegex = Regex("\\b\\d{2,4}[/-]\\d{1,2}[/-]\\d{1,2}\\b")
    private val timeRegex = Regex("\\b\\d{1,2}:\\d{2}(?::\\d{2})?\\b")
    private val otpRegex = Regex("(?<![ء-ۿA-Za-z])(?:رمز|پویا|کد\\s*تایید|کد\\s*تأیید|otp|code)")

    fun normalizeMessage(message: String): String {
        val out = StringBuilder(message.length)
        for (ch in message) {
            val cp = ch.code
            when {
                cp == 0x064A || cp == 0x0649 -> out.append('\u06CC')
                cp == 0x0643 -> out.append('\u06A9')
                cp in 0x06F0..0x06F9 -> out.append((cp - 0x06F0 + '0'.code).toChar())
                cp in 0x0660..0x0669 -> out.append((cp - 0x0660 + '0'.code).toChar())
                isBidi(cp) -> Unit
                else -> out.append(ch)
            }
        }
        return out.toString().replace("\r\n", "\n")
    }

    private fun isBidi(codePoint: Int): Boolean =
        codePoint == 8203 || codePoint == 8204 || codePoint == 8205 ||
            codePoint == 8206 || codePoint == 8207 || codePoint == 65279 ||
            codePoint in 8234..8238 || codePoint in 8294..8297

    private fun numberAfterLabel(text: String, patterns: List<Regex>): Long? {
        for (pattern in patterns) {
            val match = pattern.find(text) ?: continue
            val raw = match.groupValues.getOrNull(1) ?: continue
            val parsed = parseGrouped(raw)
            if (parsed != null) return parsed
        }
        return null
    }

    private fun parseGrouped(raw: String): Long? {
        val compact = raw.replace(",", "")
        if (compact.length !in 1..12) return null
        val value = compact.toLongOrNull() ?: return null
        return value.takeIf { it >= 0L }
    }

    private fun keywordDirectionNear(text: String, amountStart: Int): String? {
        if (amountStart <= 0) return null
        val startIndex = minOf(amountStart - 1, text.length - 1)
        var bestIndex = -1
        var direction: String? = null
        for (keyword in depositKeywords) {
            val index = text.lastIndexOf(keyword, startIndex, ignoreCase = true)
            if (index >= 0 && index <= startIndex && index > bestIndex) {
                bestIndex = index
                direction = "in"
            }
        }
        for (keyword in withdrawalKeywords) {
            val index = text.lastIndexOf(keyword, startIndex, ignoreCase = true)
            if (index >= 0 && index <= startIndex && index > bestIndex) {
                bestIndex = index
                direction = "out"
            }
        }
        return direction
    }

    private fun adjacentSign(text: String): String {
        trailingSignRegex.find(text)?.groupValues?.getOrNull(1)?.let { if (it.isNotEmpty()) return it }
        leadingSignRegex.find(text)?.groupValues?.getOrNull(1)?.let { if (it.isNotEmpty()) return it }
        return ""
    }

    private fun signChars(ch: Char?): String = when (ch) {
        '+' -> "+"
        '-', '−' -> ch.toString()
        else -> ""
    }

    /**
     * Extracts main account balance (in Rials) from non-OTP, non-sub-account balance-only bank SMS.
     * Explicitly rejects Blu Box / Piggybank / Loan / Installment / Failed-transaction / Promo SMS
     * so sub-account or promotional figures can NEVER overwrite a bank's real primary account balance.
     */
    fun extractBalanceRial(rawMessage: String): Long? {
        val text = normalizeMessage(rawMessage)
        if (otpRegex.containsMatchIn(text)) return null
        if (nonMainBalanceContextKeywords.any { text.contains(it) }) return null
        return numberAfterLabel(text, strictBalanceLabelRegexes)
    }

    override fun parse(rawMessage: String): ParsedSms? {
        val text = normalizeMessage(rawMessage)
        if (otpRegex.containsMatchIn(text)) return null

        val balance = numberAfterLabel(text, balanceLabelRegexes)
        if (balance == null && notificationMarkers.any { text.contains(it) }) return null

        var amount: Long? = null
        var amountStart = -1
        var amountFromLabel = false
        var sign = ""

        for (pattern in amountLabelRegexes) {
            val match = pattern.find(text) ?: continue
            val group = match.groups[1] ?: continue
            val parsed = parseGrouped(group.value) ?: continue
            if (parsed <= 0L) continue
            amount = parsed
            amountStart = group.range.first
            val signBefore = signChars(text.getOrNull(group.range.first - 1))
            val signAfter = signChars(text.getOrNull(group.range.last + 1))
            sign = if (signBefore.isNotEmpty()) signBefore else signAfter
            amountFromLabel = true
            break
        }

        if (amount == null) {
            for (match in signedGroupedRegex.findAll(text)) {
                val numberGroup = match.groups[2] ?: continue
                val parsed = parseGrouped(numberGroup.value) ?: continue
                if (parsed <= 0L) continue
                if (balance != null && parsed == balance) continue

                amount = parsed
                amountStart = numberGroup.range.first
                val explicitSign = match.groupValues.getOrNull(1).orEmpty()
                val signAfterNumber = signChars(text.getOrNull(numberGroup.range.last + 1))
                sign = if (explicitSign.isNotEmpty()) explicitSign else signAfterNumber
                break
            }
        }

        val parsedAmount = amount ?: return null
        if (parsedAmount <= 0L) return null

        var direction = keywordDirectionNear(text, amountStart)
        if (direction == null) {
            direction = when {
                depositKeywords.any { text.contains(it) } -> "in"
                withdrawalKeywords.any { text.contains(it) } -> "out"
                else -> null
            }
        }

        if (sign.isEmpty()) sign = adjacentSign(text)
        if (direction == null) {
            direction = when (sign) {
                "+" -> "in"
                "-", "−" -> "out"
                else -> null
            }
        }

        if (direction == null) {
            direction = when {
                depositPositional.any { text.contains(it) } -> "in"
                withdrawPositional.any { text.contains(it) } -> "out"
                contextWithdraw.any { text.contains(it) } -> "out"
                else -> null
            }
        }

        if (balance == null && (!amountFromLabel || direction == null)) return null

        val date = dateRegex.find(text)?.value
        val time = timeRegex.find(text)?.value
        return ParsedSms(direction, parsedAmount, balance, date, time)
    }

    fun dedupPayload(address: String, dateMillis: Long, body: String): String =
        normalizeSender(address) + "|" + dateMillis.toString() + "|" + body

    fun dedupKeyOf(sms: ParserSms): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(dedupPayload(sms.address, sms.dateMillis, sms.body).toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    fun dedupKey(address: String, dateMillis: Long, body: String): String =
        dedupKeyOf(ParserSms(address, dateMillis, body))

    fun parserCatalog(): Map<String, Any> = linkedMapOf(
        "depositKeywords" to depositKeywords,
        "withdrawalKeywords" to withdrawalKeywords,
        "depositPositional" to depositPositional,
        "withdrawPositional" to withdrawPositional,
        "contextWithdraw" to contextWithdraw,
        "notificationMarkers" to notificationMarkers,
        "amountLabels" to amountLabels,
        "balanceLabels" to balanceLabels,
        "afterLabelRegex" to AFTER_LABEL,
        "signedGroupedRegex" to signedGroupedRegex.pattern,
        "trailingSignRegex" to trailingSignRegex.pattern,
        "leadingSignRegex" to leadingSignRegex.pattern,
        "dateRegex" to dateRegex.pattern,
        "timeRegex" to timeRegex.pattern,
        "otpRegex" to otpRegex.pattern
    )
}

data class ParsedSmsResult(
    val bankName: String,
    val cardLast4: String,
    val cardLabel: String,
    val rawAmountRial: Long,
    val amountToman: Long,
    val extractedBalanceToman: Long?,
    val isIncome: Boolean,
    val isMicroFee: Boolean,
    val isSelfTransfer: Boolean,
    val isSalaryDeposit: Boolean,
    val isUntaggedPurchase: Boolean,
    val isValidFinancialSms: Boolean,
    val dedupKey: String,
    val weekdayDate: String,
    val jalaliYear: Int,
    val timeStr: String,
    val detectedTitle: String,
    val detectedStore: String,
    val detectedPurpose: String,
    val summaryMessage: String
) {
    val subtitle: String
        get() = "$weekdayDate • $timeStr • $cardLabel"
}

object BankSmsParserEngine {

    fun parseWithJibemanEngine(
        senderAddress: String,
        body: String,
        timestampMillis: Long
    ): ParsedSmsResult? {
        val bankProfile = JibemanSmsEngine.resolveBank(senderAddress) ?: return null
        val parsed = JibemanSmsEngine.parse(body) ?: return null

        val amountToman = parsed.amount / 10L
        if (amountToman <= 0L) return null

        val balanceToman = parsed.balance?.div(10L)
        val isDeposit = (parsed.direction == "in")
        val bankName = bankProfile.nameFa
        val uniqueDedupKey = JibemanSmsEngine.dedupKey(senderAddress, timestampMillis, body)

        val realJalali = JalaliCalendarUtils.timestampToJalali(timestampMillis)
        val normalizedBody = JibemanSmsEngine.normalizeMessage(body)

        val maskedCardRegex = Regex("""(?:[0-9]{4,6}[*xX#-]+([0-9]{4}))|(?:([0-9]{4})[*xX#-]+[0-9]{4,6})""")
        val labeledCardRegex = Regex("""(?:کارت|حساب|سپرده)[:\s*#-]*([0-9]{4,16})""")
        val maskedMatch = maskedCardRegex.find(normalizedBody)
        val rawLast4 = when {
            maskedMatch != null -> maskedMatch.groupValues[1].ifEmpty { maskedMatch.groupValues[2] }
            else -> {
                val digits = labeledCardRegex.find(normalizedBody)?.groupValues?.get(1).orEmpty()
                if (digits.length >= 4) digits.takeLast(4) else ""
            }
        }
        val persianCard4 = PersianNumberUtils.toPersianDigits(rawLast4)
        val cardLabel = if (persianCard4.isNotEmpty()) {
            "کارت $bankName $persianCard4"
        } else {
            "کارت $bankName"
        }

        val isMicroFee = !isDeposit &&
            (normalizedBody.contains("کارمزد") || normalizedBody.contains("آبونمان") || normalizedBody.contains("هزینه پیامک")) &&
            amountToman in 1L..5_000L

        val isSelfTransfer = normalizedBody.contains("کارت خود") ||
            normalizedBody.contains("حساب خود") ||
            normalizedBody.contains("انتقال خودی")

        val isSalaryDeposit = isDeposit && (
            normalizedBody.contains("حقوق") ||
                normalizedBody.contains("پاداش") ||
                (normalizedBody.contains("پایا") && amountToman >= 12_000_000L)
            )

        val isUntaggedPurchase = !isDeposit && !isMicroFee && !isSelfTransfer

        val detectedStore = when {
            isMicroFee -> "شبکه بانکی شاپرک"
            isSelfTransfer -> "بین کارت‌های خودی ($bankName)"
            isSalaryDeposit -> "واریز حقوق ماهانه ($bankName)"
            isDeposit -> "واریز بانکی ($bankName)"
            else -> bankName
        }

        val detectedPurpose = when {
            isMicroFee -> "کارمزد خرد بانکی (نادیده گرفته شده)"
            isSelfTransfer -> "جابه‌جایی داخلی بدون اثر بر تراز"
            isSalaryDeposit -> "شارژ ماه حقوقی جدید"
            isDeposit -> "افزایش موجودی حساب"
            else -> ""
        }

        val detectedTitle = when {
            isMicroFee -> "کارمزد خرد بانکی"
            isSelfTransfer -> "انتقال بین بانکی خودی ($bankName)"
            isSalaryDeposit -> "واریز حقوق ($bankName)"
            isDeposit -> "واریز به $cardLabel"
            else -> "برداشت از $cardLabel"
        }

        val summaryMessage = "پیامک بانک $bankName به مبلغ ${PersianNumberUtils.formatToman(amountToman, true)} ثبت شد."

        return ParsedSmsResult(
            bankName = bankName,
            cardLast4 = persianCard4,
            cardLabel = cardLabel,
            rawAmountRial = parsed.amount,
            amountToman = amountToman,
            extractedBalanceToman = balanceToman,
            isIncome = isDeposit,
            isMicroFee = isMicroFee,
            isSelfTransfer = isSelfTransfer,
            isSalaryDeposit = isSalaryDeposit,
            isUntaggedPurchase = isUntaggedPurchase,
            isValidFinancialSms = true,
            dedupKey = uniqueDedupKey,
            weekdayDate = realJalali.dateWithYear,
            jalaliYear = realJalali.year,
            timeStr = realJalali.timeStr,
            detectedTitle = detectedTitle,
            detectedStore = detectedStore,
            detectedPurpose = detectedPurpose,
            summaryMessage = summaryMessage
        )
    }

    /**
     * Identifies banks ONLY via `JibemanSmsEngine.resolveBank(senderAddress)` (plus custom sender numbers
     * explicitly linked by the user). NEVER guesses bank names from SMS body text so promo SMS like
     * "گردونه ملی گلد" are 100% rejected.
     */
    fun detectIranianBankName(rawSms: String, senderAddress: String = ""): String {
        if (senderAddress.isNotBlank()) {
            JibemanSmsEngine.resolveBank(senderAddress)?.let { return it.nameFa }
        }
        return "بانک"
    }

    fun parseBankSms(
        rawSms: String,
        timestampMillis: Long = System.currentTimeMillis(),
        senderAddress: String = ""
    ): ParsedSmsResult {
        if (senderAddress.isNotBlank()) {
            parseWithJibemanEngine(senderAddress, rawSms, timestampMillis)?.let { return it }
        }
        val jibemanParsed = JibemanSmsEngine.parse(rawSms)
        val bankName = detectIranianBankName(rawSms, senderAddress)
        val realJalali = JalaliCalendarUtils.timestampToJalali(timestampMillis)
        val amountRial = jibemanParsed?.amount ?: 0L
        val amountToman = amountRial / 10L
        val balanceToman = jibemanParsed?.balance?.div(10L)
        val isDeposit = jibemanParsed?.direction == "in"
        val dedupKey = JibemanSmsEngine.dedupKey(senderAddress, timestampMillis, rawSms)

        val cardLabel = "کارت $bankName"
        return ParsedSmsResult(
            bankName = bankName,
            cardLast4 = "",
            cardLabel = cardLabel,
            rawAmountRial = amountRial,
            amountToman = amountToman,
            extractedBalanceToman = balanceToman,
            isIncome = isDeposit,
            isMicroFee = false,
            isSelfTransfer = false,
            isSalaryDeposit = isDeposit && rawSms.contains("حقوق"),
            isUntaggedPurchase = !isDeposit,
            isValidFinancialSms = jibemanParsed != null && amountToman > 0L,
            dedupKey = dedupKey,
            weekdayDate = realJalali.dateWithYear,
            jalaliYear = realJalali.year,
            timeStr = realJalali.timeStr,
            detectedTitle = if (isDeposit) "واریز به $cardLabel" else "برداشت از $cardLabel",
            detectedStore = if (isDeposit) "واریز بانکی ($bankName)" else bankName,
            detectedPurpose = if (isDeposit) "افزایش موجودی حساب" else "",
            summaryMessage = "پیامک بانک $bankName به مبلغ ${PersianNumberUtils.formatToman(amountToman, true)} ثبت شد."
        )
    }
}

typealias ParsedVoiceCommand = ParsedVoiceExpense

data class ParsedVoiceExpense(
    val recognizedSpeech: String,
    val totalAmountToman: Long,
    val userShareToman: Long,
    val dongAmountToman: Long,
    val dongFriendName: String,
    val storeName: String,
    val purposeCategory: String,
    val cardLabel: String,
    val category: String = "خوراکی و روزمره",
    val merchant: String = "",
    val isIncome: Boolean = false,
    val commitmentKind: String? = null, // null = regular tx, "DEBT" = بدهی/برنامه خرید آینده, "RECEIVABLE" = طلب/درآمد آینده
    val dueDateHint: String = "",
    val eventDayOffset: Int = 0,        // e.g. -1 = دیروز/دیشب, -2 = پریروز
    val commitmentSubLabel: String = "", // e.g. "برنامه خرید", "درآمد آینده", "بدهی", "طلب"
    val matchedBankTxId: Long? = null   // Non-null when auto-matched / user-linked to an untagged bank SMS row
) {
    val title: String
        get() = purposeCategory

    val amountToman: Long
        get() = totalAmountToman

    val isCommitment: Boolean
        get() = commitmentKind != null
}

/**
 * Lightweight projection of an existing untagged bank SMS transaction used for
 * batch voice auto-matching ("تطبیق خودکار ویس با پیامک‌های بانکی").
 */
data class UntaggedSmsCandidate(
    val id: Long,
    val bankName: String,
    val amountToman: Long,
    val isIncome: Boolean,
    val timestamp: Long,
    val jalaliDate: String,
    val timeStr: String,
    val cardInfo: String
)

/**
 * Result of matching a single spoken voice item against untagged same-day bank SMS transactions.
 * - [matchedCandidate]: exact amount + same Jalali day + same direction match (auto-linked by default).
 * - [suggestedCandidate]: closest remaining unclaimed same-day bank SMS of the same direction when no exact
 *   match exists (so user saves as cash by default OR links to the closest bank SMS with 1 tap).
 */
data class VoiceSmsMatchResult(
    val matchedCandidate: UntaggedSmsCandidate? = null,
    val suggestedCandidate: UntaggedSmsCandidate? = null,
    val isExactAutoMatch: Boolean = false
)

/**
 * Pure P0/P1 batch voice-to-bank-SMS matcher:
 * 1. Never matches future commitments (`isCommitment == true`) or zero-amount items.
 * 2. Matches strictly within the spoken item's target Jalali day (`nowMillis + eventDayOffset * 86_400_000L`)
 *    and matching transaction direction (`INCOME` vs `EXPENSE`).
 * 3. Performs 1-to-1 greedy exact-amount matching first so multiple identical-amount purchases on the
 *    same day map cleanly to distinct bank SMS rows without double-claiming.
 * 4. For remaining unmatched spoken items, suggests the closest unclaimed same-day untagged bank SMS
 *    (handling rounding or transfer fees) so the user can choose cash vs. link with 1 tap.
 */
object OrosiVoiceSmsMatcher {
    private const val DAY_MS = 86_400_000L

    fun matchBatch(
        items: List<ParsedVoiceExpense>,
        untaggedBankSms: List<UntaggedSmsCandidate>,
        nowMillis: Long = System.currentTimeMillis()
    ): List<VoiceSmsMatchResult> {
        if (items.isEmpty()) return emptyList()
        if (untaggedBankSms.isEmpty()) return List(items.size) { VoiceSmsMatchResult() }

        val sortedCandidates = untaggedBankSms.sortedByDescending { it.timestamp }
        val claimedIds = HashSet<Long>()
        val results = MutableList(items.size) { VoiceSmsMatchResult() }
        val itemTargetDates = Array(items.size) { idx ->
            val offset = items[idx].eventDayOffset
            JalaliCalendarUtils.timestampToJalali(nowMillis + offset * DAY_MS).dateWithYear
        }

        // Pass 1: 1-to-1 exact amount + same Jalali day + same direction
        for (i in items.indices) {
            val item = items[i]
            if (item.isCommitment || item.totalAmountToman <= 0L) continue
            val targetDate = itemTargetDates[i]
            val exact = sortedCandidates.firstOrNull { cand ->
                cand.id !in claimedIds &&
                    cand.jalaliDate == targetDate &&
                    cand.isIncome == item.isIncome &&
                    cand.amountToman == item.totalAmountToman
            }
            if (exact != null) {
                claimedIds.add(exact.id)
                results[i] = VoiceSmsMatchResult(
                    matchedCandidate = exact,
                    suggestedCandidate = null,
                    isExactAutoMatch = true
                )
            }
        }

        // Pass 2: Closest unclaimed same-day same-direction suggestion for remaining non-commitment items
        for (i in items.indices) {
            if (results[i].matchedCandidate != null) continue
            val item = items[i]
            if (item.isCommitment || item.totalAmountToman <= 0L) continue
            val targetDate = itemTargetDates[i]
            val sameDayPool = sortedCandidates.filter { cand ->
                cand.id !in claimedIds &&
                    cand.jalaliDate == targetDate &&
                    cand.isIncome == item.isIncome
            }
            if (sameDayPool.isEmpty()) continue

            val closest = sameDayPool.minByOrNull { cand ->
                kotlin.math.abs(cand.amountToman - item.totalAmountToman)
            } ?: continue

            val diff = kotlin.math.abs(closest.amountToman - item.totalAmountToman)
            val maxAmount = kotlin.math.max(closest.amountToman, item.totalAmountToman).coerceAtLeast(1L)
            val isReasonablyClose = diff <= 50_000L || (diff.toDouble() / maxAmount.toDouble()) <= 0.35 || sameDayPool.size <= 2
            if (isReasonablyClose) {
                results[i] = VoiceSmsMatchResult(
                    matchedCandidate = null,
                    suggestedCandidate = closest,
                    isExactAutoMatch = false
                )
            }
        }

        return results
    }
}

object PersianVoiceSlangParserEngine {
    private val exactPhraseAmounts = listOf(
        "یک میلیون و پانصد" to 1_500_000L,
        "یک میلیون و پونصد" to 1_500_000L,
        "یک میلیون و دویست" to 1_200_000L,
        "یک و نیم میلیون" to 1_500_000L,
        "دو و نیم میلیون" to 2_500_000L,
        "سه و نیم میلیون" to 3_500_000L,
        "نیم میلیون" to 500_000L
    )

    private val numberWordTokens = mapOf(
        "یک" to 1L,
        "یه" to 1L,
        "دو" to 2L,
        "سه" to 3L,
        "چهار" to 4L,
        "پنج" to 5L,
        "شش" to 6L,
        "شیش" to 6L,
        "هفت" to 7L,
        "هشت" to 8L,
        "نه" to 9L,
        "ده" to 10L,
        "یازده" to 11L,
        "دوازده" to 12L,
        "سیزده" to 13L,
        "چهارده" to 14L,
        "پانزده" to 15L,
        "پونزده" to 15L,
        "شانزده" to 16L,
        "شونزده" to 16L,
        "هفده" to 17L,
        "هجده" to 18L,
        "هیجده" to 18L,
        "نوزده" to 19L,
        "بیست" to 20L,
        "سی" to 30L,
        "چهل" to 40L,
        "پنجاه" to 50L,
        "شصت" to 60L,
        "هفتاد" to 70L,
        "هشتاد" to 80L,
        "نود" to 90L,
        "صد" to 100L,
        "یکصد" to 100L,
        "دویست" to 200L,
        "سیصد" to 300L,
        "چهارصد" to 400L,
        "چارصد" to 400L,
        "پانصد" to 500L,
        "پونصد" to 500L,
        "ششصد" to 600L,
        "شیشصد" to 600L,
        "هفتصد" to 700L,
        "هشتصد" to 800L,
        "نهصد" to 900L
    )

    private val knownBrandsAndStores = listOf(
        "ایرانسل" to "ایرانسل",
        "همراه اول" to "همراه اول",
        "رایتل" to "رایتل",
        "شاتل" to "شاتل",
        "آسیاتک" to "آسیاتک",
        "اسنپ‌مارکت" to "اسنپ‌مارکت",
        "اسنپ مارکت" to "اسنپ‌مارکت",
        "اسنپمارکت" to "اسنپ‌مارکت",
        "اسنپ فود" to "اسنپ‌فود",
        "اسنپ" to "اسنپ",
        "تپسی" to "تپسی",
        "دیجی‌کالا" to "دیجی‌کالا",
        "دیجی کالا" to "دیجی‌کالا",
        "دیجیکالا" to "دیجی‌کالا",
        "افق کوروش" to "افق کوروش",
        "هایپراستار" to "هایپراستار",
        "شهروند" to "فروشگاه شهروند",
        "نانوایی" to "نانوایی",
        "نونوایی" to "نونوایی",
        "داروخانه" to "داروخانه",
        "سوپرمارکت" to "سوپرمارکت",
        "کافه لمیز" to "کافه لمیز",
        "رستوران نایب" to "رستوران نایب"
    )

    private val multiWordConversationalFillers = listOf(
        "سلام علیکم",
        "خسته نباشید",
        "فکر کنم",
        "فکر میکنم",
        "فکر می‌کنم",
        "یادم نیست",
        "یادم نمیاد",
        "یادم نمی‌آد",
        "یادم هست",
        "یادمه که",
        "بعد یادمه",
        "چی بود",
        "پرداخت کردم",
        "کارت کشیدم",
        "واریز کردم",
        "دریافت کردم",
        "دریافت شد",
        "واریز شد",
        "به حسابم اومد",
        "به حسابم آمد",
        "به حسابم نشست",
        "به کارتم ریخت",
        "به کارتم زد",
        "برام ریختن",
        "برام ریختند",
        "برایم ریختند",
        "برایم ریختن",
        "برام واریز کردن",
        "برام واریز کردند",
        "واریز کردن",
        "واریز کردند",
        "برام زدن",
        "برام زدند",
        "بهم داد",
        "به من داد",
        "خرید کردم",
        "خرج کردم",
        "پول دادم",
        "حساب کردم",
        "رفتم بیرون",
        "بیرون رفتم",
        "اشتباه شد",
        "به مبلغ",
        "به جاش",
        "به جایش",
        "به جای آن"
    )

    // Note: "برای", "بابت", "بابتِ", "واسه" are intentionally NOT in singleWordConversationalFillers
    // so they are preserved when connecting two meaningful nouns (Rule B: "انتقال پول به احمد بابت عسل")
    // and handled via pivot rule when preceded by narrative intros or at the start of a phrase (Rule A).
    private val singleWordConversationalFillers = listOf(
        "سلام",
        "علیکم",
        "درود",
        "والا",
        "خوب",
        "خب",
        "راستی",
        "ببین",
        "دیگه",
        "دیگر",
        "هم",
        "من",
        "ولی",
        "اما",
        "برام",
        "برایم",
        "هنوز",
        "ریختن",
        "ریختند",
        "رفتم",
        "بیرون",
        "اومدم",
        "آمدم",
        "برگشتم",
        "امروز",
        "دیروز",
        "دیشب",
        "پریروز",
        "بعد",
        "یادمه",
        "کلاً",
        "کلا",
        "جمعاً",
        "جمعا",
        "مجموعاً",
        "مجموعا",
        "سرجمع",
        "خریدم",
        "بخرم",
        "گرفتم",
        "بگیرم",
        "دادم",
        "بدم",
        "کردم",
        "زدم",
        "شد",
        "بود",
        "بودش",
        "هست",
        "نبود",
        "دیدم",
        "را",
        "رو",
        "ببخشید",
        "یعنی",
        "منظورم",
        "هزارش",
        "هزاری",
        "هزار",
        "تومانش",
        "تومنش",
        "تومانی",
        "تومنی",
        "تومان",
        "تومن",
        "ریالش",
        "ریالی",
        "ریال",
        "میلیونی",
        "میلیون",
        "میلیاردی",
        "میلیارد",
        "مبلغ",
        "بجاش",
        "بجایش"
    )

    private val narrativeIntroTriggerWords = listOf(
        "سلام",
        "رفتم",
        "بیرون",
        "اومدم",
        "آمدم",
        "برگشتم",
        "امروز",
        "دیروز",
        "دیشب",
        "پریروز",
        "بعد",
        "یادمه",
        "یادم هست",
        "کلاً",
        "کلا",
        "جمعاً",
        "جمعا",
        "مجموعاً",
        "مجموعا",
        "سرجمع",
        "خرج کردم",
        "پول دادم",
        "کارت کشیدم",
        "پرداخت کردم",
        "خرید کردم"
    )

    private val countingUnits = listOf(
        "کیلوگرم", "کیلو", "گرم", "دونه", "دانه", "عدد", "بسته",
        "بطری", "جلد", "برگ", "سیخ", "پرس", "قوطی", "پاکت", "شانه", "جفت", "تا"
    )

    private val nonMonetaryMeasurementUnits = listOf(
        "سوتی", "سوت",
        "کیلوگرمی", "کیلوگرم", "کیلویی", "کیلو", "گرمی", "گرم",
        "مثقالی", "مثقال", "قیراطی", "قیراط", "عیاری", "عیار",
        "میلی‌لیتری", "میلی‌لیتر", "میلی لیتر", "لیتری", "لیتر", "سی‌سی", "سی سی",
        "سانتی‌متری", "سانتی‌متر", "سانتی متر", "سانتی", "سانت",
        "میلی‌متری", "میلی‌متر", "میلی متر", "متری", "متر", "اینچی", "اینچ",
        "گیگابایتی", "گیگابایت", "گیگی", "گیگ", "مگابایتی", "مگابایت", "مگی", "مگ", "ترابایتی", "ترابایت",
        "واتی", "وات", "ولتی", "ولت", "آمپری", "آمپر", "هرتزی", "هرتز", "درصدی", "درصد", "درجه",
        "روزه", "ماهه", "ساله", "ساعته", "نفره", "تکه"
    )

    private val attributeDescriptorKeywords = listOf(
        "سایز", "شماره", "مدل", "طبقه", "واحد", "پلاک", "خط", "کد",
        "جلد", "صفحه", "فصل", "ترم", "کلاس", "پایه", "برگ", "ساعت",
        "سایت", "وبسایت", "وب‌سایت", "فروشگاه", "برنامه", "اپ", "اپلیکیشن",
        "کانال", "پیج", "شعبه", "ایستگاه", "سری", "نسخه",
        "آیفون", "سامسونگ", "پژو", "پراید", "سمند", "دنا", "تارا", "تیبا", "کوییک"
    )

    private val dativeCompoundActionNouns = listOf(
        "کمک", "هدیه", "کادو", "قرض", "صدقه", "عیدی", "انعام", "خیریه", "نذری"
    )

    private val smallQuantityWords = mapOf(
        "یه" to 1,
        "یک" to 1,
        "دو" to 2,
        "سه" to 3,
        "چهار" to 4,
        "پنج" to 5,
        "شش" to 6,
        "شیش" to 6,
        "هفت" to 7,
        "هشت" to 8,
        "نه" to 9,
        "ده" to 10,
        "یازده" to 11,
        "دوازده" to 12
    )

    private val currencyAndMultiplierWords = setOf(
        "تومان", "تومن", "تومنش", "تومانش", "تومانی", "تومنی",
        "هزار", "هزارش", "هزاری",
        "میلیون", "ملیون", "میلیونی",
        "میلیارد", "میلیاردی",
        "ریال", "ریالش", "ریالی"
    )

    private val compoundMoneyPrefixWords = setOf(
        "بیست", "سی", "چهل", "پنجاه", "شصت", "هفتاد", "هشتاد", "نود",
        "صد", "یکصد", "دویست", "سیصد", "چهارصد", "چارصد", "پانصد", "پونصد",
        "ششصد", "شیشصد", "هفتصد", "هشتصد", "نهصد", "هزار", "میلیون", "ملیون", "میلیارد"
    )

    // Pre-compiled Regex instances for high-speed execution (<100ms for 100 complex utterances)
    private val whitespaceRegex = Regex("""\s+""")
    private val precompiledStumbleRegex = Regex(
        """(?<=^|\s)[۰-۹0-9,٬،]+(?:\s+[۰-۹0-9,٬،]+)*(?=\s+[۰-۹0-9,٬،]+\s*(?:میلیاردی|میلیارد|میلیونی|میلیون|ملیون|هزار\s*تومانش|هزار\s*تومنش|هزار\s*تومانی|هزار\s*تومنی|هزار\s*تومان|هزار\s*تومن|هزارش|هزاری|هزار|تومانش|تومنش|تومانی|تومنی|تومان|تومن|ریالش|ریالی|ریال)(?=\s|$))"""
    )
    private val repeatedNahRegex = Regex("""(?<=^|\s)نه(?:\s+نه)+(?=\s|$)""")
    private val standaloneNahRegex = Regex("""(?<=^|\s)نه(?=\s|$)""")
    private val compoundNineSuffixRegex = Regex("""(?:${compoundMoneyPrefixWords.joinToString("|")})\s+و$""")
    private val followedByUnitOrCurrencyRegex = Regex(
        """^(?:${(currencyAndMultiplierWords + countingUnits + nonMonetaryMeasurementUnits).joinToString("|")})(?=\s|$)"""
    )
    private val precompiledMultiWordFillersRegex = Regex(
        """(?<=^|\s)(?:${multiWordConversationalFillers.sortedByDescending { it.length }.joinToString("|")})(?=\s|$)"""
    )
    private val precompiledSingleWordFillersRegex = Regex(
        """(?<=^|\s)(?:${singleWordConversationalFillers.sortedByDescending { it.length }.joinToString("|")})(?=\s|$)"""
    )
    private val precompiledNarrativeIntroRegex = Regex(
        """(?<=^|\s)(?:${narrativeIntroTriggerWords.sortedByDescending { it.length }.joinToString("|")})(?=\s|$)"""
    )

    data class ProtectedQuantitiesText(
        val protectedText: String,
        val replacements: Map<String, String>
    ) {
        fun restore(input: String): String {
            var out = input
            for ((token, replacement) in replacements) {
                out = out.replace(token, replacement)
            }
            return out.replace(whitespaceRegex, " ").trim()
        }
    }

    /**
     * Strips bare stumbled numbers immediately preceding `<number> <currency>`
     * (e.g. "۲۱۷ ۲۱۸ تومن بابت کارمزد" -> "۲۱۸ تومن بابت کارمزد").
     */
    fun stripStumbledNumbers(rawText: String): String {
        val normalized = PersianNumberUtils.normalizePersianText(rawText)
        return precompiledStumbleRegex.replace(normalized) { match ->
            val before = normalized.substring(0, match.range.first).trimEnd()
            val prevWord = before.split(whitespaceRegex).lastOrNull().orEmpty()
            val numTokens = match.value.trim().split(whitespaceRegex).filter { it.isNotBlank() }
            if (prevWord in attributeDescriptorKeywords && numTokens.isNotEmpty()) {
                // Keep the attribute number (e.g. "۴۲" in "سایز ۴۲ ۷۵۰ هزار تومان") and only drop any subsequent stumbled numbers
                numTokens.first()
            } else {
                " "
            }
        }.replace(whitespaceRegex, " ").trim()
    }

    /**
     * Strips standalone negation/correction "نه" ("no") while preserving "نه" when used as the number 9
     * (e.g., "سی و نه تومن", "نه هزار تومان", "نه تا بستنی").
     */
    private fun stripNonNumericNahTokens(rawText: String): String {
        var text = PersianNumberUtils.normalizePersianText(rawText)
        if (!text.contains("نه")) return text
        // Repeated "نه نه..." is always "no, no...", never the number 9
        text = text.replace(repeatedNahRegex, " ")

        return standaloneNahRegex.replace(text) { match ->
            val before = text.substring(0, match.range.first).trimEnd()
            val after = text.substring(match.range.last + 1).trimStart()
            val isCompoundNine = compoundNineSuffixRegex.containsMatchIn(before)
            val isFollowedByUnitOrCurrency = followedByUnitOrCurrencyRegex.containsMatchIn(after)
            if (isCompoundNine || isFollowedByUnitOrCurrency) {
                match.value
            } else {
                " "
            }
        }.replace(whitespaceRegex, " ").trim()
    }

    /**
     * Resolves mid-speech self-corrections where the user states an amount, says a correction marker
     * (`نه نه...`, `نبود`, `بود نه`, `اشتباه شد`, `ببخشید`, `یعنی`, `منظورم`, `ولی نه`, or `نه` before a new number),
     * and then states the corrected amount (e.g. "۵۰ تومن نانوایی بود نه نه نه نه ۳۵ تومن بود نانوایی" -> "۳۵ تومن نانوایی",
     * or "۵۰ هزار تومان دادم یه سکه پارسیان ۲۰ سوتی ولی می‌خواستم ۲۰ تومنی بخرم ولی نه ۱۰۰ تومنی خریدم" -> "۱۰۰ تومنی خریدم سکه پارسیان ۲۰ سوتی").
     */
    fun resolveMidSpeechSelfCorrections(rawText: String): String {
        val normalized = stripStumbledNumbers(PersianNumberUtils.normalizePersianText(rawText))
        val correctionRegex = Regex(
            """^(.*?)(?<=^|\s)(?:(?:ولی|اما)\s+نه(?:\s+نه)*|(?:نه\s+)+نه|بود\s+نه(?:\s+نه)*|نبود(?:\s+نه)*|اشتباه\s+شد|ببخشید|منظورم|یعنی|نه(?=\s+[۰-۹0-9]))(?=\s|$)\s*(.+)$"""
        )
        val match = correctionRegex.find(normalized) ?: return stripNonNumericNahTokens(normalized)
        val rawLeftPart = match.groupValues[1].trim()
        // Strip any intermediate soft-cancelled sub-clause ("ولی می‌خواستم ۲۰ تومنی بخرم") from leftPart so the original subject is cleanly extracted
        val leftPart = rawLeftPart.replace(
            Regex("""(?<=^|\s)(?:ولی|اما)?\s*(?:میخواستم|می‌خواستم|میخواهم|میخوام)\s+.*?(?:بدم|بخرم|بگیرم|کنم)?(?=\s+(?:ولی|اما)|$)"""),
            " "
        ).replace(whitespaceRegex, " ").trim()
        val rightPart = resolveMidSpeechSelfCorrections(match.groupValues[2].trim())

        val leftAmount = parseSpokenPersianAmountToman(leftPart)
        val rightAmount = parseSpokenPersianAmountToman(rightPart)
        if (leftAmount > 0L && rightAmount > 0L) {
            val leftParsed = parseSingleVoiceClause(leftPart, null, "نقدی / دستی")
            val rightParsed = parseSingleVoiceClause(rightPart, null, "نقدی / دستی")
            return if (rightParsed.purposeCategory.isNotBlank()) {
                stripNonNumericNahTokens(rightPart)
            } else if (leftParsed.purposeCategory.isNotBlank()) {
                stripNonNumericNahTokens("$rightPart ${leftParsed.purposeCategory}")
            } else {
                stripNonNumericNahTokens(rightPart)
            }
        }
        return stripNonNumericNahTokens(normalized)
    }

    /**
     * Strips stammered / mental-math counting and sub-price chains before the final item noun
     * (e.g., "دو تا سه تا ۵ تومن ۵ تومن نانوایی" -> "نانوایی").
     * Preserves single legitimate item quantities like "دو تا بستنی" or "یک شارژ ایرانسل".
     */
    fun stripMentalMathChains(rawText: String): String {
        val text = stripStumbledNumbers(PersianNumberUtils.normalizePersianText(rawText))
        val unitsPattern = countingUnits.joinToString("|")
        val qtyWordsPattern = smallQuantityWords.keys.sortedByDescending { it.length }.joinToString("|")
        // Matches a single small count or sub-price phrase (e.g. "دو تا", "سه تا", "۵ تومن", "۱۰ تومن")
        val singleSmallCountOrSubPrice =
            """(?:(?:$qtyWordsPattern|[۰-۹0-9]{1,2})\s*(?:$unitsPattern|تومانش|تومنش|تومانی|تومنی|تومان|تومن))"""
        val stammerChainRegex = Regex(
            """(?<=^|\s)$singleSmallCountOrSubPrice(?:\s+$singleSmallCountOrSubPrice)+(?=\s|$)"""
        )
        return stammerChainRegex.replace(text, " ").replace(whitespaceRegex, " ").trim()
    }

    /**
     * Normalizes compound Persian nouns (e.g. "سیب زمینی" -> "سیب‌زمینی") and protects item quantities
     * (like "یک شارژ", "دو تا سیب‌زمینی", "دو تا بستنی", "۳ دونه نان") with placeholders so they are:
     * 1) NEVER mistaken for 1,000 or 2,000 Toman money amounts, and
     * 2) Preserved in the final item title with clean Persian digits ("۱ شارژ ایرانسل", "۲ تا سیب‌زمینی").
     * First strips any back-to-back stammered mental-math chains ("دو تا سه تا ۵ تومن ۵ تومن").
     */
    fun protectItemQuantities(rawText: String): ProtectedQuantitiesText {
        var text = stripNonNumericNahTokens(stripMentalMathChains(rawText))
            .replace(Regex("""سیب\s+زمینی"""), "سیب‌زمینی")
            .replace(Regex("""تخم\s+مرغ"""), "تخم‌مرغ")
            .replace(Regex("""گوجه\s+فرنگی"""), "گوجه‌فرنگی")

        val replacements = linkedMapOf<String, String>()
        var counter = 0

        val qtyWordsPattern = smallQuantityWords.keys.sortedByDescending { it.length }.joinToString("|")
        val allSpokenNumsPattern = (numberWordTokens.keys + smallQuantityWords.keys)
            .distinct()
            .sortedByDescending { it.length }
            .joinToString("|")
        val currencyWordsPattern = currencyAndMultiplierWords.sortedByDescending { it.length }.joinToString("|")

        // Pattern 0A: Non-monetary physical, weight, gold, volume, or technical units (e.g. "۳۵۰ سوت", "۲۰ سوتی", "سی سوتی", "۱۸ عیار", "۲۰ گیگ", "۲۰۰ گرم")
        // Supports both digits and spoken number words (like "سی سوتی" -> "۳۰ سوتی", "بیست سوتی" -> "۲۰ سوتی").
        val measurementUnitsPattern = nonMonetaryMeasurementUnits.sortedByDescending { it.length }.joinToString("|")
        val measurementRegex = Regex(
            """(?<=^|\s)([۰-۹0-9]{1,5}(?:\.[۰-۹0-9]+)?|(?:$allSpokenNumsPattern)(?:\s+و\s+(?:$allSpokenNumsPattern))*)\s*($measurementUnitsPattern)(?=\s|$|،)"""
        )
        var hasProtectedMeasurementUnit = false
        text = measurementRegex.replace(text) { match ->
            val rawNumToken = match.groupValues[1].trim()
            val unitToken = match.groupValues[2].trim()
            val numericVal = smallQuantityWords[rawNumToken]
                ?: numberWordTokens[rawNumToken]?.toInt()
                ?: PersianNumberUtils.toEnglishDigits(rawNumToken).toIntOrNull()
                ?: run {
                    val parts = rawNumToken.split(Regex("""\s+و\s+""")).mapNotNull { numberWordTokens[it.trim()] }
                    if (parts.isNotEmpty()) parts.sum().toInt() else null
                }
            val formattedNum = if (numericVal != null) {
                PersianNumberUtils.toPersianDigits(numericVal.toString())
            } else {
                PersianNumberUtils.toPersianDigits(rawNumToken)
            }
            val key = "⟪QTY${('A' + counter++)}⟫"
            replacements[key] = "$formattedNum $unitToken"
            hasProtectedMeasurementUnit = true
            key
        }

        // Pattern 0B: Descriptive attribute keyword followed by a number (e.g. "سایز ۴۲", "واحد ۵", "طبقه ۳", "مدل ۱۴۰۳", "پلاک ۱۲")
        // Protected as long as the number is NOT immediately followed by a currency/multiplier word (so "واحد ۵۰۰ تومن" still parses ۵۰۰ تومن).
        val descriptorPattern = attributeDescriptorKeywords.sortedByDescending { it.length }.joinToString("|")
        val descriptorRegex = Regex(
            """(?<=^|\s)($descriptorPattern)\s+([۰-۹0-9]{1,6}|$qtyWordsPattern)(?!\s*(?:$currencyWordsPattern)(?=\s|$|،))(?=\s|$|،)"""
        )
        text = descriptorRegex.replace(text) { match ->
            val descWord = match.groupValues[1].trim()
            val rawNumToken = match.groupValues[2].trim()
            val numericVal = smallQuantityWords[rawNumToken]
                ?: PersianNumberUtils.toEnglishDigits(rawNumToken).toIntOrNull()
            val formattedNum = if (numericVal != null) {
                PersianNumberUtils.toPersianDigits(numericVal.toString())
            } else {
                PersianNumberUtils.toPersianDigits(rawNumToken)
            }
            val key = "⟪QTY${('A' + counter++)}⟫"
            replacements[key] = "$descWord $formattedNum"
            key
        }

        // Pattern A: <number word or 1..2 digit number> + <counting unit (تا/دونه/عدد/بسته/کیلو/...)> + <non-currency noun>
        val unitsPattern = countingUnits.joinToString("|")
        val withUnitRegex = Regex(
            """(?<=^|\s)($qtyWordsPattern|[۰-۹0-9]{1,2})\s*($unitsPattern)(?=\s+([^\s]+))"""
        )

        text = withUnitRegex.replace(text) { match ->
            val rawNumToken = match.groupValues[1]
            val unitToken = match.groupValues[2]
            val followingWord = match.groupValues[3]
                .replace("،", "")
                .replace(".", "")
                .trim()

            if (followingWord in currencyAndMultiplierWords || followingWord in numberWordTokens) {
                match.value
            } else {
                val numericVal = smallQuantityWords[rawNumToken]
                    ?: PersianNumberUtils.toEnglishDigits(rawNumToken).toIntOrNull()
                if (numericVal != null && numericVal in 1..99) {
                    val key = "⟪QTY${('A' + counter++)}⟫"
                    val persianNum = PersianNumberUtils.toPersianDigits(numericVal.toString())
                    replacements[key] = "$persianNum $unitToken"
                    key
                } else {
                    match.value
                }
            }
        }

        // Pattern B: Small number word (یه/یک/دو/سه/.../نه) directly preceding an item noun WITHOUT a counting unit or currency word
        val directNounWordsPattern = listOf("یه", "یک", "دو", "سه", "چهار", "پنج", "شش", "شیش", "هفت", "هشت", "نه")
            .joinToString("|")
        val directNounRegex = Regex("""(?<=^|\s)($directNounWordsPattern)(?=\s+([^\s]+))""")
        val indefiniteRecipientNouns = setOf(
            "فقیر", "نیازمند", "بنده", "گدا", "مسکین", "خیریه", "دوست", "رفیق", "نفر", "بچه", "کودک"
        )

        text = directNounRegex.replace(text) { match ->
            val wordToken = match.groupValues[1]
            val followingWord = match.groupValues[2]
                .replace("،", "")
                .replace(".", "")
                .trim()

            // Check if preceded by "<tens/hundreds> و " (e.g., "سی و نه تومن" or "بیست و پنج هزار")
            val prefixBeforeMatch = text.substring(0, match.range.first).trimEnd()
            val prevWordBeforeMatch = prefixBeforeMatch.split(whitespaceRegex).lastOrNull().orEmpty()
            val isPartOfCompoundMoney = if (prefixBeforeMatch.endsWith("و")) {
                val beforeVav = prefixBeforeMatch.removeSuffix("و").trimEnd()
                val prevWord = beforeVav.split(whitespaceRegex).lastOrNull().orEmpty()
                prevWord in compoundMoneyPrefixWords || prevWord in numberWordTokens
            } else {
                false
            }

            val isInvalidFollowing = followingWord in currencyAndMultiplierWords ||
                followingWord in numberWordTokens ||
                followingWord in singleWordConversationalFillers ||
                followingWord in countingUnits ||
                followingWord == "و" ||
                followingWord == "نیم" ||
                followingWord == "که" ||
                followingWord == "دنگ" ||
                followingWord == "سهم" ||
                followingWord.startsWith("⟪QTY")

            if (isPartOfCompoundMoney || isInvalidFollowing) {
                match.value
            } else {
                val numericVal = smallQuantityWords[wordToken] ?: 1
                val key = "⟪QTY${('A' + counter++)}⟫"
                // When the clause already specifies a measurement unit (like "یک سکه پارسیان ۲۰ سوتی" or "یه سکه ۲۰ سوتی"),
                // "یک"/"یه" is an indefinite article ("a"), so strip it to keep the title clean ("سکه پارسیان ۲۰ سوتی").
                val isIndefiniteArticleOrRecipient = (wordToken == "یه" || wordToken == "یک") &&
                    (prevWordBeforeMatch == "به" || followingWord in indefiniteRecipientNouns || hasProtectedMeasurementUnit)
                if (isIndefiniteArticleOrRecipient) {
                    replacements[key] = ""
                } else {
                    val persianNum = PersianNumberUtils.toPersianDigits(numericVal.toString())
                    replacements[key] = persianNum
                }
                key
            }
        }

        return ProtectedQuantitiesText(text, replacements)
    }

    private val incomeSpeechRegex = Regex(
        """(?<=^|\s)(?:دریافت\s+کردم|دریافت\s+شد|دریافتی|دریافت\s+وجه|واریز\s+شد|واریز\s+کردن|واریز\s+کردند|واریزی|واریز\s+به\s+حساب|واریز\s+حقوق|برام\s+ریختن|برام\s+ریختند|برایم\s+ریختند|برام\s+ریخت|برام\s+زدن|برام\s+زد|(?:حقوق|حقوقم|طلب|طلبم|طلبمو|پاداش|پاداشم|عیدی|سود|هدیه|جایزه|کارانه|پورسانت|یارانه|یارانه‌ام)(?:\s+[^\s]+){0,2}\s+(?:گرفتم|ریختن|ریختند|واریز\s+شد|اومد|آمد|نشست)|فروختم|بهم\s+داد|به\s+من\s+داد|به\s+حسابم\s+اومد|به\s+حسابم\s+آمد|به\s+حسابم\s+نشست|به\s+کارتم\s+ریخت|به\s+کارتم\s+زد|درآمدم|درآمد|طلبم\s+رو\s+داد|طلبم\s+را\s+داد|قرضم\s+رو\s+پس\s+داد|پس\s+گرفتم)(?=\s|$)|(?<=^|\s)(?:دریافت|واریز)$"""
    )

    private val nominalIncomeSubjectRegex = Regex(
        """(?<!از\s)(?<=^|\s)(?:حقوقم|حقوق\s+من|حقوق|یارانه‌ام|یارانه\s+من|یارانه|پاداشم|پاداش\s+من|پاداش|عیدیم|عیدی\s+من|عیدی|کارانه‌ام|کارانه|پورسانت|درآمدم|درآمد\s+من|سود\s+بانکی|سود\s+سپرده)(?=\s|$)"""
    )

    private val expenseVerbOrMarkerRegex = Regex(
        """(?<=^|\s)(?:خرج\s+کردم|خرجش\s+کردم|خرج\s+کرد|خرج\s+شد|خرج|پرداخت\s+کردم|پرداخت|خریدم|خرید\s+کردم|بخرم|کارت\s+کشیدم|پول\s+دادم|دادم|بدم|هزینه|قسط|اجاره|قبض)(?=\s|$)"""
    )

    fun detectVoiceTransactionIsIncome(rawSpeech: String): Boolean {
        val norm = PersianNumberUtils.normalizePersianText(rawSpeech.trim())
        if (norm.isBlank()) return false
        if (incomeSpeechRegex.containsMatchIn(norm)) return true
        if (nominalIncomeSubjectRegex.containsMatchIn(norm) &&
            !expenseVerbOrMarkerRegex.containsMatchIn(norm) &&
            !norm.contains("از حقوق") &&
            !norm.contains("از یارانه")
        ) {
            return true
        }
        return false
    }

    fun inferExpenseCategory(title: String, isIncome: Boolean = false): String {
        if (isIncome) return "درآمد و واریزی"
        val t = PersianNumberUtils.normalizePersianText(title)
        return when {
            listOf("شارژ", "ایرانسل", "همراه اول", "رایتل", "اینترنت", "قبض", "برق", "آب", "گاز", "تلفن", "شاتل", "آسیاتک", "هاست").any { t.contains(it) } ->
                "قبض و شارژ"
            listOf("نانوایی", "نونوایی", "نان", "نون", "میوه", "موز", "سیب‌زمینی", "سیب زمینی", "بستنی", "گوشت", "مرغ", "پفک", "تنقلات", "سوپرمارکت", "ماست", "شیر", "خوراکی", "غذا", "رستوران", "کافه", "شام", "ناهار", "صبحانه").any { t.contains(it) } ->
                "خوراکی و روزمره"
            listOf("دمپایی", "کفش", "لباس", "مانتو", "شلوار", "پوشاک", "پیراهن").any { t.contains(it) } ->
                "پوشاک و خرید"
            listOf("اسنپ", "تپسی", "تاکسی", "بنزین", "مترو", "اتوبوس").any { t.contains(it) } ->
                "حمل و نقل"
            listOf("داروخانه", "دارو", "دکتر", "آزمایش", "پزشک").any { t.contains(it) } ->
                "درمان و سلامت"
            else -> "خوراکی و روزمره"
        }
    }

    private val leadingPrepositionsToStrip = listOf(
        "به", "از", "برای", "بابتِ", "بابت", "تو", "توی", "در", "واسه", "که", "را", "رو"
    )

    private fun deduplicateConsecutiveWords(input: String): String {
        val tokens = input.split(whitespaceRegex).filter { it.isNotBlank() }
        if (tokens.size <= 1) return tokens.joinToString(" ")
        val deduped = mutableListOf<String>()
        for (token in tokens) {
            if (deduped.isEmpty() || deduped.last() != token) {
                deduped.add(token)
            }
        }
        return deduped.joinToString(" ")
    }

    private fun trimLeadingPrepositionsAndVav(input: String): String {
        var s = deduplicateConsecutiveWords(input.replace(whitespaceRegex, " ").trim())
        var changed = true
        while (changed) {
            changed = false
            while (s.startsWith("و ")) {
                s = s.removePrefix("و ").trim()
                changed = true
            }
            while (s.endsWith(" و")) {
                s = s.removeSuffix(" و").trim()
                changed = true
            }
            while (s.startsWith("ش ") || s.startsWith("ی ")) {
                s = s.removePrefix("ش ").removePrefix("ی ").trim()
                changed = true
            }
            while (s.endsWith(" ی")) {
                s = s.removeSuffix(" ی").trim()
                changed = true
            }
            for (prep in leadingPrepositionsToStrip) {
                if (s.startsWith("$prep ")) {
                    s = s.removePrefix("$prep ").trim()
                    changed = true
                }
                if (s.endsWith(" $prep")) {
                    s = s.removeSuffix(" $prep").trim()
                    changed = true
                }
            }
            if (s.startsWith("خرید ") && s.removePrefix("خرید ").trim().isNotBlank()) {
                s = s.removePrefix("خرید ").trim()
                changed = true
            }
            if (s.startsWith("خرج ") &&
                !s.startsWith("خرج از ") &&
                s != "خرج کرد" &&
                s.removePrefix("خرج ").trim().isNotBlank()
            ) {
                s = s.removePrefix("خرج ").trim()
                changed = true
            }
        }
        s = deduplicateConsecutiveWords(s)
        if (s == "و" || s == "ش" || s == "ی" || s in leadingPrepositionsToStrip) return ""
        return s
    }

    private fun stripBasicFillersOnly(rawText: String): String {
        var result = stripNonNumericNahTokens(PersianNumberUtils.normalizePersianText(rawText))
        result = result.replace(precompiledMultiWordFillersRegex, " ")
        result = result.replace(precompiledSingleWordFillersRegex, " ")
        return trimLeadingPrepositionsAndVav(result)
    }

    /**
     * Applies Rule A and Rule B for Persian voice NLU:
     * - Strip leading prepositions (`به `, `از `, `برای `, `بابت `, `تو `, `توی `, `در `, `واسه `)
     *   so `"دادم به بستنی"` ALWAYS becomes `"بستنی"`.
     * - Pivot on `"برای"` / `"بابت"` / `"واسه"` after narrative intros (`رفتم`, `بیرون`, `اومدم`, `خرج کردم`, ...).
     * - Preserve `"بابت"` or `"برای"` ONLY when it sits *between* two meaningful noun phrases
     *   (e.g., `"انتقال پول به احمد بابت عسل"`).
     */
    fun stripGreetingsAndFillers(rawText: String): String {
        val normalized = stripMentalMathChains(rawText)
        val pivotRegex = Regex("""^(.*?)(?<=^|\s)(برای|بابتِ|بابت|واسه)(?=\s|$)(.*)$""")
        val match = pivotRegex.find(normalized)

        val result = if (match != null) {
            val leftRaw = match.groupValues[1].trim()
            val prep = if (match.groupValues[2] == "بابتِ") "بابت" else match.groupValues[2].trim()
            val rightRaw = match.groupValues[3].trim()

            val hasNarrativeIntroOnLeft = precompiledNarrativeIntroRegex.containsMatchIn(leftRaw)
            val leftClean = stripBasicFillersOnly(leftRaw)
            val rightClean = stripBasicFillersOnly(rightRaw)

            when {
                hasNarrativeIntroOnLeft && rightClean.isNotBlank() -> rightClean
                leftClean.isBlank() -> rightClean
                rightClean.isBlank() -> leftClean
                else -> "$leftClean $prep $rightClean"
            }
        } else {
            stripBasicFillersOnly(normalized)
        }

        return trimLeadingPrepositionsAndVav(result)
    }

    private val softCancelWantButSplitRegex = Regex(
        """^(.*?(?<=^|\s)(?:میخواستم|می‌خواستم|میخواهم|میخوام)(?=\s|$).*?)\s+(?:ولی|اما)(?!\s+(?:نه|ندادم|نکردم|نخریدم|نگرفتم|نزدم|نشد|پشیمون|پشیمان|منصرف|کنسل|لغو)(?=\s|$))\s+(.+)$"""
    )

    /**
     * Resolves soft-cancelled "می‌خواستم ... ولی <actual clause>" patterns BEFORE narrative intro pivoting
     * so that sentences like:
     * - "سلام ۲۵ تومن می‌خواستم خرج کنم ولی ۲۰ تومن خرج کردم برای یک موز" -> "۲۰ تومن خرج کردم برای یک موز" (20,000 Tomans, not 25,000!)
     * - "سلام رفتم بیرون اومدم بعد یادمه که برای دوتا بستنی می‌خواستم ۳۹ تومن بدم ولی ۴۹ تومن دادم" -> "۲ تا بستنی ۴۹ تومن دادم"
     */
    fun resolveSoftCancelledWantButClauses(rawText: String): String {
        val normalized = PersianNumberUtils.normalizePersianText(rawText.trim())
        if (!normalized.contains("میخواستم") && !normalized.contains("می‌خواستم") &&
            !normalized.contains("میخواهم") && !normalized.contains("میخوام")
        ) {
            return normalized
        }
        val match = softCancelWantButSplitRegex.find(normalized) ?: return normalized
        val rawBeforeBut = match.groupValues[1].trim()
        val rawAfterBut = match.groupValues[2].trim()
        if (rawAfterBut.isBlank() || hasHardNegationMarker(rawAfterBut)) return normalized

        // Check if rawBeforeBut contains an earlier independent clause separated by " و " before the "می‌خواستم" part
        var preservedLeadingClause = ""
        var cancelledSegment = rawBeforeBut
        if (rawBeforeBut.contains(" و ")) {
            val lastVavIdx = rawBeforeBut.lastIndexOf(" و ")
            val beforeVav = rawBeforeBut.substring(0, lastVavIdx).trim()
            val afterVav = rawBeforeBut.substring(lastVavIdx + 3).trim()
            if (Regex("""(?<=^|\s)(?:میخواستم|می‌خواستم|میخواهم|میخوام)(?=\s|$)""").containsMatchIn(afterVav)) {
                val probeBeforeVav = parseSingleVoiceClause(beforeVav, null, "نقدی / دستی")
                if (probeBeforeVav.totalAmountToman > 0L && probeBeforeVav.purposeCategory.isNotBlank()) {
                    preservedLeadingClause = beforeVav
                    cancelledSegment = afterVav
                }
            }
        }

        val afterAmount = parseSpokenPersianAmountToman(rawAfterBut)
        if (afterAmount <= 0L) return normalized

        val subjectFromCancelled = extractSubjectFromSoftCancelledClause(cancelledSegment)
        val afterPivotProbe = pivotNarrativeIntroKeepingAmountInternal(rawAfterBut)
        val afterParsed = parseSingleVoiceClause(afterPivotProbe, null, "نقدی / دستی")

        val resolvedTail = if (afterParsed.purposeCategory.isNotBlank() || subjectFromCancelled.isBlank()) {
            rawAfterBut
        } else {
            "$subjectFromCancelled $rawAfterBut"
        }

        return if (preservedLeadingClause.isNotBlank()) {
            "$preservedLeadingClause و $resolvedTail"
        } else {
            resolvedTail
        }
    }

    private fun pivotNarrativeIntroKeepingAmountInternal(rawText: String): String {
        val cleaned = PersianNumberUtils.normalizePersianText(rawText.trim())
        val pivotRegex = Regex("""^(.*?)(?<=^|\s)(?:برای|بابتِ|بابت|واسه)(?=\s|$)\s*(.+)$""")
        val match = pivotRegex.find(cleaned) ?: return trimLeadingPrepositionsAndVav(cleaned)

        val leftRaw = match.groupValues[1].trim()
        val rightRaw = trimLeadingPrepositionsAndVav(match.groupValues[2].trim())
        if (rightRaw.isBlank()) return trimLeadingPrepositionsAndVav(cleaned)

        // Never pivot across a "که" breakdown or when the left side has "تومنش"/"تومانش"
        if (leftRaw.contains("که") || leftRaw.contains("تومنش") || leftRaw.contains("تومانش")) {
            return trimLeadingPrepositionsAndVav(cleaned)
        }

        val hasNarrativeIntroOnLeft = precompiledNarrativeIntroRegex.containsMatchIn(leftRaw)
        if (!hasNarrativeIntroOnLeft) {
            return trimLeadingPrepositionsAndVav(cleaned)
        }

        val leftAmount = parseSpokenPersianAmountToman(leftRaw)
        val rightAmount = parseSpokenPersianAmountToman(rightRaw)
        return when {
            leftAmount > 0L && rightAmount == 0L -> {
                val amtStr = if (leftAmount >= 1_000L && leftAmount % 1_000L == 0L) {
                    "${leftAmount / 1_000L} هزار تومان"
                } else {
                    "${leftAmount * 10L} ریال"
                }
                "$amtStr $rightRaw"
            }
            leftAmount == 0L -> rightRaw
            else -> trimLeadingPrepositionsAndVav(cleaned)
        }
    }

    private fun pivotNarrativeIntroKeepingAmount(rawText: String): String {
        val softResolved = resolveSoftCancelledWantButClauses(rawText)
        return pivotNarrativeIntroKeepingAmountInternal(softResolved)
    }

    private val colloquialMillionMacroRegex = Regex(
        """(?<=^|\s)(?:حقوق|حقوقم|دستمزد|دستمزدم|عیدی|عیدیم|پاداش|پاداشم|درآمد|درآمدم|اجاره|رهن|ودیعه|پیش‌پرداخت|پیش\s+پرداخت|ماشین|خودرو|خونه|خانه|آپارتمان|ملک|زمین|لپ‌تاپ|لپ\s+تاپ|آیفون|دوچرخه|موتور|موتورسیکلت|گوشی|موبایل|تبلت|تلویزیون|یخچال|لباسشویی|کامپیوتر|پلی‌استیشن|مبل|مبلمان|فرش|قسط‌های\s+بزرگ|قسط\s+بزرگ|قسط‌های\s+بزرگی|قسط\s+بزرگی|وام\s+بزرگ|قسط\s+مسکن|قسط\s+ماشین|قسط\s+خودرو)(?=\s|$)"""
    )

    private fun shouldInferColloquialMillionScale(clause: String, numVal: Double): Boolean {
        if (numVal !in 1.0..999.0) return false
        return colloquialMillionMacroRegex.containsMatchIn(clause)
    }

    /**
     * Parses spoken Persian numbers and digits into Tomans while ignoring item quantities and mental-math sub-prices:
     * - Strips bare stumbled numbers before `<number> <currency>` (e.g. "۲۱۷ ۲۱۸ تومن" -> "۲۱۸ تومن").
     * - When a clause contains multiple currency amounts (e.g. "۳۹ هزار تومان دادم به دو تا سه تا ۵ تومن ۵ تومن نانوایی"),
     *   takes the LARGEST explicit currency amount in that clause (`39,000` Tomans), ignoring smaller sub-amounts (`۵ تومن`).
     * - Disambiguates colloquial Iranian `"تومن"` scale: when no explicit `"هزار"`/`"میلیون"` is spoken and the clause
     *   describes a macro Iranian category (`حقوق`, `اجاره`, `رهن`, `ماشین`, `خانه`, `قسط‌های بزرگ`), `۳۰ تومن` -> `30,000,000` Tomans.
     */
    fun parseSpokenPersianAmountToman(rawText: String): Long {
        val cleaned = stripStumbledNumbers(PersianNumberUtils.normalizePersianText(rawText.trim()))
        if (cleaned.isBlank()) return 0L

        for ((phrase, amt) in exactPhraseAmounts) {
            if (cleaned.contains(phrase)) return amt
        }

        val protectedText = protectItemQuantities(cleaned).protectedText
        val textWithoutQtyPlaceholders = protectedText.replace(Regex("""⟪QTY[A-Z]+⟫"""), " ")
        val engWithoutPlaceholders = PersianNumberUtils.toEnglishDigits(textWithoutQtyPlaceholders)

        // 1. Check ALL numeric digit amounts in the clause.
        //    Strongly prefer explicit currency/multiplier amounts (e.g. "۵۵ هزار تومان")
        //    over bare numbers (e.g. brand/site numbers like "۵۰۴۰").
        val digitRegex = Regex(
            """([0-9]+(?:\.[0-9]+)?)\s*(میلیاردی|میلیارد|میلیونی|میلیون|ملیون|هزار\s*تومانش|هزار\s*تومنش|هزار\s*تومانی|هزار\s*تومنی|هزار\s*تومان|هزار\s*تومن|هزارش|هزاری|هزار|تومانش|تومنش|تومانی|تومنی|تومان|تومن|ریالش|ریالی|ریال)?"""
        )
        var maxExplicitCurrencyDigitAmount = 0L
        var maxBareDigitAmount = 0L
        for (digitMatch in digitRegex.findAll(engWithoutPlaceholders)) {
            val numVal = digitMatch.groupValues[1].toDoubleOrNull() ?: 0.0
            val unit = digitMatch.groupValues[2].trim()
            if (numVal > 0.0) {
                val candidate = when {
                    unit.startsWith("میلیارد") -> (numVal * 1_000_000_000.0).toLong()
                    unit.startsWith("میلیون") || unit == "ملیون" -> (numVal * 1_000_000.0).toLong()
                    unit.startsWith("ریال") -> (numVal / 10.0).toLong()
                    unit.startsWith("هزار") -> (numVal * 1_000.0).toLong()
                    numVal in 1.0..999.0 && unit.startsWith("توم") && shouldInferColloquialMillionScale(cleaned, numVal) ->
                        (numVal * 1_000_000.0).toLong()
                    numVal in 1.0..999.0 -> (numVal * 1_000.0).toLong()
                    else -> numVal.toLong()
                }
                if (unit.isNotEmpty()) {
                    if (candidate > maxExplicitCurrencyDigitAmount) {
                        maxExplicitCurrencyDigitAmount = candidate
                    }
                } else {
                    if (candidate > maxBareDigitAmount) {
                        maxBareDigitAmount = candidate
                    }
                }
            }
        }
        if (maxExplicitCurrencyDigitAmount > 0L) {
            return maxExplicitCurrencyDigitAmount
        }
        if (maxBareDigitAmount > 0L) {
            return maxBareDigitAmount
        }

        // 2. Parse spoken Persian number words: e.g. "پانصد هزار تومان", "پنجاه تومن", "هفتاد تومن", "سی و نه تومن"
        val tokens = textWithoutQtyPlaceholders
            .replace("،", " ")
            .replace(".", " ")
            .split(whitespaceRegex)
            .filter { it.isNotBlank() }

        var maxSpokenAmount = 0L
        var totalAccumulated = 0L
        var currentGroup = 0L
        var matchedAnyNumberWord = false
        var hasExplicitMultiplier = false

        fun flushCurrentPhrase() {
            if (!matchedAnyNumberWord) return
            var phraseVal = totalAccumulated
            if (currentGroup > 0L) {
                phraseVal += if (!hasExplicitMultiplier && currentGroup in 1L..999L) {
                    if (shouldInferColloquialMillionScale(cleaned, currentGroup.toDouble())) {
                        currentGroup * 1_000_000L
                    } else {
                        currentGroup * 1_000L
                    }
                } else if (hasExplicitMultiplier && totalAccumulated >= 1_000_000L && currentGroup in 1L..999L) {
                    currentGroup * 1_000L
                } else {
                    currentGroup
                }
            }
            if (phraseVal > maxSpokenAmount) {
                maxSpokenAmount = phraseVal
            }
            totalAccumulated = 0L
            currentGroup = 0L
            matchedAnyNumberWord = false
            hasExplicitMultiplier = false
        }

        for (rawToken in tokens) {
            val token = rawToken.trim()
            if (token == "و") continue

            val cleanWord = token
                .removeSuffix("تومنش")
                .removeSuffix("تومانش")
                .removeSuffix("تومانی")
                .removeSuffix("تومنی")
                .removeSuffix("تومان")
                .removeSuffix("تومن")
                .trim()

            val wordVal = numberWordTokens[cleanWord] ?: numberWordTokens[token]
            if (wordVal != null) {
                currentGroup += wordVal
                matchedAnyNumberWord = true
                if (token.endsWith("تومان") || token.endsWith("تومن") || token.endsWith("تومانش") ||
                    token.endsWith("تومنش") || token.endsWith("تومانی") || token.endsWith("تومنی")
                ) {
                    flushCurrentPhrase()
                }
                continue
            }

            when (token) {
                "میلیارد", "میلیاردی" -> {
                    val base = if (currentGroup > 0L) currentGroup else 1L
                    totalAccumulated += base * 1_000_000_000L
                    currentGroup = 0L
                    matchedAnyNumberWord = true
                    hasExplicitMultiplier = true
                }
                "میلیون", "ملیون", "میلیونی" -> {
                    val base = if (currentGroup > 0L) currentGroup else 1L
                    totalAccumulated += base * 1_000_000L
                    currentGroup = 0L
                    matchedAnyNumberWord = true
                    hasExplicitMultiplier = true
                }
                "هزار", "هزارش", "هزاری" -> {
                    val base = if (currentGroup > 0L) currentGroup else 1L
                    totalAccumulated += base * 1_000L
                    currentGroup = 0L
                    matchedAnyNumberWord = true
                    hasExplicitMultiplier = true
                }
                "تومان", "تومن", "تومنش", "تومانش", "تومانی", "تومنی" -> {
                    if (matchedAnyNumberWord) {
                        flushCurrentPhrase()
                    }
                }
            }
        }

        flushCurrentPhrase()
        return maxSpokenAmount
    }

    /**
     * Formats multiple parsed voice commands into a single combined note for an existing bank SMS row:
     * e.g. "۲ تا شارژ (۵۰,۰۰۰) + ۲ تا بستنی (۵۰,۰۰۰)" or "بستنی (۴۹,۰۰۰) + کاغذ (۵۰۰)"
     */
    fun formatCombinedMultiVoiceNote(items: List<ParsedVoiceExpense>): String {
        return items.joinToString(" + ") { item ->
            "${item.purposeCategory} (${PersianNumberUtils.formatToman(item.totalAmountToman, showUnit = false)})"
        }
    }

    private val negationAndCancellationMarkers = listOf(
        "ندادم",
        "نخریدم",
        "نگرفتم",
        "نکردم",
        "نزدم",
        "نشد",
        "کنسل",
        "کنسلش",
        "لغو",
        "پشیمون",
        "پشیمان",
        "منصرف",
        "نخواستم",
        "نمیخوام",
        "نمی‌خوام",
        "نمیخواستم",
        "نمی‌خواستم"
    )

    private val precompiledHardNegationRegex = Regex(
        """(?<=^|\s)(?:${negationAndCancellationMarkers.joinToString("|")})(?=\s|$)"""
    )

    private fun hasHardNegationMarker(clause: String): Boolean {
        val norm = PersianNumberUtils.normalizePersianText(clause.trim())
        if (norm.isBlank()) return false
        return precompiledHardNegationRegex.containsMatchIn(norm)
    }

    /**
     * Rule B (Negation & Cancellation Filter):
     * Returns true if the clause contains a negation or cancellation marker such as
     * `ندادم`, `نخریدم`, `نگرفتم`, `نکردم`, `نزدم`, `نشد`, `کنسل`, `لغو`, `پشیمون`, `منصرف`, `نخواستم`,
     * or `میخواستم / می‌خواستم ... ولی / اما`.
     */
    fun isNegatedOrCancelledClause(clause: String): Boolean {
        val norm = PersianNumberUtils.normalizePersianText(clause.trim())
        if (norm.isBlank()) return false
        if (hasHardNegationMarker(norm)) return true
        val hasWantedTo = Regex("""(?<=^|\s)(?:میخواستم|می‌خواستم|میخواهم|میخوام)(?=\s|$)""").containsMatchIn(norm)
        val hasBut = Regex("""(?<=^|\s)(?:ولی|اما)(?=\s|$)""").containsMatchIn(norm)
        return hasWantedTo && hasBut
    }

    private val remainingBalanceCommentaryRegex = Regex(
        """(?<=^|\s)(?:باقی\s*مانده|باقی‌مانده|باقی\s*مونده|باقی‌مونده|باقی\s*ماند|باقی\s*موند|مونده|موند|اضافه\s+اومد|اضافه\s+آمد|اضافه\s+موند|اضافه\s+مونده|تهش\s+موند|تهش\s+مونده|موجودی\s+مونده|موجودی\s+مانده|موجودی\s+باقی|مانده\s+دارم|مونده\s+دارم|مانده\s+است|بقیه(?:‌اش|اش|ش|شو|‌شو)|مابقی(?:ش|شو)?|نگه\s*داشتم|نگه‌داشتم|نگهش\s*داشتم|نگه\s+میدارم|نگه\s+می‌دارم|کنار\s+گذاشتم|گذاشتم\s+کنار|گذاشتم\s+بمونه|دست\s+نخورد)(?=\s|$)"""
    )

    private val leftoverReferenceNounRegex = Regex(
        """(?<=^|\s)(?:بقیه(?:‌اش|اش|ش|شو|‌شو)?|مابقی(?:ش|شو)?|باقی\s*مانده|باقی‌مانده|باقی\s*مونده|باقی‌مونده|اضافه|تهش)(?=\s|$)"""
    )

    private val keptOrSetAsideVerbRegex = Regex(
        """(?<=^|\s)(?:نگه\s*داشتم|نگه‌داشتم|نگهش\s*داشتم|نگه\s+میدارم|نگه\s+می‌دارم|کنار\s+گذاشتم|گذاشتم\s+کنار|گذاشتم\s+بمونه|پس\s*انداز\s+کردم|پس‌انداز\s+کردم|دست\s+نخورد)(?=\s|$)"""
    )

    private val explicitActionVerbInClauseRegex = Regex(
        """(?<=^|\s)(?:خرج\s+کردم|خرج\s+شد|پرداخت\s+کردم|خریدم|خرید\s+کردم|دادم|گرفتم|واریز\s+کردم|واریز\s+شد|دریافت\s+کردم|فروختم|کارت\s+کشیدم|ریختن|ریختند)(?=\s|$)"""
    )

    /**
     * Returns true when a clause merely states leftover/remaining balance commentary
     * (e.g. "۵ میلیون تومان هنوز باقی مانده", "۵ تومن موند", or "بقیه‌اش رو هم نگه داشتم")
     * rather than an actual transaction.
     */
    fun isRemainingBalanceCommentaryClause(clause: String): Boolean {
        val norm = PersianNumberUtils.normalizePersianText(clause.trim())
        if (norm.isBlank()) return false
        if (explicitActionVerbInClauseRegex.containsMatchIn(norm)) return false
        if (keptOrSetAsideVerbRegex.containsMatchIn(norm)) {
            val hasExplicitAmount = parseSpokenPersianAmountToman(norm) > 0L
            // Drop kept/set-aside commentary whenever it refers to leftover ("بقیه‌اش/مابقی") OR has no explicit amount
            if (!hasExplicitAmount || leftoverReferenceNounRegex.containsMatchIn(norm)) {
                return true
            }
        }
        return remainingBalanceCommentaryRegex.containsMatchIn(norm)
    }

    /**
     * Extracts the intended item subject from a soft-cancelled "می‌خواستم X بدم ولی" clause
     * so that if the immediately following clause ("۴۹ تومن دادم") omits the noun, the subject ("۲ تا بستنی") is preserved.
     */
    private fun extractSubjectFromSoftCancelledClause(cancelledClause: String): String {
        val stripped = PersianNumberUtils.normalizePersianText(cancelledClause)
            .replace(Regex("""(?<=^|\s)(?:میخواستم|می‌خواستم|میخواهم|میخوام|خرج\s+کنم|بدم|بخرم|بگیرم|کنم|ولی|اما)(?=\s|$)"""), " ")
            .replace(whitespaceRegex, " ")
            .trim()
        if (stripped.isBlank()) return ""
        return parseSingleVoiceClause(stripped, null, "نقدی / دستی").purposeCategory
    }

    /**
     * Resolves observation/browsing clauses (ending with `دیدم`, `نگاه کردم`, `قیمت کردم`, `پیدا کردم`, `مقایسه کردم`)
     * followed by an actual purchase clause (`... خریدم / گرفتم / دادم ...`) that references one of the observed items by its attribute
     * (e.g. "رفتم بیرون یک سکه پارسیان ۲۰ سوتی و یک سکه پارسیان ۳۰ سوتی دیدم و سی سوتی را خریدم ۲۵ هزار تومان"
     * or ASR contraction "سیتی را خریدم ۲۵ هزار تومان" -> "سکه پارسیان ۳۰ سوتی ۲۵ هزار تومان").
     */
    fun resolveObservationClausesAndBackReferences(rawText: String): String {
        val normalized = PersianNumberUtils.normalizePersianText(rawText.trim())
        if (!normalized.contains("دیدم") && !normalized.contains("نگاه") && !normalized.contains("قیمت") && !normalized.contains("پیدا") && !normalized.contains("مقایسه")) {
            return normalized
        }
        val obsSplitRegex = Regex(
            """^(.*?)(?<=^|\s)(?:دیدم|نگاه\s+کردم|قیمت\s+کردم|پیدا\s+کردم|مقایسه\s+کردم)(?=\s|$)\s*(?:و|بعد|ولی|اما)?\s*(.+)$"""
        )
        val match = obsSplitRegex.find(normalized) ?: return normalized
        val observationSegment = match.groupValues[1].trim()
        var purchaseSegment = match.groupValues[2].trim()
        if (purchaseSegment.isBlank()) return normalized

        // Normalize Google Speech-to-Text phonetic contraction "سیتی" -> "۳۰ سوتی" when "سوتی" or "سوت" appeared in observationSegment
        if (observationSegment.contains("سوت") && Regex("""(?<=^|\s)سیتی(?=\s|$)""").containsMatchIn(purchaseSegment)) {
            purchaseSegment = purchaseSegment.replace(Regex("""(?<=^|\s)سیتی(?=\s|$)"""), "۳۰ سوتی")
        }

        val observedCandidates = observationSegment
            .split(Regex("""(?:\s+و\s+|،)"""))
            .map { parseSingleVoiceClause(it.trim(), null, "نقدی / دستی").purposeCategory }
            .filter { it.isNotBlank() }

        if (observedCandidates.isEmpty()) return purchaseSegment

        val purchaseParsed = parseSingleVoiceClause(purchaseSegment, null, "نقدی / دستی")
        val refTitle = purchaseParsed.purposeCategory.trim()

        val resolvedFullTitle = when {
            refTitle.isBlank() || refTitle == "اون" || refTitle == "این" || refTitle == "دومی" || refTitle == "آخری" ->
                observedCandidates.last()
            refTitle == "اولی" ->
                observedCandidates.first()
            else -> {
                observedCandidates.firstOrNull { candidate ->
                    candidate.contains(refTitle) && candidate.length > refTitle.length
                } ?: observedCandidates.lastOrNull { candidate ->
                    // If refTitle is an attribute like "۳۰ سوتی" and candidate shares the unit
                    refTitle.split(" ").any { tok -> tok.length >= 2 && candidate.contains(tok) } &&
                        candidate.length > refTitle.length
                } ?: refTitle
            }
        }

        return if (resolvedFullTitle != refTitle && resolvedFullTitle.isNotBlank()) {
            if (purchaseParsed.totalAmountToman > 0L) {
                val amtStr = if (purchaseParsed.totalAmountToman >= 1_000L && purchaseParsed.totalAmountToman % 1_000L == 0L) {
                    "${purchaseParsed.totalAmountToman / 1_000L} هزار تومان"
                } else {
                    "${purchaseParsed.totalAmountToman * 10L} ریال"
                }
                "$resolvedFullTitle $amtStr"
            } else {
                resolvedFullTitle
            }
        } else {
            purchaseSegment
        }
    }

    /**
     * Rule A (Asyndetic Clause Splitting):
     * Splits consecutive clauses even when the user does NOT say " و " between them, whenever a new
     * `<number> + <currency: تومن/تومان/تومانش/تومنش/تومنی/تومانی/هزار/میلیون>` pattern begins after a noun or payment verb
     * (`دادم`, `خریدم`, `گرفتم`, `زدم`, `کردم`).
     */
    private fun splitAsyndeticAmountClauses(clause: String): List<String> {
        val trimmed = stripStumbledNumbers(clause.trim())
        if (trimmed.isBlank()) return emptyList()

        val numberWordsPattern = numberWordTokens.keys.sortedByDescending { it.length }.joinToString("|")
        val amountStartRegex = Regex(
            """(?<=^|\s)(?:[۰-۹0-9][۰-۹0-9,٬،]*(?:\.[۰-۹0-9]+)?|(?:$numberWordsPattern)(?:\s+و\s+(?:$numberWordsPattern))*)\s+(?:میلیاردی|میلیارد|میلیونی|میلیون|ملیون|هزار\s*تومانش|هزار\s*تومنش|هزار\s*تومانی|هزار\s*تومنی|هزار\s*تومان|هزار\s*تومن|هزارش|هزاری|هزار|تومانش|تومنش|تومانی|تومنی|تومان|تومن|ریالش|ریالی|ریال)(?=\s|$)"""
        )

        val matches = amountStartRegex.findAll(trimmed).toList()
        if (matches.size <= 1) return listOf(trimmed)

        val result = mutableListOf<String>()
        var currentStart = 0

        val trailingTemporalAnchorRegex = Regex(
            """(?:^|\s)((?:پس‌فردا|پس\s+فردا|فردا|پریروز|دیروز|دیشب|امروز|هفته\s+بعد|هفته\s+آینده|ماه\s+بعد|ماه\s+آینده)(?:\s+(?:هم|دیگه|دیگر))?|بعدش|بعد\s+هم|همچنین)$"""
        )

        for (i in 1 until matches.size) {
            val match = matches[i]
            var candidateSplitIdx = match.range.first
            var precedingSegment = trimmed.substring(currentStart, candidateSplitIdx).trim()

            // Do not split if preceding segment is part of a "که ... دنگ" phrase
            if (precedingSegment.endsWith("که") || precedingSegment.endsWith("و")) {
                continue
            }

            // Pull trailing temporal anchors (e.g. "... حقوق گرفتم فردا هم | ۱۵ تومان ...") forward onto the upcoming clause
            val temporalTailMatch = trailingTemporalAnchorRegex.find(precedingSegment)
            if (temporalTailMatch != null) {
                val anchorGroupStartInPreceding = temporalTailMatch.groups[1]?.range?.first ?: -1
                if (anchorGroupStartInPreceding > 0) {
                    val trimmedBeforeAnchor = precedingSegment.substring(0, anchorGroupStartInPreceding).trim()
                    if (trimmedBeforeAnchor.isNotBlank()) {
                        val rawBeforeMatch = trimmed.substring(currentStart, candidateSplitIdx)
                        val anchorText = temporalTailMatch.groupValues[1]
                        val anchorOffsetInRaw = rawBeforeMatch.lastIndexOf(anchorText)
                        if (anchorOffsetInRaw > 0) {
                            candidateSplitIdx = currentStart + anchorOffsetInRaw
                            precedingSegment = trimmedBeforeAnchor
                        }
                    }
                }
            }

            val precedingParsed = parseSingleVoiceClause(precedingSegment, null, "نقدی / دستی")
            val hasExplicitVerb = Regex("""(?<=^|\s)(?:دادم|ندادم|خریدم|نخریدم|گرفتم|نگرفتم|زدم|نزدم|کردم|نکردم|فروختم|بدم|بود|نشد|شد|ریختن|ریختند|ریخت|اومد|آمد|نشست)(?=\s|$)""")
                .containsMatchIn(precedingSegment)

            if (precedingParsed.totalAmountToman > 0L &&
                (precedingParsed.purposeCategory.isNotBlank() || hasExplicitVerb || isNegatedOrCancelledClause(precedingSegment))
            ) {
                result.add(precedingSegment)
                currentStart = candidateSplitIdx
            }
        }

        val tail = trimmed.substring(currentStart).trim()
        if (tail.isNotBlank()) {
            result.add(tail)
        }
        return result
    }

    private fun extractCandidateSubClauses(cleaned: String): List<String> {
        val obsResolved = resolveObservationClausesAndBackReferences(cleaned)
        val selfCorrected = resolveMidSpeechSelfCorrections(obsResolved)
        val softResolved = resolveSoftCancelledWantButClauses(selfCorrected)
        val normalized = stripMentalMathChains(softResolved)

        // Protect " و " inside compound Persian numbers (e.g. "سی و نه", "بیست و پنج", "یک میلیون و دویست")
        val prefixPattern = compoundMoneyPrefixWords.sortedByDescending { it.length }.joinToString("|")
        val numWordsPattern = (numberWordTokens.keys + setOf("نیم")).sortedByDescending { it.length }.joinToString("|")
        val compoundVavRegex = Regex("""(?<=^|\s)($prefixPattern)\s+و\s+(?=($numWordsPattern)(?=\s|$))""")
        var protectedCompoundNums = normalized
        while (compoundVavRegex.containsMatchIn(protectedCompoundNums)) {
            protectedCompoundNums = compoundVavRegex.replace(protectedCompoundNums) { m ->
                "${m.groupValues[1]}⟪NUMVAV⟫"
            }
        }

        // Also split on " که " when it introduces an itemized breakdown (e.g. "امروز ۵۰ هزار تومان خرج کردم که ۲۵ تومنش برای موز بود و ۳۵ تومنش برای بستنی")
        val splitPattern = if (!protectedCompoundNums.contains("دنگ") &&
            !protectedCompoundNums.contains("دُنگ") &&
            !protectedCompoundNums.contains("سهم")
        ) {
            Regex("""(?:\s+و\s+|،|\s+که\s+(?=[۰-۹0-9]|(?:$numWordsPattern)(?=\s|$)))""")
        } else {
            Regex("""(?:\s+و\s+|،)""")
        }

        val byConjOrComma = protectedCompoundNums
            .split(splitPattern)
            .map {
                val restored = it.replace("⟪NUMVAV⟫", " و ").trim()
                resolveSoftCancelledWantButClauses(resolveMidSpeechSelfCorrections(restored))
            }
            .filter { it.isNotBlank() }

        val afterContrastSplit = mutableListOf<String>()
        for (part in byConjOrComma) {
            afterContrastSplit.addAll(splitOnContrastConjunction(part))
        }

        val afterAsyndetic = mutableListOf<String>()
        for (part in afterContrastSplit) {
            afterAsyndetic.addAll(splitAsyndeticAmountClauses(part))
        }

        val expanded = mutableListOf<String>()
        for (part in afterAsyndetic) {
            expanded.addAll(splitOnMidSentenceVerb(part))
        }
        return expanded
    }

    /**
     * Splits independent clauses separated by contrast conjunctions (`ولی`, `اما`) when NOT a
     * cancelled/negated "می‌خواستم ... ولی ندادم" clause and BOTH sides carry an explicit amount
     * (e.g. "سلام من ۳۹ تومن بدهی دارم ولی فردا ۵۰ تومن طلب دارم" -> two clean clauses without "فردا" leaking).
     */
    private fun splitOnContrastConjunction(clause: String): List<String> {
        val trimmed = clause.trim()
        if (trimmed.isBlank() || isNegatedOrCancelledClause(trimmed)) return listOf(trimmed)
        val contrastRegex = Regex("""\s+(?:ولی|اما)\s+""")
        val matches = contrastRegex.findAll(trimmed).toList()
        if (matches.isEmpty()) return listOf(trimmed)

        val parts = mutableListOf<String>()
        var startIdx = 0
        for (m in matches) {
            val left = trimmed.substring(startIdx, m.range.first).trim()
            val right = trimmed.substring(m.range.last + 1).trim()
            if (left.isNotBlank() &&
                right.isNotBlank() &&
                !hasHardNegationMarker(left) &&
                !hasHardNegationMarker(right) &&
                parseSpokenPersianAmountToman(left) > 0L &&
                parseSpokenPersianAmountToman(right) > 0L
            ) {
                parts.add(left)
                startIdx = m.range.last + 1
            }
        }
        val tail = trimmed.substring(startIdx).trim()
        if (tail.isNotBlank()) parts.add(tail)
        return if (parts.isEmpty()) listOf(trimmed) else parts
    }

    private fun splitOnMidSentenceVerb(clause: String): List<String> {
        val midVerbRegex = Regex("""^(.+?)\s+(?:خریدم|گرفتم|دادم|زدم)\s+(.+)$""")
        val match = midVerbRegex.find(clause.trim()) ?: return listOf(clause.trim())
        val left = match.groupValues[1].trim()
        val right = match.groupValues[2].trim()
        if (isNegatedOrCancelledClause(left) || isNegatedOrCancelledClause(right)) {
            return listOf(clause.trim())
        }
        val rightParsed = parseSingleVoiceClause(right, null, "نقدی / دستی")
        val leftParsed = parseSingleVoiceClause(left, null, "نقدی / دستی")
        return if (leftParsed.purposeCategory.isNotBlank() && rightParsed.purposeCategory.isNotBlank()) {
            listOf(left, right)
        } else {
            listOf(clause.trim())
        }
    }

    private fun normalizeComparableNoun(title: String): String {
        return PersianNumberUtils.normalizePersianText(title)
            .replace("نونوایی", "نانوایی")
            .replace("نون", "نان")
            .replace(whitespaceRegex, " ")
            .trim()
    }

    /**
     * Reconciles sub-thousand Toman amounts (e.g. "۵۰۰ تومانش بابت کاغذ" on a 49,500 SMS, or "۲۱۸ تومن بابت کارمزد" on a 107,218 SMS)
     * where the user spoke a number in 1..999 without "هزار" that represents literal Tomans rather than thousands of Tomans.
     */
    private fun reconcileSubThousandMultiItems(
        items: List<ParsedVoiceExpense>,
        clauses: List<String>,
        fallbackTotalToman: Long?
    ): List<ParsedVoiceExpense> {
        if (items.size < 2) return items
        if (items.any { it.isIncome } && items.any { !it.isIncome }) return items

        val canDemoteToSubThousand = items.mapIndexed { idx, item ->
            val clause = clauses.getOrNull(idx).orEmpty()
            val hasExplicitHezar = Regex("""(?<=^|\s)(?:هزارش|هزاری|هزار|میلیونی|میلیون|ملیون|میلیاردی|میلیارد)(?=\s|$)""").containsMatchIn(clause)
            !hasExplicitHezar && item.totalAmountToman in 1_000L..999_000L && (item.totalAmountToman % 1_000L == 0L)
        }

        // 1. If we have a known target total (from bank SMS or summary intro header), check if demoting any candidate items
        //    from N*1000 to literal N makes the total match (or fixes an item that alone exceeds fallbackTotalToman).
        if (fallbackTotalToman != null && fallbackTotalToman > 0L) {
            val currentSum = items.sumOf { it.totalAmountToman }
            if (currentSum != fallbackTotalToman) {
                for (i in items.indices) {
                    if (canDemoteToSubThousand[i]) {
                        val literalToman = items[i].totalAmountToman / 1_000L
                        val candidateSum = currentSum - items[i].totalAmountToman + literalToman
                        if (candidateSum == fallbackTotalToman ||
                            (items[i].totalAmountToman > fallbackTotalToman && literalToman < fallbackTotalToman)
                        ) {
                            return items.mapIndexed { idx, it ->
                                if (idx == i) it.copy(totalAmountToman = literalToman, userShareToman = literalToman) else it
                            }
                        }
                    }
                }
            }
        }

        // 2. Even without a bank SMS fallback, if an item is a bank transfer fee ("کارمزد") with a raw number 100..999
        //    (without "هزار") alongside a main transfer item >= 10,000 Tomans, treat the fee as literal Tomans (e.g. 218 Tomans).
        val hasMajorTransferItem = items.any { it.totalAmountToman >= 10_000L && !it.purposeCategory.contains("کارمزد") }
        if (hasMajorTransferItem) {
            return items.mapIndexed { idx, item ->
                val literalToman = item.totalAmountToman / 1_000L
                if (canDemoteToSubThousand[idx] && item.purposeCategory.contains("کارمزد") && literalToman in 100L..999L) {
                    item.copy(totalAmountToman = literalToman, userShareToman = literalToman)
                } else {
                    item
                }
            }
        }

        return items
    }

    /**
     * Collapses consecutive items that have the exact same normalized item title when spoken as a self-correction
     * (e.g. "۵۰ تومان نانوایی ۳۵ تومن نانوایی" -> keeps only the second item: "نانوایی" with 35,000 Tomans).
     */
    private fun collapseConsecutiveSameNounCorrections(items: List<ParsedVoiceExpense>): List<ParsedVoiceExpense> {
        if (items.size <= 1) return items
        val collapsed = mutableListOf<ParsedVoiceExpense>()
        for (item in items) {
            val prev = collapsed.lastOrNull()
            if (prev != null &&
                prev.isIncome == item.isIncome &&
                prev.purposeCategory.isNotBlank() &&
                normalizeComparableNoun(prev.purposeCategory) == normalizeComparableNoun(item.purposeCategory)
            ) {
                collapsed[collapsed.lastIndex] = item
            } else {
                collapsed.add(item)
            }
        }
        return collapsed
    }

    private val summaryHeaderRegex = Regex(
        """(?<=^|\s)(?:کلاً|کلا|جمعاً|جمعا|مجموعاً|مجموعا|سرجمع|خرج\s+کردم|خرید\s+کردم|پول\s+دادم|کارت\s+کشیدم|پرداخت\s+کردم)(?=\s|$)"""
    )

    private val partitiveSpendBackRefRegex = Regex(
        """(?<=^|\s)(?:تومانش|تومنش|هزارش|ریالش|ازش)(?=\s|$)"""
    )

    /**
     * Extracts the stated summary header total at the start of a multi-item voice utterance
     * (e.g. `50,000` in `"سلام امروز ۵۰ هزار تومان خرج کردم که ۲۵ تومنش برای موز بود و ۳۵ تومنش برای بستنی"`
     * or `"رفتم بیرون کلاً ۵۰ هزار تومان خرج کردم ۲۵ تومن بستنی ۲۵ تومن دمپایی"`), or `null` if no summary header was spoken.
     */
    fun extractSpokenSummaryHeaderTotalToman(rawSpeech: String): Long? {
        val rawCleaned = stripStumbledNumbers(PersianNumberUtils.normalizePersianText(rawSpeech.trim()))
        if (rawCleaned.isBlank()) return null
        if (rawCleaned.contains("که") && (rawCleaned.contains("دنگ") || rawCleaned.contains("دُنگ") || rawCleaned.contains("سهم"))) {
            return null
        }
        val cleaned = pivotNarrativeIntroKeepingAmount(rawCleaned)
        val rawCandidateParts = extractCandidateSubClauses(cleaned)
        if (rawCandidateParts.size < 2) return null

        val firstRaw = rawCandidateParts.first()
        if (!summaryHeaderRegex.containsMatchIn(firstRaw)) return null
        val firstProbe = parseSingleVoiceClause(firstRaw, null, "نقدی / دستی")
        if (firstProbe.totalAmountToman <= 0L || firstProbe.purposeCategory.isNotBlank()) return null

        val restHaveItemWithAmount = rawCandidateParts.drop(1).any { part ->
            val p = parseSingleVoiceClause(part, null, "نقدی / دستی")
            p.totalAmountToman > 0L && p.purposeCategory.isNotBlank()
        }
        return if (restHaveItemWithAmount) firstProbe.totalAmountToman else null
    }

    /**
     * Detects if the user stated a summary header total at the beginning of the sentence (e.g. "امروز ۵۰ هزار تومان خرج کردم که...")
     * that conflicts with the sum of the itemized breakdown (e.g. "۲۵ تومنش برای موز بود و ۳۵ تومنش برای بستنی" = 60,000 Tomans).
     * Returns the stated summary header amount in Tomans (e.g. `50,000L`) if there is a mismatch, or `null` if they match or no summary header was spoken.
     */
    fun detectSpokenSummaryTotalMismatch(
        rawSpeech: String,
        multiItems: List<ParsedVoiceExpense> = emptyList()
    ): Long? {
        val statedSummaryHeader = extractSpokenSummaryHeaderTotalToman(rawSpeech) ?: return null
        val items = if (multiItems.isNotEmpty()) {
            multiItems
        } else {
            parseMultiVoiceCommands(rawSpeech)
        }
        if (items.size < 2) return null
        if (items.any { it.isIncome } && items.any { !it.isIncome }) return null
        val itemsSum = items.sumOf { it.totalAmountToman }
        return if (itemsSum > 0L && itemsSum != statedSummaryHeader) statedSummaryHeader else null
    }

    /**
     * Detects if the user explicitly spoke an amount (or multiple item amounts) that conflicts with the
     * target bank SMS amount (`fallbackTotalToman`).
     * Returns the conflicting spoken amount in Tomans if there is a mismatch, or `null` if it matches or no amount was spoken.
     */
    fun detectBankSmsAmountMismatch(
        rawSpeech: String,
        fallbackTotalToman: Long,
        multiItems: List<ParsedVoiceExpense> = emptyList()
    ): Long? {
        if (fallbackTotalToman <= 0L) return null
        val cleaned = PersianNumberUtils.normalizePersianText(rawSpeech.trim())
        if (cleaned.isBlank()) return null

        val itemsToCheck = if (multiItems.isNotEmpty()) {
            multiItems
        } else {
            parseMultiVoiceCommands(cleaned, "نقدی / دستی", fallbackTotalToman)
        }

        if (itemsToCheck.size > 1) {
            val sumSpoken = itemsToCheck.sumOf { it.totalAmountToman }
            return if (sumSpoken > 0L && sumSpoken != fallbackTotalToman) sumSpoken else null
        }

        // Single-item bank SMS tagging: check if the user explicitly spoke an amount in rawSpeech
        val freeParsedItems = parseMultiVoiceCommands(cleaned, "نقدی / دستی", fallbackTotalToman = null)
        val spokenAmount = freeParsedItems.sumOf { it.totalAmountToman }
        if (spokenAmount <= 0L) return null

        // Allow exact match or sub-thousand match (e.g. 500 spoken as 500 on a 500 Toman SMS)
        if (spokenAmount == fallbackTotalToman) return null
        if (spokenAmount in 1_000L..999_000L && (spokenAmount / 1_000L) == fallbackTotalToman) return null

        return spokenAmount
    }

    fun parseMultiVoiceCommands(
        rawSpeech: String,
        defaultBankName: String = "نقدی / دستی",
        fallbackTotalToman: Long? = null
    ): List<ParsedVoiceExpense> {
        val rawCleaned = stripStumbledNumbers(PersianNumberUtils.normalizePersianText(rawSpeech.trim()))
        if (rawCleaned.isBlank()) return emptyList()

        if (rawCleaned.contains("که") && (rawCleaned.contains("دنگ") || rawCleaned.contains("دُنگ") || rawCleaned.contains("سهم"))) {
            if (isNegatedOrCancelledClause(rawCleaned)) return emptyList()
            return listOf(parseSingleVoiceClause(rawCleaned, fallbackTotalToman, defaultBankName))
        }

        val cleaned = pivotNarrativeIntroKeepingAmount(rawCleaned)
        val rawCandidateParts = extractCandidateSubClauses(cleaned)

        // Rule B: Drop any negated or cancelled clause (e.g. "۵۵ تومن میخواستم بدم نونوایی ولی ندادم"),
        // while carrying forward the item noun from a soft-cancelled "می‌خواستم X بدم ولی" clause if the next clause omits the noun!
        val candidateParts = mutableListOf<String>()
        var pendingSubjectFromSoftCancelled = ""

        for (rawPart in rawCandidateParts) {
            if (isRemainingBalanceCommentaryClause(rawPart)) {
                pendingSubjectFromSoftCancelled = ""
                continue
            }
            if (isNegatedOrCancelledClause(rawPart)) {
                if (!hasHardNegationMarker(rawPart)) {
                    val subject = extractSubjectFromSoftCancelledClause(rawPart)
                    if (subject.isNotBlank()) {
                        pendingSubjectFromSoftCancelled = subject
                    }
                } else {
                    pendingSubjectFromSoftCancelled = ""
                }
            } else {
                var effectivePart = rawPart
                if (pendingSubjectFromSoftCancelled.isNotBlank()) {
                    val probe = parseSingleVoiceClause(rawPart, null, defaultBankName)
                    if (probe.purposeCategory.isBlank() && probe.totalAmountToman > 0L) {
                        effectivePart = "$pendingSubjectFromSoftCancelled $rawPart"
                    }
                    pendingSubjectFromSoftCancelled = ""
                }
                candidateParts.add(effectivePart)
            }
        }

        val nonEmptyCandidateParts = candidateParts.filter { part ->
            val p = parseSingleVoiceClause(part, null, defaultBankName)
            p.totalAmountToman > 0L || p.purposeCategory.isNotBlank()
        }

        if (nonEmptyCandidateParts.isEmpty()) {
            return emptyList()
        }

        // Check if the first clause is a Summary Intro Header (e.g. "اومدم کلاً ۵۰ هزار تومان خرج کردم" or "سلام امروز ۵۰ هزار تومان خرج کردم")
        // followed by itemized sub-clauses ("۲۵ تومن بستنی", "۲۵ تومن دمپایی").
        // Must contain an explicit summary/spending marker so compound numbers like "یک میلیون و دویست هزار تومان خرید گوشت" are preserved!
        var summaryHeaderAmount: Long? = null
        val activeCandidateParts = if (nonEmptyCandidateParts.size >= 2) {
            val firstRaw = nonEmptyCandidateParts.first()
            val firstProbe = parseSingleVoiceClause(firstRaw, null, defaultBankName)
            val isSummaryHeaderClause = summaryHeaderRegex.containsMatchIn(firstRaw)
            val restHaveItemWithAmount = nonEmptyCandidateParts.drop(1).any { part ->
                val p = parseSingleVoiceClause(part, null, defaultBankName)
                p.totalAmountToman > 0L && p.purposeCategory.isNotBlank()
            }
            if (isSummaryHeaderClause &&
                firstProbe.totalAmountToman > 0L &&
                firstProbe.purposeCategory.isBlank() &&
                restHaveItemWithAmount
            ) {
                summaryHeaderAmount = firstProbe.totalAmountToman
                nonEmptyCandidateParts.drop(1)
            } else {
                nonEmptyCandidateParts
            }
        } else {
            nonEmptyCandidateParts
        }

        val effectiveFallbackTotal = fallbackTotalToman ?: summaryHeaderAmount

        if (activeCandidateParts.size == 1) {
            return listOf(parseSingleVoiceClause(activeCandidateParts.first(), fallbackTotalToman, defaultBankName))
        }

        // Case A: Tagging an existing bank transaction (or summary total) with known total (e.g. 100,000 or 49,500 Tomans)
        if (effectiveFallbackTotal != null && effectiveFallbackTotal > 0L && activeCandidateParts.size == 2) {
            val firstParsed = parseSingleVoiceClause(activeCandidateParts[0], null, defaultBankName)
            val secondParsed = parseSingleVoiceClause(activeCandidateParts[1], null, defaultBankName)

            if (firstParsed.purposeCategory.isNotBlank() && secondParsed.purposeCategory.isNotBlank()) {
                if (firstParsed.totalAmountToman == 0L &&
                    secondParsed.totalAmountToman in 1L until effectiveFallbackTotal
                ) {
                    val remainder = (effectiveFallbackTotal - secondParsed.totalAmountToman).coerceAtLeast(0L)
                    return collapseConsecutiveSameNounCorrections(
                        listOf(
                            firstParsed.copy(totalAmountToman = remainder, userShareToman = remainder),
                            secondParsed
                        )
                    )
                } else if (secondParsed.totalAmountToman == 0L &&
                    firstParsed.totalAmountToman in 1L until effectiveFallbackTotal
                ) {
                    val remainder = (effectiveFallbackTotal - firstParsed.totalAmountToman).coerceAtLeast(0L)
                    return collapseConsecutiveSameNounCorrections(
                        listOf(
                            firstParsed,
                            secondParsed.copy(totalAmountToman = remainder, userShareToman = remainder)
                        )
                    )
                } else if (firstParsed.totalAmountToman > 0L && secondParsed.totalAmountToman > 0L) {
                    val reconciled = reconcileSubThousandMultiItems(
                        items = listOf(firstParsed, secondParsed),
                        clauses = activeCandidateParts,
                        fallbackTotalToman = effectiveFallbackTotal
                    )
                    return collapseConsecutiveSameNounCorrections(reconciled)
                }
            }
        }

        // Case B: Multi-clause sentence where sub-clauses have explicit stated amounts > 0L
        val mergedClauses = mutableListOf<String>()
        var currentClause = activeCandidateParts.first()

        for (nextPart in activeCandidateParts.drop(1)) {
            val currentParsed = parseSingleVoiceClause(currentClause, null, defaultBankName)
            val nextParsed = parseSingleVoiceClause(nextPart, null, defaultBankName)
            val nextAmount = nextParsed.totalAmountToman
            val hasExpenseVerbInNext = expenseVerbOrMarkerRegex.containsMatchIn(nextPart)

            // If currentParsed is an INCOME clause and nextPart is an EXPENSE clause (e.g. "حقوق من ۵۰ هزار تومان بود و ۲۵ هزار تومانش رو خرج کردم"),
            // keep them as separate clauses with independent transaction directions!
            if (currentParsed.totalAmountToman > 0L &&
                currentParsed.isIncome &&
                !nextParsed.isIncome &&
                nextAmount > 0L &&
                (nextParsed.purposeCategory.isNotBlank() || hasExpenseVerbInNext)
            ) {
                mergedClauses.add(currentClause)
                currentClause = nextPart
            } else if (currentParsed.totalAmountToman > 0L &&
                currentParsed.purposeCategory.isNotBlank() &&
                nextAmount > 0L &&
                nextParsed.purposeCategory.isBlank()
            ) {
                // If nextPart has a new amount but NO noun (e.g. "... ولی ۱۰۰ تومنی خریدم"), it overrides the price of currentClause!
                currentClause = "${currentParsed.purposeCategory} $nextPart"
            } else if (currentParsed.totalAmountToman > 0L &&
                currentParsed.purposeCategory.isNotBlank() &&
                nextAmount > 0L
            ) {
                mergedClauses.add(currentClause)
                currentClause = nextPart
            } else {
                currentClause = "$currentClause و $nextPart"
            }
        }
        mergedClauses.add(currentClause)

        if (mergedClauses.size >= 2) {
            val validClauses = mutableListOf<String>()
            val parsedItems = mutableListOf<ParsedVoiceExpense>()
            var lastIncomeTitle = ""
            var lastMillionIncomeToman = 0L
            val partitiveMacroExpenseRegex = Regex("""(?<=^|\s)(?:قسط|قسط‌ها|قسطها|اقساط|وام|اجاره|رهن|چک|بدهی)(?=\s|$)""")
            val microEverydayRegex = Regex("""(?<=^|\s)(?:بستنی|نان|نانوایی|نون|نونوایی|پفک|شارژ|تاکسی|میوه|موز|قهوه|چای|غذا|ناهار|شام|صبحانه|خوراکی|تنقلات)(?=\s|$)""")
            val explicitUnitRegex = Regex("""(?<=^|\s)(?:هزارش|هزاری|هزار|میلیونی|میلیون|ملیون|میلیاردی|میلیارد|ریالش|ریالی|ریال)(?=\s|$)""")

            val continuationIncomeCueRegex = Regex(
                """(?<=^|\s)(?:دیگه|دیگر|حسابم|کارتم|واریز|حقوق|درآمد|پاداش|میاد|می‌آید|می‌ریزن|میریزن)(?=\s|$)"""
            )

            for (clause in mergedClauses) {
                var parsed = parseSingleVoiceClause(clause, null, defaultBankName)
                if (parsed.isIncome && parsed.totalAmountToman > 0L && parsed.purposeCategory.isNotBlank()) {
                    // Contextual million promotion for follow-up bank inflows after a million-scale income
                    // (e.g. "پریروز ۳۰ تومن حقوق گرفتم فردا هم ۱۵ تومان دیگه میاد به حسابم" -> 15,000,000 Tomans)
                    if (lastMillionIncomeToman >= 5_000_000L &&
                        parsed.totalAmountToman in 1_000L..999_000L &&
                        parsed.totalAmountToman % 1_000L == 0L &&
                        !explicitUnitRegex.containsMatchIn(clause) &&
                        continuationIncomeCueRegex.containsMatchIn(clause) &&
                        !microEverydayRegex.containsMatchIn(clause)
                    ) {
                        val promotedIncome = parsed.totalAmountToman * 1_000L
                        parsed = parsed.copy(totalAmountToman = promotedIncome, userShareToman = promotedIncome)
                    }
                    lastIncomeTitle = parsed.purposeCategory
                    if (parsed.totalAmountToman >= 5_000_000L) {
                        lastMillionIncomeToman = parsed.totalAmountToman
                    }
                } else if (!parsed.isIncome && parsed.totalAmountToman > 0L) {
                    if (lastMillionIncomeToman >= 5_000_000L &&
                        parsed.totalAmountToman in 1_000L..999_000L &&
                        parsed.totalAmountToman % 1_000L == 0L &&
                        partitiveSpendBackRefRegex.containsMatchIn(clause) &&
                        !explicitUnitRegex.containsMatchIn(clause) &&
                        partitiveMacroExpenseRegex.containsMatchIn(clause) &&
                        !microEverydayRegex.containsMatchIn(clause) &&
                        (parsed.totalAmountToman * 1_000L) <= lastMillionIncomeToman
                    ) {
                        val promoted = parsed.totalAmountToman * 1_000L
                        parsed = parsed.copy(totalAmountToman = promoted, userShareToman = promoted)
                    }
                    if ((parsed.purposeCategory.isBlank() || parsed.purposeCategory == "خرج کرد") &&
                        expenseVerbOrMarkerRegex.containsMatchIn(clause)
                    ) {
                        val enrichedTitle = if (lastIncomeTitle.isNotBlank() &&
                            lastIncomeTitle != "دریافت وجه" &&
                            lastIncomeTitle != "واریز وجه"
                        ) {
                            "خرج از $lastIncomeTitle"
                        } else {
                            "خرج کرد"
                        }
                        parsed = parsed.copy(purposeCategory = enrichedTitle)
                    }
                }
                if (parsed.totalAmountToman > 0L && parsed.purposeCategory.isNotBlank()) {
                    validClauses.add(clause)
                    parsedItems.add(parsed)
                }
            }

            if (parsedItems.size >= 2) {
                val reconciled = reconcileSubThousandMultiItems(
                    items = parsedItems,
                    clauses = validClauses,
                    fallbackTotalToman = effectiveFallbackTotal
                )
                return collapseConsecutiveSameNounCorrections(reconciled)
            }
        }

        val combinedKept = activeCandidateParts.joinToString(" و ")
        return listOf(parseSingleVoiceClause(combinedKept, fallbackTotalToman, defaultBankName))
    }

    fun parseVoiceCommand(
        rawSpeech: String,
        fallbackTotalToman: Long? = null,
        defaultBankName: String = "نقدی / دستی"
    ): ParsedVoiceExpense {
        val cleaned = PersianNumberUtils.normalizePersianText(rawSpeech.trim())
        val isTaggingExistingSms = fallbackTotalToman != null && fallbackTotalToman > 0L
        val detectedIncome = !isTaggingExistingSms && detectVoiceTransactionIsIncome(cleaned)

        val multiItems = parseMultiVoiceCommands(cleaned, defaultBankName, fallbackTotalToman)
        if (multiItems.size > 1) {
            if (isTaggingExistingSms) {
                val combinedNote = formatCombinedMultiVoiceNote(multiItems)
                val effectiveBank = defaultBankName.ifBlank { "بلو" }
                return ParsedVoiceExpense(
                    recognizedSpeech = cleaned,
                    totalAmountToman = fallbackTotalToman!!,
                    userShareToman = fallbackTotalToman,
                    dongAmountToman = 0L,
                    dongFriendName = "",
                    storeName = effectiveBank,
                    purposeCategory = combinedNote,
                    cardLabel = "کارت $effectiveBank",
                    category = multiItems.first().category,
                    merchant = multiItems.first().merchant,
                    isIncome = false
                )
            } else {
                val sumAmount = multiItems.sumOf { it.totalAmountToman }
                val combinedNote = formatCombinedMultiVoiceNote(multiItems)
                val allIncome = multiItems.all { it.isIncome }
                val anyIncome = detectedIncome || multiItems.any { it.isIncome }
                return ParsedVoiceExpense(
                    recognizedSpeech = cleaned,
                    totalAmountToman = sumAmount,
                    userShareToman = sumAmount,
                    dongAmountToman = 0L,
                    dongFriendName = "",
                    storeName = defaultBankName.ifBlank { "نقدی / دستی" },
                    purposeCategory = combinedNote,
                    cardLabel = "نقدی / دستی",
                    category = if (anyIncome) "درآمد و واریزی" else multiItems.first().category,
                    merchant = multiItems.first().merchant,
                    isIncome = allIncome
                )
            }
        }

        if (multiItems.size == 1) {
            val single = multiItems.first()
            return if (isTaggingExistingSms) {
                val effectiveBank = defaultBankName.ifBlank { "بلو" }
                single.copy(
                    totalAmountToman = fallbackTotalToman!!,
                    userShareToman = (fallbackTotalToman - single.dongAmountToman).coerceAtLeast(0L),
                    storeName = effectiveBank,
                    cardLabel = "کارت $effectiveBank",
                    isIncome = false
                )
            } else {
                single
            }
        }

        if (isNegatedOrCancelledClause(cleaned)) {
            return ParsedVoiceExpense(
                recognizedSpeech = cleaned,
                totalAmountToman = 0L,
                userShareToman = 0L,
                dongAmountToman = 0L,
                dongFriendName = "",
                storeName = defaultBankName,
                purposeCategory = "",
                cardLabel = "نقدی / دستی",
                isIncome = detectedIncome
            )
        }

        return parseSingleVoiceClause(cleaned, fallbackTotalToman, defaultBankName)
    }

    private fun detectSpokenCardBankInVoice(speech: String): String {
        val bankNamesPattern = "بلو|ملت|ملی|سپه|پاسارگاد|سامان|صادرات|تجارت|رسالت|ویپاد|مسکن|رفاه|پارسیان|کشاورزی|گردشگری|شهر|سینا|آینده|دی|کارآفرین"
        val explicitSourceCardRegex = Regex("""(?:با|از)\s+کارت\s+($bankNamesPattern)(?=\s|$)""")
        val fallbackCardRegex = Regex("""(?<!به\s)کارت\s+($bankNamesPattern)(?=\s|$)""")
        val match = explicitSourceCardRegex.find(speech) ?: fallbackCardRegex.find(speech) ?: return "بانک"
        return when (val raw = match.groupValues[1]) {
            "ملی" -> "ملی ایران"
            "صادرات" -> "صادرات ایران"
            "رسالت" -> "قرض‌الحسنه رسالت"
            "رفاه" -> "رفاه کارگران"
            else -> raw
        }
    }

    private val settledObligationVerbRegex = Regex(
        """(?<=^|\s)(?:دادم|پس\s+دادم|پرداخت\s+کردم|تسویه\s+کردم|واریز\s+کردم|گرفتم|پس\s+گرفتم|دریافت\s+کردم|واریز\s+شد|ریختن|ریختند|برگشت|برگردوند|برگرداند|خریدم|خرج\s+کردم)(?=\s|$)"""
    )

    private val futureDebtClauseRegex = Regex(
        """(?<=^|\s)(?:بدهی\s+دارم|بدهی\s+داریم|بدهکارم|بدهکار\s+هستم|باید\s+بدم|باید\s+بدهم|باید\s+پرداخت\s+کنم|قرض\s+دارم|بدهیم\s+هست|بدهی\s+منه)(?=\s|$)"""
    )

    private val futurePlannedPurchaseRegex = Regex(
        """(?<=^|\s)(?:می‌خوام|میخوام|می‌خواهم|میخواهم|قراره|قرار\s+است|قصد\s+دارم)(?=\s|$)"""
    )

    private val futureExpectedInflowRegex = Regex(
        """(?<=^|\s)(?:میاد\s+(?:به|تو|توی)\s+(?:حسابم|کارتم)|می‌آید\s+(?:به|تو|توی)\s+(?:حسابم|کارتم)|به\s+(?:حسابم|کارتم)\s+میاد|به\s+(?:حسابم|کارتم)\s+می‌آید|می‌ریزن\s+(?:به|تو)\s+(?:حسابم|کارتم)|میریزن\s+(?:به|تو)\s+(?:حسابم|کارتم)|برام\s+می‌ریزن|برام\s+میریزن|قراره\s+واریز\s+(?:بشه|بشود|شه|شود|کنن|کنند)|واریز\s+(?:بشه|بشود|شه|شود|میشه|می‌شه|می‌شود|میشود|خواهد\s+شد|کنن|کنند)|میاد\s+به\s+حساب|می‌آید\s+به\s+حساب)(?=\s|$)"""
    )

    private val futureReceivableClauseRegex = Regex(
        """(?<=^|\s)(?:طلب\s+دارم|طلب\s+داریم|طلبکارم|طلبکار\s+هستم|باید\s+بگیرم|باید\s+بهم\s+بده|باید\s+بهم\s+بدن|قراره\s+بهم\s+بده|قراره\s+بهم\s+بدن|بهم\s+بدهکاره|بهم\s+بدهکارن|به\s+من\s+بدهکاره|طلبم\s+هست|طلب\s+منه)(?=\s|$)"""
    )

    private val dueDateHintRegex = Regex(
        """(?<=^|\s)(پس‌فردا|پس\s+فردا|فردا|هفته\s+بعد|هفته\s+آینده|ماه\s+بعد|ماه\s+آینده)(?=\s|$)"""
    )

    private val pastDayOffsetTwoRegex = Regex("""(?<=^|\s)(?:پریروز|دو\s+روز\s+پیش|۲\s+روز\s+پیش|پریشب)(?=\s|$)""")
    private val pastDayOffsetOneRegex = Regex("""(?<=^|\s)(?:دیروز|دیشب|یک\s+روز\s+پیش|یه\s+روز\s+پیش)(?=\s|$)""")
    private val pastDayOffsetThreeRegex = Regex("""(?<=^|\s)(?:سه\s+روز\s+پیش|۳\s+روز\s+پیش)(?=\s|$)""")

    fun extractPastRelativeDayOffset(rawSpeech: String): Int {
        val norm = PersianNumberUtils.normalizePersianText(rawSpeech.trim())
        return when {
            pastDayOffsetThreeRegex.containsMatchIn(norm) -> -3
            pastDayOffsetTwoRegex.containsMatchIn(norm) -> -2
            pastDayOffsetOneRegex.containsMatchIn(norm) -> -1
            else -> 0
        }
    }

    /**
     * Detects whether a spoken clause describes an unpaid/future obligation, expected future inflow,
     * or future planned purchase (`"DEBT"` or `"RECEIVABLE"`) rather than an already-settled transaction.
     */
    fun detectVoiceCommitmentKind(rawSpeech: String): String? {
        val norm = PersianNumberUtils.normalizePersianText(rawSpeech.trim())
        if (norm.isBlank()) return null
        if (isNegatedOrCancelledClause(norm)) return null
        if (settledObligationVerbRegex.containsMatchIn(norm)) return null
        return when {
            futureReceivableClauseRegex.containsMatchIn(norm) -> "RECEIVABLE"
            futureExpectedInflowRegex.containsMatchIn(norm) -> "RECEIVABLE"
            futureDebtClauseRegex.containsMatchIn(norm) -> "DEBT"
            futurePlannedPurchaseRegex.containsMatchIn(norm) -> "DEBT"
            else -> null
        }
    }

    private fun parseSingleVoiceClause(
        rawSpeech: String,
        fallbackTotalToman: Long? = null,
        defaultBankName: String = "نقدی / دستی"
    ): ParsedVoiceExpense {
        val cleaned = pivotNarrativeIntroKeepingAmountInternal(
            stripStumbledNumbers(PersianNumberUtils.normalizePersianText(rawSpeech.trim()))
        )
        val isTaggingExistingSms = fallbackTotalToman != null && fallbackTotalToman > 0L
        val detectedCommitmentKind = if (isTaggingExistingSms) null else detectVoiceCommitmentKind(rawSpeech)
        val isExpectedFutureInflow = detectedCommitmentKind == "RECEIVABLE" &&
            futureExpectedInflowRegex.containsMatchIn(cleaned) &&
            !futureReceivableClauseRegex.containsMatchIn(cleaned)
        val isPlannedFuturePurchase = detectedCommitmentKind == "DEBT" &&
            futurePlannedPurchaseRegex.containsMatchIn(cleaned) &&
            !futureDebtClauseRegex.containsMatchIn(cleaned)
        val commitmentSubLabel = when {
            isExpectedFutureInflow -> "واریز آینده"
            isPlannedFuturePurchase -> "برنامه خرید"
            detectedCommitmentKind == "RECEIVABLE" -> "طلب"
            detectedCommitmentKind == "DEBT" -> "بدهی"
            else -> ""
        }
        val extractedDueDateHint = if (detectedCommitmentKind != null) {
            dueDateHintRegex.find(cleaned)?.groupValues?.get(1)?.replace(whitespaceRegex, " ")?.trim().orEmpty()
        } else {
            ""
        }
        val extractedPastDayOffset = if (isTaggingExistingSms || detectedCommitmentKind != null) {
            0
        } else {
            extractPastRelativeDayOffset(rawSpeech)
        }
        val isIncomeClause = when {
            isTaggingExistingSms -> false
            detectedCommitmentKind == "RECEIVABLE" -> true
            detectedCommitmentKind == "DEBT" -> false
            else -> detectVoiceTransactionIsIncome(rawSpeech)
        }

        val speechBeforeDong = if (cleaned.contains("که") && (cleaned.contains("دنگ") || cleaned.contains("دُنگ") || cleaned.contains("سهم"))) {
            cleaned.substringBefore("که").trim()
        } else {
            cleaned
        }

        // 1. Parse Total Amount in Toman (takes largest explicit currency amount in the clause)
        val totalToman: Long = if (isTaggingExistingSms) {
            fallbackTotalToman!!
        } else {
            parseSpokenPersianAmountToman(speechBeforeDong)
        }

        // 2. Parse Dong Split (سهم دنگ) if spoken
        var dongAmount = 0L
        var dongFriend = ""
        if (cleaned.contains("دنگ") || cleaned.contains("دُنگ") || cleaned.contains("سهم")) {
            val friendRegex = Regex("""(?:دنگ|دُنگ|سهم)\s+([^\s]+)""")
            val rawFriend = friendRegex.find(cleaned)?.groupValues?.get(1)
                ?.replace("بود", "")
                ?.replace("شد", "")
                ?.trim()
            dongFriend = if (!rawFriend.isNullOrBlank()) rawFriend else "دوست"

            if (cleaned.contains("نصفش") || cleaned.contains("نصف")) {
                dongAmount = totalToman / 2L
            } else if (cleaned.contains("که")) {
                val afterKeh = cleaned.substringAfter("که")
                val parsedDong = parseSpokenPersianAmountToman(afterKeh)
                if (parsedDong > 0L) {
                    dongAmount = parsedDong
                } else {
                    val dongNumRegex = Regex("""([0-9]+)\s*(?:هزار\s*)?(?:تومنش|تومانش|تومانی|تومنی|تومن|هزار)""")
                    val dongMatch = dongNumRegex.find(PersianNumberUtils.toEnglishDigits(afterKeh))
                    if (dongMatch != null) {
                        val rawDongNum = dongMatch.groupValues[1].toLongOrNull() ?: 0L
                        dongAmount = if (rawDongNum in 1L..999L) rawDongNum * 1_000L else rawDongNum
                    }
                }
            }
        }

        // 3. Resolve Bank / Source Label
        val effectiveBankName = if (isTaggingExistingSms) {
            defaultBankName.ifBlank { "بلو" }
        } else {
            val spokenBank = detectSpokenCardBankInVoice(speechBeforeDong)
            if (spokenBank != "بانک") spokenBank else defaultBankName.takeIf { it.isNotBlank() && it != "بانک" } ?: "نقدی / دستی"
        }
        val cardLabel = if (effectiveBankName == "نقدی / دستی") "نقدی / دستی" else "کارت $effectiveBankName"

        // 4. Protect item quantities (after stripping stammered mental-math chains)
        val protectedQuantities = protectItemQuantities(speechBeforeDong)
        var corePhrase = protectedQuantities.protectedText

        exactPhraseAmounts.forEach { (phrase, _) ->
            corePhrase = corePhrase.replace(phrase, " ")
        }
        // Strip numeric digits (outside ⟪QTY...⟫ placeholders):
        // If an explicit currency/multiplier number exists in the clause (e.g. "۵۵ هزار تومان"),
        // ONLY strip currency-tagged numbers so bare brand/identifier numbers (e.g. "۵۰۴۰") stay intact!
        val explicitCurrencyDigitRegex = Regex(
            """[۰-۹0-9][۰-۹0-9,٬،]*\s*(?:(?:میلیاردی|میلیارد|میلیونی|میلیون|ملیون|هزارش|هزاری|هزار)\s*(?:تومانش|تومنش|تومانی|تومنی|تومان|تومن|ریالش|ریالی|ریال)?|(?:تومانش|تومنش|تومانی|تومنی|تومان|تومن|ریالش|ریالی|ریال))"""
        )
        if (explicitCurrencyDigitRegex.containsMatchIn(corePhrase)) {
            corePhrase = corePhrase.replace(explicitCurrencyDigitRegex, " ")
        } else {
            corePhrase = corePhrase.replace(
                Regex("""[۰-۹0-9][۰-۹0-9,٬،]*\s*(?:میلیاردی|میلیارد|میلیونی|میلیون|ملیون|هزارش|هزاری|هزار)?\s*(?:تومانش|تومنش|تومانی|تومنی|تومان|تومن|ریالش|ریالی|ریال)?"""),
                " "
            )
        }
        // Strip spoken number words followed by هزار/میلیون/تومن/تومان/تومانش/تومنش/تومانی/تومنی
        val numberWordsPattern = numberWordTokens.keys.sortedByDescending { it.length }.joinToString("|")
        corePhrase = corePhrase.replace(
            Regex("""(?<=^|\s)(?:(?:$numberWordsPattern)(?:\s+و\s+(?:$numberWordsPattern))*)\s*(?:میلیاردی|میلیارد|میلیونی|میلیون|ملیون|هزارش|هزاری|هزار)?\s*(?:تومانش|تومنش|تومانی|تومنی|تومان|تومن|ریالش|ریالی|ریال)(?=\s|$)"""),
            " "
        )
        corePhrase = corePhrase.replace(
            Regex("""(?<=^|\s)(?:(?:$numberWordsPattern)(?:\s+و\s+(?:$numberWordsPattern))*)\s+(?:میلیاردی|میلیارد|میلیونی|میلیون|ملیون|هزارش|هزاری|هزار)(?=\s|$)"""),
            " "
        )

        // Normalize Persian dative compound light-verb phrases BEFORE preposition stripping:
        // e.g. "به فقیر کمک کردم" -> "کمک به فقیر", "به علی هدیه دادم" -> "هدیه به علی"
        val dativeNounsPattern = dativeCompoundActionNouns.joinToString("|")
        val dativeCompoundRegex = Regex(
            """(?<=^|\s)به\s+(.+?)\s+($dativeNounsPattern)(?:\s+(?:کردم|دادم|زدم|بدم|کنم|بکنم))?(?=\s|$)"""
        )
        corePhrase = dativeCompoundRegex.replace(corePhrase) { m ->
            val recipient = m.groupValues[1].trim()
            val actionNoun = m.groupValues[2].trim()
            if (recipient.isNotBlank()) "$actionNoun به $recipient" else actionNoun
        }

        // Normalize Persian 1st-person sales phrases: e.g. "گوجه فروختم" -> "فروش گوجه"
        val saleVerbRegex = Regex("""(?<=^|\s)(?:فروختم|فروختیم|فروش\s+رفت)(?=\s|$)""")
        val hasSaleVerb = saleVerbRegex.containsMatchIn(corePhrase)
        if (hasSaleVerb) {
            corePhrase = corePhrase.replace(saleVerbRegex, " ")
        }

        // Normalize colloquial Persian possessive suffix after "خرج" before "شد":
        // e.g. "خرج بستنیش شد" -> "خرج بستنی شد", "خرج کرایه‌اش شد" -> "خرج کرایه شد"
        val preservedIshWords = setOf("آرایش", "نمایش", "آزمایش", "پالایش", "فرمایش", "افزایش", "کاهش", "آسایش", "ستایش")
        corePhrase = Regex("""(?<=^|\s)خرج\s+([ء-ۿ\u200C]{3,})ه‌اش(?=\s+شد(?:\s|$))""")
            .replace(corePhrase) { m -> "خرج ${m.groupValues[1]}ه" }
        corePhrase = Regex("""(?<=^|\s)خرج\s+([ء-ۿ\u200C]{2,}یش)(?=\s+شد(?:\s|$))""")
            .replace(corePhrase) { m ->
                val word = m.groupValues[1]
                if (word in preservedIshWords) m.value else "خرج ${word.removeSuffix("ش")}"
            }

        // Strip trailing relative filler clauses like "بزرگی که داشتم" -> "بزرگ", "بدهی‌ای که داشتم" -> "بدهی"
        corePhrase = Regex("""([ء-ۿ\u200C]{2,})(?:‌ای|ی)\s+(?:که\s+داشتم|که\s+دارم|که\s+بود|که\s+بودش|که\s+می‌دادم|که\s+میدادم)(?=\s|$)""")
            .replace(corePhrase) { m -> m.groupValues[1] }
        corePhrase = Regex("""(?<=^|\s)(?:که\s+داشتم|که\s+دارم|که\s+بود|که\s+بودش|که\s+می‌دادم|که\s+میدادم)(?=\s|$)""")
            .replace(corePhrase, " ")

        corePhrase = corePhrase
            .replace(Regex("""(?:با|از)\s+کارت\s+[^\s]+"""), " ")
            .replace(Regex("""(?<!به\s)کارت\s+(?:بلو|ملت|ملی|سپه|پاسارگاد|سامان|صادرات|تجارت|رسالت|ویپاد|مسکن|رفاه|پارسیان|کشاورزی|گردشگری|شهر|سینا|آینده|دی|کارآفرین)"""), " ")

        if (detectedCommitmentKind != null) {
            corePhrase = corePhrase
                .replace(dueDateHintRegex, " ")
                .replace(futureExpectedInflowRegex, " ")
                .replace(futurePlannedPurchaseRegex, " ")
                .replace(Regex("""(?<=^|\s)(?:دارم|داریم|هستم|هست|باید\s+بدم|باید\s+بدهم|باید\s+پرداخت\s+کنم|باید\s+بگیرم|قراره\s+بهم\s+بده|قراره\s+بهم\s+بدن|باید\s+بهم\s+بده|باید\s+بهم\s+بدن|بخرم|بگیرم|بدم|بدهم|پرداخت\s+کنم|خرج\s+کنم)(?=\s|$)"""), " ")
                .replace(Regex("""(?<=^|\s)(?:بدهکارم|بدهکار)(?=\s|$)"""), " بدهی ")
                .replace(Regex("""(?<=^|\s)(?:طلبکارم|طلبکار)(?=\s|$)"""), " طلب ")
                .replace(Regex("""(?<=^|\s)به\s+([ء-ۿ\u200C]+)\s+بدهی(?=\s|$)"""), "بدهی به $1")
                .replace(Regex("""(?<=^|\s)از\s+([ء-ۿ\u200C]+)\s+طلب(?=\s|$)"""), "طلب از $1")
                .replace(whitespaceRegex, " ")
                .trim()
        }

        val explicitFromPattern = Regex("""(.+?)\s+از\s+(.+)""")
        val explicitMatch = if (detectedCommitmentKind == null) {
            explicitFromPattern.find(corePhrase.trim())
        } else {
            null
        }

        val storeName: String
        val rawPurposeCategory: String
        val detectedBrand = knownBrandsAndStores.firstOrNull { cleaned.contains(it.first) }?.second.orEmpty()

        if (explicitMatch != null) {
            val leftPart = trimLeadingPrepositionsAndVav(
                protectedQuantities.restore(stripGreetingsAndFillers(explicitMatch.groupValues[1]))
            )
            val rightStore = trimLeadingPrepositionsAndVav(
                protectedQuantities.restore(stripGreetingsAndFillers(explicitMatch.groupValues[2]))
            )
            val matchedKnownStore = knownBrandsAndStores.firstOrNull { rightStore.contains(it.first) }?.second
                ?: rightStore.ifBlank { effectiveBankName }
            storeName = if (isTaggingExistingSms) effectiveBankName else matchedKnownStore
            rawPurposeCategory = if (isTaggingExistingSms && matchedKnownStore.isNotBlank() && leftPart != matchedKnownStore) {
                "${leftPart.ifBlank { matchedKnownStore }} از $matchedKnownStore"
            } else {
                leftPart.ifBlank { matchedKnownStore }
            }
        } else {
            val stripped = trimLeadingPrepositionsAndVav(
                protectedQuantities.restore(stripGreetingsAndFillers(corePhrase))
            )
            storeName = if (isTaggingExistingSms) {
                effectiveBankName
            } else {
                detectedBrand.ifBlank { effectiveBankName }
            }
            rawPurposeCategory = if (stripped.isNotBlank()) {
                stripped
            } else if (isTaggingExistingSms) {
                trimLeadingPrepositionsAndVav(
                    protectedQuantities.restore(stripGreetingsAndFillers(protectedQuantities.protectedText))
                )
            } else {
                ""
            }
        }

        val rawCleanedPurpose = trimLeadingPrepositionsAndVav(rawPurposeCategory)
        val cleanedPurpose = when (rawCleanedPurpose) {
            "حقوقم", "حقوق من" -> "حقوق"
            "یارانه‌ام", "یارانه من" -> "یارانه"
            "پاداشم", "پاداش من" -> "پاداش"
            "عیدیم", "عیدی من" -> "عیدی"
            "درآمدم", "درآمد من" -> "درآمد"
            "کارانه‌ام", "کارانه من" -> "کارانه"
            else -> rawCleanedPurpose
        }
        val purposeCategory = if (detectedCommitmentKind != null) {
            val cleanCommitmentTitle = cleanedPurpose
                .replace(Regex("""^(?:به\s+)?(?:یه|یک|۱)\s+"""), "")
                .trim()
            when {
                cleanCommitmentTitle.isBlank() && isExpectedFutureInflow -> "واریز به حساب"
                cleanCommitmentTitle.isBlank() && isPlannedFuturePurchase -> "خرید برنامه‌ریزی‌شده"
                cleanCommitmentTitle.isBlank() -> if (detectedCommitmentKind == "RECEIVABLE") "طلب" else "بدهی"
                else -> cleanCommitmentTitle
            }
        } else if (isIncomeClause) {
            val saleNormalized = if (hasSaleVerb) {
                when {
                    cleanedPurpose.isBlank() -> "فروش"
                    cleanedPurpose.startsWith("فروش ") || cleanedPurpose == "فروش" -> cleanedPurpose
                    else -> "فروش $cleanedPurpose"
                }
            } else {
                cleanedPurpose
            }
            when (saleNormalized) {
                "", "دریافت", "دریافتی" -> if (rawSpeech.contains("واریز")) "واریز وجه" else "دریافت وجه"
                "واریز", "واریزی" -> "واریز وجه"
                else -> saleNormalized
            }
        } else if (cleanedPurpose.isBlank() &&
            totalToman > 0L &&
            partitiveSpendBackRefRegex.containsMatchIn(cleaned) &&
            expenseVerbOrMarkerRegex.containsMatchIn(cleaned)
        ) {
            "خرج کرد"
        } else {
            cleanedPurpose
        }
        val safeDongAmount = dongAmount.coerceIn(0L, totalToman)
        val userShare = (totalToman - safeDongAmount).coerceAtLeast(0L)
        val category = when {
            isExpectedFutureInflow -> "تعهدات (واریز آینده)"
            isPlannedFuturePurchase -> "تعهدات (برنامه خرید)"
            detectedCommitmentKind == "DEBT" -> "تعهدات (بدهی)"
            detectedCommitmentKind == "RECEIVABLE" -> "تعهدات (طلب)"
            else -> inferExpenseCategory(purposeCategory, isIncome = isIncomeClause)
        }

        return ParsedVoiceExpense(
            recognizedSpeech = cleaned,
            totalAmountToman = totalToman,
            userShareToman = userShare,
            dongAmountToman = safeDongAmount,
            dongFriendName = dongFriend,
            storeName = storeName,
            purposeCategory = purposeCategory,
            cardLabel = cardLabel,
            category = category,
            merchant = detectedBrand,
            isIncome = isIncomeClause,
            commitmentKind = detectedCommitmentKind,
            dueDateHint = extractedDueDateHint,
            eventDayOffset = extractedPastDayOffset,
            commitmentSubLabel = commitmentSubLabel
        )
    }

    fun parseSpokenText(
        text: String,
        fallbackTotalToman: Long? = null,
        defaultBankName: String = "نقدی / دستی"
    ): ParsedVoiceExpense = parseVoiceCommand(text, fallbackTotalToman, defaultBankName)
}


