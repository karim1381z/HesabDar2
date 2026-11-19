package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Immutable
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.data.local.TransactionEntity
import com.example.domain.JalaliCalendarUtils
import com.example.domain.PersianNumberUtils
import com.example.ui.components.OrosiAmountText
import com.example.ui.theme.AlabasterSilk
import com.example.ui.theme.NishapurTurquoise
import com.example.ui.theme.NishapurTurquoiseBright
import com.example.ui.theme.NishapurTurquoiseDeep
import com.example.ui.theme.NishapurTurquoiseSoft
import com.example.ui.theme.OrosiHairline
import com.example.ui.theme.OrosiOnSurfaceVariant
import com.example.ui.theme.OrosiSurfaceContainer
import com.example.ui.theme.OrosiSurfaceHigh
import com.example.ui.theme.PersianLapis
import com.example.ui.theme.PersianLapisLight
import com.example.ui.theme.RubyRose
import com.example.ui.theme.RubyRoseSoft
import com.example.ui.theme.SaffronGoldLight

private val jalaliMonthNames = arrayOf(
    "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
    "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
)

// Hoisted static shapes and colors for zero per-frame allocations during 120Hz scrolling
private val TxCardShape20 = RoundedCornerShape(20.dp)
private val TxCardShape18 = RoundedCornerShape(18.dp)
private val TxMicPillShape10 = RoundedCornerShape(10.dp)
private val TxUntaggedRowBg = Color(0xFF102432)
private val TxIncomeCardBg = NishapurTurquoiseDeep.copy(alpha = 0.45f)
private val TxExpenseCardBg = RubyRose.copy(alpha = 0.16f)
private val TxUnitIncomeColor = NishapurTurquoiseBright.copy(alpha = 0.82f)
private val TxUnitExpenseColor = RubyRoseSoft.copy(alpha = 0.82f)
private val TxUnitMutedColor = OrosiOnSurfaceVariant.copy(alpha = 0.82f)

@Immutable
private data class GroupedTxRowUi(
    val transaction: TransactionEntity,
    val shortBank: String,
    val metaLabel: String,
    val isIncome: Boolean,
    val accentColor: Color,
    val unitColor: Color,
    val displayAmountToman: Long,
    val formattedAmountRun: String,
    val hasCustomNote: Boolean,
    val noteText: String,
    val isLongNote: Boolean
)

@Immutable
private data class DayTransactionGroup(
    val dateSortKey: Int,
    val dateHeaderLabel: String,
    val dayNetSumToman: Long,
    val formattedDayNetRun: String,
    val rows: List<GroupedTxRowUi>
)

@Composable
fun TransactionsScreen(
    transactions: List<TransactionEntity>,
    selectedYearMonthKey: Int,
    hideAmounts: Boolean,
    isLoadingSelectedMonth: Boolean = false,
    onChangeMonth: (Int) -> Unit,
    onTransactionClick: (TransactionEntity) -> Unit,
    onUntaggedMicClick: (TransactionEntity) -> Unit,
    onDeleteTransaction: (TransactionEntity) -> Unit = {},
    onBackToHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBackToHome)

    val currentYearMonthKey = remember {
        JalaliCalendarUtils.fromTimestamp(System.currentTimeMillis()).yearMonthKey
    }
    val canGoNextMonth = selectedYearMonthKey < currentYearMonthKey
    val selectedYear = selectedYearMonthKey / 100
    val selectedMonth = (selectedYearMonthKey % 100).coerceIn(1, 12)
    val monthName = jalaliMonthNames[selectedMonth - 1]
    val monthHeaderLabel = "$monthName ${PersianNumberUtils.toPersianDigits(selectedYear.toString())}"

    // Strictly filter by BOTH jalaliMonth == selectedMonth AND jalaliYear == selectedYear
    // computed from timestampMillis via JalaliCalendarUtils.timestampToJalali(timestampMillis),
    // and ALWAYS sort filteredTransactions by it.timestampMillis DESC before calling .groupBy { it.jalaliDate }!
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

    // Group strictly after sorting by timestampMillis DESC so date headers are chronological from newest to oldest
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
                    val noteText = if (hasCustomNote) {
                        tx.title.ifBlank { tx.displayTitle }
                    } else if (tx.isUntagged) {
                        if (isIncome) "واریز به کارت ($shortBank)" else "خرید کارتی ($shortBank)"
                    } else {
                        tx.title
                    }
                    val dispAmt = tx.userShareToman.takeIf { it > 0L } ?: tx.totalAmountToman
                    GroupedTxRowUi(
                        transaction = tx,
                        shortBank = shortBank,
                        metaLabel = if (tx.timeStr.isNotBlank()) "$shortBank • ${tx.timeStr}" else shortBank,
                        isIncome = isIncome,
                        accentColor = if (isIncome) NishapurTurquoiseBright else RubyRoseSoft,
                        unitColor = if (isIncome) TxUnitIncomeColor else TxUnitExpenseColor,
                        displayAmountToman = dispAmt,
                        formattedAmountRun = PersianNumberUtils.formatIsolatedAmountRun(dispAmt, isIncome),
                        hasCustomNote = hasCustomNote,
                        noteText = noteText,
                        isLongNote = noteText.length > 24
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

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("transactions_screen"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Month Selector Header: right (>) goes to previous month, left (<) goes to next month (disabled on current month)
        item(key = "month_selector_header", contentType = "month_selector") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(OrosiSurfaceHigh, TxCardShape20)
                    .border(1.dp, OrosiHairline, TxCardShape20)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .testTag("month_selector_header"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // RIGHT side button in RTL: points RIGHT (>) -> Previous month
                Surface(
                    shape = CircleShape,
                    color = OrosiSurfaceContainer,
                    modifier = Modifier
                        .size(40.dp)
                        .border(1.dp, PersianLapisLight.copy(alpha = 0.35f), CircleShape)
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
                            tint = AlabasterSilk
                        )
                    }
                }

                Text(
                    text = monthHeaderLabel,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                    color = AlabasterSilk,
                    modifier = Modifier.testTag("selected_month_label")
                )

                // LEFT side button in RTL: points LEFT (<) -> Next month (disabled & dimmed when on current month)
                Surface(
                    shape = CircleShape,
                    color = if (canGoNextMonth) OrosiSurfaceContainer else OrosiSurfaceContainer.copy(alpha = 0.38f),
                    modifier = Modifier
                        .size(40.dp)
                        .border(
                            1.dp,
                            if (canGoNextMonth) PersianLapisLight.copy(alpha = 0.35f) else OrosiHairline.copy(alpha = 0.35f),
                            CircleShape
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
                            tint = if (canGoNextMonth) AlabasterSilk else OrosiOnSurfaceVariant.copy(alpha = 0.3f)
                        )
                    }
                }
            }
        }

        // 2. Two Calm Read-Only KPI Summary Panels (borderless tonal surfaces — clearly non-clickable)
        item(key = "monthly_summary_cards", contentType = "month_summary") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // دریافتی ماه (سبز / فیروزه‌ای)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(TxCardShape20)
                        .background(TxIncomeCardBg, TxCardShape20)
                        .padding(16.dp)
                        .testTag("monthly_income_summary_card"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ArrowDownward,
                            contentDescription = null,
                            tint = NishapurTurquoiseBright,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "دریافتی ماه",
                            style = MaterialTheme.typography.labelLarge,
                            color = NishapurTurquoiseSoft
                        )
                    }
                    OrosiAmountText(
                        amountToman = monthlyIncomeToman,
                        color = AlabasterSilk,
                        numberStyle = MaterialTheme.typography.titleMedium.copy(fontSize = 15.5.sp),
                        numberFontWeight = FontWeight.ExtraBold,
                        unitStyle = MaterialTheme.typography.labelSmall,
                        unitColor = NishapurTurquoiseSoft,
                        hideAmount = hideAmounts,
                        stackedUnit = true
                    )
                }

                // پرداختی ماه (کهربایی / قرمز ملایم)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(TxCardShape20)
                        .background(TxExpenseCardBg, TxCardShape20)
                        .padding(16.dp)
                        .testTag("monthly_expense_summary_card"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ArrowUpward,
                            contentDescription = null,
                            tint = RubyRoseSoft,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "پرداختی ماه",
                            style = MaterialTheme.typography.labelLarge,
                            color = RubyRoseSoft
                        )
                    }
                    OrosiAmountText(
                        amountToman = monthlyExpenseToman,
                        color = AlabasterSilk,
                        numberStyle = MaterialTheme.typography.titleMedium.copy(fontSize = 15.5.sp),
                        numberFontWeight = FontWeight.ExtraBold,
                        unitStyle = MaterialTheme.typography.labelSmall,
                        unitColor = RubyRoseSoft,
                        hideAmount = hideAmounts,
                        stackedUnit = true
                    )
                }
            }
        }

        if (isLoadingSelectedMonth) {
            item(key = "month_loading_banner", contentType = "loading_banner") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(OrosiSurfaceHigh, TxCardShape18)
                        .border(1.dp, NishapurTurquoiseBright.copy(alpha = 0.45f), TxCardShape18)
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
                val shimmerBrush = orosiShimmerBrush()
                TransactionRowSkeletonShimmer(shimmerBrush = shimmerBrush)
            }
        } else if (sortedDayGroups.isEmpty()) {
            item(key = "empty_month_card", contentType = "empty_state") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(OrosiSurfaceContainer, TxCardShape20)
                        .border(1.dp, OrosiHairline, TxCardShape20)
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
                }
            }
        } else {
            // 3. Transactions Grouped by Jalali Date Headers sorted newest day first (timestampMillis DESC)
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
                            .padding(top = 8.dp, bottom = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = dayGroup.dateHeaderLabel,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
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
                            prefixText = if (!hideAmounts && dayGroup.dayNetSumToman < 0L) "پرداختی" else null,
                            preformattedNumberRun = dayGroup.formattedDayNetRun,
                            modifier = Modifier.testTag("day_net_sum_${dayGroup.dateSortKey}")
                        )
                    }
                }

                items(
                    items = dayGroup.rows,
                    key = { row -> "month_tx_${row.transaction.id}" },
                    contentType = { "grouped_tx_row" }
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
                            JibemanGroupedTransactionRow(
                                row = row,
                                hideAmounts = hideAmounts,
                                onTransactionClick = onTransactionClick,
                                onRowLongClickId = onClearDeleteConfirm,
                                onUntaggedMicClick = onUntaggedMicClick
                            )
                        }
                    } else {
                        JibemanGroupedTransactionRow(
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
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun JibemanGroupedTransactionRow(
    row: GroupedTxRowUi,
    hideAmounts: Boolean,
    onTransactionClick: (TransactionEntity) -> Unit,
    onRowLongClickId: ((Long) -> Unit)? = null,
    onUntaggedMicClick: (TransactionEntity) -> Unit
) {
    val tx = row.transaction
    val cardBg = if (tx.isUntagged) TxUntaggedRowBg else OrosiSurfaceContainer

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(TxCardShape18)
            .background(cardBg, TxCardShape18)
            .combinedClickable(
                onClick = { onTransactionClick(tx) },
                onLongClick = if (onRowLongClickId != null) { { onRowLongClickId(tx.id) } } else null
            )
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag("grouped_tx_${tx.id}"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            com.example.ui.components.BankLogoAvatar(
                bankName = row.shortBank,
                size = 40.dp
            )

            Text(
                text = row.metaLabel,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = OrosiOnSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = row.noteText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (row.hasCustomNote) FontWeight.Bold else FontWeight.Normal,
                fontSize = if (row.isLongNote) 13.sp else 14.sp,
                lineHeight = 19.sp,
                color = if (row.hasCustomNote) SaffronGoldLight else OrosiOnSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            if (tx.isUntagged) {
                val untaggedBtnLabel = if (row.isIncome) "بگو از کجا اومد؟" else "بگو بابت چی؟"
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clip(TxMicPillShape10)
                        .background(NishapurTurquoise, TxMicPillShape10)
                        .clickable { onUntaggedMicClick(tx) }
                        .padding(horizontal = 9.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = untaggedBtnLabel,
                        tint = AlabasterSilk,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = untaggedBtnLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = AlabasterSilk,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}
