package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.data.local.AppSettingsEntity
import com.example.data.local.BankAccountEntity
import com.example.data.local.CommitmentEntity
import com.example.data.local.TransactionEntity
import com.example.domain.OrosiCashflowAnalyzer
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
import com.example.ui.theme.PersianLapisDeep
import com.example.ui.theme.PersianLapisGlow
import com.example.ui.theme.PersianLapisLight
import com.example.ui.theme.RubyRose
import com.example.ui.theme.RubyRoseSoft
import com.example.ui.theme.SaffronGold
import com.example.ui.theme.SaffronGoldBright
import com.example.ui.theme.SaffronGoldLight

// Hoisted static shapes, colors, and brushes to guarantee zero per-frame allocations during 120Hz scrolling
private val DashCardShape28 = RoundedCornerShape(28.dp)
private val DashCardShape20 = RoundedCornerShape(20.dp)
private val DashCardShape18 = RoundedCornerShape(18.dp)
private val DashPillShape12 = RoundedCornerShape(12.dp)
private val DashPillShape50 = RoundedCornerShape(50)
private val DashTankhahCardBg = Color(0xFF112936)
private val DashDeleteOverlayBg = OrosiSurfaceHigh.copy(alpha = 0.96f)
private val DashExpenseTagBg = RubyRose.copy(alpha = 0.22f)
private val DashIncomeTagBg = NishapurTurquoiseDeep.copy(alpha = 0.72f)
private val DashHeroCircleGlowColor = NishapurTurquoiseBright.copy(alpha = 0.08f)
private val DashUnitIncomeColor = NishapurTurquoiseSoft.copy(alpha = 0.82f)
private val DashUnitGoldColor = SaffronGoldLight.copy(alpha = 0.82f)
private val DashUnitExpenseColor = RubyRoseSoft.copy(alpha = 0.82f)
private val DashUnitMutedColor = OrosiOnSurfaceVariant.copy(alpha = 0.82f)
private val DashHeaderVoiceBrush = Brush.horizontalGradient(
    colors = listOf(NishapurTurquoise, NishapurTurquoiseDeep)
)
private val DashHeroBgBrush = Brush.linearGradient(
    colors = listOf(PersianLapisGlow, PersianLapis, PersianLapisDeep),
    start = Offset(0f, 0f),
    end = Offset(900f, 700f)
)
private val DashHeroBorderBrush = Brush.verticalGradient(
    colors = listOf(
        Color.White.copy(alpha = 0.26f),
        PersianLapisLight.copy(alpha = 0.12f)
    )
)

@Composable
fun DashboardScreen(
    settings: AppSettingsEntity,
    bankAccounts: List<BankAccountEntity>,
    commitments: List<CommitmentEntity>,
    transactions: List<TransactionEntity>,
    isSyncingSms: Boolean = false,
    hasLoadedFromDb: Boolean = true,
    onToggleHideAmounts: () -> Unit,
    onSetOnlyTankhahMode: (Boolean) -> Unit,
    onToggleAccountTankhah: (String) -> Unit,
    onOpenAccountsTab: () -> Unit,
    onHeroCardClick: () -> Unit,
    onAddVoiceClick: () -> Unit,
    onTransactionClick: (TransactionEntity) -> Unit,
    onUntaggedMicClick: (TransactionEntity) -> Unit,
    onDeleteTransaction: (TransactionEntity) -> Unit = {},
    onOpenTransactionsTab: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val totalAllAccountsBalance = remember(bankAccounts) {
        bankAccounts.sumOf { it.balanceToman.coerceAtLeast(0L) }
    }
    val rawTankhahSum = remember(bankAccounts) {
        bankAccounts.filter { it.isTankhah }.sumOf { it.balanceToman.coerceAtLeast(0L) }
    }
    val nonTankhahBalance = remember(bankAccounts) {
        bankAccounts.filter { !it.isTankhah }.sumOf { it.balanceToman.coerceAtLeast(0L) }
    }
    val unpaidCommitmentsToman = remember(commitments) {
        commitments
            .filter { !it.isSettled && (it.category == "SAYYAD_ISSUED" || it.category == "LOAN_INSTALLMENT") }
            .sumOf { it.amountToman }
    }
    val tankhahBalance = remember(rawTankhahSum, unpaidCommitmentsToman) {
        if (unpaidCommitmentsToman in 1L until rawTankhahSum) {
            (rawTankhahSum - unpaidCommitmentsToman).coerceAtLeast(0L)
        } else {
            rawTankhahSum
        }
    }

    val visibleTransactions = remember(transactions) {
        transactions.filter { it.totalAmountToman > 0L }
    }
    val recentTxRows = remember(visibleTransactions) {
        visibleTransactions.take(20).map { tx ->
            val shortBank = tx.shortBankName
            val isIncome = tx.type == "INCOME"
            val rowTitle = if (tx.isUntagged) {
                if (isIncome) "واریز به کارت ($shortBank)" else "خرید کارتی ($shortBank)"
            } else {
                tx.title.ifBlank { tx.displayTitle }
            }
            val displayAmt = when (tx.type) {
                "SELF_TRANSFER" -> tx.totalAmountToman
                else -> tx.userShareToman.takeIf { it > 0L } ?: tx.totalAmountToman
            }
            val color = when (tx.type) {
                "INCOME" -> NishapurTurquoiseSoft
                "GOLD_SAVE" -> SaffronGoldLight
                "SELF_TRANSFER" -> OrosiOnSurfaceVariant
                else -> RubyRoseSoft
            }
            val uColor = when (tx.type) {
                "INCOME" -> DashUnitIncomeColor
                "GOLD_SAVE" -> DashUnitGoldColor
                "SELF_TRANSFER" -> DashUnitMutedColor
                else -> DashUnitExpenseColor
            }
            DashTxRowUi(
                transaction = tx,
                shortBank = shortBank,
                logoKey = com.example.ui.components.JibemanAssetLogoCache.resolveBankLogoKey(shortBank),
                rowTitle = rowTitle,
                subtitleText = tx.simpleSubtitle,
                displayAmountToman = displayAmt,
                formattedAmountRun = PersianNumberUtils.formatIsolatedAmountRun(displayAmt, isIncome),
                isIncome = isIncome,
                amountColor = color,
                unitColor = uColor,
                isLongTitle = rowTitle.length > 22
            )
        }
    }

    // Single hoisted delete-confirm ID for the whole LazyColumn (zero per-row Box / state overhead on scroll)
    var activeDeleteConfirmId by remember { mutableStateOf<Long?>(null) }
    val onRowLongClickForId = remember { { id: Long -> activeDeleteConfirmId = id } }
    val onClearDeleteConfirm = remember { { _: Long -> activeDeleteConfirmId = null } }

    // Gate initial skeleton shimmer & sync banner on hasLoadedFromDb so cold starts with cached Room data
    // NEVER flash "۰ تومان" or "در حال بازخوانی پیامک‌های بانکی..." before Room emits its first snapshot.
    val isInitialSkeletonLoading = hasLoadedFromDb && isSyncingSms && bankAccounts.isEmpty() && visibleTransactions.isEmpty()
    val showHeroPlaceholderShimmer = !hasLoadedFromDb || isInitialSkeletonLoading
    val shimmerBrush = if (showHeroPlaceholderShimmer) orosiShimmerBrush() else null

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("dashboard_screen"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (hasLoadedFromDb && isSyncingSms && !isInitialSkeletonLoading) {
            item(key = "sms_sync_banner", contentType = "sync_banner") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(OrosiSurfaceHigh)
                        .border(1.dp, NishapurTurquoiseBright, RoundedCornerShape(20.dp))
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                        .testTag("sms_sync_loading_card"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    CircularProgressIndicator(
                        color = NishapurTurquoiseBright,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "در حال بازخوانی و تحلیل پیامک‌های بانکی...",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = AlabasterSilk
                    )
                }
            }
        }

        // 1. Jibeman-Style Financial Status Hero Card ("وضعیت مالی")
        item(key = "hero_card", contentType = "hero_card") {
            JibemanFinancialStatusHeroCard(
                totalAllAccountsBalance = totalAllAccountsBalance,
                unpaidCommitmentsToman = unpaidCommitmentsToman,
                tankhahBalance = tankhahBalance,
                nonTankhahBalance = nonTankhahBalance,
                hideAmounts = settings.hideAmounts,
                tankhahModeEnabled = settings.tankhahModeEnabled,
                onlyTankhahMode = settings.onlyTankhahMode,
                isSyncingSms = hasLoadedFromDb && isSyncingSms,
                showInitialShimmer = showHeroPlaceholderShimmer,
                shimmerBrush = shimmerBrush,
                onToggleHideAmounts = onToggleHideAmounts,
                onSetOnlyTankhahMode = onSetOnlyTankhahMode,
                onClick = onHeroCardClick
            )
        }

        // 2. Horizontal "حساب‌ها" (Bank Accounts) Carousel
        item(key = "accounts_carousel_section", contentType = "accounts_carousel") {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "حساب‌ها",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = AlabasterSilk
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier
                            .clip(DashPillShape50)
                            .clickable(onClick = onOpenAccountsTab)
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .testTag("open_accounts_tab_link")
                    ) {
                        Text(
                            text = "مدیریت همه حساب‌ها",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = NishapurTurquoiseSoft
                        )
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = null,
                            tint = NishapurTurquoiseSoft,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("bank_accounts_carousel"),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (isInitialSkeletonLoading && shimmerBrush != null) {
                        items(2, key = { "account_shimmer_$it" }, contentType = { "account_shimmer" }) { idx ->
                            AccountCardSkeletonShimmer(
                                shimmerBrush = shimmerBrush,
                                modifier = Modifier.testTag("account_shimmer_card_$idx")
                            )
                        }
                    } else {
                        items(
                            items = bankAccounts,
                            key = { it.bankName },
                            contentType = { "bank_carousel_card" }
                        ) { account ->
                            BankAccountCarouselCard(
                                account = account,
                                hideAmounts = settings.hideAmounts,
                                showTankhahBadge = settings.tankhahModeEnabled,
                                onCardClick = onOpenAccountsTab,
                                onToggleAccountTankhah = onToggleAccountTankhah
                            )
                        }
                    }
                }
            }
        }

        // 3. Section Header "تراکنش‌های اخیر" + Sleek Turquoise Pill Button "+ ثبت صوتی"
        item(key = "recent_transactions_header", contentType = "section_header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "تراکنش‌های اخیر",
                    style = MaterialTheme.typography.titleLarge,
                    color = AlabasterSilk
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(DashHeaderVoiceBrush, DashPillShape50)
                        .border(1.dp, NishapurTurquoiseBright, DashPillShape50)
                        .clickable(onClick = onAddVoiceClick)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .testTag("header_voice_add_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "+ ثبت صوتی",
                        tint = AlabasterSilk,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "+ ثبت صوتی",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = AlabasterSilk,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        // 4. Recent Transaction Cards (or Calm Shimmer Skeletons during initial inbox scan)
        if (isInitialSkeletonLoading && shimmerBrush != null) {
            items(4, key = { "home_tx_shimmer_$it" }, contentType = { "tx_shimmer" }) { idx ->
                TransactionRowSkeletonShimmer(
                    shimmerBrush = shimmerBrush,
                    modifier = Modifier.testTag("tx_shimmer_card_$idx")
                )
            }
        } else {
            items(
                items = recentTxRows,
                key = { row -> row.transaction.id },
                contentType = { "transaction_card" }
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
                        CalmTransactionCardFast(
                            row = row,
                            hideAmounts = settings.hideAmounts,
                            onTransactionClick = onTransactionClick,
                            onRowLongClickId = onClearDeleteConfirm,
                            onUntaggedMicClick = onUntaggedMicClick
                        )
                    }
                } else {
                    CalmTransactionCardFast(
                        row = row,
                        hideAmounts = settings.hideAmounts,
                        onTransactionClick = onTransactionClick,
                        onRowLongClickId = onRowLongClickForId,
                        onUntaggedMicClick = onUntaggedMicClick
                    )
                }
            }
        }

        if (visibleTransactions.isNotEmpty()) {
            item(key = "view_all_transactions_footer", contentType = "footer_link") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(DashCardShape18)
                        .background(OrosiSurfaceHigh)
                        .clickable(onClick = onOpenTransactionsTab)
                        .padding(vertical = 14.dp, horizontal = 18.dp)
                        .testTag("view_all_transactions_button")
                ) {
                    Text(
                        text = "مشاهده همه تراکنش‌ها (${PersianNumberUtils.toPersianDigits(visibleTransactions.size.toString())} مورد)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = NishapurTurquoiseSoft
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.ChevronLeft,
                        contentDescription = null,
                        tint = NishapurTurquoiseSoft,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun InlineDeleteConfirmOverlayBox(
    transactionId: Long,
    onConfirmDelete: () -> Unit,
    onCancel: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        content()
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(DashDeleteOverlayBg, DashCardShape20)
                .border(1.5.dp, RubyRoseSoft, DashCardShape20)
                .padding(horizontal = 14.dp)
                .testTag("swipe_delete_confirm_bar_$transactionId"),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "حذف این تراکنش؟",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = RubyRoseSoft
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(RubyRose, DashPillShape12)
                            .border(1.dp, RubyRoseSoft, DashPillShape12)
                            .clickable(onClick = onConfirmDelete)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .testTag("confirm_swipe_delete_$transactionId")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "حذف قطعی",
                            tint = AlabasterSilk,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "حذف قطعی",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = AlabasterSilk,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(OrosiSurfaceContainer, DashPillShape12)
                            .border(1.dp, OrosiHairline, DashPillShape12)
                            .clickable(onClick = onCancel)
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                            .testTag("cancel_swipe_delete_$transactionId")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "انصراف",
                            tint = OrosiOnSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "انصراف",
                            style = MaterialTheme.typography.labelLarge,
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
}

@Composable
fun TwoStepSwipeToDeleteWrapper(
    transactionId: Long,
    onConfirmDelete: () -> Unit,
    content: @Composable (onLongPressTrigger: () -> Unit) -> Unit
) {
    var isConfirmOpen by remember(transactionId) { mutableStateOf(false) }

    if (isConfirmOpen) {
        InlineDeleteConfirmOverlayBox(
            transactionId = transactionId,
            onConfirmDelete = {
                isConfirmOpen = false
                onConfirmDelete()
            },
            onCancel = { isConfirmOpen = false }
        ) {
            content { isConfirmOpen = false }
        }
    } else {
        content { isConfirmOpen = true }
    }
}

private enum class HeroAccountFilter {
    ALL,
    PERSONAL,
    TANKHAH_COMPANY
}

@Composable
private fun JibemanFinancialStatusHeroCard(
    totalAllAccountsBalance: Long,
    unpaidCommitmentsToman: Long,
    tankhahBalance: Long,
    nonTankhahBalance: Long,
    hideAmounts: Boolean,
    tankhahModeEnabled: Boolean,
    onlyTankhahMode: Boolean,
    isSyncingSms: Boolean = false,
    showInitialShimmer: Boolean = false,
    shimmerBrush: Brush? = null,
    onToggleHideAmounts: () -> Unit,
    onSetOnlyTankhahMode: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    var selectedFilter by androidx.compose.runtime.remember(tankhahModeEnabled) {
        androidx.compose.runtime.mutableStateOf(
            if (!tankhahModeEnabled) HeroAccountFilter.ALL
            else if (onlyTankhahMode) HeroAccountFilter.TANKHAH_COMPANY
            else HeroAccountFilter.PERSONAL
        )
    }

    val effectiveFilter = if (!tankhahModeEnabled) HeroAccountFilter.ALL else selectedFilter

    val selectedTotalBalance = when (effectiveFilter) {
        HeroAccountFilter.ALL -> totalAllAccountsBalance
        HeroAccountFilter.PERSONAL -> nonTankhahBalance
        HeroAccountFilter.TANKHAH_COMPANY -> tankhahBalance
    }

    val heroDisplay = OrosiCashflowAnalyzer.resolveHeroBalanceDisplay(
        totalBalanceToman = selectedTotalBalance,
        unpaidCommitmentsToman = unpaidCommitmentsToman
    )
    val progressRatio = heroDisplay.coverageRatio

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DashCardShape28)
            .background(DashHeroBgBrush, DashCardShape28)
            .drawBehind {
                drawCircle(
                    color = DashHeroCircleGlowColor,
                    radius = size.height * 0.6f,
                    center = Offset(size.width * 0.15f, size.height * 0.2f)
                )
            }
            .border(
                width = 1.dp,
                brush = DashHeroBorderBrush,
                shape = DashCardShape28
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 20.dp)
            .testTag("dashboard_hero_card")
    ) {
            // Top Row: "وضعیت مالی" on the right, Eye icon (👁) on the left
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "وضعیت مالی",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp
                    ),
                    color = AlabasterSilk
                )

                IconButton(
                    onClick = onToggleHideAmounts,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.14f))
                        .testTag("toggle_hide_amounts_button")
                ) {
                    Icon(
                        imageVector = if (hideAmounts) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (hideAmounts) "نمایش مبالغ" else "مخفی کردن مبالغ",
                        tint = AlabasterSilk,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            // If tankhahModeEnabled == true: show a compact 3-chip filter bar ("همه حساب‌ها" | "شخصی" | "تنخواه / شرکت")
            if (tankhahModeEnabled) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.28f))
                        .border(1.dp, PersianLapisLight.copy(alpha = 0.32f), RoundedCornerShape(50))
                        .padding(3.dp)
                        .testTag("hero_mode_switcher"),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val chips = listOf(
                        Triple(HeroAccountFilter.ALL, "همه حساب‌ها", "mode_all_accounts_button"),
                        Triple(HeroAccountFilter.PERSONAL, "شخصی", "mode_personal_accounts_button"),
                        Triple(HeroAccountFilter.TANKHAH_COMPANY, "تنخواه / شرکت", "mode_tankhah_only_button")
                    )
                    chips.forEach { (filterMode, label, tag) ->
                        val isSelected = effectiveFilter == filterMode
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(50))
                                .background(if (isSelected) NishapurTurquoise else Color.Transparent)
                                .clickable {
                                    selectedFilter = filterMode
                                    when (filterMode) {
                                        HeroAccountFilter.TANKHAH_COMPANY -> onSetOnlyTankhahMode(true)
                                        HeroAccountFilter.PERSONAL -> onSetOnlyTankhahMode(false)
                                        HeroAccountFilter.ALL -> onSetOnlyTankhahMode(false)
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                .testTag(tag)
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp
                                ),
                                color = if (isSelected) AlabasterSilk else PersianLapisLight,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Center Subtitle (13.sp): "موجودی آزاد" (or "موجودی شخصی" / "موجودی تنخواه و شرکت")
            // Center Big Number (28.sp bold, single-line): "۵,۳۴۳,۲۰۰ تومان"
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val centerSubtitle = when (effectiveFilter) {
                    HeroAccountFilter.ALL -> if (heroDisplay.isShortfall) "موجودی فعلی حساب‌ها" else "موجودی آزاد"
                    HeroAccountFilter.PERSONAL -> if (heroDisplay.isShortfall) "موجودی فعلی شخصی" else "موجودی شخصی"
                    HeroAccountFilter.TANKHAH_COMPANY -> if (heroDisplay.isShortfall) "موجودی فعلی تنخواه و شرکت" else "موجودی تنخواه و شرکت"
                }

                Text(
                    text = centerSubtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = PersianLapisLight,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.height(6.dp))

                if (showInitialShimmer && shimmerBrush != null) {
                    Box(
                        modifier = Modifier
                            .width(196.dp)
                            .height(34.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(shimmerBrush)
                            .testTag("hero_balance_shimmer")
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "در حال دریافت موجودی همه بانک‌ها...",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = NishapurTurquoiseSoft
                    )
                } else {
                    OrosiAmountText(
                        amountToman = heroDisplay.headlineAmountToman,
                        color = AlabasterSilk,
                        numberStyle = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 28.sp,
                            lineHeight = 34.sp
                        ),
                        unitStyle = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        ),
                        unitColor = PersianLapisLight,
                        hideAmount = hideAmounts,
                        unitSpacing = 6.dp,
                        modifier = Modifier.testTag("hero_peace_of_mind_amount")
                    )

                    if (heroDisplay.isShortfall) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .clip(DashPillShape12)
                                .background(SaffronGold.copy(alpha = 0.16f))
                                .padding(horizontal = 11.dp, vertical = 5.dp)
                                .testTag("hero_commitment_shortfall_badge")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(SaffronGoldBright)
                            )
                            Text(
                                text = "کسری پوشش تعهدات:",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = SaffronGoldLight,
                                maxLines = 1
                            )
                            OrosiAmountText(
                                amountToman = heroDisplay.shortfallAmountToman,
                                color = SaffronGoldLight,
                                numberStyle = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                                numberFontWeight = FontWeight.ExtraBold,
                                unitStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                                unitColor = SaffronGoldLight.copy(alpha = 0.85f),
                                hideAmount = hideAmounts,
                                unitSpacing = 3.dp
                            )
                        }
                    }

                    AnimatedVisibility(visible = isSyncingSms) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(top = 6.dp)
                        ) {
                            LinearProgressIndicator(
                                color = NishapurTurquoiseBright,
                                trackColor = Color.White.copy(alpha = 0.16f),
                                modifier = Modifier
                                    .width(140.dp)
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(50))
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "در حال به‌روزرسانی مانده بانک‌ها...",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                                color = NishapurTurquoiseSoft
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Horizontal progress bar (Turquoise when covered, warm Saffron-Amber when showing commitment shortfall)
            LinearProgressIndicator(
                progress = { progressRatio },
                color = if (heroDisplay.isShortfall) SaffronGoldBright else NishapurTurquoiseBright,
                trackColor = Color.White.copy(alpha = 0.18f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(50))
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Two clean summary rows below the progress bar (matching Jibeman):
            // 1) Green dot (●): "موجودی کل" on the right, amount on the left.
            // 2) Red/Coral dot (●): "تعهدات مالی این ماه" on the right, commitment amount (۰ تومان) on the left.
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF34D399))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "موجودی کل",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = PersianLapisLight
                        )
                    }
                    if (showInitialShimmer && shimmerBrush != null) {
                        Box(
                            modifier = Modifier
                                .width(92.dp)
                                .height(16.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(shimmerBrush)
                        )
                    } else {
                        OrosiAmountText(
                            amountToman = selectedTotalBalance,
                            color = NishapurTurquoiseSoft,
                            numberStyle = MaterialTheme.typography.titleSmall,
                            numberFontWeight = FontWeight.Bold,
                            unitStyle = MaterialTheme.typography.labelSmall,
                            unitColor = NishapurTurquoiseSoft.copy(alpha = 0.85f),
                            hideAmount = hideAmounts
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(RubyRoseSoft)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "تعهدات مالی این ماه",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = PersianLapisLight
                        )
                    }
                    if (showInitialShimmer && shimmerBrush != null) {
                        Box(
                            modifier = Modifier
                                .width(72.dp)
                                .height(16.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(shimmerBrush)
                        )
                    } else {
                        OrosiAmountText(
                            amountToman = unpaidCommitmentsToman,
                            color = RubyRoseSoft,
                            numberStyle = MaterialTheme.typography.titleSmall,
                            numberFontWeight = FontWeight.Bold,
                            unitStyle = MaterialTheme.typography.labelSmall,
                            unitColor = RubyRoseSoft.copy(alpha = 0.85f),
                            hideAmount = hideAmounts
                        )
                    }
                }
            }
    }
}

@Composable
internal fun orosiShimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "orosiShimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 900f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1250, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )
    return Brush.linearGradient(
        colors = listOf(
            OrosiSurfaceContainer.copy(alpha = 0.85f),
            PersianLapisLight.copy(alpha = 0.28f),
            OrosiSurfaceContainer.copy(alpha = 0.85f)
        ),
        start = Offset(translateAnim - 300f, 0f),
        end = Offset(translateAnim, 200f)
    )
}

@Composable
private fun AccountCardSkeletonShimmer(
    shimmerBrush: Brush,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(184.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(OrosiSurfaceHigh)
            .border(1.dp, OrosiHairline, RoundedCornerShape(20.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(shimmerBrush)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .width(74.dp)
                    .height(14.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(shimmerBrush)
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                modifier = Modifier
                    .width(60.dp)
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(shimmerBrush)
            )
            Box(
                modifier = Modifier
                    .width(116.dp)
                    .height(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(shimmerBrush)
            )
        }
    }
}

@Composable
internal fun TransactionRowSkeletonShimmer(
    shimmerBrush: Brush,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(OrosiSurfaceContainer)
            .border(1.dp, OrosiHairline, RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(shimmerBrush)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.62f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(shimmerBrush)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.42f)
                    .height(11.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(shimmerBrush)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .width(78.dp)
                .height(16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(shimmerBrush)
        )
    }
}

@Immutable
private data class DashTxRowUi(
    val transaction: TransactionEntity,
    val shortBank: String,
    val logoKey: String?,
    val rowTitle: String,
    val subtitleText: String,
    val displayAmountToman: Long,
    val formattedAmountRun: String,
    val isIncome: Boolean,
    val amountColor: Color,
    val unitColor: Color,
    val isLongTitle: Boolean
)

@Composable
private fun BankAccountCarouselCard(
    account: BankAccountEntity,
    hideAmounts: Boolean,
    showTankhahBadge: Boolean,
    onCardClick: () -> Unit,
    onToggleAccountTankhah: (String) -> Unit
) {
    val cardBg = if (showTankhahBadge && account.isTankhah) DashTankhahCardBg else OrosiSurfaceHigh
    val formattedBalance = remember(account.balanceToman) {
        PersianNumberUtils.formatIsolatedAmountRun(account.balanceToman, false)
    }
    val logoKey = remember(account.bankName) {
        com.example.ui.components.JibemanAssetLogoCache.resolveBankLogoKey(account.bankName)
    }

    Column(
        modifier = Modifier
            .width(184.dp)
            .clip(DashCardShape20)
            .background(cardBg, DashCardShape20)
            .clickable(onClick = onCardClick)
            .padding(14.dp)
            .testTag("carousel_account_${account.bankName}"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                com.example.ui.components.BankLogoAvatar(
                    bankName = account.bankName,
                    size = 40.dp,
                    preResolvedLogoKey = logoKey
                )
                Text(
                    text = account.bankName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = AlabasterSilk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (showTankhahBadge) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier
                        .clip(DashPillShape50)
                        .background(if (account.isTankhah) NishapurTurquoiseDeep else OrosiSurfaceContainer)
                        .clickable { onToggleAccountTankhah(account.bankName) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("carousel_tankhah_badge_${account.bankName}")
                ) {
                    if (account.isTankhah) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = NishapurTurquoiseBright,
                            modifier = Modifier.size(11.dp)
                        )
                    }
                    Text(
                        text = if (account.isTankhah) "تنخواه" else "شخصی",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (account.isTankhah) NishapurTurquoiseSoft else OrosiOnSurfaceVariant
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = "آخرین مانده",
                style = MaterialTheme.typography.labelSmall,
                color = OrosiOnSurfaceVariant
            )
            OrosiAmountText(
                amountToman = account.balanceToman,
                color = AlabasterSilk,
                numberStyle = MaterialTheme.typography.titleSmall,
                numberFontWeight = FontWeight.ExtraBold,
                unitStyle = MaterialTheme.typography.labelSmall,
                unitColor = OrosiOnSurfaceVariant,
                hideAmount = hideAmounts,
                preformattedNumberRun = formattedBalance
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CalmTransactionCardFast(
    row: DashTxRowUi,
    hideAmounts: Boolean,
    onTransactionClick: (TransactionEntity) -> Unit,
    onRowLongClickId: ((Long) -> Unit)? = null,
    onUntaggedMicClick: (TransactionEntity) -> Unit
) {
    val tx = row.transaction

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(OrosiSurfaceContainer, DashCardShape20)
            .combinedClickable(
                onClick = { onTransactionClick(tx) },
                onLongClick = if (onRowLongClickId != null) { { onRowLongClickId(tx.id) } } else null
            )
            .padding(horizontal = 14.dp, vertical = 11.dp)
            .testTag("transaction_item_${tx.id}"),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        com.example.ui.components.BankLogoAvatar(
            bankName = row.shortBank,
            size = 38.dp,
            preResolvedLogoKey = row.logoKey,
            directionIndicatorIsIncome = row.isIncome
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = row.rowTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                fontSize = if (row.isLongTitle) 13.5.sp else 14.5.sp,
                lineHeight = 19.sp,
                color = AlabasterSilk,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = row.subtitleText,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.5.sp,
                lineHeight = 15.sp,
                color = OrosiOnSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("transaction_subtitle_${tx.id}")
            )
            if (tx.isUntagged) {
                PulsingTurquoiseMicButton(
                    isIncome = row.isIncome,
                    onClick = { onUntaggedMicClick(tx) },
                    testTag = "untagged_mic_button_${tx.id}"
                )
            }
        }

        OrosiAmountText(
            amountToman = row.displayAmountToman,
            color = row.amountColor,
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

@Composable
fun CalmTransactionCard(
    transaction: TransactionEntity,
    hideAmounts: Boolean = false,
    onRowClick: () -> Unit,
    onRowLongClick: (() -> Unit)? = null,
    onUntaggedMicClick: () -> Unit
) {
    val row = remember(transaction) {
        val shortBank = transaction.shortBankName
        val isIncome = transaction.type == "INCOME"
        val rowTitle = if (transaction.isUntagged) {
            if (isIncome) "واریز به کارت ($shortBank)" else "خرید کارتی ($shortBank)"
        } else {
            transaction.title.ifBlank { transaction.displayTitle }
        }
        val displayAmt = when (transaction.type) {
            "SELF_TRANSFER" -> transaction.totalAmountToman
            else -> transaction.userShareToman.takeIf { it > 0L } ?: transaction.totalAmountToman
        }
        val color = when (transaction.type) {
            "INCOME" -> NishapurTurquoiseSoft
            "GOLD_SAVE" -> SaffronGoldLight
            "SELF_TRANSFER" -> OrosiOnSurfaceVariant
            else -> RubyRoseSoft
        }
        val uColor = when (transaction.type) {
            "INCOME" -> DashUnitIncomeColor
            "GOLD_SAVE" -> DashUnitGoldColor
            "SELF_TRANSFER" -> DashUnitMutedColor
            else -> DashUnitExpenseColor
        }
        DashTxRowUi(
            transaction = transaction,
            shortBank = shortBank,
            logoKey = com.example.ui.components.JibemanAssetLogoCache.resolveBankLogoKey(shortBank),
            rowTitle = rowTitle,
            subtitleText = transaction.simpleSubtitle,
            displayAmountToman = displayAmt,
            formattedAmountRun = PersianNumberUtils.formatIsolatedAmountRun(displayAmt, isIncome),
            isIncome = isIncome,
            amountColor = color,
            unitColor = uColor,
            isLongTitle = rowTitle.length > 22
        )
    }
    CalmTransactionCardFast(
        row = row,
        hideAmounts = hideAmounts,
        onTransactionClick = { onRowClick() },
        onRowLongClickId = if (onRowLongClick != null) { { onRowLongClick() } } else null,
        onUntaggedMicClick = { onUntaggedMicClick() }
    )
}

@Composable
private fun PulsingTurquoiseMicButton(
    isIncome: Boolean = false,
    onClick: () -> Unit,
    testTag: String
) {
    val labelText = if (isIncome) "بگو از کجا اومد؟" else "بگو بابت چی؟"
    val chipBg = if (isIncome) DashIncomeTagBg else DashExpenseTagBg
    val chipContentColor = if (isIncome) NishapurTurquoiseSoft else RubyRoseSoft
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier
            .padding(top = 3.dp)
            .wrapContentWidth(unbounded = false)
            .background(chipBg, DashPillShape12)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .testTag(testTag)
    ) {
        Icon(
            imageVector = Icons.Default.Mic,
            contentDescription = labelText,
            tint = chipContentColor,
            modifier = Modifier.size(13.dp)
        )
        Text(
            text = labelText,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            fontSize = 11.5.sp,
            lineHeight = 15.sp,
            color = chipContentColor,
            maxLines = 1,
            softWrap = false
        )
    }
}
