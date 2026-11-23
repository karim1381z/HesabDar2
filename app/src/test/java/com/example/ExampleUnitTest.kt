package com.example

import com.example.domain.BankSmsParserEngine
import com.example.domain.CashflowHealthStatus
import com.example.domain.CashflowTxSnapshot
import com.example.domain.JalaliCalendarUtils
import com.example.domain.JibemanSmsEngine
import com.example.domain.OrosiCashflowAnalyzer
import com.example.domain.OrosiVoiceSmsMatcher
import com.example.domain.ParsedVoiceExpense
import com.example.domain.PersianNumberUtils
import com.example.domain.PersianVoiceSlangParserEngine
import com.example.domain.UntaggedSmsCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun testJibemanResolveBankAndRejectPromoSenders() {
        // Non-bank senders (Digikala, Tapsi, Snapp, random promo) must be rejected (null)
        assertNull(JibemanSmsEngine.resolveBank("Digikala"))
        assertNull(JibemanSmsEngine.resolveBank("TAPSI"))
        assertNull(JibemanSmsEngine.resolveBank("Snapp"))
        assertNull(JibemanSmsEngine.resolveBank("10008590"))

        // Real Iranian bank senders must resolve to their exact BankProfile
        assertEquals("صادرات ایران", JibemanSmsEngine.resolveBank("+9830009419")?.nameFa)
        assertEquals("ملت", JibemanSmsEngine.resolveBank("Bank Mellat")?.nameFa)
        assertEquals("ملی ایران", JibemanSmsEngine.resolveBank("+9830009417")?.nameFa)
        assertEquals("بلو", JibemanSmsEngine.resolveBank("+982187641")?.nameFa)
        assertEquals("سپه", JibemanSmsEngine.resolveBank("+986715001")?.nameFa)
        assertEquals("سامان", JibemanSmsEngine.resolveBank("SamanBank")?.nameFa)
        assertEquals("پاسارگاد", JibemanSmsEngine.resolveBank("B.Pasargad")?.nameFa)
        assertEquals("ویپاد", JibemanSmsEngine.resolveBank("30009017")?.nameFa)
    }

    @Test
    fun testJibemanParseAndRialToTomanAndDedupKey() {
        val sender = "+9830009419"
        val sms = """
            بانک صادرات ایران
            برداشت
            مبلغ: ۱۷,۶۷۳,۹۰۰ ریال
            مانده: ۱۴۵,۰۰۰,۰۰۰ ریال
        """.trimIndent()
        val ts = 1730734200000L

        val result = BankSmsParserEngine.parseWithJibemanEngine(
            senderAddress = sender,
            body = sms,
            timestampMillis = ts
        )
        assertNotNull(result)
        result!!
        assertEquals("صادرات ایران", result.bankName)
        assertEquals(1_767_390L, result.amountToman)
        assertEquals(14_500_000L, result.extractedBalanceToman)
        assertEquals(false, result.isIncome)
        assertEquals(JibemanSmsEngine.dedupKey(sender, ts, sms), result.dedupKey)
    }

    @Test
    fun testJibemanRejectsOtpSms() {
        val otpSms = "بانک ملت\nرمز پویا: 123456\nمبلغ: 17,673,900"
        assertNull(JibemanSmsEngine.parse(otpSms))
    }

    @Test
    fun testVoiceTaggingNeverDefaultsToSnappMarketOrDailyPurchase() {
        // 1. User says "پفک خریدم" on a 100,000 Toman Blu transaction
        val pofak = PersianVoiceSlangParserEngine.parseVoiceCommand(
            rawSpeech = "پفک خریدم",
            fallbackTotalToman = 100_000L,
            defaultBankName = "بلو"
        )
        assertEquals("پفک", pofak.purposeCategory)
        assertEquals("بلو", pofak.storeName)
        assertFalse(pofak.storeName.contains("اسنپ"))
        assertFalse(pofak.purposeCategory.contains("خرید روزمره"))

        // 2. User says "شارژ ایرانسل" on a 49,500 Toman Sepah transaction -> purpose is "شارژ ایرانسل", bank remains "سپه"
        val irancell = PersianVoiceSlangParserEngine.parseVoiceCommand(
            rawSpeech = "شارژ ایرانسل",
            fallbackTotalToman = 49_500L,
            defaultBankName = "سپه"
        )
        assertEquals("شارژ ایرانسل", irancell.purposeCategory)
        assertEquals("سپه", irancell.storeName)
        assertEquals(49_500L, irancell.totalAmountToman)

        // 3. User says "بابت هاست پرداخت کردم"
        val host = PersianVoiceSlangParserEngine.parseVoiceCommand(
            rawSpeech = "بابت هاست پرداخت کردم",
            fallbackTotalToman = 320_000L,
            defaultBankName = "پاسارگاد"
        )
        assertEquals("هاست", host.purposeCategory)
        assertEquals("پاسارگاد", host.storeName)
    }

    @Test
    fun testVoiceSlangDongSplit() {
        val phrase = "پانصد هزار تومان خرید میوه از اسنپ‌مارکت با کارت بلو که ۲۰۰ تومنش دنگ علی بود"
        val parsed = PersianVoiceSlangParserEngine.parseSpokenText(phrase)
        assertEquals(500_000L, parsed.totalAmountToman)
        assertEquals(200_000L, parsed.dongAmountToman)
        assertEquals(300_000L, parsed.userShareToman)
        assertEquals("علی", parsed.dongFriendName)
        assertEquals("اسنپ‌مارکت", parsed.storeName)
    }

    @Test
    fun testSpokenPersianNumbersAndTomanMultiplier() {
        // 1-999 followed by تومن / تومان without هزار/میلیون = thousands of Tomans (* 1,000L)
        val p1 = PersianVoiceSlangParserEngine.parseSpokenText("۵۰ تومن نانوایی")
        assertEquals(50_000L, p1.totalAmountToman)
        assertEquals("نانوایی", p1.purposeCategory)
        assertEquals("نقدی / دستی", p1.cardLabel)

        val pNoonvaei = PersianVoiceSlangParserEngine.parseSpokenText("۳۹ تومن نونوایی")
        assertEquals(39_000L, pNoonvaei.totalAmountToman)
        assertEquals("نونوایی", pNoonvaei.purposeCategory)
        assertEquals("نقدی / دستی", pNoonvaei.cardLabel)

        val pNanvaei70 = PersianVoiceSlangParserEngine.parseSpokenText("۷۰ تومن نانوایی")
        assertEquals(70_000L, pNanvaei70.totalAmountToman)
        assertEquals("نانوایی", pNanvaei70.purposeCategory)

        val pNanvaeiSpoken70 = PersianVoiceSlangParserEngine.parseSpokenText("هفتاد تومن نانوایی")
        assertEquals(70_000L, pNanvaeiSpoken70.totalAmountToman)
        assertEquals("نانوایی", pNanvaeiSpoken70.purposeCategory)

        val p2 = PersianVoiceSlangParserEngine.parseSpokenText("۳۰۰ تومن میوه")
        assertEquals(300_000L, p2.totalAmountToman)
        assertEquals("نقدی / دستی", p2.storeName)

        val p3 = PersianVoiceSlangParserEngine.parseSpokenText("پنجاه تومن نانوایی")
        assertEquals(50_000L, p3.totalAmountToman)

        val p4 = PersianVoiceSlangParserEngine.parseSpokenText("یک میلیون و دویست هزار تومان خرید گوشت")
        assertEquals(1_200_000L, p4.totalAmountToman)
    }

    @Test
    fun testExistingBankSmsVoiceTaggingNeverChangesBankAmountOrBankName() {
        // Even if the user mentions a number while tagging an existing bank SMS, its bank amount and bank name stay locked
        val tagged = PersianVoiceSlangParserEngine.parseVoiceCommand(
            rawSpeech = "۵۰ تومن پفک و تنقلات",
            fallbackTotalToman = 100_000L,
            defaultBankName = "بلو"
        )
        assertEquals(100_000L, tagged.totalAmountToman)
        assertEquals("بلو", tagged.storeName)
    }

    @Test
    fun testGregorianToJalaliConversion() {
        val (jy, jm, jd) = JalaliCalendarUtils.gregorianToJalali(2024, 11, 4)
        assertEquals(1403, jy)
        assertEquals(8, jm)
        assertEquals(14, jd)

        // 1403 is a leap year (30 Esfand = 2025-03-20)
        val (jyLeap, jmLeap, jdLeap) = JalaliCalendarUtils.gregorianToJalali(2025, 3, 20)
        assertEquals(1403, jyLeap)
        assertEquals(12, jmLeap)
        assertEquals(30, jdLeap)

        // 1405 Nowruz = 2026-03-21
        val (jy1405, jm1405, jd1405) = JalaliCalendarUtils.gregorianToJalali(2026, 3, 21)
        assertEquals(1405, jy1405)
        assertEquals(1, jm1405)
        assertEquals(1, jd1405)

        // 2026-11-14 = 23 Aban 1405
        val (jyAban, jmAban, jdAban) = JalaliCalendarUtils.gregorianToJalali(2026, 11, 14)
        assertEquals(1405, jyAban)
        assertEquals(8, jmAban)
        assertEquals(23, jdAban)
    }

    @Test
    fun testMultiVoiceCommandsAndGreetingStripper() {
        // 1. Compound sentence: "۵۰۰ هزار نانوایی و ۳۰۰ هزار شارژ ایرانسل خریدم"
        val items1 = PersianVoiceSlangParserEngine.parseMultiVoiceCommands(
            "۵۰۰ هزار نانوایی و ۳۰۰ هزار شارژ ایرانسل خریدم"
        )
        assertEquals(2, items1.size)
        assertEquals("نانوایی", items1[0].title)
        assertEquals(500_000L, items1[0].amountToman)
        assertEquals("خوراکی و روزمره", items1[0].category)

        assertEquals("شارژ ایرانسل", items1[1].title)
        assertEquals(300_000L, items1[1].amountToman)
        assertEquals("قبض و شارژ", items1[1].category)
        assertEquals("ایرانسل", items1[1].merchant)

        // 2. Compound sentence with greetings and "خرید":
        // "سلام ۵۰۰ هزار تومان خرید نانوایی و ۳۰۰ هزار تومان خرید دمپایی"
        val items2 = PersianVoiceSlangParserEngine.parseMultiVoiceCommands(
            "سلام ۵۰۰ هزار تومان خرید نانوایی و ۳۰۰ هزار تومان خرید دمپایی"
        )
        assertEquals(2, items2.size)
        assertEquals("نانوایی", items2[0].title)
        assertEquals(500_000L, items2[0].amountToman)
        assertEquals("خوراکی و روزمره", items2[0].category)

        assertEquals("دمپایی", items2[1].title)
        assertEquals(300_000L, items2[1].amountToman)
        assertEquals("پوشاک و خرید", items2[1].category)

        // 3. Tagging a single existing bank SMS row with a multi-clause sentence
        val taggedCombined = PersianVoiceSlangParserEngine.parseVoiceCommand(
            rawSpeech = "۵۰۰ هزار نانوایی و ۳۰۰ هزار شارژ ایرانسل خریدم",
            fallbackTotalToman = 800_000L,
            defaultBankName = "بلو"
        )
        assertEquals("نانوایی (۵۰۰,۰۰۰) + شارژ ایرانسل (۳۰۰,۰۰۰)", taggedCombined.purposeCategory)
        assertEquals(800_000L, taggedCombined.totalAmountToman)
        assertEquals("بلو", taggedCombined.storeName)
    }

    @Test
    fun testItemQuantitiesAndRemainderMathAndSingleTotalMultiItems() {
        // Bug A & C: "یک شارژ ایرانسل و دو تا سیب زمینی خریدم ۵۰ هزار تومان" in "+ ثبت صوتی"
        // Must NOT split into 1,000 Toman and 2,000 Toman items; must keep quantities in title and total = 50,000
        val singleTotalCommand = PersianVoiceSlangParserEngine.parseVoiceCommand(
            rawSpeech = "یک شارژ ایرانسل و دو تا سیب زمینی خریدم ۵۰ هزار تومان",
            fallbackTotalToman = null,
            defaultBankName = "نقدی / دستی"
        )
        assertEquals(50_000L, singleTotalCommand.totalAmountToman)
        assertEquals("۱ شارژ ایرانسل و ۲ تا سیب‌زمینی", singleTotalCommand.purposeCategory)

        val multiCheck = PersianVoiceSlangParserEngine.parseMultiVoiceCommands(
            rawSpeech = "یک شارژ ایرانسل و دو تا سیب زمینی خریدم ۵۰ هزار تومان"
        )
        // Because only one clause has an explicit currency amount, it is NOT a multi-amount split
        assertEquals(1, multiCheck.size)
        assertEquals(50_000L, multiCheck[0].amountToman)
        assertEquals("۱ شارژ ایرانسل و ۲ تا سیب‌زمینی", multiCheck[0].title)

        // Bug B: Tagging an existing 100,000 Toman bank transaction via "بگو بابت چی؟"
        // User says: "سلام دو تا شارژ خریدم دو تا بستنی ۵۰ هزار تومان"
        // Part 2 ("۲ تا بستنی") = 50,000; Part 1 ("۲ تا شارژ") = 100,000 - 50,000 = 50,000!
        val remainderTagged = PersianVoiceSlangParserEngine.parseVoiceCommand(
            rawSpeech = "سلام دو تا شارژ خریدم دو تا بستنی ۵۰ هزار تومان",
            fallbackTotalToman = 100_000L,
            defaultBankName = "بلو"
        )
        assertEquals("۲ تا شارژ (۵۰,۰۰۰) + ۲ تا بستنی (۵۰,۰۰۰)", remainderTagged.purposeCategory)
        assertEquals(100_000L, remainderTagged.totalAmountToman)
        assertEquals("بلو", remainderTagged.storeName)
    }

    @Test
    fun testLiveAmountCommaFormattingAndParsing() {
        assertEquals("۱۰۰,۰۰۰", PersianNumberUtils.formatLiveAmountInput("100000"))
        assertEquals("۱۰,۰۰۰", PersianNumberUtils.formatLiveAmountInput("۱۰,۰۰۰"))
        assertEquals(10_000L, PersianNumberUtils.parseAmountInputToLong("۱۰,۰۰۰"))
        assertEquals(100_000L, PersianNumberUtils.parseAmountInputToLong("۱۰۰,۰۰۰ تومان"))
    }

    @Test
    fun testNaturalSpeechPivotRuleAAndPreserveBabatRuleB() {
        // Rule A: Narrative intro ("رفتم بیرون و اومدم کلاً ۵۰ هزار تومان خرج کردم برای تاکسی و کرایه و بستنی و")
        // -> discard narrative intro before "برای", keep 50,000 Tomans, strip trailing " و" -> "تاکسی و کرایه و بستنی"
        val ruleA = PersianVoiceSlangParserEngine.parseVoiceCommand(
            rawSpeech = "رفتم بیرون و اومدم کلاً ۵۰ هزار تومان خرج کردم برای تاکسی و کرایه و بستنی و",
            fallbackTotalToman = null,
            defaultBankName = "نقدی / دستی"
        )
        assertEquals(50_000L, ruleA.totalAmountToman)
        assertEquals("تاکسی و کرایه و بستنی", ruleA.purposeCategory)

        val ruleAMulti = PersianVoiceSlangParserEngine.parseMultiVoiceCommands(
            rawSpeech = "رفتم بیرون و اومدم کلاً ۵۰ هزار تومان خرج کردم برای تاکسی و کرایه و بستنی و"
        )
        assertEquals(1, ruleAMulti.size)
        assertEquals(50_000L, ruleAMulti[0].amountToman)
        assertEquals("تاکسی و کرایه و بستنی", ruleAMulti[0].title)

        // Rule B: Preserve "بابت" / "برای" when connecting two nouns ("انتقال پول به احمد بابت عسل")
        val ruleB = PersianVoiceSlangParserEngine.parseVoiceCommand(
            rawSpeech = "انتقال پول به احمد بابت عسل",
            fallbackTotalToman = 200_000L,
            defaultBankName = "بلو"
        )
        assertEquals("انتقال پول به احمد بابت عسل", ruleB.purposeCategory)
        assertEquals(200_000L, ruleB.totalAmountToman)
        assertEquals("بلو", ruleB.storeName)
    }

    @Test
    fun testStammeredMentalMathSpeechAndPrepositionStripping() {
        // Real-device test case from video_2 (00:48):
        // "سلام ۵۵ هزار تومان دادم به بستنی و ۳۹ هزار تومان دادم به دو تا سه تا ۵ تومن ۵ تومن نانوایی"
        val items = PersianVoiceSlangParserEngine.parseMultiVoiceCommands(
            "سلام ۵۵ هزار تومان دادم به بستنی و ۳۹ هزار تومان دادم به دو تا سه تا ۵ تومن ۵ تومن نانوایی"
        )
        assertEquals(2, items.size)
        assertEquals("بستنی", items[0].title)
        assertEquals(55_000L, items[0].amountToman)
        assertEquals("نانوایی", items[1].title)
        assertEquals(39_000L, items[1].amountToman)
    }

    @Test
    fun testAsyndeticClauseSplittingAndNegationCancellationFilter() {
        // Test case from user's video:
        // "سلام ۳۵ تومن دادم نونوایی ۵۰ تومن دادم بقالی ۵۵ تومن میخواستم بدم نونوایی ولی ندادم"
        val items = PersianVoiceSlangParserEngine.parseMultiVoiceCommands(
            "سلام ۳۵ تومن دادم نونوایی ۵۰ تومن دادم بقالی ۵۵ تومن میخواستم بدم نونوایی ولی ندادم"
        )
        assertEquals(2, items.size)
        assertEquals("نونوایی", items[0].title)
        assertEquals(35_000L, items[0].amountToman)
        assertEquals("بقالی", items[1].title)
        assertEquals(50_000L, items[1].amountToman)
    }

    @Test
    fun testRejectBodyTextPromoFalsePositiveBanks() {
        // Promo SMS mentioning "گردونه ملی گلد" from a non-bank sender must NOT be guessed as "ملی ایران"
        assertEquals("بانک", BankSmsParserEngine.detectIranianBankName("گردونه ملی گلد ۱۷,۴۳۰ ریال", "10008590"))
    }

    @Test
    fun testVideoBatch8AndBatch9VoiceScenariosAndIndefiniteRecipient() {
        // Video batch_9: "دخترم ۵۰ تومان خرج کرد"
        val daughter = PersianVoiceSlangParserEngine.parseVoiceCommand("دخترم ۵۰ تومان خرج کرد")
        assertEquals("دخترم خرج کرد", daughter.purposeCategory)
        assertEquals(50_000L, daughter.totalAmountToman)

        // Video batch_9: "۳۵ تومان هزینه قسط پنجم وام دیشب"
        val loan5th = PersianVoiceSlangParserEngine.parseVoiceCommand("۳۵ تومان هزینه قسط پنجم وام دیشب")
        assertEquals("هزینه قسط پنجم وام", loan5th.purposeCategory)
        assertEquals(35_000L, loan5th.totalAmountToman)

        // Video batch_8: "رفته بودم بیرون ۵۰ تومن میخواستم خرج کنم ولی نکردم ۵۰ تومن دادم بستنی ۳۵ تومن دادم به یه فقیر"
        val batch8Items = PersianVoiceSlangParserEngine.parseMultiVoiceCommands(
            "رفته بودم بیرون ۵۰ تومن میخواستم خرج کنم ولی نکردم ۵۰ تومن دادم بستنی ۳۵ تومن دادم به یه فقیر"
        )
        assertEquals(2, batch8Items.size)
        assertEquals("بستنی", batch8Items[0].purposeCategory)
        assertEquals(50_000L, batch8Items[0].totalAmountToman)
        assertEquals("فقیر", batch8Items[1].purposeCategory)
        assertEquals(35_000L, batch8Items[1].totalAmountToman)

        // Video batch_8: "بدهی عسل به احمد عمو" on 9,200,000 Toman Blu transaction
        val honeyDebt = PersianVoiceSlangParserEngine.parseVoiceCommand(
            rawSpeech = "بدهی عسل به احمد عمو",
            fallbackTotalToman = 9_200_000L,
            defaultBankName = "بلو"
        )
        assertEquals("بدهی عسل به احمد عمو", honeyDebt.purposeCategory)
        assertEquals(9_200_000L, honeyDebt.totalAmountToman)
        assertEquals("بلو", honeyDebt.storeName)
    }

    @Test
    fun testAll38IranianBankLogosIncludingIranVenezuelaAndLtrSenderFormatting() {
        val all38 = com.example.ui.components.JibemanAssetLogoCache.ALL_38_IRANIAN_BANKS
        assertEquals(38, all38.size)
        for ((faName, expectedKey) in all38) {
            val resolved = com.example.ui.components.JibemanAssetLogoCache.resolveBankLogoKey(faName)
            assertEquals("Logo key mismatch for $faName", expectedKey, resolved)
        }
        assertEquals(
            "ic_bank_iran_venezuela",
            com.example.ui.components.JibemanAssetLogoCache.resolveBankLogoKey("ایران ونزوئلا")
        )

        val ltrSender = PersianNumberUtils.formatLtrSender("+989999987641")
        assertEquals("\u2066\u200E+989999987641\u2069", ltrSender)
    }

    @Test
    fun testVideo55BatchAndBatch10StressScenarios() {
        // 1. Video 55batch (00:05): Summary intro total before itemized breakdown
        val summaryBreakdown = PersianVoiceSlangParserEngine.parseMultiVoiceCommands(
            "رفتم بیرون و اومدم کلاً ۵۰ هزار تومان خرج کردم ۲۵ تومن بستنی ۲۵ تومن دمپایی"
        )
        assertEquals(2, summaryBreakdown.size)
        assertEquals("بستنی", summaryBreakdown[0].purposeCategory)
        assertEquals(25_000L, summaryBreakdown[0].totalAmountToman)
        assertEquals("دمپایی", summaryBreakdown[1].purposeCategory)
        assertEquals(25_000L, summaryBreakdown[1].totalAmountToman)

        // 2. Video 55batch (00:41): Suffix "تومانش" + sub-thousand 500 Toman remainder on 49,500 Toman SMS
        val subThousand49500 = PersianVoiceSlangParserEngine.parseVoiceCommand(
            rawSpeech = "۴۹ تومانش بابت بستنی ۵۰۰ تومانش بابت کاغذ",
            fallbackTotalToman = 49_500L,
            defaultBankName = "بلو"
        )
        assertEquals("بستنی (۴۹,۰۰۰) + کاغذ (۵۰۰)", subThousand49500.purposeCategory)
        assertEquals(49_500L, subThousand49500.totalAmountToman)

        // 3. Video 55batch (00:58): Conflicting spoken amount on bank SMS ("۸۵ تومن بابت بستنی" on 80,000 Toman SMS)
        assertEquals(
            85_000L,
            PersianVoiceSlangParserEngine.detectBankSmsAmountMismatch(
                rawSpeech = "۸۵ تومن بابت بستنی",
                fallbackTotalToman = 80_000L
            )
        )
        assertNull(
            PersianVoiceSlangParserEngine.detectBankSmsAmountMismatch(
                rawSpeech = "بستنی",
                fallbackTotalToman = 80_000L
            )
        )

        // 4. Video 55batch (01:13): Stumbled number correction ("۲۱۷ ۲۱۸ تومن") + 218 Toman fee on 107,218 Toman SMS
        val stumbledFee = PersianVoiceSlangParserEngine.parseVoiceCommand(
            rawSpeech = "۱۰۷ تومن بابت انتقال پول و ۲۱۷ ۲۱۸ تومن بابت کارمزد",
            fallbackTotalToman = 107_218L,
            defaultBankName = "بلو"
        )
        assertEquals("انتقال پول (۱۰۷,۰۰۰) + کارمزد (۲۱۸)", stumbledFee.purposeCategory)
        assertEquals(107_218L, stumbledFee.totalAmountToman)

        // 5. Video 55batch_10 (00:08): Preserving item noun across "می‌خواستم ۳۹ تومن بدم ولی ۴۹ تومن دادم"
        val carriedIceCream = PersianVoiceSlangParserEngine.parseVoiceCommand(
            "سلام رفتم بیرون اومدم بعد یادمه که برای دوتا بستنی می‌خواستم ۳۹ تومن بدم ولی ۴۹ تومن دادم"
        )
        assertEquals("۲ تا بستنی", carriedIceCream.purposeCategory)
        assertEquals(49_000L, carriedIceCream.totalAmountToman)

        // 6. Video 55batch_10 (00:35, 00:42, 00:58, 01:20): Self-corrections and "بود" deduplication
        val nahCorrected = PersianVoiceSlangParserEngine.parseMultiVoiceCommands(
            "۵۰ تومن نانوایی نه نه نه نه ۳۵ تومن نانوایی"
        )
        assertEquals(1, nahCorrected.size)
        assertEquals("نانوایی", nahCorrected[0].purposeCategory)
        assertEquals(35_000L, nahCorrected[0].totalAmountToman)

        val sameNounCorrected = PersianVoiceSlangParserEngine.parseMultiVoiceCommands(
            "۵۰ تومان نانوایی ۳۵ تومن نانوایی"
        )
        assertEquals(1, sameNounCorrected.size)
        assertEquals("نانوایی", sameNounCorrected[0].purposeCategory)
        assertEquals(35_000L, sameNounCorrected[0].totalAmountToman)

        val boudCorrected = PersianVoiceSlangParserEngine.parseVoiceCommand(
            "۵۰ تومن نانوایی بود نه نه نه نه ۳۵ تومن بود نانوایی"
        )
        assertEquals("نانوایی", boudCorrected.purposeCategory)
        assertEquals(35_000L, boudCorrected.totalAmountToman)

        val boudDeduped = PersianVoiceSlangParserEngine.parseVoiceCommand(
            "۵۰ تومان نانوایی بود نانوایی"
        )
        assertEquals("نانوایی", boudDeduped.purposeCategory)
        assertEquals(50_000L, boudDeduped.totalAmountToman)
    }

    @Test
    fun testVideo66BatchAndBatch11StressScenarios() {
        // 1. Video 66batch_11 (00:05): "خرید سکه پارسیان ۳۵۰ سوت" on 1,951,672 Toman Blu SMS
        val goldCoin = PersianVoiceSlangParserEngine.parseVoiceCommand(
            rawSpeech = "خرید سکه پارسیان ۳۵۰ سوت",
            fallbackTotalToman = 1_951_672L,
            defaultBankName = "بلو"
        )
        assertEquals("سکه پارسیان ۳۵۰ سوت", goldCoin.purposeCategory)
        assertEquals(1_951_672L, goldCoin.totalAmountToman)
        assertNull(
            PersianVoiceSlangParserEngine.detectBankSmsAmountMismatch(
                rawSpeech = "خرید سکه پارسیان ۳۵۰ سوت",
                fallbackTotalToman = 1_951_672L
            )
        )

        // 2. Video 66batch_11 (00:46): "خرید کفش سایز ۴۲" without price -> 0 Tomans (does not mistake 42 for 42,000 Tomans)
        val shoeNoPrice = PersianVoiceSlangParserEngine.parseVoiceCommand("خرید کفش سایز ۴۲")
        assertEquals("کفش سایز ۴۲", shoeNoPrice.purposeCategory)
        assertEquals(0L, shoeNoPrice.totalAmountToman)

        // 3. Video 66batch_11 (00:59): "خرید کفش سایز ۴۲ ۷۵۰ هزار تومان" -> title "کفش سایز ۴۲", amount 750,000 Tomans
        val shoeWithPrice = PersianVoiceSlangParserEngine.parseVoiceCommand("خرید کفش سایز ۴۲ ۷۵۰ هزار تومان")
        assertEquals("کفش سایز ۴۲", shoeWithPrice.purposeCategory)
        assertEquals(750_000L, shoeWithPrice.totalAmountToman)

        // 4. Video 66batch (00:25): "انتقال وجه به کارت بلو" on 50,000 Toman Pasargad SMS
        val toBluCard = PersianVoiceSlangParserEngine.parseVoiceCommand(
            rawSpeech = "انتقال وجه به کارت بلو",
            fallbackTotalToman = 50_000L,
            defaultBankName = "پاسارگاد"
        )
        assertEquals("انتقال وجه به کارت بلو", toBluCard.purposeCategory)
        assertEquals(50_000L, toBluCard.totalAmountToman)
        assertEquals("پاسارگاد", toBluCard.storeName)

        // 5. Source vs Destination Card: "۱۰۰ تومن انتقال وجه به کارت بلو با کارت پاسارگاد"
        val destAndSourceCard = PersianVoiceSlangParserEngine.parseVoiceCommand(
            "۱۰۰ تومن انتقال وجه به کارت بلو با کارت پاسارگاد"
        )
        assertEquals("انتقال وجه به کارت بلو", destAndSourceCard.purposeCategory)
        assertEquals(100_000L, destAndSourceCard.totalAmountToman)
        assertEquals("کارت پاسارگاد", destAndSourceCard.cardLabel)
    }

    @Test
    fun testVideo77Batch12And13AndIncomePlusFormatting() {
        // 1. Video 77batch_12: Observation clause ("دیدم") + back-reference ("سی سوتی را خریدم ۲۵ هزار تومان")
        val phrase12 = "رفتم بیرون یک سکه پارسیان ۲۰ سوتی و یک سکه پارسیان ۳۰ سوتی دیدم و سی سوتی را خریدم ۲۵ هزار تومان"
        val items12 = PersianVoiceSlangParserEngine.parseMultiVoiceCommands(phrase12)
        assertEquals(1, items12.size)
        assertEquals("سکه پارسیان ۳۰ سوتی", items12[0].purposeCategory)
        assertEquals(25_000L, items12[0].totalAmountToman)

        // 2. Video 77batch_12 (Google ASR contraction: "سیتی را خریدم ۲۵ هزار تومان")
        val phrase12Asr = "رفتم بیرون یک سکه پارسیان ۲۰ سوتی و یک سکه پارسیان سی سوتی دیدم و سیتی را خریدم ۲۵ هزار تومان"
        val items12Asr = PersianVoiceSlangParserEngine.parseMultiVoiceCommands(phrase12Asr)
        assertEquals(1, items12Asr.size)
        assertEquals("سکه پارسیان ۳۰ سوتی", items12Asr[0].purposeCategory)
        assertEquals(25_000L, items12Asr[0].totalAmountToman)

        // 3. Video 77batch_13: Multi-stage price correction with "-ی" currency suffix ("۲۰ تومنی ... ولی نه ۱۰۰ تومنی خریدم")
        val phrase13 = "من رفتم بیرون و برگشتم ۵۰ هزار تومان دادم یه سکه پارسیان ۲۰ سوتی ولی می‌خواستم ۲۰ تومنی بخرم ولی نه ۱۰۰ تومنی خریدم"
        val items13 = PersianVoiceSlangParserEngine.parseMultiVoiceCommands(phrase13)
        assertEquals(1, items13.size)
        assertEquals("سکه پارسیان ۲۰ سوتی", items13[0].purposeCategory)
        assertEquals(100_000L, items13[0].totalAmountToman)

        val single13 = PersianVoiceSlangParserEngine.parseVoiceCommand(phrase13)
        assertEquals("سکه پارسیان ۲۰ سوتی", single13.purposeCategory)
        assertEquals(100_000L, single13.totalAmountToman)

        // 4. Income "+" sign placed on the left of the number, separated from "تومان"
        assertEquals(
            "\u2066\u200E+۱۳,۰۰۰,۰۰۰\u2069 تومان",
            PersianNumberUtils.formatIncomeToman(13_000_000L, showUnit = true)
        )
    }

    @Test
    fun testVideo88BatchAndBatch14VoiceAndIncomeScenarios() {
        // 1. Video 88batch (00:21): "سلام امروز ۵۰ هزار تومان خرج کردم که ۲۵ تومنش برای موز بود و ۳۵ تومنش برای بستنی"
        val phraseSummaryMismatch = "سلام امروز ۵۰ هزار تومان خرج کردم که ۲۵ تومنش برای موز بود و ۳۵ تومنش برای بستنی"
        val itemsMismatch = PersianVoiceSlangParserEngine.parseMultiVoiceCommands(phraseSummaryMismatch)
        assertEquals(2, itemsMismatch.size)
        assertEquals("موز", itemsMismatch[0].purposeCategory)
        assertEquals(25_000L, itemsMismatch[0].totalAmountToman)
        assertEquals("بستنی", itemsMismatch[1].purposeCategory)
        assertEquals(35_000L, itemsMismatch[1].totalAmountToman)
        assertEquals(
            50_000L,
            PersianVoiceSlangParserEngine.detectSpokenSummaryTotalMismatch(phraseSummaryMismatch, itemsMismatch)
        )

        // 2. Video 88batch (00:43): "سلام ۲۵ تومن می‌خواستم خرج کنم ولی ۲۰ تومن خرج کردم برای یک موز"
        val phraseWantBut = "سلام ۲۵ تومن می‌خواستم خرج کنم ولی ۲۰ تومن خرج کردم برای یک موز"
        val itemsWantBut = PersianVoiceSlangParserEngine.parseMultiVoiceCommands(phraseWantBut)
        assertEquals(1, itemsWantBut.size)
        assertEquals("۱ موز", itemsWantBut[0].purposeCategory)
        assertEquals(20_000L, itemsWantBut[0].totalAmountToman)

        // 3. Video 88batch_14 (00:02): "۵۰ هزار تومان دریافت کردم" -> Income (isIncome = true)
        val received50 = PersianVoiceSlangParserEngine.parseVoiceCommand("۵۰ هزار تومان دریافت کردم")
        assertEquals(true, received50.isIncome)
        assertEquals("دریافت وجه", received50.purposeCategory)
        assertEquals(50_000L, received50.totalAmountToman)

        // 4. Video Screenrecorder-2026-11-18 (00:24): Mixed Income + Expense compound sentence
        // "حقوق من ۵۰ هزار تومان بود و ۲۵ هزار تومانش رو خرج کردم"
        val mixedSentence = "حقوق من ۵۰ هزار تومان بود و ۲۵ هزار تومانش رو خرج کردم"
        val mixedItems = PersianVoiceSlangParserEngine.parseMultiVoiceCommands(mixedSentence)
        assertEquals(2, mixedItems.size)
        assertEquals("حقوق", mixedItems[0].purposeCategory)
        assertEquals(50_000L, mixedItems[0].totalAmountToman)
        assertEquals(true, mixedItems[0].isIncome)
        assertEquals("خرج از حقوق", mixedItems[1].purposeCategory)
        assertEquals(25_000L, mixedItems[1].totalAmountToman)
        assertEquals(false, mixedItems[1].isIncome)
        assertNull(
            PersianVoiceSlangParserEngine.detectSpokenSummaryTotalMismatch(mixedSentence, mixedItems)
        )
    }

    @Test
    fun testVideo99BatchVoiceNluBrandNumbersDativeCompoundsAndSales() {
        // 1. Video 99batch_16: "۵۵ هزار تومان به سایت ۵۰۴۰ دادم" -> title "سایت ۵۰۴۰", amount 55,000, expense
        val site5040 = PersianVoiceSlangParserEngine.parseVoiceCommand("۵۵ هزار تومان به سایت ۵۰۴۰ دادم")
        assertEquals("سایت ۵۰۴۰", site5040.purposeCategory)
        assertEquals(55_000L, site5040.totalAmountToman)
        assertEquals(false, site5040.isIncome)

        // 2. Explicit currency amount (5,000) beats bare identifier number (5040)
        val site5040Small = PersianVoiceSlangParserEngine.parseVoiceCommand("۵ هزار تومان به سایت ۵۰۴۰ دادم")
        assertEquals("سایت ۵۰۴۰", site5040Small.purposeCategory)
        assertEquals(5_000L, site5040Small.totalAmountToman)
        assertEquals(false, site5040Small.isIncome)

        // 3. Video 99batch_17: Mixed 3-item utterance with dative compound "به فقیر کمک کردم" -> "کمک به فقیر"
        val mixed3 = PersianVoiceSlangParserEngine.parseMultiVoiceCommands(
            "سلام مبلغ ۵۰ هزار تومان برای بستنی خرج کردم ۳۵ هزار تومان طلب دریافت کردم و ۲۵ هزار تومان به فقیر کمک کردم"
        )
        assertEquals(3, mixed3.size)
        assertEquals("بستنی", mixed3[0].purposeCategory)
        assertEquals(50_000L, mixed3[0].totalAmountToman)
        assertEquals(false, mixed3[0].isIncome)

        assertEquals("طلب", mixed3[1].purposeCategory)
        assertEquals(35_000L, mixed3[1].totalAmountToman)
        assertEquals(true, mixed3[1].isIncome)

        assertEquals("کمک به فقیر", mixed3[2].purposeCategory)
        assertEquals(25_000L, mixed3[2].totalAmountToman)
        assertEquals(false, mixed3[2].isIncome)

        // 4. Video 99batch_17: Negated clause + replacement "به جاش" + 1st-person sale "۳۵ هزار گوجه فروختم"
        val negatedAndSale = PersianVoiceSlangParserEngine.parseMultiVoiceCommands(
            "۵۰ هزار تومان می‌خواستم خرج کنم ولی نکردم به جاش ۴۵ هزار تومان طلب دریافت کردم ۳۵ هزار گوجه فروختم"
        )
        assertEquals(2, negatedAndSale.size)
        assertEquals("طلب", negatedAndSale[0].purposeCategory)
        assertEquals(45_000L, negatedAndSale[0].totalAmountToman)
        assertEquals(true, negatedAndSale[0].isIncome)

        assertEquals("فروش گوجه", negatedAndSale[1].purposeCategory)
        assertEquals(35_000L, negatedAndSale[1].totalAmountToman)
        assertEquals(true, negatedAndSale[1].isIncome)

        // 5. Generalization of dative light-verb normalization ("هدیه به علی")
        val giftToAli = PersianVoiceSlangParserEngine.parseVoiceCommand("۱۰۰ تومن به علی هدیه دادم")
        assertEquals("هدیه به علی", giftToAli.purposeCategory)
        assertEquals(100_000L, giftToAli.totalAmountToman)
        assertEquals(false, giftToAli.isIncome)
    }

    @Test
    fun testJalaliToGregorianMonthRangeAndSepahBalanceExtraction() {
        // 1. Jalali -> Gregorian round-trip including leap year 1403 and Aban 1405
        val (gy1, gm1, gd1) = JalaliCalendarUtils.jalaliToGregorian(1403, 12, 30)
        assertEquals(Triple(2025, 3, 20), Triple(gy1, gm1, gd1))

        val (gy2, gm2, gd2) = JalaliCalendarUtils.jalaliToGregorian(1405, 8, 23)
        assertEquals(Triple(2026, 11, 14), Triple(gy2, gm2, gd2))

        // Month epoch range covers exact start/end of Aban 1405
        val (startMs, endMs) = JalaliCalendarUtils.jalaliMonthEpochRangeMillis(1405, 8)
        val startJ = JalaliCalendarUtils.fromTimestamp(startMs + 60_000L)
        val endPrevJ = JalaliCalendarUtils.fromTimestamp(endMs - 60_000L)
        val endNextJ = JalaliCalendarUtils.fromTimestamp(endMs + 60_000L)
        assertEquals(140508, startJ.yearMonthKey)
        assertEquals(140508, endPrevJ.yearMonthKey)
        assertEquals(140509, endNextJ.yearMonthKey)
        assertEquals(140412, JalaliCalendarUtils.shiftJalaliYearMonth(140501, -1))
        assertEquals(140501, JalaliCalendarUtils.shiftJalaliYearMonth(140412, 1))

        // 2. Sepah extended senders & balance-only SMS extraction
        assertEquals("سپه", JibemanSmsEngine.resolveBank("BankSepah")?.nameFa)
        assertEquals("سپه", JibemanSmsEngine.resolveBank("+98200015")?.nameFa)
        assertEquals("سپه", JibemanSmsEngine.resolveBank("+9830002015")?.nameFa)

        val balanceOnlySms = "بانک سپه\nموجودی قابل برداشت: ۹,۵۰۰,۰۰۰ ریال"
        assertEquals(9_500_000L, JibemanSmsEngine.extractBalanceRial(balanceOnlySms))
    }

    @Test
    fun testVideo10BatchVoiceNluRemainingBalanceAndColloquialMillionScale() {
        // 1. Video 10batch_18: Remaining balance commentary ("۵ میلیون تومان هنوز باقی مانده") MUST be dropped!
        val salaryAndInstallmentsWithRemaining = PersianVoiceSlangParserEngine.parseMultiVoiceCommands(
            "سلام من امروز حقوقم رو ریختن ۳۰ میلیون تومان و ۲۵ میلیون تومان خرج قسط‌ها شد و ۵ میلیون تومان هنوز باقی مانده"
        )
        assertEquals(2, salaryAndInstallmentsWithRemaining.size)
        assertEquals("حقوق", salaryAndInstallmentsWithRemaining[0].purposeCategory)
        assertEquals(30_000_000L, salaryAndInstallmentsWithRemaining[0].totalAmountToman)
        assertEquals(true, salaryAndInstallmentsWithRemaining[0].isIncome)

        assertEquals("قسط‌ها", salaryAndInstallmentsWithRemaining[1].purposeCategory)
        assertEquals(25_000_000L, salaryAndInstallmentsWithRemaining[1].totalAmountToman)
        assertEquals(false, salaryAndInstallmentsWithRemaining[1].isIncome)

        // 2. Video 10batch_19: Colloquial Iranian "تومن" scale disambiguation + clean titles
        val colloquialMixed = PersianVoiceSlangParserEngine.parseMultiVoiceCommands(
            "من ۳۰ تومن حقوق برام ریختن ۲۵ تومنش خرج بستنیش شد و ۲۵ تومنش خرج قسط‌های بزرگی که داشتم"
        )
        assertEquals(3, colloquialMixed.size)
        assertEquals("حقوق", colloquialMixed[0].purposeCategory)
        assertEquals(30_000_000L, colloquialMixed[0].totalAmountToman)
        assertEquals(true, colloquialMixed[0].isIncome)

        assertEquals("بستنی", colloquialMixed[1].purposeCategory)
        assertEquals(25_000L, colloquialMixed[1].totalAmountToman)
        assertEquals(false, colloquialMixed[1].isIncome)

        assertEquals("قسط‌های بزرگ", colloquialMixed[2].purposeCategory)
        assertEquals(25_000_000L, colloquialMixed[2].totalAmountToman)
        assertEquals(false, colloquialMixed[2].isIncome)

        // 3. Generalized remaining-balance commentary variants
        assertEquals(true, PersianVoiceSlangParserEngine.isRemainingBalanceCommentaryClause("۵ تومن موند"))
        assertEquals(true, PersianVoiceSlangParserEngine.isRemainingBalanceCommentaryClause("تهش ۳ میلیون اضافه اومد"))
        assertEquals(false, PersianVoiceSlangParserEngine.isRemainingBalanceCommentaryClause("۵۰ تومن قسط مونده رو پرداخت کردم"))

        // 4. Partitive macro expense ("قسط") following million-scale income promotes to million scale, while standalone "قسط" stays thousand scale
        val partitiveInstallment = PersianVoiceSlangParserEngine.parseMultiVoiceCommands(
            "۳۰ تومن حقوق ریختن ۲۰ تومنش خرج قسط شد"
        )
        assertEquals(2, partitiveInstallment.size)
        assertEquals(30_000_000L, partitiveInstallment[0].totalAmountToman)
        assertEquals(20_000_000L, partitiveInstallment[1].totalAmountToman)
        assertEquals("قسط", partitiveInstallment[1].purposeCategory)
    }

    @Test
    fun testBluBoxBalanceRejectionAndFastMemoizedResolveBank() {
        // 1. Blu Box / piggybank / failed tx / promo SMS MUST NOT return a main account balance in extractBalanceRial
        val bluBoxSms = "بلو\nموجودی باکس شما: ۵۳۹,۴۲۰ ریال"
        assertEquals(null, JibemanSmsEngine.extractBalanceRial(bluBoxSms))

        val bluFailedSms = "بلو\nتراکنش ناموفق به دلیل عدم موجودی کافی. حداقل موجودی ۵۳۹,۴۲۰ ریال"
        assertEquals(null, JibemanSmsEngine.extractBalanceRial(bluFailedSms))

        val bluLoanSms = "بلو\nمبلغ قسط وام شما: ۵۳۹,۴۲۰ ریال، مانده وام: ۱۰,۰۰۰,۰۰۰ ریال"
        assertEquals(null, JibemanSmsEngine.extractBalanceRial(bluLoanSms))

        // 2. Real Blu transaction SMS extracts exact main balance (11,522,065 Rials = 1,152,206 Tomans)
        val realBluTxSms = "بلو\nبرداشت پول\nمبلغ: ۳۰۰,۰۰۰ ریال\nموجودی: ۱۱,۵۲۲,۰۶۵ ریال"
        val parsedBlu = JibemanSmsEngine.parse(realBluTxSms)
        assertEquals(300_000L, parsedBlu?.amount)
        assertEquals(11_522_065L, parsedBlu?.balance)

        // 3. Memoized resolveBank handles thousands of unknown + known senders in milliseconds
        val startMs = System.currentTimeMillis()
        for (i in 0 until 20_000) {
            val sender = if (i % 50 == 0) "+989820002015" else "+98912000${i % 200}"
            JibemanSmsEngine.resolveBank(sender)
        }
        val elapsedMs = System.currentTimeMillis() - startMs
        assertEquals("resolveBank 20,000 lookups took ${elapsedMs}ms", true, elapsedMs < 1_000L)
        assertEquals("بلو", JibemanSmsEngine.resolveBank("+982187641")?.nameFa)
        assertEquals("بلو", JibemanSmsEngine.resolveBank("300087641")?.nameFa)
    }

    @Test
    fun testPaydayCycleCashflowAnalyzerAndSelfTransferExclusion() {
        // Anchor date: 28 Aban 1405 (2026-11-19 12:00:00 local)
        val (gy, gm, gd) = JalaliCalendarUtils.jalaliToGregorian(1405, 8, 28)
        val cal = java.util.Calendar.getInstance().apply {
            set(gy, gm - 1, gd, 12, 0, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val nowMs = cal.timeInMillis
        val oneMinMs = 60_000L
        val oneDayMs = 86_400_000L

        // 1. Default paydayStartDay = 1 -> cycle starts 1 Aban 1405; Mehr tx (35 days ago) MUST be excluded!
        val txs = listOf(
            // Old Mehr income (outside current payday cycle)
            CashflowTxSnapshot(1L, "INCOME", 50_000_000L, 50_000_000L, bankName = "بلو", timestamp = nowMs - 35 * oneDayMs),
            // Real Aban income
            CashflowTxSnapshot(2L, "INCOME", 12_000_000L, 12_000_000L, bankName = "پاسارگاد", timestamp = nowMs - 5 * oneDayMs),
            // Real Aban expense
            CashflowTxSnapshot(3L, "EXPENSE", 15_000_000L, 15_000_000L, bankName = "بلو", timestamp = nowMs - 2 * oneDayMs),
            // Self-transfer pair: 4,000,000 from Pasargad -> Blu within 2 minutes (MUST be excluded!)
            CashflowTxSnapshot(4L, "EXPENSE", 4_000_000L, 4_000_000L, bankName = "پاسارگاد", timestamp = nowMs - oneDayMs),
            CashflowTxSnapshot(5L, "INCOME", 4_000_000L, 4_000_000L, bankName = "بلو", timestamp = nowMs - oneDayMs + 2 * oneMinMs),
            // Same-amount pair > 15 minutes apart (MUST NOT be excluded!)
            CashflowTxSnapshot(6L, "EXPENSE", 500_000L, 500_000L, bankName = "بلو", timestamp = nowMs - 3 * oneDayMs),
            CashflowTxSnapshot(7L, "INCOME", 500_000L, 500_000L, bankName = "سپه", timestamp = nowMs - 3 * oneDayMs + 20 * oneMinMs),
            // Same-bank same-amount pair within 1 min (MUST NOT be excluded as cross-bank self-transfer!)
            CashflowTxSnapshot(8L, "EXPENSE", 200_000L, 200_000L, bankName = "بلو", timestamp = nowMs - 4 * oneDayMs),
            CashflowTxSnapshot(9L, "INCOME", 200_000L, 200_000L, bankName = "بلو", timestamp = nowMs - 4 * oneDayMs + oneMinMs)
        )

        val summary = OrosiCashflowAnalyzer.analyzeCurrentPaydayCycle(
            transactions = txs,
            paydayStartDay = 1,
            reservedCommitmentsToman = 0L,
            nowMillis = nowMs
        )
        assertEquals(12_700_000L, summary.totalIncomeToman) // 12M + 500K + 200K (excludes 50M Mehr & 4M self-transfer)
        assertEquals(15_700_000L, summary.totalExpenseToman) // 15M + 500K + 200K (excludes 4M self-transfer)
        assertEquals(4_000_000L, summary.excludedSelfTransferTotalToman)
        assertEquals(1, summary.excludedSelfTransferPairs)
        assertEquals(-3_000_000L, summary.netBalanceToman)
        assertEquals(CashflowHealthStatus.DEFICIT, summary.healthStatus)

        // 2. Positive & Balanced statuses + custom paydayStartDay = 25 (25 Aban .. 24 Azar)
        val positiveSummary = OrosiCashflowAnalyzer.analyzeCurrentPaydayCycle(
            transactions = listOf(
                CashflowTxSnapshot(10L, "EXPENSE", 10_000_000L, 10_000_000L, bankName = "بلو", timestamp = nowMs - 5 * oneDayMs),
                CashflowTxSnapshot(11L, "INCOME", 20_000_000L, 20_000_000L, bankName = "بلو", timestamp = nowMs - oneDayMs),
                CashflowTxSnapshot(12L, "EXPENSE", 5_000_000L, 5_000_000L, bankName = "بلو", timestamp = nowMs)
            ),
            paydayStartDay = 25,
            reservedCommitmentsToman = 0L,
            nowMillis = nowMs
        )
        assertEquals(20_000_000L, positiveSummary.totalIncomeToman)
        assertEquals(5_000_000L, positiveSummary.totalExpenseToman)
        assertEquals(CashflowHealthStatus.POSITIVE, positiveSummary.healthStatus)

        val balancedSummary = OrosiCashflowAnalyzer.analyzeCurrentPaydayCycle(
            transactions = listOf(
                CashflowTxSnapshot(13L, "INCOME", 1_000_000L, 1_000_000L, bankName = "بلو", timestamp = nowMs),
                CashflowTxSnapshot(14L, "EXPENSE", 1_000_000L, 1_000_000L, bankName = "بلو", timestamp = nowMs - 30 * oneMinMs)
            ),
            paydayStartDay = 1,
            reservedCommitmentsToman = 0L,
            nowMillis = nowMs
        )
        assertEquals(CashflowHealthStatus.BALANCED, balancedSummary.healthStatus)
    }

    @Test
    fun testVideoABatchVoiceLeftoverKeptAndFutureDebtReceivableCommitments() {
        // 1. Video abatch_20: Leftover-kept commentary ("و بقیه‌اش رو هم نگه داشتم") MUST be dropped!
        val keptPhrase = "سلام من ۳۰ تومن حقوق گرفتم ۲۵ تومنش رو دادم بستنی ۲۵ تومنش رو دادم به خرج ماشین و بقیه‌اش رو هم نگه داشتم"
        val keptItems = PersianVoiceSlangParserEngine.parseMultiVoiceCommands(keptPhrase)
        assertEquals(3, keptItems.size)
        assertEquals("حقوق", keptItems[0].purposeCategory)
        assertEquals(30_000_000L, keptItems[0].totalAmountToman)
        assertEquals(true, keptItems[0].isIncome)
        assertNull(keptItems[0].commitmentKind)

        assertEquals("بستنی", keptItems[1].purposeCategory)
        assertEquals(25_000L, keptItems[1].totalAmountToman)
        assertEquals(false, keptItems[1].isIncome)
        assertNull(keptItems[1].commitmentKind)

        assertEquals("ماشین", keptItems[2].purposeCategory)
        assertEquals(25_000_000L, keptItems[2].totalAmountToman)
        assertEquals(false, keptItems[2].isIncome)
        assertNull(keptItems[2].commitmentKind)

        // Generalized leftover-kept commentary variants
        assertEquals(true, PersianVoiceSlangParserEngine.isRemainingBalanceCommentaryClause("بقیشو گذاشتم کنار"))
        assertEquals(true, PersianVoiceSlangParserEngine.isRemainingBalanceCommentaryClause("بقیه‌اش رو هم نگه داشتم"))

        // 2. Video abatch_21: Contrast split ("ولی") + future debt/receivable routed to Commitments ("تعهدات")
        val debtReceivablePhrase = "سلام من ۳۹ تومن بدهی دارم ولی فردا ۵۰ تومن طلب دارم"
        val commitmentItems = PersianVoiceSlangParserEngine.parseMultiVoiceCommands(debtReceivablePhrase)
        assertEquals(2, commitmentItems.size)

        assertEquals("بدهی", commitmentItems[0].purposeCategory)
        assertEquals(39_000L, commitmentItems[0].totalAmountToman)
        assertEquals(false, commitmentItems[0].isIncome)
        assertEquals("DEBT", commitmentItems[0].commitmentKind)
        assertEquals("", commitmentItems[0].dueDateHint)

        assertEquals("طلب", commitmentItems[1].purposeCategory)
        assertEquals(50_000L, commitmentItems[1].totalAmountToman)
        assertEquals(true, commitmentItems[1].isIncome)
        assertEquals("RECEIVABLE", commitmentItems[1].commitmentKind)
        assertEquals("فردا", commitmentItems[1].dueDateHint)

        // 3. Generalized named future debt & receivable with "اما" and "پس‌فردا"
        val namedCommitments = PersianVoiceSlangParserEngine.parseMultiVoiceCommands(
            "به علی ۲۰۰ تومن بدهکارم اما پس‌فردا ۳۰۰ تومن از رضا طلب دارم"
        )
        assertEquals(2, namedCommitments.size)
        assertEquals("DEBT", namedCommitments[0].commitmentKind)
        assertEquals(200_000L, namedCommitments[0].totalAmountToman)
        assertEquals(true, namedCommitments[0].purposeCategory.contains("علی"))

        assertEquals("RECEIVABLE", namedCommitments[1].commitmentKind)
        assertEquals(300_000L, namedCommitments[1].totalAmountToman)
        assertEquals("پس‌فردا", namedCommitments[1].dueDateHint)
        assertEquals(true, namedCommitments[1].purposeCategory.contains("رضا"))

        // 4. Settled debt/receivable MUST stay regular transactions (commitmentKind == null)
        val paidDebt = PersianVoiceSlangParserEngine.parseVoiceCommand("۵۰ تومن بدهی که داشتم رو دادم")
        assertNull(paidDebt.commitmentKind)
        assertEquals(false, paidDebt.isIncome)
        assertEquals(50_000L, paidDebt.totalAmountToman)

        val collectedReceivable = PersianVoiceSlangParserEngine.parseVoiceCommand("۱۰۰ تومن طلبم رو گرفتم")
        assertNull(collectedReceivable.commitmentKind)
        assertEquals(true, collectedReceivable.isIncome)
        assertEquals(100_000L, collectedReceivable.totalAmountToman)

        // 5. Relative Jalali date resolution ("فردا" advances 28 Aban -> 29 Aban)
        val (gy, gm, gd) = JalaliCalendarUtils.jalaliToGregorian(1405, 8, 28)
        val baseCal = java.util.Calendar.getInstance().apply {
            set(gy, gm - 1, gd, 12, 0, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        assertEquals(
            "۲۹ آبان ۱۴۰۵",
            JalaliCalendarUtils.resolveRelativeJalaliDate("فردا", baseCal.timeInMillis)
        )
    }

    @Test
    fun testVideoBBatchTemporalAndFutureIntentVoiceNlu() {
        // 1. Exact video bbatch_22 utterance:
        // Past salary (-2 days) + future expected inflow (tomorrow -> RECEIVABLE commitment) + planned bicycle purchase (-> DEBT commitment)
        val phrase = "خوب من پریروز ۳۰ تومن حقوق گرفتم فردا هم ۱۵ تومان دیگه میاد به حسابم و می‌خوام ۱۰ تومن بدم به یه دوچرخه"
        val items = PersianVoiceSlangParserEngine.parseMultiVoiceCommands(phrase)
        assertEquals(3, items.size)

        // Item 0: Past salary transaction
        assertEquals("حقوق", items[0].purposeCategory)
        assertEquals(30_000_000L, items[0].totalAmountToman)
        assertEquals(true, items[0].isIncome)
        assertNull(items[0].commitmentKind)
        assertEquals(-2, items[0].eventDayOffset)

        // Item 1: Future expected inflow -> RECEIVABLE commitment with dueDateHint = "فردا"
        assertEquals("واریز به حساب", items[1].purposeCategory)
        assertEquals(15_000_000L, items[1].totalAmountToman)
        assertEquals(true, items[1].isIncome)
        assertEquals("RECEIVABLE", items[1].commitmentKind)
        assertEquals("فردا", items[1].dueDateHint)
        assertEquals("واریز آینده", items[1].commitmentSubLabel)

        // Item 2: Future planned purchase -> DEBT commitment with clean title "دوچرخه"
        assertEquals("دوچرخه", items[2].purposeCategory)
        assertEquals(10_000_000L, items[2].totalAmountToman)
        assertEquals(false, items[2].isIncome)
        assertEquals("DEBT", items[2].commitmentKind)
        assertEquals("برنامه خرید", items[2].commitmentSubLabel)

        // 2. Generalized past-date single transaction ("دیروز" -> eventDayOffset = -1)
        val yesterdayBakery = PersianVoiceSlangParserEngine.parseVoiceCommand("دیروز ۵۰ تومن نانوایی خریدم")
        assertEquals("نانوایی", yesterdayBakery.purposeCategory)
        assertEquals(50_000L, yesterdayBakery.totalAmountToman)
        assertEquals(false, yesterdayBakery.isIncome)
        assertNull(yesterdayBakery.commitmentKind)
        assertEquals(-1, yesterdayBakery.eventDayOffset)

        // 3. Generalized planned durable purchase ("می‌خوام ۱۲ تومن بدم گوشی بخرم")
        val plannedPhone = PersianVoiceSlangParserEngine.parseVoiceCommand("می‌خوام ۱۲ تومن بدم گوشی بخرم")
        assertEquals("گوشی", plannedPhone.purposeCategory)
        assertEquals(12_000_000L, plannedPhone.totalAmountToman)
        assertEquals("DEBT", plannedPhone.commitmentKind)
        assertEquals("برنامه خرید", plannedPhone.commitmentSubLabel)

        // 4. Generalized future expected inflow ("پس‌فردا قراره ۲۰ میلیون واریز بشه به حسابم")
        val futureDeposit = PersianVoiceSlangParserEngine.parseVoiceCommand("پس‌فردا قراره ۲۰ میلیون واریز بشه به حسابم")
        assertEquals(20_000_000L, futureDeposit.totalAmountToman)
        assertEquals(true, futureDeposit.isIncome)
        assertEquals("RECEIVABLE", futureDeposit.commitmentKind)
        assertEquals("پس‌فردا", futureDeposit.dueDateHint)

        // 5. Negated/cancelled past intent MUST still be dropped!
        val cancelledBike = PersianVoiceSlangParserEngine.parseMultiVoiceCommands("می‌خواستم ۱۰ تومن بدم دوچرخه بخرم ولی نخریدم")
        assertEquals(0, cancelledBike.size)
    }

    @Test
    fun testFastGroupedPersianNumberFormatting() {
        assertEquals("۰", PersianNumberUtils.formatGroupedPersianLong(0L))
        assertEquals("۷۵۰", PersianNumberUtils.formatGroupedPersianLong(750L))
        assertEquals("۱,۰۰۰", PersianNumberUtils.formatGroupedPersianLong(1_000L))
        assertEquals("۴۲,۶۶۶,۸۷۷", PersianNumberUtils.formatGroupedPersianLong(42_666_877L))
        assertEquals("۱,۰۰۰,۰۰۰,۰۰۰", PersianNumberUtils.formatGroupedPersianLong(1_000_000_000L))
        assertEquals("۴۲,۶۶۶,۸۷۷ تومان", PersianNumberUtils.formatToman(42_666_877L, showUnit = true))
        assertEquals("\u2066\u200E+۱,۱۵۲,۲۰۶\u2069 تومان", PersianNumberUtils.formatIncomeToman(1_152_206L, showUnit = true))
    }

    @Test
    fun testBatchVoiceSmsAutoMatcherExactDuplicateYesterdayDirectionCommitmentAndSuggestion() {
        val (gy, gm, gd) = JalaliCalendarUtils.jalaliToGregorian(1405, 8, 28)
        val cal = java.util.Calendar.getInstance().apply {
            set(gy, gm - 1, gd, 14, 30, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val todayMs = cal.timeInMillis
        val yesterdayMs = todayMs - 86_400_000L
        val todayDate = JalaliCalendarUtils.timestampToJalali(todayMs).dateWithYear
        val yesterdayDate = JalaliCalendarUtils.timestampToJalali(yesterdayMs).dateWithYear

        val untaggedSms = listOf(
            UntaggedSmsCandidate(101L, "بلو", 35_000L, false, todayMs - 600_000L, todayDate, "۱۴:۲۰", "کارت بلو"),
            UntaggedSmsCandidate(102L, "سپه", 35_000L, false, todayMs - 1_200_000L, todayDate, "۱۴:۱۰", "کارت سپه"),
            UntaggedSmsCandidate(103L, "پاسارگاد", 120_000L, true, todayMs - 900_000L, todayDate, "۱۴:۱۵", "کارت پاسارگاد"),
            UntaggedSmsCandidate(104L, "ملت", 52_000L, false, todayMs - 300_000L, todayDate, "۱۴:۲۵", "کارت ملت"),
            UntaggedSmsCandidate(105L, "بلو", 70_000L, false, yesterdayMs, yesterdayDate, "۱۴:۳۰", "کارت بلو")
        )

        fun sampleVoiceItem(
            amount: Long,
            purpose: String,
            isIncome: Boolean = false,
            eventDayOffset: Int = 0,
            commitmentKind: String? = null
        ) = ParsedVoiceExpense(
            recognizedSpeech = purpose,
            totalAmountToman = amount,
            userShareToman = amount,
            dongAmountToman = 0L,
            dongFriendName = "",
            storeName = "نقدی / دستی",
            purposeCategory = purpose,
            cardLabel = "نقدی / دستی",
            isIncome = isIncome,
            commitmentKind = commitmentKind,
            eventDayOffset = eventDayOffset
        )

        val spokenItems = listOf(
            // 0: Today 35k expense -> matches newest 35k SMS (101)
            sampleVoiceItem(35_000L, "نانوایی", isIncome = false),
            // 1: Second today 35k expense -> matches second distinct 35k SMS (102)
            sampleVoiceItem(35_000L, "قهوه", isIncome = false),
            // 2: Today 120k income -> matches 120k income SMS (103)
            sampleVoiceItem(120_000L, "تسویه علی", isIncome = true),
            // 3: Yesterday 70k expense -> matches yesterday's 70k SMS (105)
            sampleVoiceItem(70_000L, "میوه", isIncome = false, eventDayOffset = -1),
            // 4: Future commitment with same amount as an SMS -> MUST NEVER match!
            sampleVoiceItem(52_000L, "بدهی", isIncome = false, commitmentKind = "DEBT"),
            // 5: Today 50k expense (slightly off from 52k Mellat SMS) -> no exact match, suggests 104!
            sampleVoiceItem(50_000L, "سوپرمارکت", isIncome = false),
            // 6: Today 52k INCOME -> direction mismatch against 52k EXPENSE SMS (104), so neither matched nor suggested!
            sampleVoiceItem(52_000L, "هدیه", isIncome = true)
        )

        val matches = OrosiVoiceSmsMatcher.matchBatch(spokenItems, untaggedSms, nowMillis = todayMs)
        assertEquals(7, matches.size)

        assertEquals(101L, matches[0].matchedCandidate?.id)
        assertEquals(true, matches[0].isExactAutoMatch)

        assertEquals(102L, matches[1].matchedCandidate?.id)
        assertEquals(true, matches[1].isExactAutoMatch)

        assertEquals(103L, matches[2].matchedCandidate?.id)
        assertEquals(true, matches[2].isExactAutoMatch)

        assertEquals(105L, matches[3].matchedCandidate?.id)
        assertEquals(true, matches[3].isExactAutoMatch)

        assertNull(matches[4].matchedCandidate)
        assertNull(matches[4].suggestedCandidate)

        assertNull(matches[5].matchedCandidate)
        assertEquals(104L, matches[5].suggestedCandidate?.id)
        assertEquals(false, matches[5].isExactAutoMatch)

        assertNull(matches[6].matchedCandidate)
        assertNull(matches[6].suggestedCandidate)
    }

    @Test
    fun testSelfTransferPairDetailsAndBidiScaleToggleLabels() {
        val (gy, gm, gd) = JalaliCalendarUtils.jalaliToGregorian(1405, 8, 28)
        val cal = java.util.Calendar.getInstance().apply {
            set(gy, gm - 1, gd, 15, 10, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val nowMs = cal.timeInMillis

        val summary = OrosiCashflowAnalyzer.analyzeCurrentPaydayCycle(
            transactions = listOf(
                CashflowTxSnapshot(1L, "EXPENSE", 3_500_000L, 3_500_000L, bankName = "پاسارگاد", timestamp = nowMs),
                CashflowTxSnapshot(2L, "INCOME", 3_500_000L, 3_500_000L, bankName = "بلو", timestamp = nowMs + 90_000L)
            ),
            paydayStartDay = 1,
            reservedCommitmentsToman = 0L,
            nowMillis = nowMs + 120_000L
        )
        assertEquals(1, summary.excludedSelfTransferPairs)
        assertEquals(1, summary.excludedSelfTransferDetails.size)
        val detail = summary.excludedSelfTransferDetails.first()
        assertEquals("پاسارگاد", detail.fromBankName)
        assertEquals("بلو", detail.toBankName)
        assertEquals(3_500_000L, detail.amountToman)
        assertEquals(1L, detail.expenseTxId)
        assertEquals(2L, detail.incomeTxId)
        assertEquals(true, detail.dateTimeLabel.isNotBlank())

        // BiDi LTR isolation on scale toggle labels and isolated amount runs
        assertEquals(
            "تغییر واحد به میلیون (\u2066\u200E×۱۰۰۰\u2069)",
            PersianNumberUtils.formatScaleToggleLabel(isThousandScale = true)
        )
        assertEquals(
            "تغییر واحد به هزار (\u2066\u200E÷۱۰۰۰\u2069)",
            PersianNumberUtils.formatScaleToggleLabel(isThousandScale = false)
        )
        assertEquals("\u2066\u200E+۵۰۰,۰۰۰\u2069", PersianNumberUtils.formatIsolatedAmountRun(500_000L, isIncome = true))
        assertEquals("\u2066۵۰۰,۰۰۰\u2069", PersianNumberUtils.formatIsolatedAmountRun(500_000L, isIncome = false))
    }

    @Test
    fun testColloquialCompoundMillionsApproximatePastInflowAndExplicitJalaliDatesV24() {
        // 1. Exact real-device sentence from video dbatch_25
        val dbatch25 = "خوب من امروز ۵۰۰ هزار تومان خرج تارا کردم یک و صد و خورده‌ای هم از صرافی آبان تتر اومد به حسابم"
        val items = PersianVoiceSlangParserEngine.parseMultiVoiceCommands(dbatch25)
        assertEquals(2, items.size)

        assertEquals("تارا", items[0].purposeCategory)
        assertEquals(500_000L, items[0].totalAmountToman)
        assertEquals(false, items[0].isIncome)
        assertEquals(false, items[0].isCommitment)
        assertEquals(false, items[0].isApproximateAmount)

        assertEquals("صرافی آبان تتر", items[1].purposeCategory)
        assertEquals(1_100_000L, items[1].totalAmountToman)
        assertEquals(true, items[1].isIncome)
        assertEquals(false, items[1].isCommitment)
        assertEquals(true, items[1].isApproximateAmount)
        assertEquals("", items[1].explicitJalaliDate)

        // 2. Generalized colloquial compound million shorthands
        val phone = PersianVoiceSlangParserEngine.parseVoiceCommand("دو و پونصد خرید گوشی")
        assertEquals(2_500_000L, phone.totalAmountToman)
        assertEquals("گوشی", phone.purposeCategory)

        val rent = PersianVoiceSlangParserEngine.parseVoiceCommand("یک و دویست و پنجاه اجاره")
        assertEquals(1_250_000L, rent.totalAmountToman)
        assertEquals("اجاره", rent.purposeCategory)

        val approxFood = PersianVoiceSlangParserEngine.parseVoiceCommand("حدود ۲۰۰ تومن شام")
        assertEquals(200_000L, approxFood.totalAmountToman)
        assertEquals("شام", approxFood.purposeCategory)
        assertEquals(true, approxFood.isApproximateAmount)

        // 3. Explicit Jalali date extraction & propagation without colliding with "صرافی آبان تتر"
        val datedSingle = PersianVoiceSlangParserEngine.parseVoiceCommand("۲۸ آبان ۵۰۰ تومن خرج تارا کردم")
        assertEquals("تارا", datedSingle.purposeCategory)
        assertEquals(500_000L, datedSingle.totalAmountToman)
        assertEquals(true, datedSingle.explicitJalaliDate.startsWith("۲۸ آبان "))

        val datedMulti = PersianVoiceSlangParserEngine.parseMultiVoiceCommands(
            "۲۸ آبان ۵۰۰ تومن خرج تارا کردم و یک و صد و خورده‌ای از صرافی آبان تتر اومد به حسابم"
        )
        assertEquals(2, datedMulti.size)
        assertEquals(true, datedMulti[0].explicitJalaliDate.startsWith("۲۸ آبان "))
        assertEquals(datedMulti[0].explicitJalaliDate, datedMulti[1].explicitJalaliDate)
        assertEquals("صرافی آبان تتر", datedMulti[1].purposeCategory)
        assertEquals(1_100_000L, datedMulti[1].totalAmountToman)
        assertEquals(true, datedMulti[1].isIncome)
        assertEquals(true, datedMulti[1].isApproximateAmount)

        // 4. Jalali date string round-trip conversion
        val ts28Aban = JalaliCalendarUtils.jalaliDateStringToTimestampMillis("۲۸ آبان ۱۴۰۵")
        assertEquals("۲۸ آبان ۱۴۰۵", JalaliCalendarUtils.timestampToJalali(ts28Aban!!).dateWithYear)
    }

    @Test
    fun testLateNight36hSmartWindowAndApproximateBankSmsAutoMatchV24() {
        val (gy29, gm29, gd29) = JalaliCalendarUtils.jalaliToGregorian(1405, 8, 29)
        val tehranTz = java.util.TimeZone.getTimeZone("Asia/Tehran")
        val now324Am = java.util.Calendar.getInstance(tehranTz).apply {
            set(gy29, gm29 - 1, gd29, 3, 24, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis

        val smsTaraMs = now324Am - (7L * 3_600_000L + 30L * 60_000L) // 28 Aban 19:54
        val smsAbanMs = now324Am - (14L * 3_600_000L) // 28 Aban 13:24
        val smsOldMs = now324Am - (48L * 3_600_000L) // 48h ago (outside 36h window!)

        val date28 = JalaliCalendarUtils.timestampToJalali(smsTaraMs).dateWithYear
        val date27 = JalaliCalendarUtils.timestampToJalali(smsOldMs).dateWithYear

        val untaggedSms = listOf(
            UntaggedSmsCandidate(501L, "بلو", 500_000L, false, smsTaraMs, date28, "۱۹:۵۴", "کارت بلو"),
            UntaggedSmsCandidate(502L, "بلو", 1_161_741L, true, smsAbanMs, date28, "۱۳:۲۷", "کارت بلو"),
            UntaggedSmsCandidate(503L, "سپه", 500_000L, false, smsOldMs, date27, "۱۰:۰۰", "کارت سپه")
        )

        val spokenItems = PersianVoiceSlangParserEngine.parseMultiVoiceCommands(
            "خوب من امروز ۵۰۰ هزار تومان خرج تارا کردم یک و صد و خورده‌ای هم از صرافی آبان تتر اومد به حسابم"
        )
        val matches = OrosiVoiceSmsMatcher.matchBatch(spokenItems, untaggedSms, nowMillis = now324Am)
        assertEquals(2, matches.size)

        // Item 0: exact match across midnight within 36h window -> Blu 500,000
        assertEquals(501L, matches[0].matchedCandidate?.id)
        assertEquals(true, matches[0].isExactAutoMatch)
        assertEquals(false, matches[0].isApproximateAutoMatch)

        // Item 1: approximate auto-match across midnight within 36h window -> Blu +1,161,741
        assertEquals(502L, matches[1].matchedCandidate?.id)
        assertEquals(false, matches[1].isExactAutoMatch)
        assertEquals(true, matches[1].isApproximateAutoMatch)
        assertEquals(1_161_741L, matches[1].matchedCandidate?.amountToman)

        // Explicit older date ("۲۷ آبان") strictly matches ONLY 27 Aban (503L), not 28 Aban (501L)
        val explicitOldItem = PersianVoiceSlangParserEngine.parseVoiceCommand("۲۷ آبان ۵۰۰ تومن خرج تارا کردم")
            .copy(explicitJalaliDate = date27)
        val explicitMatches = OrosiVoiceSmsMatcher.matchBatch(listOf(explicitOldItem), untaggedSms, nowMillis = now324Am)
        assertEquals(503L, explicitMatches[0].matchedCandidate?.id)
        assertEquals(true, explicitMatches[0].isExactAutoMatch)
    }

    @Test
    fun testExternalExchangeWithdrawalsAndPredicateContinuationV25() {
        // 1. Video 4 exact sentence: external exchange withdrawal + "و بعد" sequential split
        val video4 = "من یک و صد و خورده‌ای از حساب صرافی برداشت کردم کامل و بعد ۵۰۰ تومن بدهی تارا رو دادم"
        val v4Items = PersianVoiceSlangParserEngine.parseMultiVoiceCommands(video4)
        assertEquals(2, v4Items.size)
        assertEquals(true, v4Items[0].isIncome)
        assertEquals(false, v4Items[0].isCommitment)
        assertEquals(1_100_000L, v4Items[0].totalAmountToman)
        assertEquals(true, v4Items[0].isApproximateAmount)
        assertEquals("برداشت از صرافی", v4Items[0].purposeCategory)

        assertEquals(false, v4Items[1].isIncome)
        assertEquals(false, v4Items[1].isCommitment)
        assertEquals(500_000L, v4Items[1].totalAmountToman)
        assertEquals(true, v4Items[1].purposeCategory.contains("تارا"))

        // 2. Video 1 exact sentence: predicate continuation "برداشت کردم و اومد به حسابم" + "بعد ۵۰۰ تومن خرج تارا کردم"
        val video1 = "سلام من یک و صد و خورده‌ای از حسابم برداشت کردم و اومد به حسابم بعد ۵۰۰ تومن خرج تارا کردم"
        val v1Items = PersianVoiceSlangParserEngine.parseMultiVoiceCommands(video1)
        assertEquals(2, v1Items.size)
        assertEquals(true, v1Items[0].isIncome)
        assertEquals(1_100_000L, v1Items[0].totalAmountToman)
        assertEquals(true, v1Items[0].isApproximateAmount)
        assertEquals("واریز به حساب", v1Items[0].purposeCategory)

        assertEquals(false, v1Items[1].isIncome)
        assertEquals(500_000L, v1Items[1].totalAmountToman)
        assertEquals("تارا", v1Items[1].purposeCategory)

        // 3. Generalized external wallets/exchanges -> INCOME
        val nobitex = PersianVoiceSlangParserEngine.parseVoiceCommand("۲ و ۵۰۰ از نوبیتکس برداشت کردم")
        assertEquals(true, nobitex.isIncome)
        assertEquals(2_500_000L, nobitex.totalAmountToman)
        assertEquals(true, nobitex.purposeCategory.contains("نوبیتکس"))

        val digipay = PersianVoiceSlangParserEngine.parseVoiceCommand("۳۰۰ تومن از کیف پول دیجی‌پی برداشت کردم")
        assertEquals(true, digipay.isIncome)
        assertEquals(300_000L, digipay.totalAmountToman)
        assertEquals(true, digipay.purposeCategory.contains("دیجی‌پی"))

        // 4. ATM withdrawal guard -> stays EXPENSE
        val atm = PersianVoiceSlangParserEngine.parseVoiceCommand("۵۰۰ تومن از عابربانک برداشت کردم")
        assertEquals(false, atm.isIncome)
        assertEquals(500_000L, atm.totalAmountToman)

        // 5. Relative date phrase guard ("ماه بعد" / "هفته بعد") does NOT falsely split
        val nextMonth = PersianVoiceSlangParserEngine.parseMultiVoiceCommands("ماه بعد ۲ میلیون قسط دارم و ۵۰۰ تومن خرج کردم")
        assertEquals(2, nextMonth.size)
        assertEquals(2_000_000L, nextMonth[0].totalAmountToman)
        assertEquals(500_000L, nextMonth[1].totalAmountToman)
    }

    @Test
    fun testVoiceNluV26CommitmentsVaguePast7DayMatchAndCashflowNetBalance() {
        // 1. Video fbatch (01:28): "سلام من الان مقداری موجودی دارم ولی باید سر ماه ۲۴ تومان قسط بدم"
        val video2 = PersianVoiceSlangParserEngine.parseVoiceCommand(
            "سلام من الان مقداری موجودی دارم ولی باید سر ماه ۲۴ تومان قسط بدم"
        )
        assertEquals("DEBT", video2.commitmentKind)
        assertEquals("LOAN_INSTALLMENT", video2.commitmentCategory)
        assertEquals("وام و قسط", video2.commitmentSubLabel)
        assertEquals(24_000_000L, video2.totalAmountToman)
        assertEquals("سر ماه", video2.dueDateHint)
        assertEquals("قسط", video2.purposeCategory)

        // 2. Regression guard: past micro installment fee STILL stays at 35,000 Tomans (not promoted to millions!)
        val pastMicroFee = PersianVoiceSlangParserEngine.parseVoiceCommand("۳۵ تومان هزینه قسط پنجم وام دیشب")
        assertEquals(null, pastMicroFee.commitmentKind)
        assertEquals(35_000L, pastMicroFee.totalAmountToman)
        assertEquals("هزینه قسط پنجم وام", pastMicroFee.purposeCategory)

        // 3. Generalized spaced Sayyad check obligation + "سر ماه" / "آخر ماه" Jalali date resolution
        val checkObligation = PersianVoiceSlangParserEngine.parseVoiceCommand("باید آخر ماه ۱۰ تومن چک بدم")
        assertEquals("DEBT", checkObligation.commitmentKind)
        assertEquals("SAYYAD_ISSUED", checkObligation.commitmentCategory)
        assertEquals(10_000_000L, checkObligation.totalAmountToman)
        assertEquals("آخر ماه", checkObligation.dueDateHint)

        val aban30Ts = JalaliCalendarUtils.jalaliDayStartMillis(1405, 8, 30) + 12L * 3_600_000L
        assertEquals("۱ آذر ۱۴۰۵", JalaliCalendarUtils.resolveRelativeJalaliDate("سر ماه", aban30Ts))
        assertEquals("۱ آذر ۱۴۰۵", JalaliCalendarUtils.resolveRelativeJalaliDate("اول ماه", aban30Ts))
        assertEquals("۳۰ آبان ۱۴۰۵", JalaliCalendarUtils.resolveRelativeJalaliDate("آخر ماه", aban30Ts))

        // 4. Video fbatch_31 (00:35): "خوب من یه چند روز پیش ۵۰۰ هزار تومن دادم به تارا"
        val video3 = PersianVoiceSlangParserEngine.parseVoiceCommand(
            "خوب من یه چند روز پیش ۵۰۰ هزار تومن دادم به تارا"
        )
        assertEquals("تارا", video3.purposeCategory)
        assertEquals(500_000L, video3.totalAmountToman)
        assertEquals(false, video3.isIncome)
        assertEquals(false, video3.isCommitment)
        assertEquals(true, video3.isVagueRecentPast)

        // 5. 7-day rolling SMS match for vague recent past (51 hours old Blu SMS)
        val nowTs = JalaliCalendarUtils.jalaliDayStartMillis(1405, 8, 30) + 22L * 3_600_000L
        val sms51hAgoTs = nowTs - 51L * 3_600_000L
        val sms51hJalali = JalaliCalendarUtils.timestampToJalali(sms51hAgoTs)
        val matchRes = OrosiVoiceSmsMatcher.matchBatch(
            items = listOf(video3),
            untaggedBankSms = listOf(
                UntaggedSmsCandidate(
                    id = 950L,
                    bankName = "بلو",
                    amountToman = 500_000L,
                    isIncome = false,
                    timestamp = sms51hAgoTs,
                    jalaliDate = sms51hJalali.dateWithYear,
                    timeStr = sms51hJalali.timeStr,
                    cardInfo = "کارت بلو"
                )
            ),
            nowMillis = nowTs
        ).first()
        assertEquals(true, matchRes.isExactAutoMatch)
        assertEquals(950L, matchRes.matchedCandidate?.id)

        // 6. Generalized vague recent past ("اخیراً" / "هفته پیش")
        val recentlyBakery = PersianVoiceSlangParserEngine.parseVoiceCommand("اخیراً ۱۵۰ تومن دادم نانوایی")
        assertEquals("نانوایی", recentlyBakery.purposeCategory)
        assertEquals(150_000L, recentlyBakery.totalAmountToman)
        assertEquals(true, recentlyBakery.isVagueRecentPast)

        // 7. Pure payday-cycle cashflow net balance: unpaid installments/checks are tracked separately
        val cashflowSummary = OrosiCashflowAnalyzer.analyzeCurrentPaydayCycle(
            transactions = listOf(
                CashflowTxSnapshot(1L, "INCOME", 42_953_275L, 42_953_275L, "بلو", aban30Ts - 3_600_000L),
                CashflowTxSnapshot(2L, "EXPENSE", 44_079_679L, 44_079_679L, "بلو", aban30Ts - 1_800_000L)
            ),
            paydayStartDay = 1,
            reservedCommitmentsToman = 24_000_000L,
            nowMillis = aban30Ts
        )
        assertEquals(-1_126_404L, cashflowSummary.netBalanceToman)
        assertEquals(24_000_000L, cashflowSummary.reservedCommitmentsToman)
        assertEquals(CashflowHealthStatus.DEFICIT, cashflowSummary.healthStatus)
    }

    @Test
    fun testOrosiV27CommitmentTitleDatePaydayOffsetAndHeroShortfall() {
        // 1. Exact user video sentence: "خوب ۵۰ تومن طلب دارم ۲۵ تومن بدهی دارم برای دیروز بوده بدهیم و ۳۵ هزار تومان خرج بستنی کردم امروز"
        val multi = PersianVoiceSlangParserEngine.parseMultiVoiceCommands(
            "خوب ۵۰ تومن طلب دارم ۲۵ تومن بدهی دارم برای دیروز بوده بدهیم و ۳۵ هزار تومان خرج بستنی کردم امروز"
        )
        assertEquals(3, multi.size)
        assertEquals("RECEIVABLE", multi[0].commitmentKind)
        assertEquals("طلب", multi[0].purposeCategory)
        assertEquals(50_000L, multi[0].totalAmountToman)

        assertEquals("DEBT", multi[1].commitmentKind)
        assertEquals("بدهی", multi[1].purposeCategory)
        assertEquals(25_000L, multi[1].totalAmountToman)
        assertEquals("دیروز", multi[1].dueDateHint)
        assertEquals(-1, multi[1].eventDayOffset)

        assertEquals(null, multi[2].commitmentKind)
        assertEquals("بستنی", multi[2].purposeCategory)
        assertEquals(35_000L, multi[2].totalAmountToman)
        assertEquals(0, multi[2].eventDayOffset)

        // 2. Generalized commitment past-reference copula & possessive echo cleanup + preserving real "برای ..." subject
        val gen1 = PersianVoiceSlangParserEngine.parseVoiceCommand("۱۰ میلیون طلب دارم مال پریروز بوده طلبم")
        assertEquals("RECEIVABLE", gen1.commitmentKind)
        assertEquals("طلب", gen1.purposeCategory)
        assertEquals("پریروز", gen1.dueDateHint)
        assertEquals(-2, gen1.eventDayOffset)

        val gen2 = PersianVoiceSlangParserEngine.parseVoiceCommand("۵ میلیون قسط دارم برای هفته پیش بوده قسطم")
        assertEquals("DEBT", gen2.commitmentKind)
        assertEquals("LOAN_INSTALLMENT", gen2.commitmentCategory)
        assertEquals("قسط", gen2.purposeCategory)
        assertEquals("هفته پیش", gen2.dueDateHint)

        val gen3 = PersianVoiceSlangParserEngine.parseVoiceCommand("۵ میلیون بدهی دارم برای تعمیر ماشین")
        assertEquals("DEBT", gen3.commitmentKind)
        assertEquals(true, gen3.purposeCategory.contains("تعمیر ماشین"))

        // 3. Past relative Jalali date resolution across month boundary (1 Azar -> 30 Aban & 29 Aban)
        val azar1Ts = JalaliCalendarUtils.jalaliDayStartMillis(1405, 9, 1) + 1L * 3_600_000L
        assertEquals("۳۰ آبان ۱۴۰۵", JalaliCalendarUtils.resolveRelativeJalaliDate("دیروز", azar1Ts))
        assertEquals("۲۹ آبان ۱۴۰۵", JalaliCalendarUtils.resolveRelativeJalaliDate("پریروز", azar1Ts))

        // 4. Payday cycle offset navigation + auto-fallback when Day 1 of new cycle is empty
        val aban28Ts = JalaliCalendarUtils.jalaliDayStartMillis(1405, 8, 28) + 12L * 3_600_000L
        val txs = listOf(
            CashflowTxSnapshot(1L, "INCOME", 10_000_000L, 10_000_000L, "بلو", aban28Ts),
            CashflowTxSnapshot(2L, "EXPENSE", 3_500_000L, 3_500_000L, "بلو", aban28Ts + 600_000L)
        )
        val initialOffset = OrosiCashflowAnalyzer.resolveInitialPaydayCycleOffset(txs, 1, azar1Ts)
        assertEquals(-1, initialOffset)
        val prevSummary = OrosiCashflowAnalyzer.analyzeCurrentPaydayCycle(txs, 1, 0L, azar1Ts, initialOffset)
        assertEquals("۱ آبان تا ۳۰ آبان ۱۴۰۵", prevSummary.cycleLabel)
        assertEquals(10_000_000L, prevSummary.totalIncomeToman)
        assertEquals(3_500_000L, prevSummary.totalExpenseToman)
        assertEquals(6_500_000L, prevSummary.netBalanceToman)

        // 5. Home Hero Card shortfall invariant (real balance headline + shortfall indicator, never misleading 0)
        val shortfall = OrosiCashflowAnalyzer.resolveHeroBalanceDisplay(2_432_702L, 24_000_000L)
        assertEquals(true, shortfall.isShortfall)
        assertEquals(2_432_702L, shortfall.headlineAmountToman)
        assertEquals(21_567_298L, shortfall.shortfallAmountToman)

        val normal = OrosiCashflowAnalyzer.resolveHeroBalanceDisplay(10_000_000L, 3_000_000L)
        assertEquals(false, normal.isShortfall)
        assertEquals(7_000_000L, normal.headlineAmountToman)
        assertEquals(0L, normal.shortfallAmountToman)
    }

    @Test
    fun testOrosiV28VoiceColloquialMillionFutureMonthCycleFilterAndUnique7DaySmsMatch() {
        val azar1Ts = JalaliCalendarUtils.jalaliDayStartMillis(1405, 9, 1) + 14L * 3_600_000L

        // 1. Settled installment million scaling + "یه ۲۵ تومن" guard + micro-fee preservation
        val settledInstallment = PersianVoiceSlangParserEngine.parseVoiceCommand(
            "سلام من برای این ماه یه ۲۵ تومن قسط داشتم که پرداخت کردم"
        )
        assertEquals(25_000_000L, settledInstallment.totalAmountToman)
        assertEquals("قسط", settledInstallment.purposeCategory)
        assertEquals(false, settledInstallment.isIncome)
        assertEquals(null, settledInstallment.commitmentKind)

        val microInstallmentFee = PersianVoiceSlangParserEngine.parseVoiceCommand(
            "۳۵ تومان هزینه قسط پنجم وام دیشب"
        )
        assertEquals(35_000L, microInstallmentFee.totalAmountToman)

        // 2. Colloquial single-million ("حدود یه تومان از باسلام دریافت کردم") + approximate Blu SMS match
        val basalamIncome = PersianVoiceSlangParserEngine.parseVoiceCommand(
            "سلام من حدود یه تومان از باسلام دریافت کردم"
        )
        assertEquals(1_000_000L, basalamIncome.totalAmountToman)
        assertEquals(true, basalamIncome.isApproximateAmount)
        assertEquals(true, basalamIncome.isIncome)
        assertEquals(null, basalamIncome.commitmentKind)

        val sms20hAgoTs = azar1Ts - 20L * 3_600_000L
        val sms20hJalali = JalaliCalendarUtils.fromTimestamp(sms20hAgoTs)
        val basalamMatch = OrosiVoiceSmsMatcher.matchBatch(
            items = listOf(basalamIncome),
            untaggedBankSms = listOf(
                UntaggedSmsCandidate(
                    id = 980L,
                    bankName = "بلو",
                    amountToman = 940_974L,
                    isIncome = true,
                    timestamp = sms20hAgoTs,
                    jalaliDate = sms20hJalali.dateWithYear,
                    timeStr = sms20hJalali.timeStr,
                    cardInfo = "کارت بلو"
                )
            ),
            nowMillis = azar1Ts
        ).first()
        assertEquals(true, basalamMatch.isApproximateAutoMatch)
        assertEquals(980L, basalamMatch.matchedCandidate?.id)

        // Explicit thousand still stays 1,000 while colloquial "یه تومن" / "یک تومان" is 1,000,000
        assertEquals(1_000_000L, PersianVoiceSlangParserEngine.parseVoiceCommand("یه تومن خریدم از دیجی‌کالا").totalAmountToman)
        assertEquals(1_000_000L, PersianVoiceSlangParserEngine.parseVoiceCommand("یک تومان دادم به علی").totalAmountToman)
        assertEquals(1_000L, PersianVoiceSlangParserEngine.parseVoiceCommand("یک هزار تومان نانوایی").totalAmountToman)

        // 3. Future-month commitment ("من برای ماه دی ۲۵ تومان قسط دارم") + standalone month rollover
        val deyInstallment = PersianVoiceSlangParserEngine.parseVoiceCommand("من برای ماه دی ۲۵ تومان قسط دارم")
        assertEquals(25_000_000L, deyInstallment.totalAmountToman)
        assertEquals("قسط", deyInstallment.purposeCategory)
        assertEquals("DEBT", deyInstallment.commitmentKind)
        assertEquals("LOAN_INSTALLMENT", deyInstallment.commitmentCategory)
        assertEquals("ماه دی", deyInstallment.dueDateHint)
        assertEquals(true, deyInstallment.explicitJalaliDate.startsWith("۱ دی "))

        assertEquals("۱ دی ۱۴۰۵", JalaliCalendarUtils.resolveStandaloneJalaliMonthDate("ماه دی", azar1Ts))
        assertEquals("۱ دی ۱۴۰۵", JalaliCalendarUtils.resolveStandaloneJalaliMonthDate("دی ماه", azar1Ts))
        assertEquals("۱ فروردین ۱۴۰۶", JalaliCalendarUtils.resolveStandaloneJalaliMonthDate("ماه فروردین", azar1Ts))
        assertEquals(null, JalaliCalendarUtils.resolveStandaloneJalaliMonthDate("صرافی آبان تتر", azar1Ts))

        // 4. Current-cycle commitment filtering (future month excluded; current month & overdue included)
        assertEquals(false, OrosiCashflowAnalyzer.isCommitmentDueInCurrentCycle("۱ دی ۱۴۰۵", 1, azar1Ts))
        assertEquals(true, OrosiCashflowAnalyzer.isCommitmentDueInCurrentCycle("۲۵ آذر ۱۴۰۵", 1, azar1Ts))
        assertEquals(true, OrosiCashflowAnalyzer.isCommitmentDueInCurrentCycle("۱۵ آبان ۱۴۰۵", 1, azar1Ts))

        // 5. Pass 1C unique 7-day dateless SMS auto-match vs ambiguity guard
        val taraDateless = PersianVoiceSlangParserEngine.parseVoiceCommand("۵۰۰ تومن خرج تارا")
        assertEquals(500_000L, taraDateless.totalAmountToman)
        assertEquals(false, taraDateless.isVagueRecentPast)

        val sms3dAgoTs = azar1Ts - 72L * 3_600_000L
        val sms5dAgoTs = azar1Ts - 120L * 3_600_000L
        val sms3dJalali = JalaliCalendarUtils.fromTimestamp(sms3dAgoTs)
        val sms5dJalali = JalaliCalendarUtils.fromTimestamp(sms5dAgoTs)

        val uniqueMatch = OrosiVoiceSmsMatcher.matchBatch(
            items = listOf(taraDateless),
            untaggedBankSms = listOf(
                UntaggedSmsCandidate(981L, "بلو", 500_000L, false, sms3dAgoTs, sms3dJalali.dateWithYear, sms3dJalali.timeStr, "کارت بلو")
            ),
            nowMillis = azar1Ts
        ).first()
        assertEquals(true, uniqueMatch.isExactAutoMatch)
        assertEquals(981L, uniqueMatch.matchedCandidate?.id)

        val ambiguousMatch = OrosiVoiceSmsMatcher.matchBatch(
            items = listOf(taraDateless),
            untaggedBankSms = listOf(
                UntaggedSmsCandidate(982L, "بلو", 500_000L, false, sms3dAgoTs, sms3dJalali.dateWithYear, sms3dJalali.timeStr, "کارت بلو"),
                UntaggedSmsCandidate(983L, "سپه", 500_000L, false, sms5dAgoTs, sms5dJalali.dateWithYear, sms5dJalali.timeStr, "کارت سپه")
            ),
            nowMillis = azar1Ts
        ).first()
        assertEquals(null, ambiguousMatch.matchedCandidate)
        assertEquals(false, ambiguousMatch.isExactAutoMatch)
        assertEquals(982L, ambiguousMatch.suggestedCandidate?.id)
    }

    @Test
    fun testOrosiV29VoiceSttKhordeEiCycleCommitmentsAndMonthlyReports() {
        // 1. STT "۹ صد و خورده‌ای از با سلام دریافت کردم" -> 900,000 + "باسلام"
        val nineHundred = PersianVoiceSlangParserEngine.parseVoiceCommand(
            "من ۹ صد و خورده‌ای از با سلام دریافت کردم"
        )
        assertEquals(900_000L, nineHundred.totalAmountToman)
        assertEquals(true, nineHundred.isIncome)
        assertEquals(true, nineHundred.isApproximateAmount)
        assertEquals("باسلام", nineHundred.purposeCategory)
        assertEquals(null, nineHundred.commitmentKind)

        // 2. "چند روز پیش یک و خورده‌ای از با سلام اومد به حسابم" -> title "باسلام" + matches +1,161,741
        val oneAndChange = PersianVoiceSlangParserEngine.parseVoiceCommand(
            "چند روز پیش یک و خورده‌ای از با سلام اومد به حسابم"
        )
        assertEquals(1_000_000L, oneAndChange.totalAmountToman)
        assertEquals("باسلام", oneAndChange.purposeCategory)
        assertEquals(true, oneAndChange.isIncome)
        assertEquals(true, oneAndChange.isApproximateAmount)
        assertEquals(true, oneAndChange.isVagueRecentPast)

        val azar2Ts = JalaliCalendarUtils.jalaliDayStartMillis(1405, 9, 2) + 5L * 3_600_000L + 36L * 60_000L
        val sms3dTs = azar2Ts - 70L * 3_600_000L
        val sms3dJ = JalaliCalendarUtils.fromTimestamp(sms3dTs)
        val match1M = OrosiVoiceSmsMatcher.matchBatch(
            items = listOf(oneAndChange),
            untaggedBankSms = listOf(
                UntaggedSmsCandidate(991L, "بلو", 1_161_741L, true, sms3dTs, sms3dJ.dateWithYear, sms3dJ.timeStr, "کارت بلو")
            ),
            nowMillis = azar2Ts
        ).first()
        assertEquals(true, match1M.isApproximateAutoMatch)
        assertEquals(991L, match1M.matchedCandidate?.id)

        // 3. "من ۲۰۰ و خورده‌ای دریافت کردم از باسلام" -> Pass 2B 7-day unique approx links 43h-old +278,694
        val twoHundred = PersianVoiceSlangParserEngine.parseVoiceCommand(
            "من ۲۰۰ و خورده‌ای دریافت کردم از باسلام"
        )
        assertEquals(200_000L, twoHundred.totalAmountToman)
        assertEquals("باسلام", twoHundred.purposeCategory)
        assertEquals(true, twoHundred.isIncome)
        assertEquals(true, twoHundred.isApproximateAmount)

        val sms43hTs = azar2Ts - 43L * 3_600_000L
        val sms43hJ = JalaliCalendarUtils.fromTimestamp(sms43hTs)
        val match200k = OrosiVoiceSmsMatcher.matchBatch(
            items = listOf(twoHundred),
            untaggedBankSms = listOf(
                UntaggedSmsCandidate(992L, "بلو", 278_694L, true, sms43hTs, sms43hJ.dateWithYear, sms43hJ.timeStr, "کارت بلو")
            ),
            nowMillis = azar2Ts
        ).first()
        assertEquals(true, match200k.isApproximateAutoMatch)
        assertEquals(992L, match200k.matchedCandidate?.id)

        // 4. Past-cycle commitment scoping
        assertEquals(true, OrosiCashflowAnalyzer.isCommitmentDueInCycle("۱۵ آبان ۱۴۰۵", 1, azar2Ts, -1))
        assertEquals(false, OrosiCashflowAnalyzer.isCommitmentDueInCycle("۲۰ آذر ۱۴۰۵", 1, azar2Ts, -1))
        assertEquals(false, OrosiCashflowAnalyzer.isCommitmentDueInCycle("۱ دی ۱۴۰۵", 1, azar2Ts, -1))

        // 5. Smart bank tag hint examples adapt to amount scale
        assertEquals(false, com.example.ui.components.smartBankTagExamples(1_951_673L, false).contains("پفک"))
        assertEquals(true, com.example.ui.components.smartBankTagExamples(1_951_673L, false).contains("قسط"))
        assertEquals(true, com.example.ui.components.smartBankTagExamples(15_000_000L, true).contains("حقوق"))
    }

    @Test
    fun testFullOrosiRegressionAndStressSuitePasses100Percent() {
        val report = com.example.domain.OrosiRegressionSuite.runFullRegressionAndStressSuite()
        for (r in report.results) {
            assertEquals(
                "Regression test failed [${r.title}]: expected=${r.expectedSummary}, actual=${r.actualSummary}",
                true,
                r.passed
            )
        }
        assertEquals(true, report.allPassed)
        assertEquals(com.example.domain.OrosiRegressionSuite.EXPECTED_TOTAL_CASES, report.totalCount)
        assertEquals(92, report.totalCount)
        assertEquals(92, report.passedCount)
    }
}




