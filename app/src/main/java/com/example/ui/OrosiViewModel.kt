package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppSettingsEntity
import com.example.data.local.BankAccountEntity
import com.example.data.local.CommitmentEntity
import com.example.data.local.OrosiDatabase
import com.example.data.local.TransactionEntity
import com.example.data.repository.OrosiFinanceRepository
import com.example.domain.BankSmsParserEngine
import com.example.domain.JalaliCalendarUtils
import com.example.domain.OrosiVoiceSmsMatcher
import com.example.domain.ParsedSmsResult
import com.example.domain.ParsedVoiceExpense
import com.example.domain.PersianNumberUtils
import com.example.domain.PersianVoiceSlangParserEngine
import com.example.domain.UntaggedSmsCandidate
import com.example.domain.VoiceSmsMatchResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class OrosiScreen(val route: String, val persianTitle: String) {
    HOME("home", "خانه"),
    TRANSACTIONS("transactions", "تراکنش‌ها"),
    ACCOUNTS("accounts", "حساب‌ها"),
    TOOLS("tools", "ابزارها")
}

enum class CommitmentTab(val title: String) {
    SAYYAD("چک صیادی"),
    INSTALLMENTS("وام و قسط"),
    DONG("بدهی و طلب (دُنگ)"),
    REPORTS("گزارش دخل و خرج")
}

data class VoiceUiState(
    val isRecordingSimulated: Boolean = false,
    val recognizedText: String = "",
    val manualAmountText: String = "",
    val storeName: String = "",
    val purposeCategory: String = "",
    val cardLabel: String = "نقدی / دستی",
    val totalAmountToman: Long = 0L,
    val dongAmountToman: Long = 0L,
    val dongFriendName: String = "",
    val userShareToman: Long = 0L,
    val targetUntaggedTransaction: TransactionEntity? = null,
    val parsedMultiItems: List<ParsedVoiceExpense> = emptyList(),
    val hasBankAmountMismatch: Boolean = false,
    val conflictingSpokenAmountToman: Long = 0L,
    val isIncome: Boolean = false,
    val hasUserOverriddenIncomeType: Boolean = false,
    val hasSummaryTotalMismatch: Boolean = false,
    val statedSummaryHeaderToman: Long = 0L,
    val commitmentKind: String? = null,
    val dueDateHint: String = "",
    val eventDayOffset: Int = 0,
    val commitmentSubLabel: String = "",
    val singleAutoMatch: VoiceSmsMatchResult? = null,
    val singleMatchedBankTxId: Long? = null,
    val multiAutoMatches: List<VoiceSmsMatchResult> = emptyList(),
    val multiMatchedBankTxIds: List<Long?> = emptyList(),
    val isApproximateAmount: Boolean = false,
    val explicitJalaliDate: String = "",
    val isVagueRecentPast: Boolean = false,
    val commitmentCategory: String = "DONG",
    val hasUserOverriddenDestination: Boolean = false
)

data class OrosiUiState(
    val currentScreen: OrosiScreen = OrosiScreen.HOME,
    val selectedCommitmentTab: CommitmentTab = CommitmentTab.SAYYAD,
    val selectedYearMonthKey: Int = JalaliCalendarUtils.fromTimestamp(System.currentTimeMillis()).yearMonthKey,
    val settings: AppSettingsEntity = AppSettingsEntity(),
    val transactions: List<TransactionEntity> = emptyList(),
    val bankAccounts: List<BankAccountEntity> = emptyList(),
    val commitments: List<CommitmentEntity> = emptyList(),
    val voiceState: VoiceUiState = VoiceUiState(),
    val selectedTransactionForDetail: TransactionEntity? = null,
    val selectedCommitmentForAction: CommitmentEntity? = null,
    val showVoiceOverlaySheet: Boolean = false,
    val showHeroMathSheet: Boolean = false,
    val showSmsSimulatorModal: Boolean = false,
    val showPaydayBackupModal: Boolean = false,
    val showAddCommitmentModal: Boolean = false,
    val showAddBankAccountModal: Boolean = false,
    val isSyncingSms: Boolean = false,
    val hasLoadedFromDb: Boolean = false,
    val loadingMonthKey: Int? = null,
    val selectedAccountForEdit: BankAccountEntity? = null,
    val selectedAccountCustomSenders: List<String> = emptyList(),
    val isRescanningSingleBank: Boolean = false,
    val backupJsonText: String = "",
    val statusBannerMessage: String? = null
)

class OrosiViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val dao = OrosiDatabase.getInstance(appContext).orosiDao()
    private val repository = OrosiFinanceRepository(dao, appContext)

    private val _currentScreen = MutableStateFlow(OrosiScreen.HOME)
    private val _selectedCommitmentTab = MutableStateFlow(CommitmentTab.SAYYAD)
    private val _selectedYearMonthKey = MutableStateFlow(
        JalaliCalendarUtils.fromTimestamp(System.currentTimeMillis()).yearMonthKey
    )
    private var hasUserChangedMonthManually = false
    private val _voiceState = MutableStateFlow(VoiceUiState())
    private val _selectedTransactionForDetail = MutableStateFlow<TransactionEntity?>(null)
    private val _selectedCommitmentForAction = MutableStateFlow<CommitmentEntity?>(null)
    private val _showVoiceOverlaySheet = MutableStateFlow(false)
    private val _showHeroMathSheet = MutableStateFlow(false)
    private val _showSmsSimulatorModal = MutableStateFlow(false)
    private val _showPaydayBackupModal = MutableStateFlow(false)
    private val _showAddCommitmentModal = MutableStateFlow(false)
    private val _showAddBankAccountModal = MutableStateFlow(false)
    private val _isSyncingSms = MutableStateFlow(false)
    private val _loadingMonthKey = MutableStateFlow<Int?>(null)
    private val _selectedAccountForEdit = MutableStateFlow<BankAccountEntity?>(null)
    private val _selectedAccountCustomSenders = MutableStateFlow<List<String>>(emptyList())
    private val _isRescanningSingleBank = MutableStateFlow(false)
    private val _backupJsonText = MutableStateFlow("")
    private val _statusBannerMessage = MutableStateFlow<String?>(null)
    private var monthSyncJob: kotlinx.coroutines.Job? = null

    init {
        viewModelScope.launch {
            com.example.ui.components.JibemanAssetLogoCache.preloadAsync(appContext)
        }
        viewModelScope.launch {
            try {
                repository.ensureSeeded()
                val hasCachedData = dao.getAllBankAccountsNow().isNotEmpty() || dao.getAllTransactionsNow().isNotEmpty()
                if (!hasCachedData) {
                    _isSyncingSms.value = true
                }
                repository.scanDeviceSmsInboxLast30Days(appContext, daysBack = 3650)
                syncInitialSelectedMonthWithTransactions()
            } finally {
                _isSyncingSms.value = false
            }
        }
    }

    private suspend fun syncInitialSelectedMonthWithTransactions() {
        if (hasUserChangedMonthManually) return
        val nowKey = JalaliCalendarUtils.fromTimestamp(System.currentTimeMillis()).yearMonthKey
        val allTxs = dao.getAllTransactionsNow().filter { it.totalAmountToman > 0L }
        val hasCurrentMonthTxs = allTxs.any {
            JalaliCalendarUtils.fromTimestamp(it.timestamp).yearMonthKey == nowKey
        }
        if (hasCurrentMonthTxs || allTxs.isEmpty()) {
            _selectedYearMonthKey.value = nowKey
        } else {
            val newestTxKey = allTxs.maxByOrNull { it.timestamp }?.let {
                JalaliCalendarUtils.fromTimestamp(it.timestamp).yearMonthKey
            } ?: nowKey
            _selectedYearMonthKey.value = newestTxKey.coerceAtMost(nowKey)
        }
    }

    val uiState: StateFlow<OrosiUiState> = combine(
        _currentScreen,
        _selectedCommitmentTab,
        repository.settingsFlow,
        repository.transactionsFlow,
        repository.commitmentsFlow
    ) { screen, commitmentTab, settings, txs, commitments ->
        OrosiUiState(
            currentScreen = screen,
            selectedCommitmentTab = commitmentTab,
            settings = settings ?: AppSettingsEntity(),
            transactions = txs,
            commitments = commitments,
            hasLoadedFromDb = true
        )
    }.combine(
        combine(
            repository.bankAccountsFlow,
            _selectedYearMonthKey
        ) { accounts, ymKey ->
            Pair(accounts, ymKey)
        }
    ) { base, (accounts, ymKey) ->
        base.copy(
            bankAccounts = accounts,
            selectedYearMonthKey = ymKey
        )
    }.combine(
        combine(
            _voiceState,
            _showVoiceOverlaySheet,
            _showHeroMathSheet,
            _showSmsSimulatorModal,
            _showPaydayBackupModal
        ) { voice, voiceSheet, mathSheet, smsModal, paydayModal ->
            Tuple5(voice, voiceSheet, mathSheet, smsModal, paydayModal)
        }
    ) { base, modals ->
        base.copy(
            voiceState = modals.first,
            showVoiceOverlaySheet = modals.second,
            showHeroMathSheet = modals.third,
            showSmsSimulatorModal = modals.fourth,
            showPaydayBackupModal = modals.fifth
        )
    }.combine(
        combine(
            _selectedTransactionForDetail,
            _selectedCommitmentForAction,
            _showAddCommitmentModal,
            _showAddBankAccountModal
        ) { selectedTx, selectedComm, addComm, addAcc ->
            Tuple4(selectedTx, selectedComm, addComm, addAcc)
        }
    ) { state, extra ->
        state.copy(
            selectedTransactionForDetail = extra.first,
            selectedCommitmentForAction = extra.second,
            showAddCommitmentModal = extra.third,
            showAddBankAccountModal = extra.fourth
        )
    }.combine(
        combine(_backupJsonText, _statusBannerMessage, _isSyncingSms) { backup, banner, syncing ->
            Triple(backup, banner, syncing)
        }
    ) { state, triple ->
        state.copy(
            backupJsonText = triple.first,
            statusBannerMessage = triple.second,
            isSyncingSms = triple.third
        )
    }.combine(
        combine(
            _loadingMonthKey,
            _selectedAccountForEdit,
            _selectedAccountCustomSenders,
            _isRescanningSingleBank
        ) { loadingMonth, editAcc, editSenders, rescanningBank ->
            Tuple4(loadingMonth, editAcc, editSenders, rescanningBank)
        }
    ) { state, extra ->
        state.copy(
            loadingMonthKey = extra.first,
            selectedAccountForEdit = extra.second,
            selectedAccountCustomSenders = extra.third,
            isRescanningSingleBank = extra.fourth
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = OrosiUiState()
    )

    fun navigateTo(screen: OrosiScreen) {
        _currentScreen.value = screen
    }

    fun selectCommitmentTab(tab: CommitmentTab) {
        _selectedCommitmentTab.value = tab
    }

    fun changeSelectedMonth(deltaMonths: Int) {
        val currentYearMonthKey = JalaliCalendarUtils.fromTimestamp(System.currentTimeMillis()).yearMonthKey
        val current = _selectedYearMonthKey.value
        if (deltaMonths > 0 && current >= currentYearMonthKey) return
        hasUserChangedMonthManually = true
        val candidateKey = JalaliCalendarUtils.shiftJalaliYearMonth(current, deltaMonths)
        val targetKey = if (deltaMonths > 0) {
            candidateKey.coerceAtMost(currentYearMonthKey)
        } else {
            candidateKey
        }
        _selectedYearMonthKey.value = targetKey

        if (!repository.isJalaliMonthSynced(targetKey)) {
            monthSyncJob?.cancel()
            monthSyncJob = viewModelScope.launch {
                _loadingMonthKey.value = targetKey
                val startMs = System.currentTimeMillis()
                try {
                    repository.syncTransactionsForJalaliMonth(appContext, targetKey)
                    val elapsed = System.currentTimeMillis() - startMs
                    if (elapsed < 260L) {
                        delay(260L - elapsed)
                    }
                } finally {
                    if (_loadingMonthKey.value == targetKey) {
                        _loadingMonthKey.value = null
                    }
                }
            }
        } else {
            _loadingMonthKey.value = null
        }
    }

    fun toggleHideAmounts() {
        viewModelScope.launch {
            repository.toggleHideAmounts()
        }
    }

    fun setOnlyTankhahMode(onlyTankhah: Boolean) {
        viewModelScope.launch {
            repository.setOnlyTankhahMode(onlyTankhah)
        }
    }

    fun setTankhahModeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setTankhahModeEnabled(enabled)
            val msg = if (enabled) {
                "حالت تفکیک حساب تنخواه از پس‌انداز فعال شد ✓"
            } else {
                "حالت تنخواه غیرفعال شد؛ مجموع کل حساب‌ها نمایش داده می‌شود."
            }
            showBanner(msg)
        }
    }

    fun setSelectedFontFamily(fontFamilyKey: String) {
        viewModelScope.launch {
            val option = com.example.ui.theme.OrosiFontOption.fromKey(fontFamilyKey)
            repository.setSelectedFontFamily(option.key)
            showBanner("فونت برنامه به «${option.persianTitle}» تغییر یافت ✓")
        }
    }

    fun toggleAccountTankhah(bankName: String) {
        viewModelScope.launch {
            repository.toggleAccountTankhah(bankName)
        }
    }

    fun setShowAddBankAccountModal(show: Boolean) {
        _showAddBankAccountModal.value = show
    }

    fun addOrUpdateBankAccount(
        bankName: String,
        balanceToman: Long,
        isTankhah: Boolean,
        cardLast4: String = "",
        customSenderAddresses: List<String> = emptyList()
    ) {
        viewModelScope.launch {
            val finalBalance = repository.addOrUpdateBankAccount(
                bankName = bankName,
                balanceToman = balanceToman,
                isTankhah = isTankhah,
                cardLast4 = cardLast4,
                customSenderAddresses = customSenderAddresses
            )
            _showAddBankAccountModal.value = false
            val msg = if (balanceToman <= 0L && finalBalance > 0L) {
                "حساب «$bankName» اضافه شد و مانده ${PersianNumberUtils.formatToman(finalBalance, true)} از پیامک‌ها خوانده شد ✓"
            } else {
                "منبع مالی «$bankName» با موجودی ${PersianNumberUtils.formatToman(finalBalance, true)} ذخیره شد."
            }
            showBanner(msg)
        }
    }

    fun openAccountEditSheet(account: BankAccountEntity) {
        _selectedAccountCustomSenders.value = repository.getCustomSendersForBank(account.bankName)
        _selectedAccountForEdit.value = account
    }

    fun dismissAccountEditSheet() {
        _selectedAccountForEdit.value = null
        _isRescanningSingleBank.value = false
    }

    fun saveBankAccountEdits(
        originalBankName: String,
        newBankName: String,
        balanceToman: Long,
        isTankhah: Boolean,
        addedSenders: List<String>,
        removedSenders: List<String>
    ) {
        viewModelScope.launch {
            val cleanName = newBankName.trim().ifEmpty { originalBankName }
            repository.updateBankAccountDetails(
                originalBankName = originalBankName,
                newBankName = cleanName,
                balanceToman = balanceToman,
                isTankhah = isTankhah,
                addedSenders = addedSenders,
                removedSenders = removedSenders
            )
            _selectedAccountForEdit.value = null
            showBanner("تغییرات حساب «$cleanName» ذخیره شد ✓")
        }
    }

    fun rescanSingleBank(bankName: String) {
        viewModelScope.launch {
            _isRescanningSingleBank.value = true
            try {
                val discovered = repository.rescanSingleBankFromSms(appContext, bankName)
                val refreshed = dao.getBankAccountByName(bankName)
                if (refreshed != null && _selectedAccountForEdit.value != null) {
                    _selectedAccountForEdit.value = refreshed
                    _selectedAccountCustomSenders.value = repository.getCustomSendersForBank(bankName)
                }
                if (discovered != null) {
                    showBanner("مانده بانک «$bankName» از پیامک‌ها به‌روزرسانی شد: ${PersianNumberUtils.formatToman(discovered, true)} ✓")
                } else {
                    showBanner("پیامکی با مانده قابل استخراج برای «$bankName» پیدا نشد؛ می‌توانید سرشماره پیامکی را اضافه کنید.")
                }
            } finally {
                _isRescanningSingleBank.value = false
            }
        }
    }

    fun deleteBankAccount(bankName: String) {
        viewModelScope.launch {
            repository.deleteBankAccount(bankName)
            _selectedAccountForEdit.value = null
            showBanner("حساب «$bankName» از فهرست حساب‌ها حذف شد.")
        }
    }

    fun setShowHeroMathSheet(show: Boolean) {
        _showHeroMathSheet.value = show
    }

    fun openTransactionDetails(tx: TransactionEntity) {
        _selectedTransactionForDetail.value = tx
    }

    fun dismissTransactionDetails() {
        _selectedTransactionForDetail.value = null
    }

    fun deleteTransaction(tx: TransactionEntity) {
        viewModelScope.launch {
            repository.deleteTransaction(tx.id)
            _selectedTransactionForDetail.value = null
            showBanner("تراکنش از فهرست حذف شد.")
        }
    }

    fun saveTransactionEdits(
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
        viewModelScope.launch {
            repository.updateTransactionDetails(
                originalTx = originalTx,
                amountToman = amountToman,
                balanceAfterToman = balanceAfterToman,
                weekdayDate = weekdayDate,
                timeStr = timeStr,
                cardInfo = cardInfo,
                purpose = purpose,
                storeName = storeName,
                specialTagMode = specialTagMode
            )
            _selectedTransactionForDetail.value = null
            val displayLabel = purpose.trim().ifEmpty { originalTx.displayTitle }
            showBanner("جزئیات تراکنش «$displayLabel» ذخیره شد ✓")
        }
    }

    fun openCommitmentActionSheet(commitment: CommitmentEntity) {
        _selectedCommitmentForAction.value = commitment
    }

    fun dismissCommitmentActionSheet() {
        _selectedCommitmentForAction.value = null
    }

    fun updateCommitmentFromSheet(
        original: CommitmentEntity,
        title: String,
        counterparty: String,
        amountToman: Long,
        dueDateStr: String,
        isSettled: Boolean
    ) {
        viewModelScope.launch {
            repository.updateCommitmentDetails(
                original = original,
                title = title,
                counterparty = counterparty,
                amountToman = amountToman,
                dueDateStr = dueDateStr,
                isSettled = isSettled
            )
            _selectedCommitmentForAction.value = null
            val statusMsg = if (isSettled) "پاس شد / تسویه شد ✓ و تراز ماه بروزرسانی شد." else "تغییرات تعهد ذخیره شد."
            showBanner("«$title» — $statusMsg")
        }
    }

    fun deleteCommitmentFromSheet(commitment: CommitmentEntity) {
        viewModelScope.launch {
            repository.deleteCommitment(commitment.id)
            _selectedCommitmentForAction.value = null
            showBanner("«${commitment.title}» از فهرست تعهدات حذف شد.")
        }
    }

    /**
     * Opens the Voice Bottom Sheet for a new manual cash/voice transaction (`+ ثبت صوتی`)
     * with an EMPTY transcript (`""`) and `cardLabel = "نقدی / دستی"`.
     */
    fun openVoiceOverlayNew() {
        _voiceState.value = VoiceUiState(
            isRecordingSimulated = false,
            recognizedText = "",
            manualAmountText = "",
            storeName = "نقدی / دستی",
            purposeCategory = "",
            cardLabel = "نقدی / دستی",
            totalAmountToman = 0L,
            dongAmountToman = 0L,
            dongFriendName = "",
            userShareToman = 0L,
            targetUntaggedTransaction = null
        )
        _showVoiceOverlaySheet.value = true
    }

    /**
     * Opens the Voice Bottom Sheet for an existing bank SMS transaction (`"بگو بابت چی؟"`).
     * Locks the bank amount and bank name so they are NEVER altered by voice tagging!
     */
    fun openVoiceForUntaggedTransaction(tx: TransactionEntity) {
        _selectedTransactionForDetail.value = null
        val defaultAutoPurposes = setOf(
            "نیازمند تکمیل صوتی",
            "برداشت کارتی / خرید",
            "افزایش موجودی حساب",
            "واریز به حساب",
            "واریز بانکی",
            "دریافت وجه",
            "واریز حقوق ماهانه"
        )
        val initialSpeech = if (!tx.isUntagged &&
            tx.purpose.isNotBlank() &&
            tx.purpose.trim() !in defaultAutoPurposes
        ) {
            tx.purpose
        } else {
            ""
        }

        _voiceState.value = VoiceUiState(
            isRecordingSimulated = false,
            recognizedText = initialSpeech,
            manualAmountText = PersianNumberUtils.formatToman(tx.totalAmountToman),
            storeName = tx.shortBankName,
            purposeCategory = initialSpeech,
            cardLabel = tx.cardInfo,
            totalAmountToman = tx.totalAmountToman,
            dongAmountToman = tx.dongAmountToman,
            dongFriendName = tx.dongFriendName,
            userShareToman = tx.userShareToman,
            targetUntaggedTransaction = tx,
            isIncome = tx.type == "INCOME"
        )
        if (initialSpeech.isNotBlank()) {
            parseAndUpdateVoiceState(initialSpeech, tx)
        }
        _showVoiceOverlaySheet.value = true
    }

    fun dismissVoiceOverlay() {
        _showVoiceOverlaySheet.value = false
    }

    fun onVoiceSpeechChanged(newSpeech: String) {
        val currentTarget = _voiceState.value.targetUntaggedTransaction
        parseAndUpdateVoiceState(newSpeech, currentTarget)
    }

    private fun collectUntaggedBankSmsCandidates(): List<UntaggedSmsCandidate> {
        return uiState.value.transactions
            .asSequence()
            .filter { tx ->
                tx.isUntagged &&
                    tx.totalAmountToman > 0L &&
                    (tx.rawSmsBody.isNotBlank() ||
                        (tx.shortBankName != "نقدی / دستی" && tx.shortBankName != "بانک" && tx.shortBankName != "تراکنش صوتی"))
            }
            .map { tx ->
                UntaggedSmsCandidate(
                    id = tx.id,
                    bankName = tx.shortBankName,
                    amountToman = tx.totalAmountToman,
                    isIncome = tx.type == "INCOME",
                    timestamp = tx.timestamp,
                    jalaliDate = tx.jalaliDate,
                    timeStr = tx.timeStr,
                    cardInfo = tx.cardInfo
                )
            }
            .toList()
    }

    private fun computeSingleMatch(
        amountToman: Long,
        isIncome: Boolean,
        commitmentKind: String?,
        eventDayOffset: Int,
        isApproximateAmount: Boolean = false,
        explicitJalaliDate: String = "",
        isVagueRecentPast: Boolean = false
    ): VoiceSmsMatchResult? {
        if (commitmentKind != null || amountToman <= 0L) return null
        val probe = ParsedVoiceExpense(
            recognizedSpeech = "",
            totalAmountToman = amountToman,
            userShareToman = amountToman,
            dongAmountToman = 0L,
            dongFriendName = "",
            storeName = "",
            purposeCategory = "probe",
            cardLabel = "",
            isIncome = isIncome,
            commitmentKind = null,
            eventDayOffset = eventDayOffset,
            isApproximateAmount = isApproximateAmount,
            explicitJalaliDate = explicitJalaliDate,
            isVagueRecentPast = isVagueRecentPast
        )
        return OrosiVoiceSmsMatcher.matchBatch(
            items = listOf(probe),
            untaggedBankSms = collectUntaggedBankSmsCandidates()
        ).firstOrNull()
    }

    private fun inferDefaultCommitmentCategory(title: String, speech: String): String {
        val combined = PersianNumberUtils.normalizePersianText("$title $speech")
        return when {
            Regex("""(?<=^|\s)(?:قسط|قسطی|اقساط|وام)(?=\s|$)""").containsMatchIn(combined) -> "LOAN_INSTALLMENT"
            Regex("""(?<=^|\s)(?:چک|چکم|چکِ|صیادی)(?=\s|$)""").containsMatchIn(combined) -> "SAYYAD_ISSUED"
            else -> "DONG"
        }
    }

    private fun commitmentSubLabelFor(category: String, isIncome: Boolean): String = when {
        isIncome -> "طلب"
        category == "LOAN_INSTALLMENT" -> "وام و قسط"
        category == "SAYYAD_ISSUED" -> "چک صیادی"
        else -> "بدهی"
    }

    fun onToggleSingleVoiceDestination() {
        val cur = _voiceState.value
        if (cur.targetUntaggedTransaction != null || cur.parsedMultiItems.size > 1) return
        val turningIntoCommitment = cur.commitmentKind == null
        val nextKind = if (turningIntoCommitment) {
            if (cur.isIncome) "RECEIVABLE" else "DEBT"
        } else {
            null
        }
        val nextCategory = if (turningIntoCommitment && !cur.isIncome) {
            cur.commitmentCategory.takeIf { it == "LOAN_INSTALLMENT" || it == "SAYYAD_ISSUED" }
                ?: inferDefaultCommitmentCategory(cur.purposeCategory, cur.recognizedText)
        } else {
            "DONG"
        }
        val nextSubLabel = if (turningIntoCommitment) {
            commitmentSubLabelFor(nextCategory, cur.isIncome)
        } else {
            ""
        }
        val singleMatch = if (!turningIntoCommitment) {
            computeSingleMatch(
                amountToman = cur.totalAmountToman,
                isIncome = cur.isIncome,
                commitmentKind = null,
                eventDayOffset = cur.eventDayOffset,
                isApproximateAmount = cur.isApproximateAmount,
                explicitJalaliDate = cur.explicitJalaliDate,
                isVagueRecentPast = cur.isVagueRecentPast
            )
        } else {
            null
        }
        _voiceState.value = cur.copy(
            commitmentKind = nextKind,
            commitmentCategory = nextCategory,
            commitmentSubLabel = nextSubLabel,
            hasUserOverriddenDestination = true,
            singleAutoMatch = singleMatch,
            singleMatchedBankTxId = singleMatch?.matchedCandidate?.id
        )
    }

    fun onSetSingleVoiceCommitmentCategory(category: String) {
        val cur = _voiceState.value
        if (cur.targetUntaggedTransaction != null || cur.parsedMultiItems.size > 1) return
        val validCat = category.takeIf { it == "LOAN_INSTALLMENT" || it == "SAYYAD_ISSUED" || it == "DONG" } ?: "DONG"
        val isIncomeNow = if (validCat == "LOAN_INSTALLMENT" || validCat == "SAYYAD_ISSUED") false else cur.isIncome
        val nextKind = if (isIncomeNow) "RECEIVABLE" else "DEBT"
        _voiceState.value = cur.copy(
            isIncome = isIncomeNow,
            commitmentKind = nextKind,
            commitmentCategory = validCat,
            commitmentSubLabel = commitmentSubLabelFor(validCat, isIncomeNow),
            hasUserOverriddenDestination = true,
            singleAutoMatch = null,
            singleMatchedBankTxId = null
        )
    }

    fun onToggleMultiVoiceItemDestination(index: Int) {
        val cur = _voiceState.value
        if (cur.targetUntaggedTransaction != null) return
        val currentList = cur.parsedMultiItems.toMutableList()
        if (index !in currentList.indices) return
        val item = currentList[index]
        val turningIntoCommitment = !item.isCommitment
        val nextKind = if (turningIntoCommitment) {
            if (item.isIncome) "RECEIVABLE" else "DEBT"
        } else {
            null
        }
        val nextCategory = if (turningIntoCommitment && !item.isIncome) {
            item.commitmentCategory.takeIf { it == "LOAN_INSTALLMENT" || it == "SAYYAD_ISSUED" }
                ?: inferDefaultCommitmentCategory(item.purposeCategory, item.recognizedSpeech)
        } else {
            "DONG"
        }
        currentList[index] = item.copy(
            commitmentKind = nextKind,
            commitmentCategory = nextCategory,
            commitmentSubLabel = if (turningIntoCommitment) commitmentSubLabelFor(nextCategory, item.isIncome) else "",
            matchedBankTxId = if (turningIntoCommitment) null else item.matchedBankTxId
        )
        val multiMatches = OrosiVoiceSmsMatcher.matchBatch(currentList, collectUntaggedBankSmsCandidates())
        val updatedIds = multiMatches.mapIndexed { i, m ->
            if (currentList[i].isCommitment) null
            else if (i == index) m.matchedCandidate?.id
            else cur.multiMatchedBankTxIds.getOrNull(i) ?: m.matchedCandidate?.id
        }
        _voiceState.value = cur.copy(
            parsedMultiItems = currentList,
            multiAutoMatches = multiMatches,
            multiMatchedBankTxIds = updatedIds
        )
    }

    fun onSetMultiVoiceItemCommitmentCategory(index: Int, category: String) {
        val cur = _voiceState.value
        if (cur.targetUntaggedTransaction != null) return
        val currentList = cur.parsedMultiItems.toMutableList()
        if (index !in currentList.indices) return
        val item = currentList[index]
        val validCat = category.takeIf { it == "LOAN_INSTALLMENT" || it == "SAYYAD_ISSUED" || it == "DONG" } ?: "DONG"
        val isIncomeNow = if (validCat == "LOAN_INSTALLMENT" || validCat == "SAYYAD_ISSUED") false else item.isIncome
        val nextKind = if (isIncomeNow) "RECEIVABLE" else "DEBT"
        currentList[index] = item.copy(
            isIncome = isIncomeNow,
            commitmentKind = nextKind,
            commitmentCategory = validCat,
            commitmentSubLabel = commitmentSubLabelFor(validCat, isIncomeNow),
            matchedBankTxId = null
        )
        val multiMatches = OrosiVoiceSmsMatcher.matchBatch(currentList, collectUntaggedBankSmsCandidates())
        val updatedIds = multiMatches.mapIndexed { i, m ->
            if (currentList[i].isCommitment) null else cur.multiMatchedBankTxIds.getOrNull(i) ?: m.matchedCandidate?.id
        }
        _voiceState.value = cur.copy(
            parsedMultiItems = currentList,
            multiAutoMatches = multiMatches,
            multiMatchedBankTxIds = updatedIds
        )
    }

    fun onSetSingleMatchedBankTx(txId: Long?) {
        if (_voiceState.value.targetUntaggedTransaction != null) return
        _voiceState.value = _voiceState.value.copy(singleMatchedBankTxId = txId)
    }

    fun onSetMultiItemMatchedBankTx(index: Int, txId: Long?) {
        if (_voiceState.value.targetUntaggedTransaction != null) return
        val currentIds = _voiceState.value.multiMatchedBankTxIds.toMutableList()
        while (currentIds.size < _voiceState.value.parsedMultiItems.size) {
            currentIds.add(null)
        }
        if (index !in currentIds.indices) return
        // Ensure 1-to-1 claim uniqueness if user manually links a bank SMS already linked to another card
        if (txId != null) {
            for (i in currentIds.indices) {
                if (i != index && currentIds[i] == txId) {
                    currentIds[i] = null
                }
            }
        }
        currentIds[index] = txId
        _voiceState.value = _voiceState.value.copy(multiMatchedBankTxIds = currentIds)
    }

    fun onVoiceManualAmountChanged(newAmountStr: String) {
        val targetTx = _voiceState.value.targetUntaggedTransaction
        if (targetTx != null) return // Existing bank SMS amount is locked
        val formattedPersian = PersianNumberUtils.formatLiveAmountInput(newAmountStr)
        val numericVal = PersianNumberUtils.parseAmountInputToLong(formattedPersian)
            ?: PersianVoiceSlangParserEngine.parseSpokenPersianAmountToman(newAmountStr)
        val safeDong = _voiceState.value.dongAmountToman.coerceIn(0L, numericVal)
        val userShare = (numericVal - safeDong).coerceAtLeast(0L)
        val cur = _voiceState.value
        val matchRes = computeSingleMatch(
            amountToman = numericVal,
            isIncome = cur.isIncome,
            commitmentKind = cur.commitmentKind,
            eventDayOffset = cur.eventDayOffset,
            isApproximateAmount = false,
            explicitJalaliDate = cur.explicitJalaliDate,
            isVagueRecentPast = cur.isVagueRecentPast
        )
        _voiceState.value = cur.copy(
            manualAmountText = formattedPersian,
            totalAmountToman = numericVal,
            dongAmountToman = safeDong,
            userShareToman = userShare,
            isApproximateAmount = false,
            singleAutoMatch = matchRes,
            singleMatchedBankTxId = matchRes?.matchedCandidate?.id
        )
    }

    fun onVoicePurposeChanged(newPurpose: String) {
        _voiceState.value = _voiceState.value.copy(
            purposeCategory = newPurpose
        )
    }

    fun onVoiceTransactionTypeChanged(isIncome: Boolean) {
        val current = _voiceState.value
        val updatedMulti = current.parsedMultiItems.map { item ->
            val nextKind = item.commitmentKind?.let { if (isIncome) "RECEIVABLE" else "DEBT" }
            val nextCat = if (nextKind == "RECEIVABLE") "DONG" else item.commitmentCategory
            item.copy(
                isIncome = isIncome,
                commitmentKind = nextKind,
                commitmentCategory = nextCat,
                commitmentSubLabel = if (nextKind != null) commitmentSubLabelFor(nextCat, isIncome) else ""
            )
        }
        val updatedSingleKind = current.commitmentKind?.let { if (isIncome) "RECEIVABLE" else "DEBT" }
        val updatedSingleCat = if (updatedSingleKind == "RECEIVABLE") "DONG" else current.commitmentCategory
        val updatedSingleSubLabel = if (updatedSingleKind != null) {
            commitmentSubLabelFor(updatedSingleCat, isIncome)
        } else {
            ""
        }
        val singleMatch = if (current.targetUntaggedTransaction == null && updatedMulti.isEmpty()) {
            computeSingleMatch(
                amountToman = current.totalAmountToman,
                isIncome = isIncome,
                commitmentKind = updatedSingleKind,
                eventDayOffset = current.eventDayOffset,
                isApproximateAmount = current.isApproximateAmount,
                explicitJalaliDate = current.explicitJalaliDate,
                isVagueRecentPast = current.isVagueRecentPast
            )
        } else null
        val multiMatches = if (current.targetUntaggedTransaction == null && updatedMulti.size > 1) {
            OrosiVoiceSmsMatcher.matchBatch(updatedMulti, collectUntaggedBankSmsCandidates())
        } else emptyList()
        _voiceState.value = current.copy(
            isIncome = isIncome,
            hasUserOverriddenIncomeType = true,
            parsedMultiItems = updatedMulti,
            commitmentKind = updatedSingleKind,
            commitmentCategory = updatedSingleCat,
            commitmentSubLabel = updatedSingleSubLabel,
            singleAutoMatch = singleMatch,
            singleMatchedBankTxId = singleMatch?.matchedCandidate?.id,
            multiAutoMatches = multiMatches,
            multiMatchedBankTxIds = multiMatches.map { it.matchedCandidate?.id }
        )
    }

    fun onToggleMultiVoiceItemType(index: Int) {
        val currentList = _voiceState.value.parsedMultiItems.toMutableList()
        if (index !in currentList.indices) return
        val existingItem = currentList[index]
        onSetMultiVoiceItemType(index, !existingItem.isIncome)
    }

    fun onSetMultiVoiceItemType(index: Int, isIncome: Boolean) {
        val currentList = _voiceState.value.parsedMultiItems.toMutableList()
        if (index !in currentList.indices) return
        val existingItem = currentList[index]
        if (existingItem.isIncome == isIncome) return
        currentList[index] = existingItem.copy(
            isIncome = isIncome,
            commitmentKind = existingItem.commitmentKind?.let { if (isIncome) "RECEIVABLE" else "DEBT" }
        )
        val combinedNote = PersianVoiceSlangParserEngine.formatCombinedMultiVoiceNote(currentList)
        val multiMatches = if (_voiceState.value.targetUntaggedTransaction == null) {
            OrosiVoiceSmsMatcher.matchBatch(currentList, collectUntaggedBankSmsCandidates())
        } else emptyList()
        _voiceState.value = _voiceState.value.copy(
            parsedMultiItems = currentList,
            purposeCategory = combinedNote,
            multiAutoMatches = multiMatches,
            multiMatchedBankTxIds = multiMatches.map { it.matchedCandidate?.id }
        )
    }

    fun onToggleMultiVoiceItemScale(index: Int) {
        if (_voiceState.value.targetUntaggedTransaction != null) return
        val currentList = _voiceState.value.parsedMultiItems
        if (index !in currentList.indices) return
        val existing = currentList[index]
        val curAmt = existing.totalAmountToman
        val nextAmt = when {
            curAmt in 1_000L..999_000L -> curAmt * 1_000L
            curAmt >= 1_000_000L && curAmt % 1_000L == 0L -> curAmt / 1_000L
            else -> return
        }
        onUpdateMultiVoiceItem(index, existing.purposeCategory, nextAmt.toString())
    }

    fun onToggleSingleVoiceScale() {
        val current = _voiceState.value
        if (current.targetUntaggedTransaction != null) return
        val curAmt = current.totalAmountToman
        val nextAmt = when {
            curAmt in 1_000L..999_000L -> curAmt * 1_000L
            curAmt >= 1_000_000L && curAmt % 1_000L == 0L -> curAmt / 1_000L
            else -> return
        }
        onVoiceManualAmountChanged(nextAmt.toString())
    }

    fun onUpdateMultiVoiceItem(index: Int, newTitle: String, newAmountStr: String) {
        val currentList = _voiceState.value.parsedMultiItems.toMutableList()
        if (index !in currentList.indices) return
        val existingItem = currentList[index]
        val parsedAmount = PersianNumberUtils.parseAmountInputToLong(newAmountStr) ?: 0L
        val amountChanged = parsedAmount != existingItem.totalAmountToman
        currentList[index] = existingItem.copy(
            purposeCategory = newTitle,
            totalAmountToman = parsedAmount,
            userShareToman = parsedAmount,
            isApproximateAmount = if (amountChanged) false else existingItem.isApproximateAmount
        )
        val targetTx = _voiceState.value.targetUntaggedTransaction
        val combinedNote = PersianVoiceSlangParserEngine.formatCombinedMultiVoiceNote(currentList)
        val multiSum = currentList.sumOf { it.totalAmountToman }
        val newTotal = if (targetTx != null) {
            targetTx.totalAmountToman
        } else {
            multiSum
        }
        val mismatchAmount = if (targetTx != null && multiSum != targetTx.totalAmountToman) {
            multiSum
        } else {
            null
        }
        val summaryMismatch = if (targetTx == null) {
            PersianVoiceSlangParserEngine.detectSpokenSummaryTotalMismatch(
                rawSpeech = _voiceState.value.recognizedText,
                multiItems = currentList
            )
        } else {
            null
        }
        val multiMatches = if (targetTx == null) {
            OrosiVoiceSmsMatcher.matchBatch(currentList, collectUntaggedBankSmsCandidates())
        } else {
            emptyList()
        }
        val preservedIds = _voiceState.value.multiMatchedBankTxIds
        val updatedIds = multiMatches.mapIndexed { i, match ->
            if (i == index) {
                match.matchedCandidate?.id
            } else {
                preservedIds.getOrNull(i) ?: match.matchedCandidate?.id
            }
        }
        _voiceState.value = _voiceState.value.copy(
            parsedMultiItems = currentList,
            purposeCategory = combinedNote,
            totalAmountToman = newTotal,
            userShareToman = newTotal,
            manualAmountText = PersianNumberUtils.formatToman(newTotal),
            hasBankAmountMismatch = mismatchAmount != null,
            conflictingSpokenAmountToman = mismatchAmount ?: 0L,
            hasSummaryTotalMismatch = summaryMismatch != null,
            statedSummaryHeaderToman = summaryMismatch ?: 0L,
            multiAutoMatches = multiMatches,
            multiMatchedBankTxIds = updatedIds
        )
    }

    private fun parseAndUpdateVoiceState(speech: String, targetTx: TransactionEntity?) {
        val defaultBank = targetTx?.shortBankName ?: "نقدی / دستی"
        val multiItems = PersianVoiceSlangParserEngine.parseMultiVoiceCommands(
            rawSpeech = speech,
            defaultBankName = defaultBank,
            fallbackTotalToman = targetTx?.totalAmountToman
        )
        val parsed = PersianVoiceSlangParserEngine.parseVoiceCommand(
            rawSpeech = speech,
            fallbackTotalToman = targetTx?.totalAmountToman,
            defaultBankName = defaultBank
        )
        val currentManualTyped = PersianNumberUtils.parseAmountInputToLong(_voiceState.value.manualAmountText) ?: 0L

        val effectiveTotal = when {
            targetTx != null -> targetTx.totalAmountToman
            parsed.totalAmountToman > 0L -> parsed.totalAmountToman
            speech.isBlank() -> 0L
            else -> currentManualTyped
        }
        val effectiveManualText = when {
            targetTx != null -> PersianNumberUtils.formatToman(targetTx.totalAmountToman)
            parsed.totalAmountToman > 0L -> PersianNumberUtils.formatToman(parsed.totalAmountToman)
            speech.isBlank() -> ""
            else -> _voiceState.value.manualAmountText
        }
        val safeDong = parsed.dongAmountToman.coerceIn(0L, effectiveTotal)
        val effectiveUserShare = (effectiveTotal - safeDong).coerceAtLeast(0L)

        val mismatchAmount = if (targetTx != null) {
            PersianVoiceSlangParserEngine.detectBankSmsAmountMismatch(
                rawSpeech = speech,
                fallbackTotalToman = targetTx.totalAmountToman,
                multiItems = multiItems
            )
        } else {
            null
        }

        val summaryMismatchAmount = if (targetTx == null) {
            PersianVoiceSlangParserEngine.detectSpokenSummaryTotalMismatch(
                rawSpeech = speech,
                multiItems = multiItems
            )
        } else {
            null
        }

        val hasMixedDirections = multiItems.size > 1 &&
            ((multiItems.any { it.isIncome } && multiItems.any { !it.isIncome }) ||
                multiItems.any { it.isCommitment })

        val keepUserOverride = _voiceState.value.hasUserOverriddenIncomeType &&
            !hasMixedDirections &&
            parsed.commitmentKind == null

        val resolvedIsIncome = when {
            targetTx != null -> targetTx.type == "INCOME"
            keepUserOverride -> _voiceState.value.isIncome
            else -> parsed.isIncome
        }

        val effectiveMultiItems = if (multiItems.size > 1) {
            when {
                targetTx != null -> multiItems.map { it.copy(isIncome = targetTx.type == "INCOME", commitmentKind = null) }
                hasMixedDirections -> multiItems
                keepUserOverride -> multiItems.map { it.copy(isIncome = resolvedIsIncome) }
                else -> multiItems
            }
        } else {
            emptyList()
        }

        val resolvedSingleCommitmentKind = if (targetTx == null && effectiveMultiItems.isEmpty()) parsed.commitmentKind else null
        val resolvedSingleCommitmentCategory = if (resolvedSingleCommitmentKind != null) parsed.commitmentCategory else "DONG"
        val resolvedSingleDayOffset = if (targetTx == null && effectiveMultiItems.isEmpty()) parsed.eventDayOffset else 0
        val resolvedSingleExplicitDate = if (targetTx == null && effectiveMultiItems.isEmpty()) parsed.explicitJalaliDate else ""
        val resolvedSingleVaguePast = targetTx == null && effectiveMultiItems.isEmpty() && parsed.isVagueRecentPast
        val resolvedSingleApprox = targetTx == null && effectiveMultiItems.isEmpty() && parsed.isApproximateAmount

        val candidates = if (targetTx == null) collectUntaggedBankSmsCandidates() else emptyList()
        val multiMatches = if (targetTx == null && effectiveMultiItems.size > 1) {
            OrosiVoiceSmsMatcher.matchBatch(effectiveMultiItems, candidates)
        } else {
            emptyList()
        }
        val singleMatch = if (targetTx == null && effectiveMultiItems.isEmpty()) {
            computeSingleMatch(
                amountToman = effectiveTotal,
                isIncome = resolvedIsIncome,
                commitmentKind = resolvedSingleCommitmentKind,
                eventDayOffset = resolvedSingleDayOffset,
                isApproximateAmount = resolvedSingleApprox,
                explicitJalaliDate = resolvedSingleExplicitDate,
                isVagueRecentPast = resolvedSingleVaguePast
            )
        } else {
            null
        }

        _voiceState.value = _voiceState.value.copy(
            recognizedText = speech,
            manualAmountText = effectiveManualText,
            storeName = targetTx?.shortBankName ?: parsed.storeName.ifBlank { "نقدی / دستی" },
            purposeCategory = parsed.purposeCategory,
            cardLabel = targetTx?.cardInfo ?: "نقدی / دستی",
            totalAmountToman = effectiveTotal,
            dongAmountToman = safeDong,
            dongFriendName = parsed.dongFriendName,
            userShareToman = effectiveUserShare,
            targetUntaggedTransaction = targetTx,
            parsedMultiItems = effectiveMultiItems,
            hasBankAmountMismatch = mismatchAmount != null,
            conflictingSpokenAmountToman = mismatchAmount ?: 0L,
            isIncome = resolvedIsIncome,
            hasUserOverriddenIncomeType = keepUserOverride,
            hasSummaryTotalMismatch = summaryMismatchAmount != null,
            statedSummaryHeaderToman = summaryMismatchAmount ?: 0L,
            commitmentKind = resolvedSingleCommitmentKind,
            dueDateHint = if (targetTx == null && effectiveMultiItems.isEmpty()) parsed.dueDateHint else "",
            eventDayOffset = resolvedSingleDayOffset,
            commitmentSubLabel = if (targetTx == null && effectiveMultiItems.isEmpty()) parsed.commitmentSubLabel else "",
            singleAutoMatch = singleMatch,
            singleMatchedBankTxId = singleMatch?.matchedCandidate?.id,
            multiAutoMatches = multiMatches,
            multiMatchedBankTxIds = multiMatches.map { it.matchedCandidate?.id },
            isApproximateAmount = resolvedSingleApprox,
            explicitJalaliDate = resolvedSingleExplicitDate,
            isVagueRecentPast = resolvedSingleVaguePast,
            commitmentCategory = resolvedSingleCommitmentCategory,
            hasUserOverriddenDestination = false
        )
    }

    /**
     * Fallback voice simulation when running on a web emulator without a microphone.
     * Uses realistic everyday phrases like "پفک خریدم", "شارژ ایرانسل", "۷۰ تومن نانوایی"
     * and NEVER uses "اسنپ‌مارکت"!
     */
    fun triggerVoiceSimulation(samplePhrase: String? = null) {
        viewModelScope.launch {
            _voiceState.value = _voiceState.value.copy(isRecordingSimulated = true)
            delay(500L)
            if (samplePhrase != null) {
                parseAndUpdateVoiceState(samplePhrase, _voiceState.value.targetUntaggedTransaction)
            } else if (_voiceState.value.recognizedText.isBlank()) {
                val targetTx = _voiceState.value.targetUntaggedTransaction
                val phrase = when {
                    targetTx != null && targetTx.totalAmountToman == 49_500L -> "شارژ ایرانسل"
                    targetTx != null -> "پفک خریدم"
                    else -> "۷۰ تومن نانوایی"
                }
                parseAndUpdateVoiceState(phrase, targetTx)
            }
            _voiceState.value = _voiceState.value.copy(isRecordingSimulated = false)
        }
    }

    fun confirmVoiceEntry() {
        viewModelScope.launch {
            val v = _voiceState.value
            val targetTx = v.targetUntaggedTransaction

            if (targetTx != null && v.hasBankAmountMismatch) {
                val spokenFmt = PersianNumberUtils.formatToman(v.conflictingSpokenAmountToman, true)
                val bankFmt = PersianNumberUtils.formatToman(targetTx.totalAmountToman, true)
                showBanner("مبلغ گفته‌شده ($spokenFmt) با مبلغ پیامک بانک ($bankFmt) مطابقت ندارد. لطفاً اصلاح کنید.")
                return@launch
            }

            // Check if user has multi-clause items (including any inline edits or mixed income/expense items)
            val activeMultiItems = if (v.parsedMultiItems.size > 1) {
                v.parsedMultiItems
                    .filter { it.totalAmountToman > 0L && it.purposeCategory.isNotBlank() }
            } else {
                val rawMulti = PersianVoiceSlangParserEngine.parseMultiVoiceCommands(
                    rawSpeech = v.recognizedText,
                    defaultBankName = targetTx?.shortBankName ?: "نقدی / دستی",
                    fallbackTotalToman = targetTx?.totalAmountToman
                )
                val hasMixed = rawMulti.size > 1 && rawMulti.any { it.isIncome } && rawMulti.any { !it.isIncome }
                if (hasMixed) {
                    rawMulti
                } else {
                    rawMulti.map { it.copy(isIncome = v.isIncome) }
                }
            }

            if (targetTx == null && activeMultiItems.size > 1) {
                val itemsWithMatches = activeMultiItems.mapIndexed { idx, item ->
                    val linkedId = if (v.parsedMultiItems.size > 1) v.multiMatchedBankTxIds.getOrNull(idx) else null
                    item.copy(matchedBankTxId = if (item.isCommitment) null else linkedId)
                }
                val saveSummary = repository.confirmMultiVoiceExpenses(itemsWithMatches)
                val parts = mutableListOf<String>()
                if (saveSummary.taggedSmsCount > 0) {
                    parts.add("${PersianNumberUtils.toPersianDigits(saveSummary.taggedSmsCount.toString())} پیامک بانکی عنوان‌گذاری شد")
                }
                if (saveSummary.newCashTxCount > 0) {
                    parts.add("${PersianNumberUtils.toPersianDigits(saveSummary.newCashTxCount.toString())} تراکنش نقدی ثبت شد")
                }
                if (saveSummary.commitmentCount > 0) {
                    parts.add("${PersianNumberUtils.toPersianDigits(saveSummary.commitmentCount.toString())} تعهد ثبت شد")
                }
                val bannerMsg = if (parts.isNotEmpty()) {
                    parts.joinToString(" • ") + " ✓"
                } else {
                    val combinedSummary = PersianVoiceSlangParserEngine.formatCombinedMultiVoiceNote(activeMultiItems)
                    "${PersianNumberUtils.toPersianDigits(activeMultiItems.size.toString())} تراکنش مجزا ثبت شد: $combinedSummary ✓"
                }
                _showVoiceOverlaySheet.value = false
                _voiceState.value = VoiceUiState()
                showBanner(bannerMsg)
                return@launch
            }

            val typedManualAmount = PersianNumberUtils.parseAmountInputToLong(v.manualAmountText)
                ?: PersianVoiceSlangParserEngine.parseSpokenPersianAmountToman(v.manualAmountText)

            val parsedFromSpeech = PersianVoiceSlangParserEngine.parseVoiceCommand(
                rawSpeech = v.recognizedText,
                fallbackTotalToman = targetTx?.totalAmountToman,
                defaultBankName = targetTx?.shortBankName ?: "نقدی / دستی"
            )

            val finalTotalAmount = when {
                targetTx != null -> targetTx.totalAmountToman
                typedManualAmount > 0L -> typedManualAmount
                parsedFromSpeech.totalAmountToman > 0L -> parsedFromSpeech.totalAmountToman
                else -> 0L
            }

            val finalPurpose = when {
                targetTx != null && activeMultiItems.size > 1 ->
                    PersianVoiceSlangParserEngine.formatCombinedMultiVoiceNote(activeMultiItems)
                v.purposeCategory.isNotBlank() -> v.purposeCategory.trim()
                parsedFromSpeech.purposeCategory.isNotBlank() -> parsedFromSpeech.purposeCategory.trim()
                v.commitmentKind == "RECEIVABLE" -> "طلب"
                v.commitmentKind == "DEBT" -> "بدهی"
                v.isIncome -> "دریافت وجه"
                else -> ""
            }

            if (finalPurpose.isBlank()) {
                showBanner("لطفاً توضیح تراکنش را بگویید یا بنویسید (مثلاً: ۷۰ تومن نانوایی).")
                return@launch
            }

            if (targetTx == null && finalTotalAmount <= 0L) {
                showBanner("لطفاً مبلغ تراکنش را (بیشتر از ۰ تومان) بگویید یا در کادر مبلغ وارد کنید.")
                return@launch
            }

            val safeDong = parsedFromSpeech.dongAmountToman.coerceIn(0L, finalTotalAmount)
            val finalUserShare = (finalTotalAmount - safeDong).coerceAtLeast(0L)
            val effectiveCommitmentKind = if (targetTx == null) {
                if (v.hasUserOverriddenDestination) v.commitmentKind
                else (v.commitmentKind ?: parsedFromSpeech.commitmentKind)
            } else {
                null
            }
            val effectiveCommitmentCategory = if (effectiveCommitmentKind != null) {
                if (effectiveCommitmentKind == "RECEIVABLE") "DONG"
                else v.commitmentCategory.ifBlank { parsedFromSpeech.commitmentCategory }
            } else {
                "DONG"
            }
            val effectiveDueDateHint = if (effectiveCommitmentKind != null) {
                v.dueDateHint.ifBlank { parsedFromSpeech.dueDateHint }
            } else {
                ""
            }

            val finalParsed = parsedFromSpeech.copy(
                purposeCategory = finalPurpose,
                totalAmountToman = finalTotalAmount,
                dongAmountToman = safeDong,
                userShareToman = finalUserShare,
                storeName = targetTx?.shortBankName ?: parsedFromSpeech.merchant.ifBlank { "نقدی / دستی" },
                cardLabel = targetTx?.cardInfo ?: "نقدی / دستی",
                isIncome = v.isIncome,
                commitmentKind = effectiveCommitmentKind,
                dueDateHint = effectiveDueDateHint,
                eventDayOffset = if (targetTx == null) (v.eventDayOffset.takeIf { it != 0 } ?: parsedFromSpeech.eventDayOffset) else 0,
                commitmentSubLabel = if (effectiveCommitmentKind != null) v.commitmentSubLabel.ifBlank { parsedFromSpeech.commitmentSubLabel } else "",
                isApproximateAmount = v.isApproximateAmount,
                explicitJalaliDate = if (targetTx == null && effectiveCommitmentKind == null) {
                    v.explicitJalaliDate.ifBlank { parsedFromSpeech.explicitJalaliDate }
                } else {
                    ""
                },
                isVagueRecentPast = v.isVagueRecentPast,
                commitmentCategory = effectiveCommitmentCategory
            )

            val effectiveTargetBankTxId = targetTx?.id
                ?: if (effectiveCommitmentKind == null) v.singleMatchedBankTxId else null
            val linkedSingleCandidate = if (targetTx == null && effectiveTargetBankTxId != null) {
                v.singleAutoMatch?.matchedCandidate?.takeIf { it.id == effectiveTargetBankTxId }
                    ?: v.singleAutoMatch?.suggestedCandidate?.takeIf { it.id == effectiveTargetBankTxId }
            } else null

            repository.confirmVoiceExpense(finalParsed, effectiveTargetBankTxId)
            val msg = if (finalParsed.isCommitment) {
                val kindLabel = when {
                    finalParsed.commitmentKind == "RECEIVABLE" -> "طلب"
                    finalParsed.commitmentCategory == "LOAN_INSTALLMENT" -> "قسط"
                    finalParsed.commitmentCategory == "SAYYAD_ISSUED" -> "چک"
                    else -> "بدهی"
                }
                val tabLabel = when {
                    finalParsed.commitmentCategory == "LOAN_INSTALLMENT" -> "وام و قسط"
                    finalParsed.commitmentCategory == "SAYYAD_ISSUED" -> "چک صیادی"
                    else -> "تعهدات"
                }
                "$kindLabel «${finalParsed.purposeCategory}» (${PersianNumberUtils.formatToman(finalTotalAmount, true)}) در بخش $tabLabel ثبت شد ✓"
            } else if (linkedSingleCandidate != null) {
                "پیامک بانک ${linkedSingleCandidate.bankName} با عنوان «${finalParsed.purposeCategory}» تکمیل شد ✓"
            } else if (finalParsed.dongAmountToman > 0L && finalParsed.dongFriendName.isNotBlank()) {
                "ثبت شد! «${finalParsed.purposeCategory}» — فقط سهم شما (${PersianNumberUtils.formatToman(finalParsed.userShareToman, true)}) کسر شد."
            } else if (finalParsed.isIncome) {
                "واریزی «${finalParsed.purposeCategory}» (${PersianNumberUtils.formatIncomeToman(finalTotalAmount, true)}) ثبت شد ✓"
            } else {
                "تراکنش با عنوان «${finalParsed.purposeCategory}» (${PersianNumberUtils.formatToman(finalTotalAmount, true)}) ثبت شد ✓"
            }
            _showVoiceOverlaySheet.value = false
            _voiceState.value = VoiceUiState()
            showBanner(msg)
        }
    }

    fun setShowSmsSimulator(show: Boolean) {
        _showSmsSimulatorModal.value = show
    }

    fun previewBankSms(rawSms: String): ParsedSmsResult {
        return BankSmsParserEngine.parseBankSms(rawSms)
    }

    fun processBankSms(rawSms: String) {
        viewModelScope.launch {
            val result = repository.processIncomingBankSms(rawSms)
            _showSmsSimulatorModal.value = false
            if (result != null) {
                showBanner(result.summaryMessage)
            }
        }
    }

    /**
     * Silent, 100% automatic zero-click background SMS sync triggered on app launch,
     * permission grant, Lifecycle.Event.ON_RESUME, and ContentObserver on Telephony.Sms.CONTENT_URI.
     */
    fun autoSyncSmsSilently() {
        viewModelScope.launch {
            try {
                repository.scanDeviceSmsInboxLast30Days(appContext, daysBack = 3650)
                syncInitialSelectedMonthWithTransactions()
            } finally {
                _isSyncingSms.value = false
            }
        }
    }

    fun scanDeviceSmsInboxLast30Days() {
        viewModelScope.launch {
            val count = repository.scanDeviceSmsInboxLast30Days(appContext, daysBack = 3650)
            syncInitialSelectedMonthWithTransactions()
            _showSmsSimulatorModal.value = false
            if (count > 0) {
                val countStr = PersianNumberUtils.toPersianDigits(count.toString())
                showBanner("$countStr پیامک بانکی جدید به‌صورت خودکار همگام‌سازی شد.")
            }
        }
    }

    /**
     * Triggered when the user taps "پاکسازی و بازخوانی تمیز پیامک‌های واقعی" in the status modal.
     * Shows a sleek loading indicator (`isSyncingSms = true`) instead of flashing 0 Toman.
     */
    fun cleanPurgeAndRescanRealSms() {
        viewModelScope.launch {
            _showSmsSimulatorModal.value = false
            _isSyncingSms.value = true
            try {
                val count = repository.cleanPurgeAndRescanRealSms(appContext)
                syncInitialSelectedMonthWithTransactions()
                val countStr = PersianNumberUtils.toPersianDigits(count.toString())
                if (count > 0) {
                    showBanner("پاکسازی انجام شد و $countStr پیامک واقعی بانک با مانده حساب‌ها بازخوانی شد ✓")
                } else {
                    showBanner("پاکسازی و بازخوانی تمیز پیامک‌های بانکی انجام شد ✓")
                }
            } finally {
                _isSyncingSms.value = false
            }
        }
    }

    fun setShowPaydayBackupModal(show: Boolean) {
        if (show) {
            viewModelScope.launch {
                _backupJsonText.value = repository.exportBackupJson()
                _showPaydayBackupModal.value = true
            }
        } else {
            _showPaydayBackupModal.value = false
        }
    }

    fun savePaydayAndGoldSettings(
        paydayStartDay: Int,
        paydayCycleLabel: String,
        autoDetectSalary: Boolean,
        goldPrice18kToman: Long,
        dailySafeBudgetToman: Long
    ) {
        viewModelScope.launch {
            repository.updatePaydayAndGoldSettings(
                paydayStartDay = paydayStartDay,
                paydayCycleLabel = paydayCycleLabel,
                autoDetectSalary = autoDetectSalary,
                goldPrice18kToman = goldPrice18kToman,
                dailySafeBudgetToman = dailySafeBudgetToman
            )
            _showPaydayBackupModal.value = false
            showBanner("تنظیمات ماه حقوقی ذخیره شد.")
        }
    }

    fun importBackupJson(json: String) {
        viewModelScope.launch {
            val ok = repository.importBackupJson(json)
            if (ok) {
                _showPaydayBackupModal.value = false
                showBanner("پشتیبان دیتابیس آفلاین با موفقیت بازیابی شد.")
            } else {
                showBanner("خطا در ساختار فایل پشتیبان JSON.")
            }
        }
    }

    fun resetDatabaseToDefault() {
        viewModelScope.launch {
            repository.resetToDefaultSeedData()
            _backupJsonText.value = repository.exportBackupJson()
            showBanner("داده‌های برنامه به حالت پیش‌فرض بازنشانی شد.")
        }
    }

    fun setShowAddCommitmentModal(show: Boolean) {
        _showAddCommitmentModal.value = show
    }

    fun addNewCommitment(
        category: String,
        title: String,
        counterparty: String,
        amountToman: Long,
        dueDateStr: String,
        sayyadId: String
    ) {
        viewModelScope.launch {
            repository.addNewCommitment(category, title, counterparty, amountToman, dueDateStr, sayyadId)
            _showAddCommitmentModal.value = false
            showBanner("تعهد جدید «$title» ثبت شد.")
        }
    }

    fun toggleCommitmentSettled(commitment: CommitmentEntity) {
        viewModelScope.launch {
            repository.toggleCommitmentSettled(commitment)
            val msg = if (!commitment.isSettled) {
                "«${commitment.title}» پاس شد / تسویه شد ✓"
            } else {
                "وضعیت «${commitment.title}» به حالت فعال بازگشت."
            }
            showBanner(msg)
        }
    }

    fun dismissBanner() {
        _statusBannerMessage.value = null
    }

    private fun showBanner(message: String) {
        viewModelScope.launch {
            _statusBannerMessage.value = message
            delay(4000L)
            if (_statusBannerMessage.value == message) {
                _statusBannerMessage.value = null
            }
        }
    }
}

private data class Tuple4<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)

private data class Tuple5<A, B, C, D, E>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E
)
