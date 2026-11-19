package com.example.data.repository

import android.content.Context
import android.net.Uri
import android.provider.Telephony
import com.example.data.local.AppSettingsEntity
import com.example.data.local.BankAccountEntity
import com.example.data.local.CommitmentEntity
import com.example.data.local.OrosiDao
import com.example.data.local.TransactionEntity
import com.example.domain.BankSmsParserEngine
import com.example.domain.JalaliCalendarUtils
import com.example.domain.JibemanSmsEngine
import com.example.domain.ParsedSmsResult
import com.example.domain.ParsedVoiceExpense
import com.example.domain.PersianNumberUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class OrosiFinanceRepository(
    private val dao: OrosiDao,
    private val appContext: Context? = null
) {

    companion object {
        const val PREFS_NAME = "orosi_offline_db_v15_jibeman"
        private val smsSyncMutex = Mutex()
        @Volatile
        private var lastCompletedScanEpochMs: Long = 0L

        private val demoStoreNames = setOf(
            "شرکت فناوری اُرسی",
            "سپر تورمی اُرسی",
            "کارت‌های خودی"
        )

        private val demoTitles = setOf(
            "برداشت کارتی نامشخص (پیامک بلو)",
            "برداشت کارتی نامشخص (پیامک سپه)",
            "واریز حقوق ماهانه",
            "اسنپ‌مارکت (خرید میوه و پروتئین)",
            "برداشت پایانه فروشگاهی (پیامک ملت)",
            "تبدیل خودکار مازاد به طلای ۱۸ عیار",
            "انتقال بین بانکی خودی (بلو به ملت)",
            "واریز حقوق آبان‌ماه (آغاز ماه حقوقی)"
        )

        private val demoCommitmentSayyadIds = setOf(
            "۴۰۳۸۱۲۹۴۰۱۸۲۷۳۶۴",
            "۴۰۳۹۵۵۱۸۲۷۳۶۴۹۱۰",
            "قرارداد ۹۹۸۲۱۴"
        )
    }

    val transactionsFlow: Flow<List<TransactionEntity>> = dao.observeTransactions()
    val bankAccountsFlow: Flow<List<BankAccountEntity>> = dao.observeBankAccounts()
    val commitmentsFlow: Flow<List<CommitmentEntity>> = dao.observeCommitments()
    val settingsFlow: Flow<AppSettingsEntity?> = dao.observeSettings()

    private fun getPrimaryPrefs() =
        appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private suspend fun persistSnapshotToSharedPrefs() {
        if (appContext == null) return
        try {
            val json = exportBackupJson()
            getPrimaryPrefs()?.edit()?.putString("backup_snapshot_json", json)?.apply()
        } catch (_: Exception) {
        }
    }

    private fun isBogusOrTestTransaction(tx: TransactionEntity): Boolean {
        if (tx.totalAmountToman <= 0L) return true
        if (tx.bankName == "بانک" || tx.bankName == "تراکنش صوتی") return true
        if (tx.storeName == "تراکنش صوتی" || tx.storeName == "بانک") return true
        if (tx.cardInfo == "کارت بانک" || tx.cardInfo == "کارت تراکنش صوتی") return true
        val combinedText = "${tx.title} ${tx.purpose} ${tx.storeName}"
        if (combinedText.contains("به تومن برای خرید خانه بزرگ") ||
            combinedText.contains("گوگولی") ||
            combinedText.contains("سلام علیکم") ||
            combinedText.contains("منفی خرج")
        ) {
            return true
        }
        return false
    }

    private fun isDemoTransaction(tx: TransactionEntity): Boolean {
        if (isBogusOrTestTransaction(tx)) return true
        if (tx.storeName in demoStoreNames || tx.title in demoTitles) return true
        if (tx.rawSmsBody.contains("برداشت شتابی ۱,۰۰۰,۰۰۰ ریال\nمانده: ۱۴۲,۵۰۰,۰۰۰ ریال") ||
            tx.rawSmsBody.contains("برداشت: ۴۹۵,۰۰۰ ریال\nمانده: ۹,۸۴۴,۳۳۰ ریال") ||
            tx.rawSmsBody.contains("واریز به حساب بلو مبلغ ۴۲۰,۰۰۰,۰۰۰ ریال")
        ) {
            return true
        }
        if ((tx.weekdayDate == "جمعه ۱ آبان" || tx.weekdayDate == "پنجشنبه ۳۰ مهر" || tx.weekdayDate == "چهارشنبه ۲۹ مهر" || tx.weekdayDate == "سه‌شنبه ۲۸ مهر") &&
            tx.totalAmountToman in setOf(350_000L, 420_000L, 195_000L, 1_200_000L, 5_000_000L, 42_000_000L, 165_000L, 280_000L)
        ) {
            return true
        }
        return false
    }

    private fun isDemoCommitment(c: CommitmentEntity): Boolean {
        if (c.sayyadId in demoCommitmentSayyadIds) return true
        if (c.counterparty.contains("محمدرضا کاظمی") ||
            c.counterparty.contains("شرکت پارس‌نگار") ||
            c.title == "دنگ شام رستوران سنتی نایب" ||
            c.title == "قسط وام تسهیلات بانک ملت"
        ) {
            return true
        }
        return false
    }

    private fun loadCustomSendersFromPrefs() {
        val prefs = getPrimaryPrefs() ?: return
        try {
            val raw = prefs.getString("custom_sender_bank_map_json", "{}") ?: "{}"
            val obj = JSONObject(raw)
            val keys = obj.keys()
            while (keys.hasNext()) {
                val sender = keys.next()
                val bankName = obj.optString(sender, "")
                if (sender.isNotBlank() && bankName.isNotBlank()) {
                    JibemanSmsEngine.registerCustomSenderForBank(sender, bankName)
                }
            }
        } catch (_: Exception) {
        }
    }

    fun saveCustomSendersForBank(bankName: String, senderAddresses: List<String>) {
        val prefs = getPrimaryPrefs() ?: return
        val cleanBank = bankName.trim()
        if (cleanBank.isEmpty() || senderAddresses.isEmpty()) return
        try {
            val raw = prefs.getString("custom_sender_bank_map_json", "{}") ?: "{}"
            val obj = JSONObject(raw)
            for (addr in senderAddresses) {
                val cleanAddr = addr.trim()
                if (cleanAddr.isNotEmpty()) {
                    obj.put(cleanAddr, cleanBank)
                    JibemanSmsEngine.registerCustomSenderForBank(cleanAddr, cleanBank)
                }
            }
            prefs.edit().putString("custom_sender_bank_map_json", obj.toString()).apply()
        } catch (_: Exception) {
        }
    }

    fun getCustomSendersForBank(bankName: String): List<String> {
        val prefs = getPrimaryPrefs() ?: return emptyList()
        val cleanBank = bankName.trim()
        if (cleanBank.isEmpty()) return emptyList()
        return try {
            val raw = prefs.getString("custom_sender_bank_map_json", "{}") ?: "{}"
            val obj = JSONObject(raw)
            val result = mutableListOf<String>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val sender = keys.next()
                val mappedBank = obj.optString(sender, "")
                if (mappedBank == cleanBank && sender.isNotBlank()) {
                    result.add(sender)
                }
            }
            result
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun removeCustomSendersForBank(senderAddresses: List<String>) {
        val prefs = getPrimaryPrefs() ?: return
        if (senderAddresses.isEmpty()) return
        try {
            val raw = prefs.getString("custom_sender_bank_map_json", "{}") ?: "{}"
            val obj = JSONObject(raw)
            for (addr in senderAddresses) {
                val cleanAddr = addr.trim()
                if (cleanAddr.isNotEmpty()) {
                    obj.remove(cleanAddr)
                    JibemanSmsEngine.unregisterCustomSender(cleanAddr)
                }
            }
            prefs.edit().putString("custom_sender_bank_map_json", obj.toString()).apply()
        } catch (_: Exception) {
        }
    }

    fun isJalaliMonthSynced(yearMonthKey: Int): Boolean {
        val prefs = getPrimaryPrefs() ?: return true
        val synced = prefs.getStringSet("synced_jalali_months_v1", emptySet()) ?: emptySet()
        return yearMonthKey.toString() in synced
    }

    private fun markJalaliMonthsSynced(prefs: android.content.SharedPreferences, monthKeys: Collection<Int>) {
        val existing = prefs.getStringSet("synced_jalali_months_v1", emptySet())?.toMutableSet()
            ?: mutableSetOf()
        var changed = false
        for (key in monthKeys) {
            if (existing.add(key.toString())) {
                changed = true
            }
        }
        if (changed) {
            prefs.edit().putStringSet("synced_jalali_months_v1", existing).apply()
        }
    }

    suspend fun ensureSeeded() {
        loadCustomSendersFromPrefs()
        val prefs = getPrimaryPrefs()
        if (appContext != null && prefs != null && !prefs.getBoolean("v15_jibeman_migrated", false)) {
            dao.clearAllTransactions()
            dao.clearAllBankAccounts()
            dao.getAllCommitmentsNow().filter { isDemoCommitment(it) }.forEach { demoC ->
                dao.deleteCommitmentById(demoC.id)
            }
            prefs.edit()
                .remove("backup_snapshot_json")
                .remove("jibeman_dedup_keys")
                .remove("card_balances_v4_json")
                .putBoolean("v15_jibeman_migrated", true)
                .apply()
        }

        val currentSettings = dao.getSettingsNow()
        val existingTx = dao.getAllTransactionsNow()

        if (currentSettings == null && existingTx.isEmpty() && appContext != null) {
            val savedJson = getPrimaryPrefs()?.getString("backup_snapshot_json", null)
            if (!savedJson.isNullOrBlank()) {
                val restored = importBackupJsonInternal(savedJson, persistToPrefs = false)
                if (restored) {
                    cleanupDemoAndDuplicatesIfRealSmsPresent()
                    recalculateRealSafeToSpend()
                    return
                }
            }
        }

        val nowJalali = JalaliCalendarUtils.timestampToJalali(System.currentTimeMillis())

        if (currentSettings == null) {
            dao.saveSettings(
                AppSettingsEntity(
                    id = 1,
                    dailySafeBudgetToman = 0L,
                    monthlyBalanceToman = 0L,
                    paydayStartDay = 1,
                    paydayCycleLabel = "۱ ${nowJalali.monthName} تا ۳۰ ${nowJalali.monthName} ${PersianNumberUtils.toPersianDigits(nowJalali.year.toString())}",
                    autoDetectSalary = true,
                    goldPrice18kToman = 4_360_000L,
                    goldReserveToman = 0L,
                    goldGrams = 0.0,
                    dailyAutoSaveEnabled = true,
                    hideAmounts = false,
                    onlyTankhahMode = true,
                    tankhahModeEnabled = false
                )
            )
        }

        // NEVER seed fake demo transactions, fake bank accounts, or fake commitments!
        val hadCleanupChanges = cleanupDemoAndDuplicatesIfRealSmsPresent()
        recalculateRealSafeToSpend()
        if (currentSettings == null || hadCleanupChanges) {
            persistSnapshotToSharedPrefs()
        }
    }

    private suspend fun cleanupDemoAndDuplicatesIfRealSmsPresent(): Boolean {
        var changed = false
        val prefs = getPrimaryPrefs()
        val allTxs = dao.getAllTransactionsNow()

        // Always purge any bogus, 0-Toman, or demo transactions so the ledger stays 100% clean
        val demoTxs = allTxs.filter { isDemoTransaction(it) }
        if (demoTxs.isNotEmpty()) {
            demoTxs.forEach { demoTx ->
                dao.deleteTransactionById(demoTx.id)
            }
            changed = true
        }

        val allAccounts = dao.getAllBankAccountsNow()
        if (allAccounts.any { it.bankName == "بانک" }) {
            dao.deleteBankAccount("بانک")
            changed = true
        }
        if (allAccounts.any { it.bankName == "تراکنش صوتی" }) {
            dao.deleteBankAccount("تراکنش صوتی")
            changed = true
        }
        if (isDemoBankSet(allAccounts)) {
            dao.clearAllBankAccounts()
            changed = true
        }

        val allCommitments = dao.getAllCommitmentsNow()
        val demoComms = allCommitments.filter { isDemoCommitment(it) }
        if (demoComms.isNotEmpty()) {
            demoComms.forEach { demoC ->
                dao.deleteCommitmentById(demoC.id)
            }
            changed = true
        }

        // Deduplicate any duplicate SMS transactions in Room DB (preferring user-tagged rows)
        val remainingTxs = if (changed) dao.getAllTransactionsNow() else allTxs
        val smsGroups = remainingTxs
            .filter { it.rawSmsBody.isNotBlank() }
            .groupBy { tx ->
                "${tx.bankName}|${tx.totalAmountToman}|${tx.type}|${tx.timestamp / 60_000L}|${tx.rawSmsBody.trim().hashCode()}"
            }
        for ((_, group) in smsGroups) {
            if (group.size > 1) {
                val keeper = group.sortedWith(
                    compareBy<TransactionEntity> { it.isUntagged }
                        .thenBy { it.id }
                ).first()
                group.filter { it.id != keeper.id }.forEach { dup ->
                    dao.deleteTransactionById(dup.id)
                    changed = true
                }
            }
        }

        // One-time normalization so only the primary spending bank ("بلو" or the first bank) defaults to isTankhah = true
        val normalizedV5 = prefs?.getBoolean("tankhah_default_normalized_v5", false) == true
        if (!normalizedV5) {
            val currentAccounts = dao.getAllBankAccountsNow()
            if (currentAccounts.size > 1 && currentAccounts.count { it.isTankhah } > 1) {
                val primaryBankName = currentAccounts.firstOrNull { it.bankName == "بلو" }?.bankName
                    ?: currentAccounts.first().bankName
                val normalizedList = currentAccounts.map { acc ->
                    acc.copy(isTankhah = acc.bankName == primaryBankName)
                }
                dao.upsertBankAccounts(normalizedList)
                changed = true
            }
            if (currentAccounts.isNotEmpty()) {
                prefs?.edit()?.putBoolean("tankhah_default_normalized_v5", true)?.apply()
            }
        }

        // One-time migration: mark existing untagged auto-purpose bank INCOME SMS rows as isUntagged = true
        // so the user can voice-tag deposits ("بگو بابت چی؟") just like withdrawals.
        val incomeMigrationDone = prefs?.getBoolean("untagged_income_migration_v1", false) == true
        if (!incomeMigrationDone) {
            val defaultAutoIncomePurposes = setOf(
                "",
                "افزایش موجودی حساب",
                "واریز به حساب",
                "واریز بانکی",
                "دریافت وجه",
                "واریز حقوق ماهانه"
            )
            val currentTxs = dao.getAllTransactionsNow()
            var migratedAny = false
            for (tx in currentTxs) {
                if (tx.type == "INCOME" &&
                    tx.rawSmsBody.isNotBlank() &&
                    !tx.isUntagged &&
                    tx.purpose.trim() in defaultAutoIncomePurposes
                ) {
                    dao.updateTransaction(tx.copy(isUntagged = true))
                    migratedAny = true
                    changed = true
                }
            }
            if (currentTxs.isNotEmpty() || migratedAny) {
                prefs?.edit()?.putBoolean("untagged_income_migration_v1", true)?.apply()
            }
        }
        return changed
    }

    private suspend fun upsertDiscoveredBankAccount(
        bankNameFa: String,
        bankNameEn: String,
        balanceToman: Long?,
        smsTimestamp: Long
    ) {
        val existing = dao.getBankAccountByName(bankNameFa)
        if (existing == null) {
            val allExisting = dao.getAllBankAccountsNow()
            val defaultTankhah = bankNameFa == "بلو" || allExisting.isEmpty()
            dao.upsertBankAccount(
                BankAccountEntity(
                    bankName = bankNameFa,
                    nameEn = bankNameEn,
                    balanceToman = balanceToman ?: 0L,
                    isTankhah = defaultTankhah,
                    cardLast4 = "",
                    lastUpdatedTimestamp = smsTimestamp,
                    isManual = false
                )
            )
        } else if (!existing.isManual) {
            val shouldUpdateBalance = balanceToman != null && smsTimestamp >= existing.lastUpdatedTimestamp
            val newBalance = if (shouldUpdateBalance) balanceToman!! else existing.balanceToman
            val newNameEn = bankNameEn.ifBlank { existing.nameEn }
            val newTs = maxOf(existing.lastUpdatedTimestamp, smsTimestamp)
            if (newBalance != existing.balanceToman || newNameEn != existing.nameEn || newTs != existing.lastUpdatedTimestamp) {
                dao.upsertBankAccount(
                    existing.copy(
                        nameEn = newNameEn,
                        balanceToman = newBalance,
                        lastUpdatedTimestamp = newTs
                    )
                )
            }
        }
    }

    fun getTotalExtractedCardBalanceToman(): Long {
        val prefs = getPrimaryPrefs() ?: return 0L
        return try {
            val rawJson = prefs.getString("card_balances_v4_json", "{}") ?: "{}"
            val obj = JSONObject(rawJson)
            var sum = 0L
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val item = obj.optJSONObject(k)
                if (item != null) {
                    sum += item.optLong("balanceToman", 0L).coerceAtLeast(0L)
                }
            }
            sum
        } catch (_: Exception) {
            0L
        }
    }

    private fun saveExtractedCardBalance(bankKey: String, balanceToman: Long, smsTimestamp: Long) {
        val prefs = getPrimaryPrefs() ?: return
        try {
            val rawJson = prefs.getString("card_balances_v4_json", "{}") ?: "{}"
            val obj = JSONObject(rawJson)
            val existingObj = obj.optJSONObject(bankKey)
            val prevTimestamp = existingObj?.optLong("ts", 0L) ?: 0L
            val prevBalance = existingObj?.optLong("balanceToman", -1L) ?: -1L
            if (smsTimestamp > prevTimestamp || (smsTimestamp == prevTimestamp && balanceToman != prevBalance)) {
                val entry = JSONObject().apply {
                    put("balanceToman", balanceToman)
                    put("ts", smsTimestamp)
                }
                obj.put(bankKey, entry)
                prefs.edit().putString("card_balances_v4_json", obj.toString()).apply()
            }
        } catch (_: Exception) {
        }
    }

    /**
     * Dynamically recalculates `monthlyBalanceToman` and `dailySafeBudgetToman`
     * from the user's actual bank accounts in Room (either all accounts or only Tankhah accounts),
     * minus unpaid commitments of this month.
     * Only writes to Room if values actually changed, preventing spurious UI recompositions during scroll.
     */
    suspend fun recalculateRealSafeToSpend() {
        val currentSettings = dao.getSettingsNow() ?: AppSettingsEntity()
        val allAccounts = dao.getAllBankAccountsNow()
        val allTxs = dao.getAllTransactionsNow()
        val allCommitments = dao.getAllCommitmentsNow()

        val activeAccounts = if (currentSettings.tankhahModeEnabled && currentSettings.onlyTankhahMode) {
            allAccounts.filter { it.isTankhah }
        } else {
            allAccounts
        }
        val accountsBalanceSum = activeAccounts.sumOf { it.balanceToman.coerceAtLeast(0L) }
        val totalAllAccountsSum = allAccounts.sumOf { it.balanceToman.coerceAtLeast(0L) }

        val nonDemoTxs = allTxs.filter { !isDemoTransaction(it) }
        val totalDeposits = nonDemoTxs.filter { it.type == "INCOME" }.sumOf { it.userShareToman }
        val totalExpenses = nonDemoTxs.filter { it.type == "EXPENSE" }.sumOf { it.userShareToman }

        val unpaidCommitments = allCommitments
            .filter { !it.isSettled && (it.category == "SAYYAD_ISSUED" || it.category == "LOAN_INSTALLMENT") }
            .sumOf { it.amountToman }

        val rawAccountBalanceToman: Long = when {
            allAccounts.isNotEmpty() -> accountsBalanceSum
            totalDeposits > totalExpenses -> totalDeposits - totalExpenses
            currentSettings.monthlyBalanceToman > 0L -> currentSettings.monthlyBalanceToman
            else -> 0L
        }

        val freeMonthlyBalance = (rawAccountBalanceToman - unpaidCommitments).coerceAtLeast(0L)

        val remainingDays = JalaliCalendarUtils
            .calculateRemainingDaysToPayday(currentSettings.paydayStartDay)
            .coerceIn(1, 30)

        val rawDailySafe = if (freeMonthlyBalance > 0L) {
            (freeMonthlyBalance / remainingDays.toLong()).coerceAtLeast(1_000L)
        } else {
            0L
        }

        val roundedDailySafe = PersianNumberUtils.roundDownToCleanThousands(rawDailySafe)
        val newMonthlyBalance = totalAllAccountsSum.coerceAtLeast(rawAccountBalanceToman)

        if (currentSettings.monthlyBalanceToman != newMonthlyBalance ||
            currentSettings.dailySafeBudgetToman != roundedDailySafe
        ) {
            dao.saveSettings(
                currentSettings.copy(
                    monthlyBalanceToman = newMonthlyBalance,
                    dailySafeBudgetToman = roundedDailySafe
                )
            )
        }
    }

    /**
     * Processes an incoming SMS from `BankSmsBroadcastReceiver` using `JibemanSmsEngine`:
     * 1. Check `val bankProfile = JibemanSmsEngine.resolveBank(senderAddress)`. If null, reject.
     * 2. Call `val parsed = JibemanSmsEngine.parse(body) ?: return null`.
     * 3. Convert Rial to Toman (`parsed.amount / 10L` and `parsed.balance?.div(10L)`).
     * 4. Set `isDeposit = (parsed.direction == "in")` and `bankName = bankProfile.nameFa`.
     * 5. Deduplicate using `JibemanSmsEngine.dedupKey(senderAddress, timestampMillis, body)`.
     */
    suspend fun processIncomingBankSms(
        rawSms: String,
        timestampMillis: Long = System.currentTimeMillis(),
        senderAddress: String = ""
    ): ParsedSmsResult? = smsSyncMutex.withLock {
        loadCustomSendersFromPrefs()
        val bankProfile = JibemanSmsEngine.resolveBank(senderAddress) ?: return null
        val bankName = bankProfile.nameFa

        // Only accept SMS if JibemanSmsEngine.parse(rawSms) succeeds (never create false-positive accounts on unparsed SMS)
        val parsed = JibemanSmsEngine.parse(rawSms) ?: return null

        val amountToman = parsed.amount / 10L
        if (amountToman <= 0L) return null

        val balanceToman = parsed.balance?.div(10L)
        val isDeposit = (parsed.direction == "in")
        val uniqueDedupKey = JibemanSmsEngine.dedupKey(senderAddress, timestampMillis, rawSms)

        val fullResult = BankSmsParserEngine.parseWithJibemanEngine(
            senderAddress = senderAddress,
            body = rawSms,
            timestampMillis = timestampMillis
        ) ?: return null

        val prefs = getPrimaryPrefs()
        val hadRealSmsBefore = prefs?.getBoolean("has_real_bank_sms", false) == true
        val importedKeys = prefs?.getStringSet("jibeman_dedup_keys", emptySet())?.toMutableSet()
            ?: mutableSetOf()

        if (!hadRealSmsBefore) {
            prefs?.edit()?.putBoolean("has_real_bank_sms", true)?.apply()
            cleanupDemoAndDuplicatesIfRealSmsPresent()
        }

        upsertDiscoveredBankAccount(bankName, bankProfile.nameEn, balanceToman, timestampMillis)
        if (balanceToman != null && balanceToman >= 0L) {
            saveExtractedCardBalance(bankName, balanceToman, timestampMillis)
        }

        if (importedKeys.contains(uniqueDedupKey)) {
            recalculateRealSafeToSpend()
            return fullResult
        }

        val txType = if (isDeposit) "INCOME" else "EXPENSE"
        val realJalali = JalaliCalendarUtils.timestampToJalali(timestampMillis)

        dao.insertTransaction(
            TransactionEntity(
                title = fullResult.detectedTitle,
                storeName = fullResult.detectedStore,
                purpose = fullResult.detectedPurpose,
                totalAmountToman = amountToman,
                userShareToman = amountToman,
                weekdayDate = realJalali.dateWithYear,
                timeStr = realJalali.timeStr,
                cardInfo = fullResult.cardLabel,
                type = txType,
                isUntagged = true,
                timestamp = timestampMillis,
                balanceAfterToman = balanceToman,
                rawSmsBody = rawSms,
                bankName = bankName
            )
        )

        importedKeys.add(uniqueDedupKey)
        prefs?.edit()?.putStringSet("jibeman_dedup_keys", importedKeys)?.apply()

        if (fullResult.isSalaryDeposit) {
            val currentSettings = dao.getSettingsNow() ?: AppSettingsEntity()
            dao.saveSettings(
                currentSettings.copy(
                    paydayCycleLabel = "${realJalali.dateWithYear} تا ۳۰ روز بعد (تشخیص خودکار حقوق)"
                )
            )
        }

        recalculateRealSafeToSpend()
        persistSnapshotToSharedPrefs()
        return fullResult
    }

    /**
     * Ultra-Fast 2-Query SMS Architecture on `Dispatchers.IO`:
     * 1) **Incremental Resume Mode** (normal app resumes after initial v2 pass):
     *    Queries ONLY `WHERE date > (lastMaxSmsTs - 60_000L)` -> ~0-5 rows, completes in ~15ms!
     * 2) **Full Initial / Forced Mode**:
     *    - Query A: `WHERE date >= recentTwoMonthsStartMs` (`_ID, ADDRESS, BODY, DATE`, `DATE DESC`)
     *      Imports recent 2 Jalali months & captures authoritative real balances for active banks.
     *    - Query B: Metadata-only (`_ID, ADDRESS, DATE` — NO `BODY`) `WHERE date < recentTwoMonthsStartMs`
     *      Uses O(1) cached `JibemanSmsEngine.resolveBank` to find up to 8 newest `_ID`s per dormant bank
     *      not already discovered with a real balance in Query A (e.g. Sepah).
     *    - Query C: Targeted `WHERE _id IN (...)` fetch of `BODY` ONLY for those few historical IDs!
     * 3) **Strict Balance Authority**:
     *    - Balances extracted from real parsed debit/credit transactions (`parsed.balance`) ALWAYS take
     *      strict priority over `extractBalanceRial` fallback balances (prevents Blu Box / promo SMS
     *      from ever overwriting main account balance).
     *    - Timestamp-monotone Room updates prevent older SMS from ever regressing a newer balance.
     */
    suspend fun scanDeviceSmsInboxLast30Days(
        context: Context,
        daysBack: Int = 3650,
        force: Boolean = false
    ): Int = withContext(Dispatchers.IO) {
        smsSyncMutex.withLock {
            val nowMs = System.currentTimeMillis()
            if (!force && (nowMs - lastCompletedScanEpochMs) < 3_500L) {
                return@withLock 0
            }
            loadCustomSendersFromPrefs()
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val hadRealSmsBefore = prefs.getBoolean("has_real_bank_sms", false)
            val cleanLedgerV15Done = prefs.getBoolean("jibeman_clean_ledger_v15", false)
            val fullInboxBalancePassV2Done = prefs.getBoolean("full_inbox_balance_pass_v2", false)
            val lastMaxSmsTs = prefs.getLong("last_max_sms_ts_v2", 0L)

            val nowJalali = JalaliCalendarUtils.fromTimestamp(nowMs)
            val currentMonthKey = nowJalali.yearMonthKey
            val prevMonthKey = JalaliCalendarUtils.shiftJalaliYearMonth(currentMonthKey, -1)
            val recentTwoMonthsStartMs = JalaliCalendarUtils
                .jalaliMonthEpochRangeMillis(prevMonthKey / 100, prevMonthKey % 100)
                .first

            val existingAllTxs = dao.getAllTransactionsNow()
            val existingAccountsBeforeScan = dao.getAllBankAccountsNow()
            val savedTankhahPrefs = existingAccountsBeforeScan
                .filter { !isDemoBankSet(existingAccountsBeforeScan) || hadRealSmsBefore }
                .associate { it.bankName to it.isTankhah }

            val savedCustomTagsBySmsKey = existingAllTxs
                .filter { !it.isUntagged && it.rawSmsBody.isNotBlank() && it.purpose.isNotBlank() && it.purpose != "نیازمند تکمیل صوتی" }
                .associateBy { "${it.bankName}|${it.totalAmountToman}|${it.timestamp}" }
            val savedManualCashTxs = existingAllTxs.filter {
                it.rawSmsBody.isBlank() &&
                    it.bankName == "نقدی / دستی" &&
                    !isBogusOrTestTransaction(it)
            }

            if (!cleanLedgerV15Done) {
                prefs.edit().remove("jibeman_dedup_keys").apply()
            }

            val importedKeys = prefs.getStringSet("jibeman_dedup_keys", emptySet())?.toMutableSet()
                ?: mutableSetOf()
            val initialImportedKeyCount = importedKeys.size

            val existingRoomFingerprints = if (cleanLedgerV15Done) {
                existingAllTxs
                    .filter { it.rawSmsBody.isNotBlank() }
                    .mapTo(mutableSetOf()) { "${it.bankName}|${it.totalAmountToman}|${it.timestamp}|${it.rawSmsBody.trim().hashCode()}" }
            } else {
                mutableSetOf()
            }

            val needFullPass = force ||
                !cleanLedgerV15Done ||
                !fullInboxBalancePassV2Done ||
                existingAccountsBeforeScan.isEmpty() ||
                lastMaxSmsTs <= 0L

            var importedCount = 0
            var foundAnyRealBankSms = false
            var wipedForV15Rebuild = false
            var anyAccountChanged = false
            var maxObservedSmsTs = lastMaxSmsTs

            data class DiscoveredBankState(
                val nameFa: String,
                val nameEn: String,
                var latestRealBalanceToman: Long? = null,
                var latestRealBalanceTimestamp: Long = 0L,
                var latestFallbackBalanceToman: Long? = null,
                var latestFallbackBalanceTimestamp: Long = 0L,
                var latestSeenTimestamp: Long = 0L
            ) {
                val effectiveBalanceToman: Long?
                    get() = latestRealBalanceToman ?: latestFallbackBalanceToman
                val effectiveBalanceTimestamp: Long
                    get() = if (latestRealBalanceToman != null) latestRealBalanceTimestamp else latestFallbackBalanceTimestamp
            }

            val discoveredBanks = linkedMapOf<String, DiscoveredBankState>()
            val pendingBatch = mutableListOf<TransactionEntity>()

            fun recordBankBalance(
                bankName: String,
                bankNameEn: String,
                timestampMillis: Long,
                realBalanceToman: Long?,
                fallbackBalanceToman: Long?
            ) {
                val bankState = discoveredBanks.getOrPut(bankName) {
                    DiscoveredBankState(
                        nameFa = bankName,
                        nameEn = bankNameEn,
                        latestSeenTimestamp = timestampMillis
                    )
                }
                if (timestampMillis > bankState.latestSeenTimestamp) {
                    bankState.latestSeenTimestamp = timestampMillis
                }
                if (realBalanceToman != null && realBalanceToman >= 0L) {
                    if (bankState.latestRealBalanceToman == null || timestampMillis >= bankState.latestRealBalanceTimestamp) {
                        bankState.latestRealBalanceToman = realBalanceToman
                        bankState.latestRealBalanceTimestamp = timestampMillis
                    }
                }
                if (fallbackBalanceToman != null && fallbackBalanceToman >= 0L) {
                    if (bankState.latestFallbackBalanceToman == null || timestampMillis >= bankState.latestFallbackBalanceTimestamp) {
                        bankState.latestFallbackBalanceToman = fallbackBalanceToman
                        bankState.latestFallbackBalanceTimestamp = timestampMillis
                    }
                }
            }

            suspend fun flushDiscoveredAccountsToRoom() {
                if (!foundAnyRealBankSms) return
                if (!hadRealSmsBefore || !cleanLedgerV15Done) {
                    prefs.edit()
                        .putBoolean("has_real_bank_sms", true)
                        .putBoolean("jibeman_clean_ledger_v15", true)
                        .apply()
                }

                val existingAccountsByName = dao.getAllBankAccountsNow().associateBy { it.bankName }
                val primaryDefaultBank = if (discoveredBanks.containsKey("بلو")) {
                    "بلو"
                } else {
                    discoveredBanks.keys.firstOrNull().orEmpty()
                }
                val changedAccountsToUpsert = mutableListOf<BankAccountEntity>()
                for (st in discoveredBanks.values) {
                    val prev = existingAccountsByName[st.nameFa]
                    val resolvedTankhah = savedTankhahPrefs[st.nameFa]
                        ?: prev?.isTankhah
                        ?: (st.nameFa == primaryDefaultBank)

                    val effectiveBal = st.effectiveBalanceToman
                    val effectiveTs = st.effectiveBalanceTimestamp
                    if (effectiveBal != null && effectiveBal >= 0L) {
                        saveExtractedCardBalance(st.nameFa, effectiveBal, effectiveTs)
                    }

                    val isManualOverrideNewer = prev != null &&
                        prev.isManual &&
                        prev.balanceToman > 0L &&
                        prev.lastUpdatedTimestamp > effectiveTs
                    val canOverwritePrevBalance = prev == null ||
                        force ||
                        !fullInboxBalancePassV2Done ||
                        effectiveTs >= prev.lastUpdatedTimestamp

                    val resolvedBalance = when {
                        isManualOverrideNewer -> prev!!.balanceToman
                        effectiveBal != null && canOverwritePrevBalance -> effectiveBal
                        else -> prev?.balanceToman ?: 0L
                    }
                    val resolvedTs = if (force || !fullInboxBalancePassV2Done) {
                        maxOf(effectiveTs, st.latestSeenTimestamp)
                    } else {
                        maxOf(effectiveTs, st.latestSeenTimestamp, prev?.lastUpdatedTimestamp ?: 0L)
                    }
                    val resolvedIsManual = isManualOverrideNewer
                    val candidate = BankAccountEntity(
                        bankName = st.nameFa,
                        nameEn = st.nameEn,
                        balanceToman = resolvedBalance,
                        isTankhah = resolvedTankhah,
                        cardLast4 = prev?.cardLast4 ?: "",
                        lastUpdatedTimestamp = resolvedTs,
                        isManual = resolvedIsManual
                    )
                    if (prev == null ||
                        prev.balanceToman != candidate.balanceToman ||
                        prev.isTankhah != candidate.isTankhah ||
                        prev.nameEn != candidate.nameEn ||
                        prev.isManual != candidate.isManual ||
                        prev.lastUpdatedTimestamp != candidate.lastUpdatedTimestamp
                    ) {
                        changedAccountsToUpsert.add(candidate)
                    }
                }
                if (changedAccountsToUpsert.isNotEmpty()) {
                    dao.upsertBankAccounts(changedAccountsToUpsert)
                    anyAccountChanged = true
                    recalculateRealSafeToSpend()
                }
            }

            fun openInboxCursor(
                projection: Array<String>,
                selection: String?,
                selectionArgs: Array<String>?,
                sortOrder: String
            ) = context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            ) ?: context.contentResolver.query(
                Uri.parse("content://sms/inbox"),
                projection,
                selection,
                selectionArgs,
                sortOrder
            )

            suspend fun processFullSmsRow(
                senderAddress: String,
                body: String,
                timestampMillis: Long,
                importTransactionRow: Boolean
            ) {
                if (timestampMillis > maxObservedSmsTs) {
                    maxObservedSmsTs = timestampMillis
                }
                val bankProfile = JibemanSmsEngine.resolveBank(senderAddress) ?: return
                if (body.isBlank()) return

                val parsed = JibemanSmsEngine.parse(body)
                val fallbackBalanceRial = if (parsed == null) {
                    JibemanSmsEngine.extractBalanceRial(body)
                } else {
                    null
                }
                if (parsed == null && fallbackBalanceRial == null) return

                val realBalanceToman = parsed?.balance?.div(10L)
                val fallbackBalanceToman = fallbackBalanceRial?.div(10L)
                val bankName = bankProfile.nameFa
                foundAnyRealBankSms = true

                if (!cleanLedgerV15Done && !wipedForV15Rebuild) {
                    dao.clearAllTransactions()
                    dao.clearAllBankAccounts()
                    if (savedManualCashTxs.isNotEmpty()) {
                        dao.insertAllTransactions(savedManualCashTxs)
                    }
                    wipedForV15Rebuild = true
                }

                recordBankBalance(
                    bankName = bankName,
                    bankNameEn = bankProfile.nameEn,
                    timestampMillis = timestampMillis,
                    realBalanceToman = realBalanceToman,
                    fallbackBalanceToman = fallbackBalanceToman
                )

                if (importTransactionRow && parsed != null) {
                    val amountToman = parsed.amount / 10L
                    if (amountToman > 0L) {
                        val uniqueDedupKey = JibemanSmsEngine.dedupKey(senderAddress, timestampMillis, body)
                        val isDeposit = (parsed.direction == "in")
                        val roomFingerprint = "$bankName|$amountToman|$timestampMillis|${body.trim().hashCode()}"

                        if (!importedKeys.contains(uniqueDedupKey) && !existingRoomFingerprints.contains(roomFingerprint)) {
                            val fullResult = BankSmsParserEngine.parseWithJibemanEngine(
                                senderAddress = senderAddress,
                                body = body,
                                timestampMillis = timestampMillis
                            )
                            if (fullResult != null) {
                                val realJalali = JalaliCalendarUtils.timestampToJalali(timestampMillis)
                                val txType = if (isDeposit) "INCOME" else "EXPENSE"
                                val prevTagged = savedCustomTagsBySmsKey["$bankName|$amountToman|$timestampMillis"]

                                pendingBatch.add(
                                    TransactionEntity(
                                        title = prevTagged?.title ?: fullResult.detectedTitle,
                                        storeName = bankName,
                                        purpose = prevTagged?.purpose ?: fullResult.detectedPurpose,
                                        totalAmountToman = amountToman,
                                        userShareToman = prevTagged?.userShareToman ?: amountToman,
                                        dongAmountToman = prevTagged?.dongAmountToman ?: 0L,
                                        dongFriendName = prevTagged?.dongFriendName ?: "",
                                        weekdayDate = realJalali.dateWithYear,
                                        timeStr = realJalali.timeStr,
                                        cardInfo = fullResult.cardLabel,
                                        type = txType,
                                        isUntagged = prevTagged?.isUntagged ?: true,
                                        timestamp = timestampMillis,
                                        balanceAfterToman = realBalanceToman,
                                        rawSmsBody = body,
                                        bankName = bankName
                                    )
                                )
                                existingRoomFingerprints.add(roomFingerprint)
                                importedCount++
                            }
                        }
                        importedKeys.add(uniqueDedupKey)
                    }
                }
            }

            try {
                val fullProjection = arrayOf(
                    Telephony.Sms._ID,
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE
                )
                val sortOrder = "${Telephony.Sms.DATE} DESC"

                if (!needFullPass) {
                    // FAST INCREMENTAL RESUME PATH (~15ms): Query ONLY SMS newer than last sync!
                    val sinceTs = (lastMaxSmsTs - 60_000L).coerceAtLeast(0L)
                    openInboxCursor(
                        projection = fullProjection,
                        selection = "${Telephony.Sms.DATE} > ?",
                        selectionArgs = arrayOf(sinceTs.toString()),
                        sortOrder = sortOrder
                    )?.use { c ->
                        val addrIdx = c.getColumnIndex(Telephony.Sms.ADDRESS)
                        val bodyIdx = c.getColumnIndex(Telephony.Sms.BODY)
                        val dateIdx = c.getColumnIndex(Telephony.Sms.DATE)
                        while (c.moveToNext()) {
                            val senderAddress = if (addrIdx >= 0) c.getString(addrIdx).orEmpty().trim() else ""
                            val timestampMillis = if (dateIdx >= 0) c.getLong(dateIdx) else continue
                            if (timestampMillis > maxObservedSmsTs) maxObservedSmsTs = timestampMillis
                            if (senderAddress.isBlank() || JibemanSmsEngine.resolveBank(senderAddress) == null) continue
                            val body = if (bodyIdx >= 0) c.getString(bodyIdx).orEmpty().trim() else ""
                            processFullSmsRow(
                                senderAddress = senderAddress,
                                body = body,
                                timestampMillis = timestampMillis,
                                importTransactionRow = timestampMillis >= recentTwoMonthsStartMs
                            )
                        }
                    }

                    if (pendingBatch.isNotEmpty()) {
                        dao.insertAllTransactions(pendingBatch)
                    }
                    flushDiscoveredAccountsToRoom()
                } else {
                    // FULL INITIAL / V2 REPAIR PASS (3 targeted SQL queries, < 1 second total!)
                    // Query A: Recent 2 Jalali months with BODY (imports transactions + captures active bank balances)
                    openInboxCursor(
                        projection = fullProjection,
                        selection = "${Telephony.Sms.DATE} >= ?",
                        selectionArgs = arrayOf(recentTwoMonthsStartMs.toString()),
                        sortOrder = sortOrder
                    )?.use { c ->
                        val addrIdx = c.getColumnIndex(Telephony.Sms.ADDRESS)
                        val bodyIdx = c.getColumnIndex(Telephony.Sms.BODY)
                        val dateIdx = c.getColumnIndex(Telephony.Sms.DATE)
                        while (c.moveToNext()) {
                            val senderAddress = if (addrIdx >= 0) c.getString(addrIdx).orEmpty().trim() else ""
                            val timestampMillis = if (dateIdx >= 0) c.getLong(dateIdx) else continue
                            if (timestampMillis > maxObservedSmsTs) maxObservedSmsTs = timestampMillis
                            if (senderAddress.isBlank() || JibemanSmsEngine.resolveBank(senderAddress) == null) continue
                            val body = if (bodyIdx >= 0) c.getString(bodyIdx).orEmpty().trim() else ""
                            processFullSmsRow(
                                senderAddress = senderAddress,
                                body = body,
                                timestampMillis = timestampMillis,
                                importTransactionRow = true
                            )
                        }
                    }

                    // Flush recent 2 months & active banks immediately so UI renders in ~200ms!
                    if (pendingBatch.isNotEmpty()) {
                        dao.insertAllTransactions(pendingBatch)
                        pendingBatch.clear()
                    }
                    flushDiscoveredAccountsToRoom()

                    // Query B: Metadata-only historical balance discovery (NO BODY column -> tiny cursor, O(1) resolveBank!)
                    val metaProjection = arrayOf(
                        Telephony.Sms._ID,
                        Telephony.Sms.ADDRESS,
                        Telephony.Sms.DATE
                    )
                    val historicalCandidateIdsByBank = linkedMapOf<String, MutableList<Long>>()
                    openInboxCursor(
                        projection = metaProjection,
                        selection = "${Telephony.Sms.DATE} < ?",
                        selectionArgs = arrayOf(recentTwoMonthsStartMs.toString()),
                        sortOrder = sortOrder
                    )?.use { c ->
                        val idIdx = c.getColumnIndex(Telephony.Sms._ID)
                        val addrIdx = c.getColumnIndex(Telephony.Sms.ADDRESS)
                        while (c.moveToNext()) {
                            val senderAddress = if (addrIdx >= 0) c.getString(addrIdx).orEmpty().trim() else ""
                            if (senderAddress.isBlank()) continue
                            val profile = JibemanSmsEngine.resolveBank(senderAddress) ?: continue
                            val bankName = profile.nameFa
                            // Skip banks whose real balance was already captured in Query A!
                            if (discoveredBanks[bankName]?.latestRealBalanceToman != null) continue
                            val bucket = historicalCandidateIdsByBank.getOrPut(bankName) { mutableListOf() }
                            if (bucket.size < 8 && idIdx >= 0) {
                                bucket.add(c.getLong(idIdx))
                            }
                        }
                    }

                    // Query C: Fetch BODY ONLY for the few collected historical bank SMS IDs (~5-30 rows max!)
                    val allHistoricalIds = historicalCandidateIdsByBank.values.flatten()
                    if (allHistoricalIds.isNotEmpty()) {
                        for (chunk in allHistoricalIds.chunked(200)) {
                            val idListSql = chunk.joinToString(",")
                            openInboxCursor(
                                projection = fullProjection,
                                selection = "${Telephony.Sms._ID} IN ($idListSql)",
                                selectionArgs = null,
                                sortOrder = sortOrder
                            )?.use { c ->
                                val addrIdx = c.getColumnIndex(Telephony.Sms.ADDRESS)
                                val bodyIdx = c.getColumnIndex(Telephony.Sms.BODY)
                                val dateIdx = c.getColumnIndex(Telephony.Sms.DATE)
                                while (c.moveToNext()) {
                                    val senderAddress = if (addrIdx >= 0) c.getString(addrIdx).orEmpty().trim() else ""
                                    if (senderAddress.isBlank()) continue
                                    val timestampMillis = if (dateIdx >= 0) c.getLong(dateIdx) else continue
                                    val body = if (bodyIdx >= 0) c.getString(bodyIdx).orEmpty().trim() else ""
                                    processFullSmsRow(
                                        senderAddress = senderAddress,
                                        body = body,
                                        timestampMillis = timestampMillis,
                                        importTransactionRow = false
                                    )
                                }
                            }
                        }
                        flushDiscoveredAccountsToRoom()
                    }

                    markJalaliMonthsSynced(prefs, listOf(currentMonthKey, prevMonthKey))
                    prefs.edit()
                        .putBoolean("full_inbox_balance_pass_v1", true)
                        .putBoolean("full_inbox_balance_pass_v2", true)
                        .apply()
                }

                if (maxObservedSmsTs > lastMaxSmsTs) {
                    prefs.edit().putLong("last_max_sms_ts_v2", maxObservedSmsTs).apply()
                }
            } catch (_: SecurityException) {
                // Permission not yet granted
            } catch (_: Exception) {
            }

            // Guarantee bank accounts are never left empty if transactions exist in Room
            rebuildBankAccountsFromTransactionsIfEmpty(savedTankhahPrefs)

            if (importedKeys.size != initialImportedKeyCount || !cleanLedgerV15Done) {
                prefs.edit().putStringSet("jibeman_dedup_keys", importedKeys).apply()
            }
            if (importedCount > 0 || anyAccountChanged || wipedForV15Rebuild) {
                recalculateRealSafeToSpend()
                persistSnapshotToSharedPrefs()
            }
            lastCompletedScanEpochMs = System.currentTimeMillis()
            importedCount
        }
    }

    /**
     * On-Demand Lazy Month Sync: Queries ONLY SMS messages within the exact Jalali month's
     * `[startInclusiveMillis, endExclusiveMillis)` epoch window, imports them into Room,
     * and caches the month key in SharedPreferences so subsequent visits are instant.
     */
    suspend fun syncTransactionsForJalaliMonth(
        context: Context,
        yearMonthKey: Int,
        force: Boolean = false
    ): Int = withContext(Dispatchers.IO) {
        smsSyncMutex.withLock {
            loadCustomSendersFromPrefs()
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (!force && isJalaliMonthSynced(yearMonthKey)) {
                return@withLock 0
            }

            val year = yearMonthKey / 100
            val month = (yearMonthKey % 100).coerceIn(1, 12)
            val (startMs, endMs) = JalaliCalendarUtils.jalaliMonthEpochRangeMillis(year, month)

            val existingAllTxs = dao.getAllTransactionsNow()
            val existingRoomFingerprints = existingAllTxs
                .filter { it.rawSmsBody.isNotBlank() }
                .mapTo(mutableSetOf()) { "${it.bankName}|${it.totalAmountToman}|${it.timestamp}|${it.rawSmsBody.trim().hashCode()}" }
            val importedKeys = prefs.getStringSet("jibeman_dedup_keys", emptySet())?.toMutableSet()
                ?: mutableSetOf()
            val initialKeyCount = importedKeys.size

            val monthBatch = mutableListOf<TransactionEntity>()
            var importedCount = 0

            try {
                val projection = arrayOf(
                    Telephony.Sms._ID,
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE
                )
                val selection = "${Telephony.Sms.DATE} >= ? AND ${Telephony.Sms.DATE} < ?"
                val selectionArgs = arrayOf(startMs.toString(), endMs.toString())
                val sortOrder = "${Telephony.Sms.DATE} DESC"

                val cursor = context.contentResolver.query(
                    Telephony.Sms.Inbox.CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    sortOrder
                ) ?: context.contentResolver.query(
                    Uri.parse("content://sms/inbox"),
                    projection,
                    selection,
                    selectionArgs,
                    sortOrder
                )

                cursor?.use { c ->
                    val addrIdx = c.getColumnIndex(Telephony.Sms.ADDRESS)
                    val bodyIdx = c.getColumnIndex(Telephony.Sms.BODY)
                    val dateIdx = c.getColumnIndex(Telephony.Sms.DATE)

                    while (c.moveToNext()) {
                        val senderAddress = if (addrIdx >= 0) c.getString(addrIdx).orEmpty().trim() else ""
                        if (senderAddress.isBlank()) continue
                        val bankProfile = JibemanSmsEngine.resolveBank(senderAddress) ?: continue

                        val body = if (bodyIdx >= 0) c.getString(bodyIdx).orEmpty().trim() else ""
                        if (body.isBlank()) continue

                        val timestampMillis = if (dateIdx >= 0) c.getLong(dateIdx) else continue
                        val uniqueDedupKey = JibemanSmsEngine.dedupKey(senderAddress, timestampMillis, body)

                        val parsed = JibemanSmsEngine.parse(body) ?: continue
                        val amountToman = parsed.amount / 10L
                        if (amountToman <= 0L) continue
                        val balanceToman = parsed.balance?.div(10L)
                        val bankName = bankProfile.nameFa
                        val isDeposit = (parsed.direction == "in")
                        val roomFingerprint = "$bankName|$amountToman|$timestampMillis|${body.trim().hashCode()}"

                        if (!importedKeys.contains(uniqueDedupKey) && !existingRoomFingerprints.contains(roomFingerprint)) {
                            val fullResult = BankSmsParserEngine.parseWithJibemanEngine(
                                senderAddress = senderAddress,
                                body = body,
                                timestampMillis = timestampMillis
                            )
                            if (fullResult != null) {
                                val realJalali = JalaliCalendarUtils.timestampToJalali(timestampMillis)
                                monthBatch.add(
                                    TransactionEntity(
                                        title = fullResult.detectedTitle,
                                        storeName = bankName,
                                        purpose = fullResult.detectedPurpose,
                                        totalAmountToman = amountToman,
                                        userShareToman = amountToman,
                                        weekdayDate = realJalali.dateWithYear,
                                        timeStr = realJalali.timeStr,
                                        cardInfo = fullResult.cardLabel,
                                        type = if (isDeposit) "INCOME" else "EXPENSE",
                                        isUntagged = true,
                                        timestamp = timestampMillis,
                                        balanceAfterToman = balanceToman,
                                        rawSmsBody = body,
                                        bankName = bankName
                                    )
                                )
                                existingRoomFingerprints.add(roomFingerprint)
                                importedCount++
                            }
                        }
                        importedKeys.add(uniqueDedupKey)
                    }
                }

                if (monthBatch.isNotEmpty()) {
                    dao.insertAllTransactions(monthBatch)
                }
                markJalaliMonthsSynced(prefs, listOf(yearMonthKey))
                if (importedKeys.size != initialKeyCount) {
                    prefs.edit().putStringSet("jibeman_dedup_keys", importedKeys).apply()
                }
                if (importedCount > 0) {
                    recalculateRealSafeToSpend()
                    persistSnapshotToSharedPrefs()
                }
            } catch (_: SecurityException) {
            } catch (_: Exception) {
            }
            importedCount
        }
    }

    /**
     * Scans the entire SMS inbox specifically for [targetBankName] (including any newly linked
     * custom senders) to extract its latest balance (even from balance-only SMS messages) and
     * import any recent transactions for that bank. Returns the discovered balance or `null`.
     */
    suspend fun rescanSingleBankFromSms(
        context: Context,
        targetBankName: String
    ): Long? = withContext(Dispatchers.IO) {
        smsSyncMutex.withLock {
            loadCustomSendersFromPrefs()
            val cleanBank = targetBankName.trim()
            if (cleanBank.isEmpty()) return@withLock null
            val cleanNorm = cleanBank.replace("\u200C", " ")
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val nowJalali = JalaliCalendarUtils.fromTimestamp(System.currentTimeMillis())
            val prevMonthKey = JalaliCalendarUtils.shiftJalaliYearMonth(nowJalali.yearMonthKey, -1)
            val recentTwoMonthsStartMs = JalaliCalendarUtils
                .jalaliMonthEpochRangeMillis(prevMonthKey / 100, prevMonthKey % 100)
                .first

            val existingAllTxs = dao.getAllTransactionsNow()
            val existingRoomFingerprints = existingAllTxs
                .filter { it.rawSmsBody.isNotBlank() }
                .mapTo(mutableSetOf()) { "${it.bankName}|${it.totalAmountToman}|${it.timestamp}|${it.rawSmsBody.trim().hashCode()}" }
            val importedKeys = prefs.getStringSet("jibeman_dedup_keys", emptySet())?.toMutableSet()
                ?: mutableSetOf()

            var latestRealBalanceToman: Long? = null
            var latestRealBalanceTs = 0L
            var latestFallbackBalanceToman: Long? = null
            var latestFallbackBalanceTs = 0L
            var matchedNameEn = ""
            val newTxs = mutableListOf<TransactionEntity>()

            try {
                val metaProjection = arrayOf(
                    Telephony.Sms._ID,
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.DATE
                )
                val fullProjection = arrayOf(
                    Telephony.Sms._ID,
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE
                )
                val sortOrder = "${Telephony.Sms.DATE} DESC"

                fun openInboxCursor(
                    projection: Array<String>,
                    selection: String?,
                    selectionArgs: Array<String>?
                ) = context.contentResolver.query(
                    Telephony.Sms.Inbox.CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    sortOrder
                ) ?: context.contentResolver.query(
                    Uri.parse("content://sms/inbox"),
                    projection,
                    selection,
                    selectionArgs,
                    sortOrder
                )

                // Step 1: Fast metadata-only scan (NO BODY!) to gather matching SMS IDs for this bank
                val targetIds = mutableListOf<Long>()
                var historicalCollected = 0
                openInboxCursor(metaProjection, null, null)?.use { c ->
                    val idIdx = c.getColumnIndex(Telephony.Sms._ID)
                    val addrIdx = c.getColumnIndex(Telephony.Sms.ADDRESS)
                    val dateIdx = c.getColumnIndex(Telephony.Sms.DATE)
                    while (c.moveToNext()) {
                        val senderAddress = if (addrIdx >= 0) c.getString(addrIdx).orEmpty().trim() else ""
                        if (senderAddress.isBlank()) continue
                        val profile = JibemanSmsEngine.resolveBank(senderAddress) ?: continue
                        val profileNorm = profile.nameFa.replace("\u200C", " ")
                        if (profile.nameFa != cleanBank && profileNorm != cleanNorm) continue
                        if (matchedNameEn.isBlank()) matchedNameEn = profile.nameEn

                        val timestampMillis = if (dateIdx >= 0) c.getLong(dateIdx) else System.currentTimeMillis()
                        if (idIdx >= 0) {
                            if (timestampMillis >= recentTwoMonthsStartMs) {
                                targetIds.add(c.getLong(idIdx))
                            } else if (historicalCollected < 15) {
                                targetIds.add(c.getLong(idIdx))
                                historicalCollected++
                            } else {
                                break
                            }
                        }
                    }
                }

                // Step 2: Fetch BODY only for matching SMS IDs
                for (chunk in targetIds.chunked(200)) {
                    val idListSql = chunk.joinToString(",")
                    openInboxCursor(fullProjection, "${Telephony.Sms._ID} IN ($idListSql)", null)?.use { c ->
                        val addrIdx = c.getColumnIndex(Telephony.Sms.ADDRESS)
                        val bodyIdx = c.getColumnIndex(Telephony.Sms.BODY)
                        val dateIdx = c.getColumnIndex(Telephony.Sms.DATE)

                        while (c.moveToNext()) {
                            val senderAddress = if (addrIdx >= 0) c.getString(addrIdx).orEmpty().trim() else ""
                            if (senderAddress.isBlank()) continue
                            val timestampMillis = if (dateIdx >= 0) c.getLong(dateIdx) else System.currentTimeMillis()
                            val body = if (bodyIdx >= 0) c.getString(bodyIdx).orEmpty().trim() else ""
                            if (body.isBlank()) continue

                            val parsed = JibemanSmsEngine.parse(body)
                            val realBalRial = parsed?.balance
                            val fallbackBalRial = if (parsed == null) JibemanSmsEngine.extractBalanceRial(body) else null

                            if (realBalRial != null && (latestRealBalanceToman == null || timestampMillis >= latestRealBalanceTs)) {
                                latestRealBalanceToman = realBalRial / 10L
                                latestRealBalanceTs = timestampMillis
                            }
                            if (fallbackBalRial != null && (latestFallbackBalanceToman == null || timestampMillis >= latestFallbackBalanceTs)) {
                                latestFallbackBalanceToman = fallbackBalRial / 10L
                                latestFallbackBalanceTs = timestampMillis
                            }

                            if (timestampMillis >= recentTwoMonthsStartMs && parsed != null) {
                                val amountToman = parsed.amount / 10L
                                if (amountToman > 0L) {
                                    val uniqueDedupKey = JibemanSmsEngine.dedupKey(senderAddress, timestampMillis, body)
                                    val roomFingerprint = "$cleanBank|$amountToman|$timestampMillis|${body.trim().hashCode()}"
                                    if (!importedKeys.contains(uniqueDedupKey) && !existingRoomFingerprints.contains(roomFingerprint)) {
                                        val fullResult = BankSmsParserEngine.parseWithJibemanEngine(
                                            senderAddress = senderAddress,
                                            body = body,
                                            timestampMillis = timestampMillis
                                        )
                                        if (fullResult != null) {
                                            val realJalali = JalaliCalendarUtils.timestampToJalali(timestampMillis)
                                            val isDeposit = parsed.direction == "in"
                                            newTxs.add(
                                                TransactionEntity(
                                                    title = fullResult.detectedTitle,
                                                    storeName = cleanBank,
                                                    purpose = fullResult.detectedPurpose,
                                                    totalAmountToman = amountToman,
                                                    userShareToman = amountToman,
                                                    weekdayDate = realJalali.dateWithYear,
                                                    timeStr = realJalali.timeStr,
                                                    cardInfo = fullResult.cardLabel,
                                                    type = if (isDeposit) "INCOME" else "EXPENSE",
                                                    isUntagged = true,
                                                    timestamp = timestampMillis,
                                                    balanceAfterToman = realBalRial?.div(10L),
                                                    rawSmsBody = body,
                                                    bankName = cleanBank
                                                )
                                            )
                                            existingRoomFingerprints.add(roomFingerprint)
                                        }
                                    }
                                    importedKeys.add(uniqueDedupKey)
                                }
                            }
                        }
                    }
                }

                val effectiveBalance = latestRealBalanceToman ?: latestFallbackBalanceToman
                val effectiveBalanceTs = if (latestRealBalanceToman != null) latestRealBalanceTs else latestFallbackBalanceTs

                if (newTxs.isNotEmpty()) {
                    dao.insertAllTransactions(newTxs)
                    prefs.edit().putStringSet("jibeman_dedup_keys", importedKeys).apply()
                }
                if (effectiveBalance != null) {
                    val existing = dao.getBankAccountByName(cleanBank)
                    dao.upsertBankAccount(
                        BankAccountEntity(
                            bankName = cleanBank,
                            nameEn = matchedNameEn.ifBlank { existing?.nameEn ?: cleanBank },
                            balanceToman = effectiveBalance,
                            isTankhah = existing?.isTankhah ?: false,
                            cardLast4 = existing?.cardLast4 ?: "",
                            lastUpdatedTimestamp = effectiveBalanceTs,
                            isManual = false
                        )
                    )
                    saveExtractedCardBalance(cleanBank, effectiveBalance, effectiveBalanceTs)
                }
                if (effectiveBalance != null || newTxs.isNotEmpty()) {
                    recalculateRealSafeToSpend()
                    persistSnapshotToSharedPrefs()
                }
                return@withLock effectiveBalance
            } catch (_: SecurityException) {
            } catch (_: Exception) {
            }
            latestRealBalanceToman ?: latestFallbackBalanceToman
        }
    }

    private fun isDemoBankSet(accounts: List<BankAccountEntity>): Boolean {
        val names = accounts.map { it.bankName }.toSet()
        return names == setOf("بلو", "سپه", "پاسارگاد", "ملت", "ویپاد") &&
            accounts.any { it.bankName == "بلو" && it.balanceToman == 14_250_000L }
    }

    private suspend fun rebuildBankAccountsFromTransactionsIfEmpty(
        savedTankhahPrefs: Map<String, Boolean> = emptyMap()
    ) {
        val currentAccounts = dao.getAllBankAccountsNow()
        if (currentAccounts.isNotEmpty()) return

        val allTxs = dao.getAllTransactionsNow()
            .filter { it.bankName.isNotBlank() && it.bankName != "نقدی / دستی" && it.bankName != "بانک" && it.bankName != "تراکنش صوتی" }
        if (allTxs.isEmpty()) return

        val groupedByBank = allTxs.groupBy { it.bankName }
        val primaryDefaultBank = if (groupedByBank.containsKey("بلو")) {
            "بلو"
        } else {
            groupedByBank.keys.firstOrNull().orEmpty()
        }

        val rebuilt = groupedByBank.map { (bankName, txs) ->
            val sortedTxs = txs.sortedByDescending { it.timestamp }
            val latestWithBalance = sortedTxs.firstOrNull { it.balanceAfterToman != null }
            val latestTs = sortedTxs.first().timestamp
            val profile = JibemanSmsEngine.BANK_PROFILES.firstOrNull { it.nameFa == bankName }
            BankAccountEntity(
                bankName = bankName,
                nameEn = profile?.nameEn ?: bankName,
                balanceToman = latestWithBalance?.balanceAfterToman ?: 0L,
                isTankhah = savedTankhahPrefs[bankName] ?: (bankName == primaryDefaultBank),
                cardLast4 = "",
                lastUpdatedTimestamp = latestTs,
                isManual = false
            )
        }
        if (rebuilt.isNotEmpty()) {
            dao.upsertBankAccounts(rebuilt)
        }
    }

    /**
     * Triggered when user taps "پاکسازی و بازخوانی تمیز پیامک‌های واقعی".
     * Always rebuilds and updates bank accounts (`isTankhah = true` on `"بلو"` and `false` on others
     * if no prior tankhah preference exists) and saves the snapshot immediately.
     */
    suspend fun cleanPurgeAndRescanRealSms(context: Context): Int = withContext(Dispatchers.IO) {
        val priorTankhahPrefs: Map<String, Boolean>
        smsSyncMutex.withLock {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val existingAccounts = dao.getAllBankAccountsNow()
            priorTankhahPrefs = if (existingAccounts.isNotEmpty() && !isDemoBankSet(existingAccounts)) {
                existingAccounts.associate { it.bankName to it.isTankhah }
            } else {
                emptyMap()
            }
            prefs.edit()
                .remove("jibeman_dedup_keys")
                .remove("card_balances_v4_json")
                .remove("backup_snapshot_json")
                .remove("synced_jalali_months_v1")
                .remove("last_max_sms_ts_v2")
                .putBoolean("full_inbox_balance_pass_v1", false)
                .putBoolean("full_inbox_balance_pass_v2", false)
                .putBoolean("has_real_bank_sms", false)
                .apply()
            dao.clearAllTransactions()
            dao.clearAllBankAccounts()
            dao.getAllCommitmentsNow().filter { isDemoCommitment(it) }.forEach { demoC ->
                dao.deleteCommitmentById(demoC.id)
            }
            lastCompletedScanEpochMs = 0L
        }
        val count = scanDeviceSmsInboxLast30Days(context, daysBack = 3650, force = true)
        smsSyncMutex.withLock {
            rebuildBankAccountsFromTransactionsIfEmpty(priorTankhahPrefs)
            if (dao.getAllTransactionsNow().isEmpty() && dao.getAllBankAccountsNow().isEmpty()) {
                getPrimaryPrefs()?.edit()?.putBoolean("has_real_bank_sms", false)?.apply()
                ensureSeeded()
            } else if (priorTankhahPrefs.isNotEmpty()) {
                val restoredTankhah = dao.getAllBankAccountsNow().map { acc ->
                    val pref = priorTankhahPrefs[acc.bankName]
                    if (pref != null) acc.copy(isTankhah = pref) else acc
                }
                dao.upsertBankAccounts(restoredTankhah)
            }
            recalculateRealSafeToSpend()
            persistSnapshotToSharedPrefs()
        }
        count
    }

    suspend fun cleanRescanRealSms(context: Context): Int = cleanPurgeAndRescanRealSms(context)

    private suspend fun insertVoiceCommitment(item: ParsedVoiceExpense, timestampMillis: Long) {
        val cleanPurpose = item.purposeCategory.trim().ifBlank {
            if (item.commitmentKind == "RECEIVABLE") "طلب" else "بدهی"
        }
        val isReceivable = item.commitmentKind == "RECEIVABLE" || item.isIncome
        val resolvedJalaliDate = if (item.dueDateHint.isNotBlank()) {
            "${item.dueDateHint} • ${JalaliCalendarUtils.resolveRelativeJalaliDate(item.dueDateHint, timestampMillis)}"
        } else {
            JalaliCalendarUtils.timestampToJalali(timestampMillis).dateWithYear
        }
        dao.insertCommitment(
            CommitmentEntity(
                category = "DONG",
                title = cleanPurpose,
                counterparty = if (isReceivable) "طلب دارم • ثبت صوتی" else "بدهی دارم • ثبت صوتی",
                amountToman = item.totalAmountToman,
                dueDateStr = resolvedJalaliDate,
                sayyadId = "",
                statusText = if (isReceivable) "طلب دارم (دریافتنی)" else "بدهی دارم (پرداختنی)",
                isSettled = false,
                timestamp = timestampMillis
            )
        )
    }

    /**
     * Inserts multiple voice expenses and/or future commitments (بدهی / طلب) when the user speaks
     * a multi-clause sentence in "+ ثبت صوتی" mode.
     */
    suspend fun confirmMultiVoiceExpenses(items: List<ParsedVoiceExpense>) {
        val validItems = items.filter { it.totalAmountToman > 0L && it.purposeCategory.isNotBlank() }
        if (validItems.isEmpty()) return

        val baseNow = System.currentTimeMillis()
        val nowJalali = JalaliCalendarUtils.timestampToJalali(baseNow)
        val manualBankLabel = "نقدی / دستی"

        validItems.forEachIndexed { index, item ->
            if (item.isCommitment) {
                insertVoiceCommitment(item, baseNow + index)
                return@forEachIndexed
            }

            val cleanPurpose = item.purposeCategory.trim()
            val resolvedStore = item.merchant.trim()
                .ifBlank { item.storeName.trim() }
                .takeIf { it.isNotBlank() && it != "بانک" && it != "تراکنش صوتی" }
                ?: manualBankLabel

            dao.insertTransaction(
                TransactionEntity(
                    title = cleanPurpose,
                    storeName = resolvedStore,
                    purpose = cleanPurpose,
                    totalAmountToman = item.totalAmountToman,
                    userShareToman = item.totalAmountToman,
                    dongAmountToman = 0L,
                    dongFriendName = "",
                    weekdayDate = nowJalali.weekdayDate,
                    timeStr = nowJalali.timeStr,
                    cardInfo = manualBankLabel,
                    type = if (item.isIncome) "INCOME" else "EXPENSE",
                    isUntagged = false,
                    timestamp = baseNow + index,
                    rawSmsBody = "",
                    bankName = manualBankLabel
                )
            )
        }

        recalculateRealSafeToSpend()
        persistSnapshotToSharedPrefs()
    }

    /**
     * Saves a voice-tagged expense, future commitment (بدهی/طلب), or updates an untagged SMS transaction.
     * - When tagging an existing bank SMS (`targetUntaggedTransactionId != null`), updates BOTH
     *   `tx.title` and `tx.purpose` and sets `isUntagged = false`—NEVER changes its bank amount or bank name!
     * - When creating a new manual voice entry (`targetUntaggedTransactionId == null`),
     *   future obligations (`isCommitment == true`) route directly to Room `commitments` (`DONG` tab),
     *   while completed transactions save to Room `transactions`.
     */
    suspend fun confirmVoiceExpense(
        parsedVoice: ParsedVoiceExpense,
        targetUntaggedTransactionId: Long? = null
    ) {
        val now = System.currentTimeMillis()
        val nowJalali = JalaliCalendarUtils.timestampToJalali(now)

        val cleanPurpose = parsedVoice.purposeCategory.trim()
        if (cleanPurpose.isBlank()) return

        if (targetUntaggedTransactionId != null) {
            val existing = dao.getTransactionById(targetUntaggedTransactionId)
            if (existing != null) {
                val effectiveTotal = existing.totalAmountToman
                val effectiveDong = parsedVoice.dongAmountToman.coerceAtMost(effectiveTotal)
                val effectiveUserShare = (effectiveTotal - effectiveDong).coerceAtLeast(0L)

                val updatedTitle = if (effectiveDong > 0L) {
                    "$cleanPurpose (سهم شما)"
                } else {
                    cleanPurpose
                }

                // Update BOTH tx.title and tx.purpose, set isUntagged = false, keep bankName & amount locked!
                dao.updateTransaction(
                    existing.copy(
                        title = updatedTitle,
                        purpose = cleanPurpose,
                        userShareToman = effectiveUserShare,
                        dongAmountToman = effectiveDong,
                        dongFriendName = parsedVoice.dongFriendName,
                        isUntagged = false
                    )
                )

                if (effectiveDong > 0L && parsedVoice.dongFriendName.isNotBlank()) {
                    dao.insertCommitment(
                        CommitmentEntity(
                            category = "DONG",
                            title = "دنگ $cleanPurpose",
                            counterparty = "طلب دارم • سهم ${parsedVoice.dongFriendName} • ${existing.cardInfo}",
                            amountToman = effectiveDong,
                            dueDateStr = "${existing.weekdayDate} • ${existing.timeStr}",
                            sayyadId = "",
                            statusText = "طلب دارم (به من بدهکار است) • ${parsedVoice.dongFriendName}",
                            isSettled = false,
                            timestamp = now
                        )
                    )
                }
                recalculateRealSafeToSpend()
                persistSnapshotToSharedPrefs()
                return
            }
        }

        // Manual cash/voice entry: block saving if amount <= 0L!
        if (parsedVoice.totalAmountToman <= 0L) return

        // Route future debt / receivable directly to Commitments ("بدهی و طلب")
        if (parsedVoice.isCommitment) {
            insertVoiceCommitment(parsedVoice, now)
            recalculateRealSafeToSpend()
            persistSnapshotToSharedPrefs()
            return
        }

        val effectiveDong = parsedVoice.dongAmountToman.coerceIn(0L, parsedVoice.totalAmountToman)
        val effectiveUserShare = (parsedVoice.totalAmountToman - effectiveDong).coerceAtLeast(0L)
        val manualBankLabel = "نقدی / دستی"
        val resolvedStore = parsedVoice.merchant.trim()
            .ifBlank { parsedVoice.storeName.trim() }
            .takeIf { it.isNotBlank() && it != "بانک" && it != "تراکنش صوتی" }
            ?: manualBankLabel

        val title = if (effectiveDong > 0L) {
            "$cleanPurpose (سهم خالص شما)"
        } else {
            cleanPurpose
        }

        dao.insertTransaction(
            TransactionEntity(
                title = title,
                storeName = resolvedStore,
                purpose = cleanPurpose,
                totalAmountToman = parsedVoice.totalAmountToman,
                userShareToman = effectiveUserShare,
                dongAmountToman = effectiveDong,
                dongFriendName = parsedVoice.dongFriendName,
                weekdayDate = nowJalali.weekdayDate,
                timeStr = nowJalali.timeStr,
                cardInfo = manualBankLabel,
                type = if (parsedVoice.isIncome) "INCOME" else "EXPENSE",
                isUntagged = false,
                timestamp = now,
                rawSmsBody = "",
                bankName = manualBankLabel
            )
        )

        if (effectiveDong > 0L && parsedVoice.dongFriendName.isNotBlank()) {
            dao.insertCommitment(
                CommitmentEntity(
                    category = "DONG",
                    title = "دنگ $cleanPurpose",
                    counterparty = "طلب دارم • سهم ${parsedVoice.dongFriendName} • $manualBankLabel",
                    amountToman = effectiveDong,
                    dueDateStr = "${nowJalali.weekdayDate} • ${nowJalali.timeStr}",
                    sayyadId = "",
                    statusText = "طلب دارم (به من بدهکار است) • ${parsedVoice.dongFriendName}",
                    isSettled = false,
                    timestamp = now
                )
            )
        }

        recalculateRealSafeToSpend()
        persistSnapshotToSharedPrefs()
    }

    suspend fun toggleHideAmounts() {
        val current = dao.getSettingsNow() ?: AppSettingsEntity()
        dao.saveSettings(current.copy(hideAmounts = !current.hideAmounts))
        persistSnapshotToSharedPrefs()
    }

    suspend fun setOnlyTankhahMode(onlyTankhah: Boolean) {
        val current = dao.getSettingsNow() ?: AppSettingsEntity()
        dao.saveSettings(current.copy(onlyTankhahMode = onlyTankhah))
        recalculateRealSafeToSpend()
        persistSnapshotToSharedPrefs()
    }

    suspend fun setTankhahModeEnabled(enabled: Boolean) {
        val current = dao.getSettingsNow() ?: AppSettingsEntity()
        dao.saveSettings(
            current.copy(
                tankhahModeEnabled = enabled,
                onlyTankhahMode = if (enabled) true else current.onlyTankhahMode
            )
        )
        recalculateRealSafeToSpend()
        persistSnapshotToSharedPrefs()
    }

    suspend fun setSelectedFontFamily(fontFamilyKey: String) {
        val current = dao.getSettingsNow() ?: AppSettingsEntity()
        dao.saveSettings(current.copy(selectedFontFamily = fontFamilyKey.trim().ifEmpty { "IRANSANSX" }))
        persistSnapshotToSharedPrefs()
    }

    suspend fun toggleAccountTankhah(bankName: String) {
        val account = dao.getBankAccountByName(bankName) ?: return
        dao.upsertBankAccount(account.copy(isTankhah = !account.isTankhah))
        recalculateRealSafeToSpend()
        persistSnapshotToSharedPrefs()
    }

    suspend fun addOrUpdateBankAccount(
        bankName: String,
        balanceToman: Long,
        isTankhah: Boolean,
        cardLast4: String = "",
        customSenderAddresses: List<String> = emptyList()
    ): Long {
        val cleanName = bankName.trim().ifEmpty { return 0L }
        if (customSenderAddresses.isNotEmpty()) {
            saveCustomSendersForBank(cleanName, customSenderAddresses)
        }
        val isKnownBank = JibemanSmsEngine.isKnownBankName(cleanName)
        val shouldTryAutoSync = (isKnownBank || customSenderAddresses.isNotEmpty()) && appContext != null
        val existing = dao.getBankAccountByName(cleanName)
        dao.upsertBankAccount(
            BankAccountEntity(
                bankName = cleanName,
                nameEn = existing?.nameEn ?: if (isKnownBank) cleanName else "Custom Account",
                balanceToman = balanceToman.coerceAtLeast(0L),
                isTankhah = isTankhah,
                cardLast4 = PersianNumberUtils.toPersianDigits(cardLast4.trim()),
                lastUpdatedTimestamp = if (balanceToman > 0L) System.currentTimeMillis() else 0L,
                isManual = !shouldTryAutoSync || (balanceToman > 0L && customSenderAddresses.isEmpty() && !isKnownBank)
            )
        )
        var finalBalance = balanceToman.coerceAtLeast(0L)
        if (shouldTryAutoSync && appContext != null) {
            val discovered = rescanSingleBankFromSms(appContext, cleanName)
            if (discovered != null && (balanceToman <= 0L || customSenderAddresses.isNotEmpty())) {
                finalBalance = discovered
            } else if (balanceToman > 0L) {
                val refreshed = dao.getBankAccountByName(cleanName)
                if (refreshed != null && refreshed.balanceToman != balanceToman) {
                    dao.upsertBankAccount(
                        refreshed.copy(
                            balanceToman = balanceToman,
                            lastUpdatedTimestamp = System.currentTimeMillis(),
                            isManual = true
                        )
                    )
                }
            }
        }
        recalculateRealSafeToSpend()
        persistSnapshotToSharedPrefs()
        return finalBalance
    }

    suspend fun updateBankAccountDetails(
        originalBankName: String,
        newBankName: String,
        balanceToman: Long,
        isTankhah: Boolean,
        addedSenders: List<String> = emptyList(),
        removedSenders: List<String> = emptyList()
    ) {
        val cleanOriginal = originalBankName.trim()
        val cleanNew = newBankName.trim().ifEmpty { cleanOriginal }
        if (cleanNew.isEmpty()) return

        if (removedSenders.isNotEmpty()) {
            removeCustomSendersForBank(removedSenders)
        }
        if (addedSenders.isNotEmpty()) {
            saveCustomSendersForBank(cleanNew, addedSenders)
        }

        val existing = dao.getBankAccountByName(cleanOriginal)
        val balanceChangedManually = existing != null && existing.balanceToman != balanceToman.coerceAtLeast(0L)
        val updatedEntity = BankAccountEntity(
            bankName = cleanNew,
            nameEn = existing?.nameEn ?: cleanNew,
            balanceToman = balanceToman.coerceAtLeast(0L),
            isTankhah = isTankhah,
            cardLast4 = existing?.cardLast4 ?: "",
            lastUpdatedTimestamp = if (balanceChangedManually) System.currentTimeMillis() else (existing?.lastUpdatedTimestamp ?: System.currentTimeMillis()),
            isManual = if (balanceChangedManually) true else (existing?.isManual ?: false)
        )

        if (cleanOriginal != cleanNew) {
            // Re-map any existing custom senders to the renamed bank
            val existingSenders = getCustomSendersForBank(cleanOriginal)
            if (existingSenders.isNotEmpty()) {
                removeCustomSendersForBank(existingSenders)
                saveCustomSendersForBank(cleanNew, existingSenders)
            }
            dao.deleteBankAccount(cleanOriginal)
        }
        dao.upsertBankAccount(updatedEntity)

        if (addedSenders.isNotEmpty() && appContext != null && !balanceChangedManually) {
            rescanSingleBankFromSms(appContext, cleanNew)
        } else {
            recalculateRealSafeToSpend()
            persistSnapshotToSharedPrefs()
        }
    }

    suspend fun deleteBankAccount(bankName: String) {
        val cleanName = bankName.trim()
        if (cleanName.isEmpty()) return
        dao.deleteBankAccount(cleanName)
        val linkedSenders = getCustomSendersForBank(cleanName)
        if (linkedSenders.isNotEmpty()) {
            removeCustomSendersForBank(linkedSenders)
        }
        try {
            val prefs = getPrimaryPrefs()
            val rawJson = prefs?.getString("card_balances_v4_json", "{}") ?: "{}"
            val obj = JSONObject(rawJson)
            if (obj.has(cleanName)) {
                obj.remove(cleanName)
                prefs?.edit()?.putString("card_balances_v4_json", obj.toString())?.apply()
            }
        } catch (_: Exception) {
        }
        recalculateRealSafeToSpend()
        persistSnapshotToSharedPrefs()
    }

    suspend fun deleteTransaction(id: Long) {
        dao.deleteTransactionById(id)
        recalculateRealSafeToSpend()
        persistSnapshotToSharedPrefs()
    }

    suspend fun addNewCommitment(
        category: String,
        title: String,
        counterparty: String,
        amountToman: Long,
        dueDateStr: String,
        sayyadId: String
    ) {
        val statusText = when (category) {
            "SAYYAD_ISSUED" -> "ثبت شده در سامانه صیاد • کسر شده از تراز ماه"
            "SAYYAD_RECEIVED" -> "استعلام صیاد: وضعیت سفید • در انتظار سررسید"
            "LOAN_INSTALLMENT" -> "محاسبه شده در سپر تعهدات ماهانه"
            else -> "در انتظار تسویه دنگ"
        }
        dao.insertCommitment(
            CommitmentEntity(
                category = category,
                title = title,
                counterparty = counterparty,
                amountToman = amountToman,
                dueDateStr = dueDateStr,
                sayyadId = PersianNumberUtils.toPersianDigits(sayyadId),
                statusText = statusText,
                isSettled = false,
                timestamp = System.currentTimeMillis()
            )
        )
        recalculateRealSafeToSpend()
        persistSnapshotToSharedPrefs()
    }

    suspend fun toggleCommitmentSettled(commitment: CommitmentEntity) {
        val newSettled = !commitment.isSettled
        dao.updateCommitment(
            commitment.copy(
                isSettled = newSettled,
                statusText = if (newSettled) "پاس شد / تسویه شد ✓" else "فعال در تعهدات ماه"
            )
        )
        recalculateRealSafeToSpend()
        persistSnapshotToSharedPrefs()
    }

    suspend fun updateCommitmentDetails(
        original: CommitmentEntity,
        title: String,
        counterparty: String,
        amountToman: Long,
        dueDateStr: String,
        isSettled: Boolean
    ) {
        dao.updateCommitment(
            original.copy(
                title = title.trim().ifEmpty { original.title },
                counterparty = counterparty.trim().ifEmpty { original.counterparty },
                amountToman = amountToman,
                dueDateStr = dueDateStr.trim().ifEmpty { original.dueDateStr },
                isSettled = isSettled,
                statusText = if (isSettled) "پاس شد / تسویه شد ✓" else "فعال در تعهدات ماه"
            )
        )
        recalculateRealSafeToSpend()
        persistSnapshotToSharedPrefs()
    }

    suspend fun deleteCommitment(id: Long) {
        dao.deleteCommitmentById(id)
        recalculateRealSafeToSpend()
        persistSnapshotToSharedPrefs()
    }

    suspend fun updateTransactionDetails(
        originalTx: TransactionEntity,
        amountToman: Long,
        balanceAfterToman: Long?,
        weekdayDate: String,
        timeStr: String,
        cardInfo: String,
        purpose: String,
        storeName: String,
        specialTagMode: String
    ) {
        val cleanStore = storeName.trim().ifEmpty { originalTx.shortBankName }
        val cleanPurpose = purpose.trim()
        val updatedTitle = if (cleanPurpose.isNotEmpty() && cleanPurpose != "نیازمند تکمیل صوتی") {
            cleanPurpose
        } else {
            originalTx.title
        }

        dao.updateTransaction(
            originalTx.copy(
                title = updatedTitle,
                storeName = cleanStore,
                purpose = cleanPurpose.ifEmpty { originalTx.purpose },
                totalAmountToman = amountToman,
                userShareToman = amountToman,
                balanceAfterToman = balanceAfterToman,
                weekdayDate = weekdayDate.trim().ifEmpty { originalTx.weekdayDate },
                timeStr = timeStr.trim().ifEmpty { originalTx.timeStr },
                cardInfo = cardInfo.trim().ifEmpty { originalTx.cardInfo },
                isUntagged = cleanPurpose.isEmpty() || cleanPurpose == "نیازمند تکمیل صوتی"
            )
        )

        // If user edited balanceAfterToman for the latest SMS transaction of a bank, also update that bank's balance if applicable
        if (
            balanceAfterToman != null &&
            balanceAfterToman >= 0L &&
            originalTx.bankName != "نقدی / دستی" &&
            originalTx.rawSmsBody.isNotBlank()
        ) {
            val bankKey = originalTx.shortBankName
            val acc = dao.getBankAccountByName(bankKey)
            if (acc != null && originalTx.timestamp >= acc.lastUpdatedTimestamp) {
                dao.upsertBankAccount(acc.copy(balanceToman = balanceAfterToman))
            }
        }

        val now = System.currentTimeMillis()
        when (specialTagMode) {
            "INSTALLMENT" -> {
                dao.insertCommitment(
                    CommitmentEntity(
                        category = "LOAN_INSTALLMENT",
                        title = "قسط/وام: ${cleanPurpose.ifEmpty { originalTx.title }}",
                        counterparty = cardInfo.trim(),
                        amountToman = amountToman,
                        dueDateStr = "$weekdayDate • $timeStr",
                        sayyadId = "",
                        statusText = "ثبت‌شده از جزئیات تراکنش",
                        isSettled = false,
                        timestamp = now
                    )
                )
            }
            "DONG" -> {
                dao.insertCommitment(
                    CommitmentEntity(
                        category = "DONG",
                        title = "بدهی/طلب (دُنگ): ${cleanPurpose.ifEmpty { originalTx.title }}",
                        counterparty = "پرداخت با ${cardInfo.trim()}",
                        amountToman = amountToman,
                        dueDateStr = "$weekdayDate • $timeStr",
                        sayyadId = "",
                        statusText = "در انتظار تسویه دُنگ",
                        isSettled = false,
                        timestamp = now
                    )
                )
            }
        }
        recalculateRealSafeToSpend()
        persistSnapshotToSharedPrefs()
    }

    suspend fun updatePaydayAndGoldSettings(
        paydayStartDay: Int,
        paydayCycleLabel: String,
        autoDetectSalary: Boolean,
        goldPrice18kToman: Long,
        dailySafeBudgetToman: Long
    ) {
        val settings = dao.getSettingsNow() ?: AppSettingsEntity()
        dao.saveSettings(
            settings.copy(
                paydayStartDay = paydayStartDay.coerceIn(1, 31),
                paydayCycleLabel = paydayCycleLabel,
                autoDetectSalary = autoDetectSalary,
                goldPrice18kToman = goldPrice18kToman,
                dailySafeBudgetToman = PersianNumberUtils.roundDownToCleanThousands(dailySafeBudgetToman)
            )
        )
        persistSnapshotToSharedPrefs()
    }

    suspend fun exportBackupJson(): String {
        val settings = dao.getSettingsNow() ?: AppSettingsEntity()
        val txs = dao.getAllTransactionsNow()
        val accounts = dao.getAllBankAccountsNow()
        val commitments = dao.getAllCommitmentsNow()

        val root = JSONObject()
        root.put("app", "OrosiFinance_KhialRahat")
        root.put("version", 4)

        val settingsObj = JSONObject().apply {
            put("dailySafeBudgetToman", PersianNumberUtils.roundDownToCleanThousands(settings.dailySafeBudgetToman))
            put("monthlyBalanceToman", settings.monthlyBalanceToman)
            put("paydayStartDay", settings.paydayStartDay)
            put("paydayCycleLabel", settings.paydayCycleLabel)
            put("autoDetectSalary", settings.autoDetectSalary)
            put("hideAmounts", settings.hideAmounts)
            put("onlyTankhahMode", settings.onlyTankhahMode)
            put("tankhahModeEnabled", settings.tankhahModeEnabled)
            put("selectedFontFamily", settings.selectedFontFamily)
        }
        root.put("settings", settingsObj)

        val accArray = JSONArray()
        accounts.forEach { acc ->
            accArray.put(
                JSONObject().apply {
                    put("bankName", acc.bankName)
                    put("nameEn", acc.nameEn)
                    put("balanceToman", acc.balanceToman)
                    put("isTankhah", acc.isTankhah)
                    put("cardLast4", acc.cardLast4)
                    put("lastUpdatedTimestamp", acc.lastUpdatedTimestamp)
                    put("isManual", acc.isManual)
                }
            )
        }
        root.put("bankAccounts", accArray)

        val txArray = JSONArray()
        txs.forEach { tx ->
            txArray.put(
                JSONObject().apply {
                    put("title", tx.title)
                    put("storeName", tx.storeName)
                    put("purpose", tx.purpose)
                    put("totalAmountToman", tx.totalAmountToman)
                    put("userShareToman", tx.userShareToman)
                    put("dongAmountToman", tx.dongAmountToman)
                    put("dongFriendName", tx.dongFriendName)
                    put("weekdayDate", tx.weekdayDate)
                    put("timeStr", tx.timeStr)
                    put("cardInfo", tx.cardInfo)
                    put("type", tx.type)
                    put("isUntagged", tx.isUntagged)
                    put("timestamp", tx.timestamp)
                    if (tx.balanceAfterToman != null) put("balanceAfterToman", tx.balanceAfterToman)
                    put("rawSmsBody", tx.rawSmsBody)
                    put("bankName", tx.bankName)
                }
            )
        }
        root.put("transactions", txArray)

        val compArray = JSONArray()
        commitments.forEach { c ->
            compArray.put(
                JSONObject().apply {
                    put("category", c.category)
                    put("title", c.title)
                    put("counterparty", c.counterparty)
                    put("amountToman", c.amountToman)
                    put("dueDateStr", c.dueDateStr)
                    put("sayyadId", c.sayyadId)
                    put("statusText", c.statusText)
                    put("isSettled", c.isSettled)
                    put("timestamp", c.timestamp)
                }
            )
        }
        root.put("commitments", compArray)

        return root.toString(2)
    }

    suspend fun importBackupJson(jsonString: String): Boolean {
        return importBackupJsonInternal(jsonString, persistToPrefs = true)
    }

    private suspend fun importBackupJsonInternal(
        jsonString: String,
        persistToPrefs: Boolean
    ): Boolean {
        return try {
            val root = JSONObject(jsonString)
            val sObj = root.optJSONObject("settings")
            if (sObj != null) {
                val rawDaily = sObj.optLong("dailySafeBudgetToman", 680_000L).coerceAtLeast(0L)
                dao.saveSettings(
                    AppSettingsEntity(
                        id = 1,
                        dailySafeBudgetToman = PersianNumberUtils.roundDownToCleanThousands(rawDaily),
                        monthlyBalanceToman = sObj.optLong("monthlyBalanceToman", 18_450_000L).coerceAtLeast(0L),
                        paydayStartDay = sObj.optInt("paydayStartDay", 1),
                        paydayCycleLabel = sObj.optString("paydayCycleLabel", "۱ آبان تا ۳۰ آبان"),
                        autoDetectSalary = sObj.optBoolean("autoDetectSalary", true),
                        hideAmounts = sObj.optBoolean("hideAmounts", false),
                        onlyTankhahMode = sObj.optBoolean("onlyTankhahMode", true),
                        tankhahModeEnabled = sObj.optBoolean("tankhahModeEnabled", false),
                        selectedFontFamily = sObj.optString("selectedFontFamily", "IRANSANSX")
                    )
                )
            }

            val accArray = root.optJSONArray("bankAccounts")
            if (accArray != null) {
                dao.clearAllBankAccounts()
                val accList = mutableListOf<BankAccountEntity>()
                for (i in 0 until accArray.length()) {
                    val o = accArray.getJSONObject(i)
                    accList.add(
                        BankAccountEntity(
                            bankName = o.optString("bankName"),
                            nameEn = o.optString("nameEn"),
                            balanceToman = o.optLong("balanceToman", 0L),
                            isTankhah = o.optBoolean("isTankhah", true),
                            cardLast4 = o.optString("cardLast4", ""),
                            lastUpdatedTimestamp = o.optLong("lastUpdatedTimestamp", System.currentTimeMillis()),
                            isManual = o.optBoolean("isManual", false)
                        )
                    )
                }
                dao.upsertBankAccounts(accList)
            }

            val txArray = root.optJSONArray("transactions")
            if (txArray != null) {
                dao.clearAllTransactions()
                val list = mutableListOf<TransactionEntity>()
                for (i in 0 until txArray.length()) {
                    val o = txArray.getJSONObject(i)
                    val ts = o.optLong("timestamp", System.currentTimeMillis())
                    val realJalali = JalaliCalendarUtils.fromTimestamp(ts)
                    val balanceAfter = if (o.has("balanceAfterToman")) o.optLong("balanceAfterToman") else null
                    list.add(
                        TransactionEntity(
                            title = o.optString("title"),
                            storeName = o.optString("storeName"),
                            purpose = o.optString("purpose"),
                            totalAmountToman = o.optLong("totalAmountToman"),
                            userShareToman = o.optLong("userShareToman"),
                            dongAmountToman = o.optLong("dongAmountToman"),
                            dongFriendName = o.optString("dongFriendName"),
                            weekdayDate = o.optString("weekdayDate").takeIf { it.isNotBlank() && it != "جمعه ۱ آبان" }
                                ?: realJalali.weekdayDate,
                            timeStr = o.optString("timeStr").takeIf { it.isNotBlank() && it != "۱۸:۱۰" }
                                ?: realJalali.timeStr,
                            cardInfo = o.optString("cardInfo", "کارت بانکی"),
                            type = o.optString("type", "EXPENSE"),
                            isUntagged = o.optBoolean("isUntagged", false),
                            timestamp = ts,
                            balanceAfterToman = balanceAfter,
                            rawSmsBody = o.optString("rawSmsBody", ""),
                            bankName = o.optString("bankName", "")
                        )
                    )
                }
                dao.insertTransactions(list)
            }

            val compArray = root.optJSONArray("commitments")
            if (compArray != null) {
                dao.clearAllCommitments()
                val list = mutableListOf<CommitmentEntity>()
                for (i in 0 until compArray.length()) {
                    val o = compArray.getJSONObject(i)
                    list.add(
                        CommitmentEntity(
                            category = o.optString("category", "SAYYAD_ISSUED"),
                            title = o.optString("title"),
                            counterparty = o.optString("counterparty"),
                            amountToman = o.optLong("amountToman"),
                            dueDateStr = o.optString("dueDateStr"),
                            sayyadId = o.optString("sayyadId"),
                            statusText = o.optString("statusText"),
                            isSettled = o.optBoolean("isSettled", false),
                            timestamp = o.optLong("timestamp", System.currentTimeMillis())
                        )
                    )
                }
                dao.insertCommitments(list)
            }
            if (persistToPrefs) {
                persistSnapshotToSharedPrefs()
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun resetToDefaultSeedData() {
        dao.clearAllTransactions()
        dao.clearAllBankAccounts()
        dao.clearAllCommitments()
        getPrimaryPrefs()?.edit()
            ?.remove("backup_snapshot_json")
            ?.remove("jibeman_dedup_keys")
            ?.remove("card_balances_v4_json")
            ?.putBoolean("has_real_bank_sms", false)
            ?.apply()
        dao.saveSettings(
            AppSettingsEntity(
                id = 1,
                dailySafeBudgetToman = 680_000L,
                monthlyBalanceToman = 18_450_000L,
                paydayStartDay = 1,
                paydayCycleLabel = "۱ آبان تا ۳۰ آبان ۱۴۰۴",
                autoDetectSalary = true,
                hideAmounts = false,
                onlyTankhahMode = false
            )
        )
        ensureSeeded()
    }
}
