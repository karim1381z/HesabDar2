package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.BankAccountEntity
import com.example.domain.JalaliCalendarUtils
import com.example.domain.PersianNumberUtils
import com.example.ui.components.BankLogoAvatar
import com.example.ui.components.JibemanAssetLogoCache
import com.example.ui.components.OrosiAmountText
import com.example.ui.theme.AlabasterSilk
import com.example.ui.theme.NishapurTurquoise
import com.example.ui.theme.NishapurTurquoiseBright
import com.example.ui.theme.NishapurTurquoiseDeep
import com.example.ui.theme.NishapurTurquoiseSoft
import com.example.ui.theme.OrosiOnSurfaceVariant
import com.example.ui.theme.OrosiSurfaceContainer
import com.example.ui.theme.OrosiSurfaceHigh
import com.example.ui.theme.PersianLapis
import com.example.ui.theme.PersianLapisLight
import com.example.ui.theme.SaffronGoldBright
import com.example.ui.theme.SaffronGoldLight

// Hoisted static shapes and colors matching the HeroMathBreakdownBottomSheet North Star
private val AccCardShape20 = RoundedCornerShape(20.dp)
private val AccSubPillShape14 = RoundedCornerShape(14.dp)
private val AccBtnShape18 = RoundedCornerShape(18.dp)
private val AccHeroCardBg = NishapurTurquoiseDeep.copy(alpha = 0.52f)
private val AccHeroIconBg = NishapurTurquoiseBright.copy(alpha = 0.20f)
private val AccSavingsCardBg = PersianLapis.copy(alpha = 0.55f)
private val AccSavingsIconBg = SaffronGoldBright.copy(alpha = 0.18f)
private val AccTankhahRowBg = Color(0xFF112936)
private val AccUnitTurquoise = NishapurTurquoiseSoft.copy(alpha = 0.80f)
private val AccUnitGold = SaffronGoldLight.copy(alpha = 0.80f)

@Immutable
private data class AccountRowUi(
    val account: BankAccountEntity,
    val logoKey: String?,
    val titleText: String,
    val shortSourceDateLabel: String,
    val formattedBalanceRun: String,
    val balanceColor: Color,
    val unitColor: Color
)

@Composable
fun AccountsScreen(
    bankAccounts: List<BankAccountEntity>,
    hideAmounts: Boolean,
    tankhahModeEnabled: Boolean = false,
    onSetTankhahModeEnabled: (Boolean) -> Unit = {},
    onToggleTankhah: (String) -> Unit,
    onAccountClick: (BankAccountEntity) -> Unit = {},
    onOpenAddAccountModal: () -> Unit,
    onBackToHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBackToHome)

    val currentJalaliYear = remember {
        JalaliCalendarUtils.fromTimestamp(System.currentTimeMillis()).year
    }

    val totalAllAccountsToman = remember(bankAccounts) {
        bankAccounts.sumOf { it.balanceToman.coerceAtLeast(0L) }
    }
    val tankhahAccountsToman = remember(bankAccounts) {
        bankAccounts.filter { it.isTankhah }.sumOf { it.balanceToman.coerceAtLeast(0L) }
    }
    val nonTankhahAccountsToman = remember(bankAccounts) {
        bankAccounts.filter { !it.isTankhah }.sumOf { it.balanceToman.coerceAtLeast(0L) }
    }

    val accountRows = remember(bankAccounts, currentJalaliYear) {
        bankAccounts.map { account ->
            val updatedJalali = JalaliCalendarUtils.fromTimestamp(account.lastUpdatedTimestamp)
            val shortDate = if (updatedJalali.year == currentJalaliYear) {
                "${PersianNumberUtils.toPersianDigits(updatedJalali.day.toString())} ${updatedJalali.monthName}"
            } else {
                updatedJalali.dateWithYear
            }
            val sourcePrefix = if (account.isManual) "دستی" else "پیامک"
            val hasPositiveBalance = account.balanceToman > 0L
            AccountRowUi(
                account = account,
                logoKey = JibemanAssetLogoCache.resolveBankLogoKey(account.bankName),
                titleText = if (account.bankName.startsWith("بانک ")) account.bankName else "بانک ${account.bankName}",
                shortSourceDateLabel = "$sourcePrefix • $shortDate",
                formattedBalanceRun = PersianNumberUtils.formatIsolatedAmountRun(account.balanceToman, false),
                balanceColor = if (hasPositiveBalance) NishapurTurquoiseSoft else SaffronGoldLight,
                unitColor = if (hasPositiveBalance) AccUnitTurquoise else AccUnitGold
            )
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("accounts_screen"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Minimalist Borderless Tonal Summary matching HeroMathBreakdownBottomSheet North Star
        item(key = "accounts_summary_card", contentType = "summary_card") {
            if (tankhahModeEnabled) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("accounts_summary_card"),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(AccHeroCardBg, AccCardShape20)
                            .padding(horizontal = 14.dp, vertical = 14.dp),
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
                                    .background(AccHeroIconBg)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccountBalanceWallet,
                                    contentDescription = null,
                                    tint = NishapurTurquoiseBright,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                            Text(
                                text = "تنخواه (روزمره)",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = NishapurTurquoiseSoft,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        OrosiAmountText(
                            amountToman = tankhahAccountsToman,
                            color = AlabasterSilk,
                            numberStyle = MaterialTheme.typography.titleMedium.copy(fontSize = 15.5.sp),
                            numberFontWeight = FontWeight.ExtraBold,
                            unitStyle = MaterialTheme.typography.labelSmall,
                            unitColor = NishapurTurquoiseSoft,
                            hideAmount = hideAmounts,
                            stackedUnit = true,
                            modifier = Modifier.testTag("tankhah_accounts_balance_text")
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(AccSavingsCardBg, AccCardShape20)
                            .padding(horizontal = 14.dp, vertical = 14.dp),
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
                                    .background(AccSavingsIconBg)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Savings,
                                    contentDescription = null,
                                    tint = SaffronGoldBright,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                            Text(
                                text = "سایر حساب‌ها",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = PersianLapisLight,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        OrosiAmountText(
                            amountToman = nonTankhahAccountsToman,
                            color = SaffronGoldLight,
                            numberStyle = MaterialTheme.typography.titleMedium.copy(fontSize = 15.5.sp),
                            numberFontWeight = FontWeight.ExtraBold,
                            unitStyle = MaterialTheme.typography.labelSmall,
                            unitColor = AccUnitGold,
                            hideAmount = hideAmounts,
                            stackedUnit = true,
                            modifier = Modifier.testTag("total_accounts_balance_text")
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AccHeroCardBg, AccCardShape20)
                        .padding(horizontal = 18.dp, vertical = 16.dp)
                        .testTag("accounts_summary_card"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(AccHeroIconBg)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountBalanceWallet,
                                contentDescription = null,
                                tint = NishapurTurquoiseBright,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "موجودی کل حساب‌ها",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = NishapurTurquoiseSoft
                            )
                            Text(
                                text = "${PersianNumberUtils.toPersianDigits(bankAccounts.size.toString())} حساب فعال",
                                style = MaterialTheme.typography.labelSmall,
                                color = OrosiOnSurfaceVariant
                            )
                        }
                    }

                    OrosiAmountText(
                        amountToman = totalAllAccountsToman,
                        color = AlabasterSilk,
                        numberStyle = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                        numberFontWeight = FontWeight.ExtraBold,
                        unitStyle = MaterialTheme.typography.labelSmall,
                        unitColor = NishapurTurquoiseSoft,
                        hideAmount = hideAmounts,
                        modifier = Modifier.testTag("total_accounts_balance_text")
                    )
                }
            }
        }

        // 2. Borderless Tonal Switch: تفکیک حساب تنخواه / شرکت
        item(key = "tankhah_mode_card", contentType = "tankhah_switch") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(OrosiSurfaceContainer, AccCardShape20)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .testTag("enable_tankhah_mode_card"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "تفکیک حساب تنخواه / شرکت",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp
                        ),
                        color = AlabasterSilk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (tankhahModeEnabled) {
                            "کارت‌های تنخواه از حساب‌های شخصی جدا هستند"
                        } else {
                            "مجموع همه حساب‌ها یکجا نمایش داده می‌شود"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = OrosiOnSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = tankhahModeEnabled,
                    onCheckedChange = onSetTankhahModeEnabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = AlabasterSilk,
                        checkedTrackColor = NishapurTurquoise
                    ),
                    modifier = Modifier.testTag("enable_tankhah_mode_switch")
                )
            }
        }

        // 3. Calm Section Header
        item(key = "accounts_section_header", contentType = "section_header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, start = 4.dp, end = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "حساب‌های بانکی و منابع مالی",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp
                    ),
                    color = PersianLapisLight
                )
                Text(
                    text = "برای ویرایش لمس کنید",
                    style = MaterialTheme.typography.labelSmall,
                    color = OrosiOnSurfaceVariant.copy(alpha = 0.78f)
                )
            }
        }

        // 4. Minimalist Borderless Bank Account Cards (single-line short date + subtle ChevronLeft)
        items(
            items = accountRows,
            key = { it.account.bankName },
            contentType = { "bank_account_card" }
        ) { row ->
            BankAccountRowCard(
                row = row,
                hideAmounts = hideAmounts,
                tankhahModeEnabled = tankhahModeEnabled,
                onClick = { onAccountClick(row.account) },
                onToggleTankhah = { onToggleTankhah(row.account.bankName) }
            )
        }

        // 5. Primary Add Source Button
        item(key = "add_financial_source_footer", contentType = "footer_button") {
            Spacer(modifier = Modifier.height(6.dp))
            Button(
                onClick = onOpenAddAccountModal,
                shape = AccBtnShape18,
                colors = ButtonDefaults.buttonColors(
                    containerColor = NishapurTurquoise,
                    contentColor = AlabasterSilk
                ),
                contentPadding = PaddingValues(vertical = 15.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("add_financial_source_button")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "افزودن منبع مالی",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun BankAccountRowCard(
    row: AccountRowUi,
    hideAmounts: Boolean,
    tankhahModeEnabled: Boolean,
    onClick: () -> Unit,
    onToggleTankhah: () -> Unit
) {
    val account = row.account
    val cardBg = if (tankhahModeEnabled && account.isTankhah) AccTankhahRowBg else OrosiSurfaceContainer

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(cardBg, AccCardShape20)
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 13.dp)
            .testTag("account_card_${account.bankName}"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BankLogoAvatar(
                bankName = account.bankName,
                size = 40.dp,
                preResolvedLogoKey = row.logoKey
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = row.titleText,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.5.sp
                    ),
                    color = AlabasterSilk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = row.shortSourceDateLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
                    color = OrosiOnSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }

            OrosiAmountText(
                amountToman = account.balanceToman,
                color = row.balanceColor,
                numberStyle = MaterialTheme.typography.titleSmall.copy(fontSize = 14.5.sp),
                numberFontWeight = FontWeight.ExtraBold,
                unitStyle = MaterialTheme.typography.labelSmall,
                unitColor = row.unitColor,
                hideAmount = hideAmounts,
                preformattedNumberRun = row.formattedBalanceRun
            )

            Icon(
                imageVector = Icons.Default.ChevronLeft,
                contentDescription = "ویرایش حساب",
                tint = OrosiOnSurfaceVariant.copy(alpha = 0.65f),
                modifier = Modifier.size(18.dp)
            )
        }

        if (tankhahModeEnabled) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(OrosiSurfaceHigh, AccSubPillShape14)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "حساب تنخواه (خرج روزمره)",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = AlabasterSilk
                )
                Switch(
                    checked = account.isTankhah,
                    onCheckedChange = { onToggleTankhah() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = AlabasterSilk,
                        checkedTrackColor = NishapurTurquoise
                    ),
                    modifier = Modifier.testTag("account_tankhah_switch_${account.bankName}")
                )
            }
        }
    }
}
