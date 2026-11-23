package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.TransactionEntity
import com.example.domain.JalaliCalendarUtils
import com.example.domain.PersianNumberUtils
import com.example.ui.components.BankLogoAvatar
import com.example.ui.components.JibemanAssetLogoCache
import com.example.ui.components.OrosiAmountText
import com.example.ui.theme.AlabasterSilk
import com.example.ui.theme.NishapurTurquoiseBright
import com.example.ui.theme.NishapurTurquoiseDeep
import com.example.ui.theme.NishapurTurquoiseSoft
import com.example.ui.theme.OrosiHairline
import com.example.ui.theme.OrosiOnSurfaceVariant
import com.example.ui.theme.OrosiSurfaceContainer
import com.example.ui.theme.OrosiSurfaceHigh
import com.example.ui.theme.OrosiSurfaceLow
import com.example.ui.theme.PersianLapisLight
import com.example.ui.theme.RubyRose
import com.example.ui.theme.RubyRoseSoft
import com.example.ui.theme.SaffronGoldLight

private val jalaliMonthNames = arrayOf(
    "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
    "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
)

// Hoisted static shapes and colors matching the approved AccountsScreen minimalist aesthetic
private val TxCardShape20 = RoundedCornerShape(20.dp)
private val TxMicPillShape12 = RoundedCornerShape(12.dp)
private val TxBarPillShape = RoundedCornerShape(50)
private val TxIncomeIconBg = NishapurTurquoiseBright.copy(alpha = 0.20f)
private val TxExpenseIconBg = RubyRoseSoft.copy(alpha = 0.18f)
private val TxIncomeTagBg = NishapurTurquoiseDeep.copy(alpha = 0.72f)
private val TxExpenseTagBg = RubyRose.copy(alpha = 0.22f)
private val TxDividerColor = OrosiHairline.copy(alpha = 0.50f)
private val TxUnitIncomeColor = NishapurTurquoiseSoft.copy(alpha = 0.80f)
private val TxUnitExpenseColor = RubyRoseSoft.copy(alpha = 0.80f)
private val TxUnitMutedColor = OrosiOnSurfaceVariant.copy(alpha = 0.80f)
private val TxBarIncomeColor = NishapurTurquoiseBright.copy(alpha = 0.78f)
private val TxBarExpenseColor = RubyRoseSoft.copy(alpha = 0.72f)
private val TxBarGoldColor = SaffronGoldLight.copy(alpha = 0.72f)
private val TxBarMutedColor = OrosiOnSurfaceVariant.copy(alpha = 0.45f)

@Immutable
private data class LedgerTxRowUi(
    val transaction: TransactionEntity,
    val shortBank: String,
    val logoKey: String?,
    val metaLabel: String,
    val isIncome: Boolean,
    val accentColor: Color,
    val unitColor: Color,
    val edgeBarColor: Color,
    val displayAmountToman: Long,
    val formattedAmountRun: String,
    val hasCustomNote: Boolean,
    val primaryTitle: String,
    val isLongTitle: Boolean
)

@Immutable
private data class DayTransactionGroup(
    val dateSortKey: Int,
    val dateHeaderLabel: String,
    val dayNetSumToman: Long,
    val formattedDayNetRun: String,
    val rows: List<LedgerTxRowUi>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    transactions: List<TransactionEntity>,
    selectedYearMonthKey: Int,
    hideAmounts: Boolean,
    isLoadingSelectedMonth: Boolean = false,
    onChangeMonth: (Int) -> Unit,
    onSelectMonth: (Int) -> Unit = {},
    onRescanMonth: () -> Unit = {},
    onTransactionClick: (TransactionEntity) -> Unit,
    onUntaggedMicClick: (TransactionEntity) -> Unit,
    onDeleteTransaction: (TransactionEntity) -> Unit = {},
    onBackToHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBackToHome)
    var showMonthPickerSheet by remember { mutableStateOf(false) }

    val currentYearMonthKey = remember {
        JalaliCalendarUtils.fromTimestamp(System.currentTimeMillis()).yearMonthKey
    }
    val canGoNextMonth = selectedYearMonthKey < currentYearMonthKey
    val selectedYear = selectedYearMonthKey / 100
    val selectedMonth = (selectedYearMonthKey % 100).coerceIn(1, 12)
    val monthName = jalaliMonthNames[selectedMonth - 1]
    val monthHeaderLabel = "$monthName ${PersianNumberUtils.toPersianDigits(selectedYear.toString())}"

    val filteredTransactions = remember(transactions, selectedYear, selectedMonth) {
        transactions
            .filter { tx ->
                tx.totalAmountToman > 0L &&
                    tx.jalaliYear == selectedYear &&
                    tx.jalaliMonth == selectedMonth
            }
            .sortedWith(
                compareByDescending<TransactionEntity> { it.timestampMillis }
                    .thenByDescending { it.id }
            )
    }

    val monthlyIncomeToman = remember(filteredTransactions) {
        filteredTransactions.filter { it.type == "INCOME" }.sumOf { it.totalAmountToman }
    }
    val monthlyExpenseToman = remember(filteredTransactions) {
        filteredTransactions.filter { it.type == "EXPENSE" }.sumOf { it.totalAmountToman }
    }

    // Group strictly after sorting by timestampMillis DESC so date headers are chronological newest -> oldest.
    // Every transaction renders as its own independent minimalist card matching AccountsScreen for 120Hz fluidity.
    val sortedDayGroups = remember(filteredTransactions) {
        filteredTransactions
            .groupBy { it.jalaliDate }
            .entries
            .map { (dateHeaderLabel, dayTxs) ->
                val sortedDayTxs = dayTxs.sortedWith(
                    compareByDescending<TransactionEntity> { it.timestampMillis }
                        .thenByDescending { it.id }
                )
                val firstTx = sortedDayTxs.first()
                val sortKey = firstTx.jalaliYear * 10000 + firstTx.jalaliMonth * 100 + firstTx.jalaliDay
                val netSum = sortedDayTxs.sumOf { tx ->
                    if (tx.type == "INCOME") tx.totalAmountToman else -tx.totalAmountToman
                }
                val rowModels = sortedDayTxs.map { tx ->
                    val isIncome = tx.type == "INCOME"
                    val shortBank = tx.shortBankName
                    val hasCustomNote = !tx.isUntagged &&
                        tx.purpose.isNotBlank() &&
                        tx.purpose != "نیازمند تکمیل صوتی" &&
                        tx.purpose != "برداشت کارتی / خرید" &&
                        tx.purpose != "افزایش موجودی حساب" &&
                        tx.purpose != "واریز به حساب"
                    val primaryTitle = when {
                        hasCustomNote -> tx.title.ifBlank { tx.displayTitle }
                        tx.isUntagged -> if (isIncome) "واریز به کارت ($shortBank)" else "خرید کارتی ($shortBank)"
                        else -> tx.title.ifBlank { tx.displayTitle }
                    }
                    val dispAmt = tx.userShareToman.takeIf { it > 0L } ?: tx.totalAmountToman
                    val amountColor = when (tx.type) {
                        "INCOME" -> NishapurTurquoiseSoft
                        "GOLD_SAVE" -> SaffronGoldLight
                        "SELF_TRANSFER" -> OrosiOnSurfaceVariant
                        else -> RubyRoseSoft
                    }
                    val uColor = when (tx.type) {
                        "INCOME" -> TxUnitIncomeColor
                        "GOLD_SAVE" -> SaffronGoldLight.copy(alpha = 0.80f)
                        "SELF_TRANSFER" -> TxUnitMutedColor
                        else -> TxUnitExpenseColor
                    }
                    val barColor = when (tx.type) {
                        "INCOME" -> TxBarIncomeColor
                        "GOLD_SAVE" -> TxBarGoldColor
                        "SELF_TRANSFER" -> TxBarMutedColor
                        else -> TxBarExpenseColor
                    }
                    LedgerTxRowUi(
                        transaction = tx,
                        shortBank = shortBank,
                        logoKey = JibemanAssetLogoCache.resolveBankLogoKey(shortBank),
                        metaLabel = if (tx.timeStr.isNotBlank()) "$shortBank • ${tx.timeStr}" else shortBank,
                        isIncome = isIncome,
                        accentColor = amountColor,
                        unitColor = uColor,
                        edgeBarColor = barColor,
                        displayAmountToman = dispAmt,
                        formattedAmountRun = PersianNumberUtils.formatIsolatedAmountRun(dispAmt, isIncome),
                        hasCustomNote = hasCustomNote,
                        primaryTitle = primaryTitle,
                        isLongTitle = primaryTitle.length > 22
                    )
                }
                DayTransactionGroup(
                    dateSortKey = sortKey,
                    dateHeaderLabel = dateHeaderLabel,
                    dayNetSumToman = netSum,
                    formattedDayNetRun = PersianNumberUtils.formatIsolatedAmountRun(kotlin.math.abs(netSum), netSum > 0L),
                    rows = rowModels
                )
            }
            .sortedByDescending { it.dateSortKey }
    }

    var activeDeleteConfirmId by remember { mutableStateOf<Long?>(null) }
    val onRowLongClickForId = remember { { id: Long -> activeDeleteConfirmId = id } }
    val onClearDeleteConfirm = remember { { _: Long -> activeDeleteConfirmId = null } }
    val shimmerBrush = if (isLoadingSelectedMonth) orosiShimmerBrush() else null

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("transactions_screen"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Minimalist Borderless Month Selector
        item(key = "month_selector_header", contentType = "month_selector") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(OrosiSurfaceContainer, TxCardShape20)
                    .padding(horizontal = 10.dp, vertical = 7.dp)
                    .testTag("month_selector_header"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(OrosiSurfaceHigh)
                ) {
                    IconButton(
                        onClick = { onChangeMonth(-1) },
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("prev_month_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "ماه قبل",
                            tint = AlabasterSilk,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clip(TxBarPillShape)
                        .background(OrosiSurfaceHigh.copy(alpha = 0.72f))
                        .clickable { showMonthPickerSheet = true }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                        .testTag("open_month_picker_button")
                ) {
                    Text(
                        text = monthHeaderLabel,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp
                        ),
                        color = AlabasterSilk,
                        modifier = Modifier.testTag("selected_month_label")
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "انتخاب سریع ماه و سال",
                        tint = NishapurTurquoiseSoft,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(
                            if (canGoNextMonth) OrosiSurfaceHigh else OrosiSurfaceHigh.copy(alpha = 0.35f)
                        )
                ) {
                    IconButton(
                        onClick = { if (canGoNextMonth) onChangeMonth(1) },
                        enabled = canGoNextMonth,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("next_month_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = "ماه بعد",
                            tint = if (canGoNextMonth) AlabasterSilk else OrosiOnSurfaceVariant.copy(alpha = 0.30f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // 2. Compact Single-Strip Monthly Summary (calm, minimal vertical footprint)
        item(key = "monthly_summary_cards", contentType = "month_summary") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(OrosiSurfaceContainer, TxCardShape20)
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("monthly_income_summary_card"),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(TxIncomeIconBg)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ArrowDownward,
                                contentDescription = null,
                                tint = NishapurTurquoiseBright,
                                modifier = Modifier.size(11.dp)
                            )
                        }
                        Text(
                            text = "دریافتی",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp
                            ),
                            color = NishapurTurquoiseSoft,
                            maxLines = 1
                        )
                    }
                    OrosiAmountText(
                        amountToman = monthlyIncomeToman,
                        color = AlabasterSilk,
                        numberStyle = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp),
                        numberFontWeight = FontWeight.ExtraBold,
                        unitStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                        unitColor = TxUnitIncomeColor,
                        isIncome = monthlyIncomeToman > 0L,
                        hideAmount = hideAmounts
                    )
                }

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(34.dp)
                        .background(TxDividerColor)
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("monthly_expense_summary_card"),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(TxExpenseIconBg)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ArrowUpward,
                                contentDescription = null,
                                tint = RubyRoseSoft,
                                modifier = Modifier.size(11.dp)
                            )
                        }
                        Text(
                            text = "پرداختی",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp
                            ),
                            color = RubyRoseSoft,
                            maxLines = 1
                        )
                    }
                    OrosiAmountText(
                        amountToman = monthlyExpenseToman,
                        color = AlabasterSilk,
                        numberStyle = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp),
                        numberFontWeight = FontWeight.ExtraBold,
                        unitStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                        unitColor = TxUnitExpenseColor,
                        hideAmount = hideAmounts
                    )
                }
            }
        }

        if (isLoadingSelectedMonth && shimmerBrush != null) {
            item(key = "month_loading_banner", contentType = "loading_banner") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(OrosiSurfaceHigh, TxCardShape20)
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .testTag("month_loading_shimmer"),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "در حال خواندن پیامک‌های $monthHeaderLabel...",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = NishapurTurquoiseSoft
                    )
                    LinearProgressIndicator(
                        color = NishapurTurquoiseBright,
                        trackColor = OrosiSurfaceContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(50))
                    )
                }
            }
            items(4, key = { "month_shimmer_row_$it" }, contentType = { "shimmer_row" }) {
                TransactionRowSkeletonShimmer(shimmerBrush = shimmerBrush)
            }
        } else if (sortedDayGroups.isEmpty()) {
            item(key = "empty_month_card", contentType = "empty_state") {
                Spacer(modifier = Modifier.height(6.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(OrosiSurfaceContainer, TxCardShape20)
                        .padding(28.dp)
                        .testTag("empty_month_transactions_card"),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "در این ماه تراکنشی ثبت نشده است",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = AlabasterSilk,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        onClick = onRescanMonth,
                        shape = RoundedCornerShape(14.dp),
                        color = NishapurTurquoiseDeep.copy(alpha = 0.72f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.2.dp,
                            NishapurTurquoiseBright.copy(alpha = 0.72f)
                        ),
                        modifier = Modifier.testTag("rescan_empty_month_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = NishapurTurquoiseBright,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "بررسی دوباره پیامک‌های این ماه",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = AlabasterSilk
                            )
                        }
                    }
                }
            }
        } else {
            // 3. Independent Minimalist Transaction Cards matching AccountsScreen aesthetic
            sortedDayGroups.forEach { dayGroup ->
                item(key = "header_${dayGroup.dateSortKey}", contentType = "day_header") {
                    val dayNetColor = when {
                        dayGroup.dayNetSumToman < 0L -> RubyRoseSoft
                        dayGroup.dayNetSumToman > 0L -> NishapurTurquoiseSoft
                        else -> OrosiOnSurfaceVariant
                    }
                    val dayNetUnitColor = when {
                        dayGroup.dayNetSumToman < 0L -> TxUnitExpenseColor
                        dayGroup.dayNetSumToman > 0L -> TxUnitIncomeColor
                        else -> TxUnitMutedColor
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 0.dp, start = 4.dp, end = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = dayGroup.dateHeaderLabel,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.5.sp,
                            color = PersianLapisLight
                        )
                        OrosiAmountText(
                            amountToman = kotlin.math.abs(dayGroup.dayNetSumToman),
                            color = dayNetColor,
                            numberStyle = MaterialTheme.typography.labelLarge,
                            numberFontWeight = FontWeight.Bold,
                            unitStyle = MaterialTheme.typography.labelSmall,
                            unitColor = dayNetUnitColor,
                            isIncome = dayGroup.dayNetSumToman > 0L,
                            hideAmount = hideAmounts,
                            prefixText = if (!hideAmounts && dayGroup.dayNetSumToman < 0L) "خالص" else null,
                            preformattedNumberRun = dayGroup.formattedDayNetRun,
                            modifier = Modifier.testTag("day_net_sum_${dayGroup.dateSortKey}")
                        )
                    }
                }

                items(
                    items = dayGroup.rows,
                    key = { row -> "month_tx_${row.transaction.id}" },
                    contentType = { "ledger_tx_card" }
                ) { row ->
                    val tx = row.transaction
                    if (activeDeleteConfirmId == tx.id) {
                        InlineDeleteConfirmOverlayBox(
                            transactionId = tx.id,
                            onConfirmDelete = {
                                activeDeleteConfirmId = null
                                onDeleteTransaction(tx)
                            },
                            onCancel = { activeDeleteConfirmId = null }
                        ) {
                            MinimalLedgerTransactionCard(
                                row = row,
                                hideAmounts = hideAmounts,
                                onTransactionClick = onTransactionClick,
                                onRowLongClickId = onClearDeleteConfirm,
                                onUntaggedMicClick = onUntaggedMicClick
                            )
                        }
                    } else {
                        MinimalLedgerTransactionCard(
                            row = row,
                            hideAmounts = hideAmounts,
                            onTransactionClick = onTransactionClick,
                            onRowLongClickId = onRowLongClickForId,
                            onUntaggedMicClick = onUntaggedMicClick
                        )
                    }
                }
            }
        }
    }
    if (showMonthPickerSheet) {
        JalaliMonthYearQuickPickerSheet(
            selectedYearMonthKey = selectedYearMonthKey,
            maxYearMonthKey = currentYearMonthKey,
            onSelectYearMonth = { ym ->
                showMonthPickerSheet = false
                onSelectMonth(ym)
            },
            onDismiss = { showMonthPickerSheet = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JalaliMonthYearQuickPickerSheet(
    selectedYearMonthKey: Int,
    maxYearMonthKey: Int,
    onSelectYearMonth: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val maxYear = maxYearMonthKey / 100
    val minYear = (maxYear - 8).coerceAtLeast(1395)
    var pickerYear by remember(selectedYearMonthKey) { mutableIntStateOf(selectedYearMonthKey / 100) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = OrosiSurfaceLow,
        contentColor = AlabasterSilk
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .testTag("jalali_month_picker_sheet"),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "انتخاب سریع ماه و سال",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                color = AlabasterSilk
            )

            // Year switcher (< Prev Year | Year | Next Year >)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(OrosiSurfaceHigh, TxCardShape20)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val canGoPrevYear = pickerYear > minYear
                val canGoNextYear = pickerYear < maxYear
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (canGoPrevYear) OrosiSurfaceContainer else OrosiSurfaceContainer.copy(alpha = 0.35f))
                ) {
                    IconButton(
                        onClick = { if (canGoPrevYear) pickerYear -= 1 },
                        enabled = canGoPrevYear,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("month_picker_prev_year")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "سال قبل",
                            tint = if (canGoPrevYear) AlabasterSilk else OrosiOnSurfaceVariant.copy(alpha = 0.30f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Text(
                    text = "سال ${PersianNumberUtils.toPersianDigits(pickerYear.toString())}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = AlabasterSilk,
                    modifier = Modifier.testTag("month_picker_year_label")
                )

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (canGoNextYear) OrosiSurfaceContainer else OrosiSurfaceContainer.copy(alpha = 0.35f))
                ) {
                    IconButton(
                        onClick = { if (canGoNextYear) pickerYear += 1 },
                        enabled = canGoNextYear,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("month_picker_next_year")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = "سال بعد",
                            tint = if (canGoNextYear) AlabasterSilk else OrosiOnSurfaceVariant.copy(alpha = 0.30f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // 4 rows × 3 columns grid of all 12 Jalali months
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (rowIdx in 0 until 4) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (colIdx in 0 until 3) {
                            val monthNum = rowIdx * 3 + colIdx + 1
                            val ymKey = pickerYear * 100 + monthNum
                            val isEnabled = ymKey <= maxYearMonthKey
                            val isSelected = ymKey == selectedYearMonthKey
                            Surface(
                                onClick = { if (isEnabled) onSelectYearMonth(ymKey) },
                                enabled = isEnabled,
                                shape = RoundedCornerShape(14.dp),
                                color = when {
                                    isSelected -> NishapurTurquoiseDeep
                                    isEnabled -> OrosiSurfaceHigh
                                    else -> OrosiSurfaceContainer.copy(alpha = 0.38f)
                                },
                                border = if (isSelected) {
                                    androidx.compose.foundation.BorderStroke(1.3.dp, NishapurTurquoiseBright)
                                } else null,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("month_picker_cell_$ymKey")
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 6.dp)
                                ) {
                                    Text(
                                        text = jalaliMonthNames[monthNum - 1],
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold
                                        ),
                                        color = when {
                                            isSelected -> AlabasterSilk
                                            isEnabled -> AlabasterSilk.copy(alpha = 0.90f)
                                            else -> OrosiOnSurfaceVariant.copy(alpha = 0.35f)
                                        },
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MinimalLedgerTransactionCard(
    row: LedgerTxRowUi,
    hideAmounts: Boolean,
    onTransactionClick: (TransactionEntity) -> Unit,
    onRowLongClickId: ((Long) -> Unit)? = null,
    onUntaggedMicClick: (TransactionEntity) -> Unit
) {
    val tx = row.transaction

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(OrosiSurfaceContainer, TxCardShape20)
            .combinedClickable(
                onClick = { onTransactionClick(tx) },
                onLongClick = if (onRowLongClickId != null) { { onRowLongClickId(tx.id) } } else null
            )
            .padding(start = 10.dp, end = 14.dp, top = 12.dp, bottom = 12.dp)
            .testTag("grouped_tx_${tx.id}"),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.5.dp)
                .height(36.dp)
                .background(row.edgeBarColor, TxBarPillShape)
        )

        BankLogoAvatar(
            bankName = row.shortBank,
            size = 40.dp,
            preResolvedLogoKey = row.logoKey
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = row.primaryTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                fontSize = if (row.isLongTitle) 13.5.sp else 14.5.sp,
                lineHeight = 19.sp,
                color = AlabasterSilk,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = row.metaLabel,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.5.sp,
                color = OrosiOnSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (tx.isUntagged) {
                val untaggedBtnLabel = if (row.isIncome) "بگو از کجا اومد؟" else "بگو بابت چی؟"
                val tagBg = if (row.isIncome) TxIncomeTagBg else TxExpenseTagBg
                val tagContentColor = if (row.isIncome) NishapurTurquoiseSoft else RubyRoseSoft
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier
                        .padding(top = 3.dp)
                        .wrapContentWidth(unbounded = false)
                        .background(tagBg, TxMicPillShape12)
                        .clickable { onUntaggedMicClick(tx) }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                        .testTag("untagged_mic_button_${tx.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = untaggedBtnLabel,
                        tint = tagContentColor,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = untaggedBtnLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp,
                        lineHeight = 15.sp,
                        color = tagContentColor,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        OrosiAmountText(
            amountToman = row.displayAmountToman,
            color = row.accentColor,
            numberStyle = MaterialTheme.typography.titleSmall,
            numberFontWeight = FontWeight.ExtraBold,
            unitStyle = MaterialTheme.typography.labelSmall,
            unitColor = row.unitColor,
            isIncome = row.isIncome,
            hideAmount = hideAmounts,
            preformattedNumberRun = row.formattedAmountRun
        )
    }
}
