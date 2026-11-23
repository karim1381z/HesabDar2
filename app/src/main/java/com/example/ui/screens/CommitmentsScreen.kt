package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.CommitmentEntity
import com.example.data.local.TransactionEntity
import com.example.domain.JalaliCalendarUtils
import com.example.domain.MonthlyReportBarPoint
import com.example.domain.MonthlyReportTxSnapshot
import com.example.domain.OrosiCashflowAnalyzer
import com.example.domain.PersianNumberUtils
import com.example.ui.CommitmentTab
import com.example.ui.components.OrosiAmountText
import com.example.ui.theme.AlabasterSilk
import com.example.ui.theme.NishapurTurquoise
import com.example.ui.theme.NishapurTurquoiseBright
import com.example.ui.theme.NishapurTurquoiseDeep
import com.example.ui.theme.NishapurTurquoiseSoft
import com.example.ui.theme.OrosiBalanceExpenseCardBg
import com.example.ui.theme.OrosiBalanceIncomeCardBg
import com.example.ui.theme.OrosiHairline
import com.example.ui.theme.OrosiOnSurfaceVariant
import com.example.ui.theme.OrosiSurfaceContainer
import com.example.ui.theme.OrosiSurfaceHigh
import com.example.ui.theme.OrosiSurfaceLow
import com.example.ui.theme.PersianLapis
import com.example.ui.theme.PersianLapisLight
import com.example.ui.theme.RoyalAmethyst
import com.example.ui.theme.RoyalAmethystContainer
import com.example.ui.theme.RoyalAmethystDeep
import com.example.ui.theme.RoyalAmethystSoft
import com.example.ui.theme.RubyRose
import com.example.ui.theme.RubyRoseSoft
import com.example.ui.theme.SaffronGold
import com.example.ui.theme.SaffronGoldBright
import com.example.ui.theme.SaffronGoldLight

private val CommCardShape22 = RoundedCornerShape(22.dp)
private val CommCardShape20 = RoundedCornerShape(20.dp)
private val CommCardShape16 = RoundedCornerShape(16.dp)
private val CommPillShape50 = RoundedCornerShape(50)

@Composable
fun CommitmentsScreen(
    selectedTab: CommitmentTab,
    onSelectTab: (CommitmentTab) -> Unit,
    commitments: List<CommitmentEntity>,
    transactions: List<TransactionEntity>,
    hideAmounts: Boolean,
    isSyncingReportMonths: Boolean = false,
    onEnsureMonthsSynced: (List<Int>) -> Unit = {},
    onCommitmentClick: (CommitmentEntity) -> Unit,
    onOpenAddCommitmentModal: () -> Unit,
    onBackToDashboard: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBackToDashboard)

    val filteredCommitments = remember(commitments, selectedTab) {
        when (selectedTab) {
            CommitmentTab.SAYYAD -> commitments.filter {
                it.category == "SAYYAD_ISSUED" || it.category == "SAYYAD_RECEIVED"
            }
            CommitmentTab.INSTALLMENTS -> commitments.filter {
                it.category == "LOAN_INSTALLMENT"
            }
            CommitmentTab.DONG -> commitments.filter {
                it.category == "DONG"
            }
            CommitmentTab.REPORTS -> emptyList()
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("commitments_screen"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Clean 2x2 Tools Grid Selector without truncation
        item(key = "tools_grid_selector", contentType = "tools_grid") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tools_grid_selector"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ToolGridCard(
                        title = "چک صیادی",
                        icon = Icons.Default.VerifiedUser,
                        isSelected = selectedTab == CommitmentTab.SAYYAD,
                        accentColor = RoyalAmethystSoft,
                        onClick = { onSelectTab(CommitmentTab.SAYYAD) },
                        testTag = "tool_tab_sayyad",
                        modifier = Modifier.weight(1f)
                    )
                    ToolGridCard(
                        title = "وام و قسط",
                        icon = Icons.Default.AccountBalance,
                        isSelected = selectedTab == CommitmentTab.INSTALLMENTS,
                        accentColor = SaffronGoldBright,
                        onClick = { onSelectTab(CommitmentTab.INSTALLMENTS) },
                        testTag = "tool_tab_installments",
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ToolGridCard(
                        title = "بدهی و طلب (دُنگ)",
                        icon = Icons.Default.Group,
                        isSelected = selectedTab == CommitmentTab.DONG,
                        accentColor = NishapurTurquoiseBright,
                        onClick = { onSelectTab(CommitmentTab.DONG) },
                        testTag = "tool_tab_dong",
                        modifier = Modifier.weight(1f)
                    )
                    ToolGridCard(
                        title = "گزارش دخل و خرج",
                        icon = Icons.Default.Assessment,
                        isSelected = selectedTab == CommitmentTab.REPORTS,
                        accentColor = PersianLapisLight,
                        onClick = { onSelectTab(CommitmentTab.REPORTS) },
                        testTag = "tool_tab_reports",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        if (selectedTab == CommitmentTab.REPORTS) {
            item(key = "reports_section", contentType = "reports") {
                IncomeExpenseReportSection(
                    transactions = transactions,
                    commitments = commitments,
                    hideAmounts = hideAmounts,
                    isSyncingReportMonths = isSyncingReportMonths,
                    onEnsureMonthsSynced = onEnsureMonthsSynced
                )
            }
        } else {
            // Reassuring Banner — borderless tonal card matching v2.8/v2.9 design grammar & accurate cycle scope
            item(key = "reassuring_banner", contentType = "info_banner") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CommCardShape20)
                        .background(NishapurTurquoiseDeep.copy(alpha = 0.56f))
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                        .testTag("reassuring_commitments_banner"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = NishapurTurquoiseBright,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "تعهدات سررسید این ماه حقوقی در محاسبه موجودی آزاد لحاظ می‌شوند؛ تعهدات ماه‌های آینده جداگانه نگهداری می‌شوند.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 20.sp
                        ),
                        color = AlabasterSilk
                    )
                }
            }

            if (filteredCommitments.isEmpty()) {
                item(key = "empty_commitments_${selectedTab.name}", contentType = "empty_card") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(CommCardShape20)
                            .background(OrosiSurfaceContainer)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "موردی در بخش «${selectedTab.title}» ثبت نشده است",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = AlabasterSilk,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "برای افزودن مورد جدید از دکمه پایین استفاده کنید.",
                            style = MaterialTheme.typography.bodySmall,
                            color = OrosiOnSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            items(
                items = filteredCommitments,
                key = { it.id },
                contentType = { if (it.category == "SAYYAD_ISSUED") "sayyad_card" else "commitment_card" }
            ) { item ->
                if (item.category == "SAYYAD_ISSUED") {
                    SpaciousPurpleSayyadCard(
                        cheque = item,
                        hideAmounts = hideAmounts,
                        onClick = { onCommitmentClick(item) }
                    )
                } else {
                    val isDebtDong = item.category == "DONG" &&
                        (item.statusText.contains("بدهی") || item.counterparty.contains("بدهی"))
                    val (accentColor, icon) = when (item.category) {
                        "SAYYAD_RECEIVED" -> Pair(NishapurTurquoiseBright, Icons.Default.CallReceived)
                        "LOAN_INSTALLMENT" -> Pair(SaffronGoldBright, Icons.Default.AccountBalance)
                        "DONG" -> Pair(if (isDebtDong) RubyRoseSoft else NishapurTurquoiseSoft, Icons.Default.Group)
                        else -> Pair(RoyalAmethystSoft, Icons.Default.VerifiedUser)
                    }
                    SpaciousCommitmentCard(
                        commitment = item,
                        accentColor = accentColor,
                        icon = icon,
                        hideAmounts = hideAmounts,
                        onClick = { onCommitmentClick(item) }
                    )
                }
            }

            // Action Button to add new Commitment
            item(key = "add_commitment_cta", contentType = "cta_button") {
                Spacer(modifier = Modifier.height(4.dp))
                val buttonLabel = when (selectedTab) {
                    CommitmentTab.SAYYAD -> "+ ثبت چک صیادی جدید"
                    CommitmentTab.INSTALLMENTS -> "+ ثبت وام یا قسط جدید"
                    CommitmentTab.DONG -> "+ ثبت بدهی یا طلب (دُنگ) جدید"
                    CommitmentTab.REPORTS -> "+ ثبت تعهد جدید"
                }
                Button(
                    onClick = onOpenAddCommitmentModal,
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RoyalAmethyst,
                        contentColor = AlabasterSilk
                    ),
                    contentPadding = PaddingValues(vertical = 16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .testTag("add_new_cheque_button")
                ) {
                    Text(
                        text = buttonLabel,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun ToolGridCard(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .heightIn(min = 50.dp)
            .clip(CommCardShape16)
            .background(if (isSelected) PersianLapis else OrosiSurfaceContainer)
            .then(
                if (isSelected) {
                    Modifier.border(1.4.dp, accentColor.copy(alpha = 0.85f), CommCardShape16)
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 13.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = accentColor,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                fontSize = 12.5.sp
            ),
            color = AlabasterSilk,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IncomeExpenseReportSection(
    transactions: List<TransactionEntity>,
    commitments: List<CommitmentEntity>,
    hideAmounts: Boolean,
    isSyncingReportMonths: Boolean,
    onEnsureMonthsSynced: (List<Int>) -> Unit
) {
    val currentJalali = remember { JalaliCalendarUtils.fromTimestamp(System.currentTimeMillis()) }
    val currentYearMonthKey = currentJalali.yearMonthKey

    // Monthly mode ("ماه‌به‌ماه") vs All-Time ("کل زمان‌ها")
    var isMonthlyMode by remember { mutableStateOf(true) }
    var selectedYearMonthKey by remember { mutableIntStateOf(currentYearMonthKey) }
    var showMonthPickerSheet by remember { mutableStateOf(false) }

    val canGoNextMonth = selectedYearMonthKey < currentYearMonthKey

    // Automatically lazy-sync selected month + previous month + 6-month trend window from SMS inbox
    val monthsNeededForView = remember(selectedYearMonthKey, isMonthlyMode) {
        if (isMonthlyMode) {
            (-5..0).map { offset ->
                JalaliCalendarUtils.shiftJalaliYearMonth(selectedYearMonthKey, offset)
            }.distinct()
        } else {
            (-5..0).map { offset ->
                JalaliCalendarUtils.shiftJalaliYearMonth(currentYearMonthKey, offset)
            }.distinct()
        }
    }

    LaunchedEffect(monthsNeededForView) {
        onEnsureMonthsSynced(monthsNeededForView)
    }

    val txSnapshots = remember(transactions) {
        transactions.map { tx ->
            MonthlyReportTxSnapshot(
                id = tx.id,
                type = tx.type,
                totalAmountToman = tx.totalAmountToman,
                userShareToman = tx.userShareToman,
                bankName = tx.shortBankName,
                displayTitle = tx.displayTitle,
                isUntagged = tx.isUntagged,
                timestamp = tx.timestamp
            )
        }
    }

    val report = remember(txSnapshots, isMonthlyMode, selectedYearMonthKey) {
        OrosiCashflowAnalyzer.analyzeMonthlyReport(
            transactions = txSnapshots,
            selectedYearMonthKey = if (isMonthlyMode) selectedYearMonthKey else null
        )
    }

    val trendPoints = remember(txSnapshots, selectedYearMonthKey) {
        OrosiCashflowAnalyzer.buildLastMonthsTrend(
            transactions = txSnapshots,
            anchorYearMonthKey = selectedYearMonthKey,
            monthCount = 6
        )
    }

    val activeCommitments = remember(commitments) {
        commitments.filter { !it.isSettled }.sumOf { it.amountToman }
    }

    val selectedMonthLabel = remember(selectedYearMonthKey) {
        val y = selectedYearMonthKey / 100
        val m = (selectedYearMonthKey % 100).coerceIn(1, 12)
        "${JalaliCalendarUtils.jalaliMonthName(m)} ${PersianNumberUtils.toPersianDigits(y.toString())}"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("reports_section"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Mode Switcher: "ماه‌به‌ماه" vs "کل زمان‌ها"
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CommCardShape16)
                .background(OrosiSurfaceContainer)
                .padding(4.dp)
                .testTag("reports_time_range_toggle"),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isMonthlyMode) NishapurTurquoise else Color.Transparent)
                    .clickable { isMonthlyMode = true }
                    .padding(vertical = 10.dp)
                    .testTag("report_toggle_this_month"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "گزارش ماه‌به‌ماه",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = if (isMonthlyMode) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = if (isMonthlyMode) AlabasterSilk else OrosiOnSurfaceVariant,
                    maxLines = 1,
                    softWrap = false
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (!isMonthlyMode) NishapurTurquoise else Color.Transparent)
                    .clickable { isMonthlyMode = false }
                    .padding(vertical = 10.dp)
                    .testTag("report_toggle_all_time"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "کل زمان‌ها",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = if (!isMonthlyMode) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = if (!isMonthlyMode) AlabasterSilk else OrosiOnSurfaceVariant,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        // 2. Jalali Month Navigator (when in Month-by-Month mode)
        if (isMonthlyMode) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CommCardShape20)
                        .background(OrosiSurfaceContainer)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            selectedYearMonthKey = JalaliCalendarUtils.shiftJalaliYearMonth(selectedYearMonthKey, -1)
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("report_prev_month_button")
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(OrosiSurfaceHigh)
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
                            .heightIn(min = 44.dp)
                            .clip(CommPillShape50)
                            .background(OrosiSurfaceHigh.copy(alpha = 0.78f))
                            .clickable { showMonthPickerSheet = true }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .testTag("report_month_picker_button")
                    ) {
                        Text(
                            text = selectedMonthLabel,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.5.sp
                            ),
                            color = AlabasterSilk
                        )
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "انتخاب سریع ماه گزارش",
                            tint = NishapurTurquoiseSoft,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            if (canGoNextMonth) {
                                selectedYearMonthKey = JalaliCalendarUtils.shiftJalaliYearMonth(selectedYearMonthKey, 1)
                            }
                        },
                        enabled = canGoNextMonth,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("report_next_month_button")
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    if (canGoNextMonth) OrosiSurfaceHigh else OrosiSurfaceHigh.copy(alpha = 0.35f)
                                )
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

                if (isSyncingReportMonths) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "در حال بارگذاری پیامک‌های ماه‌های گذشته...",
                            style = MaterialTheme.typography.labelSmall,
                            color = NishapurTurquoiseSoft
                        )
                        LinearProgressIndicator(
                            color = NishapurTurquoiseBright,
                            trackColor = OrosiSurfaceContainer,
                            modifier = Modifier
                                .weight(1f)
                                .height(3.dp)
                                .clip(CommPillShape50)
                        )
                    }
                }
            }
        }

        // 3. Comprehensive Summary & Key Metrics Card (Borderless Tonal Grammar)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CommCardShape22)
                .background(OrosiSurfaceHigh)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "خلاصه دخل و خرج (${report.periodLabel})",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = AlabasterSilk,
                    modifier = Modifier.weight(1f)
                )
                if (report.excludedSelfTransferPairs > 0) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(OrosiSurfaceContainer)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${PersianNumberUtils.toPersianDigits(report.excludedSelfTransferPairs.toString())} انتقال خودی کسر شد",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                            color = NishapurTurquoiseSoft,
                            maxLines = 1
                        )
                    }
                }
            }

            // Dual KPI tiles (Income vs Expense)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(CommCardShape16)
                        .background(OrosiBalanceIncomeCardBg)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(NishapurTurquoiseBright.copy(alpha = 0.22f))
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ArrowDownward,
                                contentDescription = null,
                                tint = NishapurTurquoiseBright,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                        Text(
                            text = "کل ورودی",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = NishapurTurquoiseSoft
                        )
                    }
                    OrosiAmountText(
                        amountToman = report.totalIncomeToman,
                        color = AlabasterSilk,
                        numberStyle = MaterialTheme.typography.titleSmall.copy(fontSize = 15.5.sp),
                        numberFontWeight = FontWeight.ExtraBold,
                        unitStyle = MaterialTheme.typography.labelSmall,
                        unitColor = NishapurTurquoiseSoft,
                        isIncome = true,
                        hideAmount = hideAmounts,
                        stackedUnit = true
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(CommCardShape16)
                        .background(OrosiBalanceExpenseCardBg)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(RubyRoseSoft.copy(alpha = 0.22f))
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ArrowUpward,
                                contentDescription = null,
                                tint = RubyRoseSoft,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                        Text(
                            text = "کل خرج مصرفی",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = RubyRoseSoft
                        )
                    }
                    OrosiAmountText(
                        amountToman = report.totalExpenseToman,
                        color = AlabasterSilk,
                        numberStyle = MaterialTheme.typography.titleSmall.copy(fontSize = 15.5.sp),
                        numberFontWeight = FontWeight.ExtraBold,
                        unitStyle = MaterialTheme.typography.labelSmall,
                        unitColor = RubyRoseSoft,
                        hideAmount = hideAmounts,
                        stackedUnit = true
                    )
                }
            }

            // Net Balance Row
            val netColor = when {
                report.netBalanceToman > 0L -> NishapurTurquoiseSoft
                report.netBalanceToman < 0L -> RubyRoseSoft
                else -> SaffronGoldLight
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(OrosiSurfaceContainer)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "تراز خالص این دوره",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = AlabasterSilk
                )
                OrosiAmountText(
                    amountToman = kotlin.math.abs(report.netBalanceToman),
                    color = netColor,
                    numberStyle = MaterialTheme.typography.titleSmall,
                    numberFontWeight = FontWeight.ExtraBold,
                    unitStyle = MaterialTheme.typography.labelSmall,
                    unitColor = netColor.copy(alpha = 0.85f),
                    isIncome = report.netBalanceToman > 0L,
                    hideAmount = hideAmounts,
                    prefixText = if (!hideAmounts && report.netBalanceToman < 0L) "کسری" else null
                )
            }

            // Key Metrics Strip: Savings Rate | Daily Average | MoM Comparison
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val savingsLabel = report.savingsRatePercent?.let { pct ->
                    if (pct >= 0) "${PersianNumberUtils.toPersianDigits(pct.toString())}٪"
                    else "کسری ${PersianNumberUtils.toPersianDigits(kotlin.math.abs(pct).toString())}٪"
                } ?: "—"
                val savingsColor = when {
                    (report.savingsRatePercent ?: 0) > 0 -> NishapurTurquoiseSoft
                    (report.savingsRatePercent ?: 0) < 0 -> RubyRoseSoft
                    else -> OrosiOnSurfaceVariant
                }
                ReportMetricTile(
                    label = "نرخ پس‌انداز",
                    value = if (hideAmounts) "•••" else savingsLabel,
                    valueColor = savingsColor,
                    modifier = Modifier.weight(1f)
                )

                ReportMetricTile(
                    label = "میانگین روزانه",
                    value = PersianNumberUtils.formatToman(
                        report.dailyAverageExpenseToman,
                        showUnit = false,
                        hideAmount = hideAmounts
                    ),
                    valueColor = AlabasterSilk,
                    modifier = Modifier.weight(1f)
                )

                val momDelta = report.expenseDeltaPercent
                val (momText, momColor) = when {
                    !isMonthlyMode || momDelta == null -> Pair("—", OrosiOnSurfaceVariant)
                    momDelta < 0 -> Pair(
                        "${PersianNumberUtils.toPersianDigits(kotlin.math.abs(momDelta).toString())}٪ کمتر",
                        NishapurTurquoiseSoft
                    )
                    momDelta > 0 -> Pair(
                        "${PersianNumberUtils.toPersianDigits(momDelta.toString())}٪ بیشتر",
                        RubyRoseSoft
                    )
                    else -> Pair("برابر", SaffronGoldLight)
                }
                ReportMetricTile(
                    label = "نسبت به ماه قبل",
                    value = if (hideAmounts) "•••" else momText,
                    valueColor = momColor,
                    modifier = Modifier.weight(1f)
                )
            }

            // Open Commitments Summary Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "تعهدات باز (چک، قسط و دُنگ):",
                    style = MaterialTheme.typography.bodySmall,
                    color = OrosiOnSurfaceVariant
                )
                OrosiAmountText(
                    amountToman = activeCommitments,
                    color = SaffronGoldLight,
                    numberStyle = MaterialTheme.typography.labelLarge,
                    numberFontWeight = FontWeight.Bold,
                    unitStyle = MaterialTheme.typography.labelSmall,
                    unitColor = SaffronGoldLight.copy(alpha = 0.8f),
                    hideAmount = hideAmounts
                )
            }
        }

        // 4. Interactive 6-Month Grouped Bar Chart (Monthly Mode)
        if (isMonthlyMode && trendPoints.isNotEmpty()) {
            SixMonthGroupedBarChartCard(
                points = trendPoints,
                selectedYearMonthKey = selectedYearMonthKey,
                onSelectMonth = { ym -> selectedYearMonthKey = ym }
            )
        }

        // 5. Expense Tagging Coverage Progress Card
        val totalExpenseCount = report.taggedExpenseCount + report.untaggedExpenseCount
        if (totalExpenseCount > 0 && report.totalExpenseToman > 0L) {
            val taggedRatio = (report.taggedExpenseToman.toFloat() / report.totalExpenseToman.toFloat())
                .coerceIn(0f, 1f)
            val taggedPercent = (taggedRatio * 100f).toInt()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CommCardShape22)
                    .background(OrosiSurfaceContainer)
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "شفافیت بابتِ مخارج",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = AlabasterSilk
                    )
                    Text(
                        text = "${PersianNumberUtils.toPersianDigits(taggedPercent.toString())}٪ مشخص شده",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = if (taggedPercent >= 70) NishapurTurquoiseSoft else SaffronGoldLight
                    )
                }
                LinearProgressIndicator(
                    progress = { taggedRatio.coerceAtLeast(0.03f) },
                    color = if (taggedPercent >= 70) NishapurTurquoiseBright else SaffronGoldBright,
                    trackColor = OrosiSurfaceHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(CommPillShape50)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "مشخص‌شده: ${PersianNumberUtils.toPersianDigits(report.taggedExpenseCount.toString())} مورد",
                        style = MaterialTheme.typography.labelSmall,
                        color = NishapurTurquoiseSoft
                    )
                    Text(
                        text = "بدون بابت: ${PersianNumberUtils.toPersianDigits(report.untaggedExpenseCount.toString())} مورد",
                        style = MaterialTheme.typography.labelSmall,
                        color = OrosiOnSurfaceVariant
                    )
                }
            }
        }

        // 6. Breakdown by Bank (Self-transfer cleaned)
        if (report.expensesByBank.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CommCardShape22)
                    .background(OrosiSurfaceContainer)
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "تفکیک مخارج بر اساس بانک",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = PersianLapisLight
                )
                val maxBankExpense = report.expensesByBank.maxOf { it.second }.coerceAtLeast(1L)
                report.expensesByBank.forEach { (bank, amount) ->
                    val ratio = (amount.toFloat() / maxBankExpense.toFloat()).coerceIn(0.06f, 1f)
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "بانک $bank",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = AlabasterSilk
                            )
                            OrosiAmountText(
                                amountToman = amount,
                                color = NishapurTurquoiseSoft,
                                numberStyle = MaterialTheme.typography.bodyMedium,
                                numberFontWeight = FontWeight.Bold,
                                unitStyle = MaterialTheme.typography.labelSmall,
                                unitColor = NishapurTurquoiseSoft.copy(alpha = 0.8f),
                                hideAmount = hideAmounts
                            )
                        }
                        LinearProgressIndicator(
                            progress = { ratio },
                            color = NishapurTurquoise,
                            trackColor = OrosiSurfaceHigh,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CommPillShape50)
                        )
                    }
                }
            }
        }

        // 7. Top Tagged Expenses (High-value labeled spending insights)
        if (report.topTaggedExpenses.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CommCardShape22)
                    .background(OrosiSurfaceContainer)
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "بیشترین مخارج برچسب‌خورده",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = RubyRoseSoft
                )
                report.topTaggedExpenses.forEach { (purposeLabel, amount) ->
                    ReportAmountRow(
                        label = purposeLabel,
                        amountToman = amount,
                        color = AlabasterSilk,
                        hideAmounts = hideAmounts
                    )
                }
            }
        }

        // 8. Top All Expenses (Preserved classic breakdown including untagged bank spends)
        if (report.topAllExpenses.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CommCardShape22)
                    .background(OrosiSurfaceContainer)
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "بیشترین مخارج بر اساس عنوان / بابت",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = SaffronGoldLight
                )
                report.topAllExpenses.forEach { (purposeLabel, amount) ->
                    ReportAmountRow(
                        label = purposeLabel,
                        amountToman = amount,
                        color = AlabasterSilk,
                        hideAmounts = hideAmounts
                    )
                }
            }
        }

        // 9. Top Income Sources
        if (report.topIncomeSources.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CommCardShape22)
                    .background(OrosiSurfaceContainer)
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "بیشترین منابع واریز",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = NishapurTurquoiseSoft
                )
                report.topIncomeSources.forEach { (sourceLabel, amount) ->
                    ReportAmountRow(
                        label = sourceLabel,
                        amountToman = amount,
                        color = NishapurTurquoiseSoft,
                        isIncome = true,
                        hideAmounts = hideAmounts
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }

    if (showMonthPickerSheet) {
        ReportMonthPickerSheet(
            selectedYearMonthKey = selectedYearMonthKey,
            maxYearMonthKey = currentYearMonthKey,
            onSelectYearMonth = { ym ->
                selectedYearMonthKey = ym
                showMonthPickerSheet = false
            },
            onDismiss = { showMonthPickerSheet = false }
        )
    }
}

@Composable
private fun ReportMetricTile(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(OrosiSurfaceContainer)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
            color = OrosiOnSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.ExtraBold,
                fontSize = 12.5.sp
            ),
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SixMonthGroupedBarChartCard(
    points: List<MonthlyReportBarPoint>,
    selectedYearMonthKey: Int,
    onSelectMonth: (Int) -> Unit
) {
    val maxVal = remember(points) {
        points.maxOfOrNull { maxOf(it.incomeToman, it.expenseToman) }?.coerceAtLeast(1L) ?: 1L
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CommCardShape22)
            .background(OrosiSurfaceContainer)
            .padding(18.dp)
            .testTag("reports_6month_chart_card"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "روند ۶ ماه اخیر",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = AlabasterSilk
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(NishapurTurquoiseBright)
                    )
                    Text(
                        text = "ورودی",
                        style = MaterialTheme.typography.labelSmall,
                        color = OrosiOnSurfaceVariant
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(RubyRoseSoft)
                    )
                    Text(
                        text = "خرج",
                        style = MaterialTheme.typography.labelSmall,
                        color = OrosiOnSurfaceVariant
                    )
                }
            }
        }

        // Pure lightweight Compose Canvas for 120Hz fluidity
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(124.dp)
        ) {
            val count = points.size.coerceAtLeast(1)
            val slotWidth = size.width / count
            val barWidth = (slotWidth * 0.24f).coerceIn(8f, 22f)
            val barGap = (slotWidth * 0.08f).coerceIn(3f, 8f)
            val chartBottom = size.height - 6f
            val usableHeight = (size.height - 16f).coerceAtLeast(24f)

            // Subtle horizontal guide lines
            val guideColor = OrosiHairline.copy(alpha = 0.55f)
            for (step in 1..2) {
                val y = chartBottom - usableHeight * (step / 3f)
                drawLine(
                    color = guideColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            // Render points left-to-right matching the Row labels below (in RTL, index 0 is on the right)
            points.forEachIndexed { idx, pt ->
                // Align with RTL Row order (index 0 at rightmost slot)
                val visualIndex = (count - 1) - idx
                val slotStart = visualIndex * slotWidth
                val centerX = slotStart + slotWidth / 2f
                val isSelected = pt.yearMonthKey == selectedYearMonthKey

                if (isSelected) {
                    drawRoundRect(
                        color = PersianLapis.copy(alpha = 0.42f),
                        topLeft = Offset(slotStart + 3f, 2f),
                        size = Size((slotWidth - 6f).coerceAtLeast(4f), size.height - 4f),
                        cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx())
                    )
                }

                val incHeight = if (pt.incomeToman > 0L) {
                    (usableHeight * (pt.incomeToman.toFloat() / maxVal.toFloat())).coerceAtLeast(6.dp.toPx())
                } else 3.dp.toPx()

                val expHeight = if (pt.expenseToman > 0L) {
                    (usableHeight * (pt.expenseToman.toFloat() / maxVal.toFloat())).coerceAtLeast(6.dp.toPx())
                } else 3.dp.toPx()

                val radius = CornerRadius(barWidth / 2f, barWidth / 2f)

                // Income bar
                drawRoundRect(
                    color = if (isSelected) NishapurTurquoiseBright else NishapurTurquoiseSoft.copy(alpha = 0.65f),
                    topLeft = Offset(centerX - barWidth - barGap / 2f, chartBottom - incHeight),
                    size = Size(barWidth, incHeight),
                    cornerRadius = radius
                )

                // Expense bar
                drawRoundRect(
                    color = if (isSelected) RubyRoseSoft else RubyRose.copy(alpha = 0.72f),
                    topLeft = Offset(centerX + barGap / 2f, chartBottom - expHeight),
                    size = Size(barWidth, expHeight),
                    cornerRadius = radius
                )
            }
        }

        // Interactive Tappable Month Labels Row (tap any month to inspect its full report)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            points.forEach { pt ->
                val isSelected = pt.yearMonthKey == selectedYearMonthKey
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) PersianLapis else Color.Transparent)
                        .clickable { onSelectMonth(pt.yearMonthKey) }
                        .padding(horizontal = 2.dp, vertical = 6.dp)
                        .testTag("report_chart_month_${pt.yearMonthKey}")
                ) {
                    Text(
                        text = pt.shortMonthName,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                        ),
                        color = if (isSelected) AlabasterSilk else OrosiOnSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportMonthPickerSheet(
    selectedYearMonthKey: Int,
    maxYearMonthKey: Int,
    onSelectYearMonth: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val maxYear = maxYearMonthKey / 100
    var pickerYear by remember(selectedYearMonthKey) {
        mutableIntStateOf((selectedYearMonthKey / 100).coerceIn(1395, maxYear))
    }

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
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "انتخاب ماه گزارش",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = AlabasterSilk
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CommCardShape16)
                    .background(OrosiSurfaceContainer)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { pickerYear = (pickerYear - 1).coerceAtLeast(1395) },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "سال قبل",
                        tint = AlabasterSilk
                    )
                }
                Text(
                    text = "سال ${PersianNumberUtils.toPersianDigits(pickerYear.toString())}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = AlabasterSilk
                )
                IconButton(
                    onClick = { if (pickerYear < maxYear) pickerYear += 1 },
                    enabled = pickerYear < maxYear,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronLeft,
                        contentDescription = "سال بعد",
                        tint = if (pickerYear < maxYear) AlabasterSilk else OrosiOnSurfaceVariant.copy(alpha = 0.35f)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (rowIdx in 0 until 4) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
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
                                    .heightIn(min = 48.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 6.dp)
                                ) {
                                    Text(
                                        text = JalaliCalendarUtils.jalaliMonthName(monthNum),
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

@Composable
private fun ReportAmountRow(
    label: String,
    amountToman: Long,
    color: Color,
    isIncome: Boolean = false,
    hideAmounts: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = OrosiOnSurfaceVariant,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.width(8.dp))
        OrosiAmountText(
            amountToman = amountToman,
            color = color,
            numberStyle = MaterialTheme.typography.titleSmall,
            numberFontWeight = FontWeight.Bold,
            unitStyle = MaterialTheme.typography.labelSmall,
            unitColor = color.copy(alpha = 0.8f),
            isIncome = isIncome,
            hideAmount = hideAmounts
        )
    }
}

@Composable
private fun SpaciousPurpleSayyadCard(
    cheque: CommitmentEntity,
    hideAmounts: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        RoyalAmethyst,
                        RoyalAmethystContainer,
                        RoyalAmethystDeep
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(800f, 600f)
                )
            )
            .clickable(onClick = onClick)
            .padding(22.dp)
            .testTag("purple_sayyad_cheque_card")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = cheque.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    lineHeight = 22.sp
                ),
                color = AlabasterSilk,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                Box(
                    modifier = Modifier
                        .clip(CommPillShape50)
                        .background(Color.White.copy(alpha = 0.14f))
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = cheque.dueDateStr,
                        style = MaterialTheme.typography.labelMedium,
                        color = RoyalAmethystSoft,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            OrosiAmountText(
                amountToman = cheque.amountToman,
                color = AlabasterSilk,
                numberStyle = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Black,
                    fontSize = 26.sp
                ),
                unitStyle = MaterialTheme.typography.titleSmall,
                unitColor = RoyalAmethystSoft,
                hideAmount = hideAmounts,
                modifier = Modifier.testTag("sayyad_hero_amount")
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (cheque.isSettled) "پاس شد / تسویه شد ✓" else cheque.counterparty,
                style = MaterialTheme.typography.bodySmall,
                color = if (cheque.isSettled) NishapurTurquoiseSoft else RoyalAmethystSoft,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SpaciousCommitmentCard(
    commitment: CommitmentEntity,
    accentColor: Color,
    icon: ImageVector,
    hideAmounts: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CommCardShape20)
            .background(OrosiSurfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp)
            .testTag("commitment_card_${commitment.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(accentColor.copy(alpha = 0.15f))
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = commitment.title,
                        tint = accentColor,
                        modifier = Modifier.size(21.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = commitment.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        lineHeight = 21.sp
                    ),
                    color = AlabasterSilk,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            OrosiAmountText(
                amountToman = commitment.amountToman,
                color = accentColor,
                numberStyle = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                numberFontWeight = FontWeight.Bold,
                unitStyle = MaterialTheme.typography.labelSmall,
                unitColor = accentColor.copy(alpha = 0.8f),
                hideAmount = hideAmounts
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(CommPillShape50)
                    .background(accentColor.copy(alpha = 0.12f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (commitment.isSettled) "پاس شد / تسویه شد ✓" else commitment.dueDateStr,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = if (commitment.isSettled) NishapurTurquoiseBright else accentColor,
                    maxLines = 1,
                    softWrap = false
                )
            }

            if (commitment.counterparty.isNotBlank()) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = commitment.counterparty,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                    color = OrosiOnSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
