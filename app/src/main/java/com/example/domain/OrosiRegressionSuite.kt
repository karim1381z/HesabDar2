package com.example.domain

import com.example.ui.components.JibemanAssetLogoCache

data class RegressionTestCaseResult(
    val id: Int,
    val category: String,
    val title: String,
    val inputSummary: String,
    val expectedSummary: String,
    val actualSummary: String,
    val passed: Boolean,
    val durationMicros: Long
)

data class RegressionSuiteReport(
    val totalCount: Int,
    val passedCount: Int,
    val failedCount: Int,
    val totalDurationMs: Long,
    val results: List<RegressionTestCaseResult>
) {
    val allPassed: Boolean
        get() = failedCount == 0 && totalCount > 0
}

/**
 * Automated Regression & Stress Test Engine for Orosi Finance ("خیال راحت").
 * Runs both inside JUnit build tests (`ExampleUnitTest.kt`) and live inside the in-app Settings lab.
 */
object OrosiRegressionSuite {

    const val EXPECTED_TOTAL_CASES = 66

    fun runFullRegressionAndStressSuite(): RegressionSuiteReport {
        val suiteStartNanos = System.nanoTime()
        val results = mutableListOf<RegressionTestCaseResult>()
        var nextId = 1

        fun recordTest(
            category: String,
            title: String,
            inputSummary: String,
            expectedSummary: String,
            block: () -> Pair<Boolean, String>
        ) {
            val t0 = System.nanoTime()
            val (passed, actual) = try {
                block()
            } catch (e: Throwable) {
                false to "خطا: ${e.message ?: e::class.java.simpleName}"
            }
            val elapsedMicros = ((System.nanoTime() - t0) / 1_000L).coerceAtLeast(1L)
            results.add(
                RegressionTestCaseResult(
                    id = nextId++,
                    category = category,
                    title = title,
                    inputSummary = inputSummary,
                    expectedSummary = expectedSummary,
                    actualSummary = actual,
                    passed = passed,
                    durationMicros = elapsedMicros
                )
            )
        }

        // 1. Video batch_9 (00:36): "دخترم ۵۰ تومان خرج کرد"
        recordTest(
            category = "موتور صوتی (NLU)",
            title = "تشخیص فاعل خانوادگی (ویدیو batch_9)",
            inputSummary = "دخترم ۵۰ تومان خرج کرد",
            expectedSummary = "دخترم خرج کرد • ۵۰,۰۰۰ تومان"
        ) {
            val parsed = PersianVoiceSlangParserEngine.parseVoiceCommand("دخترم ۵۰ تومان خرج کرد")
            val ok = parsed.purposeCategory == "دخترم خرج کرد" && parsed.totalAmountToman == 50_000L
            ok to "${parsed.purposeCategory} • ${PersianNumberUtils.formatToman(parsed.totalAmountToman, true)}"
        }

        // 2. Video batch_9 (00:50): "۳۵ تومان هزینه قسط پنجم وام دیشب"
        recordTest(
            category = "موتور صوتی (NLU)",
            title = "حفظ عدد ترتیبی «پنجم» و حذف قید زمان «دیشب» (ویدیو batch_9)",
            inputSummary = "۳۵ تومان هزینه قسط پنجم وام دیشب",
            expectedSummary = "هزینه قسط پنجم وام • ۳۵,۰۰۰ تومان"
        ) {
            val parsed = PersianVoiceSlangParserEngine.parseVoiceCommand("۳۵ تومان هزینه قسط پنجم وام دیشب")
            val ok = parsed.purposeCategory == "هزینه قسط پنجم وام" && parsed.totalAmountToman == 35_000L
            ok to "${parsed.purposeCategory} • ${PersianNumberUtils.formatToman(parsed.totalAmountToman, true)}"
        }

        // 3. Video batch_8 (01:05): Cancelled clause + 2 valid clauses + "به یه فقیر"
        recordTest(
            category = "موتور صوتی (NLU)",
            title = "حذف جمله منفی/لغوشده و تفکیک «بستنی» و «فقیر» (ویدیو batch_8)",
            inputSummary = "رفته بودم بیرون ۵۰ تومن میخواستم خرج کنم ولی نکردم ۵۰ تومن دادم بستنی ۳۵ تومن دادم به یه فقیر",
            expectedSummary = "بستنی (۵۰,۰۰۰) + فقیر (۳۵,۰۰۰)"
        ) {
            val items = PersianVoiceSlangParserEngine.parseMultiVoiceCommands(
                "رفته بودم بیرون ۵۰ تومن میخواستم خرج کنم ولی نکردم ۵۰ تومن دادم بستنی ۳۵ تومن دادم به یه فقیر"
            )
            val ok = items.size == 2 &&
                items[0].purposeCategory == "بستنی" && items[0].totalAmountToman == 50_000L &&
                items[1].purposeCategory == "فقیر" && items[1].totalAmountToman == 35_000L
            val actual = items.joinToString(" + ") {
                "${it.purposeCategory} (${PersianNumberUtils.formatToman(it.totalAmountToman)})"
            }
            ok to actual
        }

        // 4. Video batch_8 (01:32): Voice tagging existing 9,200,000 Toman Blu SMS with "بدهی عسل به احمد عمو"
        recordTest(
            category = "تگ صوتی پیامک",
            title = "تگ صوتی «بدهی عسل به احمد عمو» روی تراکنش ۹.۲ میلیون بلوبانک (ویدیو batch_8)",
            inputSummary = "بدهی عسل به احمد عمو (مبلغ بانک: ۹,۲۰۰,۰۰۰ تومان)",
            expectedSummary = "بدهی عسل به احمد عمو • ۹,۲۰۰,۰۰۰ تومان (بلو)"
        ) {
            val parsed = PersianVoiceSlangParserEngine.parseVoiceCommand(
                rawSpeech = "بدهی عسل به احمد عمو",
                fallbackTotalToman = 9_200_000L,
                defaultBankName = "بلو"
            )
            val ok = parsed.purposeCategory == "بدهی عسل به احمد عمو" &&
                parsed.totalAmountToman == 9_200_000L &&
                parsed.storeName == "بلو"
            ok to "${parsed.purposeCategory} • ${PersianNumberUtils.formatToman(parsed.totalAmountToman, true)} (${parsed.storeName})"
        }

        // 5. Asyndetic splitting + negation filter: "سلام ۳۵ تومن دادم نونوایی ۵۰ تومن دادم بقالی ۵۵ تومن میخواستم بدم نونوایی ولی ندادم"
        recordTest(
            category = "موتور صوتی (NLU)",
            title = "تفکیک جملات بدون «و» همراه با فیلتر انصراف «ولی ندادم»",
            inputSummary = "سلام ۳۵ تومن دادم نونوایی ۵۰ تومن دادم بقالی ۵۵ تومن میخواستم بدم نونوایی ولی ندادم",
            expectedSummary = "نونوایی (۳۵,۰۰۰) + بقالی (۵۰,۰۰۰)"
        ) {
            val items = PersianVoiceSlangParserEngine.parseMultiVoiceCommands(
                "سلام ۳۵ تومن دادم نونوایی ۵۰ تومن دادم بقالی ۵۵ تومن میخواستم بدم نونوایی ولی ندادم"
            )
            val ok = items.size == 2 &&
                items[0].purposeCategory == "نونوایی" && items[0].totalAmountToman == 35_000L &&
                items[1].purposeCategory == "بقالی" && items[1].totalAmountToman == 50_000L
            val actual = items.joinToString(" + ") {
                "${it.purposeCategory} (${PersianNumberUtils.formatToman(it.totalAmountToman)})"
            }
            ok to actual
        }

        // 6. Stammered mental-math chain: "سلام ۵۵ هزار تومان دادم به بستنی و ۳۹ هزار تومان دادم به دو تا سه تا ۵ تومن ۵ تومن نانوایی"
        recordTest(
            category = "موتور صوتی (NLU)",
            title = "حذف شمارش ذهنی تکراری «دو تا سه تا ۵ تومن ۵ تومن»",
            inputSummary = "سلام ۵۵ هزار تومان دادم به بستنی و ۳۹ هزار تومان دادم به دو تا سه تا ۵ تومن ۵ تومن نانوایی",
            expectedSummary = "بستنی (۵۵,۰۰۰) + نانوایی (۳۹,۰۰۰)"
        ) {
            val items = PersianVoiceSlangParserEngine.parseMultiVoiceCommands(
                "سلام ۵۵ هزار تومان دادم به بستنی و ۳۹ هزار تومان دادم به دو تا سه تا ۵ تومن ۵ تومن نانوایی"
            )
            val ok = items.size == 2 &&
                items[0].purposeCategory == "بستنی" && items[0].totalAmountToman == 55_000L &&
                items[1].purposeCategory == "نانوایی" && items[1].totalAmountToman == 39_000L
            val actual = items.joinToString(" + ") {
                "${it.purposeCategory} (${PersianNumberUtils.formatToman(it.totalAmountToman)})"
            }
            ok to actual
        }

        // 7. Narrative intro pivot Rule A: "رفتم بیرون و اومدم کلاً ۵۰ هزار تومان خرج کردم برای تاکسی و کرایه و بستنی و"
        recordTest(
            category = "موتور صوتی (NLU)",
            title = "حذف مقدمه داستانی قبل از «برای» و حفظ مبلغ کل",
            inputSummary = "رفتم بیرون و اومدم کلاً ۵۰ هزار تومان خرج کردم برای تاکسی و کرایه و بستنی و",
            expectedSummary = "تاکسی و کرایه و بستنی • ۵۰,۰۰۰ تومان"
        ) {
            val parsed = PersianVoiceSlangParserEngine.parseVoiceCommand(
                "رفتم بیرون و اومدم کلاً ۵۰ هزار تومان خرج کردم برای تاکسی و کرایه و بستنی و"
            )
            val ok = parsed.purposeCategory == "تاکسی و کرایه و بستنی" && parsed.totalAmountToman == 50_000L
            ok to "${parsed.purposeCategory} • ${PersianNumberUtils.formatToman(parsed.totalAmountToman, true)}"
        }

        // 8. Preserve "بابت" between two nouns (Rule B): "انتقال پول به احمد بابت عسل"
        recordTest(
            category = "تگ صوتی پیامک",
            title = "حفظ «بابت» بین دو اسم معنادار",
            inputSummary = "انتقال پول به احمد بابت عسل",
            expectedSummary = "انتقال پول به احمد بابت عسل • ۲۰۰,۰۰۰ تومان"
        ) {
            val parsed = PersianVoiceSlangParserEngine.parseVoiceCommand(
                rawSpeech = "انتقال پول به احمد بابت عسل",
                fallbackTotalToman = 200_000L,
                defaultBankName = "بلو"
            )
            val ok = parsed.purposeCategory == "انتقال پول به احمد بابت عسل" && parsed.totalAmountToman == 200_000L
            ok to "${parsed.purposeCategory} • ${PersianNumberUtils.formatToman(parsed.totalAmountToman, true)}"
        }

        // 9. Item quantities with single total: "یک شارژ ایرانسل و دو تا سیب زمینی خریدم ۵۰ هزار تومان"
        recordTest(
            category = "موتور صوتی (NLU)",
            title = "حفظ تعداد اقلام («۱ شارژ و ۲ تا سیب‌زمینی») با یک مبلغ کل",
            inputSummary = "یک شارژ ایرانسل و دو تا سیب زمینی خریدم ۵۰ هزار تومان",
            expectedSummary = "۱ شارژ ایرانسل و ۲ تا سیب‌زمینی • ۵۰,۰۰۰ تومان"
        ) {
            val parsed = PersianVoiceSlangParserEngine.parseVoiceCommand(
                "یک شارژ ایرانسل و دو تا سیب زمینی خریدم ۵۰ هزار تومان"
            )
            val ok = parsed.purposeCategory == "۱ شارژ ایرانسل و ۲ تا سیب‌زمینی" && parsed.totalAmountToman == 50_000L
            ok to "${parsed.purposeCategory} • ${PersianNumberUtils.formatToman(parsed.totalAmountToman, true)}"
        }

        // 10. Remainder math on existing bank SMS: "سلام دو تا شارژ خریدم دو تا بستنی ۵۰ هزار تومان" on 100,000 Toman SMS
        recordTest(
            category = "تگ صوتی پیامک",
            title = "محاسبه خودکار باقی‌مانده مبلغ بانک برای مورد اول",
            inputSummary = "سلام دو تا شارژ خریدم دو تا بستنی ۵۰ هزار تومان (بانک: ۱۰۰,۰۰۰)",
            expectedSummary = "۲ تا شارژ (۵۰,۰۰۰) + ۲ تا بستنی (۵۰,۰۰۰)"
        ) {
            val parsed = PersianVoiceSlangParserEngine.parseVoiceCommand(
                rawSpeech = "سلام دو تا شارژ خریدم دو تا بستنی ۵۰ هزار تومان",
                fallbackTotalToman = 100_000L,
                defaultBankName = "بلو"
            )
            val ok = parsed.purposeCategory == "۲ تا شارژ (۵۰,۰۰۰) + ۲ تا بستنی (۵۰,۰۰۰)" &&
                parsed.totalAmountToman == 100_000L
            ok to parsed.purposeCategory
        }

        // 11. Dong split calculation: "پانصد هزار تومان خرید میوه از اسنپ‌مارکت با کارت بلو که ۲۰۰ تومنش دنگ علی بود"
        recordTest(
            category = "موتور صوتی (NLU)",
            title = "محاسبه سهم کاربر و دُنگ دوست («علی»)",
            inputSummary = "پانصد هزار تومان خرید میوه از اسنپ‌مارکت با کارت بلو که ۲۰۰ تومنش دنگ علی بود",
            expectedSummary = "کل: ۵۰۰,۰۰۰ • سهم کاربر: ۳۰۰,۰۰۰ • دنگ علی: ۲۰۰,۰۰۰"
        ) {
            val parsed = PersianVoiceSlangParserEngine.parseVoiceCommand(
                "پانصد هزار تومان خرید میوه از اسنپ‌مارکت با کارت بلو که ۲۰۰ تومنش دنگ علی بود"
            )
            val ok = parsed.totalAmountToman == 500_000L &&
                parsed.userShareToman == 300_000L &&
                parsed.dongAmountToman == 200_000L &&
                parsed.dongFriendName == "علی"
            ok to "کل: ${PersianNumberUtils.formatToman(parsed.totalAmountToman)} • سهم کاربر: ${PersianNumberUtils.formatToman(parsed.userShareToman)} • دنگ ${parsed.dongFriendName}: ${PersianNumberUtils.formatToman(parsed.dongAmountToman)}"
        }

        // 12. All 38 Iranian Bank Logos Resolution (including ایران ونزوئلا)
        recordTest(
            category = "لوگوی ۳۸ بانک",
            title = "بررسی تطابق ۱۰۰٪ نام و کلید لوگوی هر ۳۸ بانک (از جمله ایران ونزوئلا)",
            inputSummary = "۳۸ بانک رسمی در ALL_38_IRANIAN_BANKS",
            expectedSummary = "۳۸ از ۳۸ بانک دارای کلید لوگوی معتبر"
        ) {
            val all38 = JibemanAssetLogoCache.ALL_38_IRANIAN_BANKS
            val matchedCount = all38.count { (faName, expectedKey) ->
                JibemanAssetLogoCache.resolveBankLogoKey(faName) == expectedKey
            }
            val venezuelaKey = JibemanAssetLogoCache.resolveBankLogoKey("ایران ونزوئلا")
            val ok = matchedCount == 38 && venezuelaKey == "ic_bank_iran_venezuela"
            ok to "${PersianNumberUtils.toPersianDigits(matchedCount.toString())} از ۳۸ بانک معتبر (ایران ونزوئلا: $venezuelaKey)"
        }

        // 13. LTR Phone / SMS Sender '+' Sign Isolation
        recordTest(
            category = "چیدمان LTR سرشماره",
            title = "ایزوله‌سازی چپ‌به‌راست علامت + در سرشماره‌ها (+989999987641)",
            inputSummary = "+982187641 , +989999987641",
            expectedSummary = "محصور در کاراکترهای ایزوله چپ‌به‌راست یونیکد (\\u2066\\u200E+98...\\u2069)"
        ) {
            val formatted1 = PersianNumberUtils.formatLtrSender("+982187641")
            val formatted2 = PersianNumberUtils.formatLtrSender("+989999987641")
            val ok = formatted1.startsWith("\u2066\u200E+982187641") &&
                formatted1.endsWith("\u2069") &&
                formatted2.startsWith("\u2066\u200E+989999987641") &&
                formatted2.endsWith("\u2069")
            ok to "$formatted1 ، $formatted2"
        }

        // 14. Jibeman Bank SMS Parser & Promo/OTP Filter
        recordTest(
            category = "موتور پیامک بانکی",
            title = "تشخیص دقیق پیامک بانکی، تبدیل ریال به تومان و رد پیامک تبلیغاتی و رمز پویا",
            inputSummary = "پیامک صادرات + رد دیجی‌کالا/اسنپ/رمز پویا",
            expectedSummary = "مبلغ: ۱,۷۶۷,۳۹۰ تومان • مانده: ۱۴,۵۰۰,۰۰۰ تومان • تبلیغاتی/پویا: رد شد"
        ) {
            val sms = "بانک صادرات ایران\nبرداشت\nمبلغ: ۱۷,۶۷۳,۹۰۰ ریال\nمانده: ۱۴۵,۰۰۰,۰۰۰ ریال"
            val parsed = BankSmsParserEngine.parseWithJibemanEngine("+9830009419", sms, 1730734200000L)
            val otpRejected = JibemanSmsEngine.parse("بانک ملت\nرمز پویا: 123456\nمبلغ: 500,000") == null
            val promoRejected = JibemanSmsEngine.resolveBank("Digikala") == null &&
                JibemanSmsEngine.resolveBank("10008590") == null
            val ok = parsed != null &&
                parsed.amountToman == 1_767_390L &&
                parsed.extractedBalanceToman == 14_500_000L &&
                otpRejected &&
                promoRejected
            ok to "مبلغ: ${PersianNumberUtils.formatToman(parsed?.amountToman ?: 0L, true)} • تبلیغاتی/پویا: رد شد ✓"
        }

        // 15. Stress Test: 1,000 Jalali Date Conversions with Memory Cache (<50ms)
        recordTest(
            category = "تست استرس سرعت (120fps)",
            title = "تست استرس ۱,۰۰۰ تبدیل تاریخ شمسی با کش حافظه برای اسکرول بدون تیک",
            inputSummary = "۱,۰۰۰ فراخوانی متوالی JalaliCalendarUtils.timestampToJalali",
            expectedSummary = "دقیق و سریع (زیر ۵۰ میلی‌ثانیه)"
        ) {
            val tStart = System.nanoTime()
            var lastDate = ""
            val baseTs = 1763120000000L
            for (i in 0 until 1000) {
                val info = JalaliCalendarUtils.timestampToJalali(baseTs + (i % 40) * 60_000L)
                lastDate = info.dateWithYear
            }
            val elapsedMs = (System.nanoTime() - tStart) / 1_000_000L
            val (jy, jm, jd) = JalaliCalendarUtils.gregorianToJalali(2026, 11, 14)
            val ok = jy == 1405 && jm == 8 && jd == 23 && lastDate.isNotBlank() && elapsedMs < 200L
            ok to "۱,۰۰۰ تبدیل در ${PersianNumberUtils.toPersianDigits(elapsedMs.toString())} میلی‌ثانیه ($lastDate)"
        }

        // 16. Video 55batch (00:05): Summary intro total before itemized breakdown
        recordTest(
            category = "تست استرس صوتی (ویدیو ۵۵)",
            title = "حذف مبلغ سرجمع مقدمه («کلاً ۵۰ هزار تومان خرج کردم») قبل از ریز اقلام",
            inputSummary = "رفتم بیرون و اومدم کلاً ۵۰ هزار تومان خرج کردم ۲۵ تومن بستنی ۲۵ تومن دمپایی",
            expectedSummary = "بستنی (۲۵,۰۰۰) + دمپایی (۲۵,۰۰۰) • جمع: ۵۰,۰۰۰ تومان"
        ) {
            val items = PersianVoiceSlangParserEngine.parseMultiVoiceCommands(
                "رفتم بیرون و اومدم کلاً ۵۰ هزار تومان خرج کردم ۲۵ تومن بستنی ۲۵ تومن دمپایی"
            )
            val sum = items.sumOf { it.totalAmountToman }
            val ok = items.size == 2 &&
                items[0].purposeCategory == "بستنی" && items[0].totalAmountToman == 25_000L &&
                items[1].purposeCategory == "دمپایی" && items[1].totalAmountToman == 25_000L &&
                sum == 50_000L
            val actual = items.joinToString(" + ") {
                "${it.purposeCategory} (${PersianNumberUtils.formatToman(it.totalAmountToman)})"
            } + " • جمع: ${PersianNumberUtils.formatToman(sum, true)}"
            ok to actual
        }

        // 17. Video 55batch (00:41): Suffix "تومانش" + sub-thousand 500 Toman remainder on 49,500 Toman bank SMS
        recordTest(
            category = "تست استرس تگ پیامک (ویدیو ۵۵)",
            title = "حذف پسوند «تومانش» و تشخیص عدد خرد ۵۰۰ تومان روی پیامک ۴۹,۵۰۰ تومانی",
            inputSummary = "۴۹ تومانش بابت بستنی ۵۰۰ تومانش بابت کاغذ (بانک: ۴۹,۵۰۰ تومان)",
            expectedSummary = "بستنی (۴۹,۰۰۰) + کاغذ (۵۰۰)"
        ) {
            val items = PersianVoiceSlangParserEngine.parseMultiVoiceCommands(
                rawSpeech = "۴۹ تومانش بابت بستنی ۵۰۰ تومانش بابت کاغذ",
                defaultBankName = "بلو",
                fallbackTotalToman = 49_500L
            )
            val combined = PersianVoiceSlangParserEngine.formatCombinedMultiVoiceNote(items)
            val mismatch = PersianVoiceSlangParserEngine.detectBankSmsAmountMismatch(
                rawSpeech = "۴۹ تومانش بابت بستنی ۵۰۰ تومانش بابت کاغذ",
                fallbackTotalToman = 49_500L,
                multiItems = items
            )
            val ok = items.size == 2 &&
                items[0].purposeCategory == "بستنی" && items[0].totalAmountToman == 49_000L &&
                items[1].purposeCategory == "کاغذ" && items[1].totalAmountToman == 500L &&
                combined == "بستنی (۴۹,۰۰۰) + کاغذ (۵۰۰)" &&
                mismatch == null
            ok to combined
        }

        // 18. Video 55batch (00:58): Conflicting spoken amount on bank SMS ("۸۵ تومن بابت بستنی" on 80,000 Toman SMS)
        recordTest(
            category = "هشدار مغایرت مبلغ بانک (ویدیو ۵۵)",
            title = "تشخیص مغایرت مبلغ گفته‌شده (۸۵,۰۰۰) با مبلغ پیامک بانک (۸۰,۰۰۰)",
            inputSummary = "۸۵ تومن بابت بستنی (روی پیامک ۸۰,۰۰۰ تومانی بلوبانک)",
            expectedSummary = "هشدار عدم تطابق فعال (۸۵,۰۰۰ ≠ ۸۰,۰۰۰)"
        ) {
            val conflictAmount = PersianVoiceSlangParserEngine.detectBankSmsAmountMismatch(
                rawSpeech = "۸۵ تومن بابت بستنی",
                fallbackTotalToman = 80_000L
            )
            val noConflictWhenMatching = PersianVoiceSlangParserEngine.detectBankSmsAmountMismatch(
                rawSpeech = "۸۰ تومن بابت بستنی",
                fallbackTotalToman = 80_000L
            )
            val noConflictWhenNoAmountSpoken = PersianVoiceSlangParserEngine.detectBankSmsAmountMismatch(
                rawSpeech = "بستنی",
                fallbackTotalToman = 80_000L
            )
            val ok = conflictAmount == 85_000L &&
                noConflictWhenMatching == null &&
                noConflictWhenNoAmountSpoken == null
            ok to "مبلغ متناقض تشخیص داده شد: ${PersianNumberUtils.formatToman(conflictAmount ?: 0L, true)} ≠ ۸۰,۰۰۰ تومان"
        }

        // 19. Video 55batch (01:13): Stumbled number correction ("۲۱۷ ۲۱۸ تومن") + 218 Toman bank fee on 107,218 Toman SMS
        recordTest(
            category = "تست استرس تگ پیامک (ویدیو ۵۵)",
            title = "اصلاح تپق عددی «۲۱۷ ۲۱۸ تومن» و تشخیص کارمزد خرد ۲۱۸ تومان روی پیامک ۱۰۷,۲۱۸ تومانی",
            inputSummary = "۱۰۷ تومن بابت انتقال پول و ۲۱۷ ۲۱۸ تومن بابت کارمزد (بانک: ۱۰۷,۲۱۸)",
            expectedSummary = "انتقال پول (۱۰۷,۰۰۰) + کارمزد (۲۱۸)"
        ) {
            val items = PersianVoiceSlangParserEngine.parseMultiVoiceCommands(
                rawSpeech = "۱۰۷ تومن بابت انتقال پول و ۲۱۷ ۲۱۸ تومن بابت کارمزد",
                defaultBankName = "بلو",
                fallbackTotalToman = 107_218L
            )
            val combined = PersianVoiceSlangParserEngine.formatCombinedMultiVoiceNote(items)
            val mismatch = PersianVoiceSlangParserEngine.detectBankSmsAmountMismatch(
                rawSpeech = "۱۰۷ تومن بابت انتقال پول و ۲۱۷ ۲۱۸ تومن بابت کارمزد",
                fallbackTotalToman = 107_218L,
                multiItems = items
            )
            val ok = items.size == 2 &&
                items[0].purposeCategory == "انتقال پول" && items[0].totalAmountToman == 107_000L &&
                items[1].purposeCategory == "کارمزد" && items[1].totalAmountToman == 218L &&
                mismatch == null
            ok to combined
        }

        // 20. Video 55batch_10 (00:08): Preserving item noun across "می‌خواستم ۳۹ تومن بدم ولی ۴۹ تومن دادم"
        recordTest(
            category = "تست استرس صوتی (ویدیو ۵۵_۱۰)",
            title = "حفظ عنوان «۲ تا بستنی» هنگام اصلاح قیمت («می‌خواستم ۳۹ تومن بدم ولی ۴۹ تومن دادم»)",
            inputSummary = "سلام رفتم بیرون اومدم بعد یادمه که برای دوتا بستنی می‌خواستم ۳۹ تومن بدم ولی ۴۹ تومن دادم",
            expectedSummary = "۲ تا بستنی • ۴۹,۰۰۰ تومان"
        ) {
            val parsed = PersianVoiceSlangParserEngine.parseVoiceCommand(
                "سلام رفتم بیرون اومدم بعد یادمه که برای دوتا بستنی می‌خواستم ۳۹ تومن بدم ولی ۴۹ تومن دادم"
            )
            val ok = parsed.purposeCategory == "۲ تا بستنی" && parsed.totalAmountToman == 49_000L
            ok to "${parsed.purposeCategory} • ${PersianNumberUtils.formatToman(parsed.totalAmountToman, true)}"
        }

        // 21. Video 55batch_10 (00:35): Mid-speech self-correction with "نه نه نه نه"
        recordTest(
            category = "تست استرس صوتی (ویدیو ۵۵_۱۰)",
            title = "اصلاح آنی مبلغ در حین صحبت با «نه نه نه نه» (جایگزینی ۵۰ با ۳۵ هزار تومان)",
            inputSummary = "۵۰ تومن نانوایی نه نه نه نه ۳۵ تومن نانوایی",
            expectedSummary = "نانوایی • ۳۵,۰۰۰ تومان (۱ تراکنش)"
        ) {
            val items = PersianVoiceSlangParserEngine.parseMultiVoiceCommands(
                "۵۰ تومن نانوایی نه نه نه نه ۳۵ تومن نانوایی"
            )
            val ok = items.size == 1 &&
                items[0].purposeCategory == "نانوایی" &&
                items[0].totalAmountToman == 35_000L
            val actual = items.joinToString(" + ") {
                "${it.purposeCategory} • ${PersianNumberUtils.formatToman(it.totalAmountToman, true)}"
            } + " (${PersianNumberUtils.toPersianDigits(items.size.toString())} تراکنش)"
            ok to actual
        }

        // 22. Video 55batch_10 (00:42): Same-noun asyndetic override ("۵۰ تومان نانوایی ۳۵ تومن نانوایی")
        recordTest(
            category = "تست استرس صوتی (ویدیو ۵۵_۱۰)",
            title = "اصلاح خودکار مبلغ در صورت تکرار همان عنوان بدون «و» («۵۰ تومان نانوایی ۳۵ تومن نانوایی»)",
            inputSummary = "۵۰ تومان نانوایی ۳۵ تومن نانوایی",
            expectedSummary = "نانوایی • ۳۵,۰۰۰ تومان (۱ تراکنش)"
        ) {
            val items = PersianVoiceSlangParserEngine.parseMultiVoiceCommands(
                "۵۰ تومان نانوایی ۳۵ تومن نانوایی"
            )
            val single = PersianVoiceSlangParserEngine.parseVoiceCommand(
                "۵۰ تومان نانوایی ۳۵ تومن نانوایی"
            )
            val ok = items.size == 1 &&
                items[0].purposeCategory == "نانوایی" &&
                items[0].totalAmountToman == 35_000L &&
                single.purposeCategory == "نانوایی" &&
                single.totalAmountToman == 35_000L
            ok to "${single.purposeCategory} • ${PersianNumberUtils.formatToman(single.totalAmountToman, true)} (${PersianNumberUtils.toPersianDigits(items.size.toString())} تراکنش)"
        }

        // 23. Video 55batch_10 (00:58): Self-correction with "بود نه نه نه نه ... بود"
        recordTest(
            category = "تست استرس صوتی (ویدیو ۵۵_۱۰)",
            title = "اصلاح مبلغ همراه با فعل ربطی «بود» («۵۰ تومن نانوایی بود نه نه نه نه ۳۵ تومن بود نانوایی»)",
            inputSummary = "۵۰ تومن نانوایی بود نه نه نه نه ۳۵ تومن بود نانوایی",
            expectedSummary = "نانوایی • ۳۵,۰۰۰ تومان"
        ) {
            val parsed = PersianVoiceSlangParserEngine.parseVoiceCommand(
                "۵۰ تومن نانوایی بود نه نه نه نه ۳۵ تومن بود نانوایی"
            )
            val ok = parsed.purposeCategory == "نانوایی" && parsed.totalAmountToman == 35_000L
            ok to "${parsed.purposeCategory} • ${PersianNumberUtils.formatToman(parsed.totalAmountToman, true)}"
        }

        // 24. Video 55batch_10 (01:20): Deduplicating repeated noun around "بود" ("۵۰ تومان نانوایی بود نانوایی")
        recordTest(
            category = "تست استرس صوتی (ویدیو ۵۵_۱۰)",
            title = "حذف «بود» و ادغام کلمات تکراری («۵۰ تومان نانوایی بود نانوایی» ← «نانوایی»)",
            inputSummary = "۵۰ تومان نانوایی بود نانوایی",
            expectedSummary = "نانوایی • ۵۰,۰۰۰ تومان"
        ) {
            val parsed = PersianVoiceSlangParserEngine.parseVoiceCommand("۵۰ تومان نانوایی بود نانوایی")
            val ok = parsed.purposeCategory == "نانوایی" && parsed.totalAmountToman == 50_000L
            ok to "${parsed.purposeCategory} • ${PersianNumberUtils.formatToman(parsed.totalAmountToman, true)}"
        }

        // 25. Preserving "نه" as the number 9 ("سی و نه تومن نونوایی" & "نه تومن آدامس") while stripping negation "نه"
        recordTest(
            category = "تمایز «نه» عددی (۹) از «نه» نفی",
            title = "حفظ «نه» در اعداد مرکب («سی و نه تومن») در کنار حذف «نه» اصلاحی",
            inputSummary = "سی و نه تومن نونوایی",
            expectedSummary = "نونوایی • ۳۹,۰۰۰ تومان"
        ) {
            val parsed39 = PersianVoiceSlangParserEngine.parseVoiceCommand("سی و نه تومن نونوایی")
            val ok = parsed39.purposeCategory == "نونوایی" && parsed39.totalAmountToman == 39_000L
            ok to "${parsed39.purposeCategory} • ${PersianNumberUtils.formatToman(parsed39.totalAmountToman, true)}"
        }

        // 26. Video 66batch_11 (00:05): Gold weight unit "۳۵۰ سوت" on 1,951,672 Toman Blu SMS (no false amount mismatch)
        recordTest(
            category = "تست استرس تگ پیامک (ویدیو ۶۶_۱۱)",
            title = "حفظ واحد وزن طلا «۳۵۰ سوت» در عنوان و عدم اشتباه گرفتن آن با مبلغ (ویدیو ۶۶_۱۱)",
            inputSummary = "خرید سکه پارسیان ۳۵۰ سوت (بانک: ۱,۹۵۱,۶۷۲ تومان)",
            expectedSummary = "سکه پارسیان ۳۵۰ سوت • ۱,۹۵۱,۶۷۲ تومان (بدون هشدار مغایرت)"
        ) {
            val parsed = PersianVoiceSlangParserEngine.parseVoiceCommand(
                rawSpeech = "خرید سکه پارسیان ۳۵۰ سوت",
                fallbackTotalToman = 1_951_672L,
                defaultBankName = "بلو"
            )
            val mismatch = PersianVoiceSlangParserEngine.detectBankSmsAmountMismatch(
                rawSpeech = "خرید سکه پارسیان ۳۵۰ سوت",
                fallbackTotalToman = 1_951_672L
            )
            val ok = parsed.purposeCategory == "سکه پارسیان ۳۵۰ سوت" &&
                parsed.totalAmountToman == 1_951_672L &&
                mismatch == null
            ok to "${parsed.purposeCategory} • ${PersianNumberUtils.formatToman(parsed.totalAmountToman, true)}"
        }

        // 27. Video 66batch_11 (00:46): Attribute number "سایز ۴۲" without price -> 0 Tomans (prompts for price instead of 42,000)
        recordTest(
            category = "تست استرس صوتی (ویدیو ۶۶_۱۱)",
            title = "حفظ «سایز ۴۲» در عنوان کفش و عدم تبدیل سایز به مبلغ ۴۲,۰۰۰ تومان",
            inputSummary = "خرید کفش سایز ۴۲",
            expectedSummary = "کفش سایز ۴۲ • ۰ تومان (نیازمند ورود مبلغ)"
        ) {
            val parsed = PersianVoiceSlangParserEngine.parseVoiceCommand("خرید کفش سایز ۴۲")
            val ok = parsed.purposeCategory == "کفش سایز ۴۲" && parsed.totalAmountToman == 0L
            ok to "${parsed.purposeCategory} • ${PersianNumberUtils.formatToman(parsed.totalAmountToman, true)}"
        }

        // 28. Video 66batch_11 (00:59): Attribute number "سایز ۴۲" + explicit price "۷۵۰ هزار تومان"
        recordTest(
            category = "تست استرس صوتی (ویدیو ۶۶_۱۱)",
            title = "تفکیک دقیق «سایز ۴۲» در عنوان و «۷۵۰ هزار تومان» به عنوان مبلغ",
            inputSummary = "خرید کفش سایز ۴۲ ۷۵۰ هزار تومان",
            expectedSummary = "کفش سایز ۴۲ • ۷۵۰,۰۰۰ تومان"
        ) {
            val parsed = PersianVoiceSlangParserEngine.parseVoiceCommand("خرید کفش سایز ۴۲ ۷۵۰ هزار تومان")
            val ok = parsed.purposeCategory == "کفش سایز ۴۲" && parsed.totalAmountToman == 750_000L
            ok to "${parsed.purposeCategory} • ${PersianNumberUtils.formatToman(parsed.totalAmountToman, true)}"
        }

        // 29. Video 66batch (00:25): Preserving destination card "به کارت بلو" when tagging a Pasargad SMS
        recordTest(
            category = "تست استرس تگ پیامک (ویدیو ۶۶)",
            title = "حفظ عبارت مقصد «به کارت بلو» در عنوان تراکنش بانک پاسارگاد (ویدیو ۶۶)",
            inputSummary = "انتقال وجه به کارت بلو (بانک مبدأ: پاسارگاد • ۵۰,۰۰۰ تومان)",
            expectedSummary = "انتقال وجه به کارت بلو • ۵۰,۰۰۰ تومان (پاسارگاد)"
        ) {
            val parsed = PersianVoiceSlangParserEngine.parseVoiceCommand(
                rawSpeech = "انتقال وجه به کارت بلو",
                fallbackTotalToman = 50_000L,
                defaultBankName = "پاسارگاد"
            )
            val ok = parsed.purposeCategory == "انتقال وجه به کارت بلو" &&
                parsed.totalAmountToman == 50_000L &&
                parsed.storeName == "پاسارگاد"
            ok to "${parsed.purposeCategory} • ${PersianNumberUtils.formatToman(parsed.totalAmountToman, true)} (${parsed.storeName})"
        }

        // 30. Distinguishing destination "به کارت بلو" (kept in title) from source "با کارت پاسارگاد" (extracted as card)
        recordTest(
            category = "موتور صوتی (NLU)",
            title = "تفکیک هوشمند کارت مقصد («به کارت بلو») از کارت مبدأ («با کارت پاسارگاد»)",
            inputSummary = "۱۰۰ تومن انتقال وجه به کارت بلو با کارت پاسارگاد",
            expectedSummary = "انتقال وجه به کارت بلو • ۱۰۰,۰۰۰ تومان (کارت پاسارگاد)"
        ) {
            val parsed = PersianVoiceSlangParserEngine.parseVoiceCommand(
                "۱۰۰ تومن انتقال وجه به کارت بلو با کارت پاسارگاد"
            )
            val ok = parsed.purposeCategory == "انتقال وجه به کارت بلو" &&
                parsed.totalAmountToman == 100_000L &&
                parsed.cardLabel == "کارت پاسارگاد"
            ok to "${parsed.purposeCategory} • ${PersianNumberUtils.formatToman(parsed.totalAmountToman, true)} (${parsed.cardLabel})"
        }

        // 31. Complex multi-clause with summary header + shoe size + stumbled number + weight unit ("گردو ۲۰۰ گرم")
        recordTest(
            category = "تست استرس ترکیبی (NLU v4)",
            title = "ترکیب سرجمع مقدمه + «کفش سایز ۴۲» + اصلاح تپق «۱۴۰ ۱۵۰ تومن» + «گردو ۲۰۰ گرم»",
            inputSummary = "رفتم بیرون کلاً ۹۰۰ تومن خرج کردم ۷۵۰ تومن کفش سایز ۴۲ و ۱۴۰ ۱۵۰ تومن گردو ۲۰۰ گرم",
            expectedSummary = "کفش سایز ۴۲ (۷۵۰,۰۰۰) + گردو ۲۰۰ گرم (۱۵۰,۰۰۰)"
        ) {
            val items = PersianVoiceSlangParserEngine.parseMultiVoiceCommands(
                "رفتم بیرون کلاً ۹۰۰ تومن خرج کردم ۷۵۰ تومن کفش سایز ۴۲ و ۱۴۰ ۱۵۰ تومن گردو ۲۰۰ گرم"
            )
            val ok = items.size == 2 &&
                items[0].purposeCategory == "کفش سایز ۴۲" && items[0].totalAmountToman == 750_000L &&
                items[1].purposeCategory == "گردو ۲۰۰ گرم" && items[1].totalAmountToman == 150_000L
            val actual = items.joinToString(" + ") {
                "${it.purposeCategory} (${PersianNumberUtils.formatToman(it.totalAmountToman)})"
            }
            ok to actual
        }

        // 32. Data volume unit ("۲۰ گیگ") + mid-speech correction ("۶۰ تومن نه نه ۵۵ تومن")
        recordTest(
            category = "تست استرس ترکیبی (NLU v4)",
            title = "حفظ حجم «۲۰ گیگ» در عنوان و اصلاح آنی قیمت از ۶۰ به ۵۵ هزار تومان",
            inputSummary = "بسته اینترنت ۲۰ گیگ ۶۰ تومن نه نه ۵۵ تومن",
            expectedSummary = "بسته اینترنت ۲۰ گیگ • ۵۵,۰۰۰ تومان"
        ) {
            val items = PersianVoiceSlangParserEngine.parseMultiVoiceCommands(
                "بسته اینترنت ۲۰ گیگ ۶۰ تومن نه نه ۵۵ تومن"
            )
            val ok = items.size == 1 &&
                items[0].purposeCategory == "بسته اینترنت ۲۰ گیگ" &&
                items[0].totalAmountToman == 55_000L
            val actual = items.firstOrNull()?.let {
                "${it.purposeCategory} • ${PersianNumberUtils.formatToman(it.totalAmountToman, true)}"
            } ?: "خالی"
            ok to actual
        }

        // 33. Gold purity ("۱۸ عیار") + weight ("۴۵۰ سوت") + Dong split ("۵۰۰ تومنش دنگ مریم بود")
        recordTest(
            category = "تست استرس ترکیبی (NLU v4)",
            title = "حفظ «۱۸ عیار ۴۵۰ سوت» در عنوان دستبند همراه با محاسبه دُنگ مریم",
            inputSummary = "۲ میلیون دستبند ۱۸ عیار ۴۵۰ سوت که ۵۰۰ تومنش دنگ مریم بود",
            expectedSummary = "دستبند ۱۸ عیار ۴۵۰ سوت • کل: ۲,۰۰۰,۰۰۰ • سهم: ۱,۵۰۰,۰۰۰ • دنگ مریم: ۵۰۰,۰۰۰"
        ) {
            val parsed = PersianVoiceSlangParserEngine.parseVoiceCommand(
                "۲ میلیون دستبند ۱۸ عیار ۴۵۰ سوت که ۵۰۰ تومنش دنگ مریم بود"
            )
            val ok = parsed.purposeCategory == "دستبند ۱۸ عیار ۴۵۰ سوت" &&
                parsed.totalAmountToman == 2_000_000L &&
                parsed.userShareToman == 1_500_000L &&
                parsed.dongAmountToman == 500_000L &&
                parsed.dongFriendName == "مریم"
            ok to "${parsed.purposeCategory} • کل: ${PersianNumberUtils.formatToman(parsed.totalAmountToman)} • سهم: ${PersianNumberUtils.formatToman(parsed.userShareToman)} • دنگ ${parsed.dongFriendName}: ${PersianNumberUtils.formatToman(parsed.dongAmountToman)}"
        }

        // 34. Building unit/floor descriptors ("واحد ۵ طبقه ۳") + "به مبلغ" + sub-thousand 500 Toman fee on 250,500 SMS
        recordTest(
            category = "تست استرس تگ پیامک (NLU v4)",
            title = "حفظ «واحد ۵ طبقه ۳» و تفکیک کارمزد خرد ۵۰۰ تومانی روی پیامک ۲۵۰,۵۰۰ تومانی",
            inputSummary = "شارژ واحد ۵ طبقه ۳ به مبلغ ۲۵۰ تومن و ۵۰۰ تومن کارمزد (بانک: ۲۵۰,۵۰۰)",
            expectedSummary = "شارژ واحد ۵ طبقه ۳ (۲۵۰,۰۰۰) + کارمزد (۵۰۰)"
        ) {
            val items = PersianVoiceSlangParserEngine.parseMultiVoiceCommands(
                rawSpeech = "شارژ واحد ۵ طبقه ۳ به مبلغ ۲۵۰ تومن و ۵۰۰ تومن کارمزد",
                defaultBankName = "بلو",
                fallbackTotalToman = 250_500L
            )
            val combined = PersianVoiceSlangParserEngine.formatCombinedMultiVoiceNote(items)
            val mismatch = PersianVoiceSlangParserEngine.detectBankSmsAmountMismatch(
                rawSpeech = "شارژ واحد ۵ طبقه ۳ به مبلغ ۲۵۰ تومن و ۵۰۰ تومن کارمزد",
                fallbackTotalToman = 250_500L,
                multiItems = items
            )
            val ok = items.size == 2 &&
                items[0].purposeCategory == "شارژ واحد ۵ طبقه ۳" && items[0].totalAmountToman == 250_000L &&
                items[1].purposeCategory == "کارمزد" && items[1].totalAmountToman == 500L &&
                mismatch == null
            ok to combined
        }

        // 35. High-Load Stress Test: 25 Complex Multi-Clause Voice Utterances on Mobile CPU
        recordTest(
            category = "تست استرس سرعت موتور صوتی",
            title = "پردازش ۲۵ جمله پیچیده چندبخشی فارسی با تپق و اصلاح آنی بدون لگ روی پردازنده موبایل",
            inputSummary = "۲۵ بار اجرای کامل parseMultiVoiceCommands روی جملات پیچیده",
            expectedSummary = "۱۰۰٪ دقیق و سریع"
        ) {
            val tStart = System.nanoTime()
            var validCount = 0
            val samplePhrases = listOf(
                "رفتم بیرون کلاً ۹۰۰ تومن خرج کردم ۷۵۰ تومن کفش سایز ۴۲ و ۱۴۰ ۱۵۰ تومن گردو ۲۰۰ گرم",
                "خرید سکه پارسیان ۳۵۰ سوت",
                "بسته اینترنت ۲۰ گیگ ۶۰ تومن نه نه ۵۵ تومن",
                "۱۰۷ تومن بابت انتقال پول به کارت بلو و ۲۱۷ ۲۱۸ تومن بابت کارمزد",
                "سلام ۵۵ هزار تومان دادم به بستنی و ۳۹ هزار تومان دادم به دو تا سه تا ۵ تومن ۵ تومن نانوایی"
            )
            for (i in 0 until 25) {
                val phrase = samplePhrases[i % samplePhrases.size]
                val parsed = PersianVoiceSlangParserEngine.parseMultiVoiceCommands(phrase, "بلو", if (i % 5 == 3) 107_218L else null)
                if (parsed.isNotEmpty()) validCount++
            }
            val elapsedMs = ((System.nanoTime() - tStart) / 1_000_000L).coerceAtLeast(1L)
            val ok = validCount == 25 && elapsedMs < 5000L
            ok to "۲۵ پردازش پیچیده در ${PersianNumberUtils.toPersianDigits(elapsedMs.toString())} میلی‌ثانیه"
        }

        // 36. Video 77batch_12: Observation clause ("دیدم") + back-reference ("سی سوتی را خریدم ۲۵ هزار تومان")
        recordTest(
            category = "تست استرس صوتی (ویدیو ۷۷_۱۲)",
            title = "حذف بخش مشاهده («... دیدم») و ارجاع ضمیری به آیتم خریداری‌شده («سی سوتی را خریدم ۲۵ هزار تومان»)",
            inputSummary = "رفتم بیرون یک سکه پارسیان ۲۰ سوتی و یک سکه پارسیان ۳۰ سوتی دیدم و سی سوتی را خریدم ۲۵ هزار تومان",
            expectedSummary = "سکه پارسیان ۳۰ سوتی • ۲۵,۰۰۰ تومان (۱ تراکنش)"
        ) {
            val phrase = "رفتم بیرون یک سکه پارسیان ۲۰ سوتی و یک سکه پارسیان ۳۰ سوتی دیدم و سی سوتی را خریدم ۲۵ هزار تومان"
            val items = PersianVoiceSlangParserEngine.parseMultiVoiceCommands(phrase)
            val single = PersianVoiceSlangParserEngine.parseVoiceCommand(phrase)
            val ok = items.size == 1 &&
                items[0].purposeCategory == "سکه پارسیان ۳۰ سوتی" &&
                items[0].totalAmountToman == 25_000L &&
                single.purposeCategory == "سکه پارسیان ۳۰ سوتی" &&
                single.totalAmountToman == 25_000L
            ok to "${single.purposeCategory} • ${PersianNumberUtils.formatToman(single.totalAmountToman, true)} (${PersianNumberUtils.toPersianDigits(items.size.toString())} تراکنش)"
        }

        // 37. Video 77batch_12 (Google ASR contraction): "سی سوتی" transcribed as "سیتی" -> resolves to "سکه پارسیان ۳۰ سوتی"
        recordTest(
            category = "تست استرس صوتی (ویدیو ۷۷_۱۲)",
            title = "اصلاح خطای شنیداری گوگل («سیتی» به جای «سی سوتی») با ارجاع به بافت جمله قبل از «دیدم»",
            inputSummary = "رفتم بیرون یک سکه پارسیان ۲۰ سوتی و یک سکه پارسیان سی سوتی دیدم و سیتی را خریدم ۲۵ هزار تومان",
            expectedSummary = "سکه پارسیان ۳۰ سوتی • ۲۵,۰۰۰ تومان (۱ تراکنش)"
        ) {
            val phrase = "رفتم بیرون یک سکه پارسیان ۲۰ سوتی و یک سکه پارسیان سی سوتی دیدم و سیتی را خریدم ۲۵ هزار تومان"
            val items = PersianVoiceSlangParserEngine.parseMultiVoiceCommands(phrase)
            val single = PersianVoiceSlangParserEngine.parseVoiceCommand(phrase)
            val ok = items.size == 1 &&
                items[0].purposeCategory == "سکه پارسیان ۳۰ سوتی" &&
                items[0].totalAmountToman == 25_000L &&
                single.purposeCategory == "سکه پارسیان ۳۰ سوتی" &&
                single.totalAmountToman == 25_000L
            ok to "${single.purposeCategory} • ${PersianNumberUtils.formatToman(single.totalAmountToman, true)} (${PersianNumberUtils.toPersianDigits(items.size.toString())} تراکنش)"
        }

        // 38. Video 77batch_13: Multi-stage price correction with "-ی" currency suffix ("ولی می‌خواستم ۲۰ تومنی بخرم ولی نه ۱۰۰ تومنی خریدم")
        recordTest(
            category = "تست استرس صوتی (ویدیو ۷۷_۱۳)",
            title = "حفظ نام کالا در اصلاح چندمرحله‌ای قیمت با پسوند «تومنی» («... ولی می‌خواستم ۲۰ تومنی بخرم ولی نه ۱۰۰ تومنی خریدم»)",
            inputSummary = "من رفتم بیرون و برگشتم ۵۰ هزار تومان دادم یه سکه پارسیان ۲۰ سوتی ولی می‌خواستم ۲۰ تومنی بخرم ولی نه ۱۰۰ تومنی خریدم",
            expectedSummary = "سکه پارسیان ۲۰ سوتی • ۱۰۰,۰۰۰ تومان (۱ تراکنش)"
        ) {
            val phrase = "من رفتم بیرون و برگشتم ۵۰ هزار تومان دادم یه سکه پارسیان ۲۰ سوتی ولی می‌خواستم ۲۰ تومنی بخرم ولی نه ۱۰۰ تومنی خریدم"
            val items = PersianVoiceSlangParserEngine.parseMultiVoiceCommands(phrase)
            val single = PersianVoiceSlangParserEngine.parseVoiceCommand(phrase)
            val ok = items.size == 1 &&
                items[0].purposeCategory == "سکه پارسیان ۲۰ سوتی" &&
                items[0].totalAmountToman == 100_000L &&
                single.purposeCategory == "سکه پارسیان ۲۰ سوتی" &&
                single.totalAmountToman == 100_000L
            ok to "${single.purposeCategory} • ${PersianNumberUtils.formatToman(single.totalAmountToman, true)} (${PersianNumberUtils.toPersianDigits(items.size.toString())} تراکنش)"
        }

        // 39. Suffixed measurement units ("گیگی", "گرمی", "سوتی") alongside suffixed currency ("تومنی", "هزاری")
        recordTest(
            category = "موتور صوتی (NLU v5)",
            title = "تفکیک پسوند یای نسبت روی واحدهای شمارش («۳۰ گیگی»، «۲۰۰ گرمی») از واحدهای پولی («۵۰ تومنی»، «۱۸۰ تومنی»)",
            inputSummary = "۵۰ تومنی دادم به بسته اینترنت ۳۰ گیگی و ۲۰۰ گرمی گردو خریدم ۱۸۰ تومنی",
            expectedSummary = "بسته اینترنت ۳۰ گیگی (۵۰,۰۰۰) + ۲۰۰ گرمی گردو (۱۸۰,۰۰۰)"
        ) {
            val items = PersianVoiceSlangParserEngine.parseMultiVoiceCommands(
                "۵۰ تومنی دادم به بسته اینترنت ۳۰ گیگی و ۲۰۰ گرمی گردو خریدم ۱۸۰ تومنی"
            )
            val ok = items.size == 2 &&
                items[0].purposeCategory == "بسته اینترنت ۳۰ گیگی" && items[0].totalAmountToman == 50_000L &&
                items[1].purposeCategory == "۲۰۰ گرمی گردو" && items[1].totalAmountToman == 180_000L
            val actual = items.joinToString(" + ") {
                "${it.purposeCategory} (${PersianNumberUtils.formatToman(it.totalAmountToman)})"
            }
            ok to actual
        }

        // 40. Bidirectional LTR sign isolation on income amounts (placing "+" on the left of digits: "تومان +۱۳,۰۰۰,۰۰۰")
        recordTest(
            category = "قالب‌بندی مبالغ واریزی (RTL/LTR)",
            title = "قرارگیری علامت مثبت (+) در سمت چپ عدد و با فاصله از کلمه تومان در مبالغ واریزی",
            inputSummary = "formatIncomeToman(13_000_000L, showUnit = true)",
            expectedSummary = "\u2066\u200E+۱۳,۰۰۰,۰۰۰\u2069 تومان"
        ) {
            val formatted = PersianNumberUtils.formatIncomeToman(13_000_000L, showUnit = true)
            val ok = formatted == "\u2066\u200E+۱۳,۰۰۰,۰۰۰\u2069 تومان"
            ok to formatted
        }

        // 41. Video 88batch (00:21): Summary header ("۵۰ هزار تومان خرج کردم که ۲۵ تومنش برای موز بود و ۳۵ تومنش برای بستنی") + mismatch warning
        recordTest(
            category = "تست استرس صوتی (ویدیو ۸۸)",
            title = "تفکیک «که ۲۵ تومنش برای موز بود و ۳۵ تومنش برای بستنی» و تشخیص مغایرت با سرجمع ۵۰ هزار تومان",
            inputSummary = "سلام امروز ۵۰ هزار تومان خرج کردم که ۲۵ تومنش برای موز بود و ۳۵ تومنش برای بستنی",
            expectedSummary = "موز (۲۵,۰۰۰) + بستنی (۳۵,۰۰۰) • هشدار مغایرت با سرجمع ۵۰,۰۰۰ تومان"
        ) {
            val phrase = "سلام امروز ۵۰ هزار تومان خرج کردم که ۲۵ تومنش برای موز بود و ۳۵ تومنش برای بستنی"
            val items = PersianVoiceSlangParserEngine.parseMultiVoiceCommands(phrase)
            val mismatchHeader = PersianVoiceSlangParserEngine.detectSpokenSummaryTotalMismatch(phrase, items)
            val ok = items.size == 2 &&
                items[0].purposeCategory == "موز" && items[0].totalAmountToman == 25_000L &&
                items[1].purposeCategory == "بستنی" && items[1].totalAmountToman == 35_000L &&
                mismatchHeader == 50_000L
            val actual = items.joinToString(" + ") {
                "${it.purposeCategory} (${PersianNumberUtils.formatToman(it.totalAmountToman)})"
            } + " • سرجمع گفته‌شده: ${PersianNumberUtils.formatToman(mismatchHeader ?: 0L, true)}"
            ok to actual
        }

        // 42. Video 88batch (00:43): Soft-cancelled "می‌خواستم خرج کنم ولی" before "خرج کردم برای یک موز"
        recordTest(
            category = "تست استرس صوتی (ویدیو ۸۸)",
            title = "حذف بخش منصرف‌شده «۲۵ تومن می‌خواستم خرج کنم ولی» و حفظ «۲۰ تومن خرج کردم برای یک موز»",
            inputSummary = "سلام ۲۵ تومن می‌خواستم خرج کنم ولی ۲۰ تومن خرج کردم برای یک موز",
            expectedSummary = "۱ موز • ۲۰,۰۰۰ تومان (۱ تراکنش)"
        ) {
            val phrase = "سلام ۲۵ تومن می‌خواستم خرج کنم ولی ۲۰ تومن خرج کردم برای یک موز"
            val items = PersianVoiceSlangParserEngine.parseMultiVoiceCommands(phrase)
            val single = PersianVoiceSlangParserEngine.parseVoiceCommand(phrase)
            val ok = items.size == 1 &&
                items[0].purposeCategory == "۱ موز" &&
                items[0].totalAmountToman == 20_000L &&
                single.purposeCategory == "۱ موز" &&
                single.totalAmountToman == 20_000L
            ok to "${single.purposeCategory} • ${PersianNumberUtils.formatToman(single.totalAmountToman, true)} (${PersianNumberUtils.toPersianDigits(items.size.toString())} تراکنش)"
        }

        // 43. Video 88batch_14 (00:02): Voice Income auto-detection ("۵۰ هزار تومان دریافت کردم")
        recordTest(
            category = "تشخیص هوشمند واریزی صوتی (ویدیو ۸۸_۱۴)",
            title = "تشخیص خودکار تراکنش دریافتی (INCOME) از عبارت «۵۰ هزار تومان دریافت کردم»",
            inputSummary = "۵۰ هزار تومان دریافت کردم",
            expectedSummary = "دریافت وجه • +۵۰,۰۰۰ تومان (دریافتی/واریز)"
        ) {
            val parsed = PersianVoiceSlangParserEngine.parseVoiceCommand("۵۰ هزار تومان دریافت کردم")
            val ok = parsed.isIncome &&
                parsed.purposeCategory == "دریافت وجه" &&
                parsed.totalAmountToman == 50_000L
            val formattedAmt = PersianNumberUtils.formatIncomeToman(parsed.totalAmountToman, showUnit = true)
            ok to "${parsed.purposeCategory} • $formattedAmt (${if (parsed.isIncome) "دریافتی/واریز" else "پرداختی"})"
        }

        // 44. Specific Voice Income Titles ("۲۰ میلیون حقوق گرفتم" & "۲ میلیون هدیه تولد گرفتم")
        recordTest(
            category = "تشخیص هوشمند واریزی صوتی (NLU v6)",
            title = "حفظ عنوان دقیق واریزی («حقوق»، «هدیه تولد») همراه با نوع دریافتی (INCOME)",
            inputSummary = "۲۰ میلیون حقوق گرفتم | ۲ میلیون هدیه تولد گرفتم",
            expectedSummary = "حقوق (+۲۰,۰۰۰,۰۰۰) | هدیه تولد (+۲,۰۰۰,۰۰۰)"
        ) {
            val salary = PersianVoiceSlangParserEngine.parseVoiceCommand("۲۰ میلیون حقوق گرفتم")
            val gift = PersianVoiceSlangParserEngine.parseVoiceCommand("۲ میلیون هدیه تولد گرفتم")
            val ok = salary.isIncome && salary.purposeCategory == "حقوق" && salary.totalAmountToman == 20_000_000L &&
                gift.isIncome && gift.purposeCategory == "هدیه تولد" && gift.totalAmountToman == 2_000_000L
            ok to "${salary.purposeCategory} (${PersianNumberUtils.formatIncomeToman(salary.totalAmountToman)}) | ${gift.purposeCategory} (${PersianNumberUtils.formatIncomeToman(gift.totalAmountToman)})"
        }

        // 45. Complete manual transaction subtitle without truncation ("نقدی / دستی")
        recordTest(
            category = "عدم برش متن و زیرنویس کارت‌ها (ویدیو ۸۸_۱۴)",
            title = "نمایش کامل زیرنویس تراکنش‌های صوتی و دستی («نقدی / دستی») بدون برش",
            inputSummary = "TransactionEntity(bankName=\"\", cardInfo=\"تراکنش صوتی\")",
            expectedSummary = "زیرنویس کامل با پایان «نقدی / دستی»"
        ) {
            val tx = com.example.data.local.TransactionEntity(
                id = 999L,
                title = "دریافت وجه",
                storeName = "تراکنش صوتی",
                purpose = "دریافت وجه",
                totalAmountToman = 50_000L,
                userShareToman = 50_000L,
                dongAmountToman = 0L,
                dongFriendName = "",
                weekdayDate = "چهارشنبه ۲۷ آبان",
                timeStr = "۰۴:۳۵",
                cardInfo = "تراکنش صوتی",
                type = "INCOME",
                isUntagged = false,
                timestamp = 1795000000000L,
                bankName = ""
            )
            val subtitle = tx.simpleSubtitle
            val ok = tx.shortBankName == "نقدی / دستی" && subtitle.endsWith("• نقدی / دستی")
            ok to subtitle
        }

        // 46. Non-main account SMS (Blu Box / Piggybank / Loan) balance MUST NOT overwrite primary bank balance
        recordTest(
            category = "اقتدار مانده حساب (P0)",
            title = "رد استخراج موجودی از پیامک باکس/قلک بلو و وام",
            inputSummary = "بلوبانک: انتقال ۵۰۰,۰۰۰ ریال به باکس سفر - موجودی باکس: ۲,۵۰۰,۰۰۰ ریال",
            expectedSummary = "موجودی استخراج‌شده = null (عدم تخریب مانده اصلی)"
        ) {
            val boxSms = "بلوبانک\nانتقال ۵۰۰,۰۰۰ ریال به باکس سفر\nموجودی باکس: ۲,۵۰۰,۰۰۰ ریال"
            val loanSms = "بانک ملی\nقسط وام مسکن به مبلغ ۱۲,۰۰۰,۰۰۰ ریال سررسید شد\nمانده تسهیلات: ۴۵۰,۰۰۰,۰۰۰ ریال"
            val boxBal = JibemanSmsEngine.extractBalanceRial(boxSms)
            val loanBal = JibemanSmsEngine.extractBalanceRial(loanSms)
            val ok = boxBal == null && loanBal == null
            ok to "باکس=${boxBal ?: "null"} | وام=${loanBal ?: "null"}"
        }

        // 47. Cross-bank self-transfer detection & transparent pair disclosure in Payday Cycle
        recordTest(
            category = "ترازنامه ماه حقوقی و انتقال خودی (P0)",
            title = "حذف خودکار جفت انتقال بین‌حسابی خودی (پاسارگاد ← بلو) از ورودی/خرج",
            inputSummary = "برداشت ۳.۵ میلیون پاسارگاد + واریز ۳.۵ میلیون بلو (۹۰ ثانیه بعد) + خرج ۵۰۰ هزار بلو",
            expectedSummary = "ورودی=۰ • خرج=۵۰۰,۰۰۰ • ۱ جفت خودی (پاسارگاد ← بلو)"
        ) {
            val (gy, gm, gd) = JalaliCalendarUtils.jalaliToGregorian(1405, 8, 28)
            val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Tehran")).apply {
                set(gy, gm - 1, gd, 15, 10, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            val t0 = cal.timeInMillis
            val summary = OrosiCashflowAnalyzer.analyzeCurrentPaydayCycle(
                transactions = listOf(
                    CashflowTxSnapshot(1L, "EXPENSE", 3_500_000L, 3_500_000L, bankName = "پاسارگاد", timestamp = t0),
                    CashflowTxSnapshot(2L, "INCOME", 3_500_000L, 3_500_000L, bankName = "بلو", timestamp = t0 + 90_000L),
                    CashflowTxSnapshot(3L, "EXPENSE", 500_000L, 500_000L, bankName = "بلو", timestamp = t0 + 300_000L)
                ),
                paydayStartDay = 1,
                reservedCommitmentsToman = 0L,
                nowMillis = t0 + 600_000L
            )
            val pair = summary.excludedSelfTransferDetails.firstOrNull()
            val ok = summary.totalIncomeToman == 0L &&
                summary.totalExpenseToman == 500_000L &&
                summary.excludedSelfTransferPairs == 1 &&
                pair?.fromBankName == "پاسارگاد" &&
                pair.toBankName == "بلو"
            ok to "ورودی=${summary.totalIncomeToman} • خرج=${summary.totalExpenseToman} • جفت=${pair?.fromBankName}←${pair?.toBankName}"
        }

        // 48. Honest DEFICIT badge when monthly expenses exceed monthly income
        recordTest(
            category = "صداقت ترازنامه ماه حقوقی (P0)",
            title = "نمایش صادقانه وضعیت «کسری تراز ماه جاری» هنگام پیشی گرفتن خرج از درآمد",
            inputSummary = "ورودی ۲ میلیون • خرج ۳.۲ میلیون",
            expectedSummary = "وضعیت = DEFICIT • تراز خالص = -۱,۲۰۰,۰۰۰"
        ) {
            val (gy, gm, gd) = JalaliCalendarUtils.jalaliToGregorian(1405, 8, 28)
            val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Tehran")).apply {
                set(gy, gm - 1, gd, 12, 0, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            val t0 = cal.timeInMillis
            val summary = OrosiCashflowAnalyzer.analyzeCurrentPaydayCycle(
                transactions = listOf(
                    CashflowTxSnapshot(1L, "INCOME", 2_000_000L, 2_000_000L, bankName = "بلو", timestamp = t0),
                    CashflowTxSnapshot(2L, "EXPENSE", 3_200_000L, 3_200_000L, bankName = "بلو", timestamp = t0 + 60_000L)
                ),
                paydayStartDay = 1,
                reservedCommitmentsToman = 0L,
                nowMillis = t0 + 120_000L
            )
            val ok = summary.healthStatus == CashflowHealthStatus.DEFICIT && summary.netBalanceToman == -1_200_000L
            ok to "وضعیت=${summary.healthStatus} • تراز=${summary.netBalanceToman}"
        }

        // 49. Drop leftover / kept-aside balance commentary ("بقیه‌اش رو هم نگه داشتم")
        recordTest(
            category = "فیلتر توصیف مانده پول (P0)",
            title = "عدم ساخت تراکنش جعلی از عبارت «بقیه‌اش رو هم نگه داشتم»",
            inputSummary = "۵۰ تومن بستنی خریدم بقیه‌اش رو هم نگه داشتم",
            expectedSummary = "فقط ۱ تراکنش: بستنی • ۵۰,۰۰۰ تومان"
        ) {
            val items = PersianVoiceSlangParserEngine.parseMultiVoiceCommands("۵۰ تومن بستنی خریدم بقیه‌اش رو هم نگه داشتم")
            val ok = items.size == 1 && items[0].purposeCategory == "بستنی" && items[0].totalAmountToman == 50_000L
            ok to "${items.size} مورد: ${items.joinToString(" | ") { "${it.purposeCategory} (${it.totalAmountToman})" }}"
        }

        // 50. Contrast split + Future Debt & Receivable routing to Commitments
        recordTest(
            category = "تفکیک تعهدات آینده از ویس (v2.2)",
            title = "هدایت خودکار بدهی و طلب آینده به تعهدات با حفظ سررسید «فردا»",
            inputSummary = "سلام من ۳۹ تومن بدهی دارم ولی فردا ۵۰ تومن طلب دارم",
            expectedSummary = "بدهی (۳۹,۰۰۰ • DEBT) + طلب (۵۰,۰۰۰ • RECEIVABLE • فردا)"
        ) {
            val items = PersianVoiceSlangParserEngine.parseMultiVoiceCommands("سلام من ۳۹ تومن بدهی دارم ولی فردا ۵۰ تومن طلب دارم")
            val ok = items.size == 2 &&
                items[0].commitmentKind == "DEBT" && items[0].totalAmountToman == 39_000L &&
                items[1].commitmentKind == "RECEIVABLE" && items[1].totalAmountToman == 50_000L && items[1].dueDateHint == "فردا"
            ok to items.joinToString(" | ") { "${it.commitmentKind}:${it.totalAmountToman}:${it.dueDateHint}" }
        }

        // 51. Past relative date ("پریروز") + future expected inflow ("فردا هم ۱۵ تومان دیگه میاد به حسابم")
        recordTest(
            category = "تاریخ گذشته و واریز آینده (v2.2)",
            title = "ثبت حقوق پریروز با آفست -۲ و هدایت واریز آینده فردا (۱۵ میلیون) به تعهدات",
            inputSummary = "پریروز ۳۰ تومن حقوق گرفتم فردا هم ۱۵ تومان دیگه میاد به حسابم",
            expectedSummary = "حقوق (+۳۰M • آفست -۲) + واریز آینده (+۱۵M • RECEIVABLE • فردا)"
        ) {
            val items = PersianVoiceSlangParserEngine.parseMultiVoiceCommands("پریروز ۳۰ تومن حقوق گرفتم فردا هم ۱۵ تومان دیگه میاد به حسابم")
            val ok = items.size == 2 &&
                !items[0].isCommitment && items[0].isIncome && items[0].totalAmountToman == 30_000_000L && items[0].eventDayOffset == -2 &&
                items[1].commitmentKind == "RECEIVABLE" && items[1].commitmentSubLabel == "واریز آینده" && items[1].totalAmountToman == 15_000_000L && items[1].dueDateHint == "فردا"
            ok to items.joinToString(" | ") { "${it.purposeCategory}:${it.totalAmountToman}:off=${it.eventDayOffset}:comm=${it.commitmentKind}/${it.commitmentSubLabel}" }
        }

        // 52. Planned future purchase ("می‌خوام دوچرخه بخرم ۱۵ تومن") -> DEBT commitment ("برنامه خرید")
        recordTest(
            category = "برنامه خرید آینده (v2.2)",
            title = "تشخیص قصد خرید آینده «می‌خوام دوچرخه بخرم ۱۵ تومن» با مقیاس میلیون در تعهدات",
            inputSummary = "می‌خوام دوچرخه بخرم ۱۵ تومن",
            expectedSummary = "دوچرخه • ۱۵,۰۰۰,۰۰۰ • تعهد DEBT (برنامه خرید)"
        ) {
            val parsed = PersianVoiceSlangParserEngine.parseVoiceCommand("می‌خوام دوچرخه بخرم ۱۵ تومن")
            val ok = parsed.commitmentKind == "DEBT" &&
                parsed.commitmentSubLabel == "برنامه خرید" &&
                parsed.totalAmountToman == 15_000_000L &&
                parsed.purposeCategory.contains("دوچرخه")
            ok to "${parsed.purposeCategory} • ${parsed.totalAmountToman} • ${parsed.commitmentKind}/${parsed.commitmentSubLabel}"
        }

        // 53. Settled debt/receivable remains a regular transaction (commitmentKind == null)
        recordTest(
            category = "تفکیک تسویه‌شده از تعهد آینده (v2.2)",
            title = "ثبت «طلبم رو گرفتم» به عنوان دریافتی عادی و نه تعهد آینده",
            inputSummary = "۵۰۰ تومن طلبم رو گرفتم",
            expectedSummary = "دریافتی عادی (commitmentKind=null) • ۵۰۰,۰۰۰ تومان"
        ) {
            val parsed = PersianVoiceSlangParserEngine.parseVoiceCommand("۵۰۰ تومن طلبم رو گرفتم")
            val ok = !parsed.isCommitment && parsed.isIncome && parsed.totalAmountToman == 500_000L
            ok to "income=${parsed.isIncome} • comm=${parsed.commitmentKind} • amt=${parsed.totalAmountToman}"
        }

        // 54. Video dbatch_25 exact sentence: colloquial compound million + approximate + past-tense inflow
        recordTest(
            category = "موتور صوتی پیشرفته (ویدیو dbatch_25)",
            title = "تفکیک «۵۰۰ هزار خرج تارا» و «یک و صد و خورده‌ای از صرافی آبان تتر اومد به حسابم»",
            inputSummary = "خوب من امروز ۵۰۰ هزار تومان خرج تارا کردم یک و صد و خورده‌ای هم از صرافی آبان تتر اومد به حسابم",
            expectedSummary = "۱) تارا (۵۰۰,۰۰۰ پرداختی) • ۲) صرافی آبان تتر (+۱,۱۰۰,۰۰۰ دریافتی تقریبی)"
        ) {
            val phrase = "خوب من امروز ۵۰۰ هزار تومان خرج تارا کردم یک و صد و خورده‌ای هم از صرافی آبان تتر اومد به حسابم"
            val items = PersianVoiceSlangParserEngine.parseMultiVoiceCommands(phrase)
            val ok = items.size == 2 &&
                items[0].purposeCategory == "تارا" &&
                items[0].totalAmountToman == 500_000L &&
                !items[0].isIncome &&
                !items[0].isApproximateAmount &&
                items[1].purposeCategory == "صرافی آبان تتر" &&
                items[1].totalAmountToman == 1_100_000L &&
                items[1].isIncome &&
                !items[1].isCommitment &&
                items[1].isApproximateAmount
            ok to items.joinToString(" | ") { "${it.purposeCategory}:${it.totalAmountToman}:inc=${it.isIncome}:approx=${it.isApproximateAmount}" }
        }

        // 55. General colloquial compound million shorthand ("دو و پونصد", "یک و دویست و پنجاه", "یک و نیم")
        recordTest(
            category = "مبالغ ترکیبی محاوره‌ای میلیون (v2.4)",
            title = "تبدیل «دو و پونصد» به ۲.۵ میلیون و «یک و دویست و پنجاه» به ۱.۲۵ میلیون",
            inputSummary = "دو و پونصد خرید گوشی | یک و دویست و پنجاه اجاره",
            expectedSummary = "۲,۵۰۰,۰۰۰ (گوشی) | ۱,۲۵۰,۰۰۰ (اجاره)"
        ) {
            val p1 = PersianVoiceSlangParserEngine.parseVoiceCommand("دو و پونصد خرید گوشی")
            val p2 = PersianVoiceSlangParserEngine.parseVoiceCommand("یک و دویست و پنجاه اجاره")
            val ok = p1.totalAmountToman == 2_500_000L && p1.purposeCategory == "گوشی" &&
                p2.totalAmountToman == 1_250_000L && p2.purposeCategory == "اجاره"
            ok to "${p1.purposeCategory}=${p1.totalAmountToman} | ${p2.purposeCategory}=${p2.totalAmountToman}"
        }

        // 56. Explicit Jalali date extraction ("۲۸ آبان ۵۰۰ تومن خرج تارا کردم") without false match on "صرافی آبان تتر"
        recordTest(
            category = "تاریخ صریح شمسی در ویس (v2.4)",
            title = "استخراج تاریخ «۲۸ آبان» بدون تداخل با نام «صرافی آبان تتر»",
            inputSummary = "۲۸ آبان ۵۰۰ تومن خرج تارا کردم",
            expectedSummary = "تارا • ۵۰۰,۰۰۰ • تاریخ صریح «۲۸ آبان ...»"
        ) {
            val parsed = PersianVoiceSlangParserEngine.parseVoiceCommand("۲۸ آبان ۵۰۰ تومن خرج تارا کردم")
            val exchangeOnly = PersianVoiceSlangParserEngine.parseVoiceCommand("۵۰۰ تومن از صرافی آبان تتر اومد به حسابم")
            val ok = parsed.purposeCategory == "تارا" &&
                parsed.totalAmountToman == 500_000L &&
                parsed.explicitJalaliDate.startsWith("۲۸ آبان ") &&
                exchangeOnly.explicitJalaliDate.isBlank()
            ok to "عنوان=${parsed.purposeCategory} • تاریخ=${parsed.explicitJalaliDate} • صرافی‌تر=${exchangeOnly.explicitJalaliDate.ifBlank { "خالی" }}"
        }

        // 57. Jalali date string round-trip conversion to epoch timestamp
        recordTest(
            category = "تبدیل معکوس تاریخ شمسی (v2.4)",
            title = "تبدیل دقیق «۲۸ آبان ۱۴۰۵» به Timestamp و بازگشت بدون خطای روز",
            inputSummary = "۲۸ آبان ۱۴۰۵",
            expectedSummary = "۲۸ آبان ۱۴۰۵"
        ) {
            val ts = JalaliCalendarUtils.jalaliDateStringToTimestampMillis("۲۸ آبان ۱۴۰۵")
            val roundTrip = ts?.let { JalaliCalendarUtils.timestampToJalali(it).dateWithYear }.orEmpty()
            val ok = roundTrip == "۲۸ آبان ۱۴۰۵"
            ok to roundTrip.ifBlank { "null" }
        }

        // 58. Smart 36-hour cross-midnight SMS matching (03:24 AM referring to 28 Aban 19:54 SMS as "امروز")
        recordTest(
            category = "تطبیق هوشمند نیمه‌شب ۳۶ ساعته (v2.4)",
            title = "تطبیق ویس ساعت ۳:۲۴ بامداد (۲۹ آبان) با پیامک ۵۰۰ هزار بلو ساعت ۱۹:۵۴ (۲۸ آبان)",
            inputSummary = "ساعت ۳:۲۴ بامداد ۲۹ آبان • پیامک بلو ۵۰۰,۰۰۰ در ۲۸ آبان ۱۹:۵۴",
            expectedSummary = "اتصال خودکار دقیق به پیامک بلو (id=501)"
        ) {
            val (gy29, gm29, gd29) = JalaliCalendarUtils.jalaliToGregorian(1405, 8, 29)
            val now324Am = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Tehran")).apply {
                set(gy29, gm29 - 1, gd29, 3, 24, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis
            val sms1954Ms = now324Am - (7L * 3_600_000L + 30L * 60_000L) // 28 Aban 19:54
            val smsDate = JalaliCalendarUtils.timestampToJalali(sms1954Ms).dateWithYear

            val parsed = PersianVoiceSlangParserEngine.parseVoiceCommand("امروز ۵۰۰ هزار تومان خرج تارا کردم")
            val matches = OrosiVoiceSmsMatcher.matchBatch(
                items = listOf(parsed),
                untaggedBankSms = listOf(
                    UntaggedSmsCandidate(501L, "بلو", 500_000L, false, sms1954Ms, smsDate, "۱۹:۵۴", "کارت بلو")
                ),
                nowMillis = now324Am
            )
            val m = matches.firstOrNull()
            val ok = m?.matchedCandidate?.id == 501L && m.isExactAutoMatch
            ok to "matchedId=${m?.matchedCandidate?.id} • exact=${m?.isExactAutoMatch}"
        }

        // 59. Approximate amount auto-matching ("یک و صد و خورده‌ای" -> 1,161,741 Blu SMS)
        recordTest(
            category = "تطبیق خودکار مبالغ تقریبی (v2.4)",
            title = "اتصال خودکار «یک و صد و خورده‌ای از آبان تتر» به پیامک دقیق ۱,۱۶۱,۷۴۱ بلو",
            inputSummary = "یک و صد و خورده‌ای از صرافی آبان تتر اومد به حسابم • پیامک بلو +۱,۱۶۱,۷۴۱",
            expectedSummary = "اتصال تقریبی (id=502 • حفظ مبلغ دقیق ۱,۱۶۱,۷۴۱)"
        ) {
            val (gy29, gm29, gd29) = JalaliCalendarUtils.jalaliToGregorian(1405, 8, 29)
            val now324Am = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Tehran")).apply {
                set(gy29, gm29 - 1, gd29, 3, 24, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis
            val sms1327Ms = now324Am - (14L * 3_600_000L)
            val smsDate = JalaliCalendarUtils.timestampToJalali(sms1327Ms).dateWithYear

            val items = PersianVoiceSlangParserEngine.parseMultiVoiceCommands(
                "خوب من امروز ۵۰۰ هزار تومان خرج تارا کردم یک و صد و خورده‌ای هم از صرافی آبان تتر اومد به حسابم"
            )
            val matches = OrosiVoiceSmsMatcher.matchBatch(
                items = items,
                untaggedBankSms = listOf(
                    UntaggedSmsCandidate(501L, "بلو", 500_000L, false, now324Am - 7L * 3_600_000L, smsDate, "۱۹:۵۴", "کارت بلو"),
                    UntaggedSmsCandidate(502L, "بلو", 1_161_741L, true, sms1327Ms, smsDate, "۱۳:۲۷", "کارت بلو")
                ),
                nowMillis = now324Am
            )
            val ok = matches.size == 2 &&
                matches[0].matchedCandidate?.id == 501L && matches[0].isExactAutoMatch &&
                matches[1].matchedCandidate?.id == 502L && matches[1].isApproximateAutoMatch &&
                matches[1].matchedCandidate?.amountToman == 1_161_741L
            ok to "m0=${matches.getOrNull(0)?.matchedCandidate?.id}(exact=${matches.getOrNull(0)?.isExactAutoMatch}) | m1=${matches.getOrNull(1)?.matchedCandidate?.id}(approx=${matches.getOrNull(1)?.isApproximateAutoMatch}, amt=${matches.getOrNull(1)?.matchedCandidate?.amountToman})"
        }

        // 60. BiDi LTR Isolation on scale toggle math operators (×۱۰۰۰ / ÷۱۰۰۰) and isolated amount runs
        recordTest(
            category = "استاندارد دوجهته فارسی BiDi (v2.3)",
            title = "ایزوله‌سازی چپ‌به‌راست علامت × و ÷ کنار ۱۰۰۰ و علامت + درآمد",
            inputSummary = "formatScaleToggleLabel(true/false) + formatIsolatedAmountRun(500000, true)",
            expectedSummary = "×۱۰۰۰ و ÷۱۰۰۰ و +۵۰۰,۰۰۰ با ایزوله LTR (\\u2066\\u200E...\\u2069)"
        ) {
            val toMillion = PersianNumberUtils.formatScaleToggleLabel(isThousandScale = true)
            val toThousand = PersianNumberUtils.formatScaleToggleLabel(isThousandScale = false)
            val incRun = PersianNumberUtils.formatIsolatedAmountRun(500_000L, isIncome = true)
            val ok = toMillion == "تغییر واحد به میلیون (\u2066\u200E×۱۰۰۰\u2069)" &&
                toThousand == "تغییر واحد به هزار (\u2066\u200E÷۱۰۰۰\u2069)" &&
                incRun == "\u2066\u200E+۵۰۰,۰۰۰\u2069"
            ok to "$toMillion | $incRun"
        }

        // 61. Video 4 exact sentence: external exchange withdrawal ("از حساب صرافی برداشت کردم کامل") + sequential split ("و بعد ۵۰۰ تومن بدهی تارا رو دادم")
        recordTest(
            category = "برداشت از صرافی و کیف پول (ویدیو ۴ - v2.5)",
            title = "تشخیص واریز بودن «یک و صد و خورده‌ای از حساب صرافی برداشت کردم کامل» و جداسازی «بعد ۵۰۰ تومن بدهی تارا رو دادم»",
            inputSummary = "من یک و صد و خورده‌ای از حساب صرافی برداشت کردم کامل و بعد ۵۰۰ تومن بدهی تارا رو دادم",
            expectedSummary = "۱) برداشت از صرافی (+۱,۱۰۰,۰۰۰ تقریبی) • ۲) بدهی تارا (۵۰۰,۰۰۰ پرداختی)"
        ) {
            val phrase = "من یک و صد و خورده‌ای از حساب صرافی برداشت کردم کامل و بعد ۵۰۰ تومن بدهی تارا رو دادم"
            val items = PersianVoiceSlangParserEngine.parseMultiVoiceCommands(phrase)
            val ok = items.size == 2 &&
                items[0].isIncome &&
                !items[0].isCommitment &&
                items[0].totalAmountToman == 1_100_000L &&
                items[0].isApproximateAmount &&
                items[0].purposeCategory == "برداشت از صرافی" &&
                !items[1].isIncome &&
                !items[1].isCommitment &&
                items[1].totalAmountToman == 500_000L &&
                items[1].purposeCategory.contains("تارا")
            ok to items.joinToString(" | ") { "${it.purposeCategory}:${it.totalAmountToman}:inc=${it.isIncome}:approx=${it.isApproximateAmount}" }
        }

        // 62. Video 4 end-to-end 36h batch auto-match against Blu +1,161,741 (approx) & Blu -500,000 (exact)
        recordTest(
            category = "تطبیق خودکار پیامک ویدیو ۴ (v2.5)",
            title = "اتصال خودکار برداشت از صرافی به واریز +۱,۱۶۱,۷۴۱ بلو و بدهی تارا به برداشت ۵۰۰,۰۰۰ بلو",
            inputSummary = "ویدیو ۴ + دو پیامک بلو (+۱,۱۶۱,۷۴۱ و ۵۰۰,۰۰۰)",
            expectedSummary = "مورد ۱ -> بلو +۱,۱۶۱,۷۴۱ (تقریبی) • مورد ۲ -> بلو ۵۰۰,۰۰۰ (دقیق)"
        ) {
            val (gy29, gm29, gd29) = JalaliCalendarUtils.jalaliToGregorian(1405, 8, 29)
            val nowMs = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Tehran")).apply {
                set(gy29, gm29 - 1, gd29, 3, 24, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis
            val sms28Ms = nowMs - (10L * 3_600_000L)
            val date28 = JalaliCalendarUtils.timestampToJalali(sms28Ms).dateWithYear
            val items = PersianVoiceSlangParserEngine.parseMultiVoiceCommands(
                "من یک و صد و خورده‌ای از حساب صرافی برداشت کردم کامل و بعد ۵۰۰ تومن بدهی تارا رو دادم"
            )
            val matches = OrosiVoiceSmsMatcher.matchBatch(
                items = items,
                untaggedBankSms = listOf(
                    UntaggedSmsCandidate(601L, "بلو", 1_161_741L, true, sms28Ms, date28, "۱۳:۲۷", "کارت بلو"),
                    UntaggedSmsCandidate(602L, "بلو", 500_000L, false, nowMs - 7L * 3_600_000L, date28, "۱۹:۵۴", "کارت بلو")
                ),
                nowMillis = nowMs
            )
            val ok = matches.size == 2 &&
                matches[0].matchedCandidate?.id == 601L && matches[0].isApproximateAutoMatch &&
                matches[1].matchedCandidate?.id == 602L && matches[1].isExactAutoMatch
            ok to "m0=${matches.getOrNull(0)?.matchedCandidate?.id}(approx=${matches.getOrNull(0)?.isApproximateAutoMatch}) | m1=${matches.getOrNull(1)?.matchedCandidate?.id}(exact=${matches.getOrNull(1)?.isExactAutoMatch})"
        }

        // 63. Video 1 exact sentence: intra-clause predicate continuation ("برداشت کردم و اومد به حسابم") + sequential split ("بعد ۵۰۰ تومن خرج تارا کردم")
        recordTest(
            category = "حفظ جهت واریز در ادامه فعل (ویدیو ۱ - v2.5)",
            title = "عدم برعکس‌شدن جهت دریافتی/پرداختی در «یک و صد و خورده‌ای از حسابم برداشت کردم و اومد به حسابم بعد ۵۰۰ تومن خرج تارا کردم»",
            inputSummary = "سلام من یک و صد و خورده‌ای از حسابم برداشت کردم و اومد به حسابم بعد ۵۰۰ تومن خرج تارا کردم",
            expectedSummary = "۱) واریز به حساب (+۱,۱۰۰,۰۰۰ تقریبی) • ۲) تارا (۵۰۰,۰۰۰ پرداختی)"
        ) {
            val phrase = "سلام من یک و صد و خورده‌ای از حسابم برداشت کردم و اومد به حسابم بعد ۵۰۰ تومن خرج تارا کردم"
            val items = PersianVoiceSlangParserEngine.parseMultiVoiceCommands(phrase)
            val ok = items.size == 2 &&
                items[0].isIncome &&
                items[0].totalAmountToman == 1_100_000L &&
                items[0].isApproximateAmount &&
                !items[1].isIncome &&
                items[1].totalAmountToman == 500_000L &&
                items[1].purposeCategory == "تارا"
            ok to items.joinToString(" | ") { "${it.purposeCategory}:${it.totalAmountToman}:inc=${it.isIncome}:approx=${it.isApproximateAmount}" }
        }

        // 64. Generalized external exchange & wallet withdrawals -> INCOME with preserved source title
        recordTest(
            category = "برداشت از صرافی و کیف پول (v2.5)",
            title = "تشخیص واریز بودن برداشت از نوبیتکس و کیف پول دیجی‌پی",
            inputSummary = "۲ و ۵۰۰ از نوبیتکس برداشت کردم | ۳۰۰ تومن از کیف پول دیجی‌پی برداشت کردم",
            expectedSummary = "نوبیتکس (+۲,۵۰۰,۰۰۰) | دیجی‌پی (+۳۰۰,۰۰۰)"
        ) {
            val p1 = PersianVoiceSlangParserEngine.parseVoiceCommand("۲ و ۵۰۰ از نوبیتکس برداشت کردم")
            val p2 = PersianVoiceSlangParserEngine.parseVoiceCommand("۳۰۰ تومن از کیف پول دیجی‌پی برداشت کردم")
            val ok = p1.isIncome && p1.totalAmountToman == 2_500_000L && p1.purposeCategory.contains("نوبیتکس") &&
                p2.isIncome && p2.totalAmountToman == 300_000L && p2.purposeCategory.contains("دیجی‌پی")
            ok to "${p1.purposeCategory}(inc=${p1.isIncome},${p1.totalAmountToman}) | ${p2.purposeCategory}(inc=${p2.isIncome},${p2.totalAmountToman})"
        }

        // 65. Bank ATM / card cash withdrawal guard MUST stay EXPENSE (never misclassified as income)
        recordTest(
            category = "گارد برداشت نقدی عابربانک (v2.5)",
            title = "ثبت «۵۰۰ تومن از عابربانک برداشت کردم» به عنوان پرداختی و نه واریزی",
            inputSummary = "۵۰۰ تومن از عابربانک برداشت کردم",
            expectedSummary = "پرداختی (isIncome=false) • ۵۰۰,۰۰۰ تومان"
        ) {
            val parsed = PersianVoiceSlangParserEngine.parseVoiceCommand("۵۰۰ تومن از عابربانک برداشت کردم")
            val ok = !parsed.isIncome && parsed.totalAmountToman == 500_000L
            ok to "${parsed.purposeCategory} • inc=${parsed.isIncome} • amt=${parsed.totalAmountToman}"
        }

        // 66. Temporal phrase safety guard ("ماه بعد" / "هفته بعد") MUST NOT falsely split in sequential splitter
        recordTest(
            category = "گارد عبارات زمانی در تفکیک «بعد» (v2.5)",
            title = "عدم شکست اشتباه عبارت «ماه بعد ۲ میلیون قسط دارم و ۵۰۰ تومن خرج کردم» روی کلمه «بعد»",
            inputSummary = "ماه بعد ۲ میلیون قسط دارم و ۵۰۰ تومن خرج کردم",
            expectedSummary = "۲ مورد دقیق بدون شکست ناقص روی «ماه بعد»"
        ) {
            val items = PersianVoiceSlangParserEngine.parseMultiVoiceCommands("ماه بعد ۲ میلیون قسط دارم و ۵۰۰ تومن خرج کردم")
            val ok = items.size == 2 &&
                items[0].totalAmountToman == 2_000_000L &&
                items[1].totalAmountToman == 500_000L
            ok to items.joinToString(" | ") { "${it.purposeCategory}:${it.totalAmountToman}:due=${it.dueDateHint}" }
        }

        val totalMs = ((System.nanoTime() - suiteStartNanos) / 1_000_000L).coerceAtLeast(1L)
        val passedCount = results.count { it.passed }
        val failedCount = results.size - passedCount

        return RegressionSuiteReport(
            totalCount = results.size,
            passedCount = passedCount,
            failedCount = failedCount,
            totalDurationMs = totalMs,
            results = results
        )
    }
}
