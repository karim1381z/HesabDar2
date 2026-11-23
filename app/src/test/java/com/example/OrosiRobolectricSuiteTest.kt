package com.example

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppSettingsEntity
import com.example.data.local.BankAccountEntity
import com.example.data.local.CommitmentEntity
import com.example.data.local.OrosiDatabase
import com.example.data.local.TransactionEntity
import com.example.data.repository.OrosiFinanceRepository
import com.example.domain.JalaliCalendarUtils
import com.example.ui.CommitmentTab
import com.example.ui.OrosiScreen
import com.example.ui.OrosiViewModel
import com.example.ui.components.HeroMathBreakdownBottomSheet
import com.example.ui.screens.CommitmentsScreen
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h915dp-xxhdpi")
class OrosiRobolectricSuiteTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var app: Application
    private lateinit var db: OrosiDatabase
    private lateinit var repository: OrosiFinanceRepository

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(app, OrosiDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()
        repository = OrosiFinanceRepository(
            dao = db.orosiDao(),
            appContext = app
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun roomDaoAtomicSnapshotRestoreAndInvalidJsonRollback() = runBlocking {
        val dao = db.orosiDao()
        val ts = JalaliCalendarUtils.jalaliDayStartMillis(1405, 9, 1) + 10L * 3_600_000L
        val initialTx = TransactionEntity(
            id = 10L,
            title = "نانوایی",
            storeName = "نانوایی",
            purpose = "نانوایی",
            totalAmountToman = 50_000L,
            userShareToman = 50_000L,
            dongAmountToman = 0L,
            weekdayDate = JalaliCalendarUtils.fromTimestamp(ts).weekdayDate,
            timeStr = "۱۰:۰۰",
            cardInfo = "کارت بلو",
            type = "EXPENSE",
            isUntagged = false,
            timestamp = ts,
            bankName = "بلو"
        )
        dao.upsertBankAccount(BankAccountEntity(bankName = "بلو", balanceToman = 2_500_000L))
        dao.insertTransaction(initialTx)
        dao.saveSettings(AppSettingsEntity(id = 1, monthlyBalanceToman = 2_500_000L))

        // Invalid backup JSON MUST NOT wipe existing data (atomic parse-before-mutate invariant)
        val badImported = repository.importBackupJson("{ not valid json !!!")
        assertFalse(badImported)
        assertEquals(1, dao.getAllTransactionsNow().size)
        assertEquals(2_500_000L, dao.getAllBankAccountsNow().first().balanceToman)

        // Export valid snapshot, purge via atomic DAO method, then restore atomically
        val exported = repository.exportBackupJson()
        assertTrue(exported.contains("نانوایی"))

        dao.purgeForRealSmsRescanAtomic(emptyList())
        assertEquals(0, dao.getAllTransactionsNow().size)
        assertEquals(0, dao.getAllBankAccountsNow().size)

        val restored = repository.importBackupJson(exported)
        assertTrue(restored)
        assertEquals(1, dao.getAllTransactionsNow().size)
        assertEquals("نانوایی", dao.getAllTransactionsNow().first().title)
        assertEquals(2_500_000L, dao.getAllBankAccountsNow().first().balanceToman)
    }

    @Test
    fun roomMigrations6To7And7To8CreateExpectedSchemaAndIndexes() {
        val dbName = "orosi_migration_test.db"
        app.deleteDatabase(dbName)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(app)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(6) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS `app_settings` (
                                `id` INTEGER NOT NULL PRIMARY KEY,
                                `dailySafeBudgetToman` INTEGER NOT NULL,
                                `monthlyBalanceToman` INTEGER NOT NULL,
                                `paydayStartDay` INTEGER NOT NULL,
                                `paydayCycleLabel` TEXT NOT NULL,
                                `autoDetectSalary` INTEGER NOT NULL,
                                `goldPrice18kToman` INTEGER NOT NULL,
                                `goldReserveToman` INTEGER NOT NULL,
                                `goldGrams` REAL NOT NULL,
                                `dailyAutoSaveEnabled` INTEGER NOT NULL,
                                `hideAmounts` INTEGER NOT NULL,
                                `onlyTankhahMode` INTEGER NOT NULL,
                                `tankhahModeEnabled` INTEGER NOT NULL
                            )
                            """.trimIndent()
                        )
                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS `transactions` (
                                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                `title` TEXT NOT NULL,
                                `storeName` TEXT NOT NULL,
                                `purpose` TEXT NOT NULL,
                                `totalAmountToman` INTEGER NOT NULL,
                                `userShareToman` INTEGER NOT NULL,
                                `dongAmountToman` INTEGER NOT NULL,
                                `dongFriendName` TEXT NOT NULL,
                                `weekdayDate` TEXT NOT NULL,
                                `timeStr` TEXT NOT NULL,
                                `cardInfo` TEXT NOT NULL,
                                `type` TEXT NOT NULL,
                                `isUntagged` INTEGER NOT NULL,
                                `timestamp` INTEGER NOT NULL,
                                `balanceAfterToman` INTEGER,
                                `rawSmsBody` TEXT NOT NULL,
                                `bankName` TEXT NOT NULL
                            )
                            """.trimIndent()
                        )
                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS `commitments` (
                                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                `category` TEXT NOT NULL,
                                `title` TEXT NOT NULL,
                                `counterparty` TEXT NOT NULL,
                                `amountToman` INTEGER NOT NULL,
                                `dueDateStr` TEXT NOT NULL,
                                `sayyadId` TEXT NOT NULL,
                                `statusText` TEXT NOT NULL,
                                `isSettled` INTEGER NOT NULL,
                                `timestamp` INTEGER NOT NULL
                            )
                            """.trimIndent()
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        val sqliteDb = helper.writableDatabase
        OrosiDatabase.MIGRATION_6_7.migrate(sqliteDb)
        OrosiDatabase.MIGRATION_7_8.migrate(sqliteDb)

        // Verify selectedFontFamily column added in v6->v7
        sqliteDb.query("PRAGMA table_info(app_settings)").use { cursor ->
            val cols = mutableSetOf<String>()
            val nameIdx = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                cols.add(cursor.getString(nameIdx))
            }
            assertTrue("selectedFontFamily column should exist after MIGRATION_6_7", cols.contains("selectedFontFamily"))
        }

        // Verify both composite indexes created in v7->v8
        sqliteDb.query("SELECT name FROM sqlite_master WHERE type='index'").use { cursor ->
            val indexes = mutableSetOf<String>()
            while (cursor.moveToNext()) {
                indexes.add(cursor.getString(0))
            }
            assertTrue("idx_transactions_timestamp_id missing", indexes.contains("idx_transactions_timestamp_id"))
            assertTrue("idx_commitments_settled_timestamp_id missing", indexes.contains("idx_commitments_settled_timestamp_id"))
        }

        sqliteDb.close()
        app.deleteDatabase(dbName)
    }

    @Test
    fun viewModelTestConstructorIsolatesVoiceStateAndNavigatesScreens() {
        val vm = OrosiViewModel.createForTesting(app, db.orosiDao(), repository)
        composeTestRule.setContent {
            val state by vm.uiState.collectAsState()
            Text("${state.currentScreen.route}:${state.selectedCommitmentTab.name}")
        }
        composeTestRule.waitForIdle()

        vm.navigateTo(OrosiScreen.TOOLS)
        vm.selectCommitmentTab(CommitmentTab.REPORTS)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("tools:REPORTS").assertIsDisplayed()
        assertEquals(OrosiScreen.TOOLS, vm.uiState.value.currentScreen)
        assertEquals(CommitmentTab.REPORTS, vm.uiState.value.selectedCommitmentTab)

        // Isolated voiceState updates immediately without churning root uiState
        vm.openVoiceOverlayNew()
        vm.onVoiceSpeechChanged("من ۲۰۰ و خورده‌ای دریافت کردم از باسلام")
        assertEquals("باسلام", vm.voiceState.value.purposeCategory)
        assertEquals(200_000L, vm.voiceState.value.totalAmountToman)
        assertTrue(vm.voiceState.value.isIncome)
        assertTrue(vm.voiceState.value.isApproximateAmount)
        assertEquals("", vm.uiState.value.voiceState.recognizedText)
    }

    @Test
    fun heroMathBreakdownSheetScopesPastMonthsAndExpandsUnifiedDetailsCard() {
        val nowJalali = JalaliCalendarUtils.fromTimestamp(System.currentTimeMillis())
        val prevYm = JalaliCalendarUtils.shiftJalaliYearMonth(nowJalali.yearMonthKey, -1)
        val prevYear = prevYm / 100
        val prevMonth = prevYm % 100
        val prevMonthTs = JalaliCalendarUtils.jalaliDayStartMillis(prevYear, prevMonth, 10) + 10L * 3_600_000L
        val currMonthTs = JalaliCalendarUtils.jalaliDayStartMillis(nowJalali.year, nowJalali.month, 1) + 10L * 3_600_000L

        val sampleTxs = listOf(
            TransactionEntity(
                id = 1L,
                title = "حقوق",
                storeName = "حقوق",
                purpose = "حقوق",
                totalAmountToman = 12_000_000L,
                userShareToman = 12_000_000L,
                dongAmountToman = 0L,
                weekdayDate = JalaliCalendarUtils.fromTimestamp(currMonthTs).weekdayDate,
                timeStr = "۱۰:۰۰",
                cardInfo = "کارت بلو",
                type = "INCOME",
                isUntagged = false,
                timestamp = currMonthTs,
                bankName = "بلو"
            ),
            TransactionEntity(
                id = 2L,
                title = "خرید",
                storeName = "خرید",
                purpose = "خرید",
                totalAmountToman = 3_000_000L,
                userShareToman = 3_000_000L,
                dongAmountToman = 0L,
                weekdayDate = JalaliCalendarUtils.fromTimestamp(prevMonthTs).weekdayDate,
                timeStr = "۱۲:۰۰",
                cardInfo = "کارت بلو",
                type = "EXPENSE",
                isUntagged = false,
                timestamp = prevMonthTs,
                bankName = "بلو"
            )
        )
        val sampleCommitments = listOf(
            CommitmentEntity(
                id = 1L,
                category = "LOAN_INSTALLMENT",
                title = "قسط ماه جاری",
                counterparty = "بانک",
                amountToman = 25_000_000L,
                dueDateStr = JalaliCalendarUtils.fromTimestamp(currMonthTs).dateWithYear,
                statusText = "سررسید پیش‌رو",
                isSettled = false,
                timestamp = currMonthTs
            )
        )
        val syncedKeysCalls = mutableListOf<List<Int>>()

        composeTestRule.setContent {
            MyApplicationTheme {
                HeroMathBreakdownBottomSheet(
                    settings = AppSettingsEntity(
                        monthlyBalanceToman = 3_383_676L,
                        dailySafeBudgetToman = 120_000L,
                        paydayStartDay = 1
                    ),
                    transactions = sampleTxs,
                    reservedCommitmentsToman = 25_000_000L,
                    commitments = sampleCommitments,
                    onEnsureCycleMonthsSynced = { syncedKeysCalls.add(it) },
                    onDismiss = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        // Current cycle displays daily safe budget and triggers lazy sync hook
        composeTestRule.onNodeWithTag("hero_math_sheet").assertIsDisplayed()
        composeTestRule.onNodeWithText("سهم خرجِ روزانه پیشنهادی").assertIsDisplayed()
        assertTrue("onEnsureCycleMonthsSynced should fire automatically", syncedKeysCalls.isNotEmpty())

        // Expand unified "جزئیات بیشتر" card
        composeTestRule.onNodeWithTag("hero_math_more_details_toggle").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("hero_math_more_details_content").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("موجودی فعلی کل حساب‌ها").performScrollTo().assertIsDisplayed()

        // Navigate to previous cycle -> switches to historical daily average & hides today's live bank balance
        composeTestRule.onNodeWithTag("hero_math_prev_cycle_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("میانگین خرج روزانه این ماه").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("تعداد تراکنش‌های این دوره").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun commitmentsScreenComprehensiveReportsRendersChartAndAllTimeToggle() {
        val nowTs = System.currentTimeMillis()
        val nowWeekday = JalaliCalendarUtils.fromTimestamp(nowTs).weekdayDate
        val syncedMonthBatches = mutableListOf<List<Int>>()

        composeTestRule.setContent {
            MyApplicationTheme {
                CommitmentsScreen(
                    selectedTab = CommitmentTab.REPORTS,
                    onSelectTab = {},
                    commitments = emptyList(),
                    transactions = listOf(
                        TransactionEntity(
                            id = 1L,
                            title = "حقوق",
                            storeName = "حقوق",
                            purpose = "حقوق",
                            totalAmountToman = 10_000_000L,
                            userShareToman = 10_000_000L,
                            dongAmountToman = 0L,
                            weekdayDate = nowWeekday,
                            timeStr = "۰۹:۰۰",
                            cardInfo = "کارت بلو",
                            type = "INCOME",
                            isUntagged = false,
                            timestamp = nowTs,
                            bankName = "بلو"
                        ),
                        TransactionEntity(
                            id = 2L,
                            title = "سوپرمارکت",
                            storeName = "سوپرمارکت",
                            purpose = "سوپرمارکت",
                            totalAmountToman = 1_500_000L,
                            userShareToman = 1_500_000L,
                            dongAmountToman = 0L,
                            weekdayDate = nowWeekday,
                            timeStr = "۱۱:۰۰",
                            cardInfo = "کارت بلو",
                            type = "EXPENSE",
                            isUntagged = false,
                            timestamp = nowTs + 60_000L,
                            bankName = "بلو"
                        )
                    ),
                    hideAmounts = false,
                    onEnsureMonthsSynced = { syncedMonthBatches.add(it) },
                    onCommitmentClick = {},
                    onOpenAddCommitmentModal = {},
                    onBackToDashboard = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("reports_section").assertIsDisplayed()
        composeTestRule.onNodeWithTag("reports_6month_chart_card").performScrollTo().assertIsDisplayed()
        assertNotNull(syncedMonthBatches.firstOrNull())
        assertEquals(6, syncedMonthBatches.first().size)

        // Toggle to All-Time mode cleanly
        composeTestRule.onNodeWithTag("report_toggle_all_time").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("reports_section").assertIsDisplayed()
    }
}
