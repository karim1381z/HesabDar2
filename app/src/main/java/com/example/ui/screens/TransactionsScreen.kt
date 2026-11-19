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
private val TxBadgeShape8 = RoundedCornerShape(8.dp)
private val TxMicPillShape10 = RoundedCornerShape(10.dp)
private val TxUntaggedBorderColor = NishapurTurquoise.copy(alpha = 0.4f)
private val TxIncomeCardBg = NishapurTurquoiseDeep.copy(alpha = 0.55f)
private val TxIncomeCardBorder = NishapurTurquoiseBright.copy(alpha = 0.55f)
private val TxExpenseCardBg = RubyRose.copy(alpha = 0.22f)
private val TxExpenseCardBorder = RubyRoseSoft.copy(alpha = 0.55f)

@Immutable
private data class GroupedTxRowUi(
    val transaction: TransactionEntity,
    val shortBank: String,
    val isIncome: Boolean,
    val accentColor: Color,
    val displayAmountToman: Long,
    val hasCustomNote: Boolean,
    val noteText: String
)

private data class DayTransactionGroup(
    val dateSortKey: Int,
    val dateHeaderLabel: String,
    val dayNetSumToman: Long,
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
                    GroupedTxRowUi(
                        transaction = tx,
                        shortBank = shortBank,
                        isIncome = isIncome,
                        accentColor = if (isIncome) NishapurTurquoiseBright else RubyRoseSoft,
                        displayAmountToman = tx.userShareToman.takeIf { it > 0L } ?: tx.totalAmountToman,
                        hasCustomNote = hasCustomNote,
                        noteText = noteText
                    )
                }
                DayTransactionGroup(
                    dateSortKey = sortKey,
                    dateHeaderLabel = dateHeaderLabel,
                    dayNetSumToman = netSum,
                    rows = rowModels
                )
            }
            .sortedByDescending { it.dateSortKey }
    }

    var activeDeleteConfirmId by remember { mutableStateOf<Long?>(null) }

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

        // 2. Two Summary Cards at Top: "دریافتی ماه (سبز)" and "پرداختی ماه (کهربایی/قرمز ملایم)"
        item(key = "monthly_summary_cards", contentType = "month_summary") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // دریافتی ماه (سبز / فیروزه‌ای)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(TxIncomeCardBg, TxCardShape20)
                        .border(1.dp, TxIncomeCardBorder, TxCardShape20)
                        .padding(16.dp)
                        .testTag("monthly_income_summary_card")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.ArrowDownward,
                            contentDescription = null,
                            tint = NishapurTurquoiseBright,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "دریافتی ماه",
                            style = MaterialTheme.typography.labelLarge,
                            color = NishapurTurquoiseSoft
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
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
                        .background(TxExpenseCardBg, TxCardShape20)
                        .border(1.dp, TxExpenseCardBorder, TxCardShape20)
                        .padding(16.dp)
                        .testTag("monthly_expense_summary_card")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.ArrowUpward,
                            contentDescription = null,
                            tint = RubyRoseSoft,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "پرداختی ماه",
                            style = MaterialTheme.typography.labelLarge,
                            color = RubyRoseSoft
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
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
                            unitColor = dayNetColor.copy(alpha = 0.85f),
                            isIncome = dayGroup.dayNetSumToman > 0L,
                            hideAmount = hideAmounts,
                            prefixText = if (!hideAmounts && dayGroup.dayNetSumToman < 0L) "پرداختی" else null,
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
                    val isConfirmingDelete = activeDeleteConfirmId == tx.id
                    if (isConfirmingDelete) {
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
                                onRowClick = { onTransactionClick(tx) },
                                onRowLongClick = { activeDeleteConfirmId = null },
                                onUntaggedMicClick = { onUntaggedMicClick(tx) }
                            )
                        }
                    } else {
                        JibemanGroupedTransactionRow(
                            row = row,
                            hideAmounts = hideAmounts,
                            onRowClick = { onTransactionClick(tx) },
                            onRowLongClick = { activeDeleteConfirmId = tx.id },
                            onUntaggedMicClick = { onUntaggedMicClick(tx) }
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
    onRowClick: () -> Unit,
    onRowLongClick: (() -> Unit)? = null,
    onUntaggedMicClick: () -> Unit
) {
    val tx = row.transaction
    val borderColor = if (tx.isUntagged) TxUntaggedBorderColor else OrosiHairline

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                val radiusPx = 18.dp.toPx()
                val cr = CornerRadius(radiusPx, radiusPx)
                drawRoundRect(color = OrosiSurfaceContainer, cornerRadius = cr)
                drawRoundRect(
                    color = borderColor,
                    cornerRadius = cr,
                    style = Stroke(width = 1.dp.toPx())
                )
            }
            .combinedClickable(
                onClick = onRowClick,
                onLongClick = onRowLongClick
            )
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag("grouped_tx_${tx.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                com.example.ui.components.BankLogoAvatar(
                    bankName = row.shortBank,
                    size = 44.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .background(PersianLapis, TxBadgeShape8)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = row.shortBank,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = NishapurTurquoiseSoft
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = tx.timeStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = OrosiOnSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            OrosiAmountText(
                amountToman = row.displayAmountToman,
                color = row.accentColor,
                numberStyle = MaterialTheme.typography.titleSmall,
                numberFontWeight = FontWeight.ExtraBold,
                unitStyle = MaterialTheme.typography.labelSmall,
                unitColor = row.accentColor.copy(alpha = 0.85f),
                isIncome = row.isIncome,
                hideAmount = hideAmounts
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = row.noteText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (row.hasCustomNote) FontWeight.Bold else FontWeight.Normal,
                fontSize = if (row.noteText.length > 24) 13.sp else 14.sp,
                lineHeight = 19.sp,
                color = if (row.hasCustomNote) SaffronGoldLight else OrosiOnSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            if (tx.isUntagged) {
                val untaggedBtnLabel = if (row.isIncome) "بگو از کجا اومد؟" else "بگو بابت چی؟"
                Spacer(modifier = Modifier.width(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(NishapurTurquoise, TxMicPillShape10)
                        .clickable(onClick = onUntaggedMicClick)
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = untaggedBtnLabel,
                        tint = AlabasterSilk,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = untaggedBtnLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = AlabasterSilk
                    )
                }
            }
        }
    }
}
